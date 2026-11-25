"""Cave gardens (row rc_flora): tended caves under old wood. Java: CaveGardens.java.

A chamber is carved under the turf (placed air, a dome with a flat floor), its floor moss and rooted dirt with a rooted-dirt
path, a pool with big dripleaf, cave vines hanging berried from the roof, spore blossoms, lanterns and lit candles, a
composter. The way in is a 3x3 mossy cobblestone shaft at the template's middle column, a ladder on its north wall, open at
the ground. The rock round the chamber is left unlisted (natural). The large garden has the old druid's nook (bed, barrel,
lectern, bookshelf, candles); Java finds the druid's place by the lectern, since the jigsaw may turn the piece.

All three variants share one GROUND layer (a site has one start height), so each sinks its chamber by its own depth: the
layers below a small garden's floor are simply unlisted.
"""
import math
import random

GROUND = 27          # layer of the surface's top block (the shaft's open top and its lip)
LAYERS = GROUND + 2  # two layers over the turf, empty
SIDES = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}
LICHEN = {k: "false" for k in ("down", "east", "north", "south", "up", "west", "waterlogged")}
CANDLE = {"lit": "true", "waterlogged": "false"}


def garden(api, seed, w, depth, ry, nook=False):
    """One garden: w across (odd), floor `depth` below the ground, dome `ry` high, the druid's nook if `nook`."""
    rnd = random.Random(seed)
    g = api.Grid(w, LAYERS, w)
    c = (w - 1) // 2
    F = GROUND - depth
    rx = w / 2 - 0.7
    wobble = {}

    def inside(x, y, z):
        if y <= F:
            return False
        if (x, y, z) not in wobble:
            wobble[(x, y, z)] = rnd.random() * 0.12
        return ((x - c) / rx) ** 2 + ((z - c) / rx) ** 2 + ((y - F) / ry) ** 2 <= 1 - wobble[(x, y, z)]

    cells = {(x, z) for x in range(w) for z in range(w) if inside(x, F + 1, z)}
    top = {}
    for (x, z) in sorted(cells):
        for y in range(F + 1, F + ry + 1):
            if inside(x, y, z):
                g.clear(x, y, z)
                top[(x, z)] = y
    ring = {(c + dx, c + dz) for dx in (-1, 0, 1) for dz in (-1, 0, 1)}
    reserved = set(ring)

    def free(x, z):
        return (x, z) in cells and (x, z) not in reserved

    def spot(px, pz):
        """The free floor cell nearest (px, pz)."""
        return min((p for p in sorted(cells) if free(*p)), key=lambda p: (p[0] - px) ** 2 + (p[1] - pz) ** 2)

    # the floor
    for (x, z) in sorted(cells):
        g.put(x, F, z, "moss_block" if rnd.random() < 0.75 else "rooted_dirt")

    # the way in: sleeve, a wall for the ladder inside the chamber, the ladder, the lip at the turf
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            x, z = c + dx, c + dz
            for y in range(F + 1, GROUND + 1):
                if (dx, dz) == (0, 0):
                    g.put(x, y, z, "ladder", {"facing": "south", "waterlogged": "false"})
                elif y == GROUND or dz == -1 or not inside(x, y, z):
                    g.put(x, y, z, "mossy_cobblestone")

    # the pool, away from the chamber's edge, with a bed of stone under it
    pr = 1.5 + (w - 11) * 0.12
    px, pz = c - round(rx * 0.45), c + round(rx * 0.4)
    pool = {(x, z) for (x, z) in cells if math.hypot(x - px, z - pz) <= pr
            and all((x + a, z + b) in cells for a, b in SIDES.values())}
    for (x, z) in sorted(pool):
        g.put(x, F, z, "water", {"level": "0"})
        g.put(x, F - 1, z, "stone")
    reserved |= pool
    rim = sorted({(x + a, z + b) for (x, z) in pool for a, b in SIDES.values()} - pool)
    rim = [p for p in rim if free(*p)]
    rnd.shuffle(rim)
    for (x, z) in rim[:2 + (w > 11)]:
        g.put(x, F + 1, z, "big_dripleaf_stem", {"facing": "north", "waterlogged": "false"})
        g.put(x, F + 2, z, "big_dripleaf", {"facing": "north", "tilt": "none", "waterlogged": "false"})
        reserved.add((x, z))

    # the nook (large): bed against the east wall, barrel, lectern, bookshelf, composter; or just a composter
    stand = None
    if nook:
        bx, bz = c + 7, c - 3
        g.put(bx, F + 1, bz, "straw_bed", {"facing": "east", "part": "foot", "occupied": "false"})
        g.put(bx + 1, F + 1, bz, "straw_bed", {"facing": "east", "part": "head", "occupied": "false"})
        g.barrel(c + 8, F + 1, c, "chests/cave_garden")
        g.put(c + 8, F + 2, c, "candle", dict(CANDLE, candles="2"))
        g.put(c + 7, F + 1, c + 1, "lectern", {"facing": "west", "has_book": "false", "powered": "false"})
        g.put(c + 8, F + 1, c + 2, "bookshelf")
        g.put(c + 8, F + 2, c + 2, "candle", dict(CANDLE, candles="1"))
        g.put(c + 7, F + 1, c + 3, "composter", {"level": "4"})
        g.put(c + 6, F + 1, c - 3, "lantern", {"hanging": "false", "waterlogged": "false"})
        stand = (c + 6, c + 1)
        reserved |= {(bx, bz), (bx + 1, bz), (c + 8, c), (c + 7, c + 1), (c + 8, c + 2), (c + 7, c + 3), (c + 6, c - 3), stand}
    else:
        kx, kz = spot(c + 3, c + 2)
        g.put(kx, F + 1, kz, "composter", {"level": "3"})
        reserved.add((kx, kz))

    # the path of rooted dirt from the foot of the ladder to the pool, and to the nook
    def path(tx, tz):
        x, z = c, c + 1
        for _ in range(60):
            if (x, z) in cells and (x, z) not in pool and ((x, z) not in reserved or (x, z) in ring or (x, z) == (tx, tz)):
                g.put(x, F, z, "rooted_dirt")
                reserved.add((x, z))
            if (x, z) == (tx, tz):
                break
            if abs(tx - x) >= abs(tz - z):
                x += 1 if tx > x else -1
            else:
                z += 1 if tz > z else -1
    if rim:
        path(*rim[-1])
    if stand:
        path(*stand)

    # the roof: an anchor over each hanging thing so it has something to hang from
    columns = [p for p in sorted(cells) if free(*p) and top[p] - (F + 1) >= 2]
    rnd.shuffle(columns)

    def anchor(p):
        g.put(p[0], top[p] + 1, p[1], "moss_block")

    n_vines, n_lamps, n_blossoms = {11: (3, 2, 1), 15: (5, 3, 2), 21: (9, 5, 3)}[w]
    for p in columns[:n_vines]:
        tip = max(top[p] - rnd.randint(2, 4), F + 2)
        anchor(p)
        for y in range(tip + 1, top[p] + 1):
            g.put(p[0], y, p[1], "cave_vines_plant", {"berries": "true"})
        g.put(p[0], tip, p[1], "cave_vines", {"age": "25", "berries": "true"})
        reserved.add(p)
    rest = [p for p in columns[n_vines:] if free(*p)]
    for p in rest[:n_lamps]:
        anchor(p)
        g.put(p[0], top[p], p[1], "iron_chain", {"axis": "y", "waterlogged": "false"})
        g.put(p[0], top[p] - 1, p[1], "lantern", {"hanging": "true", "waterlogged": "false"})
        reserved.add(p)
    rest = rest[n_lamps:]
    for p in rest[:n_blossoms]:
        anchor(p)
        g.put(p[0], top[p], p[1], "spore_blossom")
        reserved.add(p)
    for p in rest[n_blossoms:n_blossoms + 4 * (w // 7)]:
        anchor(p)
        g.put(p[0], top[p], p[1], "glow_lichen", dict(LICHEN, up="true"))

    # lit candles and a standing lantern on the floor, then the undergrowth
    spots = [p for p in sorted(cells) if free(*p)]
    rnd.shuffle(spots)
    for p in spots[:w // 3]:
        g.put(p[0], F + 1, p[1], "candle", dict(CANDLE, candles=str(rnd.randint(1, 3))))
        reserved.add(p)
    for p in spots[w // 3:w // 3 + 1 + w // 10]:
        g.put(p[0], F + 1, p[1], "lantern", {"hanging": "false", "waterlogged": "false"})
        reserved.add(p)
    for p in sorted(cells):
        if p in reserved:
            continue
        r = rnd.random()
        for edge, block in ((0.36, "moss_carpet"), (0.41, "azalea"), (0.46, "flowering_azalea"), (0.54, "fern"),
                            (0.57, "brown_mushroom"), (0.60, "red_mushroom")):
            if r < edge:
                g.put(p[0], F + 1, p[1], block)
                break
    return g


def sites(api):
    return [api.Site("cave_garden", {
        "cave_garden_small": garden(api, 101, 11, 16, 4),
        "cave_garden_medium": garden(api, 202, 15, 20, 5),
        "cave_garden_large": garden(api, 303, 21, 24, 7, nook=True),
    }, ground=GROUND, weights={"cave_garden_small": 6, "cave_garden_medium": 3, "cave_garden_large": 1}, worldgen={
        "step": "underground_structures", "terrain_adaptation": "none",
        "biomes": ["#minecraft:is_forest", "#minecraft:is_taiga", "#minecraft:is_jungle", "minecraft:dark_forest",
                   "minecraft:lush_caves"],
        "spacing": 48, "separation": 16, "salt": 902617355,
    })]
