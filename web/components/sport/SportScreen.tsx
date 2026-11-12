"use client";
// fIPTV: Sport wie am Fire TV – Übersicht, Meine Teams (pro Profil abgeglichen), Fußball (Top-Ligen,
// Pokal, Europapokal, Nationalteams, Testspiele) mit Tabellen und Statistik, Formel 1, Tennis, Wrestling.
import "./sport.css";

import { ArrowLeft, Loader2, RefreshCw, Star } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { findGameSources, type GameSources } from "@/lib/fiptvSportSources";
import { resolveSportsAddon, type SportsAddonEvent, type SportsAddonStream } from "@/lib/sportsAddons";
import { useAppPick } from "@/lib/store";
import {
  LEAGUES, LEAGUE_GROUPS, allMatches, decodeTeams, encodeTeams, isToday, kickoff, leagueInfo, leagueLabel, leaders, matchDetail, matches, table, teamMatches, teams,
  type League, type MatchDetail, type SportMatch, type SportTeam, type TableRow
} from "@/lib/fiptvSport";
import { F1View, TennisView, WrestlingView } from "./MoreSports";
import { GoalAlertToggle } from "@/components/fiptv/GoalAlerts";
import { FiptvIcon } from "@/components/fiptv/FiptvIcon";

type Tab = "hub" | "mine" | "football" | "f1" | "tennis" | "wrestling";
type Page = { kind: "team"; team: SportTeam } | { kind: "match"; match: SportMatch } | { kind: "league"; league: League } | null;

