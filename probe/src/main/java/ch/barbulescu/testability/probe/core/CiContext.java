package ch.barbulescu.testability.probe.core;

import java.util.function.Function;

/**
 * GitLab CI environment variables, null when the build isn't running in CI
 * (or a variable simply isn't set).
 */
public final class CiContext {

    private final String projectPath;
    private final String jobId;
    private final String commitSha;
    private final String pipelineSource;

    private CiContext(String projectPath, String jobId, String commitSha, String pipelineSource) {
        this.projectPath = projectPath;
        this.jobId = jobId;
        this.commitSha = commitSha;
        this.pipelineSource = pipelineSource;
    }

    public static CiContext fromEnvironment() {
        return from(System::getenv);
    }

    static CiContext from(Function<String, String> env) {
        return new CiContext(
                env.apply("CI_PROJECT_PATH"),
                env.apply("CI_JOB_ID"),
                env.apply("CI_COMMIT_SHA"),
                env.apply("CI_PIPELINE_SOURCE"));
    }

    public String projectPath() {
        return projectPath;
    }

    public String jobId() {
        return jobId;
    }

    public String commitSha() {
        return commitSha;
    }

    public String pipelineSource() {
        return pipelineSource;
    }
}
