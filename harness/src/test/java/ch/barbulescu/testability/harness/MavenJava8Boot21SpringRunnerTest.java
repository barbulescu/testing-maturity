package ch.barbulescu.testability.harness;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Classic {@code @RunWith(SpringRunner.class)} + JUnit 4 under Boot 2.1:
 * Surefire uses its native JUnit 4 provider, so the JUnit Platform
 * Launcher never runs and the probe's JUnit adapter never fires - proving
 * {@code tests} (and {@code versions.junitPlatform}) are absent. Yet the
 * Spring adapter attaches independently via {@code TestContextManager},
 * and it's specifically responsible for registering the shutdown hook
 * that flushes the report in this scenario, since nothing else does.
 */
class MavenJava8Boot21SpringRunnerTest {

    private static final String EXAMPLE_NAME = "maven-java8-boot21-springrunner";
    // maven:3.6.3-jdk-8 predates arm64 images; use a multi-arch JDK 8 image and this
    // example's own ./mvnw wrapper instead (Maven itself is pure Java).
    private static final String IMAGE = "eclipse-temurin:8-jdk";
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
                "probe run must produce exactly one report (via the shutdown-hook flush):\n" + probeRun.log);

        JsonNode expected = objectMapper.readTree(exampleDir.resolve("expected.json").toFile());
        JsonNode report = objectMapper.readTree(probeRun.reports.get(0));
        JsonSubset.assertSubset(expected, report, "$");

        assertNull(report.get("tests"), "tests must be absent: JUnit Platform never ran:\n" + probeRun.log);
    }

    private String mavenCommand(boolean withProbe) {
        StringBuilder args = new StringBuilder("./mvnw -B test");
        if (withProbe) {
            args.append(" -Dtestability.probe.repo=file://").append(PROBE_REPO_CONTAINER_PATH)
                    .append(" -Dtestability.probe.groupId=").append(System.getProperty("testability.harness.probeGroupId"))
                    .append(" -Dtestability.probe.artifactId=").append(System.getProperty("testability.harness.probeArtifactId"))
                    .append(" -Dtestability.probe.version=").append(System.getProperty("testability.harness.probeVersion"));
        }
        return args.toString();
    }
}
