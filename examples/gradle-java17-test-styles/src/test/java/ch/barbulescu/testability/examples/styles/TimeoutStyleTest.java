package ch.barbulescu.testability.examples.styles;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** A time limit on the class, and a tighter one on one of its two tests. */
@Timeout(10)
class TimeoutStyleTest {

    @Test
    @Timeout(1)
    void tight() {
        assertTrue(true);
    }

    @Test
    void loose() {
        assertTrue(true);
    }
}
