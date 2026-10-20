plugins {
    kotlin("jvm") version "2.4.21"
}

group = "ch.barbulescu.testability.examples"
version = "0.0.1"

kotlin {
    jvmToolchain(17)
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
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    // -PmockkVersion=... runs the same tests on another MockK version.
    testImplementation("io.mockk:mockk:${findProperty("mockkVersion") ?: "1.14.11"}")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    if (project.hasProperty("testability.probe.repo")) {
        testRuntimeOnly(
            "${project.property("testability.probe.groupId")}:${project.property("testability.probe.artifactId")}:${project.property("testability.probe.version")}"
        )
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
