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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The desktop-audio check: proves a {@code vnc://…?audio=1} URL brings the
 * remote machine's sound into the viewer, all the way to something audible.
 *
 * The server sidecar streams a fixed 440Hz tone as Opus -- 20ms frames, each in
 * an RTP packet, length-prefixed per RFC 4571 -- on a second port, which is the
 * wire format {@code app/vnc-viewer/audio.js} decodes. That makes the whole path
 * assertable end to end:
 * <ul>
 *   <li>the main process opens a <em>second</em> bridge, to the audio port,
 *       behind the same session token as the RFB one;</li>
 *   <li>the viewer deframes the stream, parses RTP and decodes the Opus frames
 *       with WebCodecs -- with no sequence gaps;</li>
 *   <li>the samples reach the speakers: the page's own FFT finds the tone at
 *       440Hz, which can only be true if capture, transport, decode, the ring
 *       buffer and playback all worked.</li>
 * </ul>
 *
 * That last assertion is the audio equivalent of the WebGL tests' "the frame is
 * not blank": a silent or stalled path reports no peak, or the wrong one.
 *
 * {@code disabledWithoutDocker} so it skips (not fails) where Docker is absent.
 */
@Epic("electron-gpu-test")
@Feature("VNC desktop audio")
@Testcontainers(disabledWithoutDocker = true)
class VncAudioFunctionalTest {

    // ChromeDriver listens on 4444 inside the harness (see the entrypoint).
    private static final int CHROMEDRIVER_PORT = 4444;

    // The app's DevTools endpoint inside the container that ChromeDriver attaches
    // to (launch.sh is started with --remote-debugging-port=9222).
    private static final String DEBUGGER_ADDRESS = "127.0.0.1:9222";

    private static final String VNC_ALIAS = "vncserver";
    private static final int VNC_PORT = 5900;
    // The conventional audio port, which `?audio=1` selects without naming it.
    private static final int AUDIO_PORT = 5901;
    private static final String VNC_PASSWORD = "s3cret";

    // The frequency the sidecar plays; the viewer must find it again after
    // decoding. The FFT's resolution is 48000/4096 ~= 11.7Hz, so allow a bin.
    private static final int TONE_HZ = 440;
    private static final int TONE_TOLERANCE_HZ = 15;

    private static final String TARGET_URL = "vnc://" + VNC_ALIAS + ":" + VNC_PORT + "?audio=1";

    static final Network NETWORK = Network.newNetwork();

    @Container
    static final GenericContainer<?> VNC_SERVER = new GenericContainer<>(TestImages.vncServer())
            .withNetwork(NETWORK)
            .withNetworkAliases(VNC_ALIAS)
            .withEnv("VNC_PASSWORD", VNC_PASSWORD)
            .withEnv("TONE_HZ", Integer.toString(TONE_HZ))
            .withExposedPorts(VNC_PORT, AUDIO_PORT)
            .waitingFor(Wait.forListeningPorts(VNC_PORT, AUDIO_PORT));

    @Container
    static final XvfbContainer XVFB = new XvfbContainer();

    @Container
    static final GenericContainer<?> ELECTRON = XVFB.prepareClient(new GenericContainer<>(TestImages.harness()))
            .dependsOn(VNC_SERVER)
            .withNetwork(NETWORK)
            .withEnv("TARGET_URL", TARGET_URL)
            .withEnv("VNC_PASSWORD", VNC_PASSWORD)
            .withExposedPorts(CHROMEDRIVER_PORT)
            .withStartupTimeout(Duration.ofSeconds(240))
            .waitingFor(
                    Wait.forHttp("/status")
                            .forPort(CHROMEDRIVER_PORT)
                            .forStatusCode(200)
                            .forResponsePredicate(body -> body.contains("\"ready\":true")));

    @Test
    @DisplayName("A vnc://…?audio=1 URL plays the remote desktop's sound")
    @Description("Streams a known 440Hz tone as Opus from the VNC sidecar's second port, and asserts the app bridged it, decoded it with WebCodecs and played it -- proven by the viewer's own FFT finding the tone again.")
    void desktopAudioPlaysInTheViewer() throws Exception {
        // The main process must have opened a second bridge, to the audio port.
        String launchLog = launchLog();
        assertTrue(launchLog.contains("vnc: bridging a viewer to " + VNC_ALIAS + ":" + AUDIO_PORT),
                "Expected a bridge to the audio port. Log was:\n" + launchLog);

        RemoteWebDriver driver = attachToApp();
        try {
            waitForDecodedAudio(driver);

            // The transport is healthy: the stream is playing and no packet was
            // skipped (a gap means the server dropped us forward for reading
            // too slowly).
            assertEquals("playing", jsString(driver, "return window.VNC_TEST.audio.state"));
            assertEquals(0L, jsLong(driver, "return window.VNC_TEST.audio.gaps"),
                    "The audio stream lost packets");

            // ...and it keeps arriving, rather than one burst having landed.
            long decoded = jsLong(driver, "return window.VNC_TEST.audio.decoded");
            Thread.sleep(2000);
            assertTrue(jsLong(driver, "return window.VNC_TEST.audio.decoded") > decoded,
                    "Audio decoding stopped after " + decoded + " packets");

            // Playback is actually consuming: 'filling' clears only once the ring
            // buffer reached its target depth and the audio thread started
            // pulling, and a buffer nothing drains would overflow instead.
            assertFalse(jsBool(driver, "return window.VNC_TEST.audio.filling"),
                    "The audio ring buffer never started playing out");
            assertEquals(0L, jsLong(driver, "return window.VNC_TEST.audio.overflows"),
                    "The audio buffer overflowed: nothing was draining it");

            // The tone survived capture, encode, bridge, decode and playback.
            assertTone(driver);
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

    private static void waitForDecodedAudio(RemoteWebDriver driver) throws InterruptedException {
        Allure.step("Wait for the viewer to decode audio from the second stream", () -> {
            long deadline = System.currentTimeMillis() + 60_000L;
            while (System.currentTimeMillis() < deadline) {
                if (jsLong(driver, "return (window.VNC_TEST.audio && window.VNC_TEST.audio.decoded) || 0") > 0) {
                    return;
                }
                Thread.sleep(250);
            }
            throw new AssertionError("No audio was decoded within 60s (state: "
                    + jsString(driver, "return window.VNC_TEST.audio && window.VNC_TEST.audio.state") + ")");
        });
    }

    /**
     * Asserts the played audio peaks at the tone the server is sending. Polls,
     * because the first frames land before the ring buffer has filled and the
     * analyser has anything to look at.
     */
    private static void assertTone(RemoteWebDriver driver) throws InterruptedException {
        Allure.step("Assert the played audio peaks at " + TONE_HZ + "Hz", () -> {
            long deadline = System.currentTimeMillis() + 20_000L;
            long frequency = 0;
            while (System.currentTimeMillis() < deadline) {
                frequency = jsLong(driver, "return Math.round(window.VNC_TEST.audioFrequency())");
                if (Math.abs(frequency - TONE_HZ) <= TONE_TOLERANCE_HZ) {
                    return;
                }
                Thread.sleep(250);
            }
            throw new AssertionError("The played audio peaked at " + frequency + "Hz, not "
                    + TONE_HZ + "Hz (+/-" + TONE_TOLERANCE_HZ + "): the tone did not survive the trip");
        });
    }

    private static String launchLog() throws IOException, InterruptedException {
        return Allure.step("Read launch.sh output from the container",
                () -> ELECTRON.execInContainer("cat", "/tmp/electron.log").getStdout());
    }

    private static String jsString(RemoteWebDriver driver, String script) {
        Object value = driver.executeScript(script);
        return value == null ? null : String.valueOf(value);
    }

    private static long jsLong(RemoteWebDriver driver, String script) {
        Object value = driver.executeScript(script);
        return value == null ? 0L : ((Number) value).longValue();
    }

    private static boolean jsBool(RemoteWebDriver driver, String script) {
        return Boolean.TRUE.equals(driver.executeScript(script));
    }
}
