package ch.barbulescu.testability.probe.junit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestShapeTest {

    @Test
    void plainTestHasNoShape() {
        TestShape shape = TestShape.of("[engine:junit-jupiter]/[class:demo.SomeTest]/[method:one()]");

        assertFalse(shape.templateInvocation);
        assertFalse(shape.dynamic);
        assertFalse(shape.nested);
    }

    @Test
    void templateInvocationIsSeen() {
        TestShape shape = TestShape.of(
                "[engine:junit-jupiter]/[class:demo.SomeTest]/[test-template:sum(int)]/[test-template-invocation:#1]");

        assertTrue(shape.templateInvocation);
        assertFalse(shape.dynamic);
    }

    @Test
    void dynamicTestIsSeenAlsoInADynamicContainer() {
        TestShape shape = TestShape.of("[engine:junit-jupiter]/[class:demo.SomeTest]/[test-factory:tests()]"
                + "/[dynamic-container:#1]/[dynamic-test:#2]");

        assertTrue(shape.dynamic);
        assertFalse(shape.templateInvocation);
    }

    @Test
    void testInANestedClassIsNestedAndMayAlsoBeParameterized() {
        TestShape shape = TestShape.of("[engine:junit-jupiter]/[class:demo.SomeTest]/[nested-class:Inner]"
                + "/[test-template:sum(int)]/[test-template-invocation:#3]");

        assertTrue(shape.nested);
        assertTrue(shape.templateInvocation);
    }

    @Test
    void otherEnginesSegmentsHaveNoShape() {
        TestShape shape = TestShape.of("[engine:jqwik]/[class:demo.Properties]/[property:sumIsCommutative(int, int)]");

        assertFalse(shape.templateInvocation);
        assertFalse(shape.dynamic);
        assertFalse(shape.nested);
    }
}
