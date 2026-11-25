allprojects {
    group = "ch.barbulescu.testability"
    version = "0.2.0-SNAPSHOT"
}

// Releases take their version from the tag (see .github/workflows/release.yml), so the
// build files keep the snapshot version and a release needs no version commits.
providers.gradleProperty("releaseVersion").orNull?.let { releaseVersion ->
    allprojects { version = releaseVersion }
}
