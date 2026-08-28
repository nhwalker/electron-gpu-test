#!/usr/bin/env bash
# Option A3: put the desktop's audio on a TCP port as a streamable WebM/Opus
# stream, for the browser's own MediaSource to play.
#
# Same encoder as A2 -- the difference is entirely on the far side: the Opus
# frames go into a WebM container instead of RTP packets, so the browser can
# demux, decode, buffer and clock them itself.
#
# A container has a header, which raw packets do not, so a client that joins
# mid-stream needs the initialisation segment before anything else will parse.
# webmmux does publish a streamheader that multifdsink-style sinks resend to
# each new client, but the client would still have to land on a cluster
# boundary. Giving every connection its own pipeline sidesteps both questions
# and is what socat's fork does here -- at the cost of one encoder per viewer,
# where A1 and A2 fan one stream out to everybody.
set -euo pipefail

SINK_NAME="${SINK_NAME:-vnc0}"
AUDIO_PORT="${AUDIO_PORT:-5901}"
DEMO_PORT="${DEMO_PORT:-8080}"
RATE="${AUDIO_RATE:-48000}"
CHANNELS="${AUDIO_CHANNELS:-2}"
BITRATE="${OPUS_BITRATE:-96000}"
FRAME_SIZE="${OPUS_FRAME_SIZE:-20}"

# The per-connection pipeline reads these; socat's child inherits the environment.
export SINK_NAME AUDIO_RATE="${RATE}" AUDIO_CHANNELS="${CHANNELS}" \
       OPUS_BITRATE="${BITRATE}" OPUS_FRAME_SIZE="${FRAME_SIZE}"

echo "audio-a3: WebM/Opus ${BITRATE}bps from ${SINK_NAME}.monitor on tcp/${AUDIO_PORT} (one pipeline per client)" >&2

socat TCP-LISTEN:"${AUDIO_PORT}",fork,reuseaddr,nodelay EXEC:/usr/local/bin/audio-a3-pipeline.sh &

# --- The standalone demo (not part of the option) -----------------------------
# Identical to the other two images: websockify relays the same TCP stream to
# the page and serves it, standing in for the Electron app's own bridge.
echo "audio-a3: demo page + WebSocket bridge on http://0.0.0.0:${DEMO_PORT}/" >&2
websockify --web /srv/demo "${DEMO_PORT}" "127.0.0.1:${AUDIO_PORT}" &
