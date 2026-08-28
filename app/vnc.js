// vnc.js -- lets the app open a `vnc://<host>:<port>` URL in a noVNC web page.
//
// A browser engine cannot speak RFB (the VNC wire protocol) over a raw TCP
// socket, and a VNC server does not speak WebSocket. noVNC bridges that gap the
// same way `websockify` does: the viewer is a normal web page that talks RFB
// over a WebSocket, and something outside the renderer relays those frames onto
// a plain TCP connection. That "something" is this module, running in Electron's
// main process:
//
//   BrowserWindow                 main process                    VNC server
//   ------------------------      -------------------------      -------------
//   http://127.0.0.1:PORT/  <-->  loopback HTTP server           
//   viewer.html + noVNC           (viewer page + noVNC files)
//   RFB over WebSocket      <-->  /ws/<token> bridge       <-->  TCP host:port
//
// Everything is bound to 127.0.0.1 on an ephemeral port and reachable only with
// an unguessable token minted for a URL that was named on the command line.
//
// The relay is byte-for-byte: no RFB parsing happens here, so every encoding,
// authentication scheme and extension noVNC supports keeps working.

'use strict';

const crypto = require('node:crypto');
const fs = require('node:fs');
const http = require('node:http');
const net = require('node:net');
const path = require('node:path');

const { WebSocketServer } = require('ws');

// The RFB default port, and the "display number" shorthand VNC clients accept:
// in `vnc://host:1`, the 1 is display :1, i.e. TCP 5901. Anything above the
// display range is taken literally as a TCP port.
const DEFAULT_VNC_PORT = 5900;
const MAX_DISPLAY_NUMBER = 99;

// Desktop audio arrives on a second port, as Opus packets framed per RFC 4571
// (see examples/vnc-audio-server). It is opt-in: a plain VNC server has no such
// port, so we only connect when a URL asks for it.
const DEFAULT_AUDIO_PORT = 5901;
const DEFAULT_AUDIO_LATENCY_MS = 120;

// Pause the VNC->browser direction while this many bytes are queued in the
// WebSocket. Framebuffer updates arrive far faster than a slow renderer drains
// them; without this the queue is unbounded.
const WS_HIGH_WATER_MARK = 4 * 1024 * 1024;

const MIME_TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png'
};

// --- URL parsing --------------------------------------------------------------

/** True for arguments this module owns, whether or not they parse. */
function isVncUrl(arg) {
  return typeof arg === 'string' && /^vnc:\/\//i.test(arg);
}

function parseBoolean(value, fallback) {
  if (value === null || value === undefined || value === '') return fallback;
  if (/^(1|true|yes|on)$/i.test(value)) return true;
  if (/^(0|false|no|off)$/i.test(value)) return false;
  throw new Error(`expected a boolean (1/0, true/false) but got "${value}"`);
}

function parseLevel(value, name) {
  if (value === null || value === undefined || value === '') return null;
  const level = Number(value);
  if (!Number.isInteger(level) || level < 0 || level > 9) {
    throw new Error(`${name} must be an integer from 0 to 9 but was "${value}"`);
  }
  return level;
}

/**
 * `audio=on` streams desktop audio from the conventional port, `audio=<port>`
 * from another one, and the default (absent, or off) leaves it alone.
 */
function parseAudioOption(value) {
  if (value === null || value === undefined || value === '') return null;
  if (/^(1|true|yes|on)$/i.test(value)) return DEFAULT_AUDIO_PORT;
  if (/^(0|false|no|off)$/i.test(value)) return null;
  const port = Number(value);
  if (!Number.isInteger(port) || port < 1 || port > 65535) {
    throw new Error(`audio must be on, off or a port number but was "${value}"`);
  }
  return port;
}

/**
 * Parses `vnc://[user[:password]@]host[:port|:display][?options]` into the
 * target description the viewer page is driven from. Returns null for anything
 * that isn't a vnc:// URL; throws with a readable message for one that is but
 * doesn't parse.
 */
