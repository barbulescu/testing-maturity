package ch.barbulescu.testability.probe.spring;

import ch.barbulescu.testability.probe.core.ProbeRecorder;
import ch.barbulescu.testability.probe.core.Safe;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

/**
 * Registered via {@code META-INF/spring.factories}; Spring appends this to
 * the default listener set automatically, UNLESS a test class declares its
 * own {@code @TestExecutionListeners} (which replaces the defaults rather
 * than merging with them by default) - that's a documented blind spot, not
 * a bug.
 */
public final class ProbeSpringTestExecutionListener extends AbstractTestExecutionListener {

    @Override
    public void beforeTestClass(TestContext testContext) {
        Safe.run(() -> {
            ProbeRecorder recorder = ProbeRecorder.getInstance();
            // A classic JUnit 4 SpringRunner test never touches the JUnit Platform Launcher,
            // so the JUnit adapter never gets a chance to register the shutdown-hook flush.
            recorder.ensureShutdownHookRegistered();

            SpringTestClassFacts facts = SpringTestClassInspector.inspect(testContext.getTestClass());
            recorder.recordSpringTestClass(facts.junit4, facts.bootTest, facts.sliceTest, facts.mockBeanFields);
        });
    }
}
