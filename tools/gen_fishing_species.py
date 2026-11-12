"""Fishing Track C/D art and data: every new species' model, texture, item icons and the blocks of the new tackle.

Run from the repo root: python -I tools/gen_fishing_species.py

Writes (all our own art, made here from scratch):
- src/client/java/com/jaymospowers/client/fishing/FishShapes.java  (the boxes of every species' model, generated)
- src/main/java/com/jaymospowers/fishing/SpeciesSizes.java          (hitbox and draw scale per species, generated)
- textures/entity/fish/<id>.png, textures/item/<id>.png (+ cooked_<id>, <id>_spawn_egg), item models and item defs
- cooking recipes, item tags, the tackle blocks' textures, block models and blockstates, lang/parts/fishing_species.json

Each model is built from a few numbers (body length, depth, head, fins...), and the texture is painted face by face
from the same boxes, so the two always line up: the back colour on top, the belly underneath, the pattern down the sides.
"""
import json
import math
import os
import random

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/jaymospowers")
DATA = os.path.join(ROOT, "src/main/resources/data")
CLIENT_JAVA = os.path.join(ROOT, "src/client/java/com/jaymospowers/client/fishing/FishShapes.java")
MAIN_JAVA = os.path.join(ROOT, "src/main/java/com/jaymospowers/fishing/SpeciesSizes.java")

# roles the client model animates (FishShapeModel): keep in step with it
STATIC, STALK, TAIL, FIN_L, FIN_R, SEG, LEG, CLAW, TENTACLE, BELL, FLAT_STALK, FLAT_TAIL, CURL = range(13)


def hexc(c, a=255):
    return ((c >> 16) & 255, (c >> 8) & 255, c & 255, a)


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(4))


def shade(c, f):
    return (max(0, min(255, int(c[0] * f))), max(0, min(255, int(c[1] * f))), max(0, min(255, int(c[2] * f))), c[3])


# --------------------------------------------------------------------------------------------------------------------
# Species: id, English name, shape, palette, what it gives. Gameplay numbers live in Species.java.
# --------------------------------------------------------------------------------------------------------------------

