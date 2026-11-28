package ch.barbulescu.testability.examples.failuretypes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** A failing assumption aborts the test: neither a failure nor a pass. */
class AssumptionTest {

    @Test
    void needsAnEnvironmentThatIsNotThere() {
        assumeTrue(System.getenv("NO_SUCH_VARIABLE") != null, "only where NO_SUCH_VARIABLE is set");
    }

    @Test
    void passes() {
    }
}
