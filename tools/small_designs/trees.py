"""Tree structures (row rc_trees): a hermit's hollow oak, a bandit lookout in a great spruce, a hanging tree with a crow.
Java: TreeSites.java (who moves in), Crow (the carrion bird), Hermits.java (the oak hermit's words).

The Java finds its marks by scanning the placed piece (the jigsaw turns a piece by a random quarter), so these marks stay:
the hollow oak's lectern (the hermit stands beside it), the lookout's chest (its watchers stand on the platform near it),
the hanging tree's skeleton skull (the hanged; the crow's home) and its standing sign (the hanging list).
"""
import math
import random

LEAF_OAK = {"distance": "1", "persistent": "true", "waterlogged": "false"}
CARDINALS = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}


def log(g, x, y, z, name, axis="y"):
    g.put(x, y, z, name, {"axis": axis})


def leaf(g, x, y, z, name):
    """A persistent leaf block where nothing else is drawn."""
    if g.get(x, y, z) is None:
        g.put(x, y, z, name, dict(LEAF_OAK))


def fences(g, y, cells, name):
    """Fence posts at `cells` (x, z) on layer y, each joined to the fence posts beside it."""
    cells = set(cells)
    for (x, z) in cells:
        props = {"waterlogged": "false"}
        for d, (dx, dz) in CARDINALS.items():
            props[d] = "true" if (x + dx, z + dz) in cells else "false"
        g.put(x, y, z, name, props)


def blob(g, cx, cz, y0, y1, radius_of, name, skip=None):
    """Leaves in a disc round (cx, cz) on each layer y0..y1, radius_of(layer) wide, a little ragged; skip(x, y, z) leaves holes."""
    rnd = random.Random(cx * 31 + cz * 17 + y0)
    for y in range(y0, y1 + 1):
        r = radius_of(y)
        for x in range(g.w):
            for z in range(g.d):
                d = math.hypot(x - cx, z - cz)
                if d <= r - (0.6 if rnd.random() < 0.3 and d > r - 1.5 else 0) and not (skip and skip(x, y, z)):
                    leaf(g, x, y, z, name)


# ---- the hermit's hollow oak -------------------------------------------------------------------------------------------

