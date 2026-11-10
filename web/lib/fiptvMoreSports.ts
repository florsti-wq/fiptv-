// fIPTV: Formel 1, Tennis (ESPN) und Wrestling (TheSportsDB) – wie in der Fire-TV-App.
import { espn } from "@/lib/fiptvSport";

type Json = Record<string, any>; // eslint-disable-line @typescript-eslint/no-explicit-any
const S = "/apis/site/v2/sports";
const V2 = "/apis/v2/sports";

const time = (s?: string) => {
  if (!s) return 0;
  const fixed = /T\d\d:\d\dZ$/.test(s) ? s.replace(/Z$/, ":00Z") : s;
  const t = Date.parse(fixed);
  return Number.isFinite(t) ? t : 0;
};
const ymd = (d: Date) => `${d.getFullYear()}${String(d.getMonth() + 1).padStart(2, "0")}${String(d.getDate()).padStart(2, "0")}`;

// ---------- Formel 1 ----------

export type F1Session = { label: string; start: number; state: string; results: Array<{ pos: number; driver: string; flag?: string; winner: boolean }> };
export type F1Weekend = { id: string; name: string; circuit: string; city: string; start: number; sessions: F1Session[] };
export type F1Standing = { rank: number; name: string; points: string; flag?: string };

const SESSION: Record<string, string> = { FP1: "1. Training", FP2: "2. Training", FP3: "3. Training", Qual: "Qualifying", SS: "Sprint-Quali", SQ: "Sprint-Quali", Sprint: "Sprint", Race: "Rennen" };
const GP: Record<string, string> = {
  "Las Vegas": "Las Vegas", Qatar: "Katar", "Abu Dhabi": "Abu Dhabi", "São Paulo": "São Paulo", "Mexico City": "Mexiko-Stadt", "United States": "USA",
  Singapore: "Singapur", Azerbaijan: "Aserbaidschan", Italian: "Italien", Dutch: "Niederlande", Hungarian: "Ungarn", Belgian: "Belgien",
  British: "Großbritannien", Austrian: "Österreich", Spanish: "Spanien", Canadian: "Kanada", Monaco: "Monaco", Miami: "Miami",
  "Saudi Arabian": "Saudi-Arabien", Bahrain: "Bahrain", Japanese: "Japan", Chinese: "China", Australian: "Australien", Madrid: "Madrid"
};
export function germanGp(name: string) {
  const core = name.replace(/ Grand Prix$/, "").trim();
  const key = Object.keys(GP).find((k) => core.endsWith(k));
  return key ? `Großer Preis von ${GP[key]}` : name;
}

export async function f1Weekends(): Promise<{ weekends: F1Weekend[]; calendar: Array<{ name: string; start: number; end: number }> }> {
  const from = new Date(); from.setDate(from.getDate() - 21);
  const to = new Date(); to.setDate(to.getDate() + 45);
  const j = await espn(`${S}/racing/f1/scoreboard`, { dates: `${ymd(from)}-${ymd(to)}` });
  const weekends: F1Weekend[] = (j.events ?? []).map((e: Json) => {
    const sessions: F1Session[] = (e.competitions ?? []).map((c: Json) => ({
      label: SESSION[c.type?.abbreviation] ?? String(c.type?.abbreviation ?? ""),
      start: time(c.date),
      state: String(c.status?.type?.state ?? ""),
      results: (c.competitors ?? []).map((r: Json) => ({ pos: Number(r.order) || 0, driver: String(r.athlete?.displayName ?? ""), flag: r.athlete?.flag?.href, winner: Boolean(r.winner) }))
        .filter((r: { pos: number }) => r.pos > 0).sort((a: { pos: number }, b: { pos: number }) => a.pos - b.pos)
    })).sort((a: F1Session, b: F1Session) => a.start - b.start);
    return {
      id: String(e.id), name: germanGp(String(e.name ?? "")), circuit: String(e.circuit?.fullName ?? ""),
      city: [e.circuit?.address?.city, e.circuit?.address?.country].filter(Boolean).join(", "),
      start: sessions[0]?.start ?? time(e.date), sessions
    };
  }).sort((a: F1Weekend, b: F1Weekend) => a.start - b.start);
  const calendar = (j.leagues?.[0]?.calendar ?? []).map((c: Json) => ({ name: germanGp(String(c.label ?? "")), start: time(c.startDate), end: time(c.endDate) }));
  return { weekends, calendar };
}

export async function f1Standings(): Promise<{ drivers: F1Standing[]; teams: F1Standing[] }> {
  const j = await espn(`${V2}/racing/f1/standings`);
  const parse = (g?: Json): F1Standing[] => (g?.standings?.entries ?? []).map((e: Json) => {
    const stats: Record<string, string> = Object.fromEntries((e.stats ?? []).map((s: Json) => [s.name, String(s.displayValue ?? "")]));
    const who = e.athlete ?? e.team;
    return { rank: Number(stats.rank) || 0, name: String(who?.displayName ?? ""), points: (stats.championshipPts ?? stats.points ?? "").trim(), flag: who?.flag?.href };
  }).sort((a: F1Standing, b: F1Standing) => (a.rank || 999) - (b.rank || 999));
  const groups: Json[] = j.children ?? [];
  return {
    drivers: parse(groups.find((g) => /driver/i.test(g.name)) ?? groups[0]),
    teams: parse(groups.find((g) => /constructor/i.test(g.name)) ?? groups[1])
  };
}

// ---------- Tennis ----------

export type TennisPlayer = { name: string; flag?: string; winner: boolean; sets: string[] };
export type TennisMatch = { id: string; round: string; start: number; state: string; p1: TennisPlayer; p2: TennisPlayer };
export type TennisTournament = { id: string; name: string; major: boolean; matches: TennisMatch[] };

