"""Draws the equestrian art, all original and procedural: horse coats for six breeds (destrier, courser, palfrey, draft,
steppe pony, desert horse; six coats each, the last one rare), each as an adult (64x64, the horse model's box layout) and
a foal (64x64, the baby model's layout); skeleton and zombie horses (three coats each, both layouts); the war elephant
(128x128, four hides, all wearing the same howdah, crimson caparison, steel head plate and sun banner); and two 16x16
items, the mane lock and the war elephant spawn egg. Markings follow the box unwrap: the blaze and star sit on the head's
top face (the forehead, since the neck tilts the head forward) and run down the muzzle's top and front; socks, stockings
and feathering are rows of the leg sides above (feathering also over) the hoof. Seeded per texture, so every run draws
the same pixels. A preview sheet of everything (4x) goes to /tmp/claude-0/equestrian-preview.png.
Run from the repo root: python3 -I kingdoms/tools/gen_equestrian.py (or from kingdoms/: python3 -I tools/gen_equestrian.py)"""
from PIL import Image, ImageDraw, ImageFont
import math
import os
import random

HERE = os.path.dirname(os.path.abspath(__file__))
TEX = os.path.normpath(os.path.join(HERE, "..", "src/main/resources/assets/jaymoskingdoms/textures"))
PREVIEW = "/tmp/claude-0/equestrian-preview.png"

SIDES = ("a", "front", "b", "back")


class Box:
    """A cube's faces in the texture (box UV): top, bottom, then side a, front (-Z), side b, back (+Z) in a row."""

    def __init__(self, u, v, w, h, d):
        self.w, self.h, self.d = w, h, d
        self.faces = {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "a": (u, v + d, d, h),
                      "front": (u + d, v + d, w, h), "b": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}


def frontness(face, i, j, fw, fh):
    """How many texels a pixel lies behind the box's front (-Z) edge; 0 on the front face itself."""
    if face == "a":
        return fw - 1 - i
    if face == "b":
        return i
    if face in ("top", "bottom"):
        return fh - 1 - j
    if face == "back":
        return 99
    return 0


def side_xy(rect, face, f, j):
    """The pixel at frontness f, row j of side face a or b."""
    x0, y0, w, h = rect
    return (x0 + (w - 1 - f if face == "a" else f), y0 + j)


def clamp(c):
    return tuple(max(0, min(255, int(v))) for v in c)


def shade(c, by):
    return clamp(tuple(v + by for v in c))


def mix(a, b, t):
    return clamp(tuple(a[k] * (1 - t) + b[k] * t for k in range(3)))


def px(img, x, y, c, a=255):
    img.putpixel((x, y), clamp(c) + (a,))


def get(img, x, y):
    return img.getpixel((x, y))[:3]


def vnoise(rnd, w, h, cell):
    """Smooth value noise in [0,1], w x h, blobs about `cell` texels across."""
    gw, gh = int(w / cell) + 2, int(h / cell) + 2
    g = [[rnd.random() for _ in range(gw)] for _ in range(gh)]
    out = [[0.0] * w for _ in range(h)]
    for y in range(h):
        for x in range(w):
            fx, fy = x / cell, y / cell
            x0, y0 = int(fx), int(fy)
            tx, ty = fx - x0, fy - y0
            tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
            top = g[y0][x0] * (1 - tx) + g[y0][x0 + 1] * tx
            bot = g[y0 + 1][x0] * (1 - tx) + g[y0 + 1][x0 + 1] * tx
            out[y][x] = top * (1 - ty) + bot * ty
    return out


