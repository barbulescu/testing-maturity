package ch.barbulescu.testability.examples.jacoco;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiscountTest {

    @Test
    void largeOrdersGetTenPercent() {
        assertEquals(10, Discount.percentFor(150));
    }
}
