#!/usr/bin/env python3
"""
Replaces the placeholder icons with Twemoji colour emoji.
Run from the project root:   pip install pillow   then   python tools/fetch_emoji.py
Needs internet. Twemoji graphics are CC-BY 4.0 (credit in README). To use another set, change BASE and
the codepoints; any PNG named like the entries below works, and you can also just drop your own 18x18 PNGs in.
"""
import io, sys, urllib.request
try:
    from PIL import Image
except ImportError:
    sys.exit("Please run: pip install pillow")

BASE = "https://raw.githubusercontent.com/jdecked/twemoji/main/assets/72x72/{}.png"
OUT = "src/main/resources/assets/bountyhunt/textures/font/icon/{}.png"
ICONS = {
    "check": "2705",    # white heavy check mark
    "cross": "274c",    # cross mark
    "swords": "2694",   # crossed swords
    "door": "1f6aa",    # door
    "skull": "2620",    # skull and crossbones
    "back": "2b05",     # left arrow
    "reroll": "1f504",  # counterclockwise arrows
    "flag": "1f6a9",    # triangular flag
    "crown": "1f451",   # crown
}
SIZE = 18  # drawn at 9 GUI units tall: exactly 1:1 at GUI scale 2, so it stays crisp

for name, code in ICONS.items():
    data = urllib.request.urlopen(BASE.format(code)).read()
    img = Image.open(io.BytesIO(data)).convert("RGBA").resize((SIZE, SIZE), Image.LANCZOS)
    img.save(OUT.format(name))
    print("ok", name, code)
print("Done. Rebuild the mod.")
