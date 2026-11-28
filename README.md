# Testability Probe

[![CI](https://github.com/barbulescu/testing-maturity/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/barbulescu/testing-maturity/actions/workflows/ci.yml?query=branch%3Amain)
![Coverage](https://raw.githubusercontent.com/barbulescu/testing-maturity/badges/jacoco.svg)
![Branches](https://raw.githubusercontent.com/barbulescu/testing-maturity/badges/branches.svg)
[![Maven Central](https://img.shields.io/maven-central/v/ch.barbulescu.testability/testability-probe)](https://central.sonatype.com/artifact/ch.barbulescu.testability/testability-probe)

A test-scoped dependency that records **facts** about how a JVM project's tests run and writes them as a JSON report. It never decides a maturity level itself. The rules that turn facts into levels live in one central place, so they can change without touching hundreds of builds.

```
build with probe on test classpath ──► testability-probe-<workerId>.json ──► central classifier ──► level
```

The probe registers itself through JUnit Platform and Spring. There is no Java agent and nothing to configure in the test code. For what it can't see, see [Blind spots](#blind-spots).

## Repository layout

The Gradle root project is named `testability-probe`.

```
testing-maturity/
├── .github/      # GitHub Actions workflows: CI, and releases to Maven Central
├── probe/        # the shipped jar: Java 8 bytecode, no runtime dependencies
│   ├── tests/pom.xml   # Maven only: runs the probe's tests (see "Building with Maven")
│   └── src/main/java/ch/barbulescu/testability/probe/
│       ├── core/    # recorder, Safe, JSON writer, file sink, CI context, module identity, build tool,
│       │            # classpath, coverage-tool and Testcontainers-use detection, test-node tracking, the Mockito bridge and the MockK observer
│       ├── junit/   # JUnit Platform TestExecutionListener
│       ├── mockito/ # Mockito MockCreationListener (loaded only when Mockito is present)
│       └── spring/  # Spring TestExecutionListener + Boot ApplicationListener
├── maven-extension/  # shipped too: Maven core extension that adds the probe to every project of a build
├── gitlab-component/ # GitLab CI/CD components that add the probe to Maven and Gradle builds (copy to GitLab)
├── classifier/   # reference CLI that turns a report into a level (not shipped with the probe)
├── harness/      # runs every example in a container, with and without the probe (not shipped)
├── examples/     # standalone projects, one per scenario
└── scripts/      # check-maven-sync.py: the Maven build must match the Gradle build
```

The project builds with Gradle (`build.gradle.kts`, `./gradlew`) or Maven (`pom.xml`, `./mvnw`). Gradle is the main build and the one CI uses for the coverage badges and the examples.

## Using the probe

Add it to the **test runtime classpath** only.

**Maven**

```xml
<dependency>
  <groupId>ch.barbulescu.testability</groupId>
  <artifactId>testability-probe</artifactId>
  <version>0.2.0</version>
  <scope>test</scope>
</dependency>
```

In a corporate parent POM, put it in `<dependencies>`, not `<dependencyManagement>`. `<dependencyManagement>` only pins versions and doesn't add the dependency to any build.

**Gradle**

```kotlin
dependencies {
    testRuntimeOnly("ch.barbulescu.testability:testability-probe:0.2.0")
}
```

Put this in a convention plugin so every project gets it. For repos that use neither a parent POM nor a convention plugin, you can add the dependency with an OpenRewrite `AddDependency` recipe.

### Adding the probe centrally with the Maven extension

`testability-probe-maven-extension` adds the probe to a Maven build without any change to its POMs. Load it as a core extension:

```bash
mvn verify -Dmaven.ext.class.path=/path/to/testability-probe-maven-extension-0.2.0.jar
```

After Maven reads the POMs and before it resolves dependencies, the extension adds the probe as a `test`-scoped dependency to every project except `pom` projects. Surefire and Failsafe then see it as if the POM declared it. The probe resolves through the build's own repositories and `settings.xml` mirror. The extension and the probe are released together, and the extension adds the probe of its own version.

| Setting | Effect |
|---|---|
| `-Dtestability.probe.skip=true` | Turns the extension off |
| `-Dtestability.probe.version=<version>` | Adds this probe version instead of the extension's own |

A project that declares the probe itself keeps its own version. If anything goes wrong, the extension logs a warning and the build goes on without the probe. It needs Maven 3.6.3 or newer on Java 8 or newer, and logs `Testability probe <version> added to <n> of <m> projects`.

To add the probe to every project from GitLab CI, for Maven, the Maven wrapper and the Gradle wrapper alike, use the GitLab CI/CD components in [`gitlab-component/`](gitlab-component/README.md).

A build that runs `mvn dependency:analyze` with `failOnWarning` reports the probe as an unused declared dependency. Such a build needs `-Dtestability.probe.skip=true`, or the probe in the analysis's ignore list.

### Configuration

| Setting | Effect |
|---|---|
| `-Dtestability.probe.dir=<path>` | Output directory for reports (highest priority) |
| env `TESTABILITY_PROBE_DIR` | Output directory if the system property isn't set |
| *(neither)* | Reports go to `java.io.tmpdir` |
| `-Dtestability.probe.debug=true` | Print the probe's own internal exceptions to stderr |

The probe writes nothing to stdout. Each test JVM writes one file named `testability-probe-<workerId>.json`.

In CI, the probe reads these GitLab variables and adds them to the report so it can be joined with other data: `CI_PROJECT_PATH`, `CI_JOB_ID`, `CI_COMMIT_SHA`, `CI_PIPELINE_SOURCE`. It also reads `CI_PROJECT_DIR` to make `moduleDir` relative to the repository root.

## How it works

### Hooks

| Hook | Registered via | Records |
|---|---|---|
| `junit.ProbeTestExecutionListener` | `META-INF/services/org.junit.platform.launcher.TestExecutionListener` | Test counts per engine (from the `[engine:…]` segment of the unique ID), pass/fail/abort/skip, wall time, the exception types failed tests threw, flaky tests, parameterized, dynamic and nested tests (from the other segments of the unique ID), tags, time limits, how many tests ran at once and whether Jupiter was configured to run them in parallel, and calls to `verifyNoMoreInteractions` and the like in the test's bytecode: see `tests.failures`, `tests.flakyTests`, `tests.style`, `tests.byTag`, `tests.timeoutClasses` and `tests.parallel` below, and [Interaction checks](#interaction-checks) |
| `spring.ProbeSpringTestExecutionListener` | `META-INF/spring.factories` | For each Spring test class: JUnit 4 (`@RunWith`) or 5, `@SpringBootTest` (and its `webEnvironment`) or a slice annotation (`org.springframework.boot.test.autoconfigure.*`), how many fields are annotated `@MockBean`/`@MockitoBean`, and how it wires in infrastructure: `@AutoConfigureTestDatabase`, `@ServiceConnection`, `@DynamicPropertySource`, `@EmbeddedKafka`. Also counts each distinct application context the tests ran against, with or without Spring Boot |
| `mockito.ProbeMockCreationListener` | Mockito's `MockitoFramework.addListener`, installed by the JUnit listener on each thread that runs tests | Every mock or spy Mockito creates (and `mockStatic` on Mockito 3.5+), attributed to the test that is running on that thread |
| `mockito.ProbeVerificationListener` | Installed with the listener above, on Mockito versions that have `VerificationListener` (added after 2.1) | Every `verify(...)` and `inOrder.verify(...)`, attributed the same way: see [Interaction checks](#interaction-checks) |
| `core.MockKObserver` | Called by the JUnit listener at every test and container start and finish | MockK mocks (MockK has no listener API): see [MockK](#mockk) |
| `spring.ProbeSpringApplicationListener` | `META-INF/spring.factories` | Spring Boot startup time, from `ApplicationStartingEvent` to `ApplicationReadyEvent` or `ApplicationFailedEvent`. On failure it records the root-cause exception **type**, never the message |

All Spring and Boot types are matched **by class or annotation name**. The jar is compiled against the oldest supported APIs (`junit-platform-launcher` 1.0.x, `spring-test`/`spring-context` 5.0.x, `mockito-core` 2.1) and never against Spring Boot, so the same jar works on Boot 2.x through 4.x, JUnit 5 through 6 and Mockito 2 through 5. Where a signature changed between versions (Mockito's `addListener` returns `void` in 2.1 and `MockitoFramework` later), the probe calls the method reflectively.

### Mock usage

The report counts how many tests used Mockito or MockK, split into plain tests and Spring tests:

- **When a test counts as using mocks.** Mockito's listeners are thread-local, so the JUnit listener installs the probe's listener on each thread when a test or container starts there. Every mock created on that thread is attributed to the node currently running on it:
  - A mock created inside the test method, its `@BeforeEach`, by `MockitoExtension`, or by a field initializer counts for that test only. JUnit creates a new instance of the class for each test, running the field initializers, before it reports that the test started. The probe recognizes that from the call stack (Jupiter's `instantiateTestClass` for a test method, JUnit 4's `createTest`) and holds the mock back for the test that starts next.
  - A mock created at class level counts for every test in the class: `@BeforeAll`, a static field, or a field of a `@TestInstance(PER_CLASS)` class, whose single instance the tests share.
  - A Spring test class that declares `@MockBean`, `@MockitoBean`, `@SpyBean` or `@MockitoSpyBean` fields counts as using mocks even when its context, and so its mocks, came from the cache.
- **When a test counts as a Spring test.** It counts as Spring if the probe's Spring test listener saw its class; otherwise it counts as plain.
- **Only finished tests count.** Skipped tests are left out.

The same attribution also counts tests that use mocks known as test smells:

| Field | Counts tests that | Detected by |
|---|---|---|
| `testsUsingStaticMocks` | mock a static method with `mockStatic`, or with MockK `mockkStatic`, `mockkObject` or `mockkConstructor` | Mockito's `onStaticMockCreated` callback (Mockito 3.5+). For MockK, see [MockK](#mockk) |
| `testsUsingDeepStubs` | create a mock with `RETURNS_DEEP_STUBS`, which hides chains of calls through several objects | The mock's default answer |
| `testsUsingLenientMocks` | create a lenient mock, which hides unused stubbing | The mock's strictness (`getStrictness()` since Mockito 4.6, `isLenient()` before). Leniency set per stubbing with `lenient().when(...)` or for a whole class with `@MockitoSettings` is not on the mock and isn't seen |
| `testsUsingSpies` | spy on a real object or create a partial mock, which mixes the object under test with stubs | A spied instance or `CALLS_REAL_METHODS` as the default answer, a Spring test class declaring `@SpyBean`/`@MockitoSpyBean` fields, or a MockK `spyk` |

`testsByMockCount` sorts the tests that used mocks by how many they used, and `maxMocksPerTest` is the highest count. Many mocks in one test usually means it knows too much about how the code is wired. A test's count adds the mocks it created itself and those created at class level. The nested mocks that deep stubs create along a call chain are not counted.

The two kinds of mocks are also counted apart, because they point at different problems:

| Field | Counts |
|---|---|
| `testsByOwnMockCount` | Like `testsByMockCount`, but only the mocks each test created itself (in the method, `@BeforeEach`, `MockitoExtension` or a field initializer). Many of them means the test sets up a lot of collaborators |
| `testsUsingOnlyClassLevelMocks` | Tests whose mocks all come from their class (`@BeforeAll`, static fields, a `PER_CLASS` instance, or the mock and spy beans of a Spring test class). Mocks shared between tests can carry stubbing and recorded calls from one test into the next |
| `classesWithClassLevelMocks` | Test classes with at least one test that used class-level mocks |

A Spring test's class-level count is at least the number of mock and spy beans its class declares, since a context from Spring's cache creates none while the test runs.

#### Interaction checks

Next to how many mocks a test uses, the report shows whether it checks results or calls:

| Field | Counts tests that | Detected by |
|---|---|---|
| `testsVerifyingInteractions` | check calls on their mocks: `verify(...)`, `inOrder.verify(...)`, BDD's `then(mock).should()`, or one of the checks below. A test with mocks that verifies nothing uses them as stubs and checks its results | Mockito's `VerificationListener`, attributed like mocks. It also sees a `verify` that fails |
| `testsVerifyingNoMoreInteractions` | check that their mocks had no calls other than those verified: `verifyNoMoreInteractions`, `verifyNoInteractions`, `verifyZeroInteractions`, `inOrder.verifyNoMoreInteractions()`, BDD's `shouldHaveNoMoreInteractions()`/`shouldHaveNoInteractions()`, their mockito-kotlin forms, and `verify(mock, only())`. Such a test pins the implementation: any new call breaks it, even when the result stays right | `only()` by the verification listener. The others never reach a Mockito listener, so the probe reads the test's bytecode |

The bytecode is read once per test class, with no bytecode library. A test counts when its method, or a `@BeforeEach`/`@AfterEach` (JUnit 4: `@Before`/`@After`) that runs for it, calls one of those methods. Calls are followed into the methods of the test class, its base classes and the classes enclosing a `@Nested` class, and into the lambdas javac compiles from them. A call made from another class, such as a shared test helper, or through a method reference (`Mockito::verifyNoMoreInteractions`) isn't seen. Neither is a check in a `@BeforeAll` or `@AfterAll`, nor any check made with MockK.

`mockitoVerificationsObserved` is `false` when the probe couldn't install the verification listener (a Mockito older than `VerificationListener`): then `testsVerifyingInteractions` misses every plain `verify`.

#### MockK

MockK has no listener API like Mockito's, so the probe finds MockK mocks after they were created. It needs no MockK or Kotlin dependency and doesn't change how MockK behaves. Tested with MockK 1.12 to 1.14; versions back to 1.9 have the same API.

- **Mocks and spies.** MockK keeps every live mock in a registry. At each test or container start and finish, the probe reads that registry and credits the mocks it hasn't seen yet. Mocks found at a test's start were created while JUnit created the test's instance (field initializers, `@MockK` fields), so they count for that test. Mocks found at its finish were created by the test, its `@BeforeEach` or its `@AfterEach`. MockK's internal mocks don't count: the temporary ones it uses for matching, and the child mocks it creates for a chain stubbed in one go (`every { a.b.c }`).
- **Static, object and constructor mocks** are usually undone before the test finishes (`unmockkAll()` in `@AfterEach`, or `mockkObject(x) { ... }`), which removes them from the registry. MockK records how to undo each one in a per-thread map, so the probe replaces that map on each thread with a copy that behaves the same and counts what goes into it.
- **`PER_CLASS` instances.** With `@TestInstance(PER_CLASS)`, or `junit.jupiter.testinstance.lifecycle.default=per_class` (common in Kotlin projects), the class's single instance is created after the class starts. Its mocks are found at the first test's start but counted for the class.
- **Limits:**
  - A mock created in `@BeforeAll` in a class with one instance per test counts as the first test's own mock. Nothing distinguishes it from the instance's mocks.
  - Before MockK 1.13, mocks don't record the thread that created them, so under parallel execution a mock can be credited to a test running on another thread.
  - Mocks of Spring beans declared with springmockk's `@MockkBean` aren't counted as declared mock beans.

### Guarantees

- **The probe never breaks the build.** Every entry point runs through `Safe.run`, which catches any `Throwable` and records its type in `probeErrors`. Each report field is computed on its own, so one bad value (for example a malformed environment variable) leaves that field empty and the rest of the report is still written. If writing the report fails, the build still succeeds.
- **Production guard.** `ProbeSpringApplicationListener` does nothing unless `org.springframework.test.context.TestContext` is on the classpath. If the jar ever ends up on a runtime classpath, it has no effect.
- **One report per classloader, rewritten at the end of every test plan.** `ProbeRecorder` is a singleton per classloader. The JUnit listener writes the report each time a test plan finishes, replacing the previous one, because one JVM can run several plans: Surefire reruns failed tests in a new plan (`rerunFailingTestsCount`), and with `forkCount` above 1 it hands test classes to the forks one at a time and runs a plan for each. The report is written to a temporary file and then moved over the old one, so a JVM killed while writing leaves the previous report intact. The recorder also registers a shutdown hook the first time either adapter uses it. The hook writes the report only if no plan has finished. This matters for plain `SpringRunner` tests under Surefire's JUnit 4 provider: the JUnit Platform launcher never runs there, so the Spring adapter is the only one that fires.
- **Java 8 bytecode, JDK-only core.** Enforced by `options.release = 8`, by ArchUnit rules (`core` may only import the JDK, `junit` only JUnit Platform, `mockito` only Mockito, `spring` only Spring) and by `ClassFileVersionTest`.

### Module identity

- `moduleDir` is taken from the `basedir` system property, which Surefire sets even with `forkCount=0`. If that isn't set, the probe uses `user.dir`. The path is made relative to `CI_PROJECT_DIR` when that variable is set.
- `workerId` is the PID plus a random hex suffix. Parallel forks (Gradle `maxParallelForks`, Surefire `forkCount > 1`) each write their own report for the same module, so consumers must add them up by `moduleDir`.
- `buildTool` is a best guess. The probe checks whether Surefire's `ForkedBooter` or Gradle's worker main class is on the classpath, and if neither is, it looks at `sun.java.command`.

## Report schema

```json
{
  "schemaVersion": 1,
  "probeVersion": "0.2.0",
  "ci": { "projectPath": "group/payments", "jobId": "123", "commitSha": "abc…", "pipelineSource": "push" },
  "moduleDir": "services/payments",
  "workerId": "4211-a7f3c09e",
  "buildTool": "MAVEN",
  "jvm": { "javaVersion": "1.8.0_402", "vendor": "Eclipse Adoptium" },
  "onClasspath": { "junit4": true, "vintage": false, "testcontainers": true,
                   "wiremock": false, "mockito": true, "mockk": false, "springBoot": true,
                   "h2": true, "hsqldb": false, "derby": false, "embeddedMongo": false, "embeddedKafka": false },
  "used": { "testcontainers": true },
  "versions": { "springBoot": "2.7.18", "junitPlatform": "1.8.2" },
  "coverage": { "jacoco": true },
  "tests": { "byEngine": { "junit-jupiter": 41 }, "succeeded": 40,
             "failed": 1, "aborted": 1, "skipped": 0, "durationMs": 18230,
             "failures": { "assertion": 1, "error": 0,
                           "byType": { "org.opentest4j.AssertionFailedError": 1 } },
             "fixedOrderClasses": 1,
             "flakyTests": 1, "retryingTestMethods": 2, "repeatedTestMethods": 1,
             "timeoutClasses": 1, "timeoutMethods": 3,
             "style": { "parameterized": 12, "dynamic": 3, "nested": 6 },
             "byTag": { "integration": 4, "slow": 2 },
             "byDuration": { "<100ms": 35, "100ms-1s": 5, "1s-10s": 1, "10s+": 0 },
             "maxTestDurationMs": 4210,
             "parallel": { "enabled": true, "modeDefault": "concurrent", "maxConcurrentTests": 4 } },
  "spring": { "testClasses": 12, "junit4Classes": 3, "bootTestClasses": 4,
              "sliceTestClasses": 5, "mockBeanFields": 17,
              "contextsLoaded": 3, "plainContextsLoaded": 1,
              "dirtiesContextClasses": 1, "dirtiesContextMethods": 2,
              "bootTestsByWebEnvironment": { "MOCK": 3, "RANDOM_PORT": 1, "DEFINED_PORT": 0, "NONE": 0 },
              "testDatabaseReplacedClasses": 2, "serviceConnectionClasses": 1,
              "dynamicPropertySourceClasses": 1, "embeddedKafkaClasses": 0 },
  "mocking": { "mockitoObserved": true, "mockitoVerificationsObserved": true, "mockkObserved": false, "plainTests": 25, "plainTestsUsingMocks": 9,
               "springTests": 16, "springTestsUsingMocks": 11,
               "testsUsingStaticMocks": 2, "testsUsingDeepStubs": 1,
               "testsUsingLenientMocks": 3, "testsUsingSpies": 4,
               "testsVerifyingInteractions": 14, "testsVerifyingNoMoreInteractions": 3,
               "testsByMockCount": { "1": 9, "2-3": 6, "4-5": 3, "6-9": 1, "10+": 0 },
               "maxMocksPerTest": 7,
               "testsByOwnMockCount": { "1": 8, "2-3": 4, "4-5": 1, "6-9": 0, "10+": 0 },
               "testsUsingOnlyClassLevelMocks": 6, "classesWithClassLevelMocks": 5 },
  "bootStarts": { "succeeded": 2, "failed": 1, "maxDurationMs": 9400,
                  "failureRootCauses": ["java.net.UnknownHostException"] },
  "probeErrors": []
}
```

How to read it:

- `onClasspath` shows which libraries are **present** on the classpath, not whether any test used them. The probe checks with `Class.forName(name, false, loader)`, so no class is initialized. Testcontainers and WireMock are often inherited from a parent POM and never used. `h2`, `hsqldb`, `derby`, `embeddedMongo` (Flapdoodle) and `embeddedKafka` (`spring-kafka-test`) are in-process stand-ins for real infrastructure. Present means some test could run against them instead of the database or broker production uses.
- `used.testcontainers` says whether Testcontainers talked to Docker in this JVM: `DockerClientFactory` created a Docker client, which every container start and Ryuk go through. It stays `false` when Testcontainers only looked for Docker and found none, as `@Testcontainers(disabledWithoutDocker = true)` does without a daemon. The probe reads the factory's `instance` and `client` fields by name (the same in 1.19 through 2.0; a different layout reads as `false`). It reads them only if Testcontainers already initialized that class, which the `testcontainers` thread group it creates shows, so looking never initializes Testcontainers.
- `coverage.jacoco` says whether JaCoCo measured coverage in this test JVM, not how much code the tests cover. It's `true` when JaCoCo's runtime (`org.jacoco.agent.rt.RT`) can be loaded. That's the case when its agent is attached (the Gradle `jacoco` plugin, `jacoco-maven-plugin`'s `prepare-agent`, or a `-javaagent` in `JAVA_TOOL_OPTIONS`) and with offline instrumentation. It's `false` when the build configures JaCoCo but the agent never reaches the test JVM, for example when Surefire's `argLine` doesn't include `@{argLine}`, or with `forkCount=0`. The coverage numbers themselves are in the build's own JaCoCo report. Other coverage tools aren't detected.
- `tests`, `spring` and `bootStarts` are left out when their adapter never ran. A missing `spring` section means no Spring test listener fired. It does not mean there were zero Spring tests. For example, a custom `@TestExecutionListeners` can replace the default listeners, and then the probe's listener never runs.
- `spring.contextsLoaded` counts distinct application contexts, not test classes. Test classes that share a context through Spring's context cache count it once. A context recreated after `@DirtiesContext` counts again, and every level of a `@ContextHierarchy` counts. `spring.plainContextsLoaded` is the subset that Spring Boot did not start, for example contexts from `@ContextConfiguration` or `@SpringJUnitConfig`. A context that fails to load is not counted here. With Boot, a failed load shows up in `bootStarts.failed`. Without Boot, it shows up only as failed tests.
- `spring.bootTestsByWebEnvironment` sorts `@SpringBootTest` classes by `webEnvironment`. `RANDOM_PORT` and `DEFINED_PORT` start a real server that tests reach over HTTP. `MOCK`, the default, starts none, so requests go through `MockMvc` or `WebTestClient`'s mock, or the tests call beans directly.
- `spring.testDatabaseReplacedClasses` counts Spring test classes where Boot replaces the configured `DataSource` with an embedded database: an `@AutoConfigureTestDatabase` whose `replace` isn't `NONE`, either on the class or carried by a slice such as `@DataJpaTest` or `@JdbcTest`. Together with `onClasspath.h2` it shows tests that run against H2 while production runs another database. The `spring.test.database.replace` property isn't read.
- `spring.serviceConnectionClasses` counts Spring test classes with a `@ServiceConnection` field, including in a base class, and `spring.dynamicPropertySourceClasses` those with a `@DynamicPropertySource` method. Both are the usual ways to point the context at a container. A `@ServiceConnection` on a `@Bean` method in a configuration class isn't seen. `spring.embeddedKafkaClasses` counts classes annotated `@EmbeddedKafka`, directly or through another annotation.
- `spring.dirtiesContextClasses` counts Spring test classes annotated `@DirtiesContext`, including through a base class, and `spring.dirtiesContextMethods` counts test methods annotated with it. Each one throws away a context that the following tests then have to rebuild, which is a common reason for a high `contextsLoaded`.
- `mocking` appears whenever `tests` does. `mockitoObserved` and `mockkObserved` say whether the probe could watch each library. If both are `false`, a zero count tells you nothing: Mockito was absent or older than 2.1, and MockK was absent or couldn't be read. Mockito and MockK are detected; EasyMock and JMockit are not. Attribution is best effort:
  - A mock created on a thread other than the test's isn't counted.
  - Under parallel execution, work stealing can attribute a class-level mock to the wrong class.
  - A mock defined in a `@TestConfiguration`, rather than as a field, counts only for the first test class that loads that context.
  - Mock beans created while Spring loads a context stay with the class, even when the context is loaded to create a test's instance (constructor injection). This holds for JUnit 4's `SpringRunner` too.
- `tests.failed` counts tests that failed, and `tests.aborted` those JUnit Platform reports as `ABORTED`: a failing assumption (`assumeTrue`, JUnit 4's `AssumptionViolatedException`), or an attempt that a retrying extension gave up in favour of another. An aborted test neither failed nor passed.
- `tests.failures` splits failed tests by the type of what they threw. The probe records the type and never the message. `assertion` counts `AssertionError` and its subclasses, such as opentest4j's `AssertionFailedError`, AssertJ's and Hamcrest's errors, Mockito's verification errors and JUnit 4's `ComparisonFailure`: the test caught a real problem. `error` counts everything else, such as `NullPointerException`, `IllegalStateException` or an `IOException`: the test itself or its environment broke before it could check anything. A Spring context that fails to load shows up here as `IllegalStateException`, for every test that needed it. With Spring Boot, its root cause is in `bootStarts.failureRootCauses`. `byType` counts each exact class, so a missing file shows up as `java.nio.file.NoSuchFileException`, not `IOException`.
- `tests.fixedOrderClasses` counts test classes that pin the order their tests run in, which usually means the tests depend on each other: `@TestMethodOrder` with any orderer except `MethodOrderer.Random`, or JUnit 4's `@FixMethodOrder(MethodSorters.NAME_ASCENDING)`. Each class is checked once, by annotation name, when its container starts.
- `tests.flakyTests` counts tests that failed and then passed in this JVM. A test is counted when it passes after a failure, so a test that never passes isn't flaky. The probe sees this in two ways:
  - The same unique ID finishes as failed and later as passed. That's a rerun, as Surefire's `rerunFailingTestsCount` does: it reruns failed tests in a second test plan in the same JVM, and the build passes.
  - A `@RetryingTest` (JUnit Pioneer), `@RepeatedIfExceptionsTest` (rerunner-jupiter) or `@RepeatedTest` has invocations that passed and invocations that didn't. Pioneer and rerunner report a failed attempt as aborted, not failed, so any result other than success counts as a failure here. A `@ParameterizedTest` with mixed results isn't flaky, since its invocations run different inputs.

  Failed attempts stay in the counts: a rerun's in `tests.failed`, and the attempts that Pioneer and rerunner retry in `tests.aborted`. rerunner-jupiter reports the repetitions it doesn't need after a pass as skipped. Gradle's test-retry plugin reruns in a new JVM, so it isn't seen: see [Blind spots](#blind-spots).
- `tests.retryingTestMethods` counts test methods annotated `@RetryingTest` or `@RepeatedIfExceptionsTest`, and `tests.repeatedTestMethods` those annotated `@RepeatedTest`. Both are matched by annotation name when the method's template starts, once per method. A retrying test hides flakiness rather than fixing it. A repeated test is often there to catch it.
- `tests.byEngine` counts tests by the engine that ran them, from the `[engine:…]` segment of their unique IDs. Besides `junit-jupiter` and `junit-vintage`, an engine shows a way of testing: `jqwik` (property-based tests), `archunit` (architecture rules, one test per rule), `cucumber` (Gherkin scenarios), `spock` and `kotest` (specifications). The reference classifier reads these, see [Classifier](#classifier-reference).
- `tests.style` counts finished tests by the segments of their unique IDs. `parameterized` counts the invocations of a test template (`[test-template:…]`), such as a `@ParameterizedTest` with three inputs as three tests, except those of `@RepeatedTest`, `@RetryingTest` and `@RepeatedIfExceptionsTest`, which run one test several times. `dynamic` counts tests a `@TestFactory` created (`[dynamic-test:…]`), also inside a dynamic container. `nested` counts tests in a `@Nested` class (`[nested-class:…]`), at any depth. One test can count in all three. These segment types are Jupiter's, so tests of other engines count in none.
- `tests.byTag` counts finished tests by tag: Jupiter's `@Tag`, JUnit 4's `@Category` (which Vintage reports as a tag named after the category class), or whatever another engine reports. A test counts once for each tag it carries itself or through one of its containers, such as its class or a `@Nested` class. Tags are names from the test code, such as `integration` or `slow`. The probe writes them as they are.
- `tests.timeoutClasses` counts test classes that limit how long their tests may run: Jupiter's `@Timeout` on the class or a base class, or a JUnit 4 `Timeout` rule in a `@Rule` or `@ClassRule` field. `tests.timeoutMethods` counts test methods with Jupiter's `@Timeout` or JUnit 4's `@Test(timeout = …)`, each once, also when it's a template that runs several times. Both are matched by name when the class or method starts. A default timeout set through `junit.jupiter.execution.timeout.default` isn't seen, nor is `@Timeout` on a lifecycle method such as `@BeforeEach`.
- `tests.byDuration` sorts finished tests by how long each took, from its start event to its finish event. That includes `@BeforeEach` and `@AfterEach`, but not class-level setup such as `@BeforeAll` or loading a Spring context. `tests.maxTestDurationMs` is the slowest test. Slow unit tests often wait with `Thread.sleep` instead of for a condition, or do real I/O.
- `tests.parallel` shows whether tests run in parallel inside the JVM. `enabled` is `junit.jupiter.execution.parallel.enabled` and `modeDefault` is `junit.jupiter.execution.parallel.mode.default` (`same_thread` or `concurrent`, `null` when unset, so Jupiter's default `same_thread` applies), as the launcher sees them: from the build tool, system properties or `junit-platform.properties`. They are read once per test plan, so on JUnit Platform before 1.8 they read as unset. `maxConcurrentTests` is the most tests running at once, counted when each test starts, whatever the configuration: with `modeDefault` `same_thread` and `junit.jupiter.execution.parallel.mode.classes.default=concurrent`, or with `@Execution(CONCURRENT)` on a class, tests run at once although the default mode says otherwise. A count above 1 with no failed tests says the tests don't share state that breaks when they run together. Separate JVMs, such as Gradle's `maxParallelForks` or Surefire's `forkCount`, each write their own report, so their tests aren't counted together. Other engines' parallel settings, such as Vintage's, aren't read.
- `tests.skipped` counts disabled tests the way Gradle and Surefire do. JUnit reports a skipped container, such as a `@Disabled` or `@Ignore` class, without any events for its children, so the probe counts the tests below it from the test plan. A skipped `@ParameterizedTest`, `@RepeatedTest` or `@TestFactory` counts as one test, because its invocations only exist at run time. Tests disabled by commenting out the code or the annotation are invisible to the probe.
- `ci.*` fields are `null` outside CI.
- The field name `markers` is reserved for a possible future agent, so adding it won't require a new schema version.

## Classifier (reference)

`classifier/` is a small CLI that reads reports and prints a level. **Its thresholds are only examples.** The probe doesn't depend on them, so an organization is expected to replace them with its own rules.

| Level | Meaning |
|---|---|
| `LEVEL_0` NO_VISIBILITY | No report, or no tests recorded |
| `LEVEL_1` TESTS_RUN | Tests ran, but at least one failed, one was flaky, the probe recorded errors, or nothing passed. A failing assumption (`aborted`) isn't a failure |
| `LEVEL_2` TESTS_PASS_CLEANLY | Tests ran, all passed, and the probe recorded no errors |
| `LEVEL_3` INTEGRATION_AWARE | Level 2, plus Testcontainers in use (`used.testcontainers`), WireMock on the classpath, or at least one `@SpringBootTest` class |

```bash
./gradlew :classifier:installDist
classifier/build/install/classifier/bin/classifier /path/to/testability-probe-*.json
# /path/to/testability-probe-4211-a7f3c09e.json: LEVEL_3 (level 2, plus evidence of real integration testing …)
```

The classifier also prints the test styles a report shows, after the level. They say how a module tests, not how well, so they don't change its level:

| Style | Shown by |
|---|---|
| `property-based` | Tests run by jqwik (`tests.byEngine.jqwik`) |
| `architecture-rules` | Rules run by ArchUnit's engine (`tests.byEngine.archunit`) |
| `bdd` | Scenarios run by Cucumber (`tests.byEngine.cucumber`) |
| `specification` | Tests run by Spock or Kotest (`tests.byEngine.spock`, `tests.byEngine.kotest`) |
| `parameterized`, `dynamic`, `nested` | A count above zero in `tests.style` |

```
/path/to/testability-probe-4211-a7f3c09e.json: LEVEL_2 (tests ran, all passed, …) styles: property-based parameterized nested
```

The classifier looks at one report at a time. To find modules that produced no report at all, compare against a list of all services (see below).

## Blind spots

Some situations can't be seen from inside the test JVM. The probe doesn't try to guess about them. Each one has an example that asserts **no report is produced**. That catches accidental behaviour changes, and it defines exactly what the static checks have to cover.

| Situation | Why there's no report | What covers it instead |
|---|---|---|
| Plain JUnit 4 without Spring (`gradle-java8-junit4-plain`) | No JUnit Platform launcher and no Spring listener, so no hook fires | Static check of dependencies or POM |
| No test sources (`gradle-java17-no-tests`) | The Gradle test task is `NO-SOURCE`, so no test JVM starts | Service list as the denominator |
| Surefire ≤ 2.21 with Jupiter (`maven-java8-surefire-2.12-jupiter`) | Surefire doesn't detect the Jupiter tests, so they silently never run | Static check of the Surefire version |

One situation produces reports but hides what matters in them. Gradle's [test-retry plugin](https://github.com/gradle/test-retry-gradle-plugin) reruns failed tests in a **new test JVM** (`gradle-java17-test-retry`). The first JVM's report has the failure, and the retry's report has the pass. Neither counts a flaky test, and the build passes. Only joining the reports of one build (same `ci.jobId` and `moduleDir`) shows that a retry happened, and the reports don't name tests, so they can't show which test it was.

## Examples and harness

Each directory under `examples/` is a standalone project that uses the tooling of its era. Where it applies, an `expected.json` file lists fields the report must contain (a subset match). Without the probe, the examples are ordinary projects. The probe is added only when the harness passes a property:

- **Gradle:** `-Ptestability.probe.repo=… -Ptestability.probe.groupId=… -Ptestability.probe.artifactId=… -Ptestability.probe.version=…`
- **Maven:** the `testability-probe` profile, activated by `-Dtestability.probe.repo=…` together with the same coordinate properties

| Example | What it tests |
|---|---|
| `maven-java8-boot21-springrunner` | Spring listener under `SpringRunner` with Surefire's JUnit 4 provider; Boot startup recorded |
| `maven-java11-boot27-vintage` | Vintage and Jupiter tests mixed; counts split correctly by engine |
| `gradle-java17-boot3-tc-wiremock` | Testcontainers and WireMock detected; `@Testcontainers(disabledWithoutDocker = true)` avoids needing Docker-in-Docker, so that class is skipped and its test counted as skipped, and `used.testcontainers` is `false` |
| `gradle-java17-integration-wiring` | How Spring tests reach the app and its database: `RANDOM_PORT` next to the default `MOCK`, a `@JdbcTest` that swaps in embedded H2, and one that keeps the configured database (`replace = NONE`) and sets its URL with `@DynamicPropertySource` |
| `gradle-java21-boot4-junit6` | Binary compatibility with the newest stack |
| `gradle-java17-context-fails` | Spring context fails to load; root-cause type captured, no message leaked |
| `gradle-java17-failure-types` | Failed tests split by what they threw: Jupiter's assertion, a plain `assert` and JUnit 4's `ComparisonFailure` count as assertions, a `NullPointerException` and a missing file as errors, and a failing assumption as aborted |
| `gradle-java17-plain-spring-contexts` | Spring without Boot: three test classes over two configurations give two plain contexts |
| `gradle-java17-mocking` | Mock usage counted per test and split between plain and Spring tests: `@Mock` via `MockitoExtension`, an inline mock in one of two methods, a field initializer in a class of three tests (one mock each), a `@BeforeAll` mock shared by two tests, `@MockitoBean` |
| `gradle-java17-mockk` | Kotlin tests with MockK, counted like Mockito's: an inline mock, a field initializer, `@MockK` fields, `mockkObject` undone in `@AfterEach`, `spyk`, a chain stubbed in one go, and a `PER_CLASS` instance whose mock both tests share. `-PmockkVersion=...` runs it on another MockK version |
| `gradle-java17-mock-smells` | Mockito smells counted per test, each next to a test without it: static mocking, deep stubs, lenient mocks, spies, and a test with six mocks |
| `gradle-java17-interaction-tests` | Tests that check results next to tests that check calls: `verify`, and `verifyNoMoreInteractions`, `verifyNoInteractions` and `only()`, which pin every call. Pinning is also found in a Jupiter `@AfterEach` through a helper and in a JUnit 4 `@After` |
| `gradle-java17-spring-smells` | Spring without Boot: `@DirtiesContext` on a class and on a method, next to a class without it |
| `gradle-java17-mock-everything` | `@MockBean` fields counted on a `@SpringBootTest` that doesn't test any real code |
| `gradle-java17-disabled-tests` | `@Disabled` on a method, a class and a nested class, disabled parameterized and repeated tests, Vintage `@Ignore` on a method and a class, and a failing assumption: all disabled tests counted as skipped |
| `gradle-java17-junit-smells` | JUnit-level smells for Jupiter and Vintage: a fixed test order (`@TestMethodOrder`, `@FixMethodOrder`) next to random order and an independent test, and a test that sleeps for over a second |
| `gradle-java17-test-styles` | Test styles: three `@ParameterizedTest` invocations next to a `@RepeatedTest` that doesn't count as parameterized, three `@TestFactory` tests (one in a dynamic container), a `@Nested` class, jqwik and ArchUnit as their own engines, tags from `@Tag` on a class, a nested class and a method and from a JUnit 4 `@Category`, and time limits from `@Timeout`, a JUnit 4 timeout rule and `@Test(timeout)`. The classifier shows the styles and keeps level 2 |
| `gradle-java17-jacoco` | The Gradle `jacoco` plugin attaches JaCoCo's agent: `coverage.jacoco` is `true`. `gradle-java17-junit-smells` and `maven-java11-boot27-vintage` expect `false` |
| `gradle-java17-custom-listeners` | `@TestExecutionListeners` replaces the defaults: `spring` section absent, `tests` section present |
| `maven-java17-surefire-rerun` | Flaky tests: Surefire's `rerunFailingTestsCount` reruns a test that failed once in a second plan, and JUnit Pioneer's `@RetryingTest` and rerunner-jupiter's `@RepeatedIfExceptionsTest` retry an attempt that failed. All three count as flaky; a `@RepeatedTest` that always passes doesn't |
| `gradle-java17-test-retry` | Blind spot: Gradle's test-retry plugin reruns the failed test in a new JVM. Two reports, one with the failure and one with the pass, and no flaky test in either |
| `maven-java17-forkcount0-multimodule` | Tests run inside the Maven JVM: one report per module, each with the correct `moduleDir` |
| `gradle-java17-parallel-execution` | Jupiter's parallel execution enabled in `junit-platform.properties`, methods concurrent: three tests that wait at a barrier for each other run at once |
| `gradle-java17-parallel-forks` | `maxParallelForks = 2`: two reports for the same module with different `workerId`s |
| `gradle-java8-junit4-plain`, `gradle-java17-no-tests`, `maven-java8-surefire-2.12-jupiter` | Blind spots: no report is expected |

The `harness` module has one JUnit 5 test per example. Each test runs the example twice in a container with the matching JDK image (`gradle:9.5.1-jdk17/21`, `maven:3.x-eclipse-temurin-11/17`, `eclipse-temurin:8-jdk`):

1. **baseline**, without the probe
2. **probe**, with the probe loaded from a local Maven repository copied into the container at `/probe/repo`

The harness checks that:

- both runs exit with the same status
- the baseline run produces no report
- the probe run produces the expected number of reports
- each report contains the fields in `expected.json` (subset match)
- `probeErrors` is empty
- where the test asserts one, the classifier returns the expected level

`MavenExtensionTest` builds `maven-java17-forkcount0-multimodule` (Maven 3.9) and `maven-java8-boot21-springrunner` (Maven 3.6.3 on Java 8) a third way: without the profile, with the probe added only by the Maven extension, the way a central CI job adds it.

The container always exits with status 0. The build's real exit code and the report contents are passed back through markers in the container log.

## Building and testing

Requirements: JDK 21 (Gradle toolchains can download it). The harness also needs a Docker-compatible socket at `/var/run/docker.sock`, which Podman machine and GitHub's Ubuntu runners both provide. The harness disables Ryuk.

```bash
./gradlew :probe:build          # unit tests, ArchUnit rules, Java 8 class-file check, JaCoCo report
./gradlew :probe:publishMavenPublicationToProbeRepoRepository   # publishes to build/probe-repo
./gradlew :maven-extension:build   # the Maven extension's unit tests
./gradlew :classifier:test      # also writes a JaCoCo report
./gradlew :harness:test         # publishes the probe first, then runs every example in containers
./gradlew :harness:test --tests '*ParallelForks*'   # a single example
```

The first harness run downloads the images and all dependencies of each example, so it is slow. The examples' containers share one dependency cache in `build/harness-cache` (Gradle, the Maven repository and the Maven wrapper's distributions), so later runs only download what changed. The probe is removed from that cache at the start of each run, so a stale snapshot is never used. In a corporate network, point the images at your private registry, for example through Testcontainers image substitution. Mirror dependency downloads through Nexus by mounting a `settings.xml` or `init.gradle`, and keep the caches between runs.

JaCoCo reports for the probe and the classifier are written to `<module>/build/reports/jacoco/test/` as HTML, XML and CSV. They cover unit tests only. Probe code that runs inside the example containers is not measured.

### Building with Maven

The Maven build mirrors the Gradle build: same coordinates, dependency versions, Java versions and tests. Requirements: JDK 21 or newer (the Maven build has no toolchain and uses the JDK it runs on).

```bash
./mvnw verify -pl '!harness'     # probe and classifier: unit tests, ArchUnit rules, JaCoCo reports
./mvnw verify -DskipHarness      # also builds the harness and publishes the probe to target/probe-repo
./mvnw verify                    # everything, including every example in containers
./mvnw verify -pl harness -am -Dtest=GradleJava17MockingTest -Dsurefire.failIfNoSpecifiedTests=false   # a single example
./mvnw -pl classifier compile exec:java -Dexec.args="/path/to/testability-probe-1.json"
```

Differences from Gradle:

- **The probe has two modules.** Gradle compiles the probe against the oldest supported JUnit Platform (1.0.3), Spring (5.0) and Mockito (2.1) and runs its tests on current versions. One Maven module can't have two versions of the same library, so `probe/pom.xml` builds the jar (against the old versions, `provided`) and `probe/tests/pom.xml` runs `probe/src/test/java` against that jar. Built on JDK 21, the two builds produce the same class files.
- **Output is in `target/`**: the probe repository in `target/probe-repo`, the examples' dependency cache in `target/harness-cache`, JaCoCo reports in `probe/tests/target/site/jacoco-aggregate/` and `classifier/target/site/jacoco/`.
- **Surefire adds the JUnit Vintage engine** to the probe tests' classpath because `junit:junit` is on it. `ClasspathPresenceTest` and `BuildToolTest` read the build tool from a system property the Maven build sets.
- **On JDKs newer than 21**, the probe tests set `-Dnet.bytebuddy.experimental=true` so Mockito 5.14 can still create mocks.

`scripts/check-maven-sync.py` compares the two builds: coordinates, modules, external dependency versions, Java versions and the classifier's main class. CI runs it, so a change to one build must be made in the other too.

### Releasing to Maven Central

The probe and the Maven extension are published, both only from the Gradle build, with the [vanniktech maven-publish plugin](https://vanniktech.github.io/gradle-maven-publish-plugin/central/) to the [Central Portal](https://central.sonatype.com). The namespace `ch.barbulescu` is verified there through a DNS TXT record on `barbulescu.ch`. Central requires sources and javadoc jars, the POM metadata in `probe/build.gradle.kts` and `maven-extension/build.gradle.kts`, and GPG signatures. The build signs only when a key is given, so CI and the harness don't need one.

To release, push a tag on `main`:

```bash
git tag v0.2.0 && git push origin v0.2.0
```

`.github/workflows/release.yml` then checks that the tag is `vX.Y.Z` and on `main`, runs the whole CI on the tagged commit, and publishes the probe and the extension as `X.Y.Z` with `publishAndReleaseToMavenCentral`. The job waits until Central reports the release as published, so a rejected deployment fails it. A published version can never be changed or deleted. Fix mistakes with a new version. Last, the workflow runs `scripts/update-doc-versions.py X.Y.Z` on `main` and pushes the result, so this README and the [GitLab component docs](gitlab-component/README.md) show the version just released. The commit says `[skip ci]`, because CI already ran on the tagged commit. The script changes only version numbers next to the probe's coordinates, not examples like the `git tag` command above.

The build files keep a `-SNAPSHOT` version. A release passes `-PreleaseVersion=X.Y.Z`, which the root `build.gradle.kts` applies to every project, so releasing needs no version commits.

The workflow signs with a CI-only subkey (`1860473F6D68D4AC`) of the signing key `50B97E484C6ACBA1`. GitHub holds only that subkey, under its own random passphrase, so a leak can't be used to sign with or change the primary key. To stop trusting CI, revoke the subkey and upload the key again. The public key must be on `keyserver.ubuntu.com` or `keys.openpgp.org`, where Central looks it up. The repository secrets are `MAVEN_CENTRAL_USERNAME` and `MAVEN_CENTRAL_PASSWORD` (a Central Portal user token), and `SIGNING_KEY`, `SIGNING_KEY_ID` and `SIGNING_KEY_PASSWORD` (the armored subkey, its short id and its passphrase).

To publish from a workstation instead, put the token and key in `~/.gradle/gradle.properties` (never in the repository):

```properties
mavenCentralUsername=<Central Portal user token name>
mavenCentralPassword=<Central Portal user token password>
signingInMemoryKeyId=<last 8 hex digits of the signing key id>
signingInMemoryKeyPassword=<key passphrase>
```

```bash
export ORG_GRADLE_PROJECT_signingInMemoryKey="$(gpg --export-secret-keys --armor <key id>)"

# Dry run: the .asc signatures, -sources.jar and -javadoc.jar appear next to the jar.
./gradlew :probe:publishToMavenLocal -PreleaseVersion=0.2.0
ls ~/.m2/repository/ch/barbulescu/testability/testability-probe/0.2.0/

# Uploads without releasing: click Publish (or Drop) under Deployments in the Portal.
./gradlew :probe:publishToMavenCentral -PreleaseVersion=0.2.0
```

## Continuous integration

`.github/workflows/ci.yml` runs on every push to `main`, on pull requests, on demand, and as the first stage of a release (see [Releasing to Maven Central](#releasing-to-maven-central)):

| Job | What it does |
|---|---|
| Build probe and classifier | `./gradlew :probe:build :maven-extension:build :classifier:test` on JDK 21, uploads the JaCoCo reports, and lists the harness test classes |
| Example `<TestClass>` | One job per harness test class, in parallel: `./gradlew :harness:test --tests <TestClass>`. A new example is picked up when its `*Test.java` is added to the harness |
| Maven build | `scripts/check-maven-sync.py`, then `./mvnw verify -DskipHarness` on JDK 21: probe and classifier tests, and the harness build up to publishing the probe |
| Coverage badge | Only on pushes to `main`. Generates instruction and branch coverage badges from the JaCoCo CSV reports, pushes them to the `badges` branch, and shows the percentages in the run summary |

Maven Central rate limits (HTTP 429) when ~20 jobs download the same files at once, so CI downloads each dependency once and caches it in the GitHub Actions cache:

- **The host Gradle builds' dependencies** (`gradle-deps-<hash of the build files>`): the build job restores them, runs `./gradlew downloadDependencies` when the build files changed, and saves them. The example jobs and the release's publish job only restore them. `setup-gradle`'s own cache is off, because it keeps one cache per job: after a dependency change, every example job downloaded it again.
- **The Maven build's repository** (`maven-deps-<hash of the POMs>`): when the POMs change, it starts from the previous cache, not from an empty repository.
- **Each example's containers** keep `build/harness-cache`, keyed by the example's test class and the examples' build files.

When a job fails, its Gradle test report is uploaded as an artifact. The badges at the top of this README show the CI status of `main` and the SVGs from the `badges` branch.

## Not in scope (yet)

- **A runtime agent.** It would only be worth reconsidering if configuration at the runner level were allowed, and if a large group of projects turned out to be unmeasured and couldn't adopt the dependency.
- **Fake `CI_*` variables in the harness.** The plan calls for them, but `ContainerBuildRunner` currently sets only `TESTABILITY_PROBE_DIR`. The `ci` fields and the `CI_PROJECT_DIR`-relative `moduleDir` are covered only by unit tests.

## License

[Apache License 2.0](LICENSE)
