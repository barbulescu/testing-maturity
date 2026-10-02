package ch.barbulescu.testability.probe.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * One instance per classloader (a plain lazy singleton already gives us
 * that: static state is scoped to the classloader that defines this
 * class), which is the right granularity - one test classpath, one report.
 */
public final class ProbeRecorder {

    private static final String PROBE_VERSION = "0.1.0";
    private static final int SCHEMA_VERSION = 1;

    private static volatile ProbeRecorder instance;

    private final String workerId = WorkerId.generate();

    // JUnit adapter
    private final AtomicBoolean testsAdapterUsed = new AtomicBoolean(false);
    private final Map<String, AtomicInteger> testsByEngine = new ConcurrentHashMap<>();
    private final AtomicInteger succeeded = new AtomicInteger();
    private final AtomicInteger failed = new AtomicInteger();
    private final AtomicInteger skipped = new AtomicInteger();
    private final AtomicLong startNanos = new AtomicLong(-1);
    private final AtomicLong durationMs = new AtomicLong();

    // Spring test-class adapter
    private final AtomicBoolean springAdapterUsed = new AtomicBoolean(false);
    private final AtomicInteger springTestClasses = new AtomicInteger();
    private final AtomicInteger junit4Classes = new AtomicInteger();
    private final AtomicInteger bootTestClasses = new AtomicInteger();
    private final AtomicInteger sliceTestClasses = new AtomicInteger();
    private final AtomicInteger mockBeanFields = new AtomicInteger();
    private final AtomicInteger contextsLoaded = new AtomicInteger();
    private final AtomicInteger plainContextsLoaded = new AtomicInteger();

    // Mock usage per finished test, split by whether its class is a Spring test class
    private final AtomicInteger plainTests = new AtomicInteger();
    private final AtomicInteger plainTestsUsingMocks = new AtomicInteger();
    private final AtomicInteger springTests = new AtomicInteger();
    private final AtomicInteger springTestsUsingMocks = new AtomicInteger();

    // Spring Boot application-start adapter
    private final AtomicBoolean bootStartsAdapterUsed = new AtomicBoolean(false);
    private final AtomicInteger bootStartsSucceeded = new AtomicInteger();
    private final AtomicInteger bootStartsFailed = new AtomicInteger();
    private final AtomicLong bootStartsMaxDurationMs = new AtomicLong();
    private final List<String> bootStartFailureRootCauses = Collections.synchronizedList(new ArrayList<String>());

    private final List<String> probeErrors = Collections.synchronizedList(new ArrayList<String>());
    private final AtomicBoolean shutdownHookRegistered = new AtomicBoolean(false);
    private final AtomicBoolean flushed = new AtomicBoolean(false);

    private ProbeRecorder() {
    }

    public static ProbeRecorder getInstance() {
        ProbeRecorder result = instance;
        if (result == null) {
            synchronized (ProbeRecorder.class) {
                result = instance;
                if (result == null) {
                    instance = result = new ProbeRecorder();
                }
            }
        }
        return result;
    }

    /**
     * Lazily registered by whichever adapter (JUnit, Spring) uses the
     * recorder first; the shutdown hook is a safety net in case an
     * explicit flush never happens. Critically, this is also the ONLY way
     * a report ever gets written when the JUnit Platform Launcher never
     * runs at all (e.g. a classic {@code SpringRunner}/JUnit 4 test under
     * Surefire's native JUnit 4 provider) - the Spring adapter must call
     * this itself rather than relying on the JUnit adapter to have done so.
     */
    public void ensureShutdownHookRegistered() {
        if (shutdownHookRegistered.compareAndSet(false, true)) {
            Runtime.getRuntime().addShutdownHook(new Thread(this::flush, "testability-probe-flush"));
        }
    }

    public void recordTestPlanStarted() {
        testsAdapterUsed.set(true);
        startNanos.compareAndSet(-1, System.nanoTime());
    }

    public void recordTestSucceeded(String engineId) {
        countFor(engineId).incrementAndGet();
        succeeded.incrementAndGet();
    }

