package ch.barbulescu.testability.examples.interactions;

public interface Notifier {

    void orderPlaced(String item);

    void lowStock(String item);
}
