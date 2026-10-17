package ch.barbulescu.testability.examples.springsmells;

/** Mutable state in a singleton bean: the usual reason someone reaches for @DirtiesContext. */
public class Counter {

    private int value;

    public int increment() {
        return ++value;
    }
}
