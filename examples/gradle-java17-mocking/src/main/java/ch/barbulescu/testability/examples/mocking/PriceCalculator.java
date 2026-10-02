package ch.barbulescu.testability.examples.mocking;

import org.springframework.stereotype.Service;

@Service
public class PriceCalculator {

    private final TaxRates taxRates;

    public PriceCalculator(TaxRates taxRates) {
        this.taxRates = taxRates;
    }

    public int grossCents(int netCents, String country) {
        return netCents + netCents * taxRates.percentFor(country) / 100;
    }
}
