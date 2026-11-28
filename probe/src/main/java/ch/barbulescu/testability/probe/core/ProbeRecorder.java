package ch.barbulescu.testability.probe.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * One instance per classloader (a plain lazy singleton already gives us
 * that: static state is scoped to the classloader that defines this
 * class), which is the right granularity - one test classpath, one report.
 */
public final class ProbeRecorder {

    private static final String PROBE_VERSION = ProbeVersion.read();
    private static final int SCHEMA_VERSION = 1;

    private static volatile ProbeRecorder instance;

    private final String workerId = WorkerId.generate();

    // JUnit adapter
    private final AtomicBoolean testsAdapterUsed = new AtomicBoolean(false);
    private final Map<String, AtomicInteger> testsByEngine = new ConcurrentHashMap<>();
    private final AtomicInteger succeeded = new AtomicInteger();
    private final AtomicInteger failed = new AtomicInteger();
    private final AtomicInteger skipped = new AtomicInteger();
    private final AtomicInteger aborted = new AtomicInteger();
    // Failed tests by what they threw: the type only, never the message.
    private final AtomicInteger assertionFailures = new AtomicInteger();
    private final AtomicInteger errorFailures = new AtomicInteger();
    private final Map<String, AtomicInteger> failuresByType = new ConcurrentHashMap<>();
    private final AtomicLong startNanos = new AtomicLong(-1);
    private final AtomicLong durationMs = new AtomicLong();
    private final AtomicInteger fixedOrderClasses = new AtomicInteger();
    private final AtomicInteger flakyTests = new AtomicInteger();
    private final AtomicInteger retryingTestMethods = new AtomicInteger();
    private final AtomicInteger repeatedTestMethods = new AtomicInteger();
    private final AtomicInteger timeoutClasses = new AtomicInteger();
    private final AtomicInteger timeoutMethods = new AtomicInteger();
    // Finished tests by where they sit, from the segments of their unique IDs.
    private final AtomicInteger parameterizedTests = new AtomicInteger();
    private final AtomicInteger dynamicTests = new AtomicInteger();
    private final AtomicInteger nestedTests = new AtomicInteger();
    // Finished tests by tag: a test counts once for each tag it or its containers carry.
    private final Map<String, AtomicInteger> testsByTag = new ConcurrentHashMap<>();
    // Finished tests by how long they took, from start to finish event.
    private static final long[] DURATION_BUCKET_MINIMUMS_MS = {0, 100, 1_000, 10_000};
    private static final String[] DURATION_BUCKET_NAMES = {"<100ms", "100ms-1s", "1s-10s", "10s+"};
    private final AtomicInteger[] testsByDuration = newCounters(DURATION_BUCKET_NAMES.length);
    private final AtomicLong maxTestDurationMs = new AtomicLong();
    // Jupiter's parallel execution: as configured in any plan, and the most tests seen running at once.
    private final AtomicBoolean parallelEnabled = new AtomicBoolean(false);
    private final AtomicReference<String> parallelModeDefault = new AtomicReference<>();
    private final AtomicInteger maxConcurrentTests = new AtomicInteger();

    // Spring test-class adapter
    private final AtomicBoolean springAdapterUsed = new AtomicBoolean(false);
    private final AtomicInteger springTestClasses = new AtomicInteger();
    private final AtomicInteger junit4Classes = new AtomicInteger();
    private final AtomicInteger bootTestClasses = new AtomicInteger();
    private final AtomicInteger sliceTestClasses = new AtomicInteger();
    private final AtomicInteger mockBeanFields = new AtomicInteger();
    private final AtomicInteger contextsLoaded = new AtomicInteger();
    private final AtomicInteger plainContextsLoaded = new AtomicInteger();
    private final AtomicLong contextLoadNanos = new AtomicLong();
    private final AtomicInteger dirtiesContextClasses = new AtomicInteger();
    private final AtomicInteger dirtiesContextMethods = new AtomicInteger();
    // How Spring tests reach the application and its infrastructure
    private static final String[] WEB_ENVIRONMENTS = {"MOCK", "RANDOM_PORT", "DEFINED_PORT", "NONE"};
    private final AtomicInteger[] bootTestsByWebEnvironment = newCounters(WEB_ENVIRONMENTS.length);
    private final AtomicInteger testDatabaseReplacedClasses = new AtomicInteger();
    private final AtomicInteger serviceConnectionClasses = new AtomicInteger();
    private final AtomicInteger dynamicPropertySourceClasses = new AtomicInteger();
    private final AtomicInteger embeddedKafkaClasses = new AtomicInteger();

