// fIPTV: Mediatheken (ARD, ZDF, arte, 3sat …) über die freie Schnittstelle von
// MediathekViewWeb – wie in der Android-App. Bilder liefert die Schnittstelle nicht,
// deshalb sucht fIPTV passende Sendungs-/Filmbilder bei TMDB (nur bei gleichem Namen).
import { tmdb } from "./tmdb";
import type { StreamSource } from "./types";
import { registerResume, resumeStartSeconds } from "./fiptvResume";

export type MediathekItem = {
  id: string;
  channel: string;
  topic: string;
  title: string;
  description?: string;
  /** Sendezeit in ms */
  startMs: number;
  durationSec: number;
  videoHd?: string;
  videoSd?: string;
  videoLow?: string;
  subtitleUrl?: string;
};

export type MediathekPage = { items: MediathekItem[]; total?: number };

export const MEDIATHEK_CHANNELS: { id: string; label: string; color: string }[] = [
  { id: "ard", label: "ARD", color: "#0b3d91" },
  { id: "zdf", label: "ZDF", color: "#f07800" },
  { id: "arte", label: "arte", color: "#e4312b" },
  { id: "3sat", label: "3sat", color: "#9c1c8c" },
  { id: "phoenix", label: "phoenix", color: "#0073b7" },
  { id: "kika", label: "KiKA", color: "#2ea43a" },
  { id: "br", label: "BR", color: "#0067b3" },
  { id: "hr", label: "HR", color: "#005eb8" },
  { id: "mdr", label: "MDR", color: "#004b87" },
  { id: "ndr", label: "NDR", color: "#1460aa" },
  { id: "rbb", label: "rbb", color: "#e31f25" },
  { id: "swr", label: "SWR", color: "#00a0d2" },
  { id: "wdr", label: "WDR", color: "#00305e" },
  { id: "orf", label: "ORF", color: "#d7141a" },
  { id: "srf", label: "SRF", color: "#af001e" }
];

function channelInfo(channel: string) {
  const lower = channel.toLowerCase();
  return MEDIATHEK_CHANNELS.find((c) => lower.includes(c.id));
}
export const channelLabel = (channel: string) => channelInfo(channel)?.label ?? channel;
export const channelColor = (channel: string) => channelInfo(channel)?.color ?? "#3a3f55";

export const bestVideo = (item: MediathekItem) => item.videoHd ?? item.videoSd ?? item.videoLow;

/** Themen, die nur Sammelbehälter sind (z. B. „Filme im Ersten“) – dann zählt der Titel. */
const CONTAINER = /(film|kino|movie|doku|reportage|dokumentation|serie|krimi|thriller|komödie|drama)/i;
export const isContainerTopic = (topic: string) => !topic.trim() || CONTAINER.test(topic);
export const showKey = (item: MediathekItem) => (isContainerTopic(item.topic) ? `t:${item.title.toLowerCase()}` : `s:${item.topic.toLowerCase()}`);

export function formatDuration(sec: number) {
  if (sec <= 0) return "";
  const m = Math.round(sec / 60);
  if (m >= 60) {
    const h = Math.floor(m / 60);
    const r = m % 60;
    return r > 0 ? `${h} Std. ${r} Min.` : `${h} Std.`;
  }
  return `${Math.max(1, m)} Min.`;
}

/** Suchtext wie auf mediathekviewweb.de: !Sender #Thema +Titel *Beschreibung, sonst Titel+Thema */
function parseSearch(text?: string) {
  const out: { fields: string[]; query: string }[] = [];
  const free: string[] = [];
  const add = (fields: string[], q: string) => { const v = q.replace(/,/g, " ").trim(); if (v) out.push({ fields, query: v }); };
  for (const tok of (text ?? "").trim().split(/\s+/).filter(Boolean)) {
    const rest = tok.slice(1);
    switch (tok[0]) {
      case "!": add(["channel"], rest); break;
      case "#": add(["topic"], rest); break;
      case "+": add(["title"], rest); break;
      case "*": add(["description"], rest); break;
      default: free.push(tok);
    }
  }
  if (free.length) add(["title", "topic"], free.join(" "));
  return out;
}

