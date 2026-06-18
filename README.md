# electron-gpu-test

A minimal Electron 41.1.1 (Chromium 146) app packaged into a UBI9 container,
configured for **NVIDIA hardware video decode** (including the WebRTC decode
path) via the `nvidia-vaapi-driver` VAAPI->NVDEC bridge.

See [`plan.md`](./plan.md) for the full rationale, caveats, and verification
steps.

## Layout

- `app/` — the Electron app and all Node/npm files
  - `main.js` — opens one window per URL passed on the command line
  - `package.json` — pins `electron@41.1.1`
  - `launch.sh` — the launch wrapper with all the GPU/Ozone switches
  - `setup-certs.sh` — imports runtime-mounted TLS certs into the NSS DB (sourced by `launch.sh`)
- `Containerfile.base` — shared platform base (UBI9 + repos + GPU/VAAPI stack + NSS cert toolchain + fonts) that both browser images build `FROM`
- `Containerfile` — Electron image, `FROM` the base
- `Containerfile.firefox` — Firefox image, `FROM` the base

## The app

It's deliberately tiny: it opens the web pages named as CLI arguments, one
window each. Any argument matching `http(s)://` or `file://` is opened; Chromium
switches are ignored. With no URL it falls back to the WebRTC samples page.

## Build

```sh
podman build -t electron-gpu-test -f Containerfile .
```

## Run (X11 — test this first)

```sh
podman run --rm --device nvidia.com/gpu=all \
  -e OZONE=x11 -e DISPLAY="$DISPLAY" \
  -v /tmp/.X11-unix:/tmp/.X11-unix:ro \
  -v "$XAUTHORITY":/home/app/.Xauthority:ro -e XAUTHORITY=/home/app/.Xauthority \
  electron-gpu-test https://webrtc.github.io/samples/
```

Run (Wayland/Weston) and the rest of the verification flow are documented in
`plan.md` and `app/launch.sh`.

## Persistent web storage (sessions, cookies, cache)

The container is ephemeral, so by default every cookie, `localStorage`,
`IndexedDB`, Cache Storage, and service worker is discarded when the container
stops — pages start logged out with a cold cache each run. To keep them, set
`ELECTRON_USER_DATA` to a path and mount a volume there: `main.js` relocates
Electron's `userData` directory to it, and the storage rides along on the
volume.

```sh
podman run --rm --device nvidia.com/gpu=all \
  -e OZONE=x11 -e DISPLAY="$DISPLAY" \
  -v /tmp/.X11-unix:/tmp/.X11-unix:ro \
  -v "$XAUTHORITY":/home/app/.Xauthority:ro -e XAUTHORITY=/home/app/.Xauthority \
  -e ELECTRON_USER_DATA=/data/profile \
  -v electron-profile:/data/profile \
  electron-gpu-test https://webrtc.github.io/samples/
```

Notes:

- A **named volume** (`electron-profile` above) is kept independently of the
  container and re-attached on the next run, so storage "reloads with the
  container". A host bind-mount (`-v /host/path:/data/profile`) works too if you
  want the files on the host.
