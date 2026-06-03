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
  RENDER_FLAGS=(
    --disable-gpu
    --enable-features=UseOzonePlatform
  )
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
