#!/usr/bin/env python3
"""fIPTV: Gratis-Sender aus iptv-org (öffentlich und von den Sendern selbst frei angebotene Streams).

Aufruf: build_free.py <ausgabe.m3u> <land.m3u> [<land.m3u> ...]   (Dateiname = Ländercode, z. B. de.m3u)
- Gruppe = Land (z. B. "Deutschland"), damit die Sender im TV-Bereich sortiert sind.
- Werbe-Plattformen (Pluto TV, Samsung TV Plus, Rakuten, Plex, Wurl/Amagi-FAST …) werden ausgelassen:
  deren Nutzungsbedingungen erlauben nur die eigenen Apps.
- Doppelte Streams nur einmal."""
import os
import re
import sys

COUNTRY = {"de": "Deutschland", "lu": "Luxemburg", "at": "Österreich", "ch": "Schweiz", "be": "Belgien",
           "fr": "Frankreich", "nl": "Niederlande", "uk": "Großbritannien", "us": "USA", "it": "Italien", "es": "Spanien"}
BLOCK = re.compile(r"pluto\.tv|plutotv|samsung|jmp2\.uk|rakuten|plex\.tv|wurl|amagi|stirr|xumo|tubi|roku|"
                   r"lg-channels|lgchannels|vizio|redbox|distro\.tv|klowdtv|fast\.channel", re.I)


def main() -> None:
    out, files = sys.argv[1], sys.argv[2:]
    seen, lines, kept, dropped = set(), ["#EXTM3U x-tvg-url=\"https://raw.githubusercontent.com/florsti-wq/fiptv-/epg/guide.xml.gz\""], 0, 0
    for path in files:
        code = os.path.basename(path).split(".")[0]
        group = COUNTRY.get(code, code.upper())
        try:
            text = open(path, encoding="utf-8", errors="ignore").read().splitlines()
        except OSError:
            continue
        info, opts = None, []
        for line in text:
            line = line.strip()
            if line.startswith("#EXTINF"):
                info, opts = line, []
            elif line.startswith("#EXTVLCOPT") or line.startswith("#KODIPROP"):
                opts.append(line)
            elif line and not line.startswith("#") and info:
                url = line
                if url in seen or BLOCK.search(url) or BLOCK.search(info):
                    dropped += 1
                elif re.search(r"\[(Geo-blocked|Geo-Blocked)\]", info) and code not in ("de", "lu", "at", "ch"):
                    dropped += 1  # außerhalb DACH/LU sowieso nicht abspielbar
                else:
                    seen.add(url)
                    entry = re.sub(r'group-title="[^"]*"', f'group-title="Gratis · {group}"', info) \
                        if "group-title=" in info else info.replace("#EXTINF:-1", f'#EXTINF:-1 group-title="Gratis · {group}"', 1)
                    lines += [entry, *opts, url]
                    kept += 1
                info, opts = None, []
    open(out, "w", encoding="utf-8").write("\n".join(lines) + "\n")
    print(f"Gratis-Sender: {kept} übernommen, {dropped} ausgelassen")


if __name__ == "__main__":
    main()
