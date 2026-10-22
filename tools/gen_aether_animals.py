"""Writes the art and data of Aether II's animals (docs/aether.md, "Aether II animals"): the skins of the kirrid, burrukai,
taegore, skyroot lizard, prismallard, glitterwing, shroudwing and aerwhale (painted here, vanilla style, our own), the food,
material and spawn egg items, the moa egg block's models and textures, loot tables, cooking recipes and the names in
lang/parts/aether_animals.json.

    python -I tools/gen_aether_mobs.py     (the older animals' skins, the sheepuff's sixteen colours, their loot)
    python -I tools/gen_aether_animals.py

Needs only Pillow. Like gen_aether_mobs.py it READS the `part(...)` lines of the models in
src/client/java/com/jaymospowers/client/aether/mob/*Model.java, so rerun it after editing a model. It borrows the painting
and file helpers from gen_aether_mobs.py. Every texture is seeded from its own name, so a rerun changes nothing.
"""
import json
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_aether_mobs as M  # noqa: E402
from PIL import Image  # noqa: E402

hx, ramp, shade, write, save = M.hx, M.ramp, M.shade, M.write, M.save
Skin, parts_of, faces = M.Skin, M.parts_of, M.faces
ASSETS, DATA, NS, SUB = M.ASSETS, M.DATA, M.NS, M.SUB
SKINS = os.path.join(ASSETS, "textures", "entity", SUB)
DARK = M.DARK
WHITE = M.WHITE

ZONES = ("highfields", "magnetic", "arctic")


def lighter(r, f=1.12):
    return [shade(c, f) for c in r]


def darker(r, f=0.8):
    return [shade(c, f) for c in r]


def hooves(sk, names, color, rows=2):
    for n in names:
        for face in ("front", "back", "left", "right"):
            x0, y0, w, h = faces(sk.parts[n])[face]
            sk.rect(n, face, 0, h - rows, w, rows, color)


def eyes(sk, name, y, color=DARK, glint=None):
    """Eyes on the two side faces of a head, near its front edge (the right face's right edge, the left face's left edge)."""
    x0, y0, w, h = faces(sk.parts[name])["right"]
    sk.at(name, "right", w - 2, y, color)
    sk.at(name, "left", 1, y, color)
    if glint:
        sk.at(name, "right", w - 3, y, glint)
        sk.at(name, "left", 2, y, glint)


# ---------------------------------------------------------------- kirrid

KIRRID_ZONE = {
    "highfields": dict(skin=ramp("b08a62", "9c7852", "c29c74", "866444"), plate=ramp("e8dcc0", "d6c8a4", "f2e8d0"), horn=None, mark=hx("7a5a40")),
    "magnetic": dict(skin=ramp("7f93a8", "6d8196", "93a8bc", "5b6f84"), plate=ramp("a8c4d8", "94b0c6", "bcd4e4"), horn=None, mark=hx("3fb8c0")),
    "arctic": dict(skin=ramp("c8d6e4", "b4c4d6", "dce6f0", "9fb0c4"), plate=ramp("eef4fa", "dbe6f0", "ffffff"),
                   horn=ramp("e4eef8", "cbdaea", "f6fbff"), mark=hx("8ea2b8")),
}
# the dye colours (names as Wool.NAMES) and the kirrid's own bare cloudwool
DYE = {"white": "e9ecec", "orange": "f07613", "magenta": "bd44b3", "light_blue": "3aafd9", "yellow": "f8c527", "lime": "70b919",
       "pink": "ed8dac", "gray": "3e4447", "light_gray": "8e8e86", "cyan": "158991", "purple": "792aac", "blue": "35399d",
       "brown": "724728", "green": "546d1b", "red": "a12722", "black": "141519"}
WOOL_NAMES = ["white", "light_blue", "cyan", "lime", "pink", "purple", "light_gray", "gray", "yellow", "brown", "magenta", "orange", "red", "green", "blue", "black"]
CLOUD = ramp("f6f9fc", "e6edf5", "ffffff", "d6e0ec")


def wool_ramp(name):
    if name == "undyed":
        return CLOUD
    base = hx(DYE[name])
    if name in ("white",):
        return ramp("f0f2f2", "e2e6e6", "fcfdfd", "d0d6d6")
    return [shade(base, 1.0), shade(base, 0.9), shade(base, 1.12), shade(base, 0.78)]


def kirrid(zone, wool):
    z = KIRRID_ZONE[zone]
    sk = Skin("kirrid_%s_%s" % (zone, wool), (128, 80), parts_of("KirridModel.java"))
    w = wool_ramp(wool)
    for n in ("body", "neck", "head"):
        sk.box(n, z["skin"])
    sk.speckle("body", [z["skin"][2], z["skin"][3]], 12)
    for n in ("leg_fr", "leg_fl", "leg_br", "leg_bl"):
        sk.box(n, darker(z["skin"], 0.9))
    hooves(sk, ("leg_fr", "leg_fl", "leg_br", "leg_bl"), hx("3a2c22"), 2)
    sk.box("plate", z["plate"])
    sk.rect("plate", "front", 3, 0, 2, 11, z["plate"][1])
    sk.rect("plate", "front", 0, 5, 8, 1, z["plate"][1])
    sk.outline("plate", ["front"], shade(z["plate"][1], 0.8))
    for n in ("horn_l", "horn_r"):
        sk.box(n, z["horn"] or z["plate"])
    for n in ("wool", "tuft", "tail"):
        sk.box(n, w, weights=(4, 3, 3, 2))
        sk.patches(n, w[2], 6, (1, 3))
        sk.patches(n, w[3], 4, (1, 2), chance=0.7)
    # the wool's loops: short darker arcs across the broad faces
    for face in ("left", "right", "top"):
        x0, y0, fw, fh = faces(sk.parts["wool"])[face]
        for _ in range(10):
            x, y = sk.rng.randrange(fw - 1), sk.rng.randrange(fh)
            sk.at("wool", face, x, y, w[3])
            sk.at("wool", face, x + 1, y, w[3])
    sk.rect("head", "front", 0, 0, 6, 6, z["skin"][1])
    eyes(sk, "head", 2, DARK, hx("f2e8d0"))
    return sk


