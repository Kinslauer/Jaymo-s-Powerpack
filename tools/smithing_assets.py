"""Writes the Smithing art (tinkering/smithing, after Overgeared, MIT): tongs, Smithing Hammers, the forging blank, the hot
ingot, the Smithing Anvil and the heads of the smith's own tools; and makes every weapon and tool part glow orange
while it's hot (it has jaymospowers:heated_at).

    python -I tools/smithing_assets.py

Everything here is drawn in this file (no outside art). Re-run it after tools/weapon_parts_assets.py or
tools/tinkering_assets.py, which rewrite the part item models without the hot glow.
"""
import importlib.util
import json
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
_spec = importlib.util.spec_from_file_location("weapon_parts_assets", os.path.join(HERE, "weapon_parts_assets.py"))
wp = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(wp)

ROOT = os.path.join(HERE, "..", "src", "main", "resources")
ASSETS = os.path.join(ROOT, "assets", "jaymospowers")
DATA = os.path.join(ROOT, "data")
ITEM = "item/tinkering/smithing/"
BLOCK = "block/tinkering/"

# the smith's tool heads, drawn in weapon_parts_assets.DRAWN: part, drawing, metal
SMITH_HEADS = [("tongs_jaws", "tongs_jaws", "iron"), ("steel_tongs_jaws", "tongs_jaws", "steel"),
               ("copper_smithing_hammer_head", "smithing_hammer_head", "copper"),
               ("steel_smithing_hammer_head", "smithing_hammer_head", "steel")]

# '#' outline, 'x' body, 'o' shine, 'w' wood, 'd' dark wood, 'r' rivet
TONGS = [
    "................",
    "..........##.##.",
    ".........#ox#ox#",
    "..........#x#x#.",
    "...........#r#..",
    "..........#x#x#.",
    ".........#x#.#x#",
    "........#x#...#.",
    ".......#x#.#....",
    "......#w#.#x#...",
    ".....#w#.#w#....",
    "....#w#.#w#.....",
    "...#w#.#w#......",
    "..#d#.#d#.......",
    "..##..##........",
    "................"]
HAMMER = [
    "................",
    "........###.....",
    ".......#oox#....",
    "......#oxxxx#...",
    ".....#oxxxxxx#..",
    "......#xxxxxx#..",
    ".......#xxxx#...",
    "......#w#xx#....",
    ".....#w#.##.....",
    "....#w#.........",
    "...#w#..........",
    "..#w#...........",
    ".#d#............",
    ".##.............",
    "................",
    "................"]
BLANK = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "..############..",
    ".#ooooooooooox#.",
    ".#xxxxxxxxxxxx#.",
    ".#xxxxxxxxxxxx#.",
    "..#xxxxxxxxxx#..",
    "...##########...",
    "................",
    "................",
    "................",
    "................",
    "................"]
WOOD = {"w": (134, 96, 56), "d": (88, 60, 34)}
TONGS_METALS = {
    "wooden_tongs": ((66, 44, 24), (150, 108, 64), (196, 152, 98), (110, 110, 112)),
    "iron_tongs": ((52, 52, 56), (150, 152, 156), (220, 222, 226), (96, 96, 100)),
    "steel_tongs": ((30, 34, 42), (108, 118, 132), (182, 192, 206), (70, 76, 86)),
}
HAMMER_METALS = {
    "copper_smithing_hammer": ((90, 40, 24), (190, 104, 70), (240, 166, 124)),
    "steel_smithing_hammer": ((30, 34, 42), (108, 118, 132), (182, 192, 206)),
}
BLANK_COLOURS = ((48, 48, 52), (128, 128, 132), (196, 196, 200))
# each ingot a hot ingot can be, by its metal (Molten ids), and that ingot's own item model
INGOTS = {
    "iron": "minecraft:item/iron_ingot", "gold": "minecraft:item/gold_ingot", "copper": "minecraft:item/copper_ingot",
    **{m: "jaymospowers:item/tinkering/" + m + "_ingot"
       for m in ("silver", "rose_gold", "amethyst_bronze", "pig_iron", "slimesteel", "steel")},
}


def paint(rows, colours):
    out = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            if c in colours:
                out.putpixel((x, y), colours[c] + (255,))
    return out


