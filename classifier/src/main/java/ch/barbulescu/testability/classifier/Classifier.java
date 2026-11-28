package ch.barbulescu.testability.classifier;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.EnumSet;
import java.util.Set;

/**
 * A REFERENCE implementation only. The whole point of keeping level rules
 * out of the probe itself (see the probe's own package docs) is that an
 * organization can swap this for its own - these specific thresholds are
 * illustrative, not canonical. This classifier only ever looks at ONE
 * report; it has nothing to say about the (probe-invisible) denominator
 * of modules that never produced a report at all - that comparison has to
 * happen one level up, against a service list.
 */
public final class Classifier {

    private Classifier() {
    }

    /**
     * Whether the report comes from one of PIT's minion JVMs. Those run the tests against
     * mutated code, so their failed tests are killed mutants, not broken tests: such a report
     * shows that the module runs mutation testing and says nothing about its level.
     * {@link #classify} doesn't tell these apart, so callers check first.
     */
    public static boolean isMutationRun(JsonNode report) {
        return report != null && "PITEST".equals(report.path("runner").asText(null));
    }

    public static Level classify(JsonNode report) {
        if (report == null) {
            return Level.NO_VISIBILITY;
        }

        JsonNode tests = report.get("tests");
        if (tests == null) {
            return Level.NO_VISIBILITY;
        }

        int succeeded = tests.path("succeeded").asInt(0);
        int failed = tests.path("failed").asInt(0);
        int skipped = tests.path("skipped").asInt(0);
        int aborted = tests.path("aborted").asInt(0);
        if (succeeded + failed + skipped + aborted == 0) {
            return Level.NO_VISIBILITY;
        }

        boolean probeHadErrors = report.path("probeErrors").size() > 0;
        // A flaky test that passed in the end isn't a clean pass either. Retrying extensions
        // report the attempts that failed as aborted, so "failed" alone may not show it.
        boolean flaky = tests.path("flakyTests").asInt(0) > 0;
        // succeeded == 0 also lands here: if everything ran was skipped, that's not a
        // clean pass, it's a module that didn't really get exercised.
        if (failed > 0 || flaky || probeHadErrors || succeeded == 0) {
            return Level.TESTS_RUN;
        }

        if (isIntegrationAware(report)) {
            return Level.INTEGRATION_AWARE;
        }

        return Level.TESTS_PASS_CLEANLY;
    }

    private static boolean isIntegrationAware(JsonNode report) {
        JsonNode onClasspath = report.path("onClasspath");
        // Testcontainers on the classpath is often inherited from a parent POM and never used,
        // so only a test that made it talk to Docker counts.
        if (report.path("used").path("testcontainers").asBoolean(false)
                || onClasspath.path("wiremock").asBoolean(false)) {
            return true;
        }
        return report.path("spring").path("bootTestClasses").asInt(0) > 0;
    }

    /**
     * The test styles a report shows: an engine that ran at least one test, a PIT minion's
     * report, or a non-zero count in {@code tests.style}. They say how a module tests, not how well, so they don't
     * change its level.
     */
    public static Set<TestStyle> styles(JsonNode report) {
        Set<TestStyle> styles = EnumSet.noneOf(TestStyle.class);
        if (isMutationRun(report)) {
            styles.add(TestStyle.MUTATION_TESTING);
        }
        JsonNode tests = report == null ? null : report.get("tests");
        if (tests == null) {
            return styles;
        }
        JsonNode byEngine = tests.path("byEngine");
        if (byEngine.path("jqwik").asInt(0) > 0) {
            styles.add(TestStyle.PROPERTY_BASED);
        }
        if (byEngine.path("archunit").asInt(0) > 0) {
            styles.add(TestStyle.ARCHITECTURE_RULES);
        }
        if (byEngine.path("cucumber").asInt(0) > 0) {
            styles.add(TestStyle.BDD);
        }
        if (byEngine.path("spock").asInt(0) > 0 || byEngine.path("kotest").asInt(0) > 0) {
            styles.add(TestStyle.SPECIFICATION);
        }
        JsonNode style = tests.path("style");
        if (style.path("parameterized").asInt(0) > 0) {
            styles.add(TestStyle.PARAMETERIZED);
        }
        if (style.path("dynamic").asInt(0) > 0) {
            styles.add(TestStyle.DYNAMIC);
        }
        if (style.path("nested").asInt(0) > 0) {
            styles.add(TestStyle.NESTED);
        }
        return styles;
    }
}
