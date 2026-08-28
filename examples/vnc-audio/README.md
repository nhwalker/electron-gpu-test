# VNC + audio examples

Two runnable answers to "how does sound get from the remote desktop into the
noVNC page", so we can pick one before building it into the app.

RFB carries no audio and noVNC has never implemented any, so audio is a second
stream whichever way we go — the same conclusion Apache Guacamole reached, where
`enable-audio` opens a second connection to PulseAudio alongside the VNC one.
These images are the server half of that second stream, plus a small browser
client for each, so the two can be heard and measured side by side.

```
Containerfile.vnc-server   Xvfb + x11vnc + PipeWire, a null sink, a test tone
   ├── Containerfile.audio-a1    + raw PCM  on tcp/5901   (no encoder)
   └── Containerfile.audio-a2    + Opus     on tcp/5901   (~1/15 the bytes)
```

Each audio image also serves its own demo page on `:8080` so you can hear it in
a plain browser. The picture stays on VNC `:5900` — point the Electron app (or
any VNC client) at it while the demo page plays the sound.

## The base image

`Containerfile.vnc-server` is the "server we control": a desktop on Xvfb
published by x11vnc, and — the part that matters — a **null sink** in PipeWire.
A null sink is a sound device with no hardware behind it; everything played on
the desktop mixes into it, and `vnc0.monitor` is the source that reads that mix
back. That monitor is the single tap every audio option captures from, so the
option only decides what happens *after* it.

PipeWire provides the PulseAudio API here (`pipewire-pulseaudio` conflicts with
the classic daemon, so it is one or the other). That choice is invisible
downstream: `pulsesrc device=vnc0.monitor` is the same line either way.

A 440 Hz sine plays into the sink at startup so a fresh container makes sound
with nothing else running (`-e TONE_WAVE=silence` to stop it, `-e TONE_HZ=…`,
`-e TONE_WAVE=ticks|pink-noise|…` for something less shrill). Anything else you
run on the display mixes in on top.

Adding a transport is one file: the entrypoint runs every executable in
`/etc/vnc-audio.d/` after the sound server is up and before it execs x11vnc.
That hook is the entire server-side difference between the two options below.

## Option A1 — raw PCM

```
pulsesrc vnc0.monitor ! audioconvert ! audioresample
                      ! audio/x-raw,format=S16LE,rate=48000,channels=2
                      ! tcpserversink :5901
```

No encoder, no container, no framing: byte `2n` of the stream is a sample. The
client hardcodes the format (it is fixed by those caps) and the only wrinkle is
that a byte stream has no packet boundaries, so a WebSocket message can end
mid-sample and the remainder has to be carried into the next one.

- **On the wire:** 1.5 Mbit/s, constant, whether or not anything is making noise.
- **Client:** `demo/a1/client.js`, ~40 lines. No codec APIs at all.
- **Server:** one pipeline. You could do it with `parec | socat` and no
  GStreamer.

## Option A2 — Opus

```
pulsesrc vnc0.monitor ! audioconvert ! audioresample
                      ! opusenc bitrate=96000 frame-size=20
                      ! rtpopuspay pt=96 ! rtpstreampay
                      ! tcpserversink :5901
```

Each 20 ms Opus frame goes out as `[u16 big-endian length][RTP packet]`. The
framing is not bespoke — `rtpstreampay` is RFC 4571, the standard way to put RTP
on a byte stream — and it buys the client a packet boundary, a sequence number
and a 48 kHz timestamp for nothing. WebCodecs' `AudioDecoder` takes the RTP
payload directly: raw Opus packets need no `description`, so there is no
container and no demuxer anywhere.

- **On the wire:** ~100 kbit/s at the default bitrate, and it drops toward
  nothing during silence.
- **Client:** `demo/a2/client.js`, ~100 lines — deframe, parse RTP, decode.
- **Server:** one longer pipeline, plus `gstreamer1-plugins-bad-free` for
  `opusenc`.
- **Bonus:** the same payload format a WebRTC path would carry later, so the
  work is not wasted if we escalate.

## What is shared

Everything after "we have samples" is identical, and lives in `demo/common/`:

- **`pcm-worklet.js`** — a ring buffer on the audio thread. This is the piece
  neither option lets you skip. Audio arrives on the network's schedule and
  leaves on the sound card's, and the two clocks differ by tens of ppm, so it
  buffers to a target depth (120 ms), starts, and nudges the depth back when it
  drifts. The correction here drops a chunk when too deep — audible as a faint
  click every few minutes; a production version would resample instead.
