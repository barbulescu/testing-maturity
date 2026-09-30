package ch.barbulescu.testability.examples.demo;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * {@code disabledWithoutDocker} keeps this example from needing a Docker
 * daemon inside the harness's own container (no Docker-in-Docker) - the
 * point here is proving {@code testcontainers} shows up in the probe's
 * {@code onClasspath} report, not exercising a real container.
 */
@Testcontainers(disabledWithoutDocker = true)
class TestcontainersPresenceTest {

    @Container
    static GenericContainer<?> container = new GenericContainer<>("alpine:3.20").withCommand("sleep", "5");

    @Test
    void containerFieldIsDeclared() {
        assertNotNull(container);
    }
}
