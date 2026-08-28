# =============================================================================
# A real VNC server for the vnc:// functional test: Xvfb holding a deterministic
# picture, published over RFB by x11vnc with password authentication, plus a
# deterministic Opus audio stream on a second port for the desktop-audio test.
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
#
# GStreamer produces the audio stream. There is deliberately no sound server
# here: the test needs a known signal in the app's own audio format, so the tone
# goes straight into the encoder. (A real desktop captures a PulseAudio/PipeWire
# monitor instead -- see examples/vnc-audio -- but everything downstream of the
# encoder, which is what the app implements, is identical.)
RUN apt-get update && apt-get install -y --no-install-recommends \
        xvfb x11vnc x11-apps x11-xserver-utils \
        gstreamer1.0-tools gstreamer1.0-plugins-base gstreamer1.0-plugins-good \
    && rm -rf /var/lib/apt/lists/*

# Fail the build, not the test run, if an element the pipeline needs is missing.
RUN gst-inspect-1.0 audiotestsrc  > /dev/null && \
    gst-inspect-1.0 opusenc       > /dev/null && \
    gst-inspect-1.0 rtpopuspay    > /dev/null && \
    gst-inspect-1.0 rtpstreampay  > /dev/null && \
    gst-inspect-1.0 tcpserversink > /dev/null

COPY vnc-server-entrypoint.sh /usr/local/bin/vnc-server-entrypoint.sh
RUN chmod +x /usr/local/bin/vnc-server-entrypoint.sh

EXPOSE 5900 5901
ENTRYPOINT ["/usr/local/bin/vnc-server-entrypoint.sh"]
