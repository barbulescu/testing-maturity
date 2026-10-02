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

/**
 * Registered via {@code META-INF/services}; self-activates on any JUnit
 * Platform run without configuration in the consuming project.
 */
public final class ProbeTestExecutionListener implements TestExecutionListener {

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
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
            recorder.recordTestMockUsage(tracker.isSpringTest(id), tracker.usesMocks(id));
        });
        Safe.run(() -> TestNodeTracker.getInstance().nodeFinished(testIdentifier.getUniqueId()));
    }

    @Override
    public void executionSkipped(TestIdentifier testIdentifier, String reason) {
        Safe.run(() -> {
            if (testIdentifier.isTest()) {
                ProbeRecorder.getInstance().recordTestSkipped(engineIdOf(testIdentifier));
            }
        });
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
