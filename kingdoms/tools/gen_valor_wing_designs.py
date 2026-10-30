"""Valor wing designs (Kingdoms 0.7.410): a Skyward Valor's wings take one of the pack's dragons' wings (Andy: "give the
valor wings designs stemming from any of the dragon models we have, not just the wyrm"). ValorWingsLook draws:

  wyrm      the ender dragon's wing shape (Xenoform Wyrm's), our own textures (tools/gen_valor_wings.py)
  notus     Notus and Boreas's wings: nyxaris's "Fire Dragon" (CC BY 4.0), bone, membrane and outer wing
  zephyrus  Zephyrus's short broad ragged wings (our own model), from its hide
  eurus     Eurus and Aeolus's wyvern wings: eanncrod's "Kintsugi" (CC BY 4.0), with their seams

This script bakes the two Blockbench wings into our loader's format (bbmodels/valor_wings_<design>.json), turned so each
wing's span runs out along x with its membrane trailing back (+z), the way the wyrm's does, so one set of poses moves them
all: bones right_wing / right_wing_tip / left_wing / left_wing_tip. And for notus, zephyrus and eurus it recolours the
dragon's texture into each Valor look (textures/entity/valor/wings_<design>_<look>.png, its shading kept, the rest of the
sheet cleared) and picks out its lines for King's coating (wings_<design>_veins.png): Notus's dark veins, Eurus's gold
seams, Zephyrus's ribs. Run from the kingdoms folder:  python -I tools/gen_valor_wing_designs.py
"""
import json
import math
import os
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "src", "main", "resources", "assets", "jaymoskingdoms")
VALOR = os.path.join(ASSETS, "textures", "entity", "valor")

# each look: dark, light (the ramp the dragon's shading is mapped onto), alpha share
LOOKS = {
    "iron": ((0x2A, 0x2E, 0x36), (0xA0, 0xA8, 0xB6), 1.0),
    "stone": ((0x3A, 0x35, 0x2F), (0x9A, 0x92, 0x84), 1.0),
    "scale": ((0x0E, 0x22, 0x1A), (0x5E, 0x9E, 0x7A), 1.0),
    "ash": ((0x40, 0x3E, 0x3E), (0xC4, 0xC0, 0xBA), 0.92),
    "haze": ((0xA0, 0x48, 0x10), (0xFF, 0xD8, 0x80), 0.62),
    "lacquer": ((0x05, 0x05, 0x08), (0x60, 0x60, 0x88), 1.0),
}


def rot_zyx(x, y, z):
    cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
    rx = ((1, 0, 0), (0, cx, -sx), (0, sx, cx))
    ry = ((cy, 0, sy), (0, 1, 0), (-sy, 0, cy))
    rz = ((cz, -sz, 0), (sz, cz, 0), (0, 0, 1))
    return mul(mul(rz, ry), rx)


def mul(a, b):
    return tuple(tuple(sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)) for i in range(3))


def apply(m, v):
    return [sum(m[i][k] * v[k] for k in range(3)) for i in range(3)]


def turn(quads, m):
    out = []
    for q in quads:
        f = list(q)
        for v in range(4):
            f[v * 5:v * 5 + 3] = [round(c, 4) for c in apply(m, q[v * 5:v * 5 + 3])]
        f[20:23] = [round(c, 5) for c in apply(m, q[20:23])]
        out.append(f)
    return out


# (design, source model, its texture, [(our bone, its wing bone, its tip bone)], the turn that lays its wings out)
SOURCES = (
    ("notus", "fire_dragon", "textures/entity/bb/fire_dragon.png", (("right", "leftAla", "ala1"), ("left", "righAla", "ala3")),
     {"right": (0.0, 0.0, 0.0), "left": (0.0, 0.0, 0.0)}),
    ("eurus", "kintsugi", "textures/entity/bb/kintsugi.png",
     (("right", "rightWingPivot", "rightWingOuter"), ("left", "leftWingPivot", "leftWingOuter")),
     {"right": (-1.8475, 0.0832, 1.2564), "left": (-1.8475, -0.0833, -1.2564)}),
)


