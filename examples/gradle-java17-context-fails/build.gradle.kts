plugins {
    java
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
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
    implementation("org.springframework.boot:spring-boot-starter")
    testImplementation("org.springframework.boot:spring-boot-starter-test")

    if (project.hasProperty("testability.probe.repo")) {
        testRuntimeOnly(
            "${project.property("testability.probe.groupId")}:${project.property("testability.probe.artifactId")}:${project.property("testability.probe.version")}"
        )
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
