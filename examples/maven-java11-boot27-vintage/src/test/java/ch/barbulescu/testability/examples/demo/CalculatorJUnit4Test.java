package ch.barbulescu.testability.examples.demo;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CalculatorJUnit4Test {

    @Test
    public void addsTwoNumbers() {
        assertEquals(5, new Calculator().add(2, 3));
    }
}