def hollow_oak():
    """A giant old oak, bark all round, hollow from the roots up: a room at the ground with a plank floor and a door, a
    ladder to a loft, a window, the hermit's things; the trunk is closed over the loft and the crown sits on it. Layers:
    0-1 the foundation, 2 the ground (the floor), 3-6 the room, 7 the loft floor, 8-10 the loft, 11 the roof, 12-19 the crown."""
    g = _api.Grid(17, 20, 17)
    w = g.w
    c = 8
    lo, hi = c - 3, c + 2            # the trunk is six across: 5..10
    ilo, ihi = lo + 1, hi - 1        # the hollow is four across: 6..9
    for y in range(0, 12):
        for x in range(lo, hi + 1):
            for z in range(lo, hi + 1):
                inner = ilo <= x <= ihi and ilo <= z <= ihi
                if y == 2 and inner:
                    g.put(x, y, z, "spruce_planks")
                elif 3 <= y <= 10 and inner:
                    g.clear(x, y, z)
                else:
                    log(g, x, y, z, "oak_wood")
    # the loft floor (a hole where the ladder comes up)
    for x in range(ilo, ihi + 1):
        for z in range(ilo, ihi + 1):
            if (x, z) != (6, 7):
                g.put(x, 7, z, "spruce_planks")
    # the way in: a door in the south wall, a path to it
    g.clear(7, 3, hi); g.clear(7, 4, hi)
    g.put(7, 3, hi, "oak_door", {"facing": "south", "half": "lower", "hinge": "left", "open": "false", "powered": "false"})
    g.put(7, 4, hi, "oak_door", {"facing": "south", "half": "upper", "hinge": "left", "open": "false", "powered": "false"})
    for z in range(hi + 1, hi + 4):
        for x in (6, 7, 8):
            g.put(x, 2, z, "dirt_path")
    # a window in the loft's east wall
    g.clear(hi, 9, 7); g.clear(hi, 9, 8)
    # the ladder, against the west wall
    for y in range(3, 9):
        g.put(ilo, y, 7, "ladder", {"facing": "east", "waterlogged": "false"})
    # roots: bark laid out from the trunk at the ground, the first block a step higher beside it
    rnd = random.Random(2024)
    path = {(6, hi + 1), (7, hi + 1), (8, hi + 1), (6, hi + 2), (7, hi + 2), (8, hi + 2), (6, hi + 3), (7, hi + 3), (8, hi + 3)}
    for (dx, dz) in ((-1, 0), (1, 0), (0, -1), (0, 1)):
        for along in rnd.sample(range(lo, hi + 1), 2):
            for out in range(1, 4):
                x = (lo - out if dx < 0 else hi + out) if dx else along
                z = (lo - out if dz < 0 else hi + out) if dz else along
                if (x, z) in path:
                    continue
                g.put(x, 2, z, "oak_wood", {"axis": "x" if dx else "z"})
                if out == 1:
                    g.put(x, 3, z, "oak_wood", {"axis": "x" if dx else "z"})
    for (x, z) in ((lo - 1, lo - 1), (hi + 1, lo - 1), (hi + 1, hi + 1), (lo - 1, hi + 1)):
        g.put(x, 2, z, "oak_wood", {"axis": "y"})
    # the room: a straw bed, books, candles, a rug, a stand of potions, a lectern
    g.put(8, 3, 6, "straw_bed", {"facing": "east", "part": "foot", "occupied": "false"})
    g.put(9, 3, 6, "straw_bed", {"facing": "east", "part": "head", "occupied": "false"})
    for x in (6, 7):
        g.put(x, 3, 6, "bookshelf"); g.put(x, 4, 6, "bookshelf")
        g.put(x, 5, 6, "candle", {"candles": "2" if x == 6 else "1", "lit": "true", "waterlogged": "false"})
    g.put(9, 3, 7, "barrel", {"facing": "up", "open": "false"})
    g.put(6, 3, 9, "brewing_stand", {"has_bottle_0": "false", "has_bottle_1": "false", "has_bottle_2": "false"})
    g.put(9, 3, 9, "lectern", {"facing": "west", "has_book": "false", "powered": "false"})
    for (x, z) in ((7, 7), (8, 7), (7, 8), (8, 8)):
        g.put(x, 3, z, "moss_carpet")
    g.put(9, 3, 8, "candle", {"candles": "3", "lit": "true", "waterlogged": "false"})
    # the loft: a chest of the hermit's, a lantern from the roof, a second shelf
    g.chest(9, 8, 8, "chests/hollow_oak", "west")
    g.put(8, 8, 6, "bookshelf"); g.put(9, 8, 6, "bookshelf")
    g.put(8, 10, 8, "lantern", {"hanging": "true", "waterlogged": "false"})
    g.put(ilo, 8, 9, "moss_carpet"); g.put(ilo + 1, 8, 9, "moss_carpet")
    # above the loft: the stub of the trunk, arms out and up, and the crown on them
    for y in range(12, 15):
        for x in range(ilo, ihi + 1):
            for z in range(ilo, ihi + 1):
                log(g, x, y, z, "oak_wood")
    for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        lat = 8 if dx + dz > 0 else 7
        for k in range(1, 6):
            x = (9 if dx > 0 else ilo) + dx * k if dx else lat
            z = (9 if dz > 0 else ilo) + dz * k if dz else lat
            log(g, x, 12 + k // 2, z, "oak_wood", "x" if dx else "z")
    # the crown (leaves everywhere a log isn't), fuller to the north and west so the roof of the room is shaded
    blob(g, c, c, 12, 18, lambda y: {12: 6.5, 13: 7.5, 14: 7.5, 15: 7.0, 16: 6.0, 17: 4.5, 18: 3.0}[y], "oak_leaves")
    blob(g, c, c, 10, 11, lambda y: 5.0 if y == 11 else 4.0, "oak_leaves", skip=lambda x, y, z: lo <= x <= hi and lo <= z <= hi)
    blob(g, c, c, 19, 19, lambda y: 1.8, "oak_leaves")
    # undergrowth at the roots
    for (x, z) in ((3, 4), (13, 12), (12, 3), (4, 12), (2, 9)):
        if g.get(x, 3, z) is None:
            g.put(x, 3, z, "fern")
    return g


_api = None  # the small_structures module, set by sites()


# ---- the bandit lookout in a great spruce ---------------------------------------------------------------------------

def spruce_lookout():
    """A great spruce, a 2x2 trunk twenty-two up, tiers of needles, and a plank platform fourteen up with a rail and a
    ladder: a chest, a barrel, two lanterns. Layers: 0-1 the foundation, 2 the ground, 16 the platform, 17 the rail."""
    g = _api.Grid(13, 29, 13)
    tx = (6, 7)
    for y in range(0, 25):
        for x in tx:
            for z in tx:
                log(g, x, y, z, "spruce_log")
    # roots, laid out from the trunk
    for (x, z, axis) in ((5, 6, "x"), (5, 7, "x"), (8, 6, "x"), (8, 7, "x"), (6, 5, "z"), (7, 5, "z"), (7, 8, "z")):
        g.put(x, 2, z, "spruce_log", {"axis": axis})
    g.put(4, 2, 6, "spruce_log", {"axis": "x"}); g.put(9, 2, 7, "spruce_log", {"axis": "x"})
    # the platform: seven by seven round the trunk, a hole for the ladder
    for x in range(3, 10):
        for z in range(3, 10):
            if (x, z) not in {(6, 6), (7, 6), (6, 7), (7, 7), (6, 8)}:
                g.put(x, 16, z, "spruce_planks")
    # the rail: the platform's edge, but for a gap at the ladder's foot... the ladder is inside, so the rail runs whole
    ring = {(x, z) for x in range(3, 10) for z in range(3, 10) if x in (3, 9) or z in (3, 9)}
    fences(g, 17, ring, "spruce_fence")
    # the ladder up the trunk's south face (to the platform and one over it)
    for y in range(3, 18):
        g.put(6, y, 8, "ladder", {"facing": "south", "waterlogged": "false"})
    # two lantern posts at the north corners
    for (x, z) in ((3, 3), (9, 3)):
        g.put(x, 18, z, "spruce_fence", {"north": "false", "south": "false", "east": "false", "west": "false", "waterlogged": "false"})
        g.put(x, 19, z, "lantern", {"hanging": "false", "waterlogged": "false"})
    # what the band keeps up here
    g.chest(8, 17, 6, "chests/illager_sites/bandits", "east")
    g.put(8, 17, 7, "barrel", {"facing": "up", "open": "false"})
    # the needles, in tiers: the open air about the platform is left (no tier from layer 17 to 19)
    tiers = [(6, 6, 4.5), (7, 8, 4.5), (9, 9, 5.5), (10, 11, 5.5), (12, 13, 5.0), (14, 15, 5.0),
             (20, 21, 5.5), (22, 23, 4.5), (24, 25, 3.5), (26, 27, 2.5), (28, 28, 1.5)]
    for (y0, y1, r) in tiers:
        for y in range(y0, y1 + 1):
            for x in range(13):
                for z in range(13):
                    d = math.hypot(x - 6.5, z - 6.5)
                    if d <= r and not (5 <= x <= 8 and z >= 8 and y <= 17):
                        leaf(g, x, y, z, "spruce_leaves")
    return g


# ---- the hanging tree -----------------------------------------------------------------------------------------------

def hanging_tree():
    """A broad old oak with one long bare bough over the road side: an iron chain hangs from its end with the hanged's
    bones, and the gallows post beside has the Sun's hanging list on its sign. Layers: 0-1 the foundation, 2 the ground."""
    g = _api.Grid(17, 17, 17)
    for y in range(0, 11):
        for x in (8, 9):
            for z in (8, 9):
                log(g, x, y, z, "oak_log")
    for (x, z, axis) in ((7, 8, "x"), (7, 9, "x"), (10, 9, "x"), (8, 7, "z"), (9, 7, "z"), (8, 10, "z"), (9, 10, "z")):
        g.put(x, 2, z, "oak_log", {"axis": axis})
    # the bough: bare, long, to the east and a little up at its end
    for x in range(10, 16):
        log(g, x, 9, 8, "oak_log", "x")
    log(g, 15, 10, 8, "oak_log", "y")
    log(g, 12, 10, 8, "oak_log", "y")
    # the chain, and the hanged
    for y in (8, 7, 6):
        g.put(14, y, 8, "iron_chain", {"axis": "y", "waterlogged": "false"})
    g.put(14, 5, 8, "skeleton_skull", {"rotation": "4", "powered": "false"})
    g.put(14, 4, 8, "bone_block", {"axis": "y"})
    g.put(14, 2, 8, "coarse_dirt")
    g.put(13, 2, 9, "coarse_dirt")
    g.put(15, 3, 9, "dead_bush")
    # the gallows post beside, with the list nailed to it
    for y in (3, 4):
        g.put(12, y, 4, "oak_fence", {"north": "false", "south": "false", "east": "false", "west": "false", "waterlogged": "false"})
    g.put(12, 5, 4, "oak_sign", {"rotation": "6", "waterlogged": "false"})
    g.put(12, 2, 4, "coarse_dirt")
    # the crown: wide to the west, north and south, kept off the bough so it hangs in the open
    rnd = random.Random(77)
    for y in range(8, 15):
        r = {8: 4.5, 9: 6.0, 10: 6.5, 11: 6.5, 12: 6.0, 13: 5.0, 14: 3.5}[y]
        for x in range(17):
            for z in range(17):
                d = math.hypot(x - 7.0, z - 8.5)
                if d <= r - (0.7 if rnd.random() < 0.3 else 0) and not (x >= 11 and y <= 12) and not (x >= 12 and y <= 13):
                    leaf(g, x, y, z, "oak_leaves")
    for y in (15, 16):
        for x in range(17):
            for z in range(17):
                if math.hypot(x - 7.0, z - 8.5) <= (2.5 if y == 15 else 1.5):
                    leaf(g, x, y, z, "oak_leaves")
    return g


def sites(api):
    global _api
    _api = api
    forest = ["#minecraft:is_forest"]
    return [
        api.Site("hollow_oak", {"hollow_oak": hollow_oak()}, ground=2, worldgen={
            "step": "surface_structures", "terrain_adaptation": "none", "biomes": forest,
            "spacing": 36, "separation": 14, "salt": 731905284,
        }),
        api.Site("spruce_lookout", {"spruce_lookout": spruce_lookout()}, ground=2, worldgen={
            "step": "surface_structures", "terrain_adaptation": "none", "biomes": ["#minecraft:is_taiga"],
            "spacing": 34, "separation": 12, "salt": 408217739,
        }),
        api.Site("hanging_tree", {"hanging_tree": hanging_tree()}, ground=2, worldgen={
            "step": "surface_structures", "terrain_adaptation": "none",
            "biomes": ["minecraft:plains", "minecraft:sunflower_plains", "minecraft:meadow", "minecraft:forest",
                       "minecraft:flower_forest", "minecraft:birch_forest", "minecraft:savanna"],
            "spacing": 32, "separation": 12, "salt": 956340127,
        }),
    ]
