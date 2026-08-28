# electron-gpu-test

A minimal Electron 41.1.1 (Chromium 146) app packaged into a UBI9 container,
configured for **NVIDIA hardware video decode** (including the WebRTC decode
path) via the `nvidia-vaapi-driver` VAAPI->NVDEC bridge.

See [`plan.md`](./plan.md) for the full rationale, caveats, and verification
steps.

## Layout

- `app/` — the Electron app and all Node/npm files
  - `main.js` — opens one window per URL passed on the command line
  - `vnc.js` — serves the noVNC viewer and bridges it to a VNC server's TCP port
  - `vnc-viewer/` — the noVNC-backed page a `vnc://` URL opens in, and the
    Opus/WebCodecs desktop-audio client alongside it
  - `package.json` — pins `electron@41.1.1`, `@novnc/novnc` and `ws`
  - `launch.sh` — the launch wrapper with all the GPU/Ozone switches
  - `setup-certs.sh` — imports runtime-mounted TLS certs into the NSS DB (sourced by `launch.sh`)
- `tests/` — Node tests for the parts that need no display or container (`node --test tests/vnc.test.js`)
- `Containerfile` — builds the image

## The app

It's deliberately tiny: it opens the web pages named as CLI arguments, one
window each. Any argument matching `http(s)://` or `file://` is opened; Chromium
switches are ignored. With no URL it falls back to the WebRTC samples page.

