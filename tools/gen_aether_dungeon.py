"""Writes the Sentry Ruins' art and data (Powers 0.8.x, Aether II phase 3 track D): the sentry stone family, the spawner, the trap, the
crate, the locked blocks and the doorways (textures painted here in vanilla style, our own designs; models, blockstates, item
definitions, loot tables, recipes, tags), the mobs' skins and spawn eggs and the golem's bomb, the crates' loot tables, the
structure's data and its biome tag, and the names in lang/parts/aether_dungeon.json.

    python -I tools/gen_aether_dungeon.py

Needs only Pillow. Nothing is copied from The Aether II or anyone else: the notes gave behaviour and numbers, the pictures are ours.
The skins are painted onto the boxes the models declare (this script READS the `part(...)` lines of
src/client/java/com/jaymospowers/client/aether/dungeon/*Model.java, like gen_aether_monsters.py). Every texture is seeded from its own
name, so a rerun changes nothing.
"""
import json
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_aether as ga  # the stone painters and blockstate helpers (only imported: it writes nothing until main())
import gen_aether_mobs as base
from gen_aether_mobs import Skin, faces, hx, ramp, shade, write, save, egg_image

ROOT = ga.ROOT
ASSETS = ga.ASSETS
DATA = ga.DATA
MCDATA = ga.MCDATA
NS = ga.NS
SUB = ga.SUB
MODELS = os.path.join(ROOT, "src", "client", "java", "com", "jaymospowers", "client", "aether", "dungeon")
ENTITY_DIR = os.path.join(ASSETS, "textures", "entity", SUB)
ITEM_DIR = os.path.join(ASSETS, "textures", "item", SUB)
BLOCK_DIR = os.path.join(ASSETS, "textures", "block", SUB)

tex = ga.tex
jp = ga.jp
mc = ga.mc
mdl = lambda name: "%s:block/%s/%s" % (NS, SUB, name)

# ---------------------------------------------------------------- the blocks

# (id, name, kind): the cubes with a lit and an unlit look (sentry current), the plain cubes, the shaped ones
LIT_CUBES = ["sentry_bricks", "sentry_base_bricks", "sentry_flagstones", "sentry_tile"]
PLAIN_CUBES = ["sentry_capstone_bricks", "sentry_lightstone", "treasure_doorway"]
# a locked block shows the texture of the block it stands for
LOCKED = {"locked_undershale": "undershale", "locked_undershale_bricks": "undershale_bricks", "locked_sentry_bricks": "sentry_bricks_lit",
          "locked_sentry_flagstones": "sentry_flagstones_lit", "locked_sentry_lightstone": "sentry_lightstone"}
SHAPED = [("sentry_brick", "sentry_bricks")]
ALL_BLOCKS = (LIT_CUBES + PLAIN_CUBES + ["sentry_pillar", "sentry_brick_stairs", "sentry_brick_slab", "sentry_brick_wall", "sentry_brick_button",
                                         "sentry_spawner", "sentry_trap", "sentry_crate"] + list(LOCKED) + ["boss_doorway"])

NAMES = {
    "sentry_bricks": "Sentry Bricks", "sentry_base_bricks": "Sentry Base Bricks", "sentry_capstone_bricks": "Sentry Capstone Bricks",
    "sentry_flagstones": "Sentry Flagstones", "sentry_tile": "Sentry Tile", "sentry_lightstone": "Sentry Lightstone",
    "sentry_pillar": "Sentry Pillar", "sentry_brick_stairs": "Sentry Brick Stairs", "sentry_brick_slab": "Sentry Brick Slab",
    "sentry_brick_wall": "Sentry Brick Wall", "sentry_brick_button": "Sentry Brick Button", "sentry_spawner": "Sentry Spawner",
    "sentry_trap": "Sentry Trap", "sentry_crate": "Sentry Crate", "locked_undershale": "Locked Undershale",
    "locked_undershale_bricks": "Locked Undershale Bricks", "locked_sentry_bricks": "Locked Sentry Bricks",
    "locked_sentry_flagstones": "Locked Sentry Flagstones", "locked_sentry_lightstone": "Locked Sentry Lightstone",
    "boss_doorway": "Boss Doorway", "treasure_doorway": "Treasure Doorway",
}

# ---------------------------------------------------------------- painting the blocks

SLATE = ramp("565c6c", "4c5262", "626878", "40465a")        # sentry stone: undershale with a warmer cast
SLATE_DARK = hx("2c3040")
MORTAR = [hx("2c3040"), hx("252938")]
BRONZE = ramp("8a6a34", "7a5c2c", "9a7a40")                 # an unlit rune
GLOW = ramp("ffcf5a", "ffe08a", "f2a93a", "fff3c4")         # a lit one
GOLD = ramp("e8c040", "f6d866", "c9a22c", "fff0a0")

RUNES = {
    "ring": ["..oooo..", ".o....o.", "o..oo..o", "o.o..o.o", "o.o..o.o", "o..oo..o", ".o....o.", "..oooo.."],
    "cross": ["...oo...", "...oo...", "...oo...", "oooooooo", "oooooooo", "...oo...", "...oo...", "...oo..."],
    "chevron": ["o......o", ".o....o.", "..o..o..", "...oo...", "...oo...", "..o..o..", ".o....o.", "o......o"],
}


def rune(im, kind, color, ox=4, oy=4):
    px = im.load()
    for y, row in enumerate(RUNES[kind]):
        for x, ch in enumerate(row):
            if ch == "o":
                px[ox + x, oy + y] = color + (255,)


def veins(im, rng, color, chance, only=None):
    """Joint lines (the darkest pixels) turned to glowing metal, here and there."""
    px = im.load()
    for y in range(16):
        for x in range(16):
            if px[x, y][:3] in only and rng.random() < chance:
                px[x, y] = rng.choice(color) + (255,)


