package us.hebi.matlab.streaming;

import java.io.File;
import java.io.IOException;
import java.net.*;

import static us.hebi.matlab.streaming.Preconditions.*;

/**
 * Class that provides convenient helper methods to parse the
 * various possible device locations in MATLAB, e.g.,
 * <p>
 * - Numbered device, e.g., "1"
 * - Remote URL, e.g., rtsp://10.10.10.10/video/
 * - Local file identifier, e.g., /dev/usb0
 * <p>
 *
 * @author Florian Enner < florian @ hebirobotics.com >
 * @since 04 Dec 2016
 */
public class DeviceLocation {

    public DeviceLocation(Object uri) throws URISyntaxException, IOException {
        // Fail fast on bad input
        if (uri instanceof Number) {
            checkNumber((Number) uri);
        } else if (uri instanceof String) {
        } else {
            throw new IllegalArgumentException("Device location must be a String or Integer.");
        }
        this.uri = uri;

    }

    public boolean isNumber() {
        return uri instanceof Number;
    }

    private void checkNumber(Number number) {
        double device = ((Number) number).doubleValue();
        checkArgument(device == Math.rint(device), "Device number must be an integer, not a float.");
        checkArgument(device > 0, "Device must be greater or equal to 1.");
    }

    public boolean isFile() {
        return uri instanceof String && new File((String) uri).exists();
    }

    public boolean isUrl() {
        try {
            if (uri instanceof String) {
                URI uri = new URI((String) this.uri);
                return uri.isAbsolute() && uri.getHost() != null;
            }
        } catch (URISyntaxException e) {
            return false;
        }
        return false;
    }

    public boolean hasUrlScheme(String scheme) {
        checkNotNull(scheme, "Scheme can't be empty.");
        try {
            return uri instanceof String && scheme.equalsIgnoreCase(new URI((String) uri).getScheme());
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /**
     * Check whether the address is reachable (ICMP ping) to avoid an infinite wait
     * (bug?) when connecting to IP cameras that are not on the network.
     *
     * @param timeout timeout in seconds
     * @return true if host is reachable within timeout
     */
    public boolean isReachableUrl(double timeout) throws IOException {
        if (!isUrl()) // makes sure that host exists
            return false;

        try {
            InetAddress host = InetAddress.getByName(new URI((String) uri).getHost());
            int millis = (int) (timeout * 1E6);
            return host.isReachable(millis);
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private final Object uri;

}
