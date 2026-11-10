// fIPTV: Filme und Serien des IPTV-Anbieters zum Stöbern (wie der VOD-Reiter der
// Android-App). Lädt Kategorien und Titel über die Xtream-API des Anbieters.
import { buildXtreamPlayerApiUrl, fetchXtreamJson, playlistProxyHeaders, type XtreamInfo } from "./iptv";
import { inferQuality, movieVodUrl, seriesVodUrl, toSource, xtreamProviders } from "./xtreamVod";
import type { IptvPlaylistEntry, StreamSource } from "./types";
import { registerResume, resumeStartSeconds } from "./fiptvResume";

export type VodKind = "movies" | "series";

export type VodProvider = { key: string; name: string; info: XtreamInfo };

export type VodCategory = { id: string; name: string };

export type VodItem = {
  id: string;
  kind: VodKind;
  name: string;
  poster?: string;
  year?: string;
  rating?: string;
  ext?: string;
  added?: number;
  categoryId?: string;
};

export type VodEpisode = {
  id: string;
  season: number;
  episode: number;
  title: string;
  ext?: string;
  plot?: string;
  image?: string;
  duration?: string;
};

export type VodSeriesDetail = {
  plot?: string;
  cover?: string;
  genre?: string;
  cast?: string;
  rating?: string;
  seasons: number[];
  episodes: VodEpisode[];
};

type RawCategory = { category_id?: string | number; category_name?: string };
type RawItem = {
  stream_id?: string | number;
  series_id?: string | number;
  name?: string;
  title?: string;
  stream_icon?: string;
  cover?: string;
  year?: string | number;
  releaseDate?: string;
  release_date?: string;
  rating?: string | number;
  container_extension?: string;
  added?: string | number;
  last_modified?: string | number;
  category_id?: string | number;
};
type RawEpisode = {
  id?: string | number;
  episode_num?: string | number;
  season?: string | number;
  title?: string;
  container_extension?: string;
  info?: { plot?: string; movie_image?: string; duration?: string; container_extension?: string };
};
type RawSeriesInfo = {
  info?: { plot?: string; cover?: string; genre?: string; cast?: string; rating?: string | number };
  episodes?: Record<string, RawEpisode[]> | RawEpisode[][];
};

export function vodProviders(playlists: IptvPlaylistEntry[], kind: VodKind): VodProvider[] {
  return xtreamProviders(playlists ?? [], kind).map(({ info, name }) => ({
    key: `${info.baseUrl}|${info.username}`,
    name: name || new URL(info.baseUrl).hostname,
    info
  }));
}

function api(info: XtreamInfo, action: string, extra: Record<string, string> = {}) {
  const url = new URL(buildXtreamPlayerApiUrl(info.baseUrl, info.username, info.password, action));
  for (const [key, value] of Object.entries(extra)) url.searchParams.set(key, value);
  return url.toString();
}

function imageUrl(raw?: string) {
  const value = raw?.trim();
  if (!value) return undefined;
  if (value.startsWith("//")) return `https:${value}`;
  return /^https?:\/\//i.test(value) ? value : undefined;
}

function yearOf(item: RawItem) {
  const text = String(item.year ?? item.releaseDate ?? item.release_date ?? item.name ?? "");
  return text.match(/\b(19|20)\d{2}\b/)?.[0];
}

export async function loadVodCategories(provider: VodProvider, kind: VodKind, userAgent?: string): Promise<VodCategory[]> {
  const raw = await fetchXtreamJson<RawCategory[]>(api(provider.info, kind === "movies" ? "get_vod_categories" : "get_series_categories"), playlistProxyHeaders(userAgent));
  if (!Array.isArray(raw)) throw new Error("Der Anbieter hat keine Kategorien geliefert.");
  return raw
    .filter((entry) => entry.category_id !== undefined && entry.category_name)
    .map((entry) => ({ id: String(entry.category_id), name: String(entry.category_name).trim() }));
}

