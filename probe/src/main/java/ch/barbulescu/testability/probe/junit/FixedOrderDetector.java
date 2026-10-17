package ch.barbulescu.testability.probe.junit;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

/**
 * Whether a test class pins the order its tests run in, which usually means they depend
 * on each other. Matched by annotation name, so neither JUnit 4 nor Jupiter is needed at
 * compile time.
 */
final class FixedOrderDetector {

    private static final String TEST_METHOD_ORDER = "org.junit.jupiter.api.TestMethodOrder";
    private static final String RANDOM_ORDERER = "org.junit.jupiter.api.MethodOrderer$Random";
    private static final String FIX_METHOD_ORDER = "org.junit.FixMethodOrder";

    private FixedOrderDetector() {
    }

    /**
     * Any Jupiter orderer except {@code Random} fixes the order. For JUnit 4, only
     * {@code NAME_ASCENDING} does: {@code DEFAULT} is the normal behaviour and {@code JVM}
     * leaves the order to the JVM.
     */
    static boolean hasFixedOrder(Class<?> testClass) {
        for (Annotation annotation : testClass.getAnnotations()) {
            String name = annotation.annotationType().getName();
            if (TEST_METHOD_ORDER.equals(name)) {
                Object orderer = valueOf(annotation);
                return !(orderer instanceof Class) || !RANDOM_ORDERER.equals(((Class<?>) orderer).getName());
            }
            if (FIX_METHOD_ORDER.equals(name)) {
                Object sorter = valueOf(annotation);
                return sorter != null && "NAME_ASCENDING".equals(sorter.toString());
            }
        }
        return false;
    }

    private static Object valueOf(Annotation annotation) {
        try {
            Method value = annotation.annotationType().getMethod("value");
            return value.invoke(annotation);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
