"use client";
// fIPTV: Filme und Serien des IPTV-Anbieters (eigener Bereich, wie der VOD-Reiter der Android-App).
import "./providerVod.css";

import { ArrowLeft, Film, Loader2, Play, RefreshCw, Search, Star, Tv } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useAppPick } from "@/lib/store";
import {
  episodeSource, loadSeriesDetail, loadVodCategories, loadVodItems, movieSource, vodProviders,
  type VodCategory, type VodItem, type VodKind, type VodSeriesDetail
} from "@/lib/fiptvProviderVod";

const PAGE = 240;
const ALL = "__all__";
const STATE_KEY = "fiptv.vod.state";

type Saved = { kind?: VodKind; provider?: string; category?: Partial<Record<VodKind, string>> };

function loadSaved(): Saved {
  try { return JSON.parse(localStorage.getItem(STATE_KEY) || "{}") as Saved; } catch { return {}; }
}
function saveState(state: Saved) {
  try { localStorage.setItem(STATE_KEY, JSON.stringify(state)); } catch { /* egal */ }
}

function friendlyError(error: unknown) {
  const text = error instanceof Error ? error.message : String(error);
  if (/temporarily paused|429|too many/i.test(text)) return "Der Anbieter begrenzt gerade die Anfragen. Bitte in einer Minute erneut versuchen.";
  if (/403|Attention|Cloudflare/i.test(text)) return "Der Anbieter lehnt diese Verbindung ab. Filme und Serien des Anbieters gehen nur in der Windows-App bzw. zu Hause.";
  return text || "Unbekannter Fehler";
}

function Poster({ src, name }: { src?: string; name: string }) {
  const [failed, setFailed] = useState(false);
  if (!src || failed) return <div className="fvod-poster fvod-poster-empty"><span>{name.slice(0, 2).toUpperCase()}</span></div>;
  return <img className="fvod-poster" src={src} alt="" loading="lazy" decoding="async" referrerPolicy="no-referrer" onError={() => setFailed(true)} />;
}

