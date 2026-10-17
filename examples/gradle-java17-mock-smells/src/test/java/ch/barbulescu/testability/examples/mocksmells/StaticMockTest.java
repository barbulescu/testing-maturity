package ch.barbulescu.testability.examples.mocksmells;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaticMockTest {

    @Test
    void mocksAStaticMethod() {
        try (MockedStatic<Clock> clock = Mockito.mockStatic(Clock.class)) {
            clock.when(Clock::today).thenReturn(LocalDate.of(2020, 1, 1));

            assertEquals("Invoice 2020", new InvoiceService().header());
        }
    }

    @Test
    void usesTheRealClock() {
        assertTrue(new InvoiceService().header().startsWith("Invoice "));
    }
}
