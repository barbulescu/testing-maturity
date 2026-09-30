package ch.barbulescu.testability.harness;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Documented blind spot: {@code tasks.test { useJUnit() }} runs tests
 * through Gradle's native JUnit 4 support, never through the JUnit
 * Platform Launcher - so the probe's {@code META-INF/services}-registered
 * listener is never invoked at all, regardless of the probe being on the
 * classpath. A static check (looking for {@code useJUnitPlatform()} in the
 * build) is required to cover this case.
 */
class GradleJava8JUnit4PlainTest {

    private static final String EXAMPLE_NAME = "gradle-java8-junit4-plain";
    // Gradle 9's daemon can't run on JDK 8 itself; the example's settings.gradle.kts
    // applies the foojay-resolver-convention plugin so its own Java 8 toolchain is
    // auto-provisioned inside the container.
    private static final String IMAGE = "gradle:9.5.1-jdk21";
    private static final String WORKDIR = "/work";
    private static final String PROBE_REPO_CONTAINER_PATH = "/probe/repo";
    private static final String PROBE_REPORT_DIR = WORKDIR + "/probe-report";

    @Test
    void probeRunProducesNoReportEitherWay() throws Exception {
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
        assertTrue(probeRun.reports.isEmpty(),
                "useJUnit() never invokes the platform launcher, so no report can be produced:\n" + probeRun.log);
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
