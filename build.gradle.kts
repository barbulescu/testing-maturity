allprojects {
    group = "ch.barbulescu.testability"
    version = "0.2.0-SNAPSHOT"
}

// CI runs this once, before the parallel example jobs, and caches the result for them
// (.github/workflows/ci.yml), so the jobs don't all download from Maven Central at once.
subprojects {
    tasks.register("downloadDependencies") {
        description = "Downloads every dependency of this project."
        val classpaths = configurations.matching { it.isCanBeResolved }
        doLast { classpaths.forEach { it.resolve() } }
    }
}

// Releases take their version from the tag (see .github/workflows/release.yml), so the
// build files keep the snapshot version and a release needs no version commits.
providers.gradleProperty("releaseVersion").orNull?.let { releaseVersion ->
    allprojects { version = releaseVersion }
}
