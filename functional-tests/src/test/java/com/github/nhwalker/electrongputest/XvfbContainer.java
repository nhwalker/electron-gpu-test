package com.github.nhwalker.electrongputest;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.Mount;
import com.github.dockerjava.api.model.MountType;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Reusable Testcontainers module: a sidecar that provides the <strong>X display</strong>
 * other containers attach to over a shared {@code /tmp/.X11-unix} socket volume,
 * and screen-records it on demand.
 *
 * <p>By default the sidecar starts its own <strong>Xvfb virtual X display</strong>
 * -- mirroring the production model where the app connects to an X server running
 * <em>outside</em> its own container, so the app image carries no display tooling.
 *
 * <p><strong>Local-display mode.</strong> Set the {@value #LOCAL_DISPLAY_ENV}
 * environment variable (e.g. {@code LOCAL_DISPLAY=:0}) to run the tests against an
 * X display already running on the host instead. In that mode the sidecar
 * bind-mounts the host's real {@code /tmp/.X11-unix} directory and uses the
 * passed-in display as-is: it starts <em>no</em> Xvfb, and the record methods
 * capture that display rather than a throwaway virtual one. (The host display
 * must of course be reachable from containers -- e.g. {@code xhost +local:}.)
 *
 * <p>The image bundles ffmpeg, so whichever display this sidecar serves can be
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
 * {@code prepareClient} mounts the shared socket source on the client, points its
 * {@code DISPLAY} at this sidecar's display, and adds a startup dependency on it.
 *
 * <p>The sidecar image is built BEFORE the test run (see
 * {@code functional-tests/containers/build-images.sh} and {@link TestImages});
 * this module only runs the pre-built tag, never builds it.
 */
public class XvfbContainer extends GenericContainer<XvfbContainer> {

    /** Default X display number served by the sidecar's own Xvfb. */
    private static final int DEFAULT_DISPLAY_NUMBER = 99;

    /**
     * Host environment variable that opts into recording an existing local X
     * display instead of an Xvfb. Holds the display to use, e.g. {@code :0}.
     */
    static final String LOCAL_DISPLAY_ENV = "LOCAL_DISPLAY";

    /** Shared X socket directory and the named volume published into it. */
    private static final String X11_SOCKET_DIR = "/tmp/.X11-unix";
    private static final String SOCKET_VOLUME = "electron-gpu-test-xsock";

    /** Where {@code record-stop.sh} writes the transcoded WebM inside the container. */
    private static final String RECORDING_PATH = "/tmp/recording.webm";

    private final int displayNumber;
    /** True when serving the host's existing local display rather than an Xvfb. */
    private final boolean localDisplay;

    public XvfbContainer() {
        this(Display.fromEnv());
    }

    public XvfbContainer(int displayNumber) {
        this(new Display(displayNumber, false));
    }

    private XvfbContainer(Display display) {
        super(TestImages.xvfb());
        this.displayNumber = display.number();
        this.localDisplay = display.local();
        // The entrypoint and recording scripts read DISPLAY_NUM for the display.
        withEnv("DISPLAY_NUM", Integer.toString(displayNumber));
        // In local-display mode, also export DISPLAY: it's the signal the
        // entrypoint uses to record the passed-in display instead of starting Xvfb.
        if (localDisplay) {
            withEnv("DISPLAY", getDisplay());
        }
        // Mount the shared socket source (named volume, or the host's bind-mounted
        // /tmp/.X11-unix in local mode)...
        withCreateContainerCmdModifier(socketMount());
        // ...and announce readiness once a usable display socket exists (xvfb-entrypoint.sh).
        waitingFor(Wait.forLogMessage(".*X-READY.*", 1));
    }

    /** The X display this sidecar serves, e.g. {@code :99} (or the local display). */
    public String getDisplay() {
        return ":" + displayNumber;
    }

    /**
     * Wires {@code client} to this sidecar: mounts the same shared X socket
     * source, points its {@code DISPLAY} at this display, and adds a startup
     * dependency on the sidecar. Returns {@code client} for further chaining.
     */
    public GenericContainer<?> prepareClient(GenericContainer<?> client) {
        client.dependsOn(this);
        client.withCreateContainerCmdModifier(socketMount());
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
     * Mounts the source backing {@code /tmp/.X11-unix}. By default that's a named
     * Docker volume shared between sidecar and client (Docker creates it on first
     * use; the sidecar's entrypoint clears any stale socket before recreating it,
     * so reusing a fixed name across runs is safe). In local-display mode it's a
     * bind mount of the host's real {@code /tmp/.X11-unix}, so both containers see
     * the host's running display socket. Either way the mount is order-independent,
     * unlike {@code withVolumesFrom} which needs the source container running.
     */
    private Consumer<CreateContainerCmd> socketMount() {
        boolean bindHostSocketDir = localDisplay;
        return cmd -> {
            List<Mount> existing = cmd.getHostConfig().getMounts();
            List<Mount> mounts = new ArrayList<>(existing != null ? existing : Collections.<Mount>emptyList());
            Mount mount = new Mount().withTarget(X11_SOCKET_DIR);
            if (bindHostSocketDir) {
                mount.withType(MountType.BIND).withSource(X11_SOCKET_DIR);
            } else {
                mount.withType(MountType.VOLUME).withSource(SOCKET_VOLUME);
            }
            mounts.add(mount);
            cmd.getHostConfig().withMounts(mounts);
        };
    }

    /**
     * The display this sidecar should serve: an Xvfb on {@link #DEFAULT_DISPLAY_NUMBER}
     * by default, or the host's existing display when {@value #LOCAL_DISPLAY_ENV}
     * names one (e.g. {@code :0}, {@code 0}, or {@code :0.0}).
     */
    private record Display(int number, boolean local) {
        static Display fromEnv() {
            String value = System.getenv(LOCAL_DISPLAY_ENV);
            if (value == null || value.isBlank()) {
                return new Display(DEFAULT_DISPLAY_NUMBER, false);
            }
            return new Display(parseDisplayNumber(value), true);
        }

        private static int parseDisplayNumber(String display) {
            String s = display.trim();
            int colon = s.lastIndexOf(':');
            if (colon >= 0) {
                s = s.substring(colon + 1);
            }
            int dot = s.indexOf('.'); // strip the screen suffix, e.g. ":0.0"
            if (dot >= 0) {
                s = s.substring(0, dot);
            }
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Could not parse an X display number from "
                        + LOCAL_DISPLAY_ENV + "=\"" + display + "\" (expected e.g. \":0\")", e);
            }
        }
    }

}
