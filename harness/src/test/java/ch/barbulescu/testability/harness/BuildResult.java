package ch.barbulescu.testability.harness;

import java.util.List;

final class BuildResult {

    final int exitCode;
    final List<String> reports;
    final String log;
    final long elapsedMillis;

    BuildResult(int exitCode, List<String> reports, String log, long elapsedMillis) {
        this.exitCode = exitCode;
        this.reports = reports;
        this.log = log;
        this.elapsedMillis = elapsedMillis;
    }
}
