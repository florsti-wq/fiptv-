"use client";
import { useEffect, useMemo, useRef, useState } from "react";
import { Check, Plus, Trash2, X } from "lucide-react";
import type { IptvChannel, IptvNowNext } from "@/lib/types";
import { cleanChannelName } from "@/lib/fiptvChannelCleanup";
import {
  createCustomGroup, customGroups, deleteCustomGroup, EPG_OFFSET_CHOICES, epgMap, epgOffsetLabel, epgOffsets, PRESET_GROUP_NAMES,
  setChannelEpgOffset, setEpgSource, toggleInCustomGroup,
} from "@/lib/fiptvLiveCustom";

/** Gemeinsamer Rahmen – nutzt das Aussehen des Sendermenüs. */
function Frame({ title, hint, onClose, children }: { title: string; hint?: string; onClose: () => void; children: React.ReactNode }) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const d = ref.current;
    if (d && !d.open) d.showModal();
    d?.querySelector<HTMLElement>("button[role=menuitem]:not(:disabled), input")?.focus();
  }, []);
  return (
    <dialog ref={ref} className="livetv-channel-menu fiptv-live-dialog" aria-label={title}
      onCancel={(e) => { e.preventDefault(); onClose(); }}
      onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <header><strong>{title}</strong><button type="button" onClick={onClose} aria-label="Schließen"><X size={18} /></button></header>
      {hint && <p className="fiptv-live-dialog-hint">{hint}</p>}
      <div role="menu" className="fiptv-live-dialog-list">{children}</div>
    </dialog>
  );
}

const key = (name: string) => cleanChannelName(name).toLowerCase().replace(/[^\p{L}\p{N} ]+/gu, "").trim();

/** Programmdaten eines anderen Senders übernehmen – ähnlichste Sender zuerst. */
export function EpgMapDialog({ channel, channels, nowNext, onClose }: {
  channel: IptvChannel; channels: IptvChannel[]; nowNext: Record<string, IptvNowNext>; onClose: () => void;
}) {
  const current = epgMap()[channel.id];
  const [filter, setFilter] = useState("");
  const list = useMemo(() => {
    const target = key(channel.name);
    const words = new Set(target.split(" ").filter(Boolean));
    const f = filter.trim().toLowerCase();
    return channels
      .filter(c => c.id !== channel.id && (nowNext[c.id]?.now || c.tvgId))
      .filter(c => !f || c.name.toLowerCase().includes(f))
      .map(c => {
        const k = key(c.name);
        const shared = k.split(" ").filter(w => words.has(w)).length;
        const score = (k === target ? 100 : k.startsWith(target) || target.startsWith(k) ? 70 : shared * 20) + (nowNext[c.id]?.now ? 5 : 0);
        return { c, score };
      })
      .sort((a, b) => b.score - a.score || a.c.name.localeCompare(b.c.name))
      .slice(0, 80)
      .map(x => x.c);
  }, [channel, channels, nowNext, filter]);
  return (
    <Frame title={`Programmdaten für „${cleanChannelName(channel.name)}“`} hint="Wähle einen Sender, dessen Programm zu diesem Sender passt. Die Zuordnung wird gespeichert." onClose={onClose}>
      <input type="search" placeholder="Sender suchen" value={filter} onChange={(e) => setFilter(e.target.value)} className="fiptv-live-dialog-search" />
      {current && <button type="button" role="menuitem" onClick={() => { setEpgSource(channel.id, null); onClose(); }}><X size={17} />Zuordnung entfernen</button>}
      {list.length === 0 && <p className="fiptv-live-dialog-hint">Keine passenden Sender mit Programmdaten gefunden.</p>}
      {list.map(c => (
        <button key={c.id} type="button" role="menuitem" onClick={() => { setEpgSource(channel.id, c.id); onClose(); }}>
          {c.id === current ? <Check size={17} /> : <span style={{ width: 17 }} />}
          <span className="fiptv-live-dialog-text"><span>{c.name}</span><small>{nowNext[c.id]?.now?.title ? `Jetzt: ${nowNext[c.id]!.now!.title}` : "Programmdaten vorhanden"}</small></span>
        </button>
      ))}
    </Frame>
  );
}