def sentry_stone(name, lit, kind):
    rng = ga.seeded("dungeon:" + name + str(lit))
    if kind == "flag":
        im = ga.flagstones("dungeon:" + name, SLATE, MORTAR)
    elif kind == "tile":
        im = ga.new()
        px = im.load()
        for ty in range(4):
            for tx in range(4):
                c = SLATE[rng.randrange(len(SLATE))]
                for y in range(4):
                    for x in range(4):
                        edge = x == 3 or y == 3
                        px[tx * 4 + x, ty * 4 + y] = (SLATE_DARK if edge else c if rng.random() < 0.7 else SLATE[rng.randrange(len(SLATE))]) + (255,)
                px[tx * 4 + 1, ty * 4 + 1] = (GLOW[1] if lit else BRONZE[0]) + (255,)
                px[tx * 4 + 2, ty * 4 + 1] = (GLOW[0] if lit else BRONZE[1]) + (255,)
        return im
    else:
        im = ga.bricks("dungeon:" + name, SLATE, MORTAR)
    joints = set(MORTAR)
    veins(im, rng, GLOW if lit else BRONZE, 0.45 if lit else 0.12, joints)
    rune(im, {"flag": "cross", "base": "chevron"}.get(kind, "ring"), GLOW[0] if lit else BRONZE[0])
    if lit:
        rune(im, {"flag": "cross", "base": "chevron"}.get(kind, "ring"), GLOW[2], 4, 4)
        px = im.load()
        # the core of the rune burns brightest
        for x, y in ((7, 7), (8, 7), (7, 8), (8, 8)):
            px[x, y] = GLOW[3] + (255,)
    return im


def capstone():
    rng = ga.seeded("dungeon:capstone")
    im = ga.bricks("dungeon:capstone", SLATE, MORTAR)
    px = im.load()
    for y in range(3):
        for x in range(16):
            px[x, y] = (BRONZE[0] if y < 2 else BRONZE[1]) + (255,) if rng.random() < 0.85 else SLATE[0] + (255,)
    for x in range(16):
        px[x, 3] = MORTAR[0] + (255,)
    return im


def lightstone():
    rng = ga.seeded("dungeon:lightstone")
    im = ga.new()
    px = im.load()
    for y in range(16):
        for x in range(16):
            d = min(x, y, 15 - x, 15 - y)
            dist = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d == 0:
                c = hx("b07a1a")
            elif d == 1:
                c = hx("e0a030")
            elif x in (5, 10) and 2 <= y <= 13 or y in (5, 10) and 2 <= x <= 13:
                c = hx("f0b840")
            else:
                c = hx("fff3c4") if dist < 3 else hx("ffe08a") if dist < 5.5 else rng.choice([hx("ffd060"), hx("f6c24c"), hx("ffdc78")])
            px[x, y] = c + (255,)
    return im


def pillar_side():
    rng = ga.seeded("dungeon:pillar_side")
    im = ga.new()
    px = im.load()
    for y in range(16):
        for x in range(16):
            if y in (0, 1, 14, 15):
                c = BRONZE[0] if y in (1, 14) else BRONZE[1]
            elif x in (0, 15):
                c = SLATE_DARK
            elif x in (4, 5, 10, 11):
                c = SLATE[1] if rng.random() < 0.8 else SLATE[3]
            else:
                c = SLATE[0] if rng.random() < 0.75 else SLATE[2]
            px[x, y] = c + (255,)
    return im


def pillar_top():
    rng = ga.seeded("dungeon:pillar_top")
    im = ga.new()
    px = im.load()
    for y in range(16):
        for x in range(16):
            d = min(x, y, 15 - x, 15 - y)
            c = SLATE_DARK if d == 0 else BRONZE[0] if d == 1 else SLATE[2] if d in (2, 3) else SLATE[0] if rng.random() < 0.6 else SLATE[1]
            px[x, y] = c + (255,)
    rune(im, "ring", BRONZE[0])
    return im


def spawner(lit):
    rng = ga.seeded("dungeon:spawner" + str(lit))
    im = ga.new()
    px = im.load()
    for y in range(16):
        for x in range(16):
            d = min(x, y, 15 - x, 15 - y)
            c = SLATE_DARK if d == 0 else SLATE[2] if d == 1 else SLATE[0] if rng.random() < 0.7 else SLATE[1]
            px[x, y] = c + (255,)
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        px[x, y] = (GLOW[2] if lit else BRONZE[0]) + (255,)
    # the slot the sentries come out of, barred
    for y in range(3, 13):
        for x in range(5, 11):
            edge = x in (5, 10) or y in (3, 12)
            if edge:
                px[x, y] = (BRONZE[1] if not lit else GLOW[2]) + (255,)
            elif x in (7, 8):
                px[x, y] = hx("2c3040") + (255,)
            else:
                px[x, y] = (hx("14161e") if not lit else rng.choice([GLOW[0], GLOW[1], GLOW[3]])) + (255,)
    return im


def trap_spent():
    rng = ga.seeded("dungeon:trap_spent")
    im = ga.flagstones("dungeon:trap_spent", [shade(c, 0.78) for c in SLATE], [hx("1c1f2a"), hx("161923")])
    px = im.load()
    for _ in range(14):
        x, y = rng.randrange(16), rng.randrange(16)
        px[x, y] = rng.choice([hx("14161e"), hx("1c1f2a")]) + (255,)
    for x, y in ((3, 3), (4, 4), (5, 4), (10, 9), (11, 10), (9, 12)):
        px[x, y] = BRONZE[1] + (255,)
    return im


CRATE = ramp("a0703a", "8f622f", "b0804a", "7a5426")
STEEL = ramp("4a5a70", "5b6d86", "70849f", "3b4a5e")


