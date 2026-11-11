"use client";
// fIPTV: Formel 1, Tennis und Wrestling im Sport-Bereich (wie in der Fire-TV-App),
// jeweils mit „Im TV“: passende Sendungen im TV-Programm des Anbieters.
import { Loader2, X } from "lucide-react";
import { useEffect, useState } from "react";
import { useAppPick } from "@/lib/store";
import { loadIptvGuideForChannels } from "@/lib/iptv";
import type { IptvChannel, IptvProgram } from "@/lib/types";
import {
  AEW_YOUTUBE, SPORT_CHANNELS, TV_PATTERNS, WWE_YOUTUBE, f1Standings, f1Weekends, tennisRanking, tennisTournaments, wrestlingShows,
  type F1Standing, type WrestlingShow
} from "@/lib/fiptvMoreSports";
import { channelVideos, type YtVideo } from "@/lib/fiptvYouTube";
import { FiptvIcon, type FiptvIconName } from "@/components/fiptv/FiptvIcon";

function useLoad<T>(load: () => Promise<T>, deps: unknown[]) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState("");
  const [n, setN] = useState(0);
  useEffect(() => {
    let cancelled = false;
    setData(null); setError("");
    load().then((d) => { if (!cancelled) setData(d); })
      .catch((e) => { if (!cancelled) setError(e instanceof Error ? e.message : "Fehler beim Laden"); });
    return () => { cancelled = true; };
  }, [...deps, n]); // eslint-disable-line react-hooks/exhaustive-deps
  return { data, error, reload: () => setN((x) => x + 1) };
}

function Wait({ error, reload }: { error: string; reload: () => void }) {
  if (error) return <div className="sp-empty">{error} <button className="sp-btn" type="button" onClick={reload}>Erneut versuchen</button></div>;
  return <div className="sp-empty"><Loader2 className="spin" /> Lade …</div>;
}

const day = (ms: number) => ms ? new Date(ms).toLocaleDateString("de-DE", { weekday: "short", day: "2-digit", month: "2-digit" }) : "";
const clock = (ms: number) => ms ? new Date(ms).toLocaleTimeString("de-DE", { hour: "2-digit", minute: "2-digit" }) : "";
const Flag = ({ src }: { src?: string }) => (src ? <img className="sp-logo" src={src} alt="" width={20} height={14} loading="lazy" /> : null);

const TAB_ICONS: Record<string, FiptvIconName | undefined> = {
  "Im TV": "OnTv", Videos: "PlayCircle", Rennwochenenden: "Calendar", Turniere: "Calendar", Shows: "Calendar",
  "Fahrer-WM": "Trophy", "Team-WM": "Trophy", Weltrangliste: "Trophy"
};

function SubTabs<T extends string>({ value, onChange, tabs }: { value: T; onChange: (v: T) => void; tabs: Array<[T, string]> }) {
  return (
    <div className="sp-tabs small" style={{ marginBottom: 16 }}>
      {tabs.map(([id, label]) => (
        <button key={id} type="button" className={value === id ? "active" : ""} onClick={() => onChange(id)}>
          {TAB_ICONS[label] && <FiptvIcon name={TAB_ICONS[label]!} size={16} />} {label}
        </button>
      ))}
    </div>
  );
}

// ---------- Formel 1 ----------

export function F1View() {
  const [tab, setTab] = useState<"weekends" | "drivers" | "teams" | "tv">("weekends");
  return (
    <>
      <SubTabs value={tab} onChange={setTab} tabs={[["weekends", "Rennwochenenden"], ["drivers", "Fahrer-WM"], ["teams", "Team-WM"], ["tv", "Im TV"]]} />
      {tab === "weekends" ? <F1Weekends /> : tab === "tv" ? <TvListings pattern={TV_PATTERNS.f1} sport="Formel 1" /> : <F1Table which={tab} />}
    </>
  );
}

