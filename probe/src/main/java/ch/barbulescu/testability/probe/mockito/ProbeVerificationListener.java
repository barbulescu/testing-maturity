package ch.barbulescu.testability.probe.mockito;

import ch.barbulescu.testability.probe.core.Safe;
import ch.barbulescu.testability.probe.core.TestNodeTracker;
import org.mockito.listeners.MockitoListener;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * A {@code VerificationListener}, which Mockito calls for every {@code verify(mock)} and
 * {@code inOrder.verify(mock)}, failed ones included. Mockito added that interface after 2.1,
 * which the probe compiles against, so the listener is a proxy of it, made only when it exists.
 * <p>
 * {@code verifyNoMoreInteractions} and {@code verifyNoInteractions} never reach a listener:
 * {@code junit.NoMoreInteractionsDetector} finds them in the test's bytecode instead.
 */
final class ProbeVerificationListener implements InvocationHandler {

    private static final String LISTENER_CLASS = "org.mockito.listeners.VerificationListener";
    private static final String EVENT_CLASS = "org.mockito.verification.VerificationEvent";
    // verify(mock, only()): the call verified was the mock's only one.
    private static final String ONLY_MODE_CLASS = "org.mockito.internal.verification.Only";

    private final Method getMode;

    private ProbeVerificationListener(Method getMode) {
        this.getMode = getMode;
    }

    /** Null when this Mockito has no {@code VerificationListener}. */
    static MockitoListener createOrNull() {
        ClassLoader loader = MockitoListener.class.getClassLoader();
        try {
            Class<?> listenerType = Class.forName(LISTENER_CLASS, false, loader);
            Method getMode = Class.forName(EVENT_CLASS, false, loader).getMethod("getMode");
            return (MockitoListener) Proxy.newProxyInstance(loader, new Class<?>[] {listenerType},
                    new ProbeVerificationListener(getMode));
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            return null;
        }
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        String name = method.getName();
        if ("equals".equals(name)) {
            // Mockito removes listeners from a set.
            return proxy == args[0];
        }
        if ("hashCode".equals(name)) {
            return System.identityHashCode(proxy);
        }
        if ("toString".equals(name)) {
            return ProbeVerificationListener.class.getSimpleName();
        }
        if ("onVerification".equals(name) && args != null && args.length == 1) {
            Safe.run(() -> TestNodeTracker.getInstance().verificationOnCurrentThread(kindsOf(args[0])));
        }
        return null;
    }

    private int kindsOf(Object event) {
        Object mode;
        try {
            mode = getMode.invoke(event);
        } catch (ReflectiveOperationException e) {
            mode = null;
        }
        boolean only = mode != null && ONLY_MODE_CLASS.equals(mode.getClass().getName());
        return TestNodeTracker.VERIFIES_INTERACTIONS | (only ? TestNodeTracker.VERIFIES_NO_MORE_INTERACTIONS : 0);
    }
}
