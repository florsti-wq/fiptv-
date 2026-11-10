// fIPTV: Hintergrundfarben pro Profil – gleiche Auswahl wie in der Android-App
// (FiptvBackgrounds.kt), abgeglichen über das Cloud-Profil (Feld fiptvBackground).
export const FIPTV_BACKGROUNDS: [id: string, name: string, color: string][] = [
  ["default", "Standard", "#08090a"],
  ["midnight", "Mitternachtsblau", "#0e1a33"],
  ["petrol", "Petrol", "#08262c"],
  ["forest", "Waldgrün", "#0e2218"],
  ["violet", "Violett", "#1d1233"],
  ["bordeaux", "Bordeaux", "#2a0e17"],
  ["brown", "Braun", "#24180f"],
  ["graphite", "Graphit", "#202327"]
];

export function fiptvBackgroundColor(id?: string | null) {
  return (FIPTV_BACKGROUNDS.find(([key]) => key === id) ?? FIPTV_BACKGROUNDS[0])[2];
}

/** etwas hellere Töne für Karten/Flächen auf dem gewählten Hintergrund */
export function lighten(hex: string, amount: number) {
  const n = parseInt(hex.slice(1), 16);
  const ch = (shift: number) => Math.min(255, Math.round(((n >> shift) & 255) + (255 - ((n >> shift) & 255)) * amount));
  return `#${[16, 8, 0].map((s) => ch(s).toString(16).padStart(2, "0")).join("")}`;
}
