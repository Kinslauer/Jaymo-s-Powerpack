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
    lang.update(n1_lang())
    path = os.path.join(ASSETS, "lang", "parts", "aether.json")
    if os.path.exists(path):  # keep the keys other generators added (the mobs')
        with open(path, encoding="utf-8") as f:
            lang = dict(json.load(f), **lang)
    write(path, lang)


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



# ================================================================ Aether II: stone, soil, ice, ores, clouds, building (N1)
# Everything below paints and writes the second set of blocks (AetherBlocks.initStones ... initBuilding, AetherItems'
# ore items). Textures are painted here in the same flat vanilla style; the JSON shapes follow the vanilla block families.

UNDER = ramp("5b6070", "4e5362", "6a7082", "3f4452")        # undershale: dark slate
UNDER_DARK = hx("33384a")
AGI = ramp("9a7fc2", "8a6db4", "ab93d0", "78599f")           # agiosite: purple
ICH = ramp("8a6847", "7a5a3c", "9b7753", "66492f")           # ichorite: brown
FER = ramp("8f8179", "7f726b", "9f9189", "6d615b")           # ferrosite: rusty grey
FADED = ramp("cfd4dc", "dde1e8", "bcc2cc", "e8ebf0")         # faded holystone: dimmer, bluer
SILT = ramp("8d9fb8", "7e90aa", "9db0c8", "6d7f99")          # shimmering silt
FSAND = ramp("b96a3d", "a95c33", "c97b4c", "974e2a")         # ferrosite sand
FMUD = ramp("5d4a45", "6a5650", "4f3e3a", "77625b")          # ferrosite mud
SNOW = ramp("f6faff", "eaf2fb", "dce8f5", "cfdeef")          # arctic snow
AICE = ramp("a9d6ef", "bde4f6", "93c5e3", "d6f0fb")          # arctic ice
PACKED = ramp("8fc0e6", "9fcaec", "7fb2da", "b2d8f2")        # arctic packed ice
ICE_CRYSTAL = ramp("3f86b8", "5aa6dc", "86c8f0", "d0f0ff", "ffffff")
QUARTZ_C = ramp("a79d8f", "cfc8bb", "eeeadd", "ffffff")
ARK_ORE = ramp("1e2f4a", "2f4a6e", "4f78a8", "9fc8f0")       # arkenium flecks: steel blue
GLINT_ORE_C = ramp("0f6f66", "1fa89a", "35d4c0", "9ff5e8")   # glint flecks: teal
CORRO = ramp("1a6a3a", "27914f", "3fbf6f", "9af0b8")         # corrobonite: emerald
ARK_GEM = ramp("1e2f4a", "3f5f86", "6f9bc8", "b5d8f8", "e8f4ff")
GLINT_GEM = ramp("0f6f66", "1fa89a", "35d4c0", "9ff5e8", "e6fffb")
CORRO_GEM = ramp("17572f", "27914f", "3fbf6f", "9af0b8", "e0ffec")
SCATTER = ramp("3fa9b8", "58c4cf", "74d6df", "a7ecf2", "e6fcff")
STEEL = ramp("4a5a70", "5b6d86", "70849f", "3b4a5e")         # arkenium metal
STEEL_LIGHT = ramp("a9c4e4", "d6ebff")

DYES = [("white", (249, 255, 254)), ("orange", (249, 128, 29)), ("magenta", (199, 78, 189)), ("light_blue", (58, 179, 218)),
        ("yellow", (254, 216, 61)), ("lime", (128, 199, 31)), ("pink", (243, 139, 170)), ("gray", (71, 79, 82)),
        ("light_gray", (157, 157, 151)), ("cyan", (22, 156, 156)), ("purple", (137, 50, 184)), ("blue", (60, 68, 170)),
        ("brown", (131, 84, 50)), ("green", (94, 124, 22)), ("red", (176, 46, 38)), ("black", (29, 29, 33))]


def alpha_all(im, a):
    px = im.load()
    for y in range(16):
        for x in range(16):
            if px[x, y][3]:
                px[x, y] = px[x, y][:3] + (a,)
    return im


def stone_tex(name, colors, dark, light=None, weights=(5, 3, 2, 2), dark_blobs=3, light_blobs=0, fleck=None, flecks=0):
    rng = seeded(name)
    im = speckle(rng, colors, list(weights))
    blobs(im, rng, dark, dark_blobs, (1, 3))
    if light:
        blobs(im, rng, light, light_blobs, (1, 2))
    if fleck:
        blobs(im, rng, fleck, flecks, (1, 2))
    return im


def irradiated_holystone():
    rng = seeded("irradiated_holystone")
    im = speckle(rng, HOLY, [5, 3, 2, 3])
    blobs(im, rng, HOLY_DARK[0], 2, (1, 2))
    px = im.load()
    # two sickly veins wandering across the tile
    for start in (3, 11):
        x, y = 0, start
        for _ in range(22):
            px[x % 16, y % 16] = hx("b4d23a") + (255,)
            if rng.random() < 0.55:
                px[(x + 1) % 16, y % 16] = hx("d6ee62") + (255,)
            x += 1
            y += rng.choice([-1, 0, 0, 1])
    blobs(im, rng, hx("8fb02a"), 4, (1, 2))
    blobs(im, rng, hx("e8f68c"), 3, (1, 1))
    return im


def crude_scatterglass():
    rng = seeded("crude_scatterglass")
    im = new()
    px = im.load()
    for y in range(16):
        for x in range(16):
            c = rng.choices(SCATTER[:4], [3, 4, 2, 1])[0]
            px[x, y] = c + (rng.choice([175, 190, 205]),)
    # opaque crystal facets: short slanting streaks
    for sx, sy in ((2, 12), (9, 14), (5, 5), (12, 8)):
        for i in range(4):
            x, y = sx + i, sy - i
            if 0 <= x < 16 and 0 <= y < 16:
                px[x, y] = SCATTER[4] + (235,)
                if x + 1 < 16:
                    px[x + 1, y] = SCATTER[3] + (225,)
    for x in range(16):
        for y in (0, 15):
            px[x, y] = SCATTER[0] + (225,)
    for y in range(16):
        for x in (0, 15):
            px[x, y] = SCATTER[0] + (225,)
    return im


def clear_glass(name, rim, fill, glint):
    """A glass block: a solid rim, a see-through middle, two bright glints."""
    rng = seeded(name)
    im = new()
    px = im.load()
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15):
                px[x, y] = rim[rng.randrange(len(rim))] + (255,)
            else:
                px[x, y] = fill + (84,)
    for i, (x, y) in enumerate([(3, 3), (4, 4), (5, 5), (3, 5), (4, 6)]):
        px[x, y] = glint[0] + (200 if i < 3 else 150,)
    for x, y in [(11, 12), (12, 11), (10, 13)]:
        px[x, y] = glint[1] + (170,)
    return im


def pane_top(rim):
    """The edge of a pane: only the two middle columns show (the model reads columns 7 and 8)."""
    im = new()
    px = im.load()
    for y in range(16):
        for x in (7, 8):
            px[x, y] = rim[x - 7] + (255,)
    return im


def flagstones(name, colors, mortar):
    """Big flags: two courses of 8, each flag its own shade, the joints offset between courses."""
    rng = seeded(name)
    im = new()
    px = im.load()
    for course in range(2):
        shift = 0 if course == 0 else 4
        for flag in range(2):
            base = rng.randrange(len(colors))
            for y in range(course * 8, course * 8 + 8):
                for x in range(flag * 8, flag * 8 + 8):
                    xx = (x + shift) % 16
                    last_row = y == course * 8 + 7
                    last_col = flag * 8 + 7 == x
                    if last_row or last_col:
                        c = mortar[0] if rng.random() < 0.75 else mortar[1]
                    else:
                        c = colors[base] if rng.random() < 0.7 else colors[rng.randrange(len(colors))]
                    px[xx, y] = c + (255,)
    return im


def pillar_side(name):
    rng = seeded(name)
    im = new()
    px = im.load()
    for x in range(16):
        for y in range(16):
            if x in (0, 15):
                c = HOLY_DARK[0]
            elif x in (1, 14):
                c = HOLY[2]
            elif x in (4, 5, 10, 11):
                c = HOLY[0] if rng.random() < 0.8 else HOLY[3]
            else:
                c = HOLY[1] if rng.random() < 0.8 else HOLY[0]
            px[x, y] = c + (255,)
    for y in (0, 15):
        for x in range(16):
            px[x, y] = (HOLY_DARK[0] if rng.random() < 0.6 else HOLY[3]) + (255,)
    return im


