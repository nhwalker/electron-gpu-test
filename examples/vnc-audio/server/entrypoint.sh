#!/usr/bin/env bash
# Brings up the example desktop and its sound, then hands off to x11vnc.
#
#   Xvfb -> a picture -> dbus -> pipewire/wireplumber/pipewire-pulse
#        -> a null sink (whose .monitor is what audio transports capture)
#        -> a test tone playing into that sink
#        -> every hook in /etc/vnc-audio.d/ (the derived images' transports)
#        -> x11vnc
#
# Nothing here is specific to a transport: the derived images only add a hook.
set -euo pipefail

DISPLAY_NUM="${DISPLAY_NUM:-20}"
SCREEN_SIZE="${SCREEN_SIZE:-1024x768x24}"
VNC_PORT="${VNC_PORT:-5900}"
VNC_PASSWORD="${VNC_PASSWORD:-s3cret}"

# The sink every transport captures from. Audio played by anything on the
# desktop lands here, and "<name>.monitor" is the source that plays it back out.
SINK_NAME="${SINK_NAME:-vnc0}"

# The built-in test signal, so a fresh container makes sound with nothing else
# running. TONE_WAVE=silence turns it off; a steady sine is the default because
# it is the easiest thing to check automatically (one peak, known frequency).
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
# user session in a container, so start one.
# (--print-address --fork keeps this to the dbus-daemon package alone; the
# dbus-launch wrapper lives in dbus-x11, which we would otherwise have to pull.)
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
# desktop mixes into it, and ${SINK_NAME}.monitor is the source that reads that
# mix back -- the single point every audio option taps.
pactl load-module module-null-sink \
    sink_name="${SINK_NAME}" \
    sink_properties="device.description=VNC_desktop_audio" >/dev/null
pactl set-default-sink "${SINK_NAME}"
log "null sink '${SINK_NAME}' ready; capture from '${SINK_NAME}.monitor'"

# --- The test tone ------------------------------------------------------------
if [[ "${TONE_WAVE}" != "silence" ]]; then
    log "playing a ${TONE_HZ}Hz ${TONE_WAVE} into ${SINK_NAME} (TONE_WAVE=silence to stop)"
    gst-launch-1.0 -q \
        audiotestsrc wave="${TONE_WAVE}" freq="${TONE_HZ}" volume="${TONE_VOLUME}" is-live=true \
        ! audioconvert ! audioresample \
        ! pulsesink device="${SINK_NAME}" &
fi

# --- Transports ---------------------------------------------------------------
# Each derived image drops one script here. They run with the sound server up
# and the sink in place, and are expected to background whatever they start.
if [[ -d /etc/vnc-audio.d ]]; then
    for hook in /etc/vnc-audio.d/*; do
        [[ -x "${hook}" ]] || continue
        log "running hook ${hook}"
        SINK_NAME="${SINK_NAME}" "${hook}"
    done
fi

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
