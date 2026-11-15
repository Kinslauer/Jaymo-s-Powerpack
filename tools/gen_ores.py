"""Writes the assets, data and lang of Andy's GT New Horizons picks, patch 7 (docs/GTNH-Plan.md, "Ores"):

* mixed veins: the partner ore and the small ores each great vein's feature carries (worldgen/feature/big_vein_*.json,
  ore_silver.json), the tiny dusts washing gives (iron, copper, tin; nine make a dust), and tin's crushed and washed ore;
* small ores (iron, copper, tin, gold, silver; stone and deepslate): their art, blockstates, models, loot tables (a
  crushed ore, as many as fortune gives; the block itself with silk touch), tags (mined like the metal's own ore) and the
  scatter's worldgen (worldgen/*/small_ores.json);
* the dowsing rod: its art (empty, and with each metal's sample in its fork), its item definition (by the
  jaymospowers:dowsing_sample component), its recipe;
* the names and JEI pages (lang/parts/ores.json).

All art is vanilla's own textures recoloured or cut down (tin's and silver's small ores from our tin and silver ore,
which are vanilla's recoloured; tin's crushed and washed ore from our iron ones, recoloured), so point it at the 26.3
client jar. Run after tools/gen_coke_and_steel.py (it rewrites big_vein_tin.json without the partner):

    python3 -I tools/gen_ores.py ~/.gradle/caches/fabric-loom/26.3/minecraft-client.jar

The ids must match com.jaymospowers.ground.Ores, ground.DowsingRodItem and tinkering.ore.OreWashing. Safe to run again.
"""
import io
import json
import os
import sys
import zipfile

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import gen_stone_age as sa  # noqa: E402

ROOT = os.path.dirname(HERE)
RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "jaymospowers")
DATA = os.path.join(RES, "data")
NS = "jaymospowers"

# ---- tuning ----
# a small ore keeps its full ore's spots only this close (pixels) to the middle of its densest clump
SPOT_RADIUS = 3.2
# nine tiny dusts make a dust
TINY_PER_DUST = 9

# metal: (raw item, the full ore textures (stone, deepslate), the tool tag its ore needs, the dowsing sample's colours)
METALS = {
    "iron": ("minecraft:raw_iron", ("mc:block/iron_ore", "mc:block/deepslate_iron_ore"), "needs_stone_tool", ((110, 78, 56), (226, 192, 170))),
    "copper": ("minecraft:raw_copper", ("mc:block/copper_ore", "mc:block/deepslate_copper_ore"), "needs_stone_tool", ((120, 58, 32), (236, 148, 104))),
    "tin": (NS + ":raw_tin", ("ours:block/tin/tin_ore", "ours:block/tin/deepslate_tin_ore"), "needs_stone_tool", ((92, 92, 98), (232, 232, 224))),
    "gold": ("minecraft:raw_gold", ("mc:block/gold_ore", "mc:block/deepslate_gold_ore"), "needs_iron_tool", ((150, 98, 20), (255, 236, 110))),
    "silver": (NS + ":raw_silver", ("ours:block/silver/silver_ore", "ours:block/silver/deepslate_silver_ore"), "needs_iron_tool",
               ((110, 116, 128), (236, 242, 250))),
}
# each great vein's partner (the ore streaked through it), and the small ores it leaves over itself
VEINS = {
    "big_vein_iron": ("iron", "copper"),
    "big_vein_copper": ("copper", "tin"),
    "big_vein_gold": ("gold", "iron"),
    "big_vein_tin": ("tin", "copper"),
    "ore_silver": ("silver", "copper"),
}
FULL_ORE = {
    "iron": ("minecraft:iron_ore", "minecraft:deepslate_iron_ore"),
    "copper": ("minecraft:copper_ore", "minecraft:deepslate_copper_ore"),
    "tin": (NS + ":tin_ore", NS + ":deepslate_tin_ore"),
    "gold": ("minecraft:gold_ore", "minecraft:deepslate_gold_ore"),
    "silver": (NS + ":silver_ore", NS + ":deepslate_silver_ore"),
}
INGOTS = {"iron": "minecraft:iron_ingot", "copper": "minecraft:copper_ingot", "tin": NS + ":tin_ingot", "gold": "minecraft:gold_ingot",
          "silver": NS + ":silver_ingot"}
