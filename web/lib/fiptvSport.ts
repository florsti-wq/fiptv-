// fIPTV: Sport (Bundesliga & Champions League) – Port des Android-SportRepository.
// Daten kommen von der öffentlichen ESPN-Schnittstelle über /api/fiptv/espn.

export type League = "ger.1" | "uefa.champions";
export const LEAGUES: Array<{ id: League; label: string }> = [
  { id: "ger.1", label: "Bundesliga" },
  { id: "uefa.champions", label: "Champions League" }
];

export type SportTeam = { id: string; name: string; logo: string; league: string };
export type MatchSide = { id: string; name: string; logo: string; score: string; winner: boolean };
export type MatchState = "pre" | "in" | "post";
export type SportMatch = {
  id: string; league: League; date: string; state: MatchState; clock: string; detail: string;
  home: MatchSide; away: MatchSide; venue?: string;
};
export type TableRow = {
  rank: number; team: SportTeam; played: number; wins: number; draws: number; losses: number;
  goalsFor: number; goalsAgainst: number; diff: number; points: number; group?: string;
};
export type MatchEvent = { minute: string; type: string; text: string; teamId: string; player: string };
export type LineupPlayer = { name: string; jersey: string; position: string; starter: boolean };
export type MatchDetail = {
  match: SportMatch; events: MatchEvent[];
  lineups: Array<{ teamId: string; formation: string; players: LineupPlayer[] }>;
  stats: Array<{ label: string; home: string; away: string }>;
};

const SITE = "/apis/site/v2/sports/soccer";
const V2 = "/apis/v2/sports/soccer";

const GERMAN: Record<string, string> = {
  "Bayern Munich": "FC Bayern", "Borussia Dortmund": "Dortmund", "Bayer Leverkusen": "Leverkusen",
  "RB Leipzig": "RB Leipzig", "VfB Stuttgart": "Stuttgart", "Eintracht Frankfurt": "Frankfurt",
  "SC Freiburg": "Freiburg", "TSG Hoffenheim": "Hoffenheim", "1. FC Heidenheim 1846": "Heidenheim",
  "Werder Bremen": "Werder Bremen", "FC Augsburg": "Augsburg", "VfL Wolfsburg": "Wolfsburg",
  "Mainz": "Mainz 05", "Borussia Mönchengladbach": "Gladbach", "Borussia Monchengladbach": "Gladbach",
  "Union Berlin": "Union Berlin", "St. Pauli": "St. Pauli", "FC St. Pauli": "St. Pauli",
  "1. FC Köln": "Köln", "FC Cologne": "Köln", "Hamburg SV": "HSV", "Hamburger SV": "HSV",
  "VfL Bochum": "Bochum", "Holstein Kiel": "Kiel", "Real Madrid": "Real Madrid", "Barcelona": "FC Barcelona",
  "Paris Saint-Germain": "Paris SG", "Internazionale": "Inter Mailand", "AC Milan": "AC Mailand",
  "Juventus": "Juventus Turin", "Bayern München": "FC Bayern"
};
export const germanName = (name: string) => GERMAN[name] ?? name;
const logoFallback = (id: string) => `https://a.espncdn.com/i/teamlogos/soccer/500/${id}.png`;

type Json = Record<string, any>; // eslint-disable-line @typescript-eslint/no-explicit-any

async function espn(path: string, params: Record<string, string> = {}): Promise<Json> {
  const query = new URLSearchParams({ path, ...params });
  const response = await fetch(`/api/fiptv/espn?${query}`, { cache: "no-store" });
  if (!response.ok) throw new Error(`Sportdaten nicht erreichbar (${response.status})`);
  return response.json();
}

const ymd = (date: Date) => `${date.getFullYear()}${String(date.getMonth() + 1).padStart(2, "0")}${String(date.getDate()).padStart(2, "0")}`;
const normDate = (value: string) => (/T\d\d:\d\dZ$/.test(value) ? value.replace(/Z$/, ":00Z") : value);
const scoreOf = (score: unknown) => {
  if (score == null) return "";
  if (typeof score === "object") return String((score as Json).displayValue ?? (score as Json).value ?? "");
  return String(score);
};

