// Minimal Electron app: opens one window per URL given on the command line.
//
// Usage (inside the container, via launch.sh):
//   /app/launch.sh https://example.com https://webrtc.github.io/samples/
//
// Any argument that looks like a URL (http/https, or a file path) is opened in
// its own BrowserWindow. Chromium's own switches (--enable-features=..., etc.)
// are ignored here. If no URL is given we fall back to a sensible default.

const { app, BrowserWindow } = require('electron');

const DEFAULT_URL = 'https://webrtc.github.io/samples/';

// Pull the URLs out of argv. argv looks like:
//   [ electronBinary, appPath, '--some-chromium-switch', 'https://...', ... ]
// so we keep only the bits that parse as http(s)/file URLs.
function urlsFromArgv(argv) {
  return argv.filter((arg) => /^(https?|file):\/\//i.test(arg));
}

function createWindow(url) {
  const win = new BrowserWindow({
    width: 1280,
    height: 800,
    webPreferences: {
      // This app only displays remote pages; keep node out of the renderer.
      nodeIntegration: false,
      contextIsolation: true
    }
  });

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

app.whenReady().then(() => {
  const urls = urlsFromArgv(process.argv);
  const targets = urls.length > 0 ? urls : [DEFAULT_URL];

  for (const url of targets) {
    createWindow(url);
  }

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      createWindow(DEFAULT_URL);
    }
  });
});

app.on('window-all-closed', () => {
  app.quit();
});
