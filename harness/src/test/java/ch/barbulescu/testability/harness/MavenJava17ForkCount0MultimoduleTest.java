package ch.barbulescu.testability.harness;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With {@code forkCount=0}, every module's tests run in the same JVM as
 * the Maven build itself - so a naive "one JVM, one report" assumption
 * would collapse both modules into a single report. Surefire sets the
 * {@code basedir} system property fresh for each module's execution even
 * without forking, which is what lets {@code moduleDir} correctly tell the
 * two modules' reports apart.
 */
class MavenJava17ForkCount0MultimoduleTest {

    private static final String EXAMPLE_NAME = "maven-java17-forkcount0-multimodule";
    private static final String IMAGE = "maven:3.9.6-eclipse-temurin-17";
    private static final String WORKDIR = "/work";
    private static final String PROBE_REPO_CONTAINER_PATH = "/probe/repo";
    private static final String PROBE_REPORT_DIR = WORKDIR + "/probe-report";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void probeRunProducesOneReportPerModule() throws Exception {
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
        assertEquals(2, probeRun.reports.size(),
                "one report per module is expected, even with forkCount=0:\n" + probeRun.log);

        Map<String, JsonNode> byModuleDir = new HashMap<>();
        for (String reportJson : probeRun.reports) {
            JsonNode report = objectMapper.readTree(reportJson);
            byModuleDir.put(report.get("moduleDir").asText(), report);
        }

        JsonNode moduleA = byModuleDir.get(WORKDIR + "/module-a");
        JsonNode moduleB = byModuleDir.get(WORKDIR + "/module-b");
        assertTrue(moduleA != null, "expected a report for module-a among: " + byModuleDir.keySet());
        assertTrue(moduleB != null, "expected a report for module-b among: " + byModuleDir.keySet());

        assertEquals("MAVEN", moduleA.get("buildTool").asText());
        assertEquals("MAVEN", moduleB.get("buildTool").asText());
        assertEquals(2, moduleA.get("tests").get("succeeded").asInt());
        assertEquals(1, moduleB.get("tests").get("succeeded").asInt());
        assertEquals(0, moduleA.get("probeErrors").size());
        assertEquals(0, moduleB.get("probeErrors").size());
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
