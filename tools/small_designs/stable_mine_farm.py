"""Stable, mine and farm (village mods patch 7): three original worldgen designs for Kingdoms.

Drawn from Andy's screenshots: a chimney house with a horse (stable), a fenced open quarry
(mine), and farmland beside a home (farm). Placed by track 2's PlacementReasons - stables by
towns, mines by buyers, farms by homes - through the structure tags written by hand in
tags/worldgen/structure/. No Java: like patch 6 (PR #2110), the designs are block grids here
and tools/small_structures.py writes the NBT templates and the worldgen JSONs from them.
"""

CARDINALS = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}


def fences(g, y, cells, name):
    """Fence posts at `cells` (x, z) on layer y, each joined to the fence posts beside it."""
    cells = set(cells)
    for (x, z) in cells:
        props = {"waterlogged": "false"}
        for d, (dx, dz) in CARDINALS.items():
            props[d] = "true" if (x + dx, z + dz) in cells else "false"
        g.put(x, y, z, name, props)


def rect(x0, x1, z0, z1):
    return [(x, z) for x in range(x0, x1 + 1) for z in range(z0, z1 + 1)]


# ---- stable --------------------------------------------------------------------------------------------------
# A chimney house with a horse: a stone foundation, a plank floor, hay stalls and a trough,
# oak walls with a door and two windows, a spruce roof, lanterns inside, and a brick chimney
# whose cap sits above the roof. Layers: 1 the foundation, 2 the ground (the floor), 3-5 the
# walls, 6 the roof, 7-9 the chimney, 10 its cap.
def stable(api):
    g = api.Grid(13, 11, 13)
    c = 6
    ring = [(x, z) for x in range(13) for z in range(13) if x in (1, 11) or z in (1, 11)]
    posts = {(1, 1), (1, 11), (11, 1), (11, 11), (6, 1), (6, 11), (1, 6), (11, 6)}
    # the foundation sits one block under the ground
    for (x, z) in rect(1, 11, 1, 11):
        g.put(x, 1, z, "cobblestone")
    # the ground: plank floor inside the walls, four hay stalls and a water trough
    for (x, z) in rect(2, 10, 2, 10):
        g.put(x, 2, z, "oak_planks")
    for (x, z) in ((3, 4), (3, 8), (9, 4), (9, 8)):
        g.put(x, 2, z, "hay_block")
    g.put(6, 2, 6, "water_cauldron")
    # the walls: oak posts, cobblestone between, a door in the north wall, windows east and west
    for (x, z) in ring:
        if (x, z) in posts:
            for y in (3, 4, 5):
                g.put(x, y, z, "oak_log", {"axis": "y"})
        elif (x, z) != (6, 1):
            for y in (3, 4, 5):
                g.put(x, y, z, "cobblestone")
    g.clear(6, 3, 1); g.clear(6, 4, 1)
    g.put(6, 3, 1, "oak_door", {"facing": "south", "half": "lower", "hinge": "left", "open": "false", "powered": "false"})
    g.put(6, 4, 1, "oak_door", {"facing": "south", "half": "upper", "hinge": "left", "open": "false", "powered": "false"})
    g.clear(1, 4, 6); g.clear(11, 4, 6)
    # the roof, the chimney and the lanterns hanging from it
    for (x, z) in rect(1, 11, 1, 11):
        if (x, z) != (3, 3):
            g.put(x, 6, z, "spruce_planks")
    for y in (7, 8, 9):
        g.put(3, y, 3, "bricks")
    g.put(3, 10, 3, "brick_stairs",
          {"half": "lower", "facing": "north", "waterlogged": "false", "shape": "straight"})
    for (x, z) in ((3, 4), (3, 8), (9, 4), (9, 8)):
        g.put(x, 5, z, "lantern", {"hanging": "true", "waterlogged": "false"})
    return g


# ---- mine ----------------------------------------------------------------------------------------------------
# A fenced open quarry: an oak fence round the mouth with a gate, timber corner posts down
# the pit, a lantern at the top of each, and ore in the floor. Layers: 0 the floor, 1-8 the
# pit, 9 the ground (the fence).
def mine(api):
    g = api.Grid(15, 10, 15)
    ring = [(x, z) for x in range(15) for z in range(15) if x in (0, 14) or z in (0, 14)]
    fences(g, 9, ring, "oak_fence")
    g.put(7, 9, 14, "oak_fence_gate",
          {"facing": "north", "open": "false", "waterlogged": "false", "in_wall": "false", "powered": "false"})
    # the pit: air down to the floor
    for y in range(1, 9):
        for (x, z) in rect(4, 10, 4, 10):
            g.clear(x, y, z)
    # the floor: stone with ore in it
    for (x, z) in rect(3, 11, 3, 11):
        g.put(x, 0, z, "stone")
    for (x, z, ore) in ((5, 5, "coal_ore"), (6, 6, "iron_ore"), (7, 5, "copper_ore"), (8, 7, "coal_ore"),
                        (9, 6, "iron_ore"), (5, 8, "copper_ore"), (7, 8, "coal_ore")):
        g.put(x, 0, z, ore)
    # timber corner posts, each capped with a lantern below the ground
    for (x, z) in ((4, 4), (4, 10), (10, 4), (10, 10)):
        for y in range(1, 8):
            g.put(x, y, z, "oak_log", {"axis": "y"})
        g.put(x, 8, z, "lantern", {"hanging": "false", "waterlogged": "false"})
    return g


