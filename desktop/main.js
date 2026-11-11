// fIPTV für Windows: startet die fIPTV-Webapp (Next.js) im Hintergrund auf diesem PC
// und zeigt sie in einem eigenen Fenster. Sender und Streams laufen über den eigenen
// Internetanschluss, genau wie beim Fire TV.
const { app, BrowserWindow, Menu, ipcMain, shell, dialog } = require("electron");
const { spawn } = require("node:child_process");
const fs = require("node:fs");
const http = require("node:http");
const path = require("node:path");

// Fester Port: Profile/Anmeldung liegen pro Adresse im Browser-Speicher.
const PORT = 38917;
const APP_URL = `http://localhost:${PORT}/`;
// Sync-Server-Adresse kommt aus dem Build (backend.json), sonst die bisherige Netlify-Adresse
// fIPTV: Google-Anmeldung für YouTube (Zugang vom Typ „TVs and Limited Input devices“)
const YT_OAUTH = (() => {
  try { const b = require("./backend.json"); return { id: b.ytClientId || "", secret: b.ytClientSecret || "" }; } catch { return { id: "", secret: "" }; }
})();
const BACKEND = (() => {
  try { return require("./backend.json").url || "https://fipsite.netlify.app/.netlify/functions"; } catch { return "https://fipsite.netlify.app/.netlify/functions"; }
})();

let server = null;
let mainWindow = null;
let quitting = false;

if (!app.requestSingleInstanceLock()) {
  app.quit();
} else {
  app.on("second-instance", () => {
    if (mainWindow) { if (mainWindow.isMinimized()) mainWindow.restore(); mainWindow.focus(); }
  });
}

const configPath = () => path.join(app.getPath("userData"), "config.json");
const logPath = () => path.join(app.getPath("userData"), "server.log");
function readConfig() {
  try { return JSON.parse(fs.readFileSync(configPath(), "utf8")); } catch { return {}; }
}
function writeConfig(config) {
  fs.mkdirSync(path.dirname(configPath()), { recursive: true });
  fs.writeFileSync(configPath(), JSON.stringify(config, null, 2));
}

// Der Server liegt gepackt in resources/server.tgz (electron-builder übernimmt keine
// node_modules) und wird pro Version einmal nach %APPDATA%\fIPTV\server entpackt.
function serverDir() {
  if (!app.isPackaged) return path.join(__dirname, "server");
  return path.join(app.getPath("userData"), "server", app.getVersion());
}

function ensureServerFiles() {
  if (!app.isPackaged) return Promise.resolve();
  const target = serverDir();
  if (fs.existsSync(path.join(target, "server.js"))) return Promise.resolve();
  const base = path.dirname(target);
  const temp = `${target}.tmp`;
  fs.rmSync(temp, { recursive: true, force: true });
  fs.mkdirSync(temp, { recursive: true });
  const tar = path.join(process.env.SystemRoot || "C:\\Windows", "System32", "tar.exe");
  return new Promise((resolve, reject) => {
    const child = spawn(tar, ["-xzf", path.join(process.resourcesPath, "server.tgz"), "-C", temp], { windowsHide: true });
    child.on("error", reject);
    child.on("exit", (code) => {
      if (code !== 0) { reject(new Error(`tar ${code}`)); return; }
      fs.renameSync(temp, target);
      // Alte Versionen aufräumen
      for (const name of fs.readdirSync(base)) {
        if (name !== app.getVersion()) fs.rmSync(path.join(base, name), { recursive: true, force: true });
      }
      resolve();
    });
  });
}

function startServer(config) {
  stopServer();
  const log = fs.createWriteStream(logPath(), { flags: "w" });
  server = spawn(process.execPath, [path.join(serverDir(), "server.js")], {
    cwd: serverDir(),
    windowsHide: true,
    stdio: ["ignore", "pipe", "pipe"],
    env: {
      ...process.env,
      ELECTRON_RUN_AS_NODE: "1",
      NODE_ENV: "production",
      PORT: String(PORT),
      HOSTNAME: "127.0.0.1",
      TMDB_API_KEY: config.tmdbKey || "",
      FIPTV_HOME_PROXY: "true",
      ALLOW_NETLIFY_MEDIA_PROXY: "true",
      NETLIFY_BACKEND_URL: BACKEND,
      FIPTV_YT_CLIENT_ID: YT_OAUTH.id,
      FIPTV_YT_CLIENT_SECRET: YT_OAUTH.secret,
      NEXT_TELEMETRY_DISABLED: "1"
    }
  });
  server.stdout.pipe(log);
  server.stderr.pipe(log);
  server.on("exit", (code) => {
    server = null;
    if (!quitting && code !== 0) {
      dialog.showErrorBox("fIPTV", `Der fIPTV-Dienst wurde beendet (Code ${code}).\nDetails: ${logPath()}`);
    }
  });
}

