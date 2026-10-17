package ch.barbulescu.testability.examples.springsmells;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringJUnitConfig(CounterConfig.class)
class CleanTest {

    @Autowired
    Counter counter;

    @Test
    void increments() {
        assertTrue(counter.increment() > 0);
    }
}
