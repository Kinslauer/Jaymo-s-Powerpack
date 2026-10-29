"""Draws and writes Jaymo's Kingdoms' whaling assets (0.7.206), all original and procedural:

- textures/entity/whale/rorqual.png and sperm.png (128x128): the great whale's two skins, painted face by face onto the
  UVs of GreatWhaleModel's boxes (keep BOXES in step with the model): dark backs fading to pale bellies, throat grooves
  under the rorqual's jaw and barnacle specks on its head, the sperm whale wrinkled and brown-grey all over.
- (stage 4) textures/entity/whale/white.png: the white whale, pale and wrinkled, scarred by old irons.
- textures/entity/whale/harpoon.png (64x32): the thrown harpoon's ash shaft and iron head (HarpoonRenderer's boxes).
- textures/item/: harpoon, whale_blubber, whale_meat, cooked_whale_meat, whale_bone, ambergris, great_whale_spawn_egg,
  whale_oil; and their items/ and models/item/ JSON.
- (stage 2) textures/block/tryworks_front.png and tryworks_front_on.png: the tryworks' brick firebox, cold and burning;
  and its blockstate, block models (bricks below, an iron try-pot on top, vanilla's cauldron textures) and item JSON.
- (whale oil's uses) textures/block/oil_lamp.png and oil_lamp_lit.png (brass, glass chimney, chain) with the oil lamp's
  standing and hanging models; textures/block/oil_pot_brazier.png and _lit.png with the boiling oil pot's model; item
  sprites oil_lamp, firebomb, powder_firebomb.

Seeded, so every run draws the same pixels. Run from kingdoms/: python3 -I tools/gen_whaling.py"""
import json
import math
import os
import random

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "src", "main", "resources", "assets", "jaymoskingdoms")

# GreatWhaleModel's boxes: name -> (u, v, w, h, d)
BOXES = {
    "body": (0, 0, 12, 12, 22),
    "head": (0, 34, 10, 8, 15),
    "jaw": (0, 60, 9, 3, 14),
    "fin": (56, 46, 10, 2, 5),
    "stock": (68, 0, 8, 8, 12),
    "dorsal": (90, 46, 1, 3, 4),
    "tail": (68, 20, 5, 5, 10),
    "flukes": (56, 36, 20, 2, 7),
}


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", newline="\n") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def clamp(c):
    return tuple(max(0, min(255, int(round(x)))) for x in c)


def mix(a, b, t):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def shade(c, k):
    return tuple(x * k for x in c)


def faces(u, v, w, h, d):
    """The six faces of a box's UV layout: name -> (x0, y0, width, height)."""
    return {
        "up": (u + d, v, w, d),
        "down": (u + d + w, v, w, d),
        "east": (u, v + d, d, h),
        "north": (u + d, v + d, w, h),
        "west": (u + d + w, v + d, d, h),
        "south": (u + 2 * d + w, v + d, w, h),
    }


def paint_whale(back, belly, seed, grooves, barnacles, wrinkles, scars=False):
    rnd = random.Random(seed)
    img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    px = img.load()
    for name, (u, v, w, h, d) in BOXES.items():
        for face, (x0, y0, fw, fh) in faces(u, v, w, h, d).items():
            for y in range(fh):
                for x in range(fw):
                    if face == "up":
                        c = back
                    elif face == "down":
                        c = belly if name in ("body", "head", "jaw", "stock") else mix(back, belly, 0.35)
                    else:
                        # sides fade from the dark back (top row) to the pale belly (bottom row)
                        t = (y + 0.5) / fh
                        t = max(0.0, (t - 0.45) / 0.55) if name in ("body", "head", "jaw", "stock") else t * 0.3
                        c = mix(back, belly, t * t)
                    n = rnd.uniform(0.9, 1.08)
                    if wrinkles and rnd.random() < 0.18:
                        n *= 0.85
                    c = shade(c, n)
                    if grooves and name == "jaw" and face == "down" and x % 2 == 0:
                        c = shade(c, 0.7)
                    if barnacles and name == "head" and face in ("up", "north") and rnd.random() < 0.07:
                        c = (205, 200, 185)
                    if scars and face != "down" and (x * 3 + y * 5 + seed) % 23 == 0:
                        # old harpoon scars and rake marks, grey against the white
                        c = (128, 122, 116)
                    px[x0 + x, y0 + y] = clamp(c) + (255,)
    return img


