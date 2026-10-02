package ch.barbulescu.testability.examples.mocking;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/** A Spring test with a mocked bean: uses mocks. */
@SpringBootTest
class SpringMockitoBeanTest {

    @Autowired
    private PriceCalculator calculator;

    @MockitoBean
    private TaxRates taxRates;

    @Test
    void usesMockedBean() {
        when(taxRates.percentFor("CH")).thenReturn(10);

        assertEquals(110, calculator.grossCents(100, "CH"));
    }
}