def pillar_top(name):
    rng = seeded(name)
    im = new()
    px = im.load()
    for y in range(16):
        for x in range(16):
            d = min(x, y, 15 - x, 15 - y)
            c = HOLY_DARK[0] if d == 0 else HOLY[2] if d in (1, 2) else HOLY[3] if d == 3 else HOLY[0] if rng.random() < 0.6 else HOLY[1]
            px[x, y] = c + (255,)
    return im


def keystone(name):
    rng = seeded(name)
    im = speckle(rng, HOLY, [5, 3, 2, 3])
    px = im.load()
    for y in range(16):
        for x in range(16):
            d = min(x, y, 15 - x, 15 - y)
            if d == 1:
                px[x, y] = HOLY_DARK[0] + (255,)
            elif d == 2:
                px[x, y] = HOLY[2] + (255,)
            dd = abs(x - 7.5) + abs(y - 7.5)
            if 5.5 <= dd <= 6.5:
                px[x, y] = HOLY_DARK[0] + (255,)
            elif dd < 5.5 and dd > 2.5:
                px[x, y] = (HOLY[3] if (x + y) % 2 else HOLY[2]) + (255,)
    for x, y in ((7, 7), (8, 7), (7, 8), (8, 8)):
        px[x, y] = HOLY_DARK[0] + (255,)
    return im


def coarse_dirt():
    rng = seeded("coarse_aether_dirt")
    im = speckle(rng, DIRT, [3, 3, 2, 2])
    blobs(im, rng, HOLY[0], 8, (1, 2))
    blobs(im, rng, HOLY_DARK[0], 4, (1, 2))
    return im


PATH_TOP = ramp("b8c6c9", "a9b9bd", "c6d2d4", "98aaaf")


def path_top():
    rng = seeded("aether_dirt_path_top")
    im = speckle(rng, PATH_TOP, [4, 3, 3, 1])
    blobs(im, rng, PATH_TOP[3], 3, (1, 2))
    return im


def path_side():
    rng = seeded("aether_dirt_path_side")
    im = speckle(rng, DIRT, [5, 3, 2, 1])
    px = im.load()
    for x in range(16):
        for y in range(2):
            px[x, y] = PATH_TOP[rng.choices([0, 1, 2], [3, 3, 2])[0]] + (255,)
    return im


def farmland(name, wet):
    rng = seeded(name)
    base = ramp("6f8288", "607379", "7e9197", "51646a") if not wet else ramp("4d6168", "40545b", "5a6f77", "354850")
    im = speckle(rng, base, [4, 3, 2, 1])
    px = im.load()
    for y in (2, 6, 10, 14):
        for x in range(16):
            px[x, y] = base[3] + (255,)
            if y + 1 < 16:
                px[x, y + 1] = base[2] + (255,) if rng.random() < 0.6 else px[x, y + 1]
    return im


def sand_like(name, colors, weights=(5, 3, 2, 2), glint=None):
    rng = seeded(name)
    im = speckle(rng, colors, list(weights))
    blobs(im, rng, colors[3], 3, (1, 2))
    if glint:
        px = im.load()
        for _ in range(7):
            px[rng.randrange(16), rng.randrange(16)] = glint + (255,)
    return im


def mud_like(name, colors):
    rng = seeded(name)
    im = speckle(rng, colors, [4, 3, 3, 2])
    blobs(im, rng, colors[2], 5, (2, 3))
    return im


def arctic_ice(name, cracks=False):
    rng = seeded(name)
    a = 150 if cracks else 175
    im = new()
    px = im.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = rng.choices(AICE, [4, 3, 3, 1])[0] + (a,)
    for x, y in [(2, 12), (3, 11), (4, 10), (5, 9), (6, 8), (10, 6), (11, 5), (12, 4)]:
        px[x, y] = hx("f4fcff") + (225,)
    if cracks:
        # a web of cracks: white lines with a dark side
        for sx, sy in ((7, 0), (0, 8)):
            x, y = sx, sy
            for _ in range(14):
                if 0 <= x < 16 and 0 <= y < 16:
                    px[x, y] = hx("f4fcff") + (240,)
                    if x + 1 < 16:
                        px[x + 1, y] = hx("5f95bd") + (210,)
                x += rng.choice([0, 1, 1])
                y += rng.choice([1, 1, 0])
    return im


def packed_ice(name):
    rng = seeded(name)
    im = speckle(rng, PACKED, [4, 3, 3, 1])
    px = im.load()
    for sx in (1, 7, 12):
        for i in range(9):
            x, y = sx + i // 3, 3 + i + (sx % 3)
            if 0 <= x < 16 and 0 <= y < 16:
                px[x, y] = hx("d6ecfa") + (255,)
    blobs(im, rng, hx("6fa2cc"), 3, (1, 2))
    return im


def spikes(spec, colors, rng):
    """Crystal spikes standing on one edge: each (base x, base y, width, tip x, tip y); later ones drawn over earlier."""
    im = new()
    px = im.load()
    for bx, by, w, tx, ty in spec:
        mask = polygon([(bx - w, by), (bx + w, by), (tx, ty)])
        xs = [p[0] for p in mask]
        ys = [p[1] for p in mask]
        cx = (min(xs) + max(xs)) / 2
        for x, y in mask:
            if inside_edge(mask, x, y):
                c = colors[0]
            else:
                c = colors[3] if x < cx - 0.5 else colors[2] if x < cx + 1.0 else colors[1]
            px[x, y] = c + (255,)
        for gx, gy in [(tx - 1 if tx > bx - 1 else tx, ty + 3)]:
            if (gx, gy) in mask and not inside_edge(mask, gx, gy):
                px[gx, gy] = colors[4] + (255,)
    return im


def hanging_ice_crystal():
    rng = seeded("arctic_ice_crystal")
    # crystals hanging from the top edge: flip the standing version
    im = spikes([(4, 15, 2, 3, 3), (11, 15, 2, 12, 5), (8, 15, 3, 8, 0)], ICE_CRYSTAL, rng)
    return im.transpose(Image.FLIP_TOP_BOTTOM)


def corrobonite_cluster():
    rng = seeded("corrobonite_cluster")
    return spikes([(3, 15, 2, 2, 7), (12, 15, 2, 13, 6), (8, 15, 3, 8, 1), (5, 15, 2, 5, 8), (10, 15, 2, 10, 9)], CORRO_GEM, rng)


def ore_on(name, base, weights, flecks, dark, spots=6):
    rng = seeded(name)
    im = speckle(rng, base, list(weights))
    blobs(im, rng, dark, 2, (1, 2))
    mark_ore(im, rng, flecks, spots)
    return im


