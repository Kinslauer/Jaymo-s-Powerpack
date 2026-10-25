"""Writes the Aether gear's art and data (Powers 0.8.x, Aether II phase 4 track G): the item sprites (tools, hammers, shears,
armour, gliders, enchanted fruit, arkenium plate), the armour layer textures, the altar's textures and model, and the item
definitions, equipment assets, recipes, loot table, tags and lang that go with them.

    python -I tools/gen_aether_gear.py

Needs only Pillow. Nothing is copied from The Aether II or anyone else: the notes gave names and numbers, every shape and
pixel here is ours, in the flat shade-ramp style of vanilla's items. Every texture is seeded from its own name, so a rerun
changes nothing. The ids must match com.jaymospowers.aether.gear.AetherGear (tiers and sets are listed here too).
"""
import json
import math
import os
import random
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_aether as ga  # ramps, tag merging and path helpers (only imported: it writes nothing until main())

ASSETS, DATA, MCDATA, NS, SUB = ga.ASSETS, ga.DATA, ga.MCDATA, ga.NS, ga.SUB
write, merge_tag, jp, mc, hx, ramp = ga.write, ga.merge_tag, ga.jp, ga.mc, ga.hx, ga.ramp
ITEM_DIR = os.path.join(ASSETS, "textures", "item", SUB)
BLOCK_DIR = os.path.join(ASSETS, "textures", "block", SUB)
EQUIP_DIR = os.path.join(ASSETS, "textures", "entity", "equipment")

# ---------------------------------------------------------------- what exists

# tier id, name, head ramp (outline, shadow, mid, light, glint), the item that makes and repairs it
TIERS = [
    ("skyroot", "Skyroot", ramp("46503d", "6e7d5f", "8a9a79", "aebca0", "d0dcc4"), "skyroot_planks"),
    ("holystone", "Holystone", ramp("6c7585", "9da5b3", "c4cad4", "e3e7ee", "ffffff"), "holystone"),
    ("zanite", "Zanite", ramp("2c1252", "5a2f9a", "8a52d8", "b98af0", "ecdcff"), "zanite_gemstone"),
    ("arkenium", "Arkenium", ramp("16243c", "34527a", "5f8ec0", "a8d0f2", "e6f4ff"), "arkenium_plate"),
    ("gravitite", "Gravitite", ramp("55103b", "962374", "d443a8", "ff7fd4", "ffd0f0"), "enchanted_gravitite"),
]
HANDLE = ramp("3d2f1f", "5c4830", "7d6444", "9d845c")  # outline, shadow, mid, light: a skyroot stick
KINDS = [("shortsword", "Shortsword"), ("hammer", "Hammer"), ("pickaxe", "Pickaxe"), ("axe", "Axe"), ("shovel", "Shovel"), ("hoe", "Hoe")]
PIECES = [("helmet", "Helmet"), ("chestplate", "Chestplate"), ("leggings", "Leggings"), ("boots", "Boots")]
# set id, name, ramp (outline, shadow, mid, light, glint), the item that repairs it, trim colour
SETS = [
    ("beast_pelt", "Beast Pelt", ramp("3b2a1a", "6d4c30", "9a7048", "c29a6a", "e3c898"), "beast_pelt", hx("e8dcc0")),
    ("burrukai_plate", "Burrukai Plate", ramp("3a2a22", "6b5242", "937964", "bda48a", "e0d0b8"), "burrukai_plate", hx("c9a24a")),
    ("zanite", "Zanite", ramp("2c1252", "5a2f9a", "8a52d8", "b98af0", "ecdcff"), "zanite_gemstone", hx("e9d8ff")),
    ("arkenium", "Arkenium", ramp("16243c", "34527a", "5f8ec0", "a8d0f2", "e6f4ff"), "arkenium_plate", hx("e0f0ff")),
    ("gravitite", "Gravitite", ramp("55103b", "962374", "d443a8", "ff7fd4", "ffd0f0"), "enchanted_gravitite", hx("ffe0f6")),
    ("neptune", "Neptune", ramp("0e3550", "1c6a94", "2fa0c8", "7fd8ee", "d8f8ff"), "zanite_gemstone", hx("f2d36b")),
]
GLIDERS = [("cold", "Cold", ramp("5f7c92", "9db8cc", "d4e6f2", "f4fbff", "ffffff")), ("golden", "Golden", ramp("9a6a12", "d9a62a", "f5cf55", "ffe98f", "fffbd0")),
           ("blue", "Blue", ramp("2a62a0", "4f92d4", "7cc0f2", "b4e2ff", "f0faff")), ("purple", "Purple", ramp("5a2f8a", "8a58c0", "b488e6", "d8b8ff", "f6eaff"))]