- The app runs as the non-root `app` user (uid 1001). The image pre-creates
  `/data/profile` owned by 1001, so a **fresh named volume** mounted there
  inherits that ownership (the runtime copies it into the empty volume on first
  use) and is writable with no extra flags. This copy-up does **not** apply to
  **bind mounts** — for those, make the host path writable by 1001 (e.g.
  `chown 1001 /host/path`, or podman's `:U` mount flag). If storage isn't
  writable, `launch.sh` exits with a fix-it message rather than letting Chromium
  fail obscurely.
- Leaving `ELECTRON_USER_DATA` unset keeps the previous behavior (storage under
  the default `~/.config/electron-gpu-test`, discarded with the container).
- **Per-origin isolation:** each window opens in its own persistent session
  partition keyed by the page's origin, so different sites can't read each
  other's cookies/storage and each remembers its own login independently. This
  is separate from the TLS trust store below.

## Runtime TLS / mutual TLS

To reach internal HTTPS endpoints behind a private CA, or servers that require a
**client certificate** (mutual TLS), mount a directory of PEM files at run time.
At launch `app/setup-certs.sh` scans it and imports everything into the app user's NSS
database (`~/.pki/nssdb`) — the store Chromium/Electron consults for extra trusted
roots and client certificates. No image rebuild is needed.

```sh
podman run --rm --device nvidia.com/gpu=all \
  -e OZONE=x11 -e DISPLAY="$DISPLAY" \
  -v /tmp/.X11-unix:/tmp/.X11-unix:ro \
  -v /path/to/my-certs:/certs:ro \
  electron-gpu-test https://internal.example.test/
```

How the directory is interpreted (scanned recursively, so a flat layout or
`ca/` + `client/` subdirs both work):

- A `*.key` file is a **client private key**; its certificate is the sibling with
  the same name and a cert extension (`client.key` ↔ `client.crt`/`.pem`/`.cert`).
  Each pair is imported as one client identity for mutual TLS.
- Any cert file (`*.crt`/`*.pem`/`*.cert`) **without** a matching `*.key` is
  imported as a **trusted CA**, so HTTPS to hosts using that CA verifies.
- An encrypted client key's passphrase is read from a sibling `*.pass` file, else
  `$TLS_CLIENT_KEY_PASS`, else the key is assumed unencrypted.

Environment knobs:

- `TLS_CERT_DIR` — directory to scan (default `/certs`).
- `TLS_CLIENT_KEY_PASS` — fallback passphrase for encrypted client keys.
- `TLS_INSECURE_SKIP_VERIFY=1` — **dev/test only**, trust any server cert. Off by
  default; prefer importing the real CA above.

This is distinct from the build-time `extra-cas/` mechanism, which only makes the
image *build* (dnf/npm) trust a TLS-intercepting proxy.

## Firefox image (same cert loading)

`Containerfile.firefox` builds a UBI9 image running **Firefox (ESR)** instead of
Electron. It loads runtime-mounted certs **the same way** the Electron app does:
it sources the very same `app/setup-certs.sh`, so the discovery rules, CA-vs-client
auto-pairing, and `cert-store: imported …` log lines are identical. The only
difference is the target NSS DB — Chromium/Electron read `~/.pki/nssdb`, while
Firefox reads `cert9.db`/`key4.db` from its profile, so `firefox/firefox-launch.sh`
points the shared importer there via the new `TLS_NSSDB` override.

```sh
podman build -t firefox-ubi9 -f Containerfile.firefox .

podman run --rm \
  -v /path/to/my-certs:/certs:ro \
  firefox-ubi9 https://internal.example.test/
```

The same env knobs apply (`TLS_CERT_DIR`, `TLS_CLIENT_KEY_PASS`). Firefox-specific
knobs: `FIREFOX_PROFILE` (profile dir, default `~/.mozilla/firefox/container.default`)
and `FIREFOX_KEEP_SANDBOX=1` (re-enable the content sandbox, which needs a
container granted unprivileged user namespaces).

### Firefox configuration (bookmarks toolbar & lockdowns)

Firefox config is driven by **enterprise policies** (`policies.json`) — the
supported way to populate the bookmarks toolbar and lock features. Two small,
editable files in the repo are the source of truth:

- **`firefox/bookmarks.json`** — the bookmarks toolbar, kept in its own file so
  editing it never means touching the larger policy JSON. A plain array:

  ```json
  [
    { "Title": "Example", "URL": "https://example.com", "Placement": "toolbar" },
    { "Title": "Docs", "URL": "https://example.com/docs", "Placement": "toolbar", "Folder": "Reference" }
  ]
  ```
  `Placement` is `toolbar` or `menu`; `Folder` (optional) nests the bookmark.

- **`firefox/policies.json`** — feature lockdowns and toolbar visibility. The
  default is a **kiosk-like security posture** (see below). Add or change any key
  from the [Firefox policy templates](https://mozilla.github.io/policy-templates/)
  here, or replace the whole set at run time (`/config/policies.json`).

#### Default kiosk-like posture

When you don't supply your own `policies.json`, the image ships a locked-down
default suited to an unattended kiosk-style display — a **full browser** (normal
chrome and UI, *not* Firefox's `--kiosk` single-window mode), hardened so a
passer-by can't reconfigure or pivot out of it:

- **No private/anonymous surfaces:** `DisablePrivateBrowsing`, `DisableForgetButton`.
- **No config/diagnostic pivots:** `DisableDeveloperTools`, `BlockAboutConfig`,
  `BlockAboutProfiles`, `BlockAboutSupport`, `DisableSafeMode`,
  `DisableProfileImport`/`DisableProfileRefresh`, `DisableSetDesktopBackground`.
- **No accounts / saved secrets** (shared device): `DisableFirefoxAccounts`,
  `PasswordManagerEnabled: false`, `OfferToSaveLogins: false`,
  `DisableMasterPasswordCreation`, `DisablePasswordReveal`, `DisableFormHistory`.
- **No add-on installs:** `ExtensionSettings` blocks `*`.
- **Quiet & controlled:** `DisableTelemetry`, `DisableFirefoxStudies`,
  `DisablePocket`, `DisableAppUpdate`/`DisableSystemAddonUpdate`,
  `DontCheckDefaultBrowser`, a stripped `FirefoxHome`, suppressed `UserMessaging`,
  and `NoDefaultBookmarks` (the toolbar shows only your managed bookmarks).

Loosen any of these by editing `firefox/policies.json` (rebuild) or mounting a
replacement at `/config/policies.json` (no rebuild).

At build, `firefox/setup-config.sh` merges these into the active `policies.json`
(written to both `/etc/firefox/policies/` and the install's `distribution/` dir).
The merge re-runs at launch, so you can **override at run time with no rebuild**:

```sh
# Swap just the bookmarks toolbar:
podman run --rm -v ./my-bookmarks.json:/config/bookmarks.json:ro firefox-ubi9 https://example.com/
# Or replace the whole policy set:
podman run --rm -v ./my-policies.json:/config/policies.json:ro firefox-ubi9 https://example.com/
```

Env knobs: `FIREFOX_BOOKMARKS` (default `/config/bookmarks.json`) and
`FIREFOX_POLICIES` (default `/config/policies.json`). Verify what Firefox is
actually enforcing by opening `about:policies#active` in the browser.

### GPU acceleration, WebRTC & hardware video decode

Like the Electron image, this carries the NVIDIA VAAPI→NVDEC bridge
(`libva-nvidia-driver`) plus `libavcodec` so Firefox can do **GPU compositing**
and **hardware video decode, including the WebRTC decode path**. The NVIDIA
driver userspace is *not* baked in — it's injected at run time by the NVIDIA
Container Toolkit (CDI), so request a GPU on the run command:

```sh
podman run --rm --device nvidia.com/gpu=all \
  -e DISPLAY="$DISPLAY" -v /tmp/.X11-unix:/tmp/.X11-unix:ro \
  firefox-ubi9 https://webrtc.github.io/samples/
```

`firefox-launch.sh` probes for a GPU (NVIDIA or any DRM render node) and adapts:

- **GPU present** → enables WebRender (`gfx.webrender.all`), VAAPI hardware decode
  (`media.ffmpeg.vaapi.enabled`, `media.hardware-video-decoding.force-enabled`)
  and the WebRTC hardware decode path (`media.navigator.mediadatadecoder_vpx_enabled`);
  selects the NVIDIA VAAPI backend (`LIBVA_DRIVER_NAME=nvidia`, `NVD_BACKEND=direct`)
  and sets `MOZ_DISABLE_RDD_SANDBOX=1` (VAAPI runs in the sandboxed RDD process —
  required for hardware decode to engage in a container) and `MOZ_X11_EGL=1`.
- **No GPU** → falls back to software WebRender; VAAPI is left off (it can't init).
  Override the probe with `FORCE_HARDWARE=1` or `FORCE_SOFTWARE=1`.

WebRTC and WebGL are pinned on (`media.peerconnection.enabled`, `webgl.force-enabled`)
so the locked-down profile can't end up with them disabled.

**Verify** on a GPU host: open `about:support` and check *Compositing = WebRender*
and the *Codec Support Information* table shows *Hardware Decoding = Yes*; run
`vainfo` in the container; or set `MOZ_LOG=PlatformDecoderModule:5` and look for
the VA-API decoder being created while playing video. (The kiosk-default policy
blocks `about:support`; lift `BlockAboutSupport` to read it.)

`FirefoxGpuFunctionalTest` automates this: it reads `about:support` through the
harness and always asserts WebRender compositing + that Firefox accepted the
VAAPI pref. Set `FIREFOX_GPU_TEST=1` on a GPU host (with the harness container
given GPU access) to also assert a codec decodes in hardware; otherwise that
part is skipped so CI stays green.

Caveats:

- **H.264/HEVC hardware decode** needs the full `ffmpeg` (patent codecs). The image
  ships the lean `libavcodec-free`, which covers the royalty-free WebRTC codecs
  (VP8/VP9/AV1). Build with the full codec set via the `FFMPEG_PACKAGES` build-arg
  where RPM Fusion (and its `ladspa` dep) resolve on your host:

  ```sh
  podman build --build-arg FFMPEG_PACKAGES=ffmpeg -t firefox-ubi9 -f Containerfile.firefox .
  # or, via the build script: FIREFOX_FFMPEG_PACKAGES=ffmpeg functional-tests/containers/build-images.sh firefox
  ```
- **WebGL** needs the GPU path (or at least a display + GL driver). Headless with
  no GPU, Firefox has no software-WebGL fallback (unlike Chromium's SwiftShader),
  so WebGL is unavailable there — the no-GPU mode targets page/video/WebRTC.
- **Sending** WebRTC media (camera/mic via `getUserMedia`) needs the relevant
  devices and PipeWire/v4l2 wired in; this image is configured for the common
  display case (receiving/decoding streams).
