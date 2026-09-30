package ch.barbulescu.testability.examples.demo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CalculatorJupiterTest {

    @Test
    void addsTwoNumbers() {
        assertEquals(5, new Calculator().add(2, 3));
    }

    @Test
    void addsNegativeNumbers() {
        assertEquals(-1, new Calculator().add(2, -3));
    }
}
