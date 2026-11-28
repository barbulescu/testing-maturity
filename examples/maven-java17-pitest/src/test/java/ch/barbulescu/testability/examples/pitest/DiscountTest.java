package ch.barbulescu.testability.examples.pitest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiscountTest {

    @Test
    void smallOrdersPayFullPrice() {
        assertEquals(50, Discount.apply(50));
    }

    @Test
    void largeOrdersGetTenPercentOff() {
        assertEquals(180, Discount.apply(200));
    }
}
