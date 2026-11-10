"use client";
// fIPTV: Im Kinderprofil die Abspielzeit zählen und bei erreichtem Tageslimit sperren.
import "./kidsGuard.css";

import { useEffect, useState } from "react";
import { PinDialog } from "@/components/profile/PinDialog";
import { addKidsSeconds, grantKidsBonus, kidsRemainingSeconds, KIDS_BONUS_MINUTES } from "@/lib/fiptvKids";
import type { Profile } from "@/lib/types";

const TICK = 15;

function pauseAll() {
  document.querySelectorAll("video").forEach((v) => { try { v.pause(); } catch { /* egal */ } });
}

export function KidsGuard({ profile, limitMinutes, onClose }: { profile: Profile | null; limitMinutes?: number; onClose: () => void }) {
  const profileId = profile?.id ?? "";
  const active = Boolean(profile?.isKidsProfile && limitMinutes && limitMinutes > 0);
  const [locked, setLocked] = useState(() => active && (kidsRemainingSeconds(profileId, limitMinutes) ?? 1) <= 0);
  const [askPin, setAskPin] = useState(false);

  useEffect(() => {
    if (!active) { setLocked(false); return; }
    const check = () => {
      const over = (kidsRemainingSeconds(profileId, limitMinutes) ?? 1) <= 0;
      setLocked(over);
      if (over) pauseAll();
    };
    check();
    const timer = window.setInterval(() => {
      const playing = Array.from(document.querySelectorAll("video")).some((v) => !v.paused && !v.ended);
      if (playing) addKidsSeconds(profileId, TICK);
      check();
    }, TICK * 1000);
    return () => window.clearInterval(timer);
  }, [active, profileId, limitMinutes]);

  useEffect(() => {
    if (!locked) return;
    pauseAll();
    // Weiterspielen verhindern, solange gesperrt
    const onPlay = (e: Event) => { (e.target as HTMLVideoElement).pause(); };
    document.addEventListener("play", onPlay, true);
    return () => document.removeEventListener("play", onPlay, true);
  }, [locked]);

  if (!locked || !profile) return null;
  return (
    <div className="fkids-lock" role="alertdialog" aria-label="Für heute ist Schluss">
      <div className="fkids-box">
        <div className="fkids-icon">⏰</div>
        <h2>Für heute ist Schluss!</h2>
        <p>Die Fernsehzeit für heute ist aufgebraucht. Morgen geht es weiter.</p>
        <div className="fkids-actions">
          <button type="button" className="fkids-btn primary" onClick={onClose} autoFocus>Zurück</button>
          {profile.parentPin
            ? <button type="button" className="fkids-btn" onClick={() => setAskPin(true)}>Eltern: +{KIDS_BONUS_MINUTES} Min.</button>
            : <p className="fkids-hint">Zum Verlängern im Profil einen Eltern-PIN festlegen.</p>}
        </div>
      </div>
      {askPin && (
        <PinDialog
          profile={profile}
          storedPin={profile.parentPin}
          label="Parent PIN"
          onClose={() => setAskPin(false)}
          onUnlock={() => { grantKidsBonus(profileId); setAskPin(false); setLocked(false); }}
        />
      )}
    </div>
  );
}
