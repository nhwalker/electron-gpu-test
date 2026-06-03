#!/usr/bin/env bash
# Boot a virtual X display, then hand off to ChromeDriver. ChromeDriver launches
# the Electron binary (supplied by the test via chromeOptions) against this
# display, so the real GUI app runs headlessly and is drivable over WebDriver.
set -euo pipefail

export DISPLAY="${DISPLAY:-:99}"

# Virtual framebuffer so Electron has a screen to render into.
Xvfb "$DISPLAY" -screen 0 1280x800x24 -nolisten tcp &
XVFB_PID=$!

# Wait for the X11 socket to appear before accepting sessions.
DISPLAY_NUM="${DISPLAY#:}"
for _ in $(seq 1 50); do
    if [ -S "/tmp/.X11-unix/X${DISPLAY_NUM}" ]; then break; fi
    sleep 0.1
done

cleanup() { kill "$XVFB_PID" 2>/dev/null || true; }
trap cleanup EXIT

# --allowed-ips= / --allowed-origins=* let the Testcontainers host connect from
# outside the container (ChromeDriver otherwise refuses non-local clients).
exec npx --no-install chromedriver \
    --port=4444 \
    --allowed-ips= \
    --allowed-origins='*' \
    --verbose
