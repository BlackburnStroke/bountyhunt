#!/usr/bin/env python3
"""
Generates the Bounty Zombie model data, its four textures (Normal, Medium, Hard, Legendary) and its four loot tables.
Everything is painted from scratch (no Mojang assets). Edit the PARTS / palettes / loot below and re-run from the project root:
    pip install pillow
    python tools/gen_bounty_zombie.py
Outputs:
  assets/bountyhunt/monster_models/bounty_zombie.json   extra cuboids per tier (read by the client at runtime)
  assets/bountyhunt/textures/entity/bounty_zombie/tier_N.png   128x128; the top-left 64x64 is the base zombie body
  data/bountyhunt/loot_tables/entities/bounty_zombie/tier_N.json
Part format: parent ("head", "body", "right_arm", "left_arm", "right_leg", "left_leg", or a path like "head/horn_r"),
name, box [x, y, z, w, h, d] (w/h/d must be whole numbers), pivot, rotation (radians), material.
"""
import json, os, random
from PIL import Image

RES = "src/main/resources"
ASSETS = RES + "/assets/bountyhunt"
DATA = RES + "/data/bountyhunt"
TEX = 128

# ---------------------------------------------------------------- parts per tier
def P(parent, name, box, mat, o=(0, 0, 0), r=(0, 0, 0)):
    return dict(p=parent, n=name, b=list(box), o=list(o), r=list(r), m=mat)

def spike(parent, name, pivot, rot, mat, h=4, w=2):
    return P(parent, name, (-w / 2, -h, -w / 2, w, h, w), mat, pivot, rot)

def tier0():
    return [P("body", "belt", (-4.5, 9.5, -2.5, 9, 2, 5), "cloth"),
            P("right_arm", "wrap_r", (-3.5, 7, -2.5, 5, 2, 5), "wrap"), P("left_arm", "wrap_l", (-1.5, 7, -2.5, 5, 2, 5), "wrap"),
            P("head", "headband", (-4.5, -6.5, -4.5, 9, 1, 9), "cloth")]

def tier1():
    return [P("body", "belt", (-4.5, 9.5, -2.5, 9, 2, 5), "cloth"), P("body", "buckle", (-1, 9.5, -2.7, 2, 2, 1), "metal"),
            P("right_arm", "paul_r", (-3.5, -3, -2.5, 5, 3, 5), "metal"), P("left_arm", "paul_l", (-1.5, -3, -2.5, 5, 3, 5), "metal"),
            P("body", "chest", (-4.5, 1, -2.7, 9, 6, 1), "metal"),
            P("head", "horn_r", (-1, -4, -1, 2, 4, 2), "bone", (-3, -8, -0.5), (0, 0, 0.5)),
            P("head", "horn_l", (-1, -4, -1, 2, 4, 2), "bone", (3, -8, -0.5), (0, 0, -0.5))]

def tier2():
    parts = [P("body", "belt", (-4.5, 9.5, -2.5, 9, 2, 5), "red"),
             P("head", "helm", (-4.5, -8.5, -4.5, 9, 3, 9), "metal"),
             P("body", "chest", (-4.5, 0.5, -2.8, 9, 8, 1), "metal"),
             P("right_arm", "paul_r", (-4, -3.5, -3, 6, 4, 6), "metal"), P("left_arm", "paul_l", (-2, -3.5, -3, 6, 4, 6), "metal"),
             spike("right_arm", "sp_r1", (-1, -3.5, 0), (0, 0, 0), "spike"), spike("left_arm", "sp_l1", (1, -3.5, 0), (0, 0, 0), "spike"),
             spike("right_arm", "sp_r2", (-3.8, -1.5, 0), (0, 0, -1.2), "spike", 3), spike("left_arm", "sp_l2", (3.8, -1.5, 0), (0, 0, 1.2), "spike", 3),
             P("right_arm", "brac_r", (-3.5, 6.5, -2.5, 5, 4, 5), "metal"), P("left_arm", "brac_l", (-1.5, 6.5, -2.5, 5, 4, 5), "metal"),
             P("head", "horn_r", (-1.5, -3, -1.5, 3, 3, 3), "bone", (-4, -7, 0), (0, 0, 0.9)),
             P("head", "horn_l", (-1.5, -3, -1.5, 3, 3, 3), "bone", (4, -7, 0), (0, 0, -0.9)),
             P("head/horn_r", "tip_r", (-1, -4, -1, 2, 4, 2), "bone", (0, -3, 0), (0, 0, 0.5)),
             P("head/horn_l", "tip_l", (-1, -4, -1, 2, 4, 2), "bone", (0, -3, 0), (0, 0, -0.5))]
    for i, y in enumerate((2, 5.5, 9)):
        parts.append(P("body", f"back{i}", (-0.5, -4, -0.5, 1, 4, 1), "spike", (0, y, 2.2), (-1.3, 0, 0)))
    return parts

