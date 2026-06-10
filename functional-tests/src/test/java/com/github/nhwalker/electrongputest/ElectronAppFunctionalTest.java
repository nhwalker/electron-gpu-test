package com.github.nhwalker.electrongputest;

import io.qameta.allure.Allure;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
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

    // Sidecar that provides ONLY the virtual X display, publishing its socket into
    // a shared volume that the app container also mounts (see XvfbContainer).
    @Container
    static final XvfbContainer XVFB = new XvfbContainer();

    // prepareClient mounts the shared X socket volume, points DISPLAY at the
    // sidecar, and adds a startup dependency on it.
    @Container
    static final GenericContainer<?> ELECTRON = XVFB.prepareClient(new GenericContainer<>(buildHarnessImage()))
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

    private static String launchLog() throws IOException, InterruptedException {
        return Allure.step("Read launch.sh output from the container",
                () -> ELECTRON.execInContainer("cat", "/tmp/electron.log").getStdout());
    }

    private static String headlineText(RemoteWebDriver driver) {
        return Allure.step("Read the headline text",
                () -> (String) driver.executeScript("return document.getElementById(\"headline\").textContent"));
    }

    private static String userAgent(RemoteWebDriver driver) {
        return Allure.step("Read navigator.userAgent",
                () -> (String) driver.executeScript("return navigator.userAgent"));
    }

    private static boolean headlineIsVisible(RemoteWebDriver driver) {
        return Allure.step("Check the headline rendered with a non-zero box",
                () -> (Boolean) driver.executeScript(
                        "const r = document.getElementById(\"headline\").getBoundingClientRect();"
                                + "return r.width > 0 && r.height > 0"));
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
