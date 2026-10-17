package ch.barbulescu.testability.examples.disabled;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DisabledMethodTest {

    @Test
    void runs() {
        assertEquals(2, 1 + 1);
    }

    @Disabled("reported by JUnit as one skipped test")
    @Test
    void disabledMethod() {
    }
}