A `vnc://host:port` argument works too — it opens the remote desktop in a
[noVNC](https://novnc.com/)-backed page the app serves itself. See
[Remote desktops over VNC](#remote-desktops-over-vnc-vnc) below.

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

## GPU rendering knobs

`launch.sh` probes for a GPU and, when one is present, launches on the ANGLE
**GL** backend with NVIDIA VAAPI hardware **decode** enabled. This is the proven
default and is all most uses need.

- `FORCE_HARDWARE=1` — skip the probe and assume a GPU is present.
- `FORCE_SOFTWARE=1` — force software rendering (no GPU path).
- `SOFTWARE_WEBGL=1` — software rendering that still provides WebGL via SwiftShader
  (only relevant when there's no GPU, e.g. CI).
- `GPU_FULL=1` — **opt-in**: on top of VAAPI decode, also turn on **Vulkan,
  WebGPU and Skia Graphite** by switching ANGLE to the Vulkan backend. This makes
  almost every `chrome://gpu` row report "hardware accelerated".

What `GPU_FULL=1` does and does not get you:

- ✅ Hardware for Canvas, Compositing, Rasterization, OpenGL, WebGL/WebGL2,
  Vulkan, WebGPU, Skia Graphite, and Video Decode.
- ❌ **Video Encode** stays software no matter what — the `nvidia-vaapi-driver`
  bridge is decode-only, and Chromium on Linux only does hardware encode through
  VAAPI (it never drives NVENC). This row cannot go green on this stack.
- ⚠️ The VAAPI zero-copy decode path was validated on the ANGLE **GL** backend;
  on the Vulkan backend decode may regress. After enabling `GPU_FULL=1`, confirm
  decode still works in `chrome://media-internals`. If it doesn't, drop the flag
  and keep the default GL path.

`GPU_FULL=1` also requires the Vulkan loader (`vulkan-loader`, baked into the
image) and the NVIDIA Vulkan ICD, which the Container Toolkit injects at runtime
because `NVIDIA_DRIVER_CAPABILITIES=all` includes the graphics capability.

```sh
podman run --rm --device nvidia.com/gpu=all \
  -e OZONE=x11 -e DISPLAY="$DISPLAY" -e GPU_FULL=1 \
  -v /tmp/.X11-unix:/tmp/.X11-unix:ro \
  -v "$XAUTHORITY":/home/app/.Xauthority:ro -e XAUTHORITY=/home/app/.Xauthority \
  electron-gpu-test chrome://gpu
```

## Remote desktops over VNC (`vnc://`)

Pass a `vnc://` URL and the app opens that remote desktop in a
[noVNC](https://novnc.com/) viewer page it serves itself — no websockify, no
separate gateway, no browser plugin.

```sh
podman run --rm --device nvidia.com/gpu=all \
  -e OZONE=x11 -e DISPLAY="$DISPLAY" \
  -v /tmp/.X11-unix:/tmp/.X11-unix:ro \
  -v "$XAUTHORITY":/home/app/.Xauthority:ro -e XAUTHORITY=/home/app/.Xauthority \
  -e VNC_PASSWORD=hunter2 \
  electron-gpu-test vnc://desktop.example.test:5901
```

VNC URLs mix freely with web URLs — each argument gets its own window:

```sh
electron-gpu-test https://webrtc.github.io/samples/ vnc://desktop.example.test:5901
```

### The URL

```
vnc://[user[:password]@]host[:port|:display][?option=value&...]
```

- **Port.** Omitted, it's the RFB default `5900`. A number of 99 or less is read
  as a *display number* the way VNC clients traditionally do it, so
  `vnc://host:1` is display `:1`, i.e. TCP 5901. Anything larger is a TCP port.
- **Password.** `VNC_PASSWORD` (and `VNC_USERNAME`) in the environment is the
  form to prefer: a password in the URL is visible to anything that can read the
  container's process list. Both the `user:password@` userinfo and
  `?password=` also work, and win over the environment. With no password at all,
  a server that wants one prompts in the page.
- **Authentication types** are whatever noVNC supports: None, VNC password,
  VeNCrypt (Plain), RA2ne, Tight, ARD and MS-Logon II. The VeNCrypt subtypes
  that wrap the RFB stream in TLS are not among them — a browser can't do TLS
  inside a stream it doesn't own.

Options, as query parameters:

| Option | Default | What it does |
| ------ | ------- | ------------ |
| `resize` | `scale` | `scale` fits the remote screen to the window, `remote` asks the server to resize its own framebuffer to the window, `off` shows it 1:1 and crops. |
| `view_only` | `0` | `1` sends no input at all — watch without touching. |
| `shared` | `1` | `0` asks the server to disconnect other clients (the RFB shared flag). |
| `quality` | server's | Tight/JPEG quality, `0`–`9`. |
| `compression` | server's | zlib compression level, `0`–`9`. |
| `reconnect` | `1` | `0` stops the viewer reconnecting after an unexpected drop. |
| `grab_keys` | `1` | `0` lets the app's menu keep its accelerators (`Ctrl+R`, `Ctrl+W`, `F11`, …) instead of passing those keys to the remote desktop. |
| `audio` | `off` | `on` streams the desktop's sound from the conventional port (5901), or name another port. See below. |
| `audio_latency` | `120` | How many milliseconds of audio to keep buffered. Lower is more responsive; higher rides out bigger hiccups. |
| `audio_channels` | `2` | `1` for a mono stream. Must match what the server encodes. |
| `title` | — | Window title, instead of the desktop name the server reports. |
| `username`, `password` | — | Credentials, for the auth types that use them. |

```sh
# Watch a lab machine, unscaled, without touching it:
electron-gpu-test 'vnc://lab-01:5901?view_only=1&resize=off'
```

### Desktop audio

RFB carries no audio, so sound is a second stream on a second port — the same
shape Guacamole uses, and the app bridges it exactly like the first one:

```sh
electron-gpu-test 'vnc://desktop.example.test:5901?audio=on'
```

The server side is one GStreamer pipeline, which
[`examples/vnc-audio`](./examples/vnc-audio) packages as a runnable image:

```
pulsesrc <sink>.monitor ! audioconvert ! audioresample
                        ! opusenc ! rtpopuspay ! rtpstreampay
                        ! tcpserversink :5901
```

That is 20 ms Opus frames, each in an RTP packet, length-prefixed per RFC 4571
— the standard way to put RTP on a byte stream. The viewer deframes it, decodes
with WebCodecs' `AudioDecoder` (raw Opus packets need no container and no
demuxer) and plays the samples through an `AudioWorklet` ring buffer. About
100 kbit/s, against 1.5 Mbit/s for the same audio uncompressed.

- The **toolbar's audio button** mutes and unmutes, and shows the stream's state:
  dimmed while it is connecting or dropped, highlighted if the browser is
  waiting for a click before it will make sound.
- **Clock drift** is the thing that decides whether this stays working: a server
  capturing at "48000 Hz" and a browser playing at "48000 Hz" differ by tens of
  ppm, which is a buffer that empties or fills over minutes. The ring buffer
  holds `audio_latency` ms and skips forward when it drifts too deep — audible
  as a faint click, rarely, and only where the alternative was a dropout.
- The audio connection **reconnects on its own** and is independent of the
  picture: the sound server can restart without disturbing the RFB session, and
  a VNC reconnect does not interrupt the audio.
- **Autoplay:** Chromium will not let a page make noise before a user gesture,
  and nothing clicks in a container, so `launch.sh` passes
  `--autoplay-policy=no-user-gesture-required`. Drop that switch if you would
  rather click first — the audio button doubles as the gesture.
- **A/V sync is approximate.** RFB has no timestamps, so there is no shared
  clock to sync the picture to; both streams are simply kept low-latency.
- **The audio port is a second door**, and the VNC password does not cover it:
  anything that can reach it hears the desktop. Tunnel it (SSH, WireGuard) on an
  untrusted network.

### How it works

A browser engine can't open a TCP socket, and a VNC server doesn't speak
WebSocket. noVNC bridges that gap the way `websockify` does — except the bridge
is the app's own main process, so nothing else has to be deployed:

```
BrowserWindow                     main process                    VNC server
--------------------------        ----------------------------    ------------
http://127.0.0.1:PORT/      <-->  loopback HTTP server
viewer.html + noVNC               (serves the viewer + noVNC)
RFB over WebSocket          <-->  /ws/<token> bridge          <-->  TCP host:5900
Opus over WebSocket         <-->  /audio/<token> bridge       <-->  TCP host:5901
```

The relay is byte-for-byte — no RFB parsing happens in the app — so every
encoding, authentication scheme and extension noVNC supports keeps working.

Notes on how it's contained:

- The viewer server binds **127.0.0.1 on an ephemeral port** and is reachable
  only with an unguessable token minted for a URL that was named on the command
  line. Both of a session's streams live behind that one token — `/ws/<token>`
  for the picture and `/audio/<token>` for the sound, which resolves to nothing
  at all unless the URL asked for audio. It checks the `Host` header (against DNS rebinding) and the WebSocket
  handshake's `Origin`, so a remote page loaded in another window of the app
  can't open a bridge. The page itself runs under a CSP that allows no remote
  code and no connection but its own bridge.
- The password reaches the page over that loopback connection, fetched by token
  — never in the window's URL, title or history.
- Each remote host gets **its own persistent storage partition**, keyed on the
  host and port rather than on the loopback port, which changes every launch.
- **Keyboard.** By default the viewer suppresses the app's menu accelerators
  while it has focus, so `Ctrl+R` and `Ctrl+W` reach the remote desktop instead
  of reloading or closing the session (`grab_keys=0` to opt out).
- **Clipboard** syncs remote → local only. The other direction needs a browser
  paste event, which noVNC swallows while it holds keyboard focus.
- The bridged connection is **as secure as RFB itself**, i.e. not very: the
  standard VNC password auth protects the password, not the session. Across an
  untrusted network, tunnel it (SSH, WireGuard, a service mesh) and point the
  URL at the tunnel's local end.

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
