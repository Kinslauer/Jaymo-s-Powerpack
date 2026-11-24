"""Mazes (row rc_maze): a hedge maze in a castle's garden (Java: HedgeMaze.java, placed by SeatCompounds) and a changing stone
maze under a great necromancer's crypt (Java: CryptMaze.java). Picture-only designs: the game lays each one out in code, so
this file draws one sample of each with the same maze algorithm as MazeGen.java (the same splitmix64 stream, the same
recursive backtracker, the same heart door), so the pictures show real layouts.
"""

MASK = (1 << 64) - 1
DIRS = [(0, -1), (1, 0), (0, 1), (-1, 0)]  # north, east, south, west (z grows southward)


class Rng:
    """splitmix64, as MazeGen.next in Java."""

    def __init__(self, seed):
        self.s = seed & MASK

    def next(self):
        self.s = (self.s + 0x9E3779B97F4A7C15) & MASK
        z = self.s
        z = ((z ^ (z >> 30)) * 0xBF58476D1CE4E5B9) & MASK
        z = ((z ^ (z >> 27)) * 0x94D049BB133111EB) & MASK
        return z ^ (z >> 31)


def layout(n, heart, sealed, seed, root):
    """open[x][z] over a (2n+1) block square: cells at odd blocks, a passage between two cells at the even block between
    them. A seeded recursive backtracker from cell `root` carves a perfect maze over every cell outside the rooms; `heart`
    (cx, cz, size in cells) is an open room reached by one door, set where the walk from the root is longest; `sealed`
    (or None) is a block of cells kept closed for the caller to fill."""
    size = 2 * n + 1
    rooms = {}
    for tag, r in (("heart", heart), ("sealed", sealed)):
        if r:
            for cx in range(r[0], r[0] + r[2]):
                for cz in range(r[1], r[1] + r[2]):
                    rooms[(cx, cz)] = tag
    opened = [[False] * size for _ in range(size)]
    rng = Rng(seed)
    visited = {root}
    depth = {root: 0}
    opened[2 * root[0] + 1][2 * root[1] + 1] = True
    stack = [root]
    while stack:
        cx, cz = stack[-1]
        cand = [(dx, dz) for dx, dz in DIRS
                if 0 <= cx + dx < n and 0 <= cz + dz < n and (cx + dx, cz + dz) not in rooms and (cx + dx, cz + dz) not in visited]
        if not cand:
            stack.pop()
            continue
        dx, dz = cand[rng.next() % len(cand)]
        nx, nz = cx + dx, cz + dz
        opened[2 * cx + 1 + dx][2 * cz + 1 + dz] = True
        opened[2 * nx + 1][2 * nz + 1] = True
        visited.add((nx, nz))
        depth[(nx, nz)] = depth[(cx, cz)] + 1
        stack.append((nx, nz))
    hx, hz, hs = heart
    for x in range(2 * hx + 1, 2 * (hx + hs - 1) + 2):
        for z in range(2 * hz + 1, 2 * (hz + hs - 1) + 2):
            opened[x][z] = True
    best = None
    for cx in range(n):
        for cz in range(n):
            if (cx, cz) not in depth:
                continue
            for dx, dz in DIRS:
                if rooms.get((cx + dx, cz + dz)) == "heart" and (best is None or depth[(cx, cz)] > best[0]):
                    best = (depth[(cx, cz)], cx, cz, dx, dz)
    _, cx, cz, dx, dz = best
    opened[2 * cx + 1 + dx][2 * cz + 1 + dz] = True
    return opened


LEAVES = {"persistent": "true", "distance": "1", "waterlogged": "false"}
LANTERN = {"hanging": "false", "waterlogged": "false"}
N = 9
HEART = (3, 3, 3)


def stair(f):
    return {"facing": f, "half": "bottom", "shape": "straight", "waterlogged": "false"}


