"use client";
// fIPTV: Tor-Hinweise für gefolgte Teams (wie am Fire TV) – Tor, VAR, Anpfiff, Abpfiff als kurzer
// Hinweis oben rechts, auch während ein Film oder Sender läuft.
import "./goalAlerts.css";

import { useEffect, useRef, useState } from "react";
import { useAppPick } from "@/lib/store";
import { kidsLevelOf } from "@/lib/kids";
import { currentScoreboard, decodeTeams, matchDetail, teamMatches, type SportMatch } from "@/lib/fiptvSport";

const KEY = "fiptv.goalAlerts";
export const goalAlertsEnabled = () => { try { return localStorage.getItem(KEY) !== "off"; } catch { return true; } };

type Alert = { key: string; title: string; match: SportMatch; line: string; mine: boolean };

export function GoalAlertToggle() {
  const [on, setOn] = useState(true);
  useEffect(() => setOn(goalAlertsEnabled()), []);
  return (
    <div className="sp-head" style={{ marginBottom: 8 }}>
      <button type="button" className={`sp-btn ${on ? "on" : ""}`} onClick={() => {
        const next = !on; setOn(next);
        try { localStorage.setItem(KEY, next ? "on" : "off"); } catch { /* egal */ }
      }}>{on ? "🔔 Tor-Hinweise: an" : "🔕 Tor-Hinweise: aus"}</button>
      <small style={{ opacity: 0.65 }}>Tore, Anpfiff und Abpfiff deiner Teams als kurzer Hinweis – auch während du etwas anderes schaust.</small>
    </div>
  );
}

export function GoalAlerts() {
  const { settings, activeProfile } = useAppPick("settings", "activeProfile");
  const teamsJson = settings.fiptvSportTeamsJson;
  const kids = Boolean(kidsLevelOf(activeProfile));
  const [alert, setAlert] = useState<Alert | null>(null);
  const queue = useRef<Alert[]>([]);

  useEffect(() => {
    if (kids) return;
    let stopped = false;
    let timer = 0;
    let schedule: SportMatch[] = [];
    let scheduleAt = 0;
    let scheduleKey = "";
    const known = new Map<string, { h: number; a: number; state: string }>();

    const tick = async (): Promise<number> => {
      if (!goalAlertsEnabled()) return 5 * 60_000;
      const followed = decodeTeams(teamsJson);
      if (!followed.length) return 10 * 60_000;
      const ids = new Set(followed.map((t) => t.id));
      const now = Date.now();
      const key = [...ids].sort().join(",");
      if (key !== scheduleKey || now - scheduleAt > 30 * 60_000) {
        const lists = await Promise.allSettled(followed.map((t) => teamMatches(t)));
        const seen = new Set<string>();
        schedule = lists.flatMap((r) => (r.status === "fulfilled" ? r.value : []))
          .filter((m) => { const t = Date.parse(m.date); return t > now - 4 * 3600_000 && t < now + 36 * 3600_000; })
          .filter((m) => (seen.has(m.id) ? false : (seen.add(m.id), true)));
        scheduleAt = now; scheduleKey = key;
      }
      const active = schedule.filter((m) => { const t = Date.parse(m.date); return m.state === "in" || (m.state !== "post" && now > t - 10 * 60_000 && now < t + 150 * 60_000); });
      if (!active.length) {
        const next = schedule.filter((m) => m.state === "pre" && Date.parse(m.date) > now).map((m) => Date.parse(m.date)).sort((a, b) => a - b)[0];
        return next ? Math.min(15 * 60_000, Math.max(60_000, next - now - 10 * 60_000)) : 15 * 60_000;
      }
      for (const league of [...new Set(active.map((m) => m.league))]) {
        const games = await currentScoreboard(league).catch(() => [] as SportMatch[]);
        for (const g of games) {
          if (!ids.has(g.home.id) && !ids.has(g.away.id)) continue;
          const h = Number(g.home.score) || 0, a = Number(g.away.score) || 0;
          const before = known.get(g.id);
          known.set(g.id, { h, a, state: g.state });
          schedule = schedule.map((m) => (m.id === g.id ? g : m));
          if (!before) continue;
          if (h > before.h || a > before.a) {
            const side = h > before.h ? g.home : g.away;
            let line = g.clock;
            try {
              const d = await matchDetail(g.id, league);
              const goal = [...d.events].reverse().find((e) => /goal/i.test(e.type) && e.teamId === side.id);
              if (goal) line = `${goal.player || goal.text} (${goal.minute})`;
            } catch { /* ohne Torschütze */ }
            queue.current.push({ key: `${g.id}:${h}:${a}`, title: `⚽ TOR für ${side.name}!`, match: g, line, mine: ids.has(side.id) });
          } else if (h < before.h || a < before.a) {
            queue.current.push({ key: `${g.id}:var:${h}:${a}`, title: "🖥 Tor zurückgenommen (VAR)", match: g, line: g.clock, mine: false });
          }
          if (before.state !== "post" && g.state === "post") queue.current.push({ key: `${g.id}:end`, title: "🏁 Abpfiff", match: g, line: "", mine: false });
          else if (before.state === "pre" && g.state === "in") queue.current.push({ key: `${g.id}:start`, title: "▶ Anpfiff", match: g, line: "", mine: false });
        }
      }
      return 40_000;
    };

    const loop = async () => {
      if (stopped) return;
      const wait = await tick().catch(() => 120_000);
      if (!stopped) timer = window.setTimeout(loop, wait);
    };
    timer = window.setTimeout(loop, 15_000);
    return () => { stopped = true; window.clearTimeout(timer); };
  }, [teamsJson, kids]);

  // Warteschlange nacheinander anzeigen (je 9 Sekunden)
  useEffect(() => {
    const t = window.setInterval(() => {
      setAlert((current) => current ?? queue.current.shift() ?? null);
    }, 1_000);
    return () => window.clearInterval(t);
  }, []);
  useEffect(() => {
    if (!alert) return;
    const t = window.setTimeout(() => setAlert(null), 9_000);
    return () => window.clearTimeout(t);
  }, [alert]);

  if (!alert) return null;
  const m = alert.match;
  return (
    <div className={`goal-alert ${alert.mine ? "mine" : ""}`} role="status" onClick={() => setAlert(null)}>
      <b className="goal-title">{alert.title}</b>
      <div className="goal-score">
        {m.home.logo && <img src={m.home.logo} alt="" />}
        <span>{m.home.name} <b>{m.home.score || "0"} : {m.away.score || "0"}</b> {m.away.name}</span>
        {m.away.logo && <img src={m.away.logo} alt="" />}
      </div>
      {alert.line && <small>{alert.line}</small>}
    </div>
  );
}
