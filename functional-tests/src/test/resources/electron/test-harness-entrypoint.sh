#!/usr/bin/env bash
# Test harness entrypoint. The virtual display comes from a sidecar Xvfb
# container that shares /tmp/.X11-unix with us, so here we just wait for that
# socket, start the REAL production app via launch.sh (with remote debugging),
# then expose ChromeDriver for Selenium to attach. ChromeDriver is only revealed
# once the app's DevTools endpoint is live, so "ChromeDriver ready" implies
# "app is up".
set -euo pipefail

export DISPLAY="${DISPLAY:-:99}"
DISPLAY_NUM="${DISPLAY#:}"
SOCKET="/tmp/.X11-unix/X${DISPLAY_NUM}"

# Wait for the X socket published by the sidecar Xvfb container (shared volume).
for _ in $(seq 1 300); do
    [ -S "${SOCKET}" ] && break
    sleep 0.1
done
[ -S "${SOCKET}" ] || { echo "X socket ${SOCKET} never appeared (is the Xvfb sidecar up?)" >&2; exit 1; }

# Launch the production app exactly as it ships: launch.sh picks hardware vs
# software rendering itself (software here, since CI has no GPU). We only add
# remote debugging + the page to load. Under Xvfb we use the X11 Ozone backend.
export OZONE=x11
TARGET_URL="${TARGET_URL:-file:///opt/render-check.html}"
/app/launch.sh --remote-debugging-port=9222 "$TARGET_URL" >/tmp/electron.log 2>&1 &

# Wait for Electron's DevTools port to start accepting connections.
for _ in $(seq 1 300); do
    if (exec 3<>/dev/tcp/127.0.0.1/9222) 2>/dev/null; then
        exec 3>&- 3<&-
        break
    fi
    sleep 0.1
done

# --allowed-ips= / --allowed-origins=* let the Testcontainers host reach the
# driver; it attaches to the already-running app via chromeOptions.debuggerAddress.
exec chromedriver --port=4444 --allowed-ips= --allowed-origins='*' --verbose
