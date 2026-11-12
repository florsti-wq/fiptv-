/**
 * fIPTV: Wo läuft das Spiel? – Gegenstück zu Android `SportSourceFinder`.
 * 1) TV-Sender deines Anbieters (Sendername wie „Bayern vs Köln“ oder passende Sendung im Programmführer)
 * 2) Sport-Kataloge installierter Stremio-Addons.
 */
import { loadIptvGuideForChannels } from "@/lib/iptv";
import { loadSportsAddonEvents, sportsEventCatalogs, type SportsAddonEvent } from "@/lib/sportsAddons";
import type { InstalledAddon, IptvChannel, IptvNowNext, IptvPlaylistEntry, IptvProgram } from "@/lib/types";
import type { SportMatch } from "@/lib/fiptvSport";

export type TvSource = { channel: IptvChannel; program?: IptvProgram; score: number; uhd: boolean };
export type GameSources = { tv: TvSource[]; addons: SportsAddonEvent[] };

const STOP = new Set(["fc", "sc", "sv", "vfl", "vfb", "tsg", "fsv", "borussia", "bayer", "eintracht", "club", "cf", "afc", "ac",
  "as", "ssc", "rb", "rc", "sk", "fk", "cd", "ud", "real", "sporting", "united", "city", "the", "de", "la",
  "1846", "1899", "1860", "04", "05", "09", "1", "06", "olympique", "athletic"]);
const SYNONYMS: Record<string, string[]> = {
  munich: ["munchen", "muenchen", "bayern"], bayern: ["fcb"], cologne: ["koln", "koeln"], koln: ["koeln", "cologne"],
  monchengladbach: ["gladbach", "moenchengladbach"], gladbach: ["monchengladbach", "moenchengladbach"],
  dortmund: ["bvb"], hamburger: ["hamburg", "hsv"], nuremberg: ["nurnberg", "nuernberg"], leverkusen: ["b04"],
  frankfurt: ["sge"], milan: ["mailand"], turin: ["juventus"], lisbon: ["lissabon"], naples: ["neapel", "napoli"], napoli: ["neapel"],
};
const SPORTS_CHANNEL = / (sport|sports|sky|dazn|bundesliga|champions|uefa|liga|ligue|football|fussball|soccer|event|ppv|bein|eurosport|magenta|prime|amazon|ard|zdf|das erste|sat 1|rtl|prosieben|servus|sport1|canal|rmc|movistar|tnt|espn|arena|nova) /;
const FOOTBALL = / (bundesliga|champions|league|uefa|fussball|football|soccer|liga|pokal|live|spieltag|cup) /;
const UHD = / (4k|uhd|2160p?) /;

export function norm(s: string): string {
  const t = s.toLowerCase().replace(/ä/g, "a").replace(/ö/g, "o").replace(/ü/g, "u").replace(/ß/g, "ss")
    .normalize("NFD").replace(/\p{M}+/gu, "");
  return ` ${t.replace(/[^a-z0-9]+/g, " ").trim()} `;
}
const has = (text: string, word: string) => text.includes(` ${word} `);

export function keywords(name: string): Set<string> {
  const words = norm(name).trim().split(" ").filter((w) => w.length >= 4 && !STOP.has(w));
  const base = words.length ? words : norm(name).trim().split(" ").filter((w) => w.length >= 3);
  return new Set([...base, ...base.flatMap((w) => SYNONYMS[w] ?? [])]);
}

const programsOf = (g?: IptvNowNext) => (g ? [...(g.recent ?? []), g.now, g.next, g.later, ...(g.upcoming ?? [])].filter((p): p is IptvProgram => Boolean(p)) : []);
const startOf = (m: SportMatch) => Date.parse(m.date);

