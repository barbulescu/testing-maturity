package ch.barbulescu.testability.examples.junitsmells;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Random order is the opposite of the smell: it flushes out hidden dependencies. */
@TestMethodOrder(MethodOrderer.Random.class)
class RandomOrderTest {

    @Test
    void one() {
        assertEquals(1, Integer.parseInt("1"));
    }

    @Test
    void two() {
        assertEquals(2, Integer.parseInt("2"));
    }
}