function side(c: Json | undefined): MatchSide {
  const team = c?.team ?? {};
  const id = String(team.id ?? "");
  return {
    id,
    name: germanName(String(team.shortDisplayName ?? team.displayName ?? team.name ?? "?")),
    logo: String(team.logo ?? team.logos?.[0]?.href ?? (id ? logoFallback(id) : "")),
    score: scoreOf(c?.score),
    winner: Boolean(c?.winner)
  };
}

function parseCompetition(event: Json, comp: Json, league: League): SportMatch {
  const competitors: Json[] = comp?.competitors ?? [];
  const home = competitors.find((c) => c.homeAway === "home") ?? competitors[0];
  const away = competitors.find((c) => c.homeAway === "away") ?? competitors[1];
  const status = comp?.status ?? event?.status ?? {};
  const state = String(status.type?.state ?? "pre") as MatchState;
  return {
    id: String(event?.id ?? comp?.id ?? ""),
    league,
    date: normDate(String(comp?.date ?? event?.date ?? "")),
    state: state === "in" || state === "post" ? state : "pre",
    clock: String(status.displayClock ?? ""),
    detail: String(status.type?.shortDetail ?? status.type?.detail ?? ""),
    home: side(home),
    away: side(away),
    venue: comp?.venue?.fullName
  };
}

export async function matches(league: League): Promise<SportMatch[]> {
  const from = new Date(); from.setDate(from.getDate() - 3);
  const to = new Date(); to.setDate(to.getDate() + 8);
  let data: Json;
  try {
    data = await espn(`${SITE}/${league}/scoreboard`, { dates: `${ymd(from)}-${ymd(to)}`, limit: "200" });
    if (!data.events?.length) data = await espn(`${SITE}/${league}/scoreboard`);
  } catch {
    data = await espn(`${SITE}/${league}/scoreboard`);
  }
  return (data.events ?? [])
    .map((e: Json) => parseCompetition(e, e.competitions?.[0], league))
    .sort((a: SportMatch, b: SportMatch) => a.date.localeCompare(b.date));
}

export async function allMatches(): Promise<SportMatch[]> {
  const lists = await Promise.allSettled(LEAGUES.map((l) => matches(l.id)));
  const all = lists.flatMap((r) => (r.status === "fulfilled" ? r.value : []));
  if (!all.length && lists.every((r) => r.status === "rejected")) throw (lists[0] as PromiseRejectedResult).reason;
  return all.sort((a, b) => a.date.localeCompare(b.date));
}

export async function teams(league: League): Promise<SportTeam[]> {
  const data = await espn(`${SITE}/${league}/teams`);
  const list: Json[] = data.sports?.[0]?.leagues?.[0]?.teams ?? [];
  return list.map(({ team }) => ({
    id: String(team.id),
    name: germanName(String(team.shortDisplayName ?? team.displayName)),
    logo: String(team.logos?.[0]?.href ?? logoFallback(String(team.id))),
    league
  })).sort((a, b) => a.name.localeCompare(b.name, "de"));
}

export async function table(league: League): Promise<TableRow[]> {
  const data = await espn(`${V2}/${league}/standings`);
  const groups: Json[] = data.children?.length ? data.children : [data];
  const rows: TableRow[] = [];
  for (const group of groups) {
    for (const entry of group.standings?.entries ?? []) {
      const stat = (name: string) => Number((entry.stats as Json[] | undefined)?.find((s) => s.name === name || s.type === name)?.value ?? 0);
      const team = entry.team ?? {};
      rows.push({
        rank: stat("rank"),
        team: { id: String(team.id), name: germanName(String(team.shortDisplayName ?? team.displayName)), logo: String(team.logos?.[0]?.href ?? logoFallback(String(team.id))), league },
        played: stat("gamesPlayed"), wins: stat("wins"), draws: stat("ties"), losses: stat("losses"),
        goalsFor: stat("pointsFor"), goalsAgainst: stat("pointsAgainst"), diff: stat("pointDifferential"), points: stat("points"),
        group: groups.length > 1 ? String(group.name ?? group.abbreviation ?? "") : undefined
      });
    }
  }
  return rows.sort((a, b) => (a.group ?? "").localeCompare(b.group ?? "") || (a.rank || 99) - (b.rank || 99) || b.points - a.points);
}

