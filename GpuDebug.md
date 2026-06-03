# Debugging: WebRTC still uses software (FFmpeg) H.264 decode on a real GPU

**Symptom (as reported):** on a real NVIDIA GPU host, a WebRTC call shows
`FFmpegVideoDecoder` (software) for H.264 in `chrome://media-internals`, and
**`vainfo` inside the container fails.**

**The single most important fact:** `vainfo` failing is the root problem.
`vainfo` is the layer *directly below* Chromium. If `vainfo` cannot bring up
the `nvidia` VA-API driver, then Chromium's `VaapiVideoDecoder` cannot
initialize either, and Chromium silently falls back to its bundled FFmpeg
software decoder. **There is no point looking at `chrome://gpu`,
`chrome://media-internals`, or the launch flags until `vainfo` succeeds.** Fix
`vainfo` first; the Chromium layer is almost certainly fine once it does.

This document works the stack from the bottom up, because that is the only
order in which the evidence is trustworthy.

---

## 0. The stack (know where the break can be)

```
host NVIDIA kernel driver  (nvidia.ko, nvidia-drm.ko [modeset?])
        │   injected at runtime by NVIDIA Container Toolkit (CDI / nvidia runtime)
        ▼
container userspace libs   libcuda.so, libnvcuvid.so  ← NVDEC ("video" capability)
        │
        ▼
DRM render node            /dev/dri/renderD128  (needed by NVD_BACKEND=direct)
        │
        ▼
bridge VA-API driver       /usr/lib64/dri[-nonfree]/nvidia_drv_video.so
        │                  (RPM: libva-nvidia-driver; selected by LIBVA_DRIVER_NAME=nvidia)
        ▼
libva (vaInitialize)  ───────────►  vainfo   ← FAILS HERE TODAY
        │
        ▼
Chromium VaapiVideoDecoder ──► (else) FFmpegVideoDecoder (software)  ← what you see
```

`vainfo` exercises everything from the kernel driver up to `vaInitialize()`.
Any break in the chain shows up as a `vainfo` failure, so its exact error
message tells us which layer is broken. **Get that message first (§1), then
match it to a cause (§3).**

---

## 1. First: capture the *exact* `vainfo` failure (do this before anything else)

Re-run `vainfo` with full diagnostics. The default output is too terse to act
on. Run this **inside the running container**:

```sh
# Make both libva and the nvidia bridge driver verbose.
export LIBVA_MESSAGING_LEVEL=2     # libva: log info + errors
export NVD_LOG=1                   # nvidia-vaapi-driver: print its own debug log
# Be explicit about device + backend so we are not guessing what it tried:
vainfo --display drm --device /dev/dri/renderD128
```

Then capture the surrounding state in one shot (paste the whole output into the
PR / issue):

```sh
echo "== env =="; env | grep -E 'LIBVA|NVD_|NVIDIA_|DISPLAY|WAYLAND' | sort
echo "== nvidia-smi =="; nvidia-smi || echo "nvidia-smi FAILED"
echo "== nvcuvid / cuda libs visible? =="; ldconfig -p | grep -E 'nvcuvid|libcuda|nvidia-encode' || echo "NONE FOUND"
echo "== dri nodes =="; ls -l /dev/dri/ 2>&1; ls -l /dev/nvidia* 2>&1
echo "== bridge driver file =="; ls -l /usr/lib64/dri*/nvidia_drv_video.so 2>&1
echo "== driver search path =="; echo "LIBVA_DRIVERS_PATH=${LIBVA_DRIVERS_PATH:-<unset, libva default>}"
echo "== modeset (informational; this is a host kernel setting) =="; cat /sys/module/nvidia_drm/parameters/modeset 2>&1
echo "== vainfo (verbose) =="; LIBVA_MESSAGING_LEVEL=2 NVD_LOG=1 vainfo --display drm --device /dev/dri/renderD128 2>&1
```

A convenience copy of this lives in `tools/gpu-diag.sh` (added by this change);
run `bash tools/gpu-diag.sh` in the container.

Map the message you get to the matching cause below:

