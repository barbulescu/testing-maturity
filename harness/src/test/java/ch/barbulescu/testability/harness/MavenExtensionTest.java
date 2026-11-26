package ch.barbulescu.testability.harness;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Builds Maven examples with the probe added only by the Maven extension, the way a central
 * CI job adds it: no profile, no POM change, just {@code -Dmaven.ext.class.path}. The probe
 * resolves from a repository that a settings file adds, as a company mirror would provide it.
 */
class MavenExtensionTest {

    private static final String WORKDIR = "/work";
    private static final String PROBE_REPO_CONTAINER_PATH = "/probe/repo";
    private static final String EXTENSION_CONTAINER_PATH = "/probe/testability-probe-maven-extension.jar";
    private static final String PROBE_REPORT_DIR = WORKDIR + "/probe-report";
    private static final String SETTINGS = "/tmp/probe-settings.xml";

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Maven 3.9 running every module's tests in its own JVM (forkCount=0): one report per jar module. */
    @Test
    void addsTheProbeToEveryModuleOfAReactor() throws Exception {
        BuildResult run = build("maven-java17-forkcount0-multimodule", "maven:3.9.6-eclipse-temurin-17", "mvn");

        assertEquals(0, run.exitCode, "build must succeed:\n" + run.log);
        assertTrue(run.log.contains("Testability probe " + probeVersion() + " added to 2 of 3 projects"),
                "the extension must add the probe to both jar modules and skip the parent:\n" + run.log);
        assertEquals(2, run.reports.size(), "one report per module is expected:\n" + run.log);

        Map<String, JsonNode> byModuleDir = new HashMap<>();
        for (String reportJson : run.reports) {
            JsonNode report = objectMapper.readTree(reportJson);
            byModuleDir.put(report.get("moduleDir").asText(), report);
        }
        for (JsonNode report : byModuleDir.values()) {
            assertEquals(probeVersion(), report.get("probeVersion").asText(), "the report must state the probe's own version");
        }
        assertEquals(2, byModuleDir.get(WORKDIR + "/module-a").get("tests").get("succeeded").asInt());
        assertEquals(1, byModuleDir.get(WORKDIR + "/module-b").get("tests").get("succeeded").asInt());
    }

    /** The oldest supported setup: Maven 3.6.3 on Java 8, and the report the probe profile also gives. */
    @Test
    void worksOnMaven363AndJava8() throws Exception {
        String example = "maven-java8-boot21-springrunner";
        BuildResult run = build(example, "eclipse-temurin:8-jdk", "./mvnw");

        assertEquals(0, run.exitCode, "build must succeed:\n" + run.log);
        assertTrue(run.log.contains("Testability probe " + probeVersion() + " added to 1 of 1 projects"),
                "the extension must add the probe:\n" + run.log);
        assertEquals(1, run.reports.size(), "exactly one report is expected:\n" + run.log);
        Path expectedFile = examplesDir().resolve(example).resolve("expected.json");
        JsonSubset.assertSubset(objectMapper.readTree(expectedFile.toFile()),
                objectMapper.readTree(run.reports.get(0)), "$");
    }

    private BuildResult build(String example, String image, String maven) throws Exception {
        Path exampleDir = examplesDir().resolve(example);
        assertTrue(Files.isDirectory(exampleDir), "example project not found: " + exampleDir);
        Path extensionJar = Paths.get(System.getProperty("testability.harness.extensionJar"));
        assertTrue(Files.isRegularFile(extensionJar), "extension jar not built: " + extensionJar);

        String settings = "<settings><profiles><profile><id>probe</id><repositories><repository>"
                + "<id>probe</id><url>file://" + PROBE_REPO_CONTAINER_PATH + "</url>"
                + "</repository></repositories></profile></profiles>"
                + "<activeProfiles><activeProfile>probe</activeProfile></activeProfiles></settings>";
        String command = "printf '%s' '" + settings + "' > " + SETTINGS
                + " && " + maven + " -B -s " + SETTINGS + " test -Dmaven.ext.class.path=" + EXTENSION_CONTAINER_PATH;

        return ContainerBuildRunner.run(image, exampleDir, WORKDIR, PROBE_REPORT_DIR,
                System.getProperty("testability.harness.probeRepoDir"), PROBE_REPO_CONTAINER_PATH, command,
                Collections.singletonMap(extensionJar, EXTENSION_CONTAINER_PATH));
    }

    private static Path examplesDir() {
        return Paths.get(System.getProperty("testability.harness.examplesDir"));
    }

    private static String probeVersion() {
        return System.getProperty("testability.harness.probeVersion");
    }
}
