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
    // Only production code is constrained to Java 8; tests run on the
    // toolchain version and may use newer JUnit.
    options.release = 8
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("org.junit.platform:junit-platform-launcher:1.0.3")
    // Deliberately NOT spring-boot: the spring package must work across Boot major
    // versions without being compiled against any one of them, so anything Boot-specific
    // (SpringApplication lifecycle events, @SpringBootTest, @MockBean/@MockitoBean) is
    // matched by class/annotation name via reflection instead of typed imports.
    compileOnly("org.springframework:spring-context:5.0.20.RELEASE")
    compileOnly("org.springframework:spring-test:5.0.20.RELEASE")
    // Oldest version with the MockitoFramework listener API.
    compileOnly("org.mockito:mockito-core:2.1.0")

    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.5.1")
    // The compileOnly launcher above pins the oldest API our production code
    // may use; running our own tests needs a launcher that actually matches
    // the jupiter-engine version above. Tests also build TestIdentifiers against it.
    testImplementation("org.junit.platform:junit-platform-launcher:6.1.3")
    // For fixture classes in spring package tests: real annotations beat hand-rolled fakes.
    // spring-boot-test pulls a matching spring-test transitively.
    testImplementation("org.springframework.boot:spring-boot-test:3.5.16")
    testImplementation("org.springframework.boot:spring-boot-test-autoconfigure:3.5.16")
    testImplementation("junit:junit:4.13.2")
    // @MockBean's default attribute values reference org.mockito.Answers, so reading the
    // annotation via reflection needs this on the classpath even without creating a mock.
    testImplementation("org.mockito:mockito-core:5.14.2")
}

tasks.processResources {
    // The version each report states (ProbeVersion). Releases take it from the tag.
    inputs.property("version", project.version.toString())
    filesMatching("**/probe.properties") {
        filter<ReplaceTokens>("tokens" to mapOf("project.version" to project.version.toString()))
    }
}

tasks.test {
    useJUnitPlatform()
    // CoverageToolTest checks for the JaCoCo agent the jacoco plugin attaches.
    systemProperty("testability.probe.test.jacoco", "true")
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    reports {
        xml.required = true
        // The CI coverage badge is computed from the CSV report.
        csv.required = true
    }
}

tasks.javadoc {
    // mockito-core 2.1.0 ships its .java files in the jar. Without an explicit source path,
    // javadoc looks for sources on the classpath and fails compiling them.
    (options as CoreJavadocOptions).addStringOption("sourcepath", sourceSets.main.get().java.srcDirs.joinToString(File.pathSeparator))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("testability-probe")
}

mavenPublishing {
    publishToMavenCentral()
    // Sign only when a key is given (see README, "Releasing"), so CI and the harness's
    // local publish keep working without secrets. Central rejects unsigned uploads.
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }
    coordinates("ch.barbulescu.testability", "testability-probe", version.toString())

    pom {
        name = "Testability Probe"
        description = "Test-scoped dependency that records facts about how a JVM project's tests run and writes them as a JSON report."
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

// The harness publishes the probe here. The plugin names its publication "maven",
// so the harness's :probe:publishMavenPublicationToProbeRepoRepository keeps working.
publishing {
    repositories {
        maven {
            name = "probeRepo"
            url = uri(rootProject.layout.buildDirectory.dir("probe-repo"))
        }
    }
}
