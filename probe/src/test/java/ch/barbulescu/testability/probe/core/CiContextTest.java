package ch.barbulescu.testability.probe.core;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CiContextTest {

    @Test
    void readsKnownVariables() {
        Map<String, String> env = new HashMap<>();
        env.put("CI_PROJECT_PATH", "group/project");
        env.put("CI_JOB_ID", "42");
        env.put("CI_COMMIT_SHA", "abc123");
        env.put("CI_PIPELINE_SOURCE", "push");

        CiContext ci = CiContext.from(env::get);

        assertEquals("group/project", ci.projectPath());
        assertEquals("42", ci.jobId());
        assertEquals("abc123", ci.commitSha());
        assertEquals("push", ci.pipelineSource());
    }

    @Test
    void isNullWhenAbsent() {
        CiContext ci = CiContext.from(key -> null);

        assertNull(ci.projectPath());
        assertNull(ci.jobId());
        assertNull(ci.commitSha());
        assertNull(ci.pipelineSource());
    }
}
