plugins {
    // Gradle 9's own daemon needs a much newer JVM than this example's Java 8
    // target, so the harness runs it under a modern JDK image and lets this
    // plugin auto-provision a Java 8 toolchain for compiling/running the code.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "gradle-java8-junit4-plain"
