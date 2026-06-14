#!/usr/bin/env bash
# firefox-launch.sh - Launch wrapper for the UBI9 Firefox container.
#
# It loads runtime-mounted TLS certs EXACTLY the way the Electron app does: by
# sourcing the very same app/setup-certs.sh, which scans a mounted directory
# (default /certs, override TLS_CERT_DIR) and imports each PEM into an NSS DB --
# standalone certs as trusted CAs, cert+key pairs as client identities for
# mutual TLS. The only difference is *which* NSS DB: Chromium/Electron read
# ~/.pki/nssdb, whereas Firefox reads cert9.db/key4.db from its profile
# directory. We point the shared importer at the profile via TLS_NSSDB, so the
# discovery rules, auto-pairing, and "cert-store: imported ..." log lines are
# identical. See app/setup-certs.sh for the full discovery rules.
#
# Pass URLs (and any extra Firefox flags) as arguments:
#   <image> https://internal.example.test/
set -euo pipefail

FIREFOX_BIN="${FIREFOX_BIN:-/usr/bin/firefox}"

# --- Pin HOME -----------------------------------------------------------------
# Containers commonly start non-root users with HOME=/ (unwritable and not where
# the profile/NSS DB should live). Mirror setup-certs.sh's ensure_writable_home
# so the profile and its NSS DB land somewhere writable and consistent, and so
# Firefox itself reads the same home. (setup-certs.sh re-checks and no-ops once
# HOME is already good.)
if [[ -z "${HOME:-}" || "$HOME" == "/" || ! -w "$HOME" ]]; then
  home_dir="$(getent passwd "$(id -u)" 2>/dev/null | cut -d: -f6)"
  export HOME="${home_dir:-/home/firefox}"
fi

# --- Firefox profile ----------------------------------------------------------
# A fixed, dedicated profile so the imported certs always land where this exact
# launch reads them. Firefox's profile NSS DB (cert9.db/key4.db) is the SQL NSS
# format that setup-certs.sh writes via the `sql:` prefix, so an import into the
# profile dir is exactly what Firefox consults for trusted roots and client
# certs.
PROFILE_DIR="${FIREFOX_PROFILE:-$HOME/.mozilla/firefox/container.default}"
mkdir -p "$PROFILE_DIR"

# Profile-local prefs that enterprise policies can't express: the cert
# auto-select isn't in the Preferences-policy allowlist, and the onboarding
# toggles have no dedicated policy key. Everything that IS a lockable feature
# (telemetry, updates, default-browser check, first-run pages) now lives in
# firefox/policies.json instead -- see setup-config.sh. Written every launch so
# the prefs can't drift; user.js wins over prefs.js on each start.
cat > "$PROFILE_DIR/user.js" <<'EOF'
// Auto-select the single client certificate for mutual TLS (no blocking dialog) --
// the same "offer the one identity we have" behaviour main.js gives Electron.
user_pref("security.default_personal_cert", "Select Automatically");
// Quiet the onboarding/whatsnew UI in a container (no dedicated policy key).
user_pref("browser.aboutwelcome.enabled", false);
user_pref("browser.startup.homepage_override.mstone", "ignore");
EOF

# --- Runtime TLS: import mounted certs into the profile's NSS DB --------------
# SOURCE (not execute) the SAME importer the Electron app uses, pointed at the
# Firefox profile. Sourcing matches launch.sh: setup-certs.sh may correct HOME,
# which the exec'd Firefox must inherit. TLS_NSSDB selects the profile DB.
export TLS_NSSDB="$PROFILE_DIR"
source "$(dirname "${BASH_SOURCE[0]}")/setup-certs.sh"

# --- Firefox enterprise policies (bookmarks toolbar + feature lockdowns) -------
# Regenerate the active policies.json from the baked defaults, applying any
# runtime-mounted overrides (/config/policies.json, /config/bookmarks.json) so
# config can change with no image rebuild. Bookmarks live in their own file
# (bookmarks.json) so the toolbar is easy to customise. Beyond the baked-in
# defaults this is a no-op when nothing is mounted. Run (not sourced): it writes
# policy files and needs nothing back in this shell. See firefox/setup-config.sh.
"$(dirname "${BASH_SOURCE[0]}")/setup-config.sh" \
  || echo "firefox-config: WARNING policy merge failed; using baked policies" >&2

# --- Content sandbox ----------------------------------------------------------
# Firefox's content sandbox needs unprivileged user namespaces, which a default
# container (no --cap-add/--security-opt) blocks -- tabs then crash before any
# page loads. Disable it by default so the app starts under a plain
# `podman run`, mirroring the Electron wrapper's unconditional --no-sandbox. This
# is a controlled test container; opt the sandbox back in with
# FIREFOX_KEEP_SANDBOX=1 (and grant the container userns, e.g. --cap-add SYS_ADMIN).
if [[ "${FIREFOX_KEEP_SANDBOX:-}" != "1" ]]; then
  export MOZ_DISABLE_CONTENT_SANDBOX=1
fi

# --no-remote / --new-instance: never hand off to or adopt another running
# Firefox; always drive THIS profile. URLs and any extra flags come from "$@".
exec "$FIREFOX_BIN" --profile "$PROFILE_DIR" --no-remote --new-instance "$@"
