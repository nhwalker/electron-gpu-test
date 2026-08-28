// The playback front end, shared by both examples.
//
// Both transports end in the same place: planar Float32 samples handed to an
// AudioWorklet. A1 gets there by scaling integers; A2 by decoding Opus. Nothing
// below this line knows which.

import { dominantFrequency } from './spectrum.js';

const WORKLET_URL = new URL('./pcm-worklet.js', import.meta.url);

export class PcmPlayer {
  /**
   * targetLatencyMs is the whole game: lower is more responsive, higher rides
   * out bigger network hiccups. 120ms is a reasonable desktop-audio default --
   * for comparison, a WebRTC jitter buffer would sit around 40-100ms and adapt.
   */
  constructor({ sampleRate = 48000, channels = 2, targetLatencyMs = 120, onStats } = {}) {
    this.sampleRate = sampleRate;
    this.channels = channels;
    this.targetLatencyMs = targetLatencyMs;
    this.onStats = onStats;
    this.context = null;
    this.node = null;
    this.analyser = null;
  }

  /**
   * Must be called from a user gesture: Chromium will not let a page start
   * making noise on its own. (In Electron, launching with
   * --autoplay-policy=no-user-gesture-required lifts that.)
   */
  async start() {
    // Pinning the context to the stream's rate means the browser resamples once,
    // at the output, instead of us doing it per packet.
    this.context = new AudioContext({ sampleRate: this.sampleRate, latencyHint: 'interactive' });
    await this.context.audioWorklet.addModule(WORKLET_URL);

    const targetFrames = Math.round((this.targetLatencyMs / 1000) * this.sampleRate);
    this.node = new AudioWorkletNode(this.context, 'pcm-player', {
      numberOfInputs: 0,
      outputChannelCount: [this.channels],
      processorOptions: {
        channels: this.channels,
        targetFrames,
        capacityFrames: targetFrames * 8 // room for a long stall before overflow
      }
    });
    this.node.port.onmessage = (event) => this.onStats && this.onStats(event.data);

    // Tapped only to show what is playing; it passes the audio straight through.
    this.analyser = new AnalyserNode(this.context, { fftSize: 4096, smoothingTimeConstant: 0 });
    this.node.connect(this.analyser).connect(this.context.destination);
    await this.context.resume();
  }

  /** A1's path: interleaved 16-bit integers, straight off the wire. */
  pushInterleavedInt16(samples) {
    const frames = Math.floor(samples.length / this.channels);
    const planes = [];
    for (let c = 0; c < this.channels; c++) {
      const plane = new Float32Array(frames);
      for (let i = 0; i < frames; i++) plane[i] = samples[i * this.channels + c] / 32768;
      planes.push(plane);
    }
    this.push(planes);
  }

  /** A2's path: whatever the audio decoder produced. */
  push(planes) {
    if (!this.node) return;
    this.node.port.postMessage({ planes }, planes.map((p) => p.buffer));
  }

  /** The loudest frequency currently playing. */
  dominantFrequency() {
    return dominantFrequency(this.analyser);
  }

  /** What the browser itself adds on top of our buffer, in milliseconds. */
  outputLatencyMs() {
    if (!this.context) return 0;
    return ((this.context.baseLatency || 0) + (this.context.outputLatency || 0)) * 1000;
  }

  async close() {
    if (this.context) await this.context.close();
    this.context = this.node = this.analyser = null;
  }
}

/**
 * What A1 and A2 both do before their first byte arrives: bring up the player
 * and route its stats into the page. Kept here so each option's client.js is
 * about its transport and nothing else.
 */
export async function startPcmPlayer({ channels, targetLatencyMs }, hooks) {
  const player = new PcmPlayer({
    channels,
    targetLatencyMs,
    onStats: (s) => {
      hooks.stat('buffered', `${s.bufferedMs.toFixed(0)} ms${s.filling ? ' (filling)' : ''}`);
      hooks.stat('underruns', s.underruns);
      hooks.stat('drift corrections', `${s.dropped} frames dropped`);
      hooks.stat('overflow drops', `${s.overflows} frames`);
    }
  });
  await player.start();
  return player;
}
