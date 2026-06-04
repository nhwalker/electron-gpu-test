package com.github.nhwalker.electrongputest;

import org.testcontainers.images.builder.ImageFromDockerfile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

/**
 * Resolves the production {@code electron-gpu-test} image that the functional
 * test harness layers build {@code FROM}.
 *
 * <p>By default it builds the image from the repo's {@code Containerfile} on the
 * local Docker daemon and returns its tag, so a bare {@code ./gradlew test}
 * "just works" with no prior setup. When {@code ELECTRON_BASE_IMAGE} is set --
 * CI builds the production image once, up front (buildx + layer cache), then
 * injects its tag -- that name is returned as-is and nothing is rebuilt. That
 * avoids a second, uncached build of the production image inside the test JVM
 * (Testcontainers' image build can't read CI's buildx cache).
 *
 * <p>Either way the harness Dockerfiles receive the result through their
 * {@code ARG BASE_IMAGE} (see each test's {@code buildHarnessImage}), so the
 * thin test-only layers always build on top of the production image.
 */
final class ProductionImage {

    /** Env var naming a pre-built production image to use as the harness base. */
    private static final String BASE_IMAGE_ENV = "ELECTRON_BASE_IMAGE";

    /** Tag used when this class builds the production image itself. */
    static final String DEFAULT_TAG = "electron-gpu-test:undertest";

    private ProductionImage() {
    }

    /**
     * Returns the production image name the harness should build {@code FROM},
     * building it from the {@code Containerfile} first unless a pre-built image
     * was injected via {@code ELECTRON_BASE_IMAGE}.
     */
    static String baseImage() {
        String prebuilt = System.getenv(BASE_IMAGE_ENV);
        if (prebuilt != null && !prebuilt.isBlank()) {
            return prebuilt;
        }

        Path repoRoot = Paths.get(System.getProperty("user.dir")).toAbsolutePath().getParent();
        if (!Files.exists(repoRoot.resolve("Containerfile"))) {
            throw new IllegalStateException("Could not locate the production Containerfile at " + repoRoot);
        }

        // Build the production image so the harness can build FROM it. Eager
        // (.get()) so the tag exists before any harness layer references it.
        new ImageFromDockerfile(DEFAULT_TAG, false)
                .withFileFromPath("Dockerfile", repoRoot.resolve("Containerfile"))
                .withFileFromPath("app", repoRoot.resolve("app"))
                .withFileFromPath("rocky9.repo", repoRoot.resolve("rocky9.repo"))
                .withFileFromPath("extra-cas", resolveExtraCaDir())
                .get();
        return DEFAULT_TAG;
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