# the tiny dusts: what nine of them make, and the dust whose colours they take
TINY = {"iron": (NS + ":iron_dust", "item/iron_dust"), "copper": (NS + ":copper_dust", "item/copper_dust"),
        "tin": (NS + ":tin_dust", "item/tin/tin_dust")}

LANG = {
    "item.jaymospowers.tiny_iron_dust": "Tiny Iron Dust",
    "item.jaymospowers.tiny_copper_dust": "Tiny Copper Dust",
    "item.jaymospowers.tiny_tin_dust": "Tiny Tin Dust",
    "item.jaymospowers.crushed_tin_ore": "Crushed Tin Ore",
    "item.jaymospowers.purified_tin_ore": "Washed Tin Ore",
    "item.jaymospowers.dowsing_rod": "Dowsing Rod",
    "jei.jaymospowers.ore_washing": "Ore Washing",
    "jei.jaymospowers.ore_washing.chances": "Nugget %s%%, tiny dust %s%% (each ore)",
    "jei.jaymospowers.ore_washing.where": "Water cauldron, or left in running water",
    "jei.jaymospowers.small_ores": "Small Ores",
    "jei.jaymospowers.small_ores.where": "Lone, just under the ground; most over a vein",
    "jei.jaymospowers.small_ores.tool": "Mined as its full ore is",
    "jei.jaymospowers.tiny_dust": "Washed out of the crushed ore of a mixed vein, now and then. Nine make a dust; it melts as a nugget does.",
    "jei.jaymospowers.small_ore": "A lone stone of ore in the rock just under the ground. It breaks into one crushed ore; taken whole, it smelts "
                                  "and melts as that crushed ore does. Most lie over a great vein: its own metal, and some of the metal streaked through it.",
    "jei.jaymospowers.dowsing_rod.1": "Right-click a sample onto the rod in your inventory: a raw ore, a crushed ore or a small ore. "
                                      "Right-click the rod with an empty hand to take it out.",
    "jei.jaymospowers.dowsing_rod.2": "Held in either hand, it ticks when ore of the sample's metal lies within %s blocks, faster and higher the nearer it is.",
}
for metal in METALS:
    name = metal.capitalize()
    LANG["block.jaymospowers.small_" + metal + "_ore"] = "Small " + name + " Ore"
    LANG["block.jaymospowers.deepslate_small_" + metal + "_ore"] = "Small Deepslate " + name + " Ore"


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def png(path, im):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im.save(path)


def ref(model):
    return {"model": {"type": "minecraft:model", "model": model}}


def merge_tag(path, values):
    """Adds values to a tag file (ours, appended to, or a new one), keeping what's there."""
    old = json.load(open(path)) if os.path.exists(path) else {"values": []}
    have = old.get("values", [])
    keys = [v if isinstance(v, str) else v.get("id") for v in have]
    for v in values:
        if (v if isinstance(v, str) else v.get("id")) not in keys:
            have.append(v)
    old["values"] = have
    write(path, old)


# ---------------------------------------------------------------- art

def differs(a, b, threshold=40):
    return sum(abs(x - y) for x, y in zip(a[:3], b[:3])) > threshold


