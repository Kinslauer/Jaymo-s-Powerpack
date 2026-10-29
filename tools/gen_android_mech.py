"""The android mech: our own model and skins for the future's walking war machines (Chrono's Los Perrito future), and the
test unit the Mad Tinkerer wheels out before the android operation (ZFighterAndroid).

One frame, four builds. Each build shows its own pieces of the frame (the optional parts below) and wears its own paint:
  0 Warden     gunmetal and amber: twin arm cannons, exhaust stacks
  1 Bulwark    olive and hazard yellow: cannon and claw, a shield on the left forearm, a shell pod on the shoulder
  2 Strider    white and red, cyan glow: twin cannons, an antenna, jets on its back instead of stacks
  3 Reclaimer  rusted salvage, green glow: cannon and claw, a sensor dome, steel patches over the rust

This file is the model's source: it writes the part block of AndroidMechModel.java (between the "parts" markers),
packs every part's texture net into a 128x128 sheet, paints each build's skin and its _eyes glow, and writes a
Blockbench file per build (tools/blockbench/android_mech/) so the model can be opened and reworked there. With
--preview <dir> it also draws each build in 3D.

    python3 -I tools/gen_android_mech.py [--preview <dir>]
"""
import base64
import io
import json
import math
import os
import random
import re
import sys
import uuid

from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
JAVA = os.path.join(ROOT, "src", "client", "java", "com", "jaymospowers", "client", "power", "chrono", "AndroidMechModel.java")
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "jaymospowers", "textures", "entity", "future")
BB = os.path.join(ROOT, "tools", "blockbench", "android_mech")
SIZE = 128

BUILDS = ["warden", "bulwark", "strider", "reclaimer"]
W, B, S, R = 1, 2, 4, 8
ALL = W | B | S | R

# name, parent, box (x, y, z), size (w, h, d), pivot (x, y, z), builds that show it.
# Model space: y is down, the feet are at y 24, the mech faces -z, +x is its left.
PARTS = [
    ("pelvis", "root", (-5, -2, -3), (10, 5, 6), (0, 2, 0), ALL),
    ("torso", "root", (-4, -8, -3), (8, 8, 6), (0, 1, 0), ALL),
    ("chest", "torso", (-8, -20, -5), (16, 12, 10), (0, 0, 0), ALL),
    ("cockpit", "torso", (-5, -18, -6), (10, 5, 1), (0, 0, 0), ALL),
    ("core", "torso", (-2, -12, -6), (4, 3, 1), (0, 0, 0), ALL),
    ("backpack", "torso", (-6, -19, 5), (12, 10, 4), (0, 0, 0), ALL),
    ("exhaust_r", "torso", (-6, -25, 6), (3, 7, 3), (0, 0, 0), W | B | R),
    ("exhaust_l", "torso", (3, -25, 6), (3, 7, 3), (0, 0, 0), W | B | R),
    ("jets", "torso", (-5, -16, 9), (10, 6, 2), (0, 0, 0), S),
    ("shell_pod", "torso", (-13, -26, -2), (6, 6, 8), (0, 0, 0), B),
    ("head", "torso", (-3, -5, -4), (6, 5, 7), (0, -20, -1), ALL),
    ("antenna", "head", (2, -11, 1), (1, 6, 1), (0, 0, 0), S),
    ("dome", "head", (-2, -7, -3), (4, 2, 5), (0, 0, 0), R),
    ("right_arm", "root", (-4, 2, -2), (4, 8, 4), (-10, -14, 0), ALL),
    ("pauldron_r", "right_arm", (-5, -3, -5), (8, 6, 10), (0, 0, 0), ALL),
    ("forearm_r", "right_arm", (-3.5, 0, -3.5), (7, 9, 7), (-2, 10, 0), ALL),
    ("cannon_r", "forearm_r", (-2, 9, -2), (4, 6, 4), (0, 0, 0), ALL),
    ("left_arm", "root", (0, 2, -2), (4, 8, 4), (10, -14, 0), ALL),
    ("pauldron_l", "left_arm", (-3, -3, -5), (8, 6, 10), (0, 0, 0), ALL),
    ("forearm_l", "left_arm", (-3.5, 0, -3.5), (7, 9, 7), (2, 10, 0), ALL),
    ("cannon_l", "forearm_l", (-2, 9, -2), (4, 6, 4), (0, 0, 0), W | S),
    ("claw_a", "forearm_l", (-3, 9, -3), (2, 4, 2), (0, 0, 0), B | R),
    ("claw_b", "forearm_l", (1, 9, -3), (2, 4, 2), (0, 0, 0), B | R),
    ("claw_c", "forearm_l", (-1, 9, 1), (2, 4, 2), (0, 0, 0), B | R),
    ("shield", "forearm_l", (3.5, -2, -6), (2, 13, 12), (0, 0, 0), B),
    ("right_leg", "root", (-2.5, 0, -3), (5, 8, 6), (-4.5, 5, 0), ALL),
    ("knee_r", "right_leg", (-2, 6, -4.5), (4, 4, 2), (0, 0, 0), ALL),
    ("shin_r", "right_leg", (-2, 0, -2), (4, 8, 4), (0, 8, 0), ALL),
    ("foot_r", "shin_r", (-3, 7, -6), (6, 4, 9), (0, 0, 0), ALL),
    ("left_leg", "root", (-2.5, 0, -3), (5, 8, 6), (4.5, 5, 0), ALL),
    ("knee_l", "left_leg", (-2, 6, -4.5), (4, 4, 2), (0, 0, 0), ALL),
    ("shin_l", "left_leg", (-2, 0, -2), (4, 8, 4), (0, 8, 0), ALL),
    ("foot_l", "shin_l", (-3, 7, -6), (6, 4, 9), (0, 0, 0), ALL),
]

