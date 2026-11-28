package ch.barbulescu.testability.probe.junit;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeoutDetectorTest {

    @Timeout(5)
    static class JupiterTimeoutFixture {
    }

    static class InheritingFixture extends JupiterTimeoutFixture {
    }

    static class TimeoutRuleFixture {
        @Rule
        public org.junit.rules.Timeout timeout = org.junit.rules.Timeout.seconds(5);
    }

    static class InheritedTimeoutRuleFixture extends TimeoutRuleFixture {
    }

    static class TimeoutClassRuleFixture {
        @ClassRule
        public static org.junit.rules.Timeout timeout = org.junit.rules.Timeout.seconds(5);
    }

    static class UnannotatedTimeoutFieldFixture {
        org.junit.rules.Timeout timeout = org.junit.rules.Timeout.seconds(5);
    }

    static class MethodsFixture {
        @Timeout(1)
        void jupiterTimeout() {
        }

        @org.junit.Test(timeout = 1000)
        public void junit4Timeout() {
        }

        @org.junit.Test
        public void junit4WithoutTimeout() {
        }

        @Test
        void plain() {
        }
    }

    @Test
    void jupiterTimeoutOnAClassIsSeenAlsoWhenInherited() {
        assertTrue(TimeoutDetector.hasTimeout(JupiterTimeoutFixture.class));
        assertTrue(TimeoutDetector.hasTimeout(InheritingFixture.class));
    }

    @Test
    void junit4TimeoutRuleIsSeenAlsoInABaseClass() {
        assertTrue(TimeoutDetector.hasTimeout(TimeoutRuleFixture.class));
        assertTrue(TimeoutDetector.hasTimeout(InheritedTimeoutRuleFixture.class));
        assertTrue(TimeoutDetector.hasTimeout(TimeoutClassRuleFixture.class));
    }

    @Test
    void timeoutFieldThatIsNoRuleDoesNotCount() {
        assertFalse(TimeoutDetector.hasTimeout(UnannotatedTimeoutFieldFixture.class));
        assertFalse(TimeoutDetector.hasTimeout(MethodsFixture.class));
    }

    @Test
    void timeoutsOnMethodsAreSeen() throws NoSuchMethodException {
        assertTrue(TimeoutDetector.hasTimeout(method("jupiterTimeout")));
        assertTrue(TimeoutDetector.hasTimeout(method("junit4Timeout")));
    }

    @Test
    void methodsWithoutTimeoutDoNotCount() throws NoSuchMethodException {
        assertFalse(TimeoutDetector.hasTimeout(method("junit4WithoutTimeout")));
        assertFalse(TimeoutDetector.hasTimeout(method("plain")));
    }

    private static Method method(String name) throws NoSuchMethodException {
        return MethodsFixture.class.getDeclaredMethod(name);
    }
}
