"""The goblin's body (Kingdoms 0.7.321, {@code GoblinBody}): kokosikk's Blockbench Workshop goblin (CC BY 4.0,
tools/blockbench/kokosikk_goblin.bbmodel, credit in textures/entity/greenskin/CREDITS.txt) gives a goblin villager its
shape. MCA's skin, face, clothes and hair (and any armour) are drawn on MCA's own body, reshaped bone by bone into the
goblin's head, tunic, arms and legs; what the goblin has beyond those (ears, nose, tusks, fists, feet and toenails, and
its cap, earring, belt, pouch, loincloth and shin wraps) is drawn here, on parts of our own, painted in the pack's style.

Run from the kingdoms project root: python -I tools/gen_goblin_body.py [--java] [--preview skin_dir]
  writes textures/entity/greenskin/goblin_body.png (64x64, box UV; flesh in greys, tinted in game by each goblin's
  green; bone and gear in their own colours); --java prints GoblinBody.PARTS; --preview draws build/goblin-body-preview.png
  (skin_dir: MCA's face.png, a clothing .png and a hair.png, as extracted from MCA's jar, for the look in game).

Changes from the workshop model: its skull, tunic, arms and shins are left to MCA's body (reshaped to them); its dagger
and coin are left off (a goblin holds what it holds); its sub-bones flattened onto six bones (only the nose's droop and
the cap tip's lean kept); every box is snapped to a whole-pixel texture size (the shape kept by a box deformation); and
all of it is repainted."""
import json
import math
import os
import random
import sys
from PIL import Image, ImageDraw

BB = "tools/blockbench/kokosikk_goblin.bbmodel"
OUT = "src/main/resources/assets/jaymoskingdoms/textures/entity/greenskin/goblin_body.png"
SHEET = 64

# The bones as GoblinBody poses MCA's body, in Minecraft model space (pixels, y down, the goblin's right at -x), before
# the hunch. Keep in step with GoblinBody.PIVOTS.
PIVOT = {"HEAD": (0.0, 6.7, -1.2), "BODY": (0.0, 6.5, 0.0), "RIGHT_ARM": (-4.65, 8.8, -0.4), "LEFT_ARM": (4.65, 8.8, -0.4),
         "RIGHT_LEG": (-3.3, 16.0, 0.0), "LEFT_LEG": (3.3, 16.0, 0.0)}
# How MCA's (a player's) boxes map onto the goblin's: per bone, the scale (pivots above)
SCALE = {"HEAD": (1.125, 1.0, 1.0), "BODY": (1.0, 0.75, 1.25), "RIGHT_ARM": (0.65, 0.75, 0.65), "LEFT_ARM": (0.65, 0.75, 0.65),
         "RIGHT_LEG": (0.75, 0.6667, 0.75), "LEFT_LEG": (0.75, 0.6667, 0.75)}
HUNCH = math.radians(12.0)
HIP = (0.0, 15.5, 0.0)

# workshop element -> (material, flag). Materials: flesh (tinted), bone, gear. Flags: when it's drawn (GoblinBody.Flag).
PICK = {
    "nose_cube": ("flesh", "ALWAYS"), "ear_base": ("flesh", "ALWAYS"), "ear_mid": ("flesh", "ALWAYS"),
    "ear_tip": ("flesh", "ALWAYS"), "ear_end": ("flesh", "ALWAYS"),
    "fist_R": ("flesh", "ALWAYS"), "fist_L": ("flesh", "ALWAYS"),
    "foot_R": ("flesh", "FEET"), "foot_L": ("flesh", "FEET"),
    "tusk_R": ("bone", "ALWAYS"), "tusk_L": ("bone", "ALWAYS"),
    "toenail_R1": ("bone", "FEET"), "toenail_R2": ("bone", "FEET"), "toenail_R3": ("bone", "FEET"),
    "toenail_L1": ("bone", "FEET"), "toenail_L2": ("bone", "FEET"), "toenail_L3": ("bone", "FEET"),
    "earring": ("gear", "EARRING"),
    "cap_band": ("gear", "CAP"), "cap_top": ("gear", "CAP"), "cap_tip_cube": ("gear", "CAP"), "cap_pom": ("gear", "CAP"),
    "belt": ("gear", "ALWAYS"), "belt_buckle": ("gear", "ALWAYS"), "pouch": ("gear", "ALWAYS"),
    "loincloth": ("gear", "LOIN"),
    "shin_wrap_R": ("gear", "WRAPS"), "shin_wrap_L": ("gear", "WRAPS"),
}
# the workshop's top bones -> ours (the head and arms hang off its torso; the loincloth hangs off nothing)
TOP = {"leg_R": "RIGHT_LEG", "leg_L": "LEFT_LEG", "torso": "BODY", "head": "HEAD", "arm_R": "RIGHT_ARM", "arm_L": "LEFT_ARM"}
# sub-bones whose turn is kept
KEEP_TURN = {"nose", "cap_tip"}

