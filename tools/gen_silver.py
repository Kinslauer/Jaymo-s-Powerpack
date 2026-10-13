"""Writes silver's assets and data (0.8.111): ores, raw silver, ingot, nugget and blocks, their recipes, loot, tags and
the ore's worldgen. The textures are vanilla's iron ones recoloured to a cool, bright silver, so point it at the 26.3
client jar (it holds assets/minecraft):

    python -I tools/gen_silver.py ~/.gradle/caches/fabric-loom/26.3/minecraft-client.jar

The ids must match com.jaymospowers.tinkering.Tinkering.registerSilver. The Tinkering material (parts, molten silver)
comes from tools/tinkering_assets.py.
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

# our block, the vanilla texture it is recoloured from, whether only its ore spots change
BLOCKS = [("silver_ore", "block/iron_ore", True), ("deepslate_silver_ore", "block/deepslate_iron_ore", True),
          ("raw_silver_block", "block/raw_iron_block", False), ("silver_block", "block/iron_block", False)]
ITEMS = [("raw_silver", "item/raw_iron"), ("silver_ingot", "item/iron_ingot"), ("silver_nugget", "item/iron_nugget")]
NAMES = {"silver_ore": "Silver Ore", "deepslate_silver_ore": "Deepslate Silver Ore", "raw_silver_block": "Block of Raw Silver",
         "silver_block": "Block of Silver", "raw_silver": "Raw Silver", "silver_ingot": "Silver Ingot",
         "silver_nugget": "Silver Nugget"}

# the vein: size, veins per chunk, and the height range (a trapezoid: most around the middle)
VEIN_SIZE, VEINS, LOW, HIGH = 9, 6, -64, 48


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def silvered(im, spots_only):
    """Iron's warm tones turned a cool, bright silver; on ores, only the coloured spots (the stone stays)."""
    im = im.convert("RGBA")
    px = im.load()
    for y in range(im.size[1]):
        for x in range(im.size[0]):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            if spots_only and s < 0.18:
                continue
            # a little brighter than iron, with its shading kept, and a cold blue cast that deepens in the shadows
            v = min(1.0, v * 1.08) if spots_only else min(1.0, 0.04 + v)
            r2, g2, b2 = colorsys.hsv_to_rgb(0.6, 0.06 + 0.14 * (1 - v), v)
            px[x, y] = (round(r2 * 255), round(g2 * 255), round(b2 * 255), a)
    return im


def textures(jar):
    with zipfile.ZipFile(jar) as z:
        def vanilla(rel):
            return Image.open(io.BytesIO(z.read("assets/minecraft/textures/" + rel + ".png")))

        for block, src, spots in BLOCKS:
            dest = os.path.join(ASSETS, "textures", "block", "silver", block + ".png")
            os.makedirs(os.path.dirname(dest), exist_ok=True)
            silvered(vanilla(src), spots).save(dest)
        for item, src in ITEMS:
            dest = os.path.join(ASSETS, "textures", "item", "silver", item + ".png")
            os.makedirs(os.path.dirname(dest), exist_ok=True)
            silvered(vanilla(src), False).save(dest)


def assets():
    ref = lambda name: {"model": {"type": "minecraft:model", "model": NS + ":" + name}}
    for block, _, _ in BLOCKS:
        write(os.path.join(ASSETS, "models", "block", "silver", block + ".json"),
              {"parent": "minecraft:block/cube_all", "textures": {"all": NS + ":block/silver/" + block}})
        write(os.path.join(ASSETS, "blockstates", block + ".json"), {"variants": {"": {"model": NS + ":block/silver/" + block}}})
        write(os.path.join(ASSETS, "items", block + ".json"), ref("block/silver/" + block))
    for item, _ in ITEMS:
        write(os.path.join(ASSETS, "models", "item", "silver", item + ".json"),
              {"parent": "minecraft:item/generated", "textures": {"layer0": NS + ":item/silver/" + item}})
        write(os.path.join(ASSETS, "items", item + ".json"), ref("item/silver/" + item))
    write(os.path.join(ASSETS, "lang", "parts", "silver.json"),
          {("block." if k.endswith(("ore", "block")) else "item.") + NS + "." + k: v for k, v in NAMES.items()})


