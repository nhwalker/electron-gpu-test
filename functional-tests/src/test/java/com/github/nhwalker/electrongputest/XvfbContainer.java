package com.github.nhwalker.electrongputest;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.Mount;
import com.github.dockerjava.api.model.MountType;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Reusable Testcontainers module: a sidecar <strong>Xvfb virtual X display</strong>
 * that other containers attach to over a shared {@code /tmp/.X11-unix} socket
 * volume -- mirroring the production model where the app connects to an X server
 * running <em>outside</em> its own container, so the app image carries no display
 * tooling.
 *
 * <p>The image also bundles ffmpeg, so the display this sidecar serves can be
 * <strong>screen-recorded on demand</strong> ({@code x11grab} raw capture,
 * transcoded to WebM) via {@link #startRecording()} / {@link #stopRecordingAsWebm()}
 * -- independent of the container's own lifecycle.
 *
 * <p>Typical use: build the sidecar, then wire the application container to its
 * display with {@link #prepareClient(GenericContainer)}:
 * <pre>{@code
 * static final XvfbContainer XVFB = new XvfbContainer();
 *
 * static final GenericContainer<?> APP = XVFB.prepareClient(new GenericContainer<>(image))
 *         .withEnv("TARGET_URL", url)
 *         .withExposedPorts(4444);
 * }</pre>
 * {@code prepareClient} mounts the shared socket volume on the client, points its
 * {@code DISPLAY} at this sidecar, and adds a startup dependency on it.
 */
public class XvfbContainer extends GenericContainer<XvfbContainer> {

    /** Tag for the (cached) sidecar image built from the test resources. */
    private static final String IMAGE_TAG = "electron-gpu-test:xvfb";

    /** Default X display number served by the sidecar. */
    private static final int DEFAULT_DISPLAY_NUMBER = 99;

    /** Shared X socket directory and the named volume published into it. */
    private static final String X11_SOCKET_DIR = "/tmp/.X11-unix";
    private static final String SOCKET_VOLUME = "electron-gpu-test-xsock";

    /** Where {@code record-stop.sh} writes the transcoded WebM inside the container. */
    private static final String RECORDING_PATH = "/tmp/recording.webm";

    private final int displayNumber;

    public XvfbContainer() {
        this(DEFAULT_DISPLAY_NUMBER);
    }

    public XvfbContainer(int displayNumber) {
        super(buildImage());
        this.displayNumber = displayNumber;
        // The entrypoint and recording scripts read DISPLAY_NUM for the display.
        withEnv("DISPLAY_NUM", Integer.toString(displayNumber));
        // Publish this sidecar's X socket into the shared volume...
        withCreateContainerCmdModifier(sharedSocketVolume());
        // ...and announce readiness once that socket exists (xvfb-entrypoint.sh).
        waitingFor(Wait.forLogMessage(".*X-READY.*", 1));
    }

    /** The X display this sidecar serves, e.g. {@code :99}. */
    public String getDisplay() {
        return ":" + displayNumber;
    }

    /**
     * Wires {@code client} to this sidecar: mounts the same shared X socket
     * volume, points its {@code DISPLAY} at this display, and adds a startup
     * dependency on the sidecar. Returns {@code client} for further chaining.
     */
    public GenericContainer<?> prepareClient(GenericContainer<?> client) {
        client.dependsOn(this);
        client.withCreateContainerCmdModifier(sharedSocketVolume());
        client.withEnv("DISPLAY", getDisplay());
        return client;
    }

    /** Starts ffmpeg recording the display (raw capture; see {@code record-start.sh}). */
    public void startRecording() throws IOException, InterruptedException {
        ExecResult result = execInContainer("/usr/local/bin/record-start.sh");
        assertEquals(0, result.getExitCode(),
                "record-start.sh failed: " + result.getStdout() + result.getStderr());
    }

    /**
     * Stops the recording -- ffmpeg flushes the raw capture and transcodes it to
     * WebM inside the container -- and returns the clip's bytes.
     */
    public byte[] stopRecordingAsWebm() throws IOException, InterruptedException {
        ExecResult result = execInContainer("/usr/local/bin/record-stop.sh", RECORDING_PATH);
        assertEquals(0, result.getExitCode(),
                "record-stop.sh failed: " + result.getStdout() + result.getStderr());
        return copyFileFromContainer(RECORDING_PATH, InputStream::readAllBytes);
    }

    /**
     * Mounts the shared X socket volume at {@code /tmp/.X11-unix}. Docker creates
     * the named volume on first use; the sidecar's entrypoint clears any stale
     * socket before recreating it, so reusing a fixed name across runs is safe.
     * A shared-volume mount is order-independent, unlike {@code withVolumesFrom}
     * which needs the source container already running.
     */
    private static Consumer<CreateContainerCmd> sharedSocketVolume() {
        return cmd -> {
            List<Mount> existing = cmd.getHostConfig().getMounts();
            List<Mount> mounts = new ArrayList<>(existing != null ? existing : Collections.<Mount>emptyList());
            mounts.add(new Mount()
                    .withType(MountType.VOLUME)
                    .withSource(SOCKET_VOLUME)
                    .withTarget(X11_SOCKET_DIR));
            cmd.getHostConfig().withMounts(mounts);
        };
    }

    /** Builds the sidecar image (Xvfb + ffmpeg + recording scripts) from test resources. */
    private static ImageFromDockerfile buildImage() {
        return new ImageFromDockerfile(IMAGE_TAG, false)
                .withFileFromClasspath("Dockerfile", "electron/xvfb.Dockerfile")
                .withFileFromClasspath("xvfb-entrypoint.sh", "electron/xvfb-entrypoint.sh")
                .withFileFromClasspath("record-start.sh", "electron/record-start.sh")
                .withFileFromClasspath("record-stop.sh", "electron/record-stop.sh");
    }
}
