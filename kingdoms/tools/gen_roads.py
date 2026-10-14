"""Draws and writes Jaymo's Kingdoms' King's Roads assets (0.7.9), original and procedural:

- textures/item/road_charter.png: a half-unrolled parchment with a dotted red route between two sign posts, a wax seal.
- its item model, item definition and recipe (a map, a feather and an ink sac).

Run from kingdoms/: python3 -I tools/gen_roads.py"""
import json
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "jaymoskingdoms")
DATA = os.path.join(RES, "data", "jaymoskingdoms")


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", newline="\n") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def charter():
    img = Image.new("RGBA", (16, 16))
    paper, shade, edge = (232, 214, 170, 255), (204, 182, 134, 255), (120, 92, 52, 255)
    route, post, seal, seal_dark = (176, 40, 32, 255), (96, 66, 36, 255), (168, 28, 36, 255), (110, 16, 22, 255)
    # the sheet, slanted a little, with a roll at its top and bottom
    for y in range(2, 14):
        for x in range(2, 14):
            img.putpixel((x, y), paper if (x + y) % 7 else shade)
    for x in range(1, 15):
        img.putpixel((x, 1), edge)
        img.putpixel((x, 2), shade)
        img.putpixel((x, 13), shade)
        img.putpixel((x, 14), edge)
    for y in range(3, 13):
        img.putpixel((2, y), shade)
        img.putpixel((13, y), shade)
    # the route: dotted, winding from the lower left post to the upper right one
    for x, y in [(4, 11), (5, 10), (6, 10), (7, 9), (8, 8), (8, 7), (9, 6), (10, 5), (11, 5)]:
        if (x + y) % 2 == 0 or x in (4, 11):
            img.putpixel((x, y), route)
    for x, y in [(3, 11), (3, 10), (12, 4), (12, 5)]:
        img.putpixel((x, y), post)
    # a wax seal at the foot
    for x, y in [(10, 11), (11, 11), (10, 12), (11, 12)]:
        img.putpixel((x, y), seal)
    img.putpixel((11, 12), seal_dark)
    img.putpixel((12, 12), seal_dark)
    return img


def main():
    path = os.path.join(ASSETS, "textures", "item", "road_charter.png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    charter().save(path)
    write_json(os.path.join(ASSETS, "models", "item", "road_charter.json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": "jaymoskingdoms:item/road_charter"}})
    write_json(os.path.join(ASSETS, "items", "road_charter.json"),
               {"model": {"type": "minecraft:model", "model": "jaymoskingdoms:item/road_charter"}})
    write_json(os.path.join(DATA, "recipe", "road_charter.json"), {
        "type": "minecraft:crafting_shapeless",
        "category": "misc",
        "ingredients": ["minecraft:map", "minecraft:feather", "minecraft:ink_sac"],
        "result": {"id": "jaymoskingdoms:road_charter"},
    })


if __name__ == "__main__":
    main()