def harpoon_texture():
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    px = img.load()
    rnd = random.Random(7)
    ash, iron, rope = (150, 112, 70), (120, 124, 130), (178, 150, 100)

    def fill(u, v, w, h, d, colour, grain):
        for face, (x0, y0, fw, fh) in faces(u, v, w, h, d).items():
            for y in range(fh):
                for x in range(fw):
                    c = shade(colour, rnd.uniform(0.85, 1.1))
                    if grain and (x + y) % 5 == 0:
                        c = shade(c, 0.85)
                    px[x0 + x, y0 + y] = clamp(c) + (255,)

    fill(0, 0, 1, 1, 24, ash, True)
    fill(0, 25, 3, 1, 5, iron, False)
    fill(20, 25, 2, 2, 2, rope, True)
    return img


def sprite(pattern, palette):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y, row in enumerate(pattern):
        for x, ch in enumerate(row):
            if ch != ".":
                px[x, y] = palette[ch] + (255,)
    return img


ITEMS = {
    "harpoon": ([
        "............OOH.",
        "...........OOHH.",
        "..........OHHH..",
        ".........WOHO...",
        "........WWO.....",
        ".......WW.......",
        "......WW........",
        ".....WW.........",
        "....WW..........",
        "...WR...........",
        "..RWR...........",
        ".R.RRR..........",
        "R...R.R.........",
        "R..R...R........",
        ".RR....R........",
        "......R.........",
    ], {"O": (110, 114, 122), "H": (175, 180, 188), "W": (140, 100, 60), "R": (190, 160, 105)}),
    "whale_blubber": ([
        "................",
        "................",
        "....DDDDDDD.....",
        "...DGGGGGGGD....",
        "..DCCCCCCCCCD...",
        "..DCYCCCCCYCD...",
        "..DCCCCYCCCCCD..",
        "..DCCCCCCCCYCD..",
        "..DCYCCCCCCCCD..",
        "..DCCCCCYCCCCD..",
        "...DCCCCCCCCD...",
        "....DDDDDDDD....",
        "................",
        "................",
        "................",
        "................",
    ], {"D": (90, 84, 70), "G": (60, 62, 66), "C": (236, 222, 176), "Y": (214, 196, 140)}),
    "whale_meat": ([
        "................",
        "................",
        "...DDDDDDDD.....",
        "..DRRRRRRRRD....",
        ".DRRMRRRRRRRD...",
        ".DRRRRRMRRRRRD..",
        ".DRRRRRRRRMRRD..",
        ".DRMRRRRRRRRRD..",
        ".DRRRRMRRRRRD...",
        "..DRRRRRRRRD....",
        "...DDDDDDDD.....",
        "................",
        "................",
        "................",
        "................",
        "................",
    ], {"D": (70, 14, 18), "R": (128, 26, 34), "M": (170, 70, 70)}),
    "cooked_whale_meat": ([
        "................",
        "................",
        "...DDDDDDDD.....",
        "..DBBBBBBBBD....",
        ".DBBLBBBBBBBD...",
        ".DBBBBBLBBBBBD..",
        ".DBBBBBBBBLBBD..",
        ".DBLBBBBBBBBBD..",
        ".DBBBBLBBBBBD...",
        "..DBBBBBBBBD....",
        "...DDDDDDDD.....",
        "................",
        "................",
        "................",
        "................",
        "................",
    ], {"D": (52, 28, 16), "B": (110, 62, 34), "L": (150, 96, 56)}),
    "whale_bone": ([
        "..BB............",
        ".BWWB...........",
        ".BWWWB..........",
        "..BWWWB.........",
        "...BWWWB........",
        "....BWWB........",
        ".....BWWB.......",
        "......BWWB......",
        "......BWWB......",
        ".......BWWB.....",
        "........BWWB....",
        ".........BWWB...",
        "..........BWWB..",
        "..........BWWWB.",
        "...........BWWB.",
        "............BB..",
    ], {"B": (160, 150, 128), "W": (232, 226, 206)}),
    "ambergris": ([
        "................",
        "................",
        "................",
        ".....DDDD.......",
        "....DGGGGDD.....",
        "...DGLGGGGGD....",
        "..DGGGGGAGGGD...",
        "..DGGAGGGGLGD...",
        "..DGGGGGGGGGD...",
        "...DGGLGGAGD....",
        "....DDGGGGD.....",
        "......DDDD......",
        "................",
        "................",
        "................",
        "................",
    ], {"D": (96, 88, 72), "G": (168, 160, 132), "L": (210, 202, 170), "A": (196, 160, 80)}),
    "great_whale_spawn_egg": ([
        "......DDDD......",
        ".....DGGGGD.....",
        "....DGGGGGGD....",
        "....DGPGGGGD....",
        "...DGGGGGPGGD...",
        "...DGGGGGGGGD...",
        "..DGPGGGGGGGGD..",
        "..DGGGGGGPGGGD..",
        "..DGGGGGGGGGGD..",
        "..DGGPGGGGGGGD..",
        "..DPPPPPPPPPPD..",
        "..DPPPPPPPPPPD..",
        "...DPPPPPPPPD...",
        "...DPPPPPPPPD...",
        "....DDPPPPDD....",
        "......DDDD......",
    ], {"D": (30, 34, 40), "G": (62, 70, 82), "P": (200, 204, 196)}),
    "whale_oil": ([
        "................",
        "......CCCC......",
        "......CWWC......",
        ".......GG.......",
        ".......GG.......",
        "......G..G......",
        ".....G....G.....",
        "....G......G....",
        "....GOOOOOOG....",
        "....GOYOOOOG....",
        "....GOOOOOOG....",
        "....GOOOOOAG....",
        "....GOOOOOOG....",
        ".....GAOOOG.....",
        "......GGGG......",
        "................",
    ], {"C": (120, 86, 52), "W": (160, 120, 76), "G": (200, 214, 222), "O": (196, 140, 40), "Y": (236, 196, 96),
        "A": (160, 104, 28)}),
    "firebomb": ([
        "......WW........",
        ".....WWWW.......",
        "......WC........",
        "......CC........",
        "......GG........",
        ".....G..G.......",
        "....G....G......",
        "...G......G.....",
        "...GOOOOOOG.....",
        "...GOYOOOOG.....",
        "...GOOOOOOG.....",
        "...GOOOOOAG.....",
        "...GOOOOOOG.....",
        "....GAOOOG......",
        ".....GGGG.......",
        "................",
    ], {"W": (232, 226, 210), "C": (120, 86, 52), "G": (190, 204, 212), "O": (196, 140, 40), "Y": (236, 196, 96),
        "A": (160, 104, 28)}),
    "powder_firebomb": ([
        "........F.......",
        ".......F........",
        "......WW........",
        ".....WWWW.......",
        "......CC........",
        "......GG........",
        "....GG..GG......",
        "...GOOOOOOG.....",
        "...GOKOOKOG.....",
        "...GOOOKOOG.....",
        "...GKOOOOOG.....",
        "...GOOKOOKG.....",
        "...GOOOOOOG.....",
        "....GKOOOG......",
        ".....GGGG.......",
        "................",
    ], {"F": (90, 80, 70), "W": (232, 226, 210), "C": (120, 86, 52), "G": (150, 160, 166), "O": (176, 122, 36),
        "K": (40, 38, 36)}),
    "oil_lamp": ([
        "................",
        ".......HH.......",
        "......H..H......",
        "......H..H......",
        ".....BBBBBB.....",
        "......GGGG......",
        "......GPPG......",
        "......GPPG......",
        "......GPWG......",
        "......GPWG......",
        "......GGGG......",
        ".....BBBBBB.....",
        "....BbbbbbbB....",
        "....BBBBBBBB....",
        "................",
        "................",
    ], {"H": (70, 60, 50), "B": (176, 132, 56), "b": (210, 168, 80), "G": (120, 112, 100), "P": (196, 214, 222),
        "W": (90, 80, 70)}),
}


