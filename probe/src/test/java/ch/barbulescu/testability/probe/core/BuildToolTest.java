package ch.barbulescu.testability.probe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuildToolTest {

    @Test
    void fallsBackToJavaCommandWhenNoWorkerClassOnClasspath() {
        ClassLoader isolatedLoader = new ClassLoader(null) {
        };

        assertEquals("MAVEN", BuildTool.detect("org.apache.maven.surefire.booter.ForkedBooter", isolatedLoader));
        assertEquals("GRADLE", BuildTool.detect("/path/to/gradle-launcher.jar", isolatedLoader));
        assertEquals("UNKNOWN", BuildTool.detect("com.example.SomeMain", isolatedLoader));
    }

    @Test
    void detectsGradleFromTheWorkerClassOnThisRealTestClasspath() {
        // These tests genuinely run inside a forked Gradle test worker, so this exercises
        // the real classpath-presence branch rather than the command-line fallback.
        assertEquals("GRADLE", BuildTool.detect(null, getClass().getClassLoader()));
    }
}
