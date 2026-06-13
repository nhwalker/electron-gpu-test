#!/usr/bin/env bash
# Provide the X display the app container records against. By default that's a
# virtual X server (Xvfb) this sidecar starts and publishes into the shared
# /tmp/.X11-unix volume. But if a display is PASSED IN -- i.e. DISPLAY is set and
# its socket already exists under the mounted /tmp/.X11-unix (e.g. the host's
# real local display, bind-mounted in) -- we use THAT display as-is and start no
# Xvfb, so ffmpeg records the passed-in display instead of a throwaway one.
#
# Either way the container announces "X-READY" once a usable display socket
# exists, so the app container's wait strategy can gate on it, and then stays
# alive so record-start.sh / record-stop.sh can grab the display on demand.
set -euo pipefail

DISPLAY_NUM="${DISPLAY_NUM:-99}"
SOCKET="/tmp/.X11-unix/X${DISPLAY_NUM}"

# A display was passed in: DISPLAY is set AND its socket is already present in the
# mounted socket dir. Use it directly -- do NOT start (or clear) an Xvfb. We only
# need to keep the container running so recording via `docker exec` can reach it.
if [ -n "${DISPLAY:-}" ] && [ -S "${SOCKET}" ]; then
    echo "X-READY display :${DISPLAY_NUM} (using passed-in display, no Xvfb)"
    exec tail -f /dev/null
fi

# No display passed in -> serve our own virtual X display on :${DISPLAY_NUM}.
rm -f "${SOCKET}"

# -ac disables host-based access control, so the app container needs no xauth
# cookie to connect over the shared socket.
Xvfb ":${DISPLAY_NUM}" -screen 0 1280x800x24 -ac -nolisten tcp &
XVFB_PID=$!

# Make the socket reachable by any uid in the app container (it runs as uid 1001).
for _ in $(seq 1 100); do
    [ -S "${SOCKET}" ] && break
    sleep 0.1
done
chmod 0777 "${SOCKET}" 2>/dev/null || true

echo "X-READY display :${DISPLAY_NUM}"
wait "${XVFB_PID}"
