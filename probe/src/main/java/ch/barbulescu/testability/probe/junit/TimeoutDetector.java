package ch.barbulescu.testability.probe.junit;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Whether a test class or method puts a time limit on its tests. Matched by name, so
 * neither JUnit 4 nor Jupiter is needed at compile time.
 */
final class TimeoutDetector {

    private static final String JUPITER_TIMEOUT = "org.junit.jupiter.api.Timeout";
    private static final String JUNIT4_TEST = "org.junit.Test";
    private static final String JUNIT4_TIMEOUT_RULE = "org.junit.rules.Timeout";
    private static final String JUNIT4_RULE = "org.junit.Rule";
    private static final String JUNIT4_CLASS_RULE = "org.junit.ClassRule";

    private TimeoutDetector() {
    }

    /**
     * Jupiter's {@code @Timeout} on the class (it's inherited), or a JUnit 4
     * {@code Timeout} rule in a {@code @Rule} or {@code @ClassRule} field, also in a base class.
     */
    static boolean hasTimeout(Class<?> testClass) {
        if (hasAnnotation(testClass.getAnnotations(), JUPITER_TIMEOUT)) {
            return true;
        }
        for (Class<?> current = testClass; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                Annotation[] annotations = field.getAnnotations();
                if ((hasAnnotation(annotations, JUNIT4_RULE) || hasAnnotation(annotations, JUNIT4_CLASS_RULE))
                        && isTimeoutRule(field.getType())) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Jupiter's {@code @Timeout} on the method, or JUnit 4's {@code @Test(timeout = ...)}. */
    static boolean hasTimeout(Method method) {
        for (Annotation annotation : method.getAnnotations()) {
            String name = annotation.annotationType().getName();
            if (JUPITER_TIMEOUT.equals(name)) {
                return true;
            }
            if (JUNIT4_TEST.equals(name)) {
                Object timeout = valueOf(annotation, "timeout");
                if (timeout instanceof Long && (Long) timeout > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isTimeoutRule(Class<?> type) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            if (JUNIT4_TIMEOUT_RULE.equals(current.getName())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAnnotation(Annotation[] annotations, String name) {
        for (Annotation annotation : annotations) {
            if (name.equals(annotation.annotationType().getName())) {
                return true;
            }
        }
        return false;
    }

    private static Object valueOf(Annotation annotation, String attribute) {
        try {
            return annotation.annotationType().getMethod(attribute).invoke(annotation);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
