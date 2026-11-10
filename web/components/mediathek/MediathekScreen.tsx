"use client";
// fIPTV: Mediatheken im Netflix-Stil (wie in der Fire-TV-App): großes Titelbild der
// gewählten Sendung, darunter Reihen; Klick öffnet die Detailseite mit weiteren Folgen.
import "./mediathek.css";

import { ArrowLeft, ChevronLeft, ChevronRight, Loader2, Play, RotateCcw, Search, X } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { useAppPick } from "@/lib/store";
import { ProgressLine, ResumeRow, resumeProgress, sourceFromResume, useResumeSync, useResumeVersion } from "@/components/fiptv/ResumeRow";
import { isResumable, remainingMin } from "@/lib/fiptvResume";
import {
  MEDIATHEK_CHANNELS, artHero, artTile, bestVideo, cachedArt, channelColor, channelLabel, formatDuration,
  isContainerTopic, lookupArt, mediathekSource, queryMediathek, showKey,
  type MediathekArt, type MediathekItem, type MediathekQuery
} from "@/lib/fiptvMediathek";

type RowDef = { key: string; title: string; query: MediathekQuery; minMinutes?: number; onePerShow?: boolean };

const ROWS: RowDef[] = [
  { key: "new", title: "Neu in den Mediatheken", query: {}, onePerShow: true },
  { key: "long", title: "Filme & lange Dokus", query: {}, minMinutes: 80, onePerShow: true },
  { key: "tatort", title: "Tatort", query: { topic: "Tatort" }, minMinutes: 60 },
  { key: "polizeiruf", title: "Polizeiruf 110", query: { topic: "Polizeiruf 110" }, minMinutes: 60 },
  { key: "terrax", title: "Terra X", query: { topic: "Terra X" } },
  { key: "doku", title: "Dokus & Reportagen", query: { search: "#doku" }, onePerShow: true },
  { key: "kids", title: "Für Kinder", query: { channel: "kika" }, minMinutes: 10, onePerShow: true },
  ...MEDIATHEK_CHANNELS.filter((c) => c.id !== "kika").map((c) => ({ key: `ch_${c.id}`, title: `Neu bei ${c.label}`, query: { channel: c.id }, onePerShow: true }))
];

type RowState = { items: MediathekItem[]; loading: boolean; error?: string; loaded: boolean };

const dateShort = new Intl.DateTimeFormat("de-DE", { weekday: "short", day: "2-digit", month: "2-digit" });
const dateLong = new Intl.DateTimeFormat("de-DE", { weekday: "long", day: "2-digit", month: "2-digit", year: "numeric", hour: "2-digit", minute: "2-digit" });

function meta(item: MediathekItem, long = false) {
  return [
    item.startMs ? (long ? dateLong : dateShort).format(new Date(item.startMs)) : null,
    formatDuration(item.durationSec) || null,
    item.videoHd ? "HD" : null,
    long && item.subtitleUrl ? "UT" : null
  ].filter(Boolean).join("  ·  ");
}

function useArt(item: MediathekItem | null | undefined) {
  const [art, setArt] = useState<MediathekArt | null | undefined>(() => (item ? cachedArt(item) : null));
  useEffect(() => {
    if (!item) { setArt(null); return; }
    let alive = true;
    const cached = cachedArt(item);
    setArt(cached);
    if (cached === undefined) void lookupArt(item).then((a) => { if (alive) setArt(a); });
    return () => { alive = false; };
  }, [item]);
  return art;
}

function ChannelBadge({ channel }: { channel: string }) {
  return <span className="fmt-badge" style={{ background: channelColor(channel) }}>{channelLabel(channel)}</span>;
}

