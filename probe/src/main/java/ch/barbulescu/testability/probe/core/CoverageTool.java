package ch.barbulescu.testability.probe.core;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Whether a coverage tool measures this test JVM, not how much it covers.
 * JaCoCo's agent jar is appended to the system class path when it's
 * attached with {@code -javaagent}, and offline-instrumented classes need
 * its runtime on the test classpath, so in both cases its public runtime
 * API class is loadable. Checked via {@code Class.forName(name, false,
 * loader)} so nothing is initialized.
 */
public final class CoverageTool {

    private static final String JACOCO_RUNTIME = "org.jacoco.agent.rt.RT";

    private CoverageTool() {
    }

    public static Map<String, Boolean> detect() {
        return detect(ClassLoader.getSystemClassLoader(), ProbeRecorder.class.getClassLoader());
    }

    static Map<String, Boolean> detect(ClassLoader systemLoader, ClassLoader testLoader) {
        Map<String, Boolean> tools = new LinkedHashMap<>();
        tools.put("jacoco", isPresent(systemLoader, JACOCO_RUNTIME) || isPresent(testLoader, JACOCO_RUNTIME));
        return tools;
    }

    private static boolean isPresent(ClassLoader loader, String className) {
        try {
            Class.forName(className, false, loader);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
