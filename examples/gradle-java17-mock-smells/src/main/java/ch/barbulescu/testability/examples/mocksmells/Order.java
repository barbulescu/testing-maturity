package ch.barbulescu.testability.examples.mocksmells;

public class Order {

    private final Customer customer;

    public Order(Customer customer) {
        this.customer = customer;
    }

    public Customer getCustomer() {
        return customer;
    }
}
