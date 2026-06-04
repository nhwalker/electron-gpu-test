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
 * WebGL functional test: drives the REAL electron-gpu-test app (built from the
 * production {@code Containerfile}) loading a NASA WebWorldWind globe, proves the
 * WebGL path renders on a GPU-less host, and files a screenshot of the rendered
 * globe into the Allure report.
 *
 * It reuses the exact selenium + electron path of {@link ElectronAppFunctionalTest}
 * (Xvfb sidecar sharing an X socket, production {@code /app/launch.sh}, ChromeDriver
 * attached over DevTools). Two differences make WebGL work and stay deterministic
 * offline:
 * <ul>
 *   <li>{@code SOFTWARE_WEBGL=1} tells launch.sh to render via SwiftShader (ANGLE)
 *       instead of {@code --disable-gpu}, so WebGL is available with no GPU; and</li>
 *   <li>the harness vendors WorldWind + its bundled imagery into the image, so the
 *       globe textures load from {@code file://} with no network.</li>
 * </ul>
 *
 * "It renders" is asserted two ways: the page confirms a live WebGL context and a
 * painted frame, and the captured screenshot is decoded and checked to be a
 * non-blank, multi-colour frame (a failed WebGL frame would be a uniform black
 * image).
 *
 * {@code disabledWithoutDocker} so it skips (not fails) where Docker is absent.
 */
@Epic("electron-gpu-test")
@Feature("WebGL rendering")
@Testcontainers(disabledWithoutDocker = true)
class WebGlWorldWindFunctionalTest {

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

    // The WorldWind page the production launcher opens (vendored into the image).
    private static final String PAGE_URL = "file:///opt/worldwind/index.html";

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
    @DisplayName("The production app renders a NASA WorldWind WebGL globe, with no GPU")
    @Description("Builds the production image, boots the real app via launch.sh with SOFTWARE_WEBGL=1 (SwiftShader), loads a vendored offline NASA WorldWind globe, asserts the WebGL frame rendered, and attaches a screenshot to the Allure report.")
    void worldWindGlobeRenders() throws Exception {
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

            // A real WebGL context was obtained and a frame was painted.
            assertTrue(jsBool(driver, "return !!window.WW_TEST.webgl"),
                    "WorldWind did not obtain a WebGL context");
            assertTrue(jsBool(driver, "return window.WW_TEST.ready === true"),
                    "WorldWind never painted a frame");

            String renderer = jsString(driver, "return window.WW_TEST.glRenderer");
            assertNotNull(renderer, "No WebGL renderer string was reported");

            // Prove we drove the real Electron app on the X11 backend (the shared
            // display), not a standalone/headless browser.
            String ua = userAgent(driver);
            assertTrue(ua.contains("Electron/41") && ua.contains("electron-gpu-test/") && ua.contains("X11"),
                    "Expected the Electron app on the X11 backend but UA was: " + ua);

            // Capture the rendered globe into the Allure report...
            byte[] png = worldWindScreenshot(driver);
            // ...and prove the WebGL frame actually drew (non-blank, multi-colour).
            assertGlobeRendered(png);
        } finally {
            driver.quit();
        }
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

    @Step("Capture a screenshot of the rendered WorldWind globe")
    @Attachment(value = "NASA WorldWind WebGL globe", type = "image/png", fileExtension = ".png")
    private static byte[] worldWindScreenshot(RemoteWebDriver driver) {
        return driver.getScreenshotAs(OutputType.BYTES);
    }

    /**
     * Decodes the screenshot and asserts the frame is non-blank and multi-coloured.
     * A WebGL frame that failed to render would be a uniform black image, so a
     * meaningful fraction of non-background pixels across several distinct colours
     * is strong evidence the globe actually drew.
     */
    @Step("Assert the captured frame is a non-blank, multi-colour render")
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
     * Builds the production image from the repo's {@code Containerfile}, then a
     * thin harness layer on top that adds ChromeDriver and the vendored NASA
     * WorldWind globe + page (offline imagery). Any host egress-proxy CA is
     * forwarded so the in-image dnf/npm downloads work behind a TLS-intercepting
     * proxy; with direct egress this is a harmless no-op.
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

        return new ImageFromDockerfile("electron-gpu-test:webgl-harness", false)
                .withFileFromClasspath("Dockerfile", "electron/webgl-harness.Dockerfile")
                .withFileFromClasspath("test-harness-entrypoint.sh", "electron/test-harness-entrypoint.sh")
                .withFileFromClasspath("webgl-worldwind.html", "electron/webgl-worldwind.html");
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
