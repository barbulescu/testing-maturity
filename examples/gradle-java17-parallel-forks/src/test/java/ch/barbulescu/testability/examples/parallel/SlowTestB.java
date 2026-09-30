package ch.barbulescu.testability.examples.parallel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SlowTestB {

    @Test
    void runsSlowly() throws InterruptedException {
        Thread.sleep(400);
        assertTrue(true);
    }
}