def dapples(rnd, w, h, spacing):
    """2 = a dapple's light core, 1 = its rim, 0 = the darker net between."""
    centres = []
    for gy in range(-1, h // spacing + 2):
        for gx in range(-1, w // spacing + 2):
            centres.append((gx * spacing + (gy % 2) * spacing / 2 + rnd.uniform(-0.8, 0.8), gy * spacing + rnd.uniform(-0.8, 0.8)))
    out = [[0] * w for _ in range(h)]
    for y in range(h):
        for x in range(w):
            d = min(math.hypot(x - cx, y - cy) for cx, cy in centres)
            out[y][x] = 2 if d < 0.9 else 1 if d < 1.6 else 0
    return out


def ovals(rnd, w, h, count, rx=(0.7, 1.4), ry=(0.5, 1.0), bias=None):
    """Random oval spots: a w x h grid of True/False. bias(x) in [0,1] thins the spots out along x."""
    out = [[False] * w for _ in range(h)]
    placed = 0
    tries = 0
    while placed < count and tries < count * 20:
        tries += 1
        cx, cy = rnd.uniform(-0.5, w - 0.5), rnd.uniform(-0.5, h - 0.5)
        if bias and rnd.random() > bias(cx):
            continue
        a, b = rnd.uniform(*rx), rnd.uniform(*ry)
        placed += 1
        for y in range(h):
            for x in range(w):
                if ((x - cx) / a) ** 2 + ((y - cy) / b) ** 2 <= 1:
                    out[y][x] = True
    return out


# ---------------------------------------------------------------------------------------------------------------------
# Horse layouts
# ---------------------------------------------------------------------------------------------------------------------
ADULT = {"body": [Box(0, 32, 10, 10, 22)], "neck": [Box(0, 35, 4, 12, 7)], "head": [Box(0, 13, 6, 5, 7)],
         "muzzle": [Box(0, 25, 4, 5, 5)], "mane": [Box(56, 36, 2, 16, 2)], "ear": [Box(19, 16, 2, 3, 1)],
         "leg": [Box(48, 21, 4, 11, 4)], "tail": [Box(42, 36, 3, 14, 4)]}
BABY = {"body": [Box(0, 13, 8, 7, 14)], "tail": [Box(24, 34, 3, 3, 8)],
        "leg": [Box(12, 46, 3, 9, 3), Box(0, 46, 3, 9, 3), Box(12, 34, 3, 9, 3), Box(0, 34, 3, 9, 3)],
        "neck": [Box(30, 0, 4, 8, 4)], "head": [Box(0, 0, 6, 4, 9)], "ear": [Box(0, 4, 2, 3, 1), Box(0, 0, 2, 3, 1)]}
# painting order: the foal's ears overlap the head's unused corner, so they go last
ORDER = ("body", "neck", "head", "muzzle", "mane", "leg", "tail", "ear")

WHITE = (238, 235, 226)
FEATHER = (228, 222, 206)
HOOF = (54, 47, 43)
HOOF_PALE = (128, 116, 98)
DARK = (22, 18, 17)

# breed character: per-pixel noise on the hide, strand spread in the hair
NOISE = {"destrier": 6, "courser": 5, "palfrey": 5, "draft": 6, "steppe_pony": 9, "desert_horse": 3}
HAIRN = {"destrier": 14, "courser": 12, "palfrey": 12, "draft": 16, "steppe_pony": 26, "desert_horse": 8}


def coat(base, hair, *marks, **kw):
    s = dict(base=base, hair=hair, points=None, points_from=4, head=None, legs=None, hoof=HOOF, spot=None, stripe=None,
             white=WHITE, feather=FEATHER, tint=None)
    s.update(kw)
    s["marks"] = set(marks)
    if "blaze" in s["marks"] and s["tint"] is None:
        s["tint"] = (200, 150, 140)
    return s


BLACK, BLACK_H = (30, 27, 29), (16, 14, 16)
BAY, BAY_DARK = (128, 70, 36), (70, 40, 24)
CHESTNUT = (158, 82, 40)
POINTS = (24, 20, 19)
DUN = (194, 162, 110)

COATS = {
    "destrier": [
        ("black", coat(BLACK, BLACK_H)),
        ("dark_bay", coat(BAY_DARK, BLACK_H, points=POINTS, points_from=3)),
        ("iron_grey", coat((92, 95, 100), (46, 46, 50), "roan", head=(70, 72, 77), legs=(60, 61, 66), spot=(170, 172, 176))),
        ("blaze_black", coat(BLACK, BLACK_H, "blaze", "socks")),
        ("liver_chestnut", coat((88, 46, 30), (64, 34, 22))),
        ("blood_roan", coat((132, 30, 30), (78, 16, 18), "roan", "shimmer", head=(96, 22, 24), legs=(84, 18, 20),
                            spot=(232, 214, 212), hoof=(40, 30, 30))),
    ],
    "courser": [
        ("bay", coat(BAY, POINTS, points=POINTS)),
        ("chestnut_blaze", coat(CHESTNUT, (142, 70, 34), "blaze")),
        ("fleabitten_grey", coat((226, 223, 214), (196, 194, 188), "flea", spot=(122, 74, 52), head=(214, 210, 202),
                                 hoof=HOOF_PALE)),
        ("golden_chestnut", coat((186, 108, 46), (204, 136, 66), "star")),
        ("black_socks", coat(BLACK, BLACK_H, "socks")),
        ("palomino", coat((214, 172, 92), (246, 240, 222), "shimmer", hoof=HOOF_PALE, tint=(150, 110, 90))),
    ],
    "palfrey": [
        ("dun", coat(DUN, (54, 40, 30), "dorsal", points=(50, 38, 30), points_from=6, stripe=(70, 50, 36))),
        ("bay_star", coat(BAY, POINTS, "star", points=POINTS)),
        ("chestnut_socks", coat(CHESTNUT, (140, 70, 34), "socks")),
        ("dapple_grey", coat((126, 128, 132), (78, 78, 84), "dapple", spot=(206, 208, 210), head=(104, 106, 110),
                             legs=(96, 98, 102))),
        ("cream", coat((234, 220, 188), (246, 240, 224), hoof=HOOF_PALE, tint=(204, 160, 150))),
        ("leopard", coat((238, 234, 226), (120, 108, 100), "leopard", spot=(52, 40, 36), hoof=(96, 84, 74),
                         tint=(170, 130, 124))),
    ],
    "draft": [
        ("black_feathered", coat(BLACK, BLACK_H, "feather")),
        ("bay_blaze", coat(BAY, POINTS, "blaze", "stockings", "feather", points=POINTS)),
        ("strawberry_roan", coat((164, 84, 54), (120, 58, 36), "roan", "feather", head=(138, 68, 42), legs=(130, 64, 40),
                                 spot=(232, 214, 204))),
        ("flaxen_chestnut", coat((150, 78, 40), (236, 212, 156), "feather")),
        ("grey", coat((168, 168, 170), (118, 118, 124), "feather", head=(150, 150, 154))),
        ("dapple_silver", coat((104, 106, 116), (226, 228, 236), "dapple", "feather", "shimmer", spot=(206, 210, 222),
                               head=(92, 94, 104), legs=(86, 88, 98), feather=(232, 234, 240))),
    ],
    "steppe_pony": [
        ("dun_stripe", coat(DUN, POINTS, "dorsal", "bars", points=POINTS, points_from=6, stripe=(48, 36, 28))),
        ("grulla", coat((124, 118, 110), POINTS, "dorsal", "bars", points=POINTS, points_from=6, stripe=(36, 32, 30),
                        head=(92, 86, 82))),
        ("bay_dun", coat((172, 120, 70), POINTS, "dorsal", "bars", points=POINTS, points_from=6, stripe=(52, 34, 24))),
        ("piebald", coat(BLACK, BLACK_H, "pinto", "stockings", "blaze")),
        ("buckskin", coat((206, 170, 108), POINTS, points=POINTS)),
        ("silver_dun", coat((196, 192, 182), (232, 232, 228), "dorsal", "bars", "shimmer", points=(84, 84, 86),
                            points_from=6, stripe=(88, 88, 90), hoof=(70, 68, 66))),
    ],
    "desert_horse": [
        ("white_grey", coat((232, 230, 224), (212, 212, 208), tint=(80, 74, 74), hoof=HOOF_PALE)),
        ("golden_bay", coat((178, 112, 48), POINTS, points=POINTS)),
        ("fleabitten", coat((228, 222, 210), (200, 196, 190), "flea", spot=(140, 84, 56), hoof=HOOF_PALE)),
        ("black_star", coat(BLACK, BLACK_H, "star")),
        ("chestnut", coat((168, 86, 40), (150, 72, 34))),
        ("gold_shimmer", coat((210, 164, 70), (232, 192, 112), "shimmer", hoof=(110, 90, 60), tint=(130, 96, 70))),
    ],
}


def shade_off(prof, face, j, fh):
    """Light from above: lighter tops, darker bellies, a few flat bands rather than a smooth ramp."""
    if prof == "hair":
        return {"top": 6, "bottom": -14}.get(face, 0)
    if prof == "none":
        return 0
    if face == "top":
        return 12 if prof == "body" else 6
    if face == "bottom":
        return -30 if prof == "body" else -16
    t = j / (fh - 1) if fh > 1 else 0.5
    if prof == "body":
        return 8 if t < 0.2 else 0 if t < 0.62 else -10 if t < 0.85 else -18
    return 4 if t < 0.25 else 0 if t < 0.7 else -6


class Horse:
    """Paints one horse texture: fill every box face through colour(), then the details."""

    def __init__(self, key, layout, baby, noise, hairn, size=64):
        self.rnd = random.Random(key)
        self.L, self.baby, self.noise, self.hairn = layout, baby, noise, hairn
        self.img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        self.cache = {}
        self.hoofrows = 1 if baby else 2

    def memo(self, key, make):
        if key not in self.cache:
            self.cache[key] = make()
        return self.cache[key]

    def strand(self, part, face, i):
        return self.memo(("strand", part, face, i), lambda: self.rnd.randint(-self.hairn, self.hairn))

    def hairpx(self, hair, part, face, i):
        return shade(hair, self.strand(part, face, i) + self.rnd.randint(-self.hairn // 4, self.hairn // 4))

    def paint(self, colour, post=None):
        for part in ORDER:
            for bi, box in enumerate(self.L.get(part, [])):
                for face, (x0, y0, fw, fh) in box.faces.items():
                    for j in range(fh):
                        for i in range(fw):
                            r = colour(part, bi, face, i, j, fw, fh)
                            if r is None:
                                self.img.putpixel((x0 + i, y0 + j), (0, 0, 0, 0))
                                continue
                            c, prof = r
                            n = 0 if prof in ("hair", "none") else self.rnd.randint(-self.noise, self.noise)
                            c = shade(c, shade_off(prof, face, j, fh) + n)
                            if post:
                                c = post(part, face, i, j, fw, fh, c, prof)
                            px(self.img, x0 + i, y0 + j, c)
        return self.img

    def face(self, part, face, bi=0):
        return self.L[part][bi].faces[face]


def eye(h, white=(226, 226, 214), dark=DARK, sunken=None):
    """A dark eye with a tiny highlight on both sides of the head, near its front top."""
    f0, r0 = (3, 1) if h.baby else (2, 1)
    for face in ("a", "b"):
        rect = h.face("head", face)
        if sunken:
            for f in range(f0 - 1, f0 + 3):
                for j in (r0 - 1, r0 + 2):
                    if 0 <= j < rect[3]:
                        x, y = side_xy(rect, face, f, j)
                        px(h.img, x, y, mix(get(h.img, x, y), sunken, 0.45))
        x, y = side_xy(rect, face, f0 + 1, r0 - 1)
        px(h.img, x, y, shade(get(h.img, x, y), -30))
        x, y = side_xy(rect, face, f0, r0 - 1)
        px(h.img, x, y, shade(get(h.img, x, y), -30))
        for f, j, c in ((f0 + 1, r0, dark), (f0, r0, white), (f0, r0 + 1, dark), (f0 + 1, r0 + 1, shade(dark, 14))):
            x, y = side_xy(rect, face, f, j)
            px(h.img, x, y, c)


def nostrils(h, deep=-48):
    """Darker nostrils on the muzzle's front, a mouth line along its sides."""
    if h.baby:
        x0, y0, w, hh = h.face("head", "front")
        spots = [(x0 + 1, y0 + 1), (x0 + 4, y0 + 1)]
        mouth = [(face, f, hh - 1) for face in ("a", "b") for f in range(3)]
        part = "head"
    else:
        x0, y0, w, hh = h.face("muzzle", "front")
        spots = [(x0, y0 + 1), (x0, y0 + 2), (x0 + 3, y0 + 1), (x0 + 3, y0 + 2)]
        mouth = [(face, f, 3) for face in ("a", "b") for f in range(4)]
        part = "muzzle"
    for x, y in spots:
        px(h.img, x, y, shade(get(h.img, x, y), deep))
    for face, f, j in mouth:
        x, y = side_xy(h.face(part, face), face, f, j)
        px(h.img, x, y, shade(get(h.img, x, y), -26))


def ears(h, inner):
    """The ear's front face (it points forward) is the inside of the ear: darker."""
    for bi in range(len(h.L["ear"])):
        x0, y0, w, hh = h.face("ear", "front", bi)
        for j in range(1, hh):
            for i in range(w):
                px(h.img, x0 + i, y0 + j, mix(get(h.img, x0 + i, y0 + j), inner, 0.55 if j > 1 or i == 0 else 0.35))


def living(breed, name, S, baby):
    lay = BABY if baby else ADULT
    h = Horse(f"{breed}/{name}/{'baby' if baby else 'adult'}", lay, baby, NOISE[breed], HAIRN[breed])
    rnd, marks = h.rnd, S["marks"]
    hair = S["hair"]
    if baby:
        hair = shade(mix(hair, S["base"], 0.2), 10)
    stripe = S["stripe"] or shade(hair, -20)
    hoofrows = h.hoofrows
    if breed == "draft":
        marks = marks | {"feather"}

    def body_pattern(c, part, face, i, j, fw, fh):
        key = (part, face)
        if "dapple" in marks and face != "bottom":
            m = h.memo(("dap",) + key, lambda: dapples(rnd, fw, fh, 3 if baby else 4))
            strength = 0.45 if part == "neck" else 1.0
            c = mix(c, S["spot"], (0.7 if m[j][i] == 2 else 0.3 if m[j][i] == 1 else 0) * strength)
        if "flea" in marks and rnd.random() < 0.11:
            c = mix(c, S["spot"], 0.8)
        if "roan" in marks and rnd.random() < 0.38:
            c = mix(c, S["spot"], 0.38 if rnd.random() < 0.7 else 0.6)
        if "pinto" in marks:
            m = h.memo(("pin",) + key, lambda: vnoise(rnd, fw, fh, 4 if baby else 5))
            thr = 0.36 if face == "bottom" else 0.62 if part == "neck" else 0.5
            if m[j][i] > thr:
                c = S["white"]
        if "leopard" in marks:
            if face in ("a", "b"):
                bias = (lambda x: 0.35 + 0.65 * (x / fw)) if face == "b" else (lambda x: 0.35 + 0.65 * (1 - x / fw))
            else:
                bias = None
            n = max(2, fw * fh // (26 if baby else 22))
            m = h.memo(("leo",) + key, lambda: ovals(rnd, fw, fh, n, bias=bias))
            if m[j][i]:
                c = S["spot"]
        return c

    def colour(part, bi, face, i, j, fw, fh):
        f = frontness(face, i, j, fw, fh)
        side = face in SIDES
        if part in ("mane", "tail"):
            c = hair
            if "pinto" in marks:
                m = h.memo(("pinh", part), lambda: vnoise(rnd, 4, 20, 4))
                if m[min(j, 19)][i % 4] > 0.55:
                    c = WHITE
            if "leopard" in marks and h.strand(part, face, i) % 3 == 0:
                c = (226, 220, 210)
            if "dorsal" in marks and part == "mane" and face == "back":
                c = mix(c, stripe, 0.7)
            return h.hairpx(c, part, face, i), "hair"
        c = S["base"]
        if part in ("head", "muzzle", "ear") and S["head"]:
            c = S["head"]
        if part == "leg" and S["legs"]:
            c = S["legs"]
        prof = "body"
        if part in ("body", "neck"):
            c = body_pattern(c, part, face, i, j, fw, fh)
        if part in ("head", "muzzle") and "leopard" in marks and rnd.random() < 0.06:
            c = S["spot"]
        if part in ("head", "muzzle") and "flea" in marks and rnd.random() < 0.08:
            c = mix(c, S["spot"], 0.7)
        if part == "neck":
            if face == "back":
                return h.hairpx(mix(hair, c, 0.35) if i in (0, fw - 1) else hair, part, face, i), "hair"
            if face in ("a", "b") and f == fw - 1 and rnd.random() < 0.65:
                return h.hairpx(hair, part, face, 0), "hair"
            if face == "top" and 0 < i < fw - 1 and j < 3:
                return h.hairpx(hair, part, face, i), "hair"
        if part == "head" and face == "top" and 1 < i < fw - 2 and j <= (1 if baby else 0):
            return h.hairpx(hair, part, face, i), "hair"   # the poll, where the mane starts
        if part == "ear" and S["points"] and (j == 0 and side or face == "top"):
            c = S["points"]
        if part == "leg":
            prof = "leg"
            if face == "top":
                return c, prof
            if face == "bottom":
                return shade(S["hoof"], -18 if (i, j) != (1, 1) else -30), "none"
            hoof_start = fh - hoofrows
            if j >= hoof_start:
                c, prof = S["hoof"], "none"
                if j == fh - 1:
                    c = shade(c, -10)
            else:
                if S["points"] and j >= S["points_from"] - (1 if baby else 0):
                    c = S["points"]
                elif "bars" in marks and j % 2 == 1:
                    c = mix(c, stripe, 0.35)
                if "leopard" in marks:
                    m = h.memo(("leol", bi, face), lambda: ovals(rnd, fw, fh, 2 if baby else 3, rx=(0.5, 0.9), ry=(0.6, 1.0)))
                    if m[j][i]:
                        c = S["spot"]
                white_from = None
                if "socks" in marks:
                    white_from = hoof_start - (2 if baby else 3)
                if "stockings" in marks:
                    white_from = fh // 2 - 1
                if white_from is not None and j >= white_from:
                    c = S["white"]
            if "feather" in marks and j >= hoof_start - (2 if baby else 3):
                toe = face == "front" and j == fh - 1 and i % 2 == 0
                if not toe and not (j == hoof_start - (2 if baby else 3) and rnd.random() < 0.4):
                    return h.hairpx(S["feather"], part, face, i), "hair"
            return c, prof
        if part in ("head", "muzzle"):
            if "blaze" in marks:
                if part == "head" and face == "top" and i in (2, 3) and j >= (3 if baby else 2):
                    c = S["white"]
                if part == "head" and face == "front" and (i in (2, 3) or baby and j == fh - 1 and 0 < i < fw - 1):
                    c = S["white"]
                if part == "muzzle" and face == "top" and i in (1, 2):
                    c = S["white"]
                if part == "muzzle" and face == "front" and (i in (1, 2) or j >= 3):
                    c = S["white"]
            if "star" in marks and part == "head" and face == "top" and i in (2, 3) and j in ((4, 5) if baby else (3, 4)):
                c = S["white"]
            tint = S["tint"] or (34, 28, 26)
            muzzleish = part == "muzzle" or baby and f < 3
            if muzzleish:
                if face == "front" and j >= (1 if baby else 2):
                    c = mix(c, tint, 0.35)
                elif face in ("a", "b") and j >= fh - 2:
                    c = mix(c, tint, 0.25)
                elif face == "bottom":
                    c = mix(c, tint, 0.3)
        if "dorsal" in marks and part == "body":
            mid = (fw // 2 - 1, fw // 2)
            if face == "top" and i in mid or face == "back" and i in mid and j < 3:
                c = mix(c, stripe, 0.8)
        return c, prof

    def post(part, face, i, j, fw, fh, c, prof):
        if "shimmer" in marks:
            if prof == "body" and (face == "top" or face in SIDES and j < fh * 0.45) and rnd.random() < 0.07:
                return shade(c, 34)
            if prof == "hair" and rnd.random() < 0.05:
                return shade(c, 26)
            if prof == "body" and face in SIDES and j == 1:
                return shade(c, 6)
        return c

    h.paint(colour, post)
    eye(h)
    nostrils(h)
    ears(h, mix(S["head"] or S["base"], DARK, 0.5))
    return h.img


# ---------------------------------------------------------------------------------------------------------------------
# Skeleton and zombie horses
# ---------------------------------------------------------------------------------------------------------------------
SKELETONS = {"bone": dict(bone=(222, 216, 196), dark=(70, 60, 52)),
             "sooty": dict(bone=(92, 86, 78), dark=(24, 20, 18), soot=(40, 36, 34)),
             "mossy": dict(bone=(196, 188, 156), dark=(62, 56, 42), moss=(78, 110, 46))}


def skeleton(name, S, baby):
    lay = BABY if baby else ADULT
    h = Horse(f"skeleton/{name}/{'baby' if baby else 'adult'}", lay, baby, 6, 8)
    rnd = h.rnd
    bone, dark = S["bone"], S["dark"]
    gap = dark

    def weather(c, part, bi, face, i, j, fw, fh):
        if "soot" in S:
            m = h.memo(("soot", part, bi, face), lambda: vnoise(rnd, fw, fh, 3))
            if m[j][i] > 0.58:
                c = mix(c, S["soot"], 0.8)
            elif rnd.random() < 0.05:
                c = shade(c, 40)
        if "moss" in S:
            m = h.memo(("moss", part, bi, face), lambda: vnoise(rnd, fw, fh, 3))
            lim = 0.5 if face == "top" else 0.62 if face != "bottom" else 0.9
            if m[j][i] > lim:
                c = shade(S["moss"], rnd.choice((-14, 0, 0, 12, 22)))
        return c

    def colour(part, bi, face, i, j, fw, fh):
        f = frontness(face, i, j, fw, fh)
        c, prof = bone, "body"
        if part == "body":
            if face in ("a", "b"):
                ribs_from, ribs_to = (2, int(fw * 0.85)) if not baby else (2, int(fw * 0.72))
                period = 2 if baby else 3
                if ribs_from <= f < ribs_to and 1 < j < fh - 1:
                    if (f - ribs_from) % period == period - 1:
                        return None if 1 < j < fh - 2 else (gap, "none")   # the gaps between the ribs
                    c = shade(bone, -8 if j > fh // 2 else 0)
                elif f >= ribs_to and fh // 2 - 1 <= j <= fh // 2 and abs(f - (ribs_to + fw) // 2) <= 1:
                    c = gap   # the hole in the pelvis
                elif j == fh - 1:
                    c = shade(bone, -16)
            elif face == "top" and i in (fw // 2 - 1, fw // 2) and j % 2 == 1:
                c = shade(bone, -26)   # vertebrae
        elif part == "neck" and face in SIDES and j % 3 == 2:
            c = shade(bone, -34)
        elif part == "mane":
            if j % 3 == 2:
                return None
            c = shade(bone, -10)
        elif part == "tail":
            if j % 3 == 2:
                c = shade(bone, -40)
            prof = "flat"
        elif part == "leg":
            prof = "leg"
            if face in SIDES:
                knee, fet = (3, 6) if baby else (4, 8)
                if j in (knee, fet):
                    c = mix(bone, dark, 0.7)
                elif j >= fh - h.hoofrows:
                    c, prof = shade(mix(bone, dark, 0.45), -6 if j == fh - 1 else 0), "none"
            elif face == "bottom":
                c = mix(bone, dark, 0.6)
        elif part == "ear":
            c = shade(bone, -12)
        return weather(c, part, bi, face, i, j, fw, fh), prof

    h.paint(colour)
    # skull: deep eye sockets, the nasal hole, teeth
    f0, r0 = (3, 0) if baby else (2, 0)
    for face in ("a", "b"):
        rect = h.face("head", face)
        for f in (f0, f0 + 1, f0 + 2):
            for j in (r0, r0 + 1, r0 + 2):
                if f == f0 + 2 and j != r0 + 1 or j >= rect[3]:
                    continue
                x, y = side_xy(rect, face, f, j)
                px(h.img, x, y, shade(dark, -14 if (f, j) == (f0 + 1, r0 + 1) else 0))
    if baby:
        x0, y0, w, hh = h.face("head", "front")
        for i in (1, 2, 3, 4):
            px(h.img, x0 + i, y0 + 1, dark if i in (2, 3) else shade(bone, -30))
        for i in range(w):
            px(h.img, x0 + i, y0 + 3, dark if i % 2 else bone)
        for face in ("a", "b"):
            for f in range(3):
                x, y = side_xy(h.face("head", face), face, f, 3)
                px(h.img, x, y, dark if f % 2 else bone)
    else:
        x0, y0, w, hh = h.face("muzzle", "front")
        for i in (1, 2):
            for j in (0, 1):
                px(h.img, x0 + i, y0 + j, dark)
        for i in range(w):
            px(h.img, x0 + i, y0 + 3, dark)
            px(h.img, x0 + i, y0 + 4, bone if i % 2 == 0 else shade(bone, -30))
        for face in ("a", "b"):
            rect = h.face("muzzle", face)
            for f in range(5):
                x, y = side_xy(rect, face, f, 3)
                px(h.img, x, y, dark)
                x, y = side_xy(rect, face, f, 4)
                px(h.img, x, y, bone if f % 2 == 0 else shade(bone, -34))
    ears(h, dark)
    return h.img


ZOMBIES = {"rotten": dict(hide=(96, 116, 86), hair=(48, 54, 40), flesh=(170, 70, 74), eye=(150, 160, 60)),
           "pale": dict(hide=(150, 160, 172), hair=(70, 74, 84), flesh=(136, 84, 96), eye=(40, 40, 52)),
           "mottled": dict(hide=(112, 86, 60), hair=(54, 44, 34), flesh=(150, 70, 70), green=(106, 128, 66),
                           eye=(60, 48, 36))}


def zombie(name, S, baby):
    lay = BABY if baby else ADULT
    h = Horse(f"zombie/{name}/{'baby' if baby else 'adult'}", lay, baby, 9, 14)
    rnd = h.rnd
    hide = S["hide"]
    flesh = S["flesh"]
    bonec = (222, 214, 190)
    rib_side = "a"

    def colour(part, bi, face, i, j, fw, fh):
        f = frontness(face, i, j, fw, fh)
        c, prof = hide, "body"
        if part in ("mane", "tail"):
            ragged = (j > fh * 0.55 and rnd.random() < 0.35) or rnd.random() < 0.08
            if ragged and face in SIDES:
                return None
            return h.hairpx(S["hair"], part, face, i), "hair"
        if part == "neck" and face == "back":
            return h.hairpx(S["hair"], part, face, i), "hair"
        if "green" in S and face != "bottom":
            m = h.memo(("grn", part, bi, face), lambda: vnoise(rnd, fw, fh, 3))
            if m[j][i] > 0.55:
                c = mix(hide, S["green"], 0.85)
        if name == "pale":
            m = h.memo(("blt", part, bi, face), lambda: vnoise(rnd, fw, fh, 4))
            c = mix(c, (110, 116, 140), 0.35 if m[j][i] > 0.6 else 0)
        lim = {"rotten": 0.7, "pale": 0.86, "mottled": 0.8}[name]
        if part in ("body", "neck", "leg") and face != "top":
            m = h.memo(("fl", part, bi, face), lambda: vnoise(rnd, fw, fh, 3))
            if m[j][i] > lim:
                c = shade(flesh, -30 if m[j][i] > lim + 0.08 else 0)
            elif m[j][i] > lim - 0.04:
                c = shade(c, -24)
        if name == "rotten" and part == "body" and face == rib_side:
            lo, hi = (3, 9) if baby else (5, 13)
            if lo <= f < hi and 1 < j < fh - 2:
                c = bonec if (f - lo) % 2 == 0 else shade(flesh, -50)
            elif lo - 1 <= f <= hi and 0 < j < fh - 1:
                c = shade(flesh, -10)
        if part == "leg":
            prof = "leg"
            if face == "bottom":
                return (40, 36, 34), "none"
            if face in SIDES and j >= fh - h.hoofrows:
                return (48, 44, 40), "none"
        return c, prof

    h.paint(colour)
    eye(h, white=S["eye"], dark=(26, 22, 20), sunken=(20, 18, 24))
    nostrils(h, deep=-40)
    ears(h, shade(hide, -50))
    return h.img


# ---------------------------------------------------------------------------------------------------------------------
# War elephant (128x128)
# ---------------------------------------------------------------------------------------------------------------------
EL = {"body": Box(0, 0, 14, 13, 22), "head": Box(0, 36, 10, 10, 8), "left_ear": Box(40, 36, 7, 9, 1),
      "right_ear": Box(58, 36, 7, 9, 1), "trunk1": Box(76, 0, 4, 5, 4), "trunk2": Box(76, 10, 3, 5, 3),
      "trunk3": Box(76, 19, 2, 4, 2), "tusk": Box(92, 0, 1, 1, 6), "leg": Box(0, 56, 5, 9, 5), "tail": Box(108, 0, 1, 8, 1),
      "tuft": Box(108, 10, 2, 2, 2), "platform": Box(0, 72, 16, 1, 12), "rail_fb": Box(0, 86, 16, 2, 1),
      "rail_side": Box(36, 86, 1, 2, 12), "blanket": Box(0, 101, 15, 8, 12), "plate": Box(60, 72, 11, 6, 1),
      "pole": Box(88, 72, 1, 10, 1), "flag": Box(94, 72, 0, 6, 5)}

ELEPHANTS = {"grey": dict(hide=(128, 124, 122)),
             "dusty": dict(hide=(132, 96, 76), mud=(168, 134, 100)),
             "painted": dict(hide=(124, 122, 122), paint=True),
             "albino": dict(hide=(222, 194, 188), eye=(176, 66, 76), inner=(232, 150, 156))}

CRIMSON = (152, 26, 34)
GOLD = (216, 172, 62)
WOOD = (88, 54, 30)
BRASS = (196, 156, 66)
STEEL = (160, 166, 178)
OCHRE = (204, 140, 52)
PAINT_WHITE = (236, 232, 222)


def elephant(name, S):
    rnd = random.Random("war_elephant/" + name)
    img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    hide = S["hide"]

    def fill(box, fn):
        for face, (x0, y0, fw, fh) in box.faces.items():
            for j in range(fh):
                for i in range(fw):
                    c = fn(face, i, j, fw, fh)
                    if c is not None:
                        px(img, x0 + i, y0 + j, c)

    def hide_fn(key, lower_dark=False, period=3, base=None):
        b0 = base or hide
        phase = rnd.randrange(period)
        mud = {}

        def fn(face, i, j, fw, fh):
            c = b0
            if "mud" in S:
                if face not in mud:
                    mud[face] = vnoise(rnd, fw, fh, 3)
                if mud[face][j][i] > 0.56:
                    c = S["mud"] if rnd.random() > 0.12 else shade(S["mud"], -36)
            off = shade_off("body", face, j, fh)
            if face in SIDES:
                if (j + phase) % period == 0 and rnd.random() > 0.2:
                    off -= 13   # wrinkle
                elif (j + phase) % period == 1:
                    off += 3
                if lower_dark and j >= fh - 4:
                    off -= 10
            return shade(c, off + rnd.randint(-5, 5))
        return fn

    fill(EL["body"], hide_fn("body"))
    fill(EL["head"], hide_fn("head"))
    for k in ("trunk1", "trunk2", "trunk3"):
        fill(EL[k], hide_fn(k, period=2))
    fill(EL["leg"], hide_fn("leg", lower_dark=True))
    fill(EL["tail"], hide_fn("tail", period=2))
    for k in ("left_ear", "right_ear"):
        fill(EL[k], hide_fn(k, period=3))
        x0, y0, w, h = EL[k].faces["front"]   # inside of the ear: pinkish, veined
        inner = S.get("inner", (178, 132, 128))
        for j in range(1, h - 1):
            for i in range(1, w - 1):
                c = mix(get(img, x0 + i, y0 + j), inner, 0.5)
                if (i + j) % 4 == 0 and rnd.random() < 0.6:
                    c = shade(c, -18)
                px(img, x0 + i, y0 + j, c)
    # trunk tip nostrils
    x0, y0, w, h = EL["trunk3"].faces["bottom"]
    for i in range(w):
        px(img, x0 + i, y0, shade(hide, -40))
        px(img, x0 + i, y0 + 1, (60, 40, 40) if True else hide)
    # eyes on the head sides
    eyec = S.get("eye", (26, 20, 18))
    for face in ("a", "b"):
        rect = EL["head"].faces[face]
        x, y = side_xy(rect, face, 2, 4)
        px(img, x, y, eyec)
        x, y = side_xy(rect, face, 3, 4)
        px(img, x, y, shade(eyec, 30))
        for f in (2, 3):
            x, y = side_xy(rect, face, f, 3)
            px(img, x, y, shade(get(img, x, y), -26))
        x, y = side_xy(rect, face, 2, 5)
        px(img, x, y, shade(get(img, x, y), -14))
    # toenails on each leg side
    for face in SIDES:
        x0, y0, w, h = EL["leg"].faces[face]
        for i in (0, 2, 4):
            px(img, x0 + i, y0 + h - 1, (222, 214, 192))
            px(img, x0 + i, y0 + h - 2, (204, 196, 174))
    x0, y0, w, h = EL["leg"].faces["bottom"]
    for j in range(h):
        for i in range(w):
            px(img, x0 + i, y0 + j, shade((70, 62, 58), rnd.randint(-5, 5)))
    # tusks: ivory, yellower at the root, the tip (front face) brightest
    for face, (x0, y0, w, h) in EL["tusk"].faces.items():
        for j in range(h):
            for i in range(w):
                f = frontness(face, i, j, w, h)
                c = mix((236, 228, 200), (196, 174, 124), min(1, f / 8)) if f < 99 else (180, 160, 112)
                px(img, x0 + i, y0 + j, shade(c, 10 if face == "top" else -12 if face == "bottom" else 0))
    # tail tuft: dark coarse hair
    for face, (x0, y0, w, h) in EL["tuft"].faces.items():
        for j in range(h):
            for i in range(w):
                px(img, x0 + i, y0 + j, shade((40, 34, 32), rnd.randint(-8, 8)))
    # war paint
    if S.get("paint"):
        x0, y0, w, h = EL["head"].faces["front"]
        for j in range(1, 8):
            for i, c in ((1, OCHRE), (2, OCHRE), (4, PAINT_WHITE), (5, PAINT_WHITE), (7, OCHRE), (8, OCHRE)):
                if not (j == 7 and i in (1, 8)):
                    px(img, x0 + i, y0 + j, shade(c, rnd.randint(-8, 6)))
        for face in ("a", "b"):
            rect = EL["head"].faces[face]
            for f in range(1, 7):
                for j, c in ((7, OCHRE), (8, PAINT_WHITE)):
                    x, y = side_xy(rect, face, f, j)
                    px(img, x, y, shade(c, rnd.randint(-8, 6)))
        for k in ("left_ear", "right_ear"):
            x0, y0, w, h = EL[k].faces["back"]
            for j, c in ((2, OCHRE), (4, PAINT_WHITE), (6, OCHRE)):
                for i in range(w):
                    if not (j == 6 and i in (0, w - 1)):
                        px(img, x0 + i, y0 + j, shade(c, rnd.randint(-8, 6)))
            x0, y0, w, h = EL[k].faces["front"]
            for j in range(h):
                px(img, x0 + (w - 1 if k == "left_ear" else 0), y0 + j, shade(PAINT_WHITE, rnd.randint(-8, 4)))
        for k, rows in (("trunk1", ((1, OCHRE), (3, PAINT_WHITE))), ("trunk2", ((2, OCHRE),)), ("trunk3", ((1, PAINT_WHITE),))):
            for face in SIDES:
                x0, y0, w, h = EL[k].faces[face]
                for j, c in rows:
                    for i in range(w):
                        px(img, x0 + i, y0 + j, shade(c, rnd.randint(-8, 6)))

    # --- the harness: identical on every coat (its own seed) ---
    rnd = random.Random("war_elephant/harness")

    def wood(face, i, j, fw, fh, base=WOOD, planks=True):
        c = base
        if face == "top" and planks and j % 3 == 2:
            c = shade(base, -22)
        off = {"top": 8, "bottom": -22}.get(face, -4)
        return shade(c, off + rnd.randint(-5, 5) + (6 if rnd.random() < 0.06 else 0))

    def platform(face, i, j, fw, fh):
        if face == "top" and (i in (0, fw - 1) or j in (0, fh - 1)):
            return shade(BRASS, 10 if (i + j) % 4 == 0 else rnd.randint(-8, 4))
        if face in SIDES and i % 4 == 1:
            return shade(BRASS, 4)
        return wood(face, i, j, fw, fh)
    fill(EL["platform"], platform)

    def rail(along):
        def fn(face, i, j, fw, fh):
            n = fw if along == "x" else fh
            k = i if along == "x" else (j if face in ("top", "bottom") else i)
            if face in ("top", "bottom") or face in (("front", "back") if along == "x" else ("a", "b")):
                if k in (0, (fw if face in SIDES else n) - 1):
                    return shade(BRASS, 14 if face == "top" else 0)
            return wood(face, i, j, fw, fh, base=(104, 66, 38), planks=False)
        return fn
    fill(EL["rail_fb"], rail("x"))
    fill(EL["rail_side"], rail("z"))

    def blanket(face, i, j, fw, fh):
        if face == "top":
            return shade(CRIMSON, -20 + rnd.randint(-4, 4))
        if face == "bottom":
            return shade((92, 20, 26), rnd.randint(-4, 4))
        c = CRIMSON
        cc = i % 6
        if abs(cc - 3) + abs(j - 3) == 1:
            c = GOLD   # a row of small gold lozenges
        elif (cc, j) == (3, 3):
            c = shade(GOLD, 24)
        elif j == 1 and cc == 0:
            c = shade(GOLD, -20)
        if j == fh - 2:
            c = GOLD
        if j == fh - 1:
            c = shade(GOLD, -20) if i % 2 == 0 else shade(CRIMSON, -40)
        if j == 0:
            c = shade(c, -18)
        return shade(c, rnd.randint(-6, 6) - (8 if face in ("a", "b") and j > 4 and c == CRIMSON else 0))
    fill(EL["blanket"], blanket)

    def plate(face, i, j, fw, fh):
        if face == "front":
            c = STEEL
            if i == fw // 2:
                c = GOLD
            elif i in (0, fw - 1) or j in (0, fh - 1):
                c = shade(STEEL, -26)
            elif (i, j) in ((1, 1), (fw - 2, 1), (1, fh - 2), (fw - 2, fh - 2), (3, 1), (fw - 4, 1)):
                c = (78, 80, 88)
            elif (i, j) in ((2, 2), (fw - 3, 2)):
                c = shade(STEEL, 40)
            elif j == 1:
                c = shade(STEEL, 14)
            return shade(c, rnd.randint(-4, 4))
        return shade(STEEL, -40 + rnd.randint(-4, 4))
    fill(EL["plate"], plate)

    def pole(face, i, j, fw, fh):
        if face == "top":
            return GOLD
        return wood(face, i, j, fw, fh, base=(110, 74, 42), planks=False)
    fill(EL["pole"], pole)

    sun = ["..g..", ".GGG.", "gGOGg", "gGOGg", ".GGG.", "..g.."]

    def flag(face, i, j, fw, fh):
        if face not in ("a", "b"):
            return None
        col = i if face == "b" else fw - 1 - i
        ch = sun[j][col]
        c = {"g": shade(GOLD, -14), "G": GOLD, "O": (230, 120, 40)}.get(ch, CRIMSON)
        return shade(c, rnd.randint(-5, 5) + (-10 if ch == "." and col == fw - 1 else 0))
    fill(EL["flag"], flag)
    return img


# ---------------------------------------------------------------------------------------------------------------------
# Items
# ---------------------------------------------------------------------------------------------------------------------
def outline(img, colour):
    w, h = img.size
    src = img.copy()
    for y in range(h):
        for x in range(w):
            if src.getpixel((x, y))[3]:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if 0 <= x + dx < w and 0 <= y + dy < h and src.getpixel((x + dx, y + dy))[3]:
                    px(img, x, y, colour)
                    break


def mane_lock():
    rnd = random.Random("item/mane_lock")
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    browns = [(60, 38, 24), (80, 52, 30), (96, 64, 38), (50, 32, 20)]
    for s, off in enumerate((-1.5, -0.5, 0.5, 1.5)):
        col = browns[s]
        for k in range(120):
            t = k / 119
            if t > 0.96 - abs(off) * 0.12:
                break
            x = 8.5 + off * (1 - 0.6 * t) + 3.6 * math.sin(t * 3.4 - 0.5) - 1.5 * t
            y = 1.5 + 12.8 * t
            px(img, int(x), int(y), shade(col, rnd.randint(-6, 6)))
    # a curl at the tip
    for x, y in ((5, 14), (4, 14), (4, 13)):
        px(img, x, y, browns[1])
    # highlights along one edge
    for y in range(2, 13, 3):
        for x in range(16):
            if img.getpixel((x, y))[3]:
                px(img, x, y, (130, 92, 56))
                break
    # the red ribbon tied round the top, with a bow
    for x, y in ((8, 4), (9, 4), (10, 4), (11, 4), (7, 4), (8, 5), (9, 5), (10, 5), (11, 5), (7, 5)):
        if img.getpixel((x, y))[3] or x in (7, 11):
            px(img, x, y, (196, 32, 38) if y == 4 else (150, 22, 28))
    for x, y, c in ((12, 3, (196, 32, 38)), (13, 3, (176, 26, 32)), (13, 4, (150, 22, 28)), (12, 4, (214, 60, 60)),
                    (12, 6, (176, 26, 32)), (13, 7, (150, 22, 28)), (11, 6, (196, 32, 38))):
        px(img, x, y, c)
    outline(img, (34, 20, 14))
    return img


def spawn_egg():
    rnd = random.Random("item/war_elephant_spawn_egg")
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    cx, cy, b = 7.5, 8.6, 6.4
    inside = set()
    for y in range(16):
        for x in range(16):
            t = (y - cy) / b
            a = 4.4 + 0.9 * t
            if abs(t) <= 1 and ((x - cx) / a) ** 2 + t * t <= 1:
                inside.add((x, y))
    grey = (132, 128, 128)
    for x, y in inside:
        off = 0
        if x - cx + (y - cy) * 0.8 > 2.5:
            off = -16
        if x - cx + (y - cy) * 0.8 > 4.8:
            off = -28
        px(img, x, y, shade(grey, off + rnd.randint(-4, 4)))
    for x, y in ((5, 4), (5, 5), (4, 6)):
        px(img, x, y, (200, 198, 196))
    for spot in (((9, 4), (10, 4), (9, 5)), ((5, 9), (6, 9), (5, 10), (6, 10)), ((10, 9), (11, 10), (10, 10)),
                 ((7, 13), (8, 13)), ((8, 7),)):
        for x, y in spot:
            if (x, y) in inside:
                px(img, x, y, (168, 28, 36) if x - cx + (y - cy) * 0.8 < 3 else (124, 20, 28))
    outline(img, (32, 30, 32))
    return img


# ---------------------------------------------------------------------------------------------------------------------
def save(img, rel, sheet):
    path = os.path.join(TEX, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    sheet.append((rel, img))


def preview(sheet):
    font = ImageFont.load_default()
    rows, row = [], []
    last = None
    for rel, img in sheet:
        group = os.path.dirname(rel) + ("/baby" if rel.endswith("_baby.png") else "")
        if row and (group != last or len(row) >= 6):
            rows.append(row)
            row = []
        row.append((rel, img))
        last = group
    rows.append(row)
    pad, label = 10, 14
    def tile(img):
        return max(img.width * 4, 170)
    width = max(sum(tile(i) + pad for _, i in r) for r in rows) + pad
    height = sum(max(i.height * 4 for _, i in r) + label + pad for r in rows) + pad
    out = Image.new("RGBA", (width, height), (40, 40, 46, 255))
    d = ImageDraw.Draw(out)
    y = pad
    for r in rows:
        x = pad
        for rel, img in r:
            w, h = img.width * 4, img.height * 4
            d.text((x, y), rel.replace("entity/", "").replace(".png", ""), fill=(230, 230, 230), font=font)
            for cy in range(0, h, 16):
                for cx in range(0, w, 16):
                    d.rectangle((x + cx, y + label + cy, x + cx + 15, y + label + cy + 15),
                                fill=(70, 70, 76) if (cx + cy) // 16 % 2 else (90, 90, 96))
            big = img.resize((w, h), Image.NEAREST)
            out.alpha_composite(big, (x, y + label))
            x += tile(img) + pad
        y += max(i.height * 4 for _, i in r) + label + pad
    os.makedirs(os.path.dirname(PREVIEW), exist_ok=True)
    out.save(PREVIEW)


def main():
    sheet = []
    for breed, coats in COATS.items():
        for name, spec in coats:
            save(living(breed, name, spec, False), f"entity/equine/{breed}/{name}.png", sheet)
        for name, spec in coats:
            save(living(breed, name, spec, True), f"entity/equine/{breed}/{name}_baby.png", sheet)
    for kind, table, fn in (("skeleton", SKELETONS, skeleton), ("zombie", ZOMBIES, zombie)):
        for name, spec in table.items():
            save(fn(name, spec, False), f"entity/equine/{kind}/{name}.png", sheet)
        for name, spec in table.items():
            save(fn(name, spec, True), f"entity/equine/{kind}/{name}_baby.png", sheet)
    for name, spec in ELEPHANTS.items():
        save(elephant(name, spec), f"entity/war_elephant/{name}.png", sheet)
    save(mane_lock(), "item/mane_lock.png", sheet)
    save(spawn_egg(), "item/war_elephant_spawn_egg.png", sheet)
    preview(sheet)
    print(f"wrote {len(sheet)} textures under {TEX}; preview at {PREVIEW}")


if __name__ == "__main__":
    main()
