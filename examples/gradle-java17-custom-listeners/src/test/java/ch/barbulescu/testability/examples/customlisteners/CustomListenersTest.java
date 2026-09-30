package ch.barbulescu.testability.examples.customlisteners;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;

/**
 * {@code @TestExecutionListeners} without an explicit {@code mergeMode}
 * defaults to {@code REPLACE_DEFAULTS} - the probe's spring.factories
 * listener is part of that default set, so it never attaches here. This
 * is a documented blind spot, not a bug.
 */
@SpringBootTest
@TestExecutionListeners(listeners = NoOpTestExecutionListener.class)
class CustomListenersTest {

    @Test
    void contextLoads() {
    }
}
