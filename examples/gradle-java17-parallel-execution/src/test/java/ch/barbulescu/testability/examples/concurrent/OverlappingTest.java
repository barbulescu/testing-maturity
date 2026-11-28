package ch.barbulescu.testability.examples.concurrent;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

/**
 * Each test waits until all three are running, so with parallel execution they overlap for
 * certain; run one after another, the first would time out.
 */
class OverlappingTest {

    private static final CyclicBarrier ALL_RUNNING = new CyclicBarrier(3);

    @Test
    void first() throws Exception {
        ALL_RUNNING.await(30, TimeUnit.SECONDS);
    }

    @Test
    void second() throws Exception {
        ALL_RUNNING.await(30, TimeUnit.SECONDS);
    }

    @Test
    void third() throws Exception {
        ALL_RUNNING.await(30, TimeUnit.SECONDS);
    }
}
