package ch.barbulescu.testability.probe.mockito;

import ch.barbulescu.testability.probe.core.TestNodeTracker;
import org.mockito.Answers;
import org.mockito.mock.MockCreationSettings;
import org.mockito.stubbing.Answer;

/** Reads from a mock's creation settings which of the tracker's smell flags apply to it. */
final class MockKinds {

    private static final String DEEP_STUBS_ANSWER_PREFIX = "org.mockito.internal.stubbing.defaultanswers.ReturnsDeepStubs";

    private MockKinds() {
    }

    static int of(MockCreationSettings<?> settings) {
        int kinds = 0;
        if (isDeepStubs(settings.getDefaultAnswer())) {
            kinds |= TestNodeTracker.DEEP_STUBS;
        }
        if (isLenient(settings)) {
            kinds |= TestNodeTracker.LENIENT;
        }
        if (settings.getSpiedInstance() != null || settings.getDefaultAnswer() == Answers.CALLS_REAL_METHODS) {
            kinds |= TestNodeTracker.SPY;
        }
        return kinds;
    }

    /**
     * Mock-level leniency only. {@code getStrictness()} exists since Mockito 4.6 and
     * {@code isLenient()} from 2.20 until it was deprecated; neither exists in 2.1, which
     * this module compiles against, so both are called reflectively.
     */
    private static boolean isLenient(MockCreationSettings<?> settings) {
        Object strictness = invoke(settings, "getStrictness");
        if (strictness != null) {
            return "LENIENT".equals(strictness.toString());
        }
        return Boolean.TRUE.equals(invoke(settings, "isLenient"));
    }

    private static Object invoke(Object target, String methodName) {
        try {
            return target.getClass().getMethod(methodName).invoke(target);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    /** A mock the test created itself has the enum constant; the nested mocks of a chain have the internal class. */
    static boolean isDeepStubsChild(MockCreationSettings<?> settings) {
        Answer<?> answer = settings.getDefaultAnswer();
        return answer != null && answer != Answers.RETURNS_DEEP_STUBS
                && answer.getClass().getName().startsWith(DEEP_STUBS_ANSWER_PREFIX);
    }

    private static boolean isDeepStubs(Answer<?> answer) {
        return answer == Answers.RETURNS_DEEP_STUBS
                || answer != null && answer.getClass().getName().startsWith(DEEP_STUBS_ANSWER_PREFIX);
    }
}
