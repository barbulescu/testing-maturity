package ch.barbulescu.testability.probe.junit;

import java.lang.reflect.Method;

/** Finds the method a {@code MethodSource} names. */
final class TestMethods {

    private TestMethods() {
    }

    /**
     * {@code parameterTypes} as a {@code MethodSource} gives them: comma-separated names. An
     * overloaded method is told apart by them. Returns null when no method matches.
     */
    static Method find(Class<?> testClass, String methodName, String parameterTypes) {
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
