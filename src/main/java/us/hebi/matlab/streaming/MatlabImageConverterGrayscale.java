package us.hebi.matlab.streaming;

import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.OpenCVFrameConverter;

import java.nio.ByteBuffer;

import static org.bytedeco.javacpp.opencv_core.*;
import static us.hebi.matlab.streaming.Preconditions.*;

/**
 * Converts grayscale images to a MATLAB readable column-major format.
 *
 * @author Florian Enner < florian @ hebirobotics.com >
 * @since 04 Dec 2016
 */
class MatlabImageConverterGrayscale implements MatlabImageConverter {

    @Override
    public void writeFrameToBuffer(Frame source, ByteBuffer destination) {
        checkNotNull(source);
        checkNotNull(destination);

        // Convert to OpenCV format and use OpenCV to reshape memory
        Mat mat = matConverter.convertToMat(source); // Internally reuses the same Mat
        if (mat == null || mat.rows() != height || mat.cols() != width || mat.channels() != 1) {
            System.err.println("Unexpected image dimensions. Skipping frame.");
            return;
        }

        // Transpose to column-major format and write to destination buffer
        transpose(mat, transposed);
        buffer.position(0);
        destination.put(buffer);

    }

    MatlabImageConverterGrayscale(int width, int height) {
        // Initialize all pointers with dummy data
        this.width = width;
        this.height = height;

        Mat mat = new Mat(height, width, CV_8U);
        transpose(mat, transposed);
        buffer = transposed.createBuffer();
        mat.release();

    }

    final int width;
    final int height;

    final Mat transposed = new Mat();
    final ByteBuffer buffer;

    final OpenCVFrameConverter<Mat> matConverter = new OpenCVFrameConverter.ToMat();

}
