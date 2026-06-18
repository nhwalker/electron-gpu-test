#!/usr/bin/env bash
# Firefox test-harness entrypoint. Re-run the policy merge so any mounted
# /config override (e.g. a test bookmarks.json) is applied, then expose
# geckodriver. Selenium connects and launches Firefox (headless) itself, picking
# up the global enterprise policies this image baked in.
set -euo pipefail

# Apply runtime config overrides (no-op beyond the baked defaults when none are
# mounted). Same merge the production launcher runs.
/app/setup-config.sh || echo "firefox-config: WARNING policy merge failed; using baked policies" >&2

# geckodriver speaks WebDriver directly on 4444. Bind all interfaces and allow
# the Testcontainers host's loopback Host header so the mapped port is reachable.
# (--allow-origins is omitted: the classic WebDriver client sends no Origin
# header, and geckodriver rejects a wildcard there.)
exec geckodriver \
    --host 0.0.0.0 \
    --port 4444 \
    --allow-hosts 127.0.0.1 localhost
