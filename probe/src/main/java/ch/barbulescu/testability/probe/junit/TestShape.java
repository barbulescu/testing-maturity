package ch.barbulescu.testability.probe.junit;

import org.junit.platform.engine.UniqueId;

/**
 * Where a test sits, read from the segment types of its unique ID. Jupiter names them;
 * other engines use their own types, so their tests have none of these shapes.
 */
final class TestShape {

    /** An invocation of a test template: {@code @ParameterizedTest}, {@code @RepeatedTest}, and the like. */
    final boolean templateInvocation;
    /** Created at run time by a {@code @TestFactory}, also inside a dynamic container. */
    final boolean dynamic;
    /** In a {@code @Nested} class. */
    final boolean nested;

    private TestShape(boolean templateInvocation, boolean dynamic, boolean nested) {
        this.templateInvocation = templateInvocation;
        this.dynamic = dynamic;
        this.nested = nested;
    }

    static TestShape of(String uniqueId) {
        boolean templateInvocation = false;
        boolean dynamic = false;
        boolean nested = false;
        for (UniqueId.Segment segment : UniqueId.parse(uniqueId).getSegments()) {
            String type = segment.getType();
            if ("test-template".equals(type)) {
                templateInvocation = true;
            } else if ("dynamic-test".equals(type)) {
                dynamic = true;
            } else if ("nested-class".equals(type)) {
                nested = true;
            }
        }
        return new TestShape(templateInvocation, dynamic, nested);
    }
}
