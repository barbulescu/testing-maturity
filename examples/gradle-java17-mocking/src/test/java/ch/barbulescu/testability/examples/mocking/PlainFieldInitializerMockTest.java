package ch.barbulescu.testability.examples.mocking;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The mock is created by a field initializer. JUnit creates a new instance of the class for
 * each test, before the test's start event, so each test has one mock of its own.
 */
class PlainFieldInitializerMockTest {

    private final TaxRates taxRates = Mockito.mock(TaxRates.class);

    @Test
    void unstubbedMockReturnsZero() {
        assertEquals(100, new PriceCalculator(taxRates).grossCents(100, "CH"));
    }

    @Test
    void unstubbedMockReturnsZeroForAnotherCountry() {
        assertEquals(100, new PriceCalculator(taxRates).grossCents(100, "DE"));
    }

    @Test
    void unstubbedMockReturnsZeroForZeroAmount() {
        assertEquals(0, new PriceCalculator(taxRates).grossCents(0, "CH"));
    }
}