/** Titel einer Kategorie; ohne Kategorie alle Titel (kann bei großen Anbietern dauern). */
export async function loadVodItems(provider: VodProvider, kind: VodKind, categoryId?: string, userAgent?: string): Promise<VodItem[]> {
  const action = kind === "movies" ? "get_vod_streams" : "get_series";
  const raw = await fetchXtreamJson<RawItem[]>(api(provider.info, action, categoryId ? { category_id: categoryId } : {}), playlistProxyHeaders(userAgent));
  if (!Array.isArray(raw)) throw new Error("Der Anbieter hat keine Titel geliefert.");
  const items: VodItem[] = [];
  for (const entry of raw) {
    const id = kind === "movies" ? entry.stream_id : entry.series_id;
    const name = String(entry.name ?? entry.title ?? "").trim();
    if (id === undefined || !name) continue;
    const rating = Number(entry.rating);
    items.push({
      id: String(id),
      kind,
      name,
      poster: imageUrl(kind === "movies" ? entry.stream_icon : entry.cover ?? entry.stream_icon),
      year: yearOf(entry),
      rating: Number.isFinite(rating) && rating > 0 ? rating.toFixed(1) : undefined,
      ext: entry.container_extension,
      added: Number(entry.added ?? entry.last_modified) || undefined,
      categoryId: entry.category_id !== undefined ? String(entry.category_id) : undefined
    });
  }
  return items;
}

export async function loadSeriesDetail(provider: VodProvider, seriesId: string, userAgent?: string): Promise<VodSeriesDetail> {
  const raw = await fetchXtreamJson<RawSeriesInfo>(api(provider.info, "get_series_info", { series_id: seriesId }), playlistProxyHeaders(userAgent));
  const groups = raw?.episodes ? (Array.isArray(raw.episodes) ? raw.episodes : Object.values(raw.episodes)) : [];
  const episodes: VodEpisode[] = [];
  for (const group of groups) {
    for (const ep of group ?? []) {
      if (ep?.id === undefined) continue;
      const season = Number(ep.season) || 1;
      const episode = Number(ep.episode_num) || episodes.filter((e) => e.season === season).length + 1;
      episodes.push({
        id: String(ep.id),
        season,
        episode,
        title: String(ep.title ?? "").trim() || `Folge ${episode}`,
        ext: ep.container_extension ?? ep.info?.container_extension,
        plot: ep.info?.plot?.trim() || undefined,
        image: imageUrl(ep.info?.movie_image),
        duration: ep.info?.duration || undefined
      });
    }
  }
  episodes.sort((a, b) => a.season - b.season || a.episode - b.episode);
  const rating = Number(raw?.info?.rating);
  return {
    plot: raw?.info?.plot?.trim() || undefined,
    cover: imageUrl(raw?.info?.cover),
    genre: raw?.info?.genre || undefined,
    cast: raw?.info?.cast || undefined,
    rating: Number.isFinite(rating) && rating > 0 ? rating.toFixed(1) : undefined,
    seasons: [...new Set(episodes.map((e) => e.season))],
    episodes
  };
}

export function movieSource(provider: VodProvider, item: VodItem, userAgent?: string, profileId = "", fromStart = false): StreamSource {
  const url = movieVodUrl(provider.info, item.id, item.ext);
  const fiptvResume = { url, kind: "MOVIE" as const, title: item.name, subtitle: provider.name, image: item.poster };
  registerResume(profileId, fiptvResume);
  return { ...toSource(item.name, url, inferQuality(item.name), provider.name, userAgent), fiptvResume, resumePositionSeconds: fromStart ? 0 : resumeStartSeconds(profileId, url) };
}

export function episodeSource(provider: VodProvider, series: VodItem, episode: VodEpisode, userAgent?: string, profileId = "", fromStart = false): StreamSource {
  const label = `${series.name} – S${episode.season}E${episode.episode}${episode.title && !/^Folge \d+$/.test(episode.title) ? ` · ${episode.title}` : ""}`;
  const url = seriesVodUrl(provider.info, episode.id, episode.ext);
  const fiptvResume = {
    url, kind: "EPISODE" as const, title: `S${episode.season} E${episode.episode} · ${episode.title}`, subtitle: series.name,
    image: episode.image ?? series.poster, season: episode.season, episode: episode.episode
  };
  registerResume(profileId, fiptvResume);
  return { ...toSource(label, url, inferQuality(`${series.name} ${episode.title}`), provider.name, userAgent), fiptvResume, resumePositionSeconds: fromStart ? 0 : resumeStartSeconds(profileId, url) };
}
