# GitLab CI/CD component: testability probe

Two GitLab CI/CD components that run a project's build with the [testability probe](../README.md) on the test classpath, without any change to the project's build files:

| Component | For | How the probe gets in |
|---|---|---|
| `maven` | `mvn` and the Maven wrapper `./mvnw` | Downloads the [Maven extension](../README.md#adding-the-probe-centrally-with-the-maven-extension) and loads it through `MAVEN_OPTS` |
| `gradle` | the Gradle wrapper `./gradlew` and `gradle` | Writes an init script and passes it with `--init-script` |

Each component adds one job. The job runs the build, writes the probe's reports to `.testability-reports/`, and keeps them as job artifacts, also when the build fails.

## Setting up the component project

The components live in a GitLab project of their own, for example `platform/testability-probe-ci`. Projects then include them from there.

1. Create the project and copy this directory into it, so it has `templates/maven.yml`, `templates/gradle.yml` and this `README.md` at its root.
2. Make the project visible to the projects that will use it: internal, or private with those projects' members as at least reporters.
3. Optional, to list it in the CI/CD Catalog: in **Settings > General > Visibility, project features, permissions**, turn on **CI/CD Catalog project**, and add a `.gitlab-ci.yml` that creates a release for each tag:

   ```yaml
   create-release:
     stage: deploy
     image: registry.gitlab.com/gitlab-org/release-cli:latest
     rules:
       - if: $CI_COMMIT_TAG =~ /^\d+\.\d+\.\d+$/
     script: echo "Releasing $CI_COMMIT_TAG"
     release:
       tag_name: $CI_COMMIT_TAG
       description: Release $CI_COMMIT_TAG
   ```

4. Tag the first version: `git tag 1.0.0 && git push origin 1.0.0`.

Component versions (`@1.0.0` below) are the component project's own tags. They are not probe versions, which the `version` input selects. Without the Catalog, a component can still be included from any tag, branch or commit SHA.

## Using the components

In the project's `.gitlab-ci.yml`, include the component that matches the build. The examples use `$CI_SERVER_FQDN/platform/testability-probe-ci`; replace it with your component project's path.

The component adds a job named `test`. If the project already has a job of that name, the project's own keys win over the component's (GitLab merges the two). Either remove the project's job or give the component's job another name with `job-name`.

### Maven

```yaml
include:
  - component: $CI_SERVER_FQDN/platform/testability-probe-ci/maven@1.0.0
    inputs:
      image: maven:3.9-eclipse-temurin-21
      args: -B verify
```

### Maven wrapper

Use an image with only a JDK. `./mvnw` downloads the Maven version the project pins in `.mvn/wrapper/maven-wrapper.properties`.

```yaml
include:
  - component: $CI_SERVER_FQDN/platform/testability-probe-ci/maven@1.0.0
    inputs:
      image: eclipse-temurin:17-jdk
      maven: ./mvnw
      args: -B verify
```

### Gradle wrapper

```yaml
include:
  - component: $CI_SERVER_FQDN/platform/testability-probe-ci/gradle@1.0.0
    inputs:
      image: eclipse-temurin:17-jdk
      args: check
```

For a Gradle installed in the image instead of the wrapper, set `gradle: gradle` and use an image such as `gradle:9-jdk21`.

### Behind a company mirror

The Maven component downloads the extension with Maven, so the mirror and credentials in the job's `settings.xml` apply. When the settings file is not the image's default `~/.m2/settings.xml`, pass it to both the download and the build:

```yaml
    inputs:
      fetch-args: -s ci_settings.xml
      args: -B -s ci_settings.xml verify
```

The download runs Maven on a throwaway POM in `/tmp`, so the project's `.mvn/maven.config` doesn't apply to it. Anything the download needs from there goes into `fetch-args`.

The probe itself resolves like any other dependency of the build: in Maven through the same mirror, in Gradle through each project's own repositories.

## Inputs

**Both components**

| Input | Default | Meaning |
|---|---|---|
| `job-name` | `test` | Name of the job the component adds |
| `stage` | `test` | Stage of that job |
| `image` | `maven:3.9-eclipse-temurin-21` / `eclipse-temurin:21-jdk` | Image of that job |
| `args` | `-B verify` / `check` | Arguments of the build |
| `version` | `latest` | Probe version: `latest` for the latest release, or a released version |
| `reports-dir` | `.testability-reports` | Where reports go, relative to the project directory |

**`maven` only**

| Input | Default | Meaning |
|---|---|---|
| `maven` | `mvn` | `mvn`, or `./mvnw` for the Maven wrapper |
| `fetch-args` | *(none)* | Extra Maven arguments for downloading the extension, such as `-s ci_settings.xml` |

**`gradle` only**

| Input | Default | Meaning |
|---|---|---|
| `gradle` | `./gradlew` | `./gradlew`, or `gradle` for a Gradle in the image |

## Versions

With the default `version: latest`, every job uses the latest probe release, so a new probe release reaches all projects without any change. The Maven component resolves `latest` through Maven, as the version range `[0,)`. It uses a local repository of its own, so a snapshot left in the job's Maven repository can't be picked. The Gradle component uses Gradle's `latest.release`, which Gradle caches for 24 hours.

To pin a version, for example to roll out a new release step by step:

```yaml
    inputs:
      version: 0.2.0
```

## Turning the probe off

Add `-Dtestability.probe.skip=true` to `args`. The build then runs as if the component weren't there, but still in the component's job.

## When the probe can't be added

The probe must never break a build:

- **Maven:** if the extension can't be downloaded, the job prints a warning and runs the build without the probe. If the extension fails inside Maven, it logs a warning and the build goes on.
- **Gradle:** the probe is an ordinary dependency, so a repository that doesn't have it fails the build, as any missing dependency would.

Builds that check their dependencies strictly can reject the probe:

| Build | What happens | What to do |
|---|---|---|
| Maven with `dependency:analyze` and `failOnWarning` | The probe is an unused declared dependency | Add it to the analysis's ignored dependencies, or skip the probe |
| Gradle with dependency locking | The probe isn't in the lock state | Skip the probe, or add it to the project's build |
| Gradle with dependency verification (`verification-metadata.xml`) | The probe's checksums are unknown | Skip the probe, or add the checksums |

The Gradle init script adds the probe to projects that apply the `java` plugin, which includes `java-library`, Spring Boot and Kotlin JVM projects. Android projects are not covered. The Maven extension needs Maven 3.6.3 or newer.
