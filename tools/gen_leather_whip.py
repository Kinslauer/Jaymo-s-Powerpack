"""Draws the Armory's leather whip (0.15.147, our own art) and writes its model, item definition and recipe. A short
wrapped handle in the bottom-left corner, the lash looping up and round in a coil, its thin tail and pale popper hanging
off to the right. Run it from the root:

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

# leather: light, mid, dark, outline; the handle's wrap; the popper
LEATHER = ((150, 98, 56), (122, 78, 44), (88, 55, 30), (52, 32, 17))
WRAP = ((95, 70, 40), (64, 46, 26))
POPPER = (214, 196, 160)
KNOB = ((180, 180, 186), (110, 110, 118))

PIXELS = {}
# the handle, corner to corner up from the bottom-left, wrapped in bands, a metal knob at its end
for i, (x, y) in enumerate(((1, 14), (2, 13), (3, 12), (4, 11), (5, 10))):
    PIXELS[(x, y)] = WRAP[i % 2]
    PIXELS[(x + 1, y + 1)] = WRAP[1]
PIXELS[(0, 15)] = KNOB[0]
PIXELS[(1, 15)] = KNOB[1]
PIXELS[(0, 14)] = KNOB[1]
# the coil: two loops of lash, one just behind the other, round a hole (lit on the upper left, shaded lower right)
for cx, cy, shade in ((9.0, 7.0, 1), (10.0, 6.0, 0)):
    for y in range(16):
        for x in range(16):
            d = ((x + 0.5 - cx - 0.5) ** 2 + (y + 0.5 - cy - 0.5) ** 2) ** 0.5
            if abs(d - 3.6) < 0.62:
                lit = (x - cx) + (y - cy) < 0
                PIXELS[(x, y)] = LEATHER[0 if lit else 1] if shade == 0 else LEATHER[1 if lit else 2]
# the tail: out of the coil's top right, hanging off to the corner, the popper at its end
for x, y in ((13, 3), (14, 2), (14, 1)):
    PIXELS[(x, y)] = LEATHER[1]
PIXELS[(15, 0)] = POPPER


def texture(path):
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for (x, y), colour in PIXELS.items():
        im.putpixel((x, y), colour + (255,))
    # a dark outline under and right of every pixel, as vanilla's items have
    for (x, y) in list(PIXELS):
        for nx, ny in ((x + 1, y), (x, y + 1)):
            hole = ((nx - 9.5) ** 2 + (ny - 7.0) ** 2) ** 0.5 < 3.0
            if 0 <= nx < 16 and 0 <= ny < 16 and (nx, ny) not in PIXELS and not hole:
                im.putpixel((nx, ny), LEATHER[3] + (255,))
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
    write(os.path.join(ASSETS, "items", NAME + ".json"),
          {"model": {"type": "minecraft:model", "model": "jaymospowers:item/armory/" + NAME}})
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
