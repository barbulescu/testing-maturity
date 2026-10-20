package ch.barbulescu.testability.probe.core;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Finds the MockK mocks created since the last look. MockK has no listener API like
 * Mockito's, and the factories that create its mocks sit in final fields, so instead the
 * probe reads MockK's registry of live mocks ({@code StubRepository.getAllStubs()}) at
 * every node start and finish and picks out the ones it hasn't seen yet.
 * <p>
 * Static, object and constructor mocks are usually undone before the test's finish event
 * ({@code unmockkAll()} in {@code @AfterEach}, or the scoped {@code mockkObject(x) { }}),
 * which removes them from that registry. MockK records how to undo each one in a per-thread
 * map in {@code MockKCancellationRegistry}, so on each thread the probe replaces that map
 * with a copy that behaves the same but also counts what is put into it.
 * <p>
 * Everything is looked up by name (MockK 1.9 to 1.14), so the probe needs no MockK or Kotlin
 * dependency. Looking never initializes MockK: until a test first uses it, its gateway isn't
 * set and there is nothing to read.
 */
public final class MockKObserver {

    /** Count and kind flags of new mocks; {@link #NONE} when there are none. */
    public static final class NewMocks {
        public final int count;
        public final int kinds;

        NewMocks(int count, int kinds) {
            this.count = count;
            this.kinds = kinds;
        }
    }

    public static final NewMocks NONE = new NewMocks(0, 0);

    private static final String GATEWAY_COMPANION_CLASS = "io.mockk.MockKGateway$Companion";
    private static final String JVM_GATEWAY_CLASS = "io.mockk.impl.JvmMockKGateway";
    private static final String STUB_REPOSITORY_CLASS = "io.mockk.impl.stub.StubRepository";
    private static final String STUB_CLASS = "io.mockk.impl.stub.MockKStub";
    private static final String FUNCTION0_CLASS = "kotlin.jvm.functions.Function0";
    private static final String CANCELLATION_REGISTRY_CLASS = "io.mockk.MockKCancellationRegistry";
    private static final String CANCELLATION_TYPE_CLASS = "io.mockk.MockKCancellationRegistry$Type";
    private static final String[] CANCELLATION_TYPES = {"OBJECT", "STATIC", "CONSTRUCTOR"};

    private static final Map<Object, Boolean> SEEN = Collections.synchronizedMap(new WeakHashMap<Object, Boolean>());

    private static volatile Reflection reflection;
    private static volatile boolean unavailable;

    private MockKObserver() {
    }

    private static final class Reflection {
        final Field implementation;
        final Method invoke;
        final Class<?> jvmGateway;
        final Method getStubRepo;
        final Method getAllStubs;
        final Class<?> stub;
        final Method getMockType;
        final Method getName;
        /** Since MockK 1.13; null before. */
        final Method getThreadId;
        /** Per-thread maps of the static, object and constructor mocks to undo; empty if unreadable. */
        final List<ThreadLocal<Map<Object, Object>>> cancellationMaps;

        Reflection(ClassLoader loader) throws ReflectiveOperationException {
            implementation = Class.forName(GATEWAY_COMPANION_CLASS, false, loader).getField("implementation");
            invoke = Class.forName(FUNCTION0_CLASS, false, loader).getMethod("invoke");
            jvmGateway = Class.forName(JVM_GATEWAY_CLASS, false, loader);
            getStubRepo = jvmGateway.getMethod("getStubRepo");
            getAllStubs = Class.forName(STUB_REPOSITORY_CLASS, false, loader).getMethod("getAllStubs");
            stub = Class.forName(STUB_CLASS, false, loader);
            getMockType = stub.getMethod("getMockType");
            getName = stub.getMethod("getName");
            Method threadId;
            try {
                threadId = stub.getMethod("getThreadId");
            } catch (NoSuchMethodException e) {
                threadId = null;
            }
            getThreadId = threadId;
            cancellationMaps = cancellationMaps(loader);
        }

        @SuppressWarnings("unchecked")
        private static List<ThreadLocal<Map<Object, Object>>> cancellationMaps(ClassLoader loader) {
            List<ThreadLocal<Map<Object, Object>>> maps = new ArrayList<>();
            try {
                Class<?> registryClass = Class.forName(CANCELLATION_REGISTRY_CLASS, false, loader);
                Class<?> typeClass = Class.forName(CANCELLATION_TYPE_CLASS, false, loader);
                Object registry = registryClass.getField("INSTANCE").get(null);
                Method subRegistry = registryClass.getMethod("subRegistry", typeClass);
                for (String type : CANCELLATION_TYPES) {
                    Object perType = subRegistry.invoke(registry, typeClass.getMethod("valueOf", String.class).invoke(null, type));
                    Field mapTl = perType.getClass().getDeclaredField("mapTl");
                    mapTl.setAccessible(true);
                    maps.add((ThreadLocal<Map<Object, Object>>) mapTl.get(perType));
                }
                return maps;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                // Static, object and constructor mocks are then only seen while they're alive.
                return Collections.emptyList();
            }
        }
    }

