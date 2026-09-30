package ch.barbulescu.testability.examples.mockall;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Every collaborator is mocked - nothing real is exercised here beyond
 * wiring. This example proves the probe counts {@code mockBeanFields}
 * without judging whether that's a good test.
 */
@SpringBootTest
class CheckoutServiceTest {

    @Autowired
    private CheckoutService checkoutService;

    @MockitoBean
    private PaymentGateway paymentGateway;

    @MockitoBean
    private FraudDetector fraudDetector;

    @MockitoBean
    private NotificationSender notificationSender;

    @Test
    void chargesWhenNotSuspicious() {
        when(fraudDetector.isSuspicious(500)).thenReturn(false);
        when(paymentGateway.charge(500)).thenReturn(true);

        assertTrue(checkoutService.checkout(500));
    }
}
