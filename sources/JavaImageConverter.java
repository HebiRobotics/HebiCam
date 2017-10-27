package sources;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class JavaImageConverter {

    public void setImage(Object rawImg, String jpegPath) throws Exception {
        int width, height, channels;
        String format;

        if (rawImg instanceof byte[][][]) { // rgb
            byte[][][] matrix = (byte[][][]) rawImg;
            height = matrix.length;
            width = matrix[0].length;
            channels = matrix[0][0].length;
            format = String.format("byte[%d][%d][%d]", height, width, channels);

        } else if (rawImg instanceof byte[][]) { // grayscale
            byte[][] matrix = (byte[][]) rawImg;
            height = matrix.length;
            width = matrix[0].length;
            channels = 1;
            format = String.format("byte[%d][%d]", height, width);

        } else {
            throw new IllegalArgumentException("Unsupported format");
        }

        System.out.println(format);

        // get buffered image. MATLAB's im2java2d() unfortunately requiers a toolbox,
        // but we can load the jpeg data directly and store it in a BufferedImage.
        BufferedImage bufferedImage = ImageIO.read(new File(jpegPath));

        // convert data to various formats
        this.rawFormat3d = rawImg;
        this.matlabPixelFormat1d = toMatlabPixelFormat(rawImg, height, width, channels);
        this.javaPixelFormat1d = ((DataBufferByte) bufferedImage.getRaster().getDataBuffer()).getData();
        this.jpegData = compressToJpeg(bufferedImage);

    }

    /**
     * convert to 1d array with matlab's col major memory layout ([c][w][h])
     */
    private static byte[] toMatlabPixelFormat(Object img, int height, int width, int channels) {
        byte[] result = new byte[height * width * channels];
        int i = 0;
        for (int c = 0; c < channels; c++) {
            for (int w = 0; w < width; w++) {
                for (int h = 0; h < height; h++) {

                    if (img instanceof byte[][]) { // grayscale
                        result[i++] = ((byte[][]) img)[h][w];

                    } else if (img instanceof byte[][][]) { // rgb
                        result[i++] = ((byte[][][]) img)[h][w][c];
                    } else {
                        throw new IllegalArgumentException();
                    }

                }
            }
        }
        return result;
    }

    private byte[] compressToJpeg(BufferedImage bufferedImage) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ImageIO.write(bufferedImage, "jpg", buffer);
        return buffer.toByteArray();
    }

    public Object getRawFormat3d() {
        return rawFormat3d;
    }

    public byte[] getMatlabPixelFormat1d() {
        return matlabPixelFormat1d;
    }

    public byte[] getJavaPixelFormat1d() {
        return javaPixelFormat1d;
    }

    public byte[] getJpegData() {
        return jpegData;
    }

    public void lock() {
        if(isLocked)
            throw new IllegalStateException("Already locked");
        lock.readLock().lock();
        isLocked = true;
    }

    public void tryUnlock() {
        if(isLocked){
            lock.readLock().unlock();
            isLocked = false;
        }
    }

    Object rawFormat3d; // e.g. byte[1080][1920][3] for rgb or byte[1080][1920] for grayscale
    byte[] matlabPixelFormat1d; // e.g. byte[3x1920x1080]
    byte[] javaPixelFormat1d; // e.g. byte[1080x1920x3]
    byte[] jpegData; // JPEG compressed data

    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private boolean isLocked = false;

}