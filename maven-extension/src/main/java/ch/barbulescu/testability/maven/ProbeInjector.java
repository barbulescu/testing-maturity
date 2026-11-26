package ch.barbulescu.testability.maven;

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;

/**
 * Maven core extension that adds the probe as a {@code test}-scoped dependency to every
 * project in the reactor, so a build gets the probe without any change to its POMs:
 * <pre>mvn verify -Dmaven.ext.class.path=/path/to/testability-probe-maven-extension.jar</pre>
 * Maven calls {@link #afterProjectsRead} after it has read every POM and before it resolves
 * any dependencies, so to Surefire and Failsafe the probe looks as if the POM declared it.
 * <p>
 * Like the probe, the extension must never break a build: any failure is logged as a
 * warning and the build goes on without the probe.
 */
@Named("testability-probe")
@Singleton
public class ProbeInjector extends AbstractMavenLifecycleParticipant {

    static final String GROUP_ID = "ch.barbulescu.testability";
    static final String ARTIFACT_ID = "testability-probe";
    /** {@code -Dtestability.probe.skip=true} turns the extension off. */
    static final String SKIP_PROPERTY = "testability.probe.skip";
    /** {@code -Dtestability.probe.version=X} injects probe X instead of the extension's own version. */
    static final String VERSION_PROPERTY = "testability.probe.version";

    private static final Logger LOG = LoggerFactory.getLogger(ProbeInjector.class);

    @Override
    public void afterProjectsRead(MavenSession session) {
        try {
            if (Boolean.parseBoolean(property(session, SKIP_PROPERTY))) {
                LOG.info("Testability probe not added: {} is set", SKIP_PROPERTY);
                return;
            }
            String override = property(session, VERSION_PROPERTY);
            String version = override != null && !override.isEmpty() ? override : ownVersion();
            int added = inject(session.getProjects(), version);
            LOG.info("Testability probe {} added to {} of {} projects", version, added, session.getProjects().size());
        } catch (RuntimeException | LinkageError | IOException e) {
            LOG.warn("Testability probe not added: {}", e.toString());
        }
    }

    /** Adds the probe to each project that can run tests and doesn't declare it already. */
    static int inject(List<MavenProject> projects, String version) {
        int added = 0;
        for (MavenProject project : projects) {
            if ("pom".equals(project.getPackaging()) || declaresProbe(project)) {
                continue;
            }
            Dependency probe = new Dependency();
            probe.setGroupId(GROUP_ID);
            probe.setArtifactId(ARTIFACT_ID);
            probe.setVersion(version);
            probe.setScope("test");
            project.getModel().addDependency(probe);
            added++;
        }
        return added;
    }

    /** A team that declares the probe itself keeps its own version and scope. */
    private static boolean declaresProbe(MavenProject project) {
        for (Dependency dependency : project.getDependencies()) {
            if (GROUP_ID.equals(dependency.getGroupId()) && ARTIFACT_ID.equals(dependency.getArtifactId())) {
                return true;
            }
        }
        return false;
    }

    private static String property(MavenSession session, String name) {
        String value = session.getUserProperties().getProperty(name);
        return value != null ? value : session.getSystemProperties().getProperty(name);
    }

    /** The probe and the extension are released together, so the extension injects its own version. */
    static String ownVersion() throws IOException {
        Properties properties = new Properties();
        try (InputStream in = ProbeInjector.class.getResourceAsStream("extension.properties")) {
            if (in == null) {
                throw new IOException("extension.properties is missing from the extension jar");
            }
            properties.load(in);
        }
        return properties.getProperty("version");
    }
}
