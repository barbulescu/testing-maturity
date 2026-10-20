package ch.barbulescu.testability.examples.mocking;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * One mock created in {@code @BeforeAll} and shared by both tests (class level). The second
 * test also creates two mocks of its own.
 */
class PlainBeforeAllMockTest {

    private static TaxRates sharedTaxRates;

    @BeforeAll
    static void createSharedMock() {
        sharedTaxRates = Mockito.mock(TaxRates.class);
    }

    @Test
    void usesOnlyTheSharedMock() {
        assertEquals(100, new PriceCalculator(sharedTaxRates).grossCents(100, "CH"));
    }

    @Test
    void alsoCreatesItsOwnMocks() {
        TaxRates swiss = Mockito.mock(TaxRates.class);
        TaxRates german = Mockito.mock(TaxRates.class);
        when(swiss.percentFor("CH")).thenReturn(8);
        when(german.percentFor("DE")).thenReturn(19);

        assertEquals(108, new PriceCalculator(swiss).grossCents(100, "CH"));
        assertEquals(119, new PriceCalculator(german).grossCents(100, "DE"));
        assertEquals(100, new PriceCalculator(sharedTaxRates).grossCents(100, "DE"));
    }
}
