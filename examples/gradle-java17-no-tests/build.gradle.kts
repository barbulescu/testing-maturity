plugins {
    java
}

group = "ch.barbulescu.testability.examples"
version = "0.0.1"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
    // Injected only when the harness runs this example with the probe attached.
    if (project.hasProperty("testability.probe.repo")) {
        maven {
            url = uri(project.property("testability.probe.repo") as String)
        }
    }
}

dependencies {
    if (project.hasProperty("testability.probe.repo")) {
        testRuntimeOnly(
            "${project.property("testability.probe.groupId")}:${project.property("testability.probe.artifactId")}:${project.property("testability.probe.version")}"
        )
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
