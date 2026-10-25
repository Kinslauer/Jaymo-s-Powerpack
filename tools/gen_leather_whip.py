"""Draws the Armory's leather whip (0.16.1, our own art) and writes its model, item definition and recipe. A coiled
bullwhip: the lash wound round in a ring with a groove between its turns and pale spots on the leather, the rawhide fall
curling inside with its popper, the braided handle up the left between a knot at each end, a wrist loop hanging from the
butt. Painted by hand on a shade grid, lit from the top-left like vanilla's items. (0.16.11) In the hand only the
handle is drawn (leather_whip_handle: the braided grip, its tip where client/armory/LashSockets.WHIP_TIP says); the lash
off it is LashRenderer's, physical. Run it from the root:

    python -I tools/gen_leather_whip.py
"""
import json
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "jaymospowers")
DATA = os.path.join(RES, "data", "jaymospowers")
NAME = "leather_whip"


def rgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


# 0-6: the leather, darkest to lightest; a-d: the rawhide fall and popper, darkest to lightest
SHADES = dict(zip("0123456", map(rgb, ("25140a", "3d2313", "5a361d", "784a28", "935e33", "aa7442", "c08c58"))))
SHADES.update(zip("abcd", map(rgb, ("6e5320", "a3802e", "d2ac4a", "ecd27e"))))

GRID = (
    "................",
    "........22222...",
    "....22.2556541..",
    "...256555222451.",
    "...2435524ccd431",
    "...134124...c130",
    ".25312624.d.b150",
    ".23412523.bb4130",
    "2531.24523441330",
    "2341..144111330.",
    "2652...1353340..",
    "4351....10000...",
    "1331............",
    ".3.3............",
    ".3..3...........",
    "..22............",
)


# (0.16.11) the handle alone, corner to middle as a held item's is: the braid, a knot at the tip (8, 7) where the lash
# leaves it, the butt knot and the wrist loop
HANDLE = (
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "........35......",
    ".......3542.....",
    "......45321.....",
    ".....5241.......",
    "....4251........",
    "...5241.........",
    "..4251..........",
    ".35241..........",
    "13542...........",
    "321.............",
)


def texture(path, grid=GRID):
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(grid):
        for x, ch in enumerate(row):
            if ch in SHADES:
                im.putpixel((x, y), SHADES[ch] + (255,))
    im.save(path)


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", newline="\n") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def main():
    tex = os.path.join(ASSETS, "textures", "item", "armory", NAME + ".png")
    os.makedirs(os.path.dirname(tex), exist_ok=True)
    texture(tex)
    write(os.path.join(ASSETS, "models", "item", "armory", NAME + ".json"),
          {"parent": "minecraft:item/handheld", "textures": {"layer0": "jaymospowers:item/armory/" + NAME}})
    texture(os.path.join(ASSETS, "textures", "item", "armory", NAME + "_handle.png"), HANDLE)
    write(os.path.join(ASSETS, "models", "item", "armory", NAME + "_handle.json"),
          {"parent": "minecraft:item/handheld", "textures": {"layer0": "jaymospowers:item/armory/" + NAME + "_handle"}})
    write(os.path.join(ASSETS, "items", NAME + ".json"), {"model": {
        "type": "minecraft:select",
        "property": "minecraft:display_context",
        "cases": [{
            "when": ["firstperson_righthand", "firstperson_lefthand", "thirdperson_righthand", "thirdperson_lefthand"],
            "model": {"type": "minecraft:model", "model": "jaymospowers:item/armory/" + NAME + "_handle"},
        }],
        "fallback": {"type": "minecraft:model", "model": "jaymospowers:item/armory/" + NAME},
    }})
    write(os.path.join(DATA, "recipe", "armory", NAME + ".json"), {
        "type": "minecraft:crafting_shaped",
        "category": "equipment",
        "key": {"L": "minecraft:leather", "S": "minecraft:stick", "T": "minecraft:string"},
        "pattern": ["  L", " LT", "S  "],
        "result": {"id": "jaymospowers:" + NAME},
    })
    write(os.path.join(DATA, "advancement", "recipes", "combat", NAME + ".json"), {
        "parent": "minecraft:recipes/root",
        "criteria": {
            "has_leather": {"conditions": {"items": [{"items": "minecraft:leather"}]}, "trigger": "minecraft:inventory_changed"},
            "has_the_recipe": {"conditions": {"recipes": "jaymospowers:armory/" + NAME}, "trigger": "minecraft:recipe_unlocked"},
        },
        "requirements": [["has_the_recipe", "has_leather"]],
        "rewards": {"recipes": ["jaymospowers:armory/" + NAME]},
    })


if __name__ == "__main__":
    main()
