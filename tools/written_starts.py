"""The written starts that ship with Kingdoms (WrittenStarts.java, docs/WrittenStarts.md): builds drawn here as block grids,
written as structure templates, the same files a structure block saves when Andy builds one by hand in creative.

- data/jaymoskingdoms/structure/written_starts/<name>.nbt (DataVersion and block names as tools/small_structures.py)

    python -I tools/written_starts.py [blockstates_jar]

With the 26.3 client jar (blockstates_jar, or the loom cache), every block and its properties are checked against the
game's own blockstate files. The start's JSON (written_starts/<name>.json) and its tale (tales/<tale>.json) are written by
hand.

Marks are structure blocks in Data mode, as an author places them in creative: `spawn[:<facing>]`, `place:<key>`,
`gate:<key>` (WrittenStarts.java has the rules).
"""
import importlib.util
import json
import os
import sys
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
_spec = importlib.util.spec_from_file_location("small_structures", os.path.join(HERE, "small_structures.py"))
ss = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(ss)
nbt, Tag = ss.nbt, ss.Tag

OUT = os.path.join(ss.DATA, "structure", "written_starts")
CLIENT_JAR = os.path.expanduser("~/.gradle/caches/fabric-loom/26.3/minecraft-client.jar")


def items(*stacks):
    """A container's Items: (slot, id, count)."""
    return Tag(nbt.LIST, (nbt.COMPOUND, [Tag(nbt.COMPOUND, {"Slot": Tag(nbt.BYTE, slot), "id": Tag(nbt.STRING, "minecraft:" + item),
                                                              "count": Tag(nbt.INT, count)}) for slot, item, count in stacks]))


def chest(g, x, y, z, facing, *stacks):
    g.put(x, y, z, "chest", {"facing": facing, "type": "single", "waterlogged": "false"},
          {"id": Tag(nbt.STRING, "minecraft:chest"), "Items": items(*stacks)})


def barrel(g, x, y, z, *stacks):
    g.put(x, y, z, "barrel", {"facing": "up", "open": "false"}, {"id": Tag(nbt.STRING, "minecraft:barrel"), "Items": items(*stacks)})


def mark(g, x, y, z, name):
    """A structure block in Data mode named `name`: what an author leaves in a build for WrittenStarts to read."""
    g.put(x, y, z, "structure_block", {"mode": "data"},
          {"id": Tag(nbt.STRING, "minecraft:structure_block"), "mode": Tag(nbt.STRING, "DATA"), "metadata": Tag(nbt.STRING, name),
           "name": Tag(nbt.STRING, ""), "author": Tag(nbt.STRING, "")})


# ---- the way-hut ------------------------------------------------------------------------------------------------------
# A Hearthkeeper's way-hut (Lore.md, "Way-huts"): one room of field stone under a plank roof, a hearth laid ready, a cot,
# the keeper's chest, and a store under the floor behind an iron trapdoor. Layers 0-2 are the cellar, under the ground;
# layer 3 is the ground's own top (the floor), so the start's JSON says "ground": 3. The door is on the south side (z=8).

W, D, H, GROUND = 9, 9, 10, 3


