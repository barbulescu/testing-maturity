package ch.barbulescu.testability.examples.jacoco;

public final class Discount {

    private Discount() {
    }

    public static int percentFor(int orderTotal) {
        return orderTotal >= 100 ? 10 : 0;
    }
}
