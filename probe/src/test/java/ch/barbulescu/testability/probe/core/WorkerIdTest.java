package ch.barbulescu.testability.probe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkerIdTest {

    @Test
    void differsBetweenCalls() {
        String first = WorkerId.generate();
        String second = WorkerId.generate();

        assertNotEquals(first, second);
        assertTrue(first.contains("-"));
    }
}