def small_ore(ore, base):
    """The ore's spots in one small clump (the densest, kept within SPOT_RADIUS of its middle), the bare rock elsewhere."""
    w, h = ore.size
    spot = [[differs(ore.getpixel((x, y)), base.getpixel((x, y))) for x in range(w)] for y in range(h)]
    best, centre = -1, (w // 2, h // 2)
    for cy in range(3, h - 3):
        for cx in range(3, w - 3):
            n = sum(spot[y][x] for y in range(cy - 3, cy + 4) for x in range(cx - 3, cx + 4))
            if n > best:
                best, centre = n, (cx, cy)
    out = base.copy()
    for y in range(h):
        for x in range(w):
            if spot[y][x] and (x - centre[0]) ** 2 + (y - centre[1]) ** 2 <= SPOT_RADIUS ** 2:
                out.putpixel((x, y), ore.getpixel((x, y)))
    return out


def tiny(dust_colours, glowstone):
    """A pinch of dust: vanilla's glowstone dust on the dust's own colours, shrunk to a small pile at the bottom."""
    pixels = [dust_colours.getpixel((x, y)) for y in range(dust_colours.height) for x in range(dust_colours.width)
              if dust_colours.getpixel((x, y))[3]]
    dark = min(pixels, key=sa.lum)[:3]
    light = max(pixels, key=sa.lum)[:3]
    pile = sa.recolour(glowstone, dark, light).resize((9, 9), Image.NEAREST)
    out = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    out.paste(pile, (4, 6), pile)
    return out


def rod(stick):
    """The forked rod: two arms and a stem in the stick's own browns, a Y lying point-down."""
    colours = sorted({stick.getpixel((x, y))[:3] for y in range(stick.height) for x in range(stick.width) if stick.getpixel((x, y))[3]},
                     key=sum)
    dark, mid, light = colours[0], colours[len(colours) // 2], colours[-1]
    out = Image.new("RGBA", (16, 16), (0, 0, 0, 0))

    def line(x0, y0, x1, y1):
        steps = max(abs(x1 - x0), abs(y1 - y0))
        for i in range(steps + 1):
            x = round(x0 + (x1 - x0) * i / steps)
            y = round(y0 + (y1 - y0) * i / steps)
            out.putpixel((x, y), mid + (255,))
            if x + 1 < 16:
                out.putpixel((x + 1, y), dark + (255,))
            if x - 1 >= 0 and out.getpixel((x - 1, y))[3] == 0:
                out.putpixel((x - 1, y), light + (255,))

    line(3, 1, 7, 8)
    line(12, 1, 8, 8)
    line(8, 8, 8, 15)
    return out


def sample(colours):
    """The sample bound in the rod's fork: a little lump of the metal's colours."""
    dark, light = colours
    out = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    lump = {(7, 6): light, (8, 6): light, (6, 7): light, (7, 7): light, (8, 7): dark, (9, 7): dark, (7, 8): dark, (8, 8): dark}
    for (x, y), c in lump.items():
        out.putpixel((x, y), tuple(c) + (255,))
    return out


def art(jar):
    def vanilla(rel):
        return Image.open(io.BytesIO(jar.read("assets/minecraft/textures/" + rel + ".png"))).convert("RGBA")

    def texture(ref_):
        kind, rel = ref_.split(":", 1)
        if kind == "mc":
            return vanilla(rel)
        return Image.open(os.path.join(ASSETS, "textures", rel + ".png")).convert("RGBA")

    block = os.path.join(ASSETS, "textures", "block", "ores")
    item = os.path.join(ASSETS, "textures", "item", "ores")
    stone, deepslate = vanilla("block/stone"), vanilla("block/deepslate")
    for metal, (_, (ore, deep), _, colours) in METALS.items():
        png(os.path.join(block, "small_" + metal + "_ore.png"), small_ore(texture(ore), stone))
        png(os.path.join(block, "deepslate_small_" + metal + "_ore.png"), small_ore(texture(deep), deepslate))
        png(os.path.join(item, "dowsing_sample_" + metal + ".png"), sample(colours))
    glowstone = vanilla("item/glowstone_dust")
    for metal, (_, dust) in TINY.items():
        png(os.path.join(item, "tiny_" + metal + "_dust.png"), tiny(Image.open(os.path.join(ASSETS, "textures", dust + ".png")).convert("RGBA"), glowstone))
    png(os.path.join(item, "dowsing_rod.png"), rod(vanilla("item/stick")))
    ore_items = os.path.join(ASSETS, "textures", "item", "ore")
    for kind in ("crushed", "purified"):
        iron = Image.open(os.path.join(ore_items, kind + "_iron_ore.png")).convert("RGBA")
        png(os.path.join(ore_items, kind + "_tin_ore.png"), sa.recolour(iron, *METALS["tin"][3]))


# ---------------------------------------------------------------- models

def assets():
    blockstates = os.path.join(ASSETS, "blockstates")
    bmodels = os.path.join(ASSETS, "models", "block", "ores")
    items = os.path.join(ASSETS, "items")
    imodels = os.path.join(ASSETS, "models", "item", "ores")
    for metal in METALS:
        for name in ("small_" + metal + "_ore", "deepslate_small_" + metal + "_ore"):
            write(os.path.join(bmodels, name + ".json"), {"parent": "minecraft:block/cube_all", "textures": {"all": NS + ":block/ores/" + name}})
            write(os.path.join(blockstates, name + ".json"), {"variants": {"": {"model": NS + ":block/ores/" + name}}})
            write(os.path.join(items, name + ".json"), ref(NS + ":block/ores/" + name))
    for metal in TINY:
        name = "tiny_" + metal + "_dust"
        write(os.path.join(imodels, name + ".json"), {"parent": "minecraft:item/generated", "textures": {"layer0": NS + ":item/ores/" + name}})
        write(os.path.join(items, name + ".json"), ref(NS + ":item/ores/" + name))
    for kind in ("crushed", "purified"):
        name = kind + "_tin_ore"
        write(os.path.join(ASSETS, "models", "item", "ore", name + ".json"),
              {"parent": "minecraft:item/generated", "textures": {"layer0": NS + ":item/ore/" + name}})
        write(os.path.join(items, name + ".json"), ref(NS + ":item/ore/" + name))
    # the rod: empty, or with a sample of a metal in its fork (picked by the sample's item id)
    write(os.path.join(imodels, "dowsing_rod.json"), {"parent": "minecraft:item/handheld", "textures": {"layer0": NS + ":item/ores/dowsing_rod"}})
    cases = []
    for metal, (raw, _, _, _) in METALS.items():
        write(os.path.join(imodels, "dowsing_rod_" + metal + ".json"), {"parent": "minecraft:item/handheld", "textures": {
            "layer0": NS + ":item/ores/dowsing_rod", "layer1": NS + ":item/ores/dowsing_sample_" + metal}})
        cases.append({"when": [raw, NS + ":crushed_" + metal + "_ore", NS + ":small_" + metal + "_ore", NS + ":deepslate_small_" + metal + "_ore"],
                      "model": {"type": "minecraft:model", "model": NS + ":item/ores/dowsing_rod_" + metal}})
    write(os.path.join(items, "dowsing_rod.json"), {"model": {"type": "minecraft:select", "property": "minecraft:component",
                                                             "component": NS + ":dowsing_sample", "cases": cases,
                                                             "fallback": {"type": "minecraft:model", "model": NS + ":item/ores/dowsing_rod"}}})


# ---------------------------------------------------------------- data

def recipe(name, data):
    write(os.path.join(DATA, NS, "recipe", "ores", name + ".json"), data)


def data():
    for metal, (dust, _) in TINY.items():
        recipe(dust.split(":")[1] + "_from_tiny", sa.shapeless("misc", [NS + ":tiny_" + metal + "_dust"] * TINY_PER_DUST, dust))
    # the dowsing rod: a forked branch of sticks, string to bind the sample in its fork
    recipe("dowsing_rod", sa.shaped("equipment", ["S S", " T ", " S "], {"S": "minecraft:stick", "T": "minecraft:string"}, NS + ":dowsing_rod"))
    # tin crushes and washes like the others (tinkering/ore/OreWashing)
    recipe("crushed_tin_ore", {"type": NS + ":hammer_crafting", "category": "misc", "ingredients": [NS + ":raw_tin", "#" + NS + ":hammers"],
                               "result": {"id": NS + ":crushed_tin_ore", "count": 1}})
    for kind, time in (("smelting", 200), ("blasting", 100)):
        for src in ("crushed", "purified"):
            recipe("tin_ingot_from_" + kind + "_" + src, {"type": "minecraft:" + kind, "category": "misc", "cookingtime": time, "experience": 0.7,
                                                          "group": "tin_ingot", "ingredient": NS + ":" + src + "_tin_ore",
                                                          "result": {"id": NS + ":tin_ingot"}})
    # a silk-touched small ore smelts like the crushed ore it holds (slowly in a plain furnace: slow_smelt)
    for metal, ingot in INGOTS.items():
        for name in ("small_" + metal + "_ore", "deepslate_small_" + metal + "_ore"):
            for kind, time in (("smelting", 200), ("blasting", 100)):
                recipe(metal + "_ingot_from_" + kind + "_" + name, {"type": "minecraft:" + kind, "category": "misc", "cookingtime": time,
                                                                   "experience": 0.7, "group": metal + "_ingot", "ingredient": NS + ":" + name,
                                                                   "result": {"id": ingot}})
    loot()
    tags()
    worldgen()


def loot():
    for metal in METALS:
        for name in ("small_" + metal + "_ore", "deepslate_small_" + metal + "_ore"):
            write(os.path.join(DATA, NS, "loot_table", "blocks", name + ".json"), {
                "type": "minecraft:block", "random_sequence": NS + ":blocks/" + name, "pools": [{"rolls": 1, "entries": [{
                    "type": "minecraft:alternatives", "children": [
                        {"type": "minecraft:item", "condition": "minecraft:tool/can_silk_touch", "name": NS + ":" + name},
                        {"type": "minecraft:item", "name": NS + ":crushed_" + metal + "_ore", "modifier": [
                            {"type": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"},
                            {"type": "minecraft:explosion_decay"}]}]}]}]})


def tags():
    mc = os.path.join(DATA, "minecraft", "tags", "block")
    c = os.path.join(DATA, "c", "tags", "block")
    every = []
    for metal, (_, _, tool, _) in METALS.items():
        ids = [NS + ":small_" + metal + "_ore", NS + ":deepslate_small_" + metal + "_ore"]
        every += ids
        merge_tag(os.path.join(mc, tool + ".json"), ids)
        # the block tags only (the prospector's pick, the dowsing rod, ore tells): no item tag, so no smelting recipe takes them
        merge_tag(os.path.join(c, "ores", metal + ".json"), ids)
    merge_tag(os.path.join(mc, "mineable", "pickaxe.json"), every)
    merge_tag(os.path.join(DATA, NS, "tags", "item", "slow_smelt.json"), [NS + ":crushed_tin_ore"] + every)
    # silver's ores count as ores (the prospector's pick, ore tells, vein mining), as tin's do
    merge_tag(os.path.join(c, "ores.json"), ["#c:ores/silver"])


def worldgen():
    wg = os.path.join(DATA, NS, "worldgen")
    for feature, (metal, partner) in VEINS.items():
        path = os.path.join(wg, "feature", feature + ".json")
        vein = json.load(open(path))
        vein["partner"], vein["partner_deep"] = FULL_ORE[partner]
        vein["small"], vein["small_deep"] = NS + ":small_" + metal + "_ore", NS + ":deepslate_small_" + metal + "_ore"
        vein["small_partner"], vein["small_partner_deep"] = NS + ":small_" + partner + "_ore", NS + ":deepslate_small_" + partner + "_ore"
        write(path, vein)
    write(os.path.join(wg, "feature", "small_ores.json"), {"type": NS + ":small_ores"})
    write(os.path.join(wg, "placed_feature", "small_ores.json"), {"feature": NS + ":small_ores", "placement": [{"type": "minecraft:biome"}]})


def main():
    if len(sys.argv) != 2 or not os.path.isfile(sys.argv[1]):
        sys.exit(__doc__)
    with zipfile.ZipFile(sys.argv[1]) as jar:
        art(jar)
    assets()
    data()
    write(os.path.join(ASSETS, "lang", "parts", "ores.json"), LANG)


if __name__ == "__main__":
    main()
