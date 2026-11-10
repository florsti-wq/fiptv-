"use client";
// fIPTV: Sport wie am Fire TV – Übersicht (live, heute, nächste Spiele, Ergebnisse),
// Tabellen, Meine Teams (pro Profil abgeglichen) und Spieldetails.
import "./sport.css";

import { ArrowLeft, Loader2, RefreshCw, Star } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useAppPick } from "@/lib/store";
import {
  LEAGUES, allMatches, decodeTeams, encodeTeams, isToday, kickoff, leagueLabel, matchDetail, matches, table, teamMatches, teams,
  type League, type MatchDetail, type SportMatch, type SportTeam, type TableRow
} from "@/lib/fiptvSport";

type Tab = "hub" | "mine" | League;
type Page = { kind: "team"; team: SportTeam } | { kind: "match"; match: SportMatch } | null;

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

function Logo({ src, size = 28 }: { src: string; size?: number }) {
  return src ? <img className="sp-logo" src={src} alt="" width={size} height={size} loading="lazy" /> : <span className="sp-logo" style={{ width: size, height: size }} />;
}

function MatchCard({ m, onOpen }: { m: SportMatch; onOpen: (m: SportMatch) => void }) {
  const live = m.state === "in";
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
    </button>
  );
}

function MatchList({ title, list, onOpen }: { title: string; list: SportMatch[]; onOpen: (m: SportMatch) => void }) {
  if (!list.length) return null;
  return (
    <section className="sp-section">
      <h2>{title}</h2>
      <div className="sp-row">{list.map((m) => <MatchCard key={`${m.league}-${m.id}`} m={m} onOpen={onOpen} />)}</div>
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
  if (!data) return <Status loading={loading} error={error} reload={reload} />;
  const mine = new Set(myTeams.map((t) => t.id));
  const isMine = (m: SportMatch) => mine.has(m.home.id) || mine.has(m.away.id);
  const live = data.filter((m) => m.state === "in");
  const today = data.filter((m) => m.state === "pre" && isToday(m));
  const next = data.filter((m) => m.state === "pre" && !isToday(m));
  const results = data.filter((m) => m.state === "post").reverse();
  const myNext = data.filter((m) => isMine(m) && m.state !== "post");
  return (
    <>
      <MatchList title="🔴 Jetzt live" list={live} onOpen={onOpen} />
      <MatchList title="⭐ Meine Teams" list={myNext.slice(0, 12)} onOpen={onOpen} />
      <MatchList title="Heute" list={today} onOpen={onOpen} />
      <MatchList title="Nächste Spiele" list={next.slice(0, 30)} onOpen={onOpen} />
      <MatchList title="Ergebnisse" list={results.slice(0, 30)} onOpen={onOpen} />
      {!data.length && <div className="sp-empty">Gerade keine Spiele in den nächsten Tagen.</div>}
    </>
  );
}

function LeagueView({ league, myTeams, toggle, onOpen, onTeam }: {
  league: League; myTeams: SportTeam[]; toggle: (t: SportTeam) => void; onOpen: (m: SportMatch) => void; onTeam: (t: SportTeam) => void;
}) {
  const games = useLoad(() => matches(league), [league]);
  const tab = useLoad(() => table(league), [league]);
  const mine = new Set(myTeams.map((t) => t.id));
  const groups = useMemo(() => {
    const map = new Map<string, TableRow[]>();
    for (const row of tab.data ?? []) map.set(row.group ?? "", [...(map.get(row.group ?? "") ?? []), row]);
    return [...map.entries()];
  }, [tab.data]);
  return (
    <>
      {games.data ? (
        <>
          <MatchList title="Live & demnächst" list={games.data.filter((m) => m.state !== "post")} onOpen={onOpen} />
          <MatchList title="Ergebnisse" list={games.data.filter((m) => m.state === "post").reverse()} onOpen={onOpen} />
        </>
      ) : <Status {...games} />}
      <section className="sp-section">
        <h2>Tabelle</h2>
        {tab.data ? groups.map(([group, rows]) => (
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
        )) : <Status {...tab} />}
      </section>
    </>
  );
}

function MineView({ myTeams, toggle, onTeam }: { myTeams: SportTeam[]; toggle: (t: SportTeam) => void; onTeam: (t: SportTeam) => void }) {
  const [league, setLeague] = useState<League>("ger.1");
  const all = useLoad(() => teams(league), [league]);
  const mine = new Set(myTeams.map((t) => t.id));
  return (
    <>
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
        <div className="sp-tabs small">{LEAGUES.map((l) => <button key={l.id} type="button" className={league === l.id ? "active" : ""} onClick={() => setLeague(l.id)}>{l.label}</button>)}</div>
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
  const icon = (type: string) => (/goal/i.test(type) ? "⚽" : /red/i.test(type) ? "🟥" : /yellow/i.test(type) ? "🟨" : /sub/i.test(type) ? "🔁" : "•");
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
            {LEAGUES.map((l) => <button key={l.id} type="button" className={tab === l.id ? "active" : ""} onClick={() => setTab(l.id)}>{l.label}</button>)}
          </div>
        )}
        <button type="button" className="sp-btn" onClick={() => setKey((k) => k + 1)} title="Neu laden"><RefreshCw size={16} /></button>
      </div>
      <div key={key}>
        {page?.kind === "match" ? <MatchPage match={page.match} />
          : page?.kind === "team" ? <TeamPage team={page.team} myTeams={myTeams} toggle={toggle} onOpen={openMatch} />
          : tab === "hub" ? <Hub myTeams={myTeams} onOpen={openMatch} />
          : tab === "mine" ? <MineView myTeams={myTeams} toggle={toggle} onTeam={openTeam} />
          : <LeagueView league={tab} myTeams={myTeams} toggle={toggle} onOpen={openMatch} onTeam={openTeam} />}
      </div>
    </div>
  );
}
