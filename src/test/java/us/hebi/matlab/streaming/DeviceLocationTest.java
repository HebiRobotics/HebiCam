package us.hebi.matlab.streaming;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Florian Enner < florian @ hebirobotics.com >
 * @since 04 Dec 2016
 */
public class CameraLocationTest {

    @Test
    public void isNumber() throws Exception {
        assertTrue(new CameraLocation(1).isNumber());
        assertTrue(new CameraLocation(2).isNumber());
        assertTrue(new CameraLocation(3).isNumber());
        assertFalse(new CameraLocation("http://github.com/video").isNumber());
        assertFalse(new CameraLocation("/dev/0").isNumber());

        try {
            new CameraLocation(-1).isNumber();
            fail("Negative number (-1)");
        }catch (IllegalArgumentException iae){
        }

        try {
            new CameraLocation(0).isNumber();
            fail("Negative number (0)");
        }catch (IllegalArgumentException iae){
        }

        try {
            new CameraLocation(2.3).isNumber();
            fail("Float");
        }catch (IllegalArgumentException iae){
        }

    }

    @Test
    public void isURL() throws Exception {
        assertTrue(new CameraLocation("http://github.com/video").isUrl());
        assertTrue(new CameraLocation("http://127.0.0.1/video").isUrl());
        assertTrue(new CameraLocation("rtsp://10.10.10.1/video").isUrl());
        assertFalse(new CameraLocation(1).isUrl());
        assertFalse(new CameraLocation("/dev/usb0").isUrl());
    }

    @Test
    public void isReachableURL() throws Exception {

    }

    @Test
    public void isFile() throws Exception {
        assertTrue(new CameraLocation("/dev/usb0").isFile());
        assertFalse(new CameraLocation("http://github.com/video").isFile());
        assertFalse(new CameraLocation("rtsp://10.10.10.1/video").isFile());
        assertFalse(new CameraLocation(1).isFile());
    }

}