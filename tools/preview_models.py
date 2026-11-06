#!/usr/bin/env python3
"""
Renders a preview sheet of every bounty mob on every tier, drawn as the real Minecraft model
(cuboid parts, posed like in-game, isometric projection) with the tier texture applied at the
correct in-game scale (x1 Normal, x1.5 Medium, x2 Hard, x2.5 Legendary).

  pip install pillow
  python tools/preview_models.py            ->  tools/preview_models.png

Vanilla skins are read from the local 1.20.1 jar (same rule as gen_monster_textures.py).
The Bounty Zombie row uses the mod's painted textures plus the extra cuboids from
assets/bountyhunt/monster_models/bounty_zombie.json (UVs recomputed exactly like the generator).

Offline approximation, so hats/limb splays are close but not pixel-perfect.
"""
import glob, io, json, math, os, sys, zipfile
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources")
OUT_DEFAULT = os.path.join(ROOT, "tools", "preview_models.png")
P = math.pi
ARMS_FWD = (-P / 2, 0.0, 0.0)          # zombie / skeleton arms point forward
ARMS_WITCH = (-0.9, 0.0, 0.0)

# ---------------------------------------------------------------- jar / mod textures
def find_jar():
    appdata = os.environ.get("APPDATA", "")
    home = os.path.expanduser("~")
    cands = [os.path.join(appdata, ".minecraft", "versions", "1.20.1", "1.20.1.jar"),
             os.path.join(home, ".minecraft", "versions", "1.20.1", "1.20.1.jar")]
    cands += glob.glob(os.path.join(home, ".gradle", "caches", "fabric-loom", "**", "*1.20.1*.jar"), recursive=True)
    for c in cands:
        if c and os.path.isfile(c):
            try:
                with zipfile.ZipFile(c) as z:
                    if "assets/minecraft/textures/entity/zombie/zombie.png" in z.namelist():
                        return c
            except zipfile.BadZipFile:
                pass
    sys.exit("Could not find the Minecraft 1.20.1 jar.")

JAR = find_jar()
_CACHE = {}

def jar_png(path):
    if path not in _CACHE:
        with zipfile.ZipFile(JAR) as z:
            _CACHE[path] = Image.open(io.BytesIO(z.read(path))).convert("RGBA")
    return _CACHE[path]

def mod_png(rel):
    return Image.open(os.path.join(RES, "assets", "bountyhunt", rel)).convert("RGBA")

# ---------------------------------------------------------------- matrices & projection
def mat_identity():
    return ((1, 0, 0), (0, 1, 0), (0, 0, 1))

def mat_mul(a, b):
    return tuple(tuple(sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)) for i in range(3))

def mat_apply(m, v):
    return tuple(sum(m[i][k] * v[k] for k in range(3)) for i in range(3))

def mat_rot(rx, ry, rz):
    cx, sx, cy, sy, cz, sz = (math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz))
    out = mat_identity()
    for m in (((1, 0, 0), (0, cx, -sx), (0, sx, cx)),          # X
              ((cy, 0, sy), (0, 1, 0), (-sy, 0, cy)),          # Y
              ((cz, -sz, 0), (sz, cz, 0), (0, 0, 1))):         # Z
        out = mat_mul(m, out)
    return out

def rot_of(part, poses):
    r = (part.get("rot", (0, 0, 0)) if "mat" not in part else (0, 0, 0))
    if part.get("is_biped") and part["n"] in poses:
        r = poses[part["n"]]
    return r if r else (0, 0, 0)

YAW, PITCH = math.radians(135), math.radians(30)
ca, sa = math.cos(YAW), math.sin(YAW)
cp, sp = math.cos(PITCH), math.sin(PITCH)

def project(x, y, z):
    """MC model coords (y down) -> (screen_x, screen_y, depth)."""
    X1 = x * ca + z * sa
    Z1 = -x * sa + z * ca
    Y2 = cp * y + sp * Z1   # model y is DOWN; smaller y = higher on screen
    Z2 = cp * Z1 - sp * y
    return (X1, Y2, Z2)

