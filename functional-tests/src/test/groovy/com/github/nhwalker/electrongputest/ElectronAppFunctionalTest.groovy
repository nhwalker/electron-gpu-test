package com.github.nhwalker.electrongputest

import io.qameta.allure.Description
import io.qameta.allure.Epic
import io.qameta.allure.Feature
import io.qameta.allure.Step
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.remote.RemoteWebDriver
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.builder.ImageFromDockerfile
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.util.function.Predicate

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertTrue

/**
 * Drives the REAL electron-gpu-test app, built from the production
 * {@code Containerfile} -- not a separate test build.
 *
 * Testcontainers first builds the production image (NVIDIA stack and all), then
 * builds a thin harness layer ON TOP of it that adds only Xvfb + a version-matched
 * ChromeDriver (see {@code electron/harness.Dockerfile}). The harness boots the app
 * through the production {@code /app/launch.sh}, which detects the absence of a GPU
 * and falls back to software rendering -- proving the NVIDIA stack is harmless on a
 * GPU-less host. Selenium then attaches to the running app and asserts on what it
 * rendered.
 *
 * {@code disabledWithoutDocker} so it skips (not fails) where Docker is absent.
 */
@Epic('electron-gpu-test')
@Feature('Electron app rendering')
@Testcontainers(disabledWithoutDocker = true)
class ElectronAppFunctionalTest {

    // ChromeDriver listens on 4444 inside the harness (see the entrypoint).
    private static final int CHROMEDRIVER_PORT = 4444

    // The app's DevTools endpoint inside the container that ChromeDriver attaches
    // to (launch.sh is started with --remote-debugging-port=9222).
    private static final String DEBUGGER_ADDRESS = '127.0.0.1:9222'

    @Container
    static final GenericContainer<?> ELECTRON = new GenericContainer<>(buildHarnessImage())
            .withExposedPorts(CHROMEDRIVER_PORT)
            .withStartupTimeout(Duration.ofSeconds(240))
            .waitingFor(
                    Wait.forHttp('/status')
                            .forPort(CHROMEDRIVER_PORT)
                            .forStatusCode(200)
                            .forResponsePredicate({ String body -> body.contains('"ready":true') } as Predicate))

    @Test
    @DisplayName('The production app renders a page, falling back to software rendering with no GPU')
    @Description('Builds the production image, boots the real app via launch.sh (which selects software rendering on a GPU-less host), and drives it with Selenium.')
    void productionAppRendersPage() {
        // launch.sh must have taken the no-GPU software path -- i.e. the NVIDIA
        // stack stayed inert rather than failing the launch.
        assertTrue(launchLog().contains('software rendering'),
                "Expected launch.sh to fall back to software rendering. Log was:\n${launchLog()}".toString())

        RemoteWebDriver driver = attachToApp()
        try {
            // The app opened this page itself from its argv (main.js -> loadURL).
            assertEquals('file:///opt/render-check.html', driver.currentUrl)
            assertEquals('electron render check', driver.title)
            assertEquals('Electron rendered this page', headlineText(driver))

            // Prove we drove the real Electron app, not a standalone browser:
            // Electron stamps the app name + "Electron/<version>" into the UA.
            String ua = userAgent(driver)
            assertTrue(ua.contains('Electron/41'),
                    "Expected an Electron user agent but was: ${ua}".toString())
            assertTrue(ua.contains('electron-gpu-test/'),
                    "Expected the app's name in the user agent but was: ${ua}".toString())

            // Prove the page actually rendered (compositing works in software mode).
            assertTrue(headlineIsVisible(driver), 'Headline rendered with zero size')
        } finally {
            driver.quit()
        }
    }

    @Step('Attach a WebDriver session to the running Electron app')
    private static RemoteWebDriver attachToApp() {
        ChromeOptions options = new ChromeOptions()
        // Attach to the app already launched by launch.sh inside the container.
        options.setExperimentalOption('debuggerAddress', DEBUGGER_ADDRESS)
        URL url = URI.create("http://${ELECTRON.host}:${ELECTRON.getMappedPort(CHROMEDRIVER_PORT)}/").toURL()
        return new RemoteWebDriver(url, options)
    }

    @Step('Read launch.sh output from the container')
    private static String launchLog() {
        return ELECTRON.execInContainer('cat', '/tmp/electron.log').stdout
    }

    @Step('Read the headline text')
    private static String headlineText(RemoteWebDriver driver) {
        return driver.executeScript('return document.getElementById("headline").textContent') as String
    }

    @Step('Read navigator.userAgent')
    private static String userAgent(RemoteWebDriver driver) {
        return driver.executeScript('return navigator.userAgent') as String
    }

    @Step('Check the headline rendered with a non-zero box')
    private static boolean headlineIsVisible(RemoteWebDriver driver) {
        return driver.executeScript(
                'const r = document.getElementById("headline").getBoundingClientRect();' +
                        'return r.width > 0 && r.height > 0') as Boolean
    }

    /**
     * Builds the production image from the repo's {@code Containerfile}, then a
     * thin harness layer (Xvfb + ChromeDriver) on top of it. Any host egress-proxy
     * CA is forwarded so the in-image dnf/npm downloads work behind a TLS-
     * intercepting proxy; with direct egress this is a harmless no-op.
     */
    private static ImageFromDockerfile buildHarnessImage() {
        Path repoRoot = Paths.get(System.getProperty('user.dir')).toAbsolutePath().parent
        assertTrue(Files.exists(repoRoot.resolve('Containerfile')),
                "Could not locate the production Containerfile at ${repoRoot}".toString())

        // Build the production image first so the harness can build FROM it.
        new ImageFromDockerfile('electron-gpu-test:undertest', false)
                .withFileFromPath('Dockerfile', repoRoot.resolve('Containerfile'))
                .withFileFromPath('app', repoRoot.resolve('app'))
                .withFileFromPath('rocky9.repo', repoRoot.resolve('rocky9.repo'))
                .withFileFromPath('extra-cas', resolveExtraCaDir())
                .get()

        return new ImageFromDockerfile('electron-gpu-test:harness', false)
                .withFileFromClasspath('Dockerfile', 'electron/harness.Dockerfile')
                .withFileFromClasspath('test-harness-entrypoint.sh', 'electron/test-harness-entrypoint.sh')
                .withFileFromClasspath('render-check.html', 'electron/render-check.html')
    }

    /**
     * Returns a directory to mount at the production build context's
     * {@code extra-cas/}. Behind a TLS-intercepting proxy it contains the host CA
     * bundle so dnf/npm trust the proxy; otherwise it holds only a placeholder so
     * the Containerfile's COPY succeeds.
     */
    private static Path resolveExtraCaDir() {
        Path dir = Files.createTempDirectory('electron-extra-cas')
        Path hostBundle = Paths.get('/etc/ssl/certs/ca-certificates.crt')
        if (behindEgressProxy() && Files.exists(hostBundle)) {
            Files.copy(hostBundle, dir.resolve('host-egress-bundle.crt'))
        } else {
            Files.writeString(dir.resolve('.keep'), '')
        }
        return dir
    }

    /** Detects the sandbox/corporate egress proxy by its installed CA marker. */
    private static boolean behindEgressProxy() {
        Path caDir = Paths.get('/usr/local/share/ca-certificates')
        if (!Files.isDirectory(caDir)) {
            return false
        }
        Files.list(caDir).withCloseable { stream ->
            return stream.anyMatch { p -> p.fileName.toString().toLowerCase().contains('egress') }
        }
    }
}
