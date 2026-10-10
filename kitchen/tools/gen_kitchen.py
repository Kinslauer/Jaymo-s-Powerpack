"""Assets for the kitchen drop (0.4): the cookware and the foods (the kitchen blocks themselves come from Cooking for
Blockheads, which this mod requires). Original pixel art drawn here with Pillow; nothing is copied.

Run from anywhere: python tools/gen_kitchen.py
"""
import json
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "assets" / "jaymoskitchen"
DATA = ROOT.parent.parent / "data"
NS = "jaymoskitchen"

rng = random.Random(4040)


def jitter(c, amount=8):
    d = rng.randint(-amount, amount)
    return tuple(max(0, min(255, v + d)) for v in c[:3]) + (255,)


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (255,)


def new(color=(0, 0, 0, 0)):
    return Image.new("RGBA", (16, 16), color)


def rect(img, x0, y0, x1, y1, color, noise=0):
    """Fills x0..x1-1, y0..y1-1."""
    for y in range(y0, y1):
        for x in range(x0, x1):
            img.putpixel((x, y), jitter(color, noise) if noise else color + (() if len(color) == 4 else (255,)))


def outline_rect(img, x0, y0, x1, y1, color):
    for x in range(x0, x1):
        img.putpixel((x, y0), color)
        img.putpixel((x, y1 - 1), color)
    for y in range(y0, y1):
        img.putpixel((x0, y), color)
        img.putpixel((x1 - 1, y), color)


# ---------------------------------------------------------------- item sprites
P = {
    ".": (0, 0, 0, 0),
    "k": (28, 28, 32, 255), "g": (110, 114, 122, 255), "s": (164, 168, 176, 255), "w": (216, 220, 226, 255),
    "b": (60, 40, 22, 255), "o": (128, 88, 50, 255), "t": (180, 136, 86, 255), "T": (214, 176, 120, 255),
    "e": (246, 244, 236, 255), "y": (250, 196, 40, 255), "Y": (255, 230, 120, 255),
    "r": (196, 54, 50, 255), "R": (140, 30, 30, 255), "p": (240, 170, 160, 255),
    "f": (238, 196, 70, 255), "F": (200, 150, 40, 255),
    "c": (110, 66, 36, 255), "C": (74, 42, 22, 255), "m": (240, 230, 216, 255),
    "v": (80, 150, 60, 255), "n": (226, 120, 40, 255), "B": (124, 84, 50, 255),
    "j": (150, 30, 60, 255), "J": (200, 60, 90, 255), "l": (200, 224, 232, 255),
    "u": (130, 92, 60, 255), "x": (176, 126, 84, 255), "h": (238, 224, 190, 255),
    "q": (150, 100, 70, 255), "Q": (200, 60, 50, 255),
}

