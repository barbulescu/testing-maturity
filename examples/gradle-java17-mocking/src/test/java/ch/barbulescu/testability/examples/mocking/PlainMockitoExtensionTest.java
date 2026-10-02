package ch.barbulescu.testability.examples.mocking;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/** {@code @Mock} fields, created by the extension before each test: uses mocks. */
@ExtendWith(MockitoExtension.class)
class PlainMockitoExtensionTest {

    @Mock
    private TaxRates taxRates;

    @Test
    void usesStubbedRate() {
        when(taxRates.percentFor("CH")).thenReturn(50);

        assertEquals(150, new PriceCalculator(taxRates).grossCents(100, "CH"));
    }
}
