"""Art and item files for track A of Jaymo's Alchemy: the Scrying Lens, the Apothecary's Codex, the phials, and the 33
essence symbols the Codex and the lens panel draw. Also writes their item definitions, models and recipes.

Item art follows docs/Vanilla-Style.md: 16x16, no black outlines (the darkest shade of the material outlines it), light
from the top-left, vanilla's own ramps for gold, glass and the stick. The essence symbols are our own alchemical-looking
glyphs (never anyone else's aspect icons), drawn in each essence's colour with a lit top-left edge and a shadow.

The essence phial is two layers, like vanilla's potion: layer0 the liquid (grey, tinted by the `jaymospowers:essence`
item tint source in client/alchemy/CodexClient), layer1 the glass over it.

Run from the repository root: python3 -I tools/gen_alchemy_codex.py
"""
import json
import math
import os
from PIL import Image

ASSETS = "src/main/resources/assets/jaymospowers/"
DATA = "src/main/resources/data/jaymospowers/"

# the essences and their colours, as in alchemy/Essence.java
ESSENCES = {
    "fire": 0xE2572B, "water": 0x3B78D8, "earth": 0x6B4F2A, "air": 0xDCE8F0, "salt": 0xF2EEE2, "sulphur": 0xD9C22E,
    "mercury": 0xA9B4BF, "life": 0xD8364A, "light": 0xFFF3A0, "metal": 0x8E8E96, "frost": 0xA8E4FF, "motion": 0xB7F0D0,
    "spark": 0xFF9B21, "rot": 0x5E6B2A, "venom": 0x7AC74F, "crystal": 0x7FE0D8, "spirit": 0xC9B8FF, "plant": 0x4E9A2E,
    "beast": 0x9C6B3E, "death": 0x3A3340, "mind": 0xF2A8E0, "shadow": 0x2B2240, "arcana": 0x9B4DFF, "wealth": 0xF5C542,
    "craft": 0xB08A5A, "war": 0xA02020, "undeath": 0x5B6E4A, "flux": 0x8A2BB8,
}


def hx(h):
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255)


def hexc(s):
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))


def mix(a, b, t):
    return tuple(int(round(a[i] * (1 - t) + b[i] * t)) for i in range(3))


def save(im, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im.save(path)


def paint(rows, pal):
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), [len(r) for r in rows]
    im = Image.new("RGBA", (16, 16))
    for y, r in enumerate(rows):
        for x, ch in enumerate(r):
            if ch != ".":
                im.putpixel((x, y), pal[ch] + (255,))
    return im


# --- vanilla ramps ---
GOLD = [hexc(c) for c in ("3f2e0e", "825d16", "dc9613", "e9b115", "eaee57", "fdff76", "ffffff")]
STICK = [hexc(c) for c in ("281e0b", "493615", "684e1e", "896727")]
GLASS_L, GLASS_M, GLASS_D, GLASS_X = hexc("d4e5f7"), hexc("b3cfec"), hexc("8badd0"), hexc("5d8fc2")
CORK_L, CORK_D = hexc("d46d49"), hexc("a94725")


