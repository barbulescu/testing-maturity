package ch.barbulescu.testability.probe.junit;

import ch.barbulescu.testability.probe.core.ClassFileCalls;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Whether a test checks that its mocks had no other calls than those it verified:
 * {@code verifyNoMoreInteractions}, {@code verifyNoInteractions} and their BDD, {@code InOrder}
 * and mockito-kotlin forms. Mockito runs these without telling any listener, so the test's
 * bytecode is read instead.
 * <p>
 * A test counts when its method, or a {@code @BeforeEach}/{@code @AfterEach} (JUnit 4:
 * {@code @Before}/{@code @After}) method that runs for it, calls one of them. Calls are
 * followed into the methods of the test class, its base classes and, for a {@code @Nested}
 * class, its enclosing classes, and into the lambdas javac compiles out of those methods.
 * A call made from another class, such as a shared helper, isn't seen.
 */
final class NoMoreInteractionsDetector {

    private static final Set<String> CALL_NAMES = new HashSet<>(Arrays.asList(
            "verifyNoMoreInteractions", "verifyNoInteractions", "verifyZeroInteractions",
            "shouldHaveNoMoreInteractions", "shouldHaveNoInteractions", "shouldHaveZeroInteractions"));
    // Mockito, BDDMockito, InOrder, BDDMockito.Then, and mockito-kotlin before and after it moved.
    private static final String[] CALL_OWNER_PREFIXES = {"org/mockito/", "com/nhaarman/mockitokotlin2/"};

    private static final Set<String> LIFECYCLE_ANNOTATIONS = new HashSet<>(Arrays.asList(
            "org.junit.jupiter.api.BeforeEach", "org.junit.jupiter.api.AfterEach",
            "org.junit.Before", "org.junit.After"));

    private final Map<Class<?>, Map<String, List<ClassFileCalls.Call>>> callsByClass = new ConcurrentHashMap<>();
    private final Map<String, Boolean> resultByTest = new ConcurrentHashMap<>();

    boolean verifiesNoMoreInteractions(Class<?> testClass, Method testMethod) {
        String key = testClass.getName() + "#" + testMethod.getName() + descriptorOf(testMethod);
        Boolean result = resultByTest.get(key);
        if (result == null) {
            result = scan(testClass, testMethod);
            resultByTest.put(key, result);
        }
        return result;
    }