export type MediathekQuery = { search?: string; channel?: string; topic?: string; minDurationSec?: number; offset?: number; size?: number };

export function buildQuery(q: MediathekQuery) {
  const queries = parseSearch(q.search);
  if (q.channel) queries.push({ fields: ["channel"], query: q.channel });
  if (q.topic) queries.push({ fields: ["topic"], query: q.topic.replace(/,/g, " ").trim() });
  return {
    queries,
    sortBy: "timestamp",
    sortOrder: "desc",
    future: false,
    offset: q.offset ?? 0,
    size: q.size ?? 40,
    ...(q.minDurationSec && q.minDurationSec > 0 ? { duration_min: q.minDurationSec } : {})
  };
}

const httpUrl = (v: unknown) => (typeof v === "string" && /^https?:\/\//i.test(v.trim()) ? v.trim() : undefined);

type RawResult = Record<string, unknown>;

export function parseResponse(json: { err?: unknown[] | null; result?: { results?: RawResult[]; queryInfo?: { totalResults?: number } } }): MediathekPage {
  if (Array.isArray(json.err) && json.err.length) throw new Error(`Mediathek: ${json.err.join(", ")}`);
  const items: MediathekItem[] = [];
  for (const r of json.result?.results ?? []) {
    const hd = httpUrl(r.url_video_hd);
    const sd = httpUrl(r.url_video);
    const low = httpUrl(r.url_video_low);
    if (!hd && !sd && !low) continue;
    const channel = String(r.channel ?? "");
    const topic = String(r.topic ?? "");
    const title = String(r.title ?? "") || topic;
    const timestamp = Number(r.timestamp) || 0;
    const description = String(r.description ?? "").trim();
    items.push({
      id: String(r.id ?? "") || `${channel}|${topic}|${title}|${timestamp}`,
      channel, topic, title,
      description: description || undefined,
      startMs: timestamp * 1000,
      durationSec: Number(r.duration) || 0,
      videoHd: hd, videoSd: sd, videoLow: low,
      subtitleUrl: httpUrl(r.url_subtitle)
    });
  }
  const total = json.result?.queryInfo?.totalResults;
  return { items, total: typeof total === "number" && total >= 0 ? total : undefined };
}

export async function queryMediathek(q: MediathekQuery, signal?: AbortSignal): Promise<MediathekPage> {
  const response = await fetch("/api/fiptv/mediathek", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(buildQuery(q)),
    signal
  });
  if (!response.ok) throw new Error(`Mediathek nicht erreichbar (HTTP ${response.status})`);
  return parseResponse(await response.json());
}

// ---------- Bilder (TMDB) ----------
export type MediathekArt = { backdrop?: string; poster?: string };
export const artTile = (art?: MediathekArt | null) => art?.backdrop ? `https://image.tmdb.org/t/p/w780${art.backdrop}` : art?.poster ? `https://image.tmdb.org/t/p/w500${art.poster}` : undefined;
export const artHero = (art?: MediathekArt | null) => art?.backdrop ? `https://image.tmdb.org/t/p/w1280${art.backdrop}` : art?.poster ? `https://image.tmdb.org/t/p/w780${art.poster}` : undefined;

const ART_KEY = "fiptv.mediathek.art.v1";
const MISS_RETRY_MS = 7 * 24 * 60 * 60 * 1000;
type StoredArt = { b?: string; p?: string; miss?: number };
let artStore: Record<string, StoredArt> | null = null;
const pending = new Map<string, Promise<MediathekArt>>();
let running = 0;
const queue: (() => void)[] = [];

function store(): Record<string, StoredArt> {
  if (artStore) return artStore;
  try { artStore = JSON.parse(localStorage.getItem(ART_KEY) || "{}") as Record<string, StoredArt>; } catch { artStore = {}; }
  return artStore;
}
let saveTimer: ReturnType<typeof setTimeout> | undefined;
function persist() {
  clearTimeout(saveTimer);
  saveTimer = setTimeout(() => { try { localStorage.setItem(ART_KEY, JSON.stringify(store())); } catch { /* voll */ } }, 800);
}

