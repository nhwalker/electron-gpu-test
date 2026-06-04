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
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

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
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

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

    // The X display the sidecar serves and the app connects to.
    private static final String DISPLAY = ":99";

    // A named Docker volume shared between the sidecar and the app container.
    private static final String X11_SOCKET_DIR = "/tmp/.X11-unix";
    private static final String XSOCK_VOLUME = "electron-gpu-test-xsock";

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

    // Sidecar that provides ONLY the virtual X display, publishing its socket into
    // the shared volume that the app container also mounts.
    @Container
    static final GenericContainer<?> XVFB = new GenericContainer<>(buildXvfbImage())
            .withCreateContainerCmdModifier(shareXSocketVolume())
            .waitingFor(Wait.forLogMessage(".*X-READY.*", 1));

    @Container
    static final GenericContainer<?> ELECTRON = new GenericContainer<>(buildHarnessImage())
            .dependsOn(XVFB, NGINX)
            .withNetwork(NETWORK)
            // Mount the same X socket volume and connect to the sidecar's display.
            .withCreateContainerCmdModifier(shareXSocketVolume())
            .withEnv("DISPLAY", DISPLAY)
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

    @Step("Attach a WebDriver session to the running Electron app")
    private static RemoteWebDriver attachToApp() throws MalformedURLException {
        ChromeOptions options = new ChromeOptions();
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
     * Builds the production image from the repo's {@code Containerfile}, then the
     * thin harness layer (ChromeDriver only) on top of it -- the same harness used
     * by {@link ElectronAppFunctionalTest}. Any host egress-proxy CA is forwarded
     * so the in-image dnf/npm downloads work behind a TLS-intercepting proxy; with
     * direct egress this is a harmless no-op.
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

        return new ImageFromDockerfile("electron-gpu-test:harness", false)
                .withFileFromClasspath("Dockerfile", "electron/harness.Dockerfile")
                .withFileFromClasspath("test-harness-entrypoint.sh", "electron/test-harness-entrypoint.sh")
                .withFileFromClasspath("render-check.html", "electron/render-check.html");
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
