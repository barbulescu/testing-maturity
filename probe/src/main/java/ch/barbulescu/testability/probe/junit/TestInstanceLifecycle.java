package ch.barbulescu.testability.probe.junit;

import org.junit.platform.launcher.TestPlan;

import java.lang.annotation.Annotation;

/**
 * Whether JUnit Jupiter creates one instance for all tests of a class: {@code @TestInstance(PER_CLASS)}
 * on the class or a superclass, or {@code junit.jupiter.testinstance.lifecycle.default=per_class},
 * which is common in Kotlin projects. Matched by name, as the probe isn't compiled against Jupiter.
 */
final class TestInstanceLifecycle {

    private static final String TEST_INSTANCE = "org.junit.jupiter.api.TestInstance";
    private static final String DEFAULT_LIFECYCLE = "junit.jupiter.testinstance.lifecycle.default";

    private TestInstanceLifecycle() {
    }

    /** {@code testClass} and {@code testPlan} may be null. */
    static boolean isPerClass(Class<?> testClass, TestPlan testPlan) {
        if (testClass == null) {
            return false;
        }
        for (Class<?> current = testClass; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Annotation annotation : current.getDeclaredAnnotations()) {
                if (TEST_INSTANCE.equals(annotation.annotationType().getName())) {
                    return "PER_CLASS".equals(String.valueOf(valueOf(annotation)));
                }
            }
        }
        return "per_class".equalsIgnoreCase(ConfigurationParameters.get(testPlan, DEFAULT_LIFECYCLE));
    }

    private static Object valueOf(Annotation annotation) {
        try {
            return annotation.annotationType().getMethod("value").invoke(annotation);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
