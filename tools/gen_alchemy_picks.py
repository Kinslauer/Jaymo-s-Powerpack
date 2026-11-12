"""Alchemy picks (skin draughts, dampening, the Draught of Release and the two pacts): item textures, item models and
definitions, and the three status icons.

Textures are painted as pixel grids after docs/Vanilla-Style.md: 16x16 items (the effect icons 18x18), no black outlines,
light from the top-left, a few shades per ramp, no half-transparent pixels. The bottles share one glass body.

Run from the repository root: python3 -I tools/gen_alchemy_picks.py
Needs only Pillow; reads nothing from the Minecraft jar.
"""
import json
import math
import os

from PIL import Image

ASSETS = "src/main/resources/assets/jaymospowers/"
ITEM_TEX = ASSETS + "textures/item/alchemy/"
EFFECT_TEX = ASSETS + "textures/mob_effect/"
CLEAR = (0, 0, 0, 0)


def hexc(s):
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


# dark to light
GLASS = [hexc(c) for c in ("6a9ba5", "86bac6", "a8d0d9", "d0eae9")]
OAK = [hexc(c) for c in ("67502c", "7e6237", "967441", "af8f55")]
WAX = [hexc(c) for c in ("6e1d1d", "8f2a2a", "b13a3a", "cf5252")]
EARTH = [hexc(c) for c in ("4b3b2a", "66503a", "866a4a", "a8895f", "cdb78a")]
SLATE = [hexc(c) for c in ("3d4650", "56626e", "73818e", "97a6b2", "c3cfd8")]
PALE = [hexc(c) for c in ("7d6fb0", "9d90d0", "bfb4ea", "e1daf8", "ffffff")]
PHIAL = [hexc(c) for c in ("5b3f9a", "7b58c4", "9d7ce0", "c3aaf5", "ece2ff")]
EMBER = [hexc(c) for c in ("5a1d12", "9b3416", "d2562b", "f08a35", "ffd36b")]
HOLLOW = [hexc(c) for c in ("14102a", "2a2150", "453a7c", "6b58a8", "a99ad6")]
DAMP = [hexc(c) for c in ("3b434c", "56616d", "7c8791", "a3adb6", "d0d7dd")]
GLEAM = hexc("f2eaff")

BOTTLE_ROWS = {  # row -> half width of the glass body (centre between columns 7 and 8)
    3: 2, 4: 2, 5: 2, 6: 3, 7: 4, 8: 5, 9: 5, 10: 5, 11: 5, 12: 5, 13: 4,
}


def new(size=16):
    return Image.new("RGBA", (size, size), CLEAR)


def save(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def bottle(liquid, fill_from, stopper):
    img = new()
    px = img.load()
    inside = {}
    for y, hw in BOTTLE_ROWS.items():
        for x in range(8 - hw, 8 + hw):
            inside[(x, y)] = True
    for (x, y) in inside:
        edge = any((x + dx, y + dy) not in inside for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if edge:
            px[x, y] = GLASS[3] if (x + y) % 2 == 0 and (x < 8 and y < 8) else GLASS[1] if x < 8 and y < 11 else GLASS[0]
        elif y >= fill_from:
            lit = x <= 5
            px[x, y] = liquid[3] if lit else liquid[2] if x < 9 else liquid[1] if y < 12 else liquid[0]
        else:
            px[x, y] = GLASS[3] if (x, y) == (6, 6) else CLEAR
    for y in (1, 2):
        for x in range(6, 10):
            px[x, y] = stopper[3] if y == 1 and x < 8 else stopper[2] if x < 8 else stopper[1]
    px[9, 2] = stopper[0]
    px[4, 9] = liquid[-1]
    px[4, 10] = liquid[-1]
    return img


def skin_draught():
    img = bottle(EARTH, 8, OAK)
    px = img.load()
    # flecks of stone in the liquid
    px[7, 10] = SLATE[3]
    px[9, 12] = SLATE[2]
    save(img, ITEM_TEX + "skin_draught.png")


def dampening_draught():
    img = bottle(SLATE, 7, OAK)
    px = img.load()
    px[8, 9] = DAMP[4]
    px[9, 11] = DAMP[4]
    save(img, ITEM_TEX + "dampening_draught.png")


def release_draught():
    img = bottle(PALE, 8, WAX)
    px = img.load()
    px[10, 9] = GLEAM
    px[7, 11] = GLEAM
    save(img, ITEM_TEX + "release_draught.png")


def power_phial():
    img = bottle(PHIAL, 6, WAX)
    px = img.load()
    for (x, y) in ((6, 8), (9, 10), (7, 12), (10, 7)):
        px[x, y] = GLEAM
    # the wax seal runs over the stopper
    px[8, 3] = WAX[2]
    px[7, 3] = WAX[3]
    save(img, ITEM_TEX + "power_phial.png")


def disc(ramp, fn):
    """An 18x18 icon from a function (x, y) -> ramp index or None."""
    img = new(18)
    px = img.load()
    for y in range(18):
        for x in range(18):
            i = fn(x + 0.5 - 9.0, y + 0.5 - 9.0)
            if i is not None:
                px[x, y] = ramp[max(0, min(len(ramp) - 1, i))]
    return img


def dampened_icon():
    """A muffled disc: a grey round with a slash through it."""
    def shade(dx, dy):
        r = math.hypot(dx, dy)
        if r > 7.2:
            return None
        if abs(dx + dy) < 1.2 and r > 2.0:
            return 0
        if r > 6.2:
            return 1
        return 3 if dx + dy < -3 else 2
    save(disc(DAMP, shade), EFFECT_TEX + "dampened.png")


def cinder_icon():
    """A flame in an ember ring."""
    def shade(dx, dy):
        r = math.hypot(dx, dy)
        if 6.0 < r <= 7.6:
            return 1 if dx + dy > 0 else 2
        width = 4.2 - (dy + 5.0) * 0.45 if dy > -5.5 else 0
        tip = dy < -5.0 and abs(dx) < 1.0 + (dy + 7.0) * 0.4
        if (dy < 5.5 and dy > -7.0 and abs(dx) < max(width, 0.0) + (0.0 if dy > 0 else 0.0)) or tip:
            if abs(dx) < 1.4 and dy > -1:
                return 4
            return 3 if abs(dx) < 2.6 else 2
        return None
    save(disc(EMBER, shade), EFFECT_TEX + "cinder_pact.png")


def hollow_icon():
    """A ring around nothing: a pale rim, a dark hollow."""
    def shade(dx, dy):
        r = math.hypot(dx, dy)
        if r > 7.6:
            return None
        if r > 6.0:
            return 4 if dx + dy < -2 else 3
        if r > 4.4:
            return 2
        return 0 if r > 2.4 else 1
    save(disc(HOLLOW, shade), EFFECT_TEX + "hollow_pact.png")


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def item_files(name):
    write_json(ASSETS + "items/%s.json" % name, {"model": {"type": "minecraft:model", "model": "jaymospowers:item/alchemy/" + name}})
    write_json(ASSETS + "models/item/alchemy/%s.json" % name,
               {"parent": "minecraft:item/generated", "textures": {"layer0": "jaymospowers:item/alchemy/" + name}})


def main():
    skin_draught()
    dampening_draught()
    release_draught()
    power_phial()
    dampened_icon()
    cinder_icon()
    hollow_icon()
    for name in ("skin_draught", "dampening_draught", "release_draught", "power_phial"):
        item_files(name)


if __name__ == "__main__":
    main()
