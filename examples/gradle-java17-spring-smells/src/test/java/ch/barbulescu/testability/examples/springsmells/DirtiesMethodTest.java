package ch.barbulescu.testability.examples.springsmells;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringJUnitConfig(CounterConfig.class)
class DirtiesMethodTest {

    @Autowired
    Counter counter;

    @Test
    @DirtiesContext
    void dirtiesAfterThisTest() {
        assertTrue(counter.increment() > 0);
    }

    @Test
    void leavesTheContext() {
        assertTrue(counter.increment() > 0);
    }
}
