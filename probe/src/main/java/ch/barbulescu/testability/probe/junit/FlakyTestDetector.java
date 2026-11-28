package ch.barbulescu.testability.probe.junit;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Finds tests that both failed and passed in this JVM, which shows up in two ways:
 * <ul>
 *     <li>The same unique ID finishes as not successful and later as successful. That's a
 *     rerun in a new test plan, as Surefire's {@code rerunFailingTestsCount} does.</li>
 *     <li>A retrying or repeated test template has invocations that passed and invocations
 *     that didn't. Each invocation has its own unique ID. JUnit Pioneer's
 *     {@code @RetryingTest} and rerunner-jupiter's {@code @RepeatedIfExceptionsTest}
 *     report a failed attempt as aborted, not failed, so any outcome but success counts as
 *     a failure here.</li>
 * </ul>
 * A test is counted when it passes after a failure, so a test that never passes isn't
 * flaky, and one that keeps flipping counts once per pass after a failure.
 */
final class FlakyTestDetector {

    private static final int PASSED = 1;
    private static final int NOT_PASSED = 2;

    private final Set<String> notPassedTestIds = ConcurrentHashMap.newKeySet();
    private final Map<String, AtomicInteger> templateOutcomes = new ConcurrentHashMap<>();

    /** A retrying or repeated template started: its invocations' outcomes are collected. */
    void templateStarted(String templateId) {
        templateOutcomes.put(templateId, new AtomicInteger());
    }

    /** True if this test passed after failing under the same unique ID. */
    boolean testFinished(String testId, String parentId, boolean passed) {
        AtomicInteger outcomes = parentId != null ? templateOutcomes.get(parentId) : null;
        if (outcomes != null) {
            int outcome = passed ? PASSED : NOT_PASSED;
            outcomes.accumulateAndGet(outcome, (current, update) -> current | update);
        }
        if (!passed) {
            notPassedTestIds.add(testId);
            return false;
        }
        return notPassedTestIds.remove(testId);
    }

    /** True if this was a retrying or repeated template whose invocations both passed and failed. */
    boolean containerFinished(String containerId) {
        AtomicInteger outcomes = templateOutcomes.remove(containerId);
        return outcomes != null && outcomes.get() == (PASSED | NOT_PASSED);
    }
}
