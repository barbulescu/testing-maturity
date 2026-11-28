package ch.barbulescu.testability.examples.failuretypes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Tests that caught a real problem: each fails with an AssertionError or a subclass of it. */
class AssertionFailuresTest {

    @Test
    void jupiterAssertion() {
        assertEquals(3, 1 + 1);
    }

    @Test
    void plainJavaAssert() {
        // Gradle runs tests with assertions enabled.
        int sum = 1 + 1;
        assert sum == 3 : "sum is " + sum;
    }
}
