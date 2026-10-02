package ch.barbulescu.testability.probe.core;

/**
 * Bridges to the {@code mockito} adapter by class name, so {@code core}
 * keeps depending on the JDK only. Mockito's listeners are thread-local,
 * which is why this is called on every node start rather than once.
 */
public final class MockitoHook {

    private static final String MOCKITO_CLASS = "org.mockito.Mockito";
    private static final String INSTALLER_CLASS = "ch.barbulescu.testability.probe.mockito.MockitoListenerInstaller";

    private static volatile Runnable installer;
    private static volatile boolean unavailable;
    private static volatile boolean observed;

    private MockitoHook() {
    }

    public static void ensureInstalledOnCurrentThread() {
        if (unavailable) {
            return;
        }
        try {
            Runnable current = installer;
            if (current == null) {
                ClassLoader loader = MockitoHook.class.getClassLoader();
                Class.forName(MOCKITO_CLASS, false, loader);
                current = (Runnable) Class.forName(INSTALLER_CLASS, true, loader).getDeclaredConstructor().newInstance();
                installer = current;
            }
            current.run();
            observed = true;
        } catch (LinkageError | ReflectiveOperationException e) {
            // Mockito absent, or older than 2.1 (no listener API): nothing to observe.
            unavailable = true;
        }
    }

    /** False means a zero mock count says nothing: Mockito was absent or couldn't be hooked. */
    public static boolean isObserved() {
        return observed;
    }
}
