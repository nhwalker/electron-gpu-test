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
    ├── java/.../WebGlWorldWindSpinFunctionalTest.java     # as above, but the globe spins -> stills + MP4 in Allure
    └── resources/
        ├── web/index.html                                 # page served during the Chromium E2E test
        └── electron/                                      # thin test harness layered on the PRODUCTION image
            ├── harness.Dockerfile                         # FROM electron-gpu-test + ChromeDriver (no Xvfb)
            ├── webgl-harness.Dockerfile                   # as above + vendored offline NASA WorldWind
            ├── webgl-spin-harness.Dockerfile              # thin layer on webgl-harness adding the spinning page
            ├── test-harness-entrypoint.sh                 # wait for shared X socket -> launch.sh -> ChromeDriver
            ├── xvfb.Dockerfile                            # standalone Xvfb sidecar image (Xvfb + ffmpeg)
            ├── xvfb-entrypoint.sh                         # serves the X display into the shared socket volume
            ├── record-start.sh                            # start ffmpeg x11grab raw capture of the display (on demand)
            ├── record-stop.sh                             # stop capture, transcode to WebM, emit the file
            ├── render-check.html                          # deterministic page the app loads
            ├── webgl-worldwind.html                       # NASA WorldWind globe page (offline) for the WebGL test
            └── webgl-worldwind-spin.html                  # NASA WorldWind globe that spins, for the animation test
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

- **`WebGlWorldWindSpinFunctionalTest`** — the **WebGL animation** check. It reuses the same image chain and
  selenium/electron path as `WebGlWorldWindFunctionalTest`, but loads a globe page (`webgl-worldwind-spin.html`,
  added by a thin `webgl-spin-harness.Dockerfile` layer) that **rotates the camera longitude from wall-clock
  time every animation frame**, so the globe spins on its own. Over a **~5 second** window the test proves the
  globe is genuinely animating two independent ways: WorldWind's own **redraw counter keeps climbing**, and
  two screenshots captured ~5s apart **differ** by a meaningful fraction of pixels (a frozen render would not).
  Meanwhile the spinning display is **screen-recorded by ffmpeg running in the Xvfb sidecar** — started/stopped
  on demand via `docker exec` (`record-start.sh` / `record-stop.sh`), so recording is **independent of the
  container's lifecycle**. ffmpeg's `x11grab` captures **raw** frames (no codec runs during the grab, so it
  can't lag or drop frames), then `record-stop.sh` **transcodes the capture to WebM** (VP9) and the clip is
  copied back to the host and attached to the Allure report — alongside the two **still screenshots** bracketing
  the spin.

## Run

```sh
# Run all tests (Docker-backed ones skip automatically if Docker is absent).
./gradlew test

# Generate and open the Allure report afterwards.
./gradlew allureReport      # writes build/reports/allure-report
./gradlew allureServe       # builds + serves the report in a browser
```

Raw Allure results land in `build/allure-results`.

### Reusing a pre-built production image

By default the suite builds the production image from the repo's `Containerfile`
on the local Docker daemon (so a bare `./gradlew test` just works). The thin
test-only harness layers always build `FROM` that image via their
`ARG BASE_IMAGE`.

If the production image has already been built — e.g. CI builds it once up front
with buildx + layer cache — set **`ELECTRON_BASE_IMAGE`** to its tag and the
suite skips the in-JVM build, building the harness layers `FROM` that tag
instead. This avoids a second, uncached build of the production image inside the
test JVM (Testcontainers' image build can't read buildx's cache):

```sh
docker build -t electron-gpu-test:undertest -f ../Containerfile ..
ELECTRON_BASE_IMAGE=electron-gpu-test:undertest ./gradlew test
```
