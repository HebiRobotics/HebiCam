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
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;

import static us.hebi.matlab.streaming.Preconditions.checkNotNull;

/**
 * @author Florian Enner < florian @ hebirobotics.com >
 * @since 01 May 2015
 */
public class Resources {

    public static void closeSilently(Closeable resource) {
        if (resource != null) {
            try {
                resource.close();
            } catch (IOException e) {
                // Ignore
            }
        }
    }

    private Resources() {

    }

    /**
     * source:  http://stackoverflow.com/a/19447758/3574093
     *
     * @param cb
     */
    public static void closeDirectBuffer(final ByteBuffer cb) {
        checkNotNull(cb);
        if (!cb.isDirect()) return;

        // we could use this type cast and call functions without reflection code,
        // but static import from sun.* package is risky for non-SUN virtual machine.
        //try { ((sun.nio.ch.DirectBuffer)cb).cleaner().clean(); } catch (Exception ex) { }
        try {
            Method cleaner = cb.getClass().getMethod("cleaner");
            cleaner.setAccessible(true);
            Method clean = Class.forName("sun.misc.Cleaner").getMethod("clean");
            clean.setAccessible(true);
            clean.invoke(cleaner.invoke(cb));
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }

}
