package ch.barbulescu.testability.probe.core;

/**
 * What drives the tests in this JVM, when it isn't the build tool's own test task.
 * PIT's minions ({@code org.pitest.coverage.execute.CoverageMinion} and
 * {@code org.pitest.mutationtest.execute.MutationTestMinion}) run the tests through JUnit
 * Platform, so the probe's listener fires there too, once in a JVM for the coverage run and
 * again in each JVM that runs tests against mutated code. Their main class is on the command
 * line; PIT's jars aren't checked, since a project may have them on its test classpath.
 */
public final class Runner {

    private Runner() {
    }

    /** {@code "PITEST"} in a PIT minion, {@code null} in an ordinary test JVM. */
    public static String detect() {
        return detect(System.getProperty("sun.java.command"));
    }

    static String detect(String javaCommand) {
        if (javaCommand != null && javaCommand.trim().startsWith("org.pitest.")) {
            return "PITEST";
        }
        return null;
    }
}