FLESH = (214, 214, 210)
IVORY = (236, 226, 196)
GEAR = {"cap_band": (128, 34, 30), "cap_top": (146, 40, 34), "cap_tip_cube": (146, 40, 34), "cap_pom": (206, 168, 64),
        "earring": (214, 178, 70), "belt": (78, 52, 34), "belt_buckle": (190, 154, 70), "pouch": (112, 78, 48),
        "loincloth": (96, 78, 54), "shin_wrap_R": (170, 154, 120), "shin_wrap_L": (170, 154, 120)}


def to_mc(p):
    """A Blockbench point (y up, the model's right at +x) in model space."""
    return (-p[0], 24.0 - p[1], p[2])


def mc_box(e):
    a, b = to_mc(e["from"]), to_mc(e["to"])
    lo = tuple(min(a[k], b[k]) for k in range(3))
    hi = tuple(max(a[k], b[k]) for k in range(3))
    return lo, hi


def mc_turn(r):
    """A Blockbench turn (degrees) in model space: x and y turn the other way, z the same."""
    return (-math.radians(r[0]), -math.radians(r[1]), math.radians(r[2]))


def parts(data):
    """[(bone, name, material, flag, sub pivot (or None), turn, lo, hi)] in model space."""
    elements = {e["uuid"]: e for e in data["elements"]}
    out = []

    def walk(node, bone, sub):
        if isinstance(node, str):
            e = elements[node]
            name = e["name"]
            lo, hi = mc_box(e)
            if name in ("ear_base", "ear_mid", "ear_tip", "ear_end"):
                name = name  # one name on both sides: the side is in the box's x
            if name in PICK:
                mat, flag = PICK[name]
                out.append((bone or "BODY", name, mat, flag, sub, lo, hi))
            return
        b = TOP.get(node["name"], bone)
        s = sub
        if node["name"] in KEEP_TURN:
            s = (to_mc(node["origin"]), mc_turn(node.get("rotation", [0, 0, 0])))
        for child in node.get("children", []):
            walk(child, b, s)

    for node in data["outliner"]:
        walk(node, None, None)
    return out


def snap(lo, hi):
    """A whole-pixel texture size for the box and the deformation that keeps its true size."""
    size = [hi[k] - lo[k] for k in range(3)]
    whole = [max(1, int(round(v))) for v in size]
    grow = [(size[k] - whole[k]) / 2.0 for k in range(3)]
    return whole, grow


def layout(items):
    """Shelf-packs each box's texture (2(w+d) wide, d+h tall) on the sheet."""
    x = y = shelf = 0
    places = []
    for it in items:
        (w, h, d), _ = snap(it[5], it[6])
        ww, hh = 2 * (w + d), d + h
        if x + ww > SHEET:
            x, y, shelf = 0, y + shelf + 1, 0
        places.append((x, y))
        x += ww + 1
        shelf = max(shelf, hh)
    if y + shelf > SHEET:
        raise SystemExit("goblin body parts don't fit the sheet")
    return places


def clamp(c):
    return tuple(max(0, min(255, int(v))) for v in c)


def shade(c, by):
    return clamp(tuple(v + by for v in c))


