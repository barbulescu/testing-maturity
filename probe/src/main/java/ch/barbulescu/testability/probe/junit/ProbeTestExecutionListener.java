package ch.barbulescu.testability.probe.junit;

import ch.barbulescu.testability.probe.core.MockKObserver;
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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registered via {@code META-INF/services}; self-activates on any JUnit
 * Platform run without configuration in the consuming project.
 */
public final class ProbeTestExecutionListener implements TestExecutionListener {

    // One JVM can run several test plans, each with a new instance of this listener: Surefire
    // reruns failed tests in a new plan. What has to be seen across plans is static, so per
    // classloader like the recorder: a class or template is counted once, and a rerun that
    // passes is matched with the run that failed.
    private static final Set<String> INSPECTED_CLASSES = ConcurrentHashMap.newKeySet();
    private static final Set<String> INSPECTED_TEMPLATES = ConcurrentHashMap.newKeySet();
    private static final FlakyTestDetector FLAKY_TESTS = new FlakyTestDetector();

    private volatile TestPlan testPlan;
    private final Map<String, Long> testStartNanos = new ConcurrentHashMap<>();
    private final Map<String, Boolean> perClassByClassName = new ConcurrentHashMap<>();

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
            if (testIdentifier.isTest()) {
                testStartNanos.put(testIdentifier.getUniqueId(), System.nanoTime());
            }
        });
        Safe.run(() -> {
            TestNodeTracker.getInstance().nodeStarted(
                    testIdentifier.getUniqueId(), testIdentifier.getParentId().orElse(null), classNameOf(testIdentifier),
                    testIdentifier.isTest());
            MockitoHook.ensureInstalledOnCurrentThread();
        });
        Safe.run(() -> inspectTestClass(testIdentifier));
        Safe.run(() -> inspectTestTemplate(testIdentifier));
        Safe.run(() -> {
            MockKObserver.watchCurrentThread();
            // MockK mocks created since the last event on this thread were created while JUnit
            // prepared this test: its instance (field initializers, @MockK fields). A PER_CLASS
            // instance is created after its class started, so its mocks are found here too, on
            // the first test, but belong to the class. Mocks from @BeforeAll land on the first
            // test as well; nothing tells them apart from the instance's.
            String creditTo = testIdentifier.isTest() && isPerClass(testIdentifier)
                    ? testIdentifier.getParentId().orElse(null)
                    : testIdentifier.getUniqueId();
            creditNewMockKMocks(creditTo);
        });
    }

    private static void creditNewMockKMocks(String nodeId) {
        MockKObserver.NewMocks mocks = MockKObserver.newMocksOnCurrentThread();
        if (nodeId != null) {
            TestNodeTracker.getInstance().mocksCreatedFor(nodeId, mocks.count, mocks.kinds);
        }
    }

    private boolean isPerClass(TestIdentifier test) {
        String className = classNameOf(test);
        if (className == null) {
            return false;
        }
        Boolean perClass = perClassByClassName.get(className);
        if (perClass == null) {
            perClass = TestInstanceLifecycle.isPerClass(loadClass(className), testPlan);
            perClassByClassName.put(className, perClass);
        }
        return perClass;
    }

    /** Once per test class, when its container starts. */
    private void inspectTestClass(TestIdentifier testIdentifier) {
        TestSource source = testIdentifier.getSource().orElse(null);
        if (!testIdentifier.isContainer() || !(source instanceof ClassSource)) {
            return;
        }
        String className = ((ClassSource) source).getClassName();
        if (!INSPECTED_CLASSES.add(className)) {
            return;
        }
        Class<?> testClass = loadClass(className);
        if (testClass != null && FixedOrderDetector.hasFixedOrder(testClass)) {
            ProbeRecorder.getInstance().recordFixedOrderClass();
        }
    }

    /**
     * A container with a method source is a test template or factory, such as a
     * {@code @RepeatedTest}. Its method is counted once, also when Surefire reruns it.
     */
    private void inspectTestTemplate(TestIdentifier testIdentifier) {
        TestSource source = testIdentifier.getSource().orElse(null);
        if (!testIdentifier.isContainer() || !(source instanceof MethodSource)) {
            return;
        }
        MethodSource method = (MethodSource) source;
        Class<?> testClass = loadClass(method.getClassName());
        RepetitionKind kind = testClass == null
                ? RepetitionKind.NONE
                : RepetitionKind.of(testClass, method.getMethodName(), method.getMethodParameterTypes());
        if (kind == RepetitionKind.NONE) {
            return;
        }
        FLAKY_TESTS.templateStarted(testIdentifier.getUniqueId());
        if (INSPECTED_TEMPLATES.add(testIdentifier.getUniqueId())) {
            if (kind == RepetitionKind.RETRYING) {
                ProbeRecorder.getInstance().recordRetryingTestMethod();
            } else {
                ProbeRecorder.getInstance().recordRepeatedTestMethod();
            }
        }
    }

    private static Class<?> loadClass(String className) {
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        for (ClassLoader loader : new ClassLoader[] {contextLoader, ProbeTestExecutionListener.class.getClassLoader()}) {
            if (loader == null) {
                continue;
            }
            try {
                return Class.forName(className, false, loader);
            } catch (ClassNotFoundException | LinkageError e) {
                // try the next loader
            }
        }
        return null;
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult result) {
        // Created while the node ran: the test method, @BeforeEach and @AfterEach, or @AfterAll.
        Safe.run(() -> creditNewMockKMocks(testIdentifier.getUniqueId()));
        Safe.run(() -> {
            String id = testIdentifier.getUniqueId();
            boolean flaky = testIdentifier.isTest()
                    ? FLAKY_TESTS.testFinished(id, testIdentifier.getParentId().orElse(null),
                            result.getStatus() == TestExecutionResult.Status.SUCCESSFUL)
                    : FLAKY_TESTS.containerFinished(id);
            if (flaky) {
                ProbeRecorder.getInstance().recordFlakyTest();
            }
        });
        Safe.run(() -> {
            if (!testIdentifier.isTest()) {
                return;
            }
            String engineId = engineIdOf(testIdentifier);
            ProbeRecorder recorder = ProbeRecorder.getInstance();
            if (result.getStatus() == TestExecutionResult.Status.SUCCESSFUL) {
                recorder.recordTestSucceeded(engineId);
            } else if (result.getStatus() == TestExecutionResult.Status.ABORTED) {
                recorder.recordTestAborted(engineId);
            } else {
                recorder.recordTestFailed(engineId, result.getThrowable().orElse(null));
            }
            Long startNanos = testStartNanos.remove(testIdentifier.getUniqueId());
            if (startNanos != null) {
                recorder.recordTestDuration((System.nanoTime() - startNanos) / 1_000_000);
            }
            TestNodeTracker tracker = TestNodeTracker.getInstance();
            String id = testIdentifier.getUniqueId();
            recorder.recordTestMockUsage(tracker.isSpringTest(id), tracker.usesMocks(id), tracker.mockKinds(id),
                    tracker.ownMockCount(id), tracker.classLevelMockCount(id), tracker.className(id));
        });
        Safe.run(() -> TestNodeTracker.getInstance().nodeFinished(testIdentifier.getUniqueId()));
    }

    /**
     * A skipped container (e.g. a {@code @Disabled} class) gets no events for its descendants,
     * so its tests are counted here from the test plan instead - as Gradle and Surefire do.
     */
    @Override
    public void executionSkipped(TestIdentifier testIdentifier, String reason) {
        Safe.run(() -> TestNodeTracker.getInstance().discardPendingMocks());
        Safe.run(MockKObserver::newMocksOnCurrentThread);
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
