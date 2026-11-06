#!/usr/bin/env python3
"""
Generates tougher-looking monster textures for Medium / Hard / Legendary bounties from the vanilla textures.

  pip install pillow
  python tools/gen_monster_textures.py [path/to/1.20.1.jar] [--preview sheet.png]

The vanilla textures are read from your own Minecraft jar (nothing from Mojang is shipped in this project) and
written to src/main/resources/assets/bountyhunt/textures/entity/<mob>_<tier>.png (tier 1 = Medium, 2 = Hard,
3 = Legendary). The mod swaps them in automatically; a mob without a generated file keeps its vanilla texture.
The layout is identical to the vanilla file, so the models line up. Tune the look in TIERS below and re-run.
If you publish the mod, ship this script instead of the generated files (they are derived from Mojang assets).
"""
import glob, io, os, random, sys, zipfile
from PIL import Image

OUT = "src/main/resources/assets/bountyhunt/textures/entity/{}_{}.png"

# registry name -> (path inside the jar, front-of-head region x0,y0,x1,y1 used for the glowing face)
MOBS = {
    "zombie":      ("assets/minecraft/textures/entity/zombie/zombie.png",       (8, 8, 16, 16)),
    "husk":        ("assets/minecraft/textures/entity/zombie/husk.png",         (8, 8, 16, 16)),
    "skeleton":    ("assets/minecraft/textures/entity/skeleton/skeleton.png",   (8, 8, 16, 16)),
    "stray":       ("assets/minecraft/textures/entity/skeleton/stray.png",      (8, 8, 16, 16)),
    "spider":      ("assets/minecraft/textures/entity/spider/spider.png",       (40, 12, 48, 20)),
    "cave_spider": ("assets/minecraft/textures/entity/spider/cave_spider.png",  (40, 12, 48, 20)),
    "creeper":     ("assets/minecraft/textures/entity/creeper/creeper.png",     (8, 8, 16, 16)),
    "witch":       ("assets/minecraft/textures/entity/witch.png",               (8, 8, 16, 18)),
    "enderman":    ("assets/minecraft/textures/entity/enderman/enderman.png",   (8, 8, 16, 16)),
}

# sat/contrast/dark: colour grade. tint+amt: colour wash (scaled by brightness so shading survives).
# veins: glowing cracks. scars: slashes. gilt: share of bright pixels turned to the tint colour. grain: roughness.
TIERS = {
    1: dict(sat=0.85, contrast=1.10, dark=0.92, tint=(130, 60, 190), amt=0.22, vein=(190, 110, 255), veins=5,  scars=0, glow=(200, 120, 255), gilt=0.00, grain=4),
    2: dict(sat=0.80, contrast=1.20, dark=0.82, tint=(190, 35, 30),  amt=0.30, vein=(255, 70, 40),   veins=10, scars=2, glow=(255, 60, 40),   gilt=0.00, grain=8),
    3: dict(sat=0.60, contrast=1.30, dark=0.70, tint=(255, 190, 50), amt=0.20, vein=(255, 215, 80),  veins=16, scars=3, glow=(255, 235, 120), gilt=0.35, grain=10),
}


def find_jar(arg):
    cands = [arg] if arg else []
    home = os.path.expanduser("~")
    appdata = os.environ.get("APPDATA", "")
    cands += [os.path.join(appdata, ".minecraft", "versions", "1.20.1", "1.20.1.jar"),
              os.path.join(home, ".minecraft", "versions", "1.20.1", "1.20.1.jar"),
              os.path.join(home, "Library", "Application Support", "minecraft", "versions", "1.20.1", "1.20.1.jar")]
    cands += glob.glob(os.path.join(home, ".gradle", "caches", "fabric-loom", "**", "*1.20.1*.jar"), recursive=True)
    for c in cands:
        if c and os.path.isfile(c):
            try:
                with zipfile.ZipFile(c) as z:
                    if MOBS["zombie"][0] in z.namelist():
                        return c
            except zipfile.BadZipFile:
                pass
    sys.exit("Could not find the Minecraft 1.20.1 jar. Run it once in the launcher, or pass the path to 1.20.1.jar.")


def clamp(v):
    return max(0, min(255, int(v)))


def blend(a, b, k):
    return tuple(clamp(a[i] * (1 - k) + b[i] * k) for i in range(3))


