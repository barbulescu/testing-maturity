package ch.barbulescu.testability.examples.mocking;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The mock is created by a field initializer, which runs before the test
 * method's start event - the probe attributes it to the class instead.
 */
class PlainFieldInitializerMockTest {

    private final TaxRates taxRates = Mockito.mock(TaxRates.class);

    @Test
    void unstubbedMockReturnsZero() {
        assertEquals(100, new PriceCalculator(taxRates).grossCents(100, "CH"));
    }
}
