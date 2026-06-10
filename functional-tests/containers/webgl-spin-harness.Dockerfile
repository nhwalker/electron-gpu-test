# =============================================================================
# Test-only layer for the NASA WorldWind *spin* functional test. It is a thin
# layer ON TOP of the WebGL harness image (webgl-harness.Dockerfile), which
# already carries the production app, a version-matched ChromeDriver, the shared
# test entrypoint, and vendored offline NASA WebWorldWind under /opt/worldwind.
#
# All this layer adds is the spinning-globe page. Which page the app opens is
# chosen at run time via the TARGET_URL env var (see test-harness-entrypoint.sh),
# so this image reuses the inherited ENTRYPOINT and ChromeDriver unchanged -- the
# spin test just points TARGET_URL at file:///opt/worldwind/spin.html.
# =============================================================================
ARG BASE_IMAGE=electron-gpu-test:webgl-harness
FROM ${BASE_IMAGE}

USER root

# The spinning-globe page, alongside the static one the base image vendored. It
# reuses the same offline worldwind.min.js + images/ already in /opt/worldwind.
COPY webgl-worldwind-spin.html /opt/worldwind/spin.html
RUN chown app:app /opt/worldwind/spin.html

USER app
