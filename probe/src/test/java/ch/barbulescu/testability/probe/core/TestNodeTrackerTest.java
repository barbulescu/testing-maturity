package ch.barbulescu.testability.probe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestNodeTrackerTest {

    private static final String ENGINE = "[engine:junit-jupiter]";
    private static final String CLASS_A = ENGINE + "/[class:A]";
    private static final String CLASS_B = ENGINE + "/[class:B]";

    private final TestNodeTracker tracker = new TestNodeTracker();

    @Test
    void mockCreatedDuringATestMarksOnlyThatTest() {
        tracker.nodeStarted(ENGINE, null, null, false);
        tracker.nodeStarted(CLASS_A, ENGINE, "A", false);
        tracker.nodeStarted(CLASS_A + "/[method:one()]", CLASS_A, "A", true);
        tracker.mockCreatedOnCurrentThread();
        tracker.nodeFinished(CLASS_A + "/[method:one()]");
        tracker.nodeStarted(CLASS_A + "/[method:two()]", CLASS_A, "A", true);
        tracker.nodeFinished(CLASS_A + "/[method:two()]");

        assertTrue(tracker.usesMocks(CLASS_A + "/[method:one()]"));
        assertFalse(tracker.usesMocks(CLASS_A + "/[method:two()]"));
    }

    @Test
    void mockCreatedAtClassLevelMarksEveryTestOfTheClass() {
        tracker.nodeStarted(ENGINE, null, null, false);
        tracker.nodeStarted(CLASS_A, ENGINE, "A", false);
        // e.g. @BeforeAll
        tracker.mockCreatedOnCurrentThread();
        tracker.nodeStarted(CLASS_A + "/[method:one()]", CLASS_A, "A", true);
        tracker.nodeFinished(CLASS_A + "/[method:one()]");
        tracker.nodeFinished(CLASS_A);
        tracker.nodeStarted(CLASS_B, ENGINE, "B", false);
        tracker.nodeStarted(CLASS_B + "/[method:one()]", CLASS_B, "B", true);

        assertTrue(tracker.usesMocks(CLASS_A + "/[method:one()]"));
        assertFalse(tracker.usesMocks(CLASS_B + "/[method:one()]"));
    }

    @Test
    void mockKindsCombineAcrossTheTestAndItsAncestors() {
        tracker.nodeStarted(ENGINE, null, null, false);
        tracker.nodeStarted(CLASS_A, ENGINE, "A", false);
        tracker.mockCreatedOnCurrentThread(TestNodeTracker.STATIC_MOCK, true);
        tracker.nodeStarted(CLASS_A + "/[method:one()]", CLASS_A, "A", true);
        tracker.mockCreatedOnCurrentThread();
        tracker.nodeFinished(CLASS_A + "/[method:one()]");
        tracker.nodeFinished(CLASS_A);
        tracker.nodeStarted(CLASS_B, ENGINE, "B", false);
        tracker.nodeStarted(CLASS_B + "/[method:one()]", CLASS_B, "B", true);
        tracker.mockCreatedOnCurrentThread();

        assertEquals(TestNodeTracker.STATIC_MOCK, tracker.mockKinds(CLASS_A + "/[method:one()]"));
        assertEquals(0, tracker.mockKinds(CLASS_B + "/[method:one()]"));
    }

    @Test
    void mockCountAddsTheTestAndItsAncestorsButSkipsUncountedMocks() {
        tracker.nodeStarted(ENGINE, null, null, false);
        tracker.nodeStarted(CLASS_A, ENGINE, "A", false);
        tracker.mockCreatedOnCurrentThread();
        tracker.nodeStarted(CLASS_A + "/[method:one()]", CLASS_A, "A", true);
        tracker.mockCreatedOnCurrentThread();
        tracker.mockCreatedOnCurrentThread(TestNodeTracker.DEEP_STUBS, true);
        tracker.mockCreatedOnCurrentThread(TestNodeTracker.DEEP_STUBS, false);

        assertEquals(3, tracker.mockCount(CLASS_A + "/[method:one()]"));
    }

    @Test
    void springClassCountsAtLeastItsDeclaredMockBeans() {
        tracker.springTestClass("A", 2, 1);
        tracker.nodeStarted(ENGINE, null, null, false);
        tracker.nodeStarted(CLASS_A, ENGINE, "A", false);
        tracker.nodeStarted(CLASS_A + "/[method:one()]", CLASS_A, "A", true);

        assertEquals(3, tracker.mockCount(CLASS_A + "/[method:one()]"));
    }

    @Test
    void mockCreatedOnTheEngineRootIsIgnored() {
        tracker.nodeStarted(ENGINE, null, null, false);
        tracker.mockCreatedOnCurrentThread();
        tracker.nodeStarted(CLASS_A, ENGINE, "A", false);
        tracker.nodeStarted(CLASS_A + "/[method:one()]", CLASS_A, "A", true);

        assertFalse(tracker.usesMocks(CLASS_A + "/[method:one()]"));
    }

    @Test
    void mockCreatedOnAnotherThreadIsNotAttributed() throws InterruptedException {
        tracker.nodeStarted(ENGINE, null, null, false);
        tracker.nodeStarted(CLASS_A, ENGINE, "A", false);
        tracker.nodeStarted(CLASS_A + "/[method:one()]", CLASS_A, "A", true);

        Thread other = new Thread(tracker::mockCreatedOnCurrentThread);
        other.start();
        other.join();

        assertFalse(tracker.usesMocks(CLASS_A + "/[method:one()]"));
    }

    @Test
    void springClassIsRecognizedByNameIncludingDynamicChildren() {
        tracker.springTestClass("A", 0, 0);
        tracker.nodeStarted(ENGINE, null, null, false);
        tracker.nodeStarted(CLASS_A, ENGINE, "A", false);
        tracker.nodeStarted(CLASS_A + "/[test-factory:f()]", CLASS_A, "A", false);
        tracker.nodeStarted(CLASS_A + "/[test-factory:f()]/[dynamic-test:#1]", CLASS_A + "/[test-factory:f()]", null, true);

        assertTrue(tracker.isSpringTest(CLASS_A + "/[test-factory:f()]/[dynamic-test:#1]"));
        assertFalse(tracker.usesMocks(CLASS_A + "/[test-factory:f()]/[dynamic-test:#1]"));
    }

    @Test
    void springClassDeclaringMockBeansUsesMocksEvenWithoutCreatingAny() {
        tracker.springTestClass("A", 2, 0);
        tracker.nodeStarted(ENGINE, null, null, false);
        tracker.nodeStarted(CLASS_A, ENGINE, "A", false);
        tracker.nodeStarted(CLASS_A + "/[method:one()]", CLASS_A, "A", true);

        assertTrue(tracker.isSpringTest(CLASS_A + "/[method:one()]"));
        assertTrue(tracker.usesMocks(CLASS_A + "/[method:one()]"));
    }

    @Test
    void springClassDeclaringSpyBeansUsesSpiesEvenWithoutCreatingAny() {
        tracker.springTestClass("A", 0, 1);
        tracker.nodeStarted(ENGINE, null, null, false);
        tracker.nodeStarted(CLASS_A, ENGINE, "A", false);
        tracker.nodeStarted(CLASS_A + "/[method:one()]", CLASS_A, "A", true);

        assertEquals(TestNodeTracker.SPY, tracker.mockKinds(CLASS_A + "/[method:one()]"));
    }

    @Test
    void plainTestIsNotSpring() {
        tracker.springTestClass("A", 2, 0);
        tracker.nodeStarted(ENGINE, null, null, false);
        tracker.nodeStarted(CLASS_B, ENGINE, "B", false);
        tracker.nodeStarted(CLASS_B + "/[method:one()]", CLASS_B, "B", true);

        assertFalse(tracker.isSpringTest(CLASS_B + "/[method:one()]"));
        assertFalse(tracker.usesMocks(CLASS_B + "/[method:one()]"));
    }

    @Test
    void mockCreatedWhileCreatingATestInstanceCountsForThatTestOnly() {
        tracker.nodeStarted(ENGINE, null, null, false);
        tracker.nodeStarted(CLASS_A, ENGINE, "A", false);
        for (String test : new String[] {"/[method:one()]", "/[method:two()]"}) {
            // A field initializer: runs before the test's start event, once per test
            tracker.mockCreatedOnCurrentThread(0, true, jupiterStack("instantiateTestClass", "prepare"));
            tracker.nodeStarted(CLASS_A + test, CLASS_A, "A", true);
            tracker.nodeFinished(CLASS_A + test);
        }

        assertEquals(1, tracker.mockCount(CLASS_A + "/[method:one()]"));
        assertEquals(1, tracker.mockCount(CLASS_A + "/[method:two()]"));
    }

    @Test
    void perClassInstanceCountsForTheWholeClass() {
        tracker.nodeStarted(ENGINE, null, null, false);
        tracker.nodeStarted(CLASS_A, ENGINE, "A", false);
        // @TestInstance(PER_CLASS): instantiated by the class's own prepare, not a test method's
        tracker.mockCreatedOnCurrentThread(0, true, new StackTraceElement[] {
                frame("org.mockito.Mockito", "mock"),
                frame("org.junit.jupiter.engine.descriptor.ClassBasedTestDescriptor", "instantiateTestClass"),
                frame("org.junit.jupiter.engine.descriptor.ClassBasedTestDescriptor", "prepare")});
        tracker.nodeStarted(CLASS_A + "/[method:one()]", CLASS_A, "A", true);
        tracker.nodeFinished(CLASS_A + "/[method:one()]");
        tracker.nodeStarted(CLASS_A + "/[method:two()]", CLASS_A, "A", true);

        assertEquals(1, tracker.mockCount(CLASS_A + "/[method:one()]"));
        assertEquals(1, tracker.mockCount(CLASS_A + "/[method:two()]"));
    }

    @Test
    void junit4TestInstanceCountsForThatTestOnly() {
        tracker.nodeStarted(ENGINE, null, null, false);
        tracker.nodeStarted(CLASS_A, ENGINE, "A", false);
        tracker.mockCreatedOnCurrentThread(0, true, new StackTraceElement[] {
                frame("org.mockito.Mockito", "mock"),
                frame("A", "<init>"),
                frame("org.junit.runners.BlockJUnit4ClassRunner", "createTest"),
                frame("org.junit.runners.BlockJUnit4ClassRunner", "methodBlock")});
        tracker.nodeStarted(CLASS_A + "/[method:one()]", CLASS_A, "A", true);
        tracker.nodeFinished(CLASS_A + "/[method:one()]");
        tracker.nodeStarted(CLASS_A + "/[method:two()]", CLASS_A, "A", true);

        assertEquals(1, tracker.mockCount(CLASS_A + "/[method:one()]"));
        assertEquals(0, tracker.mockCount(CLASS_A + "/[method:two()]"));
    }

    @Test
    void springContextLoadedWhileCreatingATestInstanceCountsForTheClass() {
        tracker.nodeStarted(ENGINE, null, null, false);
        tracker.nodeStarted(CLASS_A, ENGINE, "A", false);
        // Constructor injection: Spring loads the context, creating its mock beans
        tracker.mockCreatedOnCurrentThread(0, true, new StackTraceElement[] {
                frame("org.mockito.Mockito", "mock"),
                frame("org.springframework.test.context.cache.DefaultCacheAwareContextLoaderDelegate", "loadContext"),
                frame("org.junit.jupiter.engine.descriptor.ClassBasedTestDescriptor", "instantiateTestClass"),
                frame("org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor", "prepare")});
        tracker.nodeStarted(CLASS_A + "/[method:one()]", CLASS_A, "A", true);
        tracker.nodeFinished(CLASS_A + "/[method:one()]");
        tracker.nodeStarted(CLASS_A + "/[method:two()]", CLASS_A, "A", true);

        assertEquals(1, tracker.mockCount(CLASS_A + "/[method:two()]"));
    }

    @Test
    void mocksOfASkippedTestAreDiscarded() {
        tracker.nodeStarted(ENGINE, null, null, false);
        tracker.nodeStarted(CLASS_A, ENGINE, "A", false);
        tracker.mockCreatedOnCurrentThread(0, true, jupiterStack("instantiateTestClass", "prepare"));
        tracker.discardPendingMocks();
        tracker.nodeStarted(CLASS_A + "/[method:two()]", CLASS_A, "A", true);

        assertFalse(tracker.usesMocks(CLASS_A + "/[method:two()]"));
    }

    private static StackTraceElement[] jupiterStack(String instantiateMethod, String prepareMethod) {
        return new StackTraceElement[] {
                frame("org.mockito.Mockito", "mock"),
                frame("A", "<init>"),
                frame("org.junit.jupiter.engine.descriptor.ClassBasedTestDescriptor", instantiateMethod),
                frame("org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor", prepareMethod)};
    }

    private static StackTraceElement frame(String className, String methodName) {
        return new StackTraceElement(className, methodName, null, -1);
    }
}