def puffy(name, tint=None):
    rng = seeded("cloudwool")
    colors = ramp("ffffff", "eef2f8", "d9e0ec", "c2cbdc")
    im = speckle(rng, colors, [4, 3, 2, 1])
    px = im.load()
    # small rounded puffs: a lit top-left pixel and a shaded bottom-right one on a grid, jittered
    for cy in range(2, 16, 5):
        for cx in range(2, 16, 5):
            x, y = (cx + rng.randint(-1, 1)) % 16, (cy + rng.randint(-1, 1)) % 16
            px[x, y] = colors[0] + (255,)
            px[(x + 1) % 16, (y + 1) % 16] = colors[3] + (255,)
    if tint:
        for y in range(16):
            for x in range(16):
                r, g, b, a = px[x, y]
                px[x, y] = (r * tint[0] // 255, g * tint[1] // 255, b * tint[2] // 255, a)
    return im


def cloud_front(name, base, arrow):
    """The purple aercloud's fling face: its cloud with an arrow in it."""
    im = cloud(name, base, [225, 205, 185])
    px = im.load()
    for x, y in [(7, 3), (8, 3), (6, 4), (7, 4), (8, 4), (9, 4), (5, 5), (6, 5), (7, 5), (8, 5), (9, 5), (10, 5), (7, 6), (8, 6), (7, 7), (8, 7),
                 (7, 8), (8, 8), (7, 9), (8, 9), (7, 10), (8, 10), (7, 11), (8, 11)]:
        px[x, y] = arrow + (245,)
    return im


def steel_bars():
    """Bars: two bars in the middle (the post) and one each side, two rails; the model reads columns 7-8 and 8-15."""
    im = new()
    px = im.load()
    for y in range(16):
        for x in (7, 8):
            px[x, y] = STEEL[x - 7 + (1 if y % 5 == 0 else 0)] + (255,)
        for x in (3, 12):
            px[x, y] = STEEL[2] + (255,) if x == 3 else STEEL[1] + (255,)
    for x in range(16):
        for y in (2, 13):
            px[x, y] = STEEL_LIGHT[0] + (255,) if x % 4 else STEEL[2] + (255,)
    return im


def steel_chain_block():
    """The chain model reads a 6-wide strip: two planes of 3 columns, links every 6 rows."""
    im = new()
    px = im.load()
    for plane in range(2):
        x0 = plane * 3
        for y in range(16):
            r = y % 6
            if r in (0, 5):
                cols = {x0 + 1: STEEL_LIGHT[0]}
            elif r in (1, 4):
                cols = {x0: STEEL[1], x0 + 2: STEEL[0]}
            else:
                cols = {x0: STEEL[1], x0 + 2: STEEL[3]}
            for x, c in cols.items():
                px[x, y] = c + (255,)
    return im


def steel_lantern():
    """The lantern strip: body side (0..5, 2..8), top and bottom (0..5, 9..14), cap bits and handle (the model's own layout)."""
    rng = seeded("arkenium_lantern")
    im = new()
    px = im.load()
    metal = [STEEL[2], STEEL[1], STEEL[0]]
    glow = [hx("e8fbff"), hx("c4f0ff"), hx("9fe2ff")]
    for y in range(2, 9):
        for x in range(6):
            frame = x in (0, 5) or y in (2, 8)
            px[x, y] = (metal[rng.randrange(3)] if frame else glow[0 if (x + y) % 3 == 0 else 1 if y < 5 else 2]) + (255,)
    for y in range(9, 15):
        for x in range(6):
            px[x, y] = metal[rng.randrange(3)] + (255,)
    for y in range(0, 2):
        for x in range(1, 5):
            px[x, y] = metal[rng.randrange(3)] + (255,)
    for y in range(10, 14):
        for x in range(1, 5):
            px[x, y] = metal[(x + y) % 3] + (255,)
    for x in range(11, 14):
        for y in list(range(1, 5)) + list(range(6, 12)):
            px[x, y] = STEEL[3] + (255,) if x == 13 else STEEL[1] + (255,)
    return im


def lantern_item():
    im = new()
    px = im.load()
    metal = [STEEL[2], STEEL[1], STEEL[0]]
    for x in range(6, 10):
        px[x, 2] = STEEL[3] + (255,)
    for x in (5, 10):
        for y in (3, 4):
            px[x, y] = STEEL[3] + (255,)
    for y in range(5, 12):
        for x in range(4, 12):
            frame = x in (4, 11) or y in (5, 11)
            px[x, y] = (metal[(x * 3 + y) % 3] if frame else (hx("e8fbff") if y < 8 else hx("9fe2ff") if y > 9 else hx("c4f0ff"))) + (255,)
    for x in range(5, 11):
        px[x, 12] = STEEL[3] + (255,)
    return im


def chain_item():
    im = new()
    px = im.load()
    for i in range(14):
        x, y = 3 + i * 10 // 14 + (1 if i % 2 else 0), 13 - i
        px[x, y] = STEEL_LIGHT[0 if i % 3 else 1] + (255,)
        px[x + 1, y] = STEEL[1 if i % 2 else 0] + (255,)
        px[x, y - 1 if y > 0 else y] = STEEL[3] + (255,)
    return im


def pebble_item():
    rng = seeded("holystone_rock_item")
    im = new()
    for cx, cy, rx, ry in ((6, 11, 3.5, 2.6), (11, 12, 3.0, 2.2), (9, 8.5, 2.6, 2.2)):
        paint_mask(im, ellipse(cx, cy, rx, ry), [HOLY_DARK[0], HOLY[0], HOLY[1], HOLY[2]], rng)
    return im


def spike_item():
    rng = seeded("pointed_holystone_item")
    im = new()
    paint_mask(im, polygon([(3, 15), (13, 15), (8, 0)]), [HOLY_DARK[0], HOLY[0], HOLY[1], HOLY[2]], rng)
    return im


def coin():
    rng = seeded("glint_coin")
    im = new()
    paint_mask(im, ellipse(8, 8, 6, 6), [hx("0f6f66"), hx("1fa89a"), hx("35d4c0"), hx("9ff5e8")], rng)
    px = im.load()
    for x, y in [(8, 5), (8, 6), (8, 7), (8, 8), (8, 9), (8, 10), (7, 6), (9, 6), (7, 10), (9, 10)]:
        px[x, y] = hx("0f6f66") + (255,)
    px[5, 5] = hx("e6fffb") + (255,)
    return im


def shard_item(name, colors, poly, glints):
    return gem(polygon(poly), colors, seeded(name), glints)


def cluster_item():
    return corrobonite_cluster()


def n1_make_blocks():
    t = {}
    t["undershale"] = stone_tex("undershale", UNDER, UNDER_DARK, hx("7d8498"), light_blobs=3)
    t["agiosite"] = stone_tex("agiosite", AGI, hx("5e4382"), hx("c3a9e6"), light_blobs=4)
    t["ichorite"] = stone_tex("ichorite", ICH, hx("4f3722"), hx("a98a62"), light_blobs=4)
    t["ferrosite"] = stone_tex("ferrosite", FER, hx("574c46"), None, fleck=hx("b5633a"), flecks=6)
    t["irradiated_holystone"] = irradiated_holystone()
    t["crude_scatterglass"] = crude_scatterglass()
    t["scatterglass"] = clear_glass("scatterglass", [hx("3fa9b8"), hx("58c4cf"), hx("2f8f9e")], hx("9fe6ee"), [hx("e6fcff"), hx("f4feff")])
    t["scatterglass_pane_top"] = pane_top([hx("3fa9b8"), hx("58c4cf")])
    t["quicksoil_glass_pane_top"] = pane_top([hx("d89a28"), hx("c68418")])
    t["undershale_bricks"] = bricks("undershale_bricks", UNDER, [UNDER_DARK, hx("2a2e3d")])
    t["agiosite_bricks"] = bricks("agiosite_bricks", AGI, [hx("5e4382"), hx("523a73")])
    t["icestone_bricks"] = bricks("icestone_bricks", ICE, [hx("8fc1da"), hx("7fb2cb")])
    t["ichorite_bricks"] = bricks("ichorite_bricks", ICH, [hx("4f3722"), hx("402d1c")])
    t["faded_holystone_bricks"] = bricks("faded_holystone_bricks", FADED, [hx("9aa3b3"), hx("8b94a6")])
    t["holystone_flagstones"] = flagstones("holystone_flagstones", HOLY, [HOLY_DARK[0], hx("bcc2cd")])
    t["faded_holystone_flagstones"] = flagstones("faded_holystone_flagstones", FADED, [hx("9aa3b3"), hx("8b94a6")])
    t["holystone_pillar"] = pillar_side("holystone_pillar")
    t["holystone_pillar_top"] = pillar_top("holystone_pillar_top")
    t["holystone_keystone"] = keystone("holystone_keystone")
    t["coarse_aether_dirt"] = coarse_dirt()
    t["aether_dirt_path_top"] = path_top()
    t["aether_dirt_path_side"] = path_side()
    t["aether_farmland_top"] = farmland("aether_farmland_top", False)
    t["aether_farmland_moist"] = farmland("aether_farmland_moist", True)
    t["shimmering_silt"] = sand_like("shimmering_silt", SILT, glint=hx("d4e4f8"))
    t["ferrosite_sand"] = sand_like("ferrosite_sand", FSAND)
    t["ferrosite_mud"] = mud_like("ferrosite_mud", FMUD)
    t["arctic_snow_block"] = speckle(seeded("arctic_snow_block"), SNOW, [5, 3, 2, 1])
    t["arctic_ice"] = arctic_ice("arctic_ice")
    t["fragile_arctic_ice"] = arctic_ice("fragile_arctic_ice", cracks=True)
    t["arctic_packed_ice"] = packed_ice("arctic_packed_ice")
    t["arctic_ice_crystal"] = hanging_ice_crystal()
    t["holystone_quartz_ore"] = ore_on("holystone_quartz_ore", HOLY, [5, 3, 2, 3], QUARTZ_C, HOLY_DARK[0])
    t["arkenium_ore"] = ore_on("arkenium_ore", HOLY, [5, 3, 2, 3], ARK_ORE, HOLY_DARK[0], 5)
    t["glint_ore"] = ore_on("glint_ore", HOLY, [5, 3, 2, 3], GLINT_ORE_C, HOLY_DARK[0], 5)
    t["corrobonite_ore"] = ore_on("corrobonite_ore", UNDER, [5, 3, 2, 2], CORRO, UNDER_DARK, 6)
    t["corrobonite_cluster"] = corrobonite_cluster()
    t["undershale_ambrosium_ore"] = ore_on("undershale_ambrosium_ore", UNDER, [5, 3, 2, 2], AMBROSIUM, UNDER_DARK)
    t["undershale_zanite_ore"] = ore_on("undershale_zanite_ore", UNDER, [5, 3, 2, 2], ZANITE, UNDER_DARK)
    t["undershale_arkenium_ore"] = ore_on("undershale_arkenium_ore", UNDER, [5, 3, 2, 2], ramp("5f86b8", "7fa6d6", "9fc4ee", "e6f4ff"), UNDER_DARK, 5)
    t["undershale_glint_ore"] = ore_on("undershale_glint_ore", UNDER, [5, 3, 2, 2], GLINT_ORE_C, UNDER_DARK, 5)
    t["undershale_gravitite_ore"] = ore_on("undershale_gravitite_ore", UNDER, [5, 3, 2, 2], GRAVITITE, UNDER_DARK)
    t["arkenium_block"] = bordered("arkenium_block", ramp("a9c4e4", "8fb0d8"), ramp("4f6a8c", "46607f", "5a7799", "3d5472"),
                                   ramp("2c3d58", "34476a"), hx("cfe4ff"))
    t["glint_block"] = bordered("glint_block", ramp("a6fff0", "7ff0dc"), ramp("35d4c0", "2fc3b0", "44e0cc", "29b09e"),
                                ramp("187d72", "1e8f82"), hx("e0fffa"))
    t["corrobonite_block"] = bordered("corrobonite_block", ramp("b6f7cf", "8fe8b0"), ramp("3fbf6f", "36ac62", "4fd07f", "2f9a56"),
                                      ramp("1e6b3b", "237a43"), hx("d8ffe6"))
    t["golden_amber_block"] = bordered("golden_amber_block", ramp("ffdf80", "f9c85a"), ramp("e3951c", "d4860f", "f0a830", "c27a0c"),
                                       ramp("8a520a", "a0620d"), hx("ffe9a0"))
    t["gravitite_block"] = bordered("gravitite_block", ramp("f7a8e0", "ea82ca"), ramp("b8409a", "a8358a", "c84daa", "982c7c"),
                                    ramp("70154f", "82205f"), hx("ffc4ee"))
    t["green_aercloud"] = cloud("green_aercloud", ramp("c9f5c3", "a6e89e", "86d67e"), [225, 205, 185])
    t["purple_aercloud"] = cloud("purple_aercloud", ramp("e2c9ff", "cba6f2", "b387e0"), [225, 205, 185])
    t["purple_aercloud_front"] = cloud_front("purple_aercloud_front", ramp("e2c9ff", "cba6f2", "b387e0"), hx("6f3db8"))
    t["storm_aercloud"] = cloud("storm_aercloud", ramp("7c8290", "666c7a", "525866"), [235, 220, 205])
    for dye, rgb in DYES:
        if dye == "white":
            t["cloudwool"] = puffy("cloudwool")
        else:
            t[dye + "_cloudwool"] = puffy(dye + "_cloudwool", rgb)
    t["arkenium_bars"] = steel_bars()
    t["arkenium_chain"] = steel_chain_block()
    t["arkenium_lantern"] = steel_lantern()
    return t


def n1_make_items():
    out = {}
    out["inert_arkenium"] = gem(polygon([(8, 1), (13, 5), (12, 12), (8, 15), (3, 11), (3, 5)]), ARK_GEM, seeded("inert_arkenium"), [(6, 4), (5, 6), (10, 11)])
    out["glint_gemstone"] = gem(polygon([(8, 0), (13, 5), (11, 14), (5, 14), (3, 5)]), GLINT_GEM, seeded("glint_gemstone"), [(6, 4), (7, 3), (6, 6)])
    out["glint_coin"] = coin()
    out["corrobonite_crystal"] = gem(polygon([(8, 0), (11, 4), (11, 11), (8, 15), (5, 11), (5, 4)]), CORRO_GEM, seeded("corrobonite_crystal"), [(7, 3), (6, 5), (7, 6)])
    out["scatterglass_shard"] = gem(polygon([(5, 1), (11, 3), (13, 9), (9, 15), (4, 11), (3, 5)]), SCATTER, seeded("scatterglass_shard"), [(6, 4), (5, 5), (10, 10)])
    out["arkenium_lantern"] = lantern_item()
    out["arkenium_chain"] = chain_item()
    out["holystone_rock"] = pebble_item()
    out["pointed_holystone"] = spike_item()
    return out


# ---- what exists (mirrors AetherBlocks.initStones ... initBuilding)
N1_FAMILIES = [  # id prefix, base block id, display prefix, stone to cut it from as well (bricks)
    ("holystone", "holystone", "Holystone", None), ("mossy_holystone", "mossy_holystone", "Mossy Holystone", None),
    ("holystone_brick", "holystone_bricks", "Holystone Brick", "holystone"),
    ("faded_holystone_brick", "faded_holystone_bricks", "Faded Holystone Brick", None),
    ("undershale", "undershale", "Undershale", None), ("undershale_brick", "undershale_bricks", "Undershale Brick", "undershale"),
    ("agiosite", "agiosite", "Agiosite", None), ("agiosite_brick", "agiosite_bricks", "Agiosite Brick", "agiosite"),
    ("icestone_brick", "icestone_bricks", "Icestone Brick", "icestone"),
    ("ichorite", "ichorite", "Ichorite", None), ("ichorite_brick", "ichorite_bricks", "Ichorite Brick", "ichorite")]
N1_SHAPES = ("stairs", "slab", "wall")
N1_DYED = [d for d, _ in DYES if d != "white"]
# plain cubes (one texture, named like the block)
N1_CUBES = ["undershale", "agiosite", "ichorite", "ferrosite", "irradiated_holystone", "undershale_bricks", "agiosite_bricks", "icestone_bricks",
            "ichorite_bricks", "faded_holystone_bricks", "holystone_flagstones", "faded_holystone_flagstones", "holystone_keystone",
            "coarse_aether_dirt", "arctic_snow_block", "arctic_packed_ice", "ferrosite_mud", "shimmering_silt", "ferrosite_sand",
            "holystone_quartz_ore", "arkenium_ore", "glint_ore", "corrobonite_ore", "undershale_ambrosium_ore", "undershale_zanite_ore",
            "undershale_arkenium_ore", "undershale_glint_ore", "undershale_gravitite_ore", "arkenium_block", "glint_block",
            "corrobonite_block", "golden_amber_block", "gravitite_block", "cloudwool"] + [d + "_cloudwool" for d in N1_DYED]
# cubes drawn through the translucent pass
N1_GLASSY = ["crude_scatterglass", "scatterglass", "arctic_ice", "fragile_arctic_ice", "green_aercloud", "storm_aercloud"]
N1_CARPETS = ["cloudwool_carpet"] + [d + "_cloudwool_carpet" for d in N1_DYED]
N1_OTHER = ["holystone_rock", "pointed_holystone", "holystone_pillar", "holystone_button", "holystone_pressure_plate", "aether_dirt_path",
            "aether_farmland", "arctic_snow", "arctic_ice_crystal", "corrobonite_cluster", "scatterglass_pane", "quicksoil_glass_pane",
            "purple_aercloud", "arkenium_lantern", "arkenium_chain", "arkenium_bars"]
N1_SHAPED = ["%s_%s" % (f[0], sh) for f in N1_FAMILIES for sh in N1_SHAPES]
N1_BLOCKS = N1_CUBES + N1_GLASSY + N1_CARPETS + N1_OTHER + N1_SHAPED
N1_ITEMS = ["inert_arkenium", "glint_gemstone", "glint_coin", "corrobonite_crystal", "scatterglass_shard"]
# blocks whose item is drawn flat from an item texture of its own, or from its block texture
N1_FLAT_ITEM = {"holystone_rock": "item", "pointed_holystone": "item", "arkenium_lantern": "item", "arkenium_chain": "item",
                "arctic_ice_crystal": "block", "corrobonite_cluster": "block", "scatterglass_pane": "block", "quicksoil_glass_pane": "block",
                "arkenium_bars": "block"}

N1_NAME_FIX = {"arkenium": "Arkenium", "undershale": "Undershale", "aether": "Aether"}


def n1_title(i):
    return " ".join(w.capitalize() for w in i.split("_"))


def n1_names():
    names = {}
    for b in N1_BLOCKS:
        names[b] = n1_title(b)
    for f in N1_FAMILIES:
        for sh in N1_SHAPES:
            names["%s_%s" % (f[0], sh)] = "%s %s" % (f[2], sh.capitalize())
    names.update({"holystone_button": "Holystone Button", "holystone_pressure_plate": "Holystone Pressure Plate",
                  "holystone_quartz_ore": "Holystone Quartz Ore", "golden_amber_block": "Block of Golden Amber",
                  "arkenium_block": "Block of Arkenium", "glint_block": "Block of Glint", "corrobonite_block": "Block of Corrobonite",
                  "gravitite_block": "Block of Gravitite", "undershale_gravitite_ore": "Undershale Gravitite Ore"})
    items = {i: n1_title(i) for i in N1_ITEMS}
    items["glint_gemstone"] = "Glint Gemstone"
    return names, items


def n1_textures():
    for name, im in n1_make_blocks().items():
        path = os.path.join(ASSETS, "textures", "block", SUB, name + ".png")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        im.save(path)
    for name, im in n1_make_items().items():
        path = os.path.join(ASSETS, "textures", "item", SUB, name + ".png")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        im.save(path)


def box_faces(frm, to, tex):
    """A cuboid's six faces with the usual automatic UVs."""
    x0, y0, z0 = frm
    x1, y1, z1 = to
    f = {
        "down": {"uv": [x0, z0, x1, z1], "texture": tex},
        "up": {"uv": [x0, z0, x1, z1], "texture": tex},
        "north": {"uv": [16 - x1, 16 - y1, 16 - x0, 16 - y0], "texture": tex},
        "south": {"uv": [x0, 16 - y1, x1, 16 - y0], "texture": tex},
        "west": {"uv": [z0, 16 - y1, z1, 16 - y0], "texture": tex},
        "east": {"uv": [16 - z1, 16 - y1, 16 - z0, 16 - y0], "texture": tex},
    }
    return f


def stairs_state(model_base):
    """The vanilla stairs blockstate: straight, inner and outer corners, every facing and half."""
    base_y = {"east": 0, "south": 90, "west": 180, "north": 270}
    out = {}
    for facing, y in base_y.items():
        for half in ("bottom", "top"):
            for shape in ("straight", "inner_left", "inner_right", "outer_left", "outer_right"):
                model = model_base + ("" if shape == "straight" else "_inner" if shape.startswith("inner") else "_outer")
                if half == "bottom":
                    ry = y if shape in ("straight", "inner_right", "outer_right") else (y - 90) % 360
                    rx = 0
                else:
                    ry = y if shape in ("straight", "inner_left", "outer_left") else (y + 90) % 360
                    rx = 180
                entry = {"model": model}
                if rx:
                    entry["x"] = rx
                if ry:
                    entry["y"] = ry
                if rx or ry:
                    entry["uvlock"] = True
                # key order like vanilla: model, uvlock, x, y
                ordered = {"model": entry["model"]}
                if "uvlock" in entry:
                    ordered["uvlock"] = True
                if "x" in entry:
                    ordered["x"] = entry["x"]
                if "y" in entry:
                    ordered["y"] = entry["y"]
                out["facing=%s,half=%s,shape=%s" % (facing, half, shape)] = ordered
    return out


def wall_state(prefix):
    parts = [{"apply": {"model": prefix + "_post"}, "when": {"up": "true"}}]
    for kind, suffix in (("low", "_side"), ("tall", "_side_tall")):
        for d, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
            apply = {"model": prefix + suffix, "uvlock": True}
            if y:
                apply["y"] = y
            parts.append({"apply": apply, "when": {d: kind}})
    return {"multipart": parts}


def button_state(model, pressed):
    out = {}
    for face, rx in (("floor", 0), ("wall", 90), ("ceiling", 180)):
        for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
            for powered in ("false", "true"):
                if face == "wall":
                    ry = y
                elif face == "floor":
                    ry = y
                else:
                    ry = (y + 180) % 360
                entry = {"model": pressed if powered == "true" else model}
                if face == "wall":
                    entry["uvlock"] = True
                if rx:
                    entry["x"] = rx
                if ry:
                    entry["y"] = ry
                out["face=%s,facing=%s,powered=%s" % (face, facing, powered)] = entry
    return out


def n1_assets():
    bs = os.path.join(ASSETS, "blockstates")
    mdl = lambda name: "%s:block/%s/%s" % (NS, SUB, name)
    state = lambda model, **rot: dict({"model": mdl(model)}, **rot)
    glassy = lambda t, n=None: {"force_translucent": True, "sprite": tex(n or t)}

    def bstate(name, data):
        write(os.path.join(bs, name + ".json"), data)

    def item_def(name, model):
        write(os.path.join(ASSETS, "items", name + ".json"), {"model": {"type": "minecraft:model", "model": model}})

    # cubes
    for b in N1_CUBES:
        block_model(b, {"parent": mc("block/cube_all"), "textures": {"all": tex(b)}})
        bstate(b, {"variants": {"": state(b)}})
        item_def(b, mdl(b))
    for b in N1_GLASSY:
        block_model(b, {"parent": mc("block/cube_all"), "textures": {"all": glassy(b)}})
        bstate(b, {"variants": {"": state(b)}})
        item_def(b, mdl(b))
    # carpets
    for b in N1_CARPETS:
        block_model(b, {"parent": mc("block/carpet"), "textures": {"wool": tex(b.replace("_carpet", ""))}})
        bstate(b, {"variants": {"": state(b)}})
        item_def(b, mdl(b))
    # the pillar
    block_model("holystone_pillar", {"parent": mc("block/cube_column"), "textures": {"end": tex("holystone_pillar_top"), "side": tex("holystone_pillar")}})
    block_model("holystone_pillar_horizontal", {"parent": mc("block/cube_column_horizontal"),
                                                "textures": {"end": tex("holystone_pillar_top"), "side": tex("holystone_pillar")}})
    bstate("holystone_pillar", {"variants": {"axis=x": state("holystone_pillar_horizontal", x=90, y=90), "axis=y": state("holystone_pillar"),
                                             "axis=z": state("holystone_pillar_horizontal", x=90)}})
    item_def("holystone_pillar", mdl("holystone_pillar"))
    # button and plate (stone ones; holystone texture)
    block_model("holystone_button", {"parent": mc("block/button"), "textures": {"texture": tex("holystone")}})
    block_model("holystone_button_pressed", {"parent": mc("block/button_pressed"), "textures": {"texture": tex("holystone")}})
    block_model("holystone_button_inventory", {"parent": mc("block/button_inventory"), "textures": {"texture": tex("holystone")}})
    bstate("holystone_button", {"variants": button_state(mdl("holystone_button"), mdl("holystone_button_pressed"))})
    item_def("holystone_button", mdl("holystone_button_inventory"))
    block_model("holystone_pressure_plate", {"parent": mc("block/pressure_plate_up"), "textures": {"texture": tex("holystone")}})
    block_model("holystone_pressure_plate_down", {"parent": mc("block/pressure_plate_down"), "textures": {"texture": tex("holystone")}})
    bstate("holystone_pressure_plate", {"variants": {"powered=false": state("holystone_pressure_plate"), "powered=true": state("holystone_pressure_plate_down")}})
    item_def("holystone_pressure_plate", mdl("holystone_pressure_plate"))
    # stairs, slabs, walls
    for prefix, base, _, _ in N1_FAMILIES:
        t = {"bottom": tex(base), "side": tex(base), "top": tex(base)}
        block_model(prefix + "_stairs", {"parent": mc("block/stairs"), "textures": t})
        block_model(prefix + "_stairs_inner", {"parent": mc("block/inner_stairs"), "textures": t})
        block_model(prefix + "_stairs_outer", {"parent": mc("block/outer_stairs"), "textures": t})
        bstate(prefix + "_stairs", {"variants": stairs_state(mdl(prefix + "_stairs"))})
        item_def(prefix + "_stairs", mdl(prefix + "_stairs"))
        block_model(prefix + "_slab", {"parent": mc("block/slab"), "textures": t})
        block_model(prefix + "_slab_top", {"parent": mc("block/slab_top"), "textures": t})
        bstate(prefix + "_slab", {"variants": {"type=bottom": state(prefix + "_slab"), "type=double": state(base), "type=top": state(prefix + "_slab_top")}})
        item_def(prefix + "_slab", mdl(prefix + "_slab"))
        w = {"wall": tex(base)}
        block_model(prefix + "_wall_post", {"parent": mc("block/template_wall_post"), "textures": w})
        block_model(prefix + "_wall_side", {"parent": mc("block/template_wall_side"), "textures": w})
        block_model(prefix + "_wall_side_tall", {"parent": mc("block/template_wall_side_tall"), "textures": w})
        block_model(prefix + "_wall_inventory", {"parent": mc("block/wall_inventory"), "textures": w})
        bstate(prefix + "_wall", wall_state(mdl(prefix + "_wall")))
        item_def(prefix + "_wall", mdl(prefix + "_wall_inventory"))
    # soils
    block_model("aether_dirt_path", {"parent": mc("block/template_cube_bottom_top_indented"),
                                     "textures": {"bottom": tex("aether_dirt"), "side": tex("aether_dirt_path_side"), "top": tex("aether_dirt_path_top")}})
    bstate("aether_dirt_path", {"variants": {"": [state("aether_dirt_path"), state("aether_dirt_path", y=90), state("aether_dirt_path", y=180),
                                                  state("aether_dirt_path", y=270)]}})
    item_def("aether_dirt_path", mdl("aether_dirt_path"))
    for m, top in (("aether_farmland", "aether_farmland_top"), ("aether_farmland_moist", "aether_farmland_moist")):
        block_model(m, {"parent": mc("block/template_cube_bottom_top_indented"),
                        "textures": {"bottom": tex("aether_dirt"), "side": tex("aether_dirt"), "top": tex(top)}})
    bstate("aether_farmland", {"variants": dict({"moisture=%d" % i: state("aether_farmland") for i in range(7)}, **{"moisture=7": state("aether_farmland_moist")})})
    item_def("aether_farmland", mdl("aether_farmland"))
    # arctic snow layers (one to seven; eight is the snow block)
    snow = {}
    for n in range(1, 8):
        h = 2 * n
        faces = {"down": {"uv": [0, 0, 16, 16], "texture": "#texture", "cullface": "down"}, "up": {"uv": [0, 0, 16, 16], "texture": "#texture"}}
        for side in ("north", "south", "west", "east"):
            faces[side] = {"uv": [0, 16 - h, 16, 16], "texture": "#texture", "cullface": side}
        block_model("arctic_snow_height%d" % h, {"parent": mc("block/block"), "textures": {"particle": tex("arctic_snow_block"), "texture": tex("arctic_snow_block")},
                                                "elements": [{"from": [0, 0, 0], "to": [16, h, 16], "faces": faces}]})
        snow["layers=%d" % n] = state("arctic_snow_height%d" % h)
    snow["layers=8"] = state("arctic_snow_block")
    bstate("arctic_snow", {"variants": snow})
    item_def("arctic_snow", mdl("arctic_snow_height2"))
    # the hanging crystal and the cluster (cross models; the crystal is drawn through the translucent pass)
    block_model("arctic_ice_crystal", {"parent": mc("block/cross"), "textures": {"cross": tex("arctic_ice_crystal")}})
    bstate("arctic_ice_crystal", {"variants": {"": state("arctic_ice_crystal")}})
    block_model("corrobonite_cluster", {"parent": mc("block/cross"), "textures": {"cross": tex("corrobonite_cluster")}})
    bstate("corrobonite_cluster", {"variants": {"facing=down": state("corrobonite_cluster", x=180), "facing=east": state("corrobonite_cluster", x=90, y=90),
                                                "facing=north": state("corrobonite_cluster", x=90), "facing=south": state("corrobonite_cluster", x=90, y=180),
                                                "facing=up": state("corrobonite_cluster"), "facing=west": state("corrobonite_cluster", x=90, y=270)}})
    # holystone rock (three pebbles) and pointed holystone (a stepped cone), custom elements on the holystone texture
    pebbles = [([3, 0, 4], [8, 3, 9]), ([8, 0, 7], [13, 2.5, 12]), ([5.5, 0, 9], [9.5, 2, 12.5]), ([4.5, 3, 5], [7.5, 4, 8])]
    block_model("holystone_rock", {"parent": mc("block/block"), "textures": {"particle": tex("holystone"), "all": tex("holystone")},
                                   "elements": [{"from": a, "to": b, "faces": box_faces(a, b, "#all")} for a, b in pebbles]})
    bstate("holystone_rock", {"variants": {"": [state("holystone_rock"), state("holystone_rock", y=90), state("holystone_rock", y=180), state("holystone_rock", y=270)]}})
    cone = [([3, 0, 3], [13, 4, 13]), ([4, 4, 4], [12, 8, 12]), ([5.5, 8, 5.5], [10.5, 12, 10.5]), ([7, 12, 7], [9, 16, 9])]
    block_model("pointed_holystone", {"parent": mc("block/block"), "textures": {"particle": tex("holystone"), "all": tex("holystone")},
                                      "elements": [{"from": a, "to": b, "faces": box_faces(a, b, "#all")} for a, b in cone]})
    bstate("pointed_holystone", {"variants": {"": state("pointed_holystone")}})
    # panes
    for pane, glass, top in (("scatterglass_pane", "scatterglass", "scatterglass_pane_top"), ("quicksoil_glass_pane", "quicksoil_glass", "quicksoil_glass_pane_top")):
        t = {"edge": glassy(top), "pane": glassy(glass)}
        for part in ("post", "side", "side_alt", "noside", "noside_alt"):
            block_model("%s_%s" % (pane, part), {"parent": mc("block/template_glass_pane_" + part), "textures": t})
        bstate(pane, pane_state(pane, mdl))
    # bars, chain, lantern
    t = {"bars": tex("arkenium_bars"), "edge": tex("arkenium_bars")}
    for part in ("post_ends", "post", "cap", "cap_alt", "side", "side_alt"):
        block_model("arkenium_bars_" + part, {"parent": mc("block/template_bars_" + part), "textures": t})
    bstate("arkenium_bars", bars_state(mdl))
    block_model("arkenium_chain", {"parent": mc("block/template_chain"), "textures": {"texture": tex("arkenium_chain")}})
    bstate("arkenium_chain", {"variants": {"axis=x": state("arkenium_chain", x=90, y=90), "axis=y": state("arkenium_chain"), "axis=z": state("arkenium_chain", x=90)}})
    block_model("arkenium_lantern", {"parent": mc("block/template_lantern"), "textures": {"lantern": tex("arkenium_lantern")}})
    block_model("arkenium_lantern_hanging", {"parent": mc("block/template_hanging_lantern"), "textures": {"lantern": tex("arkenium_lantern")}})
    bstate("arkenium_lantern", {"variants": {"hanging=false": state("arkenium_lantern"), "hanging=true": state("arkenium_lantern_hanging")}})
    # purple aercloud: the front face (where it flings) shows an arrow; north is the model's front
    front, other = glassy("purple_aercloud_front"), glassy("purple_aercloud")
    faces = {d: {"uv": [0, 0, 16, 16], "texture": "#front" if d == "north" else "#all"} for d in ("down", "up", "north", "south", "west", "east")}
    for d in faces:
        faces[d]["cullface"] = d
    block_model("purple_aercloud", {"parent": mc("block/block"), "textures": {"particle": other, "all": other, "front": front},
                                    "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": faces}]})
    bstate("purple_aercloud", {"variants": {"facing=north": state("purple_aercloud"), "facing=east": state("purple_aercloud", y=90),
                                            "facing=south": state("purple_aercloud", y=180), "facing=west": state("purple_aercloud", y=270),
                                            "facing=up": state("purple_aercloud", x=270), "facing=down": state("purple_aercloud", x=90)}})
    # items: flat ones
    for b in ("holystone_rock", "pointed_holystone", "arkenium_lantern", "arkenium_chain", "arctic_ice_crystal", "corrobonite_cluster", "scatterglass_pane",
              "quicksoil_glass_pane", "arkenium_bars"):
        kind = N1_FLAT_ITEM[b]
        src = tex(b, "item") if kind == "item" else tex({"scatterglass_pane": "scatterglass", "quicksoil_glass_pane": "quicksoil_glass"}.get(b, b))
        write(os.path.join(ASSETS, "models", "item", SUB, b + ".json"), {"parent": mc("item/generated"), "textures": {"layer0": src}})
        item_def(b, "%s:item/%s/%s" % (NS, SUB, b))
    item_def("purple_aercloud", mdl("purple_aercloud"))
    # items of the new materials
    for i in N1_ITEMS:
        write(os.path.join(ASSETS, "models", "item", SUB, i + ".json"), {"parent": mc("item/generated"), "textures": {"layer0": tex(i, "item")}})
        item_def(i, "%s:item/%s/%s" % (NS, SUB, i))


