"""
The ten votive panels of the Lantern Warden (paintings jaymospowers:votive_1..10, 4 wide and 6 tall), which hang in the
Darkened Chapel where the Church of Sin's icons hung. Our own pictures: the Warden's offerings (a lantern, a candle, a
bone, the glow of the deep squid) and his keepers, in the night blues and lamp gold of a soul lantern.

Writes the textures, the painting_variant files and the placeable tag. Run from the project root:
    python3 -I tools/gen_votive_panels.py
"""
import json
import math
import os

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEXTURES = os.path.join(ROOT, "src", "main", "resources", "assets", "jaymospowers", "textures", "painting")
VARIANTS = os.path.join(ROOT, "src", "main", "resources", "data", "jaymospowers", "painting_variant")
TAG = os.path.join(ROOT, "src", "main", "resources", "data", "minecraft", "tags", "painting_variant", "placeable.json")
W, H = 64, 96

NIGHT_TOP, NIGHT_LOW = (15, 18, 26), (26, 32, 42)
INK, SHADE, STONE = (9, 11, 15), (20, 24, 32), (44, 52, 64)
GOLD, GOLD_DARK, FLAME = (226, 184, 104), (150, 110, 52), (255, 238, 190)
SOUL, SOUL_DARK = (118, 204, 214), (52, 110, 128)
BONE, BONE_DARK = (206, 200, 178), (132, 126, 110)

