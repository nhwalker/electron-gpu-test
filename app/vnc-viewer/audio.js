// Desktop audio: Opus packets, framed per RFC 4571, decoded with WebCodecs.
// examples/vnc-audio-server is a server that speaks it.
//
// The main process bridges the server's audio port to a WebSocket on this same
// loopback origin, exactly as it does for the RFB connection, so what arrives
// here is what the server's pipeline produced:
//
//   [u16 big-endian length][RTP packet] [u16 length][RTP packet] ...
//
// Each RTP packet holds one whole Opus frame, plus a sequence number and a
// 48kHz timestamp. AudioDecoder takes the payload directly -- raw Opus packets
// need no `description`, so there is no container and no demuxer.

import { AudioPlayer } from './audio-player.js';

const RTP_MIN_HEADER = 12;
const RECONNECT_DELAY_MS = 3000;

export class DesktopAudio {
  /**
   * @param config the session's `audio` block: wsPath, channels, targetLatencyMs
   * @param onState called with {state, blocked, muted} whenever the UI should change
   */
  constructor(config, onState) {
    this.config = config;
    this.onState = onState || (() => {});
    this.player = null;
    this.socket = null;
    this.decoder = null;
    this.state = 'idle';
    this.muted = false;
    this.stopped = false;
    this.reconnectTimer = null;
    this.stats = { packets: 0, decoded: 0, gaps: 0, bufferedMs: 0, underruns: 0, dropped: 0 };
  }

  async start() {
    this.player = new AudioPlayer({
      channels: this.config.channels,
      targetLatencyMs: this.config.targetLatencyMs,
      onStats: (stats) => Object.assign(this.stats, stats)
    });
    const { blocked } = await this.player.start();
    this.connect();
    this.report(blocked ? 'blocked' : 'connecting');
  }

  connect() {
    clearTimeout(this.reconnectTimer);
    this.openDecoder();

    this.socket = new WebSocket(`ws://${location.host}${this.config.wsPath}`);
    this.socket.binaryType = 'arraybuffer';

    let carry = new Uint8Array(0);
    let lastSequence = null;
    let baseTimestamp = null;

    this.socket.addEventListener('open', () => this.report('playing'));
    this.socket.addEventListener('error', () => this.report('error'));
    this.socket.addEventListener('close', () => {
      this.report('disconnected');
      // The desktop's audio server can restart independently of the VNC one;
      // keep trying quietly until the window goes away.
      if (!this.stopped) this.reconnectTimer = setTimeout(() => this.connect(), RECONNECT_DELAY_MS);
    });

    this.socket.addEventListener('message', (event) => {
      const chunk = new Uint8Array(event.data);

      // A WebSocket message is not a packet boundary: join what was left over
      // from last time, then take whole packets off the front.
      const merged = new Uint8Array(carry.length + chunk.length);
      merged.set(carry);
      merged.set(chunk, carry.length);
      const view = new DataView(merged.buffer);

      let offset = 0;
      for (;;) {
        if (merged.length - offset < 2) break;
        const length = view.getUint16(offset);
        if (merged.length - offset - 2 < length) break;

        const packet = merged.subarray(offset + 2, offset + 2 + length);
        offset += 2 + length;
        this.stats.packets++;

        const rtp = parseRtp(packet);
        if (!rtp) continue;

        // Sequence numbers are 16-bit and wrap. A gap means the server skipped
        // us forward because we were reading too slowly.
        if (lastSequence !== null && rtp.sequence !== ((lastSequence + 1) & 0xffff)) this.stats.gaps++;
        lastSequence = rtp.sequence;

        // RTP timestamps tick at 48kHz from a random start, so anchor on the
        // first packet; unsigned subtraction handles the 32-bit wrap.
        if (baseTimestamp === null) baseTimestamp = rtp.timestamp;
        const elapsed = (rtp.timestamp - baseTimestamp) >>> 0;

        if (this.decoder && this.decoder.state === 'configured') {
          this.decoder.decode(new EncodedAudioChunk({
            type: 'key', // every Opus frame stands alone
            timestamp: Math.round((elapsed / this.player.sampleRate) * 1e6), // microseconds
            data: rtp.payload
          }));
        }
      }
      carry = merged.slice(offset);
    });
  }

  openDecoder() {
    if (this.decoder && this.decoder.state !== 'closed') this.decoder.close();
    this.decoder = new AudioDecoder({
      output: (audioData) => {
        this.stats.decoded++;
        // Copy the samples out before closing: the AudioData is only valid
        // until then.
        const planes = [];
        for (let plane = 0; plane < audioData.numberOfChannels; plane++) {
          const samples = new Float32Array(audioData.numberOfFrames);
          audioData.copyTo(samples, { planeIndex: plane, format: 'f32-planar' });
          planes.push(samples);
        }
        audioData.close();
        this.player.push(planes);
      },
      error: (err) => {
        console.error(`vnc-audio: decoder error: ${err.message}`);
        this.report('error');
      }
    });
    this.decoder.configure({
      codec: 'opus',
      sampleRate: this.player.sampleRate,
      numberOfChannels: this.config.channels
    });
  }

  /** Called from a click, so it doubles as the gesture autoplay may be waiting for. */
  async toggleMuted() {
    this.muted = !this.muted;
    this.player.setMuted(this.muted);
    if (!this.muted) await this.player.resume();
    this.report(this.state);
  }

  dominantFrequency() {
    return this.player ? this.player.dominantFrequency() : 0;
  }

  report(state) {
    this.state = state;
    this.onState({ state, blocked: !!this.player && this.player.blocked, muted: this.muted });
  }

  async stop() {
    this.stopped = true;
    clearTimeout(this.reconnectTimer);
    if (this.socket) this.socket.close();
    if (this.decoder && this.decoder.state !== 'closed') this.decoder.close();
    if (this.player) await this.player.close();
  }
}

/** Pulls the payload out of one RTP packet. */
function parseRtp(packet) {
  if (packet.length < RTP_MIN_HEADER) return null;
  if (packet[0] >> 6 !== 2) return null; // not RTP version 2

  const hasPadding = (packet[0] & 0x20) !== 0;
  const hasExtension = (packet[0] & 0x10) !== 0;
  const csrcCount = packet[0] & 0x0f;
  const view = new DataView(packet.buffer, packet.byteOffset, packet.byteLength);

  let start = RTP_MIN_HEADER + 4 * csrcCount;
  if (hasExtension) {
    if (packet.length < start + 4) return null;
    start += 4 + 4 * view.getUint16(start + 2);
  }
  let end = packet.length;
  if (hasPadding) end -= packet[packet.length - 1];
  if (end <= start) return null;

  return {
    sequence: view.getUint16(2),
    timestamp: view.getUint32(4),
    payload: packet.subarray(start, end)
  };
}
