plugins {
    application
    jacoco
}

application {
    mainClass.set("ch.barbulescu.testability.classifier.Main")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Never shipped in the probe jar itself - this is a standalone tool, so
    // Jackson is fine here.
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    reports {
        xml.required = true
        // The CI coverage badge is computed from the CSV report.
        csv.required = true
    }
}
