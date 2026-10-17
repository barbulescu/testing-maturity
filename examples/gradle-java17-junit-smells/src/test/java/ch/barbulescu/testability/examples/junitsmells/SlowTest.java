package ch.barbulescu.testability.examples.junitsmells;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SlowTest {

    /** Waits for time to pass instead of for a condition: slow, and flaky on a busy machine. */
    @Test
    void sleepsInsteadOfWaitingForACondition() throws InterruptedException {
        long start = System.nanoTime();
        Thread.sleep(1_100);
        assertTrue(System.nanoTime() - start >= 1_000_000_000L);
    }
}