# ---- farm ----------------------------------------------------------------------------------------------------
# Farmland beside a home: four plots crossed by dirt paths, each plot with its crop rows,
# a tool shed in the south-east corner, a fence round the lot, lanterns at its corners and
# a scarecrow in the first plot. Layers: 0 the earth, 1 the ground (farmland and paths),
# 2 the shed's roof.
def farm(api):
    g = api.Grid(15, 3, 15)
    cross = [(x, z) for x in range(2, 13) for z in (6, 7, 8)] + [(x, z) for z in range(2, 13) for x in (6, 7, 8)]
    cross_set = set(cross)
    for (x, z) in cross:
        g.put(x, 1, z, "dirt_path")
    shed = set(rect(11, 13, 11, 13))
    for (x, z) in rect(2, 12, 2, 12):
        if (x, z) not in cross_set and (x, z) not in shed:
            g.put(x, 0, z, "dirt")
    plots = {"wheat": rect(2, 5, 2, 5), "carrots": rect(9, 12, 2, 5),
             "potatoes": rect(2, 5, 9, 12), "wheat2": rect(9, 12, 9, 12)}
    crop = {"wheat": "wheat", "carrots": "carrots", "potatoes": "potatoes", "wheat2": "wheat"}
    want = {"wheat": 0, "carrots": 1, "potatoes": 2, "wheat2": 0}
    for kind, cells in plots.items():
        for (x, z) in cells:
            g.put(x, 1, z, "farmland", {"moisture": "7"})
            if (x + z) % 3 == want[kind]:
                g.put(x, 1, z, crop[kind], {"age": "7"})
    # the shed: log walls, a plank roof that overhangs, a door, hay and a trough inside
    for (x, z) in shed:
        if x in (11, 13) or z in (11, 13):
            for y in (1, 2):
                g.put(x, y, z, "oak_log", {"axis": "y"})
    for (x, z) in rect(10, 14, 10, 14):
        if (x, z) not in shed:
            g.put(x, 3, z, "spruce_planks")
    g.clear(12, 1, 11); g.clear(12, 2, 11)
    g.put(12, 1, 11, "oak_door", {"facing": "south", "half": "lower", "hinge": "left", "open": "false", "powered": "false"})
    g.put(12, 2, 11, "oak_door", {"facing": "south", "half": "upper", "hinge": "left", "open": "false", "powered": "false"})
    g.put(12, 1, 12, "hay_block")
    g.put(11, 1, 12, "water_cauldron")
    # fences round the farm, lanterns at its corners, a gate on the north side, a scarecrow
    ring = [(x, z) for x in range(15) for z in range(15) if x in (0, 14) or z in (0, 14)]
    fences(g, 1, ring, "oak_fence")
    g.put(7, 1, 0, "oak_fence_gate",
          {"facing": "south", "open": "false", "waterlogged": "false", "in_wall": "false", "powered": "false"})
    for (x, z) in ((0, 0), (0, 14), (14, 0), (14, 14)):
        g.put(x, 2, z, "lantern", {"hanging": "false", "waterlogged": "false"})
    g.put(2, 1, 2, "oak_fence",
          {"waterlogged": "false", "north": "false", "south": "false", "east": "false", "west": "false"})
    g.put(2, 2, 2, "pumpkin")
    return g


def sites(api):
    return [
        api.Site("stable", {"stable": stable(api)}, ground=2, worldgen={
            "step": "surface_structures", "terrain_adaptation": "none",
            "biomes": ["minecraft:plains", "minecraft:sunflower_plains", "minecraft:meadow",
                       "minecraft:snowy_plains", "minecraft:savanna", "minecraft:savanna_plateau",
                       "minecraft:taiga", "minecraft:birch_forest", "minecraft:flower_forest"],
            "spacing": 34, "separation": 12, "salt": 2117731,
        }),
        api.Site("mine", {"mine": mine(api)}, ground=9, worldgen={
            "step": "surface_structures", "terrain_adaptation": "none",
            "biomes": ["minecraft:badlands", "minecraft:eroded_badlands", "minecraft:wooded_badlands",
                       "minecraft:stony_shore", "minecraft:windswept_hills", "minecraft:windswept_gravelly_hills",
                       "minecraft:savanna_plateau", "minecraft:windswept_forest"],
            "spacing": 38, "separation": 16, "salt": 9982213,
        }),
        api.Site("farm", {"farm": farm(api)}, ground=1, worldgen={
            "step": "surface_structures", "terrain_adaptation": "none",
            "biomes": ["minecraft:plains", "minecraft:sunflower_plains", "minecraft:meadow",
                       "minecraft:snowy_plains", "minecraft:savanna", "minecraft:taiga"],
            "spacing": 28, "separation": 10, "salt": 5543321,
        }),
    ]