function parseVncUrl(arg) {
  if (!isVncUrl(arg)) return null;

  let url;
  try {
    url = new URL(arg);
  } catch (_) {
    throw new Error('could not be parsed as a URL');
  }

  // WHATWG URL keeps IPv6 literals bracketed; net.connect wants them bare.
  const host = url.hostname.replace(/^\[|\]$/g, '');
  if (!host) throw new Error('no host in the URL');

  let port = DEFAULT_VNC_PORT;
  if (url.port !== '') {
    const number = Number(url.port);
    port = number <= MAX_DISPLAY_NUMBER ? DEFAULT_VNC_PORT + number : number;
  }

  const params = url.searchParams;

  // Credentials may come from the userinfo, from query parameters, or -- the
  // form that keeps them out of `ps` output -- from the environment.
  const username = decodeURIComponent(url.username) || params.get('username')
    || process.env.VNC_USERNAME || '';
  const password = decodeURIComponent(url.password) || params.get('password')
    || process.env.VNC_PASSWORD || '';

  const audioPort = parseAudioOption(params.get('audio'));
  const audioLatency = Number(params.get('audio_latency') || DEFAULT_AUDIO_LATENCY_MS);
  if (!Number.isFinite(audioLatency) || audioLatency < 20 || audioLatency > 2000) {
    throw new Error(`audio_latency must be 20-2000 (ms) but was "${params.get('audio_latency')}"`);
  }
  const audioChannels = Number(params.get('audio_channels') || 2);
  if (![1, 2].includes(audioChannels)) {
    throw new Error(`audio_channels must be 1 or 2 but was "${params.get('audio_channels')}"`);
  }

  const resize = (params.get('resize') || 'scale').toLowerCase();
  if (!['scale', 'remote', 'off'].includes(resize)) {
    throw new Error(`resize must be scale, remote or off but was "${resize}"`);
  }

  // Only carry credentials that were actually supplied: noVNC treats a defined
  // password as "use this one", so an empty string would authenticate with a
  // blank password instead of prompting for the real one.
  const credentials = {};
  if (username) credentials.username = username;
  if (password) credentials.password = password;

  const target = {
    host,
    port,
    label: `vnc://${url.host || host}`,
    // Same host, second port: the audio stream the viewer decodes with
    // WebCodecs. null when the URL didn't ask for audio.
    audio: audioPort === null ? null : {
      port: audioPort,
      channels: audioChannels,
      // How much audio to keep buffered before playing: lower is more
      // responsive, higher rides out bigger network hiccups.
      targetLatencyMs: audioLatency
    },
    credentials,
    options: {
      // Send no input to the server; just watch.
      viewOnly: parseBoolean(params.get('view_only'), false),
      // scale: fit the remote framebuffer to the window. remote: ask the server
      // to resize its framebuffer to the window. off: crop, with scrollbars.
      resize,
      // Let other clients stay connected (RFB "shared" flag).
      shared: parseBoolean(params.get('shared'), true),
      // Tight/JPEG quality and zlib compression, when the server supports them.
      quality: parseLevel(params.get('quality'), 'quality'),
      compression: parseLevel(params.get('compression'), 'compression'),
      // Reconnect automatically after an unexpected disconnect.
      reconnect: parseBoolean(params.get('reconnect'), true),
      reconnectDelay: 3000,
      // Stop Electron's menu accelerators (Ctrl+R, Ctrl+W, F11, ...) from
      // swallowing keys the remote desktop should receive.
      grabKeys: parseBoolean(params.get('grab_keys'), true),
      title: params.get('title') || ''
    }
  };

  // A session is keyed by where it connects, so a window's storage (and the
  // viewer's own preferences) follow the remote host rather than the loopback
  // port, which changes on every launch.
  target.partition = `persist:vnc-${`${host}_${port}`.replace(/[^a-zA-Z0-9._-]/g, '_')}`;
  return target;
}

// --- The noVNC package --------------------------------------------------------

/**
 * Locates the installed @novnc/novnc package directory. Its `exports` field
 * only names core/rfb.js, so resolve that and walk up to the package root; the
 * viewer needs the whole tree (core/ imports ../vendor/pako/...).
 */
function resolveNovncRoot() {
  const entry = require.resolve('@novnc/novnc'); // <root>/core/rfb.js
  return path.dirname(path.dirname(entry));
}

// --- The loopback viewer server ----------------------------------------------

class VncViewerServer {
  constructor(options = {}) {
    this._viewerRoot = options.viewerRoot || path.join(__dirname, 'vnc-viewer');
    this._novncRoot = options.novncRoot || resolveNovncRoot();
    this._sessions = new Map();
    this._server = null;
    this._wss = null;
    this._origin = null;
  }

