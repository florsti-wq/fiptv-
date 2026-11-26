"use client";

import { useEffect, useState } from "react";
import { animationsEnabled, installEffects, playEffect, soundsEnabled } from "@/lib/fiptvEffects";
import "./fiptvEffects.css";

const SHOWN_KEY = "fiptv.introShown";

/** fIPTV: Startanimation (Logo) beim Öffnen der App; jede Taste oder ein Klick überspringt sie. */
export function FiptvIntro() {
  const [phase, setPhase] = useState<"off" | "in" | "out">("off");

  useEffect(() => {
    installEffects();
    let shown = false;
    try { shown = sessionStorage.getItem(SHOWN_KEY) === "1"; sessionStorage.setItem(SHOWN_KEY, "1"); } catch { /* ignore */ }
    if (shown) return undefined;
    if (!animationsEnabled()) {
      if (soundsEnabled()) playEffect("start");
      return undefined;
    }
    setPhase("in");
    const sound = window.setTimeout(() => playEffect("start"), 120);
    const out = window.setTimeout(() => setPhase("out"), 1600);
    const done = window.setTimeout(() => setPhase("off"), 1950);
    return () => { window.clearTimeout(sound); window.clearTimeout(out); window.clearTimeout(done); };
  }, []);

  useEffect(() => {
    if (phase !== "in") return undefined;
    const skip = (e: Event) => {
      e.preventDefault();
      e.stopPropagation();
      setPhase("out");
      window.setTimeout(() => setPhase("off"), 180);
    };
    window.addEventListener("keydown", skip, { capture: true });
    return () => window.removeEventListener("keydown", skip, { capture: true });
  }, [phase]);

  if (phase === "off") return null;
  return (
    <div className={`fiptv-intro ${phase === "out" ? "is-out" : ""}`} aria-hidden="true"
      onClick={() => { setPhase("out"); window.setTimeout(() => setPhase("off"), 180); }}>
      <div className="fiptv-intro-logo">
        {"fIPTV".split("").map((c, i) => (
          <span key={i} style={{ animationDelay: `${60 + i * 90}ms` }}>{c}</span>
        ))}
      </div>
      <div className="fiptv-intro-bar" />
    </div>
  );
}
