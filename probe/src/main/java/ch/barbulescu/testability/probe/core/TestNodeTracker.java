package ch.barbulescu.testability.probe.core;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

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

    /** Kinds of mock a test used, combined as bit flags. */
    public static final int STATIC_MOCK = 1;
    public static final int DEEP_STUBS = 2;
    public static final int LENIENT = 4;
    public static final int SPY = 8;

    private static final TestNodeTracker INSTANCE = new TestNodeTracker();

    private final ThreadLocal<Deque<String>> running = ThreadLocal.withInitial(ArrayDeque::new);
    private final Map<String, String> parentOf = new ConcurrentHashMap<>();
    private final Map<String, String> classNameOf = new ConcurrentHashMap<>();
    private final Map<String, NodeMocks> mocksByNode = new ConcurrentHashMap<>();
    private final Set<String> springClasses = ConcurrentHashMap.newKeySet();
    private final Map<String, Integer> mockBeansBySpringClass = new ConcurrentHashMap<>();
    private final Set<String> springClassesDeclaringSpies = ConcurrentHashMap.newKeySet();

    private static final class NodeMocks {
        final AtomicInteger count = new AtomicInteger();
        final AtomicInteger kinds = new AtomicInteger();
    }

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
        mockCreatedOnCurrentThread(0, true);
    }

    /**
     * {@code kinds} is a combination of the kind flags above, or 0 for an ordinary mock.
     * {@code countsAsMock} is false for mocks the test didn't ask for itself, such as the
     * nested mocks deep stubs create along a call chain: they set flags but aren't counted.
     */
    public void mockCreatedOnCurrentThread(int kinds, boolean countsAsMock) {
        String current = running.get().peek();
        if (current != null && parentOf.containsKey(current)) {
            NodeMocks mocks = mocksByNode.computeIfAbsent(current, key -> new NodeMocks());
            mocks.kinds.accumulateAndGet(kinds, (a, b) -> a | b);
            if (countsAsMock) {
                mocks.count.incrementAndGet();
            }
        }
    }

    /** Declared {@code @MockBean}/{@code @MockitoBean} and spy bean fields, counted separately. */
    public void springTestClass(String className, int mockBeanFields, int spyBeanFields) {
        springClasses.add(className);
        if (mockBeanFields + spyBeanFields > 0) {
            mockBeansBySpringClass.put(className, mockBeanFields + spyBeanFields);
        }
        if (spyBeanFields > 0) {
            springClassesDeclaringSpies.add(className);
        }
    }

    public boolean isSpringTest(String id) {
        String className = classNameOf.get(id);
        return className != null && springClasses.contains(className);
    }

    public boolean usesMocks(String id) {
        String className = classNameOf.get(id);
        if (className != null && mockBeansBySpringClass.containsKey(className)) {
            return true;
        }
        for (String node = id; node != null; node = parentOf.get(node)) {
            if (mocksByNode.containsKey(node)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Mocks created by this node and its ancestors. A Spring test class counts at least its
     * declared mock beans, since a context from the cache creates none while the test runs.
     */
    public int mockCount(String id) {
        int count = 0;
        for (String node = id; node != null; node = parentOf.get(node)) {
            NodeMocks mocks = mocksByNode.get(node);
            if (mocks != null) {
                count += mocks.count.get();
            }
        }
        String className = classNameOf.get(id);
        Integer declared = className != null ? mockBeansBySpringClass.get(className) : null;
        return declared != null ? Math.max(count, declared) : count;
    }

    /** The kind flags of every mock created by this node or any of its ancestors. */
    public int mockKinds(String id) {
        String className = classNameOf.get(id);
        int kinds = className != null && springClassesDeclaringSpies.contains(className) ? SPY : 0;
        for (String node = id; node != null; node = parentOf.get(node)) {
            NodeMocks mocks = mocksByNode.get(node);
            if (mocks != null) {
                kinds |= mocks.kinds.get();
            }
        }
        return kinds;
    }
}
