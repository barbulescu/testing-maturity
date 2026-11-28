package ch.barbulescu.testability.classifier;

/**
 * A way of testing a report shows a module uses, next to its level. Engine-based styles come
 * from {@code tests.byEngine}, the others from {@code tests.style}. Like the levels, these are
 * only examples of what can be read from a report.
 */
public enum TestStyle {

    /** jqwik: properties checked against generated inputs. */
    PROPERTY_BASED("property-based"),
    /** ArchUnit: rules about the code's structure. */
    ARCHITECTURE_RULES("architecture-rules"),
    /** Cucumber: scenarios written in Gherkin. */
    BDD("bdd"),
    /** Spock or Kotest: tests written as specifications. */
    SPECIFICATION("specification"),
    /** One test run over several inputs, e.g. {@code @ParameterizedTest}. */
    PARAMETERIZED("parameterized"),
    /** Tests created at run time by a {@code @TestFactory}. */
    DYNAMIC("dynamic"),
    /** Tests grouped in {@code @Nested} classes. */
    NESTED("nested");

    private final String label;

    TestStyle(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
