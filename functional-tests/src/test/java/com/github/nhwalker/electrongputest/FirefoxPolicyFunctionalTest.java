package com.github.nhwalker.electrongputest;

import io.qameta.allure.Allure;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the Firefox image's enterprise-policy configuration is actually
 * APPLIED -- not merely written to disk. It mounts a custom bookmarks file at the
 * runtime override path {@code /config/bookmarks.json}, then drives the harness's
 * in-container geckodriver to open {@code about:policies#active} (Firefox's own
 * report of the policies it is currently enforcing) and asserts both a baked-in
 * lockdown ({@code DisablePrivateBrowsing}) and the mounted toolbar bookmark show
 * up there.
 *
 * <p>The WebDriver session is driven from INSIDE the container (via
 * {@code firefox-policy-check.sh} run with {@code execInContainer}): geckodriver
 * pins its Host-header allowlist to {@code host:port}, which would reject
 * Testcontainers' random mapped port, so we never expose the driver to the host.
 * This mirrors how {@link TlsMtlsFunctionalTest} asserts on in-container launch
 * logs rather than reaching back through a mapped port.
 *
 * <p>It also asserts the {@code firefox-config:} merge log to prove the runtime
 * bookmarks override was the thing that took effect (no image rebuild needed).
 *
 * {@code disabledWithoutDocker} so it skips (not fails) where Docker is absent.
 */
@Epic("electron-gpu-test")
@Feature("Firefox enterprise policies")
@Testcontainers(disabledWithoutDocker = true)
class FirefoxPolicyFunctionalTest {

    // The Title of the bookmark in src/test/resources/firefox/bookmarks.json,
    // mounted as the runtime toolbar override below.
    private static final String OVERRIDE_BOOKMARK = "PolicyToolbarMark";

    // The harness runs setup-config.sh (applying our mounted /config override),
    // then geckodriver in the foreground. Wait until geckodriver is listening.
    @Container
    static final GenericContainer<?> FIREFOX = new GenericContainer<>(TestImages.firefoxHarness())
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("firefox/bookmarks.json", 0644),
                    "/config/bookmarks.json")
            .waitingFor(Wait.forLogMessage(".*Listening on 0\\.0\\.0\\.0:4444.*", 1))
            .withStartupTimeout(Duration.ofSeconds(120));

    @Test
    @DisplayName("Firefox enforces the baked lockdown and the mounted toolbar bookmark (about:policies#active)")
    @Description("Mounts a custom /config/bookmarks.json, drives the in-container geckodriver to open "
            + "about:policies#active, and asserts DisablePrivateBrowsing and the mounted bookmark are active -- "
            + "proving both baked lockdowns and runtime bookmark customization reach Firefox.")
    void policiesAreApplied() throws Exception {
        // The merge log proves the runtime override (not just the baked default) was applied.
        String mergeLog = FIREFOX.getLogs();
        assertTrue(mergeLog.contains("firefox-config: using mounted bookmarks /config/bookmarks.json"),
                "Expected the mounted bookmarks override to be used. Logs were:\n" + mergeLog);

        // Ask Firefox itself what policies are active, via the baked check script.
        org.testcontainers.containers.Container.ExecResult check = Allure.step("Drive geckodriver -> about:policies#active",
                () -> FIREFOX.execInContainer(
                        "/usr/local/bin/firefox-policy-check.sh",
                        "DisablePrivateBrowsing",
                        OVERRIDE_BOOKMARK));
        String out = check.getStdout() + check.getStderr();

        assertTrue(out.contains("policy-check: FOUND DisablePrivateBrowsing"),
                "Expected DisablePrivateBrowsing active in about:policies. Output was:\n" + out);
        assertTrue(out.contains("policy-check: FOUND " + OVERRIDE_BOOKMARK),
                "Expected the mounted toolbar bookmark active in about:policies. Output was:\n" + out);
    }
}