def paint(items, places, seed="goblin-body"):
    rnd = random.Random(seed)
    img = Image.new("RGBA", (SHEET, SHEET), (0, 0, 0, 0))
    for it, (u, v) in zip(items, places):
        name, mat = it[1], it[2]
        (w, h, d), _ = snap(it[5], it[6])
        ww, hh = 2 * (w + d), d + h
        for yy in range(v, v + hh):
            for xx in range(u, u + ww):
                edge = xx in (u, u + ww - 1) or yy in (v, v + hh - 1)
                if (yy - v) < d and not (u + d <= xx < u + d + 2 * w):
                    continue  # the corners of a box's sheet that no face uses
                if mat == "flesh":
                    c = shade(FLESH, rnd.randint(-14, 8) - (24 if edge else 0))
                    if rnd.random() < 0.1:
                        c = shade(c, -40)  # a wart, a pock
                    if name.startswith("ear_") and name != "ear_base" and (yy - v) >= d and rnd.random() < 0.35:
                        c = shade(c, -18)  # the thin ends of the ears, darker
                elif mat == "bone":
                    root = (yy - v) >= hh - 1 if name.startswith("tusk") else (yy - v) < d
                    c = shade(IVORY, rnd.randint(-10, 6) - (34 if root else 0))
                else:
                    base = GEAR[name]
                    c = shade(base, rnd.randint(-10, 8) - (18 if edge else 0))
                    if name.startswith("cap_band") and (yy - v) >= d and (xx - u) % 3 == 0:
                        c = shade(base, -26)  # the band's turned-up ribs
                    if name == "belt" and (yy - v) >= d and (xx - u) % 4 == 1:
                        c = shade(base, -20)  # stitching
                    if name.startswith("shin_wrap") and (yy - v) >= d and (xx + yy) % 3 == 0:
                        c = shade(base, -30)  # the wrap's crossings
                    if name == "loincloth" and (yy - v) == hh - 1 and (xx % 2):
                        continue  # a frayed hem
                img.putpixel((xx, yy), c + (255,))
    return img


def java(items, places):
    """GoblinBody.PARTS: one part per box, on its bone, relative to the bone's pivot (or the sub-bone's)."""
    rows = []
    for it, (u, v) in zip(items, places):
        bone, name, mat, flag, sub, lo, hi = it
        (w, h, d), (gx, gy, gz) = snap(lo, hi)
        piv = PIVOT[bone]
        if sub is not None:
            origin, turn = sub
            p = tuple(origin[k] - piv[k] for k in range(3))
            corner = tuple(lo[k] - origin[k] + (gx, gy, gz)[k] for k in range(3))
        else:
            p, turn = (0.0, 0.0, 0.0), (0.0, 0.0, 0.0)
            corner = tuple(lo[k] - piv[k] + (gx, gy, gz)[k] for k in range(3))
        f = lambda x: ("%.4f" % x).rstrip("0").rstrip(".").replace("-0", "-0") + "F" if abs(x) > 1e-6 else "0.0F"
        fix = lambda s: s if "." in s else s.replace("F", ".0F")
        g = lambda x: fix(f(x))
        rows.append('\t\tpart(Bone.%s, Flag.%s, %s, "%s", %s, %s, %s, %s, %s, %s, box(%d, %d, %s, %s, %s, %d, %d, %d, %s, %s, %s))' % (
            bone, flag, "true" if mat == "flesh" else "false", name, g(p[0]), g(p[1]), g(p[2]), g(turn[0]), g(turn[1]), g(turn[2]),
            u, v, g(corner[0]), g(corner[1]), g(corner[2]), w, h, d, g(gx), g(gy), g(gz)))
    return ",\n".join(rows)


# ---- preview: the goblin as drawn in game, MCA's body reshaped and our parts on it ----