def loot():
    def drop_self(block):
        return {"type": "minecraft:block", "random_sequence": NS + ":blocks/" + block, "pools": [{
            "rolls": 1, "entries": [{"type": "minecraft:item", "name": NS + ":" + block}],
            "condition": {"type": "minecraft:survives_explosion"}}]}

    for block, _, ore in BLOCKS:
        table = drop_self(block)
        if ore:
            table["pools"] = [{"rolls": 1, "entries": [{"type": "minecraft:alternatives", "children": [
                {"type": "minecraft:item", "condition": "minecraft:tool/can_silk_touch", "name": NS + ":" + block},
                {"type": "minecraft:item", "name": NS + ":raw_silver", "modifier": [
                    {"type": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"},
                    {"type": "minecraft:explosion_decay"}]}]}]}]
        write(os.path.join(DATA, "loot_table", "blocks", block + ".json"), table)


def recipes():
    out = os.path.join(DATA, "recipe", "silver")
    ingot, nugget, raw = NS + ":silver_ingot", NS + ":silver_nugget", NS + ":raw_silver"
    for kind, time in (("smelting", 200), ("blasting", 100)):
        write(os.path.join(out, "silver_ingot_from_" + kind + ".json"), {
            "type": "minecraft:" + kind, "category": "misc", "cookingtime": time, "experience": 0.7, "group": "silver_ingot",
            "ingredient": "#c:ores/silver", "result": {"id": ingot}})
        write(os.path.join(out, "silver_ingot_from_" + kind + "_raw.json"), {
            "type": "minecraft:" + kind, "category": "misc", "cookingtime": time, "experience": 0.7, "group": "silver_ingot",
            "ingredient": raw, "result": {"id": ingot}})
    for small, big, name in ((ingot, NS + ":silver_block", "silver_block"), (nugget, ingot, "silver_ingot_from_nuggets"),
                             (raw, NS + ":raw_silver_block", "raw_silver_block")):
        write(os.path.join(out, name + ".json"), {
            "type": "minecraft:crafting_shaped", "category": "misc", "key": {"#": small}, "pattern": ["###", "###", "###"],
            "result": {"id": big}})
    for big, small, name in ((NS + ":silver_block", ingot, "silver_ingot_from_block"), (ingot, nugget, "silver_nugget"),
                             (NS + ":raw_silver_block", raw, "raw_silver")):
        write(os.path.join(out, name + ".json"), {
            "type": "minecraft:crafting_shapeless", "category": "misc", "ingredients": [big], "result": {"id": small, "count": 9}})


def tags():
    ores = [NS + ":silver_ore", NS + ":deepslate_silver_ore"]
    blocks = ores + [NS + ":raw_silver_block", NS + ":silver_block"]
    c = os.path.join(RES, "data", "c", "tags")
    for kind in ("block", "item"):
        write(os.path.join(c, kind, "ores", "silver.json"), {"values": ores})
        write(os.path.join(c, kind, "storage_blocks", "silver.json"), {"values": [NS + ":silver_block"]})
        write(os.path.join(c, kind, "storage_blocks", "raw_silver.json"), {"values": [NS + ":raw_silver_block"]})
    write(os.path.join(c, "item", "ingots", "silver.json"), {"values": [NS + ":silver_ingot"]})
    write(os.path.join(c, "item", "nuggets", "silver.json"), {"values": [NS + ":silver_nugget"]})
    write(os.path.join(c, "item", "raw_materials", "silver.json"), {"values": [NS + ":raw_silver"]})
    # mined like gold: with a pickaxe, iron or better
    mc = os.path.join(RES, "data", "minecraft", "tags", "block")
    for tag in ("mineable/pickaxe", "needs_iron_tool"):
        path = os.path.join(mc, tag + ".json")
        old = json.load(open(path)) if os.path.exists(path) else {"values": []}
        write(path, {"values": [v for v in old["values"] if v not in blocks] + blocks})


def worldgen():
    write(os.path.join(DATA, "worldgen", "feature", "ore_silver.json"), {
        "type": "minecraft:ore", "size": VEIN_SIZE, "discard_chance_on_air_exposure": 0.0, "targets": [
            {"target": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:stone_ore_replaceables"},
             "state": NS + ":silver_ore"},
            {"target": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:deepslate_ore_replaceables"},
             "state": NS + ":deepslate_silver_ore"}]})
    write(os.path.join(DATA, "worldgen", "placed_feature", "ore_silver.json"), {
        "feature": NS + ":ore_silver", "placement": [
            {"type": "minecraft:count", "count": VEINS}, {"type": "minecraft:in_square"},
            {"type": "minecraft:height_range", "height": {"type": "minecraft:trapezoid",
                                                          "min_inclusive": {"absolute": LOW}, "max_inclusive": {"absolute": HIGH}}},
            {"type": "minecraft:biome"}]})


def main():
    if len(sys.argv) != 2 or not os.path.isfile(sys.argv[1]):
        sys.exit(__doc__)
    textures(sys.argv[1])
    assets()
    loot()
    recipes()
    tags()
    worldgen()


if __name__ == "__main__":
    main()
