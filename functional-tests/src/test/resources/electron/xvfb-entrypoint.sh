#!/usr/bin/env bash
# Run a virtual X server and publish its socket into the shared /tmp/.X11-unix
# volume. Announces "X-READY" once the socket exists so the app container's wait
# strategy can gate on it.
set -euo pipefail

DISPLAY_NUM="${DISPLAY_NUM:-99}"
SOCKET="/tmp/.X11-unix/X${DISPLAY_NUM}"

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
