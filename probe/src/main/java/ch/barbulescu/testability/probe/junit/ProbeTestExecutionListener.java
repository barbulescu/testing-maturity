package ch.barbulescu.testability.probe.junit;

import ch.barbulescu.testability.probe.core.ProbeRecorder;
import ch.barbulescu.testability.probe.core.Safe;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.UniqueId;
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
        });
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
