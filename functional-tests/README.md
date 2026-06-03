# functional-tests

End-to-end / functional tests for **electron-gpu-test**.

A Gradle (Groovy build) project with tests written in **Java 25**, using:

| Concern            | Library / tool                         | Version  |
| ------------------ | -------------------------------------- | -------- |
| Build              | Gradle (wrapper)                       | 9.5.1    |
| Language           | Java                                   | 25       |
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
    ├── java/.../StackSmokeTest.java                       # Docker-free wiring checks
    ├── java/.../BrowserContainerFunctionalTest.java       # nginx + Selenium E2E (standalone Chromium)
    ├── java/.../ElectronAppFunctionalTest.java            # drives the REAL Electron app
    ├── java/.../WebGlWorldWindFunctionalTest.java         # drives the app rendering a NASA WorldWind WebGL globe
    └── resources/
        ├── web/index.html                                 # page served during the Chromium E2E test
        └── electron/                                      # thin test harness layered on the PRODUCTION image
            ├── harness.Dockerfile                         # FROM electron-gpu-test + ChromeDriver (no Xvfb)
            ├── webgl-harness.Dockerfile                   # as above + vendored offline NASA WorldWind
            ├── test-harness-entrypoint.sh                 # wait for shared X socket -> launch.sh -> ChromeDriver
            ├── xvfb.Dockerfile                            # standalone Xvfb sidecar image
            ├── xvfb-entrypoint.sh                         # serves the X display into the shared socket volume
            ├── render-check.html                          # deterministic page the app loads
            └── webgl-worldwind.html                       # NASA WorldWind globe page (offline) for the WebGL test
```

## Tests

- **`StackSmokeTest`** — Docker-free; verifies the Java 25 toolchain and that every library resolves.
- **`BrowserContainerFunctionalTest`** — boots nginx + a Selenium **standalone Chromium** container and drives one against the other.
- **`ElectronAppFunctionalTest`** — Testcontainers builds the **production image** (the repo's
  `Containerfile`, NVIDIA stack and all), then a thin harness layer on top that adds only a version-matched
  ChromeDriver. The virtual display comes from a **separate Xvfb sidecar container** that shares its
  `/tmp/.X11-unix` socket with the app via a Docker volume — so the app connects to an X server *outside*
  its container, mirroring how the production image attaches to the host's X server, and the app image
  carries no display tooling. The harness starts the **real app via the production `/app/launch.sh`**,
  which detects the absence of a GPU and **falls back to software rendering** — so the NVIDIA stack is
  proven harmless on a GPU-less host. Selenium then attaches to the running app and asserts on what it
  rendered, including a `navigator.userAgent` check proving it's Electron on the X11 backend, not a
  standalone browser.

  Building the production image runs `dnf`/`npm` (downloads Electron + a matching ChromeDriver), so this
  test needs network access at build time and takes a few minutes on a cold cache. Behind a TLS-intercepting
  proxy, drop the proxy's CA into the repo root's `extra-cas/` (the test forwards a detected host CA
  automatically; see the `Containerfile`'s optional CA-trust step).

- **`WebGlWorldWindFunctionalTest`** — the **WebGL** check. It reuses the exact same selenium + electron
  path as `ElectronAppFunctionalTest` (Xvfb sidecar → production `/app/launch.sh` → ChromeDriver attach),
  but loads a **NASA WebWorldWind** globe. Two things make WebGL
  work and stay deterministic: the app is launched with `SOFTWARE_WEBGL=1`, so `launch.sh` renders via
  **SwiftShader** (ANGLE) instead of `--disable-gpu` — giving a real, GPU-less WebGL implementation; and the
  harness **vendors WorldWind plus its bundled imagery** into the image, so the globe textures load from
  `file://` with **no network** at run time. The test asserts the page obtained a live WebGL context and
  painted a frame, then **captures a screenshot of the rendered globe into the Allure report** and decodes it
  to confirm the frame is a non-blank, multi-colour render (a failed WebGL frame would be uniform black).

## Run

```sh
# Run all tests (Docker-backed ones skip automatically if Docker is absent).
./gradlew test

# Generate and open the Allure report afterwards.
./gradlew allureReport      # writes build/reports/allure-report
./gradlew allureServe       # builds + serves the report in a browser
```

Raw Allure results land in `build/allure-results`.
