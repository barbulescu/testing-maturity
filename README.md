# Testability Probe

A test-scoped dependency that records **facts** about how a JVM project's tests run and writes them as a JSON report. It never decides a maturity level itself. The rules that turn facts into levels live in one central place, so they can change without touching hundreds of builds.

```
build with probe on test classpath ──► testability-probe-<workerId>.json ──► central classifier ──► level
```

The probe registers itself through JUnit Platform and Spring. There is no Java agent and nothing to configure in the test code. For what it can't see, see [Blind spots](#blind-spots).

## Repository layout

```
testability-probe/
├── probe/        # the shipped jar: Java 8 bytecode, no runtime dependencies
│   └── src/main/java/ch/barbulescu/testability/probe/
│       ├── core/    # recorder, JSON writer, CI context, module identity, file sink
│       ├── junit/   # JUnit Platform TestExecutionListener
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
| `spring.ProbeSpringTestExecutionListener` | `META-INF/spring.factories` | For each Spring test class: JUnit 4 (`@RunWith`) or 5, `@SpringBootTest` or a slice annotation (`org.springframework.boot.test.autoconfigure.*`), and how many fields are annotated `@MockBean`/`@MockitoBean` |
| `spring.ProbeSpringApplicationListener` | `META-INF/spring.factories` | Spring Boot startup time, from `ApplicationStartingEvent` to `ApplicationReadyEvent` or `ApplicationFailedEvent`. On failure it records the root-cause exception **type**, never the message |

All Spring and Boot types are matched **by class or annotation name**. The jar is compiled against the oldest supported APIs (`junit-platform-launcher` 1.0.x, `spring-test`/`spring-context` 5.0.x) and never against Spring Boot, so the same jar works on Boot 2.x through 4.x and JUnit 5 through 6.

### Guarantees

- **The probe never breaks the build.** Every entry point runs through `Safe.run`, which catches any `Throwable` and records its type in `probeErrors`. Each report field is computed on its own, so one bad value (for example a malformed environment variable) leaves that field empty and the rest of the report is still written. If writing the report fails, the build still succeeds.
- **Production guard.** `ProbeSpringApplicationListener` does nothing unless `org.springframework.test.context.TestContext` is on the classpath. If the jar ever ends up on a runtime classpath, it has no effect.
- **One report per classloader, written once.** `ProbeRecorder` is a singleton per classloader and registers a shutdown hook the first time either adapter uses it. The hook writes the report. This matters for plain `SpringRunner` tests under Surefire's JUnit 4 provider: the JUnit Platform launcher never runs there, so the Spring adapter is the only one that fires.
- **Java 8 bytecode, JDK-only core.** Enforced by `options.release = 8`, by ArchUnit rules (`core` may only import the JDK, `junit` only JUnit Platform, `spring` only Spring) and by `ClassFileVersionTest`.

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
             "failed": 1, "skipped": 0, "durationMs": 18230 },
  "spring": { "testClasses": 12, "junit4Classes": 3, "bootTestClasses": 4,
              "sliceTestClasses": 5, "mockBeanFields": 17 },
  "bootStarts": { "succeeded": 2, "failed": 1, "maxDurationMs": 9400,
                  "failureRootCauses": ["java.net.UnknownHostException"] },
  "probeErrors": []
}
```

How to read it:

- `onClasspath` shows which libraries are **present** on the classpath, not whether any test used them. The probe checks with `Class.forName(name, false, loader)`, so no class is initialized.
- `tests`, `spring` and `bootStarts` are left out when their adapter never ran. A missing `spring` section means no Spring test listener fired. It does not mean there were zero Spring tests. For example, a custom `@TestExecutionListeners` can replace the default listeners, and then the probe's listener never runs.
- JUnit Platform's `ABORTED` status is counted under `failed`.
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
| `gradle-java17-boot3-tc-wiremock` | Testcontainers and WireMock detected; `@Testcontainers(disabledWithoutDocker = true)` avoids needing Docker-in-Docker |
| `gradle-java21-boot4-junit6` | Binary compatibility with the newest stack |
| `gradle-java17-context-fails` | Spring context fails to load; root-cause type captured, no message leaked |
| `gradle-java17-mock-everything` | `@MockBean` fields counted on a `@SpringBootTest` that doesn't test any real code |
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

Requirements: JDK 21 (Gradle toolchains can download it). The harness also needs a Docker-compatible socket at `/var/run/docker.sock`, which Podman machine provides. The harness disables Ryuk.

```bash
./gradlew :probe:build          # unit tests, ArchUnit rules, Java 8 class-file check
./gradlew :probe:publishMavenPublicationToProbeRepoRepository   # publishes to build/probe-repo
./gradlew :classifier:test
./gradlew :harness:test         # publishes the probe first, then runs every example in containers
./gradlew :harness:test --tests '*ParallelForks*'   # a single example
```

The first harness run downloads the images and all dependencies of each example, so it is slow. In a corporate network, point the images at your private registry, for example through Testcontainers image substitution. Mirror dependency downloads through Nexus by mounting a `settings.xml` or `init.gradle`, and keep the caches between runs.

## Not in scope (yet)

- **A runtime agent.** It would only be worth reconsidering if configuration at the runner level were allowed, and if a large group of projects turned out to be unmeasured and couldn't adopt the dependency.
- **Fake `CI_*` variables in the harness.** The plan calls for them, but `ContainerBuildRunner` currently sets only `TESTABILITY_PROBE_DIR`. The `ci` fields and the `CI_PROJECT_DIR`-relative `moduleDir` are covered only by unit tests.
