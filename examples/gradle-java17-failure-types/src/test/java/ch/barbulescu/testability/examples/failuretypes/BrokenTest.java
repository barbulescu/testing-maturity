package ch.barbulescu.testability.examples.failuretypes;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Tests that broke before they could check anything: the test or its environment is at fault. */
class BrokenTest {

    private String name;

    @Test
    void nullPointer() {
        assertEquals(4, name.length());
    }

    @Test
    void missingFile() throws IOException {
        assertEquals("x", Files.readString(Paths.get("/no/such/file")));
    }
}
