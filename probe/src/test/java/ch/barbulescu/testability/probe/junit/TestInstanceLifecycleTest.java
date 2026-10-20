package ch.barbulescu.testability.probe.junit;

import org.junit.jupiter.api.TestInstance;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherConfig;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestInstanceLifecycleTest {

    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    static class PerClass {
    }

    static class InheritsPerClass extends PerClass {
    }

    @TestInstance(TestInstance.Lifecycle.PER_METHOD)
    static class PerMethod {
    }

    static class NoAnnotation {
    }

    @Test
    void annotationOnTheClassOrASuperclassDecides() {
        assertTrue(TestInstanceLifecycle.isPerClass(PerClass.class, null));
        assertTrue(TestInstanceLifecycle.isPerClass(InheritsPerClass.class, null));
        assertFalse(TestInstanceLifecycle.isPerClass(PerMethod.class, null));
        assertFalse(TestInstanceLifecycle.isPerClass(NoAnnotation.class, null));
        assertFalse(TestInstanceLifecycle.isPerClass(null, null));
    }

    @Test
    void defaultLifecycleComesFromTheConfiguration() {
        assertTrue(TestInstanceLifecycle.isPerClass(NoAnnotation.class, planWithDefault("per_class")));
        assertFalse(TestInstanceLifecycle.isPerClass(PerMethod.class, planWithDefault("per_class")));
        assertFalse(TestInstanceLifecycle.isPerClass(NoAnnotation.class, planWithDefault("per_method")));
    }

    private static TestPlan planWithDefault(String lifecycle) {
        return LauncherFactory.create(LauncherConfig.builder().enableTestExecutionListenerAutoRegistration(false).build())
                .discover(LauncherDiscoveryRequestBuilder.request()
                        .configurationParameter("junit.jupiter.testinstance.lifecycle.default", lifecycle)
                        .build());
    }
}
