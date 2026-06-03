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
# source-build fallback documented in plan.md instead.
RUN dnf -y install --setopt=install_weak_deps=False \
        libva-nvidia-driver && \
    dnf clean all

# --- Node.js (build-time only): used to npm-install the Electron app below.
# The app runs on Electron's own bundled Node at runtime, not this one. ---
RUN dnf -y module enable nodejs:20 && \
    dnf -y install nodejs npm && \
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
    chmod +x /app/launch.sh && \
    npm cache clean --force

# Run as non-root where possible.
RUN useradd -m -u 1001 app && chown -R app:app /app
USER app

# Launch via the wrapper that carries all the switches. Pass URLs as args:
#   <image> https://example.com https://webrtc.github.io/samples/
ENTRYPOINT ["/app/launch.sh"]