BRASS, BRASS_HI, IRON, IRON_DARK = (176, 132, 56), (214, 172, 86), (110, 112, 118), (52, 52, 58)


def oil_lamp_texture(lit):
    """16x16: brass (x0-8, y0-8), the glass chimney's side (x8-12, y0-6), chain (x12-15, y0-8), handle (x0-1, y8-10)."""
    rnd = random.Random(21)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y in range(8):
        for x in range(8):
            c = BRASS_HI if (x + y) % 5 == 0 else shade(BRASS, rnd.uniform(0.85, 1.05))
            px[x, y] = clamp(c) + (255,)
    for y in range(6):
        for x in range(8, 12):
            if x in (8, 11):
                c = IRON_DARK
            elif lit:
                c = (255, 236, 150) if y < 3 else (255, 180, 60) if y < 5 else (120, 80, 50)
            else:
                c = (190, 208, 216) if y < 5 else (90, 80, 70)
            px[x, y] = clamp(c) + (255,)
    for y in range(8):
        for x in range(12, 15):
            link = (y % 4 in (0, 3) and x == 13) or (y % 4 in (1, 2) and x in (12, 14))
            if link:
                px[x, y] = IRON_DARK + (255,)
    for y in range(8, 10):
        px[0, y] = clamp(shade(BRASS, 0.7)) + (255,)
    return img


