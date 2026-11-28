package ch.barbulescu.testability.harness;

import ch.barbulescu.testability.classifier.Classifier;
import ch.barbulescu.testability.classifier.Level;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Surefire's {@code rerunFailingTestsCount} reruns a failed test in a second
 * test plan in the same JVM, and JUnit Pioneer and rerunner-jupiter retry a
 * failed attempt within the test. Each flaky test fails once and then passes,
 * so the build succeeds: proves the probe counts all three as flaky, and
 * writes the report again after the rerun's plan. rerunner-jupiter reports
 * the repetitions it no longer needs as skipped.
 */
class MavenJava17SurefireRerunTest {

    private static final String EXAMPLE_NAME = "maven-java17-surefire-rerun";
    private static final String IMAGE = "maven:3.9.6-eclipse-temurin-17";
    private static final String WORKDIR = "/work";
    private static final String PROBE_REPO_CONTAINER_PATH = "/probe/repo";
    private static final String PROBE_REPORT_DIR = WORKDIR + "/probe-report";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void probeRunMatchesBaselineAndProducesExpectedReport() throws Exception {
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
        assertEquals(1, probeRun.reports.size(),
                "probe run must produce exactly one report:\n" + probeRun.log);

        JsonNode expected = objectMapper.readTree(exampleDir.resolve("expected.json").toFile());
        JsonNode report = objectMapper.readTree(probeRun.reports.get(0));
        JsonSubset.assertSubset(expected, report, "$");

        // The build passes, but the failed attempts are in the report: not a clean run.
        assertEquals(Level.TESTS_RUN, Classifier.classify(report),
                "expected flaky tests to keep the run from counting as clean:\n" + report);
    }

    private String mavenCommand(boolean withProbe) {
        StringBuilder args = new StringBuilder("mvn -B test");
        if (withProbe) {
            args.append(" -Dtestability.probe.repo=file://").append(PROBE_REPO_CONTAINER_PATH)
                    .append(" -Dtestability.probe.groupId=").append(System.getProperty("testability.harness.probeGroupId"))
                    .append(" -Dtestability.probe.artifactId=").append(System.getProperty("testability.harness.probeArtifactId"))
                    .append(" -Dtestability.probe.version=").append(System.getProperty("testability.harness.probeVersion"));
        }
        return args.toString();
    }
}
