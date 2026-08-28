# =============================================================================
# A real VNC server for the vnc:// functional test: Xvfb holding a deterministic
# picture, published over RFB by x11vnc with password authentication.
#
# Nothing here is part of the product -- it stands in for whatever remote desktop
# an operator points the app at, so the test can prove the app's noVNC path end
# to end (RFB handshake, VNC auth, framebuffer decode, live updates) against a
# genuine VNC server rather than a stub.
#
# Pulled from the AWS ECR public mirror of Docker Hub to avoid Hub pull limits.
# =============================================================================
FROM public.ecr.aws/docker/library/debian:bookworm-slim

# xvfb serves the display, x11vnc exports it over RFB, and the x11-apps /
# x11-xserver-utils clients draw the picture the test asserts on.
RUN apt-get update && apt-get install -y --no-install-recommends \
        xvfb x11vnc x11-apps x11-xserver-utils \
    && rm -rf /var/lib/apt/lists/*

COPY vnc-server-entrypoint.sh /usr/local/bin/vnc-server-entrypoint.sh
RUN chmod +x /usr/local/bin/vnc-server-entrypoint.sh

EXPOSE 5900
ENTRYPOINT ["/usr/local/bin/vnc-server-entrypoint.sh"]
