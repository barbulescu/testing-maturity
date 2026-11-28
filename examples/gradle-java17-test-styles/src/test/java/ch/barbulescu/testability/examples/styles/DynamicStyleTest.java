package ch.barbulescu.testability.examples.styles;

import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicContainer.dynamicContainer;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/** Three dynamic tests, one of them in a dynamic container. */
class DynamicStyleTest {

    @TestFactory
    Stream<DynamicNode> tests() {
        return Stream.of(
                dynamicTest("first", () -> assertTrue(true)),
                dynamicTest("second", () -> assertTrue(true)),
                dynamicContainer("group", Stream.of(dynamicTest("third", () -> assertTrue(true)))));
    }
}
