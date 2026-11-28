package ch.barbulescu.testability.examples.styles;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** ArchUnit: one rule, run by its own engine. */
@AnalyzeClasses(packages = "ch.barbulescu.testability.examples.styles")
class ArchitectureRulesTest {

    @ArchTest
    static final ArchRule noJdbc = noClasses().should().dependOnClassesThat().resideInAPackage("java.sql..");
}
