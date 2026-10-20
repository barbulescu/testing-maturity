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
 * ancestors. Work done at class level (a {@code @BeforeAll}, a static field)
 * is attributed to the class container and therefore counts for all of its
 * tests.
 * <p>
 * JUnit creates a test's instance (running its field initializers) before
 * reporting that the test started, while the class is still the running node.
 * Mocks created then are held back and attributed to the next test that starts
 * on the thread, so a field initializer counts once per test, like a
 * {@code @BeforeEach}.
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
    private final Set<String> tests = ConcurrentHashMap.newKeySet();
    private final ThreadLocal<NodeMocks> pendingForNextTest = new ThreadLocal<>();
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

    /**
     * {@code className} may be null (e.g. a dynamic test), in which case the parent's is inherited.
     * {@code test} is false for containers.
     */
    public void nodeStarted(String id, String parentId, String className, boolean test) {
        if (parentId != null) {
            parentOf.put(id, parentId);
        }
        String effectiveClassName = className != null || parentId == null ? className : classNameOf.get(parentId);
        if (effectiveClassName != null) {
            classNameOf.put(id, effectiveClassName);
        }
        running.get().push(id);
        if (test) {
            tests.add(id);
            NodeMocks pending = pendingForNextTest.get();
            if (pending != null) {
                pendingForNextTest.remove();
                mocksByNode.put(id, pending);
            }
        }
    }

    public void nodeFinished(String id) {
        running.get().removeFirstOccurrence(id);
        if (!tests.contains(id)) {
            pendingForNextTest.remove();
        }
    }

    /** A skipped test may have had its instance created, but it never starts. */
    public void discardPendingMocks() {
        pendingForNextTest.remove();
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
        mockCreatedOnCurrentThread(kinds, countsAsMock, null);
    }

    /** {@code stack} is the creating thread's stack, captured here when null (tests pass their own). */
    void mockCreatedOnCurrentThread(int kinds, boolean countsAsMock, StackTraceElement[] stack) {
        String current = running.get().peek();
        if (current == null || !parentOf.containsKey(current)) {
            return;
        }
        NodeMocks mocks;
        if (!tests.contains(current)
                && isCreatingTestInstance(stack != null ? stack : new Throwable().getStackTrace())) {
            mocks = pendingForNextTest.get();
            if (mocks == null) {
                mocks = new NodeMocks();
                pendingForNextTest.set(mocks);
            }
        } else {
            mocks = mocksByNode.computeIfAbsent(current, key -> new NodeMocks());
        }
        mocks.kinds.accumulateAndGet(kinds, (a, b) -> a | b);
        if (countsAsMock) {
            mocks.count.incrementAndGet();
        }
    }

    /**
     * Whether the mock is being created while JUnit creates the instance for a single test:
     * Jupiter's {@code instantiateTestClass} called from a test method's {@code prepare}
     * (not a {@code PER_CLASS} instance, which the class's tests share), or JUnit 4's
     * {@code createTest}. A Spring frame between the mock and the instantiation means Spring
     * is loading its context, e.g. to inject the constructor: its mock beans belong to the class.
     */
    static boolean isCreatingTestInstance(StackTraceElement[] stack) {
        for (int i = 0; i < stack.length; i++) {
            String className = stack[i].getClassName();
            String methodName = stack[i].getMethodName();
            if (className.startsWith("org.springframework.")) {
                return false;
            }
            if ("org.junit.runners.BlockJUnit4ClassRunner".equals(className) && "createTest".equals(methodName)) {
                return true;
            }
            if (className.startsWith("org.junit.jupiter.engine.descriptor.") && "instantiateTestClass".equals(methodName)) {
                for (int j = i + 1; j < stack.length; j++) {
                    if ("org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor".equals(stack[j].getClassName())
                            && "prepare".equals(stack[j].getMethodName())) {
                        return true;
                    }
                }
                return false;
            }
        }
        return false;
    }

    /**
     * Mocks found after they were created, e.g. MockK's, credited to a node that has started.
     * Engine roots are ignored, as for {@link #mockCreatedOnCurrentThread(int, boolean)}.
     */
    public void mocksCreatedFor(String id, int count, int kinds) {
        if (count == 0 && kinds == 0 || !parentOf.containsKey(id)) {
            return;
        }
        NodeMocks mocks = mocksByNode.computeIfAbsent(id, key -> new NodeMocks());
        mocks.kinds.accumulateAndGet(kinds, (a, b) -> a | b);
        mocks.count.addAndGet(count);
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

    /** Mocks the test itself created, its instance's field initializers included. */
    public int ownMockCount(String id) {
        NodeMocks mocks = mocksByNode.get(id);
        return mocks != null ? mocks.count.get() : 0;
    }

    /**
     * Mocks created at class level and shared with the class's other tests: by the test's
     * ancestors, e.g. in {@code @BeforeAll}. A Spring test class counts at least its declared
     * mock beans, since a context from the cache creates none while the test runs.
     */
    public int classLevelMockCount(String id) {
        int count = 0;
        for (String node = parentOf.get(id); node != null; node = parentOf.get(node)) {
            NodeMocks mocks = mocksByNode.get(node);
            if (mocks != null) {
                count += mocks.count.get();
            }
        }
        String className = classNameOf.get(id);
        Integer declared = className != null ? mockBeansBySpringClass.get(className) : null;
        return declared != null ? Math.max(count, declared) : count;
    }

    /** All mocks the test used: its own and its class's. */
    public int mockCount(String id) {
        return ownMockCount(id) + classLevelMockCount(id);
    }

    /** The test's class, or null if unknown. */
    public String className(String id) {
        return classNameOf.get(id);
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