    // Mock usage per finished test, split by whether its class is a Spring test class
    private final AtomicInteger plainTests = new AtomicInteger();
    private final AtomicInteger plainTestsUsingMocks = new AtomicInteger();
    private final AtomicInteger springTests = new AtomicInteger();
    private final AtomicInteger springTestsUsingMocks = new AtomicInteger();
    private final AtomicInteger testsUsingStaticMocks = new AtomicInteger();
    private final AtomicInteger testsUsingDeepStubs = new AtomicInteger();
    private final AtomicInteger testsUsingLenientMocks = new AtomicInteger();
    private final AtomicInteger testsUsingSpies = new AtomicInteger();
    // Whether tests check calls on their mocks rather than results.
    private final AtomicInteger testsVerifyingInteractions = new AtomicInteger();
    private final AtomicInteger testsVerifyingNoMoreInteractions = new AtomicInteger();
    // Tests by how many mocks they used; a test without mocks isn't in any bucket.
    private static final int[] MOCK_COUNT_BUCKET_MINIMUMS = {1, 2, 4, 6, 10};
    private static final String[] MOCK_COUNT_BUCKET_NAMES = {"1", "2-3", "4-5", "6-9", "10+"};
    private final AtomicInteger[] testsByMockCount = newCounters(MOCK_COUNT_BUCKET_NAMES.length);
    private final AtomicInteger maxMocksPerTest = new AtomicInteger();
    // The same, counting only the mocks a test created itself, not those of its class.
    private final AtomicInteger[] testsByOwnMockCount = newCounters(MOCK_COUNT_BUCKET_NAMES.length);
    private final AtomicInteger testsUsingOnlyClassLevelMocks = new AtomicInteger();
    private final Set<String> classesWithClassLevelMocks = ConcurrentHashMap.newKeySet();

    // Spring Boot application-start adapter
    private final AtomicBoolean bootStartsAdapterUsed = new AtomicBoolean(false);
    private final AtomicInteger bootStartsSucceeded = new AtomicInteger();
    private final AtomicInteger bootStartsFailed = new AtomicInteger();
    private final AtomicLong bootStartsMaxDurationMs = new AtomicLong();
    private final List<String> bootStartFailureRootCauses = Collections.synchronizedList(new ArrayList<String>());

    private final List<String> probeErrors = Collections.synchronizedList(new ArrayList<String>());
    private final AtomicBoolean shutdownHookRegistered = new AtomicBoolean(false);
    private final AtomicBoolean written = new AtomicBoolean(false);

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
            Runtime.getRuntime().addShutdownHook(new Thread(this::flushIfNeverWritten, "testability-probe-flush"));
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

    /**
     * A test that threw: an {@link AssertionError} (also opentest4j's, AssertJ's, Mockito's
     * verification errors and JUnit 4's {@code ComparisonFailure}) means it caught a real
     * problem; anything else means the test itself or its environment broke. {@code thrown}
     * may be null when the engine reports none.
     */
    public void recordTestFailed(String engineId, Throwable thrown) {
        countFor(engineId).incrementAndGet();
        failed.incrementAndGet();
        if (thrown instanceof AssertionError) {
            assertionFailures.incrementAndGet();
        } else {
            errorFailures.incrementAndGet();
        }
        if (thrown != null) {
            failuresByType.computeIfAbsent(thrown.getClass().getName(), key -> new AtomicInteger()).incrementAndGet();
        }
    }

    /**
     * JUnit Platform's ABORTED status: a failing assumption ({@code TestAbortedException},
     * JUnit 4's {@code AssumptionViolatedException}), or an attempt a retrying extension
     * gave up on in favour of another.
     */
    public void recordTestAborted(String engineId) {
        countFor(engineId).incrementAndGet();
        aborted.incrementAndGet();
    }

    public void recordTestSkipped(String engineId) {
        countFor(engineId).incrementAndGet();
        skipped.incrementAndGet();
    }