def pane_state(pane, mdl):
    m = lambda part: mdl("%s_%s" % (pane, part))
    return {"multipart": [
        {"apply": {"model": m("post")}},
        {"apply": {"model": m("side")}, "when": {"north": "true"}},
        {"apply": {"model": m("side"), "y": 90}, "when": {"east": "true"}},
        {"apply": {"model": m("side_alt")}, "when": {"south": "true"}},
        {"apply": {"model": m("side_alt"), "y": 90}, "when": {"west": "true"}},
        {"apply": {"model": m("noside")}, "when": {"north": "false"}},
        {"apply": {"model": m("noside_alt")}, "when": {"east": "false"}},
        {"apply": {"model": m("noside_alt"), "y": 90}, "when": {"south": "false"}},
        {"apply": {"model": m("noside"), "y": 270}, "when": {"west": "false"}}]}


def bars_state(mdl):
    m = lambda part: mdl("arkenium_bars_" + part)
    parts = [{"apply": {"model": m("post_ends")}},
             {"apply": {"model": m("post")}, "when": {"east": "false", "north": "false", "south": "false", "west": "false"}},
             {"apply": {"model": m("cap")}, "when": {"east": "false", "north": "true", "south": "false", "west": "false"}},
             {"apply": {"model": m("cap"), "y": 90}, "when": {"east": "true", "north": "false", "south": "false", "west": "false"}},
             {"apply": {"model": m("cap_alt")}, "when": {"east": "false", "north": "false", "south": "true", "west": "false"}},
             {"apply": {"model": m("cap_alt"), "y": 90}, "when": {"east": "false", "north": "false", "south": "false", "west": "true"}},
             {"apply": {"model": m("side")}, "when": {"north": "true"}},
             {"apply": {"model": m("side"), "y": 90}, "when": {"east": "true"}},
             {"apply": {"model": m("side_alt")}, "when": {"south": "true"}},
             {"apply": {"model": m("side_alt"), "y": 90}, "when": {"west": "true"}}]
    return {"multipart": parts}


