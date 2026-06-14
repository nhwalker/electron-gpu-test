#!/usr/bin/env bash
# build-rpm.sh - Build the electron-gpu-test RPM.
#
# Vendors the app's runtime first (npm install fetches the prebuilt Electron),
# tars the self-contained app tree as the spec's Source0, then runs rpmbuild. The
# spec itself does no network/npm work -- so rpmbuild only needs rpm-build, while
# THIS script needs nodejs + npm (to vendor) and rpm-build (to package).
#
# Usage:   packaging/build-rpm.sh [OUTPUT_DIR]
# Output:  the built .rpm is copied to OUTPUT_DIR (default: packaging/dist/).
#
# Build it in an environment that has the tools, e.g. on the shared base image:
#   docker run --rm -v "$PWD":/src -w /src browser-base:undertest bash -c \
#     'dnf -y install nodejs npm rpm-build >/dev/null && packaging/build-rpm.sh'
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
SPEC="${SCRIPT_DIR}/electron-gpu-test.spec"
OUT_DIR="${1:-${SCRIPT_DIR}/dist}"

for tool in npm rpmbuild; do
    command -v "$tool" >/dev/null 2>&1 || {
        echo "error: '$tool' not found. Install nodejs/npm + rpm-build first." >&2
        exit 1
    }
done

NAME="$(npm pkg get name --prefix "${REPO_ROOT}/app" | tr -d '"')"
VERSION="$(npm pkg get version --prefix "${REPO_ROOT}/app" | tr -d '"')"
echo ">> building ${NAME}-${VERSION}.rpm"

WORK="$(mktemp -d)"
trap 'rm -rf "${WORK}"' EXIT
TOPDIR="${WORK}/rpmbuild"
mkdir -p "${TOPDIR}"/{SOURCES,SPECS,BUILD,RPMS,SRPMS}

# --- Vendor the app tree: copy sources, then npm install the Electron runtime ---
SRCROOT="${WORK}/${NAME}-${VERSION}"
mkdir -p "${SRCROOT}"
cp "${REPO_ROOT}/app/main.js" "${REPO_ROOT}/app/package.json" \
   "${REPO_ROOT}/app/launch.sh" "${REPO_ROOT}/app/setup-certs.sh" "${SRCROOT}/"
echo ">> npm install (fetches the prebuilt Electron runtime)"
( cd "${SRCROOT}" && npm install --omit=optional --no-audit --no-fund && npm cache clean --force >/dev/null 2>&1 || true )

# --- Source tarball -> rpmbuild --------------------------------------------------
tar -C "${WORK}" -czf "${TOPDIR}/SOURCES/${NAME}-${VERSION}.tar.gz" "${NAME}-${VERSION}"
echo ">> rpmbuild -bb"
rpmbuild -bb --define "_topdir ${TOPDIR}" "${SPEC}"

mkdir -p "${OUT_DIR}"
find "${TOPDIR}/RPMS" -name '*.rpm' -exec cp -v {} "${OUT_DIR}/" \;
echo ">> done. RPM(s) in ${OUT_DIR}:"
ls -1 "${OUT_DIR}"/*.rpm
