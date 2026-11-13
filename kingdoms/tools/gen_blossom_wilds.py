#!/usr/bin/env python3
"""
What grows on Mohknee (Blossom Lands patch 10; blossomland/BlossomWilds.java): draws the maple and ginkgo leaves (green,
and turned), their saplings, the red spider lily and the wisteria (our own, Pillow-drawn), and writes their models,
blockstates, loot, tags, names, the two trees and the far land's placed features. Run from kingdoms/:

    python3 -I tools/gen_blossom_wilds.py
"""
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_blossom_blocks as g  # noqa: E402

NS, ASSETS, DATA, RES = g.NS, g.ASSETS, g.DATA, g.RES
own = lambda n: NS + ":" + n

NAMES = {
    "maple_leaves": "Maple Leaves", "ginkgo_leaves": "Ginkgo Leaves", "maple_sapling": "Maple Sapling",
    "ginkgo_sapling": "Ginkgo Sapling", "red_spider_lily": "Red Spider Lily", "wisteria": "Wisteria",
}

GREEN = [(0x2c, 0x5a, 0x24), (0x3a, 0x72, 0x2e), (0x4c, 0x8a, 0x38), (0x66, 0xa4, 0x48)]
PALE_GREEN = [(0x4a, 0x76, 0x2c), (0x5e, 0x8e, 0x36), (0x76, 0xa6, 0x42), (0x92, 0xbe, 0x56)]
RED = [(0x7a, 0x14, 0x10), (0xa8, 0x22, 0x16), (0xcc, 0x3a, 0x1c), (0xe8, 0x62, 0x2a)]
GOLD = [(0xa8, 0x7a, 0x12), (0xcc, 0x9c, 0x1a), (0xe6, 0xbc, 0x2a), (0xf6, 0xda, 0x58)]
BARK = [(0x3e, 0x2c, 0x1e), (0x58, 0x40, 0x2a), (0x72, 0x56, 0x3a)]
LILAC = [(0x6a, 0x4a, 0xa0), (0x86, 0x62, 0xbe), (0xa4, 0x84, 0xd8), (0xc8, 0xb0, 0xee)]


def draw_leaves(name, pal, seed, fan):
    """a leaf block: clumps of small leaves with gaps between them; fan = the ginkgo's rounder, paler blades"""
    rng = random.Random(seed)
    img = g.canvas()
    for y in range(16):
        for x in range(16):
            if rng.random() < 0.16:
                continue  # a gap to see through
            k = (x * 3 + y * 5 + rng.randint(0, 2)) % 7
            c = pal[0] if k == 0 else pal[1] if k < 3 else pal[2] if k < 6 else pal[3]
            g.put(img, x, y, c)
    for _ in range(9):
        x, y = rng.randint(0, 14), rng.randint(0, 14)
        if fan:
            for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)):
                g.put(img, x + dx, y + dy, pal[3])
            g.put(img, x, y + 2, pal[1])
        else:
            for dx, dy in ((1, 0), (0, 1), (1, 1), (2, 1), (1, 2)):
                g.put(img, x + dx, y + dy, pal[3])
            g.put(img, x + 1, y + 1, pal[2])
    g.save(img, name)


def draw_sapling(name, pal, fan):
    img = g.canvas()
    for y in range(8, 16):
        g.put(img, 7 + (1 if y in (10, 11) else 0), y, BARK[1 if y % 2 else 2])
    spots = [(5, 3), (9, 2), (7, 1), (4, 6), (10, 5), (7, 5), (6, 8), (10, 8)]
    for i, (x, y) in enumerate(spots):
        c = pal[2 + i % 2]
        for dx, dy in (((0, 0), (1, 0), (0, 1), (1, 1)) if fan else ((1, 0), (0, 1), (1, 1), (2, 1), (1, 2))):
            g.put(img, x + dx, y + dy, c)
        g.put(img, x + 1, y + 1, pal[1])
    g.save(img, name)


def draw_lily():
    """a bare green stalk and on it a ring of narrow curled red petals with long stamens reaching past them"""
    img = g.canvas()
    for y in range(7, 16):
        g.put(img, 8, y, GREEN[2 if y % 3 else 1])
    for x, y in ((4, 5), (5, 4), (6, 3), (7, 3), (8, 3), (9, 3), (10, 4), (11, 5), (5, 6), (10, 6), (6, 6), (9, 6), (7, 5), (8, 5), (7, 6), (8, 6)):
        g.put(img, x, y, RED[2])
    for x, y in ((6, 4), (9, 4), (7, 4), (8, 4)):
        g.put(img, x, y, RED[3])
    for x, y in ((3, 6), (12, 6), (5, 7), (10, 7)):
        g.put(img, x, y, RED[1])
    for x, y in ((2, 3), (3, 2), (13, 3), (12, 2), (5, 1), (10, 1), (7, 0), (1, 5), (14, 5)):
        g.put(img, x, y, RED[3])
    for x, y in ((3, 4), (12, 4), (4, 2), (11, 2), (6, 1), (9, 1), (2, 5), (13, 5)):
        g.put(img, x, y, RED[1], 200)
    g.save(img, "red_spider_lily")


