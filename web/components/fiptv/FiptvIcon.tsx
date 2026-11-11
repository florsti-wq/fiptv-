// fIPTV: einheitliche Linien-Icons (eigene Zeichnungen, gleiche Formen wie in der Fire-TV-App).
import type { CSSProperties } from "react";

const PATHS = {
 "Search": "<circle cx=\"11\" cy=\"11\" r=\"7\"/><path d=\"M20 20l-4-4\"/>",
 "Home": "<path d=\"M3 11l9-7 9 7\"/><path d=\"M5 10v10h14V10\"/><path d=\"M10 20v-6h4v6\"/>",
 "Bookmark": "<path d=\"M6 3h12v18l-6-4-6 4z\"/>",
 "LiveTv": "<rect x=\"3\" y=\"7\" width=\"18\" height=\"13\" rx=\"2\"/><path d=\"M8 2.5l4 4.5 4-4.5\"/>",
 "Film": "<rect x=\"3\" y=\"4\" width=\"18\" height=\"16\" rx=\"2\"/><path d=\"M7 4v16M17 4v16M3 9h4M3 15h4M17 9h4M17 15h4\"/>",
 "Trophy": "<path d=\"M8 4h8v5a4 4 0 0 1-8 0z\"/><path d=\"M8 6H5a3 3 0 0 0 3 4M16 6h3a3 3 0 0 1-3 4\"/><path d=\"M12 13v4M8 21h8M9 17h6\"/>",
 "Mediathek": "<path d=\"M3 9l9-5 9 5\"/><path d=\"M5 9v9M9.5 9v9M14.5 9v9M19 9v9\"/><path d=\"M3 20h18\"/>",
 "VideoScreen": "<rect x=\"3\" y=\"4\" width=\"18\" height=\"13\" rx=\"2\"/><path d=\"M10 8.5v5l4-2.5z\"/><path d=\"M8 21h8M12 17v4\"/>",
 "Settings": "<path d=\"M4 6h10M18 6h2M4 12h4M12 12h8M4 18h12\"/><circle cx=\"16\" cy=\"6\" r=\"2\"/><circle cx=\"10\" cy=\"12\" r=\"2\"/><circle cx=\"18\" cy=\"18\" r=\"2\"/>",
 "Profile": "<circle cx=\"12\" cy=\"8\" r=\"4\"/><path d=\"M4 21a8 8 0 0 1 16 0\"/>",
 "Football": "<circle cx=\"12\" cy=\"12\" r=\"9\"/><path d=\"M12 7.5l3.8 2.8-1.4 4.4H9.6l-1.4-4.4z\"/><path d=\"M12 7.5V3.2M15.8 10.3l4-1.2M14.4 14.7l2.4 3.4M9.6 14.7l-2.4 3.4M8.2 10.3l-4-1.2\"/>",
 "Formula1": "<path d=\"M5 21V4\"/><path d=\"M5 4h14v10H5\"/><path d=\"M9.7 4v10M14.3 4v10M5 9h14\"/>",
 "Tennis": "<circle cx=\"12\" cy=\"12\" r=\"9\"/><path d=\"M5.6 5.6a9 9 0 0 1 0 12.8M18.4 5.6a9 9 0 0 0 0 12.8\"/>",
 "Wrestling": "<path d=\"M2 10h20v4H2z\"/><circle cx=\"12\" cy=\"12\" r=\"4.5\"/><path d=\"M10.5 12h3M12 10.5v3\"/>",
 "Overview": "<rect x=\"3\" y=\"3\" width=\"7\" height=\"7\" rx=\"1.5\"/><rect x=\"14\" y=\"3\" width=\"7\" height=\"7\" rx=\"1.5\"/><rect x=\"3\" y=\"14\" width=\"7\" height=\"7\" rx=\"1.5\"/><rect x=\"14\" y=\"14\" width=\"7\" height=\"7\" rx=\"1.5\"/>",
 "Star": "<path d=\"M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1L3.2 9.5l6.1-.9z\"/>",
 "OnTv": "<circle cx=\"12\" cy=\"12\" r=\"2\"/><path d=\"M8.5 8.5a5 5 0 0 0 0 7M15.5 8.5a5 5 0 0 1 0 7M5.6 5.6a9 9 0 0 0 0 12.8M18.4 5.6a9 9 0 0 1 0 12.8\"/>",
 "Bell": "<path d=\"M6 16v-5a6 6 0 0 1 12 0v5l2 2H4z\"/><path d=\"M10 21h4\"/>",
 "Resume": "<path d=\"M3 12a9 9 0 1 0 3-6.7\"/><path d=\"M3 4v4h4\"/><path d=\"M12 7v5l3 2\"/>",
 "NextEpisode": "<path d=\"M5 5l10 7-10 7z\"/><path d=\"M19 5v14\"/>",
 "PlayCircle": "<circle cx=\"12\" cy=\"12\" r=\"9\"/><path d=\"M10 8.5v7l6-3.5z\"/>",
 "Goal": "<path d=\"M3 19V7h18v12\"/><path d=\"M3 11h18M3 15h18M8 7v12M12 7v12M16 7v12\"/>",
 "Live": "<circle cx=\"12\" cy=\"12\" r=\"8\"/><circle cx=\"12\" cy=\"12\" r=\"3.5\" fill=\"currentColor\"/>",
 "Kids": "<path d=\"M12 3a6 6 0 0 1 6 6c0 4-3 7-6 7s-6-3-6-7a6 6 0 0 1 6-6z\"/><path d=\"M11 16h2l-1 2z\"/><path d=\"M12 18c0 2-2 1.5-2 3.5\"/>",
 "Stats": "<path d=\"M4 20h16\"/><path d=\"M7 16v-5M12 16V6M17 16V9\"/>",
 "Table": "<path d=\"M9 6h11M9 12h11M9 18h11\"/><path d=\"M4 6h1M4 12h1M4 18h1\"/>",
 "Calendar": "<rect x=\"3\" y=\"5\" width=\"18\" height=\"16\" rx=\"2\"/><path d=\"M3 10h18M8 3v4M16 3v4\"/>"
} as const;

export type FiptvIconName = keyof typeof PATHS;
type Props = { size?: number; strokeWidth?: number; className?: string; style?: CSSProperties; title?: string };

export function FiptvIcon({ name, size = 22, strokeWidth = 1.8, className, style, title }: Props & { name: FiptvIconName }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={strokeWidth}
      strokeLinecap="round" strokeLinejoin="round" className={className} style={{ flex: "0 0 auto", ...style }}
      aria-hidden={title ? undefined : true} role={title ? "img" : undefined} aria-label={title}
      // feste, eigene Zeichnungen (keine Nutzerdaten)
      dangerouslySetInnerHTML={{ __html: PATHS[name] }} />
  );
}

/** Für Stellen, die eine Icon-Komponente erwarten (z. B. Navigation) */
export const icon = (name: FiptvIconName) => {
  const C = (p: Props) => <FiptvIcon name={name} {...p} />;
  C.displayName = `FiptvIcon(${name})`;
  return C;
};