# ---------------------------------------------------------------- burrukai

BURRUKAI_ZONE = {
    "highfields": dict(hide=ramp("7a5a3c", "6a4c32", "8c6a48", "58402a"), plate=ramp("8a8f7a", "767b68", "a0a58e", "60655a"),
                       antler=ramp("e0d4b4", "c9bc9a", "efe6cc"), glow=None),
    "magnetic": dict(hide=ramp("55697e", "485a6e", "677c92", "3c4c5e"), plate=ramp("3f8f94", "347a7e", "58a8ac", "2b6468"),
                     antler=ramp("a890e0", "8e76cc", "c4b0f4"), glow=hx("7fe8f0")),
    "arctic": dict(hide=ramp("d6dee6", "c2ccd8", "e8eef4", "aeb9c8"), plate=ramp("9ec4e0", "86b0d0", "b8d8f0", "6e98b8"),
                   antler=ramp("eaf4fc", "cfe2f2", "ffffff"), glow=None),
}


def burrukai(zone):
    z = BURRUKAI_ZONE[zone]
    sk = Skin("burrukai_" + zone, (128, 80), parts_of("BurrukaiModel.java"))
    for n in ("body", "hump", "head", "tail"):
        sk.box(n, z["hide"])
    sk.speckle("body", [z["hide"][2], z["hide"][3]], 20)
    sk.speckle("hump", [z["hide"][2], z["hide"][3]], 8)
    # the plates: overlapping scutes in rows
    sk.box("plate", z["plate"])
    for face in ("top", "left", "right"):
        x0, y0, fw, fh = faces(sk.parts["plate"])[face]
        for x in range(0, fw, 3):
            for y in range(0, fh):
                sk.at("plate", face, x, y, z["plate"][3])
    sk.outline("plate", ["top"], z["plate"][3])
    for n in ("antler_l", "antler_r"):
        sk.box(n, z["antler"])
        sk.rect(n, "front", 0, 0, 1, 10, z["antler"][1])
    sk.box("leg_fr", z["hide"])
    for n in ("leg_fr", "leg_fl", "leg_br", "leg_bl"):
        sk.box(n, darker(z["hide"], 0.92))
    # leg plates on the forelegs
    for n in ("leg_fr", "leg_fl"):
        for face in ("front", "back", "left", "right"):
            x0, y0, fw, fh = faces(sk.parts[n])[face]
            sk.rect(n, face, 0, 0, fw, 6, z["plate"][0])
            sk.rect(n, face, 0, 5, fw, 1, z["plate"][3])
    hooves(sk, ("leg_fr", "leg_fl", "leg_br", "leg_bl"), hx("2c2622"), 2)
    sk.rect("head", "front", 0, 5, 6, 3, shade(z["hide"][0], 1.2))
    sk.rect("head", "front", 2, 6, 1, 1, hx("3a2c22"))
    sk.rect("head", "front", 3, 6, 1, 1, hx("3a2c22"))
    eyes(sk, "head", 2, hx("201810"), z["glow"] or hx("e8d8a8"))
    sk.rect("tail", "front", 0, 6, 2, 3, z["hide"][3])
    return sk


# ---------------------------------------------------------------- taegore

TAEGORE_ZONE = {
    "highfields": dict(skin=ramp("6e6a3c", "5e5a30", "80804a", "4c4826"), plate=ramp("a89870", "8e8058", "c0b088"), nose=hx("4a3a30")),
    "magnetic": dict(skin=ramp("6a5a80", "5a4a70", "7e6e96", "4a3c5c"), plate=ramp("8a8aa8", "757594", "a0a0bc"), nose=hx("30283a")),
    "arctic": dict(skin=ramp("b8c8d8", "a4b6c8", "cddae6", "8ea2b8"), plate=ramp("e4ecf4", "ccd8e4", "f8fcff"), nose=hx("5a6678")),
}


def taegore(zone):
    z = TAEGORE_ZONE[zone]
    sk = Skin("taegore_" + zone, (128, 80), parts_of("TaegoreModel.java"))
    for n in ("body", "head", "snout", "tail", "ear_l", "ear_r"):
        sk.box(n, z["skin"])
    sk.speckle("body", [z["skin"][2], z["skin"][3]], 24)
    sk.box("spine", z["plate"])
    sk.box("crest", z["plate"])
    for n in ("plate_l", "plate_r"):
        sk.box(n, z["plate"])
        sk.outline(n, ["left", "right"], z["plate"][1])
    sk.rect("tail", "front", 0, 0, 8, 10, z["plate"][0])
    sk.outline("tail", ["front", "back"], z["plate"][1])
    for n in ("leg_fr", "leg_fl", "leg_br", "leg_bl"):
        sk.box(n, darker(z["skin"], 0.88))
    # pale claws
    hooves(sk, ("leg_fr", "leg_fl", "leg_br", "leg_bl"), z["plate"][2], 2)
    sk.rect("snout", "front", 1, 1, 3, 2, z["nose"])
    sk.rect("snout", "front", 1, 3, 3, 1, z["skin"][3])
    eyes(sk, "head", 2, hx("201810"), hx("f0e0b0"))
    return sk


