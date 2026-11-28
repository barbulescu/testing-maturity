package ch.barbulescu.testability.examples.rerun;

import io.github.artsok.RepeatedIfExceptionsTest;
import org.junitpioneer.jupiter.RetryingTest;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Each test fails its first attempt and passes the second, retried by its own extension. */
class RetryingFlakyTest {

    private static final AtomicInteger PIONEER_ATTEMPTS = new AtomicInteger();
    private static final AtomicInteger RERUNNER_ATTEMPTS = new AtomicInteger();

    @RetryingTest(3)
    void retriedByPioneer() {
        assertTrue(PIONEER_ATTEMPTS.getAndIncrement() > 0, "first attempt fails");
    }

    @RepeatedIfExceptionsTest(repeats = 3)
    void retriedByRerunner() {
        assertTrue(RERUNNER_ATTEMPTS.getAndIncrement() > 0, "first attempt fails");
    }
}
