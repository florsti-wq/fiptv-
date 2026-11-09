const { contextBridge, ipcRenderer } = require("electron");
contextBridge.exposeInMainWorld("fiptvDesktop", {
  saveConfig: (config) => ipcRenderer.invoke("fiptv:save-config", config),
  hasConfig: () => ipcRenderer.invoke("fiptv:has-config")
});
