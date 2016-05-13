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

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Date;

import static us.hebi.matlab.streaming.Preconditions.*;
import static us.hebi.matlab.streaming.Resources.*;

/**
 * Allocates memory that can be accessed from other processes. Instantiated objects are
 * not thread safe. The byte order of the backing buffer is the native order.
 *
 * @author Florian Enner < florian @ hebirobotics.com >
 * @since 02 May 2015
 */
public class SharedMemory implements Closeable {

    public static SharedMemory allocate(int numBytes) {
        return new SharedMemory(numBytes);
    }

    public ByteBuffer clearBuffer() {
        return (ByteBuffer) buffer.clear();
    }

    @Override
    public void close() throws IOException {
        closeDirectBuffer(buffer);
        closeSilently(channel);
        if (!backingFile.delete()) {
            backingFile.deleteOnExit();
            throw new IOException("Could not delete the backing file. Are other processes still mapping it?");
        }
    }

    public File getBackingFile() {
        return backingFile;
    }

    public boolean isOpen() {
        return channel.isOpen();
    }

    private SharedMemory(int numBytes) {
        checkArgument(numBytes > 0, "Allocation must be greater than zero bytes");

        // Create uniquely named file
        backingFile = new File("shared_" + dateFormat.get().format(new Date()) + ".tmp");
        checkState(!backingFile.exists(), "Generated unique name already exists");

        // Load shared memory
        try {
            channel = new RandomAccessFile(backingFile, "rw")
                    .getChannel();
            buffer = channel
                    .map(FileChannel.MapMode.READ_WRITE, 0, numBytes)
                    .load();
            buffer.order(ByteOrder.nativeOrder());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    final File backingFile;
    final FileChannel channel;
    final MappedByteBuffer buffer;

    private static final ThreadLocal<SimpleDateFormat> dateFormat = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss.SSS");
        }
    };

}
