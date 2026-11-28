package ch.barbulescu.testability.examples.interactions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/** Each test pins every call the service makes: any new call breaks it. */
@ExtendWith(MockitoExtension.class)
class NoMoreInteractionsTest {

    @Mock
    Stock stock;
    @Mock
    Notifier notifier;

    @Test
    void lowStockIsTheOnlyOtherCall() {
        when(stock.remaining("book")).thenReturn(1);

        new OrderService(stock, notifier).place("book");

        verify(notifier).orderPlaced("book");
        verify(notifier).lowStock("book");
        verifyNoMoreInteractions(notifier);
    }

    @Test
    void outOfStockNotifiesNobody() {
        when(stock.remaining("book")).thenReturn(0);

        new OrderService(stock, notifier).place("book");

        verifyNoInteractions(notifier);
    }

    @Test
    void orderPlacedIsTheOnlyCall() {
        when(stock.remaining("book")).thenReturn(5);

        new OrderService(stock, notifier).place("book");

        verify(notifier, only()).orderPlaced("book");
    }
}
