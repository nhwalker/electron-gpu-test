#!/usr/bin/env bash
# ChromeDriver launches the configured "browser binary" as:
#     <binary> <chromedriver switches...> data:,
# and it forces a leading "--" onto every chromeOptions arg, so the Electron app
# path can't be passed as a positional arg through chromeOptions. This wrapper is
# that binary: it injects the real Electron executable, the app directory, and a
# start URL *ahead* of ChromeDriver's switches, so Electron loads the real app.
#
# ELECTRON_START_URL lets the test choose the page the app opens on launch
# (defaults to the bundled deterministic page).
exec /app/node_modules/electron/dist/electron \
    /app \
    "${ELECTRON_START_URL:-file:///app/render-check.html}" \
    "$@"