  /**
   * Mints a session token for a target. Safe to call before start(); the viewer
   * URL is only available once the server is listening.
   */
  register(target) {
    const token = crypto.randomBytes(24).toString('hex');
    this._sessions.set(token, target);
    return token;
  }

  /** Binds the HTTP + WebSocket server to an ephemeral loopback port. */
  start() {
    if (this._server) return Promise.resolve(this._origin);

    this._server = http.createServer((req, res) => this._handleRequest(req, res));
    // Handshakes are validated in _handleUpgrade before ws sees them.
    this._wss = new WebSocketServer({
      noServer: true,
      // Echo back "binary" for websockify-style clients that ask for it; noVNC
      // itself requests no subprotocol.
      handleProtocols: (protocols) => (protocols.has('binary') ? 'binary' : false)
    });
    this._server.on('upgrade', (req, socket, head) => this._handleUpgrade(req, socket, head));

    return new Promise((resolve, reject) => {
      this._server.once('error', reject);
      this._server.listen(0, '127.0.0.1', () => {
        this._server.removeListener('error', reject);
        this._origin = `http://127.0.0.1:${this._server.address().port}`;
        resolve(this._origin);
      });
    });
  }

  /** The page a BrowserWindow should load for a registered token. */
  viewerUrl(token) {
    if (!this._origin) throw new Error('the VNC viewer server has not been started');
    return `${this._origin}/viewer.html?s=${token}`;
  }

  close() {
    if (this._wss) this._wss.close();
    if (this._server) this._server.close();
    this._sessions.clear();
    this._server = null;
    this._wss = null;
    this._origin = null;
  }

  // --- HTTP -------------------------------------------------------------------

  _handleRequest(req, res) {
    // Only this process's own windows may talk to the bridge. A Host header
    // naming anything but our loopback address means the request was steered
    // here through a name that resolves to 127.0.0.1 (DNS rebinding).
    if (!this._hostAllowed(req)) {
      return respond(res, 403, 'text/plain; charset=utf-8', 'forbidden');
    }
    if (req.method !== 'GET') {
      return respond(res, 405, 'text/plain; charset=utf-8', 'method not allowed');
    }

    const url = new URL(req.url, this._origin);
    const pathname = url.pathname;

    if (pathname === '/' || pathname === '/viewer.html') {
      return this._serveFile(res, this._viewerRoot, 'viewer.html');
    }
    if (pathname.startsWith('/novnc/')) {
      return this._serveFile(res, this._novncRoot, pathname.slice('/novnc/'.length));
    }
    if (pathname.startsWith('/session/')) {
      return this._serveSession(res, pathname.slice('/session/'.length));
    }
    return this._serveFile(res, this._viewerRoot, pathname.slice(1));
  }

  /**
   * The viewer's configuration, fetched by token rather than passed in the page
   * URL -- so a password never lands in a URL, a window title or a history entry.
   * Same-origin only: no CORS headers, plus the Host check above.
   */
  _serveSession(res, token) {
    const target = this._sessions.get(token);
    if (!target) return respond(res, 404, 'text/plain; charset=utf-8', 'unknown session');
    const body = JSON.stringify({
      label: target.label,
      host: target.host,
      port: target.port,
      wsPath: `/ws/${token}`,
      audio: target.audio ? { ...target.audio, wsPath: `/audio/${token}` } : null,
      credentials: target.credentials,
      options: target.options
    });
    respond(res, 200, MIME_TYPES['.json'], body);
  }

  _serveFile(res, root, relative) {
    // Resolve inside the root and refuse anything that escapes it.
    const absolute = path.resolve(root, relative);
    if (absolute !== root && !absolute.startsWith(root + path.sep)) {
      return respond(res, 403, 'text/plain; charset=utf-8', 'forbidden');
    }
    fs.readFile(absolute, (err, data) => {
      if (err) return respond(res, 404, 'text/plain; charset=utf-8', 'not found');
      const type = MIME_TYPES[path.extname(absolute).toLowerCase()] || 'application/octet-stream';
      respond(res, 200, type, data, this._contentSecurityPolicy());
    });
  }

  /**
   * Locks the viewer down to its own origin: it loads no remote code, and the
   * only socket it may open is the bridge below. 'unsafe-inline' is allowed for
   * styles because noVNC positions its canvas with inline styles.
   */
  _contentSecurityPolicy() {
    const self = this._origin;
    return [
      "default-src 'none'",
      "script-src 'self'",
      "style-src 'self' 'unsafe-inline'",
      "img-src 'self' data: blob:",
      "font-src 'self'",
      `connect-src 'self' ${self.replace('http://', 'ws://')}`,
      "frame-ancestors 'none'",
      "base-uri 'none'"
    ].join('; ');
  }

