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
        presence.put("springBoot", isPresent(loader, "org.springframework.boot.SpringApplication"));
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
