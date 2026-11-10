"use client";
// fIPTV: YouTube wie am Fire TV – eigene Kanäle des Profils, neueste Videos, Wiedergabe im
// offiziellen YouTube-Player (eingebettet, mit YouTubes eigener Werbung/Regeln).
import "./youtube.css";

import { Loader2, Plus, RefreshCw, X } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useAppPick } from "@/lib/store";
import { ago, channelVideos, decodeChannels, encodeChannels, resolveChannel, views, type YtChannel, type YtVideo } from "@/lib/fiptvYouTube";

export function YouTubeScreen() {
  const { settings, updateSettings, setToast } = useAppPick("settings", "updateSettings", "setToast");
  const channels = useMemo(() => decodeChannels(settings.fiptvYtChannelsJson), [settings.fiptvYtChannelsJson]);
  const [active, setActive] = useState<string>("all");
  const [videos, setVideos] = useState<YtVideo[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [input, setInput] = useState("");
  const [adding, setAdding] = useState(false);
  const [playing, setPlaying] = useState<YtVideo | null>(null);
  const [reload, setReload] = useState(0);

  useEffect(() => {
    let cancelled = false;
    const list = active === "all" ? channels : channels.filter((c) => c.id === active);
    if (!list.length) { setVideos([]); return; }
    setLoading(true); setError("");
    Promise.allSettled(list.map(channelVideos)).then((results) => {
      if (cancelled) return;
      const all = results.flatMap((r) => (r.status === "fulfilled" ? r.value : []));
      const failed = results.filter((r) => r.status === "rejected").length;
      all.sort((a, b) => b.published.localeCompare(a.published));
      setVideos(all.slice(0, active === "all" ? 120 : 60));
      setError(failed ? `${failed} Kanal/Kanäle konnten nicht geladen werden.` : "");
      setLoading(false);
    });
    return () => { cancelled = true; };
  }, [channels, active, reload]);

  const save = useCallback((list: YtChannel[]) => updateSettings({ fiptvYtChannelsJson: encodeChannels(list) }), [updateSettings]);

  const add = async () => {
    if (!input.trim()) return;
    setAdding(true); setError("");
    try {
      const channel = await resolveChannel(input);
      if (channels.some((c) => c.id === channel.id)) setToast(`${channel.title} ist schon dabei`);
      else { save([...channels, channel]); setToast(`${channel.title} hinzugefügt`); }
      setInput("");
    } catch (e) {
      setError(e instanceof Error ? e.message : "Kanal nicht gefunden");
    } finally { setAdding(false); }
  };

  const remove = (channel: YtChannel) => {
    if (!window.confirm(`${channel.title} entfernen?`)) return;
    save(channels.filter((c) => c.id !== channel.id));
    if (active === channel.id) setActive("all");
  };

  useEffect(() => {
    if (!playing) return;
    const onKey = (e: KeyboardEvent) => { if (e.key === "Escape" || e.key === "Backspace") { e.preventDefault(); setPlaying(null); } };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [playing]);

  return (
    <div className="yt-screen">
      <div className="yt-head">
        <h1>YouTube</h1>
        <form className="yt-add" onSubmit={(e) => { e.preventDefault(); void add(); }}>
          <input value={input} onChange={(e) => setInput(e.target.value)} placeholder="Kanal hinzufügen: @name oder Link" aria-label="Kanal hinzufügen" />
          <button className="yt-btn" type="submit" disabled={adding}>{adding ? <Loader2 size={16} className="spin" /> : <Plus size={16} />} Hinzufügen</button>
        </form>
        <button className="yt-btn" type="button" onClick={() => setReload((n) => n + 1)} title="Neu laden"><RefreshCw size={16} /></button>
      </div>

      {channels.length > 0 && (
        <div className="yt-chips">
          <button type="button" className={`yt-chip ${active === "all" ? "active" : ""}`} onClick={() => setActive("all")}><span className="yt-dot" /> Alle Kanäle</button>
          {channels.map((c) => (
            <button key={c.id} type="button" className={`yt-chip ${active === c.id ? "active" : ""}`} onClick={() => setActive(c.id)}>
              {c.thumb ? <img src={c.thumb} alt="" /> : <span className="yt-dot" />}
              {c.title}
              <span className="yt-x" role="button" aria-label={`${c.title} entfernen`} onClick={(e) => { e.stopPropagation(); remove(c); }}><X size={14} /></span>
            </button>
          ))}
        </div>
      )}

      {error && <div className="yt-error">{error}</div>}

      {!channels.length ? (
        <div className="yt-empty">Noch keine Kanäle.<br />Füge oben einen Kanal hinzu (z. B. <b>@tagesschau</b>) – oder am Fire TV, die Liste wird pro Profil abgeglichen.</div>
      ) : loading && !videos.length ? (
        <div className="yt-empty"><Loader2 className="spin" /> Lade Videos …</div>
      ) : (
        <div className="yt-grid">
          {videos.map((v) => (
            <button key={v.id} type="button" className="yt-card" onClick={() => setPlaying(v)}>
              <img src={v.thumb || `https://i.ytimg.com/vi/${v.id}/hqdefault.jpg`} alt="" loading="lazy" />
              <b>{v.title}</b>
              <small>{v.channelTitle} · {[views(v.views), ago(v.published)].filter(Boolean).join(" · ")}</small>
            </button>
          ))}
        </div>
      )}

      {playing && (
        <div className="yt-player" role="dialog" aria-label={playing.title}>
          <div className="yt-player-bar">
            <b>{playing.title}</b>
            <button className="yt-btn" type="button" onClick={() => setPlaying(null)} autoFocus><X size={16} /> Schließen</button>
          </div>
          <iframe
            src={`https://www.youtube-nocookie.com/embed/${playing.id}?autoplay=1&rel=0&hl=de`}
            title={playing.title}
            allow="autoplay; encrypted-media; picture-in-picture; fullscreen"
            allowFullScreen
          />
        </div>
      )}
    </div>
  );
}
