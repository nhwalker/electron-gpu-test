# functional-tests

End-to-end / functional tests for **electron-gpu-test**.

A Gradle (Groovy build) project written in **Groovy on Java 25**, using:

| Concern            | Library / tool                         | Version  |
| ------------------ | -------------------------------------- | -------- |
| Build              | Gradle (wrapper)                       | 9.5.1    |
| Language           | Apache Groovy                          | 5.0.6    |
| Test framework     | JUnit 5 (Jupiter)                      | 5.14.4   |
| Container fixtures | Testcontainers                         | 2.0.5    |
| Browser automation | Selenium                               | 4.44.0   |
| Reporting          | Allure (+ Gradle plugin 4.0.2)         | 2.35.2   |

> Versions are pinned in `build.gradle` (`ext { ... }`).

## Requirements

- A JDK to run Gradle on. The build pins a **Java 25** toolchain; if Java 25
  isn't installed, the [Foojay toolchain resolver](https://github.com/gradle/foojay-toolchains)
  (configured in `settings.gradle`) downloads it automatically.
- A **Docker daemon** for the container-backed tests. Tests that need Docker are
  annotated `@Testcontainers(disabledWithoutDocker = true)`, so they are
  *skipped* rather than failed when Docker is unavailable.

## Layout

```
functional-tests/
├── build.gradle              # plugins, toolchain, dependencies (versions pinned here)
├── settings.gradle           # project name + Foojay toolchain resolver
├── gradle.properties         # build caching / parallel / configuration cache
└── src/test/
    ├── groovy/.../StackSmokeTest.groovy              # Docker-free wiring checks
    ├── groovy/.../BrowserContainerFunctionalTest.groovy  # nginx + Selenium E2E
    └── resources/web/index.html                       # page served during the E2E test
```

## Run

```sh
# Run all tests (Docker-backed ones skip automatically if Docker is absent).
./gradlew test

# Generate and open the Allure report afterwards.
./gradlew allureReport      # writes build/reports/allure-report
./gradlew allureServe       # builds + serves the report in a browser
```

Raw Allure results land in `build/allure-results`.
