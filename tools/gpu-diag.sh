#!/usr/bin/env bash
# gpu-diag.sh - one-shot NVIDIA VA-API / NVDEC diagnostic for this container.
#
# Run INSIDE the running container on the GPU host and paste the full output
# into the PR/issue. It dumps every layer of the decode stack (kernel driver ->
# injected userspace -> DRM node -> bridge driver -> libva/vainfo) so the
# failing layer is obvious. See GpuDebug.md for how to read the result.
#
# This script is read-only and never changes the system; it only inspects.
set -uo pipefail   # NOT -e: we want every probe to run even if one fails.

RENDER_NODE="${1:-/dev/dri/renderD128}"

hr() { printf '\n== %s ==\n' "$1"; }
run() { echo "\$ $*"; "$@" 2>&1 || echo "  -> command failed (exit $?)"; }

hr "relevant environment"
env | grep -E 'LIBVA|NVD_|NVIDIA_|DISPLAY|WAYLAND|XDG_RUNTIME' | sort \
  || echo "  (none set)"
echo "LIBVA_DRIVERS_PATH=${LIBVA_DRIVERS_PATH:-<unset: libva uses its built-in default>}"

hr "host driver visible? (nvidia-smi)"
run nvidia-smi
run sh -c 'nvidia-smi --query-gpu=driver_version,name --format=csv,noheader'

hr "NVDEC / CUDA userspace injected? (need libnvcuvid.so for decode)"
if ldconfig -p | grep -E 'nvcuvid|libcuda|nvidia-encode'; then :; else
  echo "  NONE FOUND -> 'video' capability likely not injected (see GpuDebug.md 3.3)"
fi

hr "device nodes"
run ls -l /dev/dri/
run sh -c 'ls -l /dev/nvidia*'

hr "nvidia-drm modeset (host kernel setting; 'Y' required for the 'direct' backend)"
run cat /sys/module/nvidia_drm/parameters/modeset

hr "bridge VA-API driver file + which paths libva will search"
run rpm -q libva libva-nvidia-driver
run sh -c 'rpm -ql libva-nvidia-driver 2>/dev/null | grep nvidia_drv_video.so'
run ls -l /usr/lib64/dri/nvidia_drv_video.so
run ls -l /usr/lib64/dri-nonfree/nvidia_drv_video.so

hr "vainfo (verbose: libva + nvidia-vaapi-driver logging), explicit DRM device"
echo "device: $RENDER_NODE"
LIBVA_MESSAGING_LEVEL=2 NVD_LOG=1 vainfo --display drm --device "$RENDER_NODE" 2>&1 \
  || echo "  -> vainfo FAILED (this is expected today; the message above is the key clue)"

hr "done"
echo "Match the vainfo message to the table in GpuDebug.md section 1, then the decision tree in section 2."
