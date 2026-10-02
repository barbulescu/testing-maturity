package ch.barbulescu.testability.probe.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.Test;

/**
 * The probe jar must stay dependency-free: {@code core} may only ever see
 * the JDK, {@code junit} may only add the JUnit Platform on top of that,
 * {@code mockito} only Mockito, and {@code spring} only Spring (never JUnit or Boot - see
 * {@code build.gradle.kts} for why).
 */
class ArchitectureTest {

    private static final JavaClasses PROBE_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("ch.barbulescu.testability.probe");

    @Test
    void coreOnlyDependsOnTheJdk() {
        ArchRuleDefinition.classes()
                .that().resideInAPackage("..core..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage("java..", "javax..", "ch.barbulescu.testability.probe.core..")
                .check(PROBE_CLASSES);
    }

    @Test
    void junitAdapterOnlyDependsOnJUnitPlatformAndCore() {
        ArchRuleDefinition.classes()
                .that().resideInAPackage("..junit..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage(
                        "java..", "javax..",
                        "ch.barbulescu.testability.probe.core..",
                        "ch.barbulescu.testability.probe.junit..",
                        "org.junit.platform..")
                .check(PROBE_CLASSES);
    }

    @Test
    void mockitoAdapterOnlyDependsOnMockitoAndCore() {
        ArchRuleDefinition.classes()
                .that().resideInAPackage("..mockito..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage(
                        "java..", "javax..",
                        "ch.barbulescu.testability.probe.core..",
                        "ch.barbulescu.testability.probe.mockito..",
                        "org.mockito..")
                .check(PROBE_CLASSES);
    }

    @Test
    void springAdapterOnlyDependsOnSpringAndCore() {
        ArchRuleDefinition.classes()
                .that().resideInAPackage("..spring..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage(
                        "java..", "javax..",
                        "ch.barbulescu.testability.probe.core..",
                        "ch.barbulescu.testability.probe.spring..",
                        "org.springframework..")
                .check(PROBE_CLASSES);
    }
}
