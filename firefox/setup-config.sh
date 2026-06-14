#!/usr/bin/env bash
# setup-config.sh - Assemble Firefox's active enterprise policy (policies.json).
#
# Firefox reads enterprise policies -- the bookmarks toolbar AND feature lockdowns
# like DisablePrivateBrowsing -- from policies.json. We keep the toolbar bookmarks
# in their OWN small file (bookmarks.json: a plain array) so customising the
# toolbar never means editing the larger policy file, then merge the two into the
# active policies.json with jq.
#
# This runs in two places with the same logic:
#   - at BUILD time, baking the repo's firefox/policies.json + firefox/bookmarks.json
#     into the image, and
#   - at LAUNCH time (from firefox-launch.sh), re-merging so an operator can mount
#     overrides and change config with NO image rebuild -- mirroring how
#     setup-certs.sh consumes runtime-mounted /certs.
#
# Inputs (any may be absent; this is a no-op beyond the baked defaults then):
#   FIREFOX_POLICIES   full base policy override  (default /config/policies.json)
#   FIREFOX_BOOKMARKS  bookmarks-array override   (default /config/bookmarks.json)
# Baked defaults shipped in the image:
#   FIREFOX_POLICIES_BASE   (default /app/firefox/policies.json)
#   FIREFOX_BOOKMARKS_BASE  (default /app/firefox/bookmarks.json)
#
# Precedence (so a full override behaves predictably):
#   base      = mounted FIREFOX_POLICIES if present, else the baked policies.json
#   bookmarks = mounted FIREFOX_BOOKMARKS if present;
#               else the baked bookmarks.json ONLY when base is the baked file;
#               else leave the override's own Bookmarks untouched.
#
# The merged result is written to BOTH locations Firefox reads on Linux, with
# identical content (so Mozilla's "combine policies from all sources" never hits a
# conflict): the system path and the install's distribution dir.
set -euo pipefail

POLICIES_BASE="${FIREFOX_POLICIES_BASE:-/app/firefox/policies.json}"
BOOKMARKS_BASE="${FIREFOX_BOOKMARKS_BASE:-/app/firefox/bookmarks.json}"
POLICIES_OVERRIDE="${FIREFOX_POLICIES:-/config/policies.json}"
BOOKMARKS_OVERRIDE="${FIREFOX_BOOKMARKS:-/config/bookmarks.json}"

# Both paths Firefox consults for policies.json on Linux.
TARGETS=(
  /etc/firefox/policies/policies.json
  /usr/lib64/firefox/distribution/policies.json
)

if ! command -v jq >/dev/null 2>&1; then
  echo "firefox-config: jq not found; leaving existing policies.json in place" >&2
  exit 0
fi

# Pick the base policy file and remember whether it's the baked default.
base="$POLICIES_BASE" base_is_baked=1
if [[ -f "$POLICIES_OVERRIDE" ]]; then
  base="$POLICIES_OVERRIDE" base_is_baked=0
  echo "firefox-config: using mounted base policies $POLICIES_OVERRIDE" >&2
fi
if [[ ! -f "$base" ]]; then
  echo "firefox-config: no base policies file ($base); nothing to do" >&2
  exit 0
fi

# Pick the bookmarks array, if any applies (see precedence above).
bookmarks=""
if [[ -f "$BOOKMARKS_OVERRIDE" ]]; then
  bookmarks="$BOOKMARKS_OVERRIDE"
  echo "firefox-config: using mounted bookmarks $BOOKMARKS_OVERRIDE" >&2
elif [[ "$base_is_baked" -eq 1 && -f "$BOOKMARKS_BASE" ]]; then
  bookmarks="$BOOKMARKS_BASE"
fi

# Build the merged policy document. With a bookmarks file, replace
# .policies.Bookmarks with its array; otherwise pass the base through unchanged.
if [[ -n "$bookmarks" ]]; then
  merged="$(jq --slurpfile bm "$bookmarks" '.policies.Bookmarks = $bm[0]' "$base")"
  count="$(jq 'length' "$bookmarks")"
else
  merged="$(jq '.' "$base")"
  count="$(jq '.policies.Bookmarks | length // 0' "$base")"
fi

# Write to every target Firefox might read. A target may be read-only (e.g. an
# operator bind-mounted their own file over it) -- skip those without failing.
wrote=0
for t in "${TARGETS[@]}"; do
  mkdir -p "$(dirname "$t")" 2>/dev/null || true
  if printf '%s\n' "$merged" > "$t" 2>/dev/null; then
    ((wrote++)) || true
  else
    echo "firefox-config: could not write $t (read-only?); skipping" >&2
  fi
done

echo "firefox-config: wrote policies.json to $wrote location(s) ($count bookmark(s))" >&2
