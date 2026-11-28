package ch.barbulescu.testability.examples.styles;

import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.Timeout;

import static org.junit.Assert.assertTrue;

/** A JUnit 4 class with a timeout rule, one test with its own timeout, and one in a category. */
public class VintageTimeoutTest {

    @Rule
    public Timeout timeout = Timeout.seconds(10);

    @Test(timeout = 1000)
    public void withTimeout() {
        assertTrue(true);
    }

    @Test
    @Category(SlowTests.class)
    public void categorized() {
        assertTrue(true);
    }
}