def draw_wisteria():
    """a hanging raceme: a thin green stem and pea flowers down it, wide at the top and thinning to a point"""
    rng = random.Random(77)
    img = g.canvas()
    for stem, top, length in ((4, 0, 13), (9, 0, 16), (13, 0, 10)):
        for y in range(top, top + length):
            g.put(img, stem, y, PALE_GREEN[1])
            width = 2 if y < top + length * 0.5 else 1 if y < top + length - 2 else 0
            for dx in range(-width, width + 1):
                if dx and rng.random() < 0.8:
                    g.put(img, stem + dx, y, LILAC[rng.choice((1, 2, 2, 3))])
            if rng.random() < 0.5:
                g.put(img, stem, y, LILAC[0])
    for x in (2, 3, 6, 7, 11, 12, 14):
        g.put(img, x, 0, PALE_GREEN[2])
    g.save(img, "wisteria")


def textures():
    draw_leaves("maple_leaves", GREEN, 11, False)
    draw_leaves("maple_leaves_turned", RED, 11, False)
    draw_leaves("ginkgo_leaves", PALE_GREEN, 12, True)
    draw_leaves("ginkgo_leaves_turned", GOLD, 12, True)
    draw_sapling("maple_sapling", RED, False)
    draw_sapling("ginkgo_sapling", GOLD, True)
    draw_lily()
    draw_wisteria()


def assets():
    for tree in ("maple", "ginkgo"):
        name = tree + "_leaves"
        for suffix in ("", "_turned"):
            g.write(ASSETS + "models/block/" + name + suffix + ".json", {"parent": "minecraft:block/leaves", "textures": {"all": g.tex(name + suffix)}})
        g.write(ASSETS + "blockstates/" + name + ".json", {"variants": {"turned=false": {"model": g.mdl(name)}, "turned=true": {"model": g.mdl(name + "_turned")}}})
        g.item(name, g.mdl(name + "_turned"))
    for name in ("maple_sapling", "ginkgo_sapling", "red_spider_lily", "wisteria"):
        g.write(ASSETS + "models/block/" + name + ".json", {"parent": "minecraft:block/cross", "textures": {"cross": g.tex(name)}})
        g.write(ASSETS + "blockstates/" + name + ".json", {"variants": {"": {"model": g.mdl(name)}}})
        g.write(ASSETS + "models/item/" + name + ".json", {"parent": "minecraft:item/generated", "textures": {"layer0": g.tex(name)}})
        g.item(name, NS + ":item/" + name)


