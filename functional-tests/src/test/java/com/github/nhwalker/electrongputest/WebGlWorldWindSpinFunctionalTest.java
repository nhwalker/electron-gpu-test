package com.github.nhwalker.electrongputest;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.Mount;
import com.github.dockerjava.api.model.MountType;
import io.qameta.allure.Attachment;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Step;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WebGL animation functional test: drives the REAL electron-gpu-test app loading
 * a NASA WebWorldWind globe that <em>spins continuously</em>, proves the WebGL
 * path animates frame-over-frame on a GPU-less host, and files both still
 * screenshots and a short WebM video of the rotation into the Allure report.
 *
 * It reuses the exact selenium + electron path of {@link WebGlWorldWindFunctionalTest}
 * (Xvfb sidecar sharing an X socket, production {@code /app/launch.sh} with
 * {@code SOFTWARE_WEBGL=1} so SwiftShader supplies WebGL, vendored offline
 * WorldWind imagery). The only difference is the page: {@code spin.html} drives
 * the camera longitude from wall-clock time every animation frame, so the globe
 * rotates on its own with no input.
 *
 * "It animates" is asserted three ways: WorldWind's own redraw counter keeps
 * climbing; two screenshots captured seconds apart are decoded and differ by a
 * meaningful fraction of pixels (a static frame would barely change); and each of
 * those frames is independently a non-blank, multi-colour render (a failed WebGL
 * frame would be uniform black). Meanwhile the X display is screen-recorded by
 * ffmpeg running in the Xvfb sidecar (started/stopped on demand via {@code docker
 * exec}, capturing raw so the grab never lags, then transcoded to WebM), and the
 * resulting ~5s clip is attached to Allure.
 *
 * {@code disabledWithoutDocker} so it skips (not fails) where Docker is absent.
 */
@Epic("electron-gpu-test")
@Feature("WebGL rendering")
@Testcontainers(disabledWithoutDocker = true)
class WebGlWorldWindSpinFunctionalTest {

    // ChromeDriver listens on 4444 inside the harness (see the entrypoint).
    private static final int CHROMEDRIVER_PORT = 4444;

    // The app's DevTools endpoint inside the container that ChromeDriver attaches
    // to (launch.sh is started with --remote-debugging-port=9222).
    private static final String DEBUGGER_ADDRESS = "127.0.0.1:9222";

    // The X display the sidecar serves and the app connects to.
    private static final String DISPLAY = ":99";

    // A named Docker volume shared between the sidecar and the app container, so
    // the sidecar's X socket is visible to the app (a shared-volume mount is
    // order-independent, unlike withVolumesFrom which needs the source running).
    private static final String X11_SOCKET_DIR = "/tmp/.X11-unix";
    private static final String XSOCK_VOLUME = "electron-gpu-test-xsock";

    // The spinning-globe page the production launcher opens (vendored into the image).
    private static final String PAGE_URL = "file:///opt/worldwind/spin.html";

    // Roughly how long ffmpeg records the spinning display for -- aiming at a ~5s
    // WebM clip in the Allure report.
    private static final Duration CAPTURE_DURATION = Duration.ofSeconds(5);

    // Where record-stop.sh writes the transcoded WebM inside the sidecar.
    private static final String RECORDING_PATH = "/tmp/recording.webm";

    // Sidecar that provides ONLY the virtual X display, publishing its socket into
    // the shared volume that the app container also mounts.
    @Container
    static final GenericContainer<?> XVFB = new GenericContainer<>(buildXvfbImage())
            .withCreateContainerCmdModifier(shareXSocketVolume())
            .waitingFor(Wait.forLogMessage(".*X-READY.*", 1));

    @Container
    static final GenericContainer<?> ELECTRON = new GenericContainer<>(buildHarnessImage())
            .dependsOn(XVFB)
            // Mount the same X socket volume and connect to the sidecar's display.
            .withCreateContainerCmdModifier(shareXSocketVolume())
            .withEnv("DISPLAY", DISPLAY)
            // Render WebGL in software via SwiftShader so the GPU-less CI host can
            // still exercise the WebGL path (see launch.sh).
            .withEnv("SOFTWARE_WEBGL", "1")
            // Tell the harness entrypoint which page to open via launch.sh.
            .withEnv("TARGET_URL", PAGE_URL)
            .withExposedPorts(CHROMEDRIVER_PORT)
            .withStartupTimeout(Duration.ofSeconds(240))
            .waitingFor(
                    Wait.forHttp("/status")
                            .forPort(CHROMEDRIVER_PORT)
                            .forStatusCode(200)
                            .forResponsePredicate(body -> body.contains("\"ready\":true")));

