package ch.barbulescu.testability.examples.contextfails;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ContextFailsTest {

    @Test
    void contextLoads() {
        // Intentionally left empty - the context fails before this method ever runs.
    }
}