def bake(design, source, wings, turns):
    model = json.load(open(os.path.join(ASSETS, "bbmodels", source + ".json")))
    by_name = {b["name"]: b for b in model["bones"]}
    bones = []
    uvs = []
    for side, wing, tip in wings:
        m = rot_zyx(*turns[side])
        w, t = by_name[wing], by_name[tip]
        parent = len(bones)
        bones.append({"name": side + "_wing", "parent": -1, "pivot": [0.0, 0.0, 0.0], "rot": [0.0, 0.0, 0.0], "quads": turn(w["quads"], m)})
        bones.append({"name": side + "_wing_tip", "parent": parent, "pivot": [round(c, 4) for c in apply(m, t["pivot"])],
                      "rot": [0.0, 0.0, 0.0], "quads": turn(t["quads"], m)})
        for q in w["quads"] + t["quads"]:
            uvs.append([(q[v * 5 + 3], q[v * 5 + 4]) for v in range(4)])
    out = {"tex": model["tex"], "bones": bones}
    with open(os.path.join(ASSETS, "bbmodels", "valor_wings_" + design + ".json"), "w") as f:
        json.dump(out, f, separators=(",", ":"))
    return uvs


def zephyrus_uvs():
    # ZephyrusDragonModel's wing boxes on its 256x256 sheet: (u, v, w, h, d)
    boxes = ((94, 95, 18, 3, 3), (112, 0, 18, 0, 26), (0, 103, 20, 2, 2), (0, 44, 20, 0, 22))
    rects = []
    for u, v, w, h, d in boxes:
        rects.append((u, v, u + 2 * (w + d), v + d + h))
    return [[(x0 / 256, y0 / 256), (x1 / 256, y1 / 256)] for x0, y0, x1, y1 in rects]


def used(size, uvs):
    """Which pixels the wings' quads draw from (their UV boxes, a pixel's margin)."""
    w, h = size
    keep = set()
    for quad in uvs:
        us = [p[0] * w for p in quad]
        vs = [p[1] * h for p in quad]
        for x in range(max(0, int(min(us)) - 1), min(w, int(math.ceil(max(us))) + 1)):
            for y in range(max(0, int(min(vs)) - 1), min(h, int(math.ceil(max(vs))) + 1)):
                keep.add((x, y))
    return keep


def lum(p):
    return (0.299 * p[0] + 0.587 * p[1] + 0.114 * p[2]) / 255.0


def vein(design, p, l):
    if design == "notus":
        return l < 0.22
    if design == "eurus":
        # the gold seams: warm and saturated
        hi, lo = max(p[:3]), min(p[:3])
        return p[0] > 120 and p[0] >= p[1] > p[2] and (hi - lo) / max(1, hi) > 0.35
    return l > 0.62


def paint(design, src_path, uvs, scale_to=None):
    src = Image.open(os.path.join(ASSETS, src_path)).convert("RGBA")
    if scale_to:
        src = src.resize(scale_to, Image.NEAREST)
    keep = used(src.size, uvs)
    px = src.load()
    lums = [lum(px[x, y]) for x, y in keep if px[x, y][3] > 0]
    lo, hi = (min(lums), max(lums)) if lums else (0.0, 1.0)
    for look, (dark, light, alpha) in LOOKS.items():
        img = Image.new("RGBA", src.size, (0, 0, 0, 0))
        out = img.load()
        for x, y in keep:
            p = px[x, y]
            if p[3] == 0:
                continue
            k = (lum(p) - lo) / max(1e-6, hi - lo)
            k = k ** 0.8
            rgb = tuple(int(dark[i] + (light[i] - dark[i]) * k) for i in range(3))
            out[x, y] = (*rgb, int(p[3] * alpha))
        img.save(os.path.join(VALOR, "wings_" + design + "_" + look + ".png"), optimize=True)
    veins = Image.new("RGBA", src.size, (0, 0, 0, 0))
    out = veins.load()
    for x, y in keep:
        p = px[x, y]
        if p[3] > 0 and vein(design, p, lum(p)):
            out[x, y] = (255, 255, 255, 255)
    veins.save(os.path.join(VALOR, "wings_" + design + "_veins.png"), optimize=True)


for design, source, tex, wings, turns in SOURCES:
    uvs = bake(design, source, wings, turns)
    # the Kintsugi's sheet is 1024 square: half that is plenty at a wing's size (its UVs are fractions)
    paint(design, tex, uvs, (512, 512) if source == "kintsugi" else None)
paint("zephyrus", "textures/entity/dragon/zephyrus/hide_0.png", zephyrus_uvs())
print("baked notus and eurus wings; painted notus, zephyrus and eurus in", len(LOOKS), "looks")
