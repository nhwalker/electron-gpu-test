#!/usr/bin/env bash
# =============================================================================
# Builds the three example images, in dependency order:
#
#   examples/vnc-audio/build.sh              # all of them
#   examples/vnc-audio/build.sh audio-a2     # just one (base must exist)
#
# Targets:
#   server     the VNC + sound server everything else builds FROM
#   audio-a1   server + raw PCM transport + its demo client
#   audio-a2   server + Opus transport + its demo client
#   audio-a3   server + WebM/Opus transport + its demo client
#
# Tooling: podman or docker, auto-detected (podman preferred, matching the
# project README); override with CONTAINER_TOOL=docker|podman.
#
# Tags default to what the run commands in README.md use, and can be overridden
# with SERVER_IMAGE / AUDIO_A1_IMAGE / AUDIO_A2_IMAGE / AUDIO_A3_IMAGE.
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

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

SERVER_IMAGE="${SERVER_IMAGE:-vnc-audio:server}"
AUDIO_A1_IMAGE="${AUDIO_A1_IMAGE:-vnc-audio:audio-a1}"
AUDIO_A2_IMAGE="${AUDIO_A2_IMAGE:-vnc-audio:audio-a2}"
AUDIO_A3_IMAGE="${AUDIO_A3_IMAGE:-vnc-audio:audio-a3}"

ALL_TARGETS=(server audio-a1 audio-a2 audio-a3)
TARGETS=("$@")
[[ ${#TARGETS[@]} -eq 0 ]] && TARGETS=("${ALL_TARGETS[@]}")

for t in "${TARGETS[@]}"; do
    case "${t}" in
        server|audio-a1|audio-a2|audio-a3) ;;
        *) echo "error: unknown target '${t}' (valid: ${ALL_TARGETS[*]})" >&2; exit 1 ;;
    esac
done

wants() {
    local t
    for t in "${TARGETS[@]}"; do [[ "${t}" == "$1" ]] && return 0; done
    return 1
}

build() {
    echo ">> ${TOOL} build $*"
    "${TOOL}" build "$@"
}

if wants server; then
    build -t "${SERVER_IMAGE}" -f "${SCRIPT_DIR}/Containerfile.vnc-server" "${SCRIPT_DIR}"
fi

if wants audio-a1; then
    build -t "${AUDIO_A1_IMAGE}" --build-arg BASE_IMAGE="${SERVER_IMAGE}" \
        -f "${SCRIPT_DIR}/Containerfile.audio-a1" "${SCRIPT_DIR}"
fi

if wants audio-a2; then
    build -t "${AUDIO_A2_IMAGE}" --build-arg BASE_IMAGE="${SERVER_IMAGE}" \
        -f "${SCRIPT_DIR}/Containerfile.audio-a2" "${SCRIPT_DIR}"
fi

if wants audio-a3; then
    build -t "${AUDIO_A3_IMAGE}" --build-arg BASE_IMAGE="${SERVER_IMAGE}" \
        -f "${SCRIPT_DIR}/Containerfile.audio-a3" "${SCRIPT_DIR}"
fi

echo ">> done: ${TARGETS[*]}"
