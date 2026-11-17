const { contextBridge, ipcRenderer } = require("electron");
contextBridge.exposeInMainWorld("fiptvDesktop", {
  saveConfig: (config) => ipcRenderer.invoke("fiptv:save-config", config),
  hasConfig: () => ipcRenderer.invoke("fiptv:has-config"),
  // fIPTV: Aufnahmen
  recordStart: (input) => ipcRenderer.invoke("fiptv:record-start", input),
  recordStop: (id) => ipcRenderer.invoke("fiptv:record-stop", id),
  recordList: () => ipcRenderer.invoke("fiptv:record-list"),
  openRecordingsFolder: () => ipcRenderer.invoke("fiptv:record-open-folder")
});
