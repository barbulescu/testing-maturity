package ch.barbulescu.testability.classifier;

import com.fasterxml.jackson.databind.JsonNode;

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
        if (succeeded + failed + skipped == 0) {
            return Level.NO_VISIBILITY;
        }

        boolean probeHadErrors = report.path("probeErrors").size() > 0;
        // succeeded == 0 also lands here: if everything ran was skipped, that's not a
        // clean pass, it's a module that didn't really get exercised.
        if (failed > 0 || probeHadErrors || succeeded == 0) {
            return Level.TESTS_RUN;
        }

        if (isIntegrationAware(report)) {
            return Level.INTEGRATION_AWARE;
        }

        return Level.TESTS_PASS_CLEANLY;
    }

    private static boolean isIntegrationAware(JsonNode report) {
        JsonNode onClasspath = report.path("onClasspath");
        if (onClasspath.path("testcontainers").asBoolean(false) || onClasspath.path("wiremock").asBoolean(false)) {
            return true;
        }
        return report.path("spring").path("bootTestClasses").asInt(0) > 0;
    }
}
