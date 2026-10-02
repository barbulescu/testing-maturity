package ch.barbulescu.testability.probe.spring;

import ch.barbulescu.testability.probe.core.ProbeRecorder;
import ch.barbulescu.testability.probe.core.Safe;
import ch.barbulescu.testability.probe.core.TestNodeTracker;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

import java.lang.reflect.Method;

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
            // Declared mock/spy beans count even when the context came from the cache, i.e. when
            // no mock was created while this class ran.
            TestNodeTracker.getInstance().springTestClass(
                    testContext.getTestClass().getName(), facts.mockBeanFields + facts.spyBeanFields > 0);
        });
    }

    /**
     * This listener has the default lowest precedence, so it runs after
     * {@code DependencyInjectionTestExecutionListener} has already loaded
     * (or fetched from the cache) the context - looking at it here never
     * triggers a load of its own. If loading failed, that listener threw
     * and this method is never reached.
     */
    @Override
    public void prepareTestInstance(TestContext testContext) {
        Safe.run(() -> {
            ApplicationContext context = loadedContextOrNull(testContext);
            if (context != null) {
                ProbeRecorder recorder = ProbeRecorder.getInstance();
                ContextTracker.shared().observe(context, recorder::recordSpringContextLoaded);
            }
        });
    }

    private static ApplicationContext loadedContextOrNull(TestContext testContext) {
        // hasApplicationContext() only exists since Spring 5.2; on older versions, fall back to
        // getApplicationContext(), which by this point is a cache hit.
        Method hasApplicationContext;
        try {
            hasApplicationContext = testContext.getClass().getMethod("hasApplicationContext");
        } catch (NoSuchMethodException e) {
            return testContext.getApplicationContext();
        }
        try {
            if (!Boolean.TRUE.equals(hasApplicationContext.invoke(testContext))) {
                return null;
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return testContext.getApplicationContext();
    }
}
