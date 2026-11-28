package ch.barbulescu.testability.probe.junit;

import org.junit.jupiter.api.Test;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherConfig;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParallelExecutionTest {

    private static final String ENABLED = "junit.jupiter.execution.parallel.enabled";
    private static final String MODE_DEFAULT = "junit.jupiter.execution.parallel.mode.default";

    @Test
    void enabledIsReadLikeJupiterReadsIt() {
        assertTrue(ParallelExecution.isEnabled(planWith(Collections.singletonMap(ENABLED, "true"))));
        assertTrue(ParallelExecution.isEnabled(planWith(Collections.singletonMap(ENABLED, " TRUE "))));
        assertFalse(ParallelExecution.isEnabled(planWith(Collections.singletonMap(ENABLED, "false"))));
        assertFalse(ParallelExecution.isEnabled(planWith(Collections.<String, String>emptyMap())));
        assertFalse(ParallelExecution.isEnabled(null));
    }

    @Test
    void modeDefaultIsTrimmedAndLowerCased() {
        assertEquals("concurrent", ParallelExecution.modeDefault(planWith(Collections.singletonMap(MODE_DEFAULT, " CONCURRENT"))));
        assertEquals("same_thread", ParallelExecution.modeDefault(planWith(Collections.singletonMap(MODE_DEFAULT, "same_thread"))));
        assertNull(ParallelExecution.modeDefault(planWith(Collections.<String, String>emptyMap())));
        assertNull(ParallelExecution.modeDefault(null));
    }

    private static TestPlan planWith(Map<String, String> parameters) {
        return LauncherFactory.create(LauncherConfig.builder().enableTestExecutionListenerAutoRegistration(false).build())
                .discover(LauncherDiscoveryRequestBuilder.request()
                        .configurationParameters(parameters)
                        .build());
    }
}
