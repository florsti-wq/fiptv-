#!/usr/bin/env python3
"""fIPTV: baut aus den Sender-Listen von iptv-org/epg eine eigene channels.xml.

Aufruf: build_channels.py <epg-ordner> <ausgabe.xml> <site> [<site> ...]
Doppelte Sender (gleicher Name) werden nur einmal geholt – die erste Seite gewinnt.
Sender ohne xmltv_id bekommen eine eigene ID, damit sie im Guide landen; fIPTV ordnet
die Sender ohnehin auch über den Namen zu."""
import re
import sys
import unicodedata
import xml.etree.ElementTree as ET


def key(name: str) -> str:
    n = unicodedata.normalize("NFKD", name).encode("ascii", "ignore").decode().lower()
    n = re.sub(r"\b(hd|uhd|4k|fhd|sd|tv)\b", " ", n)
    return re.sub(r"[^a-z0-9]+", "", n)


def main() -> None:
    root_dir, out, sites = sys.argv[1], sys.argv[2], sys.argv[3:]
    seen_names, seen_ids, rows = set(), set(), []
    for site in sites:
        tree = ET.parse(f"{root_dir}/sites/{site}/{site}.channels.xml")
        tld = site.rsplit(".", 1)[-1]
        count = 0
        for ch in tree.getroot().iter("channel"):
            name = (ch.text or "").strip()
            k = key(name)
            if not name or not k or k in seen_names:
                continue
            # "DasErste.de@SD" → "DasErste.de" (so heißen die tvg-ids in den meisten Playlists)
            xid = (ch.get("xmltv_id") or "").strip().split("@")[0] or f"{re.sub(r'[^A-Za-z0-9]+', '', name)}.{tld}"
            if xid in seen_ids:
                continue
            seen_names.add(k)
            seen_ids.add(xid)
            ch.set("xmltv_id", xid)
            rows.append(ch)
            count += 1
        print(f"{site}: {count} Sender")
    root = ET.Element("channels")
    root.extend(rows)
    ET.indent(root, "  ")
    ET.ElementTree(root).write(out, encoding="UTF-8", xml_declaration=True)
    print(f"gesamt: {len(rows)} Sender")


if __name__ == "__main__":
    main()