function F1Weekends() {
  const { data, error, reload } = useLoad(f1Weekends, []);
  if (!data) return <Wait error={error} reload={reload} />;
  const now = Date.now();
  const upcoming = data.weekends.filter((w) => w.sessions.some((s) => s.state !== "post"));
  const past = data.weekends.filter((w) => w.sessions.every((s) => s.state === "post")).reverse();
  const rest = data.calendar.filter((c) => c.end > now && !data.weekends.some((w) => w.name === c.name));
  return (
    <>
      {[...upcoming, ...past].map((w) => (
        <section key={w.id} className="sp-section">
          <h2>{w.sessions.some((s) => s.state === "in") && <span className="sp-live">● LIVE · </span>}{w.name}</h2>
          <div style={{ opacity: 0.6, marginTop: -6, marginBottom: 8 }}>{[w.circuit, w.city].filter(Boolean).join(" · ")}</div>
          <table className="sp-table"><tbody>
            {w.sessions.map((s) => (
              <tr key={s.label + s.start}>
                <td className="l" style={{ width: 130 }}><b>{s.label}</b></td>
                <td className="l" style={{ width: 170 }}>{s.state === "in" ? <span className="sp-live">● läuft gerade</span> : s.state === "post" ? `${day(s.start)} · beendet` : `${day(s.start)} ${clock(s.start)} Uhr`}</td>
                <td className="l">{s.state !== "pre" && s.results.slice(0, s.label === "Rennen" ? 10 : 3).map((r) => `${r.pos}. ${r.driver}`).join("   ")}</td>
              </tr>
            ))}
          </tbody></table>
        </section>
      ))}
      {!data.weekends.length && <div className="sp-empty">Gerade keine Rennwochenenden.</div>}
      {rest.length > 0 && (
        <section className="sp-section"><h2><FiptvIcon name="Calendar" /> Weitere Rennen der Saison</h2>
          <table className="sp-table"><tbody>{rest.map((c) => <tr key={c.name + c.start}><td className="l">{c.name}</td><td>{day(c.start)} – {day(c.end)}</td></tr>)}</tbody></table>
        </section>
      )}
    </>
  );
}

function F1Table({ which }: { which: "drivers" | "teams" }) {
  const { data, error, reload } = useLoad(f1Standings, []);
  if (!data) return <Wait error={error} reload={reload} />;
  const list: F1Standing[] = which === "drivers" ? data.drivers : data.teams;
  return (
    <section className="sp-section"><h2><FiptvIcon name="Trophy" /> {which === "drivers" ? "Fahrer-Weltmeisterschaft" : "Konstrukteurs-Weltmeisterschaft"}</h2>
      <table className="sp-table"><tbody>
        {list.map((r) => <tr key={r.rank + r.name}><td>{r.rank}</td><td className="l"><Flag src={r.flag} /> {r.name}</td><td><b>{r.points} Pkt</b></td></tr>)}
      </tbody></table>
    </section>
  );
}

// ---------- Tennis ----------

export function TennisView() {
  const [tour, setTour] = useState<"atp" | "wta">("atp");
  const [tab, setTab] = useState<"events" | "ranking" | "tv">("events");
  return (
    <>
      <div style={{ display: "flex", gap: 16, flexWrap: "wrap" }}>
        <SubTabs value={tour} onChange={setTour} tabs={[["atp", "ATP Herren"], ["wta", "WTA Damen"]]} />
        <SubTabs value={tab} onChange={setTab} tabs={[["events", "Turniere"], ["ranking", "Weltrangliste"], ["tv", "Im TV"]]} />
      </div>
      {tab === "events" ? <TennisEvents tour={tour} /> : tab === "ranking" ? <TennisRanking tour={tour} /> : <TvListings pattern={TV_PATTERNS.tennis} sport="Tennis" />}
    </>
  );
}

function TennisEvents({ tour }: { tour: "atp" | "wta" }) {
  const { data, error, reload } = useLoad(() => tennisTournaments(tour), [tour]);
  if (!data) return <Wait error={error} reload={reload} />;
  if (!data.length) return <div className="sp-empty">Diese Woche keine Turniere (oder Saisonpause).</div>;
  return (
    <>
      {data.map((t) => (
        <section key={t.id} className="sp-section"><h2><FiptvIcon name={t.major ? "Trophy" : "Tennis"} /> {t.name}</h2>
          <table className="sp-table"><tbody>
            {t.matches.slice(0, 40).map((m) => (
              <tr key={m.id}>
                <td className="l" style={{ width: 140 }}><small style={{ opacity: 0.6 }}>{m.round}</small><br />
                  {m.state === "in" ? <span className="sp-live">● LIVE</span> : m.state === "post" ? <small>beendet</small> : <small>{day(m.start)} {clock(m.start)}</small>}</td>
                <td className="l">
                  {[m.p1, m.p2].map((p, i) => (
                    <div key={i} style={{ display: "flex", gap: 8, alignItems: "center", fontWeight: p.winner ? 700 : 400 }}>
                      <Flag src={p.flag} /><span style={{ flex: 1 }}>{p.name}</span>
                      {p.sets.map((x, j) => <span key={j} style={{ width: 34, textAlign: "center" }}>{x}</span>)}
                    </div>
                  ))}
                </td>
              </tr>
            ))}
          </tbody></table>
        </section>
      ))}
    </>
  );
}

