// Option A1's client: raw PCM in, samples out. This file is the entire
// difference from A2 on the browser side.
//
// Wire format: nothing. The server's pipeline caps say S16LE / 48000 / stereo,
// so byte 2n of the stream is the low half of a sample and that is all there is
// to know. The one wrinkle is that a byte stream has no frames: a WebSocket
// message can end in the middle of a sample, so we carry the remainder.

const CHANNELS = 2;
const BYTES_PER_FRAME = CHANNELS * 2; // 2 channels, 16 bits each

export function connect(player, hooks) {
  // In this demo, websockify is relaying the server's TCP port. In the Electron
  // app the same stream would arrive from the main process's bridge instead --
  // the page cannot tell the difference.
  const ws = new WebSocket(`ws://${location.host}/`, ['binary']);
  ws.binaryType = 'arraybuffer';

  let carry = new Uint8Array(0);

  ws.addEventListener('open', () => hooks.status('playing', 'playing'));
  ws.addEventListener('close', () => hooks.status('stream closed', 'error'));
  ws.addEventListener('error', () => hooks.status('stream error', 'error'));

  ws.addEventListener('message', (event) => {
    const chunk = new Uint8Array(event.data);
    hooks.countBytes(chunk.byteLength);

    // Join whatever was left over with what just arrived. (A copy per message,
    // which is fine at 1.5 Mbit/s; a real implementation would use a ring.)
    const merged = new Uint8Array(carry.length + chunk.length);
    merged.set(carry);
    merged.set(chunk, carry.length);

    const usable = merged.length - (merged.length % BYTES_PER_FRAME);
    if (usable > 0) {
      // Safe: merged.buffer is freshly allocated, so offset 0 is 2-byte aligned.
      player.pushInterleavedInt16(new Int16Array(merged.buffer, 0, usable / 2));
    }
    carry = merged.slice(usable);
  });

  return () => ws.close();
}
