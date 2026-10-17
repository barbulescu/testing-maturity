package ch.barbulescu.testability.examples.mocksmells;

import org.junit.jupiter.api.Test;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class LenientMockTest {

    @Test
    void lenientMockHidesUnusedStubbing() {
        Customer customer = mock(Customer.class, withSettings().strictness(Strictness.LENIENT));
        when(customer.getAddress()).thenReturn(new Address("Basel"));
        // Never called: on a strict mock this stubbing would fail the test as unnecessary.
        Order order = mock(Order.class, withSettings().strictness(Strictness.LENIENT));
        when(order.getCustomer()).thenReturn(customer);

        assertEquals("Basel", customer.getAddress().getCity());
    }

    @Test
    void strictMock() {
        Customer customer = mock(Customer.class);
        when(customer.getAddress()).thenReturn(new Address("Bern"));

        assertEquals("Bern", customer.getAddress().getCity());
    }
}