def save(img, rel):
    path = os.path.join(ASSETS, "textures", rel + ".png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def item_model(rel, parent="minecraft:item/generated"):
    wp.write(os.path.join(ASSETS, "models", rel + ".json"), {"parent": parent, "textures": {"layer0": "jaymospowers:" + rel}})


def ref(model):
    return {"type": "minecraft:model", "model": model}


def anvil_top():
    """The Smithing Anvil's face: dark worked steel, a lighter strike mark in the middle, the hardy hole at one end."""
    out = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
    for y in range(16):
        for x in range(16):
            n = (x * 7 + y * 13 + x * y * 3) % 9
            v = 58 + n * 3
            if 3 <= x <= 12 and 5 <= y <= 10:
                v += 26 - abs(x - 7) * 2
            if 12 <= x <= 13 and 2 <= y <= 3:
                v = 22
            if x in (0, 15) or y in (0, 15):
                v = 36
            out.putpixel((x, y), (v, v + 2, v + 6, 255))
    return out


def smith_heads():
    for part, drawing, metal in SMITH_HEADS:
        iron = wp.drawn(drawing, "iron")
        heads = {"stone": wp.as_stone(iron), metal: wp.drawn(drawing, metal)}
        cases = []
        for m, img in heads.items():
            rel = wp.OUT + part + "_" + m
            save(img, rel)
            item_model(rel)
            cases.append({"when": m, "model": ref("jaymospowers:" + rel)})
        wp.write(os.path.join(ASSETS, "items", part + ".json"), {"model": wp.hot({
            "type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0, "cases": cases,
            "fallback": cases[0]["model"]})})


def tools():
    for name, (outline, body, shine, rivet) in TONGS_METALS.items():
        save(paint(TONGS, {"#": outline, "x": body, "o": shine, "r": rivet, **WOOD}), ITEM + name)
        item_model(ITEM + name, "minecraft:item/handheld")
        wp.write(os.path.join(ASSETS, "items", name + ".json"), {"model": ref("jaymospowers:" + ITEM + name)})
    for name, (outline, body, shine) in HAMMER_METALS.items():
        save(paint(HAMMER, {"#": outline, "x": body, "o": shine, **WOOD}), ITEM + name)
        item_model(ITEM + name, "minecraft:item/handheld")
        wp.write(os.path.join(ASSETS, "items", name + ".json"), {"model": ref("jaymospowers:" + ITEM + name)})
    save(paint(BLANK, dict(zip("#xo", BLANK_COLOURS))), ITEM + "forging_blank")
    item_model(ITEM + "forging_blank")
    wp.write(os.path.join(ASSETS, "items", "forging_blank.json"),
             {"model": wp.hot(ref("jaymospowers:" + ITEM + "forging_blank"))})
    # a hot ingot is its metal's own ingot, drawn hot (it's always hot)
    cases = [{"when": m, "model": ref(model)} for m, model in INGOTS.items()]
    wp.write(os.path.join(ASSETS, "items", "hot_ingot.json"), {"model": wp.hot({
        "type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0, "cases": cases,
        "fallback": cases[0]["model"]})["on_true"]})


def anvil():
    save(anvil_top(), BLOCK + "smithing_anvil_top")
    wp.write(os.path.join(ASSETS, "models", BLOCK + "smithing_anvil.json"), {
        "parent": "minecraft:block/template_anvil",
        "textures": {"particle": "jaymospowers:" + BLOCK + "anvil_base", "body": "jaymospowers:" + BLOCK + "anvil_base",
                     "top": "jaymospowers:" + BLOCK + "smithing_anvil_top"}})
    model = "jaymospowers:" + BLOCK + "smithing_anvil"
    # as vanilla's anvil: the block faces clockwise of the way it was placed
    wp.write(os.path.join(ASSETS, "blockstates", "smithing_anvil.json"), {"variants": {
        "facing=east": {"model": model, "y": 270}, "facing=north": {"model": model, "y": 180},
        "facing=south": {"model": model}, "facing=west": {"model": model, "y": 90}}})
    wp.write(os.path.join(ASSETS, "items", "smithing_anvil.json"), {"model": ref(model)})
    wp.write(os.path.join(DATA, "jaymospowers", "loot_table", "blocks", "smithing_anvil.json"), {
        "type": "minecraft:block", "random_sequence": "jaymospowers:blocks/smithing_anvil",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "jaymospowers:smithing_anvil"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    for tag in ("mineable/pickaxe", "needs_stone_tool"):
        path = os.path.join(DATA, "minecraft", "tags", "block", tag + ".json")
        with open(path) as f:
            data = json.load(f)
        if "jaymospowers:smithing_anvil" not in data["values"]:
            data["values"].append("jaymospowers:smithing_anvil")
            wp.write(path, data)


def glow_parts():
    """Every weapon part and Tinkers part glows while hot (left as is if it already does)."""
    items = os.path.join(ASSETS, "items")
    for name in sorted(os.listdir(items)):
        with open(os.path.join(items, name)) as f:
            data = json.load(f)
        node = data["model"]
        refs = json.dumps(node)
        if node.get("type") != "minecraft:select" or node.get("property") != "minecraft:custom_model_data":
            continue
        if "/weapon_part/" not in refs and "/tinkering/part/" not in refs:
            continue
        data["model"] = wp.hot(node)
        wp.write(os.path.join(items, name), data)


if __name__ == "__main__":
    smith_heads()
    tools()
    anvil()
    glow_parts()
