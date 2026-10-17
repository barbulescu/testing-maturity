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

    /** The enum constant for mocks a test creates; the internal class for the nested mocks of a chain. */
    private static boolean isDeepStubs(Answer<?> answer) {
        return answer == Answers.RETURNS_DEEP_STUBS
                || answer != null && answer.getClass().getName().startsWith(DEEP_STUBS_ANSWER_PREFIX);
    }
}