SPECIES = [
    # freshwater
    dict(id="carp", name="Carp", fillet="white", len=0.55,
         shape=dict(kind="fish", L=10, H=6, W=3.5, Lh=3, Hh=5, Ls=2, Hs=3, Lt=3, Ht=6, Hd=2.5, Ld=6, dpos=0.15, barbels=1),
         pal=dict(back=0x5e4f24, flank=0xb08a38, belly=0xe6d59a, fin=0x8a5a34, pattern="scales", pc=0x7a6230)),
    dict(id="pike", name="Pike", fillet="white", len=0.75,
         shape=dict(kind="fish", L=13, H=3.5, W=3, Lh=5, Hh=2.5, Ls=2, Hs=2.5, Lt=3, Ht=5, Hd=2.5, Ld=3, dpos=0.75, apos=0.75),
         pal=dict(back=0x34421e, flank=0x5c7632, belly=0xece8d2, fin=0x9a7438, pattern="beans", pc=0xd2d28a)),
    dict(id="perch", name="Perch", fillet="white", len=0.25,
         shape=dict(kind="fish", L=7, H=4.5, W=2.5, Lh=3, Hh=4, Ls=1.5, Hs=2, Lt=2.5, Ht=4, Hd=2.5, Ld=4, dpos=0.1, dorsal2=1),
         pal=dict(back=0x3a4c22, flank=0x9aaa46, belly=0xf0ecd8, fin=0xd24e26, pattern="bars", pc=0x2a3418)),
    dict(id="zander", name="Zander", fillet="white", len=0.55,
         shape=dict(kind="fish", L=11, H=3.5, W=2.5, Lh=4, Hh=3, Ls=2, Hs=2, Lt=3, Ht=4.5, Hd=2.5, Ld=3.5, dpos=0.1, dorsal2=1),
         pal=dict(back=0x525e58, flank=0xa4aca6, belly=0xf2f2ea, fin=0x8a908a, pattern="bars", pc=0x6a726c, eye=0xe8e8c8)),
    dict(id="bream", name="Bream", fillet="white", len=0.42,
         shape=dict(kind="fish", L=8, H=7, W=2, Lh=2.5, Hh=4, Ls=1.5, Hs=2, Lt=3, Ht=6, Hd=3, Ld=2.5, dpos=0.35, apos=0.5, Ha=2),
         pal=dict(back=0x48422f, flank=0x9c8956, belly=0xd8cfae, fin=0x4c4a44, pattern="scales", pc=0x857547)),
    dict(id="tench", name="Tench", fillet="white", len=0.36,
         shape=dict(kind="fish", L=8, H=4.5, W=3, Lh=3, Hh=4, Ls=2, Hs=3, Lt=2.5, Ht=4.5, Hd=2.5, Ld=2.5, dpos=0.4, barbels=1),
         pal=dict(back=0x2c381a, flank=0x5c6828, belly=0xbcb258, fin=0x2e3420, pattern="smooth", pc=0x4a5422, eye=0xc8302a)),
    dict(id="catfish", name="Catfish", fillet="white", len=1.3,
         shape=dict(kind="fish", L=16, H=4, W=4, Lh=4, Hh=3.5, Wh=5, Ls=3, Hs=3, Lt=2.5, Ht=3.5, Hd=1.5, Ld=1.5, dpos=0.12,
                    apos=0.3, Ha=1.5, La=11, barbels=2),
         pal=dict(back=0x282a22, flank=0x585846, belly=0xb6b29e, fin=0x34342a, pattern="marble", pc=0x383828)),
    dict(id="eel", name="Eel", fillet="oily", len=0.65,
         shape=dict(kind="eel", segs=5, Lseg=4, H=2, W=1.5, Lh=2.5),
         pal=dict(back=0x383220, flank=0x686030, belly=0xc6b658, fin=0x4a4426, pattern="smooth", pc=0x383220)),
    dict(id="trout", name="Trout", fillet="oily", len=0.42,
         shape=dict(kind="fish", L=9, H=3.5, W=2.5, Lh=3, Hh=3, Ls=2, Hs=2, Lt=2.5, Ht=4, Hd=2.5, Ld=3, dpos=0.3, adipose=1),
         pal=dict(back=0x584828, flank=0xae8e4e, belly=0xf0e4c0, fin=0x8a6a3a, pattern="trout", pc=0x1e1a14, pc2=0xc03a22)),
    dict(id="chub", name="Chub", fillet="white", len=0.38,
         shape=dict(kind="fish", L=8, H=3.5, W=3, Lh=3, Hh=3, Ls=2, Hs=2, Lt=2.5, Ht=4, Hd=2.5, Ld=2.5, dpos=0.4),
         pal=dict(back=0x3c4438, flank=0xb2ae96, belly=0xf4f2ea, fin=0xd06a3a, pattern="scales", pc=0x8e8a74)),
    # northern
    dict(id="char", name="Arctic Char", fillet="oily", len=0.45,
         shape=dict(kind="fish", L=9, H=3.5, W=2.5, Lh=3, Hh=3, Ls=2, Hs=2, Lt=2.5, Ht=4, Hd=2.5, Ld=3, dpos=0.3, adipose=1),
         pal=dict(back=0x384230, flank=0x8a9a86, belly=0xd65e2a, fin=0xc8542a, pattern="spots", pc=0xecb4a0)),
    dict(id="grayling", name="Grayling", fillet="white", len=0.36,
         shape=dict(kind="fish", L=8, H=3, W=2, Lh=2.5, Hh=2.5, Ls=2, Hs=2, Lt=2.5, Ht=4, Hd=4.5, Ld=5, dpos=0.1, adipose=1),
         pal=dict(back=0x585662, flank=0xb6b8c0, belly=0xeeeef2, fin=0x88385a, pattern="spots", pc=0x2a2a32)),
    dict(id="whitefish", name="Whitefish", fillet="white", len=0.38,
         shape=dict(kind="fish", L=8, H=3.5, W=2.5, Lh=2, Hh=2.5, Ls=2, Hs=2, Lt=3, Ht=4.5, Hd=2.5, Ld=2.5, dpos=0.35, adipose=1),
         pal=dict(back=0x58687a, flank=0xc6ced6, belly=0xfafafa, fin=0x8a96a0, pattern="scales", pc=0xa8b2bc)),
    dict(id="burbot", name="Burbot", fillet="white", len=0.5,
         shape=dict(kind="fish", L=11, H=3, W=3, Lh=3, Hh=2.5, Wh=3.5, Ls=2, Hs=2, Lt=2, Ht=3, Hd=1.5, Ld=8, dpos=0.3,
                    apos=0.35, Ha=1.5, La=7, barbels=3),
         pal=dict(back=0x584828, flank=0x7a6a3a, belly=0xc6ba88, fin=0x4a3e22, pattern="marble", pc=0x2c2214)),
    # sea
    dict(id="mackerel", name="Mackerel", fillet="oily", len=0.35,
         shape=dict(kind="fish", L=9, H=3, W=2.5, Lh=3, Hh=2.5, Ls=2.5, Hs=1, Lt=3, Ht=5, Hd=2, Ld=2, dpos=0.15, dorsal2=1, fork=1),
         pal=dict(back=0x2a6a68, flank=0xa8c2ca, belly=0xffffff, fin=0x3c5a5a, pattern="waves", pc=0x0e1e1c)),
    dict(id="herring", name="Herring", fillet="oily", len=0.26,
         shape=dict(kind="fish", L=7, H=2.5, W=1.5, Lh=2, Hh=2, Ls=1.5, Hs=1, Lt=2.5, Ht=3.5, Hd=2, Ld=2, dpos=0.4, fork=1),
         pal=dict(back=0x385a72, flank=0xd6dee6, belly=0xffffff, fin=0x8aa0b0, pattern="smooth", pc=0x9aaab8)),
    dict(id="pollock", name="Pollock", fillet="white", len=0.62,
         shape=dict(kind="fish", L=11, H=3.5, W=2.5, Lh=3.5, Hh=3, Ls=2, Hs=1.5, Lt=3, Ht=5, Hd=2, Ld=2.5, dpos=0.1, dorsal2=1,
                    apos=0.45, Ha=1.5, La=3),
         pal=dict(back=0x384838, flank=0x8a8a68, belly=0xe6e6de, fin=0x4a5440, pattern="line", pc=0x26301e)),
    dict(id="sea_bass", name="Sea Bass", fillet="white", len=0.5,
         shape=dict(kind="fish", L=9, H=4, W=2.5, Lh=3.5, Hh=3.5, Ls=2, Hs=2, Lt=3, Ht=5, Hd=2.5, Ld=3, dpos=0.1, dorsal2=1),
         pal=dict(back=0x485664, flank=0xbec6ce, belly=0xfafafa, fin=0x6a7480, pattern="scales", pc=0x98a2ac)),
    dict(id="halibut", name="Halibut", fillet="white", len=1.1,
         shape=dict(kind="flat", L=12, W=8, H=2, Lh=2.5, Ls=2, Ws=3, Lt=3, Wt=7),
         pal=dict(back=0x48402e, flank=0x5e5440, belly=0xf2f2ec, fin=0x3e3828, pattern="marble", pc=0x2a261c)),
    dict(id="swordfish", name="Swordfish", fillet="oily", len=2.4,
         shape=dict(kind="fish", L=14, H=4.5, W=3.5, Lh=3, Hh=3.5, Ls=3, Hs=1.5, Lt=3, Ht=8, Hd=5, Ld=3, dpos=0.0, bill=8,
                    fork=1, pec=4),
         pal=dict(back=0x382838, flank=0x686070, belly=0xb2b2ba, fin=0x2e2230, pattern="smooth", pc=0x504860)),
    # sharks
    dict(id="catshark", name="Catshark", fillet="white", len=0.6,
         shape=dict(kind="shark", L=10, H=2.5, W=2.5, Lh=3, Hh=2, Ls=3, Hs=1.5, Lt=3, Ht=2.5, Hd=1.5, Ld=1.5, dpos=0.6,
                    dorsal2=1, pec=2.5),
         pal=dict(back=0xb6986a, flank=0xc6aa7e, belly=0xeee2c8, fin=0xa48658, pattern="spots", pc=0x4a3420)),
    dict(id="dogfish", name="Dogfish", fillet="white", len=0.9,
         shape=dict(kind="shark", L=12, H=2.5, W=2.5, Lh=3, Hh=2, Ls=3, Hs=1.5, Lt=3, Ht=3.5, Hd=2, Ld=2, dpos=0.25,
                    dorsal2=1, pec=3),
         pal=dict(back=0x66665c, flank=0x7c7c70, belly=0xf0f0ea, fin=0x5a5a50, pattern="spots", pc=0xe2e2da)),
    dict(id="blue_shark", name="Blue Shark", fillet="none", len=2.6,
         shape=dict(kind="shark", L=16, H=3.5, W=3.5, Lh=4.5, Hh=2.5, Ls=4, Hs=2, Lt=4, Ht=6, Hd=3.5, Ld=3, dpos=0.3, pec=7),
         pal=dict(back=0x28489a, flank=0x4a70c8, belly=0xf8f8fc, fin=0x203c84, pattern="smooth", pc=0x3a5cb0)),
    dict(id="hammerhead", name="Hammerhead", fillet="none", len=3.0,
         shape=dict(kind="shark", L=16, H=4, W=4, Lh=3, Hh=2, Ls=4, Hs=2, Lt=4, Ht=7, Hd=6, Ld=3, dpos=0.25, pec=5, hammer=12),
         pal=dict(back=0x76766a, flank=0x8e8e80, belly=0xf4f4ee, fin=0x66665c, pattern="smooth", pc=0x7a7a6e)),
    dict(id="great_white", name="Great White", fillet="none", len=4.0,
         shape=dict(kind="shark", L=16, H=6, W=5, Lh=5, Hh=4.5, Ls=4, Hs=2.5, Lt=4, Ht=9, Hd=5, Ld=4, dpos=0.25, pec=6),
         pal=dict(back=0x565c64, flank=0x6a7078, belly=0xf2f2f2, fin=0x4c5258, pattern="countershade", pc=0x1c1c1c)),
    # critters
    dict(id="shrimp", name="Shrimp", fillet="shell", len=0.12, cooked_tint=0xe8603a,
         shape=dict(kind="shrimp"),
         pal=dict(back=0xc4a29a, flank=0xd8bcb4, belly=0xe8d6d0, fin=0xb08880, pattern="bands", pc=0x9a726a)),
    dict(id="crayfish", name="Crayfish", fillet="shell", len=0.14, cooked_tint=0xd2401e,
         shape=dict(kind="lobster", body=3, claw=2),
         pal=dict(back=0x54442a, flank=0x6a5634, belly=0x8a7650, fin=0x86381e, pattern="smooth", pc=0x3e3220)),
    dict(id="crab", name="Crab", fillet="shell", len=0.22, cooked_tint=0xd84a24,
         shape=dict(kind="crab"),
         pal=dict(back=0x4a5a2a, flank=0x5e6c34, belly=0xc8b880, fin=0x8a6a2a, pattern="marble", pc=0x36421e)),
    dict(id="lobster", name="Lobster", fillet="shell", len=0.45, cooked_tint=0xd2321c,
         shape=dict(kind="lobster", body=4, claw=3),
         pal=dict(back=0x1e2a5a, flank=0x2c3a6e, belly=0x6a6a80, fin=0xd86a2a, pattern="spots", pc=0xd6c690)),
    dict(id="jellyfish", name="Jellyfish", fillet="jelly", len=0.3,
         shape=dict(kind="jelly"),
         pal=dict(back=0xd6c6ee, flank=0xc8b6e6, belly=0xb8a4dc, fin=0xe6dcf6, pattern="rings", pc=0x9a6ac8)),
]


# --------------------------------------------------------------------------------------------------------------------
# Model building: parts with boxes in model pixels (y down, head towards -z, the ground at y = 24).
# --------------------------------------------------------------------------------------------------------------------

class Part:
    def __init__(self, name, parent, box, offset, rot=(0, 0, 0), role=STATIC, index=0, paint="body"):
        self.name, self.parent, self.box, self.offset, self.rot = name, parent, box, offset, rot
        self.role, self.index, self.paint = role, index, paint
        self.u = self.v = 0

    def size(self):
        x, y, z, w, h, d = self.box
        return w, h, d


