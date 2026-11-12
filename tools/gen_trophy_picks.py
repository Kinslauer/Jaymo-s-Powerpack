"""Paints the trophy stand and the great foe's trophy (kingdoms/src/main/java/com/jaymoskingdoms/trophies/) and writes the
files that go with them: the stand's block model (one parent with `#base` and `#plate` face textures, a child per
material), its blockstates and item definitions, the trophy's item model and definition, the stands' loot tables and
recipes. Run it from the repository root:

    python -I tools/gen_trophy_picks.py

Needs only Pillow. The art is drawn here pixel by pixel, from nothing.
"""
import json
import os
import random

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "kingdoms", "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "jaymoskingdoms")
DATA = os.path.join(RES, "data", "jaymoskingdoms")
STANDS = ("trophy_stand_wood", "trophy_stand_stone")


def rgb(h, a=255):
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (a,)


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def wood():
    rng = random.Random(11)
    ramp = [rgb(c) for c in ("5a3b22", "6e4a2b", "835a35", "98693e")]
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            # long boards: a darker seam every five rows, grain running along x
            tone = 2 + (1 if (x + y // 3) % 7 == 0 else 0) - (1 if rng.random() < 0.18 else 0) + (1 if rng.random() < 0.12 else 0)
            if y % 5 == 4:
                tone = 0
            px[x, y] = ramp[max(0, min(3, tone))]
    return img


def stone():
    rng = random.Random(12)
    ramp = [rgb(c) for c in ("5f6266", "73777b", "888c90", "9da1a5")]
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            tone = 2 + rng.choice((-1, 0, 0, 1))
            # brick seams: courses of eight rows, joints staggered
            if y % 8 == 7 or (x + (y // 8) * 4) % 8 == 7:
                tone = 0
            px[x, y] = ramp[max(0, min(3, tone))]
    return img


def plate():
    rng = random.Random(13)
    ramp = [rgb(c) for c in ("7a5524", "a07632", "c79a45", "e3bd62")]
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            tone = 2 + rng.choice((-1, 0, 0, 1))
            if x in (0, 15) or y in (0, 15):
                tone = 0 if (x + y) % 2 else 1
            px[x, y] = ramp[max(0, min(3, tone))]
    # a few engraved lines where a name would be
    for y in (6, 8, 10):
        for x in range(3, 13):
            if (x + y) % 5:
                px[x, y] = ramp[0]
    return img


def mask():
    """The great foe's trophy: a carved mask on a gilt rim, two horns, hollow eyes."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    bone = [rgb(c) for c in ("8d8167", "b0a488", "cfc4a8", "e8dfc8")]
    gold = [rgb(c) for c in ("8a6a22", "b58c30", "d9ae48", "f1cd6a")]
    dark = rgb("1b1612")
    ember = rgb("c9722e")
    # horns
    for i, (x, y) in enumerate(((1, 2), (1, 3), (2, 4), (3, 5), (14, 2), (14, 3), (13, 4), (12, 5))):
        px[x, y] = bone[1 + (i % 2)]
    for x, y in ((0, 1), (15, 1)):
        px[x, y] = bone[2]
    # face: an oval
    for y in range(3, 15):
        half = 5 if 5 <= y <= 11 else (4 if y in (4, 12) else 3 if y in (3, 13) else 2)
        for x in range(8 - half, 8 + half):
            tone = 2 + (1 if x < 8 else 0) - (1 if y > 11 else 0) - (1 if x > 11 else 0)
            px[x, y] = bone[max(0, min(3, tone))]
    # gilt rim
    for y in range(3, 15):
        row = [x for x in range(16) if px[x, y][3] and px[x, y] in bone]
        if row:
            px[row[0], y] = gold[2]
            px[row[-1], y] = gold[1]
    for x in range(6, 10):
        px[x, 14] = gold[2]
    # eyes and mouth
    for x, y in ((5, 7), (6, 7), (5, 8), (6, 8), (9, 7), (10, 7), (9, 8), (10, 8)):
        px[x, y] = dark
    px[6, 8] = ember
    px[9, 8] = ember
    for x in range(6, 10):
        px[x, 11] = dark
    px[7, 9] = bone[0]
    px[8, 9] = bone[0]
    return img


def main():
    tex = os.path.join(ASSETS, "textures")
    os.makedirs(os.path.join(tex, "block"), exist_ok=True)
    os.makedirs(os.path.join(tex, "item"), exist_ok=True)
    wood().save(os.path.join(tex, "block", "trophy_stand_wood.png"))
    stone().save(os.path.join(tex, "block", "trophy_stand_stone.png"))
    plate().save(os.path.join(tex, "block", "trophy_plate.png"))
    mask().save(os.path.join(tex, "item", "great_foe_trophy.png"))

    def box(frm, to, tex_name):
        return {"from": frm, "to": to, "faces": {d: {"texture": "#" + tex_name} for d in ("north", "south", "east", "west", "up", "down")}}

    write(os.path.join(ASSETS, "models", "block", "trophy_stand.json"), {
        "parent": "minecraft:block/block",
        "textures": {"particle": "#base"},
        "elements": [
            box([2, 0, 2], [14, 2, 14], "base"),
            box([6, 2, 6], [10, 8, 10], "base"),
            box([3, 8, 3], [13, 10, 13], "base"),
            box([5.5, 2.5, 5], [10.5, 6.5, 6], "plate"),
        ],
    })
    for stand in STANDS:
        write(os.path.join(ASSETS, "models", "block", stand + ".json"), {
            "parent": "jaymoskingdoms:block/trophy_stand",
            "textures": {"base": "jaymoskingdoms:block/" + stand, "plate": "jaymoskingdoms:block/trophy_plate"},
        })
        write(os.path.join(ASSETS, "blockstates", stand + ".json"), {"variants": {
            "facing=north": {"model": "jaymoskingdoms:block/" + stand},
            "facing=east": {"model": "jaymoskingdoms:block/" + stand, "y": 90},
            "facing=south": {"model": "jaymoskingdoms:block/" + stand, "y": 180},
            "facing=west": {"model": "jaymoskingdoms:block/" + stand, "y": 270},
        }})
        write(os.path.join(ASSETS, "items", stand + ".json"), {"model": {"type": "minecraft:model", "model": "jaymoskingdoms:block/" + stand}})
        write(os.path.join(DATA, "loot_table", "blocks", stand + ".json"), {
            "type": "minecraft:block",
            "pools": [{"condition": {"type": "minecraft:survives_explosion"}, "entries": [{"type": "minecraft:item", "name": "jaymoskingdoms:" + stand}], "rolls": 1}],
            "random_sequence": "jaymoskingdoms:blocks/" + stand,
        })
    write(os.path.join(ASSETS, "models", "item", "great_foe_trophy.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "jaymoskingdoms:item/great_foe_trophy"}})
    write(os.path.join(ASSETS, "items", "great_foe_trophy.json"), {"model": {"type": "minecraft:model", "model": "jaymoskingdoms:item/great_foe_trophy"}})
    # no iron: a bowl for the dish, a copper ingot for the plate
    write(os.path.join(DATA, "recipe", "trophy_stand_wood.json"), {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "pattern": [" B ", " C ", "PPP"],
        "key": {"B": "minecraft:bowl", "C": "minecraft:copper_ingot", "P": "#minecraft:planks"},
        "result": {"count": 1, "id": "jaymoskingdoms:trophy_stand_wood"},
    })
    write(os.path.join(DATA, "recipe", "trophy_stand_stone.json"), {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "pattern": [" B ", " C ", "SSS"],
        "key": {"B": "minecraft:bowl", "C": "minecraft:copper_ingot", "S": "minecraft:stone_bricks"},
        "result": {"count": 1, "id": "jaymoskingdoms:trophy_stand_stone"},
    })


if __name__ == "__main__":
    main()
