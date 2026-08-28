// The demo shell, shared by both examples: a Start button, a status line and a
// stats table. It knows nothing about transports -- each example passes in its
// own connect(), which is the only file that differs between A1 and A2.

import { PcmPlayer } from './pcm-player.js';

export function run({ transport, sampleRate = 48000, channels = 2, targetLatencyMs = 120, connect }) {
  const startButton = document.getElementById('start');
  const statusEl = document.getElementById('status');
  const statsEl = document.getElementById('stats');
  document.getElementById('transport').textContent = transport;

  const values = new Map();
  const setStatus = (text, state = '') => {
    statusEl.textContent = text;
    statusEl.dataset.state = state;
  };
  const stat = (label, value) => values.set(label, value);

  let player = null;
  let bytes = 0;
  let bytesAt = performance.now();

  startButton.addEventListener('click', async () => {
    startButton.disabled = true;
    setStatus('starting the audio context…');

    player = new PcmPlayer({
      sampleRate,
      channels,
      targetLatencyMs,
      onStats: (s) => {
        stat('buffered', `${s.bufferedMs.toFixed(0)} ms${s.filling ? ' (filling)' : ''}`);
        stat('underruns', s.underruns);
        stat('drift corrections', `${s.dropped} frames dropped`);
        stat('overflow drops', `${s.overflows} frames`);
      }
    });
    await player.start();
    setStatus('connecting…');

    connect(player, {
      status: setStatus,
      stat,
      // Every transport counts bytes the same way; the rate is the headline
      // difference between the two options.
      countBytes: (n) => { bytes += n; }
    });
  });

  setInterval(() => {
    if (!player) return;
    const now = performance.now();
    const kbps = (bytes * 8) / ((now - bytesAt) || 1); // bytes/ms * 8 = kbit/s
    bytes = 0;
    bytesAt = now;
    stat('wire rate', `${kbps.toFixed(0)} kbit/s`);
    stat('output latency', `${player.outputLatencyMs().toFixed(0)} ms (browser)`);
    stat('loudest tone', `${player.dominantFrequency().toFixed(0)} Hz`);
    render();
  }, 500);

  function render() {
    statsEl.replaceChildren(...[...values].map(([label, value]) => {
      const row = document.createElement('tr');
      const name = document.createElement('th');
      name.textContent = label;
      const cell = document.createElement('td');
      cell.textContent = String(value);
      row.append(name, cell);
      return row;
    }));
  }

  // Exposed for the automated check, and handy in the console.
  window.AUDIO_DEMO = { get player() { return player; }, stats: values };
}
