package us.hebi.matlab.streaming;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Florian Enner < florian @ hebirobotics.com >
 * @since 04 Dec 2016
 */
public class DeviceLocationTest {

    @Test
    public void isNumber() throws Exception {
        assertTrue(new DeviceLocation(1).isNumber());
        assertTrue(new DeviceLocation(2).isNumber());
        assertTrue(new DeviceLocation(3).isNumber());
        assertFalse(new DeviceLocation("http://github.com/video").isNumber());
        assertFalse(new DeviceLocation("/dev/0").isNumber());

        try {
            new DeviceLocation(-1).isNumber();
            fail("Negative number (-1)");
        } catch (IllegalArgumentException iae) {
        }

        try {
            new DeviceLocation(0).isNumber();
            fail("Negative number (0)");
        } catch (IllegalArgumentException iae) {
        }

        try {
            new DeviceLocation(2.3).isNumber();
            fail("Float");
        } catch (IllegalArgumentException iae) {
        }

    }

    @Test
    public void isUrl() throws Exception {
        assertTrue(new DeviceLocation("http://127.0.0.1:80/axis-cgi/mjpg/video.cgi").isUrl()); // Axis mjpeg
        assertTrue(new DeviceLocation("rtsp://127.0.0.2/axis-media/media.amp").isUrl()); // Axis h264
        assertTrue(new DeviceLocation("rtsp://127.0.0.3/media/video1").isUrl()); // Sony camera
        assertTrue(new DeviceLocation("rtsp://127.0.0.4:554/live/ch00_0").isUrl()); // Misc ip camera
        assertTrue(new DeviceLocation("http://github.com/video").isUrl()); // web address
        assertTrue(new DeviceLocation("rtsp://10.10.10.1/video?user=x&pw=y").isUrl());
        assertTrue(new DeviceLocation("rtsp://user:pw@10.10.10.1/video").isUrl());

        assertFalse(new DeviceLocation(1).isUrl()); // Numbered device
        assertFalse(new DeviceLocation("/home/user/name/myFile.avi").isUrl()); // Linux file
        assertFalse(new DeviceLocation("/dev/video0").isUrl()); // Linux device
        assertFalse(new DeviceLocation("C:/myFile.avi").isUrl()); // Windows file

        assertTrue(new DeviceLocation("udp://@239.0.0.1:9999").isUrl());
    }

    @Test
    public void hasUrlScheme() throws Exception {
        assertTrue(new DeviceLocation("http://github.com/video").hasUrlScheme("http"));
        assertTrue(new DeviceLocation("rtsp://user:pw@10.10.10.1/video").hasUrlScheme("rtsp"));
        assertFalse(new DeviceLocation("rtsp://user:pw@10.10.10.1/video").hasUrlScheme("http"));
        assertFalse(new DeviceLocation("/dev/usb0").hasUrlScheme("http"));
    }

    /**
     * Mostly omitted for now because I'm not sure how to best check it.
     * new File(url) apparently doesn't complain for web-urls and
     * .isFile() etc. may fail on Windows for paths that are valid
     * on Linux.
     *
     * @throws Exception
     */
    @Test
    public void isFile() throws Exception {
        /*
        assertTrue(new DeviceLocation("/dev/usb0").isFile());
        assertFalse(new DeviceLocation("http://github.com/video").isFile());
        assertFalse(new DeviceLocation("rtsp://10.10.10.1/video").isFile());
        */
        assertFalse(new DeviceLocation(1).isFile());
    }

}