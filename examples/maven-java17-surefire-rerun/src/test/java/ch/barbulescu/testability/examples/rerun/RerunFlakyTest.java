package ch.barbulescu.testability.examples.rerun;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Fails the first time it runs in this JVM; Surefire's rerun then passes. */
class RerunFlakyTest {

    private static final AtomicInteger RUNS = new AtomicInteger();

    @Test
    void passesOnRerun() {
        assertTrue(RUNS.getAndIncrement() > 0, "first run fails");
    }
}
