package ch.barbulescu.testability.examples.disabled;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/** JUnit reports only the class as skipped; the probe counts its three tests from the test plan. */
@Disabled
class DisabledClassTest {

    @Test
    void one() {
    }

    @Test
    void two() {
    }

    @Test
    void three() {
    }
}
