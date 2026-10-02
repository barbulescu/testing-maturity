package ch.barbulescu.testability.probe.spring;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContextTrackerTest {

    private final ContextTracker tracker = new ContextTracker();
    private final List<Boolean> plainFlags = new ArrayList<>();

    @Test
    void contextSharedThroughTheCacheIsCountedOnce() {
        GenericApplicationContext context = new GenericApplicationContext();

        tracker.observe(context, plainFlags::add);
        tracker.observe(context, plainFlags::add);

        assertEquals(Arrays.asList(true), plainFlags);
    }

    @Test
    void distinctContextsAreCountedSeparately() {
        tracker.observe(new GenericApplicationContext(), plainFlags::add);
        tracker.observe(new GenericApplicationContext(), plainFlags::add);

        assertEquals(Arrays.asList(true, true), plainFlags);
    }

    @Test
    void bootStartedContextIsNotPlain() {
        GenericApplicationContext context = new GenericApplicationContext();
        tracker.markBootStarted(context);

        tracker.observe(context, plainFlags::add);

        assertEquals(Arrays.asList(false), plainFlags);
    }

    @Test
    void everyLevelOfAContextHierarchyIsCounted() {
        GenericApplicationContext parent = new GenericApplicationContext();
        GenericApplicationContext child = new GenericApplicationContext(parent);

        tracker.observe(child, plainFlags::add);
        // a sibling child sharing the cached parent adds only itself
        tracker.observe(new GenericApplicationContext(parent), plainFlags::add);

        assertEquals(Arrays.asList(true, true, true), plainFlags);
    }

    @Test
    void markingNullIsIgnored() {
        tracker.markBootStarted(null);

        tracker.observe(new GenericApplicationContext(), plainFlags::add);

        assertEquals(Arrays.asList(true), plainFlags);
    }
}
