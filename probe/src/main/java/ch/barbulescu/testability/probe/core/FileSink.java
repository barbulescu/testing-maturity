package ch.barbulescu.testability.probe.core;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Writes the report to a file under the configured output directory.
 * Resolution order: {@code -Dtestability.probe.dir}, then env
 * {@code TESTABILITY_PROBE_DIR}, then the JVM temp dir.
 * <p>
 * The report is rewritten whenever a test plan finishes, so it's written to a temporary
 * file first and then moved over the old one: a JVM killed mid-write leaves the previous
 * report intact rather than a truncated one. The temporary name doesn't end in
 * {@code .json}, so collectors that pick up {@code *.json} never see it.
 */
public final class FileSink implements ReportSink {

    @Override
    public void write(String fileName, String json) throws IOException {
        Path dir = resolveOutputDir();
        Files.createDirectories(dir);
        Path file = dir.resolve(fileName);
        Path temporary = dir.resolve(fileName + ".tmp");
        try (Writer writer = new OutputStreamWriter(Files.newOutputStream(temporary), StandardCharsets.UTF_8)) {
            writer.write(json);
        }
        try {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
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
