package ch.barbulescu.testability.probe.core;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Identifies which module produced a report. Surefire sets {@code basedir}
 * even with {@code forkCount=0} (in-process, one JVM shared across
 * modules), which is why it's preferred over {@code user.dir}.
 */
public final class ModuleIdentity {

    private ModuleIdentity() {
    }

    public static String moduleDir() {
        return moduleDir(System.getProperty("basedir"), System.getProperty("user.dir"), System.getenv("CI_PROJECT_DIR"));
    }

    static String moduleDir(String basedirProperty, String userDir, String ciProjectDir) {
        String dir = basedirProperty != null && !basedirProperty.isEmpty() ? basedirProperty : userDir;
        if (ciProjectDir == null || ciProjectDir.isEmpty()) {
            return dir;
        }
        try {
            Path base = Paths.get(ciProjectDir).toAbsolutePath().normalize();
            Path target = Paths.get(dir).toAbsolutePath().normalize();
            String relative = base.relativize(target).toString();
            return relative.isEmpty() ? "." : relative;
        } catch (IllegalArgumentException e) {
            // e.g. different filesystem roots on Windows - the absolute path is still useful
            return dir;
        }
    }
}
