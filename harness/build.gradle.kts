plugins {
    java
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
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.testcontainers:testcontainers:1.21.4")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    testImplementation(project(":classifier"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}

tasks.test {
    useJUnitPlatform()
    dependsOn(":probe:publishMavenPublicationToProbeRepoRepository")

    val repoDir = rootProject.layout.buildDirectory.dir("probe-repo")
    val examplesDir = rootProject.layout.projectDirectory.dir("examples")

    inputs.dir(repoDir)
    inputs.dir(examplesDir)

    systemProperty("testability.harness.probeRepoDir", repoDir.get().asFile.absolutePath)
    systemProperty("testability.harness.examplesDir", examplesDir.asFile.absolutePath)
    systemProperty("testability.harness.probeGroupId", project.group.toString())
    systemProperty("testability.harness.probeArtifactId", "testability-probe")
    systemProperty("testability.harness.probeVersion", project.version.toString())

    // Podman machine forwards a Docker-API-compatible socket to
    // /var/run/docker.sock on the host, which is Testcontainers' default -
    // no DOCKER_HOST needed. Ryuk (the resource reaper) doesn't reliably
    // work against Podman, so it's disabled; containers are closed
    // explicitly (try-with-resources) instead.
    environment("TESTCONTAINERS_RYUK_DISABLED", "true")
}
