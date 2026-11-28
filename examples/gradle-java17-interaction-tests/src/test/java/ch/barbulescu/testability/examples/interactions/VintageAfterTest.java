package ch.barbulescu.testability.examples.interactions;

import org.junit.After;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/** The same check in a JUnit 4 @After. */
public class VintageAfterTest {

    private final Stock stock = mock(Stock.class);
    private final Notifier notifier = mock(Notifier.class);

    @Test
    public void notifiesAboutTheOrder() {
        when(stock.remaining("book")).thenReturn(5);

        new OrderService(stock, notifier).place("book");

        verify(notifier).orderPlaced("book");
    }

    @After
    public void noOtherCalls() {
        verifyNoMoreInteractions(notifier);
    }
}