# MCA's (a player's) boxes: bone, texture offset, corner, size, how far it's inflated over the skin
PLAYER = [("HEAD", 0, 0, (-4, -8, -4), (8, 8, 8), 0.0), ("HEAD", 32, 0, (-4, -8, -4), (8, 8, 8), 0.5),
          ("BODY", 16, 16, (-4, 0, -2), (8, 12, 4), 0.0), ("BODY", 16, 32, (-4, 0, -2), (8, 12, 4), 0.25),
          ("RIGHT_ARM", 40, 16, (-3, -2, -2), (4, 12, 4), 0.0), ("RIGHT_ARM", 40, 32, (-3, -2, -2), (4, 12, 4), 0.25),
          ("LEFT_ARM", 32, 48, (-1, -2, -2), (4, 12, 4), 0.0), ("LEFT_ARM", 48, 48, (-1, -2, -2), (4, 12, 4), 0.25),
          ("RIGHT_LEG", 0, 16, (-2, 0, -2), (4, 12, 4), 0.0), ("RIGHT_LEG", 0, 32, (-2, 0, -2), (4, 12, 4), 0.25),
          ("LEFT_LEG", 16, 48, (-2, 0, -2), (4, 12, 4), 0.0), ("LEFT_LEG", 0, 48, (-2, 0, -2), (4, 12, 4), 0.25)]


def rx(p, a, about):
    y, z = p[1] - about[1], p[2] - about[2]
    return (p[0], about[1] + y * math.cos(a) - z * math.sin(a), about[2] + y * math.sin(a) + z * math.cos(a))


def turn(p, t):
    """rotationZYX(z, y, x) on p, as ModelPart turns."""
    x, y, z = p
    cx, sx, cy, sy, cz, sz = math.cos(t[0]), math.sin(t[0]), math.cos(t[1]), math.sin(t[1]), math.cos(t[2]), math.sin(t[2])
    y, z = y * cx - z * sx, y * sx + z * cx
    x, z = x * cy + z * sy, -x * sy + z * cy
    x, y = x * cz - y * sz, x * sz + y * cz
    return (x, y, z)


def bone_world(bone, p, lean=True):
    """A point in bone space (relative to the bone's pivot) in model space, the hunch applied as GoblinBody poses it."""
    piv = PIVOT[bone]
    q = (piv[0] + p[0], piv[1] + p[1], piv[2] + p[2])
    if bone in ("RIGHT_LEG", "LEFT_LEG"):
        return q
    hp = rx(piv, HUNCH, HIP)
    if bone == "HEAD":
        return (hp[0] + p[0], hp[1] + p[1], hp[2] + p[2])
    a = HUNCH if bone == "BODY" else HUNCH * 0.5
    r = rx((p[0], p[1], p[2]), a, (0, 0, 0))
    return (hp[0] + r[0], hp[1] + r[1], hp[2] + r[2])


def faces(u, v, w, h, d):
    """A box's faces: name, texture rect, which corner of the box each texture corner is (lo/hi per axis)."""
    return [("front", (u + d, v + d, w, h)), ("back", (u + 2 * d + w, v + d, w, h)), ("right", (u, v + d, d, h)),
            ("left", (u + d + w, v + d, d, h)), ("top", (u + d, v, w, d))]


def corners(name, lo, hi):
    (x0, y0, z0), (x1, y1, z1) = lo, hi
    return {"front": [(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0)],
            "back": [(x1, y0, z1), (x0, y0, z1), (x0, y1, z1), (x1, y1, z1)],
            "right": [(x0, y0, z1), (x0, y0, z0), (x0, y1, z0), (x0, y1, z1)],
            "left": [(x1, y0, z0), (x1, y0, z1), (x1, y1, z1), (x1, y1, z0)],
            "top": [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)]}[name]


def quads_box(tex, u, v, size, lo, hi, place, tint=None):
    w, h, d = size
    out = []
    for name, (tu, tv, tw, th) in faces(u, v, w, h, d):
        cs = [place(c) for c in corners(name, lo, hi)]
        out.append((cs, tex, (tu, tv, tw, th), name, tint))
    return out


