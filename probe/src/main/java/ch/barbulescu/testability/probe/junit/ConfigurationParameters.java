package ch.barbulescu.testability.probe.junit;

import org.junit.platform.launcher.TestPlan;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * The launcher's configuration parameters: those the build tool passes, system properties and
 * {@code junit-platform.properties}. {@code TestPlan.getConfigurationParameters()} exists since
 * JUnit Platform 1.8, so it's called reflectively; on older platforms every parameter reads as unset.
 */
final class ConfigurationParameters {

    private ConfigurationParameters() {
    }

    /** The value of {@code key}, or null when it's unset or can't be read. {@code testPlan} may be null. */
    static String get(TestPlan testPlan, String key) {
        if (testPlan == null) {
            return null;
        }
        try {
            // Looked up on the public types: the runtime classes may not be public.
            Method getConfigurationParameters = TestPlan.class.getMethod("getConfigurationParameters");
            Object parameters = getConfigurationParameters.invoke(testPlan);
            Object value = getConfigurationParameters.getReturnType().getMethod("get", String.class)
                    .invoke(parameters, key);
            return value instanceof Optional ? ((Optional<?>) value).map(String::valueOf).orElse(null) : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
