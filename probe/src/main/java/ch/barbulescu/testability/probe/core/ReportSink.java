package ch.barbulescu.testability.probe.core;

public interface ReportSink {

    void write(String fileName, String json) throws Exception;
}