def fish_parts(s):
    p = []
    L, H, W = s["L"], s["H"], s["W"]
    Lh, Hh, Wh = s["Lh"], s["Hh"], s.get("Wh", W * 0.9)
    Ls, Hs, Lt, Ht = s["Ls"], s["Hs"], s["Lt"], s["Ht"]
    Ws = max(1.0, W * 0.5)
    total = Lh + L + Ls + Lt
    z0 = -total / 2 + Lh
    y0 = 24 - max(H, Ht) / 2 - 0.5
    shark = s["kind"] == "shark"
    p.append(Part("body", None, (-W / 2, -H / 2, 0, W, H, L), (0, y0, z0)))
    if shark:
        # a shark's snout: the head narrows and the mouth sits under it
        p.append(Part("head", None, (-Wh / 2, -Hh / 2, -Lh, Wh, Hh, Lh), (0, y0 - (H - Hh) / 2 + 0.2, z0)))
    else:
        p.append(Part("head", None, (-Wh / 2, -Hh / 2, -Lh, Wh, Hh, Lh), (0, y0 - (H - Hh) * 0.15, z0)))
    p.append(Part("stalk", None, (-Ws / 2, -Hs / 2, 0, Ws, Hs, Ls), (0, y0 - (H - Hs) * 0.1, z0 + L), role=STALK))
    if shark:
        # the upper lobe longer than the lower
        p.append(Part("tail", "stalk", (0, -Ht * 0.75, 0, 0, Ht, Lt), (0, 0, Ls - 0.5), rot=(0.45, 0, 0), role=TAIL, paint="fin"))
    elif s.get("fork"):
        p.append(Part("tail", "stalk", (0, -Ht / 2, 0, 0, Ht, Lt), (0, 0, Ls - 0.3), role=TAIL, paint="fork"))
    else:
        p.append(Part("tail", "stalk", (0, -Ht / 2, 0, 0, Ht, Lt), (0, 0, Ls - 0.3), role=TAIL, paint="fin"))
    Hd, Ld = s["Hd"], s["Ld"]
    p.append(Part("dorsal", None, (0, -Hd, 0, 0, Hd, Ld), (0, y0 - H / 2, z0 + s["dpos"] * L), rot=(-0.15 if shark else 0, 0, 0), paint="fin"))
    if s.get("dorsal2"):
        p.append(Part("dorsal2", None, (0, -Hd * 0.6, 0, 0, Hd * 0.6, Ld * 0.8), (0, y0 - H / 2, z0 + min(0.8, s["dpos"] + 0.45) * L), paint="fin"))
    if s.get("adipose"):
        p.append(Part("adipose", None, (0, -1, 0, 0, 1, 1.5), (0, y0 - H / 2, z0 + L * 0.85), paint="fin"))
    pec = s.get("pec", 2.0)
    angle = 0.25 if shark else math.pi / 5
    p.append(Part("left_fin", None, (0, 0, 0, pec, 0, pec * 0.8), (W / 2, y0 + H / 4, z0 + 0.5), rot=(0, 0, angle), role=FIN_L, paint="fin"))
    p.append(Part("right_fin", None, (-pec, 0, 0, pec, 0, pec * 0.8), (-W / 2, y0 + H / 4, z0 + 0.5), rot=(0, 0, -angle), role=FIN_R, paint="fin"))
    Ha, La = s.get("Ha", 1.5), s.get("La", 2)
    p.append(Part("anal", None, (0, 0, 0, 0, Ha, La), (0, y0 + H / 2, z0 + s.get("apos", 0.6) * L), paint="fin"))
    b = s.get("barbels", 0)
    if b:
        n = 2 if b == 1 else (4 if b == 2 else 1)
        reach = 1.5 if b == 1 else (6 if b == 2 else 1.5)
        hy = y0 + Hh / 2 - 0.5
        for i in range(n):
            if b == 3:
                p.append(Part("barbel0", None, (-0.25, 0, -0.25, 0.5, reach, 0.5), (0, hy, z0 - Lh + 0.8), paint="fin"))
                break
            side = -1 if i % 2 == 0 else 1
            up = 0.0 if i < 2 else 1.0
            p.append(Part("barbel%d" % i, None, (-0.25, -0.25, 0, 0.5, 0.5, reach), (side * (Wh / 2 - 0.3), hy - up, z0 - Lh + 0.4),
                          rot=(0.3, side * 2.6, 0), paint="fin"))
    if s.get("bill"):
        p.append(Part("bill", "head", (-0.5, -0.5, -s["bill"], 1, 1, s["bill"]), (0, 0, -Lh), paint="bill"))
    if s.get("hammer"):
        hw = s["hammer"]
        p.append(Part("hammer", "head", (-hw / 2, -0.75, -2, hw, 1.5, 2), (0, 0, -Lh + 1), paint="body"))
    return p


def eel_parts(s):
    p = []
    n, Ls, H, W, Lh = s["segs"], s["Lseg"], s["H"], s["W"], s["Lh"]
    total = Lh + n * Ls
    z0 = -total / 2 + Lh
    y0 = 24 - H / 2 - 0.5
    p.append(Part("head", None, (-W / 2, -H / 2, -Lh, W, H, Lh), (0, y0, z0)))
    parent = None
    for i in range(n):
        name = "seg%d" % i
        taper = 1.0 - 0.12 * i
        p.append(Part(name, parent, (-W * taper / 2, -H * taper / 2, 0, W * taper, H * taper, Ls),
                      (0, y0, z0) if parent is None else (0, 0, Ls), role=SEG, index=i))
        # a low fin along the back and belly of the back half
        if i >= 1:
            p.append(Part("fin%d" % i, name, (0, -H * taper / 2 - 0.8, 0, 0, H * taper + 1.6, Ls), (0, 0, 0), paint="fin"))
        parent = name
    return p


def flat_parts(s):
    # a flatfish lies on its side: wide and thin, eyes on top, fins round the edge, the tail in the flat
    p = []
    L, W, H, Lh, Ls, Ws, Lt, Wt = s["L"], s["W"], s["H"], s["Lh"], s["Ls"], s["Ws"], s["Lt"], s["Wt"]
    total = Lh + L + Ls + Lt
    z0 = -total / 2 + Lh
    y0 = 24 - H / 2 - 0.1
    p.append(Part("body", None, (-W / 2, -H / 2, 0, W, H, L), (0, y0, z0)))
    p.append(Part("head", None, (-W * 0.3, -H / 2, -Lh, W * 0.6, H, Lh), (0, y0, z0)))
    p.append(Part("fringe_l", None, (0, 0, 0, 1.5, 0, L * 0.9), (W / 2, y0, z0 + L * 0.05), role=FIN_L, paint="fin"))
    p.append(Part("fringe_r", None, (-1.5, 0, 0, 1.5, 0, L * 0.9), (-W / 2, y0, z0 + L * 0.05), role=FIN_R, paint="fin"))
    p.append(Part("stalk", None, (-Ws / 2, -H / 2 + 0.3, 0, Ws, H - 0.6, Ls), (0, y0, z0 + L), role=FLAT_STALK))
    p.append(Part("tail", "stalk", (-Wt / 2, 0, 0, Wt, 0, Lt), (0, 0, Ls - 0.3), role=FLAT_TAIL, paint="fin"))
    return p


def shrimp_parts(s):
    p = []
    y0 = 22.5
    p.append(Part("head", None, (-1, -1, -3, 2, 2, 3), (0, y0, -1)))
    p.append(Part("rostrum", "head", (-0.25, -0.5, -2, 0.5, 0.5, 2), (0, -0.5, -3), paint="fin"))
    for side in (-1, 1):
        p.append(Part("feeler%s" % ("l" if side > 0 else "r"), "head", (0, 0, -7, 0, 0.5, 7), (side * 0.6, 0, -3),
                      rot=(0.2, side * 0.25, 0), role=TENTACLE, index=0 if side > 0 else 1, paint="fin"))
    parent = None
    for i in range(4):
        name = "seg%d" % i
        w = 2.0 - 0.3 * i
        p.append(Part(name, parent, (-w / 2, -w / 2, 0, w, w, 1.6), (0, y0, -1) if parent is None else (0, 0.1, 1.6),
                      rot=(0.18, 0, 0), role=CURL, index=i))
        parent = name
    p.append(Part("fan", parent, (-1.5, 0, 0, 3, 0, 2), (0, 0, 1.6), rot=(0.2, 0, 0), paint="fin"))
    for i in range(4):
        p.append(Part("leg%d" % i, "head" if i < 2 else "seg0", (-0.25, 0, -0.25, 0.5, 1.5, 0.5), (0, 0.9, -2.2 + i * 0.9 if i < 2 else -0.5 + (i - 2) * 0.8),
                      role=LEG, index=i, paint="fin"))
    return p