/** Eigene Gruppen: Sender aufnehmen/entfernen, neue Gruppe, Gruppe löschen. */
export function CustomGroupsDialog({ channel, onClose }: { channel: IptvChannel; onClose: () => void }) {
  const [mode, setMode] = useState<"list" | "new" | "delete">("list");
  const [name, setName] = useState("");
  const [version, setVersion] = useState(0);
  const groups = useMemo(() => customGroups(), [version]); // eslint-disable-line react-hooks/exhaustive-deps
  const refresh = () => setVersion(v => v + 1);
  const channelName = cleanChannelName(channel.name);
  if (mode === "new") {
    const taken = new Set(groups.map(g => g.name.toLowerCase()));
    const create = (n: string) => { if (!n.trim()) return; createCustomGroup(n, channel.id); setName(""); setMode("list"); refresh(); };
    return (
      <Frame title="Neue Gruppe" hint={`„${channelName}“ kommt gleich hinein.`} onClose={onClose}>
        {PRESET_GROUP_NAMES.filter(n => !taken.has(n.toLowerCase())).map(n => (
          <button key={n} type="button" role="menuitem" onClick={() => create(n)}><Plus size={17} />{n}</button>
        ))}
        <form onSubmit={(e) => { e.preventDefault(); create(name); }} className="fiptv-live-dialog-form">
          <input value={name} maxLength={30} placeholder="Eigener Name" onChange={(e) => setName(e.target.value)} className="fiptv-live-dialog-search" />
          <button type="submit" role="menuitem"><Check size={17} />Anlegen</button>
        </form>
        <button type="button" role="menuitem" onClick={() => setMode("list")}>Zurück</button>
      </Frame>
    );
  }
  if (mode === "delete") {
    return (
      <Frame title="Gruppe löschen" hint="Die Sender selbst bleiben erhalten, nur die Gruppe verschwindet." onClose={onClose}>
        {groups.map(g => (
          <button key={g.id} type="button" role="menuitem" onClick={() => { deleteCustomGroup(g.id); setMode("list"); refresh(); }}>
            <Trash2 size={17} />„{g.name}“ löschen ({g.channelIds.length} Sender)
          </button>
        ))}
        <button type="button" role="menuitem" onClick={() => setMode("list")}>Zurück</button>
      </Frame>
    );
  }
  return (
    <Frame title="Eigene Gruppen" hint={`„${channelName}“ in Gruppen aufnehmen oder entfernen.`} onClose={onClose}>
      {groups.map(g => (
        <button key={g.id} type="button" role="menuitem" onClick={() => { toggleInCustomGroup(g.id, channel.id); refresh(); }}>
          {g.channelIds.includes(channel.id) ? <Check size={17} /> : <span style={{ width: 17 }} />}
          <span className="fiptv-live-dialog-text"><span>{g.name}</span><small>{g.channelIds.length} Sender</small></span>
        </button>
      ))}
      <button type="button" role="menuitem" onClick={() => setMode("new")}><Plus size={17} />Neue Gruppe anlegen</button>
      {groups.length > 0 && <button type="button" role="menuitem" onClick={() => setMode("delete")}><Trash2 size={17} />Gruppe löschen …</button>}
      <button type="button" role="menuitem" onClick={onClose}>Fertig</button>
    </Frame>
  );
}

/** Zeitversatz der Programmdaten für einen Sender. */
export function EpgOffsetDialog({ channel, onClose }: { channel: IptvChannel; onClose: () => void }) {
  const o = epgOffsets();
  const current = o.channels[channel.id];
  const pick = (m: number | null) => { setChannelEpgOffset(channel.id, m); onClose(); };
  return (
    <Frame title={`Programmzeit für „${cleanChannelName(channel.name)}“`} hint="Laufen Sendungen früher oder später als im Programm angegeben? Dann hier verschieben. Plus = Sendungen beginnen später." onClose={onClose}>
      <button type="button" role="menuitem" onClick={() => pick(null)}>
        {current === undefined ? <Check size={17} /> : <span style={{ width: 17 }} />}
        <span className="fiptv-live-dialog-text"><span>Wie alle Sender ({epgOffsetLabel(o.global)})</span><small>Einstellung unter Wiedergabe → Zeitversatz Programm</small></span>
      </button>
      {EPG_OFFSET_CHOICES.map((m) => (
        <button key={m} type="button" role="menuitem" onClick={() => pick(m)}>
          {current === m ? <Check size={17} /> : <span style={{ width: 17 }} />}{epgOffsetLabel(m)}
        </button>
      ))}
    </Frame>
  );
}

/** Uhrzeit wählen, ab der zeitversetzt (Sendung verpasst) gespielt wird. */
export function JumpDialog({ channel, onPick, onClose }: { channel: IptvChannel; onPick: (t: number) => void; onClose: () => void }) {
  const times = useMemo(() => {
    const now = Date.now();
    const quarter = 15 * 60_000, hour = 3600_000;
    const base = now - (now % quarter);
    const maxBack = Math.min(Math.max(channel.catchupDays ?? 1, 1) * 24, 48) * hour;
    const out: number[] = [];
    for (let i = 1; i <= 12; i++) out.push(base - i * quarter);
    for (let t = base - (base % hour) - 3 * hour; now - t <= maxBack; t -= hour) out.push(t);
    return [...new Set(out)].filter((t) => now - t <= maxBack);
  }, [channel.catchupDays]);
  const day = (t: number) => {
    const d = new Date(t), today = new Date(), y = new Date(Date.now() - 86_400_000);
    if (d.toDateString() === today.toDateString()) return "Heute";
    if (d.toDateString() === y.toDateString()) return "Gestern";
    return d.toLocaleDateString("de-DE", { weekday: "long" });
  };
  return (
    <Frame title={`„${cleanChannelName(channel.name)}“ – zurückspringen`} hint="Ab dieser Uhrzeit zeitversetzt weiterschauen. Am Ende jeder Sendung geht es automatisch mit der nächsten weiter, bis du wieder live bist." onClose={onClose}>
      {times.map((t) => {
        const ago = Math.round((Date.now() - t) / 60_000);
        const agoText = ago < 60 ? `vor ${ago} Min.` : `vor ${Math.floor(ago / 60)} Std.${ago % 60 >= 15 ? ` ${ago % 60} Min.` : ""}`;
        return (
          <button key={t} type="button" role="menuitem" onClick={() => onPick(t)}>
            <span className="fiptv-live-dialog-text"><span>{day(t)}, {new Date(t).toLocaleTimeString("de-DE", { hour: "2-digit", minute: "2-digit" })} Uhr</span><small>{agoText}</small></span>
          </button>
        );
      })}
    </Frame>
  );
}
