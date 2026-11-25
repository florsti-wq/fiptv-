/**
 * fIPTV: „Stream-Problem melden“ (Windows/Web). Merkt sich die letzten Player-Ereignisse und
 * schreibt auf Wunsch einen Bericht. Zugangsdaten werden nie aufgenommen: von Adressen bleibt nur der Server.
 */
import { LIVE_LATENCY_LABELS, liveLatency } from "@/lib/fiptvEffects";

const events: string[] = [];
let current: { url?: string; title?: string } = {};

export function redactUrl(url?: string | null): string {
  if (!url) return "–";
  try {
    const u = new URL(url.trim().split(" ")[0]);
    const ext = /\.([a-z0-9]{2,5})$/i.exec(u.pathname)?.[1];
    return `${u.protocol}//${u.host}/…${ext ? ` (…${ext})` : ""}`;
  } catch { return "(Adresse ausgeblendet)"; }
}
const redactText = (t: string) => t.replace(/https?:\/\/[^\s"']+/g, (m) => redactUrl(m));

export function reportEvent(text: string) {
  events.push(`${new Date().toLocaleTimeString("de-DE")}  ${redactText(text)}`);
  while (events.length > 40) events.shift();
}

/** Vom Player: welcher Stream läuft gerade (für den Bericht). */
export function reportStream(url: string | null | undefined, title?: string) {
  if (url === current.url) return;
  current = { url: url ?? undefined, title };
  reportEvent(`neuer Stream ${redactUrl(url)}`);
}

/** Ereignisse eines <video> mitschreiben; gibt eine Aufräumfunktion zurück. */
export function watchVideo(video: HTMLVideoElement): () => void {
  const on = (name: string, text: string) => { const f = () => reportEvent(text); video.addEventListener(name, f); return () => video.removeEventListener(name, f); };
  const offs = [
    on("waiting", "lädt / puffert"), on("stalled", "Daten bleiben aus"), on("playing", "läuft"), on("ended", "Stream zu Ende"),
    on("emptied", "Quelle gewechselt"),
  ];
  const onError = () => reportEvent(`FEHLER Medien-Code ${video.error?.code ?? "?"}: ${video.error?.message ?? ""}`);
  video.addEventListener("error", onError);
  return () => { offs.forEach((f) => f()); video.removeEventListener("error", onError); };
}

export function buildStreamReport(channelName: string, extra: Record<string, string> = {}): string {
  const video = document.querySelector<HTMLVideoElement>("video");
  const lines: string[] = [];
  lines.push("fIPTV – Stream-Bericht (Windows/Web)");
  lines.push(`Erstellt: ${new Date().toLocaleString("de-DE")}`);
  lines.push(`App: ${"fiptvDesktop" in window ? "Windows-App" : "Browser"} · ${navigator.userAgent}`);
  const conn = (navigator as unknown as { connection?: { effectiveType?: string; downlink?: number } }).connection;
  if (conn) lines.push(`Netz: ${conn.effectiveType ?? "?"}, ca. ${conn.downlink ?? "?"} Mbit/s (Schätzung des Browsers)`);
  lines.push("");
  lines.push(`Sender: ${channelName}`);
  lines.push(`Server: ${redactUrl(current.url)}`);
  if (video) {
    const q = video.getVideoPlaybackQuality?.();
    let ahead = 0;
    for (let i = 0; i < video.buffered.length; i++) if (video.buffered.start(i) <= video.currentTime && video.currentTime <= video.buffered.end(i)) ahead = video.buffered.end(i) - video.currentTime;
    lines.push(`Zustand: ${video.error ? "Fehler" : video.paused ? "pausiert" : video.readyState < 3 ? "puffert" : "läuft"}`);
    lines.push(`Bild: ${video.videoWidth}×${video.videoHeight}`);
    lines.push(`Puffer voraus: ${ahead.toFixed(1)} s · Tempo ${video.playbackRate}`);
    if (q) lines.push(`Bilder: gezeigt ${q.totalVideoFrames}, ausgelassen ${q.droppedVideoFrames}`);
    if (video.error) lines.push(`Letzter Fehler: Medien-Code ${video.error.code} ${video.error.message}`);
  } else lines.push("Zustand: kein Player offen");
  lines.push(`Live-Verzögerung: ${LIVE_LATENCY_LABELS[liveLatency()]}`);
  for (const [k, v] of Object.entries(extra)) lines.push(`${k}: ${v}`);
  lines.push("", "Letzte Ereignisse:", ...events.map((e) => `  ${e}`));
  return lines.join("\n");
}

/** Bericht in die Zwischenablage legen und als Datei speichern. */
export async function saveStreamReport(text: string): Promise<string> {
  let copied = false;
  try { await navigator.clipboard.writeText(text); copied = true; } catch { /* egal */ }
  try {
    const a = document.createElement("a");
    a.href = URL.createObjectURL(new Blob([text], { type: "text/plain;charset=utf-8" }));
    a.download = "fiptv-stream-bericht.txt";
    document.body.appendChild(a); a.click(); a.remove();
    setTimeout(() => URL.revokeObjectURL(a.href), 5000);
    return copied ? "Stream-Bericht kopiert und als Datei gespeichert" : "Stream-Bericht als Datei gespeichert";
  } catch { return copied ? "Stream-Bericht in die Zwischenablage kopiert" : "Bericht konnte nicht gespeichert werden"; }
}