# plate, plate shadow, frame (joints, barrels), accent (stripes, decals), glow
PAINT = {
    "warden": ((96, 108, 122), (62, 70, 82), (38, 40, 46), (232, 142, 40), (255, 176, 64)),
    "bulwark": ((104, 112, 74), (66, 72, 46), (40, 41, 36), (222, 192, 52), (255, 64, 44)),
    "strider": ((214, 218, 224), (150, 158, 168), (48, 52, 60), (198, 40, 44), (84, 214, 255)),
    "reclaimer": ((146, 92, 60), (96, 60, 40), (54, 48, 44), (176, 172, 164), (140, 255, 96)),
}

# the bare frame under the plates: joints, the upper limbs and the barrels
FRAME = {"torso", "right_arm", "left_arm", "shin_r", "shin_l", "cannon_r", "cannon_l", "claw_a", "claw_b", "claw_c",
         "antenna", "exhaust_r", "exhaust_l", "jets"}


# ---------------------------------------------------------------- layout

def pack():
    """Every part's net (2(w+d) by d+h) on the sheet: shelves, tallest first."""
    order = sorted(PARTS, key=lambda p: (-(p[3][2] + p[3][1]), -(p[3][0] + p[3][2])))
    uv, x, y, shelf = {}, 0, 0, 0
    for name, _, _, (w, h, d), _, _ in order:
        nw, nh = 2 * (w + d), d + h
        if x + nw > SIZE:
            x, y, shelf = 0, y + shelf, 0
        uv[name] = (x, y)
        x += nw
        shelf = max(shelf, nh)
    assert y + shelf <= SIZE, "the mech's nets don't fit a %d sheet" % SIZE
    return uv


def faces(u, v, w, h, d):
    """The six faces of a net as (x, y, w, h). 'front' looks toward -z, where the mech faces."""
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
            "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}


def num(f):
    return ("%d" % f if f == int(f) else ("%s" % f)) + "F"


