"""Draw The Last Lantern, a second written-start example (docs/LastLantern.md).

    python -I tools/last_lantern.py <26.3 client jar> [preview directory]

Uses the same grid, NBT writer and block check as the way-hut. Previews show the
template's blocks, with author markers removed as WrittenStarts does in game.
"""
import importlib.util
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
_spec = importlib.util.spec_from_file_location("written_starts", os.path.join(HERE, "written_starts.py"))
ws = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(ws)
ss = ws.ss

GROUND = 4


def last_lantern():
    g = ss.Grid(13, 12, 14)
    # Clear the full footprint above ground, including the covered porch.
    for x in range(g.w):
        for z in range(g.d):
            for y in range(GROUND + 1, g.h):
                g.clear(x, y, z)
    # A three-block-high store beneath the northwest half of the hut.
    for x in range(2, 9):
        for z in range(2, 8):
            for y in range(GROUND):
                if x in (2, 8) or z in (2, 7) or y == 0:
                    g.put(x, y, z, "stone_bricks")
                else:
                    g.clear(x, y, z)
    for y in range(1, GROUND):
        g.put(3, y, 3, "ladder", {"facing": "south", "waterlogged": "false"})
    ws.chest(g, 7, 1, 5, "west", (0, "stone_axe", 1), (2, "stone_pickaxe", 1),
             (4, "stone_shovel", 1), (9, "torch", 12), (11, "leather_boots", 1))
    ws.barrel(g, 7, 1, 3, (0, "potato", 8), (3, "coal", 6), (6, "dried_kelp", 8))
    g.put(6, 3, 5, "lantern", {"hanging": "true", "waterlogged": "false"})
    ws.mark(g, 6, 1, 4, "place:store")
    # The fieldstone footing and plank floor. The porch has no cellar under it.
    for x in range(1, 12):
        for z in range(1, 13):
            g.put(x, GROUND, z, "cobblestone" if x in (1, 11) or z == 1 else "spruce_planks")
    g.put(3, GROUND, 3, "iron_trapdoor", {"facing": "south", "half": "top", "open": "false",
                                        "powered": "false", "waterlogged": "false"})
    # Walls and corner posts, with a front door facing the porch.
    for y in range(5, 8):
        for x in range(1, 12):
            for z in range(1, 10):
                if x in (1, 11) or z in (1, 9):
                    if x in (1, 11) and z in (1, 9):
                        g.put(x, y, z, "spruce_log", {"axis": "y"})
                    else:
                        g.put(x, y, z, "mossy_cobblestone" if (x + y + z) % 5 == 0 else "cobblestone")
    for half, y in (("lower", 5), ("upper", 6)):
        g.put(6, y, 9, "spruce_door", {"facing": "north", "half": half, "hinge": "left",
                                      "open": "false", "powered": "false"})
    for x in (1, 11):
        for z in (4, 7):
            g.put(x, 6, z, "glass_pane", {"north": "true", "south": "true", "east": "false",
                                         "west": "false", "waterlogged": "false"})
    # A low pitched spruce roof; each slab has a full block directly beneath it.
    for x in range(g.w):
        roof = 8 + min(x, 12 - x) // 2
        for z in range(0, 11):
            for y in range(8, roof + 1):
                g.put(x, y, z, "spruce_planks")
            g.put(x, roof + 1, z, "spruce_slab", {"type": "bottom", "waterlogged": "false"})
    for x in range(2, 11):
        for z in (11, 12):
            g.put(x, 8, z, "spruce_slab", {"type": "bottom", "waterlogged": "false"})
    for x in (2, 10):
        for y in range(5, 8):
            g.put(x, y, 12, "spruce_log", {"axis": "y"})
    g.put(6, 7, 11, "lantern", {"hanging": "true", "waterlogged": "false"})
    # An open stone smoke shaft, the hearth laid ready but cold.
    for y in range(5, 12):
        for x in range(5, 8):
            for z in range(1, 4):
                if (x, z) == (6, 2):
                    g.clear(x, y, z)
                elif (x, z) != (6, 3) or y >= 7:
                    g.put(x, y, z, "cobblestone")
    g.put(6, 5, 2, "campfire", {"facing": "south", "lit": "false", "signal_fire": "false", "waterlogged": "false"})
    ws.chest(g, 9, 5, 2, "south", (0, "bread", 4), (2, "apple", 2), (4, "flint_and_steel", 1),
             (9, "stick", 6), (11, "string", 2))
    g.put(9, 5, 6, "white_bed", {"facing": "east", "occupied": "false", "part": "foot"})
    g.put(10, 5, 6, "white_bed", {"facing": "east", "occupied": "false", "part": "head"})
    g.put(2, 5, 6, "crafting_table")
    g.put(2, 5, 7, "lantern", {"hanging": "false", "waterlogged": "false"})
    ws.mark(g, 9, 5, 5, "spawn:west")
    ws.mark(g, 2, 6, 6, "place:board")
    ws.mark(g, 6, 5, 5, "place:hearth")
    ws.mark(g, 3, 5, 3, "gate:cellar")
    return g


def main():
    jar = sys.argv[1] if len(sys.argv) > 1 else ws.CLIENT_JAR
    g = last_lantern()
    problems = ws.check(g, jar)
    if problems:
        raise SystemExit("\n".join(problems))
    path = os.path.join(ws.OUT, "last_lantern.nbt")
    ws.nbt.save(path, ss.template(g))
    print(f"last_lantern: {len(g.b)} blocks -> {path}")
    if len(sys.argv) > 2:
        previews = sys.argv[2]
        os.makedirs(previews, exist_ok=True)
        ss.COL.update({"crafting_table": (145, 105, 60), "white_bed": (225, 222, 210)})
        # Remove markers in all views. The cutaways only hide existing blocks.
        for at, (name, _, _) in list(g.b.items()):
            if name == "minecraft:structure_block":
                g.clear(*at)
        ss.picture(g, os.path.join(previews, "exterior.png"), "The Last Lantern - exterior", GROUND, 20)
        for at in list(g.b):
            x, y, z = at
            if y >= 8 or y > GROUND and (x == 11 or z == 9):
                g.clear(*at)
        ss.picture(g, os.path.join(previews, "room.png"), "Room - roof and near walls hidden", GROUND, 20)
        for at in list(g.b):
            x, y, z = at
            if y >= GROUND or x == 8 or z == 7:
                g.clear(*at)
        ss.picture(g, os.path.join(previews, "cellar.png"), "Cellar - floor and near walls hidden", 0, 20)


if __name__ == "__main__":
    main()