| `vainfo` / libva message (substring)                                   | Most likely cause | Section |
|------------------------------------------------------------------------|-------------------|---------|
| `vaInitialize failed ... unknown libva error` + `nvidia ... init failed` (direct backend) | `direct` backend can't open DRM render node — **nvidia-drm modeset not on**, or no renderD* node | §3.1 |
| `va_openDriver() returns -1`, `/usr/lib64/dri/nvidia_drv_video.so` *not found* | driver `.so` installed under a path libva doesn't search (`dri-nonfree`) | §3.2 |
| driver loads but `cuvid`/`libnvcuvid.so` cannot be resolved / `dlopen` fails | NVDEC userspace not injected — **`video` capability missing** | §3.3 |
| `vainfo: VA-API version ... driver version ...` mismatch, or driver "init failed" with a version note | libva ABI / driver-version mismatch (host driver too old, or RPM built for another version) | §3.4 |
| works with `--display drm` but your earlier run used X11/EGL and failed | wrong display/backend selection | §3.5 |
| `nvidia-smi` itself fails / no `/dev/nvidia*` | GPU not injected into the container at all | §3.6 |

---

## 2. Quick decision tree

```
nvidia-smi works in container?
├── NO  → GPU not injected at all. Go to §3.6 (toolkit / CDI / runtime). STOP here until fixed.
└── YES → libnvcuvid.so visible (ldconfig -p | grep nvcuvid)?
          ├── NO  → "video" capability missing. Go to §3.3.
          └── YES → /dev/dri/renderD128 present?
                    ├── NO  → no render node (modeset off / not injected). Go to §3.1.
                    └── YES → nvidia_drv_video.so on libva's search path?
                              ├── NO  → wrong install path. Go to §3.2.
                              └── YES → run vainfo; read its error and use §3.1 / §3.4 / §3.5.
```

---

## 3. Root causes, ranked, each with options to try

### 3.1 `direct` backend needs `nvidia-drm modeset=1` **and** a DRM render node — MOST LIKELY

`launch.sh`/the image set `NVD_BACKEND=direct`. The `direct` backend talks to
the kernel through a **DRM render node** (`/dev/dri/renderD128`). On a
proprietary-only NVIDIA setup that node **does not exist unless the
`nvidia-drm` kernel module is loaded with `modeset=1` on the host.** No render
node → the `direct` backend can't open the device → `vaInitialize` fails →
`vainfo` fails. This is the classic cause and fits "real GPU, but `vainfo`
fails."

Check (in container):
```sh
ls -l /dev/dri/             # is there a renderD128 at all?
cat /sys/module/nvidia_drm/parameters/modeset   # want: Y  (this reflects the HOST kernel)
```

**Options (pick one):**

- **Option A — enable modeset on the host (preferred for `direct`).** On the
  host, set kernel param `nvidia-drm.modeset=1` (e.g. via
  `/etc/modprobe.d/nvidia.conf`: `options nvidia-drm modeset=1`, then rebuild
  initramfs / reboot, or add `nvidia-drm.modeset=1` to the kernel cmdline).
  Confirm `/dev/dri/renderD128` appears on the host, then make sure the
  container gets it (CDI normally injects it; otherwise add
  `--device /dev/dri/renderD128`). Re-run `vainfo`.

- **Option B — switch the bridge to the `egl` backend.** If you can't change
  the host, set `NVD_BACKEND=egl` and retry. The `egl` backend doesn't need a
  DRM render node but **does** need a usable EGL display, so it generally needs
  a real X/Wayland session (not headless). Caveat: the project chose `direct`
  on purpose (plan.md) because `egl`'s X11 support is fragile and being
  removed — treat `egl` as a *diagnostic* to confirm the rest of the stack
  works, not the final answer.

- **Option C — confirm the render node is actually mapped in.** Even with
  modeset on, verify the container sees the node (`ls -l /dev/dri`). With CDI
  (`--device nvidia.com/gpu=all`) it should; with the legacy `--gpus`/runtime
  path or a hand-rolled `--privileged` run it may not. If missing, add
  `--device /dev/dri:/dev/dri` or regenerate the CDI spec (§3.6).