function TennisRanking({ tour }: { tour: "atp" | "wta" }) {
  const { data, error, reload } = useLoad(() => tennisRanking(tour), [tour]);
  if (!data) return <Wait error={error} reload={reload} />;
  return (
    <section className="sp-section"><h2><FiptvIcon name="Trophy" /> {tour === "atp" ? "ATP" : "WTA"}-Weltrangliste</h2>
      <table className="sp-table"><tbody>
        {data.map((r) => (
          <tr key={r.rank + r.name}>
            <td>{r.rank}</td><td className="l"><Flag src={r.flag} /> {r.name}</td>
            <td style={{ color: r.trend.startsWith("▲") ? "#22c55e" : "#ef4444", width: 50 }}>{r.trend}</td><td>{r.points} Pkt</td>
          </tr>
        ))}
      </tbody></table>
    </section>
  );
}

// ---------- Wrestling ----------

export function WrestlingView() {
  const [tab, setTab] = useState<"shows" | "tv" | "videos">("shows");
  return (
    <>
      <SubTabs value={tab} onChange={setTab} tabs={[["shows", "Shows"], ["tv", "Im TV"], ["videos", "Videos"]]} />
      {tab === "shows" ? <WrestlingShows /> : tab === "tv" ? <TvListings pattern={TV_PATTERNS.wrestling} sport="Wrestling" /> : <WrestlingVideos />}
    </>
  );
}

function WrestlingShows() {
  const { data, error, reload } = useLoad(wrestlingShows, []);
  if (!data) return <Wait error={error} reload={reload} />;
  const cutoff = Date.now() - 4 * 3600_000;
  const next = data.filter((s) => s.start > cutoff);
  const past = data.filter((s) => s.start <= cutoff).reverse();
  const Show = ({ s }: { s: WrestlingShow }) => (
    <div className="sp-show">
      {s.image && <img src={s.image} alt="" loading="lazy" />}
      <div>
        <b>{s.promotion} · {s.name}</b>
        <div style={{ opacity: 0.65 }}>{[`${day(s.start)} ${clock(s.start)} Uhr`, s.venue].filter(Boolean).join(" · ")}</div>
        {s.card && <pre className="sp-card">{s.card.split("\n").filter(Boolean).slice(0, 10).join("\n")}</pre>}
      </div>
    </div>
  );
  return (
    <>
      {next.length > 0 && <section className="sp-section"><h2><FiptvIcon name="Calendar" /> Nächste Shows</h2>{next.map((s) => <Show key={s.promotion + s.name} s={s} />)}</section>}
      {past.length > 0 && <section className="sp-section"><h2><FiptvIcon name="Wrestling" /> Letzte Shows & Ergebnisse</h2>{past.map((s) => <Show key={s.promotion + s.name} s={s} />)}</section>}
      {!data.length && <div className="sp-empty">Keine Show-Daten verfügbar.</div>}
      <div className="sp-empty">In Deutschland: WWE Raw, SmackDown & große Events bei Netflix, AEW u. a. bei DAZN/TNT. Unter „Im TV“ siehst du, was dein Anbieter zeigt.</div>
    </>
  );
}

function WrestlingVideos() {
  const { data, error, reload } = useLoad(async () => {
    const lists = await Promise.allSettled([
      channelVideos({ id: WWE_YOUTUBE, title: "WWE", thumb: "" }),
      channelVideos({ id: AEW_YOUTUBE, title: "AEW", thumb: "" })
    ]);
    return lists.flatMap((r) => (r.status === "fulfilled" ? r.value : [])).sort((a, b) => b.published.localeCompare(a.published));
  }, []);
  const [playing, setPlaying] = useState<YtVideo | null>(null);
  if (!data) return <Wait error={error} reload={reload} />;
  return (
    <>
      <div className="sp-videos">
        {data.slice(0, 40).map((v) => (
          <button key={v.id} type="button" className="sp-video" onClick={() => setPlaying(v)}>
            <img src={v.thumb || `https://i.ytimg.com/vi/${v.id}/hqdefault.jpg`} alt="" loading="lazy" />
            <b>{v.title}</b><small>{v.channelTitle}</small>
          </button>
        ))}
      </div>
      {playing && (
        <div className="sp-player" role="dialog" aria-label={playing.title}>
          <div className="sp-player-bar"><b>{playing.title}</b><button className="sp-btn" type="button" onClick={() => setPlaying(null)} autoFocus><X size={16} /> Schließen</button></div>
          <iframe src={`https://www.youtube-nocookie.com/embed/${playing.id}?autoplay=1&rel=0&hl=de`} title={playing.title}
            allow="autoplay; encrypted-media; picture-in-picture; fullscreen" allowFullScreen />
        </div>
      )}
    </>
  );
}

