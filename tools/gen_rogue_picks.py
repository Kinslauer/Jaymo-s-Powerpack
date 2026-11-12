"""Draws and writes the rogue picks' art for Jaymo's Kingdoms (kingdoms/src/main/java/com/jaymoskingdoms/forms/Lasso.java and
RogueForms.java): the lasso's 16x16 item art, its item model and definition (26.3: `minecraft:item/generated` with a `layer0`
texture and the `minecraft:model` definition), its crafting recipe, and the skill-bar icons of the Disarm Rush and Dry Remark
forms. Run it from the root:

    python -I tools/gen_rogue_picks.py

The recipe is a plain shaped one (string and leather: no iron, so no Tinkers route is owed); JEI shows it by itself.
"""
import json
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "kingdoms", "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "jaymoskingdoms")
DATA = os.path.join(RES, "data", "jaymoskingdoms")


def rgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


OUT = rgb("2e2218")
ROPE = [rgb("e6d3a3"), rgb("c4a56a"), rgb("8f7340")]
STEEL = [rgb("d6dade"), rgb("9aa2ab"), rgb("5f6670")]
HAND = [rgb("f2c9a0"), rgb("d19b6c")]
SPARK = rgb("ffe27a")

# a coil of rope with a loop thrown out at its top right
COIL = {
    (3, 9): 1, (4, 8): 0, (5, 8): 0, (6, 8): 0, (7, 8): 1, (8, 9): 1, (8, 10): 2, (7, 11): 2, (6, 12): 2, (5, 12): 2, (4, 12): 2,
    (3, 11): 1, (3, 10): 1, (4, 10): 0, (5, 10): 1, (6, 10): 1, (5, 9): 2, (6, 9): 2, (4, 11): 1,
}
LOOP = [(8, 7), (9, 6), (10, 5), (11, 4), (12, 3), (13, 3), (13, 4), (13, 5), (12, 6), (11, 7), (10, 7), (9, 8), (12, 4), (12, 5)]
TAIL = [(7, 7), (6, 6), (6, 5)]


def outline(img):
    px = img.load()
    solid = {(x, y) for x in range(16) for y in range(16) if px[x, y][3]}
    for (x, y) in solid:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, ny = x + dx, y + dy
            if 0 <= nx < 16 and 0 <= ny < 16 and (nx, ny) not in solid:
                px[nx, ny] = OUT


def lasso():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for (x, y), shade in COIL.items():
        px[x, y] = ROPE[shade]
    for i, (x, y) in enumerate(LOOP):
        px[x, y] = ROPE[i % 2]
    for (x, y) in TAIL:
        px[x, y] = ROPE[1]
    outline(img)
    return img


def icon(draw):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw(img.load())
    outline(img)
    return img


def disarm(px):
    # a blade knocked spinning out of an open hand
    for i in range(7):
        px[3 + i, 11 - i] = STEEL[i % 2]
    px[2, 12] = STEEL[2]
    px[9, 4] = STEEL[0]
    for (x, y) in ((10, 11), (11, 11), (12, 11), (10, 12), (11, 12), (12, 12), (11, 13), (12, 13)):
        px[x, y] = HAND[(x + y) % 2]
    for (x, y) in ((12, 3), (13, 5), (4, 5)):
        px[x, y] = SPARK


def remark(px):
    # a speech bubble with its tail, and a small blade under it
    for x in range(3, 13):
        for y in range(3, 9):
            px[x, y] = ROPE[0] if (x + y) % 5 else ROPE[1]
    for (x, y) in ((5, 9), (6, 10), (5, 10)):
        px[x, y] = ROPE[1]
    for i in range(4):
        px[8 + i, 13 - i] = STEEL[i % 2]
    for (x, y) in ((5, 5), (7, 5), (9, 5)):
        px[x, y] = OUT


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", newline="\n") as f:
        f.write(text)


def main():
    lasso().save(os.path.join(ASSETS, "textures", "item", "lasso.png"))
    icon(disarm).save(os.path.join(ASSETS, "textures", "spell", "form_disarm_rush.png"))
    icon(remark).save(os.path.join(ASSETS, "textures", "spell", "form_dry_remark.png"))
    write(os.path.join(ASSETS, "models", "item", "lasso.json"),
          json.dumps({"parent": "minecraft:item/generated", "textures": {"layer0": "jaymoskingdoms:item/lasso"}}, indent=2) + "\n")
    write(os.path.join(ASSETS, "items", "lasso.json"),
          json.dumps({"model": {"type": "minecraft:model", "model": "jaymoskingdoms:item/lasso"}}, indent=2) + "\n")
    write(os.path.join(DATA, "recipe", "lasso.json"), json.dumps({
        "type": "minecraft:crafting_shaped",
        "category": "equipment",
        "key": {"S": "#c:strings", "L": "minecraft:leather"},
        "pattern": ["SS ", "SLS", " SS"],
        "result": {"id": "jaymoskingdoms:lasso", "count": 1},
    }, indent=2) + "\n")


if __name__ == "__main__":
    main()
