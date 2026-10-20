"""Writes The Aether's blocks and items (Powers 0.13.36): the 16x16 textures (painted here, vanilla style: flat shade
ramps and light noise, nothing copied from The Aether mod or anyone else), and the blockstates, models, item
definitions, loot tables, recipes, tags and lang that go with them.

    python -I tools/gen_aether.py

Needs only Pillow. Every texture is seeded from its own name, so a rerun changes nothing. The ids must match
com.jaymospowers.aether.AetherBlocks / AetherItems. Existing tag files (data/minecraft/tags/...) are merged into, not
replaced. The recipes for the stews are here too: in 26.3 a flower's suspicious stew effect lives in the recipe.
"""
import json
import os
import random

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "jaymospowers")
DATA = os.path.join(RES, "data", "jaymospowers")
MCDATA = os.path.join(RES, "data", "minecraft")
NS = "jaymospowers"
SUB = "aether"  # folder under textures/block, textures/item, models/block, models/item and recipe


def hx(s):
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))


def ramp(*s):
    return [hx(c) for c in s]


# ---------------------------------------------------------------- painting

def new():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def seeded(name):
    return random.Random("aether:" + name)


def speckle(rng, colors, weights):
    """Every pixel one of the colours, by weight: the flat noise vanilla's stones and soils have."""
    im = new()
    px = im.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = rng.choices(colors, weights)[0] + (255,)
    return im


def blobs(im, rng, color, count, size=(1, 3), where=None):
    """Small clumps of one colour dropped on the tile (ore flecks, moss, cracks)."""
    px = im.load()
    for _ in range(count):
        cx, cy = rng.randrange(16), rng.randrange(16)
        w, h = rng.randint(*size), rng.randint(*size)
        for dy in range(h):
            for dx in range(w):
                x, y = cx + dx, cy + dy
                if 0 <= x < 16 and 0 <= y < 16 and px[x, y][3] and (where is None or where(x, y)):
                    if rng.random() < 0.8:
                        px[x, y] = color + (255,)


def stone(name, colors, weights=(5, 2, 2, 1), cracks=None):
    rng = seeded(name)
    im = speckle(rng, colors, weights)
    if cracks:
        blobs(im, rng, cracks, 4, (1, 3))
    return im


def mark_ore(im, rng, rampc, spots=6):
    """Ore flecks: a few small clumps, lit from the top left, on whatever stone is already in `im`."""
    px = im.load()
    placed = []
    tries = 0
    while len(placed) < spots and tries < 200:
        tries += 1
        cx, cy = rng.randint(1, 13), rng.randint(1, 13)
        if any(abs(cx - ox) < 4 and abs(cy - oy) < 4 for ox, oy in placed):
            continue
        placed.append((cx, cy))
        cells = [(0, 0), (1, 0), (0, 1), (1, 1)]
        if rng.random() < 0.5:
            cells.remove(rng.choice(cells[1:]))
        if rng.random() < 0.4:
            cells.append(rng.choice([(2, 1), (-1, 1), (1, 2), (0, 2)]))
        for dx, dy in cells:
            x, y = cx + dx, cy + dy
            if 0 <= x < 16 and 0 <= y < 16:
                edge = (dx, dy) in [(1, 1), (2, 1), (1, 2), (0, 2)]
                px[x, y] = (rampc[0] if edge else rampc[2] if (dx, dy) != (0, 0) else rampc[3]) + (255,)


def bordered(name, light, mid, dark, sparkle=None):
    """A metal-block face like vanilla's gold block: lit top and left rim, shaded bottom and right, noisy middle."""
    rng = seeded(name)
    im = new()
    px = im.load()
    for y in range(16):
        for x in range(16):
            if x == 0 or y == 0:
                c = light[0] if rng.random() < 0.7 else light[1]
            elif x == 15 or y == 15:
                c = dark[0] if rng.random() < 0.7 else dark[1]
            else:
                c = rng.choices(mid, [4, 3, 3, 1][:len(mid)])[0]
            px[x, y] = c + (255,)
    if sparkle:
        blobs(im, rng, sparkle, 5, (1, 2), where=lambda x, y: 1 <= x <= 14 and 1 <= y <= 14)
    return im


def wrapped_noise(rng, cells=4):
    """Smooth value noise that tiles (clouds)."""
    grid = [[rng.random() for _ in range(cells)] for _ in range(cells)]
    out = [[0.0] * 16 for _ in range(16)]
    step = 16 / cells
    for y in range(16):
        for x in range(16):
            gx, gy = x / step, y / step
            x0, y0 = int(gx) % cells, int(gy) % cells
            x1, y1 = (x0 + 1) % cells, (y0 + 1) % cells
            fx, fy = gx - int(gx), gy - int(gy)
            fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
            top = grid[y0][x0] * (1 - fx) + grid[y0][x1] * fx
            bot = grid[y1][x0] * (1 - fx) + grid[y1][x1] * fx
            out[y][x] = top * (1 - fy) + bot * fy
    return out


def cloud(name, colors, alphas):
    rng = seeded(name)
    n = wrapped_noise(rng, 4)
    m = wrapped_noise(rng, 8)
    im = new()
    px = im.load()
    for y in range(16):
        for x in range(16):
            v = n[y][x] * 0.7 + m[y][x] * 0.3
            i = 0 if v > 0.56 else 1 if v > 0.42 else 2
            px[x, y] = colors[i] + (alphas[i],)
    return im


def bark(name, colors):
    """Vertical grain: each column leans to one shade, with the odd pixel off."""
    rng = seeded(name)
    im = new()
    px = im.load()
    lean = [rng.randrange(len(colors)) for _ in range(16)]
    for x in range(16):
        if x > 0 and rng.random() < 0.45:
            lean[x] = lean[x - 1]
        for y in range(16):
            i = lean[x] if rng.random() < 0.8 else rng.randrange(len(colors))
            px[x, y] = colors[i] + (255,)
    return im


