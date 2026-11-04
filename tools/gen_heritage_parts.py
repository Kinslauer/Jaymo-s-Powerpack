"""Beastfolk and Sylvan model-part textures (Kingdoms 0.7.490): the ears, tails, wings, legs, crest and sprigs that used to be
painted flat on the skin are real model parts now (client BeastParts), each design with its own 64x64 sheet:

parts/<key>.png       - the sheet (vanilla-style 16px-scale pixel art, nothing copied)
parts/<key>_glow.png  - drawn emissive over it (the Sprite's and Faerie's wing edges, the Kitsune's fire-tipped tails)

One layout for every design (u, v, w, h, d of each box, as BeastParts builds them; a box's unwrap is 2d+2w wide, d+h tall):
  ear0 3x2x1 (0,0)   ear1 2x2x1 (0,3)   ear2 1x2x1 (0,6)      stacked up into a pointed ear
  tail 3x3x4 (0,16)  tail tip 3x3x4 (16,16)                    one segment of a tail, and the last
  wing cover 3x6x1 (0,24)  flight 3x8x1 (10,24)  tip 2x3x1 (20,24)   a folded wing, shoulder to feathertip
  crest 1x4x1 (28,24)   twig trunk 1x4x1 (34,24)   twig 1x3x1 (40,24)   leaf plane 3x3x0 (46,24)
  leg 1x1x6 (0,34)                                              a spider leg's segment
  gossamer upper 10x12x0 (0,44)   lower 7x8x0 (22,44)          flat wings: front half, then the back half (flipped)
Run from the project root:  python -I tools/gen_heritage_parts.py
"""
import math
import os
import random
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "kingdoms", "src", "main", "resources", "assets",
                   "jaymoskingdoms", "textures", "entity", "heritage", "parts")
os.makedirs(OUT, exist_ok=True)

EAR0, EAR1, EAR2 = (0, 0, 3, 2, 1), (0, 3, 2, 2, 1), (0, 6, 1, 2, 1)
TAIL, TIP = (0, 16, 3, 3, 4), (16, 16, 3, 3, 4)
COVER, FLIGHT, WTIP = (0, 24, 3, 6, 1), (10, 24, 3, 8, 1), (20, 24, 2, 3, 1)
CREST, TRUNK, TWIG, LEAF = (28, 24, 1, 4, 1), (34, 24, 1, 4, 1), (40, 24, 1, 3, 1), (46, 24, 3, 3, 0)
LEG = (0, 34, 1, 1, 6)
UPPER_W, UPPER_H, UPPER_PIVOT = 10, 12, 7  # pivot: the row the wing hangs from (BeastParts boxes it at y -7)
LOWER_W, LOWER_H, LOWER_PIVOT = 7, 8, 1
UPPER_AT, LOWER_AT = (0, 44), (22, 44)


def faces(u, v, w, h, d):
    """A box's six unwrapped faces: name -> (x, y, width, height)."""
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
            "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}


def paint(img, box, fn):
    """fn(face, x, y, w, h) -> colour or None, for every pixel of the box's unwrap (x, y local to the face)."""
    for name, (fx, fy, fw, fh) in faces(*box).items():
        for y in range(fh):
            for x in range(fw):
                c = fn(name, x, y, fw, fh)
                if c:
                    img.putpixel((fx + x, fy + y), c)


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c[:3]) + (c[3] if len(c) > 3 else 255,)


def opaque(c):
    return tuple(c[:3]) + (255,)


def ear(img, pal):
    fur, inner, tip = pal["fur"], pal["inner"], pal["tip"]

    def level(n):
        def f(face, x, y, w, h):
            if n == 2:  # the point
                return tip if face != "bottom" else shade(fur[1], 0.8)
            if face == "front" and y > 0 and (x == 1 or w == 2):  # the inside of the ear, below its rim
                return inner
            return fur[(x + y) % len(fur)]
        return f
    for n, box in enumerate((EAR0, EAR1, EAR2)):
        paint(img, box, level(n))


