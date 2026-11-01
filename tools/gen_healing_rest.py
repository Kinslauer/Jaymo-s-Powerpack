"""Healing, campfire rest / hot springs / brews track (0.19.24): the two healing brews' textures, models, item
definitions, crucible recipes, the herbs' essences and the lang part.

Bottles follow docs/Vanilla-Style.md as the alchemy phials do: 16x16, no black outline (the outline is the darkest
shade of what it wraps, lighter on the lit side), light from the top-left, a glint, under 18 colours.

Run from the repository root: python3 -I tools/gen_healing_rest.py   (needs only Pillow)
"""
import json
import os

from PIL import Image

ASSETS = "src/main/resources/assets/jaymospowers/"
DATA = "src/main/resources/data/jaymospowers/"
ITEM_TEX = ASSETS + "textures/item/alchemy/"


def hexc(s):
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


GLASS = [hexc(c) for c in ("6a9ba5", "86bac6", "a8d0d9", "d0eae9")]
OAK = [hexc(c) for c in ("67502c", "7e6237", "967441", "af8f55")]
# dark to light
BONE = [hexc(c) for c in ("a9894f", "c9aa6b", "e6cf96", "f6ebc4", "ffffff")]
VIGOUR = [hexc(c) for c in ("a8283a", "cc4452", "e8707a", "f7a8a8", "ffffff")]
CLEAR = (0, 0, 0, 0)

BOTTLE_ROWS = {3: 2, 4: 2, 5: 2, 6: 3, 7: 4, 8: 5, 9: 5, 10: 5, 11: 5, 12: 5, 13: 4}


def bottle(liquid, fill_from):
    img = Image.new("RGBA", (16, 16), CLEAR)
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
            px[x, y] = OAK[3] if y == 1 and x < 8 else OAK[2] if x < 8 else OAK[1]
    px[9, 2] = OAK[0]
    px[4, 9] = liquid[-1]
    px[4, 10] = liquid[-1]
    return img


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def elixir():
    img = bottle(BONE, 8)
    px = img.load()
    # a knitted bone floating in it: two knobs and a shaft
    for x, y in ((6, 10), (7, 10), (8, 10), (9, 10)):
        px[x, y] = BONE[4]
    px[6, 9] = BONE[4]
    px[6, 11] = BONE[4]
    px[9, 9] = BONE[3]
    px[9, 11] = BONE[3]
    img.save(ITEM_TEX + "bone_knitting_elixir.png")


def vigour():
    img = bottle(VIGOUR, 8)
    px = img.load()
    # a small bright heart-spark in the liquid
    for x, y in ((7, 10), (9, 10), (6, 9), (10, 9)):
        px[x, y] = VIGOUR[4]
    px[8, 11] = VIGOUR[4]
    img.save(ITEM_TEX + "draught_of_vigour.png")


def item(name):
    write_json(ASSETS + "models/item/alchemy/" + name + ".json", {
        "parent": "minecraft:item/generated", "textures": {"layer0": "jaymospowers:item/alchemy/" + name}})
    write_json(ASSETS + "items/" + name + ".json", {
        "model": {"type": "minecraft:model", "model": "jaymospowers:item/alchemy/" + name}})


if __name__ == "__main__":
    os.makedirs(ITEM_TEX, exist_ok=True)
    elixir()
    vigour()
    item("bone_knitting_elixir")
    item("draught_of_vigour")
    crucible = DATA + "alchemy/crucible/"
    write_json(crucible + "bone_knitting_elixir.json", {
        "catalyst": "minecraft:glass_bottle",
        "essences": {"plant": 6, "life": 4, "earth": 4},
        "result": {"item": "jaymospowers:bone_knitting_elixir", "count": 1},
        "research": "bonesetting",
    })
    write_json(crucible + "draught_of_vigour.json", {
        "catalyst": "minecraft:glass_bottle",
        "essences": {"plant": 6, "life": 8, "light": 3, "water": 3},
        "result": {"item": "jaymospowers:draught_of_vigour", "count": 1},
        "research": "vigour",
    })
    # the herbs (items added by the herbs track) as ingredients: what they dissolve into
    write_json(DATA + "alchemy/essences/healing_rest_herbs.json", {"items": {
        "jaymospowers:comfrey": {"plant": 3, "earth": 2, "life": 1},
        "jaymospowers:yarrow": {"plant": 3, "life": 2, "light": 1},
        "jaymospowers:plantain": {"plant": 3, "water": 2, "life": 1},
        "jaymospowers:wild_garlic": {"plant": 3, "life": 1, "salt": 1},
        "jaymospowers:chamomile": {"plant": 3, "life": 2, "spirit": 1},
    }})
    write_json(ASSETS + "lang/parts/healing_rest.json", {
        "item.jaymospowers.bone_knitting_elixir": "Bone-Knitting Elixir",
        "item.jaymospowers.draught_of_vigour": "Draught of Vigour",
        "message.jaymospowers.brew.knit": "A deep ache in the bone, and then the bone begins to knit.",
        "message.jaymospowers.brew.vigour": "Warmth runs through every limb.",
        "research.jaymospowers.bonesetting": "Bonesetting",
        "research.jaymospowers.bonesetting.text": "Comfrey was called knitbone for a reason. Plant, life and earth, boiled together in a pot and bottled, make a Bone-Knitting Elixir: drunk, it sets the worst broken limb and knits it over two minutes. Herbs dissolve into the pot like any plant; comfrey and yarrow give the most.",
        "research.jaymospowers.vigour": "Vigour",
        "research.jaymospowers.vigour.text": "Life and plant, with a little light and water, bottled while the pot is hot. The Draught of Vigour mends every part of the body over a minute, further than a body will mend itself by rest and herbs alone.",
    })
