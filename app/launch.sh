#!/usr/bin/env bash
# launch.sh - Electron 41.1.1 (Chromium 146) launch switches for
# NVIDIA hardware DECODE (incl. WebRTC). Works on X11 (test first) and
# Wayland/Weston (later) - the NVIDIA decode path is identical for both
# because we use the nvidia-vaapi-driver "direct" backend.
set -euo pipefail

# The Electron binary that npm installed into the app's node_modules. The
# Containerfile installs the app (and its electron devDependency) under /app.
ELECTRON_BIN="${ELECTRON_BIN:-/app/node_modules/electron/dist/electron}"

# --- Pick the Ozone platform ---
# For deterministic early testing, force x11 by exporting OZONE=x11.
# Leave unset to auto-detect (prefers Wayland if WAYLAND_DISPLAY is present,
# otherwise X11).
OZONE_FLAG="--ozone-platform-hint=auto"
if [[ "${OZONE:-}" == "x11" ]]; then
  : "${DISPLAY:?X11 selected but DISPLAY is not set (mount /tmp/.X11-unix and set DISPLAY)}"
  OZONE_FLAG="--ozone-platform=x11"
elif [[ "${OZONE:-}" == "wayland" ]]; then
  : "${WAYLAND_DISPLAY:?Wayland selected but WAYLAND_DISPLAY is not set}"
  : "${XDG_RUNTIME_DIR:?Wayland selected but XDG_RUNTIME_DIR is not set}"
  OZONE_FLAG="--ozone-platform=wayland"
fi

# --- Hardware vs software rendering -------------------------------------------
# The NVIDIA flags below target hardware decode/render. With no GPU present (a CI
# runner, or any plain `docker/podman run` without --device nvidia.com/gpu=all),
# forcing the VAAPI/NVIDIA GL path makes Chromium fail to bring up its GPU stack.
# Detect that and fall back to software rendering so the app still starts and
# renders -- the NVIDIA stack in the image is then simply inert.
#
# Override the probe with FORCE_SOFTWARE=1 (always software) or FORCE_HARDWARE=1
# (assume a GPU and skip the probe).
gpu_present() {
  [[ "${FORCE_HARDWARE:-}" == "1" ]] && return 0
  [[ "${FORCE_SOFTWARE:-}" == "1" ]] && return 1
  # The NVIDIA userspace is injected at runtime; its device nodes are the signal.
  compgen -G "/dev/nvidia*" >/dev/null 2>&1 && return 0
  # Any other DRM render node (non-NVIDIA GPUs).
  compgen -G "/dev/dri/renderD*" >/dev/null 2>&1 && return 0
  return 1
}

# --- Runtime TLS: import mounted certs into the app user's NSS DB -------------
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
# Containers commonly start non-root users with HOME=/ (not the passwd home),
# which is unwritable and is NOT where Chromium would look for the NSS DB. Pin
# HOME to the user's real, writable home and EXPORT it so the exec'd Electron
# reads the same ~/.pki/nssdb we import into.
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

if gpu_present; then
  RENDER_FLAGS=(
    --enable-features=UseOzonePlatform,AcceleratedVideoDecodeLinuxGL,AcceleratedVideoDecodeLinuxZeroCopyGL,VaapiOnNvidiaGPUs,VaapiIgnoreDriverChecks
    --use-gl=angle
    --use-angle=gl
    --ignore-gpu-blocklist
    --disable-gpu-driver-bug-workarounds
  )
else
  echo "launch.sh: no GPU detected -> software rendering (NVIDIA hardware decode disabled)" >&2
  # The NVIDIA VAAPI driver can't initialise without the GPU; stop libva from
  # even trying to load it (avoids noisy init failures).
  unset LIBVA_DRIVER_NAME NVD_BACKEND
  if [[ "${SOFTWARE_WEBGL:-}" == "1" ]]; then
    # Software rendering that STILL provides WebGL. Plain --disable-gpu turns the
    # GPU process off entirely, which also disables WebGL -- so a GPU-less host
    # can't run WebGL content at all. SwiftShader (Chromium's CPU rasteriser,
    # reached through ANGLE) gives a real, if slow, WebGL implementation instead,
    # letting the WebGL path be exercised on hosts with no GPU (e.g. CI runners).
    # --enable-unsafe-swiftshader opts back into SwiftShader-backed WebGL, which
    # recent Chromium gates behind a flag now that hardware is the default.
    echo "launch.sh: SOFTWARE_WEBGL=1 -> SwiftShader WebGL (ANGLE, CPU)" >&2
    RENDER_FLAGS=(
      --use-gl=angle
      --use-angle=swiftshader
      --enable-unsafe-swiftshader
      --enable-features=UseOzonePlatform
    )
  else
    RENDER_FLAGS=(
      --disable-gpu
      --enable-features=UseOzonePlatform
    )
  fi
fi

exec "$ELECTRON_BIN" /app \
  "$OZONE_FLAG" \
  "${RENDER_FLAGS[@]}" \
  --no-sandbox \
  "$@"

# NOTE on --no-sandbox (was --disable-gpu-sandbox):
# As the non-root 'app' user in a default container, Chromium's setuid/namespace
# sandbox can't initialise -- it dies with "Failed to move to new namespace ...
# Operation not permitted" before any page loads. --disable-gpu-sandbox only
# covers the GPU process, not that one, so it isn't enough on its own.
# --no-sandbox disables the full sandbox and lets the app start with a plain
# `docker/podman run` (no --security-opt/--cap-add needed). Acceptable here
# because this is a controlled GPU-decode test container. To keep the sandbox
# instead, make chrome-sandbox setuid-root (chmod 4755, owned root) AND run with
# --security-opt seccomp=unconfined (or --cap-add SYS_ADMIN).

# --- Run-command differences between display servers (NOT in this script) -----
# X11 (test here first):
#   podman run --device nvidia.com/gpu=all \
#     -e OZONE=x11 -e DISPLAY="$DISPLAY" \
#     -v /tmp/.X11-unix:/tmp/.X11-unix:ro \
#     -v "$XAUTHORITY":/home/app/.Xauthority:ro -e XAUTHORITY=/home/app/.Xauthority \
#     <image> https://webrtc.github.io/samples/
#   (or `xhost +SI:localuser:1001` on the host instead of mounting the cookie)
#
# Wayland/Weston (later):
#   podman run --device nvidia.com/gpu=all \
#     -e OZONE=wayland -e WAYLAND_DISPLAY=wayland-0 -e XDG_RUNTIME_DIR=/run/user/1001 \
#     -v /run/user/1001/wayland-0:/run/user/1001/wayland-0 \
#     <image> https://webrtc.github.io/samples/
#
# Everything else (LIBVA_DRIVER_NAME=nvidia, NVD_BACKEND=direct, the feature
# flags, the GL backend) is identical across both - those live in the image.
