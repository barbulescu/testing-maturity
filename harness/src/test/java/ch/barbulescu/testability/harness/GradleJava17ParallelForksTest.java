package ch.barbulescu.testability.harness;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code maxParallelForks = 2} means two separate test JVMs - each with
 * its own {@code ProbeRecorder} instance (one per classloader) - so the
 * probe must produce two reports, not one, both for the same module but
 * with distinct {@code workerId}s and disjoint slices of the four tests.
 */
class GradleJava17ParallelForksTest {

    private static final String EXAMPLE_NAME = "gradle-java17-parallel-forks";
    private static final String IMAGE = "gradle:9.5.1-jdk17";
    private static final String WORKDIR = "/work";
    private static final String PROBE_REPO_CONTAINER_PATH = "/probe/repo";
    private static final String PROBE_REPORT_DIR = WORKDIR + "/probe-report";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void probeRunProducesOneReportPerFork() throws Exception {
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
                "two parallel forks must produce two reports:\n" + probeRun.log);

        JsonNode first = objectMapper.readTree(probeRun.reports.get(0));
        JsonNode second = objectMapper.readTree(probeRun.reports.get(1));

        for (JsonNode report : new JsonNode[] {first, second}) {
            assertEquals(1, report.get("schemaVersion").asInt());
            assertEquals("GRADLE", report.get("buildTool").asText());
            assertEquals(WORKDIR, report.get("moduleDir").asText());
            assertEquals(0, report.get("tests").get("failed").asInt());
            assertEquals(0, report.get("probeErrors").size());
        }

        assertNotEquals(first.get("workerId").asText(), second.get("workerId").asText(),
                "each fork is a separate JVM and must get its own workerId");

        int totalSucceeded = first.get("tests").get("succeeded").asInt() + second.get("tests").get("succeeded").asInt();
        assertEquals(4, totalSucceeded, "the four tests must be split, not duplicated, across the two forks");
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
