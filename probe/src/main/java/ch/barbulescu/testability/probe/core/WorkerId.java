package ch.barbulescu.testability.probe.core;

import java.lang.management.ManagementFactory;
import java.util.concurrent.ThreadLocalRandom;

/**
 * PID plus a random suffix, so concurrent forks in the same module never
 * collide on one report file. Computed once per {@link ProbeRecorder}
 * instance, i.e. once per classloader.
 */
final class WorkerId {

    private WorkerId() {
    }

    static String generate() {
        String runtimeName = ManagementFactory.getRuntimeMXBean().getName();
        int at = runtimeName.indexOf('@');
        String pid = at > 0 ? runtimeName.substring(0, at) : runtimeName;
        long suffix = ThreadLocalRandom.current().nextLong() & Long.MAX_VALUE;
        return pid + "-" + Long.toHexString(suffix);
    }
}
