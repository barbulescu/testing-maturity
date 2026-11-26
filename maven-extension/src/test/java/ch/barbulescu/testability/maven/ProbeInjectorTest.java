package ch.barbulescu.testability.maven;

import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.DefaultMavenExecutionResult;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProbeInjectorTest {

    @Test
    void addsTheProbeAsTestDependency() {
        MavenProject project = project("jar");

        int added = ProbeInjector.inject(Collections.singletonList(project), "1.2.3");

        assertEquals(1, added);
        Dependency probe = probeOf(project);
        assertEquals("1.2.3", probe.getVersion());
        assertEquals("test", probe.getScope());
    }

    @Test
    void addsTheProbeToEveryModuleButNotToPomProjects() {
        MavenProject parent = project("pom");
        MavenProject moduleA = project("jar");
        MavenProject moduleB = project("war");

        int added = ProbeInjector.inject(Arrays.asList(parent, moduleA, moduleB), "1.2.3");

        assertEquals(2, added);
        assertTrue(parent.getDependencies().isEmpty());
        assertEquals("1.2.3", probeOf(moduleA).getVersion());
        assertEquals("1.2.3", probeOf(moduleB).getVersion());
    }

    @Test
    void keepsAProbeTheProjectDeclaresItself() {
        MavenProject project = project("jar");
        Dependency own = new Dependency();
        own.setGroupId(ProbeInjector.GROUP_ID);
        own.setArtifactId(ProbeInjector.ARTIFACT_ID);
        own.setVersion("0.0.1");
        own.setScope("test");
        project.getModel().addDependency(own);

        int added = ProbeInjector.inject(Collections.singletonList(project), "1.2.3");

        assertEquals(0, added);
        assertEquals(1, project.getDependencies().size());
        assertEquals("0.0.1", probeOf(project).getVersion());
    }

    @Test
    void injectsItsOwnVersionByDefault() throws Exception {
        MavenProject project = project("jar");

        new ProbeInjector().afterProjectsRead(session(new Properties(), project));

        String version = probeOf(project).getVersion();
        assertEquals(ProbeInjector.ownVersion(), version);
        assertFalse(version.contains("@"), "the build must fill in the version, got " + version);
    }

    @Test
    void versionPropertyOverridesItsOwnVersion() {
        MavenProject project = project("jar");
        Properties userProperties = new Properties();
        userProperties.setProperty(ProbeInjector.VERSION_PROPERTY, "9.9.9");

        new ProbeInjector().afterProjectsRead(session(userProperties, project));

        assertEquals("9.9.9", probeOf(project).getVersion());
    }

    @Test
    void skipPropertyTurnsTheExtensionOff() {
        MavenProject project = project("jar");
        Properties userProperties = new Properties();
        userProperties.setProperty(ProbeInjector.SKIP_PROPERTY, "true");

        new ProbeInjector().afterProjectsRead(session(userProperties, project));

        assertTrue(project.getDependencies().isEmpty());
    }

    @Test
    void neverBreaksTheBuild() {
        MavenProject broken = new MavenProject(project("jar").getModel()) {
            @Override
            public List<Dependency> getDependencies() {
                throw new IllegalStateException("broken model");
            }
        };

        assertDoesNotThrow(() -> new ProbeInjector().afterProjectsRead(session(new Properties(), broken)));
    }

    private static MavenProject project(String packaging) {
        Model model = new Model();
        model.setGroupId("com.example");
        model.setArtifactId("app-" + packaging);
        model.setVersion("1.0");
        model.setPackaging(packaging);
        return new MavenProject(model);
    }

    @SuppressWarnings("deprecation") // the non-deprecated constructors need a full Maven runtime
    private static MavenSession session(Properties userProperties, MavenProject... projects) {
        MavenExecutionRequest request = new DefaultMavenExecutionRequest();
        request.setUserProperties(userProperties);
        request.setSystemProperties(new Properties());
        MavenSession session = new MavenSession(null, null, request, new DefaultMavenExecutionResult());
        session.setProjects(Arrays.asList(projects));
        return session;
    }

    private static Dependency probeOf(MavenProject project) {
        return project.getDependencies().stream()
                .filter(d -> ProbeInjector.ARTIFACT_ID.equals(d.getArtifactId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no probe in " + project.getDependencies()));
    }
}
