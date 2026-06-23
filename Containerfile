# =============================================================================
# Electron 41.1.1 (Chromium 146) on UBI9 with NVIDIA hardware DECODE for WebRTC
# Single stage: the VAAPI->NVDEC bridge comes from the RPM Fusion package
# `libva-nvidia-driver` (see the source-build fallback in plan.md).
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

# --- Optional: trust extra CA certificates before any TLS download ------------
# Build environments behind a TLS-intercepting proxy (corporate / CI egress
# gateways) present a private CA that dnf/curl won't trust by default, which
# breaks every https repo fetch below. Drop such CAs (one PEM per *.crt) into the
# build context's `extra-cas/` dir and they're trusted here. The dir is always
# present but usually contains only a placeholder, so with direct egress this is
# a no-op.
COPY extra-cas/ /etc/pki/ca-trust/source/anchors/
RUN update-ca-trust extract || true

# --- Repos: EPEL9 + RPM Fusion (enable free AND nonfree; the bridge has been
# listed under both). CRB (CodeReady Builder) backs some of the X libs. On UBI9
# the repo id is `ubi-9-codeready-builder-rpms`, NOT `crb` (that's the
# CentOS Stream / full-RHEL name) -- try all known ids and ignore misses.
# VERIFY these release-RPM URLs on your host. ---
RUN dnf -y install \
        https://dl.fedoraproject.org/pub/epel/epel-release-latest-9.noarch.rpm \
        https://mirrors.rpmfusion.org/free/el/rpmfusion-free-release-9.noarch.rpm \
        https://mirrors.rpmfusion.org/nonfree/el/rpmfusion-nonfree-release-9.noarch.rpm && { \
        dnf config-manager --set-enabled \
            crb ubi-9-codeready-builder-rpms \
            "codeready-builder-for-rhel-9-$(uname -m)-rpms" || true; \
        # We never install Cisco's openh264; disabling its repo (added by
        # epel-release) avoids a known-flaky build dependency on codecs.fedoraproject.org.
        dnf config-manager --set-disabled epel-cisco-openh264 || true; \
    }

# --- Rocky Linux 9 as a LAST-RESORT fallback repo (priority=200; see rocky9.repo)
# Supplies the handful of libs UBI omits (libxkbcommon-x11, libnotify, fonts).
# The low priority means dnf only pulls from Rocky what nothing else provides, so
# UBI base packages are never shadowed. ---
COPY rocky9.repo /etc/yum.repos.d/rocky9.repo

# --- Electron / Chromium 146 runtime shared libraries (REQUIRED) ---
# These are the libs a prebuilt Electron binary hard-loads on RHEL/UBI; a miss
# here should fail the build. (Verified against the CI run: every name below
# resolved in UBI9 BaseOS/AppStream + EPEL9.)
# install_weak_deps=False is essential now that the Rocky fallback repo exists:
# without it, dnf would satisfy these libs' optional Recommends (pipewire,
# flatpak, xdg-desktop-portal, ModemManager, ...) from Rocky and balloon the
# image. We want only hard deps -- Rocky stays a true last resort.
# nss-tools (certutil/pk12util) + openssl are NOT Electron runtime libs: they let
# setup-certs.sh (sourced by launch.sh) import a runtime-mounted CA bundle and
# client cert/key into the app user's NSS DB for custom-CA HTTPS and mutual TLS.
# See app/setup-certs.sh.
RUN dnf -y install --setopt=install_weak_deps=False \
        nss nspr \
        nss-tools openssl \
        atk at-spi2-atk at-spi2-core \
        cups-libs \
        libdrm mesa-libgbm mesa-libGL mesa-libEGL mesa-dri-drivers \
        libxkbcommon \
        libX11 libXcomposite libXdamage libXext libXfixes libXrandr \
        libXScrnSaver libXtst libxcb libxshmfence libXi libXcursor libXrender \
        libwayland-client libwayland-egl libwayland-cursor \
        alsa-lib pango cairo cairo-gobject gtk3 expat \
        libuuid \
        libva libva-utils \
        vulkan-loader \
        dbus-libs && \
    dnf clean all
