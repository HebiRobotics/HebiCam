package us.hebi.matlab.streaming;

import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.OpenCVFrameConverter;

import java.nio.ByteBuffer;

import static org.bytedeco.javacpp.opencv_core.*;
import static us.hebi.matlab.streaming.Preconditions.*;

/**
 * Converts BGR color images to a MATLAB readable column-major format.
 *
 * @author Florian Enner < florian @ hebirobotics.com >
 * @since 04 Dec 2016
 */
class MatlabImageConverterBGR implements MatlabImageConverter {

    @Override
    public void writeFrameToBuffer(Frame source, ByteBuffer destination) {
        checkNotNull(source);
        checkNotNull(destination);

        // Convert to OpenCV format and use OpenCV to reshape memory
        Mat mat = matConverter.convertToMat(source); // Internally reuses the same Mat
        if (mat == null || mat.rows() != height || mat.cols() != width || mat.channels() != 3) {
            System.err.println("Unexpected image dimensions. Skipping frame.");
            return;
        }

        // Split into individual colors and and transpose each channel to
        // get Matlab-like column major format
        split(mat, bgr);
        transpose(bgr.get(2), rgbTransposed.get(0));
        transpose(bgr.get(1), rgbTransposed.get(1));
        transpose(bgr.get(0), rgbTransposed.get(2));

        // Write to dest buffer
        r.position(0);
        g.position(0);
        b.position(0);
        destination.put(r).put(g).put(b);

    }

    MatlabImageConverterBGR(int width, int height) {
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

    final OpenCVFrameConverter<Mat> matConverter = new OpenCVFrameConverter.ToMat();

}
