package ch.barbulescu.testability.examples.mocksmells;

import java.time.LocalDate;

public final class Clock {

    private Clock() {
    }

    public static LocalDate today() {
        return LocalDate.now();
    }
}
