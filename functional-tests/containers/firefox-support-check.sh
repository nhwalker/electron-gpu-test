#!/usr/bin/env bash
# firefox-support-check.sh - Read what Firefox reports about graphics + media
# acceleration on about:support, and print it as assertable "support-check:" lines.
#
# Driven from INSIDE the container (like firefox-policy-check.sh) to sidestep
# geckodriver's Host-header allowlist. It opens a headless session with the same
# acceleration prefs firefox-launch.sh sets, navigates about:support, and reports:
#   support-check: compositing=<value>     e.g. "WebRender" or "WebRender (Software)"
#   support-check: vaapi-pref=<true|false> the media.ffmpeg.vaapi.enabled pref Firefox saw
#   support-check: hwdecode=<yes|no>       any codec with Hardware Decoding = Yes
#
# NOTE: the kiosk-default policy BlockAboutSupport blocks this page, so the caller
# must run the harness with a policy override that lifts the block (the GPU test
# mounts a minimal /config/policies.json). If the page is blocked we say so and fail.
set -euo pipefail

GD="http://localhost:4444"

for _ in $(seq 1 100); do
  curl -fsS "$GD/status" >/dev/null 2>&1 && break
  sleep 0.2
done

# Session with the production acceleration prefs so about:support reflects them.
caps='{"capabilities":{"alwaysMatch":{"browserName":"firefox","moz:firefoxOptions":{
  "args":["-headless"],
  "prefs":{
    "gfx.webrender.all":true,
    "media.ffmpeg.vaapi.enabled":true,
    "media.hardware-video-decoding.enabled":true,
    "media.hardware-video-decoding.force-enabled":true
  }}}}}'
sid=""
for _ in $(seq 1 50); do
  sid="$(curl -fsS -X POST "$GD/session" -H 'Content-Type: application/json' -d "$caps" \
      2>/dev/null | jq -r '.value.sessionId // empty')"
  [ -n "$sid" ] && break
  sleep 0.4
done
[ -n "$sid" ] || { echo "support-check: ERROR could not start a Firefox session" >&2; exit 1; }
cleanup() { curl -fsS -X DELETE "$GD/session/$sid" >/dev/null 2>&1 || true; }
trap cleanup EXIT

curl -fsS -X POST "$GD/session/$sid/url" -H 'Content-Type: application/json' \
    -d '{"url":"about:support"}' >/dev/null
sleep 3

title="$(curl -fsS -X POST "$GD/session/$sid/execute/sync" -H 'Content-Type: application/json' \
    -d '{"script":"return document.title;","args":[]}' | jq -r '.value')"
if [[ "$title" == *"Blocked"* ]]; then
  echo "support-check: ERROR about:support is blocked by policy (BlockAboutSupport)." \
       "Run with a /config/policies.json override that lifts the block." >&2
  exit 1
fi

text="$(curl -fsS -X POST "$GD/session/$sid/execute/sync" -H 'Content-Type: application/json' \
    -d '{"script":"return document.body.innerText;","args":[]}' | jq -r '.value')"

# about:support renders tab-separated rows. Pull the fields we care about.
compositing="$(awk -F'\t' '$1=="Compositing"{print $2; exit}' <<<"$text")"
vaapi_pref="$(awk -F'\t' '$1=="media.ffmpeg.vaapi.enabled"{print $2; exit}' <<<"$text")"
# In the "Codec Support Information" table the last column is Hardware Decoding;
# any row whose final field is "Yes" means a codec decodes in hardware.
hwdecode="$(awk -F'\t' '
  /Hardware Decoding/ {seen=1; next}
  seen && NF>=3 && $NF=="Yes" {found=1}
  END {print (found?"yes":"no")}' <<<"$text")"

echo "support-check: compositing=${compositing:-unknown}"
echo "support-check: vaapi-pref=${vaapi_pref:-false}"
echo "support-check: hwdecode=${hwdecode}"
echo "support-check: done"
