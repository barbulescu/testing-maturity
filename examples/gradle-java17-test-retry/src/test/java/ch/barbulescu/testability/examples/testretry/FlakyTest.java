package ch.barbulescu.testability.examples.testretry;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Fails the first time it runs in the build and passes after that. A file remembers the
 * first run, because the retry runs in another JVM, where static state starts over.
 */
class FlakyTest {

    @Test
    void passesOnRetry() throws Exception {
        Path marker = Paths.get(System.getProperty("flakyMarker"));
        if (Files.notExists(marker)) {
            Files.createDirectories(marker.getParent());
            Files.createFile(marker);
            fail("first run fails");
        }
    }

    @Test
    void stable() {
    }
}