# an ordered dither, so the glows break up into pixels instead of smooth bands
BAYER = [[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]

NAMES = [
    ("The Keeper", "a keeper with the lamp at his breast"),
    ("The Keeper at Vigil", "a keeper with the lamp held up"),
    ("The Lantern", "the lantern"),
    ("The Candle", "the candle"),
    ("The Bone", "the bone given back"),
    ("The Deep Glow", "the glow of the deep squid"),
    ("The Chapel", "the chapel under its lamp"),
    ("The Barrow", "the lamp on the barrow"),
    ("The Moth", "a moth at the flame"),
    ("The Three Keepers", "three keepers and their lamps"),
]


def night(seed):
    image = Image.new("RGB", (W, H))
    px = image.load()
    for y in range(H):
        for x in range(W):
            t = y / (H - 1) + (BAYER[y % 4][x % 4] - 7.5) / 16.0 * 0.22
            t = min(1.0, max(0.0, t))
            # five flat steps of sky, like a painted panel
            t = round(t * 4) / 4
            px[x, y] = tuple(round(a + (b - a) * t) for a, b in zip(NIGHT_TOP, NIGHT_LOW))
    # a few specks of wear, the same for a given panel every run
    state = seed * 7919 + 17
    for _ in range(26):
        state = (state * 1103515245 + 12345) & 0x7FFFFFFF
        x, y = state % W, (state >> 8) % H
        px[x, y] = tuple(max(0, c - 6) for c in px[x, y])
    return image


def glow(image, cx, cy, radius, colour, strength=1.0):
    px = image.load()
    for y in range(max(0, int(cy - radius)), min(H, int(cy + radius) + 1)):
        for x in range(max(0, int(cx - radius)), min(W, int(cx + radius) + 1)):
            d = math.hypot(x - cx, y - cy) / radius
            if d >= 1.0:
                continue
            a = (1.0 - d) ** 2 * strength + (BAYER[y % 4][x % 4] - 7.5) / 16.0 * 0.12
            a = round(min(1.0, max(0.0, a)) * 5) / 5 * 0.8
            px[x, y] = tuple(round(c + (g - c) * a) for c, g in zip(px[x, y], colour))


def lantern(d, x, y, colour=GOLD, core=FLAME, lit=True):
    """A small hand lantern, its top ring at (x, y): 7 wide, 11 tall."""
    d.rectangle([x - 1, y, x + 1, y], fill=INK)
    d.rectangle([x - 2, y + 1, x + 2, y + 2], fill=STONE)
    d.rectangle([x - 3, y + 3, x + 3, y + 8], fill=INK)
    if lit:
        d.rectangle([x - 2, y + 3, x + 2, y + 8], fill=colour)
        d.rectangle([x - 1, y + 4, x + 1, y + 7], fill=core)
    else:
        d.rectangle([x - 2, y + 3, x + 2, y + 8], fill=SHADE)
    d.line([x, y + 3, x, y + 8], fill=INK if not lit else GOLD_DARK)
    d.rectangle([x - 3, y + 9, x + 3, y + 10], fill=STONE)


def keeper(d, x, base, tall=44, wide=22):
    """A hooded keeper as a dark shape, feet at base, centred on x."""
    top = base - tall
    d.polygon([(x - wide // 2, base), (x - wide // 2 + 3, top + 16), (x - 5, top + 9), (x + 5, top + 9),
        (x + wide // 2 - 3, top + 16), (x + wide // 2, base)], fill=INK)
    d.ellipse([x - 6, top, x + 6, top + 14], fill=INK)
    d.polygon([(x - 6, top + 6), (x, top - 3), (x + 6, top + 6)], fill=INK)
    # the face is only a lighter hollow in the hood
    d.ellipse([x - 3, top + 4, x + 3, top + 11], fill=SHADE)
    # folds
    d.line([x - 4, top + 20, x - 6, base], fill=SHADE)
    d.line([x + 4, top + 20, x + 6, base], fill=SHADE)


def ground(d, y, colour=INK):
    d.rectangle([0, y, W, H], fill=colour)
    for x in range(0, W, 5):
        d.point((x + (x // 5) % 3, y - 1), fill=colour)


def frame(image):
    d = ImageDraw.Draw(image)
    d.rectangle([0, 0, W - 1, H - 1], outline=INK)
    d.rectangle([1, 1, W - 2, H - 2], outline=GOLD_DARK)
    d.rectangle([2, 2, W - 3, H - 3], outline=INK)
    for x, y in ((1, 1), (W - 2, 1), (1, H - 2), (W - 2, H - 2)):
        d.point((x, y), fill=GOLD)


def panel_keeper(seed, raised):
    image = night(seed)
    lx, ly = (44, 26) if raised else (32, 52)
    glow(image, lx, ly + 6, 26, GOLD)
    d = ImageDraw.Draw(image)
    ground(d, 84)
    keeper(d, 30 if raised else 32, 86, tall=52, wide=26)
    if raised:
        d.line([36, 50, 44, 28], fill=INK, width=3)
    lantern(d, lx, ly)
    return image


def panel_lantern(seed):
    image = night(seed)
    glow(image, 32, 48, 30, GOLD, 1.2)
    d = ImageDraw.Draw(image)
    d.line([32, 6, 32, 30], fill=INK)
    for y in range(8, 30, 4):
        d.point((31, y), fill=STONE)
    # a big lantern: the panel's whole subject
    d.rectangle([28, 30, 36, 31], fill=INK)
    d.rectangle([25, 32, 39, 35], fill=STONE)
    d.rectangle([22, 36, 42, 62], fill=INK)
    d.rectangle([24, 38, 40, 60], fill=GOLD)
    d.rectangle([27, 41, 37, 57], fill=FLAME)
    d.line([32, 36, 32, 62], fill=GOLD_DARK)
    d.line([22, 49, 42, 49], fill=GOLD_DARK)
    d.rectangle([21, 63, 43, 66], fill=STONE)
    d.rectangle([24, 67, 40, 68], fill=INK)
    return image


def panel_candle(seed):
    image = night(seed)
    glow(image, 32, 36, 24, GOLD, 1.1)
    d = ImageDraw.Draw(image)
    ground(d, 82)
    d.rectangle([20, 78, 44, 81], fill=STONE)
    d.rectangle([27, 46, 37, 78], fill=BONE)
    d.rectangle([35, 46, 37, 78], fill=BONE_DARK)
    d.rectangle([27, 52, 28, 60], fill=BONE_DARK)
    d.line([32, 41, 32, 46], fill=INK)
    d.polygon([(32, 26), (28, 37), (32, 42), (36, 37)], fill=GOLD)
    d.polygon([(32, 31), (30, 38), (32, 41), (34, 38)], fill=FLAME)
    return image


def panel_bone(seed):
    image = night(seed)
    glow(image, 32, 30, 18, GOLD, 0.9)
    d = ImageDraw.Draw(image)
    ground(d, 84)
    d.rectangle([14, 70, 50, 83], fill=STONE)
    d.rectangle([14, 70, 50, 71], fill=SHADE)
    for (x0, y0, x1, y1) in ((18, 62, 46, 46), (18, 46, 46, 62)):
        d.line([x0, y0, x1, y1], fill=BONE, width=3)
        for (x, y) in ((x0, y0), (x1, y1)):
            d.ellipse([x - 3, y - 3, x + 3, y + 3], fill=BONE)
            d.point((x, y), fill=BONE_DARK)
    lantern(d, 32, 22)
    return image


def panel_squid(seed):
    image = night(seed)
    glow(image, 32, 40, 28, SOUL, 1.0)
    d = ImageDraw.Draw(image)
    d.ellipse([22, 20, 42, 50], fill=SOUL_DARK)
    d.ellipse([25, 23, 39, 45], fill=SOUL)
    d.rectangle([27, 40, 29, 43], fill=INK)
    d.rectangle([35, 40, 37, 43], fill=INK)
    for i, x in enumerate((24, 28, 32, 36, 40)):
        end = 66 + (i % 2) * 8
        d.line([x, 48, x + (i - 2), end], fill=SOUL_DARK, width=2)
        d.point((x + (i - 2), end), fill=SOUL)
    for (x, y) in ((12, 24), (52, 30), (10, 62), (54, 70), (18, 80), (46, 84)):
        d.point((x, y), fill=SOUL)
    return image


def panel_chapel(seed):
    image = night(seed)
    glow(image, 32, 20, 16, GOLD, 1.0)
    d = ImageDraw.Draw(image)
    ground(d, 82)
    d.rectangle([14, 56, 50, 82], fill=INK)
    d.polygon([(10, 58), (32, 40), (54, 58)], fill=INK)
    d.rectangle([27, 28, 37, 46], fill=INK)
    d.polygon([(25, 30), (32, 20), (39, 30)], fill=INK)
    for x in (20, 30, 40):
        d.rectangle([x, 64, x + 3, 72], fill=SHADE)
    d.rectangle([29, 74, 35, 82], fill=SHADE)
    lantern(d, 32, 9)
    for x in (4, 8, 56, 60):
        d.line([x, 82, x, 66 + (x % 5)], fill=INK)
        d.polygon([(x - 3, 72), (x, 60 + (x % 5)), (x + 3, 72)], fill=INK)
    return image


def panel_barrow(seed):
    image = night(seed)
    glow(image, 32, 40, 20, SOUL, 1.0)
    d = ImageDraw.Draw(image)
    ground(d, 84)
    d.pieslice([4, 52, 60, 116], 180, 360, fill=INK)
    d.rectangle([26, 70, 38, 84], fill=SHADE)
    d.rectangle([24, 68, 40, 70], fill=STONE)
    d.rectangle([24, 70, 26, 84], fill=STONE)
    d.rectangle([38, 70, 40, 84], fill=STONE)
    for x in (10, 52):
        d.rectangle([x, 72, x + 3, 84], fill=STONE)
    lantern(d, 32, 40, colour=SOUL, core=(214, 246, 248))
    return image


def panel_moth(seed):
    image = night(seed)
    glow(image, 32, 58, 24, GOLD, 1.1)
    d = ImageDraw.Draw(image)
    lantern(d, 32, 54)
    d.rectangle([28, 66, 36, 86], fill=INK)
    d.rectangle([22, 86, 42, 88], fill=STONE)
    # the moth, wings open, above the lamp
    d.polygon([(32, 28), (18, 18), (16, 30), (26, 36)], fill=BONE_DARK)
    d.polygon([(32, 28), (46, 18), (48, 30), (38, 36)], fill=BONE_DARK)
    d.polygon([(32, 32), (22, 36), (24, 44), (31, 38)], fill=BONE)
    d.polygon([(32, 32), (42, 36), (40, 44), (33, 38)], fill=BONE)
    d.rectangle([31, 24, 33, 40], fill=INK)
    d.point((20, 24), fill=INK)
    d.point((44, 24), fill=INK)
    d.line([31, 24, 28, 19], fill=INK)
    d.line([33, 24, 36, 19], fill=INK)
    return image


def panel_three(seed):
    image = night(seed)
    for x in (14, 32, 50):
        glow(image, x, 62, 12, GOLD, 0.9)
    d = ImageDraw.Draw(image)
    ground(d, 86)
    keeper(d, 14, 88, tall=40, wide=16)
    keeper(d, 50, 88, tall=40, wide=16)
    keeper(d, 32, 88, tall=50, wide=20)
    for x in (14, 32, 50):
        lantern(d, x, 58)
    d.arc([6, 10, 58, 62], 180, 360, fill=GOLD_DARK)
    d.arc([8, 12, 56, 60], 180, 360, fill=INK)
    return image


def main():
    panels = [
        panel_keeper(1, False), panel_keeper(2, True), panel_lantern(3), panel_candle(4), panel_bone(5),
        panel_squid(6), panel_chapel(7), panel_barrow(8), panel_moth(9), panel_three(10),
    ]
    os.makedirs(TEXTURES, exist_ok=True)
    os.makedirs(VARIANTS, exist_ok=True)
    os.makedirs(os.path.dirname(TAG), exist_ok=True)
    for i, image in enumerate(panels, start=1):
        frame(image)
        image.save(os.path.join(TEXTURES, "votive_%d.png" % i))
        with open(os.path.join(VARIANTS, "votive_%d.json" % i), "w", newline="\n") as f:
            json.dump({
                "asset_id": "jaymospowers:votive_%d" % i,
                "width": 4,
                "height": 6,
                "title": {"color": "yellow", "translate": "painting.jaymospowers.votive_%d.title" % i},
                "author": {"color": "gray", "translate": "painting.jaymospowers.votive.author"},
            }, f, indent=2)
            f.write("\n")
    values = []
    if os.path.exists(TAG):
        values = [v for v in json.load(open(TAG))["values"] if not v.startswith("jaymospowers:votive_")]
    values += ["jaymospowers:votive_%d" % i for i in range(1, 11)]
    with open(TAG, "w", newline="\n") as f:
        json.dump({"replace": False, "values": values}, f, indent=2)
        f.write("\n")
    print("wrote 10 panels;", {"painting.jaymospowers.votive_%d.title" % (i + 1): n[0] for i, n in enumerate(NAMES)})


if __name__ == "__main__":
    main()
