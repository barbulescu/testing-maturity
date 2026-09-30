plugins {
    java
}

group = "ch.barbulescu.testability.examples"
version = "0.0.1"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(8)
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
    testImplementation("junit:junit:4.13.2")
    if (project.hasProperty("testability.probe.repo")) {
        testRuntimeOnly(
            "${project.property("testability.probe.groupId")}:${project.property("testability.probe.artifactId")}:${project.property("testability.probe.version")}"
        )
    }
}

tasks.withType<Test> {
    // Gradle's native JUnit4 runner - NOT useJUnitPlatform(). The JUnit Platform
    // Launcher (and therefore our META-INF/services-registered listener) is never
    // invoked at all in this mode, which is exactly the blind spot this example
    // documents: no hook exists here, so a static check is required instead.
    useJUnit()
}
