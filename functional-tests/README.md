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
- The suite's images, **built before the test run** with podman or docker by
  [`containers/build-images.sh`](./containers/build-images.sh) — the test JVM
  never builds images (see *Run* below).

## Layout

```
functional-tests/
├── build.gradle              # plugins, toolchain, dependencies (versions pinned here)
├── settings.gradle           # project name + Foojay toolchain resolver
├── gradle.properties         # build caching / parallel / configuration cache
├── containers/               # test images, built BEFORE the test run (not by it)
│   ├── build-images.sh                                    # builds every image below with podman/docker
│   ├── harness.Dockerfile                                 # FROM electron-gpu-test + ChromeDriver (no Xvfb)
│   ├── webgl-harness.Dockerfile                           # as above + vendored offline NASA WorldWind
│   ├── webgl-spin-harness.Dockerfile                      # thin layer on webgl-harness adding the spinning page
│   ├── test-harness-entrypoint.sh                         # wait for shared X socket -> launch.sh -> ChromeDriver
│   ├── xvfb.Dockerfile                                    # standalone Xvfb sidecar image (Xvfb + ffmpeg)
│   ├── xvfb-entrypoint.sh                                 # serves the X display into the shared socket volume
│   ├── record-start.sh                                    # start ffmpeg x11grab raw capture of the display (on demand)
│   ├── record-stop.sh                                     # stop capture, transcode to WebM, emit the file
│   ├── render-check.html                                  # deterministic page the app loads
│   ├── webgl-worldwind.html                               # NASA WorldWind globe page (offline) for the WebGL test
│   └── webgl-worldwind-spin.html                          # NASA WorldWind globe that spins, for the animation test
└── src/test/
    ├── java/.../StackSmokeTest.java                       # Docker-free wiring checks
    ├── java/.../BrowserContainerFunctionalTest.java       # nginx + Selenium E2E (standalone Chromium)
    ├── java/.../ElectronAppFunctionalTest.java            # drives the REAL Electron app
    ├── java/.../WebGlWorldWindFunctionalTest.java         # drives the app rendering a NASA WorldWind WebGL globe
    ├── java/.../WebGlWorldWindSpinFunctionalTest.java     # as above, but the globe spins -> stills + WebM in Allure
    ├── java/.../XvfbContainer.java                        # reusable module: Xvfb display sidecar + screen recording
    ├── java/.../TestImages.java                           # resolves the pre-built image tags the tests run
    └── resources/
        ├── web/index.html                                 # page served during the Chromium E2E test
        ├── tls/                                           # nginx mTLS config + page (mounted at run time)
        └── certs/                                         # pre-generated test CA / server / client certs
```

## Tests

