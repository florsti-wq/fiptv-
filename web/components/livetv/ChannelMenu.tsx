"use client";
import { useTranslation } from "@/lib/i18n";
import { ArrowDown, ArrowUp, Bug, CalendarSearch, Circle, Clock, History, EyeOff, FolderOpen, FolderPlus, Play, Star, X } from "lucide-react";
import { useEffect, useRef } from "react";
import type { IptvChannel } from "@/lib/types";

export type ChannelMenuState = { channel: IptvChannel; anchor: HTMLElement | null };

/**
 * Channel context menu (long-press / right-click / menu key), like Android's
 * ChannelContextMenu: favourite, reorder favourites, hide the channel's group.
 */
export function ChannelMenu({ state, groupName, favorite, canMoveUp, canMoveDown, onPlay, onToggleFavorite, onMove, onHideGroup, onClose, onRecord, programmeEnd, onEpgMap, onGroups, onEpgOffset, onJump, onReport, customMove }: {
  state: ChannelMenuState;
  /** The channel's category as shown on the Live TV page (the user's own name when set). */
  groupName?: string;
  favorite: boolean;
  canMoveUp: boolean;
  canMoveDown: boolean;
  onPlay: () => void;
  onToggleFavorite: () => void;
  onMove: (direction: -1 | 1) => void;
  onHideGroup: () => void;
  onClose: () => void;
  /** fIPTV (Windows): aufnehmen – Minuten oder null = bis Sendungsende */
  onRecord?: (minutes: number | null) => void;
  programmeEnd?: number;
  onEpgMap?: () => void;
  onGroups?: () => void;
  onEpgOffset?: () => void;
  onJump?: () => void;
  onReport?: () => void;
  customMove?: { name: string; canUp: boolean; canDown: boolean; onMove: (d: -1 | 1) => void; onRemove: () => void };
}) {
  const translateUi = useTranslation();
  const dialog = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const element = dialog.current;
    if (!element) return undefined;
    if (!element.open) element.showModal();
    element.querySelector<HTMLButtonElement>("button:not(:disabled)")?.focus();
    const anchor = state.anchor;
    return () => { if (anchor?.isConnected) anchor.focus({ preventScroll: true }); };
  }, [state]);
  const run = (action: () => void) => () => { action(); onClose(); };
  const group = groupName || state.channel.group?.trim() || translateUi("Ungrouped");
  return (
    <dialog ref={dialog} className="livetv-channel-menu" aria-label={translateUi("Channel options for {value0}", { value0: state.channel.name })}
      onCancel={(event) => { event.preventDefault(); onClose(); }}
      onClick={(event) => { if (event.target === event.currentTarget) onClose(); }}>
      <header><strong>{state.channel.name}</strong><button type="button" onClick={onClose} aria-label={translateUi("Close")}><X size={18} /></button></header>
      <div role="menu" aria-label={translateUi("Channel options")}>
        <button type="button" role="menuitem" onClick={run(onPlay)}><Play size={17} fill="currentColor" />{translateUi("Watch")}</button>
        <button type="button" role="menuitem" onClick={run(onToggleFavorite)}><Star size={17} fill={favorite ? "currentColor" : "none"} />{favorite ? translateUi("Remove favorite") : translateUi("Add favorite")}</button>
        {favorite && <button type="button" role="menuitem" disabled={!canMoveUp} onClick={run(() => onMove(-1))}><ArrowUp size={17} />{translateUi("Move favorite up")}</button>}
        {favorite && <button type="button" role="menuitem" disabled={!canMoveDown} onClick={run(() => onMove(1))}><ArrowDown size={17} />{translateUi("Move favorite down")}</button>}
        <button type="button" role="menuitem" onClick={run(onHideGroup)}><EyeOff size={17} />{translateUi("Hide category {value0}", { value0: group })}</button>
        {customMove && <button type="button" role="menuitem" disabled={!customMove.canUp} onClick={run(() => customMove.onMove(-1))}><ArrowUp size={17} />In „{customMove.name}“ nach oben</button>}
        {customMove && <button type="button" role="menuitem" disabled={!customMove.canDown} onClick={run(() => customMove.onMove(1))}><ArrowDown size={17} />In „{customMove.name}“ nach unten</button>}
        {customMove && <button type="button" role="menuitem" onClick={run(customMove.onRemove)}><X size={17} />Aus „{customMove.name}“ entfernen</button>}
        {onReport && <button type="button" role="menuitem" onClick={run(onReport)}><Bug size={17} />Stream-Problem melden</button>}
        {onJump && <button type="button" role="menuitem" onClick={run(onJump)}><History size={17} />Zu Uhrzeit zurückspringen …</button>}
        {onEpgOffset && <button type="button" role="menuitem" onClick={run(onEpgOffset)}><Clock size={17} />Programmzeit verschieben …</button>}
        {onGroups && <button type="button" role="menuitem" onClick={run(onGroups)}><FolderPlus size={17} />Eigene Gruppen …</button>}
        {onEpgMap && <button type="button" role="menuitem" onClick={run(onEpgMap)}><CalendarSearch size={17} />Programmdaten zuordnen …</button>}
        {onRecord && programmeEnd && programmeEnd > Date.now() && <button type="button" role="menuitem" onClick={run(() => onRecord(null))}><Circle size={17} fill="#e5484d" color="#e5484d" />Aufnehmen bis Sendungsende ({new Date(programmeEnd + 5 * 60_000).toLocaleTimeString("de-DE", { hour: "2-digit", minute: "2-digit" })})</button>}
        {onRecord && <button type="button" role="menuitem" onClick={run(() => onRecord(60))}><Circle size={17} color="#e5484d" />1 Stunde aufnehmen</button>}
        {onRecord && <button type="button" role="menuitem" onClick={run(() => onRecord(120))}><Circle size={17} color="#e5484d" />2 Stunden aufnehmen</button>}
        {onRecord && <button type="button" role="menuitem" onClick={run(() => { void (window as unknown as { fiptvDesktop?: { openRecordingsFolder?: () => Promise<unknown> } }).fiptvDesktop?.openRecordingsFolder?.(); })}><FolderOpen size={17} />Aufnahmen-Ordner öffnen</button>}
      </div>
    </dialog>
  );
}
