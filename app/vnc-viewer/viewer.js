// The viewer: drives noVNC's RFB client against the loopback WebSocket bridge
// the main process opened for this session (see app/vnc.js).
//
// The session token is the only thing in this page's URL; everything else --
// where to connect, with which credentials and options -- is fetched from the
// bridge by that token, so no password ends up in a URL or a window title.

import RFB from '/novnc/core/rfb.js';

const toolbar = {
  indicator: document.getElementById('indicator'),
  target: document.getElementById('target'),
  message: document.getElementById('message'),
  ctrlAltDel: document.getElementById('ctrl-alt-del'),
  fullscreen: document.getElementById('fullscreen'),
  connect: document.getElementById('connect')
};
const screenEl = document.getElementById('screen');
const dialog = document.getElementById('credentials-dialog');

// Test hook, mirroring the pattern the WebGL pages use: the functional test
// reads connection state from here instead of guessing at pixels.
const testState = { state: 'connecting', label: '', desktopName: null, error: null, connections: 0 };
window.VNC_TEST = testState;

const token = new URLSearchParams(location.search).get('s');
if (!token) {
  fail('No session token in the viewer URL.');
  throw new Error('missing session token');
}

let session;
try {
  const response = await fetch(`/session/${encodeURIComponent(token)}`);
  if (!response.ok) throw new Error(`the app returned HTTP ${response.status}`);
  session = await response.json();
} catch (err) {
  // Without the session there is nothing to connect to; say so in the page
  // rather than leaving an empty window.
  fail(`Could not load this session from the app: ${err.message}`);
  throw err;
}

const wsUrl = `ws://${location.host}${session.wsPath}`;
const credentials = { ...session.credentials };

document.title = session.options.title || session.label;
toolbar.target.textContent = session.options.title || session.label;
testState.label = session.label;

let rfb = null;
let reconnectTimer = null;
// A disconnect we asked for (or an auth failure) must not trigger the automatic
// reconnect -- only an unexpected drop should.
let reconnectSuppressed = false;

connect();

// --- Connection ---------------------------------------------------------------

function connect() {
  clearTimeout(reconnectTimer);
  reconnectSuppressed = false;
  setState('connecting', `Connecting to ${session.label}…`);
  toolbar.connect.hidden = true;

  rfb = new RFB(screenEl, wsUrl, {
    credentials,
    shared: session.options.shared
  });

  // Viewport handling: scale the remote framebuffer into the window, ask the
  // server to resize itself to the window, or show it 1:1 and clip.
  rfb.viewOnly = session.options.viewOnly;
  rfb.scaleViewport = session.options.resize === 'scale';
  rfb.resizeSession = session.options.resize === 'remote';
  rfb.clipViewport = session.options.resize === 'off';
  rfb.background = getComputedStyle(document.body).backgroundColor;
  if (session.options.quality !== null) rfb.qualityLevel = session.options.quality;
  if (session.options.compression !== null) rfb.compressionLevel = session.options.compression;

  rfb.addEventListener('connect', onConnect);
  rfb.addEventListener('disconnect', onDisconnect);
  rfb.addEventListener('credentialsrequired', onCredentialsRequired);
  rfb.addEventListener('securityfailure', onSecurityFailure);
  rfb.addEventListener('desktopname', onDesktopName);
  rfb.addEventListener('clipboard', onRemoteClipboard);
}

function onConnect() {
  testState.connections += 1;
  setState('connected', session.options.viewOnly ? 'Connected (view only)' : 'Connected');
  toolbar.ctrlAltDel.disabled = session.options.viewOnly;
}

function onDisconnect(event) {
  toolbar.ctrlAltDel.disabled = true;
  const clean = event.detail.clean;
  setState('disconnected', clean ? 'Disconnected' : 'Connection lost');

  if (!reconnectSuppressed && session.options.reconnect) {
    toolbar.message.textContent += ` — reconnecting in ${session.options.reconnectDelay / 1000}s`;
    reconnectTimer = setTimeout(connect, session.options.reconnectDelay);
  } else {
    toolbar.connect.hidden = false;
  }
}

// The server wants credentials we don't have (or the ones we had were wrong).
function onCredentialsRequired(event) {
  const types = event.detail.types || ['password'];
  if (types.includes('target')) {
    // A VNC repeater's target ID; nothing to prompt for here.
    fail('This server needs a repeater target ID, which this viewer does not support.');
    return;
  }
  promptForCredentials(types).then((entered) => {
    if (!entered) {
      reconnectSuppressed = true;
      rfb.disconnect();
      return;
    }
    Object.assign(credentials, entered);
    rfb.sendCredentials(credentials);
  });
}

function onSecurityFailure(event) {
  // Authentication was rejected. Reconnecting on a loop with the same bad
  // password would just hammer the server, so stop and drop it: the next
  // connect attempt prompts.
  reconnectSuppressed = true;
  delete credentials.password; // deleted, not blanked: an empty one would be used as-is
  testState.error = event.detail.reason || `security failure (status ${event.detail.status})`;
  toolbar.message.textContent = `Authentication failed: ${testState.error}`;
}

function onDesktopName(event) {
  testState.desktopName = event.detail.name;
  document.title = session.options.title || `${event.detail.name} — ${session.label}`;
}

// Remote -> local clipboard. The reverse needs a browser paste event, which
// noVNC swallows while it has keyboard focus, so it is not wired up.
function onRemoteClipboard(event) {
  navigator.clipboard.writeText(event.detail.text).catch(() => {
    /* Clipboard access can be denied; a failed sync is not worth interrupting. */
  });
}

// --- UI -----------------------------------------------------------------------

function setState(state, message) {
  testState.state = state;
  toolbar.indicator.dataset.state = state;
  toolbar.message.textContent = message;
}

function fail(message) {
  testState.state = 'failed';
  testState.error = message;
  toolbar.indicator.dataset.state = 'disconnected';
  toolbar.message.textContent = message;
}

function promptForCredentials(types) {
  const usernameRow = document.getElementById('username-row');
  const passwordInput = document.getElementById('password');
  usernameRow.hidden = !types.includes('username');
  document.getElementById('credentials-title').textContent = `${session.label} requires authentication`;
  passwordInput.value = '';
  dialog.showModal();
  (types.includes('username') ? document.getElementById('username') : passwordInput).focus();

  return new Promise((resolve) => {
    dialog.addEventListener('close', () => {
      // A dialog dismissed with Escape returns no value; that's a cancel.
      if (dialog.returnValue !== 'connect') return resolve(null);
      // Only what was asked for: an unwanted empty field would be sent as a
      // real (blank) credential.
      const entered = { password: passwordInput.value };
      if (types.includes('username')) entered.username = document.getElementById('username').value;
      resolve(entered);
    }, { once: true });
  });
}

toolbar.connect.addEventListener('click', connect);
toolbar.ctrlAltDel.addEventListener('click', () => rfb && rfb.sendCtrlAltDel());
toolbar.fullscreen.addEventListener('click', () => {
  if (document.fullscreenElement) document.exitFullscreen();
  else document.documentElement.requestFullscreen();
});

window.addEventListener('beforeunload', () => {
  reconnectSuppressed = true;
  if (rfb) rfb.disconnect();
});