def tier3():
    parts = [P("head", "crown", (-4.5, -9, -4.5, 9, 2, 9), "gold")]
    for i, (x, z) in enumerate(((-3.5, -3.5), (3.5, -3.5), (-3.5, 3.5), (3.5, 3.5))):
        parts.append(P("head", f"cs{i}", (-0.5, -3, -0.5, 1, 3, 1), "gold", (x, -9, z)))
    parts += [P("head", "cs4", (-0.5, -4, -0.5, 1, 4, 1), "gold", (0, -9, -4)),
              P("head", "horn_r", (-1.5, -4, -1.5, 3, 4, 3), "bone", (-4.5, -6, 0), (0, 0, -0.9)),
              P("head", "horn_l", (-1.5, -4, -1.5, 3, 4, 3), "bone", (4.5, -6, 0), (0, 0, 0.9)),
              P("head/horn_r", "tip_r", (-1, -5, -1, 2, 5, 2), "bone", (0, -4, 0), (0, 0, -0.6)),
              P("head/horn_l", "tip_l", (-1, -5, -1, 2, 5, 2), "bone", (0, -4, 0), (0, 0, 0.6)),
              P("body", "belt", (-4.5, 9.5, -2.5, 9, 2, 5), "cape"),
              P("body", "chest", (-4.5, 0.5, -2.9, 9, 9, 1), "obs"), P("body", "emblem", (-2, 3, -3.3, 4, 4, 1), "gold"),
              P("body", "cape", (-5, 0, 0, 10, 17, 1), "cape", (0, 0.5, 2.3), (0.12, 0, 0)),
              P("right_arm", "paul_r", (-4.5, -4, -3.5, 7, 5, 7), "obs"), P("left_arm", "paul_l", (-2.5, -4, -3.5, 7, 5, 7), "obs"),
              spike("right_arm", "sp_r1", (-1, -4, 0), (0, 0, 0), "gold", 5), spike("left_arm", "sp_l1", (1, -4, 0), (0, 0, 0), "gold", 5),
              spike("right_arm", "sp_r2", (-4.6, -2, 0), (0, 0, -1.2), "gold", 4), spike("left_arm", "sp_l2", (4.6, -2, 0), (0, 0, 1.2), "gold", 4),
              P("right_arm", "brac_r", (-3.5, 6, -2.5, 5, 5, 5), "gold"), P("left_arm", "brac_l", (-1.5, 6, -2.5, 5, 5, 5), "gold"),
              P("right_leg", "greave_r", (-2.5, 5, -2.5, 5, 5, 5), "obs"), P("left_leg", "greave_l", (-2.5, 5, -2.5, 5, 5, 5), "obs"),
              P("right_leg", "knee_r", (-2, 4, -3.2, 4, 3, 1), "gold"), P("left_leg", "knee_l", (-2, 4, -3.2, 4, 3, 1), "gold")]
    return parts

TIERS = [tier0(), tier1(), tier2(), tier3()]

