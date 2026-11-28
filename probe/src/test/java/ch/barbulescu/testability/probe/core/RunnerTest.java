package ch.barbulescu.testability.probe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RunnerTest {

    @Test
    void detectsPitMinionsByTheirMainClass() {
        assertEquals("PITEST", Runner.detect("org.pitest.coverage.execute.CoverageMinion 41235 2"));
        assertEquals("PITEST", Runner.detect("org.pitest.mutationtest.execute.MutationTestMinion 41236 3"));
    }

    @Test
    void anOrdinaryTestJvmHasNoRunner() {
        assertNull(Runner.detect("worker.org.gradle.process.internal.worker.GradleWorkerMain 'Gradle Test Executor 1'"));
        assertNull(Runner.detect("/tmp/surefire/surefirebooter.jar /tmp/surefire 2026 1"));
        // Only the main class counts, not an argument that names a minion.
        assertNull(Runner.detect("com.example.Main org.pitest.mutationtest.execute.MutationTestMinion"));
        assertNull(Runner.detect(null));
    }

    @Test
    void theseTestsRunOutsidePit() {
        assertNull(Runner.detect());
    }
}
