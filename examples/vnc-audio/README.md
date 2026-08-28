# VNC + audio examples

Three runnable answers to "how does sound get from the remote desktop into the
noVNC page", so we can pick one before building it into the app.

RFB carries no audio and noVNC has never implemented any, so audio is a second
stream whichever way we go — the same conclusion Apache Guacamole reached, where
`enable-audio` opens a second connection to PulseAudio alongside the VNC one.
These images are the server half of that second stream, plus a small browser
client for each, so the two can be heard and measured side by side.

```
Containerfile.vnc-server   Xvfb + x11vnc + PipeWire, a null sink, a test tone
   ├── Containerfile.audio-a1    + raw PCM      on tcp/5901  (no encoder)
   ├── Containerfile.audio-a2    + Opus in RTP  on tcp/5901  (~1/15 the bytes)
   └── Containerfile.audio-a3    + Opus in WebM on tcp/5901  (the browser plays it)
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

## Option A3 — Opus in WebM

```
pulsesrc vnc0.monitor ! audioconvert ! audioresample
                      ! opusenc bitrate=96000 frame-size=20
                      ! webmmux streamable=true
                      ! (one pipeline per client, via socat)
```

The same Opus frames as A2, in a WebM container instead of RTP packets.
`streamable=true` is the whole trick: webmmux then emits an initialisation
segment followed by an open-ended run of clusters, with no seek index or
duration to backfill — exactly what MediaSource wants, and exactly what a
file-shaped WebM is not.

The browser end is the smallest of the three: an `<audio>` element and a
`SourceBuffer`. No framing to parse, no codec API, no ring buffer — the browser
demuxes, decodes, buffers and clocks it.

What that buys is paid back in two places:

- **MediaSource is built for video-on-demand, not for live.** Nothing stops the
  playhead drifting further and further behind the newest audio, and there is no
  "catch up": the client has to watch the live edge and *seek* forward, which is
  audible, and evict what it has played or the buffer grows for the whole
  session. In the demo the playhead sits ~280 ms behind live and takes a
  correction within the first few seconds — against a steady 71–83 ms for the
  other two.
- **A container needs its header.** A client that joins mid-stream cannot parse
  anything until it has the initialisation segment, and then has to land on a
  cluster boundary. webmmux does publish a streamheader that multifdsink-style
  sinks resend to late joiners, but the example takes the simpler route and gives
  every connection its own pipeline (that is what socat's `fork` is doing) — at
  the cost of one encoder per viewer, where A1 and A2 fan one stream out to
  everybody.

- **On the wire:** ~108 kbit/s — the Opus payload plus WebM framing overhead.
- **Client:** `demo/a3/client.js`, ~110 lines, of which the transport is about
  fifteen and the rest is staying live.
- **Server:** one pipeline plus socat, and `gstreamer1-plugins-bad-free` for
  `opusenc`.

## What is shared

`demo/common/` holds everything that is not the option itself:

- **`ui.js`** — Start button, status, stats table. All three use it; none of them
  tell it anything about how they make sound.
- **`spectrum.js`** — the one shared measurement: the loudest frequency coming
  out of the speakers, so "the test tone survived the trip" reads the same way
  for all three.
- **`pcm-worklet.js` + `pcm-player.js`** — the ring buffer on the audio thread,
  used by **A1 and A2 only**. Audio arrives on the network's schedule and leaves
  on the sound card's, and the two clocks differ by tens of ppm, so it buffers to
  a target depth (120 ms), starts, and nudges the depth back when it drifts. The
  correction drops a chunk when too deep — audible as a faint click every few
  minutes; a production version would resample instead. **A3 has none of this**:
  it is the browser's problem, which is the option's whole appeal — and its
  live-edge seeking is the same problem coming back in a coarser form.

Each option's `client.js` is therefore a complete statement of it, and the three
files are directly comparable.

So the choice is roughly: **A1** if we want no codec and can spend the
bandwidth; **A2** if we want the tightest control over latency for ~60 more
lines; **A3** if we would rather the browser owned playback and can live with a
few hundred milliseconds and the odd audible seek.

## Build and run

```sh
examples/vnc-audio/build.sh            # all four images (podman or docker)

