package ch.barbulescu.testability.examples.pitest;

public final class Discount {

    private Discount() {
    }

    /** Ten percent off orders of 100 or more. */
    public static int apply(int amount) {
        if (amount >= 100) {
            return amount - amount / 10;
        }
        return amount;
    }
}