function Tile({ item, onOpen, onFocus, wide, profileId }: { item: MediathekItem; onOpen: (i: MediathekItem) => void; onFocus?: (i: MediathekItem) => void; wide?: boolean; profileId: string }) {
  const art = useArt(item);
  useResumeVersion();
  const progress = resumeProgress(profileId, bestVideo(item));
  const image = artTile(art);
  const color = channelColor(item.channel);
  return (
    <button
      type="button"
      className={`fmt-tile ${wide ? "wide" : ""}`}
      onClick={() => onOpen(item)}
      onMouseEnter={() => onFocus?.(item)}
      onFocus={() => onFocus?.(item)}
      title={item.title}
    >
      {image
        ? <img src={image} alt="" loading="lazy" decoding="async" />
        : (
          <span className="fmt-tile-fallback" style={{ background: `linear-gradient(135deg, ${color}, ${color}8c 45%, #0c0d11)` }}>
            <span>{isContainerTopic(item.topic) ? item.title : item.topic || item.title}</span>
          </span>
        )}
      <span className="fmt-tile-badge"><ChannelBadge channel={item.channel} /></span>
      <span className="fmt-tile-caption">
        <strong>{item.title}</strong>
        <small>{progress?.finished ? "✓ gesehen" : progress && isResumable(progress) ? `noch ${remainingMin(progress)} Min.` : meta(item)}</small>
      </span>
      <ProgressLine entry={progress} />
    </button>
  );
}

function Row({ def, state, onVisible, onOpen, onFocus, onRetry, profileId }: {
  profileId: string; def: RowDef; state?: RowState; onVisible: () => void; onOpen: (i: MediathekItem) => void; onFocus: (i: MediathekItem) => void; onRetry: () => void;
}) {
  const ref = useRef<HTMLElement>(null);
  const scroller = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const io = new IntersectionObserver((entries) => { if (entries.some((e) => e.isIntersecting)) { onVisible(); io.disconnect(); } }, { rootMargin: "400px" });
    io.observe(el);
    return () => io.disconnect();
  }, [onVisible]);
  if (state?.loaded && !state.items.length && !state.error) return null;
  const scroll = (dir: number) => scroller.current?.scrollBy({ left: dir * scroller.current.clientWidth * 0.85, behavior: "smooth" });
  return (
    <section className="fmt-row" ref={ref}>
      <h2>{def.title}</h2>
      {state?.error ? (
        <div className="fmt-row-error">⚠ {state.error} <button type="button" className="fmt-pill" onClick={onRetry}>Erneut versuchen</button></div>
      ) : (
        <div className="fmt-row-wrap">
          <button type="button" className="fmt-arrow left" onClick={() => scroll(-1)} aria-label="Zurück" tabIndex={-1}><ChevronLeft /></button>
          <div className="fmt-row-scroller" ref={scroller}>
            {state?.items.length
              ? state.items.map((item) => <Tile key={item.id} item={item} onOpen={onOpen} onFocus={onFocus} profileId={profileId} />)
              : Array.from({ length: 6 }, (_, i) => <span key={i} className="fmt-tile fmt-skeleton" />)}
          </div>
          <button type="button" className="fmt-arrow right" onClick={() => scroll(1)} aria-label="Weiter" tabIndex={-1}><ChevronRight /></button>
        </div>
      )}
    </section>
  );
}

function Hero({ item, art, children }: { item?: MediathekItem | null; art?: MediathekArt | null; children?: ReactNode }) {
  const image = artHero(art);
  return (
    <div className="fmt-hero">
      <div className="fmt-hero-image" style={image ? { backgroundImage: `url("${image}")` } : undefined} />
      <div className="fmt-hero-shade" />
      <div className="fmt-hero-info">
        {item ? (
          <>
            <div className="fmt-hero-kicker">
              <ChannelBadge channel={item.channel} />
              {item.topic && item.topic !== item.title && <span>{item.topic}</span>}
            </div>
            <h1>{item.title}</h1>
            <p className="fmt-hero-meta">{meta(item, true)}</p>
            {item.description && <p className="fmt-hero-desc">{item.description}</p>}
            {children}
          </>
        ) : (
          <>
            <div className="fmt-hero-kicker"><span>ARD, ZDF, arte, 3sat & Co.</span></div>
            <h1>Mediathek</h1>
            <p className="fmt-hero-meta"><Loader2 className="fmt-spin" size={16} /> wird geladen …</p>
          </>
        )}
      </div>
    </div>
  );
}

