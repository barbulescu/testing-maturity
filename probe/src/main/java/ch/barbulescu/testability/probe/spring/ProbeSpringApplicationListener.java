package ch.barbulescu.testability.probe.spring;

import ch.barbulescu.testability.probe.core.ProbeRecorder;
import ch.barbulescu.testability.probe.core.Safe;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registered via {@code META-INF/spring.factories}; attaches to every
 * {@code SpringApplication} start, so {@link ProductionGuard} keeps it
 * inert outside a test JVM. The Boot lifecycle events themselves are
 * matched and read by name/reflection rather than typed imports - see
 * {@link BootEventNames}.
 */
public final class ProbeSpringApplicationListener implements ApplicationListener<ApplicationEvent> {

    private final Map<Object, Long> startNanosBySpringApplication = new ConcurrentHashMap<>();

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        if (!ProductionGuard.isTestJvm()) {
            return;
        }
        Safe.run(() -> handle(event));
    }

    private void handle(ApplicationEvent event) {
        String eventClassName = event.getClass().getName();
        if (BootEventNames.STARTING.equals(eventClassName)) {
            Object springApplication = invoke(event, "getSpringApplication");
            if (springApplication != null) {
                startNanosBySpringApplication.put(springApplication, System.nanoTime());
            }
        } else if (BootEventNames.READY.equals(eventClassName)) {
            Object springApplication = invoke(event, "getSpringApplication");
            ContextTracker.shared().markBootStarted(invoke(event, "getApplicationContext"));
            ProbeRecorder.getInstance().recordBootStartSucceeded(durationSinceStart(springApplication));
        } else if (BootEventNames.FAILED.equals(eventClassName)) {
            Object springApplication = invoke(event, "getSpringApplication");
            Long durationMs = durationSinceStart(springApplication);
            String rootCauseType = rootCauseTypeOf(event);
            ProbeRecorder.getInstance().recordBootStartFailed(durationMs, rootCauseType);
        }
    }

    private Long durationSinceStart(Object springApplication) {
        if (springApplication == null) {
            return null;
        }
        Long startNanos = startNanosBySpringApplication.remove(springApplication);
        if (startNanos == null) {
            return null;
        }
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private String rootCauseTypeOf(ApplicationEvent event) {
        Object exception = invoke(event, "getException");
        if (!(exception instanceof Throwable)) {
            return null;
        }
        Throwable cause = (Throwable) exception;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getClass().getName();
    }

    private Object invoke(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            return null;
        }
    }
}
