# Example VNC server with desktop audio

A server to point the app at, for trying `?audio=on` against something real:

```sh
podman build -t vnc-audio-server -f examples/vnc-audio-server/Containerfile examples/vnc-audio-server
podman run --rm -p 5900:5900 -p 5901:5901 vnc-audio-server

electron-gpu-test 'vnc://:s3cret@localhost:5900?audio=on'
```

You should get the desktop in a window and a 440 Hz tone out of the speakers,
with the toolbar's audio button showing the stream's state.

## What's in it

| port | what |
| ---- | ---- |
| 5900 | RFB, from x11vnc |
| 5901 | the desktop's sound, as Opus packets in RTP, length-prefixed per RFC 4571 |

The sound comes from a PipeWire **null sink** — a device with no hardware behind
it, so everything played on the desktop mixes into it and `vnc0.monitor` is the
source that reads that mix back. That monitor is the single tap the pipeline
captures from, and it is how you would wire this up on a real desktop too:

```
pulsesrc vnc0.monitor ! audioconvert ! audioresample
                      ! opusenc bitrate=96000 frame-size=20
                      ! rtpopuspay pt=96 ! rtpstreampay
                      ! tcpserversink :5901
```

`rtpstreampay` is RFC 4571 — the standard way to put RTP on a byte stream — so
the app gets a packet boundary, a sequence number and a 48 kHz timestamp for
free, and hands each RTP payload (one whole Opus frame) straight to WebCodecs.
No container, no demuxer. About 100 kbit/s, against 1.5 Mbit/s for the same
audio uncompressed.

PipeWire provides the PulseAudio API here (`pipewire-pulseaudio` conflicts with
the classic daemon, so it is one or the other). Nothing downstream cares which
is running: `pulsesrc device=vnc0.monitor` is the same line either way.

A test tone plays into the sink at startup so a fresh container makes sound with
nothing else running. Anything else you run on the display mixes in on top.

## Knobs

`VNC_PASSWORD`, `SCREEN_SIZE`, `AUDIO_PORT`, `SINK_NAME`, `OPUS_BITRATE`,
`OPUS_FRAME_SIZE`, and for the tone: `TONE_WAVE` (`silence` to turn it off, or
`ticks`, `pink-noise`, …), `TONE_HZ`, `TONE_VOLUME`.

## Not verified

**This image has not been built** — there was no container runtime where it was
written, so the package names and the pipeline are reviewed but not run. The
likeliest snags are that `x11vnc` comes from EPEL, and that `opusenc` lives in
`gstreamer1-plugins-base` on some EL9 builds and `gstreamer1-plugins-bad-free`
on others (the image installs both). Every element the pipeline uses is asserted
with `gst-inspect-1.0` at **build** time, so a wrong guess fails the build with a
clear reason instead of producing an image that starts and stays silent.

The equivalent pipeline *is* exercised in CI, on Debian, by the functional
tests' VNC sidecar (`functional-tests/containers/vnc-server.Dockerfile`) — that
one feeds a tone straight into the encoder with no sound server, because the
test needs a known signal rather than a realistic capture path. This example is
the realistic one.
