/**
 * fIPTV: Senderliste aufräumen (wie Fire TV) – Anzeigenamen ohne Länderkürzel/Zusätze, Trenner ausblenden,
 * gleiche Sender derselben Gruppe zusammenfassen (beste Qualität laut Name vorne).
 */
import type { IptvChannel } from "@/lib/types";

const KEY = "fiptv.channelCleanup";
export function channelCleanupEnabled(): boolean {
  try { return localStorage.getItem(KEY) !== "0"; } catch { return true; }
}
export function setChannelCleanupEnabled(on: boolean) {
  try { localStorage.setItem(KEY, on ? "1" : "0"); window.dispatchEvent(new Event("fiptv-channel-cleanup")); } catch { /* ignore */ }
}

const PIPE_PREFIX = /^\s*\|\s*[A-Z]{2,4}\s*\|\s*/;
const PREFIX = /^\s*(?:[[(|]\s*)?[A-Z]{2,3}(?:[\s-]?[A-Z]{2})?\s*(?:[\])|:]|\s[|•\-–►➥▶]|➥|►)\s*/;
const DECOR = /[ᴴᴰᵁ⁴ᴷ]+|[★☆●◉◆◇■□▶►➥✦✪]+/g;
const TAGS = /\s*[[(](?:backup|bkp|alt|geo[- ]?blocked|not 24\/7|vip|test|multi|multi[- ]?audio|raw|h\.?265|hevc|50 ?fps|60 ?fps)[\])]\s*/gi;
const QUALITY = /(?<![A-Za-z0-9])(?:UHD|4K|2160p?|FHD|1080[pi]?|HD\+?|720p?|SD|576[pi]?|HEVC|H\.?265|RAW|50fps|60fps)(?![A-Za-z0-9])/gi;
const SEPARATOR = /^\s*[#=*\-_~•|]{3,}.*|.*[#=*\-_~]{4,}\s*$/;

export function cleanChannelName(name: string): string {
  const n = name.replace(PIPE_PREFIX, "").replace(PREFIX, "").replace(TAGS, " ").replace(DECOR, " ").replace(QUALITY, " ")
    .replace(/\s{2,}/g, " ").trim().replace(/^[-|:•–\s]+|[-|:•–\s]+$/g, "").trim();
  return n || name.trim();
}

export function channelQualityRank(name: string): number {
  if (/(?<![a-z0-9])(uhd|4k|2160)/i.test(name)) return 4;
  if (/(?<![a-z0-9])(fhd|1080)/i.test(name)) return 3;
  if (/(?<![a-z0-9])(hd|720)/i.test(name)) return 2;
  if (/(?<![a-z0-9])(sd|576)/i.test(name)) return 1;
  return 0;
}

export function qualityLabel(name: string): string {
  return ["", "SD", "HD", "FHD", "4K"][channelQualityRank(name)];
}

/** Trenner raus, Dubletten zusammenfassen. Gibt die Liste in Original-Reihenfolge zurück (je Sender die beste Variante). */
export function tidyChannels(channels: IptvChannel[]): { list: IptvChannel[]; variants: Map<string, IptvChannel[]> } {
  const groups = new Map<string, IptvChannel[]>();
  const keyOf = (c: IptvChannel) => `${(c.group ?? "").toLowerCase()}|${cleanChannelName(c.name).toLowerCase().replace(/[^\p{L}\p{N}]+/gu, "")}`;
  for (const c of channels) {
    if (SEPARATOR.test(c.name.trim())) continue;
    const k = keyOf(c);
    const list = groups.get(k);
    if (list) list.push(c); else groups.set(k, [c]);
  }
  const variants = new Map<string, IptvChannel[]>();
  const best = new Map<string, IptvChannel>();
  for (const [k, list] of groups) {
    const sorted = list.length > 1 ? [...list].sort((a, b) => channelQualityRank(b.name) - channelQualityRank(a.name)) : list;
    best.set(k, sorted[0]);
    if (sorted.length > 1) for (const c of sorted) variants.set(c.id, sorted);
  }
  const out: IptvChannel[] = [];
  const emitted = new Set<string>();
  for (const c of channels) {
    if (SEPARATOR.test(c.name.trim())) continue;
    const k = keyOf(c);
    if (emitted.has(k)) continue;
    emitted.add(k);
    out.push(best.get(k) ?? c);
  }
  return { list: out, variants };
}
