"""Draws and writes Jaymo's Kingdoms' natural disasters' assets (0.6.12), all original and procedural:

- textures/particle/storm_cloud_0..3.png: soft white puffs (tinted by the client) for storm clouds and funnels;
  particles/storm_cloud.json and funnel.json name them.
- textures/item/barometer.png: a brass barometer, its needle at "storm".
- the storm bell's and weathervane's block models (built from vanilla's own block textures), blockstates, item
  models, loot tables, recipes, and the pickaxe tag for the bell.

Seeded, so every run draws the same pixels. Run from kingdoms/: python3 -I tools/gen_disasters.py"""
import json
import math
import os
import random

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "jaymoskingdoms")
DATA = os.path.join(RES, "data")


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", newline="\n") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def puff(seed, size=32):
    rnd = random.Random(seed)
    # a few overlapping soft blobs make a lumpy cloud
    blobs = [(size / 2 + rnd.uniform(-4, 4), size / 2 + rnd.uniform(-4, 4), rnd.uniform(6, 9)) for _ in range(5)]
    img = Image.new("RGBA", (size, size))
    for y in range(size):
        for x in range(size):
            a = 0.0
            for bx, by, br in blobs:
                d = math.hypot(x + 0.5 - bx, y + 0.5 - by) / br
                a = max(a, max(0.0, 1.0 - d * d))
            # fades to nothing well inside the square, so a puff never shows its edges
            edge = math.hypot(x + 0.5 - size / 2, y + 0.5 - size / 2) / (size / 2 - 1)
            a *= max(0.0, 1.0 - edge ** 3)
            a *= 0.85 + rnd.random() * 0.15
            shade = int(200 + 55 * min(1.0, a * 1.4))
            img.putpixel((x, y), (shade, shade, shade, int(255 * min(1.0, a))))
    return img


def barometer():
    img = Image.new("RGBA", (16, 16))
    brass, dark_brass, face, needle = (196, 152, 64, 255), (122, 88, 32, 255), (236, 230, 210, 255), (40, 36, 40, 255)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d <= 7.2:
                img.putpixel((x, y), dark_brass if d > 6.4 else brass if d > 5.3 else face)
    # the scale's ticks (fair on the right, storm on the left)
    for a in range(200, 341, 28):
        r = math.radians(a)
        img.putpixel((round(7.5 + math.cos(r) * 4.4), round(7.5 + math.sin(r) * 4.4)), (150, 60, 40, 255))
    # the needle, swung toward storm
    for i in range(5):
        img.putpixel((round(7.5 - i * 0.8), round(7.5 - i * 0.55)), needle)
    img.putpixel((7, 8), dark_brass)
    img.putpixel((8, 7), brass)
    return img


def cube(fr, to, tex, faces="nsewud"):
    names = {"n": "north", "s": "south", "e": "east", "w": "west", "u": "up", "d": "down"}
    return {"from": fr, "to": to, "faces": {names[f]: {"texture": tex} for f in faces}}