def n1_lang():
    names, items = n1_names()
    lang = {}
    for b, n in names.items():
        lang["block.jaymospowers." + b] = n
    for i, n in items.items():
        lang["item.jaymospowers." + i] = n
    return lang



N1_ORES = {  # ore block -> (what it drops, bonus modifiers) when not mined with Silk Touch
    "holystone_quartz_ore": mc("quartz"), "arkenium_ore": jp("inert_arkenium"), "glint_ore": jp("glint_gemstone"),
    "corrobonite_ore": jp("corrobonite_crystal"), "undershale_ambrosium_ore": jp("ambrosium_shard"),
    "undershale_zanite_ore": jp("zanite_gemstone"), "undershale_arkenium_ore": jp("inert_arkenium"), "undershale_glint_ore": jp("glint_gemstone")}
N1_SILK_ONLY = ["scatterglass", "scatterglass_pane", "quicksoil_glass_pane", "arctic_ice", "fragile_arctic_ice", "arctic_packed_ice"]
N1_SOIL_DROPS_DIRT = ["aether_dirt_path", "aether_farmland"]


def n1_loot():
    out = os.path.join(DATA, "loot_table", "blocks")
    tables = {}
    special = set(N1_ORES) | set(N1_SILK_ONLY) | set(N1_SOIL_DROPS_DIRT) | {"crude_scatterglass", "arctic_snow", "corrobonite_cluster"}
    shaped_slabs = ["%s_slab" % f[0] for f in N1_FAMILIES]
    for b in N1_BLOCKS:
        if b in special or b in shaped_slabs:
            continue
        tables[b] = self_table(b)
    for b, drop in N1_ORES.items():
        tables[b] = silk_else(b, drop, ORE_BONUS)
    for b in N1_SILK_ONLY:
        tables[b] = table(b, [{"rolls": 1, "condition": SILK, "entries": [item_entry(jp(b))]}])
    for b in N1_SOIL_DROPS_DIRT:
        tables[b] = table(b, [{"rolls": 1, "condition": SURVIVES, "entries": [item_entry(jp("aether_dirt"))]}])
    tables["crude_scatterglass"] = silk_else("crude_scatterglass", jp("scatterglass_shard"), [
        {"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 2, "max": 4}},
        {"type": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:uniform_bonus_count", "parameters": {"bonusMultiplier": 1}},
        {"type": "minecraft:explosion_decay"}])
    tables["corrobonite_cluster"] = table("corrobonite_cluster", [{"rolls": 1, "condition": SURVIVES, "entries": [item_entry(jp("corrobonite_crystal"))]}])
    # a double slab gives two; layers of snow give as many snow layers as they are
    for b in shaped_slabs:
        double = {"type": "minecraft:match_block", "blocks": jp(b), "state": {"type": "double"}}
        tables[b] = table(b, [{"rolls": 1, "entries": [{"type": "minecraft:alternatives", "children": [
            item_entry(jp(b), condition=double, modifier=[{"type": "minecraft:set_count", "count": 2, "add": False}, {"type": "minecraft:explosion_decay"}]),
            item_entry(jp(b), condition=SURVIVES)]}]}])
    kids = []
    for n in range(1, 9):
        kids.append(item_entry(jp("arctic_snow"), condition={"type": "minecraft:match_block", "blocks": jp("arctic_snow"), "state": {"layers": str(n)}},
                               modifier=[{"type": "minecraft:set_count", "count": n, "add": False}, {"type": "minecraft:explosion_decay"}]))
    tables["arctic_snow"] = table("arctic_snow", [{"rolls": 1, "entries": [{"type": "minecraft:alternatives", "children": kids}]}])
    missing = [b for b in N1_BLOCKS if b not in tables]
    assert not missing, missing
    for b, t in tables.items():
        write(os.path.join(out, b + ".json"), t)


def n1_recipes():
    out = os.path.join(DATA, "recipe", SUB)
    shaped = lambda key, pattern, result, count=1, group=None, cat="building": dict(
        {"type": "minecraft:crafting_shaped", "category": cat, "key": key, "pattern": pattern,
         "result": {"id": result, "count": count} if count > 1 else {"id": result}}, **({"group": group} if group else {}))
    shapeless = lambda ings, result, count=1, group=None, cat="misc": dict(
        {"type": "minecraft:crafting_shapeless", "category": cat, "ingredients": ings,
         "result": {"id": result, "count": count} if count > 1 else {"id": result}}, **({"group": group} if group else {}))
    smelt = lambda ing, result, xp: {"type": "minecraft:smelting", "category": "blocks", "cookingtime": 200, "experience": xp,
                                     "ingredient": ing, "result": {"id": result}}
    cut = lambda ing, result, count=1: {"type": "minecraft:stonecutting", "ingredient": ing, "result": {"id": result, "count": count} if count > 1 else {"id": result}}
    r = {}

    def cutting(source, result, count=1):
        r["%s_from_%s_stonecutting" % (result, source)] = cut(jp(source), jp(result), count)

    for prefix, base, _, root in N1_FAMILIES:
        b = jp(base)
        r[prefix + "_stairs"] = shaped({"#": b}, ["#  ", "## ", "###"], jp(prefix + "_stairs"), 4)
        r[prefix + "_slab"] = shaped({"#": b}, ["###"], jp(prefix + "_slab"), 6)
        r[prefix + "_wall"] = shaped({"#": b}, ["###", "###"], jp(prefix + "_wall"), 6)
        for src in [base] + ([root] if root else []):
            cutting(src, prefix + "_stairs")
            cutting(src, prefix + "_slab", 2)
            cutting(src, prefix + "_wall")
        if root:
            cutting(root, base)
    for base, result in (("undershale", "undershale_bricks"), ("agiosite", "agiosite_bricks"), ("ichorite", "ichorite_bricks"), ("icestone", "icestone_bricks")):
        r[result] = shaped({"#": jp(base)}, ["##", "##"], jp(result), 4)
    r["faded_holystone_bricks"] = shapeless([jp("holystone_bricks")] * 4 + [mc("bone_meal")], jp("faded_holystone_bricks"), 4, None, "building")
    r["holystone_flagstones"] = shaped({"#": jp("holystone_bricks")}, ["##", "##"], jp("holystone_flagstones"), 4)
    r["faded_holystone_flagstones"] = shaped({"#": jp("faded_holystone_bricks")}, ["##", "##"], jp("faded_holystone_flagstones"), 4)
    r["holystone_pillar"] = shaped({"#": jp("holystone")}, ["#", "#"], jp("holystone_pillar"), 2)
    r["holystone_keystone"] = shaped({"#": jp("holystone_brick_slab")}, ["#", "#"], jp("holystone_keystone"))
    cutting("holystone", "holystone_flagstones")
    cutting("holystone_bricks", "holystone_flagstones")
    cutting("faded_holystone_bricks", "faded_holystone_flagstones")
    cutting("holystone", "holystone_pillar")
    cutting("holystone_bricks", "holystone_keystone")
    r["holystone_button"] = shapeless([jp("holystone")], jp("holystone_button"), 1, None, "redstone")
    r["holystone_pressure_plate"] = shaped({"#": jp("holystone")}, ["##"], jp("holystone_pressure_plate"), 1, None, "redstone")
    # glass
    r["scatterglass_from_smelting"] = smelt(jp("crude_scatterglass"), jp("scatterglass"), 0.1)
    r["scatterglass_from_shards"] = shaped({"#": jp("scatterglass_shard")}, ["##", "##"], jp("scatterglass"))
    r["scatterglass_pane"] = shaped({"#": jp("scatterglass")}, ["###", "###"], jp("scatterglass_pane"), 16)
    r["quicksoil_glass_pane"] = shaped({"#": jp("quicksoil_glass")}, ["###", "###"], jp("quicksoil_glass_pane"), 16)
    # the materials and their blocks
    for block, item in (("arkenium_block", "inert_arkenium"), ("glint_block", "glint_gemstone"), ("corrobonite_block", "corrobonite_crystal"),
                        ("golden_amber_block", "golden_amber")):
        r[block] = shaped({"#": jp(item)}, ["###", "###", "###"], jp(block))
        r[item + "_from_block"] = shapeless([jp(block)], jp(item), 9, item)
    r["gravitite_block"] = shaped({"#": jp("enchanted_gravitite")}, ["##", "##"], jp("gravitite_block"))
    r["glint_coin_from_gemstone"] = shapeless([jp("glint_gemstone")], jp("glint_coin"), 4)
    r["glint_gemstone_from_coins"] = shapeless([jp("glint_coin")] * 4, jp("glint_gemstone"), 1)
    # cloudwool
    r["cloudwool"] = shaped({"#": jp("cold_aercloud")}, ["##", "##"], jp("cloudwool"), 2, "wool")
    r["cloudwool_carpet"] = shaped({"#": jp("cloudwool")}, ["##"], jp("cloudwool_carpet"), 3, "carpet")
    for dye in N1_DYED:
        r[dye + "_cloudwool"] = shapeless([jp("cloudwool"), mc(dye + "_dye")], jp(dye + "_cloudwool"), 1, "wool", "building")
        r[dye + "_cloudwool_carpet"] = shaped({"#": jp(dye + "_cloudwool")}, ["##"], jp(dye + "_cloudwool_carpet"), 3, "carpet")
    # arkenium metalwork
    r["arkenium_lantern"] = shaped({"#": jp("inert_arkenium"), "T": jp("ambrosium_torch")}, [" # ", "#T#", " # "], jp("arkenium_lantern"), 1, None, "misc")
    r["arkenium_chain"] = shaped({"#": jp("inert_arkenium")}, ["#", "#"], jp("arkenium_chain"), 3, None, "misc")
    r["arkenium_bars"] = shaped({"#": jp("inert_arkenium")}, ["###", "###"], jp("arkenium_bars"), 16, None, "misc")
    for name, data in r.items():
        write(os.path.join(out, name + ".json"), data)


def n1_tags():
    b = os.path.join(MCDATA, "tags", "block")
    i = os.path.join(MCDATA, "tags", "item")
    jb = os.path.join(DATA, "tags", "block")
    ids = lambda names: [jp(n) for n in names]
    shaped = N1_SHAPED
    stairs = ["%s_stairs" % f[0] for f in N1_FAMILIES]
    slabs = ["%s_slab" % f[0] for f in N1_FAMILIES]
    walls = ["%s_wall" % f[0] for f in N1_FAMILIES]
    stones = ["undershale", "agiosite", "ichorite", "ferrosite", "irradiated_holystone", "crude_scatterglass", "holystone_rock", "pointed_holystone",
              "undershale_bricks", "agiosite_bricks", "icestone_bricks", "ichorite_bricks", "faded_holystone_bricks", "holystone_flagstones",
              "faded_holystone_flagstones", "holystone_pillar", "holystone_keystone", "holystone_button", "holystone_pressure_plate"]
    ores = list(N1_ORES) + ["undershale_gravitite_ore", "corrobonite_cluster"]
    storage = ["arkenium_block", "glint_block", "corrobonite_block", "golden_amber_block", "gravitite_block"]
    ices = ["arctic_ice", "fragile_arctic_ice", "arctic_packed_ice", "arctic_ice_crystal"]
    metal = ["arkenium_lantern", "arkenium_chain", "arkenium_bars"]
    shovel = ["coarse_aether_dirt", "aether_dirt_path", "aether_farmland", "shimmering_silt", "ferrosite_sand", "ferrosite_mud", "arctic_snow_block", "arctic_snow"]
    merge_tag(os.path.join(b, "mineable", "pickaxe.json"), ids(stones + shaped + ores + storage + ices + metal))
    merge_tag(os.path.join(b, "mineable", "shovel.json"), ids(shovel))
    merge_tag(os.path.join(b, "needs_stone_tool.json"), ids(["arkenium_ore", "glint_ore", "undershale_arkenium_ore", "undershale_glint_ore", "undershale_zanite_ore"]))
    merge_tag(os.path.join(b, "needs_iron_tool.json"), ids(["corrobonite_ore", "undershale_gravitite_ore"]))
    for root in (b, i):
        merge_tag(os.path.join(root, "stairs.json"), ids(stairs))
        merge_tag(os.path.join(root, "slabs.json"), ids(slabs))
        merge_tag(os.path.join(root, "walls.json"), ids(walls))
        merge_tag(os.path.join(root, "stone_buttons.json"), [jp("holystone_button")])
        merge_tag(os.path.join(root, "stone_pressure_plates.json"), [jp("holystone_pressure_plate")])
        merge_tag(os.path.join(root, "sand.json"), ids(["shimmering_silt", "ferrosite_sand"]))
        merge_tag(os.path.join(root, "wool.json"), ids(["cloudwool"] + [d + "_cloudwool" for d in N1_DYED]))
        merge_tag(os.path.join(root, "wool_carpets.json"), ids(N1_CARPETS))
        merge_tag(os.path.join(root, "dirt.json"), [jp("coarse_aether_dirt")])
    for tag in ("ice", "cannot_support_snow_layer"):
        merge_tag(os.path.join(b, tag + ".json"), ids(["arctic_ice", "fragile_arctic_ice", "arctic_packed_ice"]))
    merge_tag(os.path.join(b, "snow.json"), ids(["arctic_snow_block", "arctic_snow"]))
    merge_tag(os.path.join(b, "mud.json"), [jp("ferrosite_mud")])
    for tag in ("supports_vegetation", "supports_crops", "grows_crops"):
        merge_tag(os.path.join(b, tag + ".json"), [jp("aether_farmland")])
    merge_tag(os.path.join(b, "bars.json"), [jp("arkenium_bars")])
    merge_tag(os.path.join(b, "chains.json"), [jp("arkenium_chain")])
    merge_tag(os.path.join(b, "lanterns.json"), [jp("arkenium_lantern")])
    write(os.path.join(jb, "aether_undershale_ore_replaceables.json"), {"values": [jp("undershale")]})


def n1_main():
    n1_textures()
    n1_assets()
    n1_loot()
    n1_recipes()
    n1_tags()


if __name__ == "__main__":
    textures()
    assets()
    loot()
    recipes()
    tags()
    n1_main()
    print("Aether assets and data written.")