    @Test
    @DisplayName("The production app renders a NASA WorldWind globe that spins, with no GPU")
    @Description("Builds the production image, boots the real app via launch.sh with SOFTWARE_WEBGL=1 (SwiftShader), loads a vendored offline NASA WorldWind globe that spins on its own, proves the WebGL frame animates (rising redraw count + two differing screenshots), and attaches both stills and a ~5s MP4 of the rotation to the Allure report.")
    void worldWindGlobeSpins() throws Exception {
        // launch.sh must have taken the SwiftShader software-WebGL path -- proving
        // the app can do WebGL on a host with no GPU.
        String launchLog = launchLog();
        assertTrue(launchLog.contains("SwiftShader WebGL"),
                "Expected launch.sh to take the SwiftShader WebGL path. Log was:\n" + launchLog);

        RemoteWebDriver driver = attachToApp();
        try {
            // The app opened this page itself from its argv (main.js -> loadURL).
            assertEquals(PAGE_URL, driver.getCurrentUrl());

            // Wait for WorldWind to finish wiring up (success or failure), then
            // surface any initialization error before the other assertions.
            waitForWorldWindToSettle(driver);
            assertNull(worldWindError(driver),
                    "WorldWind reported an initialization error: " + worldWindError(driver));

            // A real WebGL context was obtained and the spin loop is running.
            assertTrue(jsBool(driver, "return !!window.WW_TEST.webgl"),
                    "WorldWind did not obtain a WebGL context");
            assertTrue(jsBool(driver, "return window.WW_TEST.ready === true"),
                    "WorldWind never painted a frame");
            assertTrue(jsBool(driver, "return window.WW_TEST.spinning === true"),
                    "WorldWind never started the spin animation");

            String renderer = jsString(driver, "return window.WW_TEST.glRenderer");
            assertNotNull(renderer, "No WebGL renderer string was reported");

            // Prove we drove the real Electron app on the X11 backend (the shared
            // display), not a standalone/headless browser.
            String ua = userAgent(driver);
            assertTrue(ua.contains("Electron/41") && ua.contains("electron-gpu-test/") && ua.contains("X11"),
                    "Expected the Electron app on the X11 backend but UA was: " + ua);

            // Screen-record the spin for ~5s, assert the globe is genuinely
            // animating, and attach the stills + WebM clip to Allure.
            recordAndAssertSpin(driver);
        } finally {
            driver.quit();
        }
    }

    /**
     * Screen-records the spinning globe with ffmpeg in the sidecar for
     * {@link #CAPTURE_DURATION}, proves the globe is actually animating, and
     * attaches the first/last stills and the WebM clip to Allure. Animation is
     * proven two independent ways: WorldWind's own redraw counter must climb over
     * the window, and two screenshots captured ~5s apart must differ by a
     * meaningful fraction of pixels.
     */
    @Step("Record the spinning globe and assert it animates")
    private static void recordAndAssertSpin(RemoteWebDriver driver) throws IOException, InterruptedException {
        long framesBefore = redrawCount(driver);

        // Start ffmpeg recording the display, then capture stills bracketing the
        // recorded window so the assertions cover the same span as the video.
        startRecording();
        byte[] firstPng = driver.getScreenshotAs(OutputType.BYTES);
        Thread.sleep(CAPTURE_DURATION.toMillis());
        byte[] lastPng = driver.getScreenshotAs(OutputType.BYTES);
        byte[] webm = stopRecordingAndFetch();

        long framesAfter = redrawCount(driver);
        assertTrue(framesAfter > framesBefore,
                "WorldWind's redraw count did not advance (" + framesBefore + " -> " + framesAfter
                        + "); the globe is not animating");

        // The first and last frames must differ -- a static globe would barely
        // change between two shots seconds apart.
        assertFramesDiffer(firstPng, lastPng);

        // Each captured still must independently be a real, non-blank render.
        assertGlobeRendered(firstPng);
        assertGlobeRendered(lastPng);

        assertTrue(webm.length > 0, "ffmpeg produced an empty WebM recording");

        // Attach the evidence: two stills bracketing the spin, then the clip.
        attachFirstFrame(firstPng);
        attachLastFrame(lastPng);
        attachSpinVideo(webm);
    }

