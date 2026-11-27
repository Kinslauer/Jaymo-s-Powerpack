"""The ruined barn (row wh_lich, Patch 4 of docs/FaeWoods-Plan.md): a barn on the plains gone to ruin, its altar still standing at the
back. Java: com.jaymoskingdoms.harvest (HarvestAltarBlock: the altar answers in late autumn, at night, to wicked resin).

Layers: 0-1 the foundation, 2 the ground (the floor), 3-7 the walls, 8-11 the roof. The barn runs along z with its big door on the
south end (z = 20) and the altar at the north end. The ruin is random but fixed by each variant's seed: gaps in the walls, a
fallen roof, hay and cobweb in the corners. Blocks of our own are named with their namespace.
"""
import random

W, D, H = 15, 23, 13
X0, X1 = 2, 12      # the walls' west and east faces
Z0, Z1 = 2, 20      # the north and south ends
ALTAR = "jaymoskingdoms:harvest_altar"

STAIR = {"half": "bottom", "shape": "straight", "waterlogged": "false"}


def barn(api, seed, ruin):
    """A barn; `ruin` (0 to 1) is how much of its walls and roof are gone."""
    rnd = random.Random(seed)
    g = api.Grid(W, H, D)

    # the floor: packed earth, planks and hay, a stone sill round it
    for x in range(X0, X1 + 1):
        for z in range(Z0, Z1 + 1):
            g.put(x, 1, z, "dirt")
            g.put(x, 2, z, rnd.choice(["coarse_dirt", "oak_planks", "spruce_planks", "hay_block", "oak_planks", "dirt_path"]) if rnd.random() < 0.7 else "coarse_dirt")
    for x in range(X0, X1 + 1):
        for z in (Z0, Z1):
            g.put(x, 2, z, "mossy_cobblestone")
    for z in range(Z0, Z1 + 1):
        for x in (X0, X1):
            g.put(x, 2, z, "mossy_cobblestone")

    # the walls: weathered planks over a cobblestone course; gaps worn through them
    for y in range(3, 8):
        for x in range(X0, X1 + 1):
            for z in range(Z0, Z1 + 1):
                if x not in (X0, X1) and z not in (Z0, Z1):
                    continue
                gone = rnd.random() < ruin * (0.25 + 0.12 * (y - 3))
                if gone:
                    continue
                if y == 3:
                    g.put(x, y, z, rnd.choice(["cobblestone", "mossy_cobblestone", "oak_planks"]))
                else:
                    g.put(x, y, z, rnd.choice(["spruce_planks", "oak_planks", "spruce_planks", "dark_oak_planks"]))
    # the big door: a gap in the south wall with the doors hanging off it
    for y in range(3, 7):
        for x in range(6, 9):
            g.clear(x, y, Z1)
    g.put(6, 3, Z1 + 1, "spruce_fence", {"north": "false", "south": "false", "east": "false", "west": "false", "waterlogged": "false"})
    g.put(8, 3, Z1 + 1, "spruce_fence", {"north": "false", "south": "false", "east": "false", "west": "false", "waterlogged": "false"})
    # windows
    for z in (6, 11, 16):
        for x in (X0, X1):
            g.clear(x, 5, z)
    # corner posts, if they stood
    for (x, z) in ((X0, Z0), (X1, Z0), (X0, Z1), (X1, Z1)):
        for y in range(3, 9):
            if rnd.random() > ruin * 0.5:
                g.put(x, y, z, "stripped_oak_log", {"axis": "y"})

    # the roof: a gable on stairs, then slabs along the ridge; much of it fallen in
    for z in range(Z0, Z1 + 1):
        for k, y in enumerate(range(8, 12)):
            for side, face in ((-1, "east"), (1, "west")):
                x = (X0 - 1 + k) if side < 0 else (X1 + 1 - k)
                if rnd.random() < ruin * (0.45 + 0.1 * k):
                    continue
                g.put(x, y, z, "spruce_stairs", dict(STAIR, facing=face))
        if rnd.random() >= ruin * 0.7:
            g.put((X0 + X1) // 2, 12, z, "spruce_slab", {"type": "bottom", "waterlogged": "false"})
    # roof beams over the floor
    for z in range(Z0 + 2, Z1, 4):
        for x in range(X0 + 1, X1):
            if rnd.random() > ruin * 0.5:
                g.put(x, 8, z, "oak_log", {"axis": "x"})

    # the altar at the north end, on a dais, between two pumpkins and a candle
    cx = (X0 + X1) // 2
    for x in range(cx - 2, cx + 3):
        for z in range(Z0 + 1, Z0 + 4):
            g.put(x, 3, z, "polished_blackstone")
    g.put(cx, 4, Z0 + 2, ALTAR)
    g.put(cx - 2, 4, Z0 + 2, "carved_pumpkin", {"facing": "south"})
    g.put(cx + 2, 4, Z0 + 2, "carved_pumpkin", {"facing": "south"})
    g.put(cx - 1, 4, Z0 + 1, "candle", {"candles": "3", "lit": "false", "waterlogged": "false"})
    g.put(cx + 1, 4, Z0 + 1, "candle", {"candles": "2", "lit": "false", "waterlogged": "false"})
    # what is kept in the barn: a chest by the west wall, hay, a scarecrow by the door, webs, a hayloft's floor
    g.chest(X0 + 1, 3, Z0 + 5, "chests/wicked_barn", "east")
    for (x, z) in ((X1 - 1, Z0 + 4), (X1 - 1, Z0 + 5), (X1 - 2, Z0 + 4), (X0 + 1, Z1 - 3), (X0 + 1, Z1 - 4), (X1 - 1, Z1 - 3)):
        if rnd.random() > 0.2:
            g.put(x, 3, z, "hay_block", {"axis": "y"})
    for (x, z) in ((X0 + 1, Z0 + 1), (X1 - 1, Z0 + 1), (X0 + 1, Z1 - 1), (X1 - 1, Z1 - 1)):
        g.put(x, 7, z, "cobweb")
    for x in range(X0 + 1, X1):
        for z in range(Z1 - 5, Z1 - 1):
            if rnd.random() < 0.65 - ruin * 0.3:
                g.put(x, 7, z, "oak_planks" if rnd.random() < 0.5 else "spruce_slab", {} if False else None)
    # (the loft planks above are plain full blocks or bottom slabs; a slab needs its properties)
    for (x, y, z) in list(g.b):
        name = g.b[(x, y, z)][0]
        if name == "minecraft:spruce_slab" and not g.b[(x, y, z)][1]:
            g.put(x, y, z, "spruce_slab", {"type": "top", "waterlogged": "false"})
    # the scarecrow at the door: a post, a hay body, a carved pumpkin head
    sx, sz = 10, Z1 + 3
    g.put(sx, 2, sz, "coarse_dirt")
    g.put(sx, 3, sz, "oak_fence", {"north": "false", "south": "false", "east": "false", "west": "false", "waterlogged": "false"})
    g.put(sx, 4, sz, "hay_block", {"axis": "y"})
    g.put(sx, 5, sz, "carved_pumpkin", {"facing": "north"})
    # the yard: dead crops outside
    for x in range(1, W - 1):
        for z in range(Z1 + 2, D):
            if rnd.random() < 0.18:
                g.put(x, 3, z, rnd.choice(["dead_bush", "wheat", "pumpkin"]), {"age": "3"} if False else None)
    for (x, y, z) in list(g.b):
        if g.b[(x, y, z)][0] == "minecraft:wheat" and not g.b[(x, y, z)][1]:
            g.put(x, y, z, "wheat", {"age": "3"})
            g.put(x, y - 1, z, "farmland", {"moisture": "0"})
    return g


def sites(api):
    return [api.Site("wicked_barn", {
        "wicked_barn_standing": barn(api, 4021, 0.25),
        "wicked_barn_ruined": barn(api, 7733, 0.6),
    }, ground=2, worldgen={
        "step": "surface_structures", "terrain_adaptation": "none",
        "biomes": ["minecraft:plains", "minecraft:sunflower_plains", "minecraft:meadow"],
        "spacing": 52, "separation": 18, "salt": 1129402371,
    })]