def oil_pot_brazier(lit):
    """16x16 iron brazier side: dark iron with a row of slots, glowing when lit."""
    rnd = random.Random(31)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
    px = img.load()
    for y in range(16):
        for x in range(16):
            c = shade(IRON, rnd.uniform(0.8, 1.05))
            if 6 <= y <= 10 and x % 3 == 1:
                c = (255, 150, 40) if lit and y > 7 else (255, 210, 90) if lit else IRON_DARK
            px[x, y] = clamp(c) + (255,)
    return img


def box(frm, to, faces_map):
    return {"from": frm, "to": to, "faces": faces_map}


def lamp_elements(lift):
    def up(v):
        return [v[0], v[1] + lift, v[2]]
    brass = lambda uv: {"uv": uv, "texture": "#lamp"}
    side4 = {"uv": [8, 0, 12, 6], "texture": "#lamp"}
    els = [
        box(up([5, 0, 5]), up([11, 2, 11]), {f: brass([0, 0, 6, 2] if f not in ("up", "down") else [0, 0, 6, 6])
                                            for f in ("north", "south", "east", "west", "up", "down")}),
        box(up([6, 2, 6]), up([10, 8, 10]), {f: side4 for f in ("north", "south", "east", "west")}),
        box(up([5.5, 8, 5.5]), up([10.5, 9, 10.5]), {f: brass([0, 2, 5, 3] if f not in ("up", "down") else [0, 0, 5, 5])
                                                    for f in ("north", "south", "east", "west", "up", "down")}),
        box(up([7.5, 9, 8]), up([8.5, 11, 8]), {"north": {"uv": [0, 8, 1, 10], "texture": "#lamp"},
                                                "south": {"uv": [0, 8, 1, 10], "texture": "#lamp"}}),
    ]
    return els


def oil_lamp_json():
    for lit in (False, True):
        tex = {"particle": "jaymoskingdoms:block/oil_lamp", "lamp": "jaymoskingdoms:block/oil_lamp" + ("_lit" if lit else "")}
        suffix = "_lit" if lit else ""
        write_json(os.path.join(ASSETS, "models", "block", "oil_lamp" + suffix + ".json"),
                   {"parent": "minecraft:block/block", "ambientocclusion": False, "textures": tex, "elements": lamp_elements(0)})
        hanging = lamp_elements(1) + [
            box([8, 12, 6.5], [8, 16, 9.5], {"east": {"uv": [12, 0, 15, 4], "texture": "#lamp"},
                                              "west": {"uv": [12, 0, 15, 4], "texture": "#lamp"}}),
            box([6.5, 12, 8], [9.5, 16, 8], {"north": {"uv": [12, 4, 15, 8], "texture": "#lamp"},
                                              "south": {"uv": [12, 4, 15, 8], "texture": "#lamp"}}),
        ]
        write_json(os.path.join(ASSETS, "models", "block", "oil_lamp_hanging" + suffix + ".json"),
                   {"parent": "minecraft:block/block", "ambientocclusion": False, "textures": tex, "elements": hanging})
    variants = {}
    for hanging in (False, True):
        for lit in (False, True):
            variants["hanging=%s,lit=%s" % (str(hanging).lower(), str(lit).lower())] = {
                "model": "jaymoskingdoms:block/oil_lamp" + ("_hanging" if hanging else "") + ("_lit" if lit else "")}
    write_json(os.path.join(ASSETS, "blockstates", "oil_lamp.json"), {"variants": variants})


