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
 * Drives the REAL electron-gpu-test app (not standalone Chromium) with Selenium.
 *
 * Testcontainers builds a CI-runnable image from the repo's {@code app/} (see
 * {@code src/test/resources/electron/Dockerfile}): it drops the production
 * NVIDIA/GPU stack and instead runs the app headlessly under Xvfb with software
 * rendering, fronted by a version-matched ChromeDriver. ChromeDriver launches
 * the Electron binary on demand, so this exercises the actual app code path:
 * argv -> BrowserWindow -> loadURL.
 *
 * {@code disabledWithoutDocker} so it skips (not fails) where Docker is absent.
 */
@Epic('electron-gpu-test')
@Feature('Electron app rendering')
@Testcontainers(disabledWithoutDocker = true)
class ElectronAppFunctionalTest {

    // ChromeDriver listens on 4444 inside the container (see the entrypoint).
    private static final int CHROMEDRIVER_PORT = 4444

    // The wrapper ChromeDriver launches as the "browser binary" (injects the
    // Electron executable + app path); must match the Dockerfile.
    private static final String ELECTRON_BINARY = '/usr/local/bin/electron-run'

    @Container
    static final GenericContainer<?> ELECTRON = new GenericContainer<>(buildElectronImage())
            .withExposedPorts(CHROMEDRIVER_PORT)
            .withStartupTimeout(Duration.ofSeconds(180))
            .waitingFor(
                    Wait.forHttp('/status')
                            .forPort(CHROMEDRIVER_PORT)
                            .forStatusCode(200)
                            .forResponsePredicate({ String body -> body.contains('"ready":true') } as Predicate))

    @Test
    @DisplayName('The real Electron app renders a page (software rendering, no GPU)')
    @Description('ChromeDriver launches the actual app/ Electron build; Selenium asserts on what the app rendered and proves it is Electron, not standalone Chromium.')
    void electronAppRendersPage() {
        RemoteWebDriver driver = newSession()
        try {
            // The app opened this page itself from its argv (main.js -> loadURL).
            assertEquals('file:///app/render-check.html', driver.currentUrl)
            assertEquals('electron render check', driver.title)
            assertEquals('Electron rendered this page', headlineText(driver))

            // Prove we drove Electron (the real app), not a standalone browser:
            // Electron stamps the app name + "Electron/<version>" into the UA.
            String ua = userAgent(driver)
            assertTrue(ua.contains('Electron/41'),
                    "Expected an Electron user agent but was: ${ua}".toString())
            assertTrue(ua.contains('electron-gpu-test/'),
                    "Expected the app's name in the user agent but was: ${ua}".toString())

            // Prove the page actually rendered (compositing works under software
            // rendering, with no GPU available).
            assertTrue(headlineIsVisible(driver), 'Headline rendered with zero size')
        } finally {
            driver.quit()
        }
    }

    @Step('Open a WebDriver session that launches the Electron app')
    private static RemoteWebDriver newSession() {
        ChromeOptions options = new ChromeOptions()
        options.setBinary(ELECTRON_BINARY)
        // Required as root in a container; --disable-gpu forces software rendering.
        options.addArguments('--no-sandbox', '--disable-dev-shm-usage', '--disable-gpu')
        URL url = URI.create("http://${ELECTRON.host}:${ELECTRON.getMappedPort(CHROMEDRIVER_PORT)}/").toURL()
        return new RemoteWebDriver(url, options)
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
     * Composes the build context for the test image from this module's resources
     * plus the real {@code app/} at the repo root. Any host egress-proxy CA is
     * forwarded so the in-image {@code npm install} works behind a TLS-intercepting
     * proxy; with direct egress this is a harmless no-op.
     */
    private static ImageFromDockerfile buildElectronImage() {
        Path repoRoot = Paths.get(System.getProperty('user.dir')).toAbsolutePath().parent
        Path appDir = repoRoot.resolve('app')
        assertTrue(Files.exists(appDir.resolve('main.js')),
                "Could not locate the app at ${appDir}".toString())

        return new ImageFromDockerfile('electron-gpu-test-functional:test', false)
                .withFileFromClasspath('Dockerfile', 'electron/Dockerfile')
                .withFileFromClasspath('electron-entrypoint.sh', 'electron/electron-entrypoint.sh')
                .withFileFromClasspath('electron-run.sh', 'electron/electron-run.sh')
                .withFileFromClasspath('render-check.html', 'electron/render-check.html')
                .withFileFromPath('app', appDir)
                .withFileFromPath('extra-cas', resolveExtraCaDir())
    }

    /**
     * Returns a directory to mount at the build context's {@code extra-cas/}. In a
     * proxied environment it contains the host CA bundle so npm trusts the proxy;
     * otherwise it contains only a placeholder so the Dockerfile COPY succeeds.
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