- **`StackSmokeTest`** — Docker-free; verifies the Java 25 toolchain and that every library resolves.
- **`BrowserContainerFunctionalTest`** — boots nginx + a Selenium **standalone Chromium** container and drives one against the other.
- **`ElectronAppFunctionalTest`** — runs the pre-built **harness image**: the production image (the repo's
  `Containerfile`, NVIDIA stack and all) plus a thin layer on top that adds only a version-matched
  ChromeDriver. The virtual display comes from a **separate Xvfb sidecar container** that shares its
  `/tmp/.X11-unix` socket with the app via a Docker volume — so the app connects to an X server *outside*
  its container, mirroring how the production image attaches to the host's X server, and the app image
  carries no display tooling. The harness starts the **real app via the production `/app/launch.sh`**,
  which detects the absence of a GPU and **falls back to software rendering** — so the NVIDIA stack is
  proven harmless on a GPU-less host. Selenium then attaches to the running app and asserts on what it
  rendered, including a `navigator.userAgent` check proving it's Electron on the X11 backend, not a
  standalone browser.

  Building the images (done up front by `containers/build-images.sh`, **not** by this test) runs `dnf`/`npm`
  (downloads Electron + a matching ChromeDriver), so the build needs network access and takes a few minutes
  on a cold cache. Behind a TLS-intercepting proxy, drop the proxy's CA into the repo root's `extra-cas/`
  (the script forwards a detected host CA automatically; see the `Containerfile`'s optional CA-trust step).

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

## Shared test infrastructure

Cross-cutting helpers used by the tests above, written as small reusable modules
rather than copied into each test:

- **`XvfbContainer`** — a reusable Testcontainers module (`extends GenericContainer<XvfbContainer>`) for the
  **display sidecar**. It owns the shared `/tmp/.X11-unix` socket source and the `X-READY` wait.
  `xvfb.prepareClient(appContainer)` wires an app container to the display (mounts the shared socket source,
  sets `DISPLAY`, adds the startup dependency). Because the sidecar also bundles ffmpeg, it exposes
  `startRecording()` / `stopRecordingAsWebm()` to screen-record the display on demand (raw capture → WebM),
  as used by the spin test. By default the sidecar starts its **own Xvfb** virtual display on `:99`; set
  `LOCAL_DISPLAY` (see *Running on the local display* below) to record an existing host display instead.
- **`TestImages`** — resolves the pre-built image tags the tests run (harness, WebGL harness, spin harness,
  Xvfb sidecar). Each accessor defaults to the tag `containers/build-images.sh` produces, overridable per
  image via an env var (see *Building the images* below), and fails fast with a pointer at the build script
  if the image is missing from the daemon.

## Run

```sh
# 1. Build the images the suite runs (podman or docker; see the script header).
containers/build-images.sh

# 2. Run all tests (Docker-backed ones skip automatically if Docker is absent).
./gradlew test

# Generate and open the Allure report afterwards.
./gradlew allureReport      # writes build/reports/allure-report
./gradlew allureServe       # builds + serves the report in a browser
```

Raw Allure results land in `build/allure-results`.

### Running on the local display

By default the suite spins up an **Xvfb sidecar** that serves a throwaway virtual
display on `:99`, and ffmpeg records that. To instead run the app on — and record
— an X display **already running on your host** (so you can watch it live), point
`LOCAL_DISPLAY` at it:

```sh
LOCAL_DISPLAY="$DISPLAY" ./gradlew test     # e.g. LOCAL_DISPLAY=:0
```

When `LOCAL_DISPLAY` is set, the sidecar **bind-mounts the host's real
`/tmp/.X11-unix`** and uses the named display as-is: it starts **no Xvfb**, and
`startRecording()` / `stopRecordingAsWebm()` capture that display. The app and
sidecar containers must be allowed to reach your X server — typically
`xhost +local:` first. Unset `LOCAL_DISPLAY` to go back to the Xvfb sidecar.

### Building the images

The test JVM **never builds container images** — every image the suite runs is
built beforehand by [`containers/build-images.sh`](./containers/build-images.sh)
(podman or docker, auto-detected; override with `CONTAINER_TOOL`). It builds, in
dependency order: the **production image** from the repo's `Containerfile`, the
**Xvfb sidecar**, and the three thin **test-harness layers** that build `FROM`
the production image (`ARG BASE_IMAGE`). Targets can be built selectively, e.g.
`containers/build-images.sh harness` after an app-only change.

The suite picks the images up by the script's default tags. To point a test at a
different pre-built tag (as CI does), set the matching env var:

- **`ELECTRON_HARNESS_IMAGE`** — production app + ChromeDriver (default `electron-gpu-test:harness`).
- **`WEBGL_HARNESS_IMAGE`** — harness + vendored offline NASA WorldWind (default `electron-gpu-test:webgl-harness`).
- **`WEBGL_SPIN_HARNESS_IMAGE`** — WebGL harness + spinning page (default `electron-gpu-test:webgl-spin-harness`).
- **`XVFB_IMAGE`** — the Xvfb sidecar `XvfbContainer` runs (default `electron-gpu-test:xvfb`).

The same vars (plus `ELECTRON_BASE_IMAGE` for the production tag the harness
layers build `FROM`) override the tags `build-images.sh` produces. In CI the
expensive production and Xvfb images are built with buildx + the GHA layer cache
and the script then builds only the thin harness layers on top
(`build-images.sh harness webgl-harness spin-harness`) — see
`.github/workflows/ci.yml`.
