package org.unizin.cmp.oai.harvester;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import com.google.common.io.ByteStreams;

public final class IOUtils {

    public static String stringFromStream(final InputStream in)
            throws IOException {
        try (final InputStream is = in) {
            final byte[] bytes = ByteStreams.toByteArray(is);
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    public static InputStream streamFromString(final String string) {
        return new ByteArrayInputStream(string.getBytes(
                StandardCharsets.UTF_8));
    }

    public static InputStream streamFromClasspathFile(final String filename) {
        final InputStream in = IOUtils.class.getResourceAsStream(filename);
        if (in == null) {
            throw new IllegalArgumentException("File " + filename +
                    " not found on the classpath.");
        }
        return in;
    }

    public static String stringFromClasspathFile(final String filename)
            throws IOException {
        return stringFromStream(streamFromClasspathFile(filename));
    }

    /** No instances allowed. */
    private IOUtils() {}
}
