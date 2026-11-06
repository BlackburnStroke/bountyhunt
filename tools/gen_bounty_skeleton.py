#!/usr/bin/env python3
"""
Generates the Bounty Skeleton model data, its four textures (Normal, Medium, Hard, Legendary) and its four loot tables.
Everything is painted from scratch (no Mojang assets). Edit the PARTS / palettes / loot below and re-run from the project root:
    pip install pillow
    python tools/gen_bounty_skeleton.py
Outputs:
  assets/bountyhunt/monster_models/bounty_skeleton.json   extra cuboids per tier (read by the client at runtime)
  assets/bountyhunt/textures/entity/bounty_skeleton/tier_N.png   128x128; the top-left 64x64 is the base skeleton body
  data/bountyhunt/loot_tables/entities/bounty_skeleton/tier_N.json
Part format: parent ("head", "body", "right_arm", "left_arm", "right_leg", "left_leg", or a path like "head/horn_r"),
name, box [x, y, z, w, h, d] (w/h/d must be whole numbers), pivot, rotation (radians), material.
NOTE: the base model is the SLIM biped (2px limbs, vanilla skeleton layout), so keep spikes/arm parts narrow.
"""
import json, os, random
from PIL import Image

RES = "src/main/resources"
ASSETS = RES + "/assets/bountyhunt"
DATA = RES + "/data/bountyhunt"
TEX = 128

os.makedirs(ASSETS + "/textures/entity/bounty_skeleton", exist_ok=True)
os.makedirs(DATA + "/loot_tables/entities/bounty_skeleton", exist_ok=True)

# ---------------------------------------------------------------- parts per tier
def P(parent, name, box, mat, o=(0, 0, 0), r=(0, 0, 0)):
    return dict(p=parent, n=name, b=list(box), o=list(o), r=list(r), m=mat)

def spike(parent, name, pivot, rot, mat, h=4, w=2):
    return P(parent, name, (-w / 2, -h, -w / 2, w, h, w), mat, pivot, rot)

def tier0():
    """Stray Marauder — no plates at all: a red bandolier across the chest, a small
    wood-and-arrow quiver on the back, and thin wrist bands. Nothing on the head."""
    return [P("body", "sash", (-2.5, 0, -2.9, 5, 13, 1), "band", (1.5, 6, 0), (0, 0.28, 0.55)),
            P("body", "quiver", (-2, 2.5, -1, 4, 7, 1), "wood", (0, 5.5, 1.6), (0.15, 0, 0)),
            spike("body/quiver", "arrow0", (-1, -3, 0), (-0.3, 0, -0.15), "arrow", 5),
            spike("body/quiver", "arrow1", (1, -3, 0), (-0.25, 0, 0.15), "arrow", 4),
            P("right_arm", "band_r", (-1.5, 8, -1.5, 3, 1, 3), "band"),
            P("left_arm", "band_l", (-1.5, 8, -1.5, 3, 1, 3), "band")]

def tier1():
    """Antlered Hunter — branched bone antlers, a fur pelt over the shoulders
    and a bone hunting bow hung diagonally on the back."""
    parts = [
        P("head", "antl_r", (-1.5, -3, -1.5, 3, 5, 3), "bone", (-4.5, -7, 0), (0, 0, -0.85)),
        P("head/antl_r", "tine_r1", (-1, -5, -1, 2, 4, 2), "bone", (0, -5, 0), (0, 0, -0.5)),
        P("head/antl_r", "tine_r2", (-1, -4, -1, 2, 3, 2), "bone", (-2.5, -3, 0), (0, 0, 0.7)),
        P("head", "antl_l", (-1.5, -3, -1.5, 3, 5, 3), "bone", (4.5, -7, 0), (0, 0, 0.85)),
        P("head/antl_l", "tine_l1", (-1, -5, -1, 2, 4, 2), "bone", (0, -5, 0), (0, 0, 0.5)),
        P("head/antl_l", "tine_l2", (-1, -4, -1, 2, 3, 2), "bone", (2.5, -3, 0), (0, 0, -0.7)),
        P("body", "collar", (-4.5, -0.5, -2.5, 9, 3, 5), "pelt"),
        P("right_arm", "pelt_r", (-3.5, -3.5, -2.2, 7, 4, 5), "pelt"),
        P("left_arm", "pelt_l", (-3.5, -3.5, -2.2, 7, 4, 5), "pelt"),
        P("body", "bow", (-0.5, -6, -3.4, 1, 16, 1), "wood", (0, 5, 2.2), (0.1, 0, -1.15)),
        P("body", "belt", (-4.5, 9.5, -2.5, 9, 2, 5), "pelt")]
    return parts

