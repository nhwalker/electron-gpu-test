package com.github.nhwalker.electrongputest;

import io.qameta.allure.Allure;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
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
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

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

    // The spinning-globe page the production launcher opens (vendored into the image).
    private static final String PAGE_URL = "file:///opt/worldwind/spin.html";

    // Roughly how long ffmpeg records the spinning display for -- aiming at a ~5s
    // WebM clip in the Allure report.
    private static final Duration CAPTURE_DURATION = Duration.ofSeconds(5);

    // Sidecar that provides the virtual X display AND records it (see XvfbContainer).
    @Container
    static final XvfbContainer XVFB = new XvfbContainer();

    // prepareClient mounts the shared X socket volume, points DISPLAY at the
    // sidecar, and adds a startup dependency on it.
    @Container
    static final GenericContainer<?> ELECTRON = XVFB.prepareClient(new GenericContainer<>(buildHarnessImage()))
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
    private static void recordAndAssertSpin(RemoteWebDriver driver) throws IOException, InterruptedException {
        Allure.step("Record the spinning globe and assert it animates", () -> {
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
        });
    }

    private static void startRecording() throws IOException, InterruptedException {
        Allure.step("Start recording the X display (ffmpeg x11grab in the Xvfb sidecar)",
                () -> XVFB.startRecording());
    }

    private static byte[] stopRecordingAndFetch() throws IOException, InterruptedException {
        return Allure.step("Stop recording, transcode to WebM, and copy it out of the sidecar",
                () -> XVFB.stopRecordingAsWebm());
    }

    private static RemoteWebDriver attachToApp() throws MalformedURLException {
        return Allure.step("Attach a WebDriver session to the running Electron app", () -> {
            ChromeOptions options = new ChromeOptions();
            // Attach to the app already launched by launch.sh inside the container.
            options.setExperimentalOption("debuggerAddress", DEBUGGER_ADDRESS);
            URL url = URI.create("http://" + ELECTRON.getHost() + ":"
                    + ELECTRON.getMappedPort(CHROMEDRIVER_PORT) + "/").toURL();
            return new RemoteWebDriver(url, options);
        });
    }

    private static void waitForWorldWindToSettle(RemoteWebDriver driver) throws InterruptedException {
        Allure.step("Wait for WorldWind to finish initializing", () -> {
            long deadline = System.currentTimeMillis() + 30_000L;
            while (System.currentTimeMillis() < deadline) {
                if (jsBool(driver, "return !!(window.WW_TEST && window.WW_TEST.done)")) {
                    return;
                }
                Thread.sleep(200);
            }
            throw new AssertionError("WorldWind did not finish initializing within 30s");
        });
    }

    private static void attachFirstFrame(byte[] png) {
        Allure.addAttachment("NASA WorldWind spin -- first frame", "image/png",
                new ByteArrayInputStream(png), ".png");
    }

    private static void attachLastFrame(byte[] png) {
        Allure.addAttachment("NASA WorldWind spin -- last frame", "image/png",
                new ByteArrayInputStream(png), ".png");
    }

    /** Attaches the ffmpeg-recorded WebM clip; Allure renders it as inline {@code <video>}. */
    private static void attachSpinVideo(byte[] webm) {
        Allure.addAttachment("NASA WorldWind WebGL globe spin", "video/webm",
                new ByteArrayInputStream(webm), ".webm");
    }

    /**
     * Decodes two screenshots and asserts a meaningful fraction of pixels changed
     * between them. The globe spins ~deg/s of camera longitude, so frames captured
     * seconds apart show clearly different terrain; a frozen render would not.
     */
    private static void assertFramesDiffer(byte[] firstPng, byte[] lastPng) throws IOException {
        Allure.step("Assert the two captured frames differ (the globe moved)", () -> {
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
        });
    }

    /**
     * Decodes a screenshot and asserts the frame is non-blank and multi-coloured.
     * A WebGL frame that failed to render would be a uniform black image, so a
     * meaningful fraction of non-background pixels across several distinct colours
     * is strong evidence the globe actually drew.
     */
    private static void assertGlobeRendered(byte[] png) throws IOException {
        Allure.step("Assert a captured frame is a non-blank, multi-colour render", () -> {
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
        });
    }

    private static String launchLog() throws IOException, InterruptedException {
        return Allure.step("Read launch.sh output from the container",
                () -> ELECTRON.execInContainer("cat", "/tmp/electron.log").getStdout());
    }

    private static String userAgent(RemoteWebDriver driver) {
        return Allure.step("Read navigator.userAgent",
                () -> (String) driver.executeScript("return navigator.userAgent"));
    }

    private static String worldWindError(RemoteWebDriver driver) {
        return Allure.step("Read the WorldWind initialization error, if any",
                () -> (String) driver.executeScript("return window.WW_TEST.error"));
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
     * Builds this test's image chain: the WebGL harness (ChromeDriver + vendored
     * offline NASA WorldWind, same tag/context as {@link WebGlWorldWindFunctionalTest}
     * so the layer cache is shared) on top of the production image from
     * {@link ProductionImage#baseImage()} (the repo's {@code Containerfile}, or a
     * CI-injected tag), then a thin spin layer that adds the spinning page.
     */
    private static ImageFromDockerfile buildHarnessImage() {
        // Build the WebGL harness FROM the production base (ARG BASE_IMAGE).
        new ImageFromDockerfile("electron-gpu-test:webgl-harness", false)
                .withBuildArg("BASE_IMAGE", ProductionImage.baseImage())
                .withFileFromClasspath("Dockerfile", "electron/webgl-harness.Dockerfile")
                .withFileFromClasspath("test-harness-entrypoint.sh", "electron/test-harness-entrypoint.sh")
                .withFileFromClasspath("webgl-worldwind.html", "electron/webgl-worldwind.html")
                .get();

        // Thin layer on top adding only the spinning page (FROM webgl-harness).
        return new ImageFromDockerfile("electron-gpu-test:webgl-spin-harness", false)
                .withFileFromClasspath("Dockerfile", "electron/webgl-spin-harness.Dockerfile")
                .withFileFromClasspath("webgl-worldwind-spin.html", "electron/webgl-worldwind-spin.html");
    }
}
