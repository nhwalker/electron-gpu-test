#!/usr/bin/env bash
# Serves a deterministic desktop over VNC: an Xvfb display with a fixed,
# multi-colour picture on it, exported by x11vnc with password authentication.
#
# The picture is chosen so the test can assert on it two ways: the static
# widgets make the framebuffer unmistakably non-blank and multi-coloured, and
# the ticking clock makes it keep changing, which proves framebuffer updates
# keep flowing rather than a single frame having arrived.
set -euo pipefail

DISPLAY_NUM="${DISPLAY_NUM:-20}"
SCREEN_SIZE="${SCREEN_SIZE:-1024x768x24}"
VNC_PORT="${VNC_PORT:-5900}"
VNC_PASSWORD="${VNC_PASSWORD:-s3cret}"

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

echo "vnc-server: serving ${DISPLAY} (${SCREEN_SIZE}) on port ${VNC_PORT}"
exec x11vnc \
    -display "${DISPLAY}" \
    -rfbport "${VNC_PORT}" \
    -passwd "${VNC_PASSWORD}" \
    -listen 0.0.0.0 \
    -forever \
    -shared \
    -noxdamage
