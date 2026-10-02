package ch.barbulescu.testability.examples.mocking;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/** Only one of the two tests creates a mock, so only that one counts. */
class PlainInlineMockTest {

    @Test
    void withInlineMock() {
        TaxRates taxRates = Mockito.mock(TaxRates.class);
        when(taxRates.percentFor("CH")).thenReturn(0);

        assertEquals(100, new PriceCalculator(taxRates).grossCents(100, "CH"));
    }

    @Test
    void withoutMock() {
        assertEquals(108, new PriceCalculator(new FixedTaxRates()).grossCents(100, "CH"));
    }
}
