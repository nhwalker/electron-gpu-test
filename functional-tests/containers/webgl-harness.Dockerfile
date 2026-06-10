# =============================================================================
# Test-only layer ON TOP of the production image (electron-gpu-test) for the
# WebGL / NASA WorldWind functional test. Like harness.Dockerfile it adds only a
# version-matched ChromeDriver so Selenium can drive the REAL app launched by the
# production /app/launch.sh -- the app, Electron binary, NVIDIA stack and launcher
# all come from the production image below.
#
# It additionally vendors NASA WebWorldWind (the JS bundle + its bundled base
# imagery) into /opt/worldwind so the globe renders entirely OFFLINE: no tile
# servers or CDN are contacted at run time, keeping the test deterministic.
#
# The virtual display is NOT installed here: a separate Xvfb sidecar provides it
# over a shared /tmp/.X11-unix socket, mirroring the production external-X model.
# =============================================================================
ARG BASE_IMAGE=electron-gpu-test:undertest
FROM ${BASE_IMAGE}

USER root

# ChromeDriver matched to Electron 41 / Chromium 146. node + npm come from the
# base image; the base trusts any proxy CA, so these downloads work behind a proxy.
RUN npm install -g --no-audit --no-fund electron-chromedriver@41

# Vendor NASA WebWorldWind. The package ships a prebuilt bundle plus its base
# imagery (incl. the Blue Marble image BMNGOneImageLayer uses), so copying just
# the bundle + images/ next to the test page lets the globe render with no network.
RUN npm install -g --no-audit --no-fund @nasaworldwind/worldwind@0.11.1 && \
    WW="$(npm root -g)/@nasaworldwind/worldwind/build/dist" && \
    mkdir -p /opt/worldwind && \
    cp "${WW}/worldwind.min.js" /opt/worldwind/worldwind.min.js && \
    cp -r "${WW}/images" /opt/worldwind/images

# The WebGL test page, plus the shared harness entrypoint that waits for the
# sidecar's X socket, starts the app via the production launcher, and exposes
# ChromeDriver once the app's DevTools endpoint is live.
COPY webgl-worldwind.html /opt/worldwind/index.html
COPY test-harness-entrypoint.sh /usr/local/bin/test-harness-entrypoint.sh
RUN chmod +x /usr/local/bin/test-harness-entrypoint.sh && chown -R app:app /opt/worldwind

EXPOSE 4444
USER app
ENTRYPOINT ["/usr/local/bin/test-harness-entrypoint.sh"]
