"""fIPTV: animierte Fluent-Emoji (Microsoft, MIT) als kleine animierte WebP aufbereiten.

Aufruf: convert.py <microsoft-repo> <list.json> <ausgabe>
"""
import json, os, sys
from PIL import Image, ImageSequence

src, listfile, out = sys.argv[1:4]
size = 192
os.makedirs(out, exist_ok=True)
items = json.load(open(listfile))
manifest = []
for it in items:
    im = Image.open(os.path.join(src, it["file"]))
    frames, durations = [], []
    for i, fr in enumerate(ImageSequence.Iterator(im)):
        durations.append(fr.info.get("duration", 33) or 33)
        frames.append(fr.convert("RGBA").resize((size, size), Image.LANCZOS))
    # jede zweite Frame-Stufe weglassen, wenn sehr viele Frames (Dateigröße)
    if len(frames) > 60:
        frames = frames[::2]
        durations = [d * 2 for d in durations[::2]]
    dst = os.path.join(out, it["slug"] + ".webp")
    frames[0].save(dst, save_all=True, append_images=frames[1:], duration=durations, loop=0,
                   quality=72, method=6, lossless=False, allow_mixed=False)
    frames[0].save(os.path.join(out, it["slug"] + "_still.webp"), quality=85, method=6)
    kb = os.path.getsize(dst) // 1024
    print(f'{it["slug"]}: {len(frames)} frames, {kb} KB')
    manifest.append({"cat": it["cat"], "name": it["name"], "slug": it["slug"], "kb": kb})
json.dump(manifest, open(os.path.join(out, "manifest.json"), "w"), indent=1)
print("total KB", sum(m["kb"] for m in manifest))
