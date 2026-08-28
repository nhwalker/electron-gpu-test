#!/usr/bin/env bash
# Brings up the example desktop, its sound, and the two ports the app connects
# to:
#
#   Xvfb -> a picture -> dbus -> pipewire/wireplumber/pipewire-pulse
#        -> a null sink (whose .monitor is what the audio pipeline captures)
#        -> a test tone playing into that sink
#        -> the Opus stream on 5901
#        -> x11vnc on 5900
set -euo pipefail

DISPLAY_NUM="${DISPLAY_NUM:-20}"
SCREEN_SIZE="${SCREEN_SIZE:-1024x768x24}"
VNC_PORT="${VNC_PORT:-5900}"
VNC_PASSWORD="${VNC_PASSWORD:-s3cret}"

# The port the app's `?audio=on` looks for, and the sink the stream captures.
# Audio played by anything on the desktop lands in that sink, and
# "<name>.monitor" is the source that plays it back out.
AUDIO_PORT="${AUDIO_PORT:-5901}"
SINK_NAME="${SINK_NAME:-vnc0}"
RATE="${AUDIO_RATE:-48000}"
CHANNELS="${AUDIO_CHANNELS:-2}"
# 96 kbit/s stereo is transparent enough for desktop audio; 64k is fine for
# speech, 128k for music. The same audio uncompressed would be 1536 kbit/s.
OPUS_BITRATE="${OPUS_BITRATE:-96000}"
# 20ms frames: the usual latency/overhead compromise. 10 halves the frame
# latency and costs ~15% more bitrate.
OPUS_FRAME_SIZE="${OPUS_FRAME_SIZE:-20}"

# The built-in test signal, so a fresh container makes sound with nothing else
# running. TONE_WAVE=silence turns it off; a steady sine is the default because
# it is the easiest thing to check (one peak, known frequency -- the viewer
# reports the loudest tone it is playing).
TONE_WAVE="${TONE_WAVE:-sine}"
TONE_HZ="${TONE_HZ:-440}"
TONE_VOLUME="${TONE_VOLUME:-0.2}"

export DISPLAY=":${DISPLAY_NUM}"
export XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-/tmp/runtime}"
mkdir -p "${XDG_RUNTIME_DIR}"
chmod 700 "${XDG_RUNTIME_DIR}"

log() { echo "vnc-audio: $*" >&2; }

wait_for() {
    local what="$1" tries="$2"; shift 2
    for _ in $(seq 1 "${tries}"); do
        if "$@" >/dev/null 2>&1; then return 0; fi
        sleep 0.1
    done
    log "timed out waiting for ${what}"
    return 1
}

# --- The display --------------------------------------------------------------
# -ac disables X access control; the only clients are inside this container.
Xvfb "${DISPLAY}" -screen 0 "${SCREEN_SIZE}" -ac -nolisten tcp &
wait_for "the X display" 300 test -S "/tmp/.X11-unix/X${DISPLAY_NUM}"

# Something to look at, so the VNC view isn't a black rectangle. xsetroot only
# ships in some EL9 package sets, so colour the root only if it is here.
command -v xsetroot >/dev/null 2>&1 && xsetroot -solid '#102040'
xlogo -geometry 400x300+80+60 -bg '#ffcc00' -fg '#cc0033' &
xeyes -geometry 200x150+560+380 -bg '#ffffff' -fg '#0033cc' &
xclock -geometry 200x200+560+60 -bg '#f5f5f5' -fg '#101010' -update 1 &

# --- The sound server ---------------------------------------------------------
# PipeWire's session manager talks over the session bus, and there is no systemd
# user session in a container, so start one. (--print-address --fork keeps this
# to the dbus-daemon package alone; the dbus-launch wrapper lives in dbus-x11.)
DBUS_SESSION_BUS_ADDRESS="$(dbus-daemon --session --print-address --fork)"
export DBUS_SESSION_BUS_ADDRESS

pipewire &
# WirePlumber only manages real devices; the null sink below is created by
# pipewire-pulse itself, so audio still works even if this one is unhappy.
wireplumber &
pipewire-pulse &

# `pactl info` succeeding is the real readiness signal: it means the PulseAudio
# API socket is up and answering, which is all any capture client needs.
wait_for "the PulseAudio API" 300 pactl info

# The null sink: a device with no hardware behind it. Everything played on the
# desktop mixes into it, and ${SINK_NAME}.monitor reads that mix back.
pactl load-module module-null-sink \
    sink_name="${SINK_NAME}" \
    sink_properties="device.description=VNC_desktop_audio" >/dev/null
pactl set-default-sink "${SINK_NAME}"
log "null sink '${SINK_NAME}' ready; capturing from '${SINK_NAME}.monitor'"

# --- The test tone ------------------------------------------------------------
if [[ "${TONE_WAVE}" != "silence" ]]; then
    log "playing a ${TONE_HZ}Hz ${TONE_WAVE} into ${SINK_NAME} (TONE_WAVE=silence to stop)"
    gst-launch-1.0 -q \
        audiotestsrc wave="${TONE_WAVE}" freq="${TONE_HZ}" volume="${TONE_VOLUME}" is-live=true \
        ! audioconvert ! audioresample \
        ! pulsesink device="${SINK_NAME}" &
fi

# --- The audio stream ---------------------------------------------------------
# opusenc      encodes 20ms frames.
# rtpopuspay   wraps each frame in an RTP packet, so it carries a sequence
#              number and a 48kHz timestamp.
# rtpstreampay adds the RFC 4571 two-byte big-endian length prefix that makes
#              RTP framing survive a byte stream.
#
# The app reads [u16 length][RTP packet] and hands each RTP payload -- one whole
# Opus frame -- to WebCodecs. No container, no demuxer.
#
# recover-policy=latest: a client that stops reading is jumped forward to the
# live edge instead of stalling the capture for everyone else.
log "streaming ${SINK_NAME}.monitor as Opus (${OPUS_BITRATE}bps, ${OPUS_FRAME_SIZE}ms) on port ${AUDIO_PORT}"
gst-launch-1.0 -q \
    pulsesrc device="${SINK_NAME}.monitor" \
    ! audioconvert ! audioresample \
    ! audio/x-raw,rate="${RATE}",channels="${CHANNELS}" \
    ! opusenc bitrate="${OPUS_BITRATE}" frame-size="${OPUS_FRAME_SIZE}" \
    ! rtpopuspay pt=96 \
    ! rtpstreampay \
    ! tcpserversink host=0.0.0.0 port="${AUDIO_PORT}" recover-policy=latest buffers-max=200 &

# --- RFB ----------------------------------------------------------------------
log "serving ${DISPLAY} (${SCREEN_SIZE}) over VNC on port ${VNC_PORT}"
exec x11vnc \
    -display "${DISPLAY}" \
    -rfbport "${VNC_PORT}" \
    -passwd "${VNC_PASSWORD}" \
    -listen 0.0.0.0 \
    -forever \
    -shared \
    -noxdamage \
    "$@"
