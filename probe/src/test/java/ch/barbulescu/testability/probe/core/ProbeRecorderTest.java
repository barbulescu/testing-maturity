package ch.barbulescu.testability.probe.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
