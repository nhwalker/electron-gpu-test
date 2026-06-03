# =============================================================================
# Test-only layer ON TOP of the production image (electron-gpu-test). It adds a
# virtual display (Xvfb) and a version-matched ChromeDriver so Selenium can drive
# the REAL app that the production /app/launch.sh launches.
#
# Nothing about the app is duplicated here: the app, its Electron binary, the
# NVIDIA stack and launch.sh all come from the production image below. This layer
# only adds test instrumentation, so the functional test exercises the genuine
# production image (with launch.sh falling back to software rendering on a
# GPU-less CI host).
# =============================================================================
ARG BASE_IMAGE=electron-gpu-test:undertest
FROM ${BASE_IMAGE}

USER root

# Xvfb gives the GUI app a screen to render into; the base already enabled the
# CRB / Rocky fallback repos that carry it.
RUN dnf -y install --setopt=install_weak_deps=False xorg-x11-server-Xvfb && dnf clean all

# ChromeDriver matched to Electron 41 / Chromium 146. node + npm come from the
# base image; the base trusts any proxy CA, so this download works behind a proxy.
RUN npm install -g --no-audit --no-fund electron-chromedriver@41

# A deterministic page for the app to load, plus the entrypoint that boots Xvfb,
# starts the app via the production launcher, and exposes ChromeDriver.
COPY render-check.html /opt/render-check.html
COPY test-harness-entrypoint.sh /usr/local/bin/test-harness-entrypoint.sh
RUN chmod +x /usr/local/bin/test-harness-entrypoint.sh && chown app:app /opt/render-check.html

EXPOSE 4444
USER app
ENTRYPOINT ["/usr/local/bin/test-harness-entrypoint.sh"]