SPRITES = {
    "cooking_pot": [
        "................",
        "................",
        "................",
        "...kkkkkkkkkk...",
        "..kwwwwwwwwwwk..",
        ".kkssssssssssk..",
        "kskksssssssskksk",
        "kgkkssssssssskgk",
        ".kk.ksssssssk.kk",
        "....ksssssssk...",
        "....ksssssssk...",
        "....kgssssssk...",
        "....kgggsssgk...",
        ".....kgggggk....",
        "......kkkkk.....",
        "................",
    ],
    "frying_pan": [
        "................",
        "................",
        "................",
        "................",
        "....kkkkkk......",
        "..kkggggggkk....",
        ".kgkkkkkkkkgk...",
        ".kgkkggkkkkgk...",
        ".kgkkkkkkkkgkkkk",
        ".kgkkkkkkkkgkoob",
        "..kggggggggkkbbk",
        "...kkkkkkkk.....",
        "................",
        "................",
        "................",
        "................",
    ],
    "saucepan": [
        "................",
        "................",
        "................",
        "................",
        "...kkkkkkk......",
        "..kwwwwwwwk.....",
        "..kssssssskkkkkk",
        "..ksssssssksooob",
        "..ksssssssskkkkk",
        "..kgssssssk.....",
        "..kgggsssgk.....",
        "...kgggggk......",
        "....kkkkk.......",
        "................",
        "................",
        "................",
    ],
    "baking_tray": [
        "................",
        "................",
        "................",
        "................",
        "................",
        "......kkkkkkkk..",
        "....kkssssssssk.",
        "..kkswwwwwwwwsk.",
        ".kswwgggggggwsk.",
        "kswwgggggggwsk..",
        "kswwwwwwwwwsk...",
        "ksssssssssskk...",
        ".kkkkkkkkkkk....",
        "................",
        "................",
        "................",
    ],
    "rolling_pin": [
        "................",
        "..............kk",
        ".............kbk",
        "............kok.",
        "..........kkkk..",
        ".........kTtk...",
        "........kTttk...",
        ".......kTttk....",
        "......kTttk.....",
        ".....kTttk......",
        "....kTttk.......",
        "...ktttk........",
        "..kkkk..........",
        ".kok............",
        "kbk.............",
        "kk..............",
    ],
    "fried_egg": [
        "................",
        "................",
        "................",
        ".....kkkkk......",
        "...kkeeeeekk....",
        "..keeeeeeeeek...",
        "..keeekkkeeeek..",
        ".keeekyyykeeek..",
        ".keeekyYykeeek..",
        ".keeekyyykeeek..",
        "..keeekkkeeek...",
        "..keeeeeeeeek...",
        "...kkeeeeekk....",
        ".....kkkkk......",
        "................",
        "................",
    ],
    "bacon": [
        "................",
        "................",
        "................",
        "............kk..",
        "..........kkrRk.",
        "........kkrpprk.",
        "......kkrpprRk..",
        "....kkrRprRkk...",
        "..kkrpprRkk.....",
        ".krpprRkk.......",
        ".kRrRkk.........",
        "..kkk...........",
        "................",
        "................",
        "................",
        "................",
    ],
    "fries": [
        "................",
        "....k.k..k......",
        "...kfkfkkfk.....",
        "...kfkfkfFk.k...",
        "..kfFkfkfkfkfk..",
        "..kfkFfkfkFkfk..",
        "..kkkkkkkkkkkk..",
        "..krrrrrrrrrrk..",
        "..krrryyyyrrrk..",
        "...krryYYyrrk...",
        "...krrryyrrrk...",
        "...krrrrrrrrk...",
        "....kRRRRRRk....",
        "....kkkkkkkk....",
        "................",
        "................",
    ],
    "vegetable_soup": [
        "................",
        "................",
        "................",
        "................",
        "................",
        "..kkkkkkkkkkkk..",
        ".knnvnnnnnvnnnk.",
        ".knnnnvnnnnnnvk.",
        ".kkkkkkkkkkkkkk.",
        ".kBuuuuuuuuuuBk.",
        "..kBuuuuuuuuBk..",
        "...kBuuuuuuBk...",
        "....kkkkkkkk....",
        "................",
        "................",
        "................",
    ],
    "berry_jam": [
        "................",
        "................",
        ".....kkkkkk.....",
        ".....kmmmmk.....",
        ".....kkkkkk.....",
        "....kllllllk....",
        "...kljjjjjjlk...",
        "...kljJjjjjlk...",
        "...kljhhhhjlk...",
        "...kljhjjhjlk...",
        "...kljhhhhjlk...",
        "...kljjjjjjlk...",
        "...kljjjjJjlk...",
        "....kllllllk....",
        ".....kkkkkk.....",
        "................",
    ],
    "hot_chocolate": [
        "................",
        "......m..m......",
        ".......m..m.....",
        "......m..m......",
        "................",
        "...kkkkkkkkk....",
        "..kmCCCCCCCmk...",
        "..kmmmmmmmmmkkk.",
        "..kmQmmmmmmmk.k.",
        "..kmQQmmmmmmk.k.",
        "..kmmQmmmmmmkkk.",
        "..kmmmmmmmmmk...",
        "...kmmmmmmmk....",
        "....kkkkkkk.....",
        "................",
        "................",
    ],
    "flatbread": [
        "................",
        "................",
        "................",
        ".....kkkkkk.....",
        "...kkTTTTTTkk...",
        "..kTTTxTTTTTTk..",
        "..kTTTTTTTxTTk..",
        ".kTxTTTTTTTTTTk.",
        ".kTTTTTxTTTTTxk.",
        ".kTTTTTTTTTTTTk.",
        "..kTTxTTTTxTTk..",
        "..kxTTTTTTTTxk..",
        "...kkxxxxxxkk...",
        ".....kkkkkk.....",
        "................",
        "................",
    ],
    "pasta": [
        "................",
        "................",
        "................",
        "................",
        ".....kkkkkk.....",
        "...kkfffYffkk...",
        "..kfYffFffYffk..",
        "..kffFkkkkfFfk..",
        ".kfYfk....kfYfk.",
        ".kfFfk....kffFk.",
        "..kffYkkkkYffk..",
        "..kfFfffYfffFk..",
        "...kkffFffYkk...",
        ".....kkkkkk.....",
        "................",
        "................",
    ],
    "mushroom_pasta": [
        "................",
        "................",
        "................",
        "......kkk.......",
        ".....kQeQk......",
        "..kkkkkkkkkkkk..",
        ".kfYfffhffYfffk.",
        ".kffFfYffffFffk.",
        ".kkkkkkkkkkkkkk.",
        ".kBuuuuuuuuuuBk.",
        "..kBuuuuuuuuBk..",
        "...kBuuuuuuBk...",
        "....kkkkkkkk....",
        "................",
        "................",
        "................",
    ],
    "brownie": [
        "................",
        "................",
        "................",
        "................",
        "....kkkkkkkk....",
        "...kcccccccck...",
        "..kcqcccqccqck..",
        "..kccccqcccccCk.",
        "..kkkkkkkkkkkCk.",
        "..kCCCCCCCCCkCk.",
        "..kCCCCCCCCCkk..",
        "..kCCCCCCCCCk...",
        "..kkkkkkkkkkk...",
        "................",
        "................",
        "................",
    ],
}


COOKWARE_3D = ("cooking_pot", "frying_pan", "saucepan", "baking_tray", "rolling_pin")