# ---------------------------------------------------------------- skyroot lizard

LIZARD = {
    "skyroot": ("7fb55a", "c8e890"), "amberoot": ("d8a03a", "f4dc86"), "greatboa": ("6fb8a8", "c0ece0"), "greatoak": ("4f9a3a", "a8d878"),
    "greatroot": ("2f6a3a", "7cb078"), "skybirch": ("b8c850", "ecf4a0"), "skypine": ("2f6b5f", "78b8a8"), "skyplane": ("a8d89a", "f2b8c8"),
    "wisproot": ("b8a8d8", "e4dcf4"), "wisptop": ("8a60c0", "d0b0f0"),
}


def lizard(name):
    base, frill = LIZARD[name]
    body = [shade(hx(base), f) for f in (1.0, 0.88, 1.12, 0.76)]
    fr = [shade(hx(frill), f) for f in (1.0, 0.9, 1.08)]
    sk = Skin("skyroot_lizard_" + name, (64, 32), parts_of("SkyrootLizardModel.java"))
    for n in ("body", "neck", "head", "snout", "tail_1", "tail_2", "tail_3"):
        sk.box(n, body)
    for n in ("frill_l", "frill_r"):
        sk.box(n, fr)
        # veins radiating across the frill
        for face in ("top", "bottom"):
            x0, y0, fw, fh = faces(sk.parts[n])[face]
            for x in range(0, fw, 2):
                for y in range(fh):
                    sk.at(n, face, x, y, body[1])
    sk.speckle("body", [body[3], fr[0]], 5)
    sk.rect("snout", "top", 0, 0, 4, 1, body[3])
    # eyes on top of the flat head
    sk.at("head", "top", 0, 1, DARK)
    sk.at("head", "top", 4, 1, DARK)
    sk.at("head", "top", 0, 2, hx("f4e890"))
    sk.at("head", "top", 4, 2, hx("f4e890"))
    for n in ("tail_1", "tail_2", "tail_3"):
        sk.rect(n, "top", 0, 0, sk.parts[n]["w"], 1, body[3])
    return sk


# ---------------------------------------------------------------- prismallard

def prismallard():
    sk = Skin("prismallard", (64, 32), parts_of("PrismallardModel.java"))
    body = ramp("5a78a8", "4a6898", "6c8cbc", "3c5888")
    teal = ramp("2f9a8a", "268a7a", "46b4a2", "1e7468")
    sk.box("body", body)
    sk.rect("body", "bottom", 0, 0, 5, 6, hx("e8e0c8"))
    sk.speckle("body", [body[2], hx("a8c0e0")], 14)
    sk.box("neck", teal)
    sk.rect("neck", "front", 0, 3, 3, 1, WHITE)
    sk.rect("neck", "back", 0, 3, 3, 1, WHITE)
    sk.rect("neck", "left", 0, 3, 2, 1, WHITE)
    sk.rect("neck", "right", 0, 3, 2, 1, WHITE)
    sk.box("head", teal)
    sk.box("bill", ramp("e8b030", "d09820", "f4c850"))
    sk.box("jaw", ramp("d8a02a", "c08a1c", "e8b440"))
    eyes(sk, "head", 1, DARK, hx("f4f0d0"))
    for n in ("leg_l", "leg_r", "foot_l", "foot_r"):
        sk.box(n, ramp("e08a30", "c8741e", "f0a050"))
    # the tail feathers: iridescent fans, a different pair of colours each
    fans = ((hx("e86aa8"), hx("8a4ad8")), (hx("2fc8c0"), hx("3a6ae0")), (hx("5ad870"), hx("e8c83a")))
    for i, (a, b) in enumerate(fans):
        n = "tail_%d" % i
        p = sk.parts[n]
        for face, (x0, y0, w, h) in faces(p).items():
            for y in range(h):
                for x in range(w):
                    t = (y / max(1, h - 1)) if face in ("top", "bottom") else (x / max(1, w - 1))
                    c = tuple(int(a[k] + (b[k] - a[k]) * t) for k in range(3))
                    if sk.rng.random() < 0.12:
                        c = shade(c, 1.15)
                    sk.px[x0 + x, y0 + y] = c + (255,)
    for n in ("wing_l", "wing_r"):
        sk.box(n, body)
        sk.rect(n, "front", 0, 3, 5, 1, hx("e8f0ff"))
        sk.speckle(n, [hx("a8c0e0")], 3)
    return sk


# ---------------------------------------------------------------- insects

def wing_cells(sk, name, fill, vein, holes=0.0, spots=None, tint_end=None):
    """A wing: cells of colour between darker veins, every other cell see-through if holes > 0."""
    p = sk.parts[name]
    for face, (x0, y0, w, h) in faces(p).items():
        for y in range(h):
            for x in range(w):
                t = x / max(1, w - 1)
                col = fill if tint_end is None else tuple(int(fill[k] + (tint_end[k] - fill[k]) * t) for k in range(3))
                if x % 4 == 0 or y % 3 == 0:
                    col = vein
                elif sk.rng.random() < holes:
                    continue
                if spots and sk.rng.random() < spots[1]:
                    col = spots[0]
                sk.px[x0 + x, y0 + y] = col + (255,)


