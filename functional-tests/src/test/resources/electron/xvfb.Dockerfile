# =============================================================================
# Sidecar that provides ONLY a virtual X display (Xvfb). It shares its
# /tmp/.X11-unix socket directory with the app container through a Docker volume,
# so the app connects to an X server running *outside* its own container -- the
# same model the production container uses against the host's X server.
#
# Pulled from the AWS ECR public mirror of Docker Hub to avoid Hub pull limits.
# =============================================================================
FROM public.ecr.aws/docker/library/debian:bookworm-slim

RUN apt-get update && apt-get install -y --no-install-recommends xvfb \
    && rm -rf /var/lib/apt/lists/*

# Declare the socket dir as a volume so the app container can share it.
VOLUME /tmp/.X11-unix

COPY xvfb-entrypoint.sh /usr/local/bin/xvfb-entrypoint.sh
RUN chmod +x /usr/local/bin/xvfb-entrypoint.sh

ENTRYPOINT ["/usr/local/bin/xvfb-entrypoint.sh"]
