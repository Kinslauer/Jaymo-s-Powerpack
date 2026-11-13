"""The tundra's blocks and plants (com.jaymospowers.tundra): textures painted from nothing here, our own models,
blockstates, item definitions, loot tables, recipes and tags. Nothing of anyone's pack is copied or read.

Textures that have a counterpart in Klei_Wright's tundra pack carry his file names under minecraft:block/ (the dwarfed
spruce ends, the shrubs, yellow moss, white ice, smooth_basalt_iron_ore, glow_lichen_ground*, glow_lichen_ceiling*,
item/glow_ink_sac), so a resource pack can replace ours by path. The rest are under jaymospowers:block/tundra/.
The dwarfed spruce's bark is the game's own spruce log texture, by reference. Seeded, so reruns give the same art.

    python3 -I tools/gen_tundra.py
"""
import json
import math
import os
import random

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
ASSETS = os.path.join(ROOT, "assets", "jaymospowers")
MC_ASSETS = os.path.join(ROOT, "assets", "minecraft")
DATA = os.path.join(ROOT, "data")
NS = "jaymospowers"

# ---- tuning (data side) ----
# raw iron from one banded iron ore (plain iron ore gives 1); Fortune multiplies it as it does any ore
BANDED_IRON_MIN, BANDED_IRON_MAX = 2, 4
# glow lichen of any kind smelts into one phosphor (the glow ink sac)
PHOSPHOR_XP = 0.1

LOGS = ["dwarfed_spruce_log", "dwarfed_spruce_post", "stripped_dwarfed_spruce_log", "stripped_dwarfed_spruce_post"]
SHRUBS = ["stunted_red_shrub", "orange_shrub", "yellow_shrub"]
LICHENS = ["sprouted_glow_lichen", "snipped_glow_lichen"]
FACES = ["north", "east", "south", "west", "up", "down"]


def jp(name):
    return "%s:%s" % (NS, name)


def mc(name):
    return "minecraft:" + name


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def merge_tag(path, values):
    """Adds `values` to a tag file, keeping what's there."""
    current = []
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            current = json.load(f).get("values", [])
    for v in values:
        if v not in current:
            current.append(v)
    write(path, {"values": current})


