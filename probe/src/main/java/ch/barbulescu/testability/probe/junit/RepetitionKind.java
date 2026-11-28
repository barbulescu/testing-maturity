package ch.barbulescu.testability.probe.junit;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

/**
 * Whether a test method runs more than once by design: it retries itself on failure, or
 * it's repeated a fixed number of times. Matched by annotation name, so neither JUnit
 * Pioneer nor rerunner-jupiter is needed at compile time.
 */
enum RepetitionKind {

    NONE, RETRYING, REPEATED;

    private static final List<String> RETRYING_ANNOTATIONS = Arrays.asList(
            "org.junitpioneer.jupiter.RetryingTest",
            "io.github.artsok.RepeatedIfExceptionsTest");
    private static final String REPEATED_TEST = "org.junit.jupiter.api.RepeatedTest";

    /** A method that isn't found is {@link #NONE}; see {@link TestMethods#find}. */
    static RepetitionKind of(Class<?> testClass, String methodName, String parameterTypes) {
        Method method = TestMethods.find(testClass, methodName, parameterTypes);
        if (method == null) {
            return NONE;
        }
        for (Annotation annotation : method.getAnnotations()) {
            String name = annotation.annotationType().getName();
            if (RETRYING_ANNOTATIONS.contains(name)) {
                return RETRYING;
            }
            if (REPEATED_TEST.equals(name)) {
                return REPEATED;
            }
        }
        return NONE;
    }
}