def oil_pot_json():
    all6 = ("north", "south", "east", "west", "up", "down")
    for lit in (False, True):
        suffix = "_lit" if lit else ""
        write_json(os.path.join(ASSETS, "models", "block", "oil_pot" + suffix + ".json"), {
            "parent": "minecraft:block/block",
            "textures": {
                "particle": "minecraft:block/cauldron_side",
                "brazier": "jaymoskingdoms:block/oil_pot_brazier" + suffix,
                "pot": "minecraft:block/cauldron_side",
                "inside": "minecraft:block/cauldron_inner",
                "iron": "minecraft:block/iron_block",
            },
            "elements": [
                box([3, 0, 3], [13, 3, 13], {f: {"uv": [3, 5, 13, 8] if f not in ("up", "down") else [3, 3, 13, 13], "texture": "#brazier",
                                                  **({"cullface": "down"} if f == "down" else {})} for f in all6}),
                box([2, 3, 2], [14, 12, 14], {**{f: {"uv": [2, 4, 14, 13], "texture": "#pot"} for f in ("north", "south", "east", "west")},
                                               "up": {"uv": [2, 2, 14, 14], "texture": "#inside"},
                                               "down": {"uv": [2, 2, 14, 14], "texture": "#pot"}}),
                box([6, 9, 0], [10, 11, 2], {f: {"uv": [6, 6, 10, 8] if f not in ("east", "west") else [0, 6, 2, 8], "texture": "#iron"}
                                             for f in all6}),
            ],
        })
    variants = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for lit in (False, True):
            v = {"model": "jaymoskingdoms:block/oil_pot" + ("_lit" if lit else "")}
            if y:
                v["y"] = y
            variants["facing=%s,lit=%s" % (facing, str(lit).lower())] = v
    write_json(os.path.join(ASSETS, "blockstates", "oil_pot.json"), {"variants": variants})
    write_json(os.path.join(ASSETS, "items", "oil_pot.json"),
               {"model": {"type": "minecraft:model", "model": "jaymoskingdoms:block/oil_pot"}})


def tryworks_front(lit):
    """Bricks (our own, after vanilla's colours) round an arched firebox: soot-black cold, a bed of flame lit."""
    rnd = random.Random(11)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
    px = img.load()
    mortar, brick = (150, 140, 130), (150, 72, 56)
    for y in range(16):
        for x in range(16):
            row = y // 4
            edge = y % 4 == 3 or (x + (4 if row % 2 else 0)) % 8 == 7
            c = mortar if edge else shade(brick, rnd.uniform(0.85, 1.1))
            px[x, y] = clamp(c) + (255,)
    # the firebox: an arch 8 wide, from the bottom up to row 5
    for y in range(5, 16):
        for x in range(4, 12):
            if y == 5 and x in (4, 11):
                continue
            if lit:
                t = (y - 5) / 10.0
                c = mix((255, 220, 90), (230, 90, 20), 1.0 - t) if rnd.random() < 0.8 else (255, 240, 160)
                if y < 9 and rnd.random() < 0.5:
                    c = (60, 30, 20)
            else:
                c = shade((26, 22, 20), rnd.uniform(0.8, 1.2))
            px[x, y] = clamp(c) + (255,)
    return img


