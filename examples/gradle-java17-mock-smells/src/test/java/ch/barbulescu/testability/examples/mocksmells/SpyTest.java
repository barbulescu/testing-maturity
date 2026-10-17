package ch.barbulescu.testability.examples.mocksmells;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class SpyTest {

    /** Half real, half stubbed: the test exercises a mix of the object under test and a fake. */
    @Test
    void stubsPartOfTheObjectUnderTest() {
        InvoiceService service = spy(new InvoiceService());
        doReturn("Invoice 1999").when(service).header();

        assertEquals("Invoice 1999", service.header());
    }

    @Test
    void mocksOnlyTheCollaborator() {
        Customer customer = mock(Customer.class);
        when(customer.getAddress()).thenReturn(new Address("Zug"));

        assertEquals("Zug", customer.getAddress().getCity());
    }
}
