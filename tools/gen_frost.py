"""The 26.4 snapshots' ice, built for our 26.3 pack (com.jaymospowers.frost): our own textures (nothing of Mojang's art is
copied; their template models are only used as parents), models, blockstates, item definitions, loot tables, recipes,
brewing recipes and tags.

Textures: icicles (base/middle/tip, hanging and standing), the ice crystal, brittle ice in four cracks, packed ice bricks
and the chiseled snowflake, snow bricks, the ice ball, the Freezing effect icon, the Frostbite's 64x64 zombie skin and
its spawn egg. Seeded, so reruns give the same art.

    python3 -I tools/gen_frost.py
"""
import json
import math
import os
import random
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_aether as ga  # stairs_state / wall_state only (it writes nothing on import)
from gen_aether_mobs import egg_image  # noqa: E402

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
ASSETS = os.path.join(ROOT, "assets", "jaymospowers")
DATA = os.path.join(ROOT, "data")
NS = "jaymospowers"


def jp(name):
    return "%s:%s" % (NS, name)


def mc(name):
    return "minecraft:" + name


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def merge_tag(path, values, replace=None):
    """Adds `values` to a tag file (keeping what's there); `replace` writes the file whole instead."""
    if replace is not None:
        write(path, {"replace": True, "values": replace})
        return
    current = []
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            current = json.load(f).get("values", [])
    for v in values:
        if v not in current:
            current.append(v)
    write(path, {"values": current})