# ---------------------------------------------------------------- part transforms
def part_transform(part, poses):
    """Returns (pivot__world, rotation_matrix__world)."""
    if "mat" in part:                      # pre-baked chain (bounty zombie extras)
        return part["piv"], part["mat"]
    if "parent_piv" in part:               # extra part with biped parent
        return part["parent_piv"], part["parent_mat"]
    pose = poses.get(part["n"], (0, 0, 0))
    return part["piv"], (mat_rot(*pose) if pose else mat_identity())

def world_vertex(part, poses, corner):
    piv, M = part_transform(part, poses)
    pw = tuple(piv[i] + mat_apply(M, corner)[i] for i in range(3))
    return pw

# ---------------------------------------------------------------- model definitions
def biped():
    return [
        dict(n="head", uv=(0, 0), b=(-4, -8, -4, 8, 8, 8), piv=(0, 0, 0), is_biped=True),
        dict(n="body", uv=(16, 16), b=(-4, 0, -2, 8, 12, 4), piv=(0, 0, 0), is_biped=True),
        dict(n="right_arm", uv=(40, 16), b=(-3, -2, -2, 4, 12, 4), piv=(-5, 2, 0), is_biped=True),
        dict(n="left_arm", uv=(32, 48), b=(-1, -2, -2, 4, 12, 4), piv=(5, 2, 0), mirror=True, is_biped=True),
        dict(n="right_leg", uv=(0, 16), b=(-2, 0, -2, 4, 12, 4), piv=(-1.9, 12, 0), is_biped=True),
        dict(n="left_leg", uv=(16, 48), b=(-2, 0, -2, 4, 12, 4), piv=(1.9, 12, 0), mirror=True, is_biped=True),
    ]

def biped_poses(arms=ARMS_FWD):
    return {"right_arm": arms, "left_arm": arms} if arms else {}

def slimped():
    parts = biped()
    for p in parts:
        p["is_biped"] = True
    slim = ["right_arm", "left_arm", "right_leg", "left_leg"]
    for p in parts:
        if p["n"] in slim:
            if p["n"].endswith("arm"):
                p["uv"] = (40, 16); p["b"] = (-1, -2, -1, 2, 12, 2)
            elif p["n"] == "right_leg":
                p["uv"] = (0, 16); p["b"] = (-1, 0, -1, 2, 12, 2)
            else:
                p["uv"] = (16, 16); p["b"] = (-1, 0, -1, 2, 12, 2)
    return parts

def spider_parts():
    parts = [
        dict(n="head", uv=(32, 4), b=(-4, 5, -10, 8, 8, 8), piv=(0, 0, 0)),
        dict(n="body", uv=(0, 12), b=(-5, 3, -2, 10, 8, 10), piv=(0, 0, 0)),
    ]
    angles = (-0.55, -0.18, 0.18, 0.55)
    zs = (-3.9, -1.3, 1.3, 3.9)
    for i in range(4):
        parts.append(dict(n=f"rleg{i}", uv=(18, 0), b=(-15, -1, -1, 15, 2, 2), piv=(-2, 12, zs[i]),
                          rot=(0.0, angles[i], -0.10)))
        parts.append(dict(n=f"lleg{i}", uv=(18, 0), b=(0, -1, -1, 15, 2, 2), piv=(2, 12, zs[i]),
                          rot=(0.0, -angles[i], 0.10), mirror=True))
    return parts

