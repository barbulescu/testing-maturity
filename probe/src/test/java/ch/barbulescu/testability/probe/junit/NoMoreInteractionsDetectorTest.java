package ch.barbulescu.testability.probe.junit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The fixtures are only read, never run: their methods carry no {@code @Test}, so no engine
 * picks them up, and their lifecycle methods never run either.
 */
class NoMoreInteractionsDetectorTest {

    private final NoMoreInteractionsDetector detector = new NoMoreInteractionsDetector();

    @SuppressWarnings("unused")
    static class DirectCallsFixture {
        Runnable mock;
        InOrder inOrder;

        void verifiesOnly() {
            Mockito.verify(mock).run();
        }

        void noMoreInteractions() {
            Mockito.verifyNoMoreInteractions(mock);
        }

        void noInteractions() {
            Mockito.verifyNoInteractions(mock);
        }

        void inOrderNoMoreInteractions() {
            inOrder.verifyNoMoreInteractions();
        }

        void bddNoMoreInteractions() {
            BDDMockito.then(mock).shouldHaveNoMoreInteractions();
        }

        void throughHelper() {
            checkMocks();
        }

        void throughStaticHelper() {
            checkMocksOf(mock);
        }

        void inLambda() {
            Runnable later = () -> Mockito.verifyNoInteractions(mock);
            later.run();
        }

        void recursesWithoutPinning(int depth) {
            if (depth > 0) {
                recursesWithoutPinning(depth - 1);
            }
        }

        private void checkMocks() {
            Mockito.verifyNoMoreInteractions(mock);
        }

        private static void checkMocksOf(Object mock) {
            Mockito.verifyNoMoreInteractions(mock);
        }
    }

    @SuppressWarnings("unused")
    static class AfterEachFixture {
        Runnable mock;

        void anyTest() {
        }

        @AfterEach
        void tearDown() {
            Mockito.verifyNoMoreInteractions(mock);
        }
    }

    @SuppressWarnings("unused")
    static class BeforeEachOnlyVerifiesFixture {
        Runnable mock;

        void anyTest() {
        }

        @BeforeEach
        void setUp() {
            Mockito.verify(mock).run();
        }
    }

    @SuppressWarnings("unused")
    static class JUnit4AfterFixture {
        Runnable mock;

        public void anyTest() {
        }

        @org.junit.After
        public void tearDown() {
            Mockito.verifyNoInteractions(mock);
        }
    }

    @SuppressWarnings("unused")
    static class BaseFixture {
        Runnable mock;

        @AfterEach
        void tearDown() {
            check();
        }

        void check() {
        }
    }

    @SuppressWarnings("unused")
    static class OverridingFixture extends BaseFixture {
        void anyTest() {
        }

        @Override
        void check() {
            Mockito.verifyNoMoreInteractions(mock);
        }
    }

    @SuppressWarnings("unused")
    static class EnclosingFixture {
        Runnable mock;

        @AfterEach
        void tearDown() {
            Mockito.verifyNoMoreInteractions(mock);
        }

        // Like a @Nested class, which would make Jupiter run the fixture.
        class Inner {
            void anyTest() {
            }
        }

        static class StaticNested {
            void anyTest() {
            }
        }
    }

    @Test
    void verifyAloneDoesNotPinInteractions() {
        assertFalse(scans(DirectCallsFixture.class, "verifiesOnly"));
        assertFalse(scans(BeforeEachOnlyVerifiesFixture.class, "anyTest"));
    }

    @Test
    void everyFormOfNoMoreInteractionsIsFound() {
        assertTrue(scans(DirectCallsFixture.class, "noMoreInteractions"));
        assertTrue(scans(DirectCallsFixture.class, "noInteractions"));
        assertTrue(scans(DirectCallsFixture.class, "inOrderNoMoreInteractions"));
        assertTrue(scans(DirectCallsFixture.class, "bddNoMoreInteractions"));
    }

    @Test
    void callsAreFollowedIntoHelpersAndLambdasOfTheClass() {
        assertTrue(scans(DirectCallsFixture.class, "throughHelper"));
        assertTrue(scans(DirectCallsFixture.class, "throughStaticHelper"));
        assertTrue(scans(DirectCallsFixture.class, "inLambda"));
    }

    @Test
    void recursionEnds() {
        assertFalse(scans(DirectCallsFixture.class, "recursesWithoutPinning"));
    }

    @Test
    void lifecycleMethodsCountForEveryTest() {
        assertTrue(scans(AfterEachFixture.class, "anyTest"));
        assertTrue(scans(JUnit4AfterFixture.class, "anyTest"));
    }

    @Test
    void aBaseClassCallIsResolvedOnTheTestClass() {
        assertTrue(scans(OverridingFixture.class, "anyTest"));
    }

    @Test
    void anEnclosingClassCountsForANestedClassOnly() {
        assertTrue(scans(EnclosingFixture.Inner.class, "anyTest"));
        assertFalse(scans(EnclosingFixture.StaticNested.class, "anyTest"));
    }

    @Test
    void descriptorsMatchTheBytecode() throws NoSuchMethodException {
        assertEquals("(I)V", NoMoreInteractionsDetector.descriptorOf(
                DirectCallsFixture.class.getDeclaredMethod("recursesWithoutPinning", int.class)));
        assertEquals("([Ljava/lang/String;JZ)[[D", NoMoreInteractionsDetector.descriptorOf(
                Signatures.class.getDeclaredMethod("mixed", String[].class, long.class, boolean.class)));
    }

    @SuppressWarnings("unused")
    static class Signatures {
        double[][] mixed(String[] names, long count, boolean flag) {
            return null;
        }
    }

    private boolean scans(Class<?> testClass, String methodName) {
        for (Method method : testClass.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                return detector.verifiesNoMoreInteractions(testClass, method);
            }
        }
        throw new AssertionError("no method " + methodName + " in " + testClass);
    }
}
