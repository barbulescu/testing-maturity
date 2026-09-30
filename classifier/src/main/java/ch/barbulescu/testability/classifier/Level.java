package ch.barbulescu.testability.classifier;

public enum Level {

    NO_VISIBILITY(0, "no report produced - module isn't measurable by the probe"),
    TESTS_RUN(1, "tests exist and ran, but the run wasn't clean (a failure, or a probe error)"),
    TESTS_PASS_CLEANLY(2, "tests ran, all passed, and the probe reported no errors of its own"),
    INTEGRATION_AWARE(3, "level 2, plus evidence of real integration testing "
            + "(Testcontainers, WireMock, or a booted Spring context)");

    private final int number;
    private final String description;

    Level(int number, String description) {
        this.number = number;
        this.description = description;
    }

    public int number() {
        return number;
    }

    public String description() {
        return description;
    }
}
