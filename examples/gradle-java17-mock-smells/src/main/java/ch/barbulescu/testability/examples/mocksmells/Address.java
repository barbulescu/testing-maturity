package ch.barbulescu.testability.examples.mocksmells;

public class Address {

    private final String city;

    public Address(String city) {
        this.city = city;
    }

    public String getCity() {
        return city;
    }
}
