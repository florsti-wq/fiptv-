// fIPTV: „Weiterschauen“ für Anbieter-Filme/-Serien und Mediathek – gleiches Format wie
// die Android-App (FiptvResume.kt), damit Fire TV, Handy und Windows-App sich abgleichen
// (Feld `fiptvResumeJson` im Cloud-Profil, je Titel gewinnt der neuere Stand).

export type FiptvResumeKind = "MOVIE" | "EPISODE" | "MEDIATHEK";

export type FiptvResumeEntry = {
  url: string;
  kind: FiptvResumeKind;
  title: string;
  subtitle: string;
  image?: string;
  positionMs: number;
  durationMs: number;
  finished: boolean;
  updatedAt: number;
  dismissed: boolean;
  season?: number;
  episode?: number;
};

/** Angaben, die ein Bereich beim Abspielen mitgibt */
export type FiptvResumeInfo = { url: string; kind: FiptvResumeKind; title: string; subtitle?: string; image?: string; season?: number; episode?: number };

const MAX_ENTRIES = 300;
const MIN_POSITION_MS = 60_000;
const listeners = new Set<() => void>();
const cache = new Map<string, FiptvResumeEntry[]>();

const storageKey = (profileId: string) => `fiptv.resume.${profileId || "default"}`;

/** Gleiche Adresse trotz unterschiedlicher Schreibweise (Kodierung, Groß-/Kleinschreibung des Hosts) */
export function resumeUrlKey(url: string) {
  let value = url.trim();
  try { value = decodeURIComponent(value); } catch { /* bleibt */ }
  return value.replace(/^(https?:\/\/)([^/]+)/i, (_, scheme: string, host: string) => scheme.toLowerCase() + host.toLowerCase()).replace(/\/+$/, "");
}

export const isResumable = (e: FiptvResumeEntry) => !e.finished && e.positionMs >= MIN_POSITION_MS && e.durationMs > 0;
export const remainingMin = (e: FiptvResumeEntry) => Math.round(Math.max(0, e.durationMs - e.positionMs) / 60_000);
export const progressOf = (e: FiptvResumeEntry) => (e.durationMs > 0 ? Math.min(1, Math.max(0, e.positionMs / e.durationMs)) : 0);

type RawEntry = { url?: string; kind?: string; title?: string; sub?: string; img?: string; tmdb?: number; s?: number; e?: number; pos?: number; dur?: number; fin?: boolean; at?: number; dis?: boolean };

function fromRaw(o: RawEntry): FiptvResumeEntry | null {
  if (!o?.url) return null;
  const kind = o.kind === "EPISODE" || o.kind === "MEDIATHEK" ? o.kind : "MOVIE";
  return {
    url: o.url, kind, title: o.title ?? "", subtitle: o.sub ?? "", image: o.img || undefined,
    season: typeof o.s === "number" && o.s >= 0 ? o.s : undefined,
    episode: typeof o.e === "number" && o.e >= 0 ? o.e : undefined,
    positionMs: Number(o.pos) || 0, durationMs: Number(o.dur) || 0,
    finished: Boolean(o.fin), updatedAt: Number(o.at) || 0, dismissed: Boolean(o.dis)
  };
}

function toRaw(e: FiptvResumeEntry): RawEntry {
  return { url: e.url, kind: e.kind, title: e.title, sub: e.subtitle, img: e.image ?? "", tmdb: -1, s: e.season ?? -1, e: e.episode ?? -1, pos: e.positionMs, dur: e.durationMs, fin: e.finished, at: e.updatedAt, dis: e.dismissed };
}

function parseList(json: string | null | undefined): FiptvResumeEntry[] {
  try {
    const arr = JSON.parse(json || "[]") as RawEntry[];
    return Array.isArray(arr) ? arr.map(fromRaw).filter((e): e is FiptvResumeEntry => Boolean(e)) : [];
  } catch { return []; }
}

function load(profileId: string): FiptvResumeEntry[] {
  let list = cache.get(profileId);
  if (!list) {
    try { list = parseList(localStorage.getItem(storageKey(profileId))); } catch { list = []; }
    cache.set(profileId, list);
  }
  return list;
}

let lastWrite = 0;
function persist(profileId: string, force = false) {
  listeners.forEach((l) => l());
  const now = Date.now();
  if (!force && now - lastWrite < 15_000) return;
  lastWrite = now;
  let list = load(profileId);
  if (list.length > MAX_ENTRIES) {
    list = [...list].sort((a, b) => b.updatedAt - a.updatedAt).slice(0, MAX_ENTRIES);
    cache.set(profileId, list);
  }
  try { localStorage.setItem(storageKey(profileId), JSON.stringify(list.map(toRaw))); } catch { /* voll */ }
}

