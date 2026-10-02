package ch.barbulescu.testability.probe.spring;

import org.springframework.context.ApplicationContext;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Counts each distinct {@code ApplicationContext} the tests ran against
 * exactly once, however many test classes share it through Spring's
 * context cache. A context recreated after {@code @DirtiesContext} is a
 * new object and therefore counted again - the same semantics as
 * {@code bootStarts}.
 * <p>
 * "Plain" means not started by Spring Boot: the {@code ApplicationListener}
 * marks every context it sees reach {@code ApplicationReadyEvent}, which
 * happens during loading, before any test listener sees the context.
 * <p>
 * Weak keys so the probe never keeps a closed context alive. Application
 * contexts don't override {@code equals}/{@code hashCode}, so a
 * {@link WeakHashMap} compares them by identity.
 */
final class ContextTracker {

    interface NewContextListener {
        void newContext(boolean plain);
    }

    private static final ContextTracker SHARED = new ContextTracker();

    private final Map<Object, Boolean> bootStarted = new WeakHashMap<>();
    private final Map<Object, Boolean> seen = new WeakHashMap<>();

    static ContextTracker shared() {
        return SHARED;
    }

    synchronized void markBootStarted(Object context) {
        if (context != null) {
            bootStarted.put(context, Boolean.TRUE);
        }
    }

    /**
     * Walks the parent chain too: a {@code @ContextHierarchy} loads every
     * level, and each level is a real context in its own right.
     */
    synchronized void observe(ApplicationContext context, NewContextListener listener) {
        for (ApplicationContext current = context; current != null; current = current.getParent()) {
            if (seen.put(current, Boolean.TRUE) == null) {
                listener.newContext(!bootStarted.containsKey(current));
            }
        }
    }
}
