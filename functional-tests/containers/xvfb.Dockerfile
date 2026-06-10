# =============================================================================
# Sidecar that provides a virtual X display (Xvfb). It shares its
# /tmp/.X11-unix socket directory with the app container through a Docker volume,
# so the app connects to an X server running *outside* its own container -- the
# same model the production container uses against the host's X server.
#
# It also carries ffmpeg + start/stop scripts so a test can RECORD that display
# on demand: ffmpeg grabs :99 right where it lives. Recording is driven via
# `docker exec` (record-start.sh / record-stop.sh), so it starts and stops
# independent of this container's own lifecycle.
#
# Pulled from the AWS ECR public mirror of Docker Hub to avoid Hub pull limits.
# =============================================================================
FROM public.ecr.aws/docker/library/debian:bookworm-slim

# xvfb serves the display; ffmpeg records it (x11grab capture + WebM transcode).
RUN apt-get update && apt-get install -y --no-install-recommends xvfb ffmpeg \
    && rm -rf /var/lib/apt/lists/*

# Declare the socket dir as a volume so the app container can share it.
VOLUME /tmp/.X11-unix

COPY xvfb-entrypoint.sh /usr/local/bin/xvfb-entrypoint.sh
COPY record-start.sh /usr/local/bin/record-start.sh
COPY record-stop.sh /usr/local/bin/record-stop.sh
RUN chmod +x /usr/local/bin/xvfb-entrypoint.sh \
        /usr/local/bin/record-start.sh \
        /usr/local/bin/record-stop.sh

ENTRYPOINT ["/usr/local/bin/xvfb-entrypoint.sh"]
