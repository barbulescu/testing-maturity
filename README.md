# Testability Probe

[![CI](https://github.com/barbulescu/testing-maturity/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/barbulescu/testing-maturity/actions/workflows/ci.yml?query=branch%3Amain)
![Coverage](https://raw.githubusercontent.com/barbulescu/testing-maturity/badges/jacoco.svg)
![Branches](https://raw.githubusercontent.com/barbulescu/testing-maturity/badges/branches.svg)

A test-scoped dependency that records **facts** about how a JVM project's tests run and writes them as a JSON report. It never decides a maturity level itself. The rules that turn facts into levels live in one central place, so they can change without touching hundreds of builds.

```
build with probe on test classpath ──► testability-probe-<workerId>.json ──► central classifier ──► level
```

The probe registers itself through JUnit Platform and Spring. There is no Java agent and nothing to configure in the test code. For what it can't see, see [Blind spots](#blind-spots).

## Repository layout

The Gradle root project is named `testability-probe`.

```
testing-maturity/
├── .github/      # GitHub Actions CI workflow
├── probe/        # the shipped jar: Java 8 bytecode, no runtime dependencies
│   └── src/main/java/ch/barbulescu/testability/probe/
│       ├── core/    # recorder, Safe, JSON writer, file sink, CI context, module identity, build tool
│       │            # and classpath detection, test-node tracking and the Mockito bridge
│       ├── junit/   # JUnit Platform TestExecutionListener
│       ├── mockito/ # Mockito MockCreationListener (loaded only when Mockito is present)
│       └── spring/  # Spring TestExecutionListener + Boot ApplicationListener
├── classifier/   # reference CLI that turns a report into a level (not shipped with the probe)
├── harness/      # runs every example in a container, with and without the probe (not shipped)
└── examples/     # standalone projects, one per scenario
```

## Using the probe

Add it to the **test runtime classpath** only.

**Maven**

```xml
<dependency>
  <groupId>ch.barbulescu.testability</groupId>
  <artifactId>testability-probe</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  <scope>test</scope>
</dependency>
```

In a corporate parent POM, put it in `<dependencies>`, not `<dependencyManagement>`. `<dependencyManagement>` only pins versions and doesn't add the dependency to any build.

**Gradle**

```kotlin
dependencies {
    testRuntimeOnly("ch.barbulescu.testability:testability-probe:0.1.0-SNAPSHOT")
}
```

Put this in a convention plugin so every project gets it. For repos that use neither a parent POM nor a convention plugin, you can add the dependency with an OpenRewrite `AddDependency` recipe.

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
| `junit.ProbeTestExecutionListener` | `META-INF/services/org.junit.platform.launcher.TestExecutionListener` | Test counts per engine (from the `[engine:…]` segment of the unique ID), pass/fail/skip, wall time |
| `spring.ProbeSpringTestExecutionListener` | `META-INF/spring.factories` | For each Spring test class: JUnit 4 (`@RunWith`) or 5, `@SpringBootTest` or a slice annotation (`org.springframework.boot.test.autoconfigure.*`), and how many fields are annotated `@MockBean`/`@MockitoBean`. Also counts each distinct application context the tests ran against, with or without Spring Boot |
| `mockito.ProbeMockCreationListener` | Mockito's `MockitoFramework.addListener`, installed by the JUnit listener on each thread that runs tests | Every mock or spy Mockito creates (and `mockStatic` on Mockito 3.5+), attributed to the test that is running on that thread |
| `spring.ProbeSpringApplicationListener` | `META-INF/spring.factories` | Spring Boot startup time, from `ApplicationStartingEvent` to `ApplicationReadyEvent` or `ApplicationFailedEvent`. On failure it records the root-cause exception **type**, never the message |

All Spring and Boot types are matched **by class or annotation name**. The jar is compiled against the oldest supported APIs (`junit-platform-launcher` 1.0.x, `spring-test`/`spring-context` 5.0.x, `mockito-core` 2.1) and never against Spring Boot, so the same jar works on Boot 2.x through 4.x, JUnit 5 through 6 and Mockito 2 through 5. Where a signature changed between versions (Mockito's `addListener` returns `void` in 2.1 and `MockitoFramework` later), the probe calls the method reflectively.

### Mock usage

The report counts how many tests used Mockito, split into plain tests and Spring tests:

- **When a test counts as using mocks.** Mockito's listeners are thread-local, so the JUnit listener installs the probe's listener on each thread when a test or container starts there. Every mock created on that thread is attributed to the node currently running on it:
  - A mock created inside the test method, its `@BeforeEach`, or by `MockitoExtension` counts for that test only.
  - A mock created at class level counts for every test in the class. That includes a field initializer, which runs before the method's start event, and `@BeforeAll`.
  - A Spring test class that declares `@MockBean`, `@MockitoBean`, `@SpyBean` or `@MockitoSpyBean` fields counts as using mocks even when its context, and so its mocks, came from the cache.
- **When a test counts as a Spring test.** It counts as Spring if the probe's Spring test listener saw its class; otherwise it counts as plain.
- **Only finished tests count.** Skipped tests are left out.

The same attribution also counts tests that use mocks known as test smells:

| Field | Counts tests that | Detected by |
|---|---|---|
| `testsUsingStaticMocks` | mock a static method with `mockStatic` | Mockito's `onStaticMockCreated` callback (Mockito 3.5+) |
| `testsUsingDeepStubs` | create a mock with `RETURNS_DEEP_STUBS`, which hides chains of calls through several objects | The mock's default answer |
| `testsUsingLenientMocks` | create a lenient mock, which hides unused stubbing | The mock's strictness (`getStrictness()` since Mockito 4.6, `isLenient()` before). Leniency set per stubbing with `lenient().when(...)` or for a whole class with `@MockitoSettings` is not on the mock and isn't seen |
| `testsUsingSpies` | spy on a real object or create a partial mock, which mixes the object under test with stubs | A spied instance or `CALLS_REAL_METHODS` as the default answer, or a Spring test class declaring `@SpyBean`/`@MockitoSpyBean` fields |

`testsByMockCount` sorts the tests that used mocks by how many they used, and `maxMocksPerTest` is the highest count. Many mocks in one test usually means it knows too much about how the code is wired. A test's count includes mocks created at class level, and a Spring test counts at least the mock and spy beans its class declares. The nested mocks that deep stubs create along a call chain are not counted.

### Guarantees

- **The probe never breaks the build.** Every entry point runs through `Safe.run`, which catches any `Throwable` and records its type in `probeErrors`. Each report field is computed on its own, so one bad value (for example a malformed environment variable) leaves that field empty and the rest of the report is still written. If writing the report fails, the build still succeeds.
- **Production guard.** `ProbeSpringApplicationListener` does nothing unless `org.springframework.test.context.TestContext` is on the classpath. If the jar ever ends up on a runtime classpath, it has no effect.
- **One report per classloader, written once.** `ProbeRecorder` is a singleton per classloader and registers a shutdown hook the first time either adapter uses it. The hook writes the report. This matters for plain `SpringRunner` tests under Surefire's JUnit 4 provider: the JUnit Platform launcher never runs there, so the Spring adapter is the only one that fires.
- **Java 8 bytecode, JDK-only core.** Enforced by `options.release = 8`, by ArchUnit rules (`core` may only import the JDK, `junit` only JUnit Platform, `mockito` only Mockito, `spring` only Spring) and by `ClassFileVersionTest`.

### Module identity

- `moduleDir` is taken from the `basedir` system property, which Surefire sets even with `forkCount=0`. If that isn't set, the probe uses `user.dir`. The path is made relative to `CI_PROJECT_DIR` when that variable is set.
- `workerId` is the PID plus a random hex suffix. Parallel forks (Gradle `maxParallelForks`, Surefire `forkCount > 1`) each write their own report for the same module, so consumers must add them up by `moduleDir`.
- `buildTool` is a best guess. The probe checks whether Surefire's `ForkedBooter` or Gradle's worker main class is on the classpath, and if neither is, it looks at `sun.java.command`.

## Report schema

```json
{
  "schemaVersion": 1,
  "probeVersion": "0.1.0",
  "ci": { "projectPath": "group/payments", "jobId": "123", "commitSha": "abc…", "pipelineSource": "push" },
  "moduleDir": "services/payments",
  "workerId": "4211-a7f3c09e",
  "buildTool": "MAVEN",
  "jvm": { "javaVersion": "1.8.0_402", "vendor": "Eclipse Adoptium" },
  "onClasspath": { "junit4": true, "vintage": false, "testcontainers": true,
                   "wiremock": false, "mockito": true, "springBoot": true },
  "versions": { "springBoot": "2.7.18", "junitPlatform": "1.8.2" },
  "tests": { "byEngine": { "junit-jupiter": 41 }, "succeeded": 40,
             "failed": 1, "skipped": 0, "durationMs": 18230,
             "fixedOrderClasses": 1 },
  "spring": { "testClasses": 12, "junit4Classes": 3, "bootTestClasses": 4,
              "sliceTestClasses": 5, "mockBeanFields": 17,
              "contextsLoaded": 3, "plainContextsLoaded": 1,
              "dirtiesContextClasses": 1, "dirtiesContextMethods": 2 },
  "mocking": { "mockitoObserved": true, "plainTests": 25, "plainTestsUsingMocks": 9,
               "springTests": 16, "springTestsUsingMocks": 11,
               "testsUsingStaticMocks": 2, "testsUsingDeepStubs": 1,
               "testsUsingLenientMocks": 3, "testsUsingSpies": 4,
               "testsByMockCount": { "1": 9, "2-3": 6, "4-5": 3, "6-9": 1, "10+": 0 },
               "maxMocksPerTest": 7 },
  "bootStarts": { "succeeded": 2, "failed": 1, "maxDurationMs": 9400,
                  "failureRootCauses": ["java.net.UnknownHostException"] },
  "probeErrors": []
}
```

How to read it:

- `onClasspath` shows which libraries are **present** on the classpath, not whether any test used them. The probe checks with `Class.forName(name, false, loader)`, so no class is initialized.
- `tests`, `spring` and `bootStarts` are left out when their adapter never ran. A missing `spring` section means no Spring test listener fired. It does not mean there were zero Spring tests. For example, a custom `@TestExecutionListeners` can replace the default listeners, and then the probe's listener never runs.
- `spring.contextsLoaded` counts distinct application contexts, not test classes. Test classes that share a context through Spring's context cache count it once. A context recreated after `@DirtiesContext` counts again, and every level of a `@ContextHierarchy` counts. `spring.plainContextsLoaded` is the subset that Spring Boot did not start, for example contexts from `@ContextConfiguration` or `@SpringJUnitConfig`. A context that fails to load is not counted here. With Boot, a failed load shows up in `bootStarts.failed`. Without Boot, it shows up only as failed tests.
- `spring.dirtiesContextClasses` counts Spring test classes annotated `@DirtiesContext`, including through a base class, and `spring.dirtiesContextMethods` counts test methods annotated with it. Each one throws away a context that the following tests then have to rebuild, which is a common reason for a high `contextsLoaded`.
- `mocking` appears whenever `tests` does. If `mockitoObserved` is `false`, Mockito was absent or older than 2.1, so a zero count tells you nothing. Only Mockito is detected; EasyMock, MockK and JMockit are not. Attribution is best effort:
  - A mock created on a thread other than the test's isn't counted.
  - Under parallel execution, work stealing can attribute a class-level mock to the wrong class.
  - A mock defined in a `@TestConfiguration`, rather than as a field, counts only for the first test class that loads that context.
- JUnit Platform's `ABORTED` status (for example a failing assumption) is counted under `failed`.
- `tests.fixedOrderClasses` counts test classes that pin the order their tests run in, which usually means the tests depend on each other: `@TestMethodOrder` with any orderer except `MethodOrderer.Random`, or JUnit 4's `@FixMethodOrder(MethodSorters.NAME_ASCENDING)`. Each class is checked once, by annotation name, when its container starts.
- `tests.skipped` counts disabled tests the way Gradle and Surefire do. JUnit reports a skipped container, such as a `@Disabled` or `@Ignore` class, without any events for its children, so the probe counts the tests below it from the test plan. A skipped `@ParameterizedTest`, `@RepeatedTest` or `@TestFactory` counts as one test, because its invocations only exist at run time. Tests disabled by commenting out the code or the annotation are invisible to the probe.
- `ci.*` fields are `null` outside CI.
- The field name `markers` is reserved for a possible future agent, so adding it won't require a new schema version.

## Classifier (reference)

`classifier/` is a small CLI that reads reports and prints a level. **Its thresholds are only examples.** The probe doesn't depend on them, so an organization is expected to replace them with its own rules.

| Level | Meaning |
|---|---|
| `LEVEL_0` NO_VISIBILITY | No report, or no tests recorded |
| `LEVEL_1` TESTS_RUN | Tests ran, but at least one failed, the probe recorded errors, or nothing passed |
| `LEVEL_2` TESTS_PASS_CLEANLY | Tests ran, all passed, and the probe recorded no errors |
| `LEVEL_3` INTEGRATION_AWARE | Level 2, plus Testcontainers or WireMock on the classpath, or at least one `@SpringBootTest` class |

```bash
./gradlew :classifier:installDist
classifier/build/install/classifier/bin/classifier /path/to/testability-probe-*.json
# /path/to/testability-probe-4211-a7f3c09e.json: LEVEL_3 (level 2, plus evidence of real integration testing …)
```

The classifier looks at one report at a time. To find modules that produced no report at all, compare against a list of all services (see below).

## Blind spots

Some situations can't be seen from inside the test JVM. The probe doesn't try to guess about them. Each one has an example that asserts **no report is produced**. That catches accidental behaviour changes, and it defines exactly what the static checks have to cover.

| Situation | Why there's no report | What covers it instead |
|---|---|---|
| Plain JUnit 4 without Spring (`gradle-java8-junit4-plain`) | No JUnit Platform launcher and no Spring listener, so no hook fires | Static check of dependencies or POM |
| No test sources (`gradle-java17-no-tests`) | The Gradle test task is `NO-SOURCE`, so no test JVM starts | Service list as the denominator |
| Surefire ≤ 2.21 with Jupiter (`maven-java8-surefire-2.12-jupiter`) | Surefire doesn't detect the Jupiter tests, so they silently never run | Static check of the Surefire version |

## Examples and harness

Each directory under `examples/` is a standalone project that uses the tooling of its era. Where it applies, an `expected.json` file lists fields the report must contain (a subset match). Without the probe, the examples are ordinary projects. The probe is added only when the harness passes a property:

- **Gradle:** `-Ptestability.probe.repo=… -Ptestability.probe.groupId=… -Ptestability.probe.artifactId=… -Ptestability.probe.version=…`
- **Maven:** the `testability-probe` profile, activated by `-Dtestability.probe.repo=…` together with the same coordinate properties

| Example | What it tests |
|---|---|
| `maven-java8-boot21-springrunner` | Spring listener under `SpringRunner` with Surefire's JUnit 4 provider; Boot startup recorded |
| `maven-java11-boot27-vintage` | Vintage and Jupiter tests mixed; counts split correctly by engine |
| `gradle-java17-boot3-tc-wiremock` | Testcontainers and WireMock detected; `@Testcontainers(disabledWithoutDocker = true)` avoids needing Docker-in-Docker, so that class is skipped and its test counted as skipped |
| `gradle-java21-boot4-junit6` | Binary compatibility with the newest stack |
| `gradle-java17-context-fails` | Spring context fails to load; root-cause type captured, no message leaked |
| `gradle-java17-plain-spring-contexts` | Spring without Boot: three test classes over two configurations give two plain contexts |
| `gradle-java17-mocking` | Mock usage counted per test and split between plain and Spring tests: `@Mock` via `MockitoExtension`, an inline mock in one of two methods, a field initializer, `@MockitoBean` |
| `gradle-java17-mock-smells` | Mockito smells counted per test, each next to a test without it: static mocking, deep stubs, lenient mocks, spies, and a test with six mocks |
| `gradle-java17-spring-smells` | Spring without Boot: `@DirtiesContext` on a class and on a method, next to a class without it |
| `gradle-java17-mock-everything` | `@MockBean` fields counted on a `@SpringBootTest` that doesn't test any real code |
| `gradle-java17-disabled-tests` | `@Disabled` on a method, a class and a nested class, disabled parameterized and repeated tests, Vintage `@Ignore` on a method and a class, and a failing assumption: all disabled tests counted as skipped |
| `gradle-java17-junit-smells` | JUnit-level smells for Jupiter and Vintage: a fixed test order (`@TestMethodOrder`, `@FixMethodOrder`) next to random order and an independent test |
| `gradle-java17-custom-listeners` | `@TestExecutionListeners` replaces the defaults: `spring` section absent, `tests` section present |
| `maven-java17-forkcount0-multimodule` | Tests run inside the Maven JVM: one report per module, each with the correct `moduleDir` |
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

The container always exits with status 0. The build's real exit code and the report contents are passed back through markers in the container log.

## Building and testing

Requirements: JDK 21 (Gradle toolchains can download it). The harness also needs a Docker-compatible socket at `/var/run/docker.sock`, which Podman machine and GitHub's Ubuntu runners both provide. The harness disables Ryuk.

```bash
./gradlew :probe:build          # unit tests, ArchUnit rules, Java 8 class-file check, JaCoCo report
./gradlew :probe:publishMavenPublicationToProbeRepoRepository   # publishes to build/probe-repo
./gradlew :classifier:test      # also writes a JaCoCo report
./gradlew :harness:test         # publishes the probe first, then runs every example in containers
./gradlew :harness:test --tests '*ParallelForks*'   # a single example
```

The first harness run downloads the images and all dependencies of each example, so it is slow. In a corporate network, point the images at your private registry, for example through Testcontainers image substitution. Mirror dependency downloads through Nexus by mounting a `settings.xml` or `init.gradle`, and keep the caches between runs.

JaCoCo reports for the probe and the classifier are written to `<module>/build/reports/jacoco/test/` as HTML, XML and CSV. They cover unit tests only. Probe code that runs inside the example containers is not measured.

## Continuous integration

`.github/workflows/ci.yml` runs on every push to `main`, on pull requests, and on demand:

| Job | What it does |
|---|---|
| Build probe and classifier | `./gradlew :probe:build :classifier:test` on JDK 21, uploads the JaCoCo reports, and lists the harness test classes |
| Example `<TestClass>` | One job per harness test class, in parallel: `./gradlew :harness:test --tests <TestClass>`. A new example is picked up when its `*Test.java` is added to the harness |
| Coverage badge | Only on pushes to `main`. Generates instruction and branch coverage badges from the JaCoCo CSV reports, pushes them to the `badges` branch, and shows the percentages in the run summary |

When a job fails, its Gradle test report is uploaded as an artifact. The badges at the top of this README show the CI status of `main` and the SVGs from the `badges` branch.

## Not in scope (yet)

- **A runtime agent.** It would only be worth reconsidering if configuration at the runner level were allowed, and if a large group of projects turned out to be unmeasured and couldn't adopt the dependency.
- **Fake `CI_*` variables in the harness.** The plan calls for them, but `ContainerBuildRunner` currently sets only `TESTABILITY_PROBE_DIR`. The `ci` fields and the `CI_PROJECT_DIR`-relative `moduleDir` are covered only by unit tests.
