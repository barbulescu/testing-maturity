package ch.barbulescu.testability.harness;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A blind spot: Gradle's test-retry plugin reruns a failed test in a new test
 * JVM, so no single JVM sees the test both fail and pass. There are two
 * reports, one with the failure and one with the pass, and neither counts a
 * flaky test. Only joining reports of the same build could find it.
 */
class GradleJava17TestRetryTest {

    private static final String EXAMPLE_NAME = "gradle-java17-test-retry";
    private static final String IMAGE = "gradle:9.5.1-jdk17";
    private static final String WORKDIR = "/work";
    private static final String PROBE_REPO_CONTAINER_PATH = "/probe/repo";
    private static final String PROBE_REPORT_DIR = WORKDIR + "/probe-report";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void retryInANewJvmProducesASecondReportAndNoFlakyTest() throws Exception {
        Path examplesDir = Paths.get(System.getProperty("testability.harness.examplesDir"));
        Path exampleDir = examplesDir.resolve(EXAMPLE_NAME);
        assertTrue(Files.isDirectory(exampleDir), "example project not found: " + exampleDir);

        BuildResult baseline = ContainerBuildRunner.run(IMAGE, exampleDir, WORKDIR, PROBE_REPORT_DIR,
                null, PROBE_REPO_CONTAINER_PATH, gradleCommand(false));
        assertEquals(0, baseline.exitCode, "baseline build must succeed:\n" + baseline.log);
        assertTrue(baseline.reports.isEmpty(), "baseline run must not produce any probe report");

        BuildResult probeRun = ContainerBuildRunner.run(IMAGE, exampleDir, WORKDIR, PROBE_REPORT_DIR,
                System.getProperty("testability.harness.probeRepoDir"), PROBE_REPO_CONTAINER_PATH, gradleCommand(true));
        assertEquals(baseline.exitCode, probeRun.exitCode,
                "probe run must exit with the same status as baseline:\n" + probeRun.log);
        assertEquals(2, probeRun.reports.size(),
                "the first run and the retry are separate JVMs, so two reports:\n" + probeRun.log);

        List<JsonNode> reports = new ArrayList<>();
        for (String report : probeRun.reports) {
            reports.add(objectMapper.readTree(report));
        }
        // The first run has both tests, the retry only the one that failed.
        reports.sort(Comparator.comparingInt((JsonNode report) -> report.get("tests").get("byEngine").get("junit-jupiter").asInt()).reversed());
        JsonNode firstRun = reports.get(0);
        JsonNode retry = reports.get(1);

        assertNotEquals(firstRun.get("workerId").asText(), retry.get("workerId").asText());
        assertEquals(1, firstRun.get("tests").get("succeeded").asInt());
        assertEquals(1, firstRun.get("tests").get("failed").asInt());
        assertEquals(1, retry.get("tests").get("succeeded").asInt());
        assertEquals(0, retry.get("tests").get("failed").asInt());
        for (JsonNode report : reports) {
            assertEquals(0, report.get("tests").get("flakyTests").asInt());
            assertEquals(0, report.get("probeErrors").size());
        }
    }

    private String gradleCommand(boolean withProbe) {
        StringBuilder args = new StringBuilder("test --console=plain --no-daemon");
        if (withProbe) {
            args.append(" -Ptestability.probe.repo=file://").append(PROBE_REPO_CONTAINER_PATH)
                    .append(" -Ptestability.probe.groupId=").append(System.getProperty("testability.harness.probeGroupId"))
                    .append(" -Ptestability.probe.artifactId=").append(System.getProperty("testability.harness.probeArtifactId"))
                    .append(" -Ptestability.probe.version=").append(System.getProperty("testability.harness.probeVersion"));
        }
        return "gradle " + args;
    }
}
