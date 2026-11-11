"""Writes the assets and data that make the four knife metals obtainable: cobalt ore in the Nether (its block, loot,
tags, smelting and worldgen), molten cobalt, manyullyn, queen's slime and hepatizon (block frames and the smeltery gauge),
the hot ingot's cases and the names (lang/parts/knife_metals.json).

The ore is vanilla's nether gold ore with its gold spots turned cobalt blue, so point it at a 26.3 client jar:

    python3 -I tools/gen_knife_metals.py /path/to/minecraft-client.jar

The ids must match com.jaymospowers.tinkering.Tinkering.registerKnifeMetals and tinkering.smeltery.Molten.
Safe to run again (every file is overwritten, every edit is idempotent).
"""
import colorsys
import io
import json
import os
import sys
import zipfile

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "jaymospowers")
DATA = os.path.join(RES, "data", "jaymospowers")
NS = "jaymospowers"

# metal: (name, hue, saturation in the dark, saturation in the light, brightness)
METALS = {
    "cobalt": ("Cobalt", 0.60, 0.85, 0.55, 1.00),
    "manyullyn": ("Manyullyn", 0.78, 0.75, 0.50, 0.95),
    "queen_slime": ("Queen's Slime", 0.40, 0.70, 0.45, 1.00),
    "hepatizon": ("Hepatizon", 0.74, 0.45, 0.30, 0.70),
}

# the vein: blocks in it, veins per chunk, and the Nether heights it lies between
VEIN_SIZE, VEINS, LOW, HIGH = 5, 4, 8, 118


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def recolour(im, hue, sat_dark, sat_light, dim, only=None):
    """The image's shading kept, its colour turned to the metal's; `only` picks which pixels change."""
    im = im.convert("RGBA")
    px = im.load()
    for y in range(im.size[1]):
        for x in range(im.size[0]):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            if only is not None and not only(h, s, v):
                continue
            v = min(1.0, v * dim)
            r2, g2, b2 = colorsys.hsv_to_rgb(hue, sat_dark + (sat_light - sat_dark) * v, v)
            px[x, y] = (round(r2 * 255), round(g2 * 255), round(b2 * 255), a)
    return im


def ore(jar):
    with zipfile.ZipFile(jar) as z:
        src = Image.open(io.BytesIO(z.read("assets/minecraft/textures/block/nether_gold_ore.png")))
    _, hue, sat_dark, sat_light, dim = METALS["cobalt"]
    # the gold spots are the yellow pixels (netherrack is red, hue under 0.03 or over 0.9)
    spots = lambda h, s, v: 0.04 < h < 0.2
    dest = os.path.join(ASSETS, "textures", "block", "tinkering", "cobalt_ore.png")
    recolour(src, hue, sat_dark, sat_light, dim, spots).save(dest)
    write(os.path.join(ASSETS, "models", "block", "tinkering", "cobalt_ore.json"),
          {"parent": "minecraft:block/cube_all", "textures": {"all": NS + ":block/tinkering/cobalt_ore"}})
    write(os.path.join(ASSETS, "blockstates", "cobalt_ore.json"), {"variants": {"": {"model": NS + ":block/tinkering/cobalt_ore"}}})
    write(os.path.join(ASSETS, "items", "cobalt_ore.json"),
          {"model": {"type": "minecraft:model", "model": NS + ":block/tinkering/cobalt_ore"}})


def molten():
    src = os.path.join(ASSETS, "textures", "block", "tinkering", "molten", "iron.png")
    with open(src + ".mcmeta") as f:
        meta = f.read()
    hot_path = os.path.join(ASSETS, "items", "hot_ingot.json")
    hot = json.load(open(hot_path))
    cases = hot["model"]["cases"]
    template = next(c for c in cases if c["when"] == "amethyst_bronze")
    for metal, (_, hue, sat_dark, sat_light, dim) in METALS.items():
        glow = recolour(Image.open(src), hue, sat_dark * 0.8, sat_light * 0.7, min(1.0, dim * 1.1))
        dest = os.path.join(os.path.dirname(src), metal + ".png")
        glow.save(dest)
        with open(dest + ".mcmeta", "w", encoding="utf-8", newline="\n") as f:
            f.write(meta)
        glow.crop((0, 0, 16, 16)).save(os.path.join(ASSETS, "textures", "gui", "tinkering", "molten", metal + ".png"))
        if not any(c["when"] == metal for c in cases):
            cases.append({"when": metal, "model": json.loads(json.dumps(template["model"]).replace("amethyst_bronze", metal))})
    write(hot_path, hot)


def data():
    ore_id, ingot = NS + ":cobalt_ore", NS + ":cobalt_ingot"
    write(os.path.join(DATA, "loot_table", "blocks", "cobalt_ore.json"), {
        "type": "minecraft:block", "random_sequence": NS + ":blocks/cobalt_ore", "pools": [{
            "rolls": 1, "entries": [{"type": "minecraft:item", "name": ore_id}],
            "condition": {"type": "minecraft:survives_explosion"}}]})
    for kind, time in (("smelting", 200), ("blasting", 100)):
        write(os.path.join(DATA, "recipe", "knife_metals", "cobalt_ingot_from_" + kind + ".json"), {
            "type": "minecraft:" + kind, "category": "misc", "cookingtime": time, "experience": 1.0, "group": "cobalt_ingot",
            "ingredient": "#c:ores/cobalt", "result": {"id": ingot}})
    for kind in ("block", "item"):
        write(os.path.join(RES, "data", "c", "tags", kind, "ores", "cobalt.json"), {"values": [ore_id]})
    for tag in ("mineable/pickaxe", "needs_iron_tool"):
        path = os.path.join(RES, "data", "minecraft", "tags", "block", tag + ".json")
        values = json.load(open(path))
        if ore_id not in values["values"]:
            values["values"].append(ore_id)
            write(path, values)
    write(os.path.join(DATA, "worldgen", "feature", "ore_cobalt.json"), {
        "type": "minecraft:ore", "discard_chance_on_air_exposure": 0.0, "size": VEIN_SIZE, "targets": [{
            "state": ore_id, "target": {"block": "minecraft:netherrack", "predicate_type": "minecraft:block_match"}}]})
    write(os.path.join(DATA, "worldgen", "placed_feature", "ore_cobalt.json"), {
        "feature": NS + ":ore_cobalt", "placement": [
            {"type": "minecraft:count", "count": VEINS}, {"type": "minecraft:in_square"},
            {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform",
                                                          "min_inclusive": {"absolute": LOW}, "max_inclusive": {"absolute": HIGH}}},
            {"type": "minecraft:biome"}]})
    lang = {"block." + NS + ".cobalt_ore": "Cobalt Ore"}
    for metal, (name, *_rest) in METALS.items():
        lang["molten." + NS + "." + metal] = "Molten " + name
    write(os.path.join(ASSETS, "lang", "parts", "knife_metals.json"), lang)


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    ore(sys.argv[1])
    molten()
    data()
