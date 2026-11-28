package ch.barbulescu.testability.probe.core;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Presence, not execution: a class being loadable says nothing about
 * whether it's actually used. Checked via {@code Class.forName(name,
 * false, loader)} so nothing is initialized.
 */
public final class ClasspathPresence {

    private ClasspathPresence() {
    }

    public static Map<String, Boolean> detect(ClassLoader loader) {
        Map<String, Boolean> presence = new LinkedHashMap<>();
        presence.put("junit4", isPresent(loader, "org.junit.runner.JUnitCore"));
        presence.put("vintage", isPresent(loader, "org.junit.vintage.engine.VintageTestEngine"));
        presence.put("testcontainers", isPresent(loader, "org.testcontainers.containers.GenericContainer"));
        // WireMock's package name (com.github.tomakehurst.wiremock) has outlived both its
        // old (com.github.tomakehurst:wiremock-jre8) and current (org.wiremock:wiremock)
        // Maven coordinates; checking both the core class and the JUnit 5 extension covers
        // projects that depend on either module.
        presence.put("wiremock",
                isPresent(loader, "com.github.tomakehurst.wiremock.WireMockServer")
                        || isPresent(loader, "com.github.tomakehurst.wiremock.junit5.WireMockExtension"));
        presence.put("mockito", isPresent(loader, "org.mockito.Mockito"));
        presence.put("mockk", isPresent(loader, "io.mockk.MockKGateway"));
        presence.put("springBoot", isPresent(loader, "org.springframework.boot.SpringApplication"));
        // In-process stand-ins for real infrastructure. Present only says a test COULD run
        // against them instead of the database or broker production uses.
        presence.put("h2", isPresent(loader, "org.h2.Driver"));
        presence.put("hsqldb", isPresent(loader, "org.hsqldb.jdbc.JDBCDriver"));
        // derbytools has the embedded driver since 10.15; before, the engine jar did. The
        // engine's own autoloaded driver covers projects that only depend on the engine.
        presence.put("derby",
                isPresent(loader, "org.apache.derby.jdbc.EmbeddedDriver")
                        || isPresent(loader, "org.apache.derby.iapi.jdbc.AutoloadedDriver"));
        // Flapdoodle's embedded MongoDB: MongodStarter up to 3.x, the transitions API since 4.x.
        presence.put("embeddedMongo",
                isPresent(loader, "de.flapdoodle.embed.mongo.MongodStarter")
                        || isPresent(loader, "de.flapdoodle.embed.mongo.transitions.Mongod"));
        presence.put("embeddedKafka", isPresent(loader, "org.springframework.kafka.test.EmbeddedKafkaBroker"));
        return presence;
    }

    public static Map<String, String> versions(ClassLoader loader) {
        Map<String, String> versions = new LinkedHashMap<>();
        putVersionIfPresent(versions, loader, "springBoot", "org.springframework.boot.SpringApplication");
        putVersionIfPresent(versions, loader, "junitPlatform", "org.junit.platform.launcher.core.LauncherFactory");
        return versions;
    }

    private static void putVersionIfPresent(Map<String, String> target, ClassLoader loader, String key, String className) {
        try {
            Class<?> type = Class.forName(className, false, loader);
            Package pkg = type.getPackage();
            String version = pkg != null ? pkg.getImplementationVersion() : null;
            if (version != null) {
                target.put(key, version);
            }
        } catch (Throwable ignored) {
            // absent, or a manifest without Implementation-Version - simply not reported
        }
    }

    private static boolean isPresent(ClassLoader loader, String className) {
        try {
            Class.forName(className, false, loader);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