> Note: you ran `--privileged`. Privileged mode does **not** create the nvidia
> DRM render node for you — it only relaxes cgroup/device permissions. The node
> still has to exist on the host (modeset) and be injected. Privileged can even
> mask the problem by letting you *see* host `/dev` while the node simply isn't
> there.

### 3.2 The driver `.so` is installed where libva doesn't look (`dri-nonfree`)

RPM Fusion has, in some builds, shipped nonfree VA-API drivers under
`/usr/lib64/dri-nonfree/` rather than `/usr/lib64/dri/`. If the `libva` in UBI9
only searches `/usr/lib64/dri`, it never finds `nvidia_drv_video.so` and
`vainfo` reports the driver as not found even though the RPM is installed.

Check:
```sh
rpm -ql libva-nvidia-driver | grep nvidia_drv_video.so   # where did the RPM put it?
ls -l /usr/lib64/dri/nvidia_drv_video.so /usr/lib64/dri-nonfree/nvidia_drv_video.so 2>&1
```

**Options:**

- **Option A — point libva at the right dir.** Set
  `LIBVA_DRIVERS_PATH=/usr/lib64/dri-nonfree:/usr/lib64/dri` (or wherever the
  RPM put it) in the image/launch env and retry `vainfo`.
- **Option B — symlink/copy into the default path** in the Containerfile:
  `ln -s /usr/lib64/dri-nonfree/nvidia_drv_video.so /usr/lib64/dri/` so no env
  is needed at runtime.
- **Option C — bake `LIBVA_DRIVERS_PATH` into the image** as an `ENV` so every
  run is correct, then add it to the launch flags' environment block.

### 3.3 NVDEC userspace (`libnvcuvid.so`) not injected — the `video` capability

The bridge `dlopen`s `libnvcuvid.so` (NVDEC) at init. That library is **only
mounted into the container when the `video` driver capability is requested.**
The image sets `NVIDIA_DRIVER_CAPABILITIES=all` (which includes `video`), but
the *effective* capabilities depend on how you launched the container and on
the CDI spec — an env var in the image can be overridden, and a CDI device may
not carry `video`.

Check:
```sh
ldconfig -p | grep -E 'nvcuvid|nvidia-encode'   # libnvcuvid.so must be listed
echo "NVIDIA_DRIVER_CAPABILITIES=$NVIDIA_DRIVER_CAPABILITIES"
```
If `libnvcuvid.so` is missing, `nvidia-smi` and GL may still work, but decode
can't — exactly your symptom.

**Options:**

- **Option A — request the capability explicitly at run time:**
  `-e NVIDIA_DRIVER_CAPABILITIES=all` (or at minimum `compute,video,graphics,utility`)
  on the `podman/docker run`. Don't rely solely on the image `ENV`.
- **Option B — use a CDI device that includes video.** Regenerate the spec
  (`sudo nvidia-ctk cdi generate --output=/etc/cdi/nvidia.yaml`) and confirm it
  lists `libnvcuvid.so`. Then run with `--device nvidia.com/gpu=all`.
- **Option C — verify the toolkit version.** Older Container Toolkit releases
  handled `video` inconsistently; update `nvidia-container-toolkit` on the host.

### 3.4 Version / ABI mismatch (host driver, libva, or the RPM build)

Three independent version constraints:

1. **Host NVIDIA driver ≥ 470 (525+ recommended)** for NVDEC + the bridge. Check
   `nvidia-smi` top-right driver version.
2. **libva ABI**: the bridge must match the container's `libva`. A
   `VA-API version` vs `driver version` mismatch line in `vainfo` points here.
3. **RPM Fusion `libva-nvidia-driver` build** may target a Fedora libva newer
   than UBI9's, or assume a newer driver than the host has.

Check:
```sh
nvidia-smi --query-gpu=driver_version --format=csv,noheader
rpm -q libva libva-nvidia-driver
vainfo 2>&1 | grep -i version
```

**Options:**