def save(im, *parts):
    path = os.path.join(ASSETS, "textures", *parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im.save(path)


def clamp(c):
    return tuple(max(0, min(255, int(round(v)))) for v in c)


def shade(c, k):
    return clamp([v * k for v in c[:3]])


def mix(a, b, t):
    return clamp([a[i] * (1 - t) + b[i] * t for i in range(3)])


# ---------------------------------------------------------------- palettes

ICE_HI = (236, 248, 255)
ICE = (186, 222, 250)
ICE_MID = (150, 196, 238)
ICE_LO = (112, 160, 214)
PACKED = [(148, 182, 236), (160, 192, 242), (138, 172, 228), (170, 200, 246)]
PACKED_MORTAR = (112, 146, 206)
SNOW = [(244, 250, 252), (236, 244, 248), (228, 238, 244), (248, 252, 255)]
SNOW_MORTAR = (196, 212, 226)


# ---------------------------------------------------------------- icicles

def icicle_column(part, rng):
    """Half-widths down a hanging piece (y 0 = the top), as floats; 0 = nothing."""
    if part == "base":
        return [4.6 - 0.9 * y / 15 for y in range(16)]
    if part == "middle":
        return [3.6 - 0.6 * y / 15 for y in range(16)]
    # the tip: tapers to a point at y 13
    return [max(0.0, 2.9 * (1 - y / 13.5) ** 0.9) if y < 14 else 0.0 for y in range(16)]


def icicle(part, seed):
    rng = random.Random("frost-icicle:" + part + seed)
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    widths = icicle_column(part, rng)
    wobble = [0.0] * 16
    w = 0.0
    for y in range(16):
        w = max(-0.6, min(0.6, w + rng.uniform(-0.25, 0.25)))
        wobble[y] = w
    streak = rng.randrange(-1, 2)
    for y in range(16):
        half = widths[y]
        if half <= 0.15:
            continue
        cx = 7.5 + wobble[y]
        for x in range(16):
            d = (x - cx) / half
            if abs(d) > 1.0:
                continue
            # light from the left: a bright rim, a clear middle, a deeper right side
            if d < -0.55:
                c, a = ICE_HI, 235
            elif d < 0.1:
                c, a = ICE, 190
            elif d < 0.6:
                c, a = ICE_MID, 205
            else:
                c, a = ICE_LO, 225
            if int(round(x - cx)) == streak and rng.random() < 0.6:
                c, a = mix(c, ICE_HI, 0.6), 225
            if rng.random() < 0.08:
                c = mix(c, (255, 255, 255), 0.5)
            px[x, y] = c + (a,)
    if part == "tip":
        # a bright drop of meltwater frozen at the point
        for y in range(15, 8, -1):
            row = [x for x in range(16) if px[x, y][3] > 0]
            if row:
                px[row[0], y] = ICE_HI + (255,)
                break
    return im


def icicle_item():
    """A whole little icicle for the inventory: base, middle and tip squeezed into one."""
    rng = random.Random("frost-icicle-item")
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    for y in range(1, 16):
        half = max(0.0, 3.4 * (1 - (y - 1) / 14.5) ** 0.8)
        if half < 0.3:
            continue
        cx = 7.5 + (0.4 if y > 8 else 0.0)
        for x in range(16):
            d = (x - cx) / half
            if abs(d) > 1.0:
                continue
            c = ICE_HI if d < -0.5 else ICE if d < 0.1 else ICE_MID if d < 0.6 else ICE_LO
            px[x, y] = c + (255,)
    # dark outline so it reads in the hotbar
    out = im.copy()
    opx = out.load()
    for y in range(16):
        for x in range(16):
            if px[x, y][3] == 0 and any(0 <= x + ox < 16 and 0 <= y + oy < 16 and px[x + ox, y + oy][3] > 0
                                        for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                opx[x, y] = (62, 96, 150, 255)
    for x in range(4, 12):
        if opx[x, 0][3] == 0 and px[x, 1][3] > 0:
            opx[x, 0] = (62, 96, 150, 255)
    return out


# ---------------------------------------------------------------- ice crystal

def ice_crystal():
    """Three pale shards standing from the bottom middle (the model turns it to face the way it grows)."""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    shards = [(7.5, 15.5, 0.0, 12.5, 1.9), (4.5, 15.5, -0.28, 7.0, 1.3), (11.0, 15.5, 0.3, 8.0, 1.4)]
    for bx, by, lean, height, half in shards:
        for t in range(int(height * 4)):
            s = t / 4.0
            y = by - s
            cx = bx + lean * s
            w = half * (1 - s / (height + 0.6))
            for x in range(16):
                d = (x - cx) / max(0.4, w)
                yy = int(y)
                if abs(d) <= 1.0 and 0 <= yy < 16:
                    c = (250, 254, 255) if d < -0.35 else (196, 240, 255) if d < 0.4 else (130, 204, 240)
                    if s > height - 1.2:
                        c = (255, 255, 255)
                    px[x, yy] = c + (255,)
    return im


# ---------------------------------------------------------------- brittle ice

def ice_base(seed):
    rng = random.Random("frost-ice:" + seed)
    im = Image.new("RGBA", (16, 16))
    px = im.load()
    for y in range(16):
        for x in range(16):
            c = mix(ICE, ICE_MID, rng.random() * 0.5)
            px[x, y] = c + (150,)
    # long pale streaks, as in real lake ice
    for _ in range(4):
        x, y = rng.randrange(16), rng.randrange(16)
        for i in range(rng.randrange(3, 7)):
            px[(x + i) % 16, (y + i // 2) % 16] = ICE_HI + (190,)
    return im


def crack_paths(seed):
    """Crack segments, in the order they open: each a list of pixels."""
    rng = random.Random("frost-cracks:" + seed)
    paths = []
    starts = [(7, 8), (3, 4), (12, 11), (11, 3), (4, 12)]
    for sx, sy in starts:
        for _ in range(2):
            x, y = sx, sy
            dx, dy = rng.choice([(1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1), (1, -1), (-1, 1)])
            path = []
            for _ in range(rng.randrange(4, 8)):
                path.append((x % 16, y % 16))
                if rng.random() < 0.35:
                    dx, dy = rng.choice([(dx, 0), (0, dy), (dx, dy)]) if (dx or dy) else (1, 0)
                    if dx == 0 and dy == 0:
                        dx = 1
                x += dx
                y += dy
            paths.append(path)
    return paths


def brittle_ice(stage):
    im = ice_base("brittle")
    px = im.load()
    paths = crack_paths("brittle")
    shown = {0: 2, 1: 5, 2: 8, 3: 10}[stage]
    for i, path in enumerate(paths[:shown]):
        fresh = i >= {0: 0, 1: 2, 2: 5, 3: 8}[stage]
        for x, y in path:
            px[x, y] = (255, 255, 255, 235) if fresh or stage == 0 else (226, 242, 255, 220)
            if stage >= 2 and (x + y) % 3 == 0:
                nx = min(15, x + 1)
                px[nx, y] = (204, 230, 250, 200)
    if stage == 3:
        # shattering: little clear holes along the worst cracks
        rng = random.Random("frost-holes")
        for path in paths[:4]:
            x, y = rng.choice(path)
            px[x, y] = (232, 246, 255, 90)
    return im


# ---------------------------------------------------------------- bricks

def bricks(colors, mortar, seed, rows=4, hi=None):
    rng = random.Random("frost-bricks:" + seed)
    im = Image.new("RGBA", (16, 16))
    px = im.load()
    h = 16 // rows
    for y in range(16):
        row = y // h
        off = 0 if row % 2 == 0 else 4
        for x in range(16):
            if y % h == h - 1 or (x + off) % 8 == 7:
                c = mortar
            else:
                c = rng.choice(colors)
                # each brick lit on its top-left edge
                if y % h == 0 or (x + off) % 8 == 0:
                    c = mix(c, hi or (255, 255, 255), 0.35)
            px[x, y] = c + (255,)
    return im


def chiseled():
    """A frame of packed ice around a six-armed snowflake."""
    rng = random.Random("frost-chiseled")
    im = Image.new("RGBA", (16, 16))
    px = im.load()
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            rim = x in (1, 14) or y in (1, 14)
            if edge:
                c = PACKED_MORTAR
            elif rim:
                c = mix(rng.choice(PACKED), (255, 255, 255), 0.3)
            else:
                c = shade(rng.choice(PACKED), 0.9)
            px[x, y] = c + (255,)
    flake = (236, 246, 255)
    cx = cy = 7.5
    for k in range(6):
        a = math.pi / 2 + k * math.pi / 3
        for r10 in range(0, 56):
            r = r10 / 10.0
            x, y = int(round(cx + r * math.cos(a))), int(round(cy - r * math.sin(a)))
            if 2 <= x <= 13 and 2 <= y <= 13:
                px[x, y] = flake + (255,)
        # the side twigs halfway out
        for side in (-1, 1):
            b = a + side * math.pi / 4
            sx, sy = cx + 3.2 * math.cos(a), cy - 3.2 * math.sin(a)
            for r10 in range(0, 18):
                r = r10 / 10.0
                x, y = int(round(sx + r * math.cos(b))), int(round(sy - r * math.sin(b)))
                if 2 <= x <= 13 and 2 <= y <= 13:
                    px[x, y] = flake + (255,)
    px[7, 7] = px[8, 8] = px[7, 8] = px[8, 7] = (255, 255, 255, 255)
    return im


# ---------------------------------------------------------------- items and icons

def ball(size, centre, radius, body, light, dark, outline, seed, frost=True):
    rng = random.Random("frost-ball:" + seed)
    im = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = im.load()
    cx, cy = centre
    inside = {}
    for y in range(size):
        for x in range(size):
            d = math.hypot(x - cx, y - cy) / radius
            # a lumpy edge
            d *= 1.0 + 0.08 * math.sin(math.atan2(y - cy, x - cx) * 5 + 1.3)
            inside[(x, y)] = d <= 1.0
    for (x, y), ok in inside.items():
        if not ok:
            continue
        edge = any(not inside.get((x + ox, y + oy), False) for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if edge:
            px[x, y] = outline + (255,)
            continue
        t = ((x - cx) + (y - cy)) / (2 * radius)
        c = mix(light, body, min(1, max(0, t + 0.6))) if t < 0 else mix(body, dark, min(1, t * 1.6))
        if frost and rng.random() < 0.12:
            c = mix(c, (255, 255, 255), 0.6)
        px[x, y] = c + (255,)
    # the glint
    gx, gy = int(cx - radius * 0.4), int(cy - radius * 0.45)
    px[gx, gy] = (255, 255, 255, 255)
    px[gx + 1, gy] = (240, 250, 255, 255)
    return im


def snowflake_icon():
    im = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
    px = im.load()
    cx = cy = 8.5
    for k in range(6):
        a = math.pi / 2 + k * math.pi / 3
        for r10 in range(0, 80):
            r = r10 / 10.0
            x, y = int(round(cx + r * math.cos(a))), int(round(cy - r * math.sin(a)))
            if 0 <= x < 18 and 0 <= y < 18:
                px[x, y] = (196, 236, 255, 255)
        for side in (-1, 1):
            b = a + side * math.pi / 4
            sx, sy = cx + 4.5 * math.cos(a), cy - 4.5 * math.sin(a)
            for r10 in range(0, 26):
                r = r10 / 10.0
                x, y = int(round(sx + r * math.cos(b))), int(round(sy - r * math.sin(b)))
                if 0 <= x < 18 and 0 <= y < 18:
                    px[x, y] = (150, 208, 245, 255)
    for x, y in ((8, 8), (9, 9), (8, 9), (9, 8)):
        px[x, y] = (255, 255, 255, 255)
    # a dark rim so it reads on the HUD
    out = im.copy()
    opx = out.load()
    for y in range(18):
        for x in range(18):
            if px[x, y][3] == 0 and any(0 <= x + ox < 18 and 0 <= y + oy < 18 and px[x + ox, y + oy][3] > 0
                                        for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                opx[x, y] = (40, 70, 120, 255)
    return out


# ---------------------------------------------------------------- the Frostbite

def faces(u, v, w, h, d):
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
            "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h)}


FB_SKIN = [(150, 178, 196), (140, 168, 188), (160, 186, 202), (132, 160, 182)]
FB_FROST = (226, 240, 250)
FB_SHIRT = [(58, 74, 98), (52, 66, 90), (64, 82, 106)]
FB_PANTS = [(40, 50, 72), (36, 46, 66), (46, 56, 78)]
FB_SHOES = (70, 76, 86)


def frostbite_skin():
    rng = random.Random("frost-frostbite")
    im = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = im.load()

    def paint(u, v, w, h, d, colour):
        for name, (fx, fy, fw, fh) in faces(u, v, w, h, d).items():
            for i in range(fw):
                for j in range(fh):
                    y = -1 if name == "top" else h if name == "bottom" else j
                    c = colour(name, i, y)
                    if c is not None:
                        px[fx + i, fy + j] = c[:3] + ((c[3],) if len(c) > 3 else (255,))

    def skin(_name, _x, _y):
        c = rng.choice(FB_SKIN)
        return mix(c, FB_FROST, 0.55) if rng.random() < 0.12 else c

    # head: frozen skin, rimed brows, sunken dark eyes with a cold blue light in them, a frost-bitten grin
    def head(name, x, y):
        c = skin(name, x, y)
        if name == "top":
            return mix(c, FB_FROST, 0.5 + rng.random() * 0.3)
        if name == "front":
            if y == 2 and 1 <= x <= 6:
                return mix(FB_FROST, c, 0.2)
            if y in (3, 4) and x in (1, 2, 5, 6):
                return (24, 34, 54) if not (y == 4 and x in (2, 5)) else (120, 214, 255)
            if y == 6 and 2 <= x <= 5:
                return (44, 54, 74) if x % 2 else (200, 220, 236)
            if y == 7 and 2 <= x <= 5 and rng.random() < 0.5:
                return mix(c, (80, 96, 120), 0.5)
        return c

    paint(0, 0, 8, 8, 8, head)

    # the hat layer: icicles hanging off the brow and jaw, frost crusted on the crown
    def hat(name, x, y):
        if name == "top":
            return FB_FROST + (230,) if rng.random() < 0.55 else None
        if name == "front" and y == 7 and x in (1, 3, 6):
            return (214, 236, 252, 220)
        if name in ("left", "right", "back") and y == 0 and rng.random() < 0.6:
            return FB_FROST + (220,)
        if name in ("left", "right") and y == 7 and x in (2, 5):
            return (214, 236, 252, 210)
        return None

    paint(32, 0, 8, 8, 8, hat)

    # body: a torn dark shirt, frost on the shoulders, flesh showing through the tears
    def body(name, y):
        def colour(face, x, yy):
            if face == "top":
                return mix(rng.choice(FB_SHIRT), FB_FROST, 0.6)
            if face == "bottom":
                return rng.choice(FB_SHIRT)
            if yy <= 1:
                return mix(rng.choice(FB_SHIRT), FB_FROST, 0.45)
            if rng.random() < 0.08:
                return rng.choice(FB_SKIN)
            c = rng.choice(FB_SHIRT)
            return mix(c, FB_FROST, 0.4) if rng.random() < 0.07 else c
        return colour

    paint(16, 16, 8, 12, 4, body("body", 0))

    def arm(face, x, y):
        if face == "top":
            return mix(rng.choice(FB_SHIRT), FB_FROST, 0.6)
        if y < 5:
            c = rng.choice(FB_SHIRT)
            return mix(c, FB_FROST, 0.4) if y == 0 else c
        if y == 11 or face == "bottom":
            # frost-white knuckles
            return mix(rng.choice(FB_SKIN), FB_FROST, 0.5)
        return skin(face, x, y)

    paint(40, 16, 4, 12, 4, arm)

    def leg(face, x, y):
        if face == "top":
            return rng.choice(FB_PANTS)
        if y >= 10 or face == "bottom":
            return shade(FB_SHOES, 0.9 + rng.random() * 0.2)
        c = rng.choice(FB_PANTS)
        if y >= 7 and rng.random() < 0.25:
            # snow caked on the shins
            return mix(c, FB_FROST, 0.6)
        return c

    paint(0, 16, 4, 12, 4, leg)
    return im


# ---------------------------------------------------------------- assets

def tex(name, kind="block"):
    return "%s:%s/frost/%s" % (NS, kind, name)


def mdl(name):
    return "%s:block/frost/%s" % (NS, name)


def block_model(name, data):
    write(os.path.join(ASSETS, "models", "block", "frost", name + ".json"), data)


def item_model(name, data):
    write(os.path.join(ASSETS, "models", "item", "frost", name + ".json"), data)


def bstate(name, data):
    write(os.path.join(ASSETS, "blockstates", name + ".json"), data)


def item_def(name, model):
    write(os.path.join(ASSETS, "items", name + ".json"), {"model": {"type": "minecraft:model", "model": model}})


def textures():
    for direction in ("down", "up"):
        for part in ("base", "middle", "tip"):
            im = icicle(part, direction)
            if direction == "up":
                im = im.transpose(Image.FLIP_TOP_BOTTOM)
            save(im, "block", "frost", "icicle_%s_%s.png" % (direction, part))
    save(icicle_item(), "item", "frost", "icicle.png")
    save(ice_crystal(), "block", "frost", "ice_crystal.png")
    for stage in range(4):
        save(brittle_ice(stage), "block", "frost", "brittle_ice_%d.png" % stage)
    save(bricks(PACKED, PACKED_MORTAR, "packed", hi=(214, 232, 255)), "block", "frost", "packed_ice_bricks.png")
    save(chiseled(), "block", "frost", "chiseled_packed_ice_bricks.png")
    save(bricks(SNOW, SNOW_MORTAR, "snow", hi=(255, 255, 255)), "block", "frost", "snow_bricks.png")
    save(ball(16, (7.5, 8.0), 5.2, (172, 214, 246), (236, 248, 255), (104, 154, 212), (48, 82, 136), "ice_ball"),
         "item", "frost", "ice_ball.png")
    save(egg_image((150, 178, 196), (52, 66, 90), (36, 48, 70), "frostbite", speckles=8, ring=True),
         "item", "frost", "frostbite_spawn_egg.png")
    save(snowflake_icon(), "mob_effect", "freezing.png")
    save(frostbite_skin(), "entity", "frostbite.png")


def family_models(prefix, base_tex, base_block):
    """Stairs, slab and wall models + blockstates + item definitions for `prefix` ("packed_ice_brick")."""
    t = {"bottom": base_tex, "side": base_tex, "top": base_tex}
    block_model(prefix + "_stairs", {"parent": mc("block/stairs"), "textures": t})
    block_model(prefix + "_stairs_inner", {"parent": mc("block/inner_stairs"), "textures": t})
    block_model(prefix + "_stairs_outer", {"parent": mc("block/outer_stairs"), "textures": t})
    bstate(prefix + "_stairs", {"variants": ga.stairs_state(mdl(prefix + "_stairs"))})
    item_def(prefix + "_stairs", mdl(prefix + "_stairs"))
    block_model(prefix + "_slab", {"parent": mc("block/slab"), "textures": t})
    block_model(prefix + "_slab_top", {"parent": mc("block/slab_top"), "textures": t})
    bstate(prefix + "_slab", {"variants": {"type=bottom": {"model": mdl(prefix + "_slab")}, "type=double": {"model": mdl(base_block)},
                                           "type=top": {"model": mdl(prefix + "_slab_top")}}})
    item_def(prefix + "_slab", mdl(prefix + "_slab"))
    w = {"wall": base_tex}
    block_model(prefix + "_wall_post", {"parent": mc("block/template_wall_post"), "textures": w})
    block_model(prefix + "_wall_side", {"parent": mc("block/template_wall_side"), "textures": w})
    block_model(prefix + "_wall_side_tall", {"parent": mc("block/template_wall_side_tall"), "textures": w})
    block_model(prefix + "_wall_inventory", {"parent": mc("block/wall_inventory"), "textures": w})
    bstate(prefix + "_wall", ga.wall_state(mdl(prefix + "_wall")))
    item_def(prefix + "_wall", mdl(prefix + "_wall_inventory"))


def cube(name, texture, translucent=False):
    t = {"force_translucent": True, "sprite": texture} if translucent else texture
    block_model(name, {"parent": mc("block/cube_all"), "textures": {"all": t}})


def assets():
    # icicles: the dripstone template (two crossed planes), our sprites
    variants = {}
    for direction in ("down", "up"):
        for part in ("base", "middle", "tip"):
            name = "icicle_%s_%s" % (direction, part)
            block_model(name, {"parent": mc("block/pointed_dripstone"), "textures": {"cross": tex(name)}})
            variants["part=%s,vertical_direction=%s" % (part, direction)] = {"model": mdl(name)}
    bstate("icicle", {"variants": variants})
    item_model("icicle", {"parent": mc("item/generated"), "textures": {"layer0": tex("icicle", "item")}})
    item_def("icicle", "%s:item/frost/icicle" % NS)

    # the ice crystal: an amethyst-style cluster turned to face the way it grows
    block_model("ice_crystal", {"parent": mc("block/cross"), "textures": {"cross": tex("ice_crystal")}})
    turns = {"up": {}, "down": {"x": 180}, "north": {"x": 90}, "east": {"x": 90, "y": 90}, "south": {"x": 90, "y": 180},
             "west": {"x": 90, "y": 270}}
    bstate("ice_crystal", {"variants": {"facing=" + f: dict({"model": mdl("ice_crystal")}, **r) for f, r in turns.items()}})
    item_model("ice_crystal", {"parent": mc("item/generated"), "textures": {"layer0": tex("ice_crystal")}})
    item_def("ice_crystal", "%s:item/frost/ice_crystal" % NS)

    # brittle ice: four cracks
    for stage in range(4):
        cube("brittle_ice_%d" % stage, tex("brittle_ice_%d" % stage), translucent=True)
    bstate("brittle_ice", {"variants": {"cracks=%d" % s: {"model": mdl("brittle_ice_%d" % s)} for s in range(4)}})
    item_def("brittle_ice", mdl("brittle_ice_0"))

    for name in ("packed_ice_bricks", "chiseled_packed_ice_bricks", "snow_bricks"):
        cube(name, tex(name))
        bstate(name, {"variants": {"": {"model": mdl(name)}}})
        item_def(name, mdl(name))
    family_models("packed_ice_brick", tex("packed_ice_bricks"), "packed_ice_bricks")
    family_models("snow_brick", tex("snow_bricks"), "snow_bricks")

    for name in ("ice_ball", "frostbite_spawn_egg"):
        item_model(name, {"parent": mc("item/generated"), "textures": {"layer0": tex(name, "item")}})
        item_def(name, "%s:item/frost/%s" % (NS, name))


# ---------------------------------------------------------------- data

def table(block, pools):
    write(os.path.join(DATA, NS, "loot_table", "blocks", block + ".json"),
          {"type": "minecraft:block", "pools": pools, "random_sequence": jp("blocks/" + block)})


def self_drop(block):
    table(block, [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": jp(block)}],
                   "condition": {"type": "minecraft:survives_explosion"}}])


def silk_only(block):
    table(block, [{"rolls": 1, "condition": "minecraft:tool/can_silk_touch", "entries": [{"type": "minecraft:item", "name": jp(block)}]}])


def slab_drop(block):
    table(block, [{"rolls": 1, "entries": [{"type": "minecraft:alternatives", "children": [
        {"type": "minecraft:item", "name": jp(block),
         "condition": {"type": "minecraft:match_block", "blocks": jp(block), "state": {"type": "double"}},
         "modifier": [{"type": "minecraft:set_count", "count": 2, "add": False}, {"type": "minecraft:explosion_decay"}]},
        {"type": "minecraft:item", "name": jp(block), "condition": {"type": "minecraft:survives_explosion"}}]}]}])


def loot():
    for b in ("packed_ice_bricks", "packed_ice_brick_stairs", "packed_ice_brick_wall", "chiseled_packed_ice_bricks",
              "snow_bricks", "snow_brick_stairs", "snow_brick_wall", "icicle"):
        self_drop(b)
    slab_drop("packed_ice_brick_slab")
    slab_drop("snow_brick_slab")
    silk_only("ice_crystal")
    silk_only("brittle_ice")

    def counted(item, lo, hi):
        return {"type": "minecraft:item", "name": item, "modifier": [
            {"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}},
            {"type": "minecraft:enchanted_count_increase", "count": {"type": "minecraft:uniform", "min": 0.0, "max": 1.0},
             "enchantment": "minecraft:looting"}]}

    write(os.path.join(DATA, NS, "loot_table", "entities", "frostbite.json"), {
        "type": "minecraft:entity",
        "pools": [{"rolls": 1, "entries": [counted("minecraft:rotten_flesh", 0, 2)]},
                  {"rolls": 1, "entries": [counted(jp("ice_ball"), 0, 2)]}],
        "random_sequence": jp("entities/frostbite")})


def recipe(name, data):
    write(os.path.join(DATA, NS, "recipe", name + ".json"), data)


def shaped(key, pattern, result, count=1, category="building"):
    out = {"type": "minecraft:crafting_shaped", "category": category, "key": key, "pattern": pattern, "result": {"id": result}}
    if count != 1:
        out["result"]["count"] = count
    return out


def cutting(src, result, count=1):
    out = {"type": "minecraft:stonecutting", "ingredient": src, "result": {"id": result}}
    if count != 1:
        out["result"]["count"] = count
    return out


def family_recipes(prefix, base, stonecutter):
    b = jp(base)
    recipe(prefix + "_stairs", shaped({"#": b}, ["#  ", "## ", "###"], jp(prefix + "_stairs"), 4))
    recipe(prefix + "_slab", shaped({"#": b}, ["###"], jp(prefix + "_slab"), 6))
    recipe(prefix + "_wall", shaped({"#": b}, ["###", "###"], jp(prefix + "_wall"), 6))
    if stonecutter:
        recipe(prefix + "_stairs_from_%s_stonecutting" % base, cutting(b, jp(prefix + "_stairs")))
        recipe(prefix + "_slab_from_%s_stonecutting" % base, cutting(b, jp(prefix + "_slab"), 2))
        recipe(prefix + "_wall_from_%s_stonecutting" % base, cutting(b, jp(prefix + "_wall")))


def brewing(name, input_item, input_potion, reagent, output_item, output_potion):
    write(os.path.join(DATA, NS, "recipe", "brewing", name + ".json"), {
        "type": "minecraft:brewing",
        "input": {"item": input_item, "potion_contents": {"potions": input_potion}},
        "output": {"components": {"minecraft:potion_contents": {"potion": output_potion}}, "id": output_item},
        "reagent": {"item": reagent}})


def recipes():
    recipe("packed_ice_bricks", shaped({"#": mc("packed_ice")}, ["##", "##"], jp("packed_ice_bricks"), 4))
    recipe("packed_ice_bricks_from_packed_ice_stonecutting", cutting(mc("packed_ice"), jp("packed_ice_bricks")))
    recipe("chiseled_packed_ice_bricks", shaped({"#": jp("packed_ice_brick_slab")}, ["#", "#"], jp("chiseled_packed_ice_bricks")))
    recipe("chiseled_packed_ice_bricks_from_packed_ice_bricks_stonecutting", cutting(jp("packed_ice_bricks"), jp("chiseled_packed_ice_bricks")))
    family_recipes("packed_ice_brick", "packed_ice_bricks", True)
    recipe("snow_bricks", shaped({"#": mc("snow_block")}, ["##", "##"], jp("snow_bricks"), 4))
    family_recipes("snow_brick", "snow_bricks", False)

    potion, splash, lingering = mc("potion"), mc("splash_potion"), mc("lingering_potion")
    fz, lfz = jp("freezing"), jp("long_freezing")
    for item in (potion, splash, lingering):
        short = item.split(":")[1]
        brewing("%s_awkward_ice_ball" % short, item, mc("awkward"), jp("ice_ball"), item, fz)
        brewing("%s_freezing_redstone" % short, item, fz, mc("redstone"), item, lfz)
    for p in (fz, lfz):
        short = p.split(":")[1]
        brewing("potion_%s_gunpowder" % short, potion, p, mc("gunpowder"), splash, p)
        brewing("splash_potion_%s_dragon_breath" % short, splash, p, mc("dragon_breath"), lingering, p)


def tags():
    t = os.path.join(DATA, "minecraft", "tags")
    pick = ["packed_ice_bricks", "packed_ice_brick_stairs", "packed_ice_brick_slab", "packed_ice_brick_wall",
            "chiseled_packed_ice_bricks", "icicle", "ice_crystal", "brittle_ice"]
    shovel = ["snow_bricks", "snow_brick_stairs", "snow_brick_slab", "snow_brick_wall"]
    merge_tag(os.path.join(t, "block", "mineable", "pickaxe.json"), [jp(b) for b in pick])
    merge_tag(os.path.join(t, "block", "mineable", "shovel.json"), [jp(b) for b in shovel])
    for kind in ("block", "item"):
        merge_tag(os.path.join(t, kind, "stairs.json"), [jp("packed_ice_brick_stairs"), jp("snow_brick_stairs")])
        merge_tag(os.path.join(t, kind, "slabs.json"), [jp("packed_ice_brick_slab"), jp("snow_brick_slab")])
        merge_tag(os.path.join(t, kind, "walls.json"), [jp("packed_ice_brick_wall"), jp("snow_brick_wall")])
    merge_tag(os.path.join(t, "block", "ice.json"), [jp("brittle_ice")])
    # (26.4) snow settles on packed ice: the game's list without packed ice, plus our own ices
    path = os.path.join(t, "block", "cannot_support_snow_layer.json")
    with open(path, encoding="utf-8") as f:
        ours = [v for v in json.load(f).get("values", []) if v.startswith(NS + ":")]
    merge_tag(path, None, replace=[mc("ice"), mc("barrier")] + ours + [jp("brittle_ice")])
    for tag in ("freeze_immune_entity_types", "powder_snow_walkable_mobs", "undead", "zombies"):
        merge_tag(os.path.join(t, "entity_type", tag + ".json"), [jp("frostbite")])


def main():
    textures()
    assets()
    loot()
    recipes()
    tags()


if __name__ == "__main__":
    main()
