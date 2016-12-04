package us.hebi.matlab.streaming;

import org.bytedeco.javacv.Frame;

import java.nio.ByteBuffer;

/**
 * Interface for converters that can convert the byte layout of
 * Java or C++ images to MATLAB's column-major format.
 *
 * @author Florian Enner < florian @ hebirobotics.com >
 * @since 01 May 2015
 */
public interface MatlabImageConverter {
    public void writeFrameToBuffer(Frame source, ByteBuffer destination);
}