def way_hut():
    g = ss.Grid(W, H, D)
    # everything over the floor is the hut's own: trees and plants where it stands are dug out
    for x in range(W):
        for z in range(D):
            for y in range(GROUND + 1, H):
                g.clear(x, y, z)
    # the cellar: stone bricks round a two by two store, under the floor
    for x in range(1, 5):
        for z in range(1, 5):
            for y in range(0, GROUND):
                wall = x in (1, 4) or z in (1, 4) or y == 0
                if wall:
                    g.put(x, y, z, "stone_bricks")
                else:
                    g.clear(x, y, z)
    for y in (1, 2):
        g.put(2, y, 2, "ladder", {"facing": "south", "waterlogged": "false"})
    g.put(3, 2, 2, "lantern", {"hanging": "true", "waterlogged": "false"})
    # what the keeper laid in below: tools to work with and food that keeps
    chest(g, 3, 1, 3, "north", (0, "stone_axe", 1), (1, "stone_pickaxe", 1), (2, "stone_shovel", 1), (4, "torch", 8), (6, "leather_boots", 1))
    barrel(g, 2, 1, 3, (0, "potato", 6), (1, "coal", 4), (2, "dried_kelp", 6))
    mark(g, 3, 1, 2, "place:cellar")
    # the floor: field stone under the walls, spruce boards inside, the trapdoor over the ladder
    for x in range(W):
        for z in range(D):
            ring = x in (0, W - 1) or z in (0, D - 1)
            g.put(x, GROUND, z, "cobblestone" if ring else "spruce_planks")
    g.put(2, GROUND, 2, "iron_trapdoor", {"facing": "south", "half": "top", "open": "false", "powered": "false", "waterlogged": "false"})
    # the walls, three high, spruce posts at the corners
    for y in range(GROUND + 1, GROUND + 4):
        for x in range(W):
            for z in range(D):
                if x in (0, W - 1) or z in (0, D - 1):
                    corner = x in (0, W - 1) and z in (0, D - 1)
                    if corner:
                        g.put(x, y, z, "spruce_log", {"axis": "y"})
                    else:
                        g.put(x, y, z, "mossy_cobblestone" if (x * 7 + y * 3 + z * 5) % 5 == 0 else "cobblestone")
    # the door, south
    for half, dy in (("lower", 1), ("upper", 2)):
        g.put(4, GROUND + dy, D - 1, "spruce_door", {"facing": "north", "half": half, "hinge": "left", "open": "false", "powered": "false"})
    # windows, east and west
    for x in (0, W - 1):
        g.put(x, GROUND + 2, 4, "glass_pane", {"north": "true", "south": "true", "east": "false", "west": "false", "waterlogged": "false"})
    # the roof: spruce boards, a smoke hole over the hearth with a stone collar round it
    for x in range(W):
        for z in range(D):
            g.put(x, GROUND + 4, z, "spruce_planks")
    g.clear(4, GROUND + 4, 1)
    for x in range(3, 6):
        for z in range(0, 3):
            if (x, z) != (4, 1):
                g.put(x, GROUND + 5, z, "cobblestone")
    # the hearth, laid and not lit, on the north wall; the keeper's chest beside it
    g.put(4, GROUND + 1, 1, "campfire", {"facing": "south", "lit": "false", "signal_fire": "false", "waterlogged": "false"})
    chest(g, 5, GROUND + 1, 1, "south", (0, "bread", 4), (1, "apple", 2), (2, "flint_and_steel", 1), (4, "stick", 6), (5, "string", 2))
    g.put(1, GROUND + 1, 1, "lantern", {"hanging": "false", "waterlogged": "false"})
    # the cot, east, and the board by the west wall with the keeper's slate on it
    g.put(6, GROUND + 1, 6, "white_bed", {"facing": "east", "occupied": "false", "part": "foot"})
    g.put(7, GROUND + 1, 6, "white_bed", {"facing": "east", "occupied": "false", "part": "head"})
    g.put(2, GROUND + 1, 5, "crafting_table")
    # the marks: wake by the cot looking at the hearth; the slate lies on the board; the gate is the trapdoor below
    mark(g, 6, GROUND + 1, 5, "spawn:north")
    mark(g, 2, GROUND + 2, 5, "place:table")
    mark(g, 4, GROUND + 1, 2, "place:hearth")
    mark(g, 2, GROUND + 1, 2, "gate:cellar")
    return g


BUILDS = {"way_hut": way_hut}


def check(g, jar):
    """Every block and property against the game's blockstate files; returns the problems."""
    bad = []
    with zipfile.ZipFile(jar) as z:
        for (x, y, zz), (name, props, _) in g.b.items():
            short = name.split(":")[1]
            if short == "air":
                continue
            try:
                states = json.loads(z.read(f"assets/minecraft/blockstates/{short}.json"))
            except KeyError:
                bad.append(f"{short} at {x},{y},{zz}: no such block")
                continue
            keys = set()
            for v in states.get("variants", {}):
                keys |= {kv.split("=")[0] for kv in v.split(",") if "=" in kv}
            for part in states.get("multipart", []):
                when = part.get("when", {})
                for w in when.get("OR", [when]) if isinstance(when, dict) else []:
                    keys |= set(w)
            if not keys:
                # (drawn by its block entity, as a chest is: its blockstate file names no properties to check against)
                continue
            unknown = set(props) - keys - {"waterlogged", "powered", "occupied", "signal_fire"}
            if unknown:
                bad.append(f"{short} at {x},{y},{zz}: unknown properties {sorted(unknown)}")
    return bad


def main():
    jar = sys.argv[1] if len(sys.argv) > 1 else CLIENT_JAR
    for name, draw in BUILDS.items():
        g = draw()
        if os.path.exists(jar):
            problems = check(g, jar)
            if problems:
                print("\n".join(problems))
                sys.exit(1)
        else:
            print("no 26.3 client jar: blocks not checked")
        path = os.path.join(OUT, name + ".nbt")
        nbt.save(path, ss.template(g))
        print(f"{name}: {len(g.b)} blocks -> {os.path.relpath(path, ss.ROOT)}")


if __name__ == "__main__":
    main()