function stopServer() {
  if (server) { try { server.kill(); } catch { /* schon beendet */ } server = null; }
}

function waitForServer(timeoutMs = 60000) {
  const started = Date.now();
  return new Promise((resolve, reject) => {
    const attempt = () => {
      const req = http.get(APP_URL, (res) => { res.resume(); resolve(); });
      req.on("error", () => {
        if (Date.now() - started > timeoutMs) reject(new Error("timeout"));
        else setTimeout(attempt, 400);
      });
      req.setTimeout(3000, () => req.destroy());
    };
    attempt();
  });
}

function isAppUrl(url) {
  try { const u = new URL(url); return u.hostname === "localhost" && u.port === String(PORT); } catch { return false; }
}

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1400,
    height: 860,
    minWidth: 900,
    minHeight: 560,
    backgroundColor: "#000000",
    title: "fIPTV",
    icon: path.join(__dirname, "build", "icon.png"),
    autoHideMenuBar: true,
    show: false,
    webPreferences: { preload: path.join(__dirname, "preload.js"), contextIsolation: true, sandbox: true }
  });
  mainWindow.once("ready-to-show", () => mainWindow.show());
  mainWindow.on("closed", () => { mainWindow = null; });

  const wc = mainWindow.webContents;
  // Warnungen/Fehler der Seite mitschreiben (Alt → fIPTV → Protokoll öffnen zeigt server.log;
  // daneben liegt renderer.log), ohne Zugangsdaten aus Adressen.
  const rendererLog = fs.createWriteStream(path.join(app.getPath("userData"), "renderer.log"), { flags: "w" });
  wc.on("console-message", (event) => {
    const level = event.level ?? event.params?.level;
    if (level === "info" || level === "debug" || level === 0 || level === 1) return;
    const text = String(event.message ?? event.params?.message ?? "").replace(/(username=|password=|\/(?:movie|series|live)\/)[^&/\s"]+(\/[^&/\s"]+)?/gi, "$1***");
    rendererLog.write(`${new Date().toISOString()} ${level} ${text.slice(0, 2000)}\n`);
  });
  // Neue Fenster (Links mit target=_blank) im normalen Browser öffnen.
  wc.setWindowOpenHandler(({ url }) => {
    if (/^https?:/i.test(url) && !isAppUrl(url)) shell.openExternal(url);
    return { action: "deny" };
  });
  // Anmeldung (fipsite) bleibt im Fenster; VLC & Co. (vlc://, Downloads) an Windows übergeben.
  wc.on("will-navigate", (event, url) => {
    if (/^https?:/i.test(url)) return;
    if (url.startsWith("file:")) return;
    event.preventDefault();
    shell.openExternal(url).catch(() => undefined);
  });
  return mainWindow;
}

function showSetup(win, message) {
  win.loadFile(path.join(__dirname, "setup.html"), { query: message ? { msg: message } : {} });
}

async function launch() {
  const win = mainWindow || createWindow();
  const config = readConfig();
  if (!config.tmdbKey) { showSetup(win); return; }
  win.loadURL(`data:text/html;charset=utf-8,${encodeURIComponent(LOADING_HTML)}`);
  try { await ensureServerFiles(); } catch (error) {
    showSetup(win, "fIPTV konnte nicht eingerichtet werden: " + (error?.message || error));
    return;
  }
  startServer(config);
  try {
    await waitForServer();
    win.loadURL(APP_URL);
  } catch {
    showSetup(win, "Der fIPTV-Dienst startet nicht. Details stehen in " + logPath());
  }
}

const LOADING_HTML = `<!doctype html><html><body style="margin:0;height:100vh;display:grid;place-items:center;background:#000;color:#cbd5e1;font-family:Segoe UI,sans-serif">
<div style="text-align:center"><div style="font-size:42px;font-weight:700;background:linear-gradient(90deg,#22D3EE,#A855F7);-webkit-background-clip:text;color:transparent">fIPTV</div>
<div style="margin-top:12px;opacity:.8">wird gestartet …</div></div></body></html>`;

