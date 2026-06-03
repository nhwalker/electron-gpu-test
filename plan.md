# Electron 41.1.1 GPU container (UBI9, NVIDIA hardware decode for WebRTC)

Electron 41.1.1 is **Chromium 146**. The goal is hardware video **decode** (including
the WebRTC decode path) inside a UBI9 container, on an NVIDIA GPU, running on X11
first and Weston/Wayland later.

## Read this first (the load-bearing caveats)

- **NVIDIA + VAAPI + Chromium is officially unsupported upstream.** Chromium on Linux
  only does hardware decode via VAAPI; NVIDIA has no native VAAPI, so we bridge it with
  elFarto’s `nvidia-vaapi-driver` (NVDEC behind a VAAPI shim). Chromium’s docs say the
  NVIDIA VA-API drivers are known not to support Chromium, and `VaapiOnNvidiaGPUs` is a
  developer-test switch that’s off by default. Treat this as **best-effort, verify
  empirically** — not guaranteed.
- The bridge is **decode-only** (encode stays on native NVENC) and decodes H.264, HEVC,
  VP8, VP9, AV1, MPEG-2, VC-1. Requires NVIDIA driver 470 or 500+.
- Version facts in our favor: WebRTC hardware decode via VAAPI requires Chrome ≥ 136
  (146 clears it), and decode is default-on **on Wayland** in Chromium 143+. On **X11 the
  explicit feature flags are mandatory** (they’re in `launch.sh`).
- We use the **`direct`** nvidia-vaapi-driver backend, which talks to the kernel driver
  directly instead of through EGL. That’s why the decode path is identical on X11 and
  Wayland — avoid the `egl` backend (its X11 support is fragile and being removed).
- The proprietary NVIDIA userspace is **not baked into the image** — it’s injected at run
  time by the NVIDIA Container Toolkit via CDI.
- The bridge is packaged: **`libva-nvidia-driver`** in RPM Fusion (renamed from
  `nvidia-vaapi-driver`), with EL9 builds. The Containerfile below uses that RPM and keeps
  a from-source build as a fallback. Tradeoff: the RPM adds RPM Fusion + EPEL repos to the
  image; the source build keeps the dependency set closed.
- Nothing here was testable against live RHEL/RPM Fusion repos; treat package names, the
  release-RPM URLs, and dependency behavior as **verify-on-your-host**.

## Verify in this order

1. `vainfo` inside the container — does the `nvidia` driver load and list codecs?
1. `chrome://gpu` → “Video Acceleration Information” shows decode profiles.
1. `chrome://media-internals` during a WebRTC call → decoder is `VaapiVideoDecoder`,
   not `FFmpegVideoDecoder`.
1. `chrome://webrtc-internals` for the live session.

If an earlier stage shows software, the later ones can’t be right. For a real
hardware-decode test use a real X server with the NVIDIA driver loaded — under Xvfb
Chromium falls back to software GL.

-----

## Containerfile

