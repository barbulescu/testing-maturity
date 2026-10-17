package ch.barbulescu.testability.probe.spring;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure reflection over a test class - no Spring {@code TestContext}
 * needed, which keeps this independently testable with plain fixture
 * classes. Everything is matched by annotation name (never imported types)
 * so this works whether the class is JUnit 4 or 5, and whatever Boot
 * version happens to be on the classpath.
 */
final class SpringTestClassInspector {

    private static final String RUN_WITH = "org.junit.runner.RunWith";
    private static final String SPRING_BOOT_TEST = "org.springframework.boot.test.context.SpringBootTest";
    private static final String SLICE_TEST_PACKAGE_PREFIX = "org.springframework.boot.test.autoconfigure.";
    private static final String DIRTIES_CONTEXT = "org.springframework.test.annotation.DirtiesContext";

    private SpringTestClassInspector() {
    }

    static SpringTestClassFacts inspect(Class<?> testClass) {
        boolean junit4 = false;
        boolean bootTest = false;
        boolean sliceTest = false;
        boolean dirtiesContext = false;

        // getAnnotations() includes @Inherited ones, so @DirtiesContext on a base class counts.
        for (Annotation annotation : testClass.getAnnotations()) {
            String name = annotation.annotationType().getName();
            if (DIRTIES_CONTEXT.equals(name)) {
                dirtiesContext = true;
            } else if (RUN_WITH.equals(name)) {
                junit4 = true;
            } else if (SPRING_BOOT_TEST.equals(name)) {
                bootTest = true;
            } else if (name.startsWith(SLICE_TEST_PACKAGE_PREFIX)) {
                sliceTest = true;
            }
        }

        return new SpringTestClassFacts(junit4, bootTest, sliceTest,
                countFieldsAnnotated(testClass, "MockBean", "MockitoBean"),
                countFieldsAnnotated(testClass, "SpyBean", "MockitoSpyBean"),
                dirtiesContext, countMethodsAnnotated(testClass, DIRTIES_CONTEXT));
    }

    private static int countMethodsAnnotated(Class<?> testClass, String annotationName) {
        int count = 0;
        for (Class<?> current = testClass; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                for (Annotation annotation : method.getAnnotations()) {
                    if (annotationName.equals(annotation.annotationType().getName())) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static int countFieldsAnnotated(Class<?> testClass, String... annotationSimpleNames) {
        int count = 0;
        for (Field field : allFieldsIncludingInherited(testClass)) {
            for (Annotation annotation : field.getAnnotations()) {
                String simpleName = annotation.annotationType().getSimpleName();
                for (String wanted : annotationSimpleNames) {
                    if (wanted.equals(simpleName)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static List<Field> allFieldsIncludingInherited(Class<?> type) {
        // @MockBean/@MockitoBean often sit on a shared base test class.
        List<Field> fields = new ArrayList<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            fields.addAll(java.util.Arrays.asList(current.getDeclaredFields()));
        }
        return fields;
    }
}
