"use client";
import { useEffect, useMemo, useRef, useState } from "react";
import { Check, Plus, Trash2, X } from "lucide-react";
import type { IptvChannel, IptvNowNext } from "@/lib/types";
import { cleanChannelName } from "@/lib/fiptvChannelCleanup";
import {
  createCustomGroup, customGroups, deleteCustomGroup, epgMap, PRESET_GROUP_NAMES, setEpgSource, toggleInCustomGroup,
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
