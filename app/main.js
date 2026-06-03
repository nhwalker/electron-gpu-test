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
