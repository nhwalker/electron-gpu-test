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
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code vnc://} check: drives the REAL electron-gpu-test app (built from
 * the production {@code Containerfile}) against a REAL VNC server, and proves
 * the remote desktop actually painted in the app's noVNC-backed viewer.
 *
 * The app is started with a single {@code vnc://vncserver:5900} argument, which
 * exercises the whole path added for it:
 * <ul>
 *   <li>{@code main.js} recognises the URL and opens the viewer page the app
 *       serves itself on loopback (so the window URL is {@code http://127.0.0.1:<port>/viewer.html});</li>
 *   <li>the main process bridges that page's WebSocket onto a TCP connection to
 *       the VNC server -- a browser engine cannot open one itself;</li>
 *   <li>noVNC completes the RFB handshake, authenticates with the password taken
 *       from {@code VNC_PASSWORD} (kept out of argv), and decodes the framebuffer.</li>
 * </ul>
 *
 * It reuses the shared {@link Network} + server-by-alias setup of
 * {@link TlsMtlsFunctionalTest} and the Xvfb sidecar + production-harness-image
 * setup of {@link ElectronAppFunctionalTest}.
 *
 * "The desktop rendered" is asserted from the canvas noVNC draws into: it has the
 * remote framebuffer's dimensions, its pixels are non-blank and multi-coloured
 * (the server draws a fixed picture), and they keep changing (the server also
 * runs a ticking clock) -- which proves framebuffer updates keep flowing rather
 * than one frame having arrived by luck.
 *
 * {@code disabledWithoutDocker} so it skips (not fails) where Docker is absent.
 */
@Epic("electron-gpu-test")
@Feature("VNC (noVNC) viewer")
@Testcontainers(disabledWithoutDocker = true)
class VncViewerFunctionalTest {

    // ChromeDriver listens on 4444 inside the harness (see the entrypoint).
    private static final int CHROMEDRIVER_PORT = 4444;

    // The app's DevTools endpoint inside the container that ChromeDriver attaches
    // to (launch.sh is started with --remote-debugging-port=9222).
    private static final String DEBUGGER_ADDRESS = "127.0.0.1:9222";

    // The VNC server sidecar: alias, port, password and the framebuffer size it
    // serves (see containers/vnc-server-entrypoint.sh).
    private static final String VNC_ALIAS = "vncserver";
    private static final int VNC_PORT = 5900;
    private static final String VNC_PASSWORD = "s3cret";
    private static final int REMOTE_WIDTH = 1024;
    private static final int REMOTE_HEIGHT = 768;

    private static final String TARGET_URL = "vnc://" + VNC_ALIAS + ":" + VNC_PORT;

    // Shared network so the app container can reach the VNC server by alias.
    static final Network NETWORK = Network.newNetwork();

    @Container
    static final GenericContainer<?> VNC_SERVER = new GenericContainer<>(TestImages.vncServer())
            .withNetwork(NETWORK)
            .withNetworkAliases(VNC_ALIAS)
            .withEnv("VNC_PASSWORD", VNC_PASSWORD)
            .withExposedPorts(VNC_PORT)
            .waitingFor(Wait.forListeningPort());

    // Sidecar that provides the virtual X display the app itself runs on.
    @Container
    static final XvfbContainer XVFB = new XvfbContainer();

    // prepareClient mounts the shared X socket volume, points DISPLAY at the
    // sidecar, and adds a startup dependency on it; the app also depends on the
    // VNC server.
    @Container
    static final GenericContainer<?> ELECTRON = XVFB.prepareClient(new GenericContainer<>(TestImages.harness()))
            .dependsOn(VNC_SERVER)
            .withNetwork(NETWORK)
            // Tell the harness entrypoint which URL to open via launch.sh.
            .withEnv("TARGET_URL", TARGET_URL)
            // The recommended way to pass a VNC password: out of the URL, so it
            // never reaches argv (and so `ps` inside the container).
            .withEnv("VNC_PASSWORD", VNC_PASSWORD)
            .withExposedPorts(CHROMEDRIVER_PORT)
            .withStartupTimeout(Duration.ofSeconds(240))
            .waitingFor(
                    Wait.forHttp("/status")
                            .forPort(CHROMEDRIVER_PORT)
                            .forStatusCode(200)
                            .forResponsePredicate(body -> body.contains("\"ready\":true")));

    @Test
    @DisplayName("A vnc:// URL opens a live remote desktop in the app's noVNC viewer")
    @Description("Runs the pre-built production image against a real x11vnc server, opens vnc://vncserver:5900 through the production launch.sh, and asserts the app bridged it to its own loopback noVNC page, authenticated, and painted the live remote framebuffer.")
    void vncUrlOpensRemoteDesktop() throws Exception {
        // The main process must have opened the viewer and bridged it to the
        // server's TCP port -- the half a renderer cannot do for itself.
        String launchLog = launchLog();
        assertTrue(launchLog.contains("vnc: opening " + TARGET_URL),
                "Expected main.js to open the VNC target. Log was:\n" + launchLog);
        assertTrue(launchLog.contains("vnc: bridging a viewer to " + VNC_ALIAS + ":" + VNC_PORT),
                "Expected the main process to bridge the viewer to the VNC server. Log was:\n" + launchLog);

        RemoteWebDriver driver = attachToApp();
        try {
            // The window is on the app's own loopback viewer page, not on the
            // vnc:// URL (which no browser engine can load).
            String currentUrl = driver.getCurrentUrl();
            assertTrue(currentUrl.startsWith("http://127.0.0.1:") && currentUrl.contains("/viewer.html?s="),
                    "Expected the loopback noVNC viewer page but was: " + currentUrl);

            waitForConnection(driver);
            assertNull(jsString(driver, "return window.VNC_TEST.error"),
                    "The viewer reported an error: " + jsString(driver, "return window.VNC_TEST.error"));
            assertEquals("connected", jsString(driver, "return window.VNC_TEST.state"));

            // The RFB handshake completed: the server named its desktop.
            assertNotNull(jsString(driver, "return window.VNC_TEST.desktopName"),
                    "The server never sent a desktop name");

            // The remote picture decoded: non-blank and multi-coloured.
            waitForRemoteFramebuffer(driver);

            // The canvas took the remote framebuffer's size, so what it painted
            // is the whole remote screen rather than some default-sized box.
            assertEquals(REMOTE_WIDTH, jsLong(driver, "return document.querySelector('#screen canvas').width"),
                    "Canvas width did not match the remote framebuffer");
            assertEquals(REMOTE_HEIGHT, jsLong(driver, "return document.querySelector('#screen canvas').height"),
                    "Canvas height did not match the remote framebuffer");

            // Prove we drove the real Electron app on the X11 backend (the shared
            // display), not a standalone/headless browser.
            String ua = userAgent(driver);
            assertTrue(ua.contains("Electron/41") && ua.contains("electron-gpu-test/") && ua.contains("X11"),
                    "Expected the Electron app on the X11 backend but UA was: " + ua);

            // Updates keep arriving: the server's clock ticks every second.
            assertFramebufferKeepsUpdating(driver);

            screenshot(driver);
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

    private static void waitForConnection(RemoteWebDriver driver) throws InterruptedException {
        Allure.step("Wait for the viewer to connect to the VNC server", () -> {
            long deadline = System.currentTimeMillis() + 60_000L;
            String state = null;
            while (System.currentTimeMillis() < deadline) {
                state = jsString(driver, "return window.VNC_TEST && window.VNC_TEST.state");
                if ("connected".equals(state)) {
                    return;
                }
                Thread.sleep(250);
            }
            throw new AssertionError("The viewer never connected (last state: " + state
                    + ", error: " + jsString(driver, "return window.VNC_TEST && window.VNC_TEST.error") + ")");
        });
    }

    /**
     * Waits for the canvas noVNC paints into to hold a non-blank, multi-coloured
     * frame. Connecting only means the RFB handshake finished -- the first
     * framebuffer update lands a moment later -- so this polls rather than
     * asserting once. The server draws a fixed picture on a coloured root
     * window, so a canvas that stays uniform means the framebuffer never decoded.
     */
    private static void waitForRemoteFramebuffer(RemoteWebDriver driver) throws InterruptedException {
        Allure.step("Wait for the remote framebuffer to decode into a non-blank, multi-colour frame", () -> {
            // Sampling happens in the page: pulling a 1024x768 image back over
            // the wire to count colours in Java would be pure waste.
            String countColors = """
                    const canvas = document.querySelector('#screen canvas');
                    const data = canvas.getContext('2d').getImageData(0, 0, canvas.width, canvas.height).data;
                    const colors = new Set();
                    for (let i = 0; i < data.length; i += 4 * 401) {
                      colors.add(((data[i] >> 4) << 8) | ((data[i + 1] >> 4) << 4) | (data[i + 2] >> 4));
                    }
                    return colors.size;
                    """;
            long deadline = System.currentTimeMillis() + 30_000L;
            long distinctColors = 0;
            while (System.currentTimeMillis() < deadline) {
                distinctColors = jsLong(driver, countColors);
                if (distinctColors >= 3) {
                    return;
                }
                Thread.sleep(250);
            }
            throw new AssertionError("The remote framebuffer never decoded: the canvas still has only "
                    + distinctColors + " distinct colour(s) after 30s");
        });
    }

    /**
     * The server runs a clock that moves every second, so the framebuffer must
     * keep changing. A viewer that decoded one frame and then stalled (a broken
     * update loop, a dead bridge) would keep returning the same signature.
     */
    private static void assertFramebufferKeepsUpdating(RemoteWebDriver driver) throws InterruptedException {
        Allure.step("Assert framebuffer updates keep arriving", () -> {
            String signature = "return (() => {"
                    + "const canvas = document.querySelector('#screen canvas');"
                    + "const data = canvas.getContext('2d').getImageData(0, 0, canvas.width, canvas.height).data;"
                    + "let hash = 0;"
                    + "for (let i = 0; i < data.length; i += 4 * 37) { hash = (hash * 31 + data[i] + data[i + 1] * 3) | 0; }"
                    + "return String(hash);"
                    + "})()";
            String first = jsString(driver, signature);
            long deadline = System.currentTimeMillis() + 20_000L;
            while (System.currentTimeMillis() < deadline) {
                Thread.sleep(500);
                if (!first.equals(jsString(driver, signature))) {
                    return;
                }
            }
            throw new AssertionError("The remote framebuffer never changed in 20s; updates stopped arriving");
        });
    }

    private static void screenshot(RemoteWebDriver driver) {
        Allure.step("Capture the remote desktop as the app rendered it", () -> {
            byte[] png = driver.getScreenshotAs(OutputType.BYTES);
            Allure.addAttachment("Remote desktop over vnc://", "image/png",
                    new ByteArrayInputStream(png), ".png");
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

    private static String jsString(RemoteWebDriver driver, String script) {
        Object value = driver.executeScript(script);
        return value == null ? null : String.valueOf(value);
    }

    private static long jsLong(RemoteWebDriver driver, String script) {
        return ((Number) driver.executeScript(script)).longValue();
    }
}
