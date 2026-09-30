package ch.barbulescu.testability.probe.spring;

/**
 * These events live in {@code org.springframework.boot}, which this module
 * deliberately does not compile against (see {@code build.gradle.kts}) -
 * matched by name instead so the listener works across Boot major versions
 * without a Boot compile dependency.
 */
final class BootEventNames {

    static final String STARTING = "org.springframework.boot.context.event.ApplicationStartingEvent";
    static final String READY = "org.springframework.boot.context.event.ApplicationReadyEvent";
    static final String FAILED = "org.springframework.boot.context.event.ApplicationFailedEvent";

    private BootEventNames() {
    }
}
