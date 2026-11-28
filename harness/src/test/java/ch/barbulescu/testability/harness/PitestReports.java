package ch.barbulescu.testability.harness;

import ch.barbulescu.testability.classifier.Classifier;
import ch.barbulescu.testability.classifier.Level;
import ch.barbulescu.testability.classifier.TestStyle;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the PIT examples of both build tools assert about a build that runs the tests, then
 * PIT: the build tool's test JVM writes the one unmarked report, and PIT's minions write
 * reports of their own for the same module, marked {@code "runner": "PITEST"}.
 */
final class PitestReports {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private PitestReports() {
    }

    static void assertMinionReportsMarked(BuildResult probeRun, JsonNode expected) throws IOException {
        List<JsonNode> testTaskReports = new ArrayList<>();
        List<JsonNode> minionReports = new ArrayList<>();
        Set<String> workerIds = new HashSet<>();
        Set<String> moduleDirs = new HashSet<>();
        for (String raw : probeRun.reports) {
            JsonNode report = OBJECT_MAPPER.readTree(raw);
            (Classifier.isMutationRun(report) ? minionReports : testTaskReports).add(report);
            workerIds.add(report.get("workerId").asText());
            moduleDirs.add(report.get("moduleDir").asText());
            assertEquals(0, report.get("probeErrors").size(), "probe errors in:\n" + report);
        }
        assertEquals(probeRun.reports.size(), workerIds.size(), "every JVM writes its own report");
        assertEquals(1, moduleDirs.size(), "all reports are for the one module: " + moduleDirs);

        assertEquals(1, testTaskReports.size(), "the test JVM writes the one unmarked report:\n" + probeRun.log);
        JsonNode testTask = testTaskReports.get(0);
        JsonSubset.assertSubset(expected, testTask, "$");
        assertEquals(Level.TESTS_PASS_CLEANLY, Classifier.classify(testTask),
                "the tests pass, so killed mutants mustn't lower the level:\n" + testTask);
        assertFalse(Classifier.styles(testTask).contains(TestStyle.MUTATION_TESTING));

        // The coverage minion and at least one minion that runs the tests against mutants.
        assertTrue(minionReports.size() >= 2, "expected PIT's minions to write reports:\n" + probeRun.log);
        int killedMutants = 0;
        for (JsonNode minion : minionReports) {
            assertEquals("PITEST", minion.get("runner").asText());
            assertTrue(Classifier.styles(minion).contains(TestStyle.MUTATION_TESTING));
            killedMutants += minion.get("tests").get("failed").asInt();
        }
        assertTrue(killedMutants > 0, "killed mutants show up as failed tests in the minions' reports");
    }
}