def tail(img, pal, box, last):
    fur, tip, band = pal["fur"], pal["tip"], pal.get("band")
    scales = pal.get("scales")

    def f(face, x, y, w, h):
        if last:
            if face in ("front", "back"):
                return shade(tip, 0.85)
            return shade(tip, 1.0 if (x + y) % 3 else 0.9)
        if scales:
            if face == "bottom":
                return scales["belly"] if (x + y) % 2 else shade(scales["belly"], 0.88)
            edge = (x + y) % 2
            return scales["edge"] if edge else scales["fur"][(x // 2 + y // 2) % len(scales["fur"])]
        # bands run round the tail: along z, which is v on the top/bottom faces and u on the sides
        along = y if face in ("top", "bottom") else x if face in ("left", "right") else 0
        c = fur[(along + x + y) % len(fur)]
        if band and face not in ("front", "back") and along % 3 == 1:
            c = band
        return c
    paint(img, box, f)


def wing(img, pal):
    prim, cov, tip = pal["prim"], pal["cov"], pal["wtip"]

    def cover(face, x, y, w, h):
        row = y if face != "top" and face != "bottom" else 0
        scallop = (x + row // 2) % 2
        return cov[scallop] if row % 2 == 0 else shade(cov[scallop], 0.8)

    def flight(face, x, y, w, h):
        if face in ("top", "bottom"):
            return prim[0]
        if y >= h - 2:
            return tip
        return prim[(x // 1 + (1 if y % 5 == 4 else 0)) % 3] if (x + y) % 4 else shade(prim[1], 1.15)

    def wtip(face, x, y, w, h):
        return tip if y >= h - 1 else prim[(x + y) % 3]
    paint(img, COVER, cover)
    paint(img, FLIGHT, flight)
    paint(img, WTIP, wtip)


def crest(img, pal):
    prim, tip = pal["prim"], pal["wtip"]
    paint(img, CREST, lambda face, x, y, w, h: tip if (face in ("front", "back", "left", "right") and y == 0) else prim[(y + x) % 3])


def leg(img, pal):
    dark, band = pal["dark"], pal["band"]
    paint(img, LEG, lambda face, x, y, w, h: band if ((x if face in ("left", "right") else y) % 4 == 3) else dark[(x + y) % 3])


def sprig(img, pal):
    bark, leaf = pal["bark"], pal["leaf"]
    paint(img, TRUNK, lambda face, x, y, w, h: bark[(x + y) % 3])
    paint(img, TWIG, lambda face, x, y, w, h: bark[(x + y + 1) % 3])
    fx, fy, fw, fh = faces(*LEAF)["front"]
    bx, by, bw, bh = faces(*LEAF)["back"]
    for fxx, fyy in ((fx, fy), (bx, by)):
        for y in range(fh):
            for x in range(fw):
                if (x == 0 or x == fw - 1) and y == 0:
                    continue  # rounded corners
                img.putpixel((fxx + x, fyy + y), leaf[2] if x == 1 else leaf[(x + y) % 2])


def lobe_rows(kind, x):
    """The rows (top, bottom) a gossamer wing fills in column x of its plane, measured from the pivot row."""
    if kind == "upper":
        up = math.ceil(6.4 * math.sin(math.pi * (x + 0.9) / (UPPER_W + 1.6)))
        down = round(3.0 * max(0.0, 1.0 - x / 6.0))
        return UPPER_PIVOT - up, UPPER_PIVOT + down
    reach = math.ceil(6.4 * math.sin(math.pi * (x + 0.9) / (LOWER_W + 1.2)))
    return LOWER_PIVOT, LOWER_PIVOT + reach


def gossamer(solid, glow, pal, rng):
    """Both flat wings, translucent, veined, their edges lit. The back half (seen from behind) is the picture flipped so that
    it hangs from the pivot edge of the box on either side."""
    for kind, (ax, ay), W, H in (("upper", UPPER_AT, UPPER_W, UPPER_H), ("lower", LOWER_AT, LOWER_W, LOWER_H)):
        body, vein, shine, alpha = pal[kind], pal["vein"], pal["shine"], pal["alpha"]
        pic = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        lit = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        for x in range(W):
            top, bottom = lobe_rows(kind, x)
            for y in range(max(0, top), min(H, bottom + 1)):
                edge = y in (top, bottom) or x == W - 1 or (x < W - 1 and not (lobe_rows(kind, x + 1)[0] <= y <= lobe_rows(kind, x + 1)[1]))
                col = body[:3] + (alpha,)
                # veins fanning out from the pivot
                pivot = UPPER_PIVOT if kind == "upper" else LOWER_PIVOT
                slope = (y - pivot) / (x + 1.0)
                if not edge and abs(slope * 2 - round(slope * 2)) < 0.12 and x > 0:
                    col = vein[:3] + (min(255, alpha + 40),)
                if edge:
                    col = (vein if (x + y) % 3 == 0 else shine)[:3] + (min(255, alpha + 50),)
                    lit.putpixel((x, y), shine[:3] + (255,))
                pic.putpixel((x, y), col)
        for spot in pal.get("spots", ()):
            if spot[0] == kind:
                pic.putpixel(spot[1:3], spot[3][:3] + (255,))
                lit.putpixel(spot[1:3], spot[3][:3] + (255,))
        flipped, flipped_lit = pic.transpose(Image.FLIP_LEFT_RIGHT), lit.transpose(Image.FLIP_LEFT_RIGHT)
        # front half (u..u+W) the picture, back half (u+W..u+2W) flipped: see the module note
        for target, a, b in ((solid, pic, flipped), (glow, lit, flipped_lit)):
            target.paste(a, (ax, ay))
            target.paste(b, (ax + W, ay))


def blank():
    return Image.new("RGBA", (64, 64), (0, 0, 0, 0))


def rgb(*a):
    return tuple(a) + (255,)


FUR_BEAST = [rgb(146, 102, 60), rgb(166, 120, 72), rgb(184, 140, 88)]
FUR_WOLF = [rgb(112, 112, 116), rgb(92, 92, 98), rgb(136, 134, 132)]
FUR_FOX = [rgb(214, 106, 40), rgb(190, 84, 30), rgb(226, 124, 52)]
FEATHER = [rgb(92, 70, 52), rgb(120, 92, 64), rgb(70, 52, 40)]
SCALES = [rgb(70, 130, 68), rgb(52, 104, 56), rgb(92, 154, 80)]
FIRE = rgb(120, 200, 255)

DESIGNS = {
    "beastfolk": {"fur": FUR_BEAST, "inner": rgb(214, 160, 150), "tip": rgb(226, 200, 160), "parts": ("ear", "tail")},
    "canine": {"fur": FUR_WOLF, "inner": rgb(70, 60, 64), "tip": rgb(208, 204, 196), "parts": ("ear", "tail")},
    "feline": {"fur": [rgb(190, 144, 84), rgb(176, 130, 76)], "inner": rgb(228, 150, 156), "tip": rgb(96, 64, 40), "band": rgb(96, 64, 40),
               "parts": ("ear", "tail")},
    "kitsune": {"fur": FUR_FOX, "inner": rgb(60, 36, 36), "tip": rgb(246, 238, 226), "parts": ("ear", "tail"), "glow_tip": FIRE},
    "avian": {"fur": FEATHER[:2], "tip": rgb(240, 232, 214), "prim": FEATHER, "cov": [rgb(188, 156, 110), rgb(214, 184, 134)],
              "wtip": rgb(240, 232, 214), "parts": ("wing", "crest", "tail")},
    "arachnid": {"dark": [rgb(48, 36, 52), rgb(66, 50, 60), rgb(34, 26, 40)], "band": rgb(150, 116, 80), "parts": ("leg",)},
    "serpen": {"fur": SCALES[:2], "tip": rgb(216, 208, 150), "scales": {"fur": SCALES, "edge": rgb(34, 72, 44), "belly": rgb(216, 208, 150)},
               "parts": ("tail",)},
    "sprite": {"upper": rgb(190, 240, 250), "lower": rgb(170, 224, 240), "vein": rgb(96, 168, 190), "shine": rgb(200, 255, 240), "alpha": 150,
               "parts": ("gossamer",)},
    "faerie": {"upper": rgb(244, 168, 220), "lower": rgb(190, 150, 240), "vein": rgb(120, 70, 150), "shine": rgb(255, 210, 250), "alpha": 190,
               "spots": (("upper", 6, 3, rgb(255, 240, 200)), ("upper", 8, 5, rgb(255, 240, 200)), ("lower", 3, 4, rgb(255, 240, 200))),
               "parts": ("gossamer",)},
    "dryad": {"bark": [rgb(98, 72, 48), rgb(80, 58, 38), rgb(116, 88, 58)], "leaf": [rgb(86, 150, 46), rgb(122, 184, 64), rgb(168, 232, 96)],
              "parts": ("sprig",)},
}

for key, pal in DESIGNS.items():
    img, glow = blank(), blank()
    rng = random.Random(sum(map(ord, key)) * 17)
    parts = pal["parts"]
    if "ear" in parts:
        ear(img, pal)
    if "tail" in parts:
        tail(img, pal, TAIL, False)
        tail(img, pal, TIP, True)
        if pal.get("glow_tip"):
            paint(glow, TIP, lambda face, x, y, w, h: pal["glow_tip"])
    if "wing" in parts:
        wing(img, pal)
    if "crest" in parts:
        crest(img, pal)
    if "leg" in parts:
        leg(img, pal)
    if "sprig" in parts:
        sprig(img, pal)
    if "gossamer" in parts:
        gossamer(img, glow, pal, rng)
    img.save(os.path.join(OUT, key + ".png"))
    if glow.getbbox():
        glow.save(os.path.join(OUT, key + "_glow.png"))
print("Heritage parts written to", os.path.normpath(OUT))
