package ch.barbulescu.testability.examples.rerun;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Repeated and plain tests that always pass: counted, but not flaky. */
class RepeatedStableTest {

    @RepeatedTest(2)
    void repeated() {
        assertEquals(2, 1 + 1);
    }

    @Test
    void plain() {
        assertEquals(2, 1 + 1);
    }
}
