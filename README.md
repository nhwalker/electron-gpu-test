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
