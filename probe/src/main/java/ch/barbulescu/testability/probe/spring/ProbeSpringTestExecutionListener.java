package ch.barbulescu.testability.probe.spring;

import ch.barbulescu.testability.probe.core.ProbeRecorder;
import ch.barbulescu.testability.probe.core.Safe;
import ch.barbulescu.testability.probe.core.TestNodeTracker;
import org.springframework.context.ApplicationContext;
import org.springframework.core.Ordered;
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
            recorder.recordSpringTestClass(facts.junit4, facts.bootTest, facts.sliceTest, facts.mockBeanFields,
                    facts.dirtiesContextClass, facts.dirtiesContextMethods);
            recorder.recordSpringTestClassWiring(facts.webEnvironment, facts.testDatabaseReplaced,
                    facts.serviceConnection, facts.dynamicPropertySource, facts.embeddedKafka);
            // Declared mock/spy beans count even when the context came from the cache, i.e. when
            // no mock was created while this class ran.
            TestNodeTracker.getInstance().springTestClass(
                    testContext.getTestClass().getName(), facts.mockBeanFields, facts.spyBeanFields);
        });
    }

    /**
     * Runs first, so this call is the one that loads the context when it
     * isn't cached yet, and its duration is the load time. Without the probe,
     * {@code DependencyInjectionTestExecutionListener} makes the same call a
     * little later in this phase. A cache hit takes next to nothing and
     * finds no new context, so it adds no time.
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public void prepareTestInstance(TestContext testContext) {
        long startNanos = System.nanoTime();
        // Outside Safe: a context that fails to load must fail the test with the same exception
        // as without the probe. Swallowing it would make Spring try again, and since 6.1 that
        // second try reports the failure threshold instead of the cause.
        ApplicationContext context = testContext.getApplicationContext();
        long loadNanos = System.nanoTime() - startNanos;
        Safe.run(() -> {
            ProbeRecorder recorder = ProbeRecorder.getInstance();
            if (ContextTracker.shared().observe(context, recorder::recordSpringContextLoaded)) {
                recorder.recordSpringContextLoadTime(loadNanos);
            }
        });
    }
}
