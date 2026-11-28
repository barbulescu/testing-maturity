package ch.barbulescu.testability.probe.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fault injection: nothing the sink does - throwing, or a real unwritable
 * directory - may ever propagate out of the recorder. This is the
 * boundary that keeps a broken filesystem or a badly-behaved custom sink
 * from ever failing the build it's observing.
 */
class ProbeRecorderTest {

    @Test
    void writeReportNeverThrowsWhenSinkThrows() {
        ProbeRecorder recorder = ProbeRecorder.getInstance();
        ReportSink throwingSink = (fileName, json) -> {
            throw new IOException("disk full");
        };

        assertDoesNotThrow(() -> recorder.writeReport(throwingSink));
    }

    @Test
    void writeReportNeverThrowsWhenSinkThrowsAnUncheckedError() {
        ProbeRecorder recorder = ProbeRecorder.getInstance();
        ReportSink throwingSink = (fileName, json) -> {
            throw new IllegalStateException("permission denied");
        };

        assertDoesNotThrow(() -> recorder.writeReport(throwingSink));
    }

    @Test
    void safelyReturnsFallbackAndRecordsErrorWhenComputationThrows() {
        ProbeRecorder recorder = ProbeRecorder.getInstance();

        String result = recorder.safely(() -> {
            throw new IllegalArgumentException("malformed input");
        }, "fallback");

        assertEquals("fallback", result);
    }

    @Test
    void durationsAreBucketedByLowerBound() {
        assertEquals(0, ProbeRecorder.durationBucketIndex(0));
        assertEquals(0, ProbeRecorder.durationBucketIndex(99));
        assertEquals(1, ProbeRecorder.durationBucketIndex(100));
        assertEquals(1, ProbeRecorder.durationBucketIndex(999));
        assertEquals(2, ProbeRecorder.durationBucketIndex(1_000));
        assertEquals(3, ProbeRecorder.durationBucketIndex(10_000));
    }

    @Test
    void safelyReturnsTheRealValueWhenComputationSucceeds() {
        ProbeRecorder recorder = ProbeRecorder.getInstance();

        String result = recorder.safely(() -> "real value", "fallback");

        assertEquals("real value", result);
    }

    /** Thrown only by this test, so the shared recorder's counts for it are this test's alone. */
    static final class RecorderTestBreakage extends RuntimeException {
        RecorderTestBreakage(String message) {
            super(message);
        }
    }

    static final class RecorderTestAssertion extends AssertionError {
        RecorderTestAssertion(String message) {
            super(message);
        }
    }

    @Test
    void failuresAreCountedByThrownTypeWithoutTheirMessages() {
        ProbeRecorder recorder = ProbeRecorder.getInstance();
        recorder.recordTestPlanStarted();
        recorder.recordTestFailed("junit-jupiter", new RecorderTestBreakage("secret-breakage-message"));
        recorder.recordTestFailed("junit-jupiter", new RecorderTestBreakage("secret-breakage-message"));
        recorder.recordTestFailed("junit-jupiter", new RecorderTestAssertion("secret-assertion-message"));
        recorder.recordTestFailed("junit-jupiter", null);

        AtomicReference<String> report = new AtomicReference<>();
        recorder.writeReport((fileName, json) -> report.set(json));

        String json = report.get();
        assertTrue(json.contains("\"" + RecorderTestBreakage.class.getName() + "\":2"), json);
        assertTrue(json.contains("\"" + RecorderTestAssertion.class.getName() + "\":1"), json);
        assertFalse(json.contains("secret-"), "messages must never reach the report:\n" + json);
    }

    @Test
    void testShapesAndTagsAreCounted() {
        ProbeRecorder recorder = ProbeRecorder.getInstance();
        recorder.recordTestPlanStarted();
        recorder.recordTestTags(Arrays.asList("recorder-test-slow", "recorder-test-integration"));
        recorder.recordTestTags(Collections.singletonList("recorder-test-slow"));

        AtomicReference<String> report = new AtomicReference<>();
        recorder.writeReport((fileName, json) -> report.set(json));

        String json = report.get();
        assertTrue(json.contains("\"recorder-test-slow\":2"), json);
        assertTrue(json.contains("\"recorder-test-integration\":1"), json);
        assertTrue(json.contains("\"style\":{\"parameterized\":"), json);
    }
}
