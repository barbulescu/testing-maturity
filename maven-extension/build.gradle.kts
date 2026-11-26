import org.apache.tools.ant.filters.ReplaceTokens

plugins {
    `java-library`
    jacoco
    id("com.vanniktech.maven.publish") version "0.37.0"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.named<JavaCompile>("compileJava") {
    // Runs inside the user's Maven, which may still run on Java 8.
    options.release = 8
}

repositories {
    mavenCentral()
}

dependencies {
    // Provided by the Maven that loads the extension. 3.6.3 is the oldest Maven we support;
    // the API used here (lifecycle participant, Sisu index, SLF4J) exists since 3.1.
    compileOnly("org.apache.maven:maven-core:3.6.3")
    compileOnly("javax.inject:javax.inject:1")
    compileOnly("org.slf4j:slf4j-api:1.7.29")

    testImplementation("org.apache.maven:maven-core:3.6.3")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}

tasks.processResources {
    // The extension injects the probe of its own version (the two are released together).
    inputs.property("version", project.version.toString())
    filesMatching("**/extension.properties") {
        filter<ReplaceTokens>("tokens" to mapOf("project.version" to project.version.toString()))
    }
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    reports {
        xml.required = true
        csv.required = true
    }
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("testability-probe-maven-extension")
}

mavenPublishing {
    publishToMavenCentral()
    // Same as the probe: sign only when a key is given (see README, "Releasing").
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }
    coordinates("ch.barbulescu.testability", "testability-probe-maven-extension", version.toString())

    pom {
        name = "Testability Probe Maven Extension"
        description = "Maven core extension that adds the testability probe to every project of a build, without changing its POMs."
        inceptionYear = "2026"
        url = "https://github.com/barbulescu/testing-maturity"
        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                distribution = "repo"
            }
        }
        developers {
            developer {
                id = "barbulescu"
                name = "Marius Barbulescu"
                url = "https://github.com/barbulescu"
            }
        }
        scm {
            url = "https://github.com/barbulescu/testing-maturity"
            connection = "scm:git:https://github.com/barbulescu/testing-maturity.git"
            developerConnection = "scm:git:ssh://git@github.com/barbulescu/testing-maturity.git"
        }
    }
}