def stylize(src, tier, face, seed):
    cfg = TIERS[tier]
    rnd = random.Random(seed)
    img = src.convert("RGBA")
    W, H = img.size
    px = img.load()
    opaque = [(x, y) for y in range(H) for x in range(W) if px[x, y][3] > 16]
    fx0, fy0, fx1, fy1 = face
    in_face = lambda x, y: fx0 <= x < fx1 and fy0 <= y < fy1

    def put(x, y, c, k=1.0):
        if 0 <= x < W and 0 <= y < H and px[x, y][3] > 16:
            px[x, y] = blend(px[x, y], c, k) + (px[x, y][3],)

    # 1. grade + tint + grain
    for (x, y) in opaque:
        r, g, b, a = px[x, y]
        lum = 0.3 * r + 0.59 * g + 0.11 * b
        c = [lum + (v - lum) * cfg["sat"] for v in (r, g, b)]
        c = [(v - 128) * cfg["contrast"] + 128 for v in c]
        c = [v * cfg["dark"] for v in c]
        shade = min(1.4, lum / 150.0 + 0.25)
        c = [c[i] * (1 - cfg["amt"]) + cfg["tint"][i] * cfg["amt"] * shade for i in range(3)]
        n = rnd.randint(-cfg["grain"], cfg["grain"])
        px[x, y] = tuple(clamp(v + n) for v in c) + (a,)

    # 2. gilded highlights (Legendary)
    if cfg["gilt"] > 0:
        for (x, y) in opaque:
            r, g, b, a = px[x, y]
            if 0.3 * r + 0.59 * g + 0.11 * b > 120 and rnd.random() < cfg["gilt"]:
                put(x, y, cfg["tint"], 0.45)

    body = [p for p in opaque if not in_face(*p)]

    # 3. scars: pale slash with a dark edge
    for _ in range(cfg["scars"]):
        x, y = rnd.choice(body)
        dx, dy = rnd.choice(((1, 1), (1, -1)))
        for i in range(rnd.randint(7, 10)):
            if not in_face(x + dx * i, y + dy * i):
                put(x + dx * i, y + dy * i, (215, 200, 190), 0.85)
                put(x + dx * i + 1, y + dy * i, (35, 15, 15), 0.6)

    # 4. glowing veins (random walks), kept off the face so it stays readable
    for _ in range(cfg["veins"]):
        x, y = rnd.choice(body)
        dx, dy = rnd.choice(((1, 0), (0, 1), (1, 1), (-1, 1)))
        for _ in range(rnd.randint(6, 14)):
            if not in_face(x, y):
                put(x, y, cfg["vein"], 0.95)
                for nx, ny in ((x + 1, y), (x, y + 1), (x - 1, y), (x, y - 1)):
                    if not in_face(nx, ny):
                        put(nx, ny, cfg["vein"], 0.28)
            if rnd.random() < 0.35:
                dx, dy = rnd.choice(((1, 0), (0, 1), (1, 1), (-1, 1), (-1, 0), (0, -1)))
            x += dx; y += dy

    # 5. glowing face: the darkest features (eyes, mouth, sockets) light up
    face_px = [(x, y) for (x, y) in opaque if in_face(x, y)]
    if face_px:
        lums = sorted(0.3 * px[p][0] + 0.59 * px[p][1] + 0.11 * px[p][2] for p in face_px)
        thr = lums[max(0, int(len(lums) * 0.20) - 1)]
        for p in face_px:
            if 0.3 * px[p][0] + 0.59 * px[p][1] + 0.11 * px[p][2] <= thr:
                put(p[0], p[1], cfg["glow"], 0.9)
    return img


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    preview = None
    if "--preview" in sys.argv:
        preview = sys.argv[sys.argv.index("--preview") + 1]
        args = [a for a in args if a != preview]
    jar = find_jar(args[0] if args else None)
    print("Using", jar)
    os.makedirs(os.path.dirname(OUT.format("x", 1)), exist_ok=True)
    sheet = []
    with zipfile.ZipFile(jar) as z:
        for name, (path, face) in MOBS.items():
            src = Image.open(io.BytesIO(z.read(path)))
            row = [src.convert("RGBA")]
            for tier in (1, 2, 3):
                out = stylize(src, tier, face, seed=hash(name) % 100000 + tier)
                out.save(OUT.format(name, tier))
                row.append(out)
            sheet.append(row)
            print("ok", name)
    if preview:
        S = 3
        w = max(r[0].width for r in sheet) * S
        h = max(r[0].height for r in sheet) * S
        img = Image.new("RGBA", (4 * (w + 8) + 8, len(sheet) * (h + 8) + 8), (50, 50, 54, 255))
        for ri, row in enumerate(sheet):
            for ci, im in enumerate(row):
                img.alpha_composite(im.resize((im.width * S, im.height * S), Image.NEAREST), (8 + ci * (w + 8), 8 + ri * (h + 8)))
        img.save(preview)
        print("Preview written to", preview, "(columns: vanilla, Medium, Hard, Legendary)")
    print("Done. Rebuild the mod.")


if __name__ == "__main__":
    main()
