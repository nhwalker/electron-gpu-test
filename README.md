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