export function ProviderVodScreen() {
  const { settings, playStream } = useAppPick("settings", "playStream");
  const userAgent = settings.customUserAgent;
  const saved = useMemo(loadSaved, []);
  const [kind, setKind] = useState<VodKind>(saved.kind ?? "movies");
  const providers = useMemo(() => vodProviders(settings.iptvPlaylists ?? [], kind), [settings.iptvPlaylists, kind]);
  const [providerKey, setProviderKey] = useState<string | undefined>(saved.provider);
  const provider = providers.find((p) => p.key === providerKey) ?? providers[0];

  const [categories, setCategories] = useState<VodCategory[]>([]);
  const [categoryId, setCategoryId] = useState<string | undefined>(saved.category?.[saved.kind ?? "movies"]);
  const [items, setItems] = useState<VodItem[]>([]);
  const [loading, setLoading] = useState<"categories" | "items" | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [limit, setLimit] = useState(PAGE);
  const [reloadKey, setReloadKey] = useState(0);

  const [series, setSeries] = useState<VodItem | null>(null);
  const [detail, setDetail] = useState<VodSeriesDetail | null>(null);
  const [detailError, setDetailError] = useState<string | null>(null);
  const [season, setSeason] = useState<number | null>(null);

  useEffect(() => {
    saveState({ kind, provider: provider?.key, category: { ...loadSaved().category, ...(categoryId ? { [kind]: categoryId } : {}) } });
  }, [kind, provider?.key, categoryId]);

  // Kategorien laden
  useEffect(() => {
    if (!provider) return;
    let cancelled = false;
    setLoading("categories"); setError(null); setCategories([]);
    loadVodCategories(provider, kind, userAgent)
      .then((list) => {
        if (cancelled) return;
        setCategories(list);
        setCategoryId((current) => current && (current === ALL || list.some((c) => c.id === current)) ? current : (loadSaved().category?.[kind] && list.some((c) => c.id === loadSaved().category?.[kind]) ? loadSaved().category?.[kind] : list[0]?.id ?? ALL));
      })
      .catch((err) => { if (!cancelled) { setError(friendlyError(err)); setLoading(null); } });
    return () => { cancelled = true; };
  }, [provider, kind, userAgent, reloadKey]);

  // Titel der Kategorie laden
  useEffect(() => {
    if (!provider || !categoryId || !categories.length && categoryId !== ALL) return;
    let cancelled = false;
    setLoading("items"); setError(null); setItems([]); setLimit(PAGE);
    loadVodItems(provider, kind, categoryId === ALL ? undefined : categoryId, userAgent)
      .then((list) => { if (!cancelled) setItems(list); })
      .catch((err) => { if (!cancelled) setError(friendlyError(err)); })
      .finally(() => { if (!cancelled) setLoading(null); });
    return () => { cancelled = true; };
  }, [provider, kind, categoryId, categories.length, userAgent, reloadKey]);

  const filtered = useMemo(() => {
    const words = query.trim().toLowerCase().split(/\s+/).filter(Boolean);
    if (!words.length) return items;
    return items.filter((item) => { const name = item.name.toLowerCase(); return words.every((w) => name.includes(w)); });
  }, [items, query]);

  const switchKind = (next: VodKind) => {
    if (next === kind) return;
    setKind(next); setCategoryId(loadSaved().category?.[next]); setItems([]); setQuery(""); setSeries(null);
  };

  const openSeries = useCallback((item: VodItem) => {
    if (!provider) return;
    setSeries(item); setDetail(null); setDetailError(null); setSeason(null);
    loadSeriesDetail(provider, item.id, userAgent)
      .then((info) => { setDetail(info); setSeason(info.seasons[0] ?? null); })
      .catch((err) => setDetailError(friendlyError(err)));
  }, [provider, userAgent]);

  const activate = (item: VodItem) => {
    if (!provider) return;
    if (item.kind === "movies") playStream(movieSource(provider, item, userAgent));
    else openSeries(item);
  };

  if (!providers.length) {
    return (
      <div className="fvod">
        <h1 className="fvod-title">VOD</h1>
        <div className="fvod-empty">
          <Film size={36} aria-hidden />
          <p>Keine Xtream-Playlist mit Filmen oder Serien gefunden.</p>
          <p className="fvod-muted">Füge in den Einstellungen unter „Live TV / IPTV“ deine Anbieter-Playlist hinzu (Xtream-Link mit Benutzername und Passwort).</p>
        </div>
      </div>
    );
  }

  if (series) {
    const episodes = detail?.episodes.filter((ep) => season === null || ep.season === season) ?? [];
    return (
      <div className="fvod">
        <button type="button" className="fvod-back" onClick={() => setSeries(null)}><ArrowLeft size={18} aria-hidden /> Zurück</button>
        <div className="fvod-series-head">
          <Poster src={detail?.cover ?? series.poster} name={series.name} />
          <div>
            <h1 className="fvod-title">{series.name}</h1>
            <div className="fvod-meta">
              {series.year && <span>{series.year}</span>}
              {(detail?.rating ?? series.rating) && <span><Star size={13} aria-hidden /> {detail?.rating ?? series.rating}</span>}
              {detail?.genre && <span>{detail.genre}</span>}
            </div>
            {detail?.plot && <p className="fvod-plot">{detail.plot}</p>}
            {detail?.cast && <p className="fvod-muted fvod-cast">{detail.cast}</p>}
          </div>
        </div>
        {detailError && <div className="fvod-error">{detailError}</div>}
        {!detail && !detailError && <div className="fvod-loading"><Loader2 className="fvod-spin" aria-hidden /> Lade Folgen …</div>}
        {detail && (
          <>
            {detail.seasons.length > 1 && (
              <div className="fvod-chips" role="tablist" aria-label="Staffeln">
                {detail.seasons.map((s) => (
                  <button key={s} type="button" role="tab" aria-selected={s === season} className={`fvod-chip ${s === season ? "active" : ""}`} onClick={() => setSeason(s)}>Staffel {s}</button>
                ))}
              </div>
            )}
            <ol className="fvod-episodes">
              {episodes.map((ep) => (
                <li key={ep.id}>
                  <button type="button" className="fvod-episode" onClick={() => provider && playStream(episodeSource(provider, series, ep, userAgent))}>
                    <span className="fvod-episode-num">{ep.episode}</span>
                    <span className="fvod-episode-text">
                      <strong>{ep.title}</strong>
                      {ep.plot && <span className="fvod-muted">{ep.plot}</span>}
                    </span>
                    {ep.duration && <span className="fvod-muted fvod-episode-dur">{ep.duration}</span>}
                    <Play size={18} aria-hidden />
                  </button>
                </li>
              ))}
              {!episodes.length && <li className="fvod-muted">Keine Folgen gefunden.</li>}
            </ol>
          </>
        )}
      </div>
    );
  }

  return (
    <div className="fvod">
      <div className="fvod-header">
        <h1 className="fvod-title">VOD</h1>
        <div className="fvod-chips" role="tablist" aria-label="Art">
          <button type="button" role="tab" aria-selected={kind === "movies"} className={`fvod-chip ${kind === "movies" ? "active" : ""}`} onClick={() => switchKind("movies")}><Film size={15} aria-hidden /> Filme</button>
          <button type="button" role="tab" aria-selected={kind === "series"} className={`fvod-chip ${kind === "series" ? "active" : ""}`} onClick={() => switchKind("series")}><Tv size={15} aria-hidden /> Serien</button>
        </div>
        {providers.length > 1 && (
          <select className="fvod-select" value={provider?.key} onChange={(e) => { setProviderKey(e.target.value); setCategoryId(undefined); }} aria-label="Anbieter">
            {providers.map((p) => <option key={p.key} value={p.key}>{p.name}</option>)}
          </select>
        )}
        <label className="fvod-search">
          <Search size={16} aria-hidden />
          <input value={query} onChange={(e) => { setQuery(e.target.value); setLimit(PAGE); }} placeholder={kind === "movies" ? "Filme suchen" : "Serien suchen"} aria-label="Suchen" />
        </label>
        <button type="button" className="fvod-icon" onClick={() => setReloadKey((k) => k + 1)} aria-label="Neu laden" title="Neu laden"><RefreshCw size={16} aria-hidden /></button>
      </div>

      <div className="fvod-body">
        <nav className="fvod-categories" aria-label="Kategorien">
          <button type="button" className={`fvod-cat ${categoryId === ALL ? "active" : ""}`} onClick={() => setCategoryId(ALL)}>Alle</button>
          {categories.map((c) => (
            <button key={c.id} type="button" className={`fvod-cat ${categoryId === c.id ? "active" : ""}`} onClick={() => setCategoryId(c.id)}>{c.name}</button>
          ))}
          {loading === "categories" && <div className="fvod-loading"><Loader2 className="fvod-spin" aria-hidden /></div>}
        </nav>

        <section className="fvod-main">
          {error && (
            <div className="fvod-error">
              {error}
              <button type="button" className="fvod-chip" onClick={() => setReloadKey((k) => k + 1)}>Erneut versuchen</button>
            </div>
          )}
          {loading === "items" && <div className="fvod-loading"><Loader2 className="fvod-spin" aria-hidden /> Lade {kind === "movies" ? "Filme" : "Serien"} …</div>}
          {!loading && !error && query && categoryId !== ALL && (
            <button type="button" className="fvod-hint" onClick={() => setCategoryId(ALL)}>Nicht gefunden? In allen Kategorien suchen</button>
          )}
          <div className="fvod-grid">
            {filtered.slice(0, limit).map((item) => (
              <button key={`${item.kind}-${item.id}`} type="button" className="fvod-card" onClick={() => activate(item)} title={item.name}>
                <Poster src={item.poster} name={item.name} />
                <span className="fvod-card-name">{item.name}</span>
                <span className="fvod-card-meta">
                  {item.year && <span>{item.year}</span>}
                  {item.rating && <span><Star size={11} aria-hidden /> {item.rating}</span>}
                </span>
              </button>
            ))}
          </div>
          {!loading && !error && !filtered.length && items.length > 0 && <p className="fvod-muted">Keine Treffer.</p>}
          {!loading && !error && !items.length && categories.length > 0 && <p className="fvod-muted">Diese Kategorie ist leer.</p>}
          {filtered.length > limit && (
            <button type="button" className="fvod-more" onClick={() => setLimit((l) => l + PAGE)}>Mehr anzeigen ({filtered.length - limit} weitere)</button>
          )}
        </section>
      </div>
    </div>
  );
}