GLITTER = {
    "indigo": dict(wing=("7a70e0", "3c3490", None, None), body="2c2850"),
    "dragonfly": dict(wing=("78d8d8", "2f8a8c", None, None), body="1e4a4c"),
    "drapewing": dict(wing=("e8a8d8", "9a4a98", None, hx("ffe0f4")), body="4a2a50"),
    "leaf_insect": dict(wing=("7ac860", "3a7a2c", None, None), body="2c5a22"),
    "quicksoil_moth": dict(wing=("d8c08a", "8a7040", None, None), body="5a4a2a"),
    "sunset_dragonfly": dict(wing=("f8b040", "a8401c", None, hx("e8483a")), body="5a2418"),
    "orange_mimic": dict(wing=("f08a2c", "8a4414", None, None), body="4a2a14"),
    "amber_moth": dict(wing=("e8b84a", "9a6a1a", None, None), body="5a3c14"),
    "spotted": dict(wing=("f0e4c0", "8a7a58", (hx("3a2c22"), 0.07), None), body="4a3c2c"),
    "blightfly": dict(wing=("b8d03a", "5a6a14", (hx("2c3a0a"), 0.06), None), body="2c3410"),
    "phantomfly": dict(wing=("e8eef4", "98a8b8", None, None), body="aab4c0"),
}


def glitterwing(name):
    d = GLITTER[name]
    fill, vein, spots, end = d["wing"]
    body = [shade(hx(d["body"]), f) for f in (1.0, 0.85, 1.2)]
    sk = Skin("glitterwing_" + name, (32, 32), parts_of("GlitterwingModel.java"))
    sk.box("body", body)
    sk.box("head", body)
    sk.rect("body", "top", 0, 0, 2, 7, hx(fill))
    eyes(sk, "head", 1, hx("f4f0a0"))
    for n in ("wing_l", "wing_r"):
        wing_cells(sk, n, hx(fill), hx(vein), holes=0.0, spots=spots, tint_end=end)
    return sk


SHROUD = {
    "scarab": ("2f5a44", "9ae8b8"), "arctic": ("c8dcee", "7a9cc0"), "fire": ("d8481c", "2a1a14"), "moss": ("5a8a3a", "a8d878"),
    "ore": ("707880", "e8c84a"), "purple": ("7a48b8", "d8b0f8"),
}


def shroudwing(name):
    base, accent = SHROUD[name]
    c = [shade(hx(base), f) for f in (1.0, 0.85, 1.15, 0.7)]
    sk = Skin("shroudwing_" + name, (32, 32), parts_of("ShroudwingModel.java"))
    sk.box("sbody", [shade(c[0], 0.6), shade(c[0], 0.5), shade(c[0], 0.7)])
    sk.box("shead", [shade(c[0], 0.5), shade(c[0], 0.4)])
    eyes(sk, "shead", 0, hx(accent))
    for n in ("elytra_l", "elytra_r"):
        sk.box(n, c)
        sk.rect(n, "top", 0, 0, 2, 6, c[0])
        sk.rect(n, "top", 0, 0, 1, 6, c[2])
        sk.speckle(n, [hx(accent)], 3, only=["top"])
    for n in ("swing_l", "swing_r"):
        p = sk.parts[n]
        for face, (x0, y0, w, h) in faces(p).items():
            for y in range(h):
                for x in range(w):
                    col = shade(hx(accent), 0.75) if (x % 3 == 0 or y % 4 == 0) else shade(hx(accent), 1.1)
                    sk.px[x0 + x, y0 + y] = col + (255,)
    return sk


# ---------------------------------------------------------------- aerwhale

