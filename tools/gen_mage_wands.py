"""Mage wands (Jaymo's Kingdoms, MageWand): the four wands' item definitions, models, textures and the sixteen recipes.

A wand is wood plus a focus: the focus is the item (amethyst, quartz, emerald, diamond), the wood is in the stack's custom
data (oak, birch, cherry, dark oak), set by the recipe. Run from the repo root: python -I tools/gen_mage_wands.py
"""
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent / "kingdoms" / "src" / "main" / "resources"
ASSETS = ROOT / "assets" / "jaymoskingdoms"
DATA = ROOT / "data" / "jaymoskingdoms"

# focus: (item it is made from, the gem's {dark, mid, light})
FOCI = {
    "amethyst": ("minecraft:amethyst_shard", ((0x6A, 0x3F, 0xB0), (0xB4, 0x8C, 0xFF), (0xE6, 0xD6, 0xFF))),
    "quartz": ("minecraft:quartz", ((0xB8, 0xAE, 0x9C), (0xF2, 0xEB, 0xDD), (0xFF, 0xFF, 0xFF))),
    "emerald": ("minecraft:emerald", ((0x12, 0x8A, 0x3E), (0x3F, 0xD0, 0x70), (0xB8, 0xFF, 0xD0))),
    "diamond": ("minecraft:diamond", ((0x2E, 0xA8, 0xA8), (0x6F, 0xE8, 0xE8), (0xD8, 0xFF, 0xFF))),
}
# wood: its planks
WOODS = {
    "oak": "minecraft:oak_planks",
    "birch": "minecraft:birch_planks",
    "cherry": "minecraft:cherry_planks",
    "dark_oak": "minecraft:dark_oak_planks",
}
SHAFT = ((0x4A, 0x32, 0x1C), (0x6B, 0x4A, 0x2A), (0x8C, 0x65, 0x3A))
CAP = (0xC8, 0xA8, 0x40)


def texture(name, gem):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    # the shaft runs up the diagonal from the lower left to the gem at the upper right
    for i in range(2, 12):
        x, y = 1 + i, 14 - i
        px[x, y] = SHAFT[1]
        px[x, y + 1] = SHAFT[0]
        if i % 3 == 0:
            px[x + 1, y] = SHAFT[2]
    # a gold cap, and the gem in a little setting
    px[12, 3] = CAP
    px[11, 4] = CAP
    dark, mid, light = gem
    for dx in range(3):
        for dy in range(3):
            px[12 + dx - 1, 1 + dy - 1 + 0] = mid if (dx, dy) != (1, 1) else light
    px[11, 0] = dark
    px[13, 2] = dark
    px[12, 2] = dark
    px[13, 1] = light
    img.save(ASSETS / "textures" / "item" / f"{name}.png")


def main():
    for focus, (stone, gem) in FOCI.items():
        name = f"{focus}_wand"
        texture(name, gem)
        (ASSETS / "items" / f"{name}.json").write_text(json.dumps(
            {"model": {"type": "minecraft:model", "model": f"jaymoskingdoms:item/{name}"}}, indent=2) + "\n")
        (ASSETS / "models" / "item" / f"{name}.json").write_text(json.dumps(
            {"parent": "minecraft:item/handheld", "textures": {"layer0": f"jaymoskingdoms:item/{name}"}}, indent=2) + "\n")
        for wood, planks in WOODS.items():
            recipe = {
                "type": "minecraft:crafting_shaped",
                "category": "equipment",
                "key": {"F": stone, "P": planks, "S": "minecraft:stick"},
                "pattern": ["  F", " P ", "S  "],
                "result": {"id": f"jaymoskingdoms:{name}", "components": {
                    "minecraft:custom_data": {"jaymoskingdoms_wand_wood": wood}}},
            }
            (DATA / "recipe" / f"{name}_{wood}.json").write_text(json.dumps(recipe, indent=2) + "\n")


if __name__ == "__main__":
    main()
