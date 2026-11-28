package ch.barbulescu.testability.probe.core;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Uses the real {@code DockerClientFactory} from the test classpath. There is no Docker
 * daemon to connect to, so the test puts a stand-in client into the factory itself. One
 * test, because the steps depend on each other: no other test touches Testcontainers, so
 * at the start its factory isn't initialized yet.
 */
class TestcontainersUsageTest {

    private static final String DOCKER_CLIENT_FACTORY = "org.testcontainers.DockerClientFactory";

    @Test
    void reportsUseOnlyOnceTheFactoryHasCreatedAClient() throws Exception {
        ClassLoader loader = getClass().getClassLoader();

        assertFalse(TestcontainersUsage.dockerClientCreated(loader), "never initialized");
        assertFalse(TestcontainersUsage.threadGroupExists("testcontainers"), "looking must not initialize it");

        Class<?> factoryClass = Class.forName(DOCKER_CLIENT_FACTORY, true, loader);
        Object factory = factoryClass.getMethod("instance").invoke(null);
        assertFalse(TestcontainersUsage.dockerClientCreated(loader), "initialized, but no client");

        Field client = factoryClass.getDeclaredField("client");
        client.setAccessible(true);
        Class<?> dockerClient = client.getType();
        client.set(factory, Proxy.newProxyInstance(loader, new Class<?>[]{dockerClient}, (proxy, method, args) -> null));
        try {
            assertTrue(TestcontainersUsage.dockerClientCreated(loader));
        } finally {
            client.set(factory, null);
        }
    }

    @Test
    void findsAThreadGroupByName() {
        ThreadGroup group = new ThreadGroup("testability-probe-test-group");

        assertTrue(TestcontainersUsage.threadGroupExists(group.getName()));
        assertFalse(TestcontainersUsage.threadGroupExists("no-such-thread-group"));
    }
}