export function MediathekScreen() {
  const { playStream, activeProfile } = useAppPick("playStream", "activeProfile");
  const profileId = activeProfile?.id ?? "";
  useResumeSync(profileId);
  useResumeVersion();
  const [longOnly, setLongOnly] = useState(true);
  const [rows, setRows] = useState<Record<string, RowState>>({});
  const [focused, setFocused] = useState<MediathekItem | null>(null);
  const [search, setSearch] = useState("");
  const [searchInput, setSearchInput] = useState("");
  const [results, setResults] = useState<MediathekItem[]>([]);
  const [resultTotal, setResultTotal] = useState<number | undefined>();
  const [searching, setSearching] = useState(false);
  const [searchError, setSearchError] = useState<string | null>(null);
  const [detail, setDetail] = useState<MediathekItem | null>(null);
  const [episodes, setEpisodes] = useState<MediathekItem[] | null>(null);
  const loadingRows = useRef(new Set<string>());

  const loadRow = useCallback((def: RowDef, force = false) => {
    if (!force && loadingRows.current.has(def.key)) return;
    loadingRows.current.add(def.key);
    setRows((r) => ({ ...r, [def.key]: { items: r[def.key]?.items ?? [], loading: true, loaded: false } }));
    const minDurationSec = def.minMinutes ? def.minMinutes * 60 : longOnly ? 20 * 60 : undefined;
    queryMediathek({ ...def.query, minDurationSec, size: def.onePerShow ? 60 : 30 })
      .then((page) => {
        const seen = new Set<string>();
        const items = def.onePerShow ? page.items.filter((i) => { const k = showKey(i); if (seen.has(k)) return false; seen.add(k); return true; }) : page.items;
        setRows((r) => ({ ...r, [def.key]: { items: items.slice(0, 30), loading: false, loaded: true } }));
      })
      .catch((err) => setRows((r) => ({ ...r, [def.key]: { items: [], loading: false, loaded: true, error: err instanceof Error ? err.message : String(err) } })));
  }, [longOnly]);

  // bei Umschalten „Nur ganze Sendungen“ alles neu
  useEffect(() => {
    loadingRows.current.clear();
    setRows({});
    ROWS.slice(0, 3).forEach((def) => loadRow(def));
  }, [loadRow]);

  // Suche
  useEffect(() => {
    if (!search) { setResults([]); setResultTotal(undefined); setSearchError(null); return; }
    const ctrl = new AbortController();
    setSearching(true); setSearchError(null); setResults([]);
    queryMediathek({ search, minDurationSec: longOnly ? 20 * 60 : undefined, size: 60 }, ctrl.signal)
      .then((page) => { setResults(page.items); setResultTotal(page.total); })
      .catch((err) => { if (!ctrl.signal.aborted) setSearchError(err instanceof Error ? err.message : String(err)); })
      .finally(() => { if (!ctrl.signal.aborted) setSearching(false); });
    return () => ctrl.abort();
  }, [search, longOnly]);

  const loadMoreResults = () => {
    setSearching(true);
    queryMediathek({ search, minDurationSec: longOnly ? 20 * 60 : undefined, size: 60, offset: results.length })
      .then((page) => { setResults((r) => [...r, ...page.items.filter((i) => !r.some((x) => x.id === i.id))]); setResultTotal(page.total); })
      .catch((err) => setSearchError(err instanceof Error ? err.message : String(err)))
      .finally(() => setSearching(false));
  };

  // Detailseite: weitere Folgen derselben Sendung
  useEffect(() => {
    setEpisodes(null);
    if (!detail || isContainerTopic(detail.topic)) { setEpisodes([]); return; }
    const ctrl = new AbortController();
    const channel = MEDIATHEK_CHANNELS.find((c) => detail.channel.toLowerCase().includes(c.id))?.id;
    queryMediathek({ topic: detail.topic, channel, size: 50 }, ctrl.signal)
      .then((page) => setEpisodes(page.items.filter((i) => i.topic.toLowerCase() === detail.topic.toLowerCase() && i.id !== detail.id)))
      .catch(() => { if (!ctrl.signal.aborted) setEpisodes([]); });
    return () => ctrl.abort();
  }, [detail]);

  useEffect(() => {
    if (!detail) return;
    const onKey = (e: KeyboardEvent) => { if (e.key === "Escape" || (e.key === "Backspace" && !(e.target instanceof HTMLInputElement))) { e.preventDefault(); setDetail(null); } };
    window.addEventListener("keydown", onKey);
    window.scrollTo({ top: 0 });
    return () => window.removeEventListener("keydown", onKey);
  }, [detail]);

  const firstItem = useMemo(() => ROWS.map((d) => rows[d.key]?.items[0]).find(Boolean) ?? null, [rows]);
  const heroItem = focused ?? firstItem;
  const heroArt = useArt(heroItem);
  const detailArt = useArt(detail);

  const play = (item: MediathekItem, fromStart = false) => {
    const source = mediathekSource(item, profileId, fromStart, artTile(cachedArt(item)));
    if (source) playStream(source);
  };
  const detailProgress = detail ? resumeProgress(profileId, bestVideo(detail)) : null;
  const detailResumable = detailProgress ? isResumable(detailProgress) : false;

  if (detail) {
    return (
      <div className="fmt">
        <Hero item={detail} art={detailArt}>
          <div className="fmt-actions">
            <button type="button" className="fmt-btn primary" onClick={() => play(detail)} autoFocus>
              <Play size={20} fill="currentColor" /> {detailResumable && detailProgress ? `Weiterschauen (noch ${remainingMin(detailProgress)} Min.)` : "Abspielen"}
            </button>
            {(detailResumable || detailProgress?.finished) && <button type="button" className="fmt-btn" onClick={() => play(detail, true)}><RotateCcw size={18} /> Von vorn</button>}
            <button type="button" className="fmt-btn" onClick={() => setDetail(null)}><ArrowLeft size={18} /> Zurück</button>
          </div>
        </Hero>
        {(episodes === null || episodes.length > 0) && (
          <section className="fmt-row">
            <h2>{episodes === null ? "Weitere Folgen werden geladen …" : `Weitere Folgen: ${detail.topic}`}</h2>
            <div className="fmt-grid">
              {(episodes ?? []).map((ep) => <Tile key={ep.id} item={ep} onOpen={setDetail} profileId={profileId} />)}
            </div>
          </section>
        )}
      </div>
    );
  }

  return (
    <div className="fmt">
      <header className="fmt-header">
        <h1 className="fmt-title">Mediathek</h1>
        <form className="fmt-search" onSubmit={(e) => { e.preventDefault(); setSearch(searchInput.trim()); }}>
          <Search size={16} aria-hidden />
          <input value={searchInput} onChange={(e) => setSearchInput(e.target.value)} placeholder="Suchen: Tatort · !zdf heute · #Thema" aria-label="In den Mediatheken suchen" />
          {search && <button type="button" className="fmt-clear" onClick={() => { setSearch(""); setSearchInput(""); }} aria-label="Suche beenden"><X size={16} /></button>}
        </form>
        <button type="button" className={`fmt-pill ${longOnly ? "active" : ""}`} onClick={() => setLongOnly((v) => !v)}>
          {longOnly ? "Nur ganze Sendungen" : "Auch kurze Clips"}
        </button>
      </header>

      {search ? (
        <section className="fmt-search-results">
          <p className="fmt-muted">
            {searchError ? `⚠ ${searchError}` : searching && !results.length ? "Wird gesucht …" : !results.length ? "Keine Sendungen gefunden." : resultTotal !== undefined ? `${resultTotal} Treffer` : ""}
          </p>
          <div className="fmt-grid">
            {results.map((item) => <Tile key={item.id} item={item} onOpen={setDetail} profileId={profileId} />)}
          </div>
          {resultTotal !== undefined && results.length < resultTotal && (
            <button type="button" className="fmt-btn more" onClick={loadMoreResults} disabled={searching}>{searching ? "Lädt …" : "Mehr laden"}</button>
          )}
        </section>
      ) : (
        <>
          <Hero item={heroItem} art={heroArt}>
            {heroItem && (
              <div className="fmt-actions">
                <button type="button" className="fmt-btn primary" onClick={() => play(heroItem)}><Play size={20} fill="currentColor" /> Abspielen</button>
                <button type="button" className="fmt-btn" onClick={() => setDetail(heroItem)}>Mehr Infos</button>
              </div>
            )}
          </Hero>
          <div className="fmt-rows">
            <ResumeRow profileId={profileId} kinds={["MEDIATHEK"]} onPlay={(e) => playStream(sourceFromResume(e))} />
            {ROWS.map((def) => (
              <Row
                key={def.key}
                def={def}
                state={rows[def.key]}
                onVisible={() => loadRow(def)}
                onOpen={setDetail}
                onFocus={setFocused}
                onRetry={() => loadRow(def, true)}
                profileId={profileId}
              />
            ))}
          </div>
        </>
      )}
    </div>
  );
}