def crab_parts(s):
    p = []
    y0 = 21.5
    p.append(Part("body", None, (-4, -1.5, -3, 8, 3, 6), (0, y0, 0)))
    for side in (-1, 1):
        sx = "l" if side > 0 else "r"
        p.append(Part("eye_" + sx, "body", (-0.5, -1.5, -0.5, 1, 1.5, 1), (side * 1.2, -1.5, -2.5), paint="eye"))
        p.append(Part("arm_" + sx, "body", (-0.75 if side < 0 else -0.75, -0.75, -2.5, 1.5, 1.5, 2.5), (side * 3.2, 0.5, -2.5),
                      rot=(0, -side * 0.5, 0), paint="body"))
        p.append(Part("claw_" + sx, "arm_" + sx, (-1.25, -1.25, -3, 2.5, 2.5, 3), (0, 0, -2.5), role=CLAW, index=0 if side > 0 else 1, paint="claw"))
        for i in range(3):
            p.append(Part("leg_%s%d" % (sx, i), "body", (0 if side > 0 else -4, -0.5, -0.5, 4, 1, 1), (side * 4, 0.5, -1 + i * 1.8),
                          rot=(0, 0, side * 0.6), role=LEG, index=i + (0 if side > 0 else 3), paint="claw"))
    return p


def lobster_parts(s):
    p = []
    b, c = s["body"], s["claw"]
    y0 = 24 - b / 2 - 1.2
    p.append(Part("body", None, (-b / 2, -b / 2, -b * 1.6, b, b, b * 1.6), (0, y0, 0)))
    parent = "body"
    for i in range(3):
        w = b * (0.9 - 0.12 * i)
        name = "tail%d" % i
        p.append(Part(name, parent, (-w / 2, -w / 2 + 0.3, 0, w, w * 0.8, b * 0.6), (0, 0, 0 if i == 0 else b * 0.6), rot=(0.08, 0, 0),
                      role=CURL, index=i))
        parent = name
    p.append(Part("fan", parent, (-b * 0.7, 0, 0, b * 1.4, 0, b * 0.7), (0, 0.2, b * 0.6), paint="claw"))
    for side in (-1, 1):
        sx = "l" if side > 0 else "r"
        p.append(Part("arm_" + sx, "body", (-0.5, -0.5, -c * 1.2, 1, 1, c * 1.2), (side * b * 0.4, b * 0.2, -b * 1.6), rot=(0, -side * 0.35, 0),
                      paint="body"))
        p.append(Part("claw_" + sx, "arm_" + sx, (-c * 0.4, -c * 0.3, -c * 1.6, c * 0.8, c * 0.6, c * 1.6), (0, 0, -c * 1.2), role=CLAW,
                      index=0 if side > 0 else 1, paint="claw"))
        p.append(Part("feeler_" + sx, "body", (0, 0, -b * 3, 0, 0.5, b * 3), (side * 0.4, -b / 2 + 0.3, -b * 1.6), rot=(0.25, side * 0.3, 0),
                      role=TENTACLE, index=0 if side > 0 else 1, paint="claw"))
        for i in range(3):
            p.append(Part("leg_%s%d" % (sx, i), "body", (0 if side > 0 else -b * 0.6, -0.25, -0.25, b * 0.6, 0.5, 0.5),
                          (side * b / 2, b * 0.3, -b * 1.3 + i * b * 0.4), rot=(0, 0, side * 0.7), role=LEG,
                          index=i + (0 if side > 0 else 3), paint="claw"))
    return p


def jelly_parts(s):
    p = []
    y0 = 14
    p.append(Part("bell", None, (-4, -3, -4, 8, 3, 8), (0, y0, 0), role=BELL))
    p.append(Part("rim", "bell", (-4.5, 0, -4.5, 9, 1, 9), (0, 0, 0), paint="rim"))
    for i in range(8):
        a = i / 8 * 2 * math.pi
        p.append(Part("tentacle%d" % i, "bell", (-0.5, 0, 0, 1, 9, 0), (math.cos(a) * 3.2, 1, math.sin(a) * 3.2), rot=(0, -a, 0),
                      role=TENTACLE, index=i, paint="tentacle"))
    for i in range(4):
        a = (i + 0.5) / 4 * 2 * math.pi
        p.append(Part("arm%d" % i, "bell", (-1, 0, 0, 2, 6, 0), (math.cos(a) * 1.2, 0.5, math.sin(a) * 1.2), rot=(0, -a, 0),
                      role=TENTACLE, index=i + 8, paint="arm"))
    return p


def parts_for(sp):
    k = sp["shape"]["kind"]
    return {"fish": fish_parts, "shark": fish_parts, "eel": eel_parts, "flat": flat_parts, "shrimp": shrimp_parts,
            "crab": crab_parts, "lobster": lobster_parts, "jelly": jelly_parts}[k](sp["shape"])


# --------------------------------------------------------------------------------------------------------------------
# UV packing and painting
# --------------------------------------------------------------------------------------------------------------------

def uv_footprint(part):
    w, h, d = (int(math.ceil(v)) for v in part.size())
    return max(1, 2 * d + 2 * w), max(1, d + h)


def pack(parts, tw=64):
    x = y = row_h = 0
    for p in sorted(parts, key=lambda q: -uv_footprint(q)[1]):
        fw, fh = uv_footprint(p)
        if x + fw > tw:
            x, y, row_h = 0, y + row_h, 0
        p.u, p.v = x, y
        x += fw
        row_h = max(row_h, fh)
    total = y + row_h
    th = 32
    while th < total:
        th *= 2
    return tw, th


def absolute(parts):
    """Each part's box corners in the model's own space (pivots added up, rotations ignored: good enough to paint by)."""
    by = {p.name: p for p in parts}
    out = {}
    for p in parts:
        ox, oy, oz = p.offset
        q = p
        while q.parent:
            q = by[q.parent]
            ox, oy, oz = ox + q.offset[0], oy + q.offset[1], oz + q.offset[2]
        x, y, z, w, h, d = p.box
        out[p.name] = (ox + x, oy + y, oz + z, w, h, d)
    return out


def bounds(parts):
    ab = absolute(parts).values()
    zs = [b[2] for b in ab] + [b[2] + b[5] for b in ab]
    ys = [b[1] for b in ab] + [b[1] + b[4] for b in ab]
    xs = [b[0] for b in ab] + [b[0] + b[3] for b in ab]
    return min(xs), max(xs), min(ys), max(ys), min(zs), max(zs)


