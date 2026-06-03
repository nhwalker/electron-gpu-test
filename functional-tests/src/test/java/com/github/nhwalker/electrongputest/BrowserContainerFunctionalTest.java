package com.github.nhwalker.electrongputest;

import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Step;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.nginx.NginxContainer;
import org.testcontainers.selenium.BrowserWebDriverContainer;
import org.testcontainers.utility.MountableFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end smoke test of the Testcontainers + Selenium stack: an nginx
 * container serves a page, a Selenium Chrome container loads it over a shared
 * Docker network, and we assert on what the browser actually rendered.
 *
 * Annotated {@code disabledWithoutDocker} so the suite is skipped (not failed)
 * on machines without a Docker daemon.
 */
@Epic("electron-gpu-test")
@Feature("Browser rendering")
@Testcontainers(disabledWithoutDocker = true)
class BrowserContainerFunctionalTest {

    // Shared network so the browser container can reach nginx by alias.
    static final Network NETWORK = Network.newNetwork();

    @Container
    static final NginxContainer NGINX = new NginxContainer("nginx:alpine")
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("web/index.html"),
                    "/usr/share/nginx/html/index.html")
            .withNetwork(NETWORK)
            .withNetworkAliases("web");

    @Container
    static final BrowserWebDriverContainer BROWSER =
            new BrowserWebDriverContainer("selenium/standalone-chrome:4.44.0")
                    .withNetwork(NETWORK);

    @Test
    @DisplayName("Chromium renders a page served from a sibling container")
    @Description("Drives the Selenium Chrome container to load the nginx-served page and asserts its title and body.")
    void rendersServedPage() {
        RemoteWebDriver driver = newSession();
        try {
            driver.get("http://web/");
            assertEquals("electron-gpu-test functional check", driver.getTitle());
            assertTrue(bodyContains(driver, "Testcontainers + Selenium"),
                    "Served page body did not render as expected");
        } finally {
            driver.quit();
        }
    }

    @Step("Open a Chrome session against the browser container")
    private static RemoteWebDriver newSession() {
        ChromeOptions options = new ChromeOptions();
        // Standard hardening for Chrome inside a container.
        options.addArguments("--no-sandbox", "--disable-dev-shm-usage");
        return new RemoteWebDriver(BROWSER.getSeleniumAddress(), options);
    }

    @Step("Assert rendered body contains \"{text}\"")
    private static boolean bodyContains(RemoteWebDriver driver, String text) {
        return driver.getPageSource().contains(text);
    }
}
