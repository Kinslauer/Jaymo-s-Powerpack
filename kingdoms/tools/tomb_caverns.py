"""
Tiv: "some of the new generated dungeon is filled with stone". The Stormborn Tomb's halls came from DungeonZ's dungeon
dimension, whose chunk generator leaves every chunk empty. Its builder saved many rooms and caverns as structure void
(no block at all) instead of air, which was the same thing there: nothing. In the overworld a void cell keeps whatever
the ground had, so those rooms came out packed with stone, ores and all (right_hallway's hall, right_split's cavern,
split's chasm, parts of the boss arena).

This writes air into those cells of the dungeon pieces' templates
(kingdoms/src/main/resources/data/jaymoskingdoms/structure/stormborn_tomb/*.nbt). It lays every piece out as
StormbornTomb.LAYOUT does (read from the Java file, so they can't drift apart), and a void cell is a room's when the
tomb's blocks stand round it on all four sides along its level and over or under it, and it joins up with the tomb's
own air. Void outside the tomb's walls stays void, so the ground still closes round it.

Only the pieces from the dungeon dimension are carved. The barrow (overworld, overworld_up) and parkour were built for
the overworld, where their voids were meant to be ground, and the descent is our own shaft, made to fit.

Needs numpy. A rerun changes nothing (no room voids are left). Run from the project root:
python3 -I kingdoms/tools/tomb_caverns.py
"""
import os
import re
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from tomb_rubble import COMPOUND, INT, LIST, ROOT, STRING, TOMB, Tag, load, save  # noqa: E402

JAVA = os.path.join(ROOT, "kingdoms", "src", "main", "java", "com", "jaymoskingdoms", "dungeon", "StormbornTomb.java")
# The pieces from DungeonZ's empty dimension; the others are left as they are.
DUNGEON = {"spawn", "first_hallway", "right_hallway", "split", "right_split", "main_right", "main", "boss"}
# Pieces whose whole box is never carved: the descent cuts through the spawn hall and the barrow, and its shaft is
# meant to run through the ground.
KEEP_BOX = {"descent"}
VOID, AIR, SOLID = 0, 1, 2
AIRS = {"minecraft:air", "minecraft:cave_air"}


def layout():
    """StormbornTomb.LAYOUT: (name, quarter turns, x, y, z) for every piece, in placing order."""
    text = open(JAVA, encoding="utf-8").read()
    parts = re.findall(r'new Part\("(\w+)", (-?\d+), (-?\d+), (-?\d+), (-?\d+)\)', text)
    if not parts:
        raise SystemExit("no LAYOUT parts found in StormbornTomb.java")
    return [(name, int(t), int(x), int(y), int(z)) for name, t, x, y, z in parts]


def grid(root):
    """The template as a VOID/AIR/SOLID grid indexed [x, y, z]."""
    v = root.value
    sx, sy, sz = (e.value for e in v["size"].value)
    names = [p.value["Name"].value for p in v["palette"].value]
    g = np.zeros((sx, sy, sz), dtype=np.int8)
    for b in v["blocks"].value:
        x, y, z = (e.value for e in b.value["pos"].value)
        g[x, y, z] = AIR if names[b.value["state"].value] in AIRS else SOLID
    return g


def turned(g, turns):
    """The grid as placed with this many clockwise quarter turns (x east, z south), and its origin's shift."""
    turns &= 3
    if turns == 0:
        return g, (0, 0)
    if turns == 2:
        return g[::-1, :, ::-1], (-(g.shape[0] - 1), -(g.shape[2] - 1))
    raise SystemExit("only 0 and 2 quarter turns are laid out here; add the others if LAYOUT needs them")


def solid_along(solid, axis, backwards):
    """Cells with a solid block at or before them, looking along axis (backwards: from the far end)."""
    a = np.flip(solid, axis) if backwards else solid
    seen = np.cumsum(a, axis=axis) > 0
    return np.flip(seen, axis) if backwards else seen


def neighbours(mask):
    """Cells next to (6 ways) a cell of mask."""
    out = np.zeros_like(mask)
    out[1:] |= mask[:-1]
    out[:-1] |= mask[1:]
    out[:, 1:] |= mask[:, :-1]
    out[:, :-1] |= mask[:, 1:]
    out[:, :, 1:] |= mask[:, :, :-1]
    out[:, :, :-1] |= mask[:, :, 1:]
    return out


def grow(seed, within):
    """Every cell of within joined (6 ways, through within) to a cell of seed."""
    reached = seed & within
    while True:
        more = neighbours(reached) & within & ~reached
        if not more.any():
            return reached
        reached |= more


def main():
    pieces = []
    for name, turns, x, y, z in layout():
        path = os.path.join(TOMB, name + ".nbt")
        tag_name, root = load(path)
        g, (dx, dz) = turned(grid(root), turns)
        pieces.append({"name": name, "turns": turns, "path": path, "tag": tag_name, "root": root, "g": g, "at": (x + dx, y, z + dz)})
    low = np.min([p["at"] for p in pieces], axis=0)
    high = np.max([np.add(p["at"], p["g"].shape) for p in pieces], axis=0)
    world = np.zeros(high - low, dtype=np.int8)
    owner = np.full(high - low, -1, dtype=np.int16)
    for i, p in enumerate(pieces):
        box = tuple(slice(a - l, a - l + n) for a, l, n in zip(p["at"], low, p["g"].shape))
        p["box"] = box
        placed = p["g"] > VOID
        world[box][placed] = p["g"][placed]
        if p["name"] in DUNGEON:
            owner[box] = i
    for p in pieces:
        if p["name"] in KEEP_BOX:
            owner[p["box"]] = -1

    solid = world == SOLID
    walled = (solid_along(solid, 0, False) & solid_along(solid, 0, True) & solid_along(solid, 2, False)
              & solid_along(solid, 2, True) & (solid_along(solid, 1, False) | solid_along(solid, 1, True)))
    rooms = grow(neighbours(world == AIR), (world == VOID) & walled & (owner >= 0))

    for i, p in enumerate(pieces):
        cells = np.argwhere(rooms & (owner == i))
        if not len(cells):
            print(f"{p['name']}: nothing to carve")
            continue
        g = p["g"]
        v = p["root"].value
        palette = v["palette"].value
        air = next((k for k, s in enumerate(palette) if s.value["Name"].value == "minecraft:air"), None)
        if air is None:
            air = len(palette)
            palette.append(Tag(COMPOUND, {"Name": Tag(STRING, "minecraft:air")}))
        sx, _, sz = g.shape
        for cell in cells:
            x, y, z = (int(c) for c in cell - [s.start for s in p["box"]])
            if p["turns"] & 3 == 2:  # back into the template's own frame
                x, z = sx - 1 - x, sz - 1 - z
            pos = Tag(LIST, [Tag(INT, x), Tag(INT, y), Tag(INT, z)], INT)
            v["blocks"].value.append(Tag(COMPOUND, {"pos": pos, "state": Tag(INT, air)}))
        save(p["path"], p["tag"], p["root"])
        print(f"{p['name']}: {len(cells)} void cells made air")


if __name__ == "__main__":
    main()
