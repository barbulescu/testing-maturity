package ch.barbulescu.testability.probe.core;

/**
 * Every probe entry point runs through here: the probe must never fail or
 * slow down the build it's observing. Failures are recorded as facts
 * (exception type only) rather than surfaced.
 */
public final class Safe {

    private Safe() {
    }

    public static void run(Runnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            handle(t);
        }
    }

    private static void handle(Throwable t) {
        try {
            ProbeRecorder.getInstance().recordError(t);
        } catch (Throwable ignored) {
            // recording the error must never itself throw
        }
        if (Boolean.getBoolean("testability.probe.debug")) {
            t.printStackTrace(System.err);
        }
    }
}