def crate(kind):
    rng = ga.seeded("dungeon:crate_" + kind)
    im = ga.new()
    px = im.load()
    for y in range(16):
        for x in range(16):
            if kind == "top":
                d = min(x, y, 15 - x, 15 - y)
                c = STEEL[3] if d == 0 else STEEL[0] if d == 1 else CRATE[2] if (x // 4 + y // 4) % 2 else CRATE[0]
                if d > 1 and rng.random() < 0.2:
                    c = CRATE[rng.randrange(4)]
            else:
                if x in (0, 15) or (kind != "top" and y == 0):
                    c = STEEL[3]
                elif x in (1, 14) or y == 1:
                    c = STEEL[0]
                elif y == 15:
                    c = CRATE[3]
                else:
                    c = CRATE[0] if (y // 4) % 2 == 0 else CRATE[1]
                    if rng.random() < 0.25:
                        c = CRATE[rng.randrange(4)]
                    if y % 4 == 3:
                        c = CRATE[3]
            px[x, y] = c + (255,)
    if kind == "front":
        # a latch of gold on the seam between lid and body
        for y in range(4, 9):
            for x in range(6, 10):
                px[x, y] = (GOLD[0] if y < 7 else GOLD[2]) + (255,)
        px[7, 6] = hx("3a2a10") + (255,)
        px[8, 6] = hx("3a2a10") + (255,)
        px[7, 7] = hx("3a2a10") + (255,)
        px[8, 7] = hx("3a2a10") + (255,)
    return im


def treasure():
    rng = ga.seeded("dungeon:treasure")
    im = ga.bricks("dungeon:treasure", SLATE, MORTAR)
    px = im.load()
    for y in range(16):
        for x in range(16):
            d = min(x, y, 15 - x, 15 - y)
            if d == 0:
                px[x, y] = GOLD[2] + (255,)
            elif d == 1:
                px[x, y] = (GOLD[0] if rng.random() < 0.7 else GOLD[1]) + (255,)
    rune(im, "cross", GOLD[1])
    return im


def make_blocks():
    out = {}
    for name, kind in (("sentry_bricks", "brick"), ("sentry_base_bricks", "base"), ("sentry_flagstones", "flag"), ("sentry_tile", "tile")):
        out[name] = sentry_stone(name, False, kind)
        out[name + "_lit"] = sentry_stone(name, True, kind)
    out["sentry_capstone_bricks"] = capstone()
    out["sentry_lightstone"] = lightstone()
    out["sentry_pillar"] = pillar_side()
    out["sentry_pillar_top"] = pillar_top()
    out["sentry_spawner"] = spawner(False)
    out["sentry_spawner_lit"] = spawner(True)
    out["sentry_trap_spent"] = trap_spent()
    out["sentry_crate_top"] = crate("top")
    out["sentry_crate_side"] = crate("side")
    out["sentry_crate_front"] = crate("front")
    out["treasure_doorway"] = treasure()
    return out


# ---------------------------------------------------------------- the items (the bomb and the spawn eggs)

BOMB_ROWS = [
    "................",
    "...........ss...",
    "..........sYs...",
    ".........ff.....",
    "........f.......",
    ".....oooooo.....",
    "....oBBBBBBo....",
    "...oBhhBBBBBo...",
    "...oBhBBBBBBo...",
    "...oBBBBBBBDo...",
    "...oBBBBBBDDo...",
    "...oBBBBBDDDo...",
    "....oBBDDDDo....",
    ".....oooooo.....",
    "................",
    "................",
]
BOMB_PALETTE = {"o": (24, 24, 32), "B": (74, 79, 94), "h": (132, 138, 156), "D": (46, 50, 62), "f": (150, 110, 60), "s": (255, 200, 80), "Y": (255, 250, 200)}


def sprite(rows, palette):
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    for y, row in enumerate(rows):
        assert len(row) == 16, (y, row)
        for x, ch in enumerate(row):
            if ch != ".":
                px[x, y] = tuple(palette[ch]) + (255,)
    return im


EGGS = {
    "slider_spawn_egg": (hx("5b6070"), hx("ffcf5a"), hx("2c3040")),
    "detonation_sentry_spawn_egg": (hx("4a4f5e"), hx("ff6a3a"), hx("262a36")),
    "sentry_golem_spawn_egg": (hx("707686"), hx("e0a030"), hx("353a4a")),
    "mimic_spawn_egg": (hx("a0703a"), hx("f6d866"), hx("5a3e1c")),
}
ITEMS = ["demolition_bomb"] + list(EGGS)
ITEM_NAMES = {"demolition_bomb": "Demolition Bomb", "slider_spawn_egg": "Slider Spawn Egg", "detonation_sentry_spawn_egg": "Detonation Sentry Spawn Egg",
              "sentry_golem_spawn_egg": "Sentry Golem Spawn Egg", "mimic_spawn_egg": "Mimic Spawn Egg"}
ENTITY_NAMES = {"slider": "Slider", "detonation_sentry": "Detonation Sentry", "sentry_golem": "Sentry Golem", "mimic": "Mimic",
                "demolition_bomb": "Demolition Bomb"}

# ---------------------------------------------------------------- the skins


def parts_of(java):
    out = {}
    with open(os.path.join(MODELS, java), encoding="utf-8") as f:
        for line in f:
            m = base.PART.search(line)
            if m:
                out[m.group(1)] = dict(u=int(m.group(2)), v=int(m.group(3)), w=int(m.group(7)), h=int(m.group(8)), d=int(m.group(9)))
    return out


DARK = hx("14161e")
PALE = hx("d8d0c0")


def frame(sk, name, face, inset, color):
    x0, y0, w, h = faces(sk.parts[name])[face]
    sk.rect(name, face, inset, inset, w - 2 * inset, 1, color)
    sk.rect(name, face, inset, h - 1 - inset, w - 2 * inset, 1, color)
    sk.rect(name, face, inset, inset, 1, h - 2 * inset, color)
    sk.rect(name, face, w - 1 - inset, inset, 1, h - 2 * inset, color)


def bricks_on(sk, name, face, mortar, course=8):
    """Courses of stone over a face, the joints offset from course to course."""
    x0, y0, w, h = faces(sk.parts[name])[face]
    for y in range(h):
        for x in range(w):
            shift = (course * 2) if (y // course) % 2 else 0
            if y % course == course - 1 or (x + shift) % (course * 2) == course * 2 - 1:
                sk.at(name, face, x, y, mortar)


def slider(awake):
    sk = Skin("slider_awake" if awake else "slider", (128, 64), parts_of("SliderModel.java"))
    sk.box("body", SLATE, top=ramp("6a7084", "5e6478", "747a8e"), bottom=ramp("3a3f50", "343848"))
    for face in ("top", "bottom", "left", "right", "back", "front"):
        bricks_on(sk, "body", face, SLATE_DARK)
        frame(sk, "body", face, 1, GLOW[2] if awake else BRONZE[0])
        frame(sk, "body", face, 2, SLATE_DARK)
    # runes on every face but the front
    x0, y0, w, h = faces(sk.parts["body"])["back"]
    for face in ("top", "bottom", "left", "right", "back"):
        for dy in range(8):
            for dx in range(8):
                if RUNES["ring"][dy][dx] == "o":
                    for sx in (0, 1):
                        for sy in (0, 1):
                            sk.at("body", face, 8 + dx * 2 + sx, 8 + dy * 2 + sy, GLOW[0] if awake else BRONZE[0])
    # the carved face
    eye = hx("ff3a2a") if awake else hx("0e1018")
    for ex in (5, 20):
        sk.rect("body", "front", ex, 8, 7, 7, SLATE_DARK)
        if awake:
            sk.rect("body", "front", ex + 1, 9, 5, 5, hx("7a1410"))
            sk.rect("body", "front", ex + 2, 10, 3, 3, eye)
            sk.rect("body", "front", ex + 2, 10, 1, 1, hx("ffd0c0"))
        else:
            sk.rect("body", "front", ex, 10, 7, 3, DARK)
            sk.rect("body", "front", ex, 9, 7, 1, BRONZE[1])
    sk.rect("body", "front", 14, 14, 4, 6, SLATE_DARK)
    sk.rect("body", "front", 7, 22, 18, 5, DARK)
    for tx in range(8, 24, 3):
        sk.rect("body", "front", tx, 22, 2, 2, PALE)
        sk.rect("body", "front", tx + 1, 25, 2, 2, PALE)
    if awake:
        sk.rect("body", "front", 7, 21, 18, 1, GLOW[2])
        sk.rect("body", "front", 7, 27, 18, 1, GLOW[2])
    return sk


def detonation_sentry(state):
    name = "detonation_sentry" + {"asleep": "", "awake": "_awake", "lit": "_lit"}[state]
    sk = Skin(name, (64, 64), parts_of("DetonationSentryModel.java"))
    body = SLATE if state != "lit" else ramp("8a6a60", "7c5e56", "98786c", "6a4e48")
    sk.box("body", body)
    for face in ("top", "bottom", "left", "right", "back", "front"):
        bricks_on(sk, "body", face, SLATE_DARK, 7)
    eye = {"asleep": hx("5a4a28"), "awake": hx("ffb030"), "lit": hx("ff3a2a")}[state]
    for ex in (3, 9):
        sk.rect("body", "front", ex, 2, 3, 3, DARK)
        sk.rect("body", "front", ex, 3, 3, 2, eye)
    # a grille for a mouth
    sk.rect("body", "front", 4, 6, 6, 3, DARK)
    for gx in (5, 7, 9):
        sk.rect("body", "front", gx, 6, 1, 3, BRONZE[1])
    sk.box("lid", ramp("7a5c2c", "6a4c22", "8a6a34"))
    sk.rect("lid", "top", 2, 2, 2, 2, hx("ff3a2a") if state == "lit" else GLOW[2] if state == "awake" else BRONZE[0])
    for n in ("leg_0", "leg_1", "leg_2", "leg_3"):
        sk.box(n, ramp("3a3f50", "343848", "424860"))
    return sk


def sentry_golem():
    sk = Skin("sentry_golem", (64, 64), parts_of("SentryGolemModel.java"))
    sk.box("head", SLATE)
    bricks_on(sk, "head", "front", SLATE_DARK, 4)
    sk.rect("head", "front", 1, 3, 2, 2, DARK)
    sk.rect("head", "front", 5, 3, 2, 2, DARK)
    sk.rect("head", "front", 1, 3, 2, 1, GLOW[0])
    sk.rect("head", "front", 5, 3, 2, 1, GLOW[0])
    sk.rect("head", "front", 2, 6, 4, 1, DARK)
    sk.rect("head", "top", 3, 3, 2, 2, BRONZE[0])
    sk.box("body", SLATE)
    sk.rect("body", "front", 1, 1, 8, 10, BRONZE[1])
    sk.rect("body", "front", 2, 2, 6, 8, SLATE[1])
    sk.rect("body", "front", 4, 4, 2, 3, GLOW[1])
    sk.rect("body", "front", 4, 4, 2, 1, GLOW[3])
    sk.outline("body", ["front"], SLATE_DARK)
    sk.box("satchel", ramp("8a5a30", "7a4c28", "9a6a3a", "623c1e"))
    sk.outline("satchel", ["top", "left", "right", "front", "back"], hx("3e2410"))
    sk.rect("satchel", "back", 2, 1, 4, 2, hx("14161e"))
    for n in ("arm_l", "arm_r"):
        sk.box(n, SLATE)
        for face in ("front", "back", "left", "right"):
            sk.rect(n, face, 0, 2, 4, 1, BRONZE[0])
            sk.rect(n, face, 0, 9, 4, 1, BRONZE[0])
    for n in ("leg_l", "leg_r"):
        sk.box(n, ramp("4c5262", "40465a", "565c6c"))
        for face in ("front", "back", "left", "right"):
            sk.rect(n, face, 0, 4, 4, 1, BRONZE[1])
            sk.rect(n, face, 0, 11, 4, 2, SLATE_DARK)
    return sk


def mimic():
    sk = Skin("mimic", (64, 64), parts_of("MimicModel.java"))
    sk.box("base", CRATE)
    sk.box("lid", CRATE)
    for n in ("base", "lid"):
        for face in ("front", "back", "left", "right"):
            x0, y0, w, h = faces(sk.parts[n])[face]
            sk.rect(n, face, 0, 0, 1, h, STEEL[3])
            sk.rect(n, face, w - 1, 0, 1, h, STEEL[3])
            sk.rect(n, face, 1, 0, 1, h, STEEL[0])
            sk.rect(n, face, w - 2, 0, 1, h, STEEL[0])
    sk.rect("lid", "front", 6, 3, 2, 2, GOLD[0])
    sk.rect("lid", "front", 6, 5, 2, 1, GOLD[2])
    sk.rect("lid", "bottom", 0, 0, 14, 14, hx("4a2a1c"))
    sk.rect("base", "top", 0, 0, 14, 14, hx("4a2a1c"))
    # teeth round the mouth, on the lid's underside and the body's rim
    for tx in range(1, 13, 3):
        sk.rect("lid", "bottom", tx, 12, 2, 2, PALE)
        sk.rect("lid", "bottom", tx, 0, 2, 2, PALE)
        sk.rect("base", "top", tx, 12, 2, 2, PALE)
        sk.rect("base", "top", tx, 0, 2, 2, PALE)
    sk.box("tongue", ramp("c8362a", "b02a22", "dc4a38"))
    for n in ("leg_0", "leg_1", "leg_2", "leg_3"):
        sk.box(n, ramp("3a3f50", "343848", "424860"))
    return sk


# ---------------------------------------------------------------- files


def textures():
    for name, im in make_blocks().items():
        save(im, os.path.join(BLOCK_DIR, name + ".png"))
    for name, sk in (("slider", slider(False)), ("slider_awake", slider(True)), ("detonation_sentry", detonation_sentry("asleep")),
                     ("detonation_sentry_awake", detonation_sentry("awake")), ("detonation_sentry_lit", detonation_sentry("lit")),
                     ("sentry_golem", sentry_golem()), ("mimic", mimic())):
        save(sk.im, os.path.join(ENTITY_DIR, name + ".png"))
    save(sprite(BOMB_ROWS, BOMB_PALETTE), os.path.join(ITEM_DIR, "demolition_bomb.png"))
    for name, (c, spot, outline) in EGGS.items():
        save(egg_image(c, spot, outline, name, 8, ring=True), os.path.join(ITEM_DIR, name + ".png"))


def assets():
    bs = os.path.join(ASSETS, "blockstates")

    def bstate(name, data):
        write(os.path.join(bs, name + ".json"), data)

    def item_def(name, model):
        write(os.path.join(ASSETS, "items", name + ".json"), {"model": {"type": "minecraft:model", "model": model}})

    def cube(name, texture):
        ga.block_model(name, {"parent": mc("block/cube_all"), "textures": {"all": tex(texture)}})

    state = lambda model, **rot: dict({"model": mdl(model)}, **rot)
    # the sentry stone: lit and unlit, with and without a signal on it
    for b in LIT_CUBES:
        cube(b, b)
        cube(b + "_lit", b + "_lit")
        bstate(b, {"variants": {"lit=%s,powered=%s" % (l, p): state(b if l == "false" else b + "_lit") for l in ("false", "true") for p in ("false", "true")}})
        item_def(b, mdl(b))
    for b in ("sentry_capstone_bricks", "sentry_lightstone", "treasure_doorway"):
        cube(b, b)
        bstate(b, {"variants": {"": state(b)}})
        item_def(b, mdl(b))
    # the pillar
    ga.block_model("sentry_pillar", {"parent": mc("block/cube_column"), "textures": {"end": tex("sentry_pillar_top"), "side": tex("sentry_pillar")}})
    ga.block_model("sentry_pillar_horizontal", {"parent": mc("block/cube_column_horizontal"),
                                                "textures": {"end": tex("sentry_pillar_top"), "side": tex("sentry_pillar")}})
    bstate("sentry_pillar", {"variants": {"axis=x": state("sentry_pillar_horizontal", x=90, y=90), "axis=y": state("sentry_pillar"),
                                          "axis=z": state("sentry_pillar_horizontal", x=90)}})
    item_def("sentry_pillar", mdl("sentry_pillar"))
    # stairs, slab, wall, button, all in the sentry bricks' look
    t = {"bottom": tex("sentry_bricks"), "side": tex("sentry_bricks"), "top": tex("sentry_bricks")}
    ga.block_model("sentry_brick_stairs", {"parent": mc("block/stairs"), "textures": t})
    ga.block_model("sentry_brick_stairs_inner", {"parent": mc("block/inner_stairs"), "textures": t})
    ga.block_model("sentry_brick_stairs_outer", {"parent": mc("block/outer_stairs"), "textures": t})
    bstate("sentry_brick_stairs", {"variants": ga.stairs_state(mdl("sentry_brick_stairs"))})
    item_def("sentry_brick_stairs", mdl("sentry_brick_stairs"))
    ga.block_model("sentry_brick_slab", {"parent": mc("block/slab"), "textures": t})
    ga.block_model("sentry_brick_slab_top", {"parent": mc("block/slab_top"), "textures": t})
    bstate("sentry_brick_slab", {"variants": {"type=bottom": state("sentry_brick_slab"), "type=double": state("sentry_bricks"),
                                              "type=top": state("sentry_brick_slab_top")}})
    item_def("sentry_brick_slab", mdl("sentry_brick_slab"))
    w = {"wall": tex("sentry_bricks")}
    ga.block_model("sentry_brick_wall_post", {"parent": mc("block/template_wall_post"), "textures": w})
    ga.block_model("sentry_brick_wall_side", {"parent": mc("block/template_wall_side"), "textures": w})
    ga.block_model("sentry_brick_wall_side_tall", {"parent": mc("block/template_wall_side_tall"), "textures": w})
    ga.block_model("sentry_brick_wall_inventory", {"parent": mc("block/wall_inventory"), "textures": w})
    bstate("sentry_brick_wall", ga.wall_state(mdl("sentry_brick_wall")))
    item_def("sentry_brick_wall", mdl("sentry_brick_wall_inventory"))
    bt = {"texture": tex("sentry_bricks")}
    ga.block_model("sentry_brick_button", {"parent": mc("block/button"), "textures": bt})
    ga.block_model("sentry_brick_button_pressed", {"parent": mc("block/button_pressed"), "textures": bt})
    ga.block_model("sentry_brick_button_inventory", {"parent": mc("block/button_inventory"), "textures": bt})
    bstate("sentry_brick_button", {"variants": ga.button_state(mdl("sentry_brick_button"), mdl("sentry_brick_button_pressed"))})
    item_def("sentry_brick_button", mdl("sentry_brick_button_inventory"))
    # the spawner: lit and unlit
    cube("sentry_spawner", "sentry_spawner")
    cube("sentry_spawner_lit", "sentry_spawner_lit")
    bstate("sentry_spawner", {"variants": {"lit=%s,powered=%s" % (l, p): state("sentry_spawner" if l == "false" else "sentry_spawner_lit")
                                           for l in ("false", "true") for p in ("false", "true")}})
    item_def("sentry_spawner", mdl("sentry_spawner"))
    # the trap: looks like flagstones until it has gone off
    cube("sentry_trap_spent", "sentry_trap_spent")
    bstate("sentry_trap", {"variants": {"loaded=true": state("sentry_flagstones"), "loaded=false": state("sentry_trap_spent")}})
    item_def("sentry_trap", mdl("sentry_flagstones"))
    # the crate: a chest-shaped box 14 tall, its latch to the front
    faces_ = ga.box_faces((1, 0, 1), (15, 14, 15), "#side")
    faces_["up"]["texture"] = faces_["down"]["texture"] = "#top"
    faces_["north"]["texture"] = "#front"
    ga.block_model("sentry_crate", {"parent": mc("block/block"), "textures": {
        "particle": tex("sentry_crate_side"), "top": tex("sentry_crate_top"), "side": tex("sentry_crate_side"), "front": tex("sentry_crate_front")},
        "elements": [{"from": [1, 0, 1], "to": [15, 14, 15], "faces": faces_}]})
    bstate("sentry_crate", {"variants": {"facing=north": state("sentry_crate"), "facing=east": state("sentry_crate", y=90),
                                         "facing=south": state("sentry_crate", y=180), "facing=west": state("sentry_crate", y=270)}})
    item_def("sentry_crate", mdl("sentry_crate"))
    # locked blocks wear the look of what they stand for; the boss doorway, closed, looks like locked sentry bricks
    for b, look in LOCKED.items():
        cube(b, look)
        bstate(b, {"variants": {"": state(b)}})
        item_def(b, mdl(b))
    cube("boss_doorway", "sentry_bricks_lit")
    bstate("boss_doorway", {"variants": {"closed=true": state("boss_doorway"), "closed=false": state("boss_doorway")}})
    item_def("boss_doorway", mdl("boss_doorway"))
    # the bomb and the eggs
    for name in ITEMS:
        write(os.path.join(ASSETS, "models", "item", SUB, name + ".json"), {"parent": mc("item/generated"), "textures": {"layer0": tex(name, "item")}})
        item_def(name, "%s:item/%s/%s" % (NS, SUB, name))


# ---------------------------------------------------------------- loot

SURVIVES = ga.SURVIVES
GIVES_NOTHING = {"type": "minecraft:block", "pools": []}


def block_loot():
    out = os.path.join(DATA, "loot_table", "blocks")
    tables = {}
    for b in LIT_CUBES + ["sentry_capstone_bricks", "sentry_lightstone", "sentry_pillar", "sentry_brick_stairs", "sentry_brick_wall", "sentry_brick_button",
                          "sentry_spawner", "sentry_trap", "sentry_crate"]:
        tables[b] = ga.self_table(b)
    double = {"type": "minecraft:match_block", "blocks": jp("sentry_brick_slab"), "state": {"type": "double"}}
    tables["sentry_brick_slab"] = ga.table("sentry_brick_slab", [{"rolls": 1, "entries": [{"type": "minecraft:alternatives", "children": [
        ga.item_entry(jp("sentry_brick_slab"), condition=double, modifier=[{"type": "minecraft:set_count", "count": 2, "add": False}, {"type": "minecraft:explosion_decay"}]),
        ga.item_entry(jp("sentry_brick_slab"), condition=SURVIVES)]}]}])
    # the walls of a boss room and its doors give nothing (the blocks are made without a loot table; this is for tidiness)
    for b in list(LOCKED) + ["boss_doorway", "treasure_doorway"]:
        tables[b] = dict(GIVES_NOTHING, random_sequence="%s:blocks/%s" % (NS, b))
    missing = [b for b in ALL_BLOCKS if b not in tables]
    assert not missing, missing
    for b, t in tables.items():
        write(os.path.join(out, b + ".json"), t)


def item(name, lo=1, hi=1, weight=1):
    entry = {"type": "minecraft:item", "name": name, "weight": weight}
    if (lo, hi) != (1, 1):
        entry["modifier"] = [{"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}]
    return entry


def pool(rolls, entries, bonus=None):
    p = {"rolls": rolls if isinstance(rolls, int) else {"type": "minecraft:uniform", "min": rolls[0], "max": rolls[1]}, "entries": entries}
    return p


def chest(name, pools):
    write(os.path.join(DATA, "loot_table", "chests", "sentry_ruins", name + ".json"),
          {"type": "minecraft:chest", "pools": pools, "random_sequence": "%s:chests/sentry_ruins/%s" % (NS, name)})


# what the notes' loot becomes with the gear we have: the Aether's own tools, armour and gliders (track G), vanilla's crossbow and
# shield, vanilla discs, our fruit and golden apples. Their zanite gloves, charms, pendants and Hammer of Demolition are not made.
RARE_GEAR = [item(jp("holystone_pickaxe"), weight=2), item(mc("crossbow"), weight=2), item(jp("zanite_pickaxe"), weight=2), item(jp("zanite_hammer")),
             item(jp("burrukai_plate_helmet")), item(jp("burrukai_plate_chestplate")), item(jp("burrukai_plate_leggings")),
             item(jp("burrukai_plate_boots")), item(mc("shield"), weight=2), item(jp("beast_pelt"), 2, 4, 3),
             item(jp("cold_aercloud_glider"), weight=3), item(jp("golden_aercloud_glider")),
             item(jp("cold_aercloud"), 2, 4, 3), item(jp("golden_aercloud"), 1, 2), item(jp("arkenium_lantern"), 1, 2), item(mc("saddle"), weight=2),
             item(jp("enchanted_gravitite"), 1, 2, 2), item(mc("golden_apple"), 1, 1, 2), item(mc("music_disc_cat"))]
NEPTUNE = [item(jp("neptune_" + p)) for p in ("helmet", "chestplate", "leggings", "boots")]
ZANITE_ARMOR = [item(jp("zanite_" + p), weight=2) for p in ("helmet", "chestplate", "leggings", "boots")]


def loot():
    rare = pool(1, RARE_GEAR)
    common = [
        pool((1, 2), RARE_GEAR),
        pool((1, 2), [item(jp("ambrosium_torch"), 2, 6, 14), item(jp("scatterglass_shard"), 2, 5, 8), item(jp("holystone_pickaxe"), weight=2),
                      item(jp("holystone_hammer"), weight=1), item(mc("crossbow"), weight=1)]),
        pool((1, 3), [item(jp("ambrosium_shard"), 2, 5, 5), item(jp("scatterglass_shard"), 1, 4, 3), item(jp("golden_amber"), 1, 3, 3),
                      item(jp("zanite_gemstone"), 1, 2, 2), item(jp("inert_arkenium"), 1, 2, 1),
                      item(jp("burrukai_plate"), 1, 3, 2)]),
        pool((0, 2), [item(jp("orange"), 1, 3, 3), item(jp("blue_berry"), 1, 3, 3), item(jp("wyndberry"), 1, 2, 2), item(jp("satival_bulb"), 1, 2, 2)]),
        pool((2, 4), [item(jp("cloudtwine"), 1, 3, 3), item(jp("swet_gel"), 1, 3, 2), item(jp("swet_sugar"), 1, 2, 1), item(jp("cockatrice_feather"), 1, 2, 1),
                      item(jp("beast_pelt"), 1, 2, 2), item(jp("glint_coin"), 1, 3, 2)]),
    ]
    chest("common", common)
    chest("rare", [rare])
    chest("deposit", common + [pool((2, 4), [item(jp("holystone"), 4, 12, 4), item(jp("undershale"), 4, 12, 4), item(jp("agiosite"), 3, 8, 3),
                                             item(jp("undershale_zanite_ore"), 1, 3, 2), item(jp("undershale_ambrosium_ore"), 1, 3, 2),
                                             item(jp("undershale_glint_ore"), 1, 2, 1)])])
    chest("cold", common + [pool((2, 4), [item(jp("icestone"), 4, 10, 4), item(jp("arctic_packed_ice"), 3, 8, 2), item(jp("arctic_snow_block"), 2, 6, 2),
                                          item(jp("swet_gel"), 2, 4, 2), item(jp("icestone_bricks"), 3, 6, 2)])])
    chest("boss", [
        pool(1, [item(mc("music_disc_otherside")), item(mc("music_disc_stal")), item(mc("music_disc_mellohi")), item(jp("irradiated_holystone"), 4, 8)]),
        # two guaranteed pieces of neptune armour, then a chance of more (or of zanite armour)
        pool(2, NEPTUNE),
        pool(1, ZANITE_ARMOR + [dict(piece, weight=1) for piece in NEPTUNE]),
        pool((1, 2), [item(jp("zanite_pickaxe"), weight=3), item(jp("zanite_hammer"), weight=3), item(jp("zanite_axe"), weight=2),
                      item(jp("zanite_shortsword"), weight=2), item(mc("shield"), weight=1), item(jp("zanite_shears"), weight=1),
                      item(jp("arkenium_lantern"), 1, 2, 1)]),
        pool((0, 1), [item(jp("golden_aercloud_glider"), weight=2), item(jp("blue_aercloud_glider"), weight=2), item(jp("purple_aercloud_glider"), weight=2),
                      item(jp("cold_aercloud_glider"), weight=1)]),
        pool((1, 3), [item(jp("zanite_gemstone"), 2, 5), item(jp("inert_arkenium"), 2, 5), item(jp("glint_gemstone"), 1, 3), item(jp("enchanted_gravitite"), 1, 3)]),
        pool((2, 3), [item(jp("enchanted_blue_berry"), 1, 3, 3), item(jp("enchanted_orange"), 1, 3, 2), item(mc("golden_apple"), 1, 2, 2),
                      item(jp("enchanted_wyndberry"), 1, 2, 1), item(mc("enchanted_golden_apple"), 1, 1, 1)]),
        pool((2, 3), [item(jp("glint_coin"), 3, 8, 3), item(jp("glint_gemstone"), 1, 2, 1)]),
        pool((1, 2), [item(jp("zanite_gemstone"), 3, 6, 2), item(jp("arkenium_plate"), 1, 3, 2), item(jp("inert_arkenium"), 2, 4, 1),
                      item(jp("golden_amber_block"), 1, 1, 1)]),
    ])


# ---------------------------------------------------------------- recipes and tags


def recipes():
    out = os.path.join(DATA, "recipe", SUB)
    shaped = lambda key, pattern, result, count=1: {"type": "minecraft:crafting_shaped", "category": "building", "key": key, "pattern": pattern,
                                                    "result": {"id": result, "count": count} if count > 1 else {"id": result}}
    cut = lambda ing, result, count=1: {"type": "minecraft:stonecutting", "ingredient": ing,
                                        "result": {"id": result, "count": count} if count > 1 else {"id": result}}
    b = jp("sentry_bricks")
    r = {
        "sentry_brick_stairs": shaped({"#": b}, ["#  ", "## ", "###"], jp("sentry_brick_stairs"), 4),
        "sentry_brick_slab": shaped({"#": b}, ["###"], jp("sentry_brick_slab"), 6),
        "sentry_brick_wall": shaped({"#": b}, ["###", "###"], jp("sentry_brick_wall"), 6),
        "sentry_brick_button": {"type": "minecraft:crafting_shapeless", "category": "redstone", "ingredients": [b], "result": {"id": jp("sentry_brick_button")}},
        "sentry_pillar": shaped({"#": b}, ["#", "#"], jp("sentry_pillar"), 2),
        "sentry_flagstones": shaped({"#": b}, ["##", "##"], jp("sentry_flagstones"), 4),
        "sentry_tile": shaped({"#": jp("sentry_flagstones")}, ["##", "##"], jp("sentry_tile"), 4),
    }
    for result, count in (("sentry_brick_stairs", 1), ("sentry_brick_slab", 2), ("sentry_brick_wall", 1), ("sentry_pillar", 1), ("sentry_flagstones", 1),
                          ("sentry_capstone_bricks", 1), ("sentry_base_bricks", 1)):
        r["%s_from_sentry_bricks_stonecutting" % result] = cut(b, jp(result), count)
    for name, data in r.items():
        write(os.path.join(out, name + ".json"), data)


def tags():
    b = os.path.join(MCDATA, "tags", "block")
    i = os.path.join(MCDATA, "tags", "item")
    jb = os.path.join(DATA, "tags", "block")
    ids = lambda names: [jp(n) for n in names]
    stone = LIT_CUBES + ["sentry_capstone_bricks", "sentry_lightstone", "sentry_pillar", "sentry_brick_stairs", "sentry_brick_slab", "sentry_brick_wall",
                         "sentry_brick_button", "sentry_spawner", "sentry_trap"]
    ga.merge_tag(os.path.join(b, "mineable", "pickaxe.json"), ids(stone + ["sentry_crate"]))
    ga.merge_tag(os.path.join(b, "needs_stone_tool.json"), ids(stone))
    for root in (b, i):
        ga.merge_tag(os.path.join(root, "stairs.json"), [jp("sentry_brick_stairs")])
        ga.merge_tag(os.path.join(root, "slabs.json"), [jp("sentry_brick_slab")])
        ga.merge_tag(os.path.join(root, "walls.json"), [jp("sentry_brick_wall")])
        ga.merge_tag(os.path.join(root, "stone_buttons.json"), [jp("sentry_brick_button")])
    unbreakable = list(LOCKED) + ["boss_doorway", "treasure_doorway"]
    for tag in ("wither_immune", "dragon_immune"):
        ga.merge_tag(os.path.join(b, tag + ".json"), ids(unbreakable + ["sentry_crate"]))
    write(os.path.join(jb, "slider_immune.json"), {"values": ids(unbreakable)})
    write(os.path.join(jb, "carries_sentry_current.json"), {"values": ids(["sentry_bricks", "sentry_base_bricks", "sentry_flagstones", "sentry_tile", "sentry_spawner"])})


# ---------------------------------------------------------------- the structure

# every Aether biome the notes allow (all but the Hestveil caverns and the Expanse)
BIOMES = ["shimmering_basin", "flourishing_field", "verdant_woods", "shrouded_forest", "violet_highwoods", "turquoise_forest", "glistening_swamp",
          "magnetic_scar", "frozen_lakes", "sheer_tundra", "enduring_woodland", "frigid_sierra", "battleground_wastes", "contaminated_jungle"]


def structure():
    write(os.path.join(DATA, "tags", "worldgen", "biome", "has_structure", "sentry_ruins.json"), {"values": [jp(b) for b in BIOMES]})
    write(os.path.join(DATA, "worldgen", "structure", "sentry_ruins.json"), {
        "type": jp("sentry_ruins"), "biomes": "#" + jp("has_structure/sentry_ruins"), "step": "underground_structures", "spawn_overrides": {},
        "terrain_adaptation": "none"})
    # spacing 12 and separation 6 chunks, triangular, like the notes' underground complexes
    write(os.path.join(DATA, "worldgen", "structure_set", "sentry_ruins.json"), {
        "structures": [{"structure": jp("sentry_ruins"), "weight": 1}],
        "placement": {"type": "minecraft:random_spread", "spacing": 12, "separation": 6, "salt": 1380213021, "spread_type": "triangular"}})


# ---------------------------------------------------------------- lang


def lang():
    mine = {"block.%s.%s" % (NS, b): n for b, n in NAMES.items()}
    mine.update({"item.%s.%s" % (NS, k): v for k, v in ITEM_NAMES.items()})
    mine.update({"entity.%s.%s" % (NS, k): v for k, v in ENTITY_NAMES.items()})
    mine["message.jaymospowers.slider.pickaxe"] = "Only a pickaxe will hurt it."
    mine["message.jaymospowers.slider.too_far"] = "You must be in its room to hurt it."
    write(os.path.join(ASSETS, "lang", "parts", "aether_dungeon.json"), mine)


def main():
    textures()
    assets()
    block_loot()
    loot()
    recipes()
    tags()
    structure()
    lang()
    print("Sentry Ruins art and data written.")


if __name__ == "__main__":
    main()
