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

import org.bytedeco.javacpp.avutil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.OpenCVFrameGrabber;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
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
public class BackingHebiCam {

    public static BackingHebiCam open(Object object) throws Exception {

        if (object instanceof Number) {

            // Numbered local device
            int matlabIndex = ((Number) object).intValue();
            checkArgument(matlabIndex > 0, "Device can't be below 1");
            int device = matlabIndex - 1; // convert MATLAB (one based) index to Java index (zero based)
            final FrameGrabber grabber = new OpenCVFrameGrabber(device);
            return new BackingHebiCam(grabber);

        } else if (object instanceof String) {
            String device = (String) object;

            // Remote URLs
            if (Resources.isRemoteUri(device)) {
                return openRemoteUri(new URI(device));
            }

            // Named local device
            final FrameGrabber grabber = new OpenCVFrameGrabber(device);
            return new BackingHebiCam(grabber);

        }

        throw new IllegalArgumentException("Expected device number, path, or remote URL");

    }

    private static BackingHebiCam openRemoteUri(URI uri) throws IOException, FrameGrabber.Exception {
        InetAddress device = InetAddress.getByName(checkNotNull(uri).getHost());
        checkNotNull(device, "No ip address found");

        // Check whether the address is reachable (ICMP ping) to avoid an infinite wait (bug?) when
        // connecting to ip cameras that are not on the network.
        int timeoutMs = 5000;
        if (!device.isReachable(timeoutMs)) {
            throw new IllegalArgumentException("Could not ping camera on address " + device + ". " +
                    "Please confirm that the address is correct and that the device is turned on.");
        }

        // Use FFmpeg for streaming
        final FrameGrabber grabber = new FFmpegFrameGrabber(uri.toString());

        // Assume that http:// urls are mjpeg streams to avoid errors. This has been true for all ip cameras
        // that I've tested (other encodings were rtsp://), but there may needs to be another flag to support
        // some more exotic cameras.
        if ("http".equalsIgnoreCase(uri.getScheme()))
            grabber.setFormat("mjpeg");
        return new BackingHebiCam(grabber);
    }

    public int getHeight() {
        return grabber.getImageHeight();
    }

    public int getWidth() {
        return grabber.getImageWidth();
    }

    public int getChannels() {
        switch (grabber.getImageMode()) {
            case COLOR:
                return 3;
            case GRAY:
                return 1;
            default:
                throw new IllegalStateException("Unknown image mode");
        }
    }

    public String getBackingFile() {
        return sharedMemory.getBackingFile().getPath();
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
                arrivalNotification.wait(DEFAULT_ACQUISITION_TIMEOUT_MS);
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
            synchronized (grabber) {
                frame = grabber.grabFrame();
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
            // if users don't release locks properly.
            boolean hasLock = memoryAccessLock.tryLock(1, SECONDS);
            if (!hasLock)
                continue;

            try {
                // Make sure memory is active and update
                if (!sharedMemory.isOpen())
                    return;

                // Write to memory in column major (MATLAB) format
                rgbConverter.convertFrameInto(frame, sharedMemory.clearBuffer());
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

    private BackingHebiCam(FrameGrabber grabber) throws FrameGrabber.Exception, IOException {
        this.grabber = checkNotNull(grabber);

        // Set a timeout in case a camera gets disconnected or shutdown. Note that this only works
        // for grabFrame() and not start()
        grabber.setTimeout(DEFAULT_ACQUISITION_TIMEOUT_MS);

        // Disable repeated log lines such as errors and warnings
        avutil.av_log_set_level(avutil.AV_LOG_SKIP_REPEATED);

        // Grab first frame to initialize converter and shared memory with correct dimensions
        grabber.start();
        Frame frame = grabber.grabFrame();
        checkArgument(getChannels() == 3, "Currently only RGB images are supported");
        rgbConverter = MatlabImageConverter.createRgbConverter(frame.imageWidth, frame.imageHeight);
        sharedMemory = SharedMemory.allocate(frame.imageWidth * frame.imageHeight * 3);

    }

    // Setup
    final FrameGrabber grabber;
    final MatlabImageConverter rgbConverter;
    final SharedMemory sharedMemory;
    final Lock memoryAccessLock = new ReentrantLock();
    final static int DEFAULT_ACQUISITION_TIMEOUT_MS = 1000;

    // State
    final Object arrivalNotification = new Object();
    volatile boolean hasUpdated = false;
    volatile boolean active = true;
    volatile boolean userHasLock = false;

    private static final AtomicInteger threadCounter = new AtomicInteger(0);

}
