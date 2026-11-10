"use client";
// fIPTV: „Weiterschauen“-Reihe für Anbieter-VOD und Mediathek (abgeglichen mit Fire TV/Handy).
import "./resumeRow.css";

import { Play, X } from "lucide-react";
import { useEffect, useState, useSyncExternalStore } from "react";
import { authClient } from "@/lib/store";
import { syncFiptvResume } from "@/lib/cloud";
import {
  continueWatching, progressOf, remainingMin, removeResume, resumeEntry, subscribeResume,
  type FiptvResumeEntry, type FiptvResumeKind
} from "@/lib/fiptvResume";
import { inferQuality, toSource } from "@/lib/xtreamVod";
import type { StreamSource } from "@/lib/types";

let version = 0;
const subscribe = (listener: () => void) => subscribeResume(() => { version += 1; listener(); });
const getVersion = () => version;

/** Neu zeichnen, wenn sich ein Fortschritt ändert */
export function useResumeVersion() {
  return useSyncExternalStore(subscribe, getVersion, () => 0);
}

/** Beim Öffnen eines Bereichs den Stand der anderen Geräte holen */
export function useResumeSync(profileId: string) {
  useEffect(() => {
    if (!profileId) return;
    void syncFiptvResume(authClient, profileId).catch(() => undefined);
  }, [profileId]);
}

export function resumeProgress(profileId: string, url?: string) {
  if (!url) return null;
  return resumeEntry(profileId, url) ?? null;
}

export function sourceFromResume(entry: FiptvResumeEntry, userAgent?: string): StreamSource {
  const info = { url: entry.url, kind: entry.kind, title: entry.title, subtitle: entry.subtitle, image: entry.image, season: entry.season, episode: entry.episode };
  const start = Math.floor(entry.positionMs / 1000);
  if (entry.kind === "MEDIATHEK") {
    return { source: entry.title, addonName: "Mediathek", addonId: "fiptv_mediathek", quality: "", size: "", url: entry.url, fiptvResume: info, resumePositionSeconds: start };
  }
  const name = entry.subtitle ? `${entry.subtitle} – ${entry.title}` : entry.title;
  return { ...toSource(name, entry.url, inferQuality(name), entry.subtitle || "Anbieter", userAgent), fiptvResume: info, resumePositionSeconds: start };
}

export function ProgressLine({ entry }: { entry: FiptvResumeEntry | null | undefined }) {
  if (!entry || entry.finished || entry.positionMs < 60_000 || entry.durationMs <= 0) return null;
  return <span className="fres-progress"><span style={{ width: `${Math.round(progressOf(entry) * 100)}%` }} /></span>;
}

export function ResumeRow({ profileId, kinds, onPlay }: { profileId: string; kinds: FiptvResumeKind[]; onPlay: (entry: FiptvResumeEntry) => void }) {
  useResumeVersion();
  const [, force] = useState(0);
  const entries = continueWatching(profileId, kinds);
  if (!entries.length) return null;
  return (
    <section className="fres">
      <h2>Weiterschauen</h2>
      <div className="fres-scroller">
        {entries.map((e) => (
          <div key={e.url} className="fres-item">
            <button type="button" className="fres-card" onClick={() => onPlay(e)} title={e.title}>
              {e.image ? <img src={e.image} alt="" loading="lazy" /> : <span className="fres-fallback">{e.subtitle || e.title}</span>}
              <span className="fres-play"><Play size={26} fill="currentColor" /></span>
              <span className="fres-caption">
                <strong>{e.title}</strong>
                <small>{e.subtitle ? `${e.subtitle} · ` : ""}noch {remainingMin(e)} Min.</small>
              </span>
              <ProgressLine entry={e} />
            </button>
            <button
              type="button"
              className="fres-remove"
              aria-label="Aus Weiterschauen entfernen"
              title="Aus Weiterschauen entfernen"
              onClick={() => { removeResume(profileId, e.url); force((n) => n + 1); void syncFiptvResume(authClient, profileId).catch(() => undefined); }}
            ><X size={14} /></button>
          </div>
        ))}
      </div>
    </section>
  );
}
