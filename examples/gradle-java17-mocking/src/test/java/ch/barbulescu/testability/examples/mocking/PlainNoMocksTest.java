package ch.barbulescu.testability.examples.mocking;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Two plain tests, real collaborators only: neither uses mocks. */
class PlainNoMocksTest {

    private final PriceCalculator calculator = new PriceCalculator(new FixedTaxRates());

    @Test
    void swissRate() {
        assertEquals(108, calculator.grossCents(100, "CH"));
    }

    @Test
    void defaultRate() {
        assertEquals(120, calculator.grossCents(100, "DE"));
    }
}
