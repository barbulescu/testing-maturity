package ch.barbulescu.testability.probe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModuleIdentityTest {

    @Test
    void prefersBasedirOverUserDir() {
        assertEquals("/repo/services/payments",
                ModuleIdentity.moduleDir("/repo/services/payments", "/somewhere/else", null));
    }

    @Test
    void fallsBackToUserDirWhenBasedirAbsent() {
        assertEquals("/repo/services/payments",
                ModuleIdentity.moduleDir(null, "/repo/services/payments", null));
    }

    @Test
    void isRelativeToCiProjectDirWhenSet() {
        assertEquals("services/payments",
                ModuleIdentity.moduleDir("/repo/services/payments", "/somewhere/else", "/repo"));
    }

    @Test
    void isDotWhenModuleDirEqualsProjectDir() {
        assertEquals(".", ModuleIdentity.moduleDir("/repo", "/somewhere/else", "/repo"));
    }
}