FOODS = [("enchanted_blue_berry", "Enchanted Blueberry", "blue_berry"), ("enchanted_orange", "Enchanted Orange", None),
         ("enchanted_wyndberry", "Enchanted Wyndberry", None)]


def seeded(name):
    return random.Random("aether-gear:" + name)


# ---------------------------------------------------------------- painting sprites from silhouettes

def new():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def cells(rows):
    """A set of (x, y) from rows of text ('#' marks a cell; rows are 16 wide, the first row is y = 0)."""
    return {(x, y) for y, row in enumerate(rows) for x, c in enumerate(row) if c == "#"}


def diag(c, xs):
    """Cells on the anti-diagonal x + y = c."""
    return {(x, c - x) for x in xs}


class Sprite:
    """A 16x16 sprite built layer by layer: fills first, one outline round everything at the end (vanilla style)."""

    def __init__(self):
        self.im = new()
        self.owner = {}  # cell -> outline colour of the layer that drew it

    def fill(self, mask, colors, rng, light=(-1.0, -1.0), bands=None):
        """Shade a silhouette lit from the upper left. colors: outline, shadow, mid, light, glint."""
        px = self.im.load()
        mask = {(x, y) for x, y in mask if 0 <= x < 16 and 0 <= y < 16}
        xs = [p[0] for p in mask]
        ys = [p[1] for p in mask]
        cx, cy = (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2
        for x, y in mask:
            d = -((x - cx) * light[0] + (y - cy) * light[1])
            span = max(max(xs) - min(xs), max(ys) - min(ys), 1)
            t = d / span  # about -1..1: negative is the lit side
            lvl = 3 if t < -0.22 else 2 if t < 0.12 else 1
            if rng.random() < 0.10:
                lvl = max(1, min(3, lvl + rng.choice([-1, 1])))
            px[x, y] = colors[lvl] + (255,)
            self.owner[(x, y)] = colors[0]

    def paint(self, cell_colors, outline):
        px = self.im.load()
        for (x, y), c in cell_colors.items():
            if 0 <= x < 16 and 0 <= y < 16:
                px[x, y] = c + (255,)
                self.owner[(x, y)] = outline

    def done(self):
        px = self.im.load()
        for (x, y), color in list(self.owner.items()):
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < 16 and 0 <= ny < 16 and (nx, ny) not in self.owner and px[nx, ny][3] == 0:
                    px[nx, ny] = color + (255,)
        return self.im


def stick(sp, c, x0, x1, rng):
    """A handle along x + y = c: mid and light pixels, so it reads like a stick."""
    m = {}
    for i, x in enumerate(range(x0, x1 + 1)):
        m[(x, c - x)] = HANDLE[3] if i % 3 == 1 else HANDLE[2]
    sp.paint(m, HANDLE[0])


# --- tools ---

def sprite_shortsword(head, rng):
    sp = Sprite()
    stick(sp, 16, 2, 4, rng)
    sp.paint({(1, 14): HANDLE[1], (2, 14): HANDLE[2]}, HANDLE[0])
    sp.paint({(x, x + 6): (HANDLE[3] if x in (5,) else HANDLE[2]) for x in range(3, 8)}, HANDLE[0])
    blade = {}
    for x in range(5, 15):
        blade[(x, 15 - x)] = head[3]
    for x in range(5, 14):
        blade[(x, 14 - x)] = head[2]
    for x in range(6, 13):
        pass
    sp.paint(blade, head[0])
    # a ridge of shadow down the lower edge and a glint at the tip
    sp.paint({(x, 13 - x): head[1] for x in range(5, 12)}, head[0])
    sp.paint({(13, 2): head[4], (11, 4): head[4]}, head[0])
    return sp.done()


def sprite_hammer(head, rng):
    sp = Sprite()
    stick(sp, 15, 2, 10, rng)
    block = cells(["................",
                   "..........####..",
                   ".........######.",
                   ".........######.",
                   "........######..",
                   "........#####...",
                   "................"])
    sp.fill(block, head, rng)
    sp.paint({(10, 2): head[4], (11, 2): head[3], (9, 3): head[3]}, head[0])
    sp.paint({(x, y): head[1] for x, y in ((8, 5), (9, 5), (10, 5), (13, 3), (13, 4), (12, 4))}, head[0])
    return sp.done()


def sprite_pickaxe(head, rng):
    sp = Sprite()
    stick(sp, 15, 2, 11, rng)
    cx, cy, r = 4.0, 11.0, 7.4
    arc = set()
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            dist = math.hypot(dx, dy)
            if abs(dist - r) <= 1.2 and (dx * 0.7071 - dy * 0.7071) / max(dist, 1e-6) > 0.50:
                arc.add((x, y))
    sp.fill(arc, head, rng)
    return sp.done()


def sprite_axe(head, rng):
    sp = Sprite()
    stick(sp, 15, 2, 11, rng)
    blade = cells(["................",
                   "........####....",
                   "......######....",
                   ".....#######....",
                   ".....#######....",
                   "......######....",
                   ".......####.....",
                   "................"])
    sp.fill(blade, head, rng)
    sp.paint({(5, 3): head[3], (5, 4): head[3], (6, 2): head[3]}, head[0])
    return sp.done()


def sprite_shovel(head, rng):
    sp = Sprite()
    stick(sp, 15, 2, 10, rng)
    spade = {(x, y) for x in range(16) for y in range(16) if ((x + 0.5 - 12.0) / 2.6) ** 2 + ((y + 0.5 - 3.6) / 3.2) ** 2 <= 1.0}
    sp.fill(spade, head, rng)
    sp.paint({(11, 11 - 11 + 3): head[4]}, head[0])
    return sp.done()


def sprite_hoe(head, rng):
    sp = Sprite()
    stick(sp, 15, 2, 11, rng)
    blade = cells(["................",
                   "...........###..",
                   "........#######.",
                   "........###..##.",
                   "........##......",
                   "........##......"])
    sp.fill(blade, head, rng)
    return sp.done()


def sprite_shears(head, rng):
    """Zanite shears: two blades crossing at a pivot, ringed handles below."""
    grid = ["................",
            "..bb........bb..",
            "..bmb......bmb..",
            "...bmb....bmb...",
            "....bmb..bmb....",
            ".....bmbbmb.....",
            "......mbbm......",
            ".......pp.......",
            "......hggh......",
            ".....hg..gh.....",
            "....hg....gh....",
            "...hhhh..hhhh...",
            "...h..h..h..h...",
            "...h..h..h..h...",
            "...hhhh..hhhh..."]
    colors = {"b": head[3], "m": head[2], "p": head[4], "h": head[1], "g": head[2]}
    sp = Sprite()
    sp.paint({(x, y): colors[c] for y, row in enumerate(grid) for x, c in enumerate(row) if c in colors}, head[0])
    return sp.done()


def sprite_plate(head, rng):
    """An arkenium plate: a steel slab with a bevel and four rivets."""
    sp = Sprite()
    sp.paint({(x, y): head[1] for x in range(4, 14) for y in range(5, 13)}, head[0])
    slab = {(x, y) for x in range(3, 13) for y in range(4, 12)} - {(3, 4), (12, 4), (3, 11), (12, 11)}
    sp.fill(slab, head, rng)
    px = sp.im.load()
    for x, y in ((4, 5), (11, 5), (4, 10), (11, 10)):
        px[x, y] = head[4] + (255,)
    for x in range(5, 11):
        px[x, 4] = head[3] + (255,)
    for y in range(6, 10):
        px[3, y] = head[3] + (255,)
    return sp.done()


def sprite_glider(ramp5, rng):
    """A glider: a puffy cloud canopy on a wooden frame."""
    sp = Sprite()
    canopy = cells(["................",
                    "................",
                    "......####......",
                    "....########....",
                    "..############..",
                    ".##############.",
                    "################",
                    "################",
                    ".####.####.####.",
                    "..##...##...##.."])
    sp.fill(canopy, ramp5, rng, light=(-0.6, -1.0))
    px = sp.im.load()
    for x, y in ((6, 3), (7, 3), (4, 4), (3, 5), (2, 6), (9, 4)):
        px[x, y] = ramp5[4] + (255,)
    frame = {(8, y): HANDLE[2] for y in range(11, 15)}
    frame.update({(x, 13): HANDLE[2] for x in range(6, 11)})
    frame[(8, 12)] = HANDLE[3]
    sp.paint(frame, HANDLE[0])
    return sp.done()


# --- armour pieces ---

PIECE_SHAPES = {
    "helmet": ["................",
               "................",
               "....########....",
               "...##########...",
               "...##########...",
               "...##########...",
               "...##########...",
               "...###....###...",
               "...###....###...",
               "...##......##..."],
    "chestplate": ["................",
                   "..###......###..",
                   ".####......####.",
                   ".#####....#####.",
                   ".##############.",
                   "..############..",
                   "..############..",
                   "..############..",
                   "..############..",
                   "..############..",
                   "...##########...",
                   "...##########..."],
    "leggings": ["................",
                 "................",
                 "...##########...",
                 "...##########...",
                 "...##########...",
                 "...##########...",
                 "...####..####...",
                 "...####..####...",
                 "...####..####...",
                 "...####..####...",
                 "...####..####...",
                 "...####..####...",
                 "...####..####..."],
    "boots": ["................",
              "................",
              "................",
              "................",
              "................",
              "................",
              "..####....####..",
              "..####....####..",
              "..####....####..",
              "..####....####..",
              ".#####....#####.",
              ".######..######.",
              ".######..######."],
}
# trim: rows (y) painted in the set's trim colour, per piece
PIECE_TRIM = {"helmet": [(x, 4) for x in range(4, 12)], "chestplate": [(x, 4) for x in range(3, 13) if x not in (7, 8)] + [(x, 9) for x in range(3, 13)],
              "leggings": [(x, 2) for x in range(4, 12)], "boots": [(x, 6) for x in (2, 3, 4, 5, 10, 11, 12, 13)]}


def sprite_armor(piece, colors, trim, rng):
    sp = Sprite()
    sp.fill(cells(PIECE_SHAPES[piece]), colors, rng)
    px = sp.im.load()
    for x, y in PIECE_TRIM[piece]:
        if px[x, y][3]:
            px[x, y] = trim + (255,)
    # a glint on the upper left shoulder or brow
    gx, gy = {"helmet": (4, 3), "chestplate": (3, 3), "leggings": (4, 3), "boots": (3, 8)}[piece]
    if px[gx, gy][3]:
        px[gx, gy] = colors[4] + (255,)
    return sp.done()


# --- enchanted fruit: the fruit it comes from, glittering ---

def sprite_enchanted(base_name, rng):
    im = Image.open(os.path.join(ITEM_DIR, base_name + ".png")).convert("RGBA")
    px = im.load()
    # a warm golden lift on the lit pixels, then four sparkles round it
    for y in range(16):
        for x in range(16):
            r, g, b, a = px[x, y]
            if a:
                px[x, y] = (min(255, r + 28), min(255, g + 22), min(255, b + 0), a)
    gold, white = hx("ffd84a"), hx("fffbd0")
    for (x, y) in ((2, 2), (13, 3), (1, 12), (14, 13)):
        for dx, dy, c in ((0, 0, white), (1, 0, gold), (-1, 0, gold), (0, 1, gold), (0, -1, gold)):
            nx, ny = x + dx, y + dy
            if 0 <= nx < 16 and 0 <= ny < 16 and px[nx, ny][3] == 0:
                px[nx, ny] = c + (255,)
    return im


TOOL_SPRITES = {"shortsword": sprite_shortsword, "hammer": sprite_hammer, "pickaxe": sprite_pickaxe, "axe": sprite_axe,
                "shovel": sprite_shovel, "hoe": sprite_hoe}




def contact(images, cols, path, scale=8):
    rows = (len(images) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * 16 * scale, rows * 16 * scale), (60, 60, 70, 255))
    for i, im in enumerate(images):
        big = im.resize((16 * scale, 16 * scale), Image.NEAREST)
        sheet.alpha_composite(big, ((i % cols) * 16 * scale, (i // cols) * 16 * scale))
    sheet.save(path)


# ---------------------------------------------------------------- armour layers (64x32, the humanoid model's own layout)

# each body part's box on the sheet: u, v, width, height, depth (the old 64x32 layout every armour layer uses)
BOXES = {"head": (0, 0, 8, 8, 8), "body": (16, 16, 8, 12, 4), "arm": (40, 16, 4, 12, 4), "leg": (0, 16, 4, 12, 4)}
# which rows (y within the part) of each part a layer paints: layer 1 holds helmet, chestplate (body and arms) and boots (the
# foot of the leg); layer 2 holds the leggings (the hips of the body, and the whole leg)
LAYER_ROWS = {1: {"head": (0, 8), "body": (0, 12), "arm": (0, 12), "leg": (8, 12)}, 2: {"body": (6, 12), "leg": (0, 12)}}


def faces(part):
    """Every face of a part's box as (x, y, w, h, kind) on the sheet."""
    u, v, w, h, d = BOXES[part]
    return [(u + d, v, w, d, "top"), (u + d + w, v, w, d, "bottom"), (u, v + d, d, h, "side"), (u + d, v + d, w, h, "front"),
            (u + d + w, v + d, d, h, "side"), (u + d + w + d, v + d, w, h, "back")]


def armor_layer(set_id, colors, trim, layer):
    rng = seeded("layer%d-%s" % (layer, set_id))
    im = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    px = im.load()
    style = {"beast_pelt": "fur", "burrukai_plate": "plate"}.get(set_id, "metal")
    for part, (lo, hi) in LAYER_ROWS[layer].items():
        u, v, w, h, d = BOXES[part]
        for fx, fy, fw, fh, kind in faces(part):
            for yy in range(fh):
                # the rows of a side face run down the part: only those inside the painted range
                row = yy if kind not in ("top", "bottom") else None
                if row is not None and not (lo <= row < hi):
                    continue
                for xx in range(fw):
                    x, y = fx + xx, fy + yy
                    edge = xx in (0, fw - 1) or (kind in ("top", "bottom") and yy in (0, fh - 1))
                    base = rng.choices(colors[1:4], [2, 5, 3])[0]
                    if style == "fur":
                        if rng.random() < 0.18:
                            base = colors[3] if rng.random() < 0.6 else colors[4]
                        if (xx + yy * 2) % 7 == 0:
                            base = colors[1]
                    elif style == "plate":
                        if (xx % 4 == 0 or (row if row is not None else yy) % 4 == 0):
                            base = colors[1]
                        elif xx % 4 == 1 and (row if row is not None else yy) % 4 == 1:
                            base = colors[3]
                    else:
                        if (xx + yy) % 6 == 0:
                            base = colors[3]
                        if rng.random() < 0.06:
                            base = colors[4]
                    if edge:
                        base = colors[0] if rng.random() < 0.55 else colors[1]
                    px[x, y] = base + (255,)
        # trim bands: round the helmet's brow, the chest's collar and belt, the leggings' belt and the boots' cuff
        bands = {(1, "head"): [3], (1, "body"): [0, 9], (1, "arm"): [10], (1, "leg"): [8], (2, "body"): [6, 7], (2, "leg"): [0]}.get((layer, part), [])
        for fx, fy, fw, fh, kind in faces(part):
            if kind in ("top", "bottom"):
                continue
            for b in bands:
                if lo <= b < hi and b < fh:
                    for xx in range(fw):
                        px[fx + xx, fy + b] = trim + (255,)
    return im


# ---------------------------------------------------------------- the altar's textures

HOLY = ga.ramp("c9ced8", "dde1e8", "eceff3", "f6f8fa")
ZAN = ga.ZANITE
AMB = ga.AMBROSIUM


def holy_noise(name):
    rng = seeded(name)
    im = ga.speckle(rng, [HOLY[0], HOLY[1], HOLY[2], HOLY[3]], [3, 5, 3, 1])
    return im, rng


def altar_top():
    im, rng = holy_noise("altar_top")
    px = im.load()
    for i in range(16):
        for c in (0, 15):
            px[i, c] = ga.HOLY_DARK[0] + (255,)
            px[c, i] = ga.HOLY_DARK[0] + (255,)
    # a ring of eight ambrosium studs round a zanite square: where the shards go
    for x, y in ((4, 4), (8, 3), (11, 4), (12, 8), (11, 11), (8, 12), (4, 11), (3, 8)):
        px[x, y] = AMB[2] + (255,)
        px[x + 1, y] = AMB[3] + (255,)
        px[x, y + 1] = AMB[1] + (255,)
    for x in range(7, 10):
        for y in range(7, 10):
            px[x, y] = ZAN[2 if (x, y) != (8, 8) else 3] + (255,)
    return im


def altar_side():
    im, rng = holy_noise("altar_side")
    px = im.load()
    for x in range(16):
        px[x, 0] = HOLY[3] + (255,)
        px[x, 15] = ga.HOLY_DARK[0] + (255,)
        px[x, 4] = ga.HOLY_DARK[0] + (255,)
        for y in (5, 6, 7):
            px[x, y] = ZAN[1 if y == 5 else 2 if y == 6 else 1] + (255,)
        px[x, 8] = ga.HOLY_DARK[0] + (255,)
    for x in (3, 8, 13):
        px[x, 6] = ZAN[3] + (255,)
    return im


def altar_column():
    im, rng = holy_noise("altar_column")
    px = im.load()
    for y in range(16):
        for x in (0, 5, 10, 15):
            px[x, y] = ga.HOLY_DARK[0] + (255,)
    for y in (3, 12):
        for x in range(16):
            px[x, y] = ZAN[1] + (255,)
    return im


def altar_base():
    im, rng = holy_noise("altar_base")
    px = im.load()
    for i in range(16):
        for c in (0, 15):
            px[i, c] = ga.HOLY_DARK[0] + (255,)
            px[c, i] = ga.HOLY_DARK[0] + (255,)
    return im


# ---------------------------------------------------------------- textures and models

def save(path, im):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im.save(path)


def item_tex(name):
    return "%s:item/%s/%s" % (NS, SUB, name)


def all_items():
    """Every item id the gear adds, with its name and the model it uses ('handheld' or 'generated')."""
    out = [("arkenium_plate", "Arkenium Plate", "generated")]
    for tid, name, head, mat in TIERS:
        for kind, kname in KINDS:
            out.append(("%s_%s" % (tid, kind), "%s %s" % (name, kname), "handheld"))
    out.append(("zanite_shears", "Zanite Shears", "generated"))
    for sid, name, colors, mat, trim in SETS:
        for piece, pname in PIECES:
            out.append(("%s_%s" % (sid, piece), "%s %s" % (name, pname), "generated"))
    for gid, name, colors in GLIDERS:
        out.append(("%s_aercloud_glider" % gid, "%s Aercloud Glider" % name, "generated"))
    for fid, name, base in FOODS:
        out.append((fid, name, "generated"))
    return out


def textures():
    for tid, name, head, mat in TIERS:
        for kind, kname in KINDS:
            save(os.path.join(ITEM_DIR, "%s_%s.png" % (tid, kind)), TOOL_SPRITES[kind](head, seeded(tid + kind)))
    zan = TIERS[2][2]
    save(os.path.join(ITEM_DIR, "zanite_shears.png"), sprite_shears(zan, seeded("shears")))
    save(os.path.join(ITEM_DIR, "arkenium_plate.png"), sprite_plate(TIERS[3][2], seeded("plate")))
    for sid, name, colors, mat, trim in SETS:
        for piece, pname in PIECES:
            save(os.path.join(ITEM_DIR, "%s_%s.png" % (sid, piece)), sprite_armor(piece, colors, trim, seeded(sid + piece)))
        for layer, folder in ((1, "humanoid"), (2, "humanoid_leggings")):
            save(os.path.join(EQUIP_DIR, folder, sid + ".png"), armor_layer(sid, colors, trim, layer))
    for gid, name, colors in GLIDERS:
        save(os.path.join(ITEM_DIR, "%s_aercloud_glider.png" % gid), sprite_glider(colors, seeded("glider" + gid)))
    # the fruit the enchanted ones are made from: berries are ours (gen_aether), oranges and wyndberries are the flora track's
    for fid, name, base in FOODS:
        src = fid.replace("enchanted_", "")
        src = "blue_berry" if src == "blue_berry" else src
        save(os.path.join(ITEM_DIR, fid + ".png"), sprite_enchanted(src, seeded(fid)))
    for name, fn in (("altar_top", altar_top), ("altar_side", altar_side), ("altar_column", altar_column), ("altar_base", altar_base)):
        save(os.path.join(BLOCK_DIR, name + ".png"), fn())


def models():
    for iid, name, parent in all_items():
        write(os.path.join(ASSETS, "models", "item", SUB, iid + ".json"),
              {"parent": mc("item/" + parent), "textures": {"layer0": item_tex(iid)}})
        write(os.path.join(ASSETS, "items", iid + ".json"), {"model": {"type": "minecraft:model", "model": "%s:item/%s/%s" % (NS, SUB, iid)}})
    # equipment assets: the layer textures, one pair per set
    for sid, name, colors, mat, trim in SETS:
        write(os.path.join(ASSETS, "equipment", sid + ".json"), {"layers": {"humanoid": [{"texture": jp(sid)}], "humanoid_leggings": [{"texture": jp(sid)}]}})
    # the altar: a base, a column and a broad top
    def box(frm, to, faces_):
        return {"from": frm, "to": to, "faces": {f: {"texture": t, "cullface": f} if cull else {"texture": t} for f, t, cull in faces_}}
    column_faces = [(f, "#column", False) for f in ("north", "south", "east", "west")]
    write(os.path.join(ASSETS, "models", "block", SUB, "altar.json"), {
        "parent": "minecraft:block/block",
        "textures": {"particle": "%s:block/%s/altar_side" % (NS, SUB), "top": "%s:block/%s/altar_top" % (NS, SUB),
                     "side": "%s:block/%s/altar_side" % (NS, SUB), "column": "%s:block/%s/altar_column" % (NS, SUB),
                     "base": "%s:block/%s/altar_base" % (NS, SUB)},
        "elements": [
            {"from": [2, 0, 2], "to": [14, 2, 14], "faces": {f: {"texture": "#base"} for f in ("north", "south", "east", "west", "up", "down")}},
            {"from": [5, 2, 5], "to": [11, 8, 11], "faces": {f: {"texture": "#column"} for f in ("north", "south", "east", "west")}},
            {"from": [0, 8, 0], "to": [16, 16, 16], "faces": {"north": {"texture": "#side", "cullface": "north"}, "south": {"texture": "#side", "cullface": "south"},
                                                              "east": {"texture": "#side", "cullface": "east"}, "west": {"texture": "#side", "cullface": "west"},
                                                              "up": {"texture": "#top", "cullface": "up"}, "down": {"texture": "#side"}}}]})
    write(os.path.join(ASSETS, "blockstates", "altar.json"), {"variants": {"": {"model": "%s:block/%s/altar" % (NS, SUB)}}})
    write(os.path.join(ASSETS, "items", "altar.json"), {"model": {"type": "minecraft:model", "model": "%s:block/%s/altar" % (NS, SUB)}})
    write(os.path.join(DATA, "loot_table", "blocks", "altar.json"), ga.self_table("altar"))


# ---------------------------------------------------------------- recipes

def recipes():
    out = os.path.join(DATA, "recipe", SUB)
    shaped = lambda key, pattern, result, count=1, cat="equipment": {
        "type": "minecraft:crafting_shaped", "category": cat, "key": key, "pattern": pattern,
        "result": {"id": result, "count": count} if count > 1 else {"id": result}}
    r = {}
    for tid, name, head, mat in TIERS:
        m = jp(mat)
        stick_ = mc("stick")
        if tid != "skyroot":  # (0.16.18) Andy: no craftable wooden weapons
            r["%s_shortsword" % tid] = shaped({"#": m, "S": stick_}, ["#", "#", "S"], jp(tid + "_shortsword"))
        r["%s_hammer" % tid] = shaped({"#": m, "S": stick_}, ["###", "#S#", " S "], jp(tid + "_hammer"))
        r["%s_pickaxe" % tid] = shaped({"#": m, "S": stick_}, ["###", " S ", " S "], jp(tid + "_pickaxe"))
        r["%s_axe" % tid] = shaped({"#": m, "S": stick_}, ["##", "#S", " S"], jp(tid + "_axe"))
        r["%s_shovel" % tid] = shaped({"#": m, "S": stick_}, ["#", "S", "S"], jp(tid + "_shovel"))
        r["%s_hoe" % tid] = shaped({"#": m, "S": stick_}, ["##", " S", " S"], jp(tid + "_hoe"))
    r["zanite_shears"] = shaped({"#": jp("zanite_gemstone")}, [" #", "# "], jp("zanite_shears"))
    patterns = {"helmet": ["###", "# #"], "chestplate": ["# #", "###", "###"], "leggings": ["###", "# #", "# #"], "boots": ["# #", "# #"]}
    for sid, name, colors, mat, trim in SETS:
        if sid == "neptune":
            continue  # boss loot only (and the altar mends it)
        for piece, pname in PIECES:
            r["%s_%s" % (sid, piece)] = shaped({"#": jp(mat)}, patterns[piece], jp("%s_%s" % (sid, piece)))
    # gliders: canopy of aerclouds, cloudtwine lines and a frame of sticks
    glider = lambda cloud, extra=None: shaped(dict({"A": jp(cloud), "T": jp("cloudtwine"), "S": mc("stick")}, **(extra or {})),
                                              ["AAA", "T T", "S S"], None) if False else None
    def glider_recipe(gid, cloud, centre):
        rec = shaped({"A": jp(cloud), "T": jp("cloudtwine"), "S": mc("stick"), "Z": centre}, ["AAA", "TZT", "S S"], jp(gid + "_aercloud_glider"), cat="equipment")
        return rec
    r["cold_aercloud_glider"] = glider_recipe("cold", "cold_aercloud", jp("ambrosium_shard"))
    r["golden_aercloud_glider"] = glider_recipe("golden", "golden_aercloud", jp("golden_amber"))
    r["blue_aercloud_glider"] = glider_recipe("blue", "blue_aercloud", jp("zanite_gemstone"))
    r["purple_aercloud_glider"] = glider_recipe("purple", "purple_aercloud", jp("zanite_gemstone"))
    r["altar"] = shaped({"H": jp("holystone"), "Z": jp("zanite_gemstone")}, ["HZH", " H ", "HHH"], jp("altar"), cat="misc")
    # arkenium plates: smelt inert arkenium (the altar does it too, for shards)
    r["arkenium_plate_from_smelting"] = {"type": "minecraft:smelting", "category": "misc", "cookingtime": 200, "experience": 0.7,
                                         "ingredient": jp("inert_arkenium"), "result": {"id": jp("arkenium_plate")}}
    r["arkenium_plate_from_blasting"] = {"type": "minecraft:blasting", "category": "misc", "cookingtime": 100, "experience": 0.7,
                                         "ingredient": jp("inert_arkenium"), "result": {"id": jp("arkenium_plate")}}
    for name, data in r.items():
        write(os.path.join(out, name + ".json"), data)


# ---------------------------------------------------------------- tags

def tags():
    ji = os.path.join(DATA, "tags", "item")
    mi = os.path.join(MCDATA, "tags", "item")
    mb = os.path.join(MCDATA, "tags", "block")
    for tid, name, head, mat in TIERS:
        write(os.path.join(ji, tid + "_tool_materials.json"), {"values": [jp(mat)]})
    for sid, name, colors, mat, trim in SETS:
        write(os.path.join(ji, "repairs_%s_armor.json" % sid), {"values": [jp(mat)]})
    write(os.path.join(ji, "aether_hammers.json"), {"values": [jp("%s_hammer" % t[0]) for t in TIERS]})
    write(os.path.join(ji, "aether_gliders.json"), {"values": [jp("%s_aercloud_glider" % g[0]) for g in GLIDERS]})
    # beast pelt armour: a taegore isn't scared of whoever wears any of it (the animals track reads this tag)
    merge_tag(os.path.join(ji, "beast_pelt_armor.json"), [jp("beast_pelt_" + p[0]) for p in PIECES])
    for tag, kind in (("swords", "shortsword"), ("pickaxes", "pickaxe"), ("axes", "axe"), ("shovels", "shovel"), ("hoes", "hoe")):
        merge_tag(os.path.join(mi, tag + ".json"), [jp("%s_%s" % (t[0], kind)) for t in TIERS])
    for tag, piece in (("head_armor", "helmet"), ("chest_armor", "chestplate"), ("leg_armor", "leggings"), ("foot_armor", "boots")):
        merge_tag(os.path.join(mi, tag + ".json"), [jp("%s_%s" % (s[0], piece)) for s in SETS])
    hammers = [jp("%s_hammer" % t[0]) for t in TIERS]
    merge_tag(os.path.join(mi, "enchantable", "mining.json"), [jp("zanite_shears")])
    merge_tag(os.path.join(mi, "enchantable", "durability.json"), hammers + [jp("zanite_shears")])
    merge_tag(os.path.join(mi, "enchantable", "melee_weapon.json"), hammers)
    merge_tag(os.path.join(mb, "mineable", "pickaxe.json"), [jp("altar")])
    merge_tag(os.path.join(mb, "needs_stone_tool.json"), [jp("altar")])


# ---------------------------------------------------------------- lang

def lang():
    mine = {"block.%s.altar" % NS: "Altar", "container.%s.altar" % NS: "Altar",
            "gui.%s.altar.shards" % NS: "Takes %s ambrosium shards", "gui.%s.altar.idle" % NS: "Put something to mend or enchant on the altar"}
    for iid, name, parent in all_items():
        mine["item.%s.%s" % (NS, iid)] = name
    write(os.path.join(ASSETS, "lang", "parts", "aether_gear.json"), mine)


def main():
    textures()
    models()
    recipes()
    tags()
    lang()


if __name__ == "__main__":
    if "--sheet" in sys.argv:
        out = sys.argv[sys.argv.index("--sheet") + 1]
        imgs = []
        for tid, name, head, _ in TIERS:
            for kind, _n in KINDS:
                imgs.append(TOOL_SPRITES[kind](head, seeded(tid + kind)))
        imgs.append(sprite_shears(TIERS[2][2], seeded("shears")))
        imgs.append(sprite_plate(TIERS[3][2], seeded("plate")))
        for gid, name, colors in GLIDERS:
            imgs.append(sprite_glider(colors, seeded("glider" + gid)))
        for sid, name, colors, mat, trim in SETS:
            for piece, _p in PIECES:
                imgs.append(sprite_armor(piece, colors, trim, seeded(sid + piece)))
        contact(imgs, 6, out)
    else:
        main()
