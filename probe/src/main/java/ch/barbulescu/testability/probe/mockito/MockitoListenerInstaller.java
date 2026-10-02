package ch.barbulescu.testability.probe.mockito;

import org.mockito.Mockito;
import org.mockito.MockitoFramework;
import org.mockito.listeners.MockitoListener;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Loaded reflectively by {@code core.MockitoHook}, only when Mockito is on
 * the classpath.
 * <p>
 * {@code addListener}/{@code removeListener} are called reflectively:
 * they return {@code void} in Mockito 2.1 and {@code MockitoFramework}
 * later, and since the return type is part of the JVM method descriptor,
 * a direct call compiled against either fails with
 * {@code NoSuchMethodError} on the other.
 */
public final class MockitoListenerInstaller implements Runnable {

    private static final ProbeMockCreationListener LISTENER = new ProbeMockCreationListener();

    private final Method addListener;
    private final Method removeListener;

    public MockitoListenerInstaller() throws NoSuchMethodException {
        addListener = MockitoFramework.class.getMethod("addListener", MockitoListener.class);
        removeListener = MockitoFramework.class.getMethod("removeListener", MockitoListener.class);
    }

    @Override
    public void run() {
        MockitoFramework framework = Mockito.framework();
        // Removed and re-added on every node start rather than once per thread: anything that
        // clears Mockito's thread-local listeners would otherwise silently blind the probe.
        // Adding twice would throw RedundantListenerException.
        invoke(removeListener, framework);
        invoke(addListener, framework);
    }

    private static void invoke(Method method, MockitoFramework framework) {
        try {
            method.invoke(framework, LISTENER);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            throw cause instanceof RuntimeException ? (RuntimeException) cause : new IllegalStateException(cause);
        }
    }
}
