package ch.barbulescu.testability.examples.junitsmells;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The second test only passes after the first: run alone, it fails. */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrderedStepsTest {

    private static final List<String> CART = new ArrayList<>();

    @Test
    @Order(1)
    void addsAnItem() {
        CART.add("book");
        assertEquals(1, CART.size());
    }

    @Test
    @Order(2)
    void checksOutTheItemAddedBefore() {
        assertEquals("book", CART.get(0));
    }
}
