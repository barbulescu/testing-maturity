package ch.barbulescu.testability.examples.interactions;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/** Mocks used as stubs only: the tests check results, not calls. */
class StateTest {

    private final Stock stock = Mockito.mock(Stock.class);
    private final OrderService service = new OrderService(stock, Mockito.mock(Notifier.class));

    @Test
    void placesAnAvailableItem() {
        when(stock.remaining("book")).thenReturn(5);

        assertTrue(service.place("book"));
    }

    @Test
    void refusesAnItemOutOfStock() {
        when(stock.remaining("book")).thenReturn(0);

        assertFalse(service.place("book"));
    }
}
