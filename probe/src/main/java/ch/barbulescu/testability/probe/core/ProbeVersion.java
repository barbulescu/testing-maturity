package ch.barbulescu.testability.probe.core;

import java.io.InputStream;
import java.util.Properties;

/**
 * The probe's own version, which the build writes into {@code probe.properties}. Releases
 * take their version from the tag, so it can't be a constant in the code.
 */
final class ProbeVersion {

    static final String UNKNOWN = "unknown";

    private ProbeVersion() {
    }

    /** Never throws: without the file, the report says {@value #UNKNOWN}. */
    static String read() {
        try (InputStream in = ProbeVersion.class.getResourceAsStream("probe.properties")) {
            if (in == null) {
                return UNKNOWN;
            }
            Properties properties = new Properties();
            properties.load(in);
            String version = properties.getProperty("version");
            return version == null || version.isEmpty() || version.startsWith("@") ? UNKNOWN : version;
        } catch (Throwable t) {
            return UNKNOWN;
        }
    }
}
