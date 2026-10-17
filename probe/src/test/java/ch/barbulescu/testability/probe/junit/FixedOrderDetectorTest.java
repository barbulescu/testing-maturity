package ch.barbulescu.testability.probe.junit;

import org.junit.FixMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.runners.MethodSorters;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FixedOrderDetectorTest {

    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    static class OrderAnnotationFixture {
    }

    @TestMethodOrder(MethodOrderer.MethodName.class)
    static class MethodNameFixture {
    }

    @TestMethodOrder(MethodOrderer.Random.class)
    static class RandomFixture {
    }

    @FixMethodOrder(MethodSorters.NAME_ASCENDING)
    static class JUnit4NameAscendingFixture {
    }

    @FixMethodOrder(MethodSorters.JVM)
    static class JUnit4JvmFixture {
    }

    static class InheritingFixture extends OrderAnnotationFixture {
    }

    static class PlainFixture {
    }

    @Test
    void deterministicJupiterOrderersFixTheOrder() {
        assertTrue(FixedOrderDetector.hasFixedOrder(OrderAnnotationFixture.class));
        assertTrue(FixedOrderDetector.hasFixedOrder(MethodNameFixture.class));
    }

    @Test
    void randomOrderDoesNotFixTheOrder() {
        assertFalse(FixedOrderDetector.hasFixedOrder(RandomFixture.class));
    }

    @Test
    void junit4NameAscendingFixesTheOrderButJvmDoesNot() {
        assertTrue(FixedOrderDetector.hasFixedOrder(JUnit4NameAscendingFixture.class));
        assertFalse(FixedOrderDetector.hasFixedOrder(JUnit4JvmFixture.class));
    }

    @Test
    void orderInheritedFromBaseClassCounts() {
        assertTrue(FixedOrderDetector.hasFixedOrder(InheritingFixture.class));
    }

    @Test
    void classWithoutOrderAnnotation() {
        assertFalse(FixedOrderDetector.hasFixedOrder(PlainFixture.class));
    }
}
