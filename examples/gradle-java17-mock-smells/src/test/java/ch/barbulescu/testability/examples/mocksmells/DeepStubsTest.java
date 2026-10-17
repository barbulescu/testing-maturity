package ch.barbulescu.testability.examples.mocksmells;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeepStubsTest {

    @Test
    void stubsAChainOfCalls() {
        Order order = mock(Order.class, RETURNS_DEEP_STUBS);
        when(order.getCustomer().getAddress().getCity()).thenReturn("Bern");

        assertEquals("Bern", order.getCustomer().getAddress().getCity());
    }

    @Test
    void stubsOneCall() {
        Customer customer = mock(Customer.class);
        when(customer.getAddress()).thenReturn(new Address("Basel"));

        assertEquals("Basel", customer.getAddress().getCity());
    }
}
