"""Writes the assets, data and lang of Clouds and Bits patch 3, "World bits" (stoneores/ and cloudbottle/):

* the 32 stone-variant ores (andesite, diorite, granite, tuff x coal, iron, copper, gold, redstone, lapis, diamond, emerald):
  their textures (the plain ore's own ore pixels, the ones that differ from vanilla's stone, laid over the stone's texture),
  models, blockstates, item definitions, smelting and blasting recipes, tags (the vanilla *_ores, c:ores/*, pickaxe and tool
  tier; block and item) and the names (lang/parts/stone_ores.json). The drops are the plain ore's own loot tables (the block
  overrides its loot table in code), so there are none here;
* worldgen: the vanilla coal, lapis, redstone and emerald ore features written again with the four variants first in their
  targets (data/minecraft/worldgen/feature/*.json), and our own vein features and placements (worldgen/feature|placed_feature/
  stone_ores/*): big coal veins in andesite and tuff, Nether gold and quartz veins;
* bottled cloud: the Bottle of Cloud's icon (drawn here, pixel by pixel), the puff's texture (our cold aercloud, thinner),
  their models and blockstate, and the names (lang/parts/cloud_bottle.json).

The ore pixels come from vanilla's own textures, so point it at the 26.3 client jar (the way gen_ores.py does):

    python3 -I tools/gen_stone_ores.py ~/.gradle/caches/fabric-loom/26.3/minecraft-client.jar

Safe to run again. The ids and counts must match com.jaymospowers.stoneores.StoneOres.
"""
import io
import json
import os
import sys
import zipfile

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "jaymospowers")
DATA = os.path.join(RES, "data")
NS = "jaymospowers"

# ---- tuning ----
STONES = ["andesite", "diorite", "granite", "tuff"]
# the ore: (what it smelts to, experience, tool tag its block needs, tag-name stem of the vanilla *_ores tags)
ORES = {
    "coal": ("minecraft:coal", 0.1, None),
    "iron": ("minecraft:iron_ingot", 0.7, "needs_stone_tool"),
    "copper": ("minecraft:copper_ingot", 0.7, "needs_stone_tool"),
    "gold": ("minecraft:gold_ingot", 1.0, "needs_iron_tool"),
    "redstone": ("minecraft:redstone", 0.7, "needs_iron_tool"),
    "lapis": ("minecraft:lapis_lazuli", 0.2, "needs_stone_tool"),
    "diamond": ("minecraft:diamond", 1.0, "needs_iron_tool"),
    "emerald": ("minecraft:emerald", 1.0, "needs_iron_tool"),
}
# the vanilla ore features whose targets get the variants first
OVERRIDDEN = ["ore_coal", "ore_coal_buried", "ore_lapis", "ore_lapis_buried", "ore_redstone", "ore_emerald"]
# a pixel is ore if it differs from the stone it sits in by more than this (summed over red, green, blue)
ORE_PIXEL = 12
# the puff's texture keeps this share of the cloud's opacity
PUFF_ALPHA = 0.7

LANG_ORES = {}
LANG_BOTTLE = {
    "item.jaymospowers.bottle_of_cloud": "Bottle of Cloud",
    "block.jaymospowers.cloud_puff": "Puff of Cloud",
}


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def png(path, im):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im.save(path)


def merge_tag(path, values):
    """Adds values to a tag file (ours, appended to, or a new one), keeping what's there."""
    old = json.load(open(path)) if os.path.exists(path) else {"values": []}
    have = old.get("values", [])
    keys = [v if isinstance(v, str) else v.get("id") for v in have]
    for v in values:
        if v not in keys:
            have.append(v)
    old["values"] = have
    write(path, old)


def title(name):
    return " ".join(w.capitalize() for w in name.split("_"))


# ---------------------------------------------------------------- ores

def ore_block_art(jar):
    def vanilla(rel):
        return Image.open(io.BytesIO(jar.read("assets/minecraft/textures/" + rel + ".png"))).convert("RGBA")

    stone = vanilla("block/stone")
    for ore in ORES:
        plain = vanilla("block/" + ore + "_ore")
        mask = [[sum(abs(a - b) for a, b in zip(plain.getpixel((x, y))[:3], stone.getpixel((x, y))[:3])) > ORE_PIXEL
                 for x in range(plain.width)] for y in range(plain.height)]
        for host in STONES:
            out = vanilla("block/" + host)
            for y in range(out.height):
                for x in range(out.width):
                    if mask[y][x]:
                        out.putpixel((x, y), plain.getpixel((x, y)))
            png(os.path.join(ASSETS, "textures", "block", "stone_ores", host + "_" + ore + "_ore.png"), out)


