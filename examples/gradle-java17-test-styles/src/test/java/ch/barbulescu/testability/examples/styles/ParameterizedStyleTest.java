package ch.barbulescu.testability.examples.styles;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Three parameterized invocations, and two repetitions that aren't parameterized. All tagged fast. */
@Tag("fast")
class ParameterizedStyleTest {

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void isPositive(int number) {
        assertTrue(number > 0);
    }

    @RepeatedTest(2)
    void repeated() {
        assertTrue(true);
    }
}
