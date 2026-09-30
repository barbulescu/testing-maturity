package ch.barbulescu.testability.examples.oldsurefire;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CalculatorTest {

    @Test
    void addsTwoNumbers() {
        assertEquals(5, 2 + 3);
    }
}
