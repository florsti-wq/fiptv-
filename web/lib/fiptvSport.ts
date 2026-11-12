// fIPTV: Sport – Port des Android-SportRepository (Fußball-Wettbewerbe, Statistik).
// Daten kommen von der öffentlichen ESPN-Schnittstelle über /api/fiptv/espn.

export type League = string;
export type LeagueGroup = "top" | "germany" | "europe" | "national" | "friendly";
export type LeagueInfo = { id: League; label: string; short: string; group: LeagueGroup; table: boolean; stats: boolean; national?: boolean };
export const LEAGUE_GROUPS: Array<{ id: LeagueGroup; label: string }> = [
  { id: "top", label: "Top-Ligen Europas" },
  { id: "germany", label: "Deutschland" },
  { id: "europe", label: "Europapokal" },
  { id: "national", label: "Nationalmannschaften" },
  { id: "friendly", label: "Freundschaftsspiele" }
];
export const LEAGUES: LeagueInfo[] = [
  { id: "ger.1", label: "Bundesliga", short: "BL", group: "top", table: true, stats: true },
  { id: "eng.1", label: "Premier League", short: "PL", group: "top", table: true, stats: true },
  { id: "esp.1", label: "LaLiga", short: "LL", group: "top", table: true, stats: true },
  { id: "ita.1", label: "Serie A", short: "SA", group: "top", table: true, stats: true },
  { id: "fra.1", label: "Ligue 1", short: "L1", group: "top", table: true, stats: true },
  { id: "ger.2", label: "2. Bundesliga", short: "BL2", group: "germany", table: true, stats: true },
  { id: "ger.dfb_pokal", label: "DFB-Pokal", short: "DFB", group: "germany", table: false, stats: true },
  { id: "uefa.champions", label: "Champions League", short: "CL", group: "europe", table: true, stats: true },
  { id: "uefa.europa", label: "Europa League", short: "EL", group: "europe", table: true, stats: true },
  { id: "uefa.europa.conf", label: "Conference League", short: "ECL", group: "europe", table: true, stats: true },
  { id: "fifa.world", label: "Weltmeisterschaft", short: "WM", group: "national", table: true, stats: true, national: true },
  { id: "uefa.euro", label: "Europameisterschaft", short: "EM", group: "national", table: true, stats: true, national: true },
  { id: "uefa.nations", label: "UEFA Nations League", short: "UNL", group: "national", table: true, stats: true, national: true },
  { id: "fifa.worldq.uefa", label: "WM-Qualifikation Europa", short: "WMQ", group: "national", table: true, stats: true, national: true },
  { id: "uefa.euroq", label: "EM-Qualifikation", short: "EMQ", group: "national", table: true, stats: true, national: true },
  { id: "fifa.friendly", label: "Länderspiele (Freundschaft)", short: "LS", group: "friendly", table: false, stats: false, national: true },
  { id: "club.friendly", label: "Vereins-Testspiele", short: "TS", group: "friendly", table: false, stats: false }
];
export const leagueInfo = (id: string) => LEAGUES.find((l) => l.id === id);

export type SportTeam = { id: string; name: string; logo: string; league: string };
export type MatchSide = { id: string; name: string; logo: string; score: string; winner: boolean; /** Originalnamen (englisch) für die Sendersuche */ alt?: string[] };
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
  "Juventus": "Juventus Turin", "Bayern München": "FC Bayern",
  "Germany": "Deutschland", "Austria": "Österreich", "Switzerland": "Schweiz", "Netherlands": "Niederlande", "France": "Frankreich",
  "Spain": "Spanien", "Italy": "Italien", "Belgium": "Belgien", "Croatia": "Kroatien", "Denmark": "Dänemark", "Poland": "Polen",
  "Czechia": "Tschechien", "Czech Republic": "Tschechien", "Hungary": "Ungarn", "Scotland": "Schottland", "Turkey": "Türkei",
  "Türkiye": "Türkei", "Sweden": "Schweden", "Norway": "Norwegen", "Luxembourg": "Luxemburg", "Brazil": "Brasilien",
  "Argentina": "Argentinien", "United States": "USA", "Greece": "Griechenland", "Serbia": "Serbien", "Slovakia": "Slowakei",
  "Slovenia": "Slowenien", "Romania": "Rumänien", "Republic of Ireland": "Irland", "Ireland": "Irland", "Northern Ireland": "Nordirland",
  "Iceland": "Island", "Finland": "Finnland", "Albania": "Albanien", "Georgia": "Georgien", "Mexico": "Mexiko", "Morocco": "Marokko"
};
export const germanName = (name: string) => GERMAN[name] ?? name;
const logoFallback = (id: string) => `https://a.espncdn.com/i/teamlogos/soccer/500/${id}.png`;

type Json = Record<string, any>; // eslint-disable-line @typescript-eslint/no-explicit-any

