"""Writes Jaymo's Kingdoms' sailing assets (0.7.4): the ship's helm and the sail, as block models built from vanilla's
own block textures (a spruce wheel on a post; a square of white wool), their blockstates, item models, loot tables and
recipes. Run from kingdoms/: python3 -I tools/gen_sailing.py"""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "jaymoskingdoms")
DATA = os.path.join(RES, "data")


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", newline="\n") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def cube(fr, to, tex, faces="nsewud"):
    names = {"n": "north", "s": "south", "e": "east", "w": "west", "u": "up", "d": "down"}
    return {"from": fr, "to": to, "faces": {names[f]: {"texture": tex} for f in faces}}


def facing_variants(model, turns):
    return {"variants": {f"facing={f}": ({"model": model, "y": y} if y else {"model": model}) for f, y in turns.items()}}


def main():
    # the helm, facing north (the way the ship sails): a post with its wheel toward the one steering, behind it
    write_json(os.path.join(ASSETS, "models", "block", "ship_helm.json"), {
        "parent": "minecraft:block/block",
        "textures": {"particle": "minecraft:block/spruce_planks", "wood": "minecraft:block/spruce_planks",
                     "dark": "minecraft:block/dark_oak_planks", "post": "minecraft:block/stripped_spruce_log",
                     "iron": "minecraft:block/iron_block"},
        "elements": [
            cube([4, 0, 4], [12, 1, 12], "#dark"),
            cube([6, 1, 6], [10, 11, 10], "#post"),
            cube([7.25, 11.25, 8], [8.75, 12.75, 11], "#iron"),
            # the rim
            cube([4, 16, 11], [12, 17, 12], "#wood"),
            cube([4, 7, 11], [12, 8, 12], "#wood"),
            cube([3, 8, 11], [4, 16, 12], "#wood"),
            cube([12, 8, 11], [13, 16, 12], "#wood"),
            # the spokes, the hub, the handles
            cube([4, 11.5, 11.25], [12, 12.5, 11.75], "#dark"),
            cube([7.5, 8, 11.25], [8.5, 16, 11.75], "#dark"),
            cube([7, 11, 10.75], [9, 13, 12.25], "#iron"),
            cube([7.5, 17, 11.25], [8.5, 19, 11.75], "#dark"),
            cube([7.5, 5, 11.25], [8.5, 7, 11.75], "#dark"),
            cube([1, 11.5, 11.25], [3, 12.5, 11.75], "#dark"),
            cube([13, 11.5, 11.25], [15, 12.5, 11.75], "#dark"),
        ]})
    write_json(os.path.join(ASSETS, "blockstates", "ship_helm.json"),
               facing_variants("jaymoskingdoms:block/ship_helm", {"north": 0, "east": 90, "south": 180, "west": 270}))
    # the sail: a square of sailcloth across the ship
    write_json(os.path.join(ASSETS, "models", "block", "sail.json"), {
        "parent": "minecraft:block/block",
        "textures": {"particle": "minecraft:block/white_wool", "cloth": "minecraft:block/white_wool"},
        "elements": [cube([0, 0, 7], [16, 16, 9], "#cloth")]})
    write_json(os.path.join(ASSETS, "blockstates", "sail.json"),
               facing_variants("jaymoskingdoms:block/sail", {"north": 0, "east": 90, "south": 0, "west": 90}))
    for name in ("ship_helm", "sail"):
        write_json(os.path.join(ASSETS, "items", f"{name}.json"), {"model": {"type": "minecraft:model", "model": f"jaymoskingdoms:block/{name}"}})
        write_json(os.path.join(DATA, "jaymoskingdoms", "loot_table", "blocks", f"{name}.json"), {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"jaymoskingdoms:{name}"}],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    write_json(os.path.join(DATA, "jaymoskingdoms", "recipe", "ship_helm.json"), {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "key": {"S": "minecraft:stick", "P": "#minecraft:planks", "C": "minecraft:compass", "I": "minecraft:iron_ingot"},
        "pattern": ["SPS", "PCP", "SIS"], "result": {"id": "jaymoskingdoms:ship_helm"}})
    write_json(os.path.join(DATA, "jaymoskingdoms", "recipe", "sail.json"), {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "key": {"S": "minecraft:stick", "W": "#minecraft:wool"},
        "pattern": [" S ", "WWW", "WWW"], "result": {"id": "jaymoskingdoms:sail", "count": 4}})


if __name__ == "__main__":
    main()