def render(rows):
    rows = [(r + "." * 16)[:16] for r in rows]
    assert len(rows) == 16
    img = new()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            img.putpixel((x, y), P[ch])
    return img


# ---------------------------------------------------------------- json helpers

def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def tex(name):
    return f"{NS}:block/{name}"


def shaped(name, pattern, key, result, count=1, category="misc"):
    out = {"id": result}
    if count > 1:
        out["count"] = count
    write_json(DATA / NS / "recipe" / f"{name}.json",
               {"type": "minecraft:crafting_shaped", "category": category, "pattern": pattern, "key": key, "result": out})


def shapeless(name, ingredients, result, count=1, category="misc"):
    out = {"id": result}
    if count > 1:
        out["count"] = count
    write_json(DATA / NS / "recipe" / f"{name}.json",
               {"type": "minecraft:crafting_shapeless", "category": category, "ingredients": ingredients, "result": out})


# ---------------------------------------------------------------- main

def main():
    block_tex = ROOT / "textures" / "block"
    item_tex = ROOT / "textures" / "item"
    block_tex.mkdir(parents=True, exist_ok=True)
    item_tex.mkdir(parents=True, exist_ok=True)

    # item sprites: cookware, the fridge's item, the foods
    for name, rows in SPRITES.items():
        render(rows).save(item_tex / f"{name}.png")
        model = {"parent": "minecraft:item/handheld" if name in ("frying_pan", "rolling_pin") else "minecraft:item/generated",
                 "textures": {"layer0": f"{NS}:item/{name}"}}
        write_json(ROOT / "models" / "item" / f"{name}.json", model)
        flat = {"type": "minecraft:model", "model": f"{NS}:item/{name}"}
        if name in COOKWARE_3D:
            # the flat sprite in GUIs; the hand-made 3D model (models/item/<name>_3d.json) everywhere else, including
            # the CfB oven top, which OvenRendererToolsMixin draws with the HEAD context
            item_model = {"type": "minecraft:select", "property": "minecraft:display_context",
                          "cases": [{"when": ["gui"], "model": flat}],
                          "fallback": {"type": "minecraft:model", "model": f"{NS}:item/{name}_3d"}}
        else:
            item_model = flat
        write_json(ROOT / "items" / f"{name}.json", {"model": item_model})

    # cookware
    shapeless("cooking_pot", ["minecraft:bowl", "minecraft:iron_ingot", "minecraft:iron_ingot", "minecraft:iron_ingot"],
              f"{NS}:cooking_pot", category="equipment")
    shaped("frying_pan", ["III", " S "], {"I": "minecraft:iron_ingot", "S": "minecraft:stick"}, f"{NS}:frying_pan", category="equipment")
    shapeless("saucepan", ["minecraft:iron_ingot", "minecraft:iron_ingot", "minecraft:stick"], f"{NS}:saucepan", category="equipment")
    shaped("baking_tray", ["III"], {"I": "minecraft:iron_ingot"}, f"{NS}:baking_tray", category="equipment")
    shaped("rolling_pin", ["SPS"], {"S": "minecraft:stick", "P": "#minecraft:planks"}, f"{NS}:rolling_pin", category="equipment")

    # foods: the cookware stays in the grid
    pan, pot, sauce, tray, pin = (f"{NS}:{t}" for t in ("frying_pan", "cooking_pot", "saucepan", "baking_tray", "rolling_pin"))
    shapeless("fried_egg", [pan, "#minecraft:eggs"], f"{NS}:fried_egg")
    shapeless("bacon", [pan, "minecraft:porkchop"], f"{NS}:bacon", 3)
    shapeless("fries", [pan, "minecraft:potato", f"{NS}:cooking_oil"], f"{NS}:fries", 2)
    shapeless("vegetable_soup", [pot, "minecraft:bowl", "minecraft:carrot", "minecraft:potato", "minecraft:beetroot"], f"{NS}:vegetable_soup")
    shapeless("berry_jam", [sauce, "minecraft:sweet_berries", "minecraft:sweet_berries", "minecraft:sweet_berries", "minecraft:sugar",
                            "minecraft:glass_bottle"], f"{NS}:berry_jam")
    shapeless("hot_chocolate", [sauce, "minecraft:milk_bucket", "minecraft:cocoa_beans", "minecraft:sugar", "minecraft:glass_bottle"],
              f"{NS}:hot_chocolate")
    shapeless("flatbread", [pin, f"{NS}:dough"], f"{NS}:flatbread", 2)
    shapeless("pasta", [pin, f"{NS}:dough", "#minecraft:eggs"], f"{NS}:pasta", 2)
    shapeless("mushroom_pasta", [pot, f"{NS}:pasta", "minecraft:brown_mushroom", "minecraft:red_mushroom", "minecraft:bowl"],
              f"{NS}:mushroom_pasta")
    shapeless("brownie", [tray, f"{NS}:flour", "minecraft:cocoa_beans", "minecraft:sugar", "#minecraft:eggs"], f"{NS}:brownie", 4)

    print("kitchen assets written")


if __name__ == "__main__":
    main()
