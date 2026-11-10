// fIPTV: YouTube-Kanäle des Profils (gleiches JSON wie Fire TV: fiptv_yt_channels).
export type YtChannel = { id: string; title: string; thumb: string };
export type YtVideo = { id: string; title: string; published: string; thumb: string; views?: number; description: string; channelId: string; channelTitle: string };

export function decodeChannels(json: string | undefined): YtChannel[] {
  try {
    const list = JSON.parse(json || "[]");
    return Array.isArray(list) ? list.filter((c) => c && c.id).map((c) => ({ id: String(c.id), title: String(c.title ?? ""), thumb: String(c.thumb ?? "") })) : [];
  } catch { return []; }
}
export const encodeChannels = (list: YtChannel[]) => JSON.stringify(list.map(({ id, title, thumb }) => ({ id, title, thumb })));

const cache = new Map<string, { at: number; videos: YtVideo[] }>();

export async function channelVideos(channel: YtChannel): Promise<YtVideo[]> {
  const hit = cache.get(channel.id);
  if (hit && Date.now() - hit.at < 10 * 60_000) return hit.videos;
  const response = await fetch(`/api/fiptv/youtube?channel=${encodeURIComponent(channel.id)}`, { cache: "no-store" });
  const data = await response.json();
  if (!response.ok) throw new Error(data.error || "YouTube nicht erreichbar");
  const videos: YtVideo[] = (data.videos ?? []).map((v: Omit<YtVideo, "channelId" | "channelTitle">) => ({ ...v, channelId: channel.id, channelTitle: channel.title || data.title }));
  cache.set(channel.id, { at: Date.now(), videos });
  return videos;
}

export async function resolveChannel(input: string): Promise<YtChannel> {
  const response = await fetch(`/api/fiptv/youtube?resolve=${encodeURIComponent(input)}`, { cache: "no-store" });
  const data = await response.json();
  if (!response.ok) throw new Error(data.error || "Kanal nicht gefunden");
  return { id: data.id, title: data.title, thumb: data.thumb };
}

export function ago(iso: string) {
  const t = new Date(iso).getTime();
  if (!t) return "";
  const min = Math.round((Date.now() - t) / 60_000);
  if (min < 60) return `vor ${Math.max(1, min)} Min.`;
  const h = Math.round(min / 60);
  if (h < 24) return `vor ${h} Std.`;
  const d = Math.round(h / 24);
  if (d < 30) return d === 1 ? "gestern" : `vor ${d} Tagen`;
  const m = Math.round(d / 30);
  return m < 12 ? `vor ${m} Mon.` : `vor ${Math.round(m / 12)} J.`;
}
export const views = (n?: number) => (n ? `${n >= 1e6 ? `${(n / 1e6).toFixed(1).replace(".", ",")} Mio.` : n >= 1e3 ? `${Math.round(n / 1e3)} Tsd.` : n} Aufrufe` : "");
