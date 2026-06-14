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
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the Firefox image is configured for GPU acceleration and hardware
 * video decode, by reading what Firefox itself reports on {@code about:support}.
 *
 * <p>It drives the harness's in-container geckodriver (via
 * {@code firefox-support-check.sh}) with the same acceleration prefs
 * {@code firefox-launch.sh} applies, and asserts on the emitted
 * {@code support-check:} lines -- the same in-container, log-asserting pattern as
 * {@link FirefoxPolicyFunctionalTest} and {@link TlsMtlsFunctionalTest}.
 *
 * <p>The kiosk-default policy blocks {@code about:support}
 * ({@code BlockAboutSupport}), so the container is started with a minimal
 * {@code /config/policies.json} override that lifts the block -- which also
 * exercises the runtime policy-override path.
 *
 * <p>Two layers of assertion:
 * <ul>
 *   <li><b>Always</b> (incl. CI, no GPU): compositing is WebRender, and Firefox
 *       accepted the VAAPI hardware-decode pref -- i.e. the acceleration config
 *       reaches the browser.</li>
 *   <li><b>GPU host only</b>: set {@code FIREFOX_GPU_TEST=1} (and, for NVIDIA,
 *       arrange the daemon/CDI so the harness container sees the GPU) to also
 *       assert a codec actually decodes in hardware. Skipped otherwise so CI
 *       stays green.</li>
 * </ul>
 *
 * {@code disabledWithoutDocker} so it skips (not fails) where Docker is absent.
 */
@Epic("electron-gpu-test")
@Feature("Firefox GPU / WebRTC")
@Testcontainers(disabledWithoutDocker = true)
class FirefoxGpuFunctionalTest {

    // Opt-in flag for the hardware-decode assertion on a real GPU host.
    private static final boolean GPU_TEST = "1".equals(System.getenv("FIREFOX_GPU_TEST"));

    @Container
    static final GenericContainer<?> FIREFOX = new GenericContainer<>(TestImages.firefoxHarness())
            // Lift the kiosk BlockAboutSupport so the troubleshooting page is readable.
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("firefox/gpu-policies.json", 0644),
                    "/config/policies.json")
            .waitingFor(Wait.forLogMessage(".*Listening on 0\\.0\\.0\\.0:4444.*", 1))
            .withStartupTimeout(Duration.ofSeconds(120));

    @Test
    @DisplayName("Firefox reports WebRender compositing and accepts the VAAPI hardware-decode config")
    @Description("Reads about:support via the in-container geckodriver and asserts compositing is "
            + "WebRender and media.ffmpeg.vaapi.enabled is true; with FIREFOX_GPU_TEST=1 also asserts a "
            + "codec decodes in hardware.")
    void reportsAccelerationConfig() throws Exception {
        Map<String, String> support = readSupport();

        assertTrue(support.getOrDefault("compositing", "").contains("WebRender"),
                "Expected WebRender compositing. about:support said:\n" + support);
        assertTrue("true".equals(support.get("vaapi-pref")),
                "Expected Firefox to accept media.ffmpeg.vaapi.enabled=true. about:support said:\n" + support);

        if (GPU_TEST) {
            assertTrue("yes".equals(support.get("hwdecode")),
                    "FIREFOX_GPU_TEST=1 but no codec reported hardware decoding. about:support said:\n" + support);
        }
    }

    /** Runs the check script in the container and parses its {@code support-check: k=v} lines. */
    private static Map<String, String> readSupport() throws Exception {
        return Allure.step("Read about:support via in-container geckodriver", () -> {
            var result = FIREFOX.execInContainer("/usr/local/bin/firefox-support-check.sh");
            String out = result.getStdout() + result.getStderr();
            assertTrue(out.contains("support-check: done"),
                    "support-check did not finish cleanly. Output was:\n" + out);
            return Arrays.stream(out.split("\\R"))
                    .map(String::trim)
                    .filter(l -> l.startsWith("support-check: ") && l.contains("="))
                    .map(l -> l.substring("support-check: ".length()).split("=", 2))
                    .collect(Collectors.toMap(kv -> kv[0], kv -> kv.length > 1 ? kv[1] : "", (a, b) -> b));
        });
    }
}
