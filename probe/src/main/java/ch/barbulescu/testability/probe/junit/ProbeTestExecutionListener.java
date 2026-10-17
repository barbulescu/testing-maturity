package ch.barbulescu.testability.probe.junit;

import ch.barbulescu.testability.probe.core.ProbeRecorder;
import ch.barbulescu.testability.probe.core.MockitoHook;
import ch.barbulescu.testability.probe.core.Safe;
import ch.barbulescu.testability.probe.core.TestNodeTracker;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Registered via {@code META-INF/services}; self-activates on any JUnit
 * Platform run without configuration in the consuming project.
 */
public final class ProbeTestExecutionListener implements TestExecutionListener {

    private volatile TestPlan testPlan;

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        this.testPlan = testPlan;
        Safe.run(() -> {
            ProbeRecorder recorder = ProbeRecorder.getInstance();
            recorder.ensureShutdownHookRegistered();
            recorder.recordTestPlanStarted();
        });
    }

    /** Called on the thread that runs the node, which is what makes mock attribution work. */
    @Override
    public void executionStarted(TestIdentifier testIdentifier) {
        Safe.run(() -> {
            TestNodeTracker.getInstance().nodeStarted(
                    testIdentifier.getUniqueId(), testIdentifier.getParentId().orElse(null), classNameOf(testIdentifier));
            MockitoHook.ensureInstalledOnCurrentThread();
        });
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult result) {
        Safe.run(() -> {
            if (!testIdentifier.isTest()) {
                return;
            }
            String engineId = engineIdOf(testIdentifier);
            ProbeRecorder recorder = ProbeRecorder.getInstance();
            if (result.getStatus() == TestExecutionResult.Status.SUCCESSFUL) {
                recorder.recordTestSucceeded(engineId);
            } else {
                recorder.recordTestFailed(engineId);
            }
            TestNodeTracker tracker = TestNodeTracker.getInstance();
            String id = testIdentifier.getUniqueId();
            recorder.recordTestMockUsage(tracker.isSpringTest(id), tracker.usesMocks(id), tracker.mockKinds(id));
        });
        Safe.run(() -> TestNodeTracker.getInstance().nodeFinished(testIdentifier.getUniqueId()));
    }

    /**
     * A skipped container (e.g. a {@code @Disabled} class) gets no events for its descendants,
     * so its tests are counted here from the test plan instead - as Gradle and Surefire do.
     */
    @Override
    public void executionSkipped(TestIdentifier testIdentifier, String reason) {
        Safe.run(() -> {
            TestPlan plan = testPlan;
            Set<TestIdentifier> descendants = plan != null && testIdentifier.isContainer()
                    ? plan.getDescendants(testIdentifier)
                    : Collections.<TestIdentifier>emptySet();
            ProbeRecorder recorder = ProbeRecorder.getInstance();
            for (TestIdentifier skippedTest : skippedTests(testIdentifier, descendants)) {
                recorder.recordTestSkipped(engineIdOf(skippedTest));
            }
        });
    }

    /**
     * The tests a skip stands for. A skipped test template ({@code @ParameterizedTest},
     * {@code @RepeatedTest}, {@code @TestFactory}) has no tests in the plan - its invocations
     * only exist at run time - so it counts as one test, matching the build tools' reports.
     */
    static List<TestIdentifier> skippedTests(TestIdentifier skipped, Collection<TestIdentifier> descendants) {
        if (skipped.isTest()) {
            return Collections.singletonList(skipped);
        }
        List<TestIdentifier> tests = new ArrayList<>();
        for (TestIdentifier descendant : descendants) {
            if (descendant.isTest()) {
                tests.add(descendant);
            }
        }
        if (tests.isEmpty() && skipped.getSource().orElse(null) instanceof MethodSource) {
            tests.add(skipped);
        }
        return tests;
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        Safe.run(() -> ProbeRecorder.getInstance().flush());
    }

    private static String classNameOf(TestIdentifier testIdentifier) {
        TestSource source = testIdentifier.getSource().orElse(null);
        if (source instanceof MethodSource) {
            return ((MethodSource) source).getClassName();
        }
        if (source instanceof ClassSource) {
            return ((ClassSource) source).getClassName();
        }
        return null;
    }

    private static String engineIdOf(TestIdentifier testIdentifier) {
        // getUniqueIdObject() isn't available on the oldest launcher version this module
        // compiles against, so the unique ID string is parsed instead.
        UniqueId uniqueId = UniqueId.parse(testIdentifier.getUniqueId());
        for (UniqueId.Segment segment : uniqueId.getSegments()) {
            if ("engine".equals(segment.getType())) {
                return segment.getValue();
            }
        }
        return "unknown";
    }
}