def loot():
    shears = {"type": "minecraft:any_of", "terms": ["minecraft:tool/can_shear", "minecraft:tool/can_silk_touch"]}
    for tree in ("maple", "ginkgo"):
        name = tree + "_leaves"
        g.write(DATA + "loot_table/blocks/" + name + ".json", {"type": "minecraft:block", "pools": [
            {"rolls": 1, "entries": [{"type": "minecraft:alternatives", "children": [
                {"type": "minecraft:item", "condition": shears, "name": own(name)},
                {"type": "minecraft:item", "condition": {"type": "minecraft:all_of", "terms": [{"type": "minecraft:survives_explosion"},
                    {"type": "minecraft:table_bonus", "chances": [0.05, 0.0625, 0.083333336, 0.1], "enchantment": "minecraft:fortune"}]},
                    "name": own(tree + "_sapling")}]}]},
            {"rolls": 1, "condition": {"type": "minecraft:inverted", "term": shears}, "entries": [{"type": "minecraft:item",
                "condition": {"type": "minecraft:table_bonus", "chances": [0.02, 0.022222223, 0.025, 0.033333335, 0.1], "enchantment": "minecraft:fortune"},
                "modifier": [{"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "max": 2, "min": 1}}, {"type": "minecraft:explosion_decay"}],
                "name": "minecraft:stick"}]}],
            "random_sequence": own("blocks/" + name)})
    for name in ("maple_sapling", "ginkgo_sapling", "red_spider_lily", "wisteria"):
        g.loot(name)


def tree(name, log, leaves, base, rand, foliage):
    g.write(DATA + "worldgen/feature/" + name + ".json", {
        "type": "minecraft:tree", "below_trunk_provider": "minecraft:soil_beneath_tree", "decorators": [], "foliage_placer": foliage,
        "foliage_provider": {"id": own(leaves), "properties": {"distance": "7", "persistent": "false", "turned": "false", "waterlogged": "false"}},
        "ignore_vines": True, "minimum_size": {"type": "minecraft:two_layers_feature_size"},
        "trunk_placer": {"type": "minecraft:straight_trunk_placer", "base_height": base, "height_rand_a": rand, "height_rand_b": 0},
        "trunk_provider": {"id": "minecraft:" + log, "properties": {"axis": "y"}}})


def spread(reach, rise):
    side = {"type": "minecraft:trapezoid", "max": reach, "min": -reach, "plateau": 0}
    return {"type": "minecraft:offset", "x": side, "y": {"type": "minecraft:trapezoid", "max": rise, "min": -rise, "plateau": 0} if rise else 0, "z": side}


def worldgen():
    # the maple: low and spreading on a dark trunk; the ginkgo: a tall straight grey trunk under a narrow crown
    tree("maple", "dark_oak_log", "maple_leaves", 4, 2, {"type": "minecraft:blob_foliage_placer", "height": 3, "offset": 0, "radius": 2})
    tree("ginkgo", "spruce_log", "ginkgo_leaves", 7, 3, {"type": "minecraft:spruce_foliage_placer",
        "offset": {"type": "minecraft:uniform", "max_inclusive": 1, "min_inclusive": 0},
        "radius": {"type": "minecraft:uniform", "max_inclusive": 2, "min_inclusive": 1},
        "trunk_height": {"type": "minecraft:uniform", "max_inclusive": 3, "min_inclusive": 2}})
    g.write(DATA + "worldgen/feature/red_spider_lily.json", {"type": "minecraft:simple_block", "to_place": {"id": own("red_spider_lily")}})
    far = {"type": own("on_mohknee")}
    square, biome = {"type": "minecraft:in_square"}, {"type": "minecraft:biome"}
    ground = {"type": "minecraft:heightmap", "heightmap": "MOTION_BLOCKING"}
    air = {"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:matching_block_tag", "tag": "minecraft:air"}}
    for name, sapling, one_in in (("maple", "maple_sapling", 3), ("ginkgo", "ginkgo_sapling", 5)):
        g.write(DATA + "worldgen/placed_feature/mohknee_" + name + "s.json", {"feature": own(name), "placement": [
            far, {"type": "minecraft:rarity_filter", "chance": one_in}, {"type": "minecraft:count", "count": 2}, square,
            {"type": "minecraft:surface_water_depth_filter", "max_water_depth": 0}, {"type": "minecraft:heightmap", "heightmap": "OCEAN_FLOOR"}, biome,
            {"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:would_survive", "state": own(sapling)}}]})
    # a thicket: one chunk in sixteen, forty canes within a few blocks of each other
    g.write(DATA + "worldgen/placed_feature/mohknee_bamboo.json", {"feature": "minecraft:bamboo_no_podzol", "placement": [
        far, {"type": "minecraft:rarity_filter", "chance": 16}, square, {"type": "minecraft:count", "count": 40}, spread(5, 0), ground, biome]})
    g.write(DATA + "worldgen/placed_feature/mohknee_spider_lilies.json", {"feature": own("red_spider_lily"), "placement": [
        far, {"type": "minecraft:rarity_filter", "chance": 12}, square, {"type": "minecraft:count", "count": 28}, spread(4, 0), ground, biome, air]})


def tags():
    leaves, saplings = [own("maple_leaves"), own("ginkgo_leaves")], [own("maple_sapling"), own("ginkgo_sapling")]
    for kind in ("block", "item"):
        g.merge_tag(RES + "data/minecraft/tags/" + kind + "/leaves.json", leaves)
        g.merge_tag(RES + "data/minecraft/tags/" + kind + "/saplings.json", saplings)
        g.merge_tag(RES + "data/minecraft/tags/" + kind + "/small_flowers.json", [own("red_spider_lily")])
    g.merge_tag(RES + "data/minecraft/tags/block/mineable/hoe.json", leaves)
    g.merge_tag(RES + "data/minecraft/tags/block/sword_efficient.json", [own("wisteria")])
    g.merge_tag(RES + "data/minecraft/tags/block/bee_attractive.json", [own("wisteria")])


if __name__ == "__main__":
    textures()
    assets()
    loot()
    worldgen()
    tags()
    g.write(ASSETS + "lang/parts/blossom_wilds.json", {"block." + NS + "." + k: v for k, v in NAMES.items()})
    print("blossom wilds: %d" % len(NAMES))
