package ch.barbulescu.testability.examples.mocking;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A Spring test against real beans: no mocks. */
@SpringBootTest
class SpringRealBeansTest {

    @Autowired
    private PriceCalculator calculator;

    @Test
    void usesRealBeans() {
        assertEquals(108, calculator.grossCents(100, "CH"));
    }
}
