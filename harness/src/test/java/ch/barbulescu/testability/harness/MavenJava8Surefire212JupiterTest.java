package ch.barbulescu.testability.harness;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Documented blind spot: Surefire 2.12.4 predates the JUnit Platform
 * provider (introduced in 2.22.0), so it falls back to its JUnit 3
 * provider, finds no {@code junit.framework.TestCase} subclasses, and
 * silently reports "Tests run: 0" with a successful build - the Jupiter
 * test never runs, and the probe's JUnit Platform listener is never
 * invoked either. This requires a static check on the Surefire version,
 * not something the probe can detect at runtime.
 */
class MavenJava8Surefire212JupiterTest {

    private static final String EXAMPLE_NAME = "maven-java8-surefire-2.12-jupiter";
    // The official maven:3.6.3-jdk-8 image predates multi-arch (arm64) builds, so this
    // uses a plain multi-arch JDK 8 image and the example's own ./mvnw wrapper instead -
    // Maven itself is pure Java, so only the JDK image's architecture matters here.
    private static final String IMAGE = "eclipse-temurin:8-jdk";
    private static final String WORKDIR = "/work";
    private static final String PROBE_REPO_CONTAINER_PATH = "/probe/repo";
    private static final String PROBE_REPORT_DIR = WORKDIR + "/probe-report";

    @Test
    void probeRunProducesNoReportEitherWay() throws Exception {
        Path examplesDir = Paths.get(System.getProperty("testability.harness.examplesDir"));
        Path exampleDir = examplesDir.resolve(EXAMPLE_NAME);
        assertTrue(Files.isDirectory(exampleDir), "example project not found: " + exampleDir);

        BuildResult baseline = ContainerBuildRunner.run(IMAGE, exampleDir, WORKDIR, PROBE_REPORT_DIR,
                null, PROBE_REPO_CONTAINER_PATH, mavenCommand(false));
        assertEquals(0, baseline.exitCode, "baseline build must succeed (even though it silently runs zero tests):\n" + baseline.log);
        assertTrue(baseline.reports.isEmpty(), "baseline run must not produce any probe report");

        BuildResult probeRun = ContainerBuildRunner.run(IMAGE, exampleDir, WORKDIR, PROBE_REPORT_DIR,
                System.getProperty("testability.harness.probeRepoDir"), PROBE_REPO_CONTAINER_PATH, mavenCommand(true));
        assertEquals(baseline.exitCode, probeRun.exitCode,
                "probe run must exit with the same status as baseline:\n" + probeRun.log);
        assertTrue(probeRun.reports.isEmpty(),
                "an old Surefire that never invokes the JUnit Platform must not produce a report:\n" + probeRun.log);
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