def tier2():
    """Runebound Reaper — a tall blade crest instead of a helm, a bone jaw guard,
    two crossed greatbones on the back, hanging scapulae and rune-carved bracers."""
    return [P("head", "crest_c", (-1, -8, -1, 2, 8, 2), "bone", (0, -8, -2.6), (0.18, 0, 0)),
            P("head", "crest_r", (-1, -7, -1, 2, 7, 2), "bone", (-3.4, -8, -2.2), (0.1, 0, -0.3)),
            P("head", "crest_l", (-1, -7, -1, 2, 7, 2), "bone", (3.4, -8, -2.2), (0.1, 0, 0.3)),
            P("head", "jaw", (-2.5, -1, 2.6, 5, 3, 2), "bone"),
            P("body", "bone_r", (-1, -6, -1, 2, 17, 2), "bone", (0, 3, 2.5), (0.12, 0, 0.8)),
            P("body", "bone_l", (-1, -6, -1, 2, 17, 2), "bone", (0, 3, 2.5), (0.12, 0, -0.8)),
            P("right_arm", "scap_r", (-3.5, -4, -1, 2, 8, 2), "bone", (-1, 1, 0), (0, 0, -0.15)),
            P("left_arm", "scap_l", (1.5, -4, -1, 2, 8, 2), "bone", (1, 1, 0), (0, 0, 0.15)),
            P("right_arm", "rune_r", (-2.5, 5.5, -2.5, 5, 4, 5), "bone"),
            P("left_arm", "rune_l", (-2.5, 5.5, -2.5, 5, 4, 5), "bone"),
            P("right_leg", "runeleg_r", (-2.5, 6, -2.5, 5, 4, 5), "rune"),
            P("left_leg", "runeleg_l", (-2.5, 6, -2.5, 5, 4, 5), "rune")]

