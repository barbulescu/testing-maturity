package ch.barbulescu.testability.probe.junit;

import io.github.artsok.RepeatedIfExceptionsTest;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junitpioneer.jupiter.RetryingTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RepetitionKindTest {

    // Abstract, so JUnit doesn't run the fixture's methods as tests of their own.
    abstract static class Fixture {

        @RetryingTest(3)
        void pioneerRetry() {
        }

        @RepeatedIfExceptionsTest(repeats = 3)
        void rerunnerRetry() {
        }

        @RepeatedTest(2)
        void repeated() {
        }

        @RepeatedTest(2)
        void overloaded(RepetitionInfo info) {
        }

        @ParameterizedTest
        void overloaded(String[] values) {
        }
    }

    abstract static class Subclass extends Fixture {
    }

    @org.junit.jupiter.api.Test
    void retryingAnnotationsOfPioneerAndRerunner() {
        assertEquals(RepetitionKind.RETRYING, RepetitionKind.of(Fixture.class, "pioneerRetry", ""));
        assertEquals(RepetitionKind.RETRYING, RepetitionKind.of(Fixture.class, "rerunnerRetry", ""));
    }

    @org.junit.jupiter.api.Test
    void repeatedTest() {
        assertEquals(RepetitionKind.REPEATED, RepetitionKind.of(Fixture.class, "repeated", ""));
    }

    @org.junit.jupiter.api.Test
    void overloadsAreToldApartByTheirParameterTypes() {
        assertEquals(RepetitionKind.REPEATED,
                RepetitionKind.of(Fixture.class, "overloaded", "org.junit.jupiter.api.RepetitionInfo"));
        assertEquals(RepetitionKind.NONE, RepetitionKind.of(Fixture.class, "overloaded", "java.lang.String[]"));
        assertEquals(RepetitionKind.NONE, RepetitionKind.of(Fixture.class, "overloaded", "[Ljava.lang.String;"));
    }

    @org.junit.jupiter.api.Test
    void findsInheritedMethods() {
        assertEquals(RepetitionKind.REPEATED, RepetitionKind.of(Subclass.class, "repeated", ""));
    }

    @org.junit.jupiter.api.Test
    void unknownMethodIsNone() {
        assertEquals(RepetitionKind.NONE, RepetitionKind.of(Fixture.class, "noSuchMethod", ""));
    }
}
