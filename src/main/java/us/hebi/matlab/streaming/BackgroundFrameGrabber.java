/*
 * Copyright (c) 2015-2016 HEBI Robotics
 *
 * Licensed either under the Apache License, Version 2.0, or (at your option)
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation (subject to the "Classpath" exception),
 * either version 2, or any later version (collectively, the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *     http://www.gnu.org/licenses/
 *     http://www.gnu.org/software/classpath/license.html
 *
 *  or as provided in the LICENSE.txt file that accompanied this code.
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package us.hebi.matlab.streaming;

import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static java.util.concurrent.TimeUnit.SECONDS;
import static us.hebi.matlab.streaming.Preconditions.*;
import static us.hebi.matlab.streaming.Resources.*;

/**
 * Backing Java class for MATLAB's VideoInput-like functionality
 *
 * @author Florian Enner < florian @ hebirobotics.com >
 * @since 01 May 2015
 */
public class BackgroundFrameGrabber {

    public BackgroundFrameGrabber(FrameGrabber grabber) throws FrameGrabber.Exception {
        this.grabber = checkNotNull(grabber, "FrameGrabber can't be empty");
        this.grabberTimeoutMs = this.grabber.getTimeout();

        // Grab first frame to initialize converter and shared memory with correct dimensions
        grabber.start();
        Frame frame = grabber.grabFrame();
        channels = frame.imageChannels;

        switch (channels) {
            case 3:
                matlabImageConverter = new MatlabImageConverterBGR(frame.imageWidth, frame.imageHeight);
                break;
            case 1:
                matlabImageConverter = new MatlabImageConverterGrayscale(frame.imageWidth, frame.imageHeight);
                break;
            default:
                throw new IllegalArgumentException("Unsupported number of channels: " + channels);
        }

        sharedMemory = SharedMemory.allocate(HEADER_BYTES + frame.imageWidth * frame.imageHeight * channels);

    }

    public int getHeight() {
        return grabber.getImageHeight();
    }

    public int getWidth() {
        return grabber.getImageWidth();
    }

    public int getChannels() {
        return channels;
    }

    public String getBackingFile() {
        return sharedMemory.getBackingFile().getPath();
    }

    /**
     * Less-blocking call that does not wait for a new image, i.e., users read whatever is in the buffer
     *
     * @return true if the lock has been acquired
     */
    @Deprecated // experimental non-public feature
    public boolean tryGetImageLock() {
        // Make sure users don't have the lock already (e.g. ctrl-c in MATLAB while reading the data)
        tryReleaseImageLock();

        // Immediately return if acquisition is not active
        if (!active) throw new IllegalStateException("grabber is not active");

        memoryAccessLock.lock();
        userHasLock = true;
        return true;

    }

    /**
     * @return true if the shared memory has been updated and the memory lock has been acquired. False if
     * the lock has not been acquired.
     */
    public boolean tryGetNextImageLock() {
        // Make sure users don't have the lock already (e.g. ctrl-c in MATLAB while reading the data)
        tryReleaseImageLock();

        // Immediately return if acquisition is not active
        if (!active) return false;

        // Try locking immediately in case the image has already updated
        if (tryLockUpdatedImage()) {
            return true;
        }

        // Wait for arrival notification or time out
        synchronized (arrivalNotification) {
            try {
                arrivalNotification.wait(grabberTimeoutMs);
            } catch (InterruptedException e) {
            }
        }

        // Try a final time
        return tryLockUpdatedImage();

    }

    /**
     * @return true if the image has updated and the lock has been acquired.
     */
    private boolean tryLockUpdatedImage() {
        if (hasUpdated) {
            memoryAccessLock.lock();
            userHasLock = true;
            hasUpdated = false;
            return true;
        }
        return false;
    }

    /**
     * @return true if the user had the lock and it has been successfully released
     */
    public void tryReleaseImageLock() {
        if (userHasLock) {
            userHasLock = false;
            memoryAccessLock.unlock();
        }
    }

    public void start() {
        checkState(active, "VideoInput must not have been stopped yet");
        Thread thread = new Thread(acquisitionLoop);
        thread.setDaemon(true);
        thread.setName("VideoInput-" + threadCounter.getAndIncrement());
        thread.start();
    }

    public void stop() throws FrameGrabber.Exception {
        // Return immediately if acquisition has already
        // stopped, e.g., on multiple calls to stop().
        if (!active) return;
        active = false;

        // Make sure that the lock is released. Note that in MATLAB this will always happen in the
        // same thread as the lock acquisition, so there can't be user-race conditions here
        tryReleaseImageLock();

        // Close shared memory
        memoryAccessLock.lock();
        try {
            closeSilently(sharedMemory);
        } finally {
            memoryAccessLock.unlock();
        }

        // Close grabber
        synchronized (grabber) {
            grabber.stop();
        }

    }

    private void runAcquisitionLoop() throws FrameGrabber.Exception, IOException, InterruptedException {
        checkState(active, "VideoInput must be active");

        while (active) {

            // Read next image from device
            Frame frame = null;
            long frameNumber = 0;
            double timestamp = 0;
            synchronized (grabber) {
                frame = grabber.grabFrame();
                frameNumber = grabber.getFrameNumber();
                timestamp = grabber.getTimestamp() * 1E-6; // [us] to [s]
            }

            // Retry grabbing frames after a timeout. Note that disconnecting IP cameras
            // shows up as null frames, and not a FrameGrabber Exception. If a connection
            // is legitimately disconnected, the MATLAB script should stop the acquisition.
            if (frame == null) {
                int retryTimeoutMs = 100;
                Thread.sleep(retryTimeoutMs);
                continue;
            }

            // Acquire lock - note that we time out after a reasonable time in order to avoid deadlocks
            // if users don't release locks properly (e.g. ctrl-c during copy).
            boolean hasLock = memoryAccessLock.tryLock(1, SECONDS);
            if (!hasLock)
                continue;

            try {
                // Make sure memory is active and update
                if (!sharedMemory.isOpen())
                    return;

                // Write frame meta data to memory
                ByteBuffer buffer = sharedMemory.clearBuffer();
                buffer.putLong(frameNumber);
                buffer.putDouble(timestamp);

                // Add image data in a MATLAB readable format
                matlabImageConverter.writeFrameToBuffer(frame, buffer);

            } finally {
                memoryAccessLock.unlock();
            }

            // Notify listeners that the data has updated
            hasUpdated = true;
            synchronized (arrivalNotification) {
                arrivalNotification.notifyAll();
            }

        }

    }

    private final Runnable acquisitionLoop = new Runnable() {
        @Override
        public void run() {
            try {
                runAcquisitionLoop();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    };

    // Setup
    final FrameGrabber grabber;
    final MatlabImageConverter matlabImageConverter;
    final SharedMemory sharedMemory;
    final Lock memoryAccessLock = new ReentrantLock();
    private final long grabberTimeoutMs;
    private final int HEADER_BYTES = 16; // 8 [frame#] + 8 [timestamp]
    private final int channels;

    // State
    final Object arrivalNotification = new Object();
    volatile boolean hasUpdated = false;
    volatile boolean active = true;
    volatile boolean userHasLock = false;

    private static final AtomicInteger threadCounter = new AtomicInteger(0);

}
