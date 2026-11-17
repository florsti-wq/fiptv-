
/**
 * fIPTV: Animationen und Töne (Web / Windows).
 * Klänge werden per Web Audio erzeugt, keine Dateien nötig.
 * Abschaltbar in den Einstellungen; Animationen über <html data-fiptv-anim="off">.
 */

const ANIM_KEY = "fiptv.effects.animations";
const SOUND_KEY = "fiptv.effects.sounds";

type Listener = () => void;
const listeners = new Set<Listener>();

function read(key: string): boolean {
  try { return localStorage.getItem(key) !== "0"; } catch { return true; }
}

export function animationsEnabled(): boolean {
  if (typeof window === "undefined") return false;
  return read(ANIM_KEY);
}

export function soundsEnabled(): boolean {
  if (typeof window === "undefined") return false;
  return read(SOUND_KEY);
}

function applyAnimAttr() {
  if (typeof document === "undefined") return;
  document.documentElement.dataset.fiptvAnim = animationsEnabled() ? "on" : "off";
}

export function setAnimations(on: boolean) {
  try { localStorage.setItem(ANIM_KEY, on ? "1" : "0"); } catch { /* ignore */ }
  applyAnimAttr();
  listeners.forEach((l) => l());
}

export function setSounds(on: boolean) {
  try { localStorage.setItem(SOUND_KEY, on ? "1" : "0"); } catch { /* ignore */ }
  listeners.forEach((l) => l());
  if (on) playEffect("ok", true);
}

const SPACE_MODE_KEY = "fiptv.space.mode";
const SPACE_PAL_KEY = "fiptv.space.palette";
export const SPACE_PALETTES = ["Magenta und Türkis", "Feuer und Eis", "Smaragd und Violett", "Bei jedem Start anders"];
const sessionPalette = Math.floor(Math.random() * 3);

/** 0 = aus, 1 = Galaxie */
export function spaceMode(): number {
  if (typeof window === "undefined") return 0;
  try { const v = localStorage.getItem(SPACE_MODE_KEY); return v === null ? 1 : Number(v) || 0; } catch { return 1; }
}
export function setSpaceMode(v: number) {
  try { localStorage.setItem(SPACE_MODE_KEY, String(v)); } catch { /* ignore */ }
  listeners.forEach((l) => l());
}
/** gewählte Farbstimmung 0..2, 3 = zufällig */
export function spacePalette(): number {
  if (typeof window === "undefined") return 3;
  try { const v = localStorage.getItem(SPACE_PAL_KEY); return v === null ? 3 : Number(v) || 0; } catch { return 3; }
}
export function setSpacePalette(v: number) {
  try { localStorage.setItem(SPACE_PAL_KEY, String(v)); } catch { /* ignore */ }
  listeners.forEach((l) => l());
}
export function spacePaletteIndex(): number {
  const p = spacePalette();
  return p === 3 ? sessionPalette : p;
}

export function subscribeEffects(l: Listener): () => void {
  listeners.add(l);
  return () => { listeners.delete(l); };
}

let ctx: AudioContext | null = null;
function audio(): AudioContext | null {
  if (typeof window === "undefined") return null;
  try {
    if (!ctx) {
      const Ctor = window.AudioContext ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
      if (!Ctor) return null;
      ctx = new Ctor();
    }
    if (ctx.state === "suspended") void ctx.resume().catch(() => undefined);
    return ctx;
  } catch { return null; }
}

function tone(a: AudioContext, freq: number, dur: number, at: number, vol: number, type: OscillatorType = "sine") {
  const o = a.createOscillator();
  const g = a.createGain();
  o.type = type;
  o.frequency.value = freq;
  const t0 = a.currentTime + at;
  g.gain.setValueAtTime(0, t0);
  g.gain.linearRampToValueAtTime(vol, t0 + 0.008);
  g.gain.exponentialRampToValueAtTime(0.0008, t0 + dur);
  o.connect(g).connect(a.destination);
  o.start(t0);
  o.stop(t0 + dur + 0.05);
}

export type EffectSound = "start" | "nav" | "ok" | "star";

export function playEffect(which: EffectSound, force = false) {
  if (!force && !soundsEnabled()) return;
  const a = audio();
  if (!a) return;
  switch (which) {
    case "start": [523.25, 659.25, 783.99, 1046.5].forEach((f, i) => tone(a, f, 0.55, 0.05 + i * 0.11, 0.09)); break;
    case "nav": tone(a, 1400, 0.05, 0, 0.035, "triangle"); break;
    case "ok": tone(a, 880, 0.12, 0, 0.07); tone(a, 1318.5, 0.16, 0.07, 0.07); break;
    case "star": tone(a, 987.77, 0.1, 0, 0.07); tone(a, 1318.5, 0.2, 0.06, 0.07); break;
  }
}

