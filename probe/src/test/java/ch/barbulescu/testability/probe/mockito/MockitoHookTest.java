package ch.barbulescu.testability.probe.mockito;

import ch.barbulescu.testability.probe.core.MockitoHook;
import ch.barbulescu.testability.probe.core.TestNodeTracker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Against the real Mockito and the shared tracker. The probe's own
 * listener is active in this test JVM too (it's on the classpath via
 * META-INF/services), so these tests push their own nodes on top of the
 * real ones and remove exactly those again.
 */
class MockitoHookTest {

    private static final String PARENT = "[engine:probe-test]/[class:Fake]";

    private final TestNodeTracker tracker = TestNodeTracker.getInstance();
    private final List<String> started = new ArrayList<>();

    @AfterEach
    void removeOwnNodes() {
        started.forEach(tracker::nodeFinished);
    }

    @Test
    void mockCreatedAfterInstallIsAttributedToCurrentNode() {
        String node = start("mock");
        MockitoHook.ensureInstalledOnCurrentThread();

        Mockito.mock(Runnable.class);

        assertTrue(MockitoHook.isObserved());
        assertTrue(tracker.usesMocks(node));
    }

    @Test
    void spyIsCountedAsMocking() {
        String node = start("spy");
        MockitoHook.ensureInstalledOnCurrentThread();

        Mockito.spy(new ArrayList<String>());

        assertTrue(tracker.usesMocks(node));
    }

    @Test
    void staticMockIsFlagged() {
        String node = start("static");
        MockitoHook.ensureInstalledOnCurrentThread();

        try (MockedStatic<StaticHolder> ignored = Mockito.mockStatic(StaticHolder.class)) {
            assertTrue(tracker.usesMocks(node));
            assertEquals(TestNodeTracker.STATIC_MOCK, tracker.mockKinds(node) & TestNodeTracker.STATIC_MOCK);
        }
    }

    @Test
    void ordinaryMockIsNotFlaggedAsStatic() {
        String node = start("ordinary");
        MockitoHook.ensureInstalledOnCurrentThread();

        Mockito.mock(Runnable.class);

        assertEquals(0, tracker.mockKinds(node) & TestNodeTracker.STATIC_MOCK);
    }

    @Test
    void deepStubsMockIsFlagged() {
        String node = start("deepStubs");
        MockitoHook.ensureInstalledOnCurrentThread();

        Mockito.mock(Runnable.class, Mockito.RETURNS_DEEP_STUBS);

        assertEquals(TestNodeTracker.DEEP_STUBS, tracker.mockKinds(node) & TestNodeTracker.DEEP_STUBS);
    }

    @Test
    void ordinaryMockIsNotFlaggedAsDeepStubs() {
        String node = start("noDeepStubs");
        MockitoHook.ensureInstalledOnCurrentThread();

        Mockito.mock(Runnable.class);

        assertEquals(0, tracker.mockKinds(node) & TestNodeTracker.DEEP_STUBS);
    }

    @Test
    void nodeWithoutMocksIsNotMarked() {
        String node = start("nothing");
        MockitoHook.ensureInstalledOnCurrentThread();

        assertFalse(tracker.usesMocks(node));
    }

    @Test
    void installingRepeatedlyOnOneThreadNeverThrows() {
        assertDoesNotThrow(() -> {
            MockitoHook.ensureInstalledOnCurrentThread();
            MockitoHook.ensureInstalledOnCurrentThread();
            MockitoHook.ensureInstalledOnCurrentThread();
        });
    }

    static final class StaticHolder {
        static String value() {
            return "real";
        }
    }

    private String start(String name) {
        String id = PARENT + "/[method:" + name + "()]";
        tracker.nodeStarted(id, PARENT, "Fake");
        started.add(id);
        return id;
    }
}