    /**
     * Same as the map it replaces, but counts the mocks put into it: one per object or class
     * mocked. MockK undoes a previous mock of the same key first, so mocking it again counts again.
     */
    static final class CountingMap extends HashMap<Object, Object> {
        int newMocks;

        CountingMap(Map<Object, Object> original) {
            super(original);
        }

        @Override
        public Object put(Object key, Object value) {
            if (!containsKey(key)) {
                newMocks++;
            }
            return super.put(key, value);
        }

        int drain() {
            int count = newMocks;
            newMocks = 0;
            return count;
        }
    }

    /** Makes the current thread's static, object and constructor mocks countable; idempotent. */
    public static void watchCurrentThread() {
        Reflection r = reflection();
        if (r == null) {
            return;
        }
        for (ThreadLocal<Map<Object, Object>> map : r.cancellationMaps) {
            Map<Object, Object> current = map.get();
            if (!(current instanceof CountingMap)) {
                map.set(new CountingMap(current));
            }
        }
    }

    /**
     * The mocks MockK created on this thread since the last call on it. Without the creating
     * thread (MockK before 1.13), every new mock counts for the calling thread.
     */
    @SuppressWarnings("deprecation") // Thread.getId(): threadId() needs Java 19
    public static NewMocks newMocksOnCurrentThread() {
        Reflection r = reflection();
        if (r == null) {
            return NONE;
        }
        try {
            Object implementation = r.implementation.get(null);
            if (implementation == null) {
                return NONE; // MockK not used yet
            }
            Object gateway = r.invoke.invoke(implementation);
            if (!r.jvmGateway.isInstance(gateway)) {
                return NONE;
            }
            List<?> stubs = (List<?>) r.getAllStubs.invoke(r.getStubRepo.invoke(gateway));
            long currentThread = Thread.currentThread().getId();
            int count = 0;
            int kinds = 0;
            boolean countedByCancellations = !r.cancellationMaps.isEmpty();
            for (ThreadLocal<Map<Object, Object>> map : r.cancellationMaps) {
                Map<Object, Object> current = map.get();
                if (current instanceof CountingMap) {
                    int created = ((CountingMap) current).drain();
                    count += created;
                    kinds |= created > 0 ? TestNodeTracker.STATIC_MOCK : 0;
                }
            }
            for (Object stub : stubs) {
                if (!r.stub.isInstance(stub)
                        || r.getThreadId != null && ((Long) r.getThreadId.invoke(stub)) != currentThread
                        || SEEN.put(stub, Boolean.TRUE) != null) {
                    continue;
                }
                String mockType = String.valueOf(r.getMockType.invoke(stub));
                if ("TEMPORARY".equals(mockType) || isChildMock(String.valueOf(r.getName.invoke(stub)))
                        || countedByCancellations && kindOf(mockType) == TestNodeTracker.STATIC_MOCK) {
                    continue;
                }
                count++;
                kinds |= kindOf(mockType);
            }
            return count == 0 ? NONE : new NewMocks(count, kinds);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            unavailable = true;
            throw new IllegalStateException("reading MockK's mocks failed", e);
        }
    }

    /** MockK's type of a mock, as the tracker's kind flags. */
    static int kindOf(String mockType) {
        if ("SPY".equals(mockType)) {
            return TestNodeTracker.SPY;
        }
        if ("STATIC".equals(mockType) || "OBJECT".equals(mockType) || "CONSTRUCTOR".equals(mockType)) {
            return TestNodeTracker.STATIC_MOCK;
        }
        return 0;
    }

    /**
     * The mocks MockK creates along a chain stubbed in one go ({@code every { a.b.c }}) are
     * named "child of ..." or "child^2 of ...". The test didn't ask for them, so they aren't counted.
     */
    static boolean isChildMock(String name) {
        return name.startsWith("child of ") || name.matches("child\\^\\d+ of .*");
    }

    /** False means MockK was absent or couldn't be read, so a zero count says nothing about it. */
    public static boolean isObserved() {
        return reflection != null && !unavailable;
    }

    private static Reflection reflection() {
        if (unavailable) {
            return null;
        }
        Reflection r = reflection;
        if (r == null) {
            try {
                r = new Reflection(MockKObserver.class.getClassLoader());
                reflection = r;
            } catch (ReflectiveOperationException | LinkageError e) {
                // MockK absent, or a version without these methods: nothing to observe.
                unavailable = true;
                return null;
            }
        }
        return r;
    }
}