# --- the lens: a gold ring round violet glass, a stick for a handle ---
def lens():
    im = Image.new("RGBA", (16, 16))
    cx, cy, ro, ri = 9.2, 6.3, 5.4, 3.5
    glass = [hexc(c) for c in ("5b43b0", "7e63d6", "a48cf0", "d2c4ff")]
    pix = {}
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - cx, y - cy)
            if d <= ro:
                pix[(x, y)] = "ring" if d > ri else "glass"

    def open_at(x, y, kind):
        return pix.get((x, y)) != kind

    for (x, y), kind in pix.items():
        dx, dy = x - cx, y - cy
        lit = (-dx - dy) / (math.hypot(dx, dy) * math.sqrt(2) + 1e-9)
        if kind == "ring":
            outer = any(pix.get((x + a, y + b)) is None for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            inner = any(pix.get((x + a, y + b)) == "glass" for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            if outer:
                c = GOLD[1] if lit > 0.1 else GOLD[0]
            elif inner:
                c = GOLD[2] if lit > 0 else GOLD[1]
            else:
                c = GOLD[5] if lit > 0.55 else GOLD[4] if lit > 0.1 else GOLD[3] if lit > -0.5 else GOLD[2]
        else:
            c = glass[3] if lit > 0.55 else glass[2] if lit > 0.0 else glass[1] if lit > -0.5 else glass[0]
        im.putpixel((x, y), c + (255,))
    # a glint on the glass
    im.putpixel((7, 4), (255, 255, 255, 255))
    im.putpixel((8, 4), glass[3] + (255,))
    im.putpixel((7, 5), glass[3] + (255,))
    # the handle: a three-pixel band running down-left from the ring, lit on its left
    for i, (x, y) in enumerate(((5, 11), (4, 12), (3, 13), (2, 14))):
        im.putpixel((x - 1, y), STICK[3] + (255,))
        im.putpixel((x, y), STICK[2] + (255,))
        im.putpixel((x + 1, y), STICK[1] + (255,))
    im.putpixel((1, 15), STICK[0] + (255,))
    im.putpixel((2, 15), STICK[0] + (255,))
    im.putpixel((3, 14), STICK[0] + (255,))
    return im


# --- the codex: a closed violet book with a gold clasp and a gem ---
def codex():
    cover = [hexc(c) for c in ("2a1747", "47287a", "6b3fb0", "8a5fd0", "a98af0")]
    pal = {"0": cover[0], "1": cover[1], "2": cover[2], "3": cover[3], "4": cover[4],
           "g": GOLD[3], "h": GOLD[5], "d": GOLD[1], "p": hexc("f0e6c8"), "q": hexc("d2c49a"), "r": hexc("a89868"),
           "e": hexc("d2c4ff"), "f": hexc("7e63d6")}
    rows = [
        "................",
        "...00000000.....",
        "..0444444441....",
        "..0433333321p...",
        "..0432222221pq..",
        "..0432gggg21pq..",
        "..043g2hh2g1pq..",
        "..043g2ef2g1pq..",
        "..043g2fe2g1pq..",
        "..0432gggg21pq..",
        "..0432222221pq..",
        "..0432222221pq..",
        "..0411111111pq..",
        "...0rqqqqqqqq...",
        "....000000000...",
        "................",
    ]
    return paint(rows, pal)


# --- the phials: a small glass vial, empty or holding a tintable liquid ---
VIAL = {  # row -> (min x, max x) of the outline
    6: (6, 9), 7: (5, 10), 8: (4, 11), 9: (4, 11), 10: (4, 11), 11: (4, 11), 12: (5, 10), 13: (6, 9),
}


def vial_glass():
    im = Image.new("RGBA", (16, 16))
    # cork
    for x, c in ((6, CORK_L), (7, CORK_L), (8, CORK_D), (9, CORK_D)):
        im.putpixel((x, 2), c + (255,))
    for x, c in ((6, CORK_L), (7, CORK_D), (8, CORK_D), (9, CORK_D)):
        im.putpixel((x, 3), c + (255,))
    # lip and neck
    for x, c in ((6, GLASS_L), (7, GLASS_M), (8, GLASS_M), (9, GLASS_D)):
        im.putpixel((x, 4), c + (255,))
    im.putpixel((7, 5), GLASS_L + (255,))
    im.putpixel((8, 5), GLASS_D + (255,))
    # body outline: light on the top-left, dark on the bottom-right
    for y, (x0, x1) in VIAL.items():
        for x in range(x0, x1 + 1):
            edge_l, edge_r = x == x0, x == x1
            edge_t, edge_b = y == 6, y == 13
            if not (edge_l or edge_r or edge_t or edge_b):
                continue
            if edge_t or (edge_l and y < 11):
                c = GLASS_L if edge_l or x < 8 else GLASS_M
            elif edge_b or edge_r:
                c = GLASS_X if x >= 8 else GLASS_D
            else:
                c = GLASS_M
            im.putpixel((x, y), c + (255,))
    # a streak of light inside
    for y in (8, 9, 10):
        im.putpixel((5, y), GLASS_L + (255,))
    return im


def vial_liquid():
    """Grey, so the tint source colours it; the top row is the lit surface."""
    im = Image.new("RGBA", (16, 16))
    shades = {"top": hexc("f2f2f2"), "mid": hexc("d2d2d2"), "dark": hexc("a6a6a6"), "low": hexc("8c8c8c")}
    for y in range(8, 13):
        x0, x1 = VIAL[y]
        for x in range(x0 + 1, x1):
            if y == 8:
                c = shades["top"]
            elif y == 12 or x >= x1 - 1:
                c = shades["low"] if y == 12 else shades["dark"]
            else:
                c = shades["mid"] if x < 8 else shades["dark"]
            im.putpixel((x, y), c + (255,))
    # the liquid fills the neck's foot too, so it reads as full
    im.putpixel((7, 7), shades["mid"] + (255,))
    im.putpixel((8, 7), shades["mid"] + (255,))
    for x in range(VIAL[7][0] + 1, VIAL[7][1]):
        im.putpixel((x, 7), shades["top"] + (255,))
    return im


# --- the essence symbols ---
class Glyph:
    def __init__(self):
        self.px = set()

    def dot(self, x, y):
        x, y = int(round(x)), int(round(y))
        if 0 <= x < 16 and 0 <= y < 16:
            self.px.add((x, y))

    def line(self, x0, y0, x1, y1):
        n = int(max(abs(x1 - x0), abs(y1 - y0)) * 2) + 1
        for i in range(n + 1):
            t = i / n
            self.dot(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t)

    def poly(self, pts, closed=True):
        for a, b in zip(pts, pts[1:] + (pts[:1] if closed else [])):
            self.line(a[0], a[1], b[0], b[1])

    def arc(self, cx, cy, r, a0, a1):
        n = int(abs(a1 - a0) / 90 * 3.1416 * r * 1.2) + 4
        for i in range(n + 1):
            a = math.radians(a0 + (a1 - a0) * i / n)
            self.dot(cx + r * math.cos(a), cy + r * math.sin(a))

    def circle(self, cx, cy, r):
        self.arc(cx, cy, r, 0, 360)

    def disc(self, cx, cy, r):
        for y in range(16):
            for x in range(16):
                if math.hypot(x - cx, y - cy) <= r:
                    self.px.add((x, y))

    def erase_disc(self, cx, cy, r):
        self.px = {(x, y) for x, y in self.px if math.hypot(x - cx, y - cy) > r}

    def tri_up(self, cx, top, bottom, half):
        self.poly([(cx, top), (cx + half, bottom), (cx - half, bottom)])

    def tri_down(self, cx, top, bottom, half):
        self.poly([(cx - half, top), (cx + half, top), (cx, bottom)])


C = 7.5


def glyph(name):
    g = Glyph()
    if name == "fire":
        g.tri_up(C, 2, 12.5, 5.5)
    elif name == "water":
        g.tri_down(C, 3, 13.5, 5.5)
    elif name == "earth":
        g.tri_down(C, 3, 13.5, 5.5)
        g.line(4, 6.5, 11, 6.5)
    elif name == "air":
        g.tri_up(C, 2, 12.5, 5.5)
        g.line(4, 9, 11, 9)
    elif name == "salt":
        g.circle(C, C, 5.5)
        g.line(2, C, 13, C)
    elif name == "sulphur":
        g.tri_up(C, 1.5, 9, 4)
        g.line(C, 9, C, 14.5)
        g.line(5, 12, 10, 12)
    elif name == "mercury":
        g.circle(C, 8, 3)
        g.arc(C, 3.5, 2.5, 180, 360)
        g.line(C, 11, C, 15)
        g.line(5, 13, 10, 13)
    elif name == "life":
        g.circle(C, 4.5, 2.7)
        g.line(C, 7, C, 14.5)
        g.line(3, 8.5, 12, 8.5)
    elif name == "light":
        g.disc(C, C, 2.2)
        for k in range(8):
            a = math.radians(k * 45)
            g.line(C + 4.2 * math.cos(a), C + 4.2 * math.sin(a), C + 6.6 * math.cos(a), C + 6.6 * math.sin(a))
    elif name == "metal":
        g.poly([(C, 1.5), (13.5, C), (C, 13.5), (1.5, C)])
        g.disc(C, C, 1.4)
    elif name == "frost":
        for k in range(3):
            a = math.radians(k * 60 + 90)
            g.line(C - 6.4 * math.cos(a), C - 6.4 * math.sin(a), C + 6.4 * math.cos(a), C + 6.4 * math.sin(a))
        for k in range(6):
            a = math.radians(k * 60 + 90)
            px, py = C + 4 * math.cos(a), C + 4 * math.sin(a)
            g.dot(px + math.cos(a + 1.9), py + math.sin(a + 1.9))
            g.dot(px + math.cos(a - 1.9), py + math.sin(a - 1.9))
    elif name == "motion":
        g.poly([(2.5, 3), (7, 7.5), (2.5, 12)], closed=False)
        g.poly([(8.5, 3), (13, 7.5), (8.5, 12)], closed=False)
    elif name == "spark":
        g.poly([(9.5, 1), (4.5, 8), (9.5, 8), (5.5, 14.5)], closed=False)
        g.dot(10, 9)
    elif name == "rot":
        g.circle(C, 5, 3)
        for x, y1 in ((4, 12.5), (7.5, 14.5), (11, 11.5)):
            g.line(x, 9.5, x, y1)
    elif name == "venom":
        g.line(C, 1.5, 3.2, 8.5)
        g.line(C, 1.5, 11.8, 8.5)
        g.arc(C, 9.2, 4.3, 0, 180)
        g.dot(6, 10)
        g.dot(6, 11)
    elif name == "crystal":
        g.poly([(C, 1.5), (12.5, 6), (C, 14.5), (2.5, 6)])
        g.line(2.5, 6, 12.5, 6)
        g.line(C, 1.5, 5.5, 6)
        g.line(C, 1.5, 9.5, 6)
    elif name == "spirit":
        g.circle(C, C, 6.2)
        n = 40
        for i in range(n + 1):
            t = i / n * 2 * math.pi
            g.dot(C + 2.8 * math.sin(t), 3 + 9 * i / n)
    elif name == "plant":
        g.line(C, 14.5, C, 4)
        g.line(C, 11, 3, 6.5)
        g.line(C, 8, 12, 4)
        g.line(C, 4, 6, 1.5)
        g.line(C, 4, 9, 1.5)
    elif name == "beast":
        g.disc(C, 10.3, 3.1)
        for x, y in ((3.2, 6.8), (6.2, 3.6), (9.2, 3.6), (12.2, 6.8)):
            g.disc(x, y, 1.5)
    elif name == "death":
        g.line(C, 1, C, 14.5)
        g.line(3.5, 5, 11.5, 5)
        g.dot(C, 15)
    elif name == "mind":
        n = 24
        for i in range(n + 1):
            x = 1.5 + 12 * i / n
            h = 4.6 * math.sin(math.pi * (x - 1.5) / 12)
            g.dot(x, C - h)
            g.dot(x, C + h)
        g.disc(C, C, 2.1)
    elif name == "shadow":
        for y in range(16):
            for x in range(16):
                if math.hypot(x - 7, y - 7.5) <= 6.2 and math.hypot(x - 10, y - 6) > 5.2:
                    g.px.add((x, y))
    elif name == "arcana":
        pts = [(C + 6.8 * math.cos(math.radians(-90 + k * 72)), C + 0.5 + 6.8 * math.sin(math.radians(-90 + k * 72))) for k in range(5)]
        g.poly([pts[0], pts[2], pts[4], pts[1], pts[3]])
    elif name == "wealth":
        g.circle(C, C, 6)
        g.circle(C, C, 3.6)
        g.dot(C, C)
    elif name == "craft":
        g.circle(C, C, 3.3)
        for k in range(8):
            a = math.radians(k * 45)
            x, y = C + 5.4 * math.cos(a), C + 5.4 * math.sin(a)
            g.dot(x, y)
            g.dot(x + (0.7 if math.cos(a) > 0.3 else -0.7 if math.cos(a) < -0.3 else 0),
                  y + (0.7 if math.sin(a) > 0.3 else -0.7 if math.sin(a) < -0.3 else 0))
    elif name == "war":
        g.line(2, 2, 13, 13)
        g.line(13, 2, 2, 13)
        g.line(9.5, 12.5, 12.5, 9.5)
        g.line(5.5, 12.5, 2.5, 9.5)
    elif name == "undeath":
        g.disc(C, 6, 5.2)
        g.px |= {(x, y) for x in range(5, 11) for y in range(10, 14)}
        g.erase_disc(5.4, 6.4, 1.5)
        g.erase_disc(9.6, 6.4, 1.5)
        g.px.discard((7, 9))
        g.px.discard((8, 9))
        g.px.discard((7, 10))
        g.px.discard((8, 10))
        for y in (12, 13):
            g.px.discard((7, y))
            g.px.discard((9, y))
    elif name == "flux":
        n = 120
        for i in range(n + 1):
            t = i / n * 12.0
            r = 0.55 * t
            g.dot(C + r * math.cos(t), C + r * math.sin(t))
    else:
        raise KeyError(name)
    return g.px


def symbol(name, colour):
    base = hx(colour)
    if sum(base) / 3 < 100:
        base = mix(base, (255, 255, 255), 0.45)
    light = mix(base, (255, 255, 255), 0.45)
    shade = mix(base, (0, 0, 0), 0.62)
    px = glyph(name)
    im = Image.new("RGBA", (16, 16))
    for x, y in px:
        if (x + 1, y + 1) not in px and 0 <= x + 1 < 16 and 0 <= y + 1 < 16:
            im.putpixel((x + 1, y + 1), shade + (255,))
    for x, y in px:
        lit = (x - 1, y - 1) not in px and ((x - 1, y) not in px or (x, y - 1) not in px)
        im.putpixel((x, y), (light if lit else base) + (255,))
    return im


def write_json(path, doc):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(doc, f, indent=2)
        f.write("\n")


def flat_item(name, texture=None):
    write_json(ASSETS + "items/%s.json" % name, {"model": {"type": "minecraft:model", "model": "jaymospowers:item/%s" % name}})
    write_json(ASSETS + "models/item/%s.json" % name,
               {"parent": "minecraft:item/generated", "textures": {"layer0": "jaymospowers:item/%s" % (texture or name)}})


def main():
    save(lens(), ASSETS + "textures/item/scrying_lens.png")
    save(codex(), ASSETS + "textures/item/apothecary_codex.png")
    save(vial_glass(), ASSETS + "textures/item/phial.png")
    save(vial_glass(), ASSETS + "textures/item/essence_phial.png")
    save(vial_liquid(), ASSETS + "textures/item/essence_phial_liquid.png")
    for name, colour in ESSENCES.items():
        save(symbol(name, colour), ASSETS + "textures/gui/alchemy/essence/%s.png" % name)

    flat_item("scrying_lens")
    flat_item("apothecary_codex")
    flat_item("phial")
    # the essence phial: liquid under glass, the liquid tinted by what is in it (a custom tint source)
    write_json(ASSETS + "models/item/essence_phial.json", {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": "jaymospowers:item/essence_phial_liquid", "layer1": "jaymospowers:item/essence_phial"},
    })
    write_json(ASSETS + "items/essence_phial.json", {"model": {
        "type": "minecraft:model", "model": "jaymospowers:item/essence_phial",
        "tints": [{"type": "jaymospowers:essence", "default": -1}],
    }})

    write_json(DATA + "recipe/scrying_lens.json", {
        "type": "minecraft:crafting_shaped", "category": "equipment",
        "key": {"A": "minecraft:amethyst_shard", "G": "minecraft:gold_ingot", "P": "minecraft:glass_pane"},
        "pattern": [" A ", "GPG", " G "],
        "result": {"id": "jaymospowers:scrying_lens"},
    })
    write_json(DATA + "recipe/apothecary_codex.json", {
        "type": "minecraft:crafting_shapeless", "category": "equipment",
        "ingredients": ["minecraft:book", "minecraft:amethyst_shard", "minecraft:ink_sac"],
        "result": {"id": "jaymospowers:apothecary_codex"},
    })
    write_json(DATA + "recipe/phial.json", {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "key": {"G": "minecraft:glass"},
        "pattern": ["G G", " G "],
        "result": {"id": "jaymospowers:phial", "count": 4},
    })


main()