export async function espn(path: string, params: Record<string, string> = {}): Promise<Json> {
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
    winner: Boolean(c?.winner),
    alt: [team.shortDisplayName, team.displayName, team.name].filter((n): n is string => typeof n === "string" && Boolean(n))
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
  // ESPN lehnt Datumsbereiche (JJJJMMTT-JJJJMMTT) seit Nov. 2026 mit Fehler 400 ab → ganze Monate holen und filtern
  const from = new Date(); from.setDate(from.getDate() - 3); from.setHours(0, 0, 0, 0);
  const to = new Date(); to.setDate(to.getDate() + 9); to.setHours(0, 0, 0, 0);
  const months = [...new Set([from, to].map((d) => ymd(d).slice(0, 6)))];
  const lists = await Promise.allSettled(months.map((m) => espn(`${SITE}/${league}/scoreboard`, { dates: m, limit: "300" })));
  const seen = new Set<string>();
  let events: Json[] = lists.flatMap((r) => (r.status === "fulfilled" ? r.value.events ?? [] : []))
    .filter((e: Json) => { const id = String(e.id); if (seen.has(id)) return false; seen.add(id); return true; })
    .filter((e: Json) => {
      const t = Date.parse(e.date);
      return e.status?.type?.state === "in" || (t >= from.getTime() && t < to.getTime());
    });
  if (!events.length) events = (await espn(`${SITE}/${league}/scoreboard`)).events ?? [];
  return events
    .map((e: Json) => parseCompetition(e, e.competitions?.[0], league))
    .sort((a: SportMatch, b: SportMatch) => a.date.localeCompare(b.date));
}

export async function allMatches(): Promise<SportMatch[]> {
  const lists = await Promise.allSettled(LEAGUES.map((l) => matches(l.id)));
  const all = lists.flatMap((r) => (r.status === "fulfilled" ? r.value : []));
  if (!all.length && lists.every((r) => r.status === "rejected")) throw (lists[0] as PromiseRejectedResult).reason;
  return all.sort((a, b) => a.date.localeCompare(b.date));
}

/** Aktueller Spieltag (für Live-Stände) */
export async function currentScoreboard(league: League): Promise<SportMatch[]> {
  const data = await espn(`${SITE}/${league}/scoreboard`);
  return (data.events ?? []).map((e: Json) => parseCompetition(e, e.competitions?.[0], league));
}

/** Wettbewerbe, in denen ein Team spielen kann */
export function competitionsOf(team: SportTeam): League[] {
  const own = leagueInfo(team.league) ?? LEAGUES[0];
  if (own.national) return LEAGUES.filter((l) => l.national).map((l) => l.id);
  const country = own.id.split(".")[0];
  return Array.from(new Set([own.id, ...LEAGUES.filter((l) => !l.national && l.id.startsWith(`${country}.`)).map((l) => l.id),
    ...LEAGUES.filter((l) => l.group === "europe").map((l) => l.id), "club.friendly"]));
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
  const leagues = competitionsOf(team);
  await Promise.allSettled(leagues.flatMap((league) => [false, true].map(async (fixture) => {
    const data = await espn(`${SITE}/${league}/teams/${team.id}/schedule`, fixture ? { fixture: "true" } : {});
    for (const e of data.events ?? []) {
      const m = parseCompetition(e, e.competitions?.[0], league);
      if (m.id && !found.has(m.id)) found.set(m.id, m);
    }
  })));
  return [...found.values()].sort((a, b) => a.date.localeCompare(b.date));
}

export type StatLeader = { rank: number; name: string; team: string; logo: string; value: string; detail: string };
export async function leaders(league: League): Promise<Array<{ title: string; leaders: StatLeader[] }>> {
  const data = await espn(`${SITE}/${league}/statistics`);
  return (data.stats ?? []).map((cat: Json) => ({
    title: cat.name === "goalsLeaders" ? "Torjäger" : cat.name === "assistsLeaders" ? "Vorlagen" : String(cat.displayName ?? ""),
    leaders: (cat.leaders ?? []).slice(0, 20).map((l: Json, i: number) => ({
      rank: i + 1,
      name: String(l.athlete?.displayName ?? ""),
      team: germanName(String(l.athlete?.team?.shortDisplayName ?? l.athlete?.team?.displayName ?? "")),
      logo: String(l.athlete?.team?.logos?.[0]?.href ?? ""),
      value: Number.isFinite(Number(l.value)) ? String(Math.round(Number(l.value))) : String(l.displayValue ?? ""),
      detail: String(l.displayValue ?? "").replace("Matches", "Spiele").replace("Goals", "Tore").replace("Assists", "Vorlagen")
    }))
  })).filter((c: { leaders: StatLeader[] }) => c.leaders.length);
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
export const leagueLabel = (id: string) => leagueInfo(id)?.label ?? id;