def tier3():
    """Bone Sovereign — a hooded void robe instead of a helmet, a halo of floating bone
    shards around the head, tattered skirt panels, a lantern at the belt and a skull staff."""
    parts = [
        # hood shell (open at the front so the face stays readable) + a drooping point
        P("head", "hood_back", (-4, -8, 2.5, 8, 10, 1.8), "void"),
        P("head", "hood_top", (-4.5, -8.5, -4, 9, 1.6, 8), "void"),
        P("head", "hood_l", (-4.6, -8, -1.5, 1.8, 8, 4), "void"),
        P("head", "hood_r", (2.8, -8, -1.5, 1.8, 8, 4), "void"),
        P("head", "hoodtip", (-1, -3, -1, 2, 4, 2), "void", (0, -8.2, 0), (-0.25, 0, 0)),
        # floating bone halo: six shards around the head, tilted
        P("head", "shard0", (-1, -2, -1, 2, 5, 2), "bone", (7.2, -4, 0), (0, 0, 0.25)),
        P("head", "shard1", (-1, -2, -1, 2, 5, 2), "bone", (-7.2, -4, 0), (0, 0, -0.25)),
        P("head", "shard2", (-1, -2, -1, 2, 4, 2), "bone", (0, -9, 5.4), (-0.25, 0, 0)),
        P("head", "shard3", (-1, -2, -1, 2, 4, 2), "bone", (0, -9, -5.4), (0.25, 0, 0)),
        P("head", "shard4", (-1, -2, -1, 2, 4, 2), "bone", (5.4, -6, 5.4), (0, 0, 0.15)),
        P("head", "shard5", (-1, -2, -1, 2, 4, 2), "bone", (-5.4, -6, -5.4), (0, 0, -0.15)),
        # tattered robe skirt + pale-gold belt chain
        P("body", "skirt_f", (-3, 9.4, -2.7, 6, 6, 1), "void", (0, 9.4, 0), (0.06, 0, 0)),
        P("body", "skirt_fl", (-4.6, 9.4, -2.3, 2, 8, 1), "void", (0, 9.4, 0), (0.05, 0, 0.08)),
        P("body", "skirt_fr", (2.6, 9.4, -2.3, 2, 8, 1), "void", (0, 9.4, 0), (0.05, 0, -0.08)),
        P("body", "skirt_bl", (-4.6, 9.4, 1.3, 2, 8, 1), "void", (0, 9.4, 0), (0.05, 0, 0.08)),
        P("body", "skirt_br", (2.6, 9.4, 1.3, 2, 8, 1), "void", (0, 9.4, 0), (0.05, 0, -0.08)),
        P("body", "chain", (-4.5, 9, -2.5, 9, 1, 5), "gold0"),
        # swinging lantern on the hip
        P("body", "lantern", (-1, 0, -1, 2, 3, 2), "gold0", (2.5, 10.5, -2.2), (0, 0, 0.15)),
        # skull staff with a soulfire tongue on top
        P("body", "staff", (-1, -14, -1, 2, 28, 2), "bone", (0, 4, 2.3), (0.08, 0, 0)),
        P("body/staff", "skull", (-2, -4, -2, 4, 4, 4), "bone", (0, -14, 0), (0, 0, 0)),
        spike("body/staff", "flame", (0, -18, 0), (0, 0, 0), "flame", 3),
        # shoulder plates under the hood
        P("right_arm", "paul_r", (-3.5, -3.5, -2.5, 6, 3, 5), "void"),
        P("left_arm", "paul_l", (-2.5, -3.5, -2.5, 6, 3, 5), "void"),
        P("right_arm", "ptom_r", (-1, -2, -1, 2, 3, 2), "gold0", (-2, -3.5, 0), (0, 0, -0.2)),
        P("left_arm", "ptom_l", (-1, -2, -1, 2, 3, 2), "gold0", (2, -3.5, 0), (0, 0, 0.2)),
        # face plate: pale-gold mouth grille below the hood
        P("head", "grille", (-2, -1, 2.8, 4, 2, 1.4), "gold0")]
    return parts

TIERS = [tier0(), tier1(), tier2(), tier3()]