    /** Also used for JUnit Platform's ABORTED status - v1's schema has no separate bucket for it. */
    public void recordTestFailed(String engineId) {
        countFor(engineId).incrementAndGet();
        failed.incrementAndGet();
    }

    public void recordTestSkipped(String engineId) {
        countFor(engineId).incrementAndGet();
        skipped.incrementAndGet();
    }

    /** Only tests that actually ran; skipped tests never reach this. */
    public void recordTestMockUsage(boolean springTest, boolean usesMocks) {
        if (springTest) {
            springTests.incrementAndGet();
            if (usesMocks) {
                springTestsUsingMocks.incrementAndGet();
            }
        } else {
            plainTests.incrementAndGet();
            if (usesMocks) {
                plainTestsUsingMocks.incrementAndGet();
            }
        }
    }

    public void recordSpringTestClass(boolean isJUnit4, boolean isBootTest, boolean isSliceTest, int mockBeanFieldCount) {
        springAdapterUsed.set(true);
        springTestClasses.incrementAndGet();
        if (isJUnit4) {
            junit4Classes.incrementAndGet();
        }
        if (isBootTest) {
            bootTestClasses.incrementAndGet();
        }
        if (isSliceTest) {
            sliceTestClasses.incrementAndGet();
        }
        mockBeanFields.addAndGet(mockBeanFieldCount);
    }

    /** Called once per distinct application context; plain means not started by Spring Boot. */
    public void recordSpringContextLoaded(boolean plain) {
        springAdapterUsed.set(true);
        contextsLoaded.incrementAndGet();
        if (plain) {
            plainContextsLoaded.incrementAndGet();
        }
    }

    public void recordBootStartSucceeded(Long durationMs) {
        bootStartsAdapterUsed.set(true);
        bootStartsSucceeded.incrementAndGet();
        updateMaxBootDuration(durationMs);
    }

    public void recordBootStartFailed(Long durationMs, String rootCauseType) {
        bootStartsAdapterUsed.set(true);
        bootStartsFailed.incrementAndGet();
        updateMaxBootDuration(durationMs);
        if (rootCauseType != null) {
            bootStartFailureRootCauses.add(rootCauseType);
        }
    }

    public void recordError(Throwable t) {
        probeErrors.add(t.getClass().getName());
    }

    public void flush() {
        if (!flushed.compareAndSet(false, true)) {
            return;
        }
        long start = startNanos.get();
        durationMs.set(start >= 0 ? (System.nanoTime() - start) / 1_000_000 : 0);
        writeReport(new FileSink());
    }

    /**
     * Package-private so fault-injection tests can substitute a deliberately
     * failing sink: no exception from the sink (disk full, unwritable
     * directory, whatever) may ever reach the caller. When the sink itself
     * fails there is, by definition, nowhere to record that failure - the
     * guarantee here is only ever "the build stays green," not "the error
     * is recorded" (that guarantee belongs to {@link #safely}, for
     * failures within a report that DOES get written).
     */
    void writeReport(ReportSink sink) {
        try {
            sink.write("testability-probe-" + workerId + ".json", toJson());
        } catch (Throwable t) {
            if (Boolean.getBoolean("testability.probe.debug")) {
                t.printStackTrace(System.err);
            }
        }
    }

    private AtomicInteger countFor(String engineId) {
        return testsByEngine.computeIfAbsent(engineId, key -> new AtomicInteger());
    }

    private void updateMaxBootDuration(Long candidateMs) {
        if (candidateMs == null) {
            return;
        }
        bootStartsMaxDurationMs.updateAndGet(current -> Math.max(current, candidateMs));
    }

    /**
     * Runs a single field's computation in isolation: if it throws (e.g. a
     * malformed CI/module-path environment value), the failure is recorded
     * as a fact and the rest of the report is still written with the
     * fallback in that one field's place, rather than losing the whole
     * report to one bad input.
     */
    <T> T safely(Callable<T> computation, T fallback) {
        try {
            return computation.call();
        } catch (Throwable t) {
            recordError(t);
            return fallback;
        }
    }

