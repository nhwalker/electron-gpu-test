// Unit/integration tests for the vnc:// support in app/vnc.js.
//
// Everything here runs on plain Node (`node --test tests/`) -- the module is
// deliberately free of Electron imports, so URL parsing, the loopback viewer
// server and the WebSocket<->TCP bridge can all be exercised without a display,
// a container or a real VNC server.

'use strict';

const assert = require('node:assert/strict');
const http = require('node:http');
const net = require('node:net');
const { after, before, describe, it } = require('node:test');

const WebSocket = require('../app/node_modules/ws');
const { isVncUrl, parseVncUrl, VncViewerServer } = require('../app/vnc.js');

describe('parseVncUrl', () => {
  it('ignores arguments that are not vnc:// URLs', () => {
    assert.equal(parseVncUrl('https://example.test/'), null);
    assert.equal(parseVncUrl('--enable-features=Foo'), null);
    assert.equal(isVncUrl('vnc://host'), true);
    assert.equal(isVncUrl('VNC://host'), true);
  });

  it('defaults to the RFB port and sensible viewer options', () => {
    const target = parseVncUrl('vnc://desktop.example.test');
    assert.equal(target.host, 'desktop.example.test');
    assert.equal(target.port, 5900);
    assert.equal(target.label, 'vnc://desktop.example.test');
    // Absent credentials are absent, not empty: see the note in vnc.js.
    assert.deepEqual(target.credentials, {});
    assert.equal(target.options.viewOnly, false);
    assert.equal(target.options.resize, 'scale');
    assert.equal(target.options.shared, true);
    assert.equal(target.options.reconnect, true);
    assert.equal(target.options.grabKeys, true);
    assert.equal(target.options.quality, null);
  });

  it('takes a port literally but expands a display number', () => {
    assert.equal(parseVncUrl('vnc://host:5901').port, 5901);
    assert.equal(parseVncUrl('vnc://host:1').port, 5901); // display :1
    assert.equal(parseVncUrl('vnc://host:99').port, 5999);
    assert.equal(parseVncUrl('vnc://host:100').port, 100);
  });

  it('reads credentials from the userinfo, percent-decoded', () => {
    const target = parseVncUrl('vnc://alice:p%40ss%20word@host:5901');
    assert.deepEqual(target.credentials, { username: 'alice', password: 'p@ss word' });
  });

  it('falls back to the environment so passwords stay out of argv', () => {
    process.env.VNC_PASSWORD = 'from-env';
    try {
      assert.equal(parseVncUrl('vnc://host').credentials.password, 'from-env');
      // An explicit password still wins.
      assert.equal(parseVncUrl('vnc://:inline@host').credentials.password, 'inline');
    } finally {
      delete process.env.VNC_PASSWORD;
    }
  });

  it('parses the viewer options from the query string', () => {
    const target = parseVncUrl(
      'vnc://host:5901?view_only=1&resize=remote&shared=0&quality=3&compression=9&reconnect=off&grab_keys=false&title=Lab');
    assert.equal(target.options.viewOnly, true);
    assert.equal(target.options.resize, 'remote');
    assert.equal(target.options.shared, false);
    assert.equal(target.options.quality, 3);
    assert.equal(target.options.compression, 9);
    assert.equal(target.options.reconnect, false);
    assert.equal(target.options.grabKeys, false);
    assert.equal(target.options.title, 'Lab');
  });

  it('unwraps IPv6 literals for the TCP connection but keeps them in the label', () => {
    const target = parseVncUrl('vnc://[::1]:5901');
    assert.equal(target.host, '::1');
    assert.equal(target.port, 5901);
    assert.equal(target.label, 'vnc://[::1]:5901');
  });

  it('keys the storage partition on the remote host, not the loopback port', () => {
    assert.equal(parseVncUrl('vnc://host:5901').partition, 'persist:vnc-host_5901');
    assert.equal(parseVncUrl('vnc://host:5901?view_only=1').partition, 'persist:vnc-host_5901');
  });

  it('rejects a malformed vnc:// URL with a readable reason', () => {
    assert.throws(() => parseVncUrl('vnc://'), /no host/);
    assert.throws(() => parseVncUrl('vnc://host?resize=huge'), /resize must be/);
    assert.throws(() => parseVncUrl('vnc://host?view_only=maybe'), /expected a boolean/);
    assert.throws(() => parseVncUrl('vnc://host?quality=12'), /quality must be/);
  });
});

