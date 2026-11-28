package ch.barbulescu.testability.probe.spring;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    // Moved to spring-boot-jdbc-test in Boot 4.
    private static final List<String> AUTO_CONFIGURE_TEST_DATABASE = Arrays.asList(
            "org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase",
            "org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase");
    private static final String SERVICE_CONNECTION = "org.springframework.boot.testcontainers.service.connection.ServiceConnection";
    private static final String DYNAMIC_PROPERTY_SOURCE = "org.springframework.test.context.DynamicPropertySource";
    private static final List<String> EMBEDDED_KAFKA = Collections.singletonList("org.springframework.kafka.test.context.EmbeddedKafka");

    private SpringTestClassInspector() {
    }

    static SpringTestClassFacts inspect(Class<?> testClass) {
        boolean junit4 = false;
        boolean bootTest = false;
        boolean sliceTest = false;
        boolean dirtiesContext = false;
        String webEnvironment = null;

        // getAnnotations() includes @Inherited ones, so @DirtiesContext on a base class counts.
        for (Annotation annotation : testClass.getAnnotations()) {
            String name = annotation.annotationType().getName();
            if (DIRTIES_CONTEXT.equals(name)) {
                dirtiesContext = true;
            } else if (RUN_WITH.equals(name)) {
                junit4 = true;
            } else if (SPRING_BOOT_TEST.equals(name)) {
                bootTest = true;
                webEnvironment = enumAttribute(annotation, "webEnvironment");
            } else if (name.startsWith(SLICE_TEST_PACKAGE_PREFIX)) {
                sliceTest = true;
            }
        }

        return new SpringTestClassFacts(junit4, bootTest, sliceTest,
                countFieldsAnnotated(testClass, "MockBean", "MockitoBean"),
                countFieldsAnnotated(testClass, "SpyBean", "MockitoSpyBean"),
                dirtiesContext, countMethodsAnnotated(testClass, DIRTIES_CONTEXT), webEnvironment,
                testDatabaseReplaced(testClass),
                countFieldsAnnotatedByName(testClass, SERVICE_CONNECTION) > 0,
                countMethodsAnnotated(testClass, DYNAMIC_PROPERTY_SOURCE) > 0,
                findAnnotation(testClass, EMBEDDED_KAFKA) != null);
    }

    /**
     * Whether Boot swaps the configured {@code DataSource} for an embedded one: an
     * {@code @AutoConfigureTestDatabase}, also the one {@code @DataJpaTest} and
     * {@code @JdbcTest} carry, whose {@code replace} isn't {@code NONE}. Its
     * {@code spring.test.database.replace} property isn't read.
     */
    private static boolean testDatabaseReplaced(Class<?> testClass) {
        Annotation annotation = findAnnotation(testClass, AUTO_CONFIGURE_TEST_DATABASE);
        return annotation != null && !"NONE".equals(enumAttribute(annotation, "replace"));
    }

    /**
     * The annotation on the class, or else on the nearest annotation that carries it: one
     * declared on the class wins over one that comes with a slice annotation.
     */
    private static Annotation findAnnotation(Class<?> testClass, List<String> names) {
        Deque<Annotation> queue = new ArrayDeque<>(Arrays.asList(testClass.getAnnotations()));
        Set<Class<?>> visited = new HashSet<>();
        while (!queue.isEmpty()) {
            Annotation annotation = queue.removeFirst();
            Class<? extends Annotation> type = annotation.annotationType();
            if (names.contains(type.getName())) {
                return annotation;
            }
            if (visited.add(type) && !type.getName().startsWith("java.lang.annotation.")) {
                try {
                    queue.addAll(Arrays.asList(type.getAnnotations()));
                } catch (LinkageError | RuntimeException e) {
                    // An annotation whose attributes reference a class that isn't on the
                    // classpath can't be read; what it carries simply isn't found.
                }
            }
        }
        return null;
    }

    /** An enum attribute's constant name, or null when the attribute can't be read. */
    private static String enumAttribute(Annotation annotation, String attribute) {
        try {
            Object value = annotation.annotationType().getMethod(attribute).invoke(annotation);
            return value instanceof Enum ? ((Enum<?>) value).name() : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return null;
        }
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

    private static int countFieldsAnnotatedByName(Class<?> testClass, String annotationName) {
        int count = 0;
        for (Field field : allFieldsIncludingInherited(testClass)) {
            for (Annotation annotation : field.getAnnotations()) {
                if (annotationName.equals(annotation.annotationType().getName())) {
                    count++;
                }
            }
        }
        return count;
    }

    private static List<Field> allFieldsIncludingInherited(Class<?> type) {
        // @MockBean/@MockitoBean often sit on a shared base test class.
        List<Field> fields = new ArrayList<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            fields.addAll(Arrays.asList(current.getDeclaredFields()));
        }
        return fields;
    }
}