- **Option A — build the bridge from source pinned to a matching version.** The
  repo already documents this fallback (plan.md "Fallback: build the bridge
  from source") using `elFarto/nvidia-vaapi-driver` + `nv-codec-headers`. Build
  against UBI9's exact `libva` so the ABI matches, and pin a driver-compatible
  tag.
- **Option B — bump/downgrade the host driver** to one the bridge supports
  (≥ the version the chosen `nvidia-vaapi-driver` tag targets).
- **Option C — align libva.** If UBI9's libva is older than the RPM expects,
  either rebuild the bridge against UBI9 libva (Option A) or pull a matching
  libva. Avoid mixing a Fedora-built driver with a much older RHEL libva.

### 3.5 Wrong display / backend selection when probing

`vainfo` defaults can probe an X11 or DRM display that isn't the one the GPU is
on. If your first failing `vainfo` used the X11 path, retry the explicit DRM
form before concluding the driver is broken:

```sh
vainfo --display drm --device /dev/dri/renderD128
```
- If `--display drm` **works** but X11 fails: the bridge is fine; the earlier
  failure was display selection. Chromium with `NVD_BACKEND=direct` uses the
  DRM path anyway, so you're likely good — move to §4.
- Multi-GPU: set `NVD_GPU=<index>` (or pick the correct `renderD12N`) so the
  bridge binds to the NVIDIA card and not an iGPU's render node.

### 3.6 GPU not injected into the container at all

If `nvidia-smi` fails or `/dev/nvidia*` is absent, nothing above matters.

Check on the host:
```sh
nvidia-smi                       # host GPU healthy?
nvidia-ctk --version             # toolkit installed?
ls /etc/cdi/ /var/run/cdi/ 2>&1  # CDI spec present?
```

**Options:**

- **Option A — CDI (matches this repo's README):** generate the spec
  (`sudo nvidia-ctk cdi generate --output=/etc/cdi/nvidia.yaml`), validate it,
  and run `--device nvidia.com/gpu=all`.
- **Option B — legacy runtime path:** `--runtime=nvidia` (Docker) or the
  `nvidia` OCI hook (Podman) with `NVIDIA_VISIBLE_DEVICES=all`.
- **Option C — beware `--privileged` interactions.** With Podman, combining
  `--privileged` with the CDI device sometimes changes which hooks run and what
  `/dev` looks like. As a controlled test, drop `--privileged` and use only
  `--device nvidia.com/gpu=all` to get a clean, well-defined injection, then add
  back only what you need.

---

## 4. After `vainfo` succeeds: confirm Chromium actually uses HW H.264

Only meaningful **once `vainfo` lists H264 decode entry points** (look for
`VAProfileH264*` with `VAEntrypointVLD`). If `vainfo` doesn't list H.264, the
bridge/driver can't decode it and Chromium is *correct* to use FFmpeg — then
it's a driver/codec-support question, not a Chromium one.

1. **`chrome://gpu`** → "Video Acceleration Information" should list **Decode**
   profiles incl. H.264. If it says hardware decode is disabled, read the
   "Problems Detected" section.
2. **`chrome://media-internals`** during a WebRTC call → decoder should be a
   hardware/Mojo path (e.g. `VaapiVideoDecoder` / `MojoVideoDecoder`), **not**
   `FFmpegVideoDecoder`.
3. **`chrome://webrtc-internals`** → confirm the inbound H.264 stream and that
   it negotiated H.264 (not VP8/VP9/AV1 — see §5).

Use a **real X server with the NVIDIA driver loaded**, not Xvfb. Under Xvfb
Chromium falls back to software GL (plan.md says this explicitly), which can
itself force software decode and mislead you.

If `vainfo` is green but Chromium still uses FFmpeg, try, one at a time:

- **Flags / features.** `launch.sh` already sets
  `VaapiOnNvidiaGPUs,VaapiIgnoreDriverChecks,AcceleratedVideoDecodeLinuxGL,AcceleratedVideoDecodeLinuxZeroCopyGL`
  and `--use-gl=angle --use-angle=gl --ignore-gpu-blocklist`. Confirm they are
  actually present on the live process: `cat /proc/$(pgrep -f electron | head -1)/cmdline | tr '\0' ' '`.
- **GPU sandbox.** The GPU process may be blocked from reaching NVDEC. The repo
  uses `--no-sandbox`; if you tightened that, the shim can be denied. Confirm
  `--no-sandbox` (or a correctly set-up `--disable-gpu-sandbox`) is in effect.
- **WebRTC-specific decode gate.** WebRTC HW *decode* via VA-API needs
  Chromium ≥ 136 (146 clears it). If a corporate policy or an extra
  `--disable-features=` is stripping it, check `chrome://gpu` "Feature status".
- **H.264 capability of the *call*.** Confirm H.264 is actually being received
  (§5) — if the call silently fell back to VP8/VP9, you'd see software for a
  *different* reason.

---

## 5. H.264-specific gotchas (why "H.264 specifically" can stay software)

Even with a working VA-API NVDEC path, H.264 can land on the software decoder
while other codecs are hardware:

- **Resolution / level below the HW minimum.** NVDEC and Chromium impose a
  minimum decode resolution; tiny test streams (e.g. a low-res webcam tile) are
  routed to software by design. Test with a ≥ 720p H.264 stream.
- **Codec actually negotiated.** WebRTC may have negotiated VP8/VP9/AV1, not
  H.264 — check `chrome://webrtc-internals` `codecId`. If it's not H.264,
  software for H.264 is irrelevant.
- **H.264 profile.** The bridge/NVDEC decodes H.264 High/Main/Constrained
  Baseline. Exotic profiles (e.g. High 4:2:2/4:4:4) fall back to software.
- **Build's H.264 support.** Electron's Chromium ships H.264, but confirm
  `chrome://gpu` lists an H.264 *Decode* profile — if VA-API exposes it but
  Chromium doesn't list it, suspect a blocklist entry (mitigated by
  `--ignore-gpu-blocklist`, already set).

---

## 6. Concrete next actions (shortest path to a verdict)

1. Run `bash tools/gpu-diag.sh` in the container; **paste the full output.** The
   verbose `vainfo` error alone usually identifies the layer.
2. Decision tree (§2): if `nvidia-smi` fails → §3.6; if `libnvcuvid.so` missing
   → §3.3; if no `/dev/dri/renderD128` → §3.1.
3. The highest-probability single fix for "real GPU + `vainfo` fails + `direct`
   backend" is **§3.1** (enable `nvidia-drm.modeset=1` on the host, or set
   `NVD_BACKEND=egl` as a diagnostic).