def creeper_parts():
    return [
        dict(n="head", uv=(0, 0), b=(-4, -2, -4, 8, 8, 8), piv=(0, 6, 0), rot=(0.06, 0, 0)),
        dict(n="body", uv=(16, 16), b=(-4, 0, -2, 8, 12, 4), piv=(0, 6, 0)),
        dict(n="leg_fl", uv=(0, 16), b=(-2, 0, -2, 4, 6, 4), piv=(-2, 18, -4)),
        dict(n="leg_fr", uv=(0, 16), b=(-2, 0, -2, 4, 6, 4), piv=(2, 18, -4), mirror=True),
        dict(n="leg_bl", uv=(0, 16), b=(-2, 0, -2, 4, 6, 4), piv=(-2, 18, 4), mirror=True),
        dict(n="leg_br", uv=(0, 16), b=(-2, 0, -2, 4, 6, 4), piv=(2, 18, 4)),
    ]

def witch_parts():
    return [
        dict(n="head", uv=(0, 0), b=(-4, -10, -4, 8, 10, 8), piv=(0, 2, 0)),
        dict(n="nose", uv=(24, 0), b=(-1.5, -1, -5, 2, 4, 2), piv=(0, 2, 0)),
        dict(n="hat_brim", uv=(36, 24), b=(-3.5, -11.5, -3.5, 7, 1, 7), piv=(0, 2, 0), rot=(0, 0.15, 0)),
        dict(n="hat_1", uv=(44, 38), b=(-2.5, -14.5, -2.5, 5, 3, 5), piv=(0, 2, 0)),
        dict(n="hat_2", uv=(46, 42), b=(-1.5, -16.5, -1.5, 3, 3, 3), piv=(0, 2, 0), rot=(0, -0.12, 0)),
        dict(n="hat_tip", uv=(44, 42), b=(-1, -20, -0.75, 2, 4, 1.5), piv=(0, 2, 0), rot=(0, 0.2, 0)),
        dict(n="body", uv=(16, 20), b=(-4, 0, -2, 8, 12, 4), piv=(0, 2, 0)),
        dict(n="right_arm", uv=(40, 38), b=(-1, -2, -1, 2, 12, 2), piv=(-5, 4, 0)),
        dict(n="left_arm", uv=(40, 38), b=(-1, -2, -1, 2, 12, 2), piv=(5, 4, 0), mirror=True),
        dict(n="right_leg", uv=(0, 22), b=(-2, 0, -1, 4, 13, 2), piv=(-1.9, 11, 0)),
        dict(n="left_leg", uv=(0, 22), b=(-2, 0, -1, 4, 13, 2), piv=(1.9, 11, 0), mirror=True),
    ]

def enderman_parts():
    return [
        dict(n="head", uv=(0, 0), b=(-4, -8, -4, 8, 8, 8), piv=(0, -13, 0)),
        dict(n="body", uv=(32, 16), b=(-4, 0, -2, 8, 12, 4), piv=(0, -14, 0)),
        dict(n="right_arm", uv=(56, 0), b=(-1, -2, -1, 2, 30, 2), piv=(-5, -12, 0)),
        dict(n="left_arm", uv=(56, 0), b=(-1, -2, -1, 2, 30, 2), piv=(5, -12, 0), mirror=True),
        dict(n="right_leg", uv=(56, 0), b=(-1, 0, -1, 2, 30, 2), piv=(-2, -5, 0)),
        dict(n="left_leg", uv=(56, 0), b=(-1, 0, -1, 2, 30, 2), piv=(2, -5, 0), mirror=True),
    ]

# ---------------------------------------------------------------- bounty monster extras
BIPED_PIVOT = {"head": (0, 0, 0), "body": (0, 0, 0), "right_arm": (-5, 2, 0),
               "left_arm": (5, 2, 0), "right_leg": (-1.9, 12, 0), "left_leg": (1.9, 12, 0)}
SLIM_PIVOT = dict(BIPED_PIVOT, right_leg=(-2, 12, 0), left_leg=(2, 12, 0))