    private boolean scan(Class<?> testClass, Method testMethod) {
        List<Class<?>> receivers = receiversOf(testClass);
        Set<String> visited = new HashSet<>();
        if (reaches(testClass, testMethod.getName() + descriptorOf(testMethod), receivers, visited)) {
            return true;
        }
        for (Class<?> receiver : receivers) {
            for (Class<?> current = receiver; isScanned(current); current = current.getSuperclass()) {
                for (Method method : current.getDeclaredMethods()) {
                    if (isLifecycleMethod(method)
                            && reaches(receiver, method.getName() + descriptorOf(method), receivers, visited)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** The test class, then the classes enclosing it as far as an inner ({@code @Nested}) class reaches. */
    private static List<Class<?>> receiversOf(Class<?> testClass) {
        List<Class<?>> receivers = new ArrayList<>();
        for (Class<?> current = testClass; current != null; current = current.getEnclosingClass()) {
            receivers.add(current);
            if (Modifier.isStatic(current.getModifiers())) {
                break;
            }
        }
        return receivers;
    }

    /** Resolved like a virtual call on {@code receiver}: from it up through its base classes. */
    private boolean reaches(Class<?> receiver, String methodKey, List<Class<?>> receivers, Set<String> visited) {
        for (Class<?> current = receiver; isScanned(current); current = current.getSuperclass()) {
            Map<String, List<ClassFileCalls.Call>> calls = callsOf(current);
            List<ClassFileCalls.Call> methodCalls = calls.get(methodKey);
            if (methodCalls == null) {
                continue;
            }
            if (!visited.add(current.getName() + "#" + methodKey)) {
                return false;
            }
            for (ClassFileCalls.Call call : methodCalls) {
                if (isNoMoreInteractionsCall(call)) {
                    return true;
                }
                Class<?> callReceiver = receiverFor(call.owner, receivers);
                if (callReceiver != null && reaches(callReceiver, call.name + call.descriptor, receivers, visited)) {
                    return true;
                }
            }
            return reachesFromLambdas(current, calls, methodKey, receivers, visited);
        }
        return false;
    }

    /** javac names a lambda after the method it's in: {@code lambda$name$0}. */
    private boolean reachesFromLambdas(Class<?> declaringClass, Map<String, List<ClassFileCalls.Call>> calls,
            String methodKey, List<Class<?>> receivers, Set<String> visited) {
        String prefix = "lambda$" + methodKey.substring(0, methodKey.indexOf('(')) + "$";
        for (String candidate : calls.keySet()) {
            if (candidate.startsWith(prefix) && reaches(declaringClass, candidate, receivers, visited)) {
                return true;
            }
        }
        return false;
    }

    /** The receiver whose hierarchy declares {@code owner}, or null for a class outside the test's. */
    private static Class<?> receiverFor(String owner, List<Class<?>> receivers) {
        String className = owner.replace('/', '.');
        for (Class<?> receiver : receivers) {
            for (Class<?> current = receiver; isScanned(current); current = current.getSuperclass()) {
                if (current.getName().equals(className)) {
                    return receiver;
                }
            }
        }
        return null;
    }

    static boolean isNoMoreInteractionsCall(ClassFileCalls.Call call) {
        if (!CALL_NAMES.contains(call.name)) {
            return false;
        }
        for (String prefix : CALL_OWNER_PREFIXES) {
            if (call.owner.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private Map<String, List<ClassFileCalls.Call>> callsOf(Class<?> type) {
        Map<String, List<ClassFileCalls.Call>> calls = callsByClass.get(type);
        if (calls == null) {
            calls = readCalls(type);
            callsByClass.put(type, calls);
        }
        return calls;
    }

    private static Map<String, List<ClassFileCalls.Call>> readCalls(Class<?> type) {
        ClassLoader loader = type.getClassLoader();
        String resource = type.getName().replace('.', '/') + ".class";
        try (InputStream in = loader == null ? null : loader.getResourceAsStream(resource)) {
            return in == null ? Collections.<String, List<ClassFileCalls.Call>>emptyMap() : ClassFileCalls.read(in);
        } catch (IOException e) {
            return Collections.emptyMap();
        }
    }

    /** The JDK's classes and those of other test frameworks never call Mockito for the test. */
    private static boolean isScanned(Class<?> type) {
        return type != null && type != Object.class && type.getClassLoader() != null && !type.isInterface();
    }

    private static boolean isLifecycleMethod(Method method) {
        for (Annotation annotation : method.getAnnotations()) {
            if (LIFECYCLE_ANNOTATIONS.contains(annotation.annotationType().getName())) {
                return true;
            }
        }
        return false;
    }

    static String descriptorOf(Method method) {
        StringBuilder descriptor = new StringBuilder("(");
        for (Class<?> parameter : method.getParameterTypes()) {
            descriptor.append(descriptorOf(parameter));
        }
        return descriptor.append(')').append(descriptorOf(method.getReturnType())).toString();
    }

    private static String descriptorOf(Class<?> type) {
        if (type.isArray()) {
            return type.getName().replace('.', '/');
        }
        if (!type.isPrimitive()) {
            return "L" + type.getName().replace('.', '/') + ";";
        }
        if (type == void.class) {
            return "V";
        }
        if (type == boolean.class) {
            return "Z";
        }
        if (type == long.class) {
            return "J";
        }
        return String.valueOf(Character.toUpperCase(type.getName().charAt(0)));
    }
}
