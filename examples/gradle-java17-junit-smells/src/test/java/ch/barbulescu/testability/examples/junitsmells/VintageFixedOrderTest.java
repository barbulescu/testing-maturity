package ch.barbulescu.testability.examples.junitsmells;

import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import static org.junit.Assert.assertEquals;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class VintageFixedOrderTest {

    private static int counter;

    @Test
    public void a_increments() {
        counter++;
        assertEquals(1, counter);
    }

    @Test
    public void b_seesTheIncrement() {
        assertEquals(1, counter);
    }
}