def write_java(uv):
    lines = []
    for name, parent, (x, y, z), (w, h, d), (px, py, pz), _ in PARTS:
        u, v = uv[name]
        var = "r" if parent == "root" else parent
        call = 'part(%s, "%s", %d, %d, %s, %s, %s, %d, %d, %d, %s, %s, %s);' % (
            var, name, u, v, num(x), num(y), num(z), w, h, d, num(px), num(py), num(pz))
        if any(p[1] == name for p in PARTS):
            call = "PartDefinition %s = %s" % (name, call)
        lines.append("\t\t" + call)
    masks = []
    for name, _, _, _, _, mask in PARTS:
        if mask != ALL:
            masks.append('\t\t\tMap.entry("%s", %d)' % (name, mask))
    with open(JAVA, encoding="utf-8") as f:
        src = f.read()
    src = re.sub(r"(// parts:start\n).*?(\t\t// parts:end)", lambda m: m.group(1) + "\n".join(lines) + "\n" + m.group(2), src,
                 flags=re.S)
    src = re.sub(r"(// builds:start\n).*?(\t\t// builds:end)", lambda m: m.group(1) + ",\n".join(masks) + "\n" + m.group(2), src,
                 flags=re.S)
    with open(JAVA, "w", encoding="utf-8", newline="\n") as f:
        f.write(src)


# ---------------------------------------------------------------- painting

def shade(c, k):
    return tuple(max(0, min(255, int(round(x * k)))) for x in c[:3])


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


LIGHT = {"top": 1.08, "front": 1.0, "left": 0.86, "right": 0.86, "back": 0.9, "bottom": 0.68}


