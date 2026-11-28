package ch.barbulescu.testability.examples.interactions;

public class OrderService {

    private final Stock stock;
    private final Notifier notifier;

    public OrderService(Stock stock, Notifier notifier) {
        this.stock = stock;
        this.notifier = notifier;
    }

    /** Returns whether the item was available. */
    public boolean place(String item) {
        int remaining = stock.remaining(item);
        if (remaining == 0) {
            return false;
        }
        notifier.orderPlaced(item);
        if (remaining == 1) {
            notifier.lowStock(item);
        }
        return true;
    }
}
