// The demo shell, shared by all three examples: a Start button, a status line
// and a stats table. It knows nothing about transports or playback -- each
// example passes in its own connect(), which is the only file that differs
// between the options.

export function run({ transport, connect }) {
  const startButton = document.getElementById('start');
  const statusEl = document.getElementById('status');
  const statsEl = document.getElementById('stats');
  document.getElementById('transport').textContent = transport;

  const values = new Map();
  const hooks = {
    status(text, state = '') {
      statusEl.textContent = text;
      statusEl.dataset.state = state;
    },
    stat(label, value) {
      values.set(label, value);
    },
    // Every transport counts bytes the same way; the rate is the headline
    // difference between the options.
    countBytes(n) {
      bytes += n;
    }
  };

  let sink = null;
  let bytes = 0;
  let bytesAt = performance.now();

  startButton.addEventListener('click', async () => {
    startButton.disabled = true;
    hooks.status('starting…');
    try {
      sink = await connect(hooks);
    } catch (err) {
      hooks.status(`failed to start: ${err.message}`, 'error');
    }
  });

  setInterval(() => {
    if (!sink) return;
    const now = performance.now();
    const kbps = (bytes * 8) / ((now - bytesAt) || 1); // bytes/ms * 8 = kbit/s
    bytes = 0;
    bytesAt = now;
    hooks.stat('wire rate', `${kbps.toFixed(0)} kbit/s`);
    if (sink.latencyMs) hooks.stat('output latency', `${sink.latencyMs().toFixed(0)} ms`);
    if (sink.dominantFrequency) hooks.stat('loudest tone', `${sink.dominantFrequency().toFixed(0)} Hz`);
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
  window.AUDIO_DEMO = { get sink() { return sink; }, stats: values };
}
