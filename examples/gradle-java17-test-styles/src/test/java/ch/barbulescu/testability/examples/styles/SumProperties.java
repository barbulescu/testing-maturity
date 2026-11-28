package ch.barbulescu.testability.examples.styles;

import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;

/** jqwik: a property checked against generated inputs, and an example. Each is one test. */
class SumProperties {

    @Property(tries = 20)
    boolean sumIsCommutative(@ForAll int a, @ForAll int b) {
        return a + b == b + a;
    }

    @Example
    boolean zeroIsNeutral() {
        return 5 + 0 == 5;
    }
}
