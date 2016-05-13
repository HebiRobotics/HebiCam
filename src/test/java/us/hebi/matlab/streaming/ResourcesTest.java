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

import org.junit.Test;

import static org.junit.Assert.*;
import static us.hebi.matlab.streaming.Resources.isRemoteUri;

/**
 * @author Florian Enner < florian @ hebirobotics.com >
 * @since 02 May 2015
 */
public class ResourcesTest {

    @Test
    public void testIsValidUrl() throws Exception {
       assertTrue(isRemoteUri("http://127.0.0.1:80/axis-cgi/mjpg/video.cgi")); // Axis mjpeg
       assertTrue(isRemoteUri("rtsp://127.0.0.2/axis-media/media.amp")); // Axis h264
       assertTrue(isRemoteUri("rtsp://127.0.0.3/media/video1")); // Sony camera
       assertTrue(isRemoteUri("rtsp://127.0.0.4:554/live/ch00_0")); // Some other ip cam

       assertFalse(isRemoteUri("/home/user/name/myFile.avi")); // Linux file
       assertFalse(isRemoteUri("/dev/video0")); // Linux device
       assertFalse(isRemoteUri("C:/myFile.avi")); // Windows file

    }

}