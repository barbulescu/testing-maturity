package ch.barbulescu.testability.examples.mocking;

import org.springframework.stereotype.Component;

@Component
public class FixedTaxRates implements TaxRates {

    @Override
    public int percentFor(String country) {
        return "CH".equals(country) ? 8 : 20;
    }
}
