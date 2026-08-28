// The AudioContext side of desktop audio: takes planar Float32 samples and
// plays them through the ring buffer in audio-worklet.js.
//
// Nothing here knows where the samples came from -- swapping the transport (see
// examples/vnc-audio) would not touch this file.

const WORKLET_URL = new URL('./audio-worklet.js', import.meta.url);

// Opus always decodes at 48kHz, so the context is pinned there and the browser
// resamples once, at the output, instead of us doing it per packet.
const SAMPLE_RATE = 48000;

export class AudioPlayer {
  constructor({ channels = 2, targetLatencyMs = 120, onStats } = {}) {
    this.channels = channels;
    this.targetLatencyMs = targetLatencyMs;
    this.onStats = onStats;
    this.context = null;
    this.node = null;
    this.gain = null;
    this.analyser = null;
  }

  get sampleRate() {
    return SAMPLE_RATE;
  }

  /**
   * Chromium blocks a page from making noise before a user gesture, so the
   * context can come up suspended -- resume() then needs a click. launch.sh
   * passes --autoplay-policy=no-user-gesture-required so the app's own windows
   * don't need one; `blocked` reports whether that worked.
   */
  async start() {
    this.context = new AudioContext({ sampleRate: SAMPLE_RATE, latencyHint: 'interactive' });
    await this.context.audioWorklet.addModule(WORKLET_URL);

    const targetFrames = Math.round((this.targetLatencyMs / 1000) * SAMPLE_RATE);
    this.node = new AudioWorkletNode(this.context, 'desktop-audio', {
      numberOfInputs: 0,
      outputChannelCount: [this.channels],
      processorOptions: {
        channels: this.channels,
        targetFrames,
        capacityFrames: targetFrames * 8 // room for a long stall before overflow
      }
    });
    this.node.port.onmessage = (event) => this.onStats && this.onStats(event.data);

    this.gain = new GainNode(this.context, { gain: 1 });
    // Tapped only so the viewer can report what is playing; passes audio through.
    this.analyser = new AnalyserNode(this.context, { fftSize: 4096, smoothingTimeConstant: 0 });
    this.node.connect(this.gain).connect(this.analyser).connect(this.context.destination);

    await this.resume();
    return { blocked: this.blocked };
  }

  async resume() {
    if (!this.context) return;
    try {
      await this.context.resume();
    } catch (_) {
      // Still suspended; the caller shows a control to try again from a click.
    }
  }

  get blocked() {
    return !!this.context && this.context.state !== 'running';
  }

  /** Samples straight from the decoder, one Float32Array per channel. */
  push(planes) {
    if (!this.node) return;
    this.node.port.postMessage({ planes }, planes.map((plane) => plane.buffer));
  }

  setMuted(muted) {
    if (this.gain) this.gain.gain.value = muted ? 0 : 1;
  }

  /**
   * The loudest frequency currently playing. Only used for reporting -- and by
   * the functional test, which asserts the server's test tone survived the trip.
   */
  dominantFrequency() {
    if (!this.analyser) return 0;
    const bins = new Float32Array(this.analyser.frequencyBinCount);
    this.analyser.getFloatFrequencyData(bins);
    let peak = 0;
    for (let i = 1; i < bins.length; i++) if (bins[i] > bins[peak]) peak = i;
    return (peak * this.context.sampleRate) / this.analyser.fftSize;
  }

  async close() {
    if (this.context) await this.context.close();
    this.context = this.node = this.gain = this.analyser = null;
  }
}
