package ch.barbulescu.testability.harness;

import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Runs one example project's build inside a container, build-tool-agnostic:
 * callers supply the shell command (e.g. {@code "gradle test ..."} or
 * {@code "mvn test ..."}) and this copies the example in, runs it, and
 * captures both the exit code and any report files written under the
 * probe's output directory - all through container log markers, so no
 * file-copy-back API is needed.
 */
final class ContainerBuildRunner {

    private static final String REPORT_BEGIN_MARKER = "TESTABILITY_REPORT_BEGIN";
    private static final String REPORT_END_MARKER = "TESTABILITY_REPORT_END";
    private static final String EXIT_CODE_MARKER = "TESTABILITY_EXIT_CODE=";
    private static final String CACHE_CONTAINER_PATH = "/cache";
    private static final AtomicBoolean probeEvictedFromCache = new AtomicBoolean(false);

    private ContainerBuildRunner() {
    }

    static BuildResult run(String image, Path exampleDir, String workdir, String probeReportContainerDir,
            String probeRepoDir, String probeRepoContainerPath, String buildCommand) throws IOException {
        // The container's own exit code is always 0: OneShotStartupCheckStrategy treats a
        // non-zero exit as a failed container *startup* and retries/throws, which would
        // wrongly fail examples like context-fails whose build is SUPPOSED to fail. The real
        // build outcome is captured via the TESTABILITY_EXIT_CODE marker instead, parsed below.
        String script = "cd " + workdir + " && " + buildCommand + "; code=$?; "
                + "echo " + EXIT_CODE_MARKER + "$code; "
                + "if [ -d " + probeReportContainerDir + " ]; then "
                + "for f in " + probeReportContainerDir + "/*.json; do "
                + "echo " + REPORT_BEGIN_MARKER + "; cat \"$f\"; echo; echo " + REPORT_END_MARKER + "; "
                + "done; fi; exit 0";

        try (GenericContainer<?> container = new GenericContainer<>(image)) {
            container.withCopyFileToContainer(MountableFile.forHostPath(exampleDir), workdir)
                    .withCommand("sh", "-c", script)
                    .withStartupCheckStrategy(new OneShotStartupCheckStrategy().withTimeout(Duration.ofMinutes(10)))
                    .withEnv("TESTABILITY_PROBE_DIR", probeReportContainerDir);
            mountDependencyCache(container);

            if (probeRepoDir != null) {
                container.withCopyFileToContainer(
                        MountableFile.forHostPath(Paths.get(probeRepoDir)), probeRepoContainerPath);
            }

            long startedAt = System.currentTimeMillis();
            container.start();
            long elapsedMillis = System.currentTimeMillis() - startedAt;

            String log = container.getLogs();
            return new BuildResult(parseExitCode(log), parseReports(log), log, elapsedMillis);
        }
    }

    /**
     * Shares one host directory between all example builds, so dependencies are downloaded
     * once instead of once per container (CI also keeps it between runs). Gradle, Maven and
     * the Maven wrapper are each pointed into it through their own environment variable.
     */
    private static void mountDependencyCache(GenericContainer<?> container) throws IOException {
        String cacheDirProperty = System.getProperty("testability.harness.cacheDir");
        if (cacheDirProperty == null || cacheDirProperty.isEmpty()) {
            return;
        }
        Path cacheDir = Paths.get(cacheDirProperty).toAbsolutePath();
        for (String subDir : new String[] {"gradle", "m2"}) {
            createWorldWritable(cacheDir.resolve(subDir));
        }
        if (probeEvictedFromCache.compareAndSet(false, true)) {
            evictProbe(cacheDir);
        }
        container.withFileSystemBind(cacheDir.toString(), CACHE_CONTAINER_PATH, BindMode.READ_WRITE)
                .withEnv("GRADLE_USER_HOME", CACHE_CONTAINER_PATH + "/gradle")
                .withEnv("MAVEN_USER_HOME", CACHE_CONTAINER_PATH + "/m2")
                .withEnv("MAVEN_OPTS", "-Dmaven.repo.local=" + CACHE_CONTAINER_PATH + "/m2/repository");
    }

    /** The image may build as a user other than the host's, so it must be able to write here. */
    private static void createWorldWritable(Path dir) throws IOException {
        Files.createDirectories(dir);
        try {
            Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwxrwxrwx"));
        } catch (UnsupportedOperationException | IOException e) {
            // not a POSIX file system, or owned by the container's user from an earlier run
        }
    }

    /**
     * Maven installs the probe snapshot into its local repository and checks for a newer one
     * only once a day, so a cached copy could stand in for the probe under test. Gradle never
     * caches artifacts from file repositories, so only Maven's copy is removed.
     */
    private static void evictProbe(Path cacheDir) throws IOException {
        Path probeDir = cacheDir.resolve("m2/repository")
                .resolve(System.getProperty("testability.harness.probeGroupId").replace('.', '/'))
                .resolve(System.getProperty("testability.harness.probeArtifactId"));
        if (!Files.exists(probeDir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(probeDir)) {
            List<Path> deepestFirst = paths.sorted(Comparator.reverseOrder()).collect(Collectors.toList());
            for (Path path : deepestFirst) {
                Files.delete(path);
            }
        }
    }

    private static int parseExitCode(String log) {
        for (String line : log.split("\\R")) {
            if (line.startsWith(EXIT_CODE_MARKER)) {
                return Integer.parseInt(line.substring(EXIT_CODE_MARKER.length()).trim());
            }
        }
        throw new IllegalStateException("no exit code marker found in container log:\n" + log);
    }

    private static List<String> parseReports(String log) {
        List<String> reports = new ArrayList<>();
        StringBuilder current = null;
        for (String line : log.split("\\R")) {
            if (line.contains(REPORT_BEGIN_MARKER)) {
                current = new StringBuilder();
            } else if (line.contains(REPORT_END_MARKER)) {
                if (current != null) {
                    reports.add(current.toString().trim());
                    current = null;
                }
            } else if (current != null) {
                current.append(line);
            }
        }
        return reports;
    }
}
