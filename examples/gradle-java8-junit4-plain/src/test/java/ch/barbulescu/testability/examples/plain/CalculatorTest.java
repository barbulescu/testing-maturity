package ch.barbulescu.testability.examples.plain;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CalculatorTest {

    @Test
    public void addsTwoNumbers() {
        assertEquals(5, new Calculator().add(2, 3));
    }
}
