// Option A3's client: a WebM stream in, an <audio> element playing it out.
// This file is the entire difference from A1 and A2 on the browser side.
//
// Wire format: a streamable WebM stream, exactly as `webmmux streamable=true`
// wrote it -- an initialisation segment (EBML header + Tracks) followed by an
// open-ended run of clusters. MediaSource wants precisely that, so the whole
// job is "hand the bytes to a SourceBuffer": no framing to parse, no codec API,
// no ring buffer. The browser demuxes, decodes, buffers and clocks it.
//
// What that costs is at the bottom of this file: MediaSource is built for
// video-on-demand, so nothing stops the playhead drifting further and further
// behind the live edge. Keeping it near the edge, and evicting what has been
// played, is the work A1 and A2 spend on a ring buffer instead.

import { dominantFrequency } from '../common/spectrum.js';

const MIME = 'audio/webm; codecs="opus"';

// How far behind the newest audio the playhead may fall before it is moved up.
// It has to be well above one cluster, or the correction fights the stream.
const MAX_LATENCY_MS = 400;
const TARGET_LATENCY_MS = 150;
// Keep this much played audio in the buffer; drop the rest, or a long session
// grows without bound.
const KEEP_PLAYED_MS = 5000;

export async function connect(hooks) {
  if (!('MediaSource' in window) || !MediaSource.isTypeSupported(MIME)) {
    throw new Error(`this browser cannot play ${MIME} through MediaSource`);
  }

  const audio = document.getElementById('output');
  const mediaSource = new MediaSource();
  audio.src = URL.createObjectURL(mediaSource);
  await new Promise((resolve) => mediaSource.addEventListener('sourceopen', resolve, { once: true }));

  const sourceBuffer = mediaSource.addSourceBuffer(MIME);
  // 'sequence': the stream's own timestamps start wherever the encoder was when
  // we connected, and we only ever append in order, so let the browser lay the
  // segments end to end rather than trusting those timestamps.
  sourceBuffer.mode = 'sequence';

  // appendBuffer is asynchronous and refuses to be called while it is working,
  // so everything off the socket goes through a queue.
  const queue = [];
  let appended = 0;
  let corrections = 0;

  const pump = () => {
    if (sourceBuffer.updating || queue.length === 0 || mediaSource.readyState !== 'open') return;
    sourceBuffer.appendBuffer(queue.shift());
    appended++;
  };
  sourceBuffer.addEventListener('updateend', pump);
  sourceBuffer.addEventListener('error', () => hooks.status('the browser rejected the stream', 'error'));

  const ws = new WebSocket(`ws://${location.host}/`, ['binary']);
  ws.binaryType = 'arraybuffer';

  ws.addEventListener('open', () => hooks.status('playing', 'playing'));
  ws.addEventListener('close', () => hooks.status('stream closed', 'error'));
  ws.addEventListener('error', () => hooks.status('stream error', 'error'));
  ws.addEventListener('message', (event) => {
    hooks.countBytes(event.data.byteLength);
    queue.push(new Uint8Array(event.data));
    pump();
    // The first append is also the cue to start playing.
    if (audio.paused) audio.play().catch(() => hooks.status('click to allow audio', 'error'));
  });

  // --- Staying live -----------------------------------------------------------
  // Everything above is the option; everything below is the price of it. The
  // element plays at its own pace, and any hiccup leaves it permanently behind:
  // MediaSource has no notion of "catch up".
  setInterval(() => {
    if (!sourceBuffer.buffered.length || mediaSource.readyState !== 'open') return;
    const end = sourceBuffer.buffered.end(sourceBuffer.buffered.length - 1);
    const behindMs = (end - audio.currentTime) * 1000;
    hooks.stat('behind live', `${behindMs.toFixed(0)} ms`);
    hooks.stat('live-edge seeks', corrections);
    hooks.stat('append queue', `${queue.length} chunk(s), ${appended} appended`);
    hooks.stat('element state', audio.paused ? 'paused' : `playing (readyState ${audio.readyState})`);

    if (behindMs > MAX_LATENCY_MS) {
      // Jumping the playhead is audible -- there is no way to skip audio
      // quietly -- which is why the threshold is generous.
      audio.currentTime = end - TARGET_LATENCY_MS / 1000;
      corrections++;
    }

    // Evict what has been played, or the buffer grows for the whole session.
    const start = sourceBuffer.buffered.start(0);
    const evictBefore = audio.currentTime - KEEP_PLAYED_MS / 1000;
    if (!sourceBuffer.updating && evictBefore > start) {
      sourceBuffer.remove(start, evictBefore);
    }
  }, 500);

  // The spectrum readout, so this option reports the same "loudest tone" as the
  // other two. Routing the element through WebAudio is demo instrumentation --
  // a real A3 client is just the <audio> element.
  const context = new AudioContext();
  const analyser = new AnalyserNode(context, { fftSize: 4096, smoothingTimeConstant: 0 });
  context.createMediaElementSource(audio).connect(analyser).connect(context.destination);
  await context.resume();

  return {
    dominantFrequency: () => dominantFrequency(analyser),
    latencyMs: () => (context.baseLatency + context.outputLatency) * 1000,
    stop: () => ws.close()
  };
}
