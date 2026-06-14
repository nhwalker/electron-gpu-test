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
- `Containerfile` — builds the image

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

- **`firefox/policies.json`** — feature lockdowns and toolbar visibility. Ships
  with `DisablePrivateBrowsing`, `DisplayBookmarksToolbar: "always"`, and a few
  telemetry/update/first-run lockdowns. Add any key from the
  [Firefox policy templates](https://mozilla.github.io/policy-templates/) here
  (e.g. `DisableDeveloperTools`, `BlockAboutConfig`, a locked `Homepage`).

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