# One option at a time -- they use the same ports, so they are swappable.
podman run --rm -p 5900:5900 -p 5901:5901 -p 8080:8080 vnc-audio:audio-a1
podman run --rm -p 5900:5900 -p 5901:5901 -p 8080:8080 vnc-audio:audio-a2
podman run --rm -p 5900:5900 -p 5901:5901 -p 8080:8080 vnc-audio:audio-a3
```

Then open <http://localhost:8080/>, press **Start audio** (Chromium will not let
a page make noise without a gesture), and watch the stats. For the picture:

```sh
electron-gpu-test 'vnc://:s3cret@localhost:5900'
```

Knobs: `VNC_PASSWORD`, `TONE_WAVE`, `TONE_HZ`, `AUDIO_PORT`, `DEMO_PORT`,
`OPUS_BITRATE`, `OPUS_FRAME_SIZE`, `SCREEN_SIZE`.

## Measured

All three clients were driven in headless Chromium against a stand-in server
that replays the exact wire format of each pipeline, with WebSocket messages
split at deliberately unaligned offsets. The A2 stream is real Opus packets
produced by Chromium's own encoder; the A3 stream is a real WebM/Opus byte
stream (initialisation segment, then clusters) produced by its MediaRecorder:

| | A1 (raw PCM) | A2 (Opus in RTP) | A3 (Opus in WebM) |
| --- | --- | --- | --- |
| wire rate | 1535 kbit/s | 102 kbit/s | 108 kbit/s |
| latency held | 71 ms buffered | 83 ms buffered | 283 ms behind live |
| corrections | 0 | 0 | 1 live-edge seek |
| underruns | 0 | 0 | n/a (browser-managed) |
| tone recovered | 440 Hz ✓ | 440 Hz ✓ | 441 Hz ✓ |
| client lines | ~40 | ~100 | ~110 |
| encoders for N viewers | 1 | 1 | N |

The 440 Hz check is the useful one: the page runs an FFT on what reaches the
speakers, so a peak at 440 Hz means the tone survived capture, transport,
decode, buffering and playback. (A3 reads 441 Hz only because its `AudioContext`
runs at the device's own rate rather than a pinned 48 kHz — the browser owns that
choice too.) It is also exactly the assertion a
functional test would make, the same way the WebGL tests assert on pixels.

## What is not verified

- **None of the images have been built.** There is no container runtime in the
  environment these were written in, so the pipelines, package names and the
  entrypoint have been reviewed but not run. Most likely snags, in order:
  `x11vnc` and `python3-websockify` come from EPEL; `opusenc` lives in
  `gstreamer1-plugins-base` on some EL9 builds and `gstreamer1-plugins-bad-free`
  on others, so A2 and A3 install the latter, and every image asserts its
  elements exist with `gst-inspect-1.0` at **build** time rather than failing at
  run time. A3 also needs `socat`.
- The demo pages were verified against a stand-in, not against the real
  pipelines, so caps negotiation, `tcpserversink` behaviour under a slow client,
  and whether `webmmux streamable=true` output is byte-for-byte as agreeable to
  MediaSource as MediaRecorder's is, are all untested.

## If we adopt one

The app changes are the same shape for any of the three: `app/vnc.js` opens a
second TCP connection to port 5901 alongside the RFB one and exposes it as a
second WebSocket on the same loopback origin, under the same token and the same
Host/Origin checks — `connect-src` in the viewer's CSP already allows it. Then the winning `client.js` moves into `app/vnc-viewer/` (with
`pcm-worklet.js`/`pcm-player.js` if it is A1 or A2), with a mute/volume control
on the toolbar.

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
├── Containerfile.audio-a2     + Opus-in-RTP transport + A2 demo client
├── Containerfile.audio-a3     + Opus-in-WebM transport + A3 demo client
├── build.sh                   builds all four, in order
├── server/
│   ├── entrypoint.sh          X -> dbus -> pipewire -> sink -> tone -> hooks -> x11vnc
│   ├── audio-a1.sh            hook: the raw PCM pipeline + websockify
│   ├── audio-a2.sh            hook: the Opus/RTP pipeline + websockify
│   ├── audio-a3.sh            hook: socat fan-out + websockify
│   └── audio-a3-pipeline.sh   one client's WebM pipeline, run by socat
└── demo/
    ├── common/                UI shell, spectrum, and the ring buffer A1/A2 share
    ├── a1/client.js           A1's whole browser side
    ├── a2/client.js           A2's whole browser side
    └── a3/client.js           A3's whole browser side
```
