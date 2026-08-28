// The playback end, shared by both examples: a ring buffer on the audio thread.
//
// This is the piece neither option lets you avoid. Sound arrives in bursts on
// the network's schedule and has to leave on the sound card's schedule, and the
// two clocks are never quite the same -- a server capturing at "48000 Hz" and a
// browser playing at "48000 Hz" differ by tens of parts per million, which is a
// buffer that quietly empties or fills over minutes. So: buffer to a target
// depth, start playing, and nudge the depth back when it drifts.
//
// The correction here (drop a chunk when too deep, insert silence when starved)
// is the honest, obvious one. It is audible as a faint click when it fires,
// which is fine for an example and every few minutes in practice. A production
// version would resample by a fraction of a percent instead, which is inaudible.

const CORRECTION_HEADROOM = 1.5; // drop back to target once depth exceeds this

class PcmPlayerProcessor extends AudioWorkletProcessor {
  constructor(options) {
    super();
    const { channels, capacityFrames, targetFrames } = options.processorOptions;
    this.channels = channels;
    this.capacity = capacityFrames;
    this.target = targetFrames;
    this.planes = Array.from({ length: channels }, () => new Float32Array(capacityFrames));

    this.read = 0;
    this.write = 0;
    this.available = 0;

    // Hold output until the buffer has reached its target depth, so playback
    // doesn't start into an immediate underrun. Re-arms after every underrun.
    this.filling = true;

    this.underruns = 0;
    this.dropped = 0;
    this.received = 0;
    this.overflows = 0;
    this.lastReport = 0;

    this.port.onmessage = (event) => this.enqueue(event.data.planes);
  }

  enqueue(planes) {
    const frames = planes[0].length;
    this.received += frames;

    // A full ring means the consumer stopped (tab hidden, context suspended).
    // Drop the oldest audio rather than the newest: what matters is staying
    // near the live edge.
    if (this.available + frames > this.capacity) {
      const drop = this.available + frames - this.capacity;
      this.read = (this.read + drop) % this.capacity;
      this.available -= drop;
      this.overflows += drop;
    }

    for (let c = 0; c < this.channels; c++) {
      const source = planes[Math.min(c, planes.length - 1)];
      const target = this.planes[c];
      const first = Math.min(frames, this.capacity - this.write);
      target.set(source.subarray(0, first), this.write);
      if (first < frames) target.set(source.subarray(first), 0);
    }
    this.write = (this.write + frames) % this.capacity;
    this.available += frames;

    if (this.filling && this.available >= this.target) this.filling = false;
  }

  process(_inputs, outputs) {
    const output = outputs[0];
    const frames = output[0].length;

    if (this.filling || this.available < frames) {
      if (!this.filling) {
        this.underruns++;
        this.filling = true; // re-buffer instead of stuttering frame by frame
      }
      this.report();
      return true; // outputs are already zeroed: silence
    }

    // Drift correction: too much audio queued means the network side is running
    // faster than playback, so skip forward to the target depth.
    const ceiling = this.target * CORRECTION_HEADROOM;
    if (this.available > ceiling) {
      const drop = Math.floor(this.available - this.target);
      this.read = (this.read + drop) % this.capacity;
      this.available -= drop;
      this.dropped += drop;
    }

    for (let c = 0; c < output.length; c++) {
      const source = this.planes[Math.min(c, this.channels - 1)];
      const first = Math.min(frames, this.capacity - this.read);
      output[c].set(source.subarray(this.read, this.read + first));
      if (first < frames) output[c].set(source.subarray(0, frames - first), first);
    }
    this.read = (this.read + frames) % this.capacity;
    this.available -= frames;

    this.report();
    return true;
  }

  report() {
    // ~5 times a second, cheap enough to leave on.
    if (currentTime - this.lastReport < 0.2) return;
    this.lastReport = currentTime;
    this.port.postMessage({
      bufferedMs: (this.available / sampleRate) * 1000,
      underruns: this.underruns,
      dropped: this.dropped,
      overflows: this.overflows,
      receivedFrames: this.received,
      filling: this.filling
    });
  }
}

registerProcessor('pcm-player', PcmPlayerProcessor);
