package ch.barbulescu.testability.probe.junit;

import org.junit.platform.launcher.TestPlan;

import java.util.Locale;

/**
 * How Jupiter is configured to run tests in parallel, read the way Jupiter reads it: the value
 * trimmed, case ignored. These are Jupiter's parameters; other engines have their own.
 */
final class ParallelExecution {

    private static final String ENABLED = "junit.jupiter.execution.parallel.enabled";
    private static final String MODE_DEFAULT = "junit.jupiter.execution.parallel.mode.default";

    private ParallelExecution() {
    }

    /** {@code testPlan} may be null. */
    static boolean isEnabled(TestPlan testPlan) {
        String value = ConfigurationParameters.get(testPlan, ENABLED);
        return value != null && "true".equalsIgnoreCase(value.trim());
    }

    /** {@code same_thread} or {@code concurrent} as configured, lower case; null when unset. */
    static String modeDefault(TestPlan testPlan) {
        String value = ConfigurationParameters.get(testPlan, MODE_DEFAULT);
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}