def zombie_extras(tier, poses, path="bounty_zombie.json", pivots=None):
    pivots = pivots or BIPED_PIVOT
    data = json.load(open(os.path.join(RES, "assets", "bountyhunt", "monster_models", path)))
    parts = data["tiers"][tier]
    # reproduce the generator's shelf packer so the UVs match the painted texture exactly
    regions = [[64, 0, 127, 127], [0, 64, 63, 127]]
    cur = [[r[0], r[1], 0] for r in regions]
    for part in sorted(parts, key=lambda q: -(q["b"][4] + q["b"][5])):
        sw, sh = 2 * int(part["b"][5]) + 2 * int(part["b"][3]), int(part["b"][5]) + int(part["b"][4])
        for ri, r in enumerate(regions):
            cx, cy, rh = cur[ri]
            if cx + sw > r[2] + 1:
                cx, cy, rh = r[0], cy + rh, 0
            if cy + sh <= r[3] + 1:
                part["uv"] = [cx, cy]
                cur[ri] = [cx + sw, cy, max(rh, sh)]
                break
    named = {p["n"]: p for p in parts}
    out = []
    for p in parts:
        chain = p["p"].split("/")
        piv, M = (0.0, 0.0, 0.0), mat_identity()
        for seg in chain:
            pw = M
            if seg in pivots:
                rp, R = pivots[seg], mat_rot(*poses.get(ARM_ALIASES.get(seg, seg), (0, 0, 0)))
            else:
                pr = named[seg]
                rp, R = tuple(pr["o"]), mat_rot(*pr["r"])
            piv = tuple(piv[i] + mat_apply(M, rp)[i] for i in range(3))
            M = mat_mul(R, M)
        piv = tuple(piv[i] + mat_apply(M, tuple(p["o"]))[i] for i in range(3))
        M = mat_mul(mat_rot(*p["r"]), M)
        out.append(dict(n="x_" + p["n"], uv=tuple(p["uv"]), b=tuple(float(v) for v in p["b"]),
                        piv=piv, mat=M, mirror=False))
    return out

ARM_ALIASES = {}

# ---------------------------------------------------------------- sheet rows
def rows_setup():
    rowspec = [
        # label, builder(returning parts, poses), jar template or None for mod-only, bounty flag, model json name
        ("Bounty Zombie (custom model)", lambda: (biped(), biped_poses()), None, "bounty_zombie.json"),
        ("Zombie", lambda: (biped(), biped_poses()), "assets/minecraft/textures/entity/zombie/zombie.png", None),
        ("Husk", lambda: (biped(), biped_poses()), "assets/minecraft/textures/entity/zombie/husk.png", None),
        ("Bounty Skeleton (custom model)", lambda: (slimped(), {k: ARMS_FWD for k in ("right_arm", "left_arm")}), None, "bounty_skeleton.json"),
        ("Skeleton", lambda: (slimped(), {k: ARMS_FWD for k in ("right_arm", "left_arm")}),
         "assets/minecraft/textures/entity/skeleton/skeleton.png", None),
        ("Stray", lambda: (slimped(), {k: ARMS_FWD for k in ("right_arm", "left_arm")}),
         "assets/minecraft/textures/entity/skeleton/stray.png", None),
        ("Spider", lambda: (spider_parts(), {}), "assets/minecraft/textures/entity/spider/spider.png", None),
        ("Cave Spider", lambda: (spider_parts(), {}), "assets/minecraft/textures/entity/spider/cave_spider.png", None),
        ("Creeper", lambda: (creeper_parts(), {"head": (0.06, 0, 0)}),
         "assets/minecraft/textures/entity/creeper/creeper.png", None),
        ("Witch (hat approximated)", lambda: (witch_parts(), {k: ARMS_WITCH for k in ("right_arm", "left_arm")}),
         "assets/minecraft/textures/entity/witch.png", None),
        ("Enderman", lambda: (enderman_parts(), {}),
         "assets/minecraft/textures/entity/enderman/enderman.png", None),
    ]
    rows = []
    for label, builder, jarpath, model in rowspec:
        parts, poses = builder()
        mobname = "bounty_" + model.split("_")[1][:-5] if model else (jarpath.split("/")[-1][:-4] if jarpath else None)
        tiers = []
        for t in range(4):
            if model:
                tiers.append(("mod", "textures/entity/%s/tier_%d.png" % (mobname, t)))
            else:
                tiers.append(("jar", jarpath) if t == 0 else ("mod", "textures/entity/%s_%d.png" % (jarpath.split("/")[-1][:-4], t)))
        rows.append(dict(label=label, parts=parts, poses=poses, tiers=tiers, model=model, Slim=model == "bounty_skeleton.json"))
    return rows

