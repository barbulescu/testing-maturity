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

    /**
     * {@code parameterTypes} as a {@code MethodSource} gives them: comma-separated names. An
     * overloaded method is told apart by them; a method that isn't found is {@link #NONE}.
     */
    static RepetitionKind of(Class<?> testClass, String methodName, String parameterTypes) {
        Method method = findMethod(testClass, methodName, parameterTypes);
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

    private static Method findMethod(Class<?> testClass, String methodName, String parameterTypes) {
        String wanted = normalize(parameterTypes);
        for (Class<?> current = testClass; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(methodName) && parametersMatch(method, wanted)) {
                    return method;
                }
            }
        }
        return null;
    }

    /** JUnit versions differ in how they name array types, so both spellings match. */
    private static boolean parametersMatch(Method method, String wanted) {
        StringBuilder names = new StringBuilder();
        StringBuilder typeNames = new StringBuilder();
        for (Class<?> type : method.getParameterTypes()) {
            if (names.length() > 0) {
                names.append(',');
                typeNames.append(',');
            }
            names.append(type.getName());
            typeNames.append(type.getTypeName());
        }
        return wanted.equals(names.toString()) || wanted.equals(typeNames.toString());
    }

    private static String normalize(String parameterTypes) {
        return parameterTypes == null ? "" : parameterTypes.replace(" ", "");
    }
}