def tryworks_json():
    write_json(os.path.join(ASSETS, "models", "block", "tryworks.json"), {
        "parent": "minecraft:block/block",
        "textures": {
            "particle": "minecraft:block/bricks",
            "bricks": "minecraft:block/bricks",
            "front": "jaymoskingdoms:block/tryworks_front",
            "pot": "minecraft:block/cauldron_side",
            "inside": "minecraft:block/cauldron_inner",
        },
        "elements": [
            {"from": [0, 0, 0], "to": [16, 10, 16], "faces": {
                "down": {"uv": [0, 0, 16, 16], "texture": "#bricks", "cullface": "down"},
                "up": {"uv": [0, 0, 16, 16], "texture": "#bricks"},
                "north": {"uv": [0, 6, 16, 16], "texture": "#front", "cullface": "north"},
                "south": {"uv": [0, 6, 16, 16], "texture": "#bricks", "cullface": "south"},
                "west": {"uv": [0, 6, 16, 16], "texture": "#bricks", "cullface": "west"},
                "east": {"uv": [0, 6, 16, 16], "texture": "#bricks", "cullface": "east"}}},
            {"from": [2, 10, 2], "to": [14, 15, 14], "faces": {
                "up": {"uv": [2, 2, 14, 14], "texture": "#inside"},
                "north": {"uv": [2, 1, 14, 6], "texture": "#pot"},
                "south": {"uv": [2, 1, 14, 6], "texture": "#pot"},
                "west": {"uv": [2, 1, 14, 6], "texture": "#pot"},
                "east": {"uv": [2, 1, 14, 6], "texture": "#pot"}}},
        ],
    })
    write_json(os.path.join(ASSETS, "models", "block", "tryworks_on.json"),
               {"parent": "jaymoskingdoms:block/tryworks", "textures": {"front": "jaymoskingdoms:block/tryworks_front_on"}})
    variants = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for lit in (False, True):
            v = {"model": "jaymoskingdoms:block/tryworks" + ("_on" if lit else "")}
            if y:
                v["y"] = y
            variants["facing=%s,lit=%s" % (facing, "true" if lit else "false")] = v
    write_json(os.path.join(ASSETS, "blockstates", "tryworks.json"), {"variants": variants})
    write_json(os.path.join(ASSETS, "items", "tryworks.json"),
               {"model": {"type": "minecraft:model", "model": "jaymoskingdoms:block/tryworks"}})


def main():
    entity = os.path.join(ASSETS, "textures", "entity", "whale")
    os.makedirs(entity, exist_ok=True)
    paint_whale((52, 60, 70), (206, 208, 200), 1, True, True, False).save(os.path.join(entity, "rorqual.png"))
    paint_whale((70, 64, 60), (120, 112, 104), 2, False, False, True).save(os.path.join(entity, "sperm.png"))
    paint_whale((222, 220, 210), (238, 236, 228), 3, False, True, True, True).save(os.path.join(entity, "white.png"))
    harpoon_texture().save(os.path.join(entity, "harpoon.png"))
    block = os.path.join(ASSETS, "textures", "block")
    os.makedirs(block, exist_ok=True)
    tryworks_front(False).save(os.path.join(block, "tryworks_front.png"))
    tryworks_front(True).save(os.path.join(block, "tryworks_front_on.png"))
    tryworks_json()
    oil_lamp_texture(False).save(os.path.join(block, "oil_lamp.png"))
    oil_lamp_texture(True).save(os.path.join(block, "oil_lamp_lit.png"))
    oil_pot_brazier(False).save(os.path.join(block, "oil_pot_brazier.png"))
    oil_pot_brazier(True).save(os.path.join(block, "oil_pot_brazier_lit.png"))
    oil_lamp_json()
    oil_pot_json()
    items = os.path.join(ASSETS, "textures", "item")
    for name, (pattern, palette) in ITEMS.items():
        assert len(pattern) == 16 and all(len(r) == 16 for r in pattern), name
        sprite(pattern, palette).save(os.path.join(items, name + ".png"))
        if name == "harpoon":
            continue  # (batch 4) the harpoon item is the 3D Blockbench model now: tools/blockbench/harpoon.py in the repo root
        write_json(os.path.join(ASSETS, "items", name + ".json"),
                   {"model": {"type": "minecraft:model", "model": "jaymoskingdoms:item/" + name}})
        parent = "minecraft:item/handheld" if name == "harpoon" else "minecraft:item/generated"
        write_json(os.path.join(ASSETS, "models", "item", name + ".json"),
                   {"parent": parent, "textures": {"layer0": "jaymoskingdoms:item/" + name}})


if __name__ == "__main__":
    main()
