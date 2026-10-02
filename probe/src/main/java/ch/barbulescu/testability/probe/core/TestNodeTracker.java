package ch.barbulescu.testability.probe.core;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Attributes what happens on a thread - Mockito creating a mock - to the
 * JUnit Platform node (test or container) currently running on it, so each
 * finished test can be classified by what happened during it or any of its
 * ancestors. Work done at class level (a {@code @BeforeAll}, or a field
 * initializer, which runs before the test method's own start event) is
 * attributed to the class container and therefore counts for all of its
 * tests.
 * <p>
 * Spring test classes are identified by class name instead: the Spring
 * listener has no JUnit Platform node at hand, but both sides agree on the
 * test class name.
 */
public final class TestNodeTracker {

    private static final TestNodeTracker INSTANCE = new TestNodeTracker();

    private final ThreadLocal<Deque<String>> running = ThreadLocal.withInitial(ArrayDeque::new);
    private final Map<String, String> parentOf = new ConcurrentHashMap<>();
    private final Map<String, String> classNameOf = new ConcurrentHashMap<>();
    private final Set<String> nodesThatCreatedMocks = ConcurrentHashMap.newKeySet();
    private final Set<String> springClasses = ConcurrentHashMap.newKeySet();
    private final Set<String> springClassesDeclaringMocks = ConcurrentHashMap.newKeySet();

    TestNodeTracker() {
    }

    public static TestNodeTracker getInstance() {
        return INSTANCE;
    }

    /** {@code className} may be null (e.g. a dynamic test), in which case the parent's is inherited. */
    public void nodeStarted(String id, String parentId, String className) {
        if (parentId != null) {
            parentOf.put(id, parentId);
        }
        String effectiveClassName = className != null || parentId == null ? className : classNameOf.get(parentId);
        if (effectiveClassName != null) {
            classNameOf.put(id, effectiveClassName);
        }
        running.get().push(id);
    }

    public void nodeFinished(String id) {
        running.get().removeFirstOccurrence(id);
    }

    /**
     * Engine roots are never marked: a mock created there (e.g. before a
     * JUnit 4.12 Vintage class container has reported its start) would
     * otherwise count against every test of the engine.
     */
    public void mockCreatedOnCurrentThread() {
        String current = running.get().peek();
        if (current != null && parentOf.containsKey(current)) {
            nodesThatCreatedMocks.add(current);
        }
    }

    public void springTestClass(String className, boolean declaresMockBeans) {
        springClasses.add(className);
        if (declaresMockBeans) {
            springClassesDeclaringMocks.add(className);
        }
    }

    public boolean isSpringTest(String id) {
        String className = classNameOf.get(id);
        return className != null && springClasses.contains(className);
    }

    public boolean usesMocks(String id) {
        String className = classNameOf.get(id);
        if (className != null && springClassesDeclaringMocks.contains(className)) {
            return true;
        }
        for (String node = id; node != null; node = parentOf.get(node)) {
            if (nodesThatCreatedMocks.contains(node)) {
                return true;
            }
        }
        return false;
    }
}