# ---------------------------------------------------------------- palettes
PAL = [
 dict(skin=(112, 140, 96), shirt=(54, 140, 160), pants=(60, 60, 140), eye=(235, 230, 120), cloth=(122, 96, 64), wrap=(205, 195, 165)),
 dict(skin=(118, 128, 112), shirt=(70, 70, 82), pants=(50, 50, 70), eye=(205, 125, 255), cloth=(90, 60, 40), metal=(130, 134, 144), bone=(222, 212, 182)),
 dict(skin=(124, 100, 96), shirt=(64, 40, 40), pants=(46, 36, 36), eye=(255, 60, 40), red=(140, 28, 28), metal=(74, 74, 84), bone=(210, 200, 170), spike=(100, 100, 112), vein=(255, 70, 40)),
 dict(skin=(98, 92, 106), shirt=(40, 32, 52), pants=(30, 26, 40), eye=(255, 228, 110), cape=(128, 20, 40), obs=(42, 34, 56), gold=(232, 190, 60), bone=(216, 206, 176), vein=(255, 215, 80)),
]
PAL[0]["vein"] = None; PAL[1]["vein"] = (190, 110, 255)

def clamp(v): return max(0, min(255, int(v)))

def faces(u, v, w, h, d):
    return {"up": (u + d, v, w, d), "down": (u + d + w, v, w, d), "west": (u, v + d, d, h),
            "north": (u + d, v + d, w, h), "east": (u + d + w, v + d, d, h), "south": (u + d + w + d, v + d, w, h)}

def paint_box(img, u, v, w, h, d, color, rnd, metal=False):
    px = img.load()
    for name, (x0, y0, fw, fh) in faces(u, v, w, h, d).items():
        shade = {"up": 1.18, "down": 0.7, "west": 0.88, "east": 0.94, "north": 1.0, "south": 0.82}[name]
        for y in range(y0, y0 + fh):
            for x in range(x0, x0 + fw):
                k = shade * (1.06 - 0.12 * (y - y0) / max(1, fh))
                n = rnd.randint(-7, 7)
                c = [clamp(color[i] * k + n) for i in range(3)]
                edge = x in (x0, x0 + fw - 1) or y in (y0, y0 + fh - 1)
                if edge and fw > 2 and fh > 2:
                    c = [clamp(v2 * 0.78) for v2 in c]
                if metal and fw >= 4 and fh >= 4 and (x - x0, y - y0) in ((1, 1), (fw - 2, 1), (1, fh - 2), (fw - 2, fh - 2)):
                    c = [clamp(v2 + 55) for v2 in c]
                px[x, y] = tuple(c) + (255,)

def pack(parts, used):
    """Place each extra part's UV block in the free area (outside the base 64x64) using a simple shelf packer."""
    regions = [[64, 0, 127, 127], [0, 64, 63, 127]]
    cur = [[r[0], r[1], 0] for r in regions]  # x, y, row height
    for part in sorted(parts, key=lambda p: -(p["b"][4] + p["b"][5])):
        w, h, d = int(part["b"][3]), int(part["b"][4]), int(part["b"][5])
        sw, sh = 2 * d + 2 * w, d + h
        for ri, r in enumerate(regions):
            cx, cy, rh = cur[ri]
            if cx + sw > r[2] + 1:
                cx, cy, rh = r[0], cy + rh, 0
            if cy + sh <= r[3] + 1:
                part["uv"] = [cx, cy]
                cur[ri] = [cx + sw, cy, max(rh, sh)]
                break
        else:
            raise SystemExit("texture area full for " + part["n"])

