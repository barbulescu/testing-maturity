package ch.barbulescu.testability.examples.interactions;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Checks the one call that matters and leaves the others open. */
class VerifyTest {

    @Test
    void notifiesAboutTheOrder() {
        Stock stock = mock(Stock.class);
        Notifier notifier = mock(Notifier.class);
        when(stock.remaining("book")).thenReturn(1);

        new OrderService(stock, notifier).place("book");

        verify(notifier).orderPlaced("book");
    }
}
