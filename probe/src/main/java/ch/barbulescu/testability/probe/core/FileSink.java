package ch.barbulescu.testability.probe.core;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Writes the report to a file under the configured output directory.
 * Resolution order: {@code -Dtestability.probe.dir}, then env
 * {@code TESTABILITY_PROBE_DIR}, then the JVM temp dir.
 */
public final class FileSink implements ReportSink {

    @Override
    public void write(String fileName, String json) throws IOException {
        Path dir = resolveOutputDir();
        Files.createDirectories(dir);
        Path file = dir.resolve(fileName);
        try (Writer writer = new OutputStreamWriter(Files.newOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write(json);
        }
    }

    private Path resolveOutputDir() {
        String prop = System.getProperty("testability.probe.dir");
        if (prop != null && !prop.isEmpty()) {
            return Paths.get(prop);
        }
        String env = System.getenv("TESTABILITY_PROBE_DIR");
        if (env != null && !env.isEmpty()) {
            return Paths.get(env);
        }
        return Paths.get(System.getProperty("java.io.tmpdir"));
    }
}