describe('VncViewerServer', () => {
  // Stands in for a VNC server: greets like RFB does, then echoes back whatever
  // the client sends, so both directions of the bridge can be observed.
  let vncServer;
  let vncPort;
  let viewer;
  let origin;
  let token;

  before(async () => {
    vncServer = net.createServer((socket) => {
      socket.write('RFB 003.008\n');
      socket.on('data', (chunk) => socket.write(chunk));
    });
    await new Promise((resolve) => vncServer.listen(0, '127.0.0.1', resolve));
    vncPort = vncServer.address().port;

    viewer = new VncViewerServer();
    token = viewer.register(parseVncUrl(`vnc://127.0.0.1:${vncPort}?password=secret`));
    origin = await viewer.start();
  });

  after(async () => {
    viewer.close();
    await new Promise((resolve) => vncServer.close(resolve));
  });

  it('binds to loopback only, on an ephemeral port', () => {
    assert.match(origin, /^http:\/\/127\.0\.0\.1:\d+$/);
    assert.equal(viewer.viewerUrl(token), `${origin}/viewer.html?s=${token}`);
  });

  it('serves the viewer page and the noVNC module tree', async () => {
    const page = await fetch(`${origin}/viewer.html`);
    assert.equal(page.status, 200);
    assert.match(page.headers.get('content-type'), /text\/html/);
    assert.match(page.headers.get('content-security-policy'), /script-src 'self'/);
    assert.match(await page.text(), /vnc-viewer|viewer\.js/);

    // rfb.js is the entry point; it imports siblings and ../vendor/pako, so the
    // whole package has to be reachable under /novnc/.
    const rfb = await fetch(`${origin}/novnc/core/rfb.js`);
    assert.equal(rfb.status, 200);
    assert.match(rfb.headers.get('content-type'), /javascript/);
    const pako = await fetch(`${origin}/novnc/vendor/pako/lib/zlib/inflate.js`);
    assert.equal(pako.status, 200);
  });

  it('hands the session config out by token, and nothing else', async () => {
    const config = await (await fetch(`${origin}/session/${token}`)).json();
    assert.equal(config.host, '127.0.0.1');
    assert.equal(config.port, vncPort);
    assert.equal(config.wsPath, `/ws/${token}`);
    assert.equal(config.credentials.password, 'secret');

    const unknown = await fetch(`${origin}/session/0123456789abcdef`);
    assert.equal(unknown.status, 404);
  });

  it('refuses requests that did not arrive over the loopback origin', async () => {
    // A page on another origin can only reach the server through a name that
    // resolves to 127.0.0.1 -- which shows up as a foreign Host header. (fetch()
    // refuses to set Host, so this one goes out over node:http.)
    const rebound = await get(`${origin}/session/${token}`, { host: 'evil.test' });
    assert.equal(rebound.status, 403);
    assert.equal((await get(`${origin}/session/${token}`)).status, 200);
  });

  it('refuses to serve files outside the viewer and noVNC roots', async () => {
    const escaped = await fetch(`${origin}/novnc/../../main.js`, { redirect: 'manual' });
    assert.notEqual(escaped.status, 200);
    // Percent-encoded traversal is normalised away by the URL parser, but a
    // path that resolves absolute would escape the root if it were joined
    // naively -- that is what the guard is there for.
    const absolute = await fetch(`${origin}/novnc//etc/passwd`);
    assert.equal(absolute.status, 403);
    const encoded = await fetch(`${origin}/%2e%2e/%2e%2e/main.js`);
    assert.notEqual(encoded.status, 200);
  });

  it('bridges the WebSocket to the VNC server in both directions', async () => {
    const ws = new WebSocket(`${origin.replace('http://', 'ws://')}/ws/${token}`, { origin });
    const received = [];
    const greeting = new Promise((resolve, reject) => {
      ws.on('message', (data) => {
        received.push(Buffer.from(data).toString('latin1'));
        if (received.length === 2) resolve(received);
      });
      ws.on('error', reject);
    });

    await new Promise((resolve, reject) => {
      ws.on('open', resolve);
      ws.on('error', reject);
    });
    // The RFB banner comes from the server unprompted; the echo proves the
    // browser->server direction carries bytes too.
    ws.send(Buffer.from('RFB 003.008\n', 'latin1'));

    assert.deepEqual(await greeting, ['RFB 003.008\n', 'RFB 003.008\n']);
    ws.close();
  });

  it('rejects a bridge handshake from another origin or an unknown token', async () => {
    const wsOrigin = origin.replace('http://', 'ws://');

    await assert.rejects(
      openWebSocket(`${wsOrigin}/ws/${token}`, { origin: 'https://evil.test' }),
      /403/);
    await assert.rejects(
      openWebSocket(`${wsOrigin}/ws/0123456789abcdef`, { origin }),
      /403/);
  });

  it('closes the viewer socket when the VNC server is unreachable', async () => {
    // Point a session at a port nothing is listening on.
    const deadPort = await closedPort();
    const deadToken = viewer.register(parseVncUrl(`vnc://127.0.0.1:${deadPort}`));
    const ws = new WebSocket(`${origin.replace('http://', 'ws://')}/ws/${deadToken}`, { origin });
    const closed = new Promise((resolve) => ws.on('close', (code, reason) => resolve({ code, reason: String(reason) })));
    const { code, reason } = await closed;
    assert.equal(code, 4000);
    assert.match(reason, /ECONNREFUSED/);
  });
});

/** A GET against the viewer server with full control over the request headers. */
function get(url, headers = {}) {
  return new Promise((resolve, reject) => {
    const req = http.request(url, { headers }, (res) => {
      res.resume();
      res.on('end', () => resolve({ status: res.statusCode }));
    });
    req.on('error', reject);
    req.end();
  });
}

function openWebSocket(url, options) {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(url, options);
    ws.on('open', () => resolve(ws));
    ws.on('error', reject);
  });
}

/** A loopback port that is guaranteed to have nothing listening on it. */
async function closedPort() {
  const probe = net.createServer();
  await new Promise((resolve) => probe.listen(0, '127.0.0.1', resolve));
  const port = probe.address().port;
  await new Promise((resolve) => probe.close(resolve));
  return port;
}
