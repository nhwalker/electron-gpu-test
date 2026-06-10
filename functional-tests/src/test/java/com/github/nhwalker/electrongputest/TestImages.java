package com.github.nhwalker.electrongputest;

import com.github.dockerjava.api.exception.NotFoundException;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.utility.DockerImageName;

/**
 * Resolves the pre-built container images the functional tests run.
 *
 * <p>The suite never builds images itself: every image is built BEFORE the test
 * run with podman/docker by {@code functional-tests/containers/build-images.sh}
 * (locally) or by the CI workflow (buildx + layer cache). Each accessor returns
 * the script's default tag unless the matching env var injects another one.
 *
 * <p>If a required image is missing from the daemon, the accessor fails fast
 * with a pointer at the build script -- rather than letting Testcontainers try
 * (and confusingly fail) to pull a local-only tag from a remote registry. The
 * presence check is skipped when Docker itself is unavailable, so
 * {@code @Testcontainers(disabledWithoutDocker = true)} still skips cleanly.
 */
final class TestImages {

    private TestImages() {
    }

    /** The Xvfb + ffmpeg display/recording sidecar (see {@link XvfbContainer}). */
    static DockerImageName xvfb() {
        return resolve("XVFB_IMAGE", "electron-gpu-test:xvfb", "xvfb");
    }

    /** The production image + a version-matched ChromeDriver. */
    static DockerImageName harness() {
        return resolve("ELECTRON_HARNESS_IMAGE", "electron-gpu-test:harness", "harness");
    }

    /** The harness + vendored offline NASA WorldWind. */
    static DockerImageName webglHarness() {
        return resolve("WEBGL_HARNESS_IMAGE", "electron-gpu-test:webgl-harness", "webgl-harness");
    }

    /** The WebGL harness + the spinning-globe page. */
    static DockerImageName webglSpinHarness() {
        return resolve("WEBGL_SPIN_HARNESS_IMAGE", "electron-gpu-test:webgl-spin-harness", "spin-harness");
    }

    private static DockerImageName resolve(String envVar, String defaultTag, String buildTarget) {
        String tag = System.getenv(envVar);
        if (tag == null || tag.isBlank()) {
            tag = defaultTag;
        }
        requirePresent(tag, envVar, buildTarget);
        return DockerImageName.parse(tag);
    }

    private static void requirePresent(String tag, String envVar, String buildTarget) {
        if (!DockerClientFactory.instance().isDockerAvailable()) {
            return; // The container tests skip without Docker; don't fail here.
        }
        try {
            DockerClientFactory.instance().client().inspectImageCmd(tag).exec();
        } catch (NotFoundException e) {
            throw new IllegalStateException("Image '" + tag + "' was not found on the container daemon. "
                    + "The functional tests do not build images; build them up front with "
                    + "`functional-tests/containers/build-images.sh` (target: " + buildTarget + "), "
                    + "or point " + envVar + " at a pre-built tag.", e);
        }
    }
}
