package ch.barbulescu.testability.examples.springsmells;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Throws the context away after the class, so the next class that needs it starts a new one. */
@SpringJUnitConfig(CounterConfig.class)
@DirtiesContext
class DirtiesClassTest {

    @Autowired
    Counter counter;

    @Test
    void increments() {
        assertTrue(counter.increment() > 0);
    }
}
