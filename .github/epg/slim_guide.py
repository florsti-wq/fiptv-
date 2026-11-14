#!/usr/bin/env python3
"""fIPTV: Programmführer verschlanken, damit Fire TV (begrenzter Speicher) ihn sicher verarbeitet.
Entfernt Bilder, Mitwirkende, Bewertungen usw. und kürzt Beschreibungen auf 220 Zeichen.
Aufruf: slim_guide.py <guide.xml> <ausgabe.xml.gz>"""
import gzip
import re
import sys

src, out = sys.argv[1], sys.argv[2]
DROP = re.compile(r"<(icon|credits|rating|star-rating|video|audio|subtitles|review|image|url|country|keyword|length|premiere|new|previously-shown)\b[^>]*?(/>|>.*?</\1>)", re.S)
DESC = re.compile(r"(<desc\b[^>]*>)(.*?)(</desc>)", re.S)


def short(m):
    text = m.group(2).strip()
    if len(text) > 220:
        text = text[:217].rsplit(" ", 1)[0] + "…"
    return m.group(1) + text + m.group(3)


data = open(src, encoding="utf-8").read()
before = len(data)
data = DROP.sub("", data)
data = DESC.sub(short, data)
data = re.sub(r"\n\s*\n", "\n", data)
with gzip.open(out, "wt", encoding="utf-8", compresslevel=9) as f:
    f.write(data)
print(f"Guide: {before // 1024} KB → {len(data) // 1024} KB")