def ore_assets():
    for ore in ORES:
        for host in STONES:
            name = host + "_" + ore + "_ore"
            LANG_ORES["block.jaymospowers." + name] = title(host) + " " + title(ore) + " Ore"
            model = NS + ":block/stone_ores/" + name
            write(os.path.join(ASSETS, "models", "block", "stone_ores", name + ".json"),
                  {"parent": "minecraft:block/cube_all", "textures": {"all": NS + ":block/stone_ores/" + name}})
            if ore == "redstone":
                states = {"variants": {"lit=false": {"model": model}, "lit=true": {"model": model}}}
            else:
                states = {"variants": {"": {"model": model}}}
            write(os.path.join(ASSETS, "blockstates", name + ".json"), states)
            write(os.path.join(ASSETS, "items", name + ".json"), {"model": {"type": "minecraft:model", "model": model}})
    write(os.path.join(ASSETS, "lang", "parts", "stone_ores.json"), LANG_ORES)


def ore_data():
    mc = os.path.join(DATA, "minecraft", "tags")
    c = os.path.join(DATA, "c", "tags")
    every = []
    for ore, (result, xp, tool) in ORES.items():
        names = [NS + ":" + host + "_" + ore + "_ore" for host in STONES]
        every += names
        for kind, time in (("smelting", 200), ("blasting", 100)):
            write(os.path.join(DATA, NS, "recipe", "stone_ores", ore + "_from_" + kind + "_stone_ores.json"), {
                "type": "minecraft:" + kind, "category": "blocks" if ore != "coal" else "misc", "cookingtime": time, "experience": xp,
                "group": result.split(":")[1], "ingredient": names, "result": {"id": result}})
        # the vanilla tags (pickaxe tiers, the smeltery's iron/gold/copper ores) and the convention's
        for side in ("block", "item"):
            merge_tag(os.path.join(mc, side, ore + "_ores.json"), names)
            merge_tag(os.path.join(c, side, "ores", ore + ".json"), names)
        if tool:
            merge_tag(os.path.join(mc, "block", tool + ".json"), names)
    merge_tag(os.path.join(mc, "block", "mineable", "pickaxe.json"), every)


def vanilla_features(jar):
    """The vanilla ore features, with a first target per stone for each variant, before the plain ones."""
    for name in OVERRIDDEN:
        feature = json.loads(jar.read("data/minecraft/worldgen/feature/" + name + ".json"))
        ore = name.split("_")[1]
        first = []
        for host in STONES:
            first.append({"state": NS + ":" + host + "_" + ore + "_ore", "target": {"predicate_type": "minecraft:block_match", "block": "minecraft:" + host}})
        feature["targets"] = first + feature["targets"]
        write(os.path.join(DATA, "minecraft", "worldgen", "feature", name + ".json"), feature)


