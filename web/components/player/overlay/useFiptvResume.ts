// fIPTV: Fortschritt von Anbieter-Filmen/-Folgen und Mediathek-Sendungen für „Weiterschauen“
// speichern und beim Schließen mit den anderen Geräten abgleichen.
import { useEffect, type RefObject } from "react";
import { authClient } from "@/lib/store";
import { syncFiptvResume } from "@/lib/cloud";
import { reportResume } from "@/lib/fiptvResume";
import type { StreamSource } from "@/lib/types";

export function useFiptvResume({ activeProfileId, stream, videoRef }: { activeProfileId: string | null; stream: StreamSource; videoRef: RefObject<HTMLVideoElement | null> }) {
  const info = stream.fiptvResume;
  useEffect(() => {
    const video = videoRef.current;
    if (!video || !info) return undefined;
    const profileId = activeProfileId ?? "";
    let lastReport = 0;
    let lastSync = 0;
    let played = false;
    const offset = () => stream.playbackSession?.startOffset ?? 0;
    const report = (closing: boolean) => {
      const duration = video.duration;
      if (!Number.isFinite(duration) || duration <= 0 || !Number.isFinite(video.currentTime)) return;
      if (video.currentTime > 1) played = true;
      if (!played) return;
      reportResume(profileId, info.url, (video.currentTime + offset()) * 1000, (duration + offset()) * 1000, closing);
    };
    const sync = (force: boolean) => {
      const now = Date.now();
      if (!played || (!force && now - lastSync < 5 * 60_000)) return;
      lastSync = now;
      void syncFiptvResume(authClient, profileId).catch(() => undefined);
    };
    const onTime = () => {
      const now = Date.now();
      if (now - lastReport < 10_000) return;
      lastReport = now;
      report(false);
      sync(false);
    };
    const onPause = () => { report(true); sync(true); };
    const onEnded = () => { report(true); sync(true); };
    const onHide = () => { if (document.visibilityState === "hidden") { report(true); sync(true); } };
    video.addEventListener("timeupdate", onTime);
    video.addEventListener("pause", onPause);
    const onSeeked = () => report(false);
    video.addEventListener("seeked", onSeeked);
    video.addEventListener("ended", onEnded);
    document.addEventListener("visibilitychange", onHide);
    window.addEventListener("pagehide", onPause);
    return () => {
      report(true);
      sync(true);
      video.removeEventListener("timeupdate", onTime);
      video.removeEventListener("pause", onPause);
      video.removeEventListener("seeked", onSeeked);
      video.removeEventListener("ended", onEnded);
      document.removeEventListener("visibilitychange", onHide);
      window.removeEventListener("pagehide", onPause);
    };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [videoRef, info?.url, activeProfileId, stream.playbackSession?.startOffset]);
}