  _hostAllowed(req) {
    const expected = this._origin.replace('http://', '');
    return req.headers.host === expected;
  }

  // --- WebSocket bridge --------------------------------------------------------

  _handleUpgrade(req, socket, head) {
    const url = new URL(req.url, this._origin);
    const endpoint = this._resolveStream(url.pathname);

    // Cross-origin WebSocket handshakes are not blocked by the browser the way
    // cross-origin fetches are, so the origin is checked here: only the viewer
    // page this server itself served may open a bridge, never a remote page
    // that happens to be loaded in another window of this app.
    if (!endpoint || !this._hostAllowed(req) || req.headers.origin !== this._origin) {
      socket.write('HTTP/1.1 403 Forbidden\r\nConnection: close\r\n\r\n');
      return socket.destroy();
    }

    this._wss.handleUpgrade(req, socket, head, (ws) => this._bridge(ws, endpoint));
  }

  /**
   * Both of a session's streams live behind the same token: /ws/<token> is the
   * RFB connection and /audio/<token> the desktop audio, when the URL asked for
   * it. Anything else -- an unknown token, or audio on a session without it --
   * resolves to nothing and is refused.
   */
  _resolveStream(pathname) {
    const match = /^\/(ws|audio)\/([0-9a-f]+)$/.exec(pathname);
    if (!match) return null;
    const [, kind, token] = match;
    const target = this._sessions.get(token);
    if (!target) return null;
    if (kind === 'ws') {
      return { host: target.host, port: target.port, label: target.label };
    }
    if (!target.audio) return null;
    return { host: target.host, port: target.audio.port, label: `${target.label} audio` };
  }

  /**
   * Relays one WebSocket to one TCP connection, byte for byte, until either
   * end goes away. Close codes carry the reason to the viewer, which shows it.
   */
  _bridge(ws, endpoint) {
    const where = `${endpoint.host}:${endpoint.port}`;
    const tcp = net.connect({ host: endpoint.host, port: endpoint.port });
    tcp.setNoDelay(true); // RFB is latency-sensitive; don't let Nagle batch input.

    let closed = false;
    const shutdown = (code, reason) => {
      if (closed) return;
      closed = true;
      tcp.destroy();
      // Close reasons are capped at 123 bytes by the WebSocket protocol.
      if (ws.readyState === ws.OPEN) ws.close(code, reason.slice(0, 100));
      else ws.terminate();
    };

    tcp.on('connect', () => console.log(`vnc: bridging a viewer to ${where}`));

    tcp.on('data', (chunk) => {
      if (ws.readyState !== ws.OPEN) return;
      ws.send(chunk, () => {
        if (tcp.isPaused() && ws.bufferedAmount <= WS_HIGH_WATER_MARK) tcp.resume();
      });
      if (ws.bufferedAmount > WS_HIGH_WATER_MARK) tcp.pause();
    });

    tcp.on('error', (err) => {
      console.error(`vnc: connection to ${where} failed: ${err.message}`);
      shutdown(4000, err.message);
    });
    tcp.on('close', () => shutdown(1000, `${where} closed the connection`));

    // Input events are small, so this direction needs no flow control.
    ws.on('message', (data) => { if (!tcp.destroyed) tcp.write(data); });
    ws.on('close', () => shutdown(1000, 'viewer closed'));
    ws.on('error', (err) => {
      console.error(`vnc: viewer socket for ${where} failed: ${err.message}`);
      shutdown(1011, err.message);
    });
  }
}

function respond(res, status, contentType, body, csp) {
  const headers = {
    'Content-Type': contentType,
    'Content-Length': Buffer.byteLength(body),
    'Cache-Control': 'no-store',
    'X-Content-Type-Options': 'nosniff'
  };
  if (csp) headers['Content-Security-Policy'] = csp;
  res.writeHead(status, headers);
  res.end(body);
}

module.exports = {
  isVncUrl,
  parseVncUrl,
  resolveNovncRoot,
  VncViewerServer,
  DEFAULT_VNC_PORT,
  DEFAULT_AUDIO_PORT
};
