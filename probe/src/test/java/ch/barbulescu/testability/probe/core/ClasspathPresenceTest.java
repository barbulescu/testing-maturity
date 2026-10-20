package ch.barbulescu.testability.probe.core;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClasspathPresenceTest {

    @Test
    void detectsWhatIsActuallyOnThisTestClasspath() {
        Map<String, Boolean> presence = ClasspathPresence.detect(getClass().getClassLoader());

        // junit:junit, mockito-core and spring-boot-test are real test dependencies of the
        // probe module itself, added for the spring package's own fixture-based tests.
        assertTrue(presence.get("junit4"));
        // Surefire adds the Vintage engine to the test classpath because junit:junit is there; Gradle doesn't.
        boolean maven = "MAVEN".equals(System.getProperty("testability.probe.test.buildTool"));
        assertEquals(maven, presence.get("vintage"));
        assertFalse(presence.get("testcontainers"));
        assertFalse(presence.get("wiremock"));
        assertTrue(presence.get("mockito"));
        assertFalse(presence.get("mockk"));
        assertTrue(presence.get("springBoot"));
    }

    @Test
    void reportsJUnitPlatformVersionWhenPresent() {
        Map<String, String> versions = ClasspathPresence.versions(getClass().getClassLoader());

        // The JUnit Platform launcher is what's driving this very test run.
        assertTrue(versions.containsKey("junitPlatform"));
    }
}
