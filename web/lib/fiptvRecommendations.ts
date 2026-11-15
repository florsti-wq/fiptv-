/**
 * fIPTV: Empfehlungen nach dem zuletzt Gesehenen (wie Fire TV): „Empfohlen für dich“ und
 * „Weil du „X“ geschaut hast“. Quelle: TMDB-Empfehlungen zu den letzten Titeln aus „Weiterschauen“.
 */
import { mapTmdbItem, tmdb } from "@/lib/tmdb";
import type { Category, MediaItem } from "@/lib/types";

export const FIPTV_REC_PREFIX = "fiptv_rec_";
type TmdbList = { results?: Array<Record<string, unknown>> };
const cache = new Map<string, { at: number; items: MediaItem[] }>();

async function recommendationsFor(item: MediaItem): Promise<MediaItem[]> {
  const type = item.mediaType === "tv" ? "tv" : "movie";
  const key = `${type}:${item.id}`;
  const hit = cache.get(key);
  if (hit && Date.now() - hit.at < 6 * 3600_000) return hit.items;
  let list = await tmdb<TmdbList>(`/${type}/${item.id}/recommendations`).catch(() => null);
  if (!list?.results?.length) list = await tmdb<TmdbList>(`/${type}/${item.id}/similar`).catch(() => null);
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const items = (list?.results ?? []).map((r) => mapTmdbItem(r as any, item.mediaType));
  cache.set(key, { at: Date.now(), items });
  return items;
}

export async function buildRecommendationRows(watched: MediaItem[]): Promise<Category[]> {
  const sources = watched.filter((m) => m.id > 0).filter((m, i, all) => all.findIndex((o) => o.mediaType === m.mediaType && o.id === m.id) === i).slice(0, 4);
  if (!sources.length) return [];
  const seen = new Set(watched.map((m) => `${m.mediaType}:${m.id}`));
  const lists = await Promise.all(sources.map(async (s) => [s, await recommendationsFor(s)] as const));
  const counts = new Map<string, number>(), byKey = new Map<string, MediaItem>();
  for (const [, items] of lists) for (const m of items) {
    const k = `${m.mediaType}:${m.id}`;
    if (seen.has(k)) continue;
    counts.set(k, (counts.get(k) ?? 0) + 1);
    if (!byKey.has(k)) byKey.set(k, m);
  }
  const rows: Category[] = [];
  const used = new Set(seen);
  const mixed = [...byKey.entries()].sort((a, b) => (counts.get(b[0]) ?? 0) - (counts.get(a[0]) ?? 0)).map(([, m]) => m).slice(0, 20);
  if (mixed.length >= 6) {
    rows.push({ id: `${FIPTV_REC_PREFIX}mix`, title: "Empfohlen für dich", items: mixed });
    mixed.slice(0, 10).forEach((m) => used.add(`${m.mediaType}:${m.id}`));
  }
  for (const [src, items] of lists.slice(0, 2)) {
    const rest = items.filter((m) => !used.has(`${m.mediaType}:${m.id}`)).slice(0, 20);
    if (rest.length >= 5) {
      rows.push({ id: `${FIPTV_REC_PREFIX}${src.mediaType}_${src.id}`, title: `Weil du „${src.title}“ geschaut hast`, items: rest });
      rest.slice(0, 8).forEach((m) => used.add(`${m.mediaType}:${m.id}`));
    }
  }
  return rows;
}