ipcMain.handle("fiptv:save-config", async (_event, input) => {
  const tmdbKey = String(input?.tmdbKey || "").trim().replace(/^TMDB_API_KEY\s*[=:]\s*/i, "").replace(/^["']|["']$/g, "");
  if (tmdbKey.length < 20) return { ok: false, error: "Das sieht nicht nach einem TMDB-Schlüssel aus." };
  writeConfig({ ...readConfig(), tmdbKey });
  launch();
  return { ok: true };
});
ipcMain.handle("fiptv:has-config", () => Boolean(readConfig().tmdbKey));

function buildMenu() {
  const template = [
    {
      label: "fIPTV",
      submenu: [
        { label: "Neu laden", accelerator: "F5", click: () => mainWindow?.webContents.reload() },
        { label: "Vollbild", accelerator: "F11", click: () => mainWindow?.setFullScreen(!mainWindow.isFullScreen()) },
        { type: "separator" },
        { label: "TMDB-Schlüssel ändern …", click: () => { stopServer(); if (mainWindow) showSetup(mainWindow); } },
        { label: "Protokoll öffnen", click: () => shell.openPath(logPath()) },
        { label: "Nach Updates suchen", click: () => checkUpdates(true) },
        { type: "separator" },
        { label: "Entwicklertools", accelerator: "Ctrl+Shift+I", click: () => mainWindow?.webContents.toggleDevTools() },
        { role: "quit", label: "Beenden" }
      ]
    },
    { label: "Bearbeiten", submenu: [{ role: "undo", label: "Rückgängig" }, { role: "redo", label: "Wiederholen" }, { type: "separator" }, { role: "cut", label: "Ausschneiden" }, { role: "copy", label: "Kopieren" }, { role: "paste", label: "Einfügen" }, { role: "selectAll", label: "Alles auswählen" }] }
  ];
  Menu.setApplicationMenu(Menu.buildFromTemplate(template));
}

function checkUpdates(manual = false) {
  if (!app.isPackaged) return;
  let autoUpdater;
  try { ({ autoUpdater } = require("electron-updater")); } catch { return; }
  autoUpdater.autoDownload = true;
  autoUpdater.removeAllListeners();
  autoUpdater.on("update-downloaded", async (info) => {
    const { response } = await dialog.showMessageBox({
      type: "info",
      buttons: ["Jetzt neu starten", "Später"],
      defaultId: 0,
      title: "fIPTV-Update",
      message: `fIPTV ${info.version} ist bereit.`,
      detail: "Neu starten, um das Update zu installieren?"
    });
    if (response === 0) { quitting = true; stopServer(); autoUpdater.quitAndInstall(); }
  });
  if (manual) {
    autoUpdater.once("update-not-available", () => dialog.showMessageBox({ type: "info", title: "fIPTV", message: `fIPTV ${app.getVersion()} ist aktuell.` }));
    autoUpdater.once("error", (error) => dialog.showMessageBox({ type: "warning", title: "fIPTV", message: "Update-Prüfung fehlgeschlagen.", detail: String(error?.message || error) }));
  }
  autoUpdater.checkForUpdates().catch(() => undefined);
}

// Die App-Seite darf Filme direkt beim Anbieter laden (z. B. für MKV-Umwandlung im
// Player). Browser verlangen dafür CORS-Freigaben, die IPTV-Anbieter nicht senden –
// in der eigenen App ergänzen wir sie für Anfragen dieser Seite.
function allowProviderCors() {
  const { session } = require("electron");
  const origin = `http://localhost:${PORT}`;
  session.defaultSession.webRequest.onHeadersReceived((details, callback) => {
    let url;
    try { url = new URL(details.url); } catch { callback({}); return; }
    const fromApp = (details.referrer || "").startsWith(origin) || details.webContentsId === mainWindow?.webContents.id;
    if (!fromApp || isAppUrl(details.url) || !/^https?:$/.test(url.protocol) || url.hostname === "fipsite.netlify.app") { callback({}); return; }
    const headers = { ...details.responseHeaders };
    for (const key of Object.keys(headers)) if (/^access-control-/i.test(key)) delete headers[key];
    headers["Access-Control-Allow-Origin"] = [origin];
    headers["Access-Control-Allow-Headers"] = ["*"];
    headers["Access-Control-Allow-Methods"] = ["GET, HEAD, OPTIONS"];
    headers["Access-Control-Expose-Headers"] = ["Content-Length, Content-Range, Content-Type, Accept-Ranges"];
    const preflight = details.method === "OPTIONS";
    callback(preflight ? { responseHeaders: headers, statusLine: "HTTP/1.1 204 No Content" } : { responseHeaders: headers });
  });
}

app.whenReady().then(() => {
  allowProviderCors();
  buildMenu();
  createWindow();
  launch();
  setTimeout(() => checkUpdates(false), 15000);
});

app.on("window-all-closed", () => { quitting = true; stopServer(); app.quit(); });
app.on("before-quit", () => { quitting = true; stopServer(); });