def render(quads, path, yaws=(0, 35, 90, 180), size=300, scale=8.0):
    sheet = Image.new("RGB", (size * len(yaws), size), (150, 170, 190))
    draw = ImageDraw.Draw(sheet)
    light = {"top": 1.0, "front": 0.85, "back": 0.85, "right": 0.7, "left": 0.7}
    for i, yaw in enumerate(yaws):
        a, pitch = math.radians(yaw), math.radians(12)

        def proj(p):
            x, y, z = p[0], p[1] - 12, p[2]
            x, z = x * math.cos(a) - z * math.sin(a), x * math.sin(a) + z * math.cos(a)
            y, z = y * math.cos(pitch) + z * math.sin(pitch), -y * math.sin(pitch) + z * math.cos(pitch)
            return (size * i + size / 2 - x * scale, size * 0.5 + y * scale, z)

        polys = []
        for cs, tex, (tu, tv, tw, th), name, tint in quads:
            P = [proj(c) for c in cs]
            T = tex.load()
            for a_ in range(tw):
                for b_ in range(th):
                    c = T[tu + a_, tv + b_]
                    if c[3] < 30:
                        continue
                    if tint:
                        c = tuple(c[k] * tint[k] // 255 for k in range(3)) + (c[3],)
                    s0, s1, t0, t1 = a_ / tw, (a_ + 1) / tw, b_ / th, (b_ + 1) / th

                    def at(s, t):
                        top = [P[0][j] + (P[1][j] - P[0][j]) * s for j in range(3)]
                        bot = [P[3][j] + (P[2][j] - P[3][j]) * s for j in range(3)]
                        return [top[j] + (bot[j] - top[j]) * t for j in range(3)]
                    q = [at(s0, t0), at(s1, t0), at(s1, t1), at(s0, t1)]
                    polys.append((sum(p[2] for p in q) / 4, [(p[0], p[1]) for p in q],
                                  tuple(int(c[k] * light[name]) for k in range(3))))
        polys.sort(key=lambda p: -p[0])
        for z, q, c in polys:
            draw.polygon(q, fill=c)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    sheet.save(path)
    print("preview", path)


def preview(items, places, body_tex, skin_dir, path):
    green = (118, 150, 70)
    skin = Image.new("RGBA", (64, 64), (214, 214, 210, 255))
    layers = [(skin, green)]
    for name in ("face", "clothes", "hair"):
        p = os.path.join(skin_dir, name + ".png")
        if os.path.exists(p):
            layers.append((Image.open(p).convert("RGBA"), (92, 62, 40) if name == "hair" else None))
    quads = []
    for layer_i, (tex, tint) in enumerate(layers):
        grow_extra = (0.0, 0.01, 0.0625, 0.125)[layer_i]
        for bone, u, v, corner, size, inflate in PLAYER:
            if layer_i == 0 and inflate > 0:
                continue  # the bare skin has no wear layer
            g = inflate + grow_extra
            lo = tuple(corner[k] - g for k in range(3))
            hi = tuple(corner[k] + size[k] + g for k in range(3))
            s = SCALE[bone]
            place = lambda c, b=bone, s=s: bone_world(b, (c[0] * s[0], c[1] * s[1], c[2] * s[2]))
            quads += quads_box(tex, u, v, size, lo, hi, place, tint)
    for it, (u, v) in zip(items, places):
        bone, name, mat, flag, sub, lo, hi = it
        (w, h, d), _ = snap(lo, hi)
        piv = PIVOT[bone]
        if sub is not None:
            origin, t = sub

            def place(c, bone=bone, origin=origin, t=t, piv=piv):
                r = turn((c[0] - origin[0], c[1] - origin[1], c[2] - origin[2]), t)
                return bone_world(bone, (origin[0] - piv[0] + r[0], origin[1] - piv[1] + r[1], origin[2] - piv[2] + r[2]))
        else:
            place = lambda c, bone=bone, piv=piv: bone_world(bone, (c[0] - piv[0], c[1] - piv[1], c[2] - piv[2]))
        quads += quads_box(body_tex, u, v, (w, h, d), lo, hi, place, green if mat == "flesh" else None)
    render(quads, path)


if __name__ == "__main__":
    data = json.load(open(BB))
    items = parts(data)
    places = layout(items)
    img = paint(items, places)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    img.save(OUT)
    print("drew", OUT, len(items), "boxes")
    if "--java" in sys.argv:
        print(java(items, places))
    if "--preview" in sys.argv:
        preview(items, places, img, sys.argv[sys.argv.index("--preview") + 1], "build/goblin-body-preview.png")