export async function teamMatches(team: SportTeam): Promise<SportMatch[]> {
  const found = new Map<string, SportMatch>();
  const leagues = Array.from(new Set([team.league as League, ...LEAGUES.map((l) => l.id)]));
  await Promise.allSettled(leagues.flatMap((league) => [false, true].map(async (fixture) => {
    const data = await espn(`${SITE}/${league}/teams/${team.id}/schedule`, fixture ? { fixture: "true" } : {});
    for (const e of data.events ?? []) {
      const m = parseCompetition(e, e.competitions?.[0], league);
      if (m.id && !found.has(m.id)) found.set(m.id, m);
    }
  })));
  return [...found.values()].sort((a, b) => a.date.localeCompare(b.date));
}

const STAT_LABELS: Array<[string, string]> = [
  ["possessionPct", "Ballbesitz %"], ["totalShots", "Schüsse"], ["shotsOnTarget", "Aufs Tor"],
  ["wonCorners", "Ecken"], ["foulsCommitted", "Fouls"], ["offsides", "Abseits"],
  ["yellowCards", "Gelbe Karten"], ["redCards", "Rote Karten"], ["saves", "Paraden"]
];

export async function matchDetail(id: string, league: League): Promise<MatchDetail> {
  const data = await espn(`${SITE}/${league}/summary`, { event: id });
  const comp = data.header?.competitions?.[0] ?? {};
  const match = parseCompetition({ id, ...data.header }, comp, league);
  if (data.gameInfo?.venue?.fullName) match.venue = data.gameInfo.venue.fullName;
  const events: MatchEvent[] = (data.keyEvents ?? []).map((e: Json) => ({
    minute: String(e.clock?.displayValue ?? ""),
    type: String(e.type?.type ?? e.type?.text ?? ""),
    text: String(e.text ?? e.type?.text ?? ""),
    teamId: String(e.team?.id ?? ""),
    player: String(e.participants?.[0]?.athlete?.displayName ?? "")
  })).filter((e: MatchEvent) => e.text || e.player);
  const lineups = (data.rosters ?? []).map((r: Json) => ({
    teamId: String(r.team?.id ?? ""),
    formation: String(r.formation ?? ""),
    players: (r.roster ?? []).map((p: Json) => ({
      name: String(p.athlete?.displayName ?? ""), jersey: String(p.jersey ?? ""),
      position: String(p.position?.abbreviation ?? ""), starter: Boolean(p.starter)
    }))
  }));
  const box: Json[] = data.boxscore?.teams ?? [];
  const byTeam = (teamId: string) => box.find((t) => String(t.team?.id) === teamId)?.statistics ?? [];
  const value = (list: Json[], name: string) => String(list.find((s) => s.name === name)?.displayValue ?? "");
  const homeStats = byTeam(match.home.id), awayStats = byTeam(match.away.id);
  const stats = STAT_LABELS.map(([name, label]) => ({ label, home: value(homeStats, name), away: value(awayStats, name) }))
    .filter((s) => s.home || s.away);
  return { match, events, lineups, stats };
}

// Lieblingsteams – gleiches JSON wie in der Fire-TV-App (fiptv_sport_teams)
export function decodeTeams(json: string | undefined): SportTeam[] {
  try {
    const list = JSON.parse(json || "[]");
    return Array.isArray(list) ? list.filter((t) => t && t.id).map((t) => ({ id: String(t.id), name: String(t.name ?? ""), logo: String(t.logo ?? ""), league: String(t.league ?? "ger.1") })) : [];
  } catch { return []; }
}
export const encodeTeams = (list: SportTeam[]) => JSON.stringify(list.map(({ id, name, logo, league }) => ({ id, name, logo, league })));

export const kickoff = (m: SportMatch) => {
  const d = new Date(m.date);
  if (Number.isNaN(d.getTime())) return "";
  return d.toLocaleString("de-DE", { weekday: "short", day: "2-digit", month: "2-digit", hour: "2-digit", minute: "2-digit" });
};
export const isToday = (m: SportMatch) => new Date(m.date).toDateString() === new Date().toDateString();
export const leagueLabel = (id: string) => LEAGUES.find((l) => l.id === id)?.label ?? id;