def painter(sp, parts):
    pal = sp["pal"]
    back, flank, belly, fin = hexc(pal["back"]), hexc(pal["flank"]), hexc(pal["belly"]), hexc(pal["fin"])
    pc = hexc(pal.get("pc", pal["back"]))
    pc2 = hexc(pal.get("pc2", pal.get("pc", pal["back"])))
    kind = sp["shape"]["kind"]
    pattern = pal["pattern"]
    rnd = random.Random(sp["id"])
    x0, x1, y0, y1, z0, z1 = bounds(parts)
    spots = [(rnd.random(), rnd.random()) for _ in range(28)]

    def body(zf, yf, side):
        # zf 0 head .. 1 tail, yf 0 top .. 1 belly
        if kind in ("crab", "lobster"):
            c = mix(back, flank, yf)
        elif pattern == "countershade":
            c = back if yf < 0.55 else (mix(back, belly, (yf - 0.55) * 8) if yf < 0.68 else belly)
        else:
            c = mix(back, flank, yf * 2.2) if yf < 0.45 else mix(flank, belly, (yf - 0.45) * 2.4)
        n = rnd.random()
        if pattern == "scales":
            if (int(zf * 40) + int(yf * 12)) % 2 == 0:
                c = mix(c, pc, 0.18)
        elif pattern == "bars" and yf < 0.7:
            if 0.15 < zf < 0.8 and int(zf * 16) % 3 == 0:
                c = mix(c, pc, 0.75)
        elif pattern == "beans" and 0.2 < yf < 0.75:
            if (int(zf * 30) + int(yf * 9) * 3) % 5 == 0:
                c = mix(c, pc, 0.8)
        elif pattern in ("spots", "trout") and yf < 0.8:
            for i, (sz, sy) in enumerate(spots):
                if abs(zf - sz) < 0.035 and abs(yf - sy * 0.75) < 0.07:
                    c = mix(c, pc2 if pattern == "trout" and i % 3 == 0 else pc, 0.85)
        elif pattern == "marble":
            m = math.sin(zf * 23 + math.sin(yf * 11) * 2) * math.cos(yf * 17 + zf * 7)
            if m > 0.35 and yf < 0.75:
                c = mix(c, pc, 0.6)
        elif pattern == "waves" and yf < 0.42:
            if math.sin(zf * 50 + math.sin(yf * 30) * 2.5) > 0.45:
                c = mix(c, pc, 0.8)
        elif pattern == "line" and abs(yf - (0.38 - 0.08 * math.sin(zf * 3))) < 0.04:
            c = mix(c, pc, 0.8)
        elif pattern == "bands" and int(zf * 12) % 2 == 0:
            c = mix(c, pc, 0.35)
        elif pattern == "rings":
            pass
        return shade(c, 0.94 + n * 0.1)

    def colour(part, face, a, b):
        """a, b: 0..1 across the face; returns RGBA."""
        ab = absolute(parts)[part.name]
        bx, by, bz, w, h, d = ab
        paint = part.paint
        if kind == "jelly":
            if paint == "bell":
                pass
            if paint == "tentacle":
                return mix(hexc(pal["fin"], 150), hexc(pal["fin"], 60), b)
            if paint == "arm":
                return mix(hexc(pal["flank"], 190), hexc(pal["pc"], 140), b)
            if paint == "rim":
                return hexc(pal["fin"], 170)
            # the bell: see-through, with four violet rings seen from above
            if face in ("top", "bottom"):
                dx, dz = a - 0.5, b - 0.5
                r = math.hypot(dx, dz)
                ang = math.atan2(dz, dx)
                for k in range(4):
                    ka = k * math.pi / 2 + math.pi / 4
                    if abs(math.hypot(dx - math.cos(ka) * 0.18, dz - math.sin(ka) * 0.18) - 0.09) < 0.04:
                        return hexc(pal["pc"], 220)
                return mix(hexc(pal["back"], 170), hexc(pal["flank"], 120), r * 2)
            return mix(hexc(pal["back"], 160), hexc(pal["belly"], 120), b)
        if paint == "eye":
            return (20, 18, 16, 255) if b < 0.5 else shade(back, 0.8)
        if paint in ("fin", "fork"):
            edge = 1.0 - b if face in ("west", "east") else 1.0
            c = mix(fin, shade(fin, 0.7), a * 0.4)
            if paint == "fork" and face in ("west", "east"):
                # a forked tail: the middle of the trailing edge cut away
                tz = a if face == "east" else 1 - a
                if tz > 0.55 and 0.4 < b < 0.6:
                    return (0, 0, 0, 0)
            if sp["id"] == "char" and face in ("west", "east") and b > 0.8:
                return (240, 240, 236, 255)
            if sp["id"] == "great_white" and part.name.endswith("_fin"):
                return mix(fin, (20, 20, 20, 255), a)
            return c
        if paint == "claw":
            return mix(hexc(pal["fin"]), shade(hexc(pal["fin"]), 0.75), b * 0.6)
        if paint == "bill":
            return mix(back, shade(back, 0.7), b)
        # body, head, stalk, segments: by where on the fish this is
        def zf_of(t):
            return ((bz + t * d) - z0) / max(0.001, z1 - z0)

        def yf_of(t):
            return ((by + t * h) - y0) / max(0.001, y1 - y0)

        if kind == "flat":
            if face == "top":
                c = body(zf_of(1 - b), 0.15 + 0.3 * abs(a - 0.5), "top")
                if part.name == "head" and 0.55 < b < 0.85 and (abs(a - 0.35) < 0.12 or abs(a - 0.6) < 0.12):
                    return (18, 16, 14, 255) if 0.65 < b < 0.78 else (220, 210, 170, 255)
                return c
            if face == "bottom":
                return shade(belly, 0.97 + 0.03 * a)
            return mix(back, belly, b)
        if face == "top":
            c = body(zf_of(1 - b), 0.02, "top")
        elif face == "bottom":
            c = body(zf_of(1 - b), 0.98, "bottom")
        elif face == "west":
            c = body(zf_of(1 - a), yf_of(b), "side")
        elif face == "east":
            c = body(zf_of(a), yf_of(b), "side")
        elif face == "north":
            c = body(0.0, yf_of(b), "front")
        else:
            c = body(1.0, yf_of(b), "back")
        if part.name == "head" and kind in ("fish", "shark", "eel") and face in ("west", "east"):
            # the eye, near the front of the head and above the middle; a gill line behind
            front = (1 - a) if face == "west" else a
            ex = 0.22 if kind != "shark" else 0.35
            if abs(front - ex) < 0.14 and abs(b - 0.35) < 0.18:
                eye = hexc(pal.get("eye", 0xd8c890))
                return (14, 12, 10, 255) if abs(front - ex) < 0.07 and abs(b - 0.35) < 0.09 else eye
            if kind != "shark" and front > 0.85 and b > 0.15:
                return shade(c, 0.78)
            if kind == "shark" and front > 0.55 and int(front * 10) % 2 == 0 and 0.3 < b < 0.7:
                return shade(c, 0.75)
        if kind == "fish" and part.name == "body" and face in ("west", "east") and abs(yf_of(b) - 0.42) < 0.03:
            # the lateral line
            c = shade(c, 0.88)
        return c

    return colour


def paint_texture(sp, parts, tw, th):
    img = Image.new("RGBA", (tw, th), (0, 0, 0, 0))
    px = img.load()
    colour = painter(sp, parts)
    for p in parts:
        w, h, d = (int(math.ceil(v)) for v in p.size())
        u, v = p.u, p.v
        faces = [
            ("top", u + d, v, w, d), ("bottom", u + d + w, v, w, d),
            ("west", u, v + d, d, h), ("north", u + d, v + d, w, h),
            ("east", u + d + w, v + d, d, h), ("south", u + 2 * d + w, v + d, w, h),
        ]
        for face, fx, fy, fw, fh in faces:
            for i in range(fw):
                for j in range(fh):
                    a = (i + 0.5) / fw
                    b = (j + 0.5) / fh
                    if 0 <= fx + i < tw and 0 <= fy + j < th:
                        px[fx + i, fy + j] = colour(p, face, a, b)
    return img


# --------------------------------------------------------------------------------------------------------------------
# Item icons (16x16): a side view of each fish drawn from its own colours
# --------------------------------------------------------------------------------------------------------------------

