package ch.barbulescu.testability.probe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProbeVersionTest {

    @Test
    void readsTheVersionTheBuildFilledIn() {
        String version = ProbeVersion.read();

        assertTrue(version.matches("\\d+\\.\\d+\\.\\d+(-SNAPSHOT)?"),
                "expected the build's version in probe.properties, got " + version);
    }
}
