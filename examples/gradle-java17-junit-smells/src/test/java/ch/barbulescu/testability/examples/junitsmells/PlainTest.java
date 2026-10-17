package ch.barbulescu.testability.examples.junitsmells;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlainTest {

    @Test
    void independent() {
        assertEquals(4, 2 + 2);
    }
}