// ---------- Im TV ----------

type Listing = { channel: IptvChannel; program: IptvProgram };

function TvListings({ pattern, sport }: { pattern: RegExp; sport: string }) {
  const { iptvSnapshot, settings, playChannel } = useAppPick("iptvSnapshot", "settings", "playChannel");
  const [state, setState] = useState<{ list: Listing[] | null; error: string }>({ list: null, error: "" });
  useEffect(() => {
    let cancelled = false;
    setState({ list: null, error: "" });
    (async () => {
      const all = iptvSnapshot.allChannels?.length ? iptvSnapshot.allChannels : iptvSnapshot.channels;
      if (!all.length) throw new Error("Live-TV ist noch nicht geladen – einmal den Reiter „TV“ öffnen.");
      // Sender mit passendem Namen (z. B. „Sky Sport F1“) und große Sport-Sender, höchstens 150
      const named = all.filter((c) => pattern.test(c.name));
      const sports = all.filter((c) => SPORT_CHANNELS.test(c.name) && !named.includes(c));
      const candidates = [...named, ...sports].slice(0, 150);
      const guide = { ...iptvSnapshot.nowNext, ...(await loadIptvGuideForChannels(settings.iptvPlaylists, candidates.filter((c) => !iptvSnapshot.nowNext[c.id]))) };
      const now = Date.now();
      const until = now + 72 * 3600_000;
      const found: Listing[] = [];
      for (const ch of candidates) {
        const g = guide[ch.id];
        if (!g) continue;
        const programs = [g.now, g.next, g.later, ...(g.upcoming ?? [])].filter((p): p is IptvProgram => Boolean(p));
        const hit = programs.find((p) => p.endUtcMillis > now && p.startUtcMillis < until && pattern.test(p.title));
        if (hit) found.push({ channel: ch, program: hit });
      }
      found.sort((a, b) => Number(b.program.startUtcMillis <= now) - Number(a.program.startUtcMillis <= now) || a.program.startUtcMillis - b.program.startUtcMillis);
      const seen = new Set<string>();
      return found.filter((l) => {
        const k = `${l.program.title}|${l.program.startUtcMillis}|${l.channel.name.replace(/\b(hd|fhd|uhd|4k|sd|raw)\b/gi, "").trim().toLowerCase()}`;
        if (seen.has(k)) return false;
        seen.add(k); return true;
      }).slice(0, 40);
    })().then((list) => { if (!cancelled) setState({ list, error: "" }); })
      .catch((e) => { if (!cancelled) setState({ list: [], error: e instanceof Error ? e.message : "Programm nicht verfügbar" }); });
    return () => { cancelled = true; };
  }, [pattern, iptvSnapshot, settings.iptvPlaylists]);

  if (!state.list) return <div className="sp-empty"><Loader2 className="spin" /> Durchsuche das TV-Programm …</div>;
  if (state.error) return <div className="sp-empty">{state.error}</div>;
  if (!state.list.length) return <div className="sp-empty">Gerade nichts zu {sport} im Programm deines Anbieters gefunden (nächste 3 Tage).</div>;
  const now = Date.now();
  return (
    <section className="sp-section"><h2><FiptvIcon name="OnTv" /> {sport} im TV</h2>
      <table className="sp-table"><tbody>
        {state.list.map(({ channel, program }) => {
          const live = program.startUtcMillis <= now;
          return (
            <tr key={channel.id + program.startUtcMillis}>
              <td className="l" style={{ width: 200 }}>{channel.logo && <img className="sp-logo" src={channel.logo} alt="" width={28} height={28} />} {channel.name}</td>
              <td className="l"><b>{program.title}</b><br /><small style={{ opacity: 0.6 }}>{day(program.startUtcMillis)} {clock(program.startUtcMillis)}–{clock(program.endUtcMillis)}</small></td>
              <td>{live ? <button type="button" className="sp-btn on" onClick={() => playChannel(channel)}>● Ansehen</button> : <small>ab {clock(program.startUtcMillis)}</small>}</td>
            </tr>
          );
        })}
      </tbody></table>
    </section>
  );
}
