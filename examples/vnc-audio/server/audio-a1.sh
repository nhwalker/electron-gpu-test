#!/usr/bin/env bash
# Option A1: put the desktop's audio on a TCP port as raw PCM.
#
# The entire server side of the option is the pipeline below. There is no
# encoder, no container and no framing: whatever byte you read at offset N is a
# sample, and the format is whatever the caps say -- which is why the client can
# hardcode it. Everything else in this file is the standalone browser demo.
set -euo pipefail

SINK_NAME="${SINK_NAME:-vnc0}"
AUDIO_PORT="${AUDIO_PORT:-5901}"
DEMO_PORT="${DEMO_PORT:-8080}"
RATE="${AUDIO_RATE:-48000}"
CHANNELS="${AUDIO_CHANNELS:-2}"

echo "audio-a1: raw S16LE ${RATE}Hz x${CHANNELS} from ${SINK_NAME}.monitor on tcp/${AUDIO_PORT}" >&2

# recover-policy=latest: a client that stops reading is jumped forward to the
# live edge instead of stalling the capture for everyone else. buffers-max
# bounds what the sink will hold for a slow client before doing that.
gst-launch-1.0 -q \
    pulsesrc device="${SINK_NAME}.monitor" \
    ! audioconvert ! audioresample \
    ! audio/x-raw,format=S16LE,rate="${RATE}",channels="${CHANNELS}",layout=interleaved \
    ! tcpserversink host=0.0.0.0 port="${AUDIO_PORT}" recover-policy=latest buffers-max=200 &

# --- The standalone demo (not part of the option) -----------------------------
# A browser cannot open a TCP socket, so something has to relay. In the Electron
# app that is the main process's bridge; here it is websockify, which also
# serves the demo page from /srv/demo. Same shape either way: the page talks
# WebSocket, the server talks TCP.
echo "audio-a1: demo page + WebSocket bridge on http://0.0.0.0:${DEMO_PORT}/" >&2
websockify --web /srv/demo "${DEMO_PORT}" "127.0.0.1:${AUDIO_PORT}" &
