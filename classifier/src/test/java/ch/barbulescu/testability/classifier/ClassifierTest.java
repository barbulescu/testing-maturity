package ch.barbulescu.testability.classifier;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClassifierTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void nullReportIsNoVisibility() {
        assertEquals(Level.NO_VISIBILITY, Classifier.classify(null));
    }

    @Test
    void missingTestsSectionIsNoVisibility() throws IOException {
        assertEquals(Level.NO_VISIBILITY, Classifier.classify(json("{\"probeErrors\":[]}")));
    }

    @Test
    void zeroTestsIsNoVisibility() throws IOException {
        assertEquals(Level.NO_VISIBILITY,
                Classifier.classify(json("{\"tests\":{\"succeeded\":0,\"failed\":0,\"skipped\":0},\"probeErrors\":[]}")));
    }

    @Test
    void failingTestsIsLevelOne() throws IOException {
        assertEquals(Level.TESTS_RUN,
                Classifier.classify(json("{\"tests\":{\"succeeded\":2,\"failed\":1,\"skipped\":0},\"probeErrors\":[]}")));
    }

    @Test
    void probeErrorsCapAtLevelOneEvenIfTestsPassed() throws IOException {
        assertEquals(Level.TESTS_RUN, Classifier.classify(
                json("{\"tests\":{\"succeeded\":2,\"failed\":0,\"skipped\":0},\"probeErrors\":[\"java.io.IOException\"]}")));
    }

    @Test
    void cleanPassIsLevelTwo() throws IOException {
        assertEquals(Level.TESTS_PASS_CLEANLY, Classifier.classify(json(
                "{\"tests\":{\"succeeded\":3,\"failed\":0,\"skipped\":0},"
                        + "\"onClasspath\":{\"testcontainers\":false,\"wiremock\":false},\"probeErrors\":[]}")));
    }

    @Test
    void testcontainersPresenceIsLevelThree() throws IOException {
        assertEquals(Level.INTEGRATION_AWARE, Classifier.classify(json(
                "{\"tests\":{\"succeeded\":1,\"failed\":0,\"skipped\":0},"
                        + "\"onClasspath\":{\"testcontainers\":true,\"wiremock\":false},\"probeErrors\":[]}")));
    }

    @Test
    void wiremockPresenceIsLevelThree() throws IOException {
        assertEquals(Level.INTEGRATION_AWARE, Classifier.classify(json(
                "{\"tests\":{\"succeeded\":1,\"failed\":0,\"skipped\":0},"
                        + "\"onClasspath\":{\"testcontainers\":false,\"wiremock\":true},\"probeErrors\":[]}")));
    }

    @Test
    void bootTestClassesIsLevelThree() throws IOException {
        assertEquals(Level.INTEGRATION_AWARE, Classifier.classify(json(
                "{\"tests\":{\"succeeded\":1,\"failed\":0,\"skipped\":0},"
                        + "\"onClasspath\":{\"testcontainers\":false,\"wiremock\":false},"
                        + "\"spring\":{\"bootTestClasses\":2},\"probeErrors\":[]}")));
    }

    @Test
    void allSkippedIsNotACleanPass() throws IOException {
        assertEquals(Level.TESTS_RUN, Classifier.classify(json(
                "{\"tests\":{\"succeeded\":0,\"failed\":0,\"skipped\":3},"
                        + "\"onClasspath\":{\"testcontainers\":false,\"wiremock\":false},\"probeErrors\":[]}")));
    }

    private JsonNode json(String content) throws IOException {
        return mapper.readTree(content);
    }
}
