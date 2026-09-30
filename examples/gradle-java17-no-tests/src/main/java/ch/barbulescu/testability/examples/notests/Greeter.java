package ch.barbulescu.testability.examples.notests;

/**
 * This module deliberately has no test sources at all: it proves the
 * probe stays silent (no report file) rather than fabricate a "zero
 * tests" report - a legitimately test-free module is covered by comparing
 * against an external service list, not by the probe itself.
 */
public class Greeter {

    public String greet(String name) {
        return "Hello, " + name;
    }
}
