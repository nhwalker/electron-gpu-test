#!/usr/bin/env bash
# Option A2: put the desktop's audio on a TCP port as framed Opus packets.
#
# opusenc      encodes 20ms frames (~96 kbit/s by default).
# rtpopuspay   wraps each frame in an RTP packet, so it carries a sequence
#              number and a 48kHz timestamp.
# rtpstreampay adds the RFC 4571 two-byte big-endian length prefix that makes
#              RTP framing survive a byte stream.
#
# The client therefore reads [u16 length][RTP packet] and hands the RTP payload
# -- one whole Opus frame -- to WebCodecs. No container, no demuxer.
set -euo pipefail

SINK_NAME="${SINK_NAME:-vnc0}"
AUDIO_PORT="${AUDIO_PORT:-5901}"
DEMO_PORT="${DEMO_PORT:-8080}"
RATE="${AUDIO_RATE:-48000}"
CHANNELS="${AUDIO_CHANNELS:-2}"
# 96 kbit/s stereo is transparent enough for desktop audio; 64k is fine for
# speech, 128k for music. Compare with A1's fixed 1536 kbit/s.
BITRATE="${OPUS_BITRATE:-96000}"
# 20ms frames: the usual latency/overhead compromise. 10 halves the frame
# latency and costs ~15% more bitrate.
FRAME_SIZE="${OPUS_FRAME_SIZE:-20}"

echo "audio-a2: Opus ${BITRATE}bps ${FRAME_SIZE}ms frames from ${SINK_NAME}.monitor on tcp/${AUDIO_PORT}" >&2

gst-launch-1.0 -q \
    pulsesrc device="${SINK_NAME}.monitor" \
    ! audioconvert ! audioresample \
    ! audio/x-raw,rate="${RATE}",channels="${CHANNELS}" \
    ! opusenc bitrate="${BITRATE}" frame-size="${FRAME_SIZE}" \
    ! rtpopuspay pt=96 \
    ! rtpstreampay \
    ! tcpserversink host=0.0.0.0 port="${AUDIO_PORT}" recover-policy=latest buffers-max=200 &

# --- The standalone demo (not part of the option) -----------------------------
# Identical to the A1 image: websockify relays the same TCP stream to the page
# and serves it, standing in for the Electron app's own bridge.
echo "audio-a2: demo page + WebSocket bridge on http://0.0.0.0:${DEMO_PORT}/" >&2
websockify --web /srv/demo "${DEMO_PORT}" "127.0.0.1:${AUDIO_PORT}" &
