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
    ├── groovy/.../StackSmokeTest.groovy                   # Docker-free wiring checks
    ├── groovy/.../BrowserContainerFunctionalTest.groovy   # nginx + Selenium E2E (standalone Chromium)
    ├── groovy/.../ElectronAppFunctionalTest.groovy        # drives the REAL Electron app
    └── resources/
        ├── web/index.html                                 # page served during the Chromium E2E test
        └── electron/                                      # thin test harness layered on the PRODUCTION image
            ├── harness.Dockerfile                         # FROM electron-gpu-test + Xvfb + ChromeDriver
            ├── test-harness-entrypoint.sh                 # Xvfb -> launch.sh (remote debug) -> ChromeDriver
            └── render-check.html                          # deterministic page the app loads
```

## Tests

- **`StackSmokeTest`** — Docker-free; verifies the Java 25 toolchain and that every library resolves.
- **`BrowserContainerFunctionalTest`** — boots nginx + a Selenium **standalone Chromium** container and drives one against the other.
- **`ElectronAppFunctionalTest`** — Testcontainers builds the **production image** (the repo's
  `Containerfile`, NVIDIA stack and all), then a thin harness layer on top that adds only Xvfb + a
  version-matched ChromeDriver. The harness starts the **real app via the production `/app/launch.sh`**,
  which detects the absence of a GPU and **falls back to software rendering** — so the NVIDIA stack is
  proven harmless on a GPU-less host. Selenium then attaches to the running app and asserts on what it
  rendered, including a `navigator.userAgent` check proving it's Electron, not standalone Chromium.

  Building the production image runs `dnf`/`npm` (downloads Electron + a matching ChromeDriver), so this
  test needs network access at build time and takes a few minutes on a cold cache. Behind a TLS-intercepting
  proxy, drop the proxy's CA into the repo root's `extra-cas/` (the test forwards a detected host CA
  automatically; see the `Containerfile`'s optional CA-trust step).

## Run

```sh
# Run all tests (Docker-backed ones skip automatically if Docker is absent).
./gradlew test

# Generate and open the Allure report afterwards.
./gradlew allureReport      # writes build/reports/allure-report
./gradlew allureServe       # builds + serves the report in a browser
```

Raw Allure results land in `build/allure-results`.