def save_mc(im, kind, name):
    path = os.path.join(MC_ASSETS, "textures", kind, name + ".png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im.save(path)


def save_own(im, name):
    path = os.path.join(ASSETS, "textures", "block", "tundra", name + ".png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im.save(path)


def clamp(c):
    return tuple(max(0, min(255, int(round(v)))) for v in c)


def shade(c, k):
    return clamp([v * k for v in c[:3]])


def mix(a, b, t):
    return clamp([a[i] * (1 - t) + b[i] * t for i in range(3)])


def blank():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def value_noise(rng, cells=4):
    """A 16x16 field of smooth noise in 0..1 that tiles."""
    grid = [[rng.random() for _ in range(cells)] for _ in range(cells)]
    out = [[0.0] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            fx, fy = x * cells / 16.0, y * cells / 16.0
            x0, y0 = int(fx) % cells, int(fy) % cells
            x1, y1 = (x0 + 1) % cells, (y0 + 1) % cells
            tx, ty = fx - int(fx), fy - int(fy)
            tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
            top = grid[y0][x0] * (1 - tx) + grid[y0][x1] * tx
            bot = grid[y1][x0] * (1 - tx) + grid[y1][x1] * tx
            out[y][x] = top * (1 - ty) + bot * ty
    return out


# ---------------------------------------------------------------- palettes

BASALT = [(74, 73, 80), (66, 65, 72), (82, 81, 88), (58, 57, 64)]
BAND_PALE = (214, 196, 186)
BAND_RUST = (176, 122, 98)
BAND_SHADOW = (52, 50, 58)

BARK = [(58, 38, 18), (46, 30, 14), (68, 46, 24)]
SPRUCE_WOOD = [(122, 90, 52), (106, 78, 44), (136, 102, 60)]
STRIPPED_WOOD = [(120, 94, 56), (132, 104, 62), (108, 84, 50)]
STRIPPED_RIM = (146, 116, 70)

MOSS = [(190, 172, 62), (206, 190, 78), (166, 148, 50), (144, 126, 42), (220, 206, 98)]

ICE_WHITE = [(234, 241, 247), (226, 235, 243), (242, 247, 251)]
ICE_VEIN = (252, 254, 255)
ICE_BLUE = (204, 219, 233)

LICHEN = {"lo": (70, 104, 100), "mid": (94, 136, 122), "hi": (132, 172, 146), "glow": (184, 222, 178)}
SPROUT = {"stalk": (150, 188, 120), "bulb": (232, 240, 150), "bulb_hi": (250, 252, 208)}
SNIP = {"lo": (62, 92, 90), "mid": (84, 120, 110), "cut": (198, 208, 176)}

TWIG = [(74, 52, 34), (58, 40, 26)]
RED_LEAF = [(150, 38, 34), (118, 26, 28), (184, 62, 44)]
ORANGE_LEAF = [(214, 122, 36), (178, 92, 26), (236, 158, 60)]
YELLOW_LEAF = [(222, 190, 52), (186, 152, 36), (240, 216, 96)]


# ---------------------------------------------------------------- full blocks

def banded_iron_ore():
    """Smooth dark basalt with three wavering bands of pale and rusty iron stone running through it."""
    rng = random.Random("tundra-banded-iron")
    noise = value_noise(rng)
    im = blank()
    px = im.load()
    for y in range(16):
        for x in range(16):
            n = noise[y][x]
            c = BASALT[0] if n < 0.45 else BASALT[1] if n < 0.62 else BASALT[2] if n < 0.85 else BASALT[3]
            if rng.random() < 0.06:
                c = shade(c, 0.9)
            px[x, y] = c + (255,)
    for base, thick in ((2, 1), (6, 2), (11, 2)):
        drift, gap = 0, -1
        for x in range(16):
            if rng.random() < 0.3:
                drift = max(-1, min(1, drift + rng.choice((-1, 1))))
            if gap < 0 and rng.random() < 0.12:
                gap = rng.randrange(1, 3)
            if gap >= 0:
                gap -= 1
                continue
            top = base + drift
            for i in range(thick):
                y = (top + i) % 16
                if i == 0:
                    c = BAND_PALE if rng.random() < 0.8 else mix(BAND_PALE, (255, 255, 255), 0.4)
                elif i == thick - 1 and thick > 2:
                    c = mix(BAND_RUST, BAND_PALE, 0.25)
                else:
                    c = BAND_RUST if rng.random() < 0.75 else shade(BAND_RUST, 0.85)
                px[x, y] = c + (255,)
            if rng.random() < 0.7:
                px[x, (top + thick) % 16] = BAND_SHADOW + (255,)
    return im


def yellow_moss():
    """Clumpy mustard-yellow moss: soft clumps, darker gaps between them, a few bright tips."""
    rng = random.Random("tundra-yellow-moss")
    big, small = value_noise(rng, 4), value_noise(rng, 8)
    im = blank()
    px = im.load()
    for y in range(16):
        for x in range(16):
            n = big[y][x] * 0.6 + small[y][x] * 0.4
            c = MOSS[3] if n < 0.34 else MOSS[2] if n < 0.46 else MOSS[0] if n < 0.6 else MOSS[1]
            if n > 0.56 and rng.random() < 0.12:
                c = MOSS[4]
            px[x, y] = c + (255,)
    return im


def white_ice():
    """Opaque, milky ice: near-white with long faint veins and a few blue-grey flaws."""
    rng = random.Random("tundra-white-ice")
    noise = value_noise(rng, 4)
    im = blank()
    px = im.load()
    for y in range(16):
        for x in range(16):
            n = noise[y][x]
            px[x, y] = (ICE_WHITE[1] if n < 0.4 else ICE_WHITE[0] if n < 0.7 else ICE_WHITE[2]) + (255,)
    for _ in range(3):
        x, y = rng.randrange(16), rng.randrange(16)
        for _ in range(rng.randrange(6, 11)):
            px[x % 16, y % 16] = ICE_VEIN + (255,)
            px[x % 16, (y + 1) % 16] = ICE_BLUE + (255,)
            x += 1
            if rng.random() < 0.45:
                y += rng.choice((-1, 1))
    for _ in range(5):
        px[rng.randrange(16), rng.randrange(16)] = ICE_BLUE + (255,)
    return im


def log_end(width, stripped):
    """The cut end of a dwarfed trunk, drawn in the middle `width` pixels (the model shows only those); the rest is filled
    with the rim colour so the sprite has no holes."""
    rng = random.Random("tundra-log-end:%d:%s" % (width, stripped))
    wood = STRIPPED_WOOD if stripped else SPRUCE_WOOD
    im = blank()
    px = im.load()
    lo, hi = 8 - width // 2, 8 + width // 2 - 1
    for y in range(16):
        for x in range(16):
            # how many pixels in from the trunk's edge (negative outside it)
            ring = min(x - lo, hi - x, y - lo, hi - y)
            if ring <= 0:
                c = STRIPPED_RIM if stripped else rng.choice(BARK)
                if stripped and rng.random() < 0.2:
                    c = shade(c, 0.93)
            else:
                # tight growth rings: a slow-grown tree has many
                c = wood[1] if ring % 2 == 0 else wood[0]
                if rng.random() < 0.15:
                    c = wood[2]
            px[x, y] = c + (255,)
    # the pith, a touch off centre
    px[7, 8] = shade(wood[1], 0.8) + (255,)
    return im


# ---------------------------------------------------------------- shrubs

def shrub(seed, leaves, height, spread, density):
    """A little woody bush for a cross model: twigs fanning up from the bottom middle, leaves clustered at their ends."""
    rng = random.Random("tundra-shrub:" + seed)
    im = blank()
    px = im.load()
    tips = []
    twigs = 5
    for i in range(twigs):
        angle = -spread + 2 * spread * i / (twigs - 1) + rng.uniform(-0.12, 0.12)
        length = height * rng.uniform(0.7, 1.0) * (1.0 - 0.25 * abs(angle) / max(0.01, spread))
        x, y = 7.5 + (i - 2) * 0.4, 15.0
        steps = int(length * 2)
        for s in range(steps):
            xi, yi = int(round(x)), int(round(y))
            if 0 <= xi < 16 and 0 <= yi < 16:
                px[xi, yi] = TWIG[0 if s % 3 else 1] + (255,)
            x += math.sin(angle) * 0.5
            y -= math.cos(angle) * 0.5
            angle += rng.uniform(-0.08, 0.08)
            if s > steps * 0.45 and rng.random() < 0.3:
                tips.append((x + rng.uniform(-1, 1), y + rng.uniform(-0.5, 0.5)))
        tips.append((x, y))
    for tx, ty in tips:
        for _ in range(density):
            lx, ly = int(round(tx + rng.gauss(0, 1.0))), int(round(ty + rng.gauss(0, 0.8)))
            if 0 <= lx < 16 and 0 <= ly < 15:
                px[lx, ly] = rng.choice(leaves[:2]) + (255,)
    # light from above: a leaf with nothing over it is bright
    for y in range(15):
        for x in range(16):
            if px[x, y][3] and px[x, y][:3] in leaves and (y == 0 or px[x, y - 1][3] == 0) and rng.random() < 0.7:
                px[x, y] = leaves[2] + (255,)
    return im


# ---------------------------------------------------------------- lichen

def lichen_patch(seed, pal, cover=0.5, sprouts=0, cuts=0):
    """A flat crust of lichen: ragged lobes with a dark rim, a paler middle and glowing flecks. `sprouts` adds pale
    bulbs; `cuts` adds the pale flat marks shears leave and takes the glow away."""
    rng = random.Random("tundra-lichen-patch:" + seed)
    big, small = value_noise(rng, 3), value_noise(rng, 8)
    im = blank()
    px = im.load()
    solid = [[False] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            # thinner towards the block's edge, so a patch reads as a patch and not as a tile
            far = ((x - 7.5) ** 2 + (y - 7.5) ** 2) / 56.0
            n = big[y][x] * 0.7 + small[y][x] * 0.3 - 0.3 * far
            solid[y][x] = n > 0.62 - cover * 0.6
    for y in range(16):
        for x in range(16):
            if not solid[y][x]:
                continue
            near_gap = any(not (0 <= x + ox < 16 and 0 <= y + oy < 16 and solid[y + oy][x + ox])
                           for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            if near_gap:
                c = pal["lo"]
            else:
                c = pal["mid"] if small[y][x] < 0.55 else pal.get("hi", pal["mid"])
            px[x, y] = c + (255,)
    inner = [(x, y) for y in range(1, 15) for x in range(1, 15) if solid[y][x] and px[x, y][:3] != pal["lo"]]
    rng.shuffle(inner)
    if cuts:
        for x, y in inner[:cuts]:
            px[x, y] = SNIP["cut"] + (255,)
    else:
        for x, y in inner[:max(3, len(inner) // 9)]:
            px[x, y] = LICHEN["glow"] + (255,)
        for x, y in inner[-sprouts:] if sprouts else []:
            px[x, y] = SPROUT["bulb"] + (255,)
            if y > 0 and solid[y - 1][x]:
                px[x, y - 1] = SPROUT["bulb_hi"] + (255,)
    return im


def lichen_tufts(seed, hanging=False, count=6, tall=(3, 6), sprouted=False, snipped=False):
    """Upright lobes of lichen for a cross model, rooted along the bottom edge (or hanging from the top). Sprouted ones
    carry a pale bulb on a stalk; snipped ones are stubs with a flat pale cut."""
    rng = random.Random("tundra-lichen-tufts:" + seed)
    im = blank()
    px = im.load()
    spots = rng.sample(range(1, 15), count)
    for bx in sorted(spots):
        h = 2 if snipped else rng.randrange(tall[0], tall[1] + 1)
        x = bx
        for i in range(h):
            y = 15 - i
            if 0 <= x < 16:
                c = LICHEN["lo"] if i == 0 else LICHEN["mid"] if i < h - 1 else LICHEN["hi"]
                if snipped:
                    c = SNIP["mid"] if i == 0 else SNIP["cut"]
                px[x, y] = c + (255,)
                # a lobe is two wide near its root
                if i < h // 2 and not snipped and 0 <= x + 1 < 16 and rng.random() < 0.6:
                    px[x + 1, y] = LICHEN["lo"] + (255,)
            if not snipped and i > 0 and rng.random() < 0.3:
                x += rng.choice((-1, 1))
        if snipped:
            continue
        ty = 15 - h
        if sprouted and 0 <= x < 16 and ty >= 1:
            px[x, ty] = SPROUT["stalk"] + (255,)
            px[x, ty - 1] = SPROUT["bulb"] + (255,)
            if rng.random() < 0.5 and ty >= 2:
                px[x, ty - 2] = SPROUT["bulb_hi"] + (255,)
        elif 0 <= x < 16 and ty >= 0 and rng.random() < 0.7:
            px[x, ty] = LICHEN["glow"] + (255,)
    return im.transpose(Image.FLIP_TOP_BOTTOM) if hanging else im


def phosphor():
    """The glow ink sac's item, redrawn as phosphor: a small heap of glowing blue-green grains."""
    rng = random.Random("tundra-phosphor")
    im = blank()
    px = im.load()
    outline, body, lit, glint = (24, 74, 80), (58, 164, 156), (136, 232, 204), (226, 255, 238)
    for y in range(16):
        for x in range(16):
            # a mound: wide at the bottom, a rounded top a little left of middle
            dx, dy = (x - 7.5) / 5.6, (y - 12.5) / 6.4
            if dy <= 0.3 and dx * dx + dy * dy <= 1.0 and y <= 13:
                d = dx * 0.6 + dy * 0.8
                c = lit if d < -0.55 else body if d < 0.1 else shade(body, 0.78)
                # grains: scattered glints on the lit side, dark gaps between grains everywhere
                roll = rng.random()
                if roll < 0.16:
                    c = glint if d < -0.2 else lit
                elif roll < 0.3:
                    c = shade(c, 0.72)
                px[x, y] = c + (255,)
    out = im.copy()
    opx = out.load()
    for y in range(16):
        for x in range(16):
            if px[x, y][3] == 0 and any(0 <= x + ox < 16 and 0 <= y + oy < 16 and px[x + ox, y + oy][3] > 0
                                        for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                opx[x, y] = outline + (255,)
    # two loose grains beside the heap
    for x, y in ((2, 13), (13, 12)):
        opx[x, y] = lit + (255,)
        opx[x, y + 1] = outline + (255,)
    return out


def textures():
    save_mc(banded_iron_ore(), "block", "smooth_basalt_iron_ore")
    save_mc(yellow_moss(), "block", "yellow_moss")
    save_mc(white_ice(), "block", "white_ice")
    save_mc(log_end(10, False), "block", "dwarfed_spruce_log_end")
    save_mc(log_end(6, False), "block", "dwarfed_spruce_post_end")
    save_mc(log_end(10, True), "block", "stripped_dwarfed_spruce_log_end")
    save_mc(log_end(6, True), "block", "stripped_dwarfed_spruce_post_end")
    save_mc(shrub("red", RED_LEAF, 6.5, 1.15, 4), "block", "stunted_red_shrub")
    save_mc(shrub("orange", ORANGE_LEAF, 9.0, 0.85, 6), "block", "orange_shrub")
    save_mc(shrub("yellow", YELLOW_LEAF, 11.0, 0.65, 5), "block", "yellow_shrub")
    for i, name in enumerate(GROUND_FLATS):
        save_mc(lichen_patch("flat%d" % i, LICHEN, cover=0.42 + 0.05 * (i % 3)), "block", name)
    for i, name in enumerate(GROUND_TUFTS):
        save_mc(lichen_tufts("ground%d" % i, count=7 + i % 3, tall=(3, 7)), "block", name)
    for i, name in enumerate(CEILINGS):
        save_mc(lichen_tufts("ceiling%d" % i, hanging=True, count=5 + i, tall=(4, 10)), "block", name)
    save_mc(phosphor(), "item", "glow_ink_sac")

    save_own(lichen_patch("sprouted", LICHEN, cover=0.55, sprouts=7), "sprouted_glow_lichen")
    save_own(lichen_tufts("sprouted-ground", count=6, tall=(3, 6), sprouted=True), "sprouted_glow_lichen_ground")
    save_own(lichen_tufts("sprouted-ceiling", hanging=True, count=6, tall=(4, 9), sprouted=True), "sprouted_glow_lichen_ceiling")
    save_own(lichen_patch("snipped", SNIP, cover=0.5, cuts=9), "snipped_glow_lichen")
    save_own(lichen_tufts("snipped-ground", count=7, snipped=True), "snipped_glow_lichen_ground")
    save_own(lichen_tufts("snipped-ceiling", hanging=True, count=7, snipped=True), "snipped_glow_lichen_ceiling")


GROUND_FLATS = ["glow_lichen_ground_flat"] + ["glow_lichen_ground_flat_%d" % i for i in range(1, 5)]
GROUND_TUFTS = ["glow_lichen_ground"] + ["glow_lichen_ground_%d" % i for i in range(1, 4)]
CEILINGS = ["glow_lichen_ceiling", "glow_lichen_ceiling1", "glow_lichen_ceiling2"]


# ---------------------------------------------------------------- models

def mctex(name):
    return "minecraft:block/" + name


def tex(name):
    return "%s:block/tundra/%s" % (NS, name)


def mdl(name):
    return "%s:block/tundra/%s" % (NS, name)


def block_model(name, data):
    write(os.path.join(ASSETS, "models", "block", "tundra", name + ".json"), data)


def item_model(name, data):
    write(os.path.join(ASSETS, "models", "item", "tundra", name + ".json"), data)


def bstate(name, data, namespace_dir=ASSETS):
    write(os.path.join(namespace_dir, "blockstates", name + ".json"), data)


def item_def(name, model):
    write(os.path.join(ASSETS, "items", name + ".json"), {"model": {"type": "minecraft:model", "model": model}})


def pillar_template(name, width):
    """An upright pillar `width` pixels across: bark on its four sides, the cut end top and bottom."""
    lo, hi = 8 - width / 2, 8 + width / 2
    side = {"uv": [lo, 0, hi, 16], "texture": "#side"}
    block_model(name, {
        "parent": mc("block/block"),
        "textures": {"particle": "#side"},
        "elements": [{
            "from": [lo, 0, lo], "to": [hi, 16, hi],
            "faces": {
                "down": {"uv": [lo, lo, hi, hi], "texture": "#end", "cullface": "down"},
                "up": {"uv": [lo, lo, hi, hi], "texture": "#end", "cullface": "up"},
                "north": side, "south": side, "west": side, "east": side}}]})


def crossed(texture_var, y0=0, y1=16):
    """Two planes crossed on the diagonals, as the game's own cross model has them."""
    turn = {"origin": [8, 8, 8], "axis": "y", "angle": 45, "rescale": True}
    return [
        {"from": [0.8, y0, 8], "to": [15.2, y1, 8], "rotation": turn, "shade_direction_override": "up",
         "faces": {"north": {"uv": [0, 0, 16, 16], "texture": texture_var}, "south": {"uv": [0, 0, 16, 16], "texture": texture_var}}},
        {"from": [8, y0, 0.8], "to": [8, y1, 15.2], "rotation": turn, "shade_direction_override": "up",
         "faces": {"west": {"uv": [0, 0, 16, 16], "texture": texture_var}, "east": {"uv": [0, 0, 16, 16], "texture": texture_var}}}]


def flat(y):
    """A crust lying on the floor (or under the ceiling), seen from both sides."""
    return {"from": [0, y, 0], "to": [16, y, 16], "shade_direction_override": "up",
            "faces": {"up": {"uv": [0, 0, 16, 16], "texture": "#flat"}, "down": {"uv": [0, 16, 16, 0], "texture": "#flat"}}}


def lichen_templates():
    # on a wall: one plane just off the north face (the blockstate turns it to the other walls)
    block_model("lichen_face", {"ambientocclusion": False, "textures": {"particle": "#lichen"}, "elements": [
        {"from": [0, 0, 0.1], "to": [16, 16, 0.1], "shade_direction_override": "up",
         "faces": {"north": {"uv": [16, 0, 0, 16], "texture": "#lichen"}, "south": {"uv": [0, 0, 16, 16], "texture": "#lichen"}}}]})
    # on the floor: a crust with tufts standing in it
    block_model("lichen_ground", {"ambientocclusion": False, "textures": {"particle": "#flat"},
                                  "elements": [flat(0.1)] + crossed("#tuft")})
    # under a ceiling: a crust with strands hanging from it
    block_model("lichen_ceiling", {"ambientocclusion": False, "textures": {"particle": "#flat"},
                                   "elements": [flat(15.9)] + crossed("#tuft")})
    # a bare crust on the floor
    block_model("lichen_flat", {"ambientocclusion": False, "textures": {"particle": "#flat"}, "elements": [flat(0.1)]})


def turned(models):
    """Each model at the four quarter turns, so neighbouring patches don't repeat."""
    return [dict({"model": m}, **({"y": y} if y else {})) for m in models for y in (0, 90, 180, 270)]


def lichen_state(side_model, ground_models, ceiling_models, extra=None):
    """A multiface blockstate: the wall plane on the four sides, our ground models on the floor face, the ceiling
    models on the ceiling face. With no face at all (never placed that way) it shows as a wall plane."""
    walls = {"north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270}}
    parts = []
    for face, turn in walls.items():
        apply = dict({"model": side_model}, **turn)
        if turn:
            apply["uvlock"] = True
        parts.append({"when": {face: "true"}, "apply": apply})
    parts.append({"when": {f: "false" for f in FACES}, "apply": {"model": side_model}})
    parts.append({"when": dict({"down": "true"}, **(extra or {})), "apply": turned(ground_models)})
    parts.append({"when": dict({"up": "true"}, **(extra or {})), "apply": turned(ceiling_models)})
    return parts


def assets():
    for name, texture in (("banded_iron_ore", "smooth_basalt_iron_ore"), ("white_ice", "white_ice"), ("yellow_moss", "yellow_moss")):
        block_model(name, {"parent": mc("block/cube_all"), "textures": {"all": mctex(texture)}})
        bstate(name, {"variants": {"": {"model": mdl(name)}}})
        item_def(name, mdl(name))
    block_model("yellow_moss_carpet", {"parent": mc("block/carpet"), "textures": {"wool": mctex("yellow_moss")}})
    bstate("yellow_moss_carpet", {"variants": {"": {"model": mdl("yellow_moss_carpet")}}})
    item_def("yellow_moss_carpet", mdl("yellow_moss_carpet"))

    # dwarfed spruce: the bark is the game's spruce log, the ends are ours
    pillar_template("pillar_10", 10)
    pillar_template("pillar_6", 6)
    for name in LOGS:
        side = "stripped_spruce_log" if name.startswith("stripped_") else "spruce_log"
        block_model(name, {"parent": mdl("pillar_10" if name.endswith("_log") else "pillar_6"),
                           "textures": {"side": mctex(side), "end": mctex(name + "_end")}})
        bstate(name, {"variants": {"axis=y": {"model": mdl(name)}, "axis=z": {"model": mdl(name), "x": 90},
                                   "axis=x": {"model": mdl(name), "x": 90, "y": 90}}})
        item_def(name, mdl(name))

    for name in SHRUBS:
        block_model(name, {"parent": mc("block/cross"), "textures": {"cross": mctex(name)}})
        bstate(name, {"variants": {"": {"model": mdl(name)}}})
        item_model(name, {"parent": mc("item/generated"), "textures": {"layer0": mctex(name)}})
        item_def(name, "%s:item/tundra/%s" % (NS, name))

    lichen_templates()
    # vanilla glow lichen: tufts and crusts on the floor, strands under a ceiling; its walls stay the game's own
    grounds = []
    for i, tuft in enumerate(GROUND_TUFTS):
        block_model("glow_lichen_ground_%d" % i, {"parent": mdl("lichen_ground"),
                                                  "textures": {"flat": mctex(GROUND_FLATS[i]), "tuft": mctex(tuft)}})
        grounds.append(mdl("glow_lichen_ground_%d" % i))
    block_model("glow_lichen_ground_bare", {"parent": mdl("lichen_flat"), "textures": {"flat": mctex(GROUND_FLATS[4])}})
    grounds.append(mdl("glow_lichen_ground_bare"))
    ceilings = []
    for i, strands in enumerate(CEILINGS):
        block_model("glow_lichen_ceiling_%d" % i, {"parent": mdl("lichen_ceiling"),
                                                   "textures": {"flat": mctex(GROUND_FLATS[i]), "tuft": mctex(strands)}})
        ceilings.append(mdl("glow_lichen_ceiling_%d" % i))
    walls = {"north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270}}
    vanilla = []
    for face, turn in walls.items():
        apply = dict({"model": mc("block/glow_lichen")}, **turn)
        if turn:
            apply["uvlock"] = True
        vanilla.append({"when": {face: "true"}, "apply": apply})
        vanilla.append({"when": {f: "false" for f in FACES}, "apply": apply})
    vanilla.append({"when": {"down": "true"}, "apply": turned(grounds)})
    vanilla.append({"when": {"up": "true"}, "apply": turned(ceilings)})
    bstate("glow_lichen", {"multipart": vanilla}, MC_ASSETS)

    # our two: sprouted (and how it looks once snipped), and the snipped lichen
    for kind in ("sprouted", "snipped"):
        base = kind + "_glow_lichen"
        block_model(base, {"parent": mdl("lichen_face"), "textures": {"lichen": tex(base)}})
        block_model(base + "_ground", {"parent": mdl("lichen_ground"), "textures": {"flat": tex(base), "tuft": tex(base + "_ground")}})
        block_model(base + "_ceiling", {"parent": mdl("lichen_ceiling"), "textures": {"flat": tex(base), "tuft": tex(base + "_ceiling")}})
        item_model(base, {"parent": mc("item/generated"), "textures": {"layer0": tex(base)}})
        item_def(base, "%s:item/tundra/%s" % (NS, base))
    # a sprouted lichen that was snipped keeps its crust and bulbs on the walls; its tufts are cut to stubs
    block_model("sprouted_glow_lichen_ground_snipped", {"parent": mdl("lichen_ground"), "textures": {
        "flat": tex("sprouted_glow_lichen"), "tuft": tex("snipped_glow_lichen_ground")}})
    block_model("sprouted_glow_lichen_ceiling_snipped", {"parent": mdl("lichen_ceiling"), "textures": {
        "flat": tex("sprouted_glow_lichen"), "tuft": tex("snipped_glow_lichen_ceiling")}})
    sprouted = lichen_state(mdl("sprouted_glow_lichen"), [mdl("sprouted_glow_lichen_ground")], [mdl("sprouted_glow_lichen_ceiling")],
                            {"snipped": "false"})
    sprouted += lichen_state(mdl("sprouted_glow_lichen"), [mdl("sprouted_glow_lichen_ground_snipped")],
                             [mdl("sprouted_glow_lichen_ceiling_snipped")], {"snipped": "true"})[-2:]
    bstate("sprouted_glow_lichen", {"multipart": sprouted})
    bstate("snipped_glow_lichen", {"multipart": lichen_state(mdl("snipped_glow_lichen"), [mdl("snipped_glow_lichen_ground")],
                                                             [mdl("snipped_glow_lichen_ceiling")])})


# ---------------------------------------------------------------- data

def table(block, pools):
    write(os.path.join(DATA, NS, "loot_table", "blocks", block + ".json"),
          {"type": "minecraft:block", "pools": pools, "random_sequence": jp("blocks/" + block)})


def self_drop(block):
    table(block, [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": jp(block)}],
                   "condition": {"type": "minecraft:survives_explosion"}}])


def per_face(block, item):
    """As glow lichen drops: with shears, one `item` for each face the block covers."""
    counts = [{"type": "minecraft:set_count", "add": True, "count": 1,
               "condition": {"type": "minecraft:match_block", "blocks": jp(block), "state": {face: "true"}}} for face in FACES]
    counts += [{"type": "minecraft:set_count", "add": True, "count": -1}, {"type": "minecraft:explosion_decay"}]
    table(block, [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": item, "condition": "minecraft:tool/can_shear",
                                           "modifier": counts}]}])


def loot():
    for b in LOGS + ["white_ice", "yellow_moss", "yellow_moss_carpet"]:
        self_drop(b)
    # silk touch keeps the ore; otherwise more raw iron than plain ore gives, and Fortune on top as for any ore
    table("banded_iron_ore", [{"rolls": 1, "entries": [{"type": "minecraft:alternatives", "children": [
        {"type": "minecraft:item", "name": jp("banded_iron_ore"), "condition": "minecraft:tool/can_silk_touch"},
        {"type": "minecraft:item", "name": mc("raw_iron"), "modifier": [
            {"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": BANDED_IRON_MIN, "max": BANDED_IRON_MAX}},
            {"type": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"},
            {"type": "minecraft:explosion_decay"}]}]}]}])
    # shrubs come away only with shears or silk touch
    for b in SHRUBS:
        table(b, [{"rolls": 1, "entries": [{"type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "name": jp(b), "condition": "minecraft:tool/can_shear"},
            {"type": "minecraft:item", "name": jp(b), "condition": "minecraft:tool/can_silk_touch"}]}]}])
    # a sprouted lichen sheared off is plain glow lichen again; a snipped one stays snipped
    per_face("sprouted_glow_lichen", mc("glow_lichen"))
    per_face("snipped_glow_lichen", jp("snipped_glow_lichen"))


def recipe(name, data):
    write(os.path.join(DATA, NS, "recipe", name + ".json"), data)


def recipes():
    for kind, time in (("smelting", 200), ("blasting", 100)):
        recipe("iron_ingot_from_%s_banded_iron_ore" % kind, {
            "type": "minecraft:" + kind, "category": "blocks", "cookingtime": time, "experience": 0.7, "group": "iron_ingot",
            "ingredient": jp("banded_iron_ore"), "result": {"id": mc("iron_ingot")}})
    # dwarfed spruce is still spruce: two thin logs or four posts make one spruce log
    for prefix in ("", "stripped_"):
        for part, count in (("log", 2), ("post", 4)):
            recipe("%sspruce_log_from_dwarfed_spruce_%s" % (prefix, part), {
                "type": "minecraft:crafting_shapeless", "category": "building", "group": prefix + "spruce_log_from_dwarfed",
                "ingredients": [jp("%sdwarfed_spruce_%s" % (prefix, part))] * count,
                "result": {"id": mc(prefix + "spruce_log")}})
    recipe("yellow_moss_carpet", {"type": "minecraft:crafting_shaped", "category": "misc", "group": "carpet",
                                  "key": {"#": jp("yellow_moss")}, "pattern": ["##"],
                                  "result": {"count": 3, "id": jp("yellow_moss_carpet")}})
    recipe("phosphor_from_smelting_glow_lichen", {
        "type": "minecraft:smelting", "category": "misc", "cookingtime": 200, "experience": PHOSPHOR_XP,
        "ingredient": "#" + jp("glow_lichens"), "result": {"id": mc("glow_ink_sac")}})


def tags():
    t = os.path.join(DATA, "minecraft", "tags")
    own = os.path.join(DATA, NS, "tags")
    logs = [jp(b) for b in LOGS]
    lichens = [jp(b) for b in LICHENS]
    shrubs = [jp(b) for b in SHRUBS]

    merge_tag(os.path.join(t, "block", "mineable", "pickaxe.json"), [jp("banded_iron_ore"), jp("white_ice")])
    merge_tag(os.path.join(t, "block", "needs_stone_tool.json"), [jp("banded_iron_ore")])
    merge_tag(os.path.join(t, "block", "mineable", "axe.json"), logs + lichens)
    merge_tag(os.path.join(t, "block", "mineable", "hoe.json"), [jp("yellow_moss"), jp("yellow_moss_carpet"), jp("yellow_moss_layer")])
    merge_tag(os.path.join(t, "block", "ice.json"), [jp("white_ice")])
    merge_tag(os.path.join(t, "block", "combination_step_sound_blocks.json"), [jp("yellow_moss_carpet")])
    for tag in ("sword_efficient", "replaceable", "inside_step_sound_blocks", "shears_minor_breaking_speed"):
        merge_tag(os.path.join(t, "block", tag + ".json"), lichens)
    merge_tag(os.path.join(t, "block", "replaceable_by_trees.json"), lichens + shrubs + [jp("yellow_moss_carpet")])
    for kind in ("block", "item"):
        merge_tag(os.path.join(t, kind, "iron_ores.json"), [jp("banded_iron_ore")])
        merge_tag(os.path.join(t, kind, "logs_that_burn.json"), ["#" + jp("dwarfed_spruce_logs")])
        # dwarfed spruce is wood like any log: felling a tree, chopping with bare hands (which wants an axe) and the recipes
        # that ask for "a log" all go by minecraft:logs
        merge_tag(os.path.join(t, kind, "logs.json"), ["#" + jp("dwarfed_spruce_logs")])
        merge_tag(os.path.join(t, kind, "moss_blocks.json"), [jp("yellow_moss")])
        merge_tag(os.path.join(own, kind, "dwarfed_spruce_logs.json"), logs)
        merge_tag(os.path.join(own, kind, "glow_lichens.json"), [mc("glow_lichen")] + lichens)
        merge_tag(os.path.join(own, kind, "tundra_shrubs.json"), shrubs)

    # what sprouted lichen breaks down, a step at a time (LichenLife). Worked stone (bricks, polished) is left out, and
    # so are ores: builds and veins are safe.
    merge_tag(os.path.join(own, "block", "lichen", "to_gravel.json"), [mc(b) for b in (
        "stone", "deepslate", "basalt", "smooth_basalt", "granite", "diorite", "andesite", "tuff", "calcite", "cobblestone",
        "cobbled_deepslate", "sandstone")])
    merge_tag(os.path.join(own, "block", "lichen", "to_soil.json"), [mc("gravel")])
    merge_tag(os.path.join(own, "block", "lichen", "to_dirt.json"), [mc("coarse_dirt")])
    merge_tag(os.path.join(own, "block", "lichen", "breakable.json"),
              ["#" + jp("lichen/to_gravel"), "#" + jp("lichen/to_soil"), "#" + jp("lichen/to_dirt")])
    merge_tag(os.path.join(own, "block", "shrub_ground.json"),
              ["#minecraft:dirt", "#minecraft:grass_blocks", "#minecraft:moss_blocks", mc("gravel")])


def lang():
    names = {
        "banded_iron_ore": "Banded Iron Ore", "white_ice": "White Ice",
        "dwarfed_spruce_log": "Dwarfed Spruce Log", "dwarfed_spruce_post": "Dwarfed Spruce Post",
        "stripped_dwarfed_spruce_log": "Stripped Dwarfed Spruce Log", "stripped_dwarfed_spruce_post": "Stripped Dwarfed Spruce Post",
        "stunted_red_shrub": "Stunted Red Shrub", "orange_shrub": "Orange Shrub", "yellow_shrub": "Yellow Shrub",
        "yellow_moss": "Yellow Moss", "yellow_moss_carpet": "Yellow Moss Carpet",
        "sprouted_glow_lichen": "Sprouted Glow Lichen", "snipped_glow_lichen": "Snipped Glow Lichen"}
    path = os.path.join(ASSETS, "lang", "parts", "tundra.json")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump({"block.%s.%s" % (NS, k): v for k, v in names.items()}, f, indent="\t")
        f.write("\n")
    # the glow ink sac is phosphor now. In the game's own namespace, where our file is read after the game's; a key in
    # our own lang file would race the game's.
    path = os.path.join(MC_ASSETS, "lang", "en_us.json")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump({"item.minecraft.glow_ink_sac": "Phosphor"}, f, indent="\t")
        f.write("\n")


def main():
    textures()
    assets()
    loot()
    recipes()
    tags()
    lang()


if __name__ == "__main__":
    main()
