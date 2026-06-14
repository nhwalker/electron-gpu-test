#!/usr/bin/env bash
# =============================================================================
# Builds every container image the functional tests need, BEFORE the tests run.
# The test JVM never builds images: it only consumes the tags produced here (or
# tags injected via the env vars below), so kick this off first:
#
#   functional-tests/containers/build-images.sh            # build everything
#   functional-tests/containers/build-images.sh harness    # build a subset
#
# Targets (default: all), in dependency order:
#   base           production image from the repo's Containerfile
#   xvfb           Xvfb + ffmpeg display/recording sidecar
#   harness        base + version-matched ChromeDriver (FROM base)
#   webgl-harness  harness + vendored offline NASA WorldWind (FROM base)
#   spin-harness   webgl-harness + the spinning-globe page (FROM webgl-harness)
#   firefox        Firefox image from the repo's Containerfile.firefox
#   firefox-harness firefox + version-matched geckodriver (FROM firefox)
#
# Tooling: podman or docker, auto-detected (podman preferred, matching the
# project README); override with CONTAINER_TOOL=docker|podman. Note that the
# Testcontainers-based suite needs the images on the SAME daemon it talks to.
#
# Image tags default to what the test suite expects (see TestImages.java) and
# can be overridden with the matching env vars:
#   ELECTRON_BASE_IMAGE, XVFB_IMAGE, ELECTRON_HARNESS_IMAGE,
#   WEBGL_HARNESS_IMAGE, WEBGL_SPIN_HARNESS_IMAGE,
#   FIREFOX_BASE_IMAGE, FIREFOX_HARNESS_IMAGE
#
# Building `base` runs dnf/npm (downloads Electron); the harness layers download
# ChromeDriver and WorldWind via npm -- so this needs network access, and takes
# a few minutes on a cold cache. Behind a TLS-intercepting egress proxy the host
# CA bundle is forwarded into the build automatically (see prepare_extra_cas).
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

TOOL="${CONTAINER_TOOL:-}"
if [[ -z "${TOOL}" ]]; then
    if command -v podman >/dev/null 2>&1; then
        TOOL=podman
    elif command -v docker >/dev/null 2>&1; then
        TOOL=docker
    else
        echo "error: neither podman nor docker found on PATH (set CONTAINER_TOOL)" >&2
        exit 1
    fi
fi

BASE_IMAGE="${ELECTRON_BASE_IMAGE:-electron-gpu-test:undertest}"
XVFB_IMAGE="${XVFB_IMAGE:-electron-gpu-test:xvfb}"
HARNESS_IMAGE="${ELECTRON_HARNESS_IMAGE:-electron-gpu-test:harness}"
WEBGL_HARNESS_IMAGE="${WEBGL_HARNESS_IMAGE:-electron-gpu-test:webgl-harness}"
WEBGL_SPIN_HARNESS_IMAGE="${WEBGL_SPIN_HARNESS_IMAGE:-electron-gpu-test:webgl-spin-harness}"
FIREFOX_BASE_IMAGE="${FIREFOX_BASE_IMAGE:-firefox-ubi9:undertest}"
FIREFOX_HARNESS_IMAGE="${FIREFOX_HARNESS_IMAGE:-firefox-ubi9:harness}"

ALL_TARGETS=(base xvfb harness webgl-harness spin-harness firefox firefox-harness)
TARGETS=("$@")
if [[ ${#TARGETS[@]} -eq 0 ]]; then
    TARGETS=("${ALL_TARGETS[@]}")
fi

wants() {
    local t
    for t in "${TARGETS[@]}"; do
        [[ "${t}" == "$1" ]] && return 0
    done
    return 1
}

# Validate target names up front so a typo doesn't silently build nothing.
for t in "${TARGETS[@]}"; do
    case "${t}" in
        base|xvfb|harness|webgl-harness|spin-harness|firefox|firefox-harness) ;;
        *)
            echo "error: unknown target '${t}' (valid: ${ALL_TARGETS[*]})" >&2
            exit 1
            ;;
    esac
done

# Behind a TLS-intercepting proxy, drop the host CA bundle into the production
# build context's extra-cas/ so dnf/npm trust the proxy (the Containerfile's
# optional CA-trust step picks it up). The copy is gitignored (extra-cas/*.crt).
prepare_extra_cas() {
    local marker_dir=/usr/local/share/ca-certificates
    local host_bundle=/etc/ssl/certs/ca-certificates.crt
    if [[ -d "${marker_dir}" && -f "${host_bundle}" ]] \
            && ls "${marker_dir}" 2>/dev/null | grep -qi egress; then
        echo ">> egress proxy detected; forwarding the host CA bundle into extra-cas/"
        cp "${host_bundle}" "${REPO_ROOT}/extra-cas/host-egress-bundle.crt"
    fi
}

build() {
    echo ">> ${TOOL} build $*"
    "${TOOL}" build "$@"
}

if wants base; then
    prepare_extra_cas
    build -t "${BASE_IMAGE}" -f "${REPO_ROOT}/Containerfile" "${REPO_ROOT}"
fi

if wants xvfb; then
    build -t "${XVFB_IMAGE}" -f "${SCRIPT_DIR}/xvfb.Dockerfile" "${SCRIPT_DIR}"
fi

if wants harness; then
    build -t "${HARNESS_IMAGE}" --build-arg BASE_IMAGE="${BASE_IMAGE}" \
        -f "${SCRIPT_DIR}/harness.Dockerfile" "${SCRIPT_DIR}"
fi

if wants webgl-harness; then
    build -t "${WEBGL_HARNESS_IMAGE}" --build-arg BASE_IMAGE="${BASE_IMAGE}" \
        -f "${SCRIPT_DIR}/webgl-harness.Dockerfile" "${SCRIPT_DIR}"
fi

if wants spin-harness; then
    build -t "${WEBGL_SPIN_HARNESS_IMAGE}" --build-arg BASE_IMAGE="${WEBGL_HARNESS_IMAGE}" \
        -f "${SCRIPT_DIR}/webgl-spin-harness.Dockerfile" "${SCRIPT_DIR}"
fi

# The Firefox image (separate from the Electron stack above) and its geckodriver
# test layer. The Firefox base reuses the same extra-cas/ proxy-CA forwarding.
if wants firefox; then
    prepare_extra_cas
    build -t "${FIREFOX_BASE_IMAGE}" -f "${REPO_ROOT}/Containerfile.firefox" "${REPO_ROOT}"
fi

if wants firefox-harness; then
    build -t "${FIREFOX_HARNESS_IMAGE}" --build-arg BASE_IMAGE="${FIREFOX_BASE_IMAGE}" \
        -f "${SCRIPT_DIR}/firefox-harness.Dockerfile" "${SCRIPT_DIR}"
fi

echo ">> done: ${TARGETS[*]}"