def main():
    for i in range(4):
        path = os.path.join(ASSETS, "textures", "particle", f"storm_cloud_{i}.png")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        puff(1000 + i).save(path)
    names = [f"jaymoskingdoms:storm_cloud_{i}" for i in range(4)]
    write_json(os.path.join(ASSETS, "particles", "storm_cloud.json"), {"textures": names})
    write_json(os.path.join(ASSETS, "particles", "funnel.json"), {"textures": names})
    path = os.path.join(ASSETS, "textures", "item", "barometer.png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    barometer().save(path)
    write_json(os.path.join(ASSETS, "models", "item", "barometer.json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": "jaymoskingdoms:item/barometer"}})

    # the storm bell: a timber frame on a stone plinth, a gold bell hung in it
    write_json(os.path.join(ASSETS, "models", "block", "storm_bell.json"), {
        "parent": "minecraft:block/block",
        "textures": {"particle": "minecraft:block/gold_block", "stone": "minecraft:block/smooth_stone",
                     "wood": "minecraft:block/dark_oak_planks", "gold": "minecraft:block/gold_block",
                     "iron": "minecraft:block/iron_block"},
        "elements": [
            cube([1, 0, 4], [15, 2, 12], "#stone"),
            cube([1, 2, 6], [3, 16, 10], "#wood"),
            cube([13, 2, 6], [15, 16, 10], "#wood"),
            cube([3, 14, 6], [13, 16, 10], "#wood"),
            cube([7.5, 12, 7.5], [8.5, 14, 8.5], "#iron"),
            cube([5, 6, 5], [11, 12, 11], "#gold"),
            cube([4, 5, 4], [12, 6.5, 12], "#gold"),
        ]})
    write_json(os.path.join(ASSETS, "blockstates", "storm_bell.json"), {"variants": {"": {"model": "jaymoskingdoms:block/storm_bell"}}})
    # the weathervane: a copper foot, an iron rod, the compass arms and the arrow
    write_json(os.path.join(ASSETS, "models", "block", "weathervane.json"), {
        "parent": "minecraft:block/block",
        "textures": {"particle": "minecraft:block/copper_block", "copper": "minecraft:block/copper_block",
                     "iron": "minecraft:block/iron_block", "dark": "minecraft:block/black_concrete"},
        "elements": [
            cube([6, 0, 6], [10, 1, 10], "#copper"),
            cube([7.5, 1, 7.5], [8.5, 15, 8.5], "#iron"),
            cube([3, 9, 7.75], [13, 9.5, 8.25], "#dark"),
            cube([7.75, 9, 3], [8.25, 9.5, 13], "#dark"),
            cube([7.6, 13, 2], [8.4, 13.8, 14], "#copper"),
            cube([6.5, 12.5, 1], [9.5, 14.3, 3], "#copper"),
            cube([7.8, 12, 12], [8.2, 15.5, 15], "#copper"),
        ]})
    write_json(os.path.join(ASSETS, "blockstates", "weathervane.json"), {"variants": {"": {"model": "jaymoskingdoms:block/weathervane"}}})
    for name in ("storm_bell", "weathervane"):
        write_json(os.path.join(ASSETS, "items", f"{name}.json"), {"model": {"type": "minecraft:model", "model": f"jaymoskingdoms:block/{name}"}})
        write_json(os.path.join(DATA, "jaymoskingdoms", "loot_table", "blocks", f"{name}.json"), {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"jaymoskingdoms:{name}"}],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    write_json(os.path.join(ASSETS, "items", "barometer.json"), {"model": {"type": "minecraft:model", "model": "jaymoskingdoms:item/barometer"}})
    write_json(os.path.join(DATA, "jaymoskingdoms", "recipe", "storm_bell.json"), {
        "type": "minecraft:crafting_shaped", "category": "redstone",
        "key": {"P": "minecraft:dark_oak_planks", "B": "minecraft:bell", "R": "minecraft:redstone", "S": "minecraft:smooth_stone_slab"},
        "pattern": ["PPP", "PBP", "SRS"], "result": {"id": "jaymoskingdoms:storm_bell"}})
    write_json(os.path.join(DATA, "jaymoskingdoms", "recipe", "weathervane.json"), {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "key": {"C": "minecraft:copper_ingot", "I": "minecraft:iron_ingot"},
        "pattern": ["CCC", " I ", " I "], "result": {"id": "jaymoskingdoms:weathervane"}})
    write_json(os.path.join(DATA, "jaymoskingdoms", "recipe", "barometer.json"), {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "key": {"C": "minecraft:copper_ingot", "G": "minecraft:glass_pane", "R": "minecraft:redstone"},
        "pattern": [" C ", "CGC", " R "], "result": {"id": "jaymoskingdoms:barometer"}})
    write_json(os.path.join(DATA, "minecraft", "tags", "block", "mineable", "pickaxe.json"),
               {"replace": False, "values": ["jaymoskingdoms:storm_bell", "jaymoskingdoms:weathervane"]})


if __name__ == "__main__":
    main()