const norm = (text: string) => text.toLowerCase()
  .replace(/ä/g, "ae").replace(/ö/g, "oe").replace(/ü/g, "ue").replace(/ß/g, "ss")
  .replace(/[^a-z0-9]/g, "");

/** „Der Fall X (1/3) - Hörfassung | ARD“ → „Der Fall X“ */
const clean = (text: string) => text
  .replace(/\(.*?\)|\[.*?\]/g, " ")
  .split(/ - | \| | – |: /)[0]
  .replace(/\b(hörfassung|audiodeskription|originalversion|ov|mit untertitel|in gebärdensprache)\b/gi, " ")
  .trim();

function artQuery(item: MediathekItem) {
  const movie = isContainerTopic(item.topic);
  const q = clean(movie ? item.title : item.topic);
  if (norm(q).length < 3) return null;
  return { q, movie, key: `${movie ? "m" : "s"}:${norm(q)}` };
}

export function cachedArt(item: MediathekItem): MediathekArt | null | undefined {
  const info = artQuery(item);
  if (!info) return null;
  const hit = store()[info.key];
  if (!hit) return undefined;
  if (hit.miss) return Date.now() - hit.miss > MISS_RETRY_MS ? undefined : null;
  return { backdrop: hit.b, poster: hit.p };
}

type TmdbHit = { name?: string; title?: string; original_name?: string; original_title?: string; backdrop_path?: string | null; poster_path?: string | null };

async function limited<T>(task: () => Promise<T>): Promise<T> {
  if (running >= 4) await new Promise<void>((resolve) => queue.push(resolve));
  running += 1;
  try { return await task(); } finally { running -= 1; queue.shift()?.(); }
}

export function lookupArt(item: MediathekItem): Promise<MediathekArt | null> {
  const cached = cachedArt(item);
  if (cached !== undefined) return Promise.resolve(cached);
  const info = artQuery(item);
  if (!info) return Promise.resolve(null);
  const existing = pending.get(info.key);
  if (existing) return existing;
  const job = limited(async () => {
    const wanted = norm(info.q);
    const match = (list?: TmdbHit[]) => (list ?? []).find((r) =>
      [r.name, r.title, r.original_name, r.original_title].some((n) => n && norm(clean(n)) === wanted) && (r.backdrop_path || r.poster_path));
    const search = (kind: "tv" | "movie") => tmdb<{ results?: TmdbHit[] }>(`search/${kind}`, { query: info.q, language: "de-DE" }).then((r) => match(r.results));
    const hit = info.movie ? (await search("movie")) ?? (await search("tv")) : (await search("tv")) ?? (await search("movie"));
    const art: MediathekArt = { backdrop: hit?.backdrop_path ?? undefined, poster: hit?.poster_path ?? undefined };
    store()[info.key] = art.backdrop || art.poster ? { b: art.backdrop, p: art.poster } : { miss: Date.now() };
    persist();
    return art.backdrop || art.poster ? art : ({} as MediathekArt);
  }).catch(() => ({} as MediathekArt)).finally(() => pending.delete(info.key));
  pending.set(info.key, job);
  return job;
}

// ---------- Abspielen ----------
export function mediathekSource(item: MediathekItem, profileId = "", fromStart = false, image?: string): StreamSource | null {
  const url = bestVideo(item);
  if (!url) return null;
  const sender = channelLabel(item.channel);
  const fiptvResume = {
    url, kind: "MEDIATHEK" as const, title: item.title,
    subtitle: [sender, item.topic !== item.title ? item.topic : ""].filter(Boolean).join(" · "), image
  };
  registerResume(profileId, fiptvResume);
  return {
    source: `${sender} · ${item.title}`,
    addonName: "Mediathek",
    addonId: "fiptv_mediathek",
    quality: item.videoHd ? "HD" : "SD",
    size: "",
    url,
    description: item.description ?? null,
    fiptvResume,
    resumePositionSeconds: fromStart ? 0 : resumeStartSeconds(profileId, url)
  };
}
