package ch.barbulescu.testability.probe.core;

import java.lang.reflect.Field;

/**
 * Whether Testcontainers actually talked to Docker in this JVM, not just whether it's on
 * the classpath. Every container start, and Ryuk, goes through the Docker client that
 * {@code DockerClientFactory.instance().client()} creates and keeps in its {@code client}
 * field, so a non-null client means some test used Testcontainers. Looking for Docker and
 * finding none (for example {@code @Testcontainers(disabledWithoutDocker = true)} without
 * a daemon) leaves it null.
 * <p>
 * Reading a static field initializes its class, and {@code DockerClientFactory}'s static
 * initializer sets up SLF4J. So the probe only reads it once the class is initialized
 * already, which it can tell from the {@code testcontainers} thread group that same
 * initializer creates and keeps in a static field.
 * <p>
 * Read by name ({@code instance} and {@code client} are the same from 1.19 to 2.0), so the
 * probe needs no Testcontainers dependency. Another layout reads as unused.
 */
public final class TestcontainersUsage {

    private static final String DOCKER_CLIENT_FACTORY = "org.testcontainers.DockerClientFactory";
    private static final String THREAD_GROUP_NAME = "testcontainers";

    private TestcontainersUsage() {
    }

    public static boolean dockerClientCreated() {
        return dockerClientCreated(ProbeRecorder.class.getClassLoader());
    }

    static boolean dockerClientCreated(ClassLoader loader) {
        if (!threadGroupExists(THREAD_GROUP_NAME)) {
            return false; // DockerClientFactory never initialized, so never used
        }
        try {
            Class<?> factoryClass = Class.forName(DOCKER_CLIENT_FACTORY, false, loader);
            Object factory = readField(factoryClass, null, "instance");
            return factory != null && readField(factoryClass, factory, "client") != null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return false;
        }
    }

    private static Object readField(Class<?> type, Object target, String name) throws ReflectiveOperationException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    /**
     * Searches every live thread group. Thread groups since Java 19 hold their subgroups
     * weakly, but Testcontainers keeps its own in a static field, so it stays enumerable.
     */
    static boolean threadGroupExists(String name) {
        ThreadGroup root = Thread.currentThread().getThreadGroup();
        while (root.getParent() != null) {
            root = root.getParent();
        }
        ThreadGroup[] groups = new ThreadGroup[root.activeGroupCount() + 16];
        int count = root.enumerate(groups, true);
        for (int i = 0; i < count; i++) {
            if (name.equals(groups[i].getName())) {
                return true;
            }
        }
        return false;
    }
}