    private String toJson() {
        ClassLoader loader = ProbeRecorder.class.getClassLoader();
        CiContext ci = safely(CiContext::fromEnvironment, CiContext.from(key -> null));

        JsonWriter writer = new JsonWriter();
        writer.beginObject();
        writer.name("schemaVersion").value(SCHEMA_VERSION);
        writer.name("probeVersion").value(PROBE_VERSION);

        writer.name("ci").beginObject();
        writer.name("projectPath").value(ci.projectPath());
        writer.name("jobId").value(ci.jobId());
        writer.name("commitSha").value(ci.commitSha());
        writer.name("pipelineSource").value(ci.pipelineSource());
        writer.endObject();

        writer.name("moduleDir").value(this.<String>safely(ModuleIdentity::moduleDir, null));
        writer.name("workerId").value(workerId);
        writer.name("buildTool").value(safely(BuildTool::detect, "UNKNOWN"));

        writer.name("jvm").beginObject();
        writer.name("javaVersion").value(System.getProperty("java.version"));
        writer.name("vendor").value(System.getProperty("java.vendor"));
        writer.endObject();

        Map<String, Boolean> onClasspath = safely(() -> ClasspathPresence.detect(loader), Collections.emptyMap());
        writer.name("onClasspath").beginObject();
        for (Map.Entry<String, Boolean> entry : onClasspath.entrySet()) {
            writer.name(entry.getKey()).value(entry.getValue());
        }
        writer.endObject();

        Map<String, String> versions = safely(() -> ClasspathPresence.versions(loader), Collections.emptyMap());
        writer.name("versions").beginObject();
        for (Map.Entry<String, String> entry : versions.entrySet()) {
            writer.name(entry.getKey()).value(entry.getValue());
        }
        writer.endObject();

        if (testsAdapterUsed.get()) {
            writer.name("tests").beginObject();
            writer.name("byEngine").beginObject();
            for (Map.Entry<String, AtomicInteger> entry : testsByEngine.entrySet()) {
                writer.name(entry.getKey()).value(entry.getValue().get());
            }
            writer.endObject();
            writer.name("succeeded").value(succeeded.get());
            writer.name("failed").value(failed.get());
            writer.name("skipped").value(skipped.get());
            writer.name("durationMs").value(durationMs.get());
            writer.endObject();

            writer.name("mocking").beginObject();
            writer.name("mockitoObserved").value(MockitoHook.isObserved());
            writer.name("plainTests").value(plainTests.get());
            writer.name("plainTestsUsingMocks").value(plainTestsUsingMocks.get());
            writer.name("springTests").value(springTests.get());
            writer.name("springTestsUsingMocks").value(springTestsUsingMocks.get());
            writer.endObject();
        }

        if (springAdapterUsed.get()) {
            writer.name("spring").beginObject();
            writer.name("testClasses").value(springTestClasses.get());
            writer.name("junit4Classes").value(junit4Classes.get());
            writer.name("bootTestClasses").value(bootTestClasses.get());
            writer.name("sliceTestClasses").value(sliceTestClasses.get());
            writer.name("mockBeanFields").value(mockBeanFields.get());
            writer.name("contextsLoaded").value(contextsLoaded.get());
            writer.name("plainContextsLoaded").value(plainContextsLoaded.get());
            writer.endObject();
        }

        if (bootStartsAdapterUsed.get()) {
            writer.name("bootStarts").beginObject();
            writer.name("succeeded").value(bootStartsSucceeded.get());
            writer.name("failed").value(bootStartsFailed.get());
            writer.name("maxDurationMs").value(bootStartsMaxDurationMs.get());
            writer.name("failureRootCauses").beginArray();
            synchronized (bootStartFailureRootCauses) {
                for (String cause : bootStartFailureRootCauses) {
                    writer.value(cause);
                }
            }
            writer.endArray();
            writer.endObject();
        }

        writer.name("probeErrors").beginArray();
        synchronized (probeErrors) {
            for (String error : probeErrors) {
                writer.value(error);
            }
        }
        writer.endArray();

        writer.endObject();
        return writer.toString();
    }
}
