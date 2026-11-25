"""Cairn caches (row rc_cairn): heaps of field stones over a buried chest. Java: Cairns.java."""
import math
import random

STONES = ["cobblestone", "cobblestone", "mossy_cobblestone", "stone", "andesite", "tuff"]


def cairn(api, seed, radius, height, long=False):
    """A pile of field stones over a buried chest. Layers: 0 under the chest, 1 the chest, 2 the earth over it, 3 the
    ground (the base stones replace its top), then the pile, narrowing, a single stone on top."""
    rnd = random.Random(seed)
    w = 2 * radius + 1 + (2 if long else 0)
    d = 2 * radius + 1
    g = api.Grid(w, 5 + height, d)
    cx, cz = w // 2, d // 2
    g.chest(cx, 1, cz, "chests/cairn", rnd.choice(["north", "south", "east", "west"]))
    g.put(cx, 2, cz, "coarse_dirt")
    # the ground layer is set stones flush with the turf; the heap rises over it, narrowing
    for layer in range(height + 1):
        r = radius + 0.5 - layer * (radius + 0.5) / (height + 0.5)
        for x in range(w):
            for z in range(d):
                dx = x - cx
                if long:
                    dx = max(0, abs(dx) - 1)
                if math.hypot(dx, z - cz) <= r + 0.15 - rnd.random() * 0.3:
                    g.put(x, 3 + layer, z, rnd.choice(STONES))
    top = 4 + height
    g.put(cx, top, cz, rnd.choice(["cobblestone_wall", "mossy_cobblestone_wall"]),
          {"up": "true", "north": "none", "south": "none", "east": "none", "west": "none", "waterlogged": "false"})
    return g


def sites(api):
    return [api.Site("cairn", {
        "cairn_round": cairn(api, 11, 2, 3),
        "cairn_tall": cairn(api, 23, 2, 4),
        "cairn_long": cairn(api, 37, 2, 3, long=True),
        "cairn_small": cairn(api, 41, 1, 2),
    }, ground=3, worldgen={
        "step": "surface_structures", "terrain_adaptation": "none",
        "biomes": ["#minecraft:is_hill", "#minecraft:is_mountain", "#minecraft:is_taiga", "minecraft:plains",
                   "minecraft:meadow", "minecraft:snowy_plains", "minecraft:sunflower_plains", "minecraft:windswept_hills",
                   "minecraft:windswept_gravelly_hills", "minecraft:stony_shore", "minecraft:savanna", "minecraft:savanna_plateau"],
        "spacing": 26, "separation": 9, "salt": 618204733,
    })]