def paint(build, uv):
    plate, shadow, frame, accent, glow = PAINT[build]
    rng = random.Random("android-mech:" + build)
    skin = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    eyes = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    sp, ep = skin.load(), eyes.load()
    steel = (172, 176, 182)
    rust = build == "reclaimer"
    # where the reclaimer was patched with bare steel (by part, a few rectangles of its front and sides)
    patches = {}

    def put(fx, fy, i, j, c):
        sp[fx + i, fy + j] = c[:3] + (255,)

    def lit(fx, fy, i, j, c):
        ep[fx + i, fy + j] = c[:3] + (255,)

    for name, _, _, (w, h, d), _, _ in PARTS:
        u, v = uv[name]
        base = frame if name in FRAME else plate
        for face, (fx, fy, fw, fh) in faces(u, v, w, h, d).items():
            k = LIGHT[face]
            big = fw >= 5 and fh >= 5
            if rust and big and name not in FRAME and rng.random() < 0.5:
                pw, ph = rng.randint(2, max(2, fw // 2)), rng.randint(2, max(2, fh // 2))
                patches[(name, face)] = (rng.randint(1, fw - pw - 1), rng.randint(1, fh - ph - 1), pw, ph)
            for i in range(fw):
                for j in range(fh):
                    c = shade(base, k * (0.94 + rng.random() * 0.1))
                    edge = big and (i in (0, fw - 1) or j in (0, fh - 1))
                    if edge and name not in FRAME:
                        c = shade(shadow, k)                         # panel seam
                    if rust and name not in FRAME and rng.random() < 0.12:
                        c = shade(mix(base, (70, 40, 28), 0.5), k)   # pitting
                    if (name, face) in patches:
                        px, py, pw, ph = patches[(name, face)]
                        if px <= i < px + pw and py <= j < py + ph:
                            c = shade(steel, k * (0.95 + rng.random() * 0.08))
                            if (i, j) in ((px, py), (px + pw - 1, py), (px, py + ph - 1), (px + pw - 1, py + ph - 1)):
                                c = shade(steel, k * 0.6)            # weld spot
                    put(fx, fy, i, j, c)
            # rivets in the corners of big plates
            if big and name not in FRAME and face != "bottom":
                for (i, j) in ((1, 1), (fw - 2, 1), (1, fh - 2), (fw - 2, fh - 2)):
                    put(fx, fy, i, j, shade(plate, k * 1.22))
            detail(name, face, fx, fy, fw, fh, k, put, lit, PAINT[build], build, rng)
    return skin, eyes


def detail(name, face, fx, fy, fw, fh, k, put, lit, paint_, build, rng):
    plate, shadow, frame, accent, glow = paint_
    dark = (20, 22, 26)

    def stripe(row, c=accent):
        for i in range(1, fw - 1):
            put(fx, fy, i, row, shade(c, k))

    def hazard():
        for i in range(fw):
            for j in range(fh):
                if (i + j) // 2 % 2 == 0 and 0 < i < fw - 1 and 0 < j < fh - 1:
                    put(fx, fy, i, j, shade((24, 24, 22), k))
                elif 0 < i < fw - 1 and 0 < j < fh - 1:
                    put(fx, fy, i, j, shade(accent, k))

    if name == "cockpit" and face == "front":
        for i in range(fw):
            for j in range(fh):
                frame_ = i in (0, fw - 1) or j in (0, fh - 1)
                if frame_:
                    put(fx, fy, i, j, shade(frame, 1.0))
                else:
                    t = j / max(1, fh - 1)
                    put(fx, fy, i, j, mix(shade(glow, 0.55), shade(glow, 0.2), t))
                    lit(fx, fy, i, j, shade(glow, 0.9 - 0.4 * t) + (255,))
        # the glint across the glass
        for i in range(2, 5):
            put(fx, fy, i, 1, mix(glow, (255, 255, 255), 0.6))
            lit(fx, fy, i, 1, mix(glow, (255, 255, 255), 0.6))
    elif name == "core" and face == "front":
        for i in range(fw):
            for j in range(fh):
                c = (255, 255, 255) if (i, j) in ((1, 1), (2, 1)) else glow
                put(fx, fy, i, j, c)
                lit(fx, fy, i, j, c)
    elif name == "head" and face == "front":
        for i in range(fw):
            put(fx, fy, i, 2, dark)
        for i in (1, 4) if build != "strider" else (1, 2, 3, 4):
            put(fx, fy, i, 2, glow)
            lit(fx, fy, i, 2, glow)
        put(fx, fy, 2, 4, shade(frame, 1.0))
        put(fx, fy, 3, 4, shade(frame, 1.0))
    elif name == "dome" and face in ("front", "top"):
        for i in range(1, fw - 1):
            put(fx, fy, i, fh // 2, glow)
            lit(fx, fy, i, fh // 2, glow)
    elif name == "antenna" and face in ("front", "back", "left", "right"):
        put(fx, fy, 0, 0, glow)
        lit(fx, fy, 0, 0, glow)
    elif name == "chest":
        if face == "front":
            stripe(fh - 3)
            stripe(fh - 4, shade(accent, 0.8))
            # the unit's number, small on its left breast
            for (i, j) in ((11, 7), (12, 7), (13, 7), (13, 8), (12, 9), (11, 10), (11, 11), (12, 11), (13, 11)):
                put(fx, fy, i, j, shade((236, 236, 230), k))
        elif face in ("left", "right"):
            for j in range(3, fh - 3, 2):
                for i in range(2, fw - 2):
                    put(fx, fy, i, j, shade(frame, k))       # cooling slats
        elif face == "top":
            for i in range(fw):
                put(fx, fy, i, fh - 1, shade(accent, k))
    elif name == "backpack" and face == "back":
        for j in range(2, fh - 2, 2):
            for i in range(2, fw - 2):
                put(fx, fy, i, j, shade(frame, k))
        if build == "strider":
            for i in range(3, fw - 3):
                lit(fx, fy, i, 2, shade(glow, 0.6))
    elif name in ("exhaust_r", "exhaust_l"):
        if face == "top":
            for i in range(fw):
                for j in range(fh):
                    put(fx, fy, i, j, dark if 0 < i < fw - 1 or 0 < j < fh - 1 else shade(frame, 1.1))
            put(fx, fy, 1, 1, (10, 10, 12))
        elif face != "bottom":
            put(fx, fy, 0, 1, shade(accent, k))
            for i in range(fw):
                put(fx, fy, i, 0, shade(frame, k * 1.25))
                put(fx, fy, i, 1, shade((70, 54, 40), k))     # soot
    elif name == "jets" and face == "back":
        for (cx, cy) in ((2, 2), (6, 2)):
            for i in range(2):
                for j in range(2):
                    put(fx, fy, cx + i, cy + j, glow)
                    lit(fx, fy, cx + i, cy + j, glow)
            for (i, j) in ((-1, 0), (-1, 1), (2, 0), (2, 1), (0, -1), (1, -1), (0, 2), (1, 2)):
                put(fx, fy, cx + i, cy + j, dark)
    elif name in ("cannon_r", "cannon_l"):
        if face == "bottom":
            # the muzzle end: a ring of four bores
            for i in range(fw):
                for j in range(fh):
                    put(fx, fy, i, j, shade(frame, 0.8))
            for (i, j) in ((1, 1), (2, 2), (1, 2), (2, 1)):
                put(fx, fy, i, j, dark)
            for (i, j) in ((0, 0), (3, 0), (0, 3), (3, 3)):
                put(fx, fy, i, j, (8, 8, 10))
        elif face != "top":
            for j in range(fh):
                put(fx, fy, 1, j, shade(frame, k * 1.4))      # barrels catching the light
            for i in range(fw):
                put(fx, fy, i, 0, shade(plate, k))             # the clamp
                put(fx, fy, i, fh - 1, shade(frame, k * 0.7))
    elif name in ("claw_a", "claw_b", "claw_c") and face != "top":
        put(fx, fy, 0, fh - 1, shade(steel_of(build), k))
        put(fx, fy, fw - 1, fh - 1, shade(steel_of(build), k))
    elif name == "shield":
        if face in ("left", "front", "right"):
            hazard() if face == "left" else None
    elif name in ("pauldron_r", "pauldron_l"):
        if face == "top":
            if build == "bulwark":
                hazard()
            else:
                for j in range(1, fh - 1):
                    put(fx, fy, fw // 2, j, shade(accent, k))
                    put(fx, fy, fw // 2 - 1, j, shade(accent, k))
        elif face in ("front",):
            stripe(1)
        side = "right" if name.endswith("_r") else "left"
        if face == side:
            # the outer face: a chevron
            for t in range(0, min(fw, fh) - 2):
                i, j = 1 + t, 1 + t // 2 + 1
                if 0 < i < fw - 1 and j < fh - 1:
                    put(fx, fy, i, j, shade(accent, k))
    elif name == "shell_pod":
        if face == "front":
            for (cx, cy) in ((1, 1), (3, 1), (1, 3), (3, 3)):
                for i in range(2):
                    for j in range(2):
                        put(fx, fy, cx + i - (1 if cx == 3 else 0), cy + j - (1 if cy == 3 else 0), dark)
                put(fx, fy, cx - (1 if cx == 3 else 0), cy - (1 if cy == 3 else 0), shade(accent, 0.9))
        elif face in ("left", "right"):
            stripe(fh // 2)
    elif name in ("knee_r", "knee_l") and face == "front":
        put(fx, fy, fw // 2 - 1, 1, glow)
        lit(fx, fy, fw // 2 - 1, 1, glow)
        put(fx, fy, fw // 2, 1, glow)
        lit(fx, fy, fw // 2, 1, glow)
    elif name in ("foot_r", "foot_l"):
        if face == "bottom":
            for j in range(0, fh, 2):
                for i in range(fw):
                    put(fx, fy, i, j, dark)
        elif face == "front":
            for i in range(1, fw - 1):
                put(fx, fy, i, 0, shade(accent, k))
        elif face in ("left", "right"):
            for i in range(fw):
                put(fx, fy, i, fh - 1, shade(frame, k))       # the sole
    elif name in ("forearm_r", "forearm_l") and face == "front":
        stripe(2)
        stripe(3, shade(accent, 0.8))
    elif name in ("right_leg", "left_leg") and face in ("front",):
        for j in range(2, fh - 1, 2):
            put(fx, fy, fw // 2, j, shade(frame, k))
    elif name == "pelvis" and face == "front":
        for i in range(fw):
            put(fx, fy, i, fh - 2, shade(frame, k))


def steel_of(build):
    return (200, 204, 210) if build != "reclaimer" else (150, 150, 146)


def save(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


# ---------------------------------------------------------------- Blockbench

def bbmodel(build, uv, skin):
    """A Blockbench (Modded Entity) file of this build: its parts as bones, each box with its box-UV offset."""
    mask = 1 << BUILDS.index(build)
    buf = io.BytesIO()
    skin.save(buf, "PNG")
    data = "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()
    ids = {}
    absolute = {}

    def pivot_of(name):
        if name == "root":
            return (0.0, 0.0, 0.0)
        if name not in absolute:
            p = next(p for p in PARTS if p[0] == name)
            par = pivot_of(p[1])
            absolute[name] = tuple(par[i] + p[4][i] for i in range(3))
        return absolute[name]

    def bb(x, y, z):
        # Blockbench's entity space: y up from the feet, x mirrored
        return [-x, 24.0 - y, z]

    elements, groups = [], {}
    for name, parent, (x, y, z), (w, h, d), _, show in PARTS:
        if not show & mask:
            continue
        o = pivot_of(name)
        a = bb(o[0] + x, o[1] + y, o[2] + z)
        b_ = bb(o[0] + x + w, o[1] + y + h, o[2] + z + d)
        frm = [min(a[i], b_[i]) for i in range(3)]
        to = [max(a[i], b_[i]) for i in range(3)]
        u, v = uv[name]
        eid = str(uuid.uuid5(uuid.NAMESPACE_URL, "mech:%s:%s:box" % (build, name)))
        elements.append({
            "name": name, "box_uv": True, "rescale": False, "locked": False, "from": frm, "to": to, "autouv": 0,
            "color": PARTS.index(next(p for p in PARTS if p[0] == name)) % 8, "origin": bb(*o), "uv_offset": [u, v],
            "faces": {
                "north": {"uv": [u + d, v + d, u + d + w, v + d + h], "texture": 0},
                "east": {"uv": [u, v + d, u + d, v + d + h], "texture": 0},
                "south": {"uv": [u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h], "texture": 0},
                "west": {"uv": [u + d + w, v + d, u + 2 * d + w, v + d + h], "texture": 0},
                "up": {"uv": [u + d + w, v + d, u + d, v], "texture": 0},
                "down": {"uv": [u + d + w, v, u + d + 2 * w, v + d], "texture": 0},
            },
            "type": "cube", "uuid": eid,
        })
        ids[name] = eid
        groups[name] = {"name": name, "origin": bb(*o), "rotation": [0, 0, 0], "color": 0, "isOpen": True, "export": True,
                        "visibility": True, "mirror_uv": False, "locked": False,
                        "uuid": str(uuid.uuid5(uuid.NAMESPACE_URL, "mech:%s:%s" % (build, name))), "children": [eid],
                        "parent": parent}
    outliner = []
    for name, g in groups.items():
        parent = g.pop("parent")
        (groups[parent]["children"] if parent in groups else outliner).append(g)
    model = {
        "meta": {"format_version": "4.10", "model_format": "modded_entity", "box_uv": True},
        "name": "android_mech_" + build, "model_identifier": "android_mech", "modded_entity_version": "Fabric 1.17+",
        "visible_box": [2, 3, 0], "resolution": {"width": SIZE, "height": SIZE},
        "elements": elements, "outliner": outliner,
        "textures": [{"path": "", "name": "android_mech_%s.png" % build, "folder": "", "namespace": "", "id": "0",
                      "width": SIZE, "height": SIZE, "uv_width": SIZE, "uv_height": SIZE, "particle": False,
                      "render_mode": "default", "visible": True, "mode": "bitmap", "saved": True,
                      "uuid": str(uuid.uuid5(uuid.NAMESPACE_URL, "mech:%s:skin" % build)), "source": data}],
    }
    os.makedirs(BB, exist_ok=True)
    with open(os.path.join(BB, "android_mech_%s.bbmodel" % build), "w", encoding="utf-8", newline="\n") as f:
        json.dump(model, f, indent=1)


# ---------------------------------------------------------------- 3D preview

def rot(rx, ry, rz):
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    Rx = ((1, 0, 0), (0, cx, -sx), (0, sx, cx))
    Ry = ((cy, 0, sy), (0, 1, 0), (-sy, 0, cy))
    Rz = ((cz, -sz, 0), (sz, cz, 0), (0, 0, 1))
    return mul(Rz, mul(Ry, Rx))


def mul(a, b):
    return tuple(tuple(sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)) for i in range(3))


def app(m, p):
    return tuple(sum(m[i][k] * p[k] for k in range(3)) for i in range(3))


def pose_of(pose):
    """Rotations (x, y, z radians) per part: as AndroidMechModel poses them."""
    r = {}
    if pose == "aim":
        r["right_arm"] = (-1.45, 0, 0)
        r["left_arm"] = (-1.45, 0, 0)
    elif pose == "walk":
        r["right_leg"] = (-0.5, 0, 0)
        r["left_leg"] = (0.45, 0, 0)
        r["shin_l"] = (0.5, 0, 0)
        r["right_arm"] = (0.25, 0, 0)
        r["left_arm"] = (-0.25, 0, 0)
    elif pose == "smash":
        r["right_arm"] = (-2.0, 0, 0)
        r["left_arm"] = (-2.0, 0, 0)
    return r


def scene(build, skin, eyes, pose, yaw, pitch, scale, glow_view):
    mask = 1 << BUILDS.index(build)
    uv = pack()
    sp, ep = skin.load(), eyes.load()
    rots = pose_of(pose)
    if pose == "aim" and not (PARTS[[p[0] for p in PARTS].index("cannon_l")][5] & mask):
        rots["left_arm"] = (0, 0, 0)
    mats = {"root": (((1, 0, 0), (0, 1, 0), (0, 0, 1)), (0.0, 0.0, 0.0))}

    def mat(name):
        if name not in mats:
            p = next(p for p in PARTS if p[0] == name)
            pm, pt = mat(p[1])
            local = rot(*rots.get(name, (0, 0, 0)))
            mats[name] = (mul(pm, local), tuple(pt[i] + app(pm, p[4])[i] for i in range(3)))
        return mats[name]

    view = mul(rot(pitch, 0, 0), rot(0, yaw, 0))
    quads = []
    for name, _, (x, y, z), (w, h, d), _, show in PARTS:
        if not show & mask:
            continue
        m, t = mat(name)
        u, v = uv[name]
        fs = faces(u, v, w, h, d)

        def world(p):
            q = app(m, p)
            q = (q[0] + t[0], q[1] + t[1], q[2] + t[2])
            return app(view, q)

        # each face: its texel grid as (origin, step along columns, step along rows) in the part's space
        corners = {
            "front": ((x, y, z), (1, 0, 0), (0, 1, 0)),
            "back": ((x + w, y, z + d), (-1, 0, 0), (0, 1, 0)),
            "right": ((x, y, z + d), (0, 0, -1), (0, 1, 0)),
            "left": ((x + w, y, z), (0, 0, 1), (0, 1, 0)),
            "top": ((x, y, z + d), (1, 0, 0), (0, 0, -1)),
            "bottom": ((x, y + h, z), (1, 0, 0), (0, 0, 1)),
        }
        normals = {"front": (0, 0, -1), "back": (0, 0, 1), "right": (-1, 0, 0), "left": (1, 0, 0), "top": (0, -1, 0),
                   "bottom": (0, 1, 0)}
        for face, (fx, fy, fw, fh) in fs.items():
            n = app(view, app(m, normals[face]))
            if n[2] > 0:   # facing away from the camera (which looks toward +z after the view turn)
                continue
            o, cu, cv = corners[face]
            for i in range(fw):
                for j in range(fh):
                    c = sp[fx + i, fy + j]
                    if c[3] == 0:
                        continue
                    if glow_view:
                        e = ep[fx + i, fy + j]
                        c = e if e[3] else shade(c, 0.18) + (255,)
                    p0 = tuple(o[a] + cu[a] * i + cv[a] * j for a in range(3))
                    pts = [world(p0), world(tuple(p0[a] + cu[a] for a in range(3))),
                           world(tuple(p0[a] + cu[a] + cv[a] for a in range(3))), world(tuple(p0[a] + cv[a] for a in range(3)))]
                    depth = sum(p[2] for p in pts) / 4.0
                    quads.append((depth, pts, c[:3]))
    quads.sort(key=lambda q: -q[0])
    return quads


def draw(quads, size, scale, centre):
    img = Image.new("RGB", size, (30, 33, 40))
    g = ImageDraw.Draw(img)
    cx, cy = centre
    for _, pts, c in quads:
        # +x (the mech's left) must land on the viewer's right when it faces us
        g.polygon([(cx + p[0] * scale, cy + p[1] * scale) for p in pts], fill=c)
    return img


def preview(out):
    from PIL import ImageFont
    os.makedirs(out, exist_ok=True)
    skins = {b: (Image.open(os.path.join(OUT, "android_mech_%s.png" % b)).convert("RGBA"),
                 Image.open(os.path.join(OUT, "android_mech_%s_eyes.png" % b)).convert("RGBA")) for b in BUILDS}
    font = ImageFont.load_default()
    tile, scale = (300, 380), 5.0
    names = {"warden": "Warden", "bulwark": "Bulwark", "strider": "Strider", "reclaimer": "Reclaimer"}

    def sheet(views, label_of, path):
        img = Image.new("RGB", (tile[0] * len(BUILDS), tile[1] * len(views)), (30, 33, 40))
        for row, (pose, yaw, pitch, glow) in enumerate(views):
            for col, b in enumerate(BUILDS):
                q = scene(b, *skins[b], pose, yaw, pitch, scale, glow)
                t = draw(q, tile, scale, (tile[0] / 2, tile[1] / 2 + 10))
                ImageDraw.Draw(t).text((10, 8), label_of(b, pose), fill=(230, 230, 230), font=font)
                img.paste(t, (col * tile[0], row * tile[1]))
        img.save(path)

    # yaw 0 shows its front, pi its back; a positive pitch looks down on it
    sheet([("stand", 0.6, 0.3, False), ("stand", math.pi + 0.6, 0.3, False)],
          lambda b, p: names[b], os.path.join(out, "android-mechs-front-back.png"))
    sheet([("aim", 1.0, 0.25, False), ("walk", 1.4, 0.2, False), ("stand", 0.5, 0.3, True)],
          lambda b, p: "%s: %s" % (names[b], {"aim": "cannons up", "walk": "striding", "stand": "in the dark"}[p]),
          os.path.join(out, "android-mechs-poses.png"))


def main():
    uv = pack()
    write_java(uv)
    for b in BUILDS:
        skin, eyes = paint(b, uv)
        save(skin, os.path.join(OUT, "android_mech_%s.png" % b))
        save(eyes, os.path.join(OUT, "android_mech_%s_eyes.png" % b))
        bbmodel(b, uv, skin)
    if "--preview" in sys.argv:
        preview(sys.argv[sys.argv.index("--preview") + 1])


if __name__ == "__main__":
    main()
