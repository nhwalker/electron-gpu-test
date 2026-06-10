#!/usr/bin/env bash
# setup-certs.sh - Runtime TLS: import mounted certs into the app user's NSS DB.
#
# Chromium/Electron on Linux read extra trusted roots AND client certificates
# from the per-user NSS database at ~/.pki/nssdb. We let an operator mount a
# directory of PEM files at run time (default /certs, override TLS_CERT_DIR) and
# import everything found there at launch -- no image rebuild per deployment.
#
# Discovery rules (the dir is scanned recursively, so flat or ca/ + client/
# layouts both work):
#   - a *.key file is a client private key; its certificate is the sibling with
#     the same stem and a cert extension (foo.key <-> foo.crt/.pem/.cert). Each
#     such pair is imported as one client identity (for mutual TLS).
#   - any cert file (*.crt/*.pem/*.cert) with NO sibling *.key is imported as a
#     trusted CA (so custom-CA HTTPS verifies).
#   - an encrypted client key's passphrase comes from a sibling foo.pass file,
#     else $TLS_CLIENT_KEY_PASS, else the key is assumed unencrypted.
# This is a no-op when the directory is absent or empty, so default runs are
# unaffected.
#
# launch.sh SOURCES this script (rather than executing it) because
# ensure_writable_home may export a corrected HOME, and the exec'd Electron
# must inherit it to read the same ~/.pki/nssdb we import into. It also runs
# standalone for testing: ./setup-certs.sh
set -euo pipefail

# Containers commonly start non-root users with HOME=/ (not the passwd home),
# which is unwritable and is NOT where Chromium would look for the NSS DB. Pin
# HOME to the user's real, writable home and EXPORT it (see header note).
ensure_writable_home() {
  [[ -n "${HOME:-}" && "$HOME" != "/" && -w "$HOME" ]] && return 0
  local home_dir
  home_dir="$(getent passwd "$(id -u)" 2>/dev/null | cut -d: -f6)"
  export HOME="${home_dir:-/home/app}"
}

# Print all cert/key files under $1 (recursive), NUL-separated and sorted.
list_cert_files() {
  find "$1" -type f \( \
      -iname '*.crt' -o -iname '*.pem' -o -iname '*.cert' -o -iname '*.key' \
    \) -print0 2>/dev/null | sort -z
}

# Create an empty-password SQL NSS DB at $1 if one isn't there yet.
ensure_nssdb() {
  [[ -f "$1/cert9.db" ]] && return 0
  mkdir -p "$1"
  certutil -d "sql:$1" -N --empty-password
}

# certutil/pk12util reject duplicate nicknames, so derive a unique nickname
# from $1, remembering past results in USED_NICKS. The result is returned in
# $NICK rather than on stdout: a $(...) call would run in a subshell and lose
# the USED_NICKS bookkeeping.
declare -A USED_NICKS=()
unique_nick() {
  local base="$1" i=1
  NICK="$base"
  while [[ -n "${USED_NICKS[$NICK]:-}" ]]; do
    NICK="${base}-$((i++))"
  done
  USED_NICKS[$NICK]=1
}

# If cert file $1 has a sibling key (same stem, .key/.KEY), print its path.
# A matching key makes the cert a client identity, not a CA.
sibling_key() {
  local stem="${1%.*}" ext
  for ext in key KEY; do
    [[ -f "$stem.$ext" ]] && { printf '%s' "$stem.$ext"; return 0; }
  done
  return 1
}

# import_client_identity <nssdb> <cert> <key> <nick>
# Client cert+key -> transient PKCS#12 -> pk12util (NSS can't import a bare
# PEM key). Passphrase: sibling .pass file, else env fallback, else none.
import_client_identity() {
  local nssdb="$1" cert="$2" key="$3" nick="$4"

  local keypass="${TLS_CLIENT_KEY_PASS:-}"
  [[ -f "${cert%.*}.pass" ]] && keypass="$(<"${cert%.*}.pass")"
  local -a passin=()
  [[ -n "$keypass" ]] && passin=(-passin "pass:$keypass")

  local p12 p12pass
  p12="$(mktemp)"
  p12pass="$(head -c 18 /dev/urandom | base64)"
  openssl pkcs12 -export -name "$nick" \
    -in "$cert" -inkey "$key" "${passin[@]}" \
    -out "$p12" -passout "pass:$p12pass"
  pk12util -d "sql:$nssdb" -i "$p12" -W "$p12pass" >/dev/null
  shred -u "$p12" 2>/dev/null || rm -f "$p12"

  echo "cert-store: imported client cert $nick" >&2
}

# import_ca_cert <nssdb> <cert> <nick>
# Standalone cert -> trusted SSL CA. Delete any existing same-nick entry first
# so re-launches are idempotent.
import_ca_cert() {
  local nssdb="$1" cert="$2" nick="$3"
  certutil -d "sql:$nssdb" -D -n "$nick" >/dev/null 2>&1 || true
  certutil -d "sql:$nssdb" -A -n "$nick" -t "C,," -i "$cert"
  echo "cert-store: imported CA $nick" >&2
}

setup_cert_store() {
  local cert_dir="${TLS_CERT_DIR:-/certs}"
  [[ -d "$cert_dir" ]] || return 0

  ensure_writable_home
  local nssdb="$HOME/.pki/nssdb"

  # Collect the PEM files once so we can pair certs with keys by stem.
  local -a all_files=()
  local f
  while IFS= read -r -d '' f; do
    all_files+=("$f")
  done < <(list_cert_files "$cert_dir")
  [[ ${#all_files[@]} -gt 0 ]] || return 0

  ensure_nssdb "$nssdb"

  local ca_count=0 client_count=0 key
  for f in "${all_files[@]}"; do
    [[ "${f,,}" == *.key ]] && continue   # keys are handled with their cert

    unique_nick "$(basename "${f%.*}")"
    if key="$(sibling_key "$f")"; then
      import_client_identity "$nssdb" "$f" "$key" "$NICK"
      ((client_count++)) || true
    else
      import_ca_cert "$nssdb" "$f" "$NICK"
      ((ca_count++)) || true
    fi
  done

  echo "cert-store: scanned $cert_dir ($ca_count CA, $client_count client)" >&2
}

setup_cert_store