# vulkan-loader provides libvulkan.so.1, the Vulkan ICD loader that Chromium's
# Vulkan/WebGPU/Skia-Graphite paths dlopen. The NVIDIA Vulkan driver itself (the
# ICD .so + its /usr/share/vulkan/icd.d/*.json manifest) is NOT baked in -- it is
# injected at run time by the NVIDIA Container Toolkit via CDI, gated on
# NVIDIA_DRIVER_CAPABILITIES including "graphics"/"display" (we set =all below).
# Do NOT add mesa-vulkan-drivers: that ships the software/AMD/Intel ICDs, which
# we don't want shadowing the injected NVIDIA ICD.

# --- Runtime libs absent from UBI9, sourced from the Rocky fallback repo ---
# UBI9's public repos don't carry these; the Rocky 9 last-resort repo above does:
#   - libxkbcommon-x11 : X11 keyboard mapping (the X11 Ozone path needs it)
#   - libnotify        : the HTML5 Notifications API
#   - liberation-*-fonts : default Western fonts for text rendering
# These now install normally (a miss is a real failure worth surfacing).
# install_weak_deps=False keeps Rocky to just these packages + their hard deps.
RUN dnf -y install --setopt=install_weak_deps=False \
        libxkbcommon-x11 \
        libnotify \
        liberation-sans-fonts liberation-serif-fonts liberation-mono-fonts && \
    dnf clean all

# --- VAAPI->NVDEC bridge from RPM Fusion (the bit previously built from source) ---
# install_weak_deps=False keeps it from pulling the RPM Fusion driver stack.
# If a HARD dependency still drags in kmod-nvidia / xorg-x11-drv-nvidia*, use the
# source-build fallback documented in plan.md instead.
RUN dnf -y install --setopt=install_weak_deps=False \
        libva-nvidia-driver && \
    dnf clean all

# --- Node.js (build-time only): used to npm-install the Electron app below.
# The app runs on Electron's own bundled Node at runtime, not this one. ---
RUN dnf -y module enable nodejs:20 && \
    dnf -y install --setopt=install_weak_deps=False nodejs npm && \
    dnf clean all

# --- Environment that selects the NVIDIA VAAPI backend ---
ENV LIBVA_DRIVER_NAME=nvidia
# nvidia-vaapi-driver backend: "direct" is recommended on recent drivers;
# fall back to "egl" if you see init failures in vainfo.
ENV NVD_BACKEND=direct
# Let the NVIDIA Container Toolkit see what it needs at runtime.
ENV NVIDIA_DRIVER_CAPABILITIES=all
ENV NVIDIA_VISIBLE_DEVICES=all

# --- The app ---
# A minimal Electron app that opens the web pages named on its command line.
# All Node/npm files live under app/ in the repo and are installed into /app.
WORKDIR /app
COPY ./app/ /app/

# Install dependencies (downloads the Electron 41.1.1 prebuilt binary) and make
# the launch wrapper executable. Skip Electron's run-time download of extra
# binaries we don't need.
RUN npm install --omit=optional && \
    chmod +x /app/launch.sh /app/setup-certs.sh && \
    npm cache clean --force

# Run as non-root where possible.
RUN useradd -m -u 1001 app && chown -R app:app /app

# --- Persistent web-storage mount point ---------------------------------------
# Pre-create the conventional ELECTRON_USER_DATA path owned by the app user. A
# *fresh named volume* mounted here inherits this dir's ownership/permissions
# (the runtime copies them into the empty volume on first use), so the non-root
# app (uid 1001) can write to it WITHOUT podman's ':U' flag or a startup chown.
# This is opt-in: storage only persists here when the operator sets
# ELECTRON_USER_DATA=/data/profile and mounts a volume there (see README). Note
# this copy-up applies to named volumes only -- bind mounts keep the host path's
# ownership, for which the ':U' flag / launch.sh guard still apply.
RUN install -d -o app -g app -m 0700 /data/profile

USER app

# Launch via the wrapper that carries all the switches. Pass URLs as args:
#   <image> https://example.com https://webrtc.github.io/samples/
ENTRYPOINT ["/app/launch.sh"]
