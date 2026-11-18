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
