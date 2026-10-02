package ch.barbulescu.testability.examples.plaincontexts;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Same configuration as the other English test: Spring's context cache
 * serves both from one context, so the probe must count it once.
 */
@SpringJUnitConfig(EnglishConfig.class)
class EnglishFirstTest {

    @Autowired
    private Greeter greeter;

    @Test
    void greetsInEnglish() {
        assertEquals("Hello, Ada", greeter.greet("Ada"));
    }
}
