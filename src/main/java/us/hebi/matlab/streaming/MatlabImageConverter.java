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

import org.bytedeco.javacpp.opencv_core.*;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.OpenCVFrameConverter;

import java.nio.ByteBuffer;

import static org.bytedeco.javacpp.opencv_core.*;
import static us.hebi.matlab.streaming.Preconditions.*;

/**
 * Utility classes that convert Frames into MATLAB's column-major format
 *
 * @author Florian Enner < florian @ hebirobotics.com >
 * @since 01 May 2015
 */
public abstract class MatlabImageConverter {

    public static MatlabImageConverter createRgbConverter(int width, int height) {
        return new RgbConverter(width, height);
    }

    private static class RgbConverter extends MatlabImageConverter {

        @Override
        public void convertMatInto(final Mat source, final ByteBuffer destination) {
            checkNotNull(destination);
            checkArgument(source != null
                    && source.rows() == height
                    && source.cols() == width
                    && source.channels() == 3, "bad dimensions");

            // Split into individual colors and and transpose each channel to
            // get Matlab-like column major format
            split(source, bgr);
            transpose(bgr.get(2), rgbTransposed.get(0));
            transpose(bgr.get(1), rgbTransposed.get(1));
            transpose(bgr.get(0), rgbTransposed.get(2));

            // Write to dest buffer
            r.position(0);
            g.position(0);
            b.position(0);
            destination.put(r).put(g).put(b);

        }

        RgbConverter(int width, int height) {
            // Initialize all pointers with dummy data
            this.width = width;
            this.height = height;
            Mat mat = new Mat(height, width, CV_8UC3);

            // Convert
            split(mat, bgr);
            transpose(bgr.get(2), rgbTransposed.get(0));
            transpose(bgr.get(1), rgbTransposed.get(1));
            transpose(bgr.get(0), rgbTransposed.get(2));

            // Create buffers
            r = rgbTransposed.get(0).createBuffer();
            g = rgbTransposed.get(1).createBuffer();
            b = rgbTransposed.get(2).createBuffer();

            // Cleanup
            mat.release();
        }

        // Vectors will be released automatically by the garbage collector
        final MatVector bgr = new MatVector(3);
        final MatVector rgbTransposed = new MatVector(3);

        final ByteBuffer r;
        final ByteBuffer g;
        final ByteBuffer b;

        final int width;
        final int height;

    }

    public abstract void convertMatInto(Mat mat, ByteBuffer destination);

    public void convertFrameInto(Frame source, ByteBuffer destination) {
        checkNotNull(source);
        checkNotNull(destination);

        // Convert to OpenCV format
        Mat mat = null;
        try {
            mat = frameConverter.convertToMat(source);
            convertMatInto(mat, destination);
        } finally {
            if (mat != null) {
                mat.release();
            }
        }

    }

    final OpenCVFrameConverter<Mat> frameConverter = new OpenCVFrameConverter.ToMat();

}
