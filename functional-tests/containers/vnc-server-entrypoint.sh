#!/usr/bin/env bash
# Serves a deterministic desktop over VNC: an Xvfb display with a fixed,
# multi-colour picture on it, exported by x11vnc with password authentication.
#
# The picture is chosen so the test can assert on it two ways: the static
# widgets make the framebuffer unmistakably non-blank and multi-coloured, and
# the ticking clock makes it keep changing, which proves framebuffer updates
# keep flowing rather than a single frame having arrived.
#
# Alongside it, a fixed-frequency tone goes out as Opus on a second port, in the
# framing the app's viewer expects -- so the audio test can assert on a known
# frequency the same way the picture test asserts on known colours.
set -euo pipefail

DISPLAY_NUM="${DISPLAY_NUM:-20}"
SCREEN_SIZE="${SCREEN_SIZE:-1024x768x24}"
VNC_PORT="${VNC_PORT:-5900}"
VNC_PASSWORD="${VNC_PASSWORD:-s3cret}"
AUDIO_PORT="${AUDIO_PORT:-5901}"
# The frequency the audio test looks for after decoding. 440Hz sits comfortably
# inside Opus's band and well away from the FFT's edges.
TONE_HZ="${TONE_HZ:-440}"

export DISPLAY=":${DISPLAY_NUM}"

# -ac disables X access control: the only clients are in this container.
Xvfb "${DISPLAY}" -screen 0 "${SCREEN_SIZE}" -ac -nolisten tcp &

for _ in $(seq 1 300); do
    [ -S "/tmp/.X11-unix/X${DISPLAY_NUM}" ] && break
    sleep 0.1
done
[ -S "/tmp/.X11-unix/X${DISPLAY_NUM}" ] || { echo "Xvfb never came up on ${DISPLAY}" >&2; exit 1; }

# The picture: a coloured root window plus widgets in fixed positions.
xsetroot -solid '#102040'
xlogo -geometry 400x300+80+60 -bg '#ffcc00' -fg '#cc0033' &
xeyes -geometry 200x150+560+380 -bg '#ffffff' -fg '#0033cc' &
# -update 1 moves the second hand every second, so the framebuffer keeps changing.
xclock -geometry 200x200+560+60 -bg '#f5f5f5' -fg '#101010' -update 1 &

# The audio stream: a sine wave, encoded as 20ms Opus frames, each one wrapped
# in RTP and length-prefixed per RFC 4571 (what `rtpstreampay` does) -- the wire
# format app/vnc-viewer/audio.js decodes.
echo "vnc-server: streaming a ${TONE_HZ}Hz tone as Opus on port ${AUDIO_PORT}" >&2
gst-launch-1.0 -q \
    audiotestsrc wave=sine freq="${TONE_HZ}" volume=0.3 is-live=true \
    ! audioconvert ! audioresample \
    ! audio/x-raw,rate=48000,channels=2 \
    ! opusenc bitrate=96000 frame-size=20 \
    ! rtpopuspay pt=96 \
    ! rtpstreampay \
    ! tcpserversink host=0.0.0.0 port="${AUDIO_PORT}" recover-policy=latest buffers-max=200 &

echo "vnc-server: serving ${DISPLAY} (${SCREEN_SIZE}) on port ${VNC_PORT}"
exec x11vnc \
    -display "${DISPLAY}" \
    -rfbport "${VNC_PORT}" \
    -passwd "${VNC_PASSWORD}" \
    -listen 0.0.0.0 \
    -forever \
    -shared \
    -noxdamage
