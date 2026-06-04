package com.github.nhwalker.electrongputest;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.Mount;
import com.github.dockerjava.api.model.MountType;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Step;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives the REAL electron-gpu-test app, built from the production
 * {@code Containerfile} -- not a separate test build.
 *
 * The virtual display lives in its OWN sidecar container (Xvfb), which shares its
 * {@code /tmp/.X11-unix} socket with the app container through a Docker volume.
 * The app thus connects to an X server running outside its container -- the same
 * model the production image uses against the host's X server -- so the app image
 * itself carries no test-only display tooling.
 *
 * Testcontainers builds the production image (NVIDIA stack and all), then a thin
 * harness layer on top that adds only a version-matched ChromeDriver. The harness
 * boots the app through the production {@code /app/launch.sh}, which detects the
 * absence of a GPU and falls back to software rendering -- proving the NVIDIA
 * stack is harmless on a GPU-less host. Selenium then attaches to the running app
 * and asserts on what it rendered.
 *
 * {@code disabledWithoutDocker} so it skips (not fails) where Docker is absent.
 */
@Epic("electron-gpu-test")
@Feature("Electron app rendering")
@Testcontainers(disabledWithoutDocker = true)
class ElectronAppFunctionalTest {

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
            .withExposedPorts(CHROMEDRIVER_PORT)
            .withStartupTimeout(Duration.ofSeconds(240))
            .waitingFor(
                    Wait.forHttp("/status")
                            .forPort(CHROMEDRIVER_PORT)
                            .forStatusCode(200)
                            .forResponsePredicate(body -> body.contains("\"ready\":true")));

    @Test
    @DisplayName("The production app renders a page via a sidecar X server, with no GPU")
    @Description("Builds the production image, serves an X display from a sidecar container, boots the real app via launch.sh (software rendering on a GPU-less host), and drives it with Selenium.")
    void productionAppRendersPage() throws Exception {
        // launch.sh must have taken the no-GPU software path -- i.e. the NVIDIA
        // stack stayed inert rather than failing the launch.
        String launchLog = launchLog();
        assertTrue(launchLog.contains("software rendering"),
                "Expected launch.sh to fall back to software rendering. Log was:\n" + launchLog);

        RemoteWebDriver driver = attachToApp();
        try {
            // The app opened this page itself from its argv (main.js -> loadURL).
            assertEquals("file:///opt/render-check.html", driver.getCurrentUrl());
            assertEquals("electron render check", driver.getTitle());
            assertEquals("Electron rendered this page", headlineText(driver));

            // Prove we drove the real Electron app on the X11 backend (the shared
            // display), not a standalone/headless browser. Electron stamps the app
            // name + "Electron/<version>" into the UA.
            String ua = userAgent(driver);
            assertTrue(ua.contains("Electron/41"),
                    "Expected an Electron user agent but was: " + ua);
            assertTrue(ua.contains("electron-gpu-test/"),
                    "Expected the app's name in the user agent but was: " + ua);
            assertTrue(ua.contains("X11"),
                    "Expected the X11 (shared display) backend but UA was: " + ua);

            // Prove the page actually rendered (compositing works in software mode).
            assertTrue(headlineIsVisible(driver), "Headline rendered with zero size");
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

    @Step("Read launch.sh output from the container")
    private static String launchLog() throws IOException, InterruptedException {
        return ELECTRON.execInContainer("cat", "/tmp/electron.log").getStdout();
    }

    @Step("Read the headline text")
    private static String headlineText(RemoteWebDriver driver) {
        return (String) driver.executeScript("return document.getElementById(\"headline\").textContent");
    }

    @Step("Read navigator.userAgent")
    private static String userAgent(RemoteWebDriver driver) {
        return (String) driver.executeScript("return navigator.userAgent");
    }

    @Step("Check the headline rendered with a non-zero box")
    private static boolean headlineIsVisible(RemoteWebDriver driver) {
        return (Boolean) driver.executeScript(
                "const r = document.getElementById(\"headline\").getBoundingClientRect();"
                        + "return r.width > 0 && r.height > 0");
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
     * Builds the thin harness layer (ChromeDriver only) on top of the production
     * image resolved by {@link ProductionImage#baseImage()} -- built from the
     * repo's {@code Containerfile}, or a tag injected by CI. The harness
     * Dockerfile picks the base up via its {@code ARG BASE_IMAGE}.
     */
    private static ImageFromDockerfile buildHarnessImage() {
        return new ImageFromDockerfile("electron-gpu-test:harness", false)
                .withBuildArg("BASE_IMAGE", ProductionImage.baseImage())
                .withFileFromClasspath("Dockerfile", "electron/harness.Dockerfile")
                .withFileFromClasspath("test-harness-entrypoint.sh", "electron/test-harness-entrypoint.sh")
                .withFileFromClasspath("render-check.html", "electron/render-check.html");
    }
}
