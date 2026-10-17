package ch.barbulescu.testability.examples.mocksmells;

public class Customer {

    private final Address address;

    public Customer(Address address) {
        this.address = address;
    }

    public Address getAddress() {
        return address;
    }
}