def paint_tier(t, parts):
    pal = PAL[t]
    rnd = random.Random(1000 + t)
    img = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 0))
    # base body (same UV layout as the vanilla biped)
    paint_box(img, 0, 0, 8, 8, 8, pal["skin"], rnd)
    paint_box(img, 16, 16, 8, 12, 4, pal["shirt"], rnd)
    paint_box(img, 40, 16, 4, 12, 4, pal["skin"], rnd)
    paint_box(img, 32, 48, 4, 12, 4, pal["skin"], rnd)
    paint_box(img, 0, 16, 4, 12, 4, pal["pants"], rnd)
    paint_box(img, 16, 48, 4, 12, 4, pal["pants"], rnd)
    px = img.load()
    # face: eyes, brow, mouth (head front face is at 8,8 size 8x8)
    dark = tuple(clamp(v * 0.3) for v in pal["skin"])
    for x in (9, 10, 13, 14):
        px[x, 11] = tuple(pal["eye"]) + (255,)
        px[x, 10] = tuple(dark) + (255,)
    for x in range(10, 14):
        px[x, 14] = tuple(dark) + (255,)
    if t >= 2:
        for x in (10, 12): px[x, 14] = (225, 220, 200, 255)
    # veins on skin (tiers 1-3)
    if pal.get("vein"):
        regions = [(0, 0, 32, 16), (40, 16, 56, 28), (32, 48, 48, 60), (16, 16, 40, 32)]
        for _ in range(4 + 5 * t):
            rx0, ry0, rx1, ry1 = rnd.choice(regions)
            x, y = rnd.randint(rx0, rx1 - 1), rnd.randint(ry0, ry1 - 1)
            dx, dy = rnd.choice(((1, 0), (0, 1), (1, 1), (-1, 1)))
            for _ in range(rnd.randint(4, 9)):
                if 8 <= x < 16 and 8 <= y < 16: break  # keep the face readable
                if rx0 <= x < rx1 and ry0 <= y < ry1 and px[x, y][3]:
                    px[x, y] = tuple(pal["vein"]) + (255,)
                x += dx; y += dy
    # extra parts
    for part in parts:
        w, h, d = int(part["b"][3]), int(part["b"][4]), int(part["b"][5])
        col = pal[part["m"]]
        paint_box(img, part["uv"][0], part["uv"][1], w, h, d, col, rnd, metal=part["m"] in ("metal", "gold", "obs"))
    if t == 3:  # emblem sparkle: bright gold on the chest emblem front
        for part in parts:
            if part["n"] == "emblem":
                u, v = part["uv"]; px[u + 1 + 1, v + 1 + 1] = (255, 250, 200, 255)
    return img

def item(name, lo=None, hi=None, chance=None):
    e = {"type": "minecraft:item", "name": "minecraft:" + name}
    if lo is not None:
        e["functions"] = [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}]
    return e

def pool(entry, chance=None):
    p = {"rolls": 1, "entries": [entry]}
    if chance is not None:
        p["conditions"] = [{"condition": "minecraft:random_chance", "chance": chance}]
    return p

LOOT = [  # placeholder rewards; the real economy is decided later
    [pool(item("rotten_flesh", 1, 3)), pool(item("iron_nugget", 1, 3), 0.3), pool(item("iron_ingot", 1, 1), 0.1)],
    [pool(item("rotten_flesh", 2, 4)), pool(item("iron_ingot", 1, 2), 0.6), pool(item("gold_nugget", 2, 5), 0.5), pool(item("emerald", 1, 1), 0.15)],
    [pool(item("iron_ingot", 2, 4)), pool(item("gold_ingot", 1, 2)), pool(item("emerald", 1, 3), 0.6), pool(item("diamond", 1, 1), 0.25)],
    [pool(item("gold_ingot", 3, 6)), pool(item("diamond", 1, 3)), pool(item("emerald", 3, 6)), pool(item("netherite_scrap", 1, 1), 0.3), pool(item("enchanted_golden_apple", 1, 1), 0.1)],
]

out = {"texture": [TEX, TEX], "tiers": []}
for t, parts in enumerate(TIERS):
    pack(parts, None)
    paint_tier(t, parts).save(f"{ASSETS}/textures/entity/bounty_zombie/tier_{t}.png")
    out["tiers"].append(parts)
    json.dump({"type": "minecraft:entity", "pools": LOOT[t]}, open(f"{DATA}/loot_tables/entities/bounty_zombie/tier_{t}.json", "w"), indent=1)
json.dump(out, open(f"{ASSETS}/monster_models/bounty_zombie.json", "w"))
print("ok:", [len(p) for p in TIERS], "parts per tier")
