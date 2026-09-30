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
 * Runs the {@code gradle-java21-boot4-junit6} example twice inside a
 * container matching its JDK - once unmodified (baseline) and once with
 * the probe attached - and checks that the probe changes nothing about the
 * build's outcome while producing a report matching the example's
 * {@code expected.json}.
 */
class GradleJava21Boot4Junit6Test {

    private static final String EXAMPLE_NAME = "gradle-java21-boot4-junit6";
    private static final String IMAGE = "gradle:9.5.1-jdk21";
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
                null, PROBE_REPO_CONTAINER_PATH, gradleCommand(false));
        assertEquals(0, baseline.exitCode, "baseline build must succeed:\n" + baseline.log);
        assertTrue(baseline.reports.isEmpty(), "baseline run must not produce any probe report");

        BuildResult probeRun = ContainerBuildRunner.run(IMAGE, exampleDir, WORKDIR, PROBE_REPORT_DIR,
                System.getProperty("testability.harness.probeRepoDir"), PROBE_REPO_CONTAINER_PATH, gradleCommand(true));
        assertEquals(baseline.exitCode, probeRun.exitCode,
                "probe run must exit with the same status as baseline:\n" + probeRun.log);
        assertEquals(1, probeRun.reports.size(),
                "probe run must produce exactly one report:\n" + probeRun.log);

        JsonNode expected = objectMapper.readTree(exampleDir.resolve("expected.json").toFile());
        JsonNode report = objectMapper.readTree(probeRun.reports.get(0));
        JsonSubset.assertSubset(expected, report, "$");

        // A generous smoke bound, not a tight perf gate: container wall-clock time is noisy
        // (image pulls, host CPU contention), so this exists to catch a pathological
        // regression (the probe doing something O(n) per test, hanging, etc.), not to
        // measure real overhead precisely.
        long maxAcceptableMillis = Math.max(baseline.elapsedMillis * 4, baseline.elapsedMillis + 30_000);
        assertTrue(probeRun.elapsedMillis <= maxAcceptableMillis,
                "probe run took " + probeRun.elapsedMillis + "ms vs baseline " + baseline.elapsedMillis
                        + "ms - overhead looks pathological, not just noise");

        // A clean pass with a real @SpringBootTest is the reference classifier's
        // textbook case for "integration aware".
        assertEquals(Level.INTEGRATION_AWARE, Classifier.classify(report),
                "expected a booted Spring context to read as integration-aware:\n" + report);
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
