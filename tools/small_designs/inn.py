"""Wayside inns (row rc_inn): a two-storey timber inn with a stable and a notice board in its yard. Placed by our own code
(WaysideInns.java) halfway along a King's Road, so it has a template only (no worldgen).

Layer 0 is the ground's own top block (the footing: cobble under the walls, planks under the hall); the walkable
floor of the ground storey is the top of layer 0. The door is on the north side (z=0 is the front, toward the road);
WaysideInns turns the template so that side faces the road. The whole box above layer 0 is air, so the template digs out
trees and plants where it stands.

The spots Java needs (WaysideInns.java has the same numbers; this prints them when the design is drawn):
    SIGN (7,2,2): a standing sign on the fence post at (7,1,2), text set by Java
    BOARD (12,1,2): the bounty board's bottom middle block, facing north
    KEEPER (6,1,8): where the innkeeper stands, behind the counter
    PATH x=4, from z=0 outward: Java lays a dirt path from the yard to the road
"""

W, D, H = 14, 16, 13
# the inn's box
X0, X1, Z0, Z1 = 0, 8, 4, 13
DOOR_X = 4
SIGN, BOARD, KEEPER = (7, 2, 2), (12, 1, 2), (6, 1, 8)

PLANK, WALL, LOG, BEAM = "spruce_planks", "oak_planks", "spruce_log", "stripped_spruce_log"


def stairs(g, x, y, z, facing, half="bottom", name="spruce_stairs"):
    g.put(x, y, z, name, {"facing": facing, "half": half, "shape": "straight", "waterlogged": "false"})


def slab(g, x, y, z, typ="bottom", name="spruce_slab"):
    g.put(x, y, z, name, {"type": typ, "waterlogged": "false"})


def fence(g, x, y, z):
    g.put(x, y, z, "spruce_fence", {"north": "false", "south": "false", "east": "false", "west": "false", "waterlogged": "false"})


def gate(g, x, y, z, facing):
    g.put(x, y, z, "spruce_fence_gate", {"facing": facing, "in_wall": "false", "open": "false", "powered": "false"})


def log(g, x, y, z, axis="y", name=LOG):
    g.put(x, y, z, name, {"axis": axis})


def pane(g, x, y, z, along_x):
    """A glass pane in a wall running along x (along_x) or along z."""
    g.put(x, y, z, "glass_pane", {"north": "false" if along_x else "true", "south": "false" if along_x else "true",
                                  "east": "true" if along_x else "false", "west": "true" if along_x else "false", "waterlogged": "false"})


def door(g, x, y, z, facing):
    for half, dy in (("lower", 0), ("upper", 1)):
        g.put(x, y + dy, z, "spruce_door", {"facing": facing, "half": half, "hinge": "left", "open": "false", "powered": "false"})


def lantern(g, x, y, z, hanging=False):
    g.put(x, y, z, "lantern", {"hanging": "true" if hanging else "false", "waterlogged": "false"})


def bed(g, x, y, z, colour, facing="east"):
    """The foot at (x, z); the head is one block on toward `facing`."""
    dx, dz = {"east": (1, 0), "west": (-1, 0), "south": (0, 1), "north": (0, -1)}[facing]
    g.put(x, y, z, colour + "_bed", {"facing": facing, "occupied": "false", "part": "foot"})
    g.put(x + dx, y, z + dz, colour + "_bed", {"facing": facing, "occupied": "false", "part": "head"})


def table(g, x, z):
    fence(g, x, 1, z)
    g.put(x, 2, z, "spruce_pressure_plate", {"powered": "false"})


def stool(g, x, z):
    slab(g, x, 1, z)


def roof_y(x):
    return 7 + min(x, X1 - x)


