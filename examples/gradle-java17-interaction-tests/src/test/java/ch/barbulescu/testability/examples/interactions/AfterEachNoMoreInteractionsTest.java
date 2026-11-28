package ch.barbulescu.testability.examples.interactions;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/** A check in @AfterEach, through a helper, pins the calls of every test in the class. */
@ExtendWith(MockitoExtension.class)
class AfterEachNoMoreInteractionsTest {

    @Mock
    Stock stock;
    @Mock
    Notifier notifier;

    @Test
    void notifiesAboutTheOrder() {
        when(stock.remaining("book")).thenReturn(5);

        new OrderService(stock, notifier).place("book");

        verify(notifier).orderPlaced("book");
    }

    @Test
    void notifiesAboutLowStock() {
        when(stock.remaining("book")).thenReturn(1);

        new OrderService(stock, notifier).place("book");

        verify(notifier).orderPlaced("book");
        verify(notifier).lowStock("book");
    }

    @AfterEach
    void noOtherCalls() {
        checkNoOtherCalls();
    }

    private void checkNoOtherCalls() {
        verifyNoMoreInteractions(notifier);
    }
}
