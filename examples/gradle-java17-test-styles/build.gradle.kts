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
    if (project.hasProperty("testability.probe.repo")) {
        maven {
            url = uri(project.property("testability.probe.repo") as String)
        }
    }
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("junit:junit:4.13.2")
    testImplementation("net.jqwik:jqwik:1.9.2")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.1")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
    if (project.hasProperty("testability.probe.repo")) {
        testRuntimeOnly(
            "${project.property("testability.probe.groupId")}:${project.property("testability.probe.artifactId")}:${project.property("testability.probe.version")}"
        )
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
