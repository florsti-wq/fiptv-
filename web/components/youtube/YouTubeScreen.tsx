"use client";
// fIPTV: YouTube wie am Fire TV – eigene Kanäle des Profils, neueste Videos, Wiedergabe im
// offiziellen YouTube-Player (eingebettet, mit YouTubes eigener Werbung/Regeln).
import "./youtube.css";

import { Loader2, Plus, RefreshCw, X } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useAppPick } from "@/lib/store";
import * as Account from "@/lib/fiptvYouTubeAccount";
import { ago, channelVideos, decodeChannels, encodeChannels, resolveChannel, views, type YtChannel, type YtVideo } from "@/lib/fiptvYouTube";

export function YouTubeScreen() {
  const { settings, updateSettings, setToast, activeProfile } = useAppPick("settings", "updateSettings", "setToast", "activeProfile");
  const profileId = activeProfile?.id ?? "default";
  // fIPTV: Google-Konto (Abos, Mag ich, Playlists)
  const [ytAvailable, setYtAvailable] = useState(false);
  const [account, setAccount] = useState<{ name: string; thumb: string } | null>(null);
  const [subs, setSubs] = useState<YtChannel[]>([]);
  const [playlists, setPlaylists] = useState<Array<{ id: string; title: string; thumb: string; count: number }>>([]);
  const [signIn, setSignIn] = useState<{ code: string; url: string; qr: string; message: string; error: boolean } | null>(null);
  const signInRun = useRef(0);
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
    void Account.accountAvailable().then(setYtAvailable);
    setAccount(Account.storedAccount(profileId));
  }, [profileId]);

  useEffect(() => {
    if (!account) { setSubs([]); setPlaylists([]); return; }
    Account.subscriptions(profileId).then(setSubs).catch((e) => setError(e instanceof Error ? e.message : "Abos nicht ladbar"));
    Account.playlists(profileId).then(setPlaylists).catch(() => setPlaylists([]));
  }, [account, profileId]);

  useEffect(() => {
    let cancelled = false;
    // Google-Konto: „Mag ich“ und Playlists direkt von YouTube
    if (active === "liked" || active.startsWith("pl:")) {
      setLoading(true); setError("");
      (active === "liked" ? Account.likedVideos(profileId) : Account.playlistVideos(profileId, active.slice(3)))
        .then((v) => { if (!cancelled) setVideos(v); })
        .catch((e) => { if (!cancelled) { setVideos([]); setError(e instanceof Error ? e.message : "YouTube nicht erreichbar"); } })
        .finally(() => { if (!cancelled) setLoading(false); });
      return () => { cancelled = true; };
    }
    const list = active === "all" ? channels : active === "subs" ? subs.slice(0, 30) : [...channels, ...subs].filter((c) => c.id === active).slice(0, 1);
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
  }, [channels, subs, active, reload, profileId]);

  const startSignIn = async () => {
    const run = ++signInRun.current;
    setSignIn({ code: "", url: "", qr: "", message: "Code wird angefordert …", error: false });
    try {
      const d = await Account.startSignIn();
      const QRCode = await import("qrcode");
      const qr = await QRCode.toDataURL(d.url, { margin: 1, width: 320 });
      setSignIn({ code: d.userCode, url: d.url, qr, message: "Warte auf Bestätigung am Handy …", error: false });
      let interval = d.interval * 1000;
      const until = Date.now() + d.expiresIn * 1000;
      while (signInRun.current === run && Date.now() < until) {
        await new Promise((r) => setTimeout(r, interval));
        if (signInRun.current !== run) return;
        const r = await Account.pollSignIn(profileId, d.deviceCode).catch(() => ({ status: "pending" } as { status: string; message?: string }));
        if (r.status === "slow") interval += 5_000;
        else if (r.status === "failed") { setSignIn((x) => x && { ...x, message: r.message || "Anmeldung fehlgeschlagen", error: true }); return; }
        else if (r.status === "done") {
          setSignIn(null);
          const acc = Account.storedAccount(profileId);
          setAccount(acc);
          setToast(`Angemeldet als ${acc?.name ?? "Google-Konto"}`);
          setActive("subs");
          return;
        }
      }
      if (signInRun.current === run) setSignIn((x) => x && { ...x, message: "Der Code ist abgelaufen – bitte neu starten.", error: true });
    } catch (e) {
      setSignIn({ code: "", url: "", qr: "", message: e instanceof Error ? e.message : "Anmeldung nicht möglich", error: true });
    }
  };
  const cancelSignIn = () => { signInRun.current++; setSignIn(null); };
  const signOut = async () => {
    if (!window.confirm(`Von Google abmelden (${account?.name})?`)) return;
    await Account.signOut(profileId);
    setAccount(null);
    if (active === "subs" || active === "liked" || active.startsWith("pl:")) setActive("all");
    setToast("Von Google abgemeldet");
  };
  const subscribeActive = async () => {
    const ch = channels.find((c) => c.id === active);
    if (!ch) return;
    try { await Account.subscribe(profileId, ch.id); setSubs((x) => [...x, ch]); setToast(`Abonniert: ${ch.title}`); }
    catch (e) { setToast(e instanceof Error ? e.message : "Abonnieren nicht möglich"); }
  };

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
        {ytAvailable && (account
          ? <button className="yt-btn" type="button" onClick={() => void signOut()} title="Google-Konto">{account.thumb && <img src={account.thumb} alt="" style={{ width: 22, height: 22, borderRadius: "50%" }} />} {account.name}</button>
          : <button className="yt-btn" type="button" onClick={() => void startSignIn()}>Mit Google anmelden</button>)}
      </div>

      {account && (
        <div className="yt-chips">
          <button type="button" className={`yt-chip ${active === "subs" ? "active" : ""}`} onClick={() => setActive("subs")}><span className="yt-dot" /> Abos</button>
          <button type="button" className={`yt-chip ${active === "liked" ? "active" : ""}`} onClick={() => setActive("liked")}><span className="yt-dot" /> Mag ich</button>
          {playlists.map((p) => (
            <button key={p.id} type="button" className={`yt-chip ${active === `pl:${p.id}` ? "active" : ""}`} onClick={() => setActive(`pl:${p.id}`)}>
              {p.thumb ? <img src={p.thumb} alt="" /> : <span className="yt-dot" />}{p.title} ({p.count})
            </button>
          ))}
        </div>
      )}
      {account && active === "subs" && subs.length > 0 && (
        <div className="yt-chips">
          {subs.map((c) => (
            <button key={c.id} type="button" className="yt-chip" onClick={() => setActive(c.id)}>
              {c.thumb ? <img src={c.thumb} alt="" /> : <span className="yt-dot" />}{c.title}
            </button>
          ))}
        </div>
      )}
      {account && channels.some((c) => c.id === active) && !subs.some((c) => c.id === active) && (
        <div style={{ marginBottom: 12 }}><button className="yt-btn" type="button" onClick={() => void subscribeActive()}>Abonnieren</button></div>
      )}

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

      {!channels.length && !account ? (
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

      {signIn && (
        <div className="yt-player" role="dialog" aria-label="Mit Google anmelden">
          <div className="yt-signin">
            <h2>Mit Google anmelden</h2>
            <div className="yt-signin-row">
              {signIn.qr && <img src={signIn.qr} alt="QR-Code zur Anmeldung" width={220} height={220} />}
              <ol>
                <li>QR-Code mit dem Handy scannen oder öffnen: <b>{signIn.url.replace(/^https:\/\//, "") || "…"}</b></li>
                <li>Diesen Code eingeben: <code>{signIn.code || "…"}</code></li>
                <li>Mit deinem Google-Konto anmelden und „Zulassen“ tippen.</li>
              </ol>
            </div>
            <p className={signIn.error ? "yt-error" : ""}>{signIn.message}</p>
            <small>fIPTV sieht dein Passwort nie. Der Zugang gilt nur für dieses Profil auf diesem PC.</small>
            <div style={{ display: "flex", gap: 10, marginTop: 14 }}>
              {signIn.error && <button className="yt-btn" type="button" onClick={() => void startSignIn()}>Neu starten</button>}
              <button className="yt-btn" type="button" onClick={cancelSignIn} autoFocus>Abbrechen</button>
            </div>
          </div>
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
