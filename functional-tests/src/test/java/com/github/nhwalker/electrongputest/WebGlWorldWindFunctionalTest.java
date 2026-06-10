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

    // The WorldWind page the production launcher opens (vendored into the image).
    private static final String PAGE_URL = "file:///opt/worldwind/index.html";

    // Sidecar that provides ONLY the virtual X display, publishing its socket into
    // a shared volume that the app container also mounts (see XvfbContainer).
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

    private static byte[] worldWindScreenshot(RemoteWebDriver driver) {
        return Allure.step("Capture a screenshot of the rendered WorldWind globe", () -> {
            byte[] png = driver.getScreenshotAs(OutputType.BYTES);
            Allure.addAttachment("NASA WorldWind WebGL globe", "image/png",
                    new ByteArrayInputStream(png), ".png");
            return png;
        });
    }

    /**
     * Decodes the screenshot and asserts the frame is non-blank and multi-coloured.
     * A WebGL frame that failed to render would be a uniform black image, so a
     * meaningful fraction of non-background pixels across several distinct colours
     * is strong evidence the globe actually drew.
     */
    private static void assertGlobeRendered(byte[] png) throws IOException {
        Allure.step("Assert the captured frame is a non-blank, multi-colour render", () -> {
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

    private static String jsString(RemoteWebDriver driver, String script) {
        return (String) driver.executeScript(script);
    }

    private static boolean jsBool(RemoteWebDriver driver, String script) {
        return Boolean.TRUE.equals(driver.executeScript(script));
    }

    /**
     * Builds the thin harness layer (ChromeDriver + vendored offline NASA
     * WorldWind globe + page) on top of the production image resolved by
     * {@link ProductionImage#baseImage()} -- built from the repo's
     * {@code Containerfile}, or a tag injected by CI. The harness Dockerfile
     * picks the base up via its {@code ARG BASE_IMAGE}.
     */
    private static ImageFromDockerfile buildHarnessImage() {
        return new ImageFromDockerfile("electron-gpu-test:webgl-harness", false)
                .withBuildArg("BASE_IMAGE", ProductionImage.baseImage())
                .withFileFromClasspath("Dockerfile", "electron/webgl-harness.Dockerfile")
                .withFileFromClasspath("test-harness-entrypoint.sh", "electron/test-harness-entrypoint.sh")
                .withFileFromClasspath("webgl-worldwind.html", "electron/webgl-worldwind.html");
    }
}
