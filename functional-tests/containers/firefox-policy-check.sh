#!/usr/bin/env bash
# firefox-policy-check.sh - Prove the baked enterprise policies are actually
# APPLIED (not just written to disk) by driving the harness's geckodriver from
# INSIDE the container: open a headless Firefox session, load about:policies#active
# (Firefox's report of the policies it is currently enforcing), and check the page
# for each needle passed as an argument.
#
# Driving in-container sidesteps geckodriver's Host-header allowlist (which pins
# host:port and so rejects Testcontainers' random mapped port). The Java test
# runs this via execInContainer and asserts on the "policy-check:" lines, the
# same way the mTLS test asserts on launch-log lines.
#
# Usage: firefox-policy-check.sh <needle> [<needle> ...]
# Prints one line per needle: "policy-check: FOUND <needle>" or "MISSING <needle>".
set -euo pipefail

GD="http://localhost:4444"

# Wait for geckodriver to accept connections.
for _ in $(seq 1 100); do
  curl -fsS "$GD/status" >/dev/null 2>&1 && break
  sleep 0.2
done

# New headless Firefox session (retry: the browser may take a moment to be ready).
sid=""
for _ in $(seq 1 50); do
  sid="$(curl -fsS -X POST "$GD/session" -H 'Content-Type: application/json' \
      -d '{"capabilities":{"alwaysMatch":{"browserName":"firefox","moz:firefoxOptions":{"args":["-headless"]}}}}' \
      2>/dev/null | jq -r '.value.sessionId // empty')"
  [ -n "$sid" ] && break
  sleep 0.4
done
[ -n "$sid" ] || { echo "policy-check: ERROR could not start a Firefox session" >&2; exit 1; }

cleanup() { curl -fsS -X DELETE "$GD/session/$sid" >/dev/null 2>&1 || true; }
trap cleanup EXIT

# Load Firefox's own active-policies report and grab the rendered source.
curl -fsS -X POST "$GD/session/$sid/url" -H 'Content-Type: application/json' \
    -d '{"url":"about:policies#active"}' >/dev/null
sleep 2
src="$(curl -fsS "$GD/session/$sid/source" | jq -r '.value')"

rc=0
for needle in "$@"; do
  if grep -qF -- "$needle" <<<"$src"; then
    echo "policy-check: FOUND $needle"
  else
    echo "policy-check: MISSING $needle"
    rc=1
  fi
done
echo "policy-check: done"
exit "$rc"