def log_top(name, bark_c, ring_c):
    """A cut log end: a bark rim, then rings, a darker heart."""
    rng = seeded(name)
    im = new()
    px = im.load()
    for y in range(16):
        for x in range(16):
            d = min(x, y, 15 - x, 15 - y)
            if d < 2:
                c = bark_c[rng.choices([0, 1, 2], [3, 4, 2])[0]]
            else:
                c = ring_c[1] if d in (3, 5) else ring_c[0]
                if d >= 6:
                    c = ring_c[2]
                if rng.random() < 0.12:
                    c = ring_c[(ring_c.index(c) + 1) % len(ring_c)]
            px[x, y] = c + (255,)
    return im


def planks(name, colors, seam):
    """Four boards across, each its own shade, with seams and a few end joints."""
    rng = seeded(name)
    im = new()
    px = im.load()
    for board in range(4):
        base = rng.randrange(len(colors))
        joint = rng.randint(3, 12)
        for y in range(board * 4, board * 4 + 4):
            for x in range(16):
                if y == board * 4 + 3:
                    c = seam
                elif x == joint and y == board * 4:
                    c = seam
                else:
                    c = colors[base] if rng.random() < 0.75 else colors[rng.randrange(len(colors))]
                px[x, y] = c + (255,)
    return im


def bricks(name, colors, mortar):
    """Four courses, each joint offset from the course above."""
    rng = seeded(name)
    im = new()
    px = im.load()
    for y in range(16):
        course = y // 4
        for x in range(16):
            shift = 0 if course % 2 == 0 else 8
            if y % 4 == 3 or (x + shift) % 16 in (7, 15) and y % 4 != 3:
                c = mortar[0] if rng.random() < 0.75 else mortar[1]
            else:
                c = colors[0] if rng.random() < 0.55 else colors[rng.randrange(1, len(colors))]
            px[x, y] = c + (255,)
    return im


def leaves(name, colors, hole_share=0.16, dark=None):
    """Leaf mass with holes (all-or-nothing alpha, so it stays cutout)."""
    rng = seeded(name)
    im = speckle(rng, colors, [3, 4, 3, 1][:len(colors)])
    px = im.load()
    holes = int(256 * hole_share)
    done = 0
    while done < holes:
        x, y = rng.randrange(16), rng.randrange(16)
        for dx, dy in rng.choice([[(0, 0)], [(0, 0), (1, 0)], [(0, 0), (0, 1)], [(0, 0), (1, 0), (0, 1)]]):
            if 0 <= x + dx < 16 and 0 <= y + dy < 16:
                px[x + dx, y + dy] = (0, 0, 0, 0)
                done += 1
    # a darker pixel under each hole's lower edge gives the layered look
    dark = dark or colors[-1]
    for y in range(15):
        for x in range(16):
            if px[x, y][3] == 0 and px[x, y + 1][3] and rng.random() < 0.5:
                px[x, y + 1] = dark + (255,)
    return im


def ellipse(cx, cy, rx, ry):
    return {(x, y) for y in range(16) for x in range(16) if ((x + 0.5 - cx) / rx) ** 2 + ((y + 0.5 - cy) / ry) ** 2 <= 1.0}


def polygon(points):
    m = Image.new("L", (16, 16), 0)
    ImageDraw.Draw(m).polygon(points, fill=255)
    px = m.load()
    return {(x, y) for y in range(16) for x in range(16) if px[x, y]}


def inside_edge(mask, x, y):
    return any((x + dx, y + dy) not in mask for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))


