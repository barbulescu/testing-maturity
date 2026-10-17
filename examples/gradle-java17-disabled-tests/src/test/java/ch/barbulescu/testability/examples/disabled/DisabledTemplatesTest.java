package ch.barbulescu.testability.examples.disabled;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

class DisabledTemplatesTest {

    /** Invocations only exist at run time, so a disabled template counts as one test. */
    @Disabled
    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void disabledParameterized(int value) {
    }

    @Disabled
    @RepeatedTest(4)
    void disabledRepeated() {
    }

    /** ABORTED: counted under failed, not skipped. */
    @Test
    void failingAssumption() {
        assumeTrue(false, "never holds");
    }

    @Nested
    @Disabled
    class DisabledNested {

        @Test
        void inner() {
        }
    }
}
