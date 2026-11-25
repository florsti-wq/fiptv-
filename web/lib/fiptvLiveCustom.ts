/**
 * fIPTV: eigene Sendergruppen und selbst zugeordnete Programmdaten (Windows/Web).
 * Gespeichert im Browser-Speicher dieses Geräts; Änderungen lösen das Ereignis
 * „fiptv-live-custom“ aus.
 */
import type { IptvNowNext } from "@/lib/types";

export type CustomGroup = { id: string; name: string; channelIds: string[] };

const GROUPS_KEY = "fiptv.customGroups";
const EPG_KEY = "fiptv.epgMap";
export const CUSTOM_GROUP_PREFIX = "fiptv:";
export const PRESET_GROUP_NAMES = ["Kinder", "Nachrichten", "Sport", "Filme", "Serien", "Dokus", "Musik"];

function read<T>(key: string, fallback: T): T {
  if (typeof window === "undefined") return fallback;
  try { const v = localStorage.getItem(key); return v ? JSON.parse(v) as T : fallback; } catch { return fallback; }
}
function write(key: string, value: unknown) {
  try { localStorage.setItem(key, JSON.stringify(value)); } catch { /* ignore */ }
  if (typeof window !== "undefined") window.dispatchEvent(new CustomEvent("fiptv-live-custom"));
}

// ---------- Gruppen ----------
export function customGroups(): CustomGroup[] { return read<CustomGroup[]>(GROUPS_KEY, []); }
export function customGroupById(categoryId: string): CustomGroup | undefined {
  return categoryId.startsWith(CUSTOM_GROUP_PREFIX) ? customGroups().find(g => CUSTOM_GROUP_PREFIX + g.id === categoryId) : undefined;
}
export function createCustomGroup(name: string, firstChannelId?: string): CustomGroup {
  const g: CustomGroup = { id: Date.now().toString(36), name: name.trim() || "Meine Sender", channelIds: firstChannelId ? [firstChannelId] : [] };
  write(GROUPS_KEY, [...customGroups(), g]);
  return g;
}
export function deleteCustomGroup(id: string) { write(GROUPS_KEY, customGroups().filter(g => g.id !== id)); }
export function renameCustomGroup(id: string, name: string) {
  write(GROUPS_KEY, customGroups().map(g => g.id === id ? { ...g, name: name.trim() || g.name } : g));
}
export function toggleInCustomGroup(id: string, channelId: string) {
  write(GROUPS_KEY, customGroups().map(g => g.id !== id ? g
    : { ...g, channelIds: g.channelIds.includes(channelId) ? g.channelIds.filter(c => c !== channelId) : [...g.channelIds, channelId] }));
}
export function moveInCustomGroup(id: string, channelId: string, delta: -1 | 1) {
  write(GROUPS_KEY, customGroups().map(g => {
    if (g.id !== id) return g;
    const list = [...g.channelIds];
    const i = list.indexOf(channelId), j = i + delta;
    if (i < 0 || j < 0 || j >= list.length) return g;
    list.splice(i, 1); list.splice(j, 0, channelId);
    return { ...g, channelIds: list };
  }));
}

// ---------- Programmdaten zuordnen ----------
export function epgMap(): Record<string, string> { return read<Record<string, string>>(EPG_KEY, {}); }
export function setEpgSource(channelId: string, sourceId: string | null) {
  const m = { ...epgMap() };
  if (!sourceId || sourceId === channelId) delete m[channelId]; else m[channelId] = sourceId;
  write(EPG_KEY, m);
}
/** Programmdaten der Quell-Sender auf die zugeordneten Sender übertragen. */
export function applyEpgMap(nowNext: Record<string, IptvNowNext>, map: Record<string, string>): Record<string, IptvNowNext> {
  const entries = Object.entries(map);
  if (!entries.length) return nowNext;
  let out: Record<string, IptvNowNext> | null = null;
  for (const [target, source] of entries) {
    const guide = nowNext[source];
    if (!guide) continue;
    out ??= { ...nowNext };
    out[target] = guide;
  }
  return out ?? nowNext;
}

// ---------- Zeitversatz der Programmdaten ----------
const OFFSET_KEY = "fiptv.epgOffset";
type OffsetStore = { global: number; channels: Record<string, number> };
export const EPG_OFFSET_CHOICES = [-180, -120, -90, -60, -30, 0, 30, 60, 90, 120, 180];
export function epgOffsets(): OffsetStore {
  const v = read<OffsetStore>(OFFSET_KEY, { global: 0, channels: {} });
  return { global: Number(v.global) || 0, channels: v.channels ?? {} };
}
export function setGlobalEpgOffset(minutes: number) { write(OFFSET_KEY, { ...epgOffsets(), global: minutes }); }
/** null = wie alle Sender */
export function setChannelEpgOffset(channelId: string, minutes: number | null) {
  const o = epgOffsets();
  const channels = { ...o.channels };
  if (minutes === null) delete channels[channelId]; else channels[channelId] = minutes;
  write(OFFSET_KEY, { ...o, channels });
}
export function epgOffsetLabel(minutes: number): string {
  if (!minutes) return "Kein Versatz";
  const sign = minutes > 0 ? "+" : "−";
  const abs = Math.abs(minutes);
  return abs % 60 === 0 ? `${sign}${abs / 60} Std.` : `${sign}${abs} Min.`;
}
/** Alle Sendungszeiten verschieben und „jetzt / als Nächstes“ neu bestimmen. */
export function applyEpgOffsets(nowNext: Record<string, IptvNowNext>, o: OffsetStore, now = Date.now()): Record<string, IptvNowNext> {
  if (!o.global && !Object.keys(o.channels).length) return nowNext;
  const out: Record<string, IptvNowNext> = {};
  for (const [id, g] of Object.entries(nowNext)) {
    const minutes = o.channels[id] ?? o.global;
    if (!minutes) { out[id] = g; continue; }
    const delta = minutes * 60_000;
    const seen = new Set<string>();
    const all = [...(g.recent ?? []), g.now, g.next, g.later, ...(g.upcoming ?? [])]
      .filter((p): p is NonNullable<typeof p> => Boolean(p))
      .filter((p) => { const k = `${p.startUtcMillis}|${p.endUtcMillis}|${p.title}`; if (seen.has(k)) return false; seen.add(k); return true; })
      .map((p) => ({ ...p, startUtcMillis: p.startUtcMillis + delta, endUtcMillis: p.endUtcMillis + delta }))
      .sort((a, b) => a.startUtcMillis - b.startUtcMillis);
    const future = all.filter((p) => p.startUtcMillis > now);
    out[id] = {
      now: all.filter((p) => p.startUtcMillis <= now && now < p.endUtcMillis).pop(),
      next: future[0],
      later: future[1],
      upcoming: future,
      recent: all.filter((p) => p.endUtcMillis <= now),
    };
  }
  return out;
}
