package ch.barbulescu.testability.probe.junit;

import org.junit.jupiter.api.Test;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestIdentifier;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProbeTestExecutionListenerTest {

    private static final UniqueId CLASS_ID = UniqueId.forEngine("junit-jupiter").append("class", "demo.SomeTest");

    @Test
    void skippedTestCountsAsItself() {
        TestIdentifier test = identifier("method", "one()", TestDescriptor.Type.TEST, MethodSource.from("demo.SomeTest", "one"));

        assertEquals(Collections.singletonList(test), ProbeTestExecutionListener.skippedTests(test, Collections.emptySet()));
    }

    @Test
    void skippedClassCountsEveryTestBelowIt() {
        TestIdentifier skippedClass = classIdentifier();
        TestIdentifier one = identifier("method", "one()", TestDescriptor.Type.TEST, MethodSource.from("demo.SomeTest", "one"));
        TestIdentifier nestedClass = identifier("nested-class", "Inner", TestDescriptor.Type.CONTAINER, ClassSource.from("demo.SomeTest$Inner"));
        TestIdentifier nestedTest = identifier("method", "two()", TestDescriptor.Type.TEST, MethodSource.from("demo.SomeTest$Inner", "two"));

        List<TestIdentifier> skipped = ProbeTestExecutionListener.skippedTests(skippedClass, Arrays.asList(one, nestedClass, nestedTest));

        assertEquals(Arrays.asList(one, nestedTest), skipped);
    }

    @Test
    void skippedTestTemplateCountsAsOneTest() {
        TestIdentifier template = identifier("test-template", "parameterized(int)", TestDescriptor.Type.CONTAINER,
                MethodSource.from("demo.SomeTest", "parameterized", "int"));

        assertEquals(Collections.singletonList(template), ProbeTestExecutionListener.skippedTests(template, Collections.emptySet()));
    }

    @Test
    void skippedClassWithoutTestsCountsNothing() {
        assertEquals(Collections.emptyList(), ProbeTestExecutionListener.skippedTests(classIdentifier(), Collections.emptySet()));
    }

    private static TestIdentifier classIdentifier() {
        return TestIdentifier.from(new Descriptor(CLASS_ID, "SomeTest", TestDescriptor.Type.CONTAINER, ClassSource.from("demo.SomeTest")));
    }

    private static TestIdentifier identifier(String segmentType, String value, TestDescriptor.Type type, TestSource source) {
        return TestIdentifier.from(new Descriptor(CLASS_ID.append(segmentType, value), value, type, source));
    }

    private static final class Descriptor extends AbstractTestDescriptor {

        private final Type type;

        Descriptor(UniqueId uniqueId, String displayName, Type type, TestSource source) {
            super(uniqueId, displayName, source);
            this.type = type;
        }

        @Override
        public Type getType() {
            return type;
        }
    }
}