def outline(img, col):
    px = img.load()
    w, h = img.size
    edge = []
    for x in range(w):
        for y in range(h):
            if px[x, y][3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < w and 0 <= ny < h and px[nx, ny][3] > 200:
                        edge.append((x, y))
                        break
    for x, y in edge:
        px[x, y] = col


def fish_icon(sp, cooked=False):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    pal = sp["pal"]
    back, flank, belly, fin = hexc(pal["back"]), hexc(pal["flank"]), hexc(pal["belly"]), hexc(pal["fin"])
    k = sp["shape"]["kind"]
    s = sp["shape"]
    if cooked:
        brown = (150, 96, 48, 255)
        back, flank, belly, fin = mix(back, brown, 0.6), mix(flank, (196, 132, 66, 255), 0.6), mix(belly, (222, 176, 110, 255), 0.6), mix(fin, brown, 0.6)
    # depth profile along the body, nose at x=1 pointing left, tail at the right, a little diagonal like vanilla fish
    if k == "eel":
        prof = lambda t: 1.2
    elif k == "flat":
        prof = lambda t: 4.5 * math.sin(math.pi * min(1, t / 0.85)) if t < 0.85 else 1.0
    else:
        depth = s.get("H", 3) / max(s.get("L", 8) + s.get("Lh", 3), 1) * 13.0
        depth = max(1.4, min(5.0, depth))
        prof = lambda t: max(0.6, depth * (math.sin(math.pi * min(1.0, (t + 0.1) / 0.9)) ** 0.8)) if t < 0.82 else 0.7
    for xi in range(1, 13):
        t = (xi - 1) / 12.0
        hh = prof(t)
        if hh <= 0.05:
            continue
        cy = 8.5 - (xi - 6) * (0.2 if k != "eel" else 0.0)
        if k == "eel":
            cy = 8 + math.sin(t * 6) * 2.5
        for yi in range(16):
            dy = yi + 0.5 - cy
            if abs(dy) <= hh:
                f = (dy + hh) / (2 * hh)
                c = mix(back, flank, f * 2) if f < 0.5 else mix(flank, belly, (f - 0.5) * 2)
                if not cooked:
                    pat = pal["pattern"]
                    if pat == "bars" and xi % 3 == 0 and f < 0.7:
                        c = mix(c, hexc(pal["pc"]), 0.7)
                    if pat in ("spots", "trout", "beans") and (xi * 7 + yi * 3) % 5 == 0 and f < 0.75:
                        c = mix(c, hexc(pal.get("pc2", pal["pc"]) if (xi + yi) % 3 == 0 else pal["pc"]), 0.75)
                    if pat == "waves" and f < 0.45 and (xi + yi) % 3 == 0:
                        c = mix(c, hexc(pal["pc"]), 0.75)
                    if pat == "marble" and (xi * 5 + yi * 11) % 7 < 2 and f < 0.7:
                        c = mix(c, hexc(pal["pc"]), 0.5)
                else:
                    if (xi + yi) % 4 == 0:
                        c = shade(c, 0.82)
                px[xi, yi] = c
    # tail
    tx = 13
    cy = 8.5 - (tx - 6) * 0.2 if k != "eel" else 8
    if k != "eel":
        for yi in range(16):
            dy = abs(yi + 0.5 - cy)
            for x in (13, 14):
                if dy <= (x - 12) * 1.6 + 0.5 and not (s.get("fork") and dy < 0.8 and x == 14):
                    px[x, yi] = fin
    # dorsal fin and eye
    if k in ("fish", "shark"):
        dx = 3 + int(s.get("dpos", 0.3) * 8)
        cy = 8.5 - (dx - 6) * 0.2
        top = int(cy - prof((dx - 1) / 12.0)) - 1
        for i in range(2 if k == "fish" else 3):
            if 0 <= top - i < 16:
                px[dx + i, top - (1 if i == 0 else 0) if k == "shark" else top] = fin
    if k == "flat":
        px[3, 6] = (16, 14, 12, 255)
        px[4, 5] = (16, 14, 12, 255)
    else:
        cy = 8.5 - (2 - 6) * 0.2 if k != "eel" else 8 + math.sin(1 / 12 * 6) * 2.5
        ey = int(cy - (0.6 if k != "eel" else 0.3))
        px[2, ey] = (16, 14, 12, 255) if not cooked else (240, 230, 210, 255)
    if s.get("bill"):
        cy = 8.5 - (1 - 6) * 0.2
        px[0, int(cy)] = back
    outline(img, shade(back, 0.45) if not cooked else (70, 40, 18, 255))
    return img


def critter_icon(sp, cooked=False):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    pal = sp["pal"]
    col = hexc(sp.get("cooked_tint", pal["back"])) if cooked else hexc(pal["back"])
    col2 = mix(col, (255, 240, 220, 255), 0.35) if cooked else hexc(pal["fin"])
    k = sp["shape"]["kind"]
    if k == "crab":
        for x in range(3, 13):
            for y in range(6, 11):
                if (x - 7.5) ** 2 / 25 + (y - 8.5) ** 2 / 6 <= 1:
                    px[x, y] = shade(col, 0.9 + 0.1 * (y < 8))
        for x, y in ((1, 5), (2, 5), (1, 4), (13, 5), (14, 5), (14, 4), (2, 6), (13, 6)):
            px[x, y] = col2
        for x in (3, 5, 10, 12):
            px[x, 11] = col2
            px[x + (-1 if x < 8 else 1), 12] = col2
        px[6, 6] = px[9, 6] = (14, 12, 10, 255)
    elif k in ("lobster",):
        for x in range(4, 14):
            hh = 1.6 if x < 9 else 1.2 - (x - 9) * 0.12
            for y in range(16):
                if abs(y + 0.5 - 8.5) <= hh:
                    px[x, y] = shade(col, 0.88 + 0.12 * ((x % 2) == 0))
        for y in range(6, 11):
            px[14, y] = col2
        for x, y in ((3, 6), (2, 5), (1, 5), (2, 4), (3, 11), (2, 12), (1, 12), (2, 13)):
            px[x, y] = col
        for x in range(0, 4):
            px[x, 7] = col2
        if not cooked and pal["pattern"] == "spots":
            for x, y in ((6, 8), (9, 9), (11, 8)):
                px[x, y] = hexc(pal["pc"])
    elif k == "shrimp":
        pts = [(5 + 3.5 * math.cos(a), 8 + 3.5 * math.sin(a)) for a in [i * 0.3 + 3.6 for i in range(12)]]
        for i, (x, y) in enumerate(pts):
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    xx, yy = int(x + 3 + dx * 0.7), int(y + dy * 0.7)
                    if 0 <= xx < 16 and 0 <= yy < 16:
                        px[xx, yy] = shade(col, 0.85 + 0.15 * (i % 2))
        for x in range(9, 15):
            px[x, 4 - (x - 9) // 3] = col2
    elif k == "jelly":
        for x in range(3, 13):
            for y in range(3, 9):
                if (x - 7.5) ** 2 / 25 + (y - 8.5) ** 2 / 30 <= 1:
                    px[x, y] = hexc(pal["back"], 210)
        for x, y in ((6, 5), (9, 5), (6, 7), (9, 7)):
            px[x, y] = hexc(pal["pc"])
        for x in (4, 6, 8, 10, 12):
            for y in range(9, 15):
                if (y + x) % 3 != 0:
                    px[x - (y % 2), y] = hexc(pal["fin"], 180)
    outline(img, shade(col, 0.45))
    return img


def egg_icon(sp):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    base = hexc(sp["pal"]["flank"])
    spot = hexc(sp["pal"].get("pc", sp["pal"]["back"]))
    rnd = random.Random(sp["id"] + "egg")
    for x in range(16):
        for y in range(16):
            if ((x - 7.5) / 5.2) ** 2 + ((y - 8.8) / 6.6) ** 2 <= 1:
                c = base
                if rnd.random() < 0.22:
                    c = spot
                px[x, y] = shade(c, 1.05 - 0.25 * (x + y) / 30)
    outline(img, shade(base, 0.4))
    return img


# --------------------------------------------------------------------------------------------------------------------
# Writing it all out
# --------------------------------------------------------------------------------------------------------------------

def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def item_model(item_id, texture=None):
    texture = texture or item_id
    write_json(os.path.join(ASSETS, "models/item", item_id + ".json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": "jaymospowers:item/" + texture}})
    write_json(os.path.join(ASSETS, "items", item_id + ".json"),
               {"model": {"type": "minecraft:model", "model": "jaymospowers:item/" + item_id}})


def cooking(raw, cooked):
    for kind, time in (("smelting", 200), ("smoking", 100), ("campfire_cooking", 600)):
        name = cooked if kind == "smelting" else "%s_from_%s" % (cooked, kind)
        write_json(os.path.join(DATA, "jaymospowers/recipe", name + ".json"), {
            "type": "minecraft:" + kind, "category": "food", "cookingtime": time, "experience": 0.35,
            "ingredient": "jaymospowers:" + raw, "result": {"id": "jaymospowers:" + cooked}})


def fmt(v):
    s = ("%.3f" % v).rstrip("0").rstrip(".")
    if s in ("-0", ""):
        s = "0"
    return s + "F"


def main():
    java_parts = []
    sizes = []
    lang = {}
    white, oily, shell, raw_all, cooked_all = [], [], [], [], []
    for sp in SPECIES:
        parts = parts_for(sp)
        tw, th = pack(parts, 64)
        img = paint_texture(sp, parts, tw, th)
        out = os.path.join(ASSETS, "textures/entity/fish", sp["id"] + ".png")
        os.makedirs(os.path.dirname(out), exist_ok=True)
        img.save(out)
        x0, x1, y0, y1, z0, z1 = bounds(parts)
        model_len = (z1 - z0) / 16.0 if sp["shape"]["kind"] != "jelly" else (x1 - x0) / 16.0
        scale = sp["len"] / max(0.01, model_len)
        width = max(0.2, min(3.5, max(x1 - x0, z1 - z0) / 16.0 * scale * 0.7))
        height = max(0.2, min(2.5, (y1 - y0) / 16.0 * scale))
        if sp["shape"]["kind"] == "jelly":
            width, height = max(0.3, (x1 - x0) / 16 * scale), max(0.3, (y1 - y0) / 16 * scale)
        sizes.append((sp["id"], width, height, scale))
        rows = []
        for p in parts:
            x, y, z, w, h, d = p.box
            rows.append("new P(\"%s\", %s, %d, %d, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %d, %d)" % (
                p.name, "null" if p.parent is None else '"%s"' % p.parent, p.u, p.v,
                fmt(x), fmt(y), fmt(z), fmt(w), fmt(h), fmt(d),
                fmt(p.offset[0]), fmt(p.offset[1]), fmt(p.offset[2]), fmt(p.rot[0]), fmt(p.rot[1]), fmt(p.rot[2]), p.role, p.index))
        java_parts.append((sp["id"], tw, th, rows))
        # items
        k = sp["shape"]["kind"]
        critter = k in ("crab", "lobster", "shrimp", "jelly")
        if sp["fillet"] != "none" or k == "jelly":
            # a big shark has no item of its own: it's cut up on the shore
            ic = critter_icon(sp) if critter else fish_icon(sp)
            ic.save(os.path.join(ASSETS, "textures/item", sp["id"] + ".png"))
            item_model(sp["id"])
            lang["item.jaymospowers." + sp["id"]] = sp["name"]
        lang["entity.jaymospowers." + sp["id"]] = sp["name"]
        egg = sp["id"] + "_spawn_egg"
        egg_icon(sp).save(os.path.join(ASSETS, "textures/item", egg + ".png"))
        item_model(egg)
        lang["item.jaymospowers." + egg] = sp["name"] + " Spawn Egg"
        if sp["fillet"] in ("white", "oily", "shell"):
            cooked = "cooked_" + sp["id"]
            cic = critter_icon(sp, True) if critter else fish_icon(sp, True)
            cic.save(os.path.join(ASSETS, "textures/item", cooked + ".png"))
            item_model(cooked)
            lang["item.jaymospowers." + cooked] = "Cooked " + sp["name"]
            cooking(sp["id"], cooked)
            raw_all.append(sp["id"])
            cooked_all.append(cooked)
        {"white": white, "oily": oily, "shell": shell}.get(sp["fillet"], []).append(sp["id"])

    # tags
    write_json(os.path.join(DATA, "jaymospowers/tags/item/fish/white.json"),
               {"replace": False, "values": ["jaymospowers:" + i for i in white]})
    write_json(os.path.join(DATA, "jaymospowers/tags/item/fish/oily.json"),
               {"replace": False, "values": ["jaymospowers:" + i for i in oily]})
    write_json(os.path.join(DATA, "jaymospowers/tags/item/fish/shellfish.json"),
               {"replace": False, "values": ["jaymospowers:" + i for i in shell]})
    write_json(os.path.join(DATA, "minecraft/tags/item/fishes.json"),
               {"replace": False, "values": ["jaymospowers:" + i for i in raw_all] + ["jaymospowers:" + i for i in cooked_all]})
    write_json(os.path.join(DATA, "c/tags/item/foods/raw_fish.json"),
               {"replace": False, "values": ["jaymospowers:" + i for i in white + oily]})
    write_json(os.path.join(DATA, "c/tags/item/foods/cooked_fish.json"),
               {"replace": False, "values": ["jaymospowers:cooked_" + i for i in white + oily]})

    # the generated Java
    with open(CLIENT_JAVA, "w", encoding="utf-8", newline="\n") as f:
        f.write("package com.jaymospowers.client.fishing;\n\nimport java.util.List;\nimport java.util.Map;\n\n")
        f.write("/**\n * Generated by tools/gen_fishing_species.py: do not edit by hand. The boxes of each species' model, in model pixels\n"
                " * (head towards -z, y down, the ground at 24), where each sits in the texture, and how it moves\n"
                " * ({@link FishShapeModel}'s roles).\n */\n")
        f.write("final class FishShapes {\n")
        f.write("\t/** A part: name, parent (or null), texture corner, box, pivot, rotation, role and index. */\n")
        f.write("\trecord P(String name, String parent, int u, int v, float x, float y, float z, float w, float h, float d,\n"
                "\t\tfloat px, float py, float pz, float rx, float ry, float rz, int role, int index) {\n\t}\n\n")
        f.write("\t/** A species' model: its texture size and parts. */\n\trecord Shape(int texW, int texH, List<P> parts) {\n\t}\n\n")
        f.write("\tprivate FishShapes() {\n\t}\n\n")
        for sid, tw, th, rows in java_parts:
            f.write("\tprivate static Shape %s() {\n\t\treturn new Shape(%d, %d, List.of(\n\t\t\t%s));\n\t}\n\n" % (
                sid.upper(), tw, th, ",\n\t\t\t".join(rows)))
        f.write("\tstatic final Map<String, Shape> ALL = Map.ofEntries(\n")
        f.write(",\n".join("\t\tMap.entry(\"%s\", %s())" % (sid, sid.upper()) for sid, _, _, _ in java_parts))
        f.write(");\n}\n")
    with open(MAIN_JAVA, "w", encoding="utf-8", newline="\n") as f:
        f.write("package com.jaymospowers.fishing;\n\nimport java.util.Map;\n\n")
        f.write("/**\n * Generated by tools/gen_fishing_species.py: do not edit by hand. Each new species' hitbox (width, height in blocks)\n"
                " * and how much its model is scaled to be its real size at its usual weight.\n */\n")
        f.write("public final class SpeciesSizes {\n\t/** Width, height, model scale. */\n\tpublic record Size(float width, float height, float scale) {\n\t}\n\n")
        f.write("\tprivate SpeciesSizes() {\n\t}\n\n")
        f.write("\tpublic static final Map<String, Size> ALL = Map.ofEntries(\n")
        f.write(",\n".join("\t\tMap.entry(\"%s\", new Size(%s, %s, %s))" % (sid, fmt(w), fmt(h), fmt(s)) for sid, w, h, s in sizes))
        f.write(");\n}\n")

    blocks(lang)
    write_json(os.path.join(ASSETS, "lang/parts/fishing_species.json"), dict(sorted(lang.items())))
    print("species:", len(SPECIES), "raw items:", len(raw_all))


# --------------------------------------------------------------------------------------------------------------------
# The new tackle's blocks and items: rod pod, crab pot, keepnet, trophy plaque; ice auger, journal, shark jaw
# --------------------------------------------------------------------------------------------------------------------

def tex(name, w, h, fn):
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = img.load()
    for x in range(w):
        for y in range(h):
            c = fn(x, y)
            if c:
                px[x, y] = c
    return img


def blocks(lang):
    rnd = random.Random(7)
    wood = lambda x, y: shade((122, 88, 52, 255), 0.85 + 0.15 * ((x * 3 + y // 4) % 5) / 4)
    metal = lambda x, y: shade((150, 150, 156, 255), 0.85 + 0.15 * ((x + y) % 3) / 2)
    rope = lambda x, y: shade((196, 172, 120, 255), 0.85 + 0.15 * ((x + y) % 4) / 3)
    net = lambda x, y: (82, 74, 52, 255) if x % 4 == 0 or y % 4 == 0 else None
    bdir = os.path.join(ASSETS, "textures/block")
    os.makedirs(bdir, exist_ok=True)
    tex("rod_pod", 16, 16, lambda x, y: metal(x, y) if y < 8 else wood(x, y)).save(os.path.join(bdir, "rod_pod.png"))
    tex("rod_pod_alarm", 16, 16, lambda x, y: (40, 40, 44, 255) if not (5 <= x <= 10 and 5 <= y <= 10) else (230, 60, 40, 255)).save(
        os.path.join(bdir, "rod_pod_alarm.png"))
    tex("crab_pot", 16, 16, lambda x, y: (96, 72, 44, 255) if x % 5 == 0 or y % 5 == 0 or x == 15 or y == 15 else None).save(
        os.path.join(bdir, "crab_pot.png"))
    tex("crab_pot_net", 16, 16, net).save(os.path.join(bdir, "crab_pot_net.png"))
    tex("keepnet", 16, 16, lambda x, y: (60, 70, 52, 230) if (x + y) % 3 == 0 or (x - y) % 3 == 0 else None).save(os.path.join(bdir, "keepnet.png"))
    tex("keepnet_ring", 16, 16, lambda x, y: metal(x, y)).save(os.path.join(bdir, "keepnet_ring.png"))
    tex("trophy_plaque", 16, 16, lambda x, y: shade((96, 60, 34, 255), 0.85 + 0.15 * ((x * 5 + y) % 7) / 6) if 0 < x < 15 and 0 < y < 15
        else (60, 36, 20, 255)).save(os.path.join(bdir, "trophy_plaque.png"))
    tex("rope", 16, 16, rope).save(os.path.join(bdir, "fishing_rope.png"))

    def model(name, elements, textures, parent="minecraft:block/block"):
        write_json(os.path.join(ASSETS, "models/block", name + ".json"),
                   {"parent": parent, "textures": textures, "elements": elements})

    def el(frm, to, tex_key, rot=None):
        faces = {d: {"texture": "#" + tex_key} for d in ("north", "south", "east", "west", "up", "down")}
        e = {"from": frm, "to": to, "faces": faces}
        if rot:
            e["rotation"] = rot
        return e

    # rod pod: a bar on two legs, three rest notches, and the bite alarm box on top
    model("rod_pod", [
        el([1, 0, 7], [2, 9, 8], "frame", {"angle": 22.5, "axis": "z", "origin": [1.5, 0, 7.5]}),
        el([14, 0, 7], [15, 9, 8], "frame", {"angle": -22.5, "axis": "z", "origin": [14.5, 0, 7.5]}),
        el([1, 8, 7], [15, 9, 9], "frame"),
        el([3, 9, 7], [4, 11, 9], "frame"), el([7.5, 9, 7], [8.5, 11, 9], "frame"), el([12, 9, 7], [13, 11, 9], "frame"),
        el([2, 9, 9], [5, 11, 11], "alarm"), el([6.5, 9, 9], [9.5, 11, 11], "alarm"), el([11, 9, 9], [14, 11, 11], "alarm"),
    ], {"frame": "jaymospowers:block/rod_pod", "alarm": "jaymospowers:block/rod_pod_alarm", "particle": "jaymospowers:block/rod_pod"})
    # crab pot: a wooden cage with netting and a funnel mouth
    model("crab_pot", [
        el([2, 0, 2], [14, 1, 14], "frame"), el([2, 9, 2], [14, 10, 14], "frame"),
        el([2, 1, 2], [3, 9, 3], "frame"), el([13, 1, 2], [14, 9, 3], "frame"), el([2, 1, 13], [3, 9, 14], "frame"), el([13, 1, 13], [14, 9, 14], "frame"),
        el([2.5, 1, 2.5], [13.5, 9, 13.5], "net"),
        el([6, 9.5, 6], [10, 11, 10], "rope"),
    ], {"frame": "jaymospowers:block/crab_pot", "net": "jaymospowers:block/crab_pot_net", "rope": "jaymospowers:block/fishing_rope",
        "particle": "jaymospowers:block/crab_pot"})
    # keepnet: a long net tube on a ring, stood in the water
    model("keepnet", [
        el([3, 0, 3], [13, 14, 13], "net"),
        el([2, 14, 2], [14, 15, 14], "ring"),
        el([7.5, 15, 7.5], [8.5, 16, 8.5], "ring"),
    ], {"net": "jaymospowers:block/keepnet", "ring": "jaymospowers:block/keepnet_ring", "particle": "jaymospowers:block/keepnet_ring"})
    # trophy plaque: a board on the wall (south face against the wall, facing north by default)
    model("trophy_plaque", [
        el([1, 3, 14], [15, 13, 16], "board"),
        el([2, 4, 13.5], [14, 12, 14], "board"),
    ], {"board": "jaymospowers:block/trophy_plaque", "particle": "jaymospowers:block/trophy_plaque"})
    for name in ("rod_pod", "crab_pot", "keepnet"):
        write_json(os.path.join(ASSETS, "blockstates", name + ".json"),
                   {"variants": {"facing=%s" % f: {"model": "jaymospowers:block/" + name, **({"y": r} if r else {})}
                                 for f, r in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}})
        write_json(os.path.join(ASSETS, "items", name + ".json"),
                   {"model": {"type": "minecraft:model", "model": "jaymospowers:block/" + name}})
    write_json(os.path.join(ASSETS, "blockstates", "trophy_plaque.json"),
               {"variants": {"facing=%s" % f: {"model": "jaymospowers:block/trophy_plaque", **({"y": r} if r else {})}
                             for f, r in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}})
    write_json(os.path.join(ASSETS, "items", "trophy_plaque.json"),
               {"model": {"type": "minecraft:model", "model": "jaymospowers:block/trophy_plaque"}})
    # items: the ice auger, the fisher's journal, a shark's jaw, an angler's ribbon
    idir = os.path.join(ASSETS, "textures/item")

    def auger(x, y):
        if 6 <= x <= 7 and 1 <= y <= 3:
            return (122, 88, 52, 255)
        if 2 <= x <= 11 and y == 2:
            return (122, 88, 52, 255)
        if 6 <= x <= 7 and 3 < y <= 14:
            return shade((170, 170, 178, 255), 0.8 + 0.2 * (y % 3 == 0))
        if (x in (5, 8)) and 6 <= y <= 14 and (y + x) % 3 == 0:
            return (130, 130, 140, 255)
        return None

    tex("ice_auger", 16, 16, auger).save(os.path.join(idir, "ice_auger.png"))

    def journal(x, y):
        if 3 <= x <= 12 and 2 <= y <= 13:
            if x == 3 or x == 12 or y == 2 or y == 13:
                return (52, 38, 24, 255)
            if 5 <= x <= 10 and 6 <= y <= 9:
                return (90, 150, 170, 255) if not (x in (5, 10) and y in (6, 9)) else (60, 110, 130, 255)
            return (108, 72, 40, 255)
        if x == 13 and 3 <= y <= 12:
            return (236, 226, 200, 255)
        return None

    tex("fishers_journal", 16, 16, journal).save(os.path.join(idir, "fishers_journal.png"))

    def jaw(x, y):
        r = math.hypot(x - 7.5, y - 8.5)
        if 3.5 <= r <= 6.5 and y >= 4:
            if r < 4.3 and (x % 2 == 0):
                return (248, 246, 238, 255)
            return (196, 176, 150, 255)
        return None

    tex("shark_jaw", 16, 16, jaw).save(os.path.join(idir, "shark_jaw.png"))

    def ribbon(x, y):
        if math.hypot(x - 7.5, y - 5.5) <= 4:
            return (40, 90, 170, 255) if math.hypot(x - 7.5, y - 5.5) > 2.2 else (230, 200, 70, 255)
        if 9 <= y <= 14 and (5 <= x <= 6 or 9 <= x <= 10):
            return (40, 90, 170, 255)
        return None

    tex("anglers_ribbon", 16, 16, ribbon).save(os.path.join(idir, "anglers_ribbon.png"))
    for i in ("ice_auger", "fishers_journal", "shark_jaw", "anglers_ribbon"):
        item_model(i)


if __name__ == "__main__":
    main()