/** Sucht Quellen für die Spiele. `knownTeams`: weitere Teamnamen, damit „Bayern – Leipzig“ nicht als Bayern-Köln zählt. */
export async function findGameSources(args: {
  games: SportMatch[]; channels: IptvChannel[]; playlists: IptvPlaylistEntry[]; guide: Record<string, IptvNowNext>;
  addons: InstalledAddon[]; knownTeams?: string[]; signal?: AbortSignal;
}): Promise<Record<string, GameSources>> {
  const { games, channels, playlists, addons } = args;
  const now = Date.now();
  const keys = games.map((g) => ({ g, home: keywords(g.home.name), away: keywords(g.away.name), start: startOf(g) }));
  const allKw = new Set([...(args.knownTeams ?? []).flatMap((n) => [...keywords(n)]), ...keys.flatMap((k) => [...k.home, ...k.away])]);
  const found = new Map<string, Map<string, { program?: IptvProgram; score: number }>>();
  const add = (gameId: string, channelId: string, program: IptvProgram | undefined, score: number) => {
    const m = found.get(gameId) ?? new Map();
    found.set(gameId, m);
    const old = m.get(channelId);
    if (!old || score > old.score) m.set(channelId, { program, score });
  };

  // 1) Sendernamen: Kandidaten für den Programmführer + Event-Sender
  const byId = new Map(channels.map((c) => [c.id, c]));
  const candidates: IptvChannel[] = [];
  const soon = keys.filter((k) => k.g.state === "in" || k.start - now < 18 * 3600_000);
  for (const c of channels) {
    const n = norm(c.name), grp = norm(c.group ?? "");
    if (SPORTS_CHANNEL.test(` ${grp} ${n} `) || [...allKw].some((w) => has(n, w))) candidates.push(c);
    for (const k of soon) if ([...k.home].some((w) => has(n, w)) && [...k.away].some((w) => has(n, w))) add(k.g.id, c.id, undefined, 3);
  }

  // 2) Programmführer im Zeitraum der Spiele
  const limited = candidates.slice(0, 2500);
  const missing = limited.filter((c) => !args.guide[c.id]);
  const guide = { ...args.guide, ...(missing.length ? await loadIptvGuideForChannels(playlists, missing).catch(() => ({})) : {}) };
  for (const c of limited) {
    for (const p of programsOf(guide[c.id])) {
      const title = norm(p.title), text = `${title} ${norm(p.description ?? "")}`;
      for (const k of keys) {
        if (p.startUtcMillis >= k.start + 150 * 60_000 || p.endUtcMillis <= k.start - 30 * 60_000) continue;
        const hT = [...k.home].some((w) => has(title, w)), aT = [...k.away].some((w) => has(title, w));
        const hX = hT || [...k.home].some((w) => has(text, w)), aX = aT || [...k.away].some((w) => has(text, w));
        let score = 0;
        if (hT && aT) score = 3;
        else if (hX && aX) score = 2;
        else if ((hT || aT) && FOOTBALL.test(` ${text} `)) {
          const others = [...allKw].filter((w) => !k.home.has(w) && !k.away.has(w));
          score = others.some((w) => has(title, w)) ? 0 : 1;
        }
        if (score > 0) add(k.g.id, c.id, p, score);
      }
    }
  }

  // 3) Stremio-Sportkataloge
  const sportAddons = addons.filter((a) => a.enabled !== false && sportsEventCatalogs(a).length > 0);
  let events: SportsAddonEvent[] = [];
  if (sportAddons.length) {
    const controller = new AbortController();
    const timer = window.setTimeout(() => controller.abort(), 25_000);
    args.signal?.addEventListener("abort", () => controller.abort());
    events = await loadSportsAddonEvents(sportAddons, controller.signal, () => {}).catch(() => []);
    window.clearTimeout(timer);
  }

  const result: Record<string, GameSources> = {};
  for (const k of keys) {
    const tv = [...(found.get(k.g.id) ?? new Map()).entries()].flatMap(([cid, v]) => {
      const channel = byId.get(cid);
      return channel ? [{ channel, program: v.program, score: v.score, uhd: UHD.test(norm(channel.name)) }] : [];
    }).sort((a, b) => b.score - a.score || Number(b.uhd) - Number(a.uhd)
      || Number(isLive(b.program, now)) - Number(isLive(a.program, now)) || a.channel.name.localeCompare(b.channel.name));
    const seen = new Set<string>();
    const addonHits = events.filter((e) => {
      const t = norm(e.title);
      const both = [...k.home].some((w) => has(t, w)) && [...k.away].some((w) => has(t, w));
      return both && (e.startsAt === undefined || Math.abs(e.startsAt - k.start) < 6 * 3600_000);
    }).sort((a, b) => Number(b.live) - Number(a.live));
    result[k.g.id] = { tv: tv.filter((s) => (seen.has(s.channel.streamUrl) ? false : (seen.add(s.channel.streamUrl), true))).slice(0, 15), addons: addonHits };
  }
  return result;
}

const isLive = (p: IptvProgram | undefined, now: number) => Boolean(p && p.startUtcMillis <= now && p.endUtcMillis > now);