let playbackActive = false;
export function setEffectsPlaybackActive(on: boolean) { playbackActive = on; }

/** Läuft gerade ein (fast) bildschirmfüllendes Video? Dann keine Klicks. */
function videoFillsScreen(): boolean {
  if (document.fullscreenElement) return true;
  for (const v of Array.from(document.querySelectorAll("video"))) {
    const r = v.getBoundingClientRect();
    if (r.width >= window.innerWidth * 0.9 && r.height >= window.innerHeight * 0.8) return true;
  }
  return false;
}

let installed = false;
let lastNav = 0;

/** Navigationsklick (Pfeiltasten) und OK-Ton (Enter), einmal global. */
export function installEffects() {
  if (installed || typeof window === "undefined") return;
  installed = true;
  applyAnimAttr();
  window.addEventListener("keydown", (e) => {
    if (!soundsEnabled() || playbackActive || (e.repeat && e.key === "Enter")) return;
    const target = e.target as HTMLElement | null;
    if (target && (target.tagName === "INPUT" || target.tagName === "TEXTAREA" || target.isContentEditable)) return;
    if (videoFillsScreen()) return;
    if (e.key === "ArrowUp" || e.key === "ArrowDown" || e.key === "ArrowLeft" || e.key === "ArrowRight") {
      const now = Date.now();
      if (now - lastNav < 70) return;
      lastNav = now;
      playEffect("nav");
    } else if (e.key === "Enter") {
      playEffect("ok");
    }
  }, { capture: true });
}

// fIPTV: Nachtmodus (Ton) – Kompressor gleicht laute und leise Stellen an.
const NIGHT_KEY = "fiptv.nightMode";
export function nightModeEnabled(): boolean {
  if (typeof window === "undefined") return false;
  try { return localStorage.getItem(NIGHT_KEY) === "1"; } catch { return false; }
}
export function setNightMode(on: boolean) {
  try { localStorage.setItem(NIGHT_KEY, on ? "1" : "0"); } catch { /* ignore */ }
  listeners.forEach((l) => l());
}

type NightChain = { ctx: AudioContext; source: MediaElementAudioSourceNode; comp: DynamicsCompressorNode; gain: GainNode };
const nightChains = new WeakMap<HTMLVideoElement, NightChain>();

/**
 * Hängt den Kompressor an ein Video. Nur bei Streams über Media Source (blob:), sonst
 * würde fremder Ton ohne CORS stumm. Einmal verbunden, wird nur noch umgeschaltet.
 */
export function applyNightMode(video: HTMLVideoElement | null) {
  if (!video) return;
  const on = nightModeEnabled();
  let chain = nightChains.get(video);
  if (!chain) {
    if (!on || !video.currentSrc.startsWith("blob:")) return;
    try {
      const Ctor = window.AudioContext ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
      if (!Ctor) return;
      const ctx = new Ctor();
      const source = ctx.createMediaElementSource(video);
      const comp = ctx.createDynamicsCompressor();
      comp.threshold.value = -30; comp.knee.value = 8; comp.ratio.value = 4; comp.attack.value = 0.008; comp.release.value = 0.25;
      const gain = ctx.createGain();
      chain = { ctx, source, comp, gain };
      nightChains.set(video, chain);
    } catch { return; }
  }
  const c = chain;
  try {
    c.source.disconnect(); c.comp.disconnect(); c.gain.disconnect();
    if (on) { c.source.connect(c.comp).connect(c.gain).connect(c.ctx.destination); c.gain.gain.value = 1.8; }
    else c.source.connect(c.ctx.destination);
    if (c.ctx.state === "suspended") void c.ctx.resume().catch(() => undefined);
  } catch { /* ignore */ }
}

// fIPTV: nach der Profilauswahl direkt mit dem letzten Sender starten
const START_LIVE_KEY = "fiptv.startLive";
export function startLiveEnabled(): boolean {
  if (typeof window === "undefined") return false;
  try { return localStorage.getItem(START_LIVE_KEY) === "1"; } catch { return false; }
}
export function setStartLive(on: boolean) {
  try { localStorage.setItem(START_LIVE_KEY, on ? "1" : "0"); } catch { /* ignore */ }
  listeners.forEach((l) => l());
}
let startLivePending = false;
let startLiveUsed = false;
/** Einmal pro App-Start: true, wenn Live TV den letzten Sender gleich abspielen soll. */
export function requestStartLive(): boolean {
  if (startLiveUsed || !startLiveEnabled()) return false;
  startLiveUsed = true; startLivePending = true;
  return true;
}
export function takeStartLive(): boolean {
  const p = startLivePending; startLivePending = false; return p;
}