```dockerfile
# =============================================================================
# Electron 41.1.1 (Chromium 146) on UBI9 with NVIDIA hardware DECODE for WebRTC
# Single stage: the VAAPI->NVDEC bridge comes from the RPM Fusion package
# `libva-nvidia-driver` (see the source-build fallback after this block).
# =============================================================================
#
# GPU model: NVIDIA proprietary userspace (libcuda, libnvcuvid/NVDEC, etc.) is
# NOT baked in. It is injected at RUN TIME by the NVIDIA Container Toolkit via
# CDI. The bridge RPM provides /usr/lib64/dri/nvidia_drv_video.so and resolves
# libnvcuvid at runtime from that CDI mount.
#
# IMPORTANT (verify on your host): not testable against live RHEL/RPM Fusion
# repos here. Confirm (a) the el9 build of libva-nvidia-driver resolves, (b) the
# release-RPM URLs below, (c) its deps do NOT drag in the full RPM Fusion driver
# stack (kmod-nvidia / xorg-x11-drv-nvidia*), which conflicts with the CDI model:
#     dnf repoquery --requires --resolve libva-nvidia-driver
# =============================================================================

FROM registry.access.redhat.com/ubi9/ubi:latest

# --- Repos: EPEL9 + RPM Fusion (enable free AND nonfree; the bridge has been
# listed under both). CRB for any -devel pulls. VERIFY these release-RPM URLs. ---
RUN dnf -y install \
        https://dl.fedoraproject.org/pub/epel/epel-release-latest-9.noarch.rpm \
        https://mirrors.rpmfusion.org/free/el/rpmfusion-free-release-9.noarch.rpm \
        https://mirrors.rpmfusion.org/nonfree/el/rpmfusion-nonfree-release-9.noarch.rpm && \
    dnf config-manager --set-enabled crb || true

# --- Electron / Chromium 146 runtime shared libraries ---
# These are the libs a prebuilt Electron binary dynamically loads on RHEL/UBI.
RUN dnf -y install \
        nss nspr \
        atk at-spi2-atk at-spi2-core \
        cups-libs \
        libdrm mesa-libgbm mesa-libGL mesa-libEGL mesa-dri-drivers \
        libxkbcommon libxkbcommon-x11 \
        libX11 libXcomposite libXdamage libXext libXfixes libXrandr \
        libXScrnSaver libXtst libxcb libxshmfence libXi libXcursor libXrender \
        libwayland-client libwayland-egl libwayland-cursor \
        alsa-lib pango cairo cairo-gobject gtk3 expat \
        libnotify libuuid \
        libva libva-utils \
        dbus-libs \
        liberation-fonts && \
    dnf clean all

# --- VAAPI->NVDEC bridge from RPM Fusion (the bit previously built from source) ---
# install_weak_deps=False keeps it from pulling the RPM Fusion driver stack.
# If a HARD dependency still drags in kmod-nvidia / xorg-x11-drv-nvidia*, use the
# source-build fallback documented below this code block instead.
RUN dnf -y install --setopt=install_weak_deps=False \
        libva-nvidia-driver && \
    dnf clean all

# --- Environment that selects the NVIDIA VAAPI backend ---
ENV LIBVA_DRIVER_NAME=nvidia
# nvidia-vaapi-driver backend: "direct" is recommended on recent drivers;
# fall back to "egl" if you see init failures in vainfo.
ENV NVD_BACKEND=direct
# Let the NVIDIA Container Toolkit see what it needs at runtime.
ENV NVIDIA_DRIVER_CAPABILITIES=all
ENV NVIDIA_VISIBLE_DEVICES=all

# --- Your app ---
WORKDIR /app
COPY ./your-electron-app/ /app/
# (Install/unpack your packaged Electron 41.1.1 app here.)

# Run as non-root where possible.
RUN useradd -m -u 1001 app && chown -R app:app /app
USER app

# Launch via the wrapper that carries all the switches.
ENTRYPOINT ["/app/launch.sh"]
```

### Fallback: build the bridge from source

Use this only if the `libva-nvidia-driver` RPM isn’t acceptable (no RPM Fusion in the
image) or its dependencies pull the driver stack. Add this as a first stage and replace
the RPM install line with `COPY --from=vaapi-build /staging/usr/ /usr/`:

