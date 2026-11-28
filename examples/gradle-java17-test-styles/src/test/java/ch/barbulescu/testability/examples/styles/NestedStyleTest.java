package ch.barbulescu.testability.examples.styles;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** One plain test, and three in a nested class tagged integration: one also slow, two parameterized. */
class NestedStyleTest {

    @Test
    void outer() {
        assertTrue(true);
    }

    @Nested
    @Tag("integration")
    class Inner {

        @Test
        @Tag("slow")
        void inner() {
            assertTrue(true);
        }

        @ParameterizedTest
        @ValueSource(strings = {"a", "b"})
        void innerParameterized(String value) {
            assertTrue(!value.isEmpty());
        }
    }
}
