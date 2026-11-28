package ch.barbulescu.testability.probe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CoverageToolTest {

    @Test
    void detectsTheJaCoCoAgentOfThisTestRun() {
        // Gradle and Maven both run these tests with JaCoCo's agent attached and say so;
        // an IDE run without coverage doesn't.
        boolean jacoco = Boolean.getBoolean("testability.probe.test.jacoco");

        assertEquals(jacoco, CoverageTool.detect().get("jacoco"));
    }

    @Test
    void reportsNoJaCoCoWhenNeitherLoaderCanSeeIt() {
        ClassLoader bootstrapOnly = new ClassLoader(null) {
        };

        assertFalse(CoverageTool.detect(bootstrapOnly, bootstrapOnly).get("jacoco"));
    }
}
