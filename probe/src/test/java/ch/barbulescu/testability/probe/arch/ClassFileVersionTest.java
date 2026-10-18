package ch.barbulescu.testability.probe.arch;

import ch.barbulescu.testability.probe.core.Safe;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Belt-and-suspenders on top of {@code options.release = 8} in the build
 * scripts: reads each compiled main class file's own major version
 * directly from its bytes, so this still catches a regression even if
 * that build configuration were ever accidentally loosened or removed.
 */
class ClassFileVersionTest {

    private static final int CLASS_MAGIC = 0xCAFEBABE;
    private static final int JAVA_8_MAJOR_VERSION = 52;

    @Test
    void everyMainClassIsCompiledForJava8() throws IOException, URISyntaxException {
        Path mainClasses = mainClassesLocation();
        Map<String, Integer> majorVersions = Files.isDirectory(mainClasses)
                ? majorVersionsInDirectory(mainClasses)
                : majorVersionsInJar(mainClasses);
        assertFalse(majorVersions.isEmpty(), "no compiled class files found under " + mainClasses);

        majorVersions.forEach((classFile, majorVersion) ->
                assertEquals(JAVA_8_MAJOR_VERSION, majorVersion, classFile + " was not compiled for Java 8"));
    }

    /**
     * Where the probe's main classes were loaded from: Gradle's classes directory, or with Maven
     * the probe module's classes directory or jar.
     */
    private Path mainClassesLocation() throws URISyntaxException {
        URL location = Safe.class.getProtectionDomain().getCodeSource().getLocation();
        return Paths.get(location.toURI());
    }

    private Map<String, Integer> majorVersionsInDirectory(Path classesDir) throws IOException {
        Map<String, Integer> majorVersions = new TreeMap<>();
        try (Stream<Path> walk = Files.walk(classesDir)) {
            for (Path classFile : walk.filter(path -> path.toString().endsWith(".class")).collect(Collectors.toList())) {
                try (InputStream in = Files.newInputStream(classFile)) {
                    majorVersions.put(classFile.toString(), majorVersionOf(classFile.toString(), in));
                }
            }
        }
        return majorVersions;
    }

    private Map<String, Integer> majorVersionsInJar(Path jar) throws IOException {
        Map<String, Integer> majorVersions = new TreeMap<>();
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            for (JarEntry entry : Collections.list(jarFile.entries())) {
                if (entry.getName().endsWith(".class")) {
                    try (InputStream in = jarFile.getInputStream(entry)) {
                        majorVersions.put(entry.getName(), majorVersionOf(entry.getName(), in));
                    }
                }
            }
        }
        return majorVersions;
    }

    private int majorVersionOf(String classFile, InputStream in) throws IOException {
        DataInputStream data = new DataInputStream(in);
        int magic = data.readInt();
        assertEquals(CLASS_MAGIC, magic, classFile + " is not a valid class file");
        data.readUnsignedShort(); // minor version, unused
        return data.readUnsignedShort();
    }
}
