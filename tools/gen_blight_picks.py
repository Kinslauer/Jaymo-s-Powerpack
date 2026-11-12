"""Draws and writes the ticking charm (src/main/java/com/jaymospowers/baubles/TickingCharm.java): its 16x16 art in the pack's
palette (outline, body, shine), its item model and item definition (26.3: `minecraft:item/generated` with a `layer0` texture and
the `minecraft:model` definition) and its crafting recipe. Run it from the root:

    python -I tools/gen_blight_picks.py

The recipe is a plain shaped one (a clock, copper, string and an amethyst shard: no iron, so no Tinkers route is owed); JEI
shows it by itself, and GearJei adds the how-to page. Lang keys are in lang/parts/blight_picks.json.
"""
import json
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "jaymospowers")
DATA = os.path.join(RES, "data", "jaymospowers")


def rgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


OUT = rgb("2b1d12")
COPPER = [rgb("f0b88a"), rgb("c8794a"), rgb("8f4b2c")]
GLASS = [rgb("e6f0c0"), rgb("a8c23a"), rgb("5f7a22")]
STRING = rgb("d8d2c0")

# a round copper case on a loop of string; a pale green dial inside with a tick mark
CASE = {
    (6, 5): 0, (7, 5): 0, (8, 5): 0, (9, 5): 1,
    (5, 6): 0, (10, 6): 1, (4, 7): 1, (11, 7): 2, (4, 8): 1, (11, 8): 2,
    (4, 9): 1, (11, 9): 2, (5, 10): 1, (10, 10): 2,
    (6, 11): 1, (7, 11): 2, (8, 11): 2, (9, 11): 2,
}
DIAL = [(5, 7), (6, 7), (7, 7), (8, 7), (9, 7), (10, 7), (5, 8), (6, 8), (7, 8), (8, 8), (9, 8), (10, 8), (5, 9), (6, 9), (7, 9),
        (8, 9), (9, 9), (10, 9), (6, 6), (7, 6), (8, 6), (9, 6), (6, 10), (7, 10), (8, 10), (9, 10)]
TICKS = [(7, 7), (7, 8), (8, 8), (9, 9)]
LOOP = [(7, 4), (8, 4), (6, 3), (9, 3), (6, 2), (9, 2), (7, 1), (8, 1)]
KNOB = [(7, 12), (8, 12), (7, 13), (8, 13)]


def charm():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for (x, y) in LOOP:
        px[x, y] = STRING
    for (x, y) in DIAL:
        px[x, y] = GLASS[1]
    for (x, y) in TICKS:
        px[x, y] = GLASS[2]
    px[6, 6] = GLASS[0]
    px[7, 6] = GLASS[0]
    for (x, y), shade in CASE.items():
        px[x, y] = COPPER[shade]
    for (x, y) in KNOB:
        px[x, y] = COPPER[1]
    # an outline round everything drawn
    solid = {(x, y) for x in range(16) for y in range(16) if px[x, y][3]}
    for (x, y) in solid:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, ny = x + dx, y + dy
            if 0 <= nx < 16 and 0 <= ny < 16 and (nx, ny) not in solid:
                px[nx, ny] = OUT
    return img


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)


def main():
    tex = os.path.join(ASSETS, "textures", "item", "bauble")
    os.makedirs(tex, exist_ok=True)
    charm().save(os.path.join(tex, "ticking_charm.png"))
    write(os.path.join(ASSETS, "models", "item", "ticking_charm.json"), json.dumps(
        {"parent": "minecraft:item/generated", "textures": {"layer0": "jaymospowers:item/bauble/ticking_charm"}}, indent=2) + "\n")
    write(os.path.join(ASSETS, "items", "ticking_charm.json"), json.dumps(
        {"model": {"type": "minecraft:model", "model": "jaymospowers:item/ticking_charm"}}, indent=2) + "\n")
    write(os.path.join(DATA, "recipe", "baubles", "ticking_charm.json"), json.dumps({
        "type": "minecraft:crafting_shaped",
        "category": "equipment",
        "key": {"S": "minecraft:string", "C": "minecraft:copper_ingot", "K": "minecraft:clock", "A": "minecraft:amethyst_shard"},
        "pattern": [" S ", "CKC", " A "],
        "result": {"id": "jaymospowers:ticking_charm", "count": 1},
    }, indent=2) + "\n")


if __name__ == "__main__":
    main()
