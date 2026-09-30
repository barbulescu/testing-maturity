package ch.barbulescu.testability.examples.mockall;

import org.springframework.stereotype.Service;

@Service
public class CheckoutService {

    private final PaymentGateway paymentGateway;
    private final FraudDetector fraudDetector;
    private final NotificationSender notificationSender;

    public CheckoutService(PaymentGateway paymentGateway, FraudDetector fraudDetector,
            NotificationSender notificationSender) {
        this.paymentGateway = paymentGateway;
        this.fraudDetector = fraudDetector;
        this.notificationSender = notificationSender;
    }

    public boolean checkout(int amountCents) {
        if (fraudDetector.isSuspicious(amountCents)) {
            return false;
        }
        boolean charged = paymentGateway.charge(amountCents);
        if (charged) {
            notificationSender.notifyCustomer("Charged " + amountCents + " cents");
        }
        return charged;
    }
}
