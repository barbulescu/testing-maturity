package ch.barbulescu.testability.probe.junit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlakyTestDetectorTest {

    private static final String CLASS = "[engine:junit-jupiter]/[class:demo.SomeTest]";
    private static final String TEST = CLASS + "/[method:one()]";
    private static final String TEMPLATE = CLASS + "/[test-template:retried()]";

    private final FlakyTestDetector detector = new FlakyTestDetector();

    @Test
    void rerunThatPassesAfterAFailureIsFlaky() {
        assertFalse(detector.testFinished(TEST, CLASS, false));
        assertTrue(detector.testFinished(TEST, CLASS, true));
    }

    @Test
    void rerunThatKeepsFailingIsNotFlaky() {
        assertFalse(detector.testFinished(TEST, CLASS, false));
        assertFalse(detector.testFinished(TEST, CLASS, false));
    }

    @Test
    void passingTwiceIsNotFlaky() {
        assertFalse(detector.testFinished(TEST, CLASS, true));
        assertFalse(detector.testFinished(TEST, CLASS, true));
    }

    @Test
    void retriedTemplateWithAFailedAndAPassedAttemptIsFlaky() {
        detector.templateStarted(TEMPLATE);
        assertFalse(detector.testFinished(TEMPLATE + "/[test-template-invocation:#1]", TEMPLATE, false));
        assertFalse(detector.testFinished(TEMPLATE + "/[test-template-invocation:#2]", TEMPLATE, true));

        assertTrue(detector.containerFinished(TEMPLATE));
    }

    @Test
    void templateWhoseAttemptsAllPassOrAllFailIsNotFlaky() {
        detector.templateStarted(TEMPLATE);
        detector.testFinished(TEMPLATE + "/[test-template-invocation:#1]", TEMPLATE, true);
        detector.testFinished(TEMPLATE + "/[test-template-invocation:#2]", TEMPLATE, true);
        assertFalse(detector.containerFinished(TEMPLATE));

        detector.templateStarted(TEMPLATE);
        detector.testFinished(TEMPLATE + "/[test-template-invocation:#1]", TEMPLATE, false);
        assertFalse(detector.containerFinished(TEMPLATE));
    }

    @Test
    void mixedInvocationsOfAnUnwatchedTemplateAreNotFlaky() {
        // A @ParameterizedTest isn't watched: its invocations run different inputs.
        String parameterized = CLASS + "/[test-template:parameterized(int)]";
        detector.testFinished(parameterized + "/[test-template-invocation:#1]", parameterized, false);
        detector.testFinished(parameterized + "/[test-template-invocation:#2]", parameterized, true);

        assertFalse(detector.containerFinished(parameterized));
    }
}
