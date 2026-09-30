package ch.barbulescu.testability.probe.core;

/**
 * Best-effort: presence of a build tool's own forked-worker class is a
 * stronger signal than the command line, which is checked only as a
 * fallback.
 */
public final class BuildTool {

    private BuildTool() {
    }

    public static String detect() {
        return detect(System.getProperty("sun.java.command"), ProbeRecorder.class.getClassLoader());
    }

    static String detect(String javaCommand, ClassLoader loader) {
        if (isOnClasspath(loader, "org.apache.maven.surefire.booter.ForkedBooter")) {
            return "MAVEN";
        }
        if (isOnClasspath(loader, "worker.org.gradle.process.internal.worker.GradleWorkerMain")) {
            return "GRADLE";
        }
        if (javaCommand != null) {
            if (javaCommand.contains("surefire")) {
                return "MAVEN";
            }
            if (javaCommand.contains("gradle")) {
                return "GRADLE";
            }
        }
        return "UNKNOWN";
    }

    private static boolean isOnClasspath(ClassLoader loader, String className) {
        try {
            Class.forName(className, false, loader);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
