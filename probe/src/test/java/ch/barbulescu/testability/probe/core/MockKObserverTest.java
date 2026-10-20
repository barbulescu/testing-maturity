package ch.barbulescu.testability.probe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** MockK isn't on this classpath; the examples run it for real. */
class MockKObserverTest {

    @Test
    void withoutMockKThereIsNothingToObserve() {
        assertSame(MockKObserver.NONE, MockKObserver.newMocksOnCurrentThread());
        assertFalse(MockKObserver.isObserved());
    }

    @Test
    void mockTypesMapToKindFlags() {
        assertEquals(0, MockKObserver.kindOf("REGULAR"));
        assertEquals(TestNodeTracker.SPY, MockKObserver.kindOf("SPY"));
        assertEquals(TestNodeTracker.STATIC_MOCK, MockKObserver.kindOf("STATIC"));
        assertEquals(TestNodeTracker.STATIC_MOCK, MockKObserver.kindOf("OBJECT"));
        assertEquals(TestNodeTracker.STATIC_MOCK, MockKObserver.kindOf("CONSTRUCTOR"));
    }

    @Test
    void childMocksOfAStubbedChainAreRecognizedByName() {
        assertTrue(MockKObserver.isChildMock("child of #3"));
        assertTrue(MockKObserver.isChildMock("child^2 of #3"));
        assertFalse(MockKObserver.isChildMock("#3"));
        assertFalse(MockKObserver.isChildMock("childRepository"));
    }

    @Test
    void countingMapCountsEveryMockPutIntoIt() {
        java.util.Map<Object, Object> original = new java.util.HashMap<>();
        original.put("existing", "undo");
        MockKObserver.CountingMap map = new MockKObserver.CountingMap(original);

        map.put("a", "undo a");
        map.put("existing", "undo again");
        map.remove("a");
        map.put("a", "undo a again");

        assertEquals(2, map.drain());
        assertEquals(0, map.drain());
        assertEquals("undo again", map.get("existing"));
    }
}