function germanRound(r: string) {
  if (/^final$/i.test(r)) return "Finale";
  if (/semifinal/i.test(r)) return "Halbfinale";
  if (/quarterfinal/i.test(r)) return "Viertelfinale";
  if (/round of 16/i.test(r)) return "Achtelfinale";
  if (/group/i.test(r)) return "Gruppenphase";
  if (/qualifying/i.test(r)) return "Qualifikation";
  const m = r.match(/^Round (\d+)$/) ?? r.match(/^(\d)(st|nd|rd|th) Round$/);
  return m ? `${m[1]}. Runde` : r;
}

export async function tennisTournaments(tour: "atp" | "wta"): Promise<TennisTournament[]> {
  const j = await espn(`${S}/tennis/${tour}/scoreboard`);
  const out: TennisTournament[] = [];
  for (const e of j.events ?? []) {
    const matches: TennisMatch[] = [];
    for (const g of e.groupings ?? []) {
      if (!/singles/i.test(g.grouping?.displayName ?? "")) continue;
      for (const c of g.competitions ?? []) {
        const cs = c.competitors ?? [];
        if (cs.length < 2) continue;
        const player = (o: Json): TennisPlayer => ({
          name: o.athlete?.displayName && o.athlete.displayName !== "null" ? String(o.athlete.displayName) : "offen",
          flag: o.athlete?.flag?.href,
          winner: Boolean(o.winner),
          sets: (o.linescores ?? []).map((l: Json) => `${Math.round(Number(l.value) || 0)}${l.tiebreak != null ? `(${l.tiebreak})` : ""}`)
        });
        const round = germanRound(String(c.round?.displayName ?? ""));
        const state = String(c.status?.type?.state ?? "");
        if (round === "Qualifikation" && state === "post") continue;
        matches.push({ id: String(c.id), round, start: time(c.date), state, p1: player(cs[0]), p2: player(cs[1]) });
      }
    }
    if (!matches.length) continue;
    const order = (m: TennisMatch) => (m.state === "in" ? 0 : m.state === "pre" ? 1 : 2);
    matches.sort((a, b) => order(a) - order(b) || (a.state === "post" ? b.start - a.start : a.start - b.start));
    out.push({ id: String(e.id), name: String(e.name ?? ""), major: Boolean(e.major), matches });
  }
  return out.sort((a, b) => Number(b.matches.some((m) => m.state === "in")) - Number(a.matches.some((m) => m.state === "in")) || Number(b.major) - Number(a.major));
}

export async function tennisRanking(tour: "atp" | "wta"): Promise<Array<{ rank: number; name: string; points: string; flag?: string; trend: string }>> {
  const j = await espn(`${S}/tennis/${tour}/rankings`);
  return (j.rankings?.[0]?.ranks ?? []).slice(0, 100).map((e: Json) => {
    const cur = Number(e.current) || 0;
    const prev = Number(e.previous) || cur;
    return {
      rank: cur, name: String(e.athlete?.displayName ?? ""), points: String(Math.round(Number(e.points) || 0)), flag: e.athlete?.flag?.href,
      trend: prev === cur ? "" : prev > cur ? `▲${prev - cur}` : `▼${cur - prev}`
    };
  });
}

// ---------- Wrestling ----------

export type WrestlingShow = { promotion: string; name: string; start: number; venue: string; image?: string; card: string };
export const WWE_YOUTUBE = "UCJ5v_MCY6GNUBTO8-D3XoAg";
export const AEW_YOUTUBE = "UCFN4JkGP_bVhAdBsoV9xftA";

export async function wrestlingShows(): Promise<WrestlingShow[]> {
  const jobs = [["4444", "WWE"], ["4563", "AEW"]].flatMap(([id, promo]) =>
    ["eventsnextleague", "eventspastleague"].map(async (ep) => {
      const j = await espn(`/tsdb/${ep}.php`, { id });
      return (j.events ?? []).map((e: Json): WrestlingShow => {
        const ts = e.strTimestamp && e.strTimestamp !== "null" ? String(e.strTimestamp) : "";
        return {
          promotion: promo, name: String(e.strEvent ?? ""),
          start: ts ? Date.parse(/Z$|[+-]\d\d:?\d\d$/.test(ts) ? ts : `${ts}Z`) || 0 : 0,
          venue: e.strVenue && e.strVenue !== "null" ? String(e.strVenue) : "",
          image: [e.strThumb, e.strPoster].find((x) => x && x !== "null") || undefined,
          card: String(e.strResult ?? "").replace(/\r/g, "").trim()
        };
      });
    }));
  const all = (await Promise.allSettled(jobs)).flatMap((r) => (r.status === "fulfilled" ? r.value : []));
  return all.sort((a, b) => a.start - b.start);
}

// ---------- Im TV ----------

export const TV_PATTERNS = {
  f1: /\b(formel ?1|formula ?1|formula one|f1|grand prix|qualifying)\b/i,
  tennis: /\b(tennis|atp|wta|davis cup|billie jean king|wimbledon|roland garros|french open|us open|australian open|laver cup|united cup)\b/i,
  wrestling: /\b(wwe|aew|wrestling|smackdown|raw|nxt|dynamite|collision|rampage|royal rumble|wrestlemania|summerslam|survivor series)\b/i
};
export const SPORT_CHANNELS = /(sport|sky|dazn|eurosport|servus|sport1|magenta|prime|ard|zdf|das erste|rtl|sat ?1|prosieben|maxx|tele ?5|dmax|nitro|netflix|bein|espn|arena|canal|tnt|orf|srf|f1|tennis|wrestling|wwe)/i;
