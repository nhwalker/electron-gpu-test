// Minimal Electron app: opens one window per URL given on the command line.
//
// Usage (inside the container, via launch.sh):
//   /app/launch.sh https://example.com https://webrtc.github.io/samples/
//   /app/launch.sh vnc://desktop.example.test:5901
//
// Any argument that looks like a URL (http/https, or a file path) is opened in
// its own BrowserWindow. A vnc:// URL is opened too, in a noVNC-backed viewer
// page the app serves and bridges itself (see vnc.js). Chromium's own switches
// (--enable-features=..., etc.) are ignored here. If no URL is given we fall
// back to a sensible default.

const { app, BrowserWindow } = require('electron');
const vnc = require('./vnc');

const DEFAULT_URL = 'https://webrtc.github.io/samples/';

// --- Persistent web storage ---------------------------------------------------
// All web storage (cookies, localStorage, IndexedDB, Cache Storage, service
// workers) lives under Electron's userData directory. The container is
// ephemeral, so by default that directory -- and every logged-in session -- is
// thrown away when the container stops. Point userData at a path the operator
// can mount as a volume (ELECTRON_USER_DATA) to make it survive container
// recreation. Unset -> the normal default (~/.config/<appName>), unchanged
// behavior. Must run before app is ready, hence at module load.
if (process.env.ELECTRON_USER_DATA) {
  app.setPath('userData', process.env.ELECTRON_USER_DATA);
}

// Serves the noVNC viewer page and bridges its WebSocket to the VNC server's TCP
// port. Started lazily: it only binds a port if a vnc:// URL was actually asked
// for.
const vncServer = new vnc.VncViewerServer();

// Per-URL storage isolation: each window gets its own *persistent* session
// partition keyed by the page's origin, so different sites can't read each
// other's cookies/storage and each remembers its own login across restarts.
// Electron stores a 'persist:'-prefixed partition under userData/Partitions/<name>,
// so these ride along on the same volume. file:// URLs (no host) share one
// "local" partition.
function partitionForUrl(url) {
  let key = 'local';
  try {
    const u = new URL(url);
    if (u.host) key = `${u.protocol.replace(':', '')}-${u.host}`;
  } catch (_) {
    // Not a parseable URL; fall back to the shared "local" partition.
  }
  // Keep the partition name to a safe, filesystem-friendly charset.
  return `persist:${key.replace(/[^a-zA-Z0-9._-]/g, '_')}`;
}

// Pull the targets out of argv. argv looks like:
//   [ electronBinary, appPath, '--some-chromium-switch', 'https://...', ... ]
// so we keep only the bits that parse as http(s)/file/vnc URLs. A malformed
// vnc:// URL is reported and skipped rather than taking the whole app down.
function targetsFromArgv(argv) {
  const targets = [];
  for (const arg of argv) {
    if (/^(https?|file):\/\//i.test(arg)) {
      targets.push({ url: arg, partition: partitionForUrl(arg) });
    } else if (vnc.isVncUrl(arg)) {
      try {
        targets.push({ vnc: vnc.parseVncUrl(arg) });
      } catch (err) {
        console.error(`vnc: ignoring "${arg}": ${err.message}`);
      }
    }
  }
  return targets;
}

function createWindow(url, options = {}) {
  const { partition = partitionForUrl(url), grabKeys = false } = options;
  const win = new BrowserWindow({
    width: 1280,
    height: 800,
    webPreferences: {
      // This app only displays remote pages; keep node out of the renderer.
      nodeIntegration: false,
      contextIsolation: true,
      // Isolate each origin's storage into its own persistent partition.
      partition
    }
  });

  // A remote desktop should receive every keystroke, including the ones the
  // default menu claims (Ctrl+R reload, Ctrl+W close, F11, ...) -- reloading the
  // viewer would drop the session. This suppresses menu accelerators while the
  // page has focus, without touching the page's own key handling.
  if (grabKeys && typeof win.webContents.setIgnoreMenuShortcuts === 'function') {
    win.webContents.setIgnoreMenuShortcuts(true);
  }

  win.loadURL(url);
  return win;
}

// Mutual TLS: when a server asks for a client certificate, Chromium offers the
// identities found in the app user's NSS DB (populated by setup_cert_store in
// launch.sh). We pick the first match and log it. The handler MUST call the real
// callback with a certificate from the list -- if it doesn't, Chromium blocks the
// whole browser waiting for a selection. Note the app-level event passes
// webContents and url BEFORE the certificate list (a 5-arg signature). Server-CA
// trust needs no handler -- importing the CA into NSS is what verifies the server.
app.on('select-client-certificate', (event, webContents, url, list, callback) => {
  if (!list || list.length === 0) return; // nothing to offer; let the default path run
  event.preventDefault();
  const chosen = list[0];
  console.log(`cert-store: selected client certificate "${chosen.subjectName}" for ${url}`);
  callback(chosen);
});

// Opt-in, INSECURE escape hatch for dev/test only: trust any server cert. Off by
// default -- normal operation relies on the CA imported into NSS verifying.
if (process.env.TLS_INSECURE_SKIP_VERIFY === '1') {
  console.warn('cert-store: TLS_INSECURE_SKIP_VERIFY=1 -- accepting ALL server certificates (insecure)');
  app.on('certificate-error', (event, _webContents, _url, _error, _certificate, callback) => {
    event.preventDefault();
    callback(true);
  });
}

app.whenReady().then(async () => {
  const targets = targetsFromArgv(process.argv);
  if (targets.length === 0) {
    targets.push({ url: DEFAULT_URL, partition: partitionForUrl(DEFAULT_URL) });
  }

  // Bring the loopback viewer server up before opening any VNC window, so the
  // window has a URL to load. If it can't bind, the VNC targets are dropped
  // (with a reason) rather than opening windows that can never connect.
  const vncTargets = targets.filter((target) => target.vnc);
  if (vncTargets.length > 0) {
    try {
      for (const target of vncTargets) {
        target.token = vncServer.register(target.vnc);
      }
      const origin = await vncServer.start();
      console.log(`vnc: serving the noVNC viewer from ${origin}`);
    } catch (err) {
      console.error(`vnc: could not start the viewer server: ${err.message}`);
      for (const target of vncTargets) {
        target.token = null;
      }
    }
  }

  let opened = 0;
  for (const target of targets) {
    if (target.vnc) {
      if (!target.token) continue;
      console.log(`vnc: opening ${target.vnc.label}`);
      createWindow(vncServer.viewerUrl(target.token), {
        partition: target.vnc.partition,
        grabKeys: target.vnc.options.grabKeys
      });
    } else {
      createWindow(target.url, { partition: target.partition });
    }
    opened += 1;
  }

  if (opened === 0) {
    console.error('No window could be opened; exiting.');
    app.exit(1);
    return;
  }

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      createWindow(DEFAULT_URL);
    }
  });
});

app.on('window-all-closed', () => {
  vncServer.close();
  app.quit();
});