function useLoad<T>(load: () => Promise<T>, deps: unknown[]) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [n, setN] = useState(0);
  useEffect(() => {
    let cancelled = false;
    setLoading(true); setError("");
    load().then((d) => { if (!cancelled) setData(d); })
      .catch((e) => { if (!cancelled) setError(e instanceof Error ? e.message : "Fehler beim Laden"); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [...deps, n]); // eslint-disable-line react-hooks/exhaustive-deps
  return { data, error, loading, reload: () => setN((x) => x + 1) };
}

/** fIPTV: TV-Sender und Addon-Streams zu Spielen (wie Fire TV). Ergebnis 5 Min. zwischengespeichert. */
const sourceCache = new Map<string, { at: number; data: GameSources }>();
function useGameSources(games: SportMatch[]): Record<string, GameSources> {
  const { iptvSnapshot, settings, addons } = useAppPick("iptvSnapshot", "settings", "addons");
  const [map, setMap] = useState<Record<string, GameSources>>({});
  const ids = games.map((g) => g.id).join(",");
  useEffect(() => {
    if (!games.length) return;
    const now = Date.now();
    const cached: Record<string, GameSources> = {};
    for (const g of games) { const c = sourceCache.get(g.id); if (c && now - c.at < 300_000) cached[g.id] = c.data; }
    setMap(cached);
    const todo = games.filter((g) => !cached[g.id]);
    const channels = iptvSnapshot.allChannels?.length ? iptvSnapshot.allChannels : iptvSnapshot.channels;
    if (!todo.length || (!channels.length && !addons.length)) return;
    const controller = new AbortController();
    findGameSources({ games: todo, channels, playlists: settings.iptvPlaylists, guide: iptvSnapshot.nowNext, addons,
      knownTeams: games.flatMap((g) => [g.home.name, g.away.name]), signal: controller.signal })
      .then((found) => {
        if (controller.signal.aborted) return;
        for (const [id, data] of Object.entries(found)) sourceCache.set(id, { at: Date.now(), data });
        setMap((old) => ({ ...old, ...found }));
      }).catch(() => {});
    return () => controller.abort();
  }, [ids, iptvSnapshot, addons, settings.iptvPlaylists]); // eslint-disable-line react-hooks/exhaustive-deps
  return map;
}

const sourceCount = (s?: GameSources) => (s ? s.tv.length + s.addons.length : 0);

function Logo({ src, size = 28 }: { src: string; size?: number }) {
  return src ? <img className="sp-logo" src={src} alt="" width={size} height={size} loading="lazy" /> : <span className="sp-logo" style={{ width: size, height: size }} />;
}

function MatchCard({ m, onOpen, sources }: { m: SportMatch; onOpen: (m: SportMatch) => void; sources?: GameSources }) {
  const live = m.state === "in";
  const count = sourceCount(sources);
  return (
    <button type="button" className={`sp-match ${live ? "live" : ""}`} onClick={() => onOpen(m)}>
      <div className="sp-match-top">
        <span>{leagueLabel(m.league)}</span>
        <span className={live ? "sp-live" : ""}>{live ? `● LIVE ${m.clock}` : m.state === "post" ? "Beendet" : kickoff(m)}</span>
      </div>
      {[m.home, m.away].map((s) => (
        <div key={s.id || s.name} className={`sp-side ${m.state === "post" && s.winner ? "win" : ""}`}>
          <Logo src={s.logo} /> <span>{s.name}</span> <b>{m.state === "pre" ? "" : s.score}</b>
        </div>
      ))}
      {count > 0 && m.state !== "post" && <div className="sp-match-src">▶ {count} {count === 1 ? "Quelle" : "Quellen"}</div>}
    </button>
  );
}

function MatchList({ title, list, onOpen, sources }: { title: string; list: SportMatch[]; onOpen: (m: SportMatch) => void; sources?: Record<string, GameSources> }) {
  if (!list.length) return null;
  return (
    <section className="sp-section">
      <h2>{title}</h2>
      <div className="sp-row">{list.map((m) => <MatchCard key={`${m.league}-${m.id}`} m={m} onOpen={onOpen} sources={sources?.[m.id]} />)}</div>
    </section>
  );
}

function Status({ loading, error, reload }: { loading: boolean; error: string; reload: () => void }) {
  if (loading) return <div className="sp-empty"><Loader2 className="spin" /> Lade …</div>;
  if (error) return <div className="sp-empty">{error} <button className="sp-btn" type="button" onClick={reload}>Erneut versuchen</button></div>;
  return null;
}

function Hub({ myTeams, onOpen }: { myTeams: SportTeam[]; onOpen: (m: SportMatch) => void }) {
  const { data, loading, error, reload } = useLoad(allMatches, []);
  useEffect(() => {
    if (!data?.some((m) => m.state === "in")) return;
    const t = window.setInterval(reload, 60_000);
    return () => window.clearInterval(t);
  }, [data]); // eslint-disable-line react-hooks/exhaustive-deps
  const soonGames = useMemo(() => (data ?? []).filter((m) => m.state === "in" || (m.state === "pre" && Date.parse(m.date) - Date.now() < 30 * 3600_000)).slice(0, 40), [data]);
  const sources = useGameSources(soonGames);
  if (!data) return <Status loading={loading} error={error} reload={reload} />;
  const mine = new Set(myTeams.map((t) => t.id));
  const isMine = (m: SportMatch) => mine.has(m.home.id) || mine.has(m.away.id);
  const games = data.filter((m) => m.league !== "club.friendly" || isMine(m));
  const live = games.filter((m) => m.state === "in");
  const today = games.filter((m) => m.state === "pre" && isToday(m));
  const next = games.filter((m) => m.state === "pre" && !isToday(m));
  const results = games.filter((m) => m.state === "post").reverse();
  const myNext = games.filter((m) => isMine(m) && m.state !== "post");
  return (
    <>
      <MatchList title="Jetzt live" list={live} onOpen={onOpen} sources={sources} />
      <MatchList title="Meine Teams" list={myNext.slice(0, 12)} onOpen={onOpen} sources={sources} />
      <MatchList title="Heute" list={today} onOpen={onOpen} sources={sources} />
      <MatchList title="Nächste Spiele" list={next.slice(0, 30)} onOpen={onOpen} sources={sources} />
      <MatchList title="Ergebnisse" list={results.slice(0, 30)} onOpen={onOpen} />
      {!games.length && <div className="sp-empty">Gerade keine Spiele in den nächsten Tagen.</div>}
    </>
  );
}

function LeagueView({ league, myTeams, toggle, onOpen, onTeam }: {
  league: League; myTeams: SportTeam[]; toggle: (t: SportTeam) => void; onOpen: (m: SportMatch) => void; onTeam: (t: SportTeam) => void;
}) {
  const info = leagueInfo(league);
  const [sub, setSub] = useState<"table" | "games" | "stats">(info?.table === false ? "games" : "table");
  return (
    <>
      <div className="sp-head"><h1 style={{ fontSize: 24 }}>{info?.label ?? league}</h1>
        <div className="sp-tabs small">
          {info?.table !== false && <button type="button" className={sub === "table" ? "active" : ""} onClick={() => setSub("table")}>Tabelle</button>}
          <button type="button" className={sub === "games" ? "active" : ""} onClick={() => setSub("games")}>Spiele</button>
          {info?.stats !== false && <button type="button" className={sub === "stats" ? "active" : ""} onClick={() => setSub("stats")}>Torjäger & Vorlagen</button>}
        </div>
      </div>
      {sub === "games" ? <LeagueGames league={league} onOpen={onOpen} />
        : sub === "stats" ? <LeadersView league={league} />
        : <LeagueTable league={league} myTeams={myTeams} toggle={toggle} onTeam={onTeam} />}
    </>
  );
}

function LeagueGames({ league, onOpen }: { league: League; onOpen: (m: SportMatch) => void }) {
  const games = useLoad(() => matches(league), [league]);
  if (!games.data) return <Status {...games} />;
  return (
    <>
      <MatchList title="Live & demnächst" list={games.data.filter((m) => m.state !== "post")} onOpen={onOpen} />
      <MatchList title="Ergebnisse" list={games.data.filter((m) => m.state === "post").reverse()} onOpen={onOpen} />
      {!games.data.length && <div className="sp-empty">Gerade keine Spiele (bei Turnieren erst, wenn sie laufen).</div>}
    </>
  );
}

function LeadersView({ league }: { league: League }) {
  const data = useLoad(() => leaders(league), [league]);
  if (!data.data) return <Status {...data} />;
  if (!data.data.length) return <div className="sp-empty">Für diesen Wettbewerb gibt es (noch) keine Statistik.</div>;
  return (
    <div className="sp-detail">
      {data.data.map((cat) => (
        <section key={cat.title} className="sp-section"><h2>{cat.title}</h2>
          <table className="sp-table"><tbody>
            {cat.leaders.map((l) => (
              <tr key={`${l.rank}-${l.name}`}>
                <td>{l.rank}</td>
                <td className="l"><Logo src={l.logo} size={20} /> <b>{l.name}</b> <small style={{ opacity: 0.6 }}>{l.team}</small></td>
                <td><b style={{ fontSize: 18 }}>{l.value}</b></td>
                <td style={{ opacity: 0.6 }}>{l.detail}</td>
              </tr>
            ))}
          </tbody></table>
        </section>
      ))}
    </div>
  );
}

function LeagueTable({ league, myTeams, toggle, onTeam }: { league: League; myTeams: SportTeam[]; toggle: (t: SportTeam) => void; onTeam: (t: SportTeam) => void }) {
  const tab = useLoad(() => table(league), [league]);
  const mine = new Set(myTeams.map((t) => t.id));
  const groups = useMemo(() => {
    const map = new Map<string, TableRow[]>();
    for (const row of tab.data ?? []) map.set(row.group ?? "", [...(map.get(row.group ?? "") ?? []), row]);
    return [...map.entries()];
  }, [tab.data]);
  if (!tab.data) return <Status {...tab} />;
  if (!tab.data.length) return <div className="sp-empty">Keine Tabelle verfügbar (bei Turnieren erst, wenn sie laufen).</div>;
  return (
    <section className="sp-section">
      {groups.map(([group, rows]) => (
        <div key={group} className="sp-table-wrap">
          {group && <h3>{group}</h3>}
          <table className="sp-table">
            <thead><tr><th>#</th><th className="l">Team</th><th>Sp</th><th>S</th><th>U</th><th>N</th><th>Tore</th><th>Diff</th><th>Pkt</th><th /></tr></thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.team.id}>
                  <td>{r.rank || ""}</td>
                  <td className="l"><button type="button" className="sp-teamlink" onClick={() => onTeam(r.team)}><Logo src={r.team.logo} size={22} /> {r.team.name}</button></td>
                  <td>{r.played}</td><td>{r.wins}</td><td>{r.draws}</td><td>{r.losses}</td>
                  <td>{r.goalsFor}:{r.goalsAgainst}</td><td>{r.diff > 0 ? `+${r.diff}` : r.diff}</td><td><b>{r.points}</b></td>
                  <td><button type="button" className={`sp-star ${mine.has(r.team.id) ? "on" : ""}`} onClick={() => toggle(r.team)} aria-label="Lieblingsteam"><Star size={16} /></button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ))}
    </section>
  );
}

function FootballView({ onLeague }: { onLeague: (l: League) => void }) {
  return (
    <>
      {LEAGUE_GROUPS.map((g) => (
        <section key={g.id} className="sp-section"><h2><FiptvIcon name={g.id === "top" ? "Trophy" : g.id === "europe" ? "Star" : "Football"} /> {g.label}</h2>
          <div className="sp-teams">
            {LEAGUES.filter((l) => l.group === g.id).map((l) => (
              <button key={l.id} type="button" className="sp-team sp-league" onClick={() => onLeague(l.id)}>
                <span className="sp-short">{l.short}</span> <b>{l.label}</b>
              </button>
            ))}
          </div>
        </section>
      ))}
      <div className="sp-empty">WM und EM zeigen Spiele und Tabellen, sobald das Turnier läuft.</div>
    </>
  );
}

function MineView({ myTeams, toggle, onTeam }: { myTeams: SportTeam[]; toggle: (t: SportTeam) => void; onTeam: (t: SportTeam) => void }) {
  const [league, setLeague] = useState<string>("ger.1");
  const all = useLoad(() => teams(league), [league]);
  const mine = new Set(myTeams.map((t) => t.id));
  return (
    <>
      <GoalAlertToggle />
      <section className="sp-section">
        <h2>Meine Teams</h2>
        {myTeams.length ? (
          <div className="sp-teams">
            {myTeams.map((t) => (
              <div key={t.id} className="sp-team">
                <button type="button" className="sp-teamlink" onClick={() => onTeam(t)}><Logo src={t.logo} size={40} /> {t.name}</button>
                <button type="button" className="sp-star on" onClick={() => toggle(t)} aria-label="Entfernen"><Star size={16} /></button>
              </div>
            ))}
          </div>
        ) : <div className="sp-empty">Noch keine Lieblingsteams. Wähle unten mit dem Stern aus – die Auswahl gilt auch am Fire TV.</div>}
      </section>
      <section className="sp-section">
        <h2>Teams hinzufügen</h2>
        <div className="sp-tabs small">
          <select className="sp-select" value={league} onChange={(e) => setLeague(e.target.value)} aria-label="Wettbewerb">
            {LEAGUES.filter((l) => l.id !== "club.friendly").map((l) => <option key={l.id} value={l.id}>{l.label}</option>)}
          </select>
        </div>
        {all.data ? (
          <div className="sp-teams">
            {all.data.map((t) => (
              <div key={t.id} className="sp-team">
                <button type="button" className="sp-teamlink" onClick={() => onTeam(t)}><Logo src={t.logo} size={32} /> {t.name}</button>
                <button type="button" className={`sp-star ${mine.has(t.id) ? "on" : ""}`} onClick={() => toggle(t)} aria-label="Lieblingsteam"><Star size={16} /></button>
              </div>
            ))}
          </div>
        ) : <Status {...all} />}
      </section>
    </>
  );
}

function TeamPage({ team, myTeams, toggle, onOpen }: { team: SportTeam; myTeams: SportTeam[]; toggle: (t: SportTeam) => void; onOpen: (m: SportMatch) => void }) {
  const games = useLoad(() => teamMatches(team), [team.id]);
  const fav = myTeams.some((t) => t.id === team.id);
  return (
    <>
      <div className="sp-hero"><Logo src={team.logo} size={72} /><h1>{team.name}</h1>
        <button type="button" className={`sp-btn ${fav ? "on" : ""}`} onClick={() => toggle(team)}><Star size={16} /> {fav ? "Lieblingsteam" : "Als Lieblingsteam"}</button>
      </div>
      {games.data ? (
        <>
          <MatchList title="Nächste Spiele" list={games.data.filter((m) => m.state !== "post")} onOpen={onOpen} />
          <MatchList title="Letzte Spiele" list={games.data.filter((m) => m.state === "post").reverse()} onOpen={onOpen} />
          {!games.data.length && <div className="sp-empty">Keine Spiele gefunden.</div>}
        </>
      ) : <Status {...games} />}
    </>
  );
}

function MatchPage({ match }: { match: SportMatch }) {
  const detail = useLoad<MatchDetail>(() => matchDetail(match.id, match.league), [match.id]);
  const m = detail.data?.match ?? match;
  useEffect(() => {
    if (m.state !== "in") return;
    const t = window.setInterval(detail.reload, 30_000);
    return () => window.clearInterval(t);
  }, [m.state]); // eslint-disable-line react-hooks/exhaustive-deps
  const one = useMemo(() => [match], [match.id]); // eslint-disable-line react-hooks/exhaustive-deps
  const sources = useGameSources(match.state === "post" ? [] : one)[match.id];
  const icon = (type: string) => (/goal/i.test(type) ? "Tor ·" : /red/i.test(type) ? "Rot ·" : /yellow/i.test(type) ? "Gelb ·" : /sub/i.test(type) ? "Wechsel ·" : "·");
  return (
    <>
      <div className="sp-score">
        <div><Logo src={m.home.logo} size={72} /><b>{m.home.name}</b></div>
        <div className="sp-score-mid">
          <span className="sp-score-num">{m.state === "pre" ? "–:–" : `${m.home.score}:${m.away.score}`}</span>
          <small className={m.state === "in" ? "sp-live" : ""}>{m.state === "in" ? `● LIVE ${m.clock}` : m.state === "post" ? "Beendet" : kickoff(m)}</small>
          <small>{leagueLabel(m.league)}{m.venue ? ` · ${m.venue}` : ""}</small>
        </div>
        <div><Logo src={m.away.logo} size={72} /><b>{m.away.name}</b></div>
      </div>
      {match.state !== "post" && <MatchSources sources={sources} />}
      {!detail.data ? <Status {...detail} /> : (
        <div className="sp-detail">
          {detail.data.events.length > 0 && (
            <section className="sp-section"><h2>Ereignisse</h2>
              <ul className="sp-events">{detail.data.events.map((e, i) => (
                <li key={i} className={e.teamId === m.away.id ? "away" : ""}><span>{e.minute}</span> {icon(e.type)} {e.player || e.text}</li>
              ))}</ul>
            </section>
          )}
          {detail.data.stats.length > 0 && (
            <section className="sp-section"><h2>Statistik</h2>
              {detail.data.stats.map((s) => {
                const h = parseFloat(s.home) || 0, a = parseFloat(s.away) || 0, pct = h + a ? (h / (h + a)) * 100 : 50;
                return (
                  <div key={s.label} className="sp-stat">
                    <div className="sp-stat-top"><b>{s.home}</b><span>{s.label}</span><b>{s.away}</b></div>
                    <div className="sp-bar"><i style={{ width: `${pct}%` }} /></div>
                  </div>
                );
              })}
            </section>
          )}
          {detail.data.lineups.some((l) => l.players.length) && (
            <section className="sp-section"><h2>Aufstellungen</h2>
              <div className="sp-lineups">
                {[m.home, m.away].map((side) => {
                  const l = detail.data!.lineups.find((x) => x.teamId === side.id);
                  if (!l) return null;
                  return (
                    <div key={side.id}>
                      <h3>{side.name}{l.formation ? ` (${l.formation})` : ""}</h3>
                      <ul>{l.players.filter((p) => p.starter).map((p) => <li key={p.name}><span>{p.jersey}</span> {p.name} <small>{p.position}</small></li>)}</ul>
                      {l.players.some((p) => !p.starter) && <><h4>Bank</h4><ul className="bench">{l.players.filter((p) => !p.starter).map((p) => <li key={p.name}><span>{p.jersey}</span> {p.name}</li>)}</ul></>}
                    </div>
                  );
                })}
              </div>
            </section>
          )}
          {!detail.data.events.length && !detail.data.stats.length && <div className="sp-empty">Noch keine Spieldaten – vor dem Anpfiff gibt es meist nur die Ansetzung.</div>}
        </div>
      )}
    </>
  );
}

function MatchSources({ sources }: { sources?: GameSources }) {
  const { playChannel } = useAppPick("playChannel");
  if (!sources) return <section className="sp-section"><h2>Wo läuft das Spiel?</h2><div className="sp-empty"><Loader2 className="spin" /> Suche Sender und Streams …</div></section>;
  const now = Date.now();
  return (
    <section className="sp-section"><h2>Wo läuft das Spiel?</h2>
      {!sources.tv.length && !sources.addons.length && <div className="sp-empty">Noch keine Sender oder Streams gefunden. Kurz vor Anpfiff tauchen oft mehr auf.</div>}
      <div className="sp-sources">
        {sources.tv.map((s) => (
          <button type="button" key={s.channel.id} className="sp-source" onClick={() => void playChannel(s.channel)}>
            {s.channel.logo ? <img src={s.channel.logo} alt="" /> : <span className="sp-source-ph">TV</span>}
            <span><b>{s.channel.name}{s.uhd ? " · 4K" : ""}</b>
              <small>{s.program ? `${s.program.startUtcMillis <= now && s.program.endUtcMillis > now ? "● läuft · " : `${new Date(s.program.startUtcMillis).toLocaleTimeString("de-DE", { hour: "2-digit", minute: "2-digit" })} · `}${s.program.title}` : "Event-Sender"}</small></span>
          </button>
        ))}
        {sources.addons.map((e) => <AddonSource key={e.key} event={e} />)}
      </div>
    </section>
  );
}

function AddonSource({ event }: { event: SportsAddonEvent }) {
  const { addons, playChannel } = useAppPick("addons", "playChannel");
  const [streams, setStreams] = useState<SportsAddonStream[] | null>(null);
  const [loading, setLoading] = useState(false);
  const ctrl = useRef<AbortController | null>(null);
  useEffect(() => () => ctrl.current?.abort(), []);
  const load = async () => {
    ctrl.current?.abort();
    const c = new AbortController(); ctrl.current = c;
    setLoading(true);
    try { const r = await resolveSportsAddon(event, addons, c.signal); if (!c.signal.aborted) setStreams(r); }
    catch { if (!c.signal.aborted) setStreams([]); }
    finally { if (!c.signal.aborted) setLoading(false); }
  };
  const play = (s: SportsAddonStream) => {
    if (s.external) { window.open(s.url, "_blank", "noopener,noreferrer"); return; }
    void playChannel({ id: `sports-addon:${event.addonId}:${event.type}:${event.eventId}`, name: event.title, group: event.addonName, streamUrl: s.url, requestHeaders: s.headers });
  };
  return (
    <>
      <button type="button" className="sp-source" aria-busy={loading} onClick={() => { if (!loading) void load(); }}>
        <span className="sp-source-ph">▶</span>
        <span><b>{event.addonName}{event.live ? " · ● live" : ""}</b><small>{loading ? "Lade Streams …" : streams?.length === 0 ? "Keine Streams – erneut versuchen" : event.title}</small></span>
      </button>
      {streams?.map((s, i) => (
        <button type="button" key={i} className="sp-source sp-source-sub" onClick={() => play(s)}>
          <span className="sp-source-ph">▶</span><span><b>{s.name}</b><small>{[s.description, s.external ? "öffnet extern ↗" : ""].filter(Boolean).join(" · ")}</small></span>
        </button>
      ))}
    </>
  );
}

export function SportScreen() {
  const { settings, updateSettings, setToast } = useAppPick("settings", "updateSettings", "setToast");
  const myTeams = useMemo(() => decodeTeams(settings.fiptvSportTeamsJson), [settings.fiptvSportTeamsJson]);
  const [tab, setTab] = useState<Tab>("hub");
  const [stack, setStack] = useState<Page[]>([]);
  const [key, setKey] = useState(0);
  const page = stack[stack.length - 1] ?? null;
  const push = (p: Page) => { setStack((s) => [...s, p]); window.scrollTo?.({ top: 0 }); };
  const back = useCallback(() => setStack((s) => s.slice(0, -1)), []);

  const toggle = useCallback((team: SportTeam) => {
    const exists = myTeams.some((t) => t.id === team.id);
    const next = exists ? myTeams.filter((t) => t.id !== team.id) : [...myTeams, team];
    updateSettings({ fiptvSportTeamsJson: encodeTeams(next) });
    setToast(exists ? `${team.name} entfernt` : `${team.name} ist jetzt Lieblingsteam`);
  }, [myTeams, updateSettings, setToast]);

  useEffect(() => {
    if (!page) return;
    const onKey = (e: KeyboardEvent) => {
      const el = e.target as HTMLElement | null;
      if (el && /^(INPUT|TEXTAREA)$/.test(el.tagName)) return;
      if (e.key === "Escape" || e.key === "Backspace") { e.preventDefault(); back(); }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [page, back]);

  const openMatch = (m: SportMatch) => push({ kind: "match", match: m });
  const openTeam = (t: SportTeam) => push({ kind: "team", team: t });

  return (
    <div className="sp-screen">
      <div className="sp-head">
        {page ? <button type="button" className="sp-btn" onClick={back}><ArrowLeft size={16} /> Zurück</button> : <h1>Sport</h1>}
        {!page && (
          <div className="sp-tabs">
            <button type="button" className={tab === "hub" ? "active" : ""} onClick={() => setTab("hub")}>Übersicht</button>
            <button type="button" className={tab === "mine" ? "active" : ""} onClick={() => setTab("mine")}>Meine Teams</button>
            <button type="button" className={tab === "football" ? "active" : ""} onClick={() => setTab("football")}><FiptvIcon name="Football" size={18} /> Fußball</button>
            <button type="button" className={tab === "f1" ? "active" : ""} onClick={() => setTab("f1")}><FiptvIcon name="Formula1" size={18} /> Formel 1</button>
            <button type="button" className={tab === "tennis" ? "active" : ""} onClick={() => setTab("tennis")}><FiptvIcon name="Tennis" size={18} /> Tennis</button>
            <button type="button" className={tab === "wrestling" ? "active" : ""} onClick={() => setTab("wrestling")}><FiptvIcon name="Wrestling" size={18} /> Wrestling</button>
          </div>
        )}
        <button type="button" className="sp-btn" onClick={() => setKey((k) => k + 1)} title="Neu laden"><RefreshCw size={16} /></button>
      </div>
      <div key={key}>
        {page?.kind === "match" ? <MatchPage match={page.match} />
          : page?.kind === "team" ? <TeamPage team={page.team} myTeams={myTeams} toggle={toggle} onOpen={openMatch} />
          : tab === "hub" ? <Hub myTeams={myTeams} onOpen={openMatch} />
          : page?.kind === "league" ? <LeagueView league={page.league} myTeams={myTeams} toggle={toggle} onOpen={openMatch} onTeam={openTeam} />
          : tab === "mine" ? <MineView myTeams={myTeams} toggle={toggle} onTeam={openTeam} />
          : tab === "football" ? <FootballView onLeague={(l) => push({ kind: "league", league: l })} />
          : tab === "f1" ? <F1View />
          : tab === "tennis" ? <TennisView />
          : <WrestlingView />}
      </div>
    </div>
  );
}
