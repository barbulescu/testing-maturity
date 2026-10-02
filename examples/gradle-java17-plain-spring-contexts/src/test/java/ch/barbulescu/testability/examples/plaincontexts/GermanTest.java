package ch.barbulescu.testability.examples.plaincontexts;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A different configuration, so a second context. */
@SpringJUnitConfig(GermanConfig.class)
class GermanTest {

    @Autowired
    private Greeter greeter;

    @Test
    void greetsInGerman() {
        assertEquals("Hallo, Ada", greeter.greet("Ada"));
    }
}
