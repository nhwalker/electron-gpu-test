#!/usr/bin/env bash
# Test harness entrypoint: boot a virtual display, start the REAL production app
# via its launch.sh (with remote debugging enabled), then expose ChromeDriver so
# Selenium can attach to the running app. ChromeDriver is only revealed once the
# app's DevTools endpoint is live, so "ChromeDriver ready" implies "app is up".
set -euo pipefail

export DISPLAY=:99
Xvfb :99 -screen 0 1280x800x24 -nolisten tcp >/tmp/xvfb.log 2>&1 &

# Wait for the X11 socket before launching the GUI app.
for _ in $(seq 1 100); do
    [ -S /tmp/.X11-unix/X99 ] && break
    sleep 0.1
done

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