# ---------------------------------------------------------------- palettes
PAL = [
 dict(bone=(222, 218, 204), shirt=(56, 50, 48), pants=(218, 214, 200), eye=(150, 225, 255),
      band=(128, 48, 52), wood=(101, 74, 48), arrow=(230, 220, 188)),
 dict(bone=(200, 196, 182), shirt=(52, 44, 38), pants=(196, 192, 178), eye=(150, 225, 255),
      pelt=(112, 78, 48), wood=(96, 70, 46), arrow=(230, 220, 188)),
 dict(bone=(208, 204, 192), shirt=(38, 36, 46), pants=(204, 200, 188), eye=(90, 225, 255),
      rune=(196, 192, 180)),
 dict(bone=(232, 228, 214), shirt=(30, 28, 40), pants=(226, 222, 208), eye=(180, 255, 240),
      void=(30, 28, 42), gold0=(222, 196, 122), flame=(176, 255, 236)),
]
PAL[0]["vein"] = None; PAL[1]["vein"] = None
PAL[2]["vein"] = (104, 232, 255)
PAL[3]["vein"] = (190, 255, 242)

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
    rnd = random.Random(2000 + t)
    img = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 0))
    # base body in the vanilla SLIM skeleton layout (2px limbs, shared tex for mirrored pairs)
    paint_box(img, 0, 0, 8, 8, 8, pal["bone"], rnd)      # head
    paint_box(img, 16, 16, 8, 12, 4, pal["shirt"], rnd)  # torso (dark cloth over a faint ribcage)
    paint_box(img, 40, 16, 2, 12, 2, pal["bone"], rnd)   # arms (both sides share this, mirrored at runtime)
    paint_box(img, 0, 16, 2, 12, 2, pal["bone"], rnd)    # legs (both sides share this, mirrored at runtime)
    px = img.load()
    # torso ribs: faint pale bands on the front face (front face = x 20..28, y 20..32)
    band = tuple(clamp(v * 1.5) for v in pal["shirt"])
    for y in (22, 25, 28, 30):
        for x in range(20, 28):
            if (x * 3 + y) % 4 != 0:
                px[x, y] = band + (255,)
    # face: the skull front is at (8,8) size 8x8
    dark = tuple(clamp(v * 0.25) for v in pal["bone"])
    socket = (16, 15, 18)
    for x in (9, 10, 13, 14):          # eye sockets
        px[x, 11] = socket + (255,)
    px[10, 12] = socket + (255,); px[13, 12] = socket + (255,)
    for x in (9, 10, 13, 14):          # glowing pupils
        px[x, 11] = tuple(pal["eye"]) + (255,)
    px[11, 12] = dark + (255,); px[12, 12] = dark + (255,)   # nasal
    for x in range(9, 15):             # jaw line with teeth
        px[x, 14] = (dark if (x - 9) % 2 else (140, 136, 128)) + (255,)     # teeth
    if t >= 2:
        for x in (9, 14): px[x, 11] = tuple(pal["eye"]) + (255,)  # keep glow at legendary colors
    # glowing cracks in the bone (tiers 1-3)
    if pal.get("vein"):
        regions = [(0, 0, 32, 16), (32, 16, 40, 30), (40, 16, 56, 30), (0, 16, 16, 30)]
        for _ in range(3 + 4 * t):
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
        paint_box(img, part["uv"][0], part["uv"][1], w, h, d, col, rnd, metal=part["m"] in ("metal", "gold", "gold0"))
    if t == 3:  # soulfire sparkle: bright flame on the staff tip and lantern glow
        for part in parts:
            if part["n"] in ("flame", "lantern"):
                u, v = part["uv"]
                w, h, d = int(part["b"][3]), int(part["b"][4]), int(part["b"][5])
                fx, fy = u + d + w // 2, v + d + h // 2  # centre of the front face
                px[fx, fy] = ((255, 255, 245, 255) if part["n"] == "flame" else tuple(pal["flame"]) + (255,))
                px[fx, fy - 1] = tuple(pal["flame"]) + (255,)
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

LOOT = [
    [pool(item("bone", 2, 4)), pool(item("arrow", 2, 5)), pool(item("iron_nugget", 1, 3), 0.2)],
    [pool(item("bone", 3, 6)), pool(item("arrow", 4, 8)), pool(item("iron_ingot", 1, 2), 0.5), pool(item("emerald", 1, 1), 0.15)],
    [pool(item("bone", 4, 8)), pool(item("arrow", 6, 12)), pool(item("gold_ingot", 1, 2)), pool(item("emerald", 2, 4), 0.6), pool(item("diamond", 1, 1), 0.2)],
    [pool(item("bone", 6, 12)), pool(item("arrow", 8, 16)), pool(item("gold_ingot", 3, 6)), pool(item("diamond", 1, 3), 0.9), pool(item("emerald", 3, 6)), pool(item("netherite_scrap", 1, 1), 0.3), pool(item("enchanted_golden_apple", 1, 1), 0.1)],
]

out = {"texture": [TEX, TEX], "tiers": []}
for t, parts in enumerate(TIERS):
    pack(parts, None)
    paint_tier(t, parts).save(f"{ASSETS}/textures/entity/bounty_skeleton/tier_{t}.png")
    out["tiers"].append(parts)
    json.dump({"type": "minecraft:entity", "pools": LOOT[t]}, open(f"{DATA}/loot_tables/entities/bounty_skeleton/tier_{t}.json", "w"), indent=1)
json.dump(out, open(f"{ASSETS}/monster_models/bounty_skeleton.json", "w"))
print("ok:", [len(p) for p in TIERS], "parts per tier")
