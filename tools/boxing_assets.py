"""Writes boxing's item models (boxing/BoxingGear: the Hand Wraps, the cestus and the bracers) and the forged heads of the
metal ones (the cestus's studs, the bracers' plates), drawn here pixel by pixel; nothing here is copied from another mod.
The gear's own icons and how it looks worn are the reskinned base gauntlet, written by tools/gauntlet_assets.py.

    python -I tools/boxing_assets.py

The heads follow tools/weapon_parts_assets.py ('#' outline, 'x' metal, 'o' shine), like tools/gen_pod_parts.py's
blanks: a stone one to shape at the Part Builder, and the metal the gear is made in.
"""
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import weapon_parts_assets as parts  # noqa: E402

ASSETS = parts.ASSETS

GEAR = ["hand_wraps", "leather_cestus", "iron_cestus", "steel_cestus", "leather_bracers", "iron_bracers", "steel_bracers"]

STUDS = [
    "................",
    "................",
    "................",
    "................",
    "..##..##..##....",
    ".#ox##ox##ox#...",
    ".#xx##xx##xx#...",
    ".#############..",
    ".#xxxxxxxxxxx#..",
    ".#oxxxoxxxoxx#..",
    "..###########...",
    "................",
    "................",
    "................",
    "................",
    "................"]
PLATES = [
    "................",
    "................",
    "...##########...",
    "..#oooooooooo#..",
    "..#oxxxxxxxxx#..",
    "..#oxxxxxxxxx#..",
    "..#xxxxxxxxxx#..",
    "...#xxxxxxxx#...",
    "...#oxxxxxxx#...",
    "...#xxxxxxxx#...",
    "....########....",
    "................",
    "................",
    "................",
    "................",
    "................"]
# head (WeaponParts part id), drawing, metal
HEADS = [("cestus_studs", STUDS, "iron"), ("steel_cestus_studs", STUDS, "steel"),
         ("bracer_plates", PLATES, "iron"), ("steel_bracer_plates", PLATES, "steel")]


def sprite(rows, colours):
    assert len(rows) == 16 and all(len(row) == 16 for row in rows), rows
    out = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            if c in colours:
                out.putpixel((x, y), colours[c] + (255,))
    return out


def save(img, path):
    full = os.path.join(ASSETS, "textures", path + ".png")
    os.makedirs(os.path.dirname(full), exist_ok=True)
    img.save(full)


def main():
    for item in GEAR:
        # (the gear's icons and its worn look are the reskinned base gauntlet: tools/gauntlet_assets.py)
        path = "item/boxing/" + item
        parts.write(os.path.join(ASSETS, "models", path + ".json"),
                    {"parent": "minecraft:item/handheld", "textures": {"layer0": "jaymospowers:" + path}})
        parts.write(os.path.join(ASSETS, "items", item + ".json"),
                    {"model": {"type": "minecraft:model", "model": "jaymospowers:" + path}})
    for part, rows, metal in HEADS:
        iron = sprite(rows, dict(zip("#xo", parts.DRAWN_COLOURS["iron"])))
        heads = {"stone": parts.as_stone(iron), metal: sprite(rows, dict(zip("#xo", parts.DRAWN_COLOURS[metal])))}
        cases = []
        for name, img in heads.items():
            path = parts.OUT + part + "_" + name
            save(img, path)
            parts.write(os.path.join(ASSETS, "models", path + ".json"),
                        {"parent": "minecraft:item/generated", "textures": {"layer0": "jaymospowers:" + path}})
            cases.append({"when": name, "model": {"type": "minecraft:model", "model": "jaymospowers:" + path}})
        parts.write(os.path.join(ASSETS, "items", part + ".json"), {"model": parts.hot({
            "type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0, "cases": cases,
            "fallback": cases[0]["model"]})})


if __name__ == "__main__":
    main()