def paint_mask(im, mask, colors, rng):
    """Fill a shape: darkest outline, then lit from the top left (colors: dark, shadow, mid, light)."""
    px = im.load()
    xs = [p[0] for p in mask]
    ys = [p[1] for p in mask]
    cx, cy = (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2
    for x, y in mask:
        if inside_edge(mask, x, y):
            c = colors[0]
        else:
            d = (x - cx) + (y - cy)
            lvl = 3 if d < -2.5 else 2 if d < 1.5 else 1
            if rng.random() < 0.15:
                lvl = max(1, min(3, lvl + rng.choice([-1, 1])))
            c = colors[lvl]
        px[x, y] = c + (255,)


def gem(mask, colors, rng, glints):
    """A crystal: facets light to dark across it, glint pixels given (5 colours: outline, shadow, mid, light, glint)."""
    im = new()
    px = im.load()
    xs = [p[0] for p in mask]
    ys = [p[1] for p in mask]
    cx, cy = (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2
    for x, y in mask:
        if inside_edge(mask, x, y):
            c = colors[0]
        else:
            d = (x - cx) + (y - cy) * 0.6
            c = colors[3] if d < -2.0 else colors[2] if d < 0.8 else colors[1]
        px[x, y] = c + (255,)
    for x, y in glints:
        if (x, y) in mask:
            px[x, y] = colors[4] + (255,)
    return im


# ---------------------------------------------------------------- the ramps

HOLY = ramp("dde1e8", "eceff3", "f6f8fa", "c9ced8")        # holystone: pale silver-white
HOLY_DARK = ramp("aeb5c1")
MINT = ramp("86d9a8", "9ae8bb", "6fc596", "b0f2cb")        # moss and grass
MINT_LEAF = ramp("6dcb98", "86dfae", "55b484", "9bedc0")
DIRT = ramp("a4b4b8", "93a5aa", "b3c1c4", "7f9298")        # aether dirt: pale grey-cyan
GOLD_GRASS = ramp("f0c84a", "ffda66", "dcb22e", "ffe790")
SAND = ramp("e6c46c", "f0d583", "d6b058", "f6e4a2")         # quicksoil
ICE = ramp("bde2f1", "d3edf8", "a6d1e6", "e9f8fd")
AMBROSIUM = ramp("5b4a05", "c9a21c", "f2d23a", "ffec7a")   # dark, shade, mid, light
ZANITE = ramp("3a1a6b", "6f3db8", "9a5fe0", "bb8bf5")
GRAVITITE = ramp("70154f", "a82a85", "e24fb5", "ff86d8")
PINK = ramp("d65fb2", "ea82ca", "b8409a", "f7a8e0")
GREY_GREEN_BARK = ramp("7b8b7a", "6b7b6a", "8b9b89", "5c6a5b")
SKYROOT_RING = ramp("b9c4a6", "a6b392", "8c9a79")
PALE_WOOD = ramp("b3bea3", "a3af92", "c3cdb3")
PALE_SEAM = hx("86917a")
STRIPPED = ramp("a9b49a", "b8c3a8", "99a48a", "c4cfb5")
AMBER_BARK = ramp("b57828", "a26820", "c98a35", "8c5818")
AMBER_RING = ramp("e2b252", "cf9a3a", "b07f26")
GOLD_LEAF = ramp("f3c63c", "ffd862", "dba92a", "ffe68a", "b98818")
CRYSTAL = ramp("adeaf0", "c6f5f8", "8bd7e2", "e3fcfd")
WHITE_APPLE_C = ramp("6f7d8c", "b4c0cc", "e3eaf0", "ffffff")


def holystone_like(name, moss=False):
    rng = seeded(name)
    im = speckle(rng, HOLY, [5, 3, 2, 3])
    blobs(im, rng, HOLY_DARK[0], 3, (1, 3))
    if moss:
        blobs(im, rng, MINT[0], 7, (2, 4))
        blobs(im, rng, MINT[2], 4, (1, 3))
        blobs(im, rng, MINT[3], 3, (1, 2))
    return im


def ore(name, rampc):
    rng = seeded(name)
    im = speckle(rng, HOLY, [5, 3, 2, 3])
    blobs(im, rng, HOLY_DARK[0], 2, (1, 2))
    mark_ore(im, rng, rampc, 6)
    return im


def grass_top(name, colors):
    rng = seeded(name)
    im = speckle(rng, colors, [4, 3, 2, 2])
    blobs(im, rng, colors[2], 6, (1, 3))
    return im


def grass_side(name, colors, dirt_colors):
    """The soil with a grass edge along the top that hangs lower in places."""
    rng = seeded(name)
    im = speckle(rng, dirt_colors, [5, 3, 2, 1])
    px = im.load()
    for x in range(16):
        depth = 3 + rng.choice([0, 0, 1, 1, 2])
        for y in range(depth):
            c = colors[rng.choices([0, 1, 2, 3], [4, 3, 2, 1])[0]]
            if y == depth - 1 and depth > 3:
                c = colors[2]
            px[x, y] = c + (255,)
    return im


def amber_glass():
    """Glass: a solid amber rim, a see-through middle with a bright slanting glint."""
    rng = seeded("quicksoil_glass")
    rim = ramp("d89a28", "c68418", "e8ae3e")
    im = new()
    px = im.load()
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15):
                px[x, y] = rim[rng.randrange(3)] + (255,)
            else:
                px[x, y] = hx("f3b94a") + (88,)
    for i, (x, y) in enumerate([(3, 3), (4, 4), (5, 5), (3, 5), (4, 6)]):
        px[x, y] = hx("ffe9a8") + (200 if i < 3 else 150,)
    for x, y in [(11, 12), (12, 11), (10, 13)]:
        px[x, y] = hx("fff3c6") + (170,)
    return im


def icestone():
    rng = seeded("icestone")
    im = speckle(rng, ICE, [4, 3, 3, 2])
    px = im.load()
    for x, y in [(2, 12), (3, 11), (4, 10), (5, 9), (6, 8), (10, 6), (11, 5), (12, 4), (13, 3)]:
        px[x, y] = hx("f4fcff") + (255,)
    for x, y in [(3, 12), (4, 11), (5, 10), (11, 6), (12, 5)]:
        px[x, y] = hx("8fc1da") + (255,)
    return im


def enchanted_gravitite():
    rng = seeded("enchanted_gravitite")
    im = speckle(rng, PINK, [4, 3, 3, 1])
    px = im.load()
    for i in range(16):
        for x, y in ((i, i), (15 - i, i)):
            if rng.random() < 0.75:
                px[x, y] = hx("ffc4ee") + (255,)
    for y in range(16):
        for x in (0, 15):
            px[x, y] = hx("a23a8c") + (255,)
    for x in range(16):
        for y in (0, 15):
            px[x, y] = hx("a23a8c") + (255,)
    for x, y in [(7, 3), (8, 12), (3, 8), (12, 7)]:
        px[x, y] = hx("ffffff") + (255,)
    return im


def crystal_leaves(fruit):
    rng = seeded("crystal_fruit_leaves" if fruit else "crystal_leaves")
    im = leaves("crystal_leaves", CRYSTAL, 0.12)
    px = im.load()
    for _ in range(7):
        x, y = rng.randrange(16), rng.randrange(16)
        if px[x, y][3]:
            px[x, y] = hx("ffffff") + (255,)
    if fruit:
        for cx, cy in [(3, 4), (10, 2), (7, 10), (12, 12)]:
            for dx, dy in [(0, 0), (1, 0), (0, 1), (1, 1)]:
                x, y = cx + dx, cy + dy
                edge = (dx, dy) in [(1, 1)]
                px[x, y] = (WHITE_APPLE_C[1] if edge else WHITE_APPLE_C[3] if (dx, dy) == (0, 0) else WHITE_APPLE_C[2]) + (255,)
    return im


def sapling(name, stem, crown):
    rng = seeded(name)
    im = new()
    px = im.load()
    for y in range(9, 16):
        px[7, y] = stem[0] + (255,)
        px[8, y] = stem[1] + (255,)
    px[6, 12] = stem[1] + (255,)
    px[9, 11] = stem[0] + (255,)
    mask = ellipse(8, 6, 5.5, 4.5) | ellipse(5.5, 8.5, 2.5, 2.2) | ellipse(10.5, 8.5, 2.5, 2.2)
    paint_mask(im, mask, [crown[2], crown[1], crown[0], crown[3]], rng)
    for _ in range(5):
        x, y = rng.choice(sorted(mask))
        if not inside_edge(mask, x, y):
            px[x, y] = crown[3] + (255,)
    return im


def berry_bush():
    rng = seeded("berry_bush")
    im = new()
    mask = ellipse(8, 7.5, 7, 5.5) | ellipse(4.5, 10, 4, 3.5) | ellipse(11.5, 10, 4, 3.5) | ellipse(8, 11.5, 5, 3.5)
    paint_mask(im, mask, [hx("2f7a57"), hx("3f9a6c"), hx("57b888"), hx("7fd6a6")], rng)
    px = im.load()
    blue = ramp("27409e", "3f5fd8", "6f8cf2", "c8d8ff")
    spots = [(3, 6), (8, 3), (11, 7), (6, 9), (2, 10), (10, 11), (7, 12), (13, 10)]
    for bx, by in spots:
        if (bx, by) in mask and (bx + 1, by + 1) in mask:
            px[bx, by] = blue[2] + (255,)
            px[bx + 1, by] = blue[1] + (255,)
            px[bx, by + 1] = blue[1] + (255,)
            px[bx + 1, by + 1] = blue[0] + (255,)
            px[bx, by] = blue[3] + (255,) if rng.random() < 0.4 else blue[2] + (255,)
    for y in range(13, 16):
        px[7, y] = hx("6e5f4c") + (255,)
        px[8, y] = hx("54483a") + (255,)
    return im


def berry_stem():
    im = new()
    px = im.load()
    a, b, c = hx("8a7a63"), hx("6e5f4c"), hx("54483a")
    for y in range(7, 16):
        px[7, y] = b + (255,)
        px[8, y] = c + (255,)
    for (x, y) in [(6, 9), (5, 8), (4, 7), (4, 6), (9, 8), (10, 7), (11, 6), (11, 5), (7, 6), (6, 5), (6, 4), (8, 6), (9, 5), (9, 4)]:
        px[x, y] = a + (255,)
    for (x, y) in [(5, 9), (10, 9), (6, 11), (9, 12)]:
        px[x, y] = c + (255,)
    return im


def flower(name, petals, centre):
    rng = seeded(name)
    im = new()
    px = im.load()
    green = ramp("3f8f4f", "56b36a", "2f6f3c")
    for y in range(8, 16):
        px[7, y] = green[0] + (255,)
    for x, y in [(6, 12), (5, 11), (8, 11), (9, 10), (6, 14)]:
        px[x, y] = green[1] + (255,)
    for x, y in [(8, 13), (7, 15)]:
        px[x, y] = green[2] + (255,)
    mask = set()
    for cx, cy in [(7, 2.5), (10, 4.5), (9, 7), (5, 7), (4, 4.5)]:
        mask |= ellipse(cx + 0.5, cy + 0.5, 2.0, 2.0)
    mask |= ellipse(7.5, 5.5, 2.5, 2.5)
    for x, y in mask:
        d = (x - 7) + (y - 5)
        px[x, y] = (petals[0] if inside_edge(mask, x, y) else petals[3] if d < -2 else petals[2] if d < 2 else petals[1]) + (255,)
    for x, y in [(7, 5), (8, 5), (7, 6), (8, 6)]:
        px[x, y] = centre + (255,)
    px[7, 5] = hx("ffffff") + (255,)
    return im


def torch():
    im = new()
    px = im.load()
    for y in range(8, 16):
        px[7, y] = hx("9a7640") + (255,)
        px[8, y] = hx("6f5329") + (255,)
    for y in (9, 12, 14):
        px[7, y] = hx("856231") + (255,)
    px[7, 6], px[8, 6] = hx("fff3a0") + (255,), hx("ffe05a") + (255,)
    px[7, 7], px[8, 7] = hx("ffd23a") + (255,), hx("e0a81e") + (255,)
    return im


def make_items():
    out = {}
    r = seeded("ambrosium_shard")
    out["ambrosium_shard"] = gem(polygon([(8, 0), (12, 4), (10, 13), (6, 15), (3, 8), (4, 3)]), AMBROSIUM + [hx("fff9c8")], r, [(6, 4), (7, 3), (6, 6)])
    r = seeded("zanite_gemstone")
    out["zanite_gemstone"] = gem(polygon([(8, 1), (13, 4), (13, 10), (8, 15), (3, 10), (3, 4)]), ZANITE + [hx("f0e2ff")], r, [(6, 4), (5, 5), (10, 11)])
    r = seeded("golden_amber")
    amber = ramp("5a2f0a", "b0680f", "e3951c", "f9bf45", "ffe9a0")
    out["golden_amber"] = gem(polygon([(5, 3), (11, 2), (14, 7), (11, 13), (6, 14), (2, 9)]), amber, r, [(5, 5), (6, 4), (4, 7)])
    im = new()
    r = seeded("blue_berry")
    blue = [hx("1c2a6b"), hx("2f4bb8"), hx("4f78e8"), hx("8fb0ff")]
    for cx, cy in [(5, 9.5), (10.5, 9.5), (8, 5.5)]:
        paint_mask(im, ellipse(cx, cy, 2.7, 2.7), blue, r)
    px = im.load()
    for x, y in [(4, 8), (9, 8), (7, 4)]:
        px[x, y] = hx("e0eaff") + (255,)
    for x, y in [(8, 2), (9, 1), (10, 1)]:
        px[x, y] = hx("3f9a6c") + (255,)
    px[8, 3] = hx("2f7a57") + (255,)
    out["blue_berry"] = im
    im = new()
    r = seeded("white_apple")
    paint_mask(im, ellipse(8, 9.5, 5.5, 5.2) | ellipse(5.5, 8.5, 3, 3.5) | ellipse(10.5, 8.5, 3, 3.5), WHITE_APPLE_C, r)
    px = im.load()
    px[8, 5] = hx("c7d1db") + (255,)
    for x, y in [(8, 2), (8, 3), (8, 4)]:
        px[x, y] = hx("7a5a30") + (255,)
    for x, y in [(9, 2), (10, 2), (11, 3), (10, 3)]:
        px[x, y] = hx("5fb87f") + (255,)
    for x, y in [(5, 7), (4, 8)]:
        px[x, y] = hx("ffffff") + (255,)
    out["white_apple"] = im
    return out


def make_blocks():
    t = {}
    t["holystone"] = holystone_like("holystone")
    t["mossy_holystone"] = holystone_like("mossy_holystone", moss=True)
    t["holystone_bricks"] = bricks("holystone_bricks", HOLY, [HOLY_DARK[0], hx("bcc2cd")])
    t["aether_dirt"] = speckle(seeded("aether_dirt"), DIRT, [5, 3, 2, 1])
    t["aether_grass_block_top"] = grass_top("aether_grass_block_top", MINT)
    t["aether_grass_block_side"] = grass_side("aether_grass_block_side", MINT, DIRT)
    t["enchanted_aether_grass_block_top"] = grass_top("enchanted_aether_grass_block_top", GOLD_GRASS)
    t["enchanted_aether_grass_block_side"] = grass_side("enchanted_aether_grass_block_side", GOLD_GRASS, DIRT)
    t["quicksoil"] = speckle(seeded("quicksoil"), SAND, [5, 3, 2, 2])
    t["quicksoil_glass"] = amber_glass()
    t["icestone"] = icestone()
    t["ambrosium_ore"] = ore("ambrosium_ore", AMBROSIUM)
    t["zanite_ore"] = ore("zanite_ore", ZANITE)
    t["gravitite_ore"] = ore("gravitite_ore", GRAVITITE)
    t["enchanted_gravitite"] = enchanted_gravitite()
    t["ambrosium_block"] = bordered("ambrosium_block", ramp("fff09a", "ffe46a"), ramp("f2d23a", "e6c52c", "ffdf50", "d4b020"),
                                    ramp("a8870f", "c29c16"), hx("fff6c0"))
    t["zanite_block"] = bordered("zanite_block", ramp("c9a2f8", "b88af0"), ramp("9a5fe0", "8c50d2", "a875ea", "7d44c4"),
                                 ramp("5b2f9a", "6a39ae"), hx("e0c8ff"))
    t["cold_aercloud"] = cloud("cold_aercloud", ramp("ffffff", "eef3fa", "dde6f2"), [225, 205, 185])
    t["blue_aercloud"] = cloud("blue_aercloud", ramp("c6e4ff", "a8d3fb", "8dc2f2"), [225, 205, 185])
    t["golden_aercloud"] = cloud("golden_aercloud", ramp("fff3b8", "ffe58c", "f6d266"), [225, 205, 185])
    t["skyroot_log"] = bark("skyroot_log", GREY_GREEN_BARK)
    t["skyroot_log_top"] = log_top("skyroot_log_top", GREY_GREEN_BARK, SKYROOT_RING)
    t["stripped_skyroot_log"] = bark("stripped_skyroot_log", STRIPPED)
    t["stripped_skyroot_log_top"] = log_top("stripped_skyroot_log_top", STRIPPED, SKYROOT_RING)
    t["skyroot_planks"] = planks("skyroot_planks", PALE_WOOD, PALE_SEAM)
    t["skyroot_leaves"] = leaves("skyroot_leaves", MINT_LEAF)
    t["skyroot_sapling"] = sapling("skyroot_sapling", ramp("6e7d6c", "8a9a86"), MINT_LEAF)
    t["golden_oak_log"] = bark("golden_oak_log", AMBER_BARK)
    t["golden_oak_log_top"] = log_top("golden_oak_log_top", AMBER_BARK, AMBER_RING)
    t["golden_oak_leaves"] = leaves("golden_oak_leaves", GOLD_LEAF[:4], dark=GOLD_LEAF[4])
    t["golden_oak_sapling"] = sapling("golden_oak_sapling", ramp("8c5818", "b57828"), GOLD_LEAF[:4])
    t["crystal_leaves"] = crystal_leaves(False)
    t["crystal_fruit_leaves"] = crystal_leaves(True)
    t["berry_bush"] = berry_bush()
    t["berry_bush_stem"] = berry_stem()
    t["white_flower"] = flower("white_flower", ramp("b3bdc7", "e0e6ec", "fafbfc", "ffffff"), hx("f3dc6a"))
    t["purple_flower"] = flower("purple_flower", ramp("5e3299", "7f4fc2", "9d70dc", "bd9cf0"), hx("ffe9a0"))
    t["ambrosium_torch"] = torch()
    return t


# ---------------------------------------------------------------- json

def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def merge_tag(path, values):
    """Adds `values` to a tag file, keeping what is there."""
    cur = {"values": []}
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            cur = json.load(f)
    for v in values:
        if v not in cur["values"]:
            cur["values"].append(v)
    write(path, cur)


def jp(name):
    return NS + ":" + name


def mc(name):
    return "minecraft:" + name


# every block id; the item of each shares it
LOGS = ["skyroot_log", "skyroot_wood", "stripped_skyroot_log", "stripped_skyroot_wood", "golden_oak_log", "golden_oak_wood"]
LEAVES = ["skyroot_leaves", "golden_oak_leaves", "crystal_leaves", "crystal_fruit_leaves"]
SAPLINGS = ["skyroot_sapling", "golden_oak_sapling"]
FLOWERS = ["white_flower", "purple_flower"]
ROTATED = ["holystone", "mossy_holystone", "aether_dirt", "quicksoil"]
CLOUDS = ["cold_aercloud", "blue_aercloud", "golden_aercloud"]
CUBES = ["holystone", "mossy_holystone", "holystone_bricks", "aether_dirt", "quicksoil", "icestone", "ambrosium_ore", "zanite_ore",
         "gravitite_ore", "enchanted_gravitite", "ambrosium_block", "zanite_block", "skyroot_planks"]
GRASSES = ["aether_grass_block", "enchanted_aether_grass_block"]
CROSSES = SAPLINGS + FLOWERS + ["berry_bush", "berry_bush_stem"]
# log -> (side texture, end texture)
LOG_TEX = {"skyroot_log": ("skyroot_log", "skyroot_log_top"), "skyroot_wood": ("skyroot_log", "skyroot_log"),
           "stripped_skyroot_log": ("stripped_skyroot_log", "stripped_skyroot_log_top"),
           "stripped_skyroot_wood": ("stripped_skyroot_log", "stripped_skyroot_log"),
           "golden_oak_log": ("golden_oak_log", "golden_oak_log_top"), "golden_oak_wood": ("golden_oak_log", "golden_oak_log")}
ALL_BLOCKS = (["holystone", "mossy_holystone", "holystone_bricks", "aether_dirt", "aether_grass_block", "enchanted_aether_grass_block",
               "quicksoil", "quicksoil_glass", "icestone", "ambrosium_ore", "zanite_ore", "gravitite_ore", "enchanted_gravitite",
               "ambrosium_block", "zanite_block"] + CLOUDS + LOGS + ["skyroot_planks"] + LEAVES + SAPLINGS
              + ["berry_bush", "berry_bush_stem"] + FLOWERS + ["ambrosium_torch"])
ITEMS = ["ambrosium_shard", "zanite_gemstone", "golden_amber", "blue_berry", "white_apple"]

NAMES = {
    "holystone": "Holystone", "mossy_holystone": "Mossy Holystone", "holystone_bricks": "Holystone Bricks",
    "aether_dirt": "Aether Dirt", "aether_grass_block": "Aether Grass Block", "enchanted_aether_grass_block": "Enchanted Aether Grass Block",
    "quicksoil": "Quicksoil", "quicksoil_glass": "Quicksoil Glass", "icestone": "Icestone",
    "ambrosium_ore": "Ambrosium Ore", "zanite_ore": "Zanite Ore", "gravitite_ore": "Gravitite Ore",
    "enchanted_gravitite": "Enchanted Gravitite", "ambrosium_block": "Block of Ambrosium", "zanite_block": "Block of Zanite",
    "cold_aercloud": "Cold Aercloud", "blue_aercloud": "Blue Aercloud", "golden_aercloud": "Golden Aercloud",
    "skyroot_log": "Skyroot Log", "skyroot_wood": "Skyroot Wood", "stripped_skyroot_log": "Stripped Skyroot Log",
    "stripped_skyroot_wood": "Stripped Skyroot Wood", "skyroot_planks": "Skyroot Planks", "skyroot_leaves": "Skyroot Leaves",
    "skyroot_sapling": "Skyroot Sapling", "golden_oak_log": "Golden Oak Log", "golden_oak_wood": "Golden Oak Wood",
    "golden_oak_leaves": "Golden Oak Leaves", "golden_oak_sapling": "Golden Oak Sapling", "crystal_leaves": "Crystal Leaves",
    "crystal_fruit_leaves": "Crystal Fruit Leaves", "berry_bush": "Berry Bush", "berry_bush_stem": "Berry Bush Stem",
    "white_flower": "White Flower", "purple_flower": "Purple Flower", "ambrosium_torch": "Ambrosium Torch",
}
ITEM_NAMES = {"ambrosium_shard": "Ambrosium Shard", "zanite_gemstone": "Zanite Gemstone", "golden_amber": "Golden Amber",
              "blue_berry": "Blue Berry", "white_apple": "White Apple"}


def tex(name, kind="block"):
    return "%s:%s/%s/%s" % (NS, kind, SUB, name)


def textures():
    for name, im in make_blocks().items():
        path = os.path.join(ASSETS, "textures", "block", SUB, name + ".png")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        im.save(path)
    for name, im in make_items().items():
        path = os.path.join(ASSETS, "textures", "item", SUB, name + ".png")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        im.save(path)


def block_model(name, data):
    write(os.path.join(ASSETS, "models", "block", SUB, name + ".json"), data)


def assets():
    translucent = lambda t: {"force_translucent": True, "sprite": tex(t)}
    state = lambda model, **rot: dict({"model": "%s:block/%s/%s" % (NS, SUB, model)}, **rot)
    spin = lambda model: [state(model), state(model, y=90), state(model, y=180), state(model, y=270)]

    for b in CUBES:
        block_model(b, {"parent": mc("block/cube_all"), "textures": {"all": tex(b)}})
    for b in CLOUDS + ["quicksoil_glass"]:
        block_model(b, {"parent": mc("block/cube_all"), "textures": {"all": translucent(b)}})
    for b in GRASSES:
        block_model(b, {"parent": mc("block/cube_bottom_top"),
                        "textures": {"bottom": tex("aether_dirt"), "top": tex(b + "_top"), "side": tex(b + "_side")}})
    for b in LOGS:
        side, end = LOG_TEX[b]
        block_model(b, {"parent": mc("block/cube_column"), "textures": {"end": tex(end), "side": tex(side)}})
        block_model(b + "_horizontal", {"parent": mc("block/cube_column_horizontal"), "textures": {"end": tex(end), "side": tex(side)}})
    for b in LEAVES:
        block_model(b, {"parent": mc("block/leaves"), "textures": {"all": tex(b)}})
    for b in CROSSES:
        block_model(b, {"parent": mc("block/cross"), "textures": {"cross": tex(b)}})
    block_model("ambrosium_torch", {"parent": mc("block/template_torch"), "textures": {"torch": tex("ambrosium_torch")}})
    block_model("ambrosium_wall_torch", {"parent": mc("block/template_torch_wall"), "textures": {"torch": tex("ambrosium_torch")}})

    # blockstates
    bs = os.path.join(ASSETS, "blockstates")
    for b in ALL_BLOCKS:
        if b in LOGS:
            write(os.path.join(bs, b + ".json"), {"variants": {"axis=x": state(b + "_horizontal", x=90, y=90),
                                                               "axis=y": state(b), "axis=z": state(b + "_horizontal", x=90)}})
        elif b in ROTATED or b in GRASSES:
            write(os.path.join(bs, b + ".json"), {"variants": {"": spin(b)}})
        else:
            write(os.path.join(bs, b + ".json"), {"variants": {"": state(b)}})
    write(os.path.join(bs, "ambrosium_wall_torch.json"), {"variants": {
        "facing=east": state("ambrosium_wall_torch"), "facing=north": state("ambrosium_wall_torch", y=270),
        "facing=south": state("ambrosium_wall_torch", y=90), "facing=west": state("ambrosium_wall_torch", y=180)}})

    # items: a block's item shows the block, except flat ones (plants, torch), which are drawn like items
    flat = CROSSES + ["ambrosium_torch"]
    for b in ALL_BLOCKS:
        if b in flat:
            model = "%s:item/%s/%s" % (NS, SUB, b)
            write(os.path.join(ASSETS, "models", "item", SUB, b + ".json"),
                  {"parent": mc("item/generated"), "textures": {"layer0": tex(b)}})
        else:
            model = "%s:block/%s/%s" % (NS, SUB, b)
        write(os.path.join(ASSETS, "items", b + ".json"), {"model": {"type": "minecraft:model", "model": model}})
    for i in ITEMS:
        write(os.path.join(ASSETS, "models", "item", SUB, i + ".json"),
              {"parent": mc("item/generated"), "textures": {"layer0": tex(i, "item")}})
        write(os.path.join(ASSETS, "items", i + ".json"),
              {"model": {"type": "minecraft:model", "model": "%s:item/%s/%s" % (NS, SUB, i)}})

    # lang
    lang = {"itemGroup.jaymospowers.aether": "The Aether"}
    # shown by the dimension's bed rule (dimension_type/aether.json): there is no night to sleep through
    lang["message.jaymospowers.aether.no_sleep"] = "There is no night in the Aether to sleep through."
    for b, n in NAMES.items():
        lang["block.jaymospowers." + b] = n
    for i, n in ITEM_NAMES.items():
        lang["item.jaymospowers." + i] = n
    write(os.path.join(ASSETS, "lang", "parts", "aether.json"), lang)


# loot: a block's table (the 26.3 format: "condition" and "modifier" on entries and pools)
SILK = "minecraft:tool/can_silk_touch"
SHEAR = "minecraft:tool/can_shear"
SURVIVES = {"type": "minecraft:survives_explosion"}
SHEAR_OR_SILK = {"type": "minecraft:any_of", "terms": [SHEAR, SILK]}
SAPLING_CHANCES = [0.05, 0.0625, 0.083333336, 0.1]


def table(block, pools):
    return {"type": "minecraft:block", "pools": pools, "random_sequence": "%s:blocks/%s" % (NS, block)}


def item_entry(name, **extra):
    return dict({"type": "minecraft:item", "name": name}, **extra)


def self_table(b):
    return table(b, [{"rolls": 1, "condition": SURVIVES, "entries": [item_entry(jp(b))]}])


def silk_else(b, drop, modifier=None):
    other = item_entry(drop)
    if modifier:
        other["modifier"] = modifier
    else:
        other["condition"] = SURVIVES
    return table(b, [{"rolls": 1, "entries": [{"type": "minecraft:alternatives", "children": [
        item_entry(jp(b), condition=SILK), other]}]}])


STICKS = {"rolls": 1, "condition": {"type": "minecraft:inverted", "term": SHEAR_OR_SILK},
          "entries": [item_entry(mc("stick"), condition={"type": "minecraft:table_bonus", "enchantment": "minecraft:fortune",
                                                         "chances": [0.02, 0.022222223, 0.025, 0.033333335, 0.1]},
                                 modifier=[{"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 2}},
                                           {"type": "minecraft:explosion_decay"}])]}


def leaves_table(b, sapling=None, fruit=None, rare=None):
    own = item_entry(jp(b), condition=SHEAR_OR_SILK)
    if sapling:
        children = [own, item_entry(jp(sapling), condition={"type": "minecraft:all_of", "terms": [
            SURVIVES, {"type": "minecraft:table_bonus", "enchantment": "minecraft:fortune", "chances": SAPLING_CHANCES}]})]
        first = {"rolls": 1, "entries": [{"type": "minecraft:alternatives", "children": children}]}
    else:
        first = {"rolls": 1, "entries": [own]}
    pools = [first, STICKS]
    if fruit:
        pools.append({"rolls": 1, "condition": {"type": "minecraft:inverted", "term": SHEAR_OR_SILK},
                      "entries": [item_entry(fruit, modifier=[{"type": "minecraft:explosion_decay"}])]})
    if rare:
        pools.append({"rolls": 1, "condition": {"type": "minecraft:inverted", "term": SHEAR_OR_SILK},
                      "entries": [item_entry(rare, condition={"type": "minecraft:table_bonus", "enchantment": "minecraft:fortune",
                                                              "chances": [0.00005, 0.0001, 0.00015, 0.00025]})]})
    return table(b, pools)


ORE_BONUS = [{"type": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"},
             {"type": "minecraft:explosion_decay"}]


def loot():
    out = os.path.join(DATA, "loot_table", "blocks")
    tables = {}
    for b in ["holystone", "mossy_holystone", "holystone_bricks", "aether_dirt", "quicksoil", "icestone", "gravitite_ore", "enchanted_gravitite",
              "ambrosium_block", "zanite_block", "skyroot_log", "skyroot_wood", "stripped_skyroot_log", "stripped_skyroot_wood",
              "skyroot_planks", "skyroot_sapling", "golden_oak_sapling", "white_flower", "purple_flower", "ambrosium_torch",
              "berry_bush_stem"] + CLOUDS:
        tables[b] = self_table(b)
    tables["aether_grass_block"] = silk_else("aether_grass_block", jp("aether_dirt"))
    tables["enchanted_aether_grass_block"] = silk_else("enchanted_aether_grass_block", jp("aether_dirt"))
    tables["quicksoil_glass"] = table("quicksoil_glass", [{"rolls": 1, "condition": SILK, "entries": [item_entry(jp("quicksoil_glass"))]}])
    tables["ambrosium_ore"] = silk_else("ambrosium_ore", jp("ambrosium_shard"), ORE_BONUS)
    tables["zanite_ore"] = silk_else("zanite_ore", jp("zanite_gemstone"), ORE_BONUS)
    for b in ("golden_oak_log", "golden_oak_wood"):
        tables[b] = table(b, [
            {"rolls": 1, "condition": SURVIVES, "entries": [item_entry(jp(b))]},
            {"rolls": 1, "condition": {"type": "minecraft:match_tool", "predicate": {"items": "#minecraft:axes"}},
             "entries": [item_entry(jp("golden_amber"), modifier=[
                 {"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 2}}])]}])
    tables["skyroot_leaves"] = leaves_table("skyroot_leaves", sapling="skyroot_sapling")
    tables["golden_oak_leaves"] = leaves_table("golden_oak_leaves", sapling="golden_oak_sapling", rare=mc("golden_apple"))
    tables["crystal_leaves"] = leaves_table("crystal_leaves")
    tables["crystal_fruit_leaves"] = leaves_table("crystal_fruit_leaves", fruit=jp("white_apple"))
    tables["berry_bush"] = table("berry_bush", [{"rolls": 1, "entries": [item_entry(jp("blue_berry"), modifier=[
        {"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 3}},
        {"type": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:uniform_bonus_count",
         "parameters": {"bonusMultiplier": 1}},
        {"type": "minecraft:explosion_decay"}])]}])
    missing = [b for b in ALL_BLOCKS if b not in tables]
    assert not missing, missing
    for b, t in tables.items():
        write(os.path.join(out, b + ".json"), t)


def recipes():
    out = os.path.join(DATA, "recipe", SUB)
    shaped = lambda key, pattern, result, count=1, group=None, cat="building": dict(
        {"type": "minecraft:crafting_shaped", "category": cat, "key": key, "pattern": pattern,
         "result": {"id": result, "count": count} if count > 1 else {"id": result}}, **({"group": group} if group else {}))
    shapeless = lambda ings, result, count=1, group=None, cat="misc": dict(
        {"type": "minecraft:crafting_shapeless", "category": cat, "ingredients": ings,
         "result": {"id": result, "count": count} if count > 1 else {"id": result}}, **({"group": group} if group else {}))
    smelt = lambda ing, result, xp: {"type": "minecraft:smelting", "category": "blocks", "cookingtime": 200, "experience": xp,
                                     "ingredient": ing, "result": {"id": result}}
    r = {}
    r["skyroot_planks"] = shapeless(["#" + jp("aether_logs")], jp("skyroot_planks"), 4, "planks", "building")
    r["skyroot_wood"] = shaped({"#": jp("skyroot_log")}, ["##", "##"], jp("skyroot_wood"), 3, "bark")
    r["stripped_skyroot_wood"] = shaped({"#": jp("stripped_skyroot_log")}, ["##", "##"], jp("stripped_skyroot_wood"), 3, "bark")
    r["golden_oak_wood"] = shaped({"#": jp("golden_oak_log")}, ["##", "##"], jp("golden_oak_wood"), 3, "bark")
    r["holystone_bricks"] = shaped({"#": jp("holystone")}, ["##", "##"], jp("holystone_bricks"), 4)
    r["ambrosium_block"] = shaped({"#": jp("ambrosium_shard")}, ["###", "###", "###"], jp("ambrosium_block"))
    r["ambrosium_shard_from_block"] = shapeless([jp("ambrosium_block")], jp("ambrosium_shard"), 9, "ambrosium_shard")
    r["zanite_block"] = shaped({"#": jp("zanite_gemstone")}, ["###", "###", "###"], jp("zanite_block"))
    r["zanite_gemstone_from_block"] = shapeless([jp("zanite_block")], jp("zanite_gemstone"), 9, "zanite_gemstone")
    r["ambrosium_torch"] = shaped({"#": mc("stick"), "A": jp("ambrosium_shard")}, ["A", "#"], jp("ambrosium_torch"), 4, None, "misc")
    r["quicksoil_glass"] = smelt(jp("quicksoil"), jp("quicksoil_glass"), 0.1)
    r["enchanted_gravitite"] = smelt(jp("gravitite_ore"), jp("enchanted_gravitite"), 1.0)
    # the flowers' stews: slow falling 4 s (80 ticks), nausea 12 s (240 ticks)
    for flower, effect, ticks in (("white_flower", "minecraft:slow_falling", 80), ("purple_flower", "minecraft:nausea", 240)):
        r["suspicious_stew_from_" + flower] = {
            "type": "minecraft:crafting_shapeless", "category": "misc", "group": "suspicious_stew",
            "ingredients": [mc("bowl"), "#minecraft:mushrooms", "#minecraft:mushrooms", jp(flower)],
            "result": {"id": mc("suspicious_stew"), "components": {"minecraft:suspicious_stew_effects": [{"duration": ticks, "id": effect}]}}}
    for name, data in r.items():
        write(os.path.join(out, name + ".json"), data)


def tags():
    b = os.path.join(MCDATA, "tags", "block")
    i = os.path.join(MCDATA, "tags", "item")
    jb = os.path.join(DATA, "tags", "block")
    ji = os.path.join(DATA, "tags", "item")
    ids = lambda names: [jp(n) for n in names]
    aether_logs = ids(LOGS)
    write(os.path.join(jb, "aether_logs.json"), {"values": aether_logs})
    write(os.path.join(ji, "aether_logs.json"), {"values": aether_logs})
    write(os.path.join(jb, "aether_stone_ore_replaceables.json"), {"values": [jp("holystone")]})
    pickaxe = ["holystone", "mossy_holystone", "holystone_bricks", "icestone", "ambrosium_ore", "zanite_ore", "gravitite_ore",
               "enchanted_gravitite", "ambrosium_block", "zanite_block"]
    shovel = ["aether_dirt", "aether_grass_block", "enchanted_aether_grass_block", "quicksoil"]
    for root, kind in ((b, "block"), (i, "item")):
        merge_tag(os.path.join(root, "logs.json"), ["#" + jp("aether_logs")])
        merge_tag(os.path.join(root, "logs_that_burn.json"), ["#" + jp("aether_logs")])
        merge_tag(os.path.join(root, "planks.json"), [jp("skyroot_planks")])
        merge_tag(os.path.join(root, "leaves.json"), ids(LEAVES))
        merge_tag(os.path.join(root, "saplings.json"), ids(SAPLINGS))
        merge_tag(os.path.join(root, "small_flowers.json"), ids(FLOWERS))
    merge_tag(os.path.join(b, "dirt.json"), [jp("aether_dirt")])
    merge_tag(os.path.join(b, "grass_blocks.json"), ids(GRASSES))
    merge_tag(os.path.join(b, "animals_spawnable_on.json"), ids(GRASSES))
    merge_tag(os.path.join(b, "mineable", "pickaxe.json"), ids(pickaxe))
    merge_tag(os.path.join(b, "mineable", "axe.json"), ["#" + jp("aether_logs"), jp("skyroot_planks")])
    merge_tag(os.path.join(b, "mineable", "shovel.json"), ids(shovel))
    merge_tag(os.path.join(b, "mineable", "hoe.json"), ids(LEAVES))
    merge_tag(os.path.join(b, "needs_stone_tool.json"), ids(["icestone", "zanite_ore", "zanite_block"]))
    merge_tag(os.path.join(b, "needs_iron_tool.json"), ids(["gravitite_ore", "enchanted_gravitite"]))


if __name__ == "__main__":
    textures()
    assets()
    loot()
    recipes()
    tags()
    print("Aether assets and data written.")
