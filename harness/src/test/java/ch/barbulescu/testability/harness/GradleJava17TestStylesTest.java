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
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test styles read from unique IDs and engines: parameterized, repeated,
 * dynamic and nested Jupiter tests, jqwik properties and an ArchUnit rule,
 * next to tags (Jupiter's {@code @Tag}, JUnit 4's {@code @Category}) and time
 * limits (Jupiter's {@code @Timeout}, JUnit 4's timeout rule and
 * {@code @Test(timeout)}).
 */
class GradleJava17TestStylesTest {

    private static final String EXAMPLE_NAME = "gradle-java17-test-styles";
    private static final String IMAGE = "gradle:9.5.1-jdk17";
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

        assertEquals(Level.TESTS_PASS_CLEANLY, Classifier.classify(report), "styles must not change the level:\n" + report);
        assertEquals(EnumSet.of(TestStyle.PROPERTY_BASED, TestStyle.ARCHITECTURE_RULES,
                        TestStyle.PARAMETERIZED, TestStyle.DYNAMIC, TestStyle.NESTED),
                Classifier.styles(report), "classifier must read the styles from the report:\n" + report);
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
