package ch.barbulescu.testability.examples.failuretypes;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** JUnit 4's assertEquals on strings throws ComparisonFailure, a subclass of AssertionError. */
public class VintageAssertionTest {

    @Test
    public void comparesStrings() {
        assertEquals("expected", "actual");
    }
}