def veins():
    wg = os.path.join(DATA, NS, "worldgen")

    def vein(name, target, ore, size, rare=None, chance=0.0):
        data = {"type": NS + ":ore_vein", "target": target, "ore": ore, "size": size}
        if rare:
            data["rare"], data["rare_chance"] = rare, chance
        write(os.path.join(wg, "feature", "stone_ores", name + ".json"), data)

    def placed(name, count_or_rarity, low, high, host):
        count = {"type": "minecraft:count", "count": count_or_rarity} if isinstance(count_or_rarity, int) else {"type": "minecraft:rarity_filter", "chance": count_or_rarity[0]}
        write(os.path.join(wg, "placed_feature", "stone_ores", name + ".json"), {
            "feature": NS + ":stone_ores/" + name,
            "placement": [count, {"type": "minecraft:in_square"},
                          {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": low, "max_inclusive": high}},
                          {"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:matching_blocks", "blocks": "minecraft:" + host}},
                          {"type": "minecraft:biome"}]})

    def match(block):
        return {"predicate_type": "minecraft:block_match", "block": "minecraft:" + block}

    # shallow andesite (about 25 to 120) and deep tuff (the bottom of the world to 0): a vein a chunk or so, and now and then a coal block
    vein("coal_vein_andesite", match("andesite"), NS + ":andesite_coal_ore", 30, "minecraft:coal_block", 0.03)
    placed("coal_vein_andesite", 24, {"absolute": 25}, {"absolute": 120}, "andesite")
    vein("coal_vein_tuff", match("tuff"), NS + ":tuff_coal_ore", 36, "minecraft:coal_block", 0.05)
    placed("coal_vein_tuff", 24, {"above_bottom": 0}, {"absolute": 0}, "tuff")
    # the Nether: gold (the odd raw gold block) and quartz, in the netherrack
    vein("nether_gold_vein", match("netherrack"), "minecraft:nether_gold_ore", 34, "minecraft:raw_gold_block", 0.03)
    placed("nether_gold_vein", (4,), {"above_bottom": 10}, {"below_top": 10}, "netherrack")
    vein("nether_quartz_vein", match("netherrack"), "minecraft:nether_quartz_ore", 40)
    placed("nether_quartz_vein", (5,), {"above_bottom": 10}, {"below_top": 10}, "netherrack")


# ---------------------------------------------------------------- bottled cloud

# the Bottle of Cloud: '.' clear, 'o' glass outline, 'g' glass sheen, 'c' cloud, 'w' cloud highlight, 'k' cork, 'K' cork shade
BOTTLE = [
    "................",
    "......kkKk......",
    "......kkKk......",
    ".......oo.......",
    "......ogGo......",
    "......ogco......",
    ".....oggwco.....",
    "....ogwwwwco....",
    "...ogwwwwwwco...",
    "...owwwwwwwcco..",
    "...owwwwwwwcco..",
    "...ogwwwwwcccc..",
    "...ocwwwwcccco..",
    "....occcccccoo..",
    ".....ooooooo....",
    "................",
]
PALETTE = {
    "o": (92, 112, 140, 255), "g": (196, 220, 244, 200), "G": (170, 198, 230, 170), "c": (206, 214, 232, 255), "w": (250, 252, 255, 255),
    "k": (150, 108, 66, 255), "K": (112, 78, 44, 255),
}


def bottle_icon():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(BOTTLE):
        assert len(row) == 16, (y, row)
        for x, ch in enumerate(row):
            if ch != ".":
                im.putpixel((x, y), PALETTE[ch])
    return im


def cloud_assets():
    png(os.path.join(ASSETS, "textures", "item", "bottle_of_cloud.png"), bottle_icon())
    cold = Image.open(os.path.join(ASSETS, "textures", "block", "aether", "cold_aercloud.png")).convert("RGBA")
    puff = cold.copy()
    for y in range(puff.height):
        for x in range(puff.width):
            r, g, b, a = puff.getpixel((x, y))
            puff.putpixel((x, y), (r, g, b, round(a * PUFF_ALPHA)))
    png(os.path.join(ASSETS, "textures", "block", "cloud_puff.png"), puff)
    write(os.path.join(ASSETS, "models", "block", "cloud_puff.json"), {
        "parent": "minecraft:block/cube_all",
        "textures": {"all": {"force_translucent": True, "sprite": NS + ":block/cloud_puff"}}})
    write(os.path.join(ASSETS, "blockstates", "cloud_puff.json"), {"variants": {"": {"model": NS + ":block/cloud_puff"}}})
    write(os.path.join(ASSETS, "models", "item", "bottle_of_cloud.json"), {
        "parent": "minecraft:item/generated", "textures": {"layer0": NS + ":item/bottle_of_cloud"}})
    write(os.path.join(ASSETS, "items", "bottle_of_cloud.json"), {"model": {"type": "minecraft:model", "model": NS + ":item/bottle_of_cloud"}})
    write(os.path.join(ASSETS, "lang", "parts", "cloud_bottle.json"), LANG_BOTTLE)


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    with zipfile.ZipFile(sys.argv[1]) as jar:
        ore_block_art(jar)
        vanilla_features(jar)
    ore_assets()
    ore_data()
    veins()
    cloud_assets()


if __name__ == "__main__":
    main()
