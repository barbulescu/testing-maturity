package ch.barbulescu.testability.examples.disabled;

import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class VintageIgnoredMethodTest {

    @Test
    public void runs() {
        assertEquals(2, 1 + 1);
    }

    @Ignore
    @Test
    public void ignoredMethod() {
    }
}