const indexOf = (list: FiptvResumeEntry[], url: string) => { const key = resumeUrlKey(url); return list.findIndex((e) => resumeUrlKey(e.url) === key); };

export function subscribeResume(listener: () => void) {
  listeners.add(listener);
  return () => { listeners.delete(listener); };
}

export function resumeEntry(profileId: string, url: string) {
  const list = load(profileId);
  return list[indexOf(list, url)];
}

/** Startposition in Sekunden, falls dort weitergeschaut werden kann */
export function resumeStartSeconds(profileId: string, url: string) {
  const e = resumeEntry(profileId, url);
  return e && isResumable(e) ? Math.floor(e.positionMs / 1000) : 0;
}

/** Titel vor dem Abspielen anmelden; vorhandene Position bleibt erhalten. */
export function registerResume(profileId: string, info: FiptvResumeInfo) {
  const list = load(profileId);
  const i = indexOf(list, info.url);
  const old = list[i];
  const entry: FiptvResumeEntry = {
    url: old?.url ?? info.url, kind: info.kind, title: info.title, subtitle: info.subtitle ?? "",
    image: info.image ?? old?.image, season: info.season, episode: info.episode,
    positionMs: old?.positionMs ?? 0, durationMs: old?.durationMs ?? 0, finished: old?.finished ?? false,
    updatedAt: old?.updatedAt ?? Date.now(), dismissed: old?.dismissed ?? false
  };
  if (i >= 0) list[i] = entry; else list.push(entry);
  persist(profileId, true);
}

/** Position vom Player */
export function reportResume(profileId: string, url: string, positionMs: number, durationMs: number, closing: boolean) {
  if (!url || !(durationMs > 0)) return;
  const list = load(profileId);
  const i = indexOf(list, url);
  if (i < 0) return;
  // Fertig, wenn höchstens 5 % fehlen oder bei langen Titeln nur noch der Abspann (2 Min.) läuft
  const finished = positionMs >= durationMs * 0.95 || (durationMs > 20 * 60_000 && durationMs - positionMs <= 2 * 60_000);
  list[i] = { ...list[i], positionMs: Math.round(positionMs), durationMs: Math.round(durationMs), finished, updatedAt: Date.now(), dismissed: false };
  persist(profileId, closing || finished);
}

export function removeResume(profileId: string, url: string) {
  const list = load(profileId);
  const i = indexOf(list, url);
  if (i < 0) return;
  list[i] = { ...list[i], dismissed: true, updatedAt: Date.now() };
  persist(profileId, true);
}

/** Angefangene Titel, neueste zuerst */
export function continueWatching(profileId: string, kinds: FiptvResumeKind[]) {
  return load(profileId)
    .filter((e) => kinds.includes(e.kind) && isResumable(e) && !e.dismissed)
    .sort((a, b) => b.updatedAt - a.updatedAt)
    .slice(0, 30);
}

// ---------- Abgleich ----------

/** Für die Cloud: die letzten 150 Einträge der letzten 120 Tage (wie Android). */
export function exportResumeJson(profileId: string): string | null {
  const cutoff = Date.now() - 120 * 24 * 60 * 60 * 1000;
  const list = load(profileId).filter((e) => e.updatedAt >= cutoff && e.durationMs > 0)
    .sort((a, b) => b.updatedAt - a.updatedAt).slice(0, 150);
  return list.length ? JSON.stringify(list.map(toRaw)) : null;
}

/** Cloud-Stand übernehmen – je Titel gewinnt der neuere Stand. */
export function mergeResumeJson(profileId: string, json: string | null | undefined) {
  const incoming = parseList(json);
  if (!incoming.length) return false;
  const list = load(profileId);
  let changed = false;
  for (const e of incoming) {
    const i = indexOf(list, e.url);
    if (i < 0) { list.push(e); changed = true; }
    else if (e.updatedAt > list[i].updatedAt) { list[i] = e; changed = true; }
  }
  if (changed) persist(profileId, true);
  return changed;
}

/** Zwei Cloud-Stände zusammenführen (ohne den lokalen Speicher anzufassen) */
export function mergeResumeLists(a: string | null | undefined, b: string | null | undefined): string | null {
  const out = new Map<string, FiptvResumeEntry>();
  for (const e of [...parseList(a), ...parseList(b)]) {
    const key = resumeUrlKey(e.url);
    const prev = out.get(key);
    if (!prev || e.updatedAt > prev.updatedAt) out.set(key, e);
  }
  const list = [...out.values()].sort((x, y) => y.updatedAt - x.updatedAt).slice(0, 150);
  return list.length ? JSON.stringify(list.map(toRaw)) : null;
}
