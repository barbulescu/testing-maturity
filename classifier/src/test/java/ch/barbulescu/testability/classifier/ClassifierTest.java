package ch.barbulescu.testability.classifier;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.EnumSet;

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
    void testcontainersUseIsLevelThree() throws IOException {
        assertEquals(Level.INTEGRATION_AWARE, Classifier.classify(json(
                "{\"tests\":{\"succeeded\":1,\"failed\":0,\"skipped\":0},"
                        + "\"onClasspath\":{\"testcontainers\":true,\"wiremock\":false},"
                        + "\"used\":{\"testcontainers\":true},\"probeErrors\":[]}")));
    }

    @Test
    void testcontainersPresentButUnusedIsLevelTwo() throws IOException {
        assertEquals(Level.TESTS_PASS_CLEANLY, Classifier.classify(json(
                "{\"tests\":{\"succeeded\":1,\"failed\":0,\"skipped\":0},"
                        + "\"onClasspath\":{\"testcontainers\":true,\"wiremock\":false},"
                        + "\"used\":{\"testcontainers\":false},\"probeErrors\":[]}")));
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
    void flakyTestIsLevelOneEvenIfNothingFailed() throws IOException {
        assertEquals(Level.TESTS_RUN, Classifier.classify(json(
                "{\"tests\":{\"succeeded\":2,\"failed\":0,\"aborted\":1,\"skipped\":0,\"flakyTests\":1},"
                        + "\"probeErrors\":[]}")));
    }

    @Test
    void failedAssumptionIsNotAFailure() throws IOException {
        assertEquals(Level.TESTS_PASS_CLEANLY, Classifier.classify(json(
                "{\"tests\":{\"succeeded\":2,\"failed\":0,\"aborted\":1,\"skipped\":0,\"flakyTests\":0},"
                        + "\"probeErrors\":[]}")));
    }

    @Test
    void allSkippedIsNotACleanPass() throws IOException {
        assertEquals(Level.TESTS_RUN, Classifier.classify(json(
                "{\"tests\":{\"succeeded\":0,\"failed\":0,\"skipped\":3},"
                        + "\"onClasspath\":{\"testcontainers\":false,\"wiremock\":false},\"probeErrors\":[]}")));
    }

    @Test
    void enginesShowTheirStyles() throws IOException {
        assertEquals(EnumSet.of(TestStyle.PROPERTY_BASED, TestStyle.ARCHITECTURE_RULES, TestStyle.BDD, TestStyle.SPECIFICATION),
                Classifier.styles(json("{\"tests\":{\"byEngine\":{\"junit-jupiter\":4,\"jqwik\":2,"
                        + "\"archunit\":3,\"cucumber\":5,\"spock\":1}}}")));
        assertEquals(EnumSet.of(TestStyle.SPECIFICATION),
                Classifier.styles(json("{\"tests\":{\"byEngine\":{\"kotest\":7}}}")));
    }

    @Test
    void engineWithoutTestsShowsNoStyle() throws IOException {
        assertEquals(EnumSet.noneOf(TestStyle.class),
                Classifier.styles(json("{\"tests\":{\"byEngine\":{\"junit-jupiter\":4,\"jqwik\":0}}}")));
    }

    @Test
    void testShapesShowTheirStyles() throws IOException {
        assertEquals(EnumSet.of(TestStyle.PARAMETERIZED, TestStyle.NESTED),
                Classifier.styles(json("{\"tests\":{\"byEngine\":{\"junit-jupiter\":9},"
                        + "\"style\":{\"parameterized\":6,\"dynamic\":0,\"nested\":2}}}")));
        assertEquals(EnumSet.of(TestStyle.DYNAMIC),
                Classifier.styles(json("{\"tests\":{\"style\":{\"parameterized\":0,\"dynamic\":3,\"nested\":0}}}")));
    }

    @Test
    void noTestsSectionShowsNoStyle() throws IOException {
        assertEquals(EnumSet.noneOf(TestStyle.class), Classifier.styles(null));
        assertEquals(EnumSet.noneOf(TestStyle.class), Classifier.styles(json("{\"probeErrors\":[]}")));
    }

    @Test
    void stylesDoNotChangeTheLevel() throws IOException {
        assertEquals(Level.TESTS_PASS_CLEANLY, Classifier.classify(json(
                "{\"tests\":{\"byEngine\":{\"jqwik\":2},\"succeeded\":2,\"failed\":0,\"skipped\":0,"
                        + "\"style\":{\"parameterized\":2}},\"probeErrors\":[]}")));
    }

    private JsonNode json(String content) throws IOException {
        return mapper.readTree(content);
    }
}
