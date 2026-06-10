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
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mutual-TLS functional test: drives the REAL electron-gpu-test app (built from
 * the production {@code Containerfile}) against an nginx server that serves HTTPS
 * with a custom CA AND requires a client certificate ({@code ssl_verify_client
 * on}). The app trusts the server only because the test CA is imported into its
 * NSS DB at launch, and the connection only completes because the app presents a
 * matching client certificate -- so a single successful page load proves both
 * custom-CA server trust and mTLS end to end.
 *
 * It combines two existing patterns:
 * <ul>
 *   <li>the shared {@link Network} + nginx-by-alias setup of
 *       {@link BrowserContainerFunctionalTest}; and</li>
 *   <li>the Xvfb sidecar + X-socket-volume + production-harness-image setup of
 *       {@link ElectronAppFunctionalTest}.</li>
 * </ul>
 *
 * The certs are dropped into the app container's default scan directory
 * {@code /certs}; the production {@code launch.sh} discovers and imports them
 * (CA vs client cert+key pair) with no per-file configuration, which also
 * exercises the directory auto-pairing rules.
 *
 * {@code disabledWithoutDocker} so it skips (not fails) where Docker is absent.
 */
@Epic("electron-gpu-test")
@Feature("TLS / mutual TLS")
@Testcontainers(disabledWithoutDocker = true)
class TlsMtlsFunctionalTest {

    // ChromeDriver listens on 4444 inside the harness (see the entrypoint).
    private static final int CHROMEDRIVER_PORT = 4444;

    // The app's DevTools endpoint inside the container that ChromeDriver attaches
    // to (launch.sh is started with --remote-debugging-port=9222).
    private static final String DEBUGGER_ADDRESS = "127.0.0.1:9222";

    // nginx network alias; must match the server cert SAN (see certs/gen-certs.sh).
    private static final String SERVER_ALIAS = "web";
    private static final String PAGE_URL = "https://" + SERVER_ALIAS + "/";

    // Shared network so the app container can reach nginx by alias over TLS.
    static final Network NETWORK = Network.newNetwork();

    // nginx serving HTTPS that REQUIRES a client cert chained to the test CA.
    @Container
    static final GenericContainer<?> NGINX = new GenericContainer<>("nginx:alpine")
            .withNetwork(NETWORK)
            .withNetworkAliases(SERVER_ALIAS)
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("tls/nginx-mtls.conf"),
                    "/etc/nginx/conf.d/default.conf")
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("tls/index.html"),
                    "/usr/share/nginx/html/index.html")
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("certs/server.crt"),
                    "/etc/nginx/certs/server.crt")
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("certs/server.key"),
                    "/etc/nginx/certs/server.key")
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("certs/test-ca.crt"),
                    "/etc/nginx/certs/test-ca.crt")
            .withExposedPorts(443)
            .waitingFor(Wait.forListeningPort());

    // Sidecar that provides the virtual X display, publishing its socket into a
    // shared volume that the app container also mounts (see XvfbContainer).
    @Container
    static final XvfbContainer XVFB = new XvfbContainer();

    // prepareClient mounts the shared X socket volume, points DISPLAY at the
    // sidecar, and adds a startup dependency on it; the app also depends on NGINX.
    @Container
    static final GenericContainer<?> ELECTRON = XVFB.prepareClient(new GenericContainer<>(buildHarnessImage()))
            .dependsOn(NGINX)
            .withNetwork(NETWORK)
            // Tell the harness entrypoint which page to open via launch.sh.
            .withEnv("TARGET_URL", PAGE_URL)
            // Drop the CA + client cert/key into the default scan dir; launch.sh's
            // setup_cert_store auto-imports them (CA, plus the client.crt/.key pair).
            // Force mode 0644: the non-root app user must read these, but OpenSSL
            // writes private keys 0600 and Testcontainers would copy them root-owned.
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("certs/test-ca.crt", 0644), "/certs/test-ca.crt")
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("certs/client.crt", 0644), "/certs/client.crt")
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("certs/client.key", 0644), "/certs/client.key")
            .withExposedPorts(CHROMEDRIVER_PORT)
            .withStartupTimeout(Duration.ofSeconds(240))
            .waitingFor(
                    Wait.forHttp("/status")
                            .forPort(CHROMEDRIVER_PORT)
                            .forStatusCode(200)
                            .forResponsePredicate(body -> body.contains("\"ready\":true")));

    @Test
    @DisplayName("The production app loads an mTLS page: custom CA trusted + client cert presented")
    @Description("Builds the production image, serves HTTPS with ssl_verify_client on from an nginx sidecar, mounts the CA + client cert/key into /certs so launch.sh imports them into NSS, and asserts the app rendered the page -- proving both custom-CA trust and mutual TLS.")
    void appLoadsMutualTlsPage() throws Exception {
        // launch.sh's cert-store must have imported the mounted CA and client cert.
        String launchLog = launchLog();
        assertTrue(launchLog.contains("cert-store: imported CA test-ca"),
                "Expected the test CA to be imported. Log was:\n" + launchLog);
        assertTrue(launchLog.contains("cert-store: imported client cert client"),
                "Expected the client cert to be imported. Log was:\n" + launchLog);

        RemoteWebDriver driver = attachToApp();
        try {
            // The app opened this HTTPS page itself from its argv (main.js -> loadURL).
            // Reaching it at all requires server-CA trust AND a presented client cert.
            assertEquals(PAGE_URL, driver.getCurrentUrl());
            assertEquals("electron-gpu-test mTLS check", driver.getTitle());
            assertEquals("Served over mutual TLS", headlineText(driver));
            assertTrue(headlineIsVisible(driver), "Headline rendered with zero size");

            // The app actually selected a client certificate for the handshake.
            assertTrue(launchLog().contains("cert-store: selected client certificate"),
                    "Expected the app to select a client certificate. Log was:\n" + launchLog());

            // Prove we drove the real Electron app on the X11 backend.
            String ua = userAgent(driver);
            assertTrue(ua.contains("Electron/41") && ua.contains("electron-gpu-test/") && ua.contains("X11"),
                    "Expected the Electron app on the X11 backend but UA was: " + ua);
        } finally {
            driver.quit();
        }
    }

    private static RemoteWebDriver attachToApp() throws MalformedURLException {
        return Allure.step("Attach a WebDriver session to the running Electron app", () -> {
            ChromeOptions options = new ChromeOptions();
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
     * Builds the thin harness layer (ChromeDriver only -- the same harness used by
     * {@link ElectronAppFunctionalTest}) on top of the production image resolved
     * by {@link ProductionImage#baseImage()} (the repo's {@code Containerfile}, or
     * a CI-injected tag). The harness Dockerfile picks the base up via its
     * {@code ARG BASE_IMAGE}.
     */
    private static ImageFromDockerfile buildHarnessImage() {
        return new ImageFromDockerfile("electron-gpu-test:harness", false)
                .withBuildArg("BASE_IMAGE", ProductionImage.baseImage())
                .withFileFromClasspath("Dockerfile", "electron/harness.Dockerfile")
                .withFileFromClasspath("test-harness-entrypoint.sh", "electron/test-harness-entrypoint.sh")
                .withFileFromClasspath("render-check.html", "electron/render-check.html");
    }
}