    @Step("Start recording the X display (ffmpeg x11grab in the Xvfb sidecar)")
    private static void startRecording() throws IOException, InterruptedException {
        var result = XVFB.execInContainer("/usr/local/bin/record-start.sh");
        assertEquals(0, result.getExitCode(),
                "record-start.sh failed: " + result.getStdout() + result.getStderr());
    }

    /**
     * Stops the recording (ffmpeg flushes the raw capture and transcodes it to
     * WebM inside the sidecar) and copies the resulting clip back to the host.
     */
    @Step("Stop recording, transcode to WebM, and copy it out of the sidecar")
    private static byte[] stopRecordingAndFetch() throws IOException, InterruptedException {
        var result = XVFB.execInContainer("/usr/local/bin/record-stop.sh", RECORDING_PATH);
        assertEquals(0, result.getExitCode(),
                "record-stop.sh failed: " + result.getStdout() + result.getStderr());
        return XVFB.copyFileFromContainer(RECORDING_PATH, java.io.InputStream::readAllBytes);
    }

    @Step("Attach a WebDriver session to the running Electron app")
    private static RemoteWebDriver attachToApp() throws MalformedURLException {
        ChromeOptions options = new ChromeOptions();
        // Attach to the app already launched by launch.sh inside the container.
        options.setExperimentalOption("debuggerAddress", DEBUGGER_ADDRESS);
        URL url = URI.create("http://" + ELECTRON.getHost() + ":"
                + ELECTRON.getMappedPort(CHROMEDRIVER_PORT) + "/").toURL();
        return new RemoteWebDriver(url, options);
    }

    @Step("Wait for WorldWind to finish initializing")
    private static void waitForWorldWindToSettle(RemoteWebDriver driver) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000L;
        while (System.currentTimeMillis() < deadline) {
            if (jsBool(driver, "return !!(window.WW_TEST && window.WW_TEST.done)")) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("WorldWind did not finish initializing within 30s");
    }

    @Attachment(value = "NASA WorldWind spin -- first frame", type = "image/png", fileExtension = ".png")
    private static byte[] attachFirstFrame(byte[] png) {
        return png;
    }

    @Attachment(value = "NASA WorldWind spin -- last frame", type = "image/png", fileExtension = ".png")
    private static byte[] attachLastFrame(byte[] png) {
        return png;
    }

    /** Attaches the ffmpeg-recorded WebM clip; Allure renders it as inline {@code <video>}. */
    @Attachment(value = "NASA WorldWind WebGL globe spin", type = "video/webm", fileExtension = ".webm")
    private static byte[] attachSpinVideo(byte[] webm) {
        return webm;
    }

    /**
     * Decodes two screenshots and asserts a meaningful fraction of pixels changed
     * between them. The globe spins ~deg/s of camera longitude, so frames captured
     * seconds apart show clearly different terrain; a frozen render would not.
     */
    @Step("Assert the two captured frames differ (the globe moved)")
    private static void assertFramesDiffer(byte[] firstPng, byte[] lastPng) throws IOException {
        BufferedImage a = ImageIO.read(new ByteArrayInputStream(firstPng));
        BufferedImage b = ImageIO.read(new ByteArrayInputStream(lastPng));
        assertNotNull(a, "First screenshot could not be decoded as an image");
        assertNotNull(b, "Last screenshot could not be decoded as an image");

        int w = Math.min(a.getWidth(), b.getWidth());
        int h = Math.min(a.getHeight(), b.getHeight());
        assertTrue(w > 0 && h > 0, "Screenshots had zero size");

        int step = Math.max(1, Math.min(w, h) / 120);
        int samples = 0;
        int changed = 0;
        for (int y = 0; y < h; y += step) {
            for (int x = 0; x < w; x += step) {
                int ca = a.getRGB(x, y);
                int cb = b.getRGB(x, y);
                int dr = Math.abs(((ca >> 16) & 0xff) - ((cb >> 16) & 0xff));
                int dg = Math.abs(((ca >> 8) & 0xff) - ((cb >> 8) & 0xff));
                int db = Math.abs((ca & 0xff) - (cb & 0xff));
                samples++;
                if (dr + dg + db > 60) {
                    changed++;
                }
            }
        }

        double changedFraction = changed / (double) samples;
        assertTrue(changedFraction > 0.05d,
                "The two frames are nearly identical (only "
                        + String.format("%.2f", changedFraction * 100)
                        + "% of pixels changed); the globe did not appear to spin");
    }

