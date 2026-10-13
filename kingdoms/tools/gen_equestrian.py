"""Jaymo's Equestrian's art, all original and procedural: the coat specs of the six breeds (destrier, courser, palfrey,
draft, steppe pony, desert horse; six coats each, the last one rare), which tools/gen_moc_horses.py paints onto Mo'
Creatures' horse (0.5.44; until then this drew them for vanilla's horse model), the noise helpers it uses, and two
16x16 items, the mane lock and the war elephant spawn egg (the war elephant and mammoth hides are Mo' Creatures', built
by tools/gen_elephant_hides.py). Coat marks: blaze, star, socks, stockings, feather, dapple, roan, flea(bitten), pinto,
leopard, dorsal (stripe), bars (on the legs), shimmer. Seeded, so every run draws the same pixels. A preview sheet goes
to /tmp/claude-0/equestrian-preview.png. Run from kingdoms/: python3 -I tools/gen_equestrian.py"""
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
    save(mane_lock(), "item/mane_lock.png", sheet)
    save(spawn_egg(), "item/war_elephant_spawn_egg.png", sheet)
    preview(sheet)
    print(f"wrote {len(sheet)} textures under {TEX}; preview at {PREVIEW}")


if __name__ == "__main__":
    main()
