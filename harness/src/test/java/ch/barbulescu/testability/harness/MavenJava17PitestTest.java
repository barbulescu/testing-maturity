package ch.barbulescu.testability.harness;

import ch.barbulescu.testability.classifier.Classifier;
import ch.barbulescu.testability.classifier.Level;
import ch.barbulescu.testability.classifier.TestStyle;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PIT runs the tests again in minion JVMs of its own, through pitest-junit5-plugin, and the
 * probe's listener fires there too: one minion for the coverage run, and more that run the
 * tests against mutated code. Each writes its own report for the same module, and a mutant
 * the tests kill shows up as a failed test. Proves those reports are marked
 * {@code "runner": "PITEST"}, so they don't count as the module's test run, while Surefire's
 * report stays as it is.
 */
class MavenJava17PitestTest {

    private static final String EXAMPLE_NAME = "maven-java17-pitest";
    private static final String IMAGE = "maven:3.9.6-eclipse-temurin-17";
    private static final String WORKDIR = "/work";
    private static final String PROBE_REPO_CONTAINER_PATH = "/probe/repo";
    private static final String PROBE_REPORT_DIR = WORKDIR + "/probe-report";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void pitMinionReportsAreMarkedAndSurefireReportIsUnchanged() throws Exception {
        Path examplesDir = Paths.get(System.getProperty("testability.harness.examplesDir"));
        Path exampleDir = examplesDir.resolve(EXAMPLE_NAME);
        assertTrue(Files.isDirectory(exampleDir), "example project not found: " + exampleDir);

        BuildResult baseline = ContainerBuildRunner.run(IMAGE, exampleDir, WORKDIR, PROBE_REPORT_DIR,
                null, PROBE_REPO_CONTAINER_PATH, mavenCommand(false));
        assertEquals(0, baseline.exitCode, "baseline build must succeed:\n" + baseline.log);
        assertTrue(baseline.reports.isEmpty(), "baseline run must not produce any probe report");

        BuildResult probeRun = ContainerBuildRunner.run(IMAGE, exampleDir, WORKDIR, PROBE_REPORT_DIR,
                System.getProperty("testability.harness.probeRepoDir"), PROBE_REPO_CONTAINER_PATH, mavenCommand(true));
        assertEquals(baseline.exitCode, probeRun.exitCode,
                "probe run must exit with the same status as baseline:\n" + probeRun.log);

        List<JsonNode> surefireReports = new ArrayList<>();
        List<JsonNode> minionReports = new ArrayList<>();
        Set<String> workerIds = new HashSet<>();
        Set<String> moduleDirs = new HashSet<>();
        for (String raw : probeRun.reports) {
            JsonNode report = objectMapper.readTree(raw);
            (Classifier.isMutationRun(report) ? minionReports : surefireReports).add(report);
            workerIds.add(report.get("workerId").asText());
            moduleDirs.add(report.get("moduleDir").asText());
            assertEquals(0, report.get("probeErrors").size(), "probe errors in:\n" + report);
        }
        assertEquals(probeRun.reports.size(), workerIds.size(), "every JVM writes its own report");
        assertEquals(1, moduleDirs.size(), "all reports are for the one module: " + moduleDirs);

        assertEquals(1, surefireReports.size(), "Surefire's JVM writes the one unmarked report:\n" + probeRun.log);
        JsonNode surefire = surefireReports.get(0);
        JsonNode expected = objectMapper.readTree(exampleDir.resolve("expected.json").toFile());
        JsonSubset.assertSubset(expected, surefire, "$");
        assertEquals(Level.TESTS_PASS_CLEANLY, Classifier.classify(surefire),
                "the tests pass, so killed mutants mustn't lower the level:\n" + surefire);
        assertFalse(Classifier.styles(surefire).contains(TestStyle.MUTATION_TESTING));

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

    private String mavenCommand(boolean withProbe) {
        StringBuilder args = new StringBuilder("mvn -B test org.pitest:pitest-maven:mutationCoverage");
        if (withProbe) {
            args.append(" -Dtestability.probe.repo=file://").append(PROBE_REPO_CONTAINER_PATH)
                    .append(" -Dtestability.probe.groupId=").append(System.getProperty("testability.harness.probeGroupId"))
                    .append(" -Dtestability.probe.artifactId=").append(System.getProperty("testability.harness.probeArtifactId"))
                    .append(" -Dtestability.probe.version=").append(System.getProperty("testability.harness.probeVersion"));
        }
        return args.toString();
    }
}