def aerwhale():
    sk = Skin("aerwhale", (256, 128), parts_of("AerwhaleModel.java"))
    top = ramp("7a9cc8", "6a8cba", "8caed6", "5a7aa8")
    belly = ramp("e4ecf4", "d2deea", "f4f8fc")
    for n in ("head", "body", "tail"):
        sk.box(n, top)
    for n in ("head", "body"):
        sk.rect(n, "bottom", 0, 0, sk.parts[n]["w"], sk.parts[n]["d"], belly[0])
    sk.box("jaw", belly)
    sk.box("dorsal", darker(top, 0.85))
    sk.box("fluke", darker(top, 0.9))
    for n in ("fin_l", "fin_r"):
        sk.box(n, darker(top, 0.88))
    sk.box("body", top)
    # a pale belly stripe and flowing lines along the back
    for face in ("left", "right"):
        for n in ("head", "body"):
            x0, y0, w, h = faces(sk.parts[n])[face]
            for x in range(w):
                sk.at(n, face, x, h - 1 - h // 3, belly[1])
                sk.at(n, face, x, h - h // 3, belly[0])
                sk.at(n, face, x, h - 2 * (h // 3), belly[0] if x % 5 else top[1])
    for n in ("head", "body"):
        sk.patches(n, top[2], 14, (2, 5), only=["top", "left", "right"])
        sk.patches(n, top[3], 10, (2, 4), only=["top", "left", "right"], chance=0.7)
    sk.patches("tail", top[3], 6, (2, 4))
    # a dozen stars on its back
    for _ in range(18):
        face = sk.rng.choice(("top",))
        n = sk.rng.choice(("head", "body"))
        x0, y0, w, h = faces(sk.parts[n])[face]
        sk.at(n, face, sk.rng.randrange(w), sk.rng.randrange(h), hx("f4f8ff"))
    # eyes on the head's sides, near the front; the jaw's line
    x0, y0, w, h = faces(sk.parts["head"])["right"]
    sk.rect("head", "right", w - 7, 5, 3, 2, hx("1c2434"))
    sk.at("head", "right", w - 6, 5, hx("f4f8ff"))
    x0, y0, w, h = faces(sk.parts["head"])["left"]
    sk.rect("head", "left", 4, 5, 3, 2, hx("1c2434"))
    sk.at("head", "left", 5, 5, hx("f4f8ff"))
    sk.rect("head", "front", 0, 10, 25, 1, hx("3a4a6a"))
    return sk


# ---------------------------------------------------------------- 16x16 sprites

def m_ellipse(cx, cy, rx, ry, ang=0.0):
    ca, sa = math.cos(ang), math.sin(ang)
    out = set()
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            u, v = dx * ca + dy * sa, -dx * sa + dy * ca
            if (u / rx) ** 2 + (v / ry) ** 2 <= 1.0:
                out.add((x, y))
    return out


def m_line(x0, y0, x1, y1, width=1):
    out = set()
    steps = int(max(abs(x1 - x0), abs(y1 - y0)) * 2) + 1
    for i in range(steps + 1):
        t = i / steps
        x, y = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t
        for dx in range(width):
            for dy in range(width):
                out.add((int(x) + dx, int(y) + dy))
    return {(x, y) for x, y in out if 0 <= x < 16 and 0 <= y < 16}


def m_rect(x0, y0, x1, y1):
    return {(x, y) for x in range(x0, x1) for y in range(y0, y1)}


def m_poly(points):
    out = set()
    n = len(points)
    for y in range(16):
        for x in range(16):
            px, py = x + 0.5, y + 0.5
            inside = False
            for i in range(n):
                (ax, ay), (bx, by) = points[i], points[(i + 1) % n]
                if (ay > py) != (by > py) and px < (bx - ax) * (py - ay) / (by - ay) + ax:
                    inside = not inside
            if inside:
                out.add((x, y))
    return out


class Sprite:
    def __init__(self, seed):
        self.im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        self.px = self.im.load()
        self.rng = random.Random("aether-animal-item:" + seed)

    def layer(self, mask, colors, outline, jitter=0.12):
        """Fill a mask lit from the upper left (colors: light, mid, dark), with a darker outline."""
        if not mask:
            return self
        lo = min(x + y for x, y in mask)
        hi = max(x + y for x, y in mask)
        for x, y in mask:
            t = (x + y - lo) / max(1, hi - lo)
            if self.rng.random() < jitter:
                t += self.rng.choice((-0.25, 0.25))
            c = colors[0] if t < 0.33 else colors[1] if t < 0.7 else colors[2]
            edge = any((x + ox, y + oy) not in mask for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            self.px[x, y] = (outline if edge else c) + (255,)
        return self

    def dots(self, mask, color, count):
        cells = sorted(mask)
        inner = [(x, y) for x, y in cells if all((x + ox, y + oy) in mask for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)))]
        for _ in range(count):
            if inner:
                x, y = self.rng.choice(inner)
                self.px[x, y] = color + (255,)
        return self

    def put(self, cells, color):
        for x, y in cells:
            if 0 <= x < 16 and 0 <= y < 16 and self.px[x, y][3]:
                self.px[x, y] = color + (255,)
        return self


RAW = (hx("e8808a"), hx("d46470"), hx("b84a58"))
RAW_EDGE = hx("7a2a36")
FAT = hx("f8e0e0")
COOKED = (hx("b8743e"), hx("9a5a2e"), hx("7a4422"))
COOKED_EDGE = hx("4a2810")
GRILL = hx("5a3014")
BONE = (hx("f2ead8"), hx("ddd2b8"), hx("bfb294"))
BONE_EDGE = hx("7a6e56")
CRUMB = (hx("e8b05a"), hx("d09840"), hx("b07c2c"))
CRUMB_EDGE = hx("6a4416")


def chop(cooked, name):
    sp = Sprite(name)
    mask = m_ellipse(7.5, 8.5, 6.3, 4.6, -0.45)
    sp.layer(mask, COOKED if cooked else RAW, COOKED_EDGE if cooked else RAW_EDGE)
    if cooked:
        for k in (-2, 1, 4):
            sp.put(m_line(3 + k, 5 + k // 2, 9 + k, 12 + k // 2) & mask, GRILL)
        sp.dots(mask, shade(COOKED[0], 1.15), 4)
    else:
        sp.put(m_line(4, 9, 11, 6) & mask, FAT)
        sp.put(m_line(5, 11, 10, 9) & mask, FAT)
        sp.dots(mask, shade(RAW[0], 1.1), 3)
    return sp.im


def ribs(cooked, name):
    sp = Sprite(name)
    bones = set()
    for x in (4, 7, 10):
        bones |= m_line(x, 10, x, 14, 1)
    sp.layer(bones, BONE, BONE_EDGE, jitter=0.0)
    mask = m_rect(3, 4, 13, 11)
    mask -= {(3, 4), (12, 4), (3, 10), (12, 10)}
    sp.layer(mask, COOKED if cooked else RAW, COOKED_EDGE if cooked else RAW_EDGE)
    for x in (5, 8, 11):
        sp.put({(x, y) for y in range(5, 10)}, GRILL if cooked else shade(RAW[2], 0.9))
    if not cooked:
        sp.put(m_line(4, 5, 11, 5), FAT)
    return sp.im


def loin(name):
    sp = Sprite(name)
    mask = m_ellipse(8, 8, 5.2, 3.8, 0.5)
    sp.layer(mask, (hx("e4929a"), hx("d07a86"), hx("b0606e")), hx("7a3440"))
    sp.put(m_line(5, 6, 11, 11) & mask, FAT)
    sp.put(m_line(4, 8, 9, 12) & mask, hx("f4c4c8"))
    return sp.im


def cutlet(name):
    sp = Sprite(name)
    mask = m_ellipse(7.8, 8.2, 6.2, 4.6, 0.3)
    sp.layer(mask, CRUMB, CRUMB_EDGE, jitter=0.2)
    sp.dots(mask, hx("f4d080"), 7)
    sp.dots(mask, hx("8a5a20"), 5)
    return sp.im


def leg(cooked, name):
    sp = Sprite(name)
    sp.layer(m_line(8, 8, 13, 13, 2), BONE, BONE_EDGE, jitter=0.0)
    sp.layer(m_ellipse(12.5, 12.5, 2.0, 2.0) | m_ellipse(14, 11, 1.2, 1.2), BONE, BONE_EDGE, jitter=0.0)
    mask = m_ellipse(6, 6, 5.0, 3.8, 0.8)
    colors = (hx("c88a5a"), hx("a86c3e"), hx("8a542c")) if cooked else (hx("e8a0a0"), hx("d48490"), hx("b86878"))
    sp.layer(mask, colors, COOKED_EDGE if cooked else hx("7a3a46"))
    if cooked:
        sp.dots(mask, hx("e0b078"), 4)
    else:
        sp.put(m_line(3, 5, 8, 8) & mask, FAT)
    return sp.im


def pelt(name):
    sp = Sprite(name)
    mask = m_poly([(2, 3), (7, 2), (13, 3), (14, 8), (12, 13), (7, 14), (3, 12), (1, 8)])
    sp.layer(mask, (hx("b08a5e"), hx("94704a"), hx("785838")), hx("3e2a18"), jitter=0.2)
    for x in range(3, 13, 2):
        sp.put(m_line(x, 4, x - 1, 12) & mask, hx("6a4c30"))
    sp.dots(mask, hx("d0aa7c"), 8)
    return sp.im


def plate(name, colors, edge, ridge):
    sp = Sprite(name)
    mask = m_poly([(8, 1.5), (13.5, 4.5), (13.5, 11.5), (8, 14.5), (2.5, 11.5), (2.5, 4.5)])
    sp.layer(mask, colors, edge, jitter=0.15)
    sp.put(m_line(8, 3, 8, 13), ridge)
    sp.put(m_line(5, 5, 8, 8), ridge)
    sp.put(m_line(11, 5, 8, 8), ridge)
    return sp.im


def kirrid_plate(name):
    sp = Sprite(name)
    mask = m_ellipse(8, 8, 4.4, 6.4, -0.2)
    sp.layer(mask, (hx("f4ecd8"), hx("e0d4b8"), hx("c4b698")), hx("7a6e56"), jitter=0.1)
    sp.put(m_line(8, 3, 8, 13) & mask, hx("c4b698"))
    sp.put(m_line(5, 7, 11, 7) & mask, hx("c4b698"))
    return sp.im


def feather(name, a, b, shaft, length=7.2, width=2.8):
    sp = Sprite(name)
    mask = m_ellipse(8, 8, length, width, -0.78)
    lo = min(x - y for x, y in mask)
    hi = max(x - y for x, y in mask)
    for x, y in mask:
        t = (x - y - lo) / max(1, hi - lo)
        c = tuple(int(a[k] + (b[k] - a[k]) * t) for k in range(3))
        if sp.rng.random() < 0.15:
            c = shade(c, 1.12)
        edge = any((x + ox, y + oy) not in mask for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        sp.px[x, y] = (shade(c, 0.6) if edge else c) + (255,)
    sp.put(m_line(2, 14, 14, 2) & mask, shaft)
    sp.put(m_line(1, 15, 3, 13), shaft)
    sp.px[1, 15] = shade(shaft, 0.7) + (255,)
    return sp.im


def lizard_stick(name):
    sp = Sprite(name)
    sp.layer(m_line(1, 15, 14, 2, 1), (hx("a8784a"), hx("8c5e34"), hx("6e4824")), hx("3e2812"), jitter=0.0)
    body = m_ellipse(8.5, 7.5, 4.6, 2.2, -0.8)
    body |= m_line(5, 11, 3, 13) | m_line(11, 4, 13, 3)
    sp.layer(body, (hx("a89a4a"), hx("8a7c38"), hx("6c6028")), hx("3a3418"), jitter=0.15)
    sp.put(m_line(6, 9, 10, 5) & body, hx("c8bc68"))
    # the frill, singed
    sp.put({(6, 6), (10, 9), (7, 5), (11, 8)}, hx("c8782c"))
    return sp.im


# the colours of the spawn eggs: body, spots
SPAWN = {
    "kirrid_spawn_egg": ("e6edf5", "a0805a", "7a8a9a"), "burrukai_spawn_egg": ("7a5a3c", "8a8f7a", "3a2a1c"),
    "taegore_spawn_egg": ("6e6a3c", "c8b88a", "3a381c"), "skyroot_lizard_spawn_egg": ("6fa84a", "c8e07a", "2c4a1a"),
    "prismallard_spawn_egg": ("2f9a8a", "9a6ad8", "1a4a44"), "glitterwing_spawn_egg": ("5a50d0", "c0c0ff", "2a2468"),
    "shroudwing_spawn_egg": ("2f5a44", "a0e0a0", "142a20"), "aerwhale_spawn_egg": ("8ab0d8", "f0f4fa", "40587a"),
}

# every item of AetherAnimals and its name
ITEMS = {
    "raw_taegore_meat": "Raw Taegore Meat", "cooked_taegore_meat": "Cooked Taegore Meat",
    "burrukai_rib_cut": "Burrukai Rib Cut", "cooked_burrukai_rib_cut": "Cooked Burrukai Rib Cut",
    "kirrid_loin": "Kirrid Loin", "kirrid_cutlet": "Kirrid Cutlet",
    "prismallard_leg": "Prismallard Leg", "prismallard_roast": "Prismallard Roast",
    "skyroot_lizard_on_a_stick": "Skyroot Lizard on a Stick", "beast_pelt": "Beast Pelt", "burrukai_plate": "Burrukai Plate",
    "prismallard_feather": "Prismallard Feather", "moa_feather": "Moa Feather", "kirrid_plate": "Kirrid Plate",
    "prismallard_egg": "Prismallard Egg",
}
SPAWN_NAMES = {"kirrid": "Kirrid", "burrukai": "Burrukai", "taegore": "Taegore", "skyroot_lizard": "Skyroot Lizard",
               "prismallard": "Prismallard", "glitterwing": "Glitterwing", "shroudwing": "Shroudwing", "aerwhale": "Aerwhale"}
ENTITIES = dict(SPAWN_NAMES, prismallard_egg="Prismallard Egg")


def items():
    imgs = {
        "raw_taegore_meat": chop(False, "raw_taegore_meat"), "cooked_taegore_meat": chop(True, "cooked_taegore_meat"),
        "burrukai_rib_cut": ribs(False, "burrukai_rib_cut"), "cooked_burrukai_rib_cut": ribs(True, "cooked_burrukai_rib_cut"),
        "kirrid_loin": loin("kirrid_loin"), "kirrid_cutlet": cutlet("kirrid_cutlet"),
        "prismallard_leg": leg(False, "prismallard_leg"), "prismallard_roast": leg(True, "prismallard_roast"),
        "skyroot_lizard_on_a_stick": lizard_stick("skyroot_lizard_on_a_stick"), "beast_pelt": pelt("beast_pelt"),
        "burrukai_plate": plate("burrukai_plate", (hx("9aa088"), hx("80866e"), hx("666c58")), hx("363a2c"), hx("4e5442")),
        "prismallard_feather": feather("prismallard_feather", hx("2fc8c0"), hx("9a5ae0"), hx("f4f0d8"), 6.6, 2.4),
        "moa_feather": feather("moa_feather", hx("a8c8f0"), hx("3a62b8"), hx("f4f8ff"), 7.4, 3.0),
        "kirrid_plate": kirrid_plate("kirrid_plate"),
        "prismallard_egg": M.egg_image(hx("d4f0e8"), hx("9a6ad8"), hx("5a8a90"), "prismallard_egg", 8),
    }
    for name, (base, spot, outline) in SPAWN.items():
        imgs[name] = M.egg_image(hx(base), hx(spot), hx(outline), name, 8, ring=True)
    for name, im in imgs.items():
        save(im, os.path.join(ASSETS, "textures", "item", SUB, name + ".png"))
        write(os.path.join(ASSETS, "models", "item", SUB, name + ".json"),
              {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/%s/%s" % (NS, SUB, name)}})
        write(os.path.join(ASSETS, "items", name + ".json"), {"model": {"type": "minecraft:model", "model": "%s:item/%s/%s" % (NS, SUB, name)}})


# ---------------------------------------------------------------- the moa egg block

MOA_KINDS = ("blue", "white", "black")
MOA_SHELL = {
    "blue": (ramp("6a9ad8", "5a8ac8", "7aaae8", "4a7ab8"), hx("e8f2ff")),
    "white": (ramp("f2ecde", "e0d8c4", "fffaf0", "cfc6b0"), hx("b89868")),
    "black": (ramp("3a3a44", "2a2a32", "4c4c58", "1e1e26"), hx("8a8a9c")),
}


def moa_egg_texture(kind, stage):
    colors, spot = MOA_SHELL[kind]
    rng = random.Random("aether-moa-egg:%s%d" % (kind, stage))
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
    px = im.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = rng.choices(colors, (5, 3, 2, 2))[0] + (255,)
    for _ in range(14):
        px[rng.randrange(16), rng.randrange(16)] = spot + (255,)
    # cracks: stage 1 a few, stage 2 a web
    dark = shade(colors[3], 0.55)
    for _ in range(0 if stage == 0 else 3 if stage == 1 else 7):
        x, y = rng.randrange(2, 14), rng.randrange(0, 4)
        for _ in range(rng.randint(5, 9)):
            px[x, y] = dark + (255,)
            x = max(0, min(15, x + rng.choice((-1, 0, 1))))
            y = min(15, y + 1)
    return im


def egg_elements(low):
    """Three stacked boxes make an egg; the faces take the matching part of one 16x16 shell texture."""
    boxes = ((4, 0, 4, 12, 6, 12), (5, 6, 5, 11, 10, 11), (6, 10, 6, 10, 12, 10))
    out = []
    for x0, y0, z0, x1, y1, z1 in boxes:
        shift = -8 if low else 0
        side_u = [x0, 16 - y1, x1, 16 - y0]
        faces_ = {
            "north": {"uv": side_u, "texture": "#shell"}, "south": {"uv": side_u, "texture": "#shell"},
            "east": {"uv": [z0, 16 - y1, z1, 16 - y0], "texture": "#shell"}, "west": {"uv": [z0, 16 - y1, z1, 16 - y0], "texture": "#shell"},
            "up": {"uv": [x0, z0, x1, z1], "texture": "#shell"}, "down": {"uv": [x0, z0, x1, z1], "texture": "#shell"},
        }
        out.append({"from": [x0, y0 + shift, z0], "to": [x1, y1 + shift, z1], "faces": faces_})
    return out


def moa_egg_block():
    parts = []
    for ki, kind in enumerate(MOA_KINDS):
        for stage in range(3):
            save(moa_egg_texture(kind, stage), os.path.join(ASSETS, "textures", "block", SUB, "moa_egg_%s_%d.png" % (kind, stage)))
            for low in (False, True):
                name = "moa_egg_%s_%d%s" % (kind, stage, "_low" if low else "")
                write(os.path.join(ASSETS, "models", "block", SUB, name + ".json"), {
                    "textures": {"particle": "#shell", "shell": "%s:block/%s/moa_egg_%s_%d" % (NS, SUB, kind, stage)},
                    "elements": egg_elements(low)})
                parts.append({"when": {"kind": str(ki), "stage": str(stage), "low": "true" if low else "false"},
                              "apply": {"model": "%s:block/%s/%s" % (NS, SUB, name)}})
    write(os.path.join(ASSETS, "blockstates", "moa_egg.json"), {"multipart": parts})
    write(os.path.join(ASSETS, "models", "item", SUB, "moa_egg.json"), {"parent": "%s:block/%s/moa_egg_blue_0" % (NS, SUB),
                                                                         "display": {"gui": {"rotation": [30, 225, 0], "scale": 1.0}}})
    write(os.path.join(ASSETS, "items", "moa_egg.json"), {"model": {"type": "minecraft:model", "model": "%s:item/%s/moa_egg" % (NS, SUB)}})
    # a broken egg drops the egg item of its kind
    pools = []
    for ki, kind in enumerate(MOA_KINDS):
        pools.append({"rolls": 1, "condition": {"type": "minecraft:block_state_property", "block": "%s:moa_egg" % NS, "properties": {"kind": str(ki)}},
                      "entries": [{"type": "minecraft:item", "name": "%s:%s_moa_egg" % (NS, kind)}]})
    write(os.path.join(DATA, "loot_table", "blocks", "moa_egg.json"),
          {"type": "minecraft:block", "pools": pools, "random_sequence": "%s:blocks/moa_egg" % NS})


# ---------------------------------------------------------------- loot, recipes, lang

def loot():
    d = M.drop
    smelt_name = lambda n: "%s:%s" % (NS, n)
    tables = {
        "kirrid": [d(smelt_name("kirrid_loin"), 1, 2, smelt=True)],  # its wool is dropped by the entity (its colour)
        "burrukai": [d(smelt_name("burrukai_rib_cut"), 1, 3, smelt=True), d(smelt_name("beast_pelt"), 0, 2), d(smelt_name("burrukai_plate"), 1, 3)],
        "taegore": [d(smelt_name("raw_taegore_meat"), 1, 3, smelt=True), d(smelt_name("beast_pelt"), 1, 3)],
        "prismallard": [d(smelt_name("prismallard_leg"), 1, 2, smelt=True), d(smelt_name("prismallard_feather"), 1, 2)],
        "skyroot_lizard": [], "glitterwing": [], "shroudwing": [], "aerwhale": [],
    }
    for name, pools in tables.items():
        write(os.path.join(DATA, "loot_table", "entities", name + ".json"),
              {"type": "minecraft:entity", "pools": pools, "random_sequence": "%s:entities/%s" % (NS, name)})


COOKING = (("raw_taegore_meat", "cooked_taegore_meat"), ("burrukai_rib_cut", "cooked_burrukai_rib_cut"),
           ("kirrid_loin", "kirrid_cutlet"), ("prismallard_leg", "prismallard_roast"))


def recipes():
    out = os.path.join(DATA, "recipe", SUB)
    for raw, cooked in COOKING:
        for kind, time in (("smelting", 200), ("smoking", 100), ("campfire_cooking", 600)):
            name = cooked if kind == "smelting" else "%s_from_%s" % (cooked, kind)
            write(os.path.join(out, name + ".json"), {"type": "minecraft:" + kind, "category": "food", "cookingtime": time, "experience": 0.35,
                                                      "ingredient": "%s:%s" % (NS, raw), "result": {"id": "%s:%s" % (NS, cooked)}})


def lang():
    mine = {}
    for k, v in ENTITIES.items():
        mine["entity.jaymospowers." + k] = v
    for k, v in ITEMS.items():
        mine["item.jaymospowers." + k] = v
    for k, v in SPAWN_NAMES.items():
        mine["item.jaymospowers.%s_spawn_egg" % k] = v + " Spawn Egg"
    mine["block.jaymospowers.moa_egg"] = "Moa Egg"
    write(os.path.join(ASSETS, "lang", "parts", "aether_animals.json"), mine)


def main():
    for zone in ZONES:
        for wool in WOOL_NAMES + ["undyed"]:
            kirrid(zone, wool).finish(os.path.join(SKINS, "kirrid_%s_%s.png" % (zone, wool)))
        burrukai(zone).finish(os.path.join(SKINS, "burrukai_%s.png" % zone))
        taegore(zone).finish(os.path.join(SKINS, "taegore_%s.png" % zone))
    for name in LIZARD:
        lizard(name).finish(os.path.join(SKINS, "skyroot_lizard_%s.png" % name))
    prismallard().finish(os.path.join(SKINS, "prismallard.png"))
    for name in GLITTER:
        glitterwing(name).finish(os.path.join(SKINS, "glitterwing_%s.png" % name))
    for name in SHROUD:
        shroudwing(name).finish(os.path.join(SKINS, "shroudwing_%s.png" % name))
    aerwhale().finish(os.path.join(SKINS, "aerwhale.png"))
    items()
    moa_egg_block()
    loot()
    recipes()
    lang()
    print("Aether II animal art and data written.")


if __name__ == "__main__":
    main()