def row_parts(row, col):
    """Static parts for a given column (extra bounty cuboids vary per tier)."""
    parts, poses = list(row["parts"]), row["poses"]
    if row["model"]:
        poses = dict(biped_poses(ARMS_FWD))
        slim = row.get("Slim", False)
        parts = list(parts) + zombie_extras(col, poses, row["model"],
                                            SLIM_PIVOT if slim else BIPED_PIVOT)
    return parts, poses

# ---------------------------------------------------------------- face build
def face_rects(u, v, w, h, d):
    # face key -> (u0, v0, fw, fh); the painted face ('north') = the mob's front
    return {"up": (u + d, v, w, d), "down": (u + d + w, v, w, d), "west": (u, v + d, d, h),
            "north": (u + d, v + d, w, h), "east": (u + d + w, v + d, d, h),
            "south": (u + d + w + d, v + d, w, h)}

FACE_CORNERS = lambda x0, y0, z0, x1, y1, z1: {
    "up":    [(x1, y0, z0), (x0, y0, z0), (x0, y0, z1), (x1, y0, z1)],
    "down":  [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
    "north": [(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0)],
    "south": [(x1, y0, z1), (x0, y0, z1), (x0, y1, z1), (x1, y1, z1)],
    "east":  [(x1, y0, z0), (x1, y0, z1), (x1, y1, z1), (x1, y1, z0)],
    "west":  [(x0, y0, z1), (x0, y0, z0), (x0, y1, z0), (x0, y1, z1)],
}
SHADE = {"up": 1.0, "down": 0.5, "north": 0.92, "south": 0.84, "east": 0.68, "west": 0.68}

def build_faces(parts, poses, colscale):
    """list of (depth, shade, dst_pts[4], src_rect(u0,v0,fw,fh), mirror)."""
    faces = []
    for part in parts:
        x0, y0, z0, w, h, d = part["b"]
        x1, y1, z1 = x0 + w, y0 + h, z0 + d
        rects = face_rects(part["uv"][0], part["uv"][1], abs(w), abs(h), abs(d))
        for fname, quad3 in FACE_CORNERS(x0, y0, z0, x1, y1, z1).items():
            pts2 = []
            depth = 0.0
            for c in quad3:
                pw = world_vertex(part, poses, c)
                px = project(pw[0] * colscale, pw[1] * colscale, pw[2] * colscale)
                pts2.append((px[0], px[1]))
                depth += px[2]
            faces.append((depth / 4.0, SHADE[fname], pts2, rects[fname], part.get("mirror", False)))
    return faces

def draw_faces(canvas, faces, scale, offx, offy, S):
    tris = sorted(faces, key=lambda f: f[0])
    for _, shade, pts2, rect, mirror in tris:
        u0, v0, fw, fh = rect
        uvs = [(u0, v0), (u0 + fw, v0), (u0 + fw, v0 + fh), (u0, v0 + fh)]
        if mirror:
            uvs = [(u0 + fw - (ux - u0), vy) for (ux, vy) in uvs]
        dst = [(p[0] * S + offx, p[1] * S + offy) for p in pts2]
        for i, j, k in ((0, 1, 2), (0, 2, 3)):
            blit_tri(canvas, uvs, [dst[i], dst[j], dst[k]], shade)

def blit_tri(canvas, uvs, dst, shade):
    cnt = (sum(p[0] for p in dst) / 3.0, sum(p[1] for p in dst) / 3.0)

    def expand(pt, k=0.5):
        vx, vy = pt[0] - cnt[0], pt[1] - cnt[1]
        ln = math.hypot(vx, vy) or 1.0
        return (pt[0] + vx / ln * k, pt[1] + vy / ln * k)

    d = [expand(p) for p in dst]
    x0, y0 = int(math.floor(min(p[0] for p in d))) - 1, int(math.floor(min(p[1] for p in d))) - 1
    x1, y1 = int(math.ceil(max(p[0] for p in d))) + 1, int(math.ceil(max(p[1] for p in d))) + 1
    if x1 <= x0 or y1 <= y0:
        return
    dl = [(p[0] - x0, p[1] - y0) for p in d]  # bbox-local coords (PIL transforms in this space)
    A = [list(p) + [1.0] for p in dl]
    det = (A[0][0] * (A[1][1] * A[2][2] - A[1][2] * A[2][1])
           - A[1][0] * (A[0][1] * A[2][2] - A[0][2] * A[2][1])
           + A[2][0] * (A[0][1] * A[1][2] - A[0][2] * A[1][1]))
    if abs(det) < 1e-12:
        return
    sx = (uvs[0][0], uvs[1][0], uvs[2][0])
    sy = (uvs[0][1], uvs[1][1], uvs[2][1])
    coeffs = []
    for bvec in (sx, sy):
        vals = []
        for col in range(3):
            M = [row[:] for row in A]
            for r in range(3):
                M[r][col] = bvec[r]
            vals.append((M[0][0] * (M[1][1] * M[2][2] - M[1][2] * M[2][1])
                         - M[1][0] * (M[0][1] * M[2][2] - M[0][2] * M[2][1])
                         + M[2][0] * (M[0][1] * M[1][2] - M[0][2] * M[1][1])) / det)
        coeffs.append(vals)
    a, b, c = coeffs[0]
    e, f, g = coeffs[1]
    warped = SRC.transform((x1 - x0, y1 - y0), Image.AFFINE, (a, b, c, e, f, g), resample=Image.NEAREST)
    if shade < 0.999:
        dark = Image.new("RGBA", warped.size, (0, 0, 0, int((1 - shade) * 255)))
        dd = Image.new("L", warped.size, 0)
        ImageDraw.Draw(dd).polygon(dl, fill=255)
        overlay = Image.new("RGBA", warped.size, (0, 0, 0, 0))
        overlay.paste(dark, (0, 0), dd)
        warped = Image.alpha_composite(warped, overlay)
    mask = Image.new("L", warped.size, 0)
    ImageDraw.Draw(mask).polygon(dl, fill=255)
    layer = Image.new("RGBA", warped.size, (0, 0, 0, 0))
    layer.paste(warped, (0, 0), mask)
    canvas.alpha_composite(layer, (x0, y0))

SRC = None  # set per cell

def render_cell(parts, poses, teximg, colscale, S, cellw, cellh):
    global SRC
    SRC = teximg
    lo, hi = render_bbox(parts, poses, colscale)
    offx = cellw / 2.0 - (lo[0] + hi[0]) / 2.0 * S
    offy = cellh / 2.0 - (lo[1] + hi[1]) / 2.0 * S
    canvas = Image.new("RGBA", (cellw, cellh), (0, 0, 0, 0))
    draw_faces(canvas, build_faces(parts, poses, colscale), S, offx, offy, S)
    return canvas

def render_bbox(parts, poses, colscale):
    lo = [1e9, 1e9]; hi = [-1e9, -1e9]
    for part in parts:
        x0, y0, z0, w, h, d = part["b"]
        for dx in (0, w):
            for dy in (0, h):
                for dz in (0, d):
                    pw = world_vertex(part, poses, (x0 + dx, y0 + dy, z0 + dz))
                    px = project(pw[0] * colscale, pw[1] * colscale, pw[2] * colscale)
                    lo[0] = min(lo[0], px[0]); lo[1] = min(lo[1], px[1])
                    hi[0] = max(hi[0], px[0]); hi[1] = max(hi[1], px[1])
    return lo, hi

# ---------------------------------------------------------------- assembly
COLS = [("Normal", 1.0), ("Medium", 1.5), ("Hard", 2.0), ("Legendary", 2.5)]

def load_font(size):
    for p in ("C:/Windows/Fonts/segoeuib.ttf", "C:/Windows/Fonts/arialbd.ttf", "C:/Windows/Fonts/arial.ttf"):
        try:
            return ImageFont.truetype(p, size)
        except Exception:
            pass
    return ImageFont.load_default()

def main():
    argv = sys.argv[1:]
    filt = None
    if "--mobs" in argv:
        i = argv.index("--mobs")
        filt = [m.strip().lower() for m in argv[i + 1].split(",")] if len(argv) > i + 1 and not argv[i + 1].startswith("-") else []
        argv = argv[:i] + argv[i + 2 if filt else i + 1:]
    out = argv[0] if argv else OUT_DEFAULT
    rows = [r for r in rows_setup()
            if not filt or any(m in r["label"].lower() for m in filt)]
    if not rows:
        sys.exit("No matching rows for --mobs %s" % filt)
    labelw, gap, pad = 200, 26, 26
    TITLE_H = 64
    cellw = 300 if not (filt and len(rows) <= 2) else 460

    # per-row scale: fit the Legendary (biggest) render into the cell
    cellh = {}
    scale = {}
    for i, row in enumerate(rows):
        parts, poses = row_parts(row, 3)
        lo, hi = render_bbox(parts, poses, COLS[3][1])
        ch = 300 if not any(p["n"].startswith("right_leg") and abs(p["b"][4] - 30) < 0.01 for p in row["parts"]) else 400
        if any(p["n"].startswith("rleg") or p["n"].startswith("lleg") for p in row["parts"]):
            ch = 360
        cellh[i] = ch
        S = min((cellw - 20) / max(hi[0] - lo[0], 1e-6), (ch - 20) / max(hi[1] - lo[1], 1e-6))
        scale[i] = min(S, 8.0 if filt else 6.0)
    W = int(pad + labelw + 4 * (cellw + gap) + pad)
    H = int(TITLE_H + sum(cellh.values()) + pad * 2)
    sheet = Image.new("RGBA", (W, H), (33, 33, 38, 255))
    d = ImageDraw.Draw(sheet)
    f_col = load_font(19); f_row = load_font(16)
    for c, (name, sc) in enumerate(COLS):
        x = pad + labelw + c * (cellw + gap) + cellw / 2
        d.text((x, 16), "%s  ×%g" % (name, sc), font=f_col, fill=(232, 232, 236), anchor="mm")
    y = TITLE_H
    for i, row in enumerate(rows):
        h = cellh[i]
        d.text((pad, y + h / 2), row["label"], font=f_row, fill=(238, 238, 242), anchor="lm")
        if i % 2 == 0:
            d.rectangle([(pad - 10, y - 6), (W - pad + 10, y + h - 6)], fill=(39, 39, 45))
        for c in range(4):
            kind, rel = row["tiers"][c]
            tex = jar_png(rel) if kind == "jar" else mod_png(rel)
            parts, poses = row_parts(row, c)
            cell = render_cell(parts, poses, tex, COLS[c][1], scale[i], cellw, h)
            sheet.alpha_composite(cell, (int(pad + labelw + c * (cellw + gap)), y))
        y += h
        d.line([(pad, y - 10), (W - pad + 10, y - 10)], fill=(54, 54, 62), width=2)
    sheet.save(out)
    print("Sheet saved to", out)

if __name__ == "__main__":
    main()
