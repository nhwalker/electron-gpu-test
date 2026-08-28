#!/usr/bin/env bash
# One connected client's stream, run by socat with stdout wired to the socket.
#
# streamable=true is the whole trick: it makes webmmux emit a header followed by
# an open-ended series of clusters, with no seek index or duration to backfill --
# which is exactly what MediaSource wants (one initialisation segment, then media
# segments) and what a file-shaped WebM is not.
set -euo pipefail

exec gst-launch-1.0 -q \
    pulsesrc device="${SINK_NAME:-vnc0}.monitor" \
    ! audioconvert ! audioresample \
    ! audio/x-raw,rate="${AUDIO_RATE:-48000}",channels="${AUDIO_CHANNELS:-2}" \
    ! opusenc bitrate="${OPUS_BITRATE:-96000}" frame-size="${OPUS_FRAME_SIZE:-20}" \
    ! webmmux streamable=true \
    ! fdsink fd=1