def inn(api):
    g = api.Grid(W, H, D)
    # everything above the footing is air first, so trees and plants where it stands are dug out
    for y in range(1, H):
        for x in range(W):
            for z in range(D):
                g.clear(x, y, z)

    # ---- the footing and the floor of the hall
    for x in range(X0, X1 + 1):
        for z in range(Z0, Z1 + 1):
            edge = x in (X0, X1) or z in (Z0, Z1)
            g.put(x, 0, z, "cobblestone" if edge else PLANK)

    # ---- the walls, both storeys: oak between spruce posts, a spruce beam at the floor line (layer 4)
    posts_x = {X0, X1}
    posts_z = {Z0, Z1}
    for y in range(1, 7):
        for x in range(X0, X1 + 1):
            for z in range(Z0, Z1 + 1):
                if x not in (X0, X1) and z not in (Z0, Z1):
                    continue
                if x in posts_x and z in posts_z:
                    log(g, x, y, z)
                elif y == 4:
                    log(g, x, y, z, "x" if z in (Z0, Z1) else "z", BEAM)
                elif (x in posts_x and z in (7, 10)) or (z in posts_z and x in (2, 6)):
                    log(g, x, y, z)
                else:
                    g.put(x, y, z, WALL)

    # windows: ground storey (layers 2-3) and upper storey (layers 5-6)
    for y in (2, 3, 5, 6):
        for z in (6, 9, 12):
            pane(g, X0, y, z, False)
            pane(g, X1, y, z, False)
        for x in (1, 7):
            if y < 4:
                pane(g, x, y, Z0, True)
                pane(g, x, y, Z1, True)
        if y > 4:
            for x in (3, 5):
                pane(g, x, y, Z0, True)
            for x in (1, 7):
                pane(g, x, y, Z1, True)

    # the door, with a porch roof, a lantern and two posts
    door(g, DOOR_X, 1, Z0, "south")
    for x in (3, 5):
        fence(g, x, 1, 2)
        fence(g, x, 2, 2)
    for x in (3, 4, 5):
        for z in (2, 3):
            slab(g, x, 3, z)
    lantern(g, DOOR_X, 2, 3, hanging=True)

    # ---- the upper floor (layer 4) and the stairs up, along the west wall
    for x in range(X0 + 1, X1):
        for z in range(Z0 + 1, Z1):
            g.put(x, 4, z, PLANK)
    for z in (7, 8, 9):
        g.clear(1, 4, z)
    for i, z in enumerate((6, 7, 8, 9)):
        y = 1 + i
        stairs(g, 1, y, z, "south")
        for under in range(1, y):
            g.put(1, under, z, PLANK)

    # a ceiling over the upper rooms (the roof's attic above is shut)
    for x in range(X0 + 1, X1):
        for z in range(Z0 + 1, Z1):
            g.put(x, 7, z, PLANK)

    # ---- the hearth: an opening in the back wall into a cobble stack outside, with a campfire at the foot
    g.clear(DOOR_X, 1, Z1)
    g.put(DOOR_X, 2, Z1, "cobblestone")
    g.put(DOOR_X, 3, Z1, "cobblestone")
    for x in (3, 5):
        for y in (1, 2, 3):
            g.put(x, y, Z1, "cobblestone")
    for y in range(0, 13):
        for x in (3, 4, 5):
            for z in (14, 15):
                if x == DOOR_X and z == 14 and y > 0:
                    continue
                g.put(x, y, z, "cobblestone")
    g.put(DOOR_X, 1, 14, "campfire", {"facing": "south", "lit": "true", "signal_fire": "false", "waterlogged": "false"})

    # ---- ground storey: the counter, with barrels behind it, tables and stools
    for z in range(6, 10):
        g.put(5, 1, z, PLANK)
    lantern(g, 5, 2, 6)
    lantern(g, 5, 2, 9)
    for z in range(6, 11):
        g.put(7, 1, z, "barrel", {"facing": "up", "open": "false"})
    for z in (7, 9):
        g.put(7, 2, z, "barrel", {"facing": "up", "open": "false"})
    table(g, 3, 7)
    for p in ((3, 6), (2, 7), (3, 8)):
        stool(g, *p)
    table(g, 3, 11)
    for p in ((3, 10), (2, 11), (3, 12)):
        stool(g, *p)
    table(g, 6, 12)
    for p in ((5, 12), (6, 11), (7, 12)):
        stool(g, *p)
    for p in ((3, 3, 9), (6, 3, 8), (2, 3, 12)):
        lantern(g, *p, hanging=True)

    # ---- upper storey: a corridor (x 1-2) and three small rooms behind a partition (x 3), each with a bed
    for z in range(Z0 + 1, Z1):
        if z not in (6, 9, 12):
            for y in (5, 6):
                g.put(3, y, z, WALL)
    for z in (6, 9, 12):
        door(g, 3, 5, z, "east")
    for z in (7, 10):
        for x in range(4, X1):
            for y in (5, 6):
                g.put(x, y, z, WALL)
    bed(g, 6, 5, 5, "red")
    bed(g, 6, 5, 8, "blue")
    bed(g, 6, 5, 11, "white")
    for z in (6, 9, 12):
        lantern(g, 7, 5, z)
    for z in (5, 8, 11):
        lantern(g, 5, 6, z, hanging=True)
    lantern(g, 2, 6, 5, hanging=True)
    lantern(g, 2, 6, 11, hanging=True)

    # ---- the roof: spruce stairs rising to a planked ridge along x=4, the gable ends filled to it
    for z in range(Z0, Z1 + 1):
        for x in range(X0, X1 + 1):
            if x == 4:
                g.put(x, 11, z, PLANK)
            else:
                stairs(g, x, roof_y(x), z, "east" if x < 4 else "west")
    for z in (Z0, Z1):
        for x in range(X0, X1 + 1):
            for y in range(7, roof_y(x)):
                g.put(x, y, z, WALL)
    for z in (Z0, Z1):
        pane(g, 4, 8, z, True)
        pane(g, 4, 9, z, True)

    # ---- the stable, a lean-to on the inn's east wall: three stalls of fences and gates, hay and a water trough each
    for x in range(9, 14):
        for z in range(Z0, Z1 + 1):
            g.put(x, 0, z, "coarse_dirt")
    for z in (4, 7, 10, 13):
        for y in (1, 2, 3):
            log(g, 13, y, z)
    for z in (5, 6, 8, 9, 11, 12):
        gate(g, 13, 1, z, "east")
    for z in (7, 10):
        for x in range(9, 13):
            fence(g, x, 1, z)
    for z in (4, 13):
        for x in range(9, 13):
            fence(g, x, 1, z)
        for y in range(2, 5):
            fence(g, 11, y, z)
        for y in range(2, 6):
            fence(g, 9, y, z)
    for z0 in (5, 8, 11):
        g.put(9, 1, z0, "hay_block", {"axis": "y"})
        g.put(10, 1, z0, "hay_block", {"axis": "y"})
        g.put(9, 1, z0 + 1, "water_cauldron", {"level": "3"})
    for z in range(Z0, Z1 + 1):
        slab(g, 13, 4, z)
        stairs(g, 12, 4, z, "west")
        slab(g, 11, 5, z)
        stairs(g, 10, 5, z, "west")
        slab(g, 9, 6, z)
    for z in (7, 10):
        lantern(g, 12, 3, z, hanging=True)

    # ---- the yard: a sign post and a lamp post by the door, a path to the road, the board's clear 3x2 face
    fence(g, SIGN[0], 1, SIGN[2])
    fence(g, 1, 1, 2)
    fence(g, 1, 2, 2)
    lantern(g, 1, 3, 2)
    for z in range(0, 4):
        g.put(DOOR_X, 0, z, "dirt_path")
    # firm ground under the yard's posts and the board, so Java's fill-under reaches them on a slope
    for x in (1, 3, 5, SIGN[0], BOARD[0] - 1, BOARD[0], BOARD[0] + 1):
        g.put(x, 0, 2, "coarse_dirt")
    return g


# The tool's name check reads `public static final Block X =` lines from Blocks.java; the coloured beds are made by
# Blocks.BED (a ColorCollection), so they are named here to pass it (26.3: ColorCollection.registerBlocks(BlockItemIds.BED)).
BEDS = {c + "_bed" for c in ("white", "red", "blue")}


def sites(api):
    plain = api.write_site
    api.write_site = lambda s, known, previews: plain(s, known | BEDS if known else known, previews)
    print("  wayside inn spots: sign", SIGN, "board", BOARD, "keeper", KEEPER, "size", (W, H, D))
    return [api.Site("wayside_inn", {"wayside_inn": inn(api)}, ground=0, worldgen=None)]
