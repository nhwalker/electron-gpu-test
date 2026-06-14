# =============================================================================
# Electron 41.1.1 (Chromium 146) on the shared browser base, with NVIDIA hardware
# DECODE for WebRTC.
#
# The GPU/VAAPI stack, EPEL/RPM Fusion/CRB + Rocky repos, build-time CA trust, the
# NSS cert toolchain and the default fonts all come from Containerfile.base. This
# image adds only the Chromium hard-load libraries, Node (build-time) and the
# Electron app. See Containerfile.base.
#
# GPU model: the NVIDIA proprietary userspace is injected at RUN TIME via CDI
# (NVIDIA Container Toolkit); the VAAPI->NVDEC bridge (libva-nvidia-driver) lives
# in the base. See plan.md for the source-build fallback and verification steps.
# =============================================================================

ARG BASE_IMAGE=browser-base:undertest
FROM ${BASE_IMAGE}

# --- Chromium runtime shared libraries ----------------------------------------
# Nothing is installed here: the shared base already provides every library a
# prebuilt Electron binary hard-loads (the whole GTK/X GUI toolkit, gtk3, atk,
# at-spi2, cairo, pango, gdk-pixbuf2, the libX*/libxkbcommon/libwayland stack,
# alsa-lib, cups-libs, ..., plus the GPU/mesa/libva and NSS libs).
#
# Three packages from the canonical Electron-on-Linux dependency lists were
# removed after a binary analysis of this build (Electron 41 / Chromium 146)
# showed none are load-time (NEEDED) deps -- Electron starts without them:
#   libXScrnSaver    - X11 MIT-SCREEN-SAVER ext (legacy idle time / screensaver
#                      inhibit). Not referenced by this binary; Chromium now does
#                      idle + inhibit over D-Bus (org.freedesktop.ScreenSaver).
#   libxkbcommon-x11 - loads the keymap from the X server. Not referenced; this
#                      build uses only CORE libxkbcommon (provided by the base) to
#                      compile keymaps from layout names.
#   libnotify        - dlopen'd for the Notifications API. Without it the app runs
#                      fine but desktop notifications won't display. Re-add it here
#                      (dnf -y install libnotify) if you need notifications.

# --- Node.js (build-time only): used to npm-install the Electron app below.
# The app runs on Electron's own bundled Node at runtime, not this one. ---
RUN dnf -y module enable nodejs:20 && \
    dnf -y install --setopt=install_weak_deps=False nodejs npm && \
    dnf clean all

# --- Environment that selects the NVIDIA VAAPI backend ---
# (NVIDIA_DRIVER_CAPABILITIES / NVIDIA_VISIBLE_DEVICES are set in the base.)
ENV LIBVA_DRIVER_NAME=nvidia
# nvidia-vaapi-driver backend: "direct" is recommended on recent drivers;
# fall back to "egl" if you see init failures in vainfo.
ENV NVD_BACKEND=direct

# --- The app ---
# A minimal Electron app that opens the web pages named on its command line.
# All Node/npm files live under app/ in the repo and are installed into /app.
WORKDIR /app
COPY ./app/ /app/

# Install dependencies (downloads the Electron 41.1.1 prebuilt binary) and make
# the launch wrapper executable. Skip Electron's run-time download of extras.
RUN npm install --omit=optional && \
    chmod +x /app/launch.sh /app/setup-certs.sh && \
    npm cache clean --force

# Run as non-root where possible.
RUN useradd -m -u 1001 app && chown -R app:app /app
USER app

# Launch via the wrapper that carries all the switches. Pass URLs as args:
#   <image> https://example.com https://webrtc.github.io/samples/
ENTRYPOINT ["/app/launch.sh"]
