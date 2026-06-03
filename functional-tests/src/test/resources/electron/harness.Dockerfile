# =============================================================================
# Test-only layer ON TOP of the production image (electron-gpu-test). It adds a
# version-matched ChromeDriver so Selenium can drive the REAL app that the
# production /app/launch.sh launches.
#
# The virtual display is intentionally NOT installed here: a separate Xvfb
# sidecar container provides it and shares its /tmp/.X11-unix socket with this
# container, mirroring how the production image attaches to an external X server.
#
# Nothing about the app is duplicated here: the app, its Electron binary, the
# NVIDIA stack and launch.sh all come from the production image below.
# =============================================================================
ARG BASE_IMAGE=electron-gpu-test:undertest
FROM ${BASE_IMAGE}

USER root

# ChromeDriver matched to Electron 41 / Chromium 146. node + npm come from the
# base image; the base trusts any proxy CA, so this download works behind a proxy.
RUN npm install -g --no-audit --no-fund electron-chromedriver@41

# A deterministic page for the app to load, plus the entrypoint that waits for
# the sidecar's X socket, starts the app via the production launcher, and exposes
# ChromeDriver.
COPY render-check.html /opt/render-check.html
COPY test-harness-entrypoint.sh /usr/local/bin/test-harness-entrypoint.sh
RUN chmod +x /usr/local/bin/test-harness-entrypoint.sh && chown app:app /opt/render-check.html

EXPOSE 4444
USER app
ENTRYPOINT ["/usr/local/bin/test-harness-entrypoint.sh"]
