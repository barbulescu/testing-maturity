package ch.barbulescu.testability.probe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestNodeTrackerTest {

    private static final String ENGINE = "[engine:junit-jupiter]";
    private static final String CLASS_A = ENGINE + "/[class:A]";
    private static final String CLASS_B = ENGINE + "/[class:B]";

    private final TestNodeTracker tracker = new TestNodeTracker();

    @Test
    void mockCreatedDuringATestMarksOnlyThatTest() {
        tracker.nodeStarted(ENGINE, null, null);
        tracker.nodeStarted(CLASS_A, ENGINE, "A");
        tracker.nodeStarted(CLASS_A + "/[method:one()]", CLASS_A, "A");
        tracker.mockCreatedOnCurrentThread();
        tracker.nodeFinished(CLASS_A + "/[method:one()]");
        tracker.nodeStarted(CLASS_A + "/[method:two()]", CLASS_A, "A");
        tracker.nodeFinished(CLASS_A + "/[method:two()]");

        assertTrue(tracker.usesMocks(CLASS_A + "/[method:one()]"));
        assertFalse(tracker.usesMocks(CLASS_A + "/[method:two()]"));
    }

    @Test
    void mockCreatedAtClassLevelMarksEveryTestOfTheClass() {
        tracker.nodeStarted(ENGINE, null, null);
        tracker.nodeStarted(CLASS_A, ENGINE, "A");
        // e.g. a field initializer, which runs before the method's start event
        tracker.mockCreatedOnCurrentThread();
        tracker.nodeStarted(CLASS_A + "/[method:one()]", CLASS_A, "A");
        tracker.nodeFinished(CLASS_A + "/[method:one()]");
        tracker.nodeFinished(CLASS_A);
        tracker.nodeStarted(CLASS_B, ENGINE, "B");
        tracker.nodeStarted(CLASS_B + "/[method:one()]", CLASS_B, "B");

        assertTrue(tracker.usesMocks(CLASS_A + "/[method:one()]"));
        assertFalse(tracker.usesMocks(CLASS_B + "/[method:one()]"));
    }

    @Test
    void mockCreatedOnTheEngineRootIsIgnored() {
        tracker.nodeStarted(ENGINE, null, null);
        tracker.mockCreatedOnCurrentThread();
        tracker.nodeStarted(CLASS_A, ENGINE, "A");
        tracker.nodeStarted(CLASS_A + "/[method:one()]", CLASS_A, "A");

        assertFalse(tracker.usesMocks(CLASS_A + "/[method:one()]"));
    }

    @Test
    void mockCreatedOnAnotherThreadIsNotAttributed() throws InterruptedException {
        tracker.nodeStarted(ENGINE, null, null);
        tracker.nodeStarted(CLASS_A, ENGINE, "A");
        tracker.nodeStarted(CLASS_A + "/[method:one()]", CLASS_A, "A");

        Thread other = new Thread(tracker::mockCreatedOnCurrentThread);
        other.start();
        other.join();

        assertFalse(tracker.usesMocks(CLASS_A + "/[method:one()]"));
    }

    @Test
    void springClassIsRecognizedByNameIncludingDynamicChildren() {
        tracker.springTestClass("A", false);
        tracker.nodeStarted(ENGINE, null, null);
        tracker.nodeStarted(CLASS_A, ENGINE, "A");
        tracker.nodeStarted(CLASS_A + "/[test-factory:f()]", CLASS_A, "A");
        tracker.nodeStarted(CLASS_A + "/[test-factory:f()]/[dynamic-test:#1]", CLASS_A + "/[test-factory:f()]", null);

        assertTrue(tracker.isSpringTest(CLASS_A + "/[test-factory:f()]/[dynamic-test:#1]"));
        assertFalse(tracker.usesMocks(CLASS_A + "/[test-factory:f()]/[dynamic-test:#1]"));
    }

    @Test
    void springClassDeclaringMockBeansUsesMocksEvenWithoutCreatingAny() {
        tracker.springTestClass("A", true);
        tracker.nodeStarted(ENGINE, null, null);
        tracker.nodeStarted(CLASS_A, ENGINE, "A");
        tracker.nodeStarted(CLASS_A + "/[method:one()]", CLASS_A, "A");

        assertTrue(tracker.isSpringTest(CLASS_A + "/[method:one()]"));
        assertTrue(tracker.usesMocks(CLASS_A + "/[method:one()]"));
    }

    @Test
    void plainTestIsNotSpring() {
        tracker.springTestClass("A", true);
        tracker.nodeStarted(ENGINE, null, null);
        tracker.nodeStarted(CLASS_B, ENGINE, "B");
        tracker.nodeStarted(CLASS_B + "/[method:one()]", CLASS_B, "B");

        assertFalse(tracker.isSpringTest(CLASS_B + "/[method:one()]"));
        assertFalse(tracker.usesMocks(CLASS_B + "/[method:one()]"));
    }
}