    /**
     * Decodes a screenshot and asserts the frame is non-blank and multi-coloured.
     * A WebGL frame that failed to render would be a uniform black image, so a
     * meaningful fraction of non-background pixels across several distinct colours
     * is strong evidence the globe actually drew.
     */
    @Step("Assert a captured frame is a non-blank, multi-colour render")
    private static void assertGlobeRendered(byte[] png) throws IOException {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
        assertNotNull(img, "Screenshot could not be decoded as an image");

        int w = img.getWidth();
        int h = img.getHeight();
        assertTrue(w > 0 && h > 0, "Screenshot had zero size (" + w + "x" + h + ")");

        int step = Math.max(1, Math.min(w, h) / 120);
        int samples = 0;
        int nonBackground = 0;
        Set<Integer> colors = new HashSet<>();
        for (int y = 0; y < h; y += step) {
            for (int x = 0; x < w; x += step) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xff;
                int g = (rgb >> 8) & 0xff;
                int b = rgb & 0xff;
                samples++;
                // Anything clearly above the near-black background counts as drawn.
                if (r + g + b > 48) {
                    nonBackground++;
                }
                // Quantise to 4 bits per channel to fold antialiasing noise together.
                colors.add(((r >> 4) << 8) | ((g >> 4) << 4) | (b >> 4));
            }
        }