    /**
     * Only tests that actually ran; skipped tests never reach this. {@code mockKinds}
     * combines {@link TestNodeTracker}'s kind flags. {@code className} may be null.
     */
    public void recordTestMockUsage(boolean springTest, boolean usesMocks, int mockKinds,
            int ownMockCount, int classLevelMockCount, String className) {
        int mockCount = ownMockCount + classLevelMockCount;
        countInMockCountBucket(testsByMockCount, mockCount);
        countInMockCountBucket(testsByOwnMockCount, ownMockCount);
        maxMocksPerTest.accumulateAndGet(mockCount, Math::max);
        if (ownMockCount == 0 && classLevelMockCount > 0) {
            testsUsingOnlyClassLevelMocks.incrementAndGet();
        }
        if (classLevelMockCount > 0 && className != null) {
            classesWithClassLevelMocks.add(className);
        }
        if ((mockKinds & TestNodeTracker.STATIC_MOCK) != 0) {
            testsUsingStaticMocks.incrementAndGet();
        }
        if ((mockKinds & TestNodeTracker.DEEP_STUBS) != 0) {
            testsUsingDeepStubs.incrementAndGet();
        }
        if ((mockKinds & TestNodeTracker.LENIENT) != 0) {
            testsUsingLenientMocks.incrementAndGet();
        }
        if ((mockKinds & TestNodeTracker.SPY) != 0) {
            testsUsingSpies.incrementAndGet();
        }
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

    /**
     * Only tests that actually ran. {@code verificationKinds} combines {@link TestNodeTracker}'s
     * verification flags. Checking for no more interactions also counts as verifying interactions.
     */
    public void recordTestVerifications(int verificationKinds) {
        if (verificationKinds != 0) {
            testsVerifyingInteractions.incrementAndGet();
        }
        if ((verificationKinds & TestNodeTracker.VERIFIES_NO_MORE_INTERACTIONS) != 0) {
            testsVerifyingNoMoreInteractions.incrementAndGet();
        }
    }

    /** A count of 0 isn't in any bucket. */
    private static void countInMockCountBucket(AtomicInteger[] buckets, int mockCount) {
        for (int i = MOCK_COUNT_BUCKET_MINIMUMS.length - 1; i >= 0; i--) {
            if (mockCount >= MOCK_COUNT_BUCKET_MINIMUMS[i]) {
                buckets[i].incrementAndGet();
                return;
            }
        }
    }

    public void recordTestDuration(long durationMs) {
        testsByDuration[durationBucketIndex(durationMs)].incrementAndGet();
        maxTestDurationMs.accumulateAndGet(durationMs, Math::max);
    }

    static int durationBucketIndex(long durationMs) {
        for (int i = DURATION_BUCKET_MINIMUMS_MS.length - 1; i > 0; i--) {
            if (durationMs >= DURATION_BUCKET_MINIMUMS_MS[i]) {
                return i;
            }
        }
        return 0;
    }

    /**
     * Once per test plan. {@code modeDefault} is null when unset. Enabled in any plan counts as
     * enabled; the mode is the last one set.
     */
    public void recordParallelConfiguration(boolean enabled, String modeDefault) {
        if (enabled) {
            parallelEnabled.set(true);
        }
        if (modeDefault != null) {
            parallelModeDefault.set(modeDefault);
        }
    }

    /** How many tests were running when one started, that one included. */
    public void recordConcurrentTests(int running) {
        maxConcurrentTests.accumulateAndGet(running, Math::max);
    }

    /** A test class whose tests run in a fixed order, e.g. {@code @TestMethodOrder(OrderAnnotation.class)}. */
    public void recordFixedOrderClass() {
        fixedOrderClasses.incrementAndGet();
    }

    /** A test that both failed and passed in this JVM: rerun, retried, or repeated with mixed results. */
    public void recordFlakyTest() {
        flakyTests.incrementAndGet();
    }

    /** A test method that retries itself on failure, e.g. JUnit Pioneer's {@code @RetryingTest}. */
    public void recordRetryingTestMethod() {
        retryingTestMethods.incrementAndGet();
    }

    /** A {@code @RepeatedTest} method. */
    public void recordRepeatedTestMethod() {
        repeatedTestMethods.incrementAndGet();
    }

    /** A test class with a time limit: Jupiter's {@code @Timeout}, or a JUnit 4 {@code Timeout} rule. */
    public void recordTimeoutClass() {
        timeoutClasses.incrementAndGet();
    }

    /** A test method with a time limit: Jupiter's {@code @Timeout}, or JUnit 4's {@code @Test(timeout = ...)}. */
    public void recordTimeoutMethod() {
        timeoutMethods.incrementAndGet();
    }

    /**
     * Where a finished test sits: an invocation of a parameterized template, a test a
     * {@code @TestFactory} created, a test in a {@code @Nested} class. One test can be all three.
     */
    public void recordTestShape(boolean parameterized, boolean dynamic, boolean nested) {
        if (parameterized) {
            parameterizedTests.incrementAndGet();
        }
        if (dynamic) {
            dynamicTests.incrementAndGet();
        }
        if (nested) {
            nestedTests.incrementAndGet();
        }
    }

    /** The tags of one finished test, its containers' included, each once. */
    public void recordTestTags(Collection<String> tags) {
        for (String tag : tags) {
            testsByTag.computeIfAbsent(tag, key -> new AtomicInteger()).incrementAndGet();
        }
    }

    public void recordSpringTestClass(boolean isJUnit4, boolean isBootTest, boolean isSliceTest, int mockBeanFieldCount,
            boolean dirtiesContextClass, int dirtiesContextMethodCount) {
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
        if (dirtiesContextClass) {
            dirtiesContextClasses.incrementAndGet();
        }
        dirtiesContextMethods.addAndGet(dirtiesContextMethodCount);
    }

    /**
     * How one Spring test class wires the application to the outside: {@code webEnvironment}
     * is {@code @SpringBootTest}'s (null without one, or an unknown value), and the flags
     * say whether the class swaps its database for an embedded one, connects to containers
     * through {@code @ServiceConnection} or {@code @DynamicPropertySource}, and starts an
     * embedded Kafka broker.
     */
    public void recordSpringTestClassWiring(String webEnvironment, boolean testDatabaseReplaced,
            boolean serviceConnection, boolean dynamicPropertySource, boolean embeddedKafka) {
        for (int i = 0; i < WEB_ENVIRONMENTS.length; i++) {
            if (WEB_ENVIRONMENTS[i].equals(webEnvironment)) {
                bootTestsByWebEnvironment[i].incrementAndGet();
            }
        }
        if (testDatabaseReplaced) {
            testDatabaseReplacedClasses.incrementAndGet();
        }
        if (serviceConnection) {
            serviceConnectionClasses.incrementAndGet();
        }
        if (dynamicPropertySource) {
            dynamicPropertySourceClasses.incrementAndGet();
        }
        if (embeddedKafka) {
            embeddedKafkaClasses.incrementAndGet();
        }
    }

    /** Called once per distinct application context; plain means not started by Spring Boot. */
    public void recordSpringContextLoaded(boolean plain) {
        springAdapterUsed.set(true);
        contextsLoaded.incrementAndGet();
        if (plain) {
            plainContextsLoaded.incrementAndGet();
        }
    }

    /**
     * Time spent loading contexts that weren't cached, once per load. A
     * {@code @ContextHierarchy} load counts once, whatever number of levels it creates.
     */
    public void recordSpringContextLoadTime(long nanos) {
        contextLoadNanos.addAndGet(nanos);
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

    /**
     * Writes the report with everything recorded so far, replacing the previous one. Called
     * whenever a test plan finishes, because one JVM can run several: Surefire reruns failed
     * tests in a new plan ({@code rerunFailingTestsCount}), and runs one plan per test class
     * when it hands classes to forks one at a time. {@code durationMs} runs from the first
     * plan's start to the last plan's end.
     */
    public synchronized void flush() {
        long start = startNanos.get();
        durationMs.set(start >= 0 ? (System.nanoTime() - start) / 1_000_000 : 0);
        writeReport(new FileSink());
        written.set(true);
    }

    /** At shutdown; leaves a report written at a plan's end, and its duration, as it is. */
    private void flushIfNeverWritten() {
        if (!written.get()) {
            flush();
        }
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

    private static AtomicInteger[] newCounters(int size) {
        AtomicInteger[] counters = new AtomicInteger[size];
        for (int i = 0; i < size; i++) {
            counters[i] = new AtomicInteger();
        }
        return counters;
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
        writer.name("runner").value(this.<String>safely(Runner::detect, null));

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

        writer.name("used").beginObject();
        writer.name("testcontainers").value(safely(TestcontainersUsage::dockerClientCreated, false));
        writer.endObject();

        Map<String, Boolean> coverage = safely(CoverageTool::detect, Collections.emptyMap());
        writer.name("coverage").beginObject();
        for (Map.Entry<String, Boolean> entry : coverage.entrySet()) {
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
            writer.name("aborted").value(aborted.get());
            writer.name("failures").beginObject();
            writer.name("assertion").value(assertionFailures.get());
            writer.name("error").value(errorFailures.get());
            writer.name("byType").beginObject();
            for (Map.Entry<String, AtomicInteger> entry : new TreeMap<>(failuresByType).entrySet()) {
                writer.name(entry.getKey()).value(entry.getValue().get());
            }
            writer.endObject();
            writer.endObject();
            writer.name("durationMs").value(durationMs.get());
            writer.name("fixedOrderClasses").value(fixedOrderClasses.get());
            writer.name("flakyTests").value(flakyTests.get());
            writer.name("retryingTestMethods").value(retryingTestMethods.get());
            writer.name("repeatedTestMethods").value(repeatedTestMethods.get());
            writer.name("timeoutClasses").value(timeoutClasses.get());
            writer.name("timeoutMethods").value(timeoutMethods.get());
            writer.name("style").beginObject();
            writer.name("parameterized").value(parameterizedTests.get());
            writer.name("dynamic").value(dynamicTests.get());
            writer.name("nested").value(nestedTests.get());
            writer.endObject();
            writer.name("byTag").beginObject();
            for (Map.Entry<String, AtomicInteger> entry : new TreeMap<>(testsByTag).entrySet()) {
                writer.name(entry.getKey()).value(entry.getValue().get());
            }
            writer.endObject();
            writer.name("byDuration").beginObject();
            for (int i = 0; i < DURATION_BUCKET_NAMES.length; i++) {
                writer.name(DURATION_BUCKET_NAMES[i]).value(testsByDuration[i].get());
            }
            writer.endObject();
            writer.name("maxTestDurationMs").value(maxTestDurationMs.get());
            writer.name("parallel").beginObject();
            writer.name("enabled").value(parallelEnabled.get());
            writer.name("modeDefault").value(parallelModeDefault.get());
            writer.name("maxConcurrentTests").value(maxConcurrentTests.get());
            writer.endObject();
            writer.endObject();

            writer.name("mocking").beginObject();
            writer.name("mockitoObserved").value(MockitoHook.isObserved());
            writer.name("mockitoVerificationsObserved").value(MockitoHook.isVerificationObserved());
            writer.name("mockkObserved").value(MockKObserver.isObserved());
            writer.name("plainTests").value(plainTests.get());
            writer.name("plainTestsUsingMocks").value(plainTestsUsingMocks.get());
            writer.name("springTests").value(springTests.get());
            writer.name("springTestsUsingMocks").value(springTestsUsingMocks.get());
            writer.name("testsUsingStaticMocks").value(testsUsingStaticMocks.get());
            writer.name("testsUsingDeepStubs").value(testsUsingDeepStubs.get());
            writer.name("testsUsingLenientMocks").value(testsUsingLenientMocks.get());
            writer.name("testsUsingSpies").value(testsUsingSpies.get());
            writer.name("testsVerifyingInteractions").value(testsVerifyingInteractions.get());
            writer.name("testsVerifyingNoMoreInteractions").value(testsVerifyingNoMoreInteractions.get());
            writer.name("testsByMockCount").beginObject();
            for (int i = 0; i < MOCK_COUNT_BUCKET_NAMES.length; i++) {
                writer.name(MOCK_COUNT_BUCKET_NAMES[i]).value(testsByMockCount[i].get());
            }
            writer.endObject();
            writer.name("maxMocksPerTest").value(maxMocksPerTest.get());
            writer.name("testsByOwnMockCount").beginObject();
            for (int i = 0; i < MOCK_COUNT_BUCKET_NAMES.length; i++) {
                writer.name(MOCK_COUNT_BUCKET_NAMES[i]).value(testsByOwnMockCount[i].get());
            }
            writer.endObject();
            writer.name("testsUsingOnlyClassLevelMocks").value(testsUsingOnlyClassLevelMocks.get());
            writer.name("classesWithClassLevelMocks").value(classesWithClassLevelMocks.size());
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
            writer.name("contextLoadMs").value(contextLoadNanos.get() / 1_000_000);
            writer.name("dirtiesContextClasses").value(dirtiesContextClasses.get());
            writer.name("dirtiesContextMethods").value(dirtiesContextMethods.get());
            writer.name("bootTestsByWebEnvironment").beginObject();
            for (int i = 0; i < WEB_ENVIRONMENTS.length; i++) {
                writer.name(WEB_ENVIRONMENTS[i]).value(bootTestsByWebEnvironment[i].get());
            }
            writer.endObject();
            writer.name("testDatabaseReplacedClasses").value(testDatabaseReplacedClasses.get());
            writer.name("serviceConnectionClasses").value(serviceConnectionClasses.get());
            writer.name("dynamicPropertySourceClasses").value(dynamicPropertySourceClasses.get());
            writer.name("embeddedKafkaClasses").value(embeddedKafkaClasses.get());
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
