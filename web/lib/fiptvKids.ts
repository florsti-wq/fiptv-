// fIPTV: Tageslimit für Kinderprofile – gleiche Einstellung wie auf dem Fire TV
// (Feld fiptvKidsLimit im Cloud-Profil, Minuten pro Tag). Verbrauch zählt jedes Gerät selbst.
export const KIDS_BONUS_MINUTES = 30;

const today = () => new Date().toISOString().slice(0, 10);
const key = (profileId: string, kind: "u" | "b") => `fiptv.kids.${kind}.${profileId}.${today()}`;

function read(k: string) {
  try { return Number(localStorage.getItem(k)) || 0; } catch { return 0; }
}
function write(k: string, value: number) {
  try {
    localStorage.setItem(k, String(value));
    // alte Tage aufräumen
    for (let i = localStorage.length - 1; i >= 0; i -= 1) {
      const name = localStorage.key(i);
      if (name?.startsWith("fiptv.kids.") && !name.endsWith(today())) localStorage.removeItem(name);
    }
  } catch { /* egal */ }
}

export const kidsUsedSeconds = (profileId: string) => read(key(profileId, "u"));
export const addKidsSeconds = (profileId: string, seconds: number) => write(key(profileId, "u"), kidsUsedSeconds(profileId) + seconds);
export const grantKidsBonus = (profileId: string) => write(key(profileId, "b"), read(key(profileId, "b")) + KIDS_BONUS_MINUTES * 60);

/** verbleibende Sekunden; null = kein Limit */
export function kidsRemainingSeconds(profileId: string, limitMinutes: number | undefined) {
  if (!limitMinutes || limitMinutes <= 0) return null;
  return limitMinutes * 60 + read(key(profileId, "b")) - kidsUsedSeconds(profileId);
}
