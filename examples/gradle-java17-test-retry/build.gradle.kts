plugins {
    java
    id("org.gradle.test-retry") version "1.6.4"
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
    if (project.hasProperty("testability.probe.repo")) {
        maven {
            url = uri(project.property("testability.probe.repo") as String)
        }
    }
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
    if (project.hasProperty("testability.probe.repo")) {
        testRuntimeOnly(
            "${project.property("testability.probe.groupId")}:${project.property("testability.probe.artifactId")}:${project.property("testability.probe.version")}"
        )
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    // Where FlakyTest remembers that it already failed once in this build.
    systemProperty("flakyMarker", layout.buildDirectory.file("flaky-marker").get().asFile.absolutePath)
    // The plugin reruns failed tests in a new test JVM. A test that passes then doesn't
    // fail the build.
    retry {
        maxRetries = 2
        failOnPassedAfterRetry = false
    }
}
