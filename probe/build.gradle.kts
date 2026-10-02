plugins {
    `java-library`
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
    // the jupiter-engine version above.
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.1.3")
    // For fixture classes in spring package tests: real annotations beat hand-rolled fakes.
    // spring-boot-test pulls a matching spring-test transitively.
    testImplementation("org.springframework.boot:spring-boot-test:3.5.16")
    testImplementation("org.springframework.boot:spring-boot-test-autoconfigure:3.5.16")
    testImplementation("junit:junit:4.13.2")
    // @MockBean's default attribute values reference org.mockito.Answers, so reading the
    // annotation via reflection needs this on the classpath even without creating a mock.
    testImplementation("org.mockito:mockito-core:5.14.2")
}

tasks.test {
    useJUnitPlatform()
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("testability-probe")
}

apply(plugin = "maven-publish")

configure<PublishingExtension> {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            groupId = "ch.barbulescu.testability"
            artifactId = "testability-probe"
        }
    }
    repositories {
        maven {
            name = "probeRepo"
            url = uri(rootProject.layout.buildDirectory.dir("probe-repo"))
        }
    }
}