```dockerfile
FROM registry.access.redhat.com/ubi9/ubi:latest AS vaapi-build

RUN dnf -y install \
        https://dl.fedoraproject.org/pub/epel/epel-release-latest-9.noarch.rpm && \
    dnf config-manager --set-enabled crb || \
    dnf config-manager --set-enabled codeready-builder-for-rhel-9-$(uname -m)-rpms || true

RUN dnf -y install \
        meson ninja-build gcc gcc-c++ git pkgconf-pkg-config \
        libva-devel libdrm-devel mesa-libEGL-devel mesa-libgbm-devel \
        gstreamer1-plugins-bad-free-devel && \
    dnf clean all

# nv-codec-headers (ffnvcodec) - the NVDEC headers the shim builds on.
RUN git clone --depth 1 https://github.com/FFmpeg/nv-codec-headers.git /tmp/nvch && \
    make -C /tmp/nvch install PREFIX=/usr

# Pin a known tag for a reproducible build (check for a newer tag first).
RUN git clone --depth 1 --branch v0.0.13 \
        https://github.com/elFarto/nvidia-vaapi-driver.git /tmp/nvd && \
    cd /tmp/nvd && \
    meson setup build --prefix=/usr && \
    ninja -C build && \
    DESTDIR=/staging ninja -C build install
# Result: /staging/usr/lib64/dri/nvidia_drv_video.so (may be lib/ on some setups)
```

-----

## launch.sh

```bash
#!/usr/bin/env bash
# launch.sh - Electron 41.1.1 (Chromium 146) launch switches for
# NVIDIA hardware DECODE (incl. WebRTC). Works on X11 (test first) and
# Wayland/Weston (later) - the NVIDIA decode path is identical for both
# because we use the nvidia-vaapi-driver "direct" backend.
set -euo pipefail

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

exec /app/electron /app \
  "$OZONE_FLAG" \
  --enable-features=UseOzonePlatform,AcceleratedVideoDecodeLinuxGL,AcceleratedVideoDecodeLinuxZeroCopyGL,VaapiOnNvidiaGPUs,VaapiIgnoreDriverChecks \
  --use-gl=angle \
  --use-angle=gl \
  --ignore-gpu-blocklist \
  --disable-gpu-driver-bug-workarounds \
  --disable-gpu-sandbox \
  "$@"

# --- Run-command differences between display servers (NOT in this script) -----
# X11 (test here first):
#   podman run --device nvidia.com/gpu=all \
#     -e OZONE=x11 -e DISPLAY="$DISPLAY" \
#     -v /tmp/.X11-unix:/tmp/.X11-unix:ro \
#     -v "$XAUTHORITY":/home/app/.Xauthority:ro -e XAUTHORITY=/home/app/.Xauthority \
#     <image>
#   (or `xhost +SI:localuser:1001` on the host instead of mounting the cookie)
#
# Wayland/Weston (later):
#   podman run --device nvidia.com/gpu=all \
#     -e OZONE=wayland -e WAYLAND_DISPLAY=wayland-0 -e XDG_RUNTIME_DIR=/run/user/1001 \
#     -v /run/user/1001/wayland-0:/run/user/1001/wayland-0 \
#     <image>
#
# Everything else (LIBVA_DRIVER_NAME=nvidia, NVD_BACKEND=direct, the feature
# flags, the GL backend) is identical across both - those live in the image.
```

-----

## Notes on the switches

- `--ozone-platform=x11` for testing now; `--ozone-platform-hint=auto` or `=wayland` for
  Weston later. Same image either way.
- `AcceleratedVideoDecodeLinuxGL` (+ `ZeroCopyGL`) are the Chromium-131+ renamed VAAPI
  decode features; WebRTC uses the same decoder path, so these enable WebRTC hardware
  decode once a working VAAPI driver is present.
- `VaapiOnNvidiaGPUs` + `VaapiIgnoreDriverChecks` opt in to VAAPI on NVIDIA and skip the
  driver allowlist that would otherwise reject the shim.
- `--use-gl=angle --use-angle=gl` is the GL backend the Chromium VAAPI docs specify for
  NVIDIA; it’s display-server-agnostic.
- `--disable-gpu-sandbox` is narrower than `--no-sandbox`; the GPU-process sandbox
  commonly blocks the shim’s access to NVDEC in a container. Use `--no-sandbox` only if
  this isn’t enough and you accept the tradeoff.
- To set these in the main process instead of the CLI, mirror them with
  `app.commandLine.appendSwitch(...)` **before** the `ready` event.