- **`pcm-player.js`** — the `AudioContext`/worklet wiring, plus an
  `AnalyserNode` so the page can show what is actually playing.
- **`ui.js`** — Start button, status, stats table.

So the choice really is only: **do we want a codec in the path, and are we
willing to write ~60 more lines of client to get 15× the bandwidth back.**

## Build and run

```sh
examples/vnc-audio/build.sh            # all three images (podman or docker)

# Option A1
podman run --rm -p 5900:5900 -p 5901:5901 -p 8080:8080 vnc-audio:audio-a1
# Option A2
podman run --rm -p 5900:5900 -p 5901:5901 -p 8080:8080 vnc-audio:audio-a2
```

Then open <http://localhost:8080/>, press **Start audio** (Chromium will not let
a page make noise without a gesture), and watch the stats. For the picture:

```sh
electron-gpu-test 'vnc://:s3cret@localhost:5900'
```

Knobs: `VNC_PASSWORD`, `TONE_WAVE`, `TONE_HZ`, `AUDIO_PORT`, `DEMO_PORT`,
`OPUS_BITRATE`, `OPUS_FRAME_SIZE`, `SCREEN_SIZE`.

## Measured

Both clients were driven in headless Chromium against a stand-in server that
replays the exact wire format of each pipeline (with WebSocket messages split at
deliberately unaligned offsets), the A2 stream being real Opus packets produced
by Chromium's own encoder:

| | A1 (raw PCM) | A2 (Opus) |
| --- | --- | --- |
| wire rate | 1535 kbit/s | 102 kbit/s |
| buffered | 71 ms | 83 ms |
| underruns / drift corrections | 0 / 0 | 0 / 0 |
| tone recovered | 440 Hz ✓ | 440 Hz ✓ |
| client lines | ~40 | ~100 |

The 440 Hz check is the useful one: the page runs an FFT on what reaches the
speakers, so a peak at 440 Hz means the tone survived capture, transport,
decode, the ring buffer and playback. It is also exactly the assertion a
functional test would make, the same way the WebGL tests assert on pixels.

## What is not verified

- **Neither image has been built.** There is no container runtime in the
  environment these were written in, so the pipelines, package names and the
  entrypoint have been reviewed but not run. Most likely snags, in order:
  `x11vnc` and `python3-websockify` come from EPEL; `opusenc` lives in
  `gstreamer1-plugins-base` on some EL9 builds and `gstreamer1-plugins-bad-free`
  on others, so A2 installs the latter and both images assert their elements
  exist with `gst-inspect-1.0` at **build** time rather than failing at run time.
- The demo pages were verified against a stand-in, not against the real
  pipelines, so caps negotiation and `tcpserversink` behaviour under a slow
  client are untested.

## If we adopt one

The app changes are the same shape for either option: `app/vnc.js` opens a
second TCP connection to port 5901 alongside the RFB one and exposes it as a
second WebSocket on the same loopback origin, under the same token and the same
Host/Origin checks — `connect-src` in the viewer's CSP already allows it. Then
`demo/common/*` and the winning `client.js` move into `app/vnc-viewer/`, with a
mute/volume control on the toolbar.

Two things to decide with it:

- **Autoplay.** Either keep a click-to-start control, or add
  `--autoplay-policy=no-user-gesture-required` to `launch.sh` so a kiosk window
  comes up already making sound.
- **Auth.** The audio port is a second door that the VNC password does not
  cover: anything that can reach 5901 hears the desktop. Options are an SSH
  tunnel, or — reusing what the image already has — having the main process
  `tls.connect` with the client certificate `setup-certs.sh` imports.

## Files

```
examples/vnc-audio/
├── Containerfile.vnc-server   base: desktop, x11vnc, PipeWire, null sink, tone
├── Containerfile.audio-a1     + raw PCM transport + A1 demo client
├── Containerfile.audio-a2     + Opus transport + A2 demo client
├── build.sh                   builds all three, in order
├── server/
│   ├── entrypoint.sh          X -> dbus -> pipewire -> sink -> tone -> hooks -> x11vnc
│   ├── audio-a1.sh            hook: the raw PCM pipeline + websockify
│   └── audio-a2.sh            hook: the Opus pipeline + websockify
└── demo/
    ├── common/                shared: ring buffer, player, UI shell, styles
    ├── a1/client.js           A1's whole browser side
    └── a2/client.js           A2's whole browser side
```