4. The second-most-likely is **§3.2** (driver `.so` under `dri-nonfree`,
   unfound by libva) — a one-line `LIBVA_DRIVERS_PATH` / symlink fix.
5. Only after `vainfo` lists `VAProfileH264* / VAEntrypointVLD`, move to §4/§5
   for the Chromium side.

---

## 7. Reference: known-good run command (CDI, X11)

```sh
podman run --rm \
  --device nvidia.com/gpu=all \
  -e NVIDIA_DRIVER_CAPABILITIES=all \
  -e OZONE=x11 -e DISPLAY="$DISPLAY" \
  -v /tmp/.X11-unix:/tmp/.X11-unix:ro \
  -v "$XAUTHORITY":/home/app/.Xauthority:ro -e XAUTHORITY=/home/app/.Xauthority \
  --device /dev/dri:/dev/dri \
  electron-gpu-test https://webrtc.github.io/samples/
```

Notes:
- Prefer this minimal CDI form over `--privileged`/`--gpus` while debugging, so
  injection is well-defined (§3.6 Option C).
- `--device /dev/dri:/dev/dri` is belt-and-suspenders for §3.1 Option C.
- To force the bridge backend for a test: add `-e NVD_BACKEND=egl` (§3.1 B) or
  `-e LIBVA_DRIVERS_PATH=/usr/lib64/dri-nonfree:/usr/lib64/dri` (§3.2 A).
- To get the verbose driver log from the app too: `-e NVD_LOG=1 -e LIBVA_MESSAGING_LEVEL=2`.