        double nonBackgroundFraction = nonBackground / (double) samples;
        assertTrue(nonBackgroundFraction > 0.01d,
                "Rendered frame looks blank: only "
                        + String.format("%.2f", nonBackgroundFraction * 100)
                        + "% of pixels were non-background");
        assertTrue(colors.size() >= 3,
                "Rendered frame has too few distinct colours (" + colors.size()
                        + "); WebGL likely did not draw");
    }

    @Step("Read launch.sh output from the container")
    private static String launchLog() throws IOException, InterruptedException {
        return ELECTRON.execInContainer("cat", "/tmp/electron.log").getStdout();
    }

    @Step("Read navigator.userAgent")
    private static String userAgent(RemoteWebDriver driver) {
        return (String) driver.executeScript("return navigator.userAgent");
    }

    @Step("Read the WorldWind initialization error, if any")
    private static String worldWindError(RemoteWebDriver driver) {
        return (String) driver.executeScript("return window.WW_TEST.error");
    }

    /** Reads WorldWind's running redraw count (climbs once per animation frame). */
    private static long redrawCount(RemoteWebDriver driver) {
        return ((Number) driver.executeScript("return window.WW_TEST.frames")).longValue();
    }

    private static String jsString(RemoteWebDriver driver, String script) {
        return (String) driver.executeScript(script);
    }

    private static boolean jsBool(RemoteWebDriver driver, String script) {
        return Boolean.TRUE.equals(driver.executeScript(script));
    }

    /**
     * Mounts the shared X socket volume at {@code /tmp/.X11-unix}. Docker creates
     * the named volume on first use; the sidecar's entrypoint clears any stale
     * socket before recreating it, so reusing a fixed name across runs is safe.
     */
    private static Consumer<CreateContainerCmd> shareXSocketVolume() {
        return cmd -> {
            List<Mount> existing = cmd.getHostConfig().getMounts();
            List<Mount> mounts = new ArrayList<>(existing != null ? existing : Collections.<Mount>emptyList());
            mounts.add(new Mount()
                    .withType(MountType.VOLUME)
                    .withSource(XSOCK_VOLUME)
                    .withTarget(X11_SOCKET_DIR));
            cmd.getHostConfig().withMounts(mounts);
        };
    }

    /** Builds the standalone Xvfb sidecar image (Xvfb + ffmpeg recording scripts). */
    private static ImageFromDockerfile buildXvfbImage() {
        return new ImageFromDockerfile("electron-gpu-test:xvfb", false)
                .withFileFromClasspath("Dockerfile", "electron/xvfb.Dockerfile")
                .withFileFromClasspath("xvfb-entrypoint.sh", "electron/xvfb-entrypoint.sh")
                .withFileFromClasspath("record-start.sh", "electron/record-start.sh")
                .withFileFromClasspath("record-stop.sh", "electron/record-stop.sh");
    }

    /**
     * Builds the image chain this test drives: the production image from the repo's
     * {@code Containerfile}; the WebGL harness on top (ChromeDriver + vendored
     * offline NASA WorldWind, identical to {@link WebGlWorldWindFunctionalTest} so
     * the layer cache is shared); and a thin spin layer that adds the spinning
     * page. Any host egress-proxy CA is forwarded so the in-image dnf/npm downloads
     * work behind a TLS-intercepting proxy; with direct egress this is a no-op.
     */
    private static ImageFromDockerfile buildHarnessImage() {
        Path repoRoot = Paths.get(System.getProperty("user.dir")).toAbsolutePath().getParent();
        assertTrue(Files.exists(repoRoot.resolve("Containerfile")),
                "Could not locate the production Containerfile at " + repoRoot);

        // Build the production image first so the harness can build FROM it.
        new ImageFromDockerfile("electron-gpu-test:undertest", false)
                .withFileFromPath("Dockerfile", repoRoot.resolve("Containerfile"))
                .withFileFromPath("app", repoRoot.resolve("app"))
                .withFileFromPath("rocky9.repo", repoRoot.resolve("rocky9.repo"))
                .withFileFromPath("extra-cas", resolveExtraCaDir())
                .get();

        // Build the WebGL harness (ChromeDriver + offline WorldWind). Same image
        // tag and context as WebGlWorldWindFunctionalTest, so this reuses its build.
        new ImageFromDockerfile("electron-gpu-test:webgl-harness", false)
                .withFileFromClasspath("Dockerfile", "electron/webgl-harness.Dockerfile")
                .withFileFromClasspath("test-harness-entrypoint.sh", "electron/test-harness-entrypoint.sh")
                .withFileFromClasspath("webgl-worldwind.html", "electron/webgl-worldwind.html")
                .get();

        // Thin layer on top adding only the spinning page.
        return new ImageFromDockerfile("electron-gpu-test:webgl-spin-harness", false)
                .withFileFromClasspath("Dockerfile", "electron/webgl-spin-harness.Dockerfile")
                .withFileFromClasspath("webgl-worldwind-spin.html", "electron/webgl-worldwind-spin.html");
    }

    /**
     * Returns a directory to mount at the production build context's
     * {@code extra-cas/}. Behind a TLS-intercepting proxy it contains the host CA
     * bundle so dnf/npm trust the proxy; otherwise it holds only a placeholder so
     * the Containerfile's COPY succeeds.
     */
    private static Path resolveExtraCaDir() {
        try {
            Path dir = Files.createTempDirectory("electron-extra-cas");
            Path hostBundle = Paths.get("/etc/ssl/certs/ca-certificates.crt");
            if (behindEgressProxy() && Files.exists(hostBundle)) {
                Files.copy(hostBundle, dir.resolve("host-egress-bundle.crt"));
            } else {
                Files.writeString(dir.resolve(".keep"), "");
            }
            return dir;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Detects the sandbox/corporate egress proxy by its installed CA marker. */
    private static boolean behindEgressProxy() {
        Path caDir = Paths.get("/usr/local/share/ca-certificates");
        if (!Files.isDirectory(caDir)) {
            return false;
        }
        try (Stream<Path> stream = Files.list(caDir)) {
            return stream.anyMatch(p -> p.getFileName().toString().toLowerCase().contains("egress"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
