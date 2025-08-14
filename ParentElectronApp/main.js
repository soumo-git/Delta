const { app, BrowserWindow, Menu } = require('electron');
const path = require('path');

function createWindow() {
  const win = new BrowserWindow({
    width: 1200,
    height: 800,
    icon: path.join(__dirname, 'assets', 'Icon.ico'),
    webPreferences: {
      nodeIntegration: true,
      contextIsolation: false
    }
  });

  win.loadFile('index.html');

  // Remove the default menu bar
  win.setMenuBarVisibility(false);
  win.removeMenu && win.removeMenu();
  Menu.setApplicationMenu(null);

  // ✅ Allow opening DevTools manually by pressing F12
  win.webContents.on('before-input-event', (event, input) => {
    if (input.key === 'F12') {
      win.webContents.openDevTools();
    }
    // Reload shortcut: Ctrl+R
    if (input.key.toLowerCase() === 'r' && input.control && !input.alt && !input.shift && !input.meta) {
      win.reload();
      event.preventDefault();
    }
  });
}

app.whenReady().then(createWindow);
