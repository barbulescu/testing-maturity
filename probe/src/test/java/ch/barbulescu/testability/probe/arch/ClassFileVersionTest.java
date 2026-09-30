package ch.barbulescu.testability.probe.arch;

import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Belt-and-suspenders on top of {@code options.release = 8} in the build
 * script: reads each compiled main class file's own major version
 * directly from its bytes, so this still catches a regression even if
 * that build configuration were ever accidentally loosened or removed.
 */
class ClassFileVersionTest {

    private static final int CLASS_MAGIC = 0xCAFEBABE;
    private static final int JAVA_8_MAJOR_VERSION = 52;

    @Test
    void everyMainClassIsCompiledForJava8() throws IOException, URISyntaxException {
        Path classesDir = mainClassesDir();
        assertTrue(Files.isDirectory(classesDir), "main classes directory not found: " + classesDir);

        List<Path> classFiles;
        try (Stream<Path> walk = Files.walk(classesDir)) {
            classFiles = walk.filter(path -> path.toString().endsWith(".class")).collect(Collectors.toList());
        }
        assertFalse(classFiles.isEmpty(), "no compiled class files found under " + classesDir);

        for (Path classFile : classFiles) {
            assertEquals(JAVA_8_MAJOR_VERSION, majorVersionOf(classFile), classFile + " was not compiled for Java 8");
        }
    }

    private Path mainClassesDir() throws URISyntaxException {
        // This test class's own compiled location is .../build/classes/java/test/...;
        // main classes sit in the sibling "main" directory.
        URL testClassLocation = ClassFileVersionTest.class.getProtectionDomain().getCodeSource().getLocation();
        Path testClasses = Paths.get(testClassLocation.toURI());
        return testClasses.getParent().resolve("main");
    }

    private int majorVersionOf(Path classFile) throws IOException {
        try (InputStream in = Files.newInputStream(classFile);
                DataInputStream data = new DataInputStream(in)) {
            int magic = data.readInt();
            assertEquals(CLASS_MAGIC, magic, classFile + " is not a valid class file");
            data.readUnsignedShort(); // minor version, unused
            return data.readUnsignedShort();
        }
    }
}
