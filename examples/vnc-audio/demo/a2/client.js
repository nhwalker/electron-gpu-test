// Option A2's client: framed Opus in, samples out. This file is the entire
// difference from A1 and A3 on the browser side.
//
// Wire format, produced by `opusenc ! rtpopuspay ! rtpstreampay`:
//
//   [u16 big-endian length][RTP packet] [u16 length][RTP packet] ...
//
// The length prefix is RFC 4571 (how RTP is carried on a byte stream); each RTP
// packet holds exactly one Opus frame, plus a sequence number and a 48kHz
// timestamp we get for free. WebCodecs decodes the frames; no container, no
// demuxer, no library.

import { startPcmPlayer } from '../common/pcm-player.js';

const SAMPLE_RATE = 48000;
const CHANNELS = 2;
const TARGET_LATENCY_MS = 120;
const RTP_MIN_HEADER = 12;

export async function connect(hooks) {
  const player = await startPcmPlayer({ channels: CHANNELS, targetLatencyMs: TARGET_LATENCY_MS }, hooks);

  let decoded = 0;
  let packets = 0;
  let gaps = 0;
  let lastSequence = null;
  let baseTimestamp = null;

  const decoder = new AudioDecoder({
    output: (audioData) => {
      decoded++;
      // Copy out before closing: the AudioData is only valid until then.
      const planes = [];
      for (let plane = 0; plane < audioData.numberOfChannels; plane++) {
        const samples = new Float32Array(audioData.numberOfFrames);
        audioData.copyTo(samples, { planeIndex: plane, format: 'f32-planar' });
        planes.push(samples);
      }
      audioData.close();
      player.push(planes);
    },
    error: (err) => hooks.status(`decoder error: ${err.message}`, 'error')
  });

  // Raw Opus packets at 48kHz need no `description`: the codec's own defaults
  // describe them. (An Ogg or WebM source would need the OpusHead here.)
  decoder.configure({ codec: 'opus', sampleRate: SAMPLE_RATE, numberOfChannels: CHANNELS });

  const ws = new WebSocket(`ws://${location.host}/`, ['binary']);
  ws.binaryType = 'arraybuffer';

  let carry = new Uint8Array(0);

  ws.addEventListener('open', () => hooks.status('playing', 'playing'));
  ws.addEventListener('close', () => hooks.status('stream closed', 'error'));
  ws.addEventListener('error', () => hooks.status('stream error', 'error'));

  ws.addEventListener('message', (event) => {
    const chunk = new Uint8Array(event.data);
    hooks.countBytes(chunk.byteLength);

    const merged = new Uint8Array(carry.length + chunk.length);
    merged.set(carry);
    merged.set(chunk, carry.length);
    const view = new DataView(merged.buffer);

    let offset = 0;
    for (;;) {
      // Enough for a length prefix, and then for the packet it announces?
      if (merged.length - offset < 2) break;
      const length = view.getUint16(offset);
      if (merged.length - offset - 2 < length) break;

      const packet = merged.subarray(offset + 2, offset + 2 + length);
      offset += 2 + length;
      packets++;

      const rtp = parseRtp(packet);
      if (!rtp) continue;

      // Sequence numbers are 16-bit and wrap; a gap means the server's sink
      // dropped us forward (a slow client), which is worth showing.
      if (lastSequence !== null && rtp.sequence !== ((lastSequence + 1) & 0xffff)) gaps++;
      lastSequence = rtp.sequence;

      // RTP timestamps tick at 48kHz and start at a random value, so anchor on
      // the first packet. Unsigned subtraction handles the 32-bit wrap.
      if (baseTimestamp === null) baseTimestamp = rtp.timestamp;
      const elapsed = (rtp.timestamp - baseTimestamp) >>> 0;

      decoder.decode(new EncodedAudioChunk({
        type: 'key', // every Opus frame stands alone
        timestamp: Math.round((elapsed / SAMPLE_RATE) * 1e6), // microseconds
        data: rtp.payload
      }));
    }

    carry = merged.slice(offset);
    hooks.stat('opus packets', `${packets} (${decoded} decoded)`);
    hooks.stat('sequence gaps', gaps);
  });

  return {
    dominantFrequency: () => player.dominantFrequency(),
    latencyMs: () => player.outputLatencyMs(),
    stop: () => {
      ws.close();
      if (decoder.state !== 'closed') decoder.close();
    }
  };
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