def hedge_maze(api, seed):
    """A castle's hedge maze: 19 x 19, hedge walls 2 high (3 on the outside), gravel walks, a fountain at the heart."""
    size = 2 * N + 1
    g = api.Grid(size, 6, size)
    opened = layout(N, HEART, None, seed, (N // 2, 0))
    gate = 2 * (N // 2) + 1
    opened[gate][0] = True  # the way in, north
    for x in range(size):
        for z in range(size):
            edge = x in (0, size - 1) or z in (0, size - 1)
            if opened[x][z]:
                g.put(x, 0, z, "gravel")
            else:
                g.put(x, 0, z, "grass_block", {"snowy": "false"})
                for y in range(1, 4 if edge else 3):
                    g.put(x, y, z, "oak_leaves", LEAVES)
    for x in (gate - 1, gate + 1):
        g.put(x, 4, 0, "lantern", LANTERN)
    # the fountain in the heart room (5 x 5): a slab rim round water, a lamp on a pillar
    c = 2 * HEART[0] + 3
    for x in range(c - 2, c + 3):
        for z in range(c - 2, c + 3):
            rim = max(abs(x - c), abs(z - c)) == 2
            g.put(x, 0, z, "stone_bricks")
            if rim:
                g.put(x, 1, z, "stone_brick_slab", {"type": "bottom", "waterlogged": "false"})
            else:
                g.put(x, 1, z, "water", {"level": "0"})
    g.put(c, 1, c, "stone_bricks")
    g.put(c, 2, c, "stone_bricks")
    g.put(c, 3, c, "lantern", LANTERN)
    return g


def crypt_maze(api, seed):
    """The maze under a crypt's hall (layer 0 floor, 1-3 walls; the ceiling left off so it can be seen into): a spiral stair
    round a pillar in the north-west corner (blocks 1..3; it rises past the picture's top to the hall floor, layer 8), a
    heart room with its chest and soul lanterns."""
    size = 2 * N + 1
    g = api.Grid(size, 10, size)
    sealed = (0, 0, 2)
    opened = layout(N, HEART, sealed, seed, (2, 1))
    opened[4][3] = True  # the stair's door
    for x in range(size):
        for z in range(size):
            g.put(x, 0, z, "deepslate_bricks")
            if not opened[x][z]:
                for y in range(1, 4):
                    r = (x * 31 + z * 17 + y * 7) % 11
                    g.put(x, y, z, "mossy_stone_bricks" if r == 0 else "cracked_stone_bricks" if r < 3 else "deepslate_bricks" if r < 5 else "stone_bricks")
    # the spiral: ring cells (local 0..2), each step a block lower than the one before, solid under each, the throat
    # (local 2, 2) left open down to the floor, a pillar in the middle
    ring = [(2, 2, "east"), (1, 2, "east"), (0, 2, "east"), (0, 1, "south"), (0, 0, "south"), (1, 0, "west"), (2, 0, "west"), (2, 1, "north")]
    for k, (wx, wz, f) in enumerate(ring):
        step = 8 - k
        for y in range(1, 9):
            if y < step and not (wx == 2 and wz == 2):
                g.put(1 + wx, y, 1 + wz, "stone_bricks")
            elif y == step:
                g.put(1 + wx, y, 1 + wz, "stone_brick_stairs", stair(f))
            else:
                g.clear(1 + wx, y, 1 + wz)
    for y in range(1, 9):
        g.put(2, y, 2, "stone_bricks")
    # the heart: a dais, the chest, soul lanterns at the corners, candles
    c = 2 * HEART[0] + 3
    for x in range(c - 1, c + 2):
        for z in range(c - 1, c + 2):
            g.put(x, 0, z, "cracked_stone_bricks")
    g.chest(c, 1, c, "chests/crypt_maze", "south")
    for dx, dz in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
        g.put(c + dx, 1, c + dz, "soul_lantern", LANTERN)
    for dx, dz in ((-1, -2), (1, 2), (-2, 1), (2, -1)):
        g.put(c + dx, 1, c + dz, "candle", {"candles": "2", "lit": "false", "waterlogged": "false"})
    return g


def sites(api):
    return [api.Site("mazes", {
        "hedge_maze_sample": hedge_maze(api, 20261124),
        "crypt_maze_sample": crypt_maze(api, 77031),
    }, ground=0, worldgen=None, template=False)]
