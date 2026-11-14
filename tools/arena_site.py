"""Draws the illagers' desert arena (our own design: Illager Arena, the mod Andy pointed at, is All Rights Reserved, so
none of its files or art are used; only the idea of a fighting pit with stands, prep rooms, sleeping quarters, a smithy
and a caster's room over it). Andy, 2026-11-14: "taking over an area like this from bandits or illagers should allow
you to add it to a schematic, so your kingdom can build it".

    python -I tools/arena_site.py

What it writes (under kingdoms/src/main/resources/data/jaymoskingdoms unless said):
- structure/illager_sites/arena.nbt: the arena, 47 x 47, its pit sunk four blocks below the sand.
- worldgen/structure, template_pool and structure_set/illager_sites/arena*.json; tags/worldgen/biome/illager_sites/arena.json
  (deserts only).
- kingdoms/src/main/java/com/jaymoskingdoms/ArenaPosts.java: where its people stand (as SitePosts does for the ported
  sites): R pillagers on the stands, M vindicators, L the warlord in the pit, C evokers, B a ravager kept for the fights,
  P the villagers penned to fight, A the allays caged in the caster's box.
"""
import importlib.util
import json
import math
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA = os.path.join(ROOT, "kingdoms", "src", "main", "resources", "data", "jaymoskingdoms")
JAVA = os.path.join(ROOT, "kingdoms", "src", "main", "java", "com", "jaymoskingdoms", "ArenaPosts.java")
NS = "jaymoskingdoms"
NAME = "arena"

# the NBT reader/writer of the Illager Structures port (same folder)
_spec = importlib.util.spec_from_file_location("illager_sites_port", os.path.join(os.path.dirname(os.path.abspath(__file__)), "illager_sites_port.py"))
nbt = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(nbt)
Tag = nbt.Tag

SIDE, HIGH = 47, 17
C = 23  # the middle
GROUND = 5  # the first layer of open air at the sand's level
PIT, PIT_WALL, ROOM_IN, ROOM_OUT, STANDS, WALL = 11.0, 12.5, 13.5, 19.5, 20.5, 22.5
WALL_TOP = 12
JUNK = NS + ":chests/illager_sites/unheld_camp"
VAULT = NS + ":chests/illager_sites/unheld_vault"
DATA_VERSION = 3465  # as the ported templates: the game's fixer brings the states up to date

grid = {}  # (x, y, z) -> (name, props dict, nbt dict or None)
posts = []


def put(x, y, z, name, props=None, data=None):
    if 0 <= x < SIDE and 0 <= y < HIGH and 0 <= z < SIDE:
        grid[(x, y, z)] = (name, props or {}, data)


def r_of(x, z):
    return math.hypot(x - C, z - C)


def angle(x, z):
    """Degrees, 0 east, 90 south, -90 north."""
    return math.degrees(math.atan2(z - C, x - C))


def outward(x, z):
    """The cardinal direction pointing away from the middle."""
    dx, dz = x - C, z - C
    if abs(dx) >= abs(dz):
        return "east" if dx > 0 else "west"
    return "south" if dz > 0 else "north"


OPP = {"north": "south", "south": "north", "east": "west", "west": "east"}


def in_sector(a, lo, hi):
    a = (a + 360.0) % 360.0
    lo, hi = (lo + 360.0) % 360.0, (hi + 360.0) % 360.0
    return lo <= a <= hi if lo <= hi else a >= lo or a <= hi


def tier(r):
    """The stands' step at r (0 by the pit, 3 at the back)."""
    return min(3, int((r - PIT_WALL) / 2.0))


# rooms under the stands: (name, from, to) in degrees
ROOMS = [("prep", -35, 35), ("sleep", 55, 125), ("smithy", 145, 215), ("pens", -80, -45), ("armory", -135, -100)]


def room_of(x, z):
    r = r_of(x, z)
    if not (ROOM_IN < r <= ROOM_OUT):
        return None
    a = angle(x, z)
    for name, lo, hi in ROOMS:
        if in_sector(a, lo, hi):
            return name
    return None


def entrance(x, z):
    """The way in from the north: three wide."""
    return abs(x - C) <= 1 and z < C


def build():
    for x in range(SIDE):
        for z in range(SIDE):
            r = r_of(x, z)
            if r > WALL:
                continue
            # the footing
            put(x, 0, z, "minecraft:sand" if r <= PIT else "minecraft:smooth_sandstone")
            for y in range(1, HIGH):
                put(x, y, z, "minecraft:air")
            if r <= PIT:
                continue
            if r <= PIT_WALL:
                # the pit's wall, a parapet on top
                for y in range(1, GROUND):
                    put(x, y, z, "minecraft:chiseled_sandstone" if y == 2 else "minecraft:cut_sandstone")
                put(x, GROUND, z, "minecraft:sandstone_wall")
                continue
            if r <= STANDS:
                t = tier(r)
                top = GROUND - 1 + t
                for y in range(1, top + 1):
                    put(x, y, z, "minecraft:sandstone")
                inner_edge = (r - PIT_WALL) % 2.0 < 1.0
                if inner_edge:
                    put(x, top, z, "minecraft:sandstone_stairs", {"facing": outward(x, z), "half": "bottom", "shape": "straight"})
                else:
                    put(x, top, z, "minecraft:cut_sandstone")
                continue
            # the outer wall: dark oak posts every so often, red bands
            a = angle(x, z)
            post = any(abs(((a - p + 180) % 360) - 180) < 3.0 for p in range(-180, 180, 30))
            for y in range(1, WALL_TOP):
                if post:
                    put(x, y, z, "minecraft:dark_oak_log", {"axis": "y"})
                elif y in (6, 10):
                    put(x, y, z, "minecraft:cut_red_sandstone")
                else:
                    put(x, y, z, "minecraft:smooth_sandstone")
            if r > WALL - 1.0 and (x + z) % 2 == 0:
                put(x, WALL_TOP, z, "minecraft:sandstone_wall")
    rooms()
    way_in()
    caster_box()
    lights()
    people()


def rooms():
    for x in range(SIDE):
        for z in range(SIDE):
            room = room_of(x, z)
            if room is None:
                continue
            put(x, 0, z, "minecraft:dark_oak_planks" if room in ("sleep", "armory") else "minecraft:smooth_sandstone")
            for y in range(1, 4):
                put(x, y, z, "minecraft:air")
    # a doorway from each room into the pit (barred for the pens)
    for name, lo, hi in ROOMS:
        mid = math.radians((lo + hi) / 2.0)
        for step in range(0, 40):
            r = PIT - 0.4 + step * 0.1
            if r > ROOM_IN + 0.6:
                break
            for side in (-1, 0, 1):
                px = C + math.cos(mid) * r - math.sin(mid) * side
                pz = C + math.sin(mid) * r + math.cos(mid) * side
                x, z = int(round(px)), int(round(pz))
                if r_of(x, z) <= PIT:
                    continue
                for y in (1, 2):
                    if name == "pens":
                        put(x, y, z, "minecraft:iron_bars")
                    else:
                        put(x, y, z, "minecraft:air")
    furnish()


def along_back(room, count, inset=0.8):
    """Spots along a room's back wall, spread over its arc: (x, z)."""
    lo, hi = next((lo, hi) for n, lo, hi in ROOMS if n == room)
    out = []
    for i in range(count):
        a = math.radians(lo + (hi - lo) * (i + 0.5) / count)
        r = ROOM_OUT - inset
        spot = (int(round(C + math.cos(a) * r)), int(round(C + math.sin(a) * r)))
        if room_of(*spot) == room and spot not in out:
            out.append(spot)
    return out


def chest(x, y, z, facing, table=JUNK):
    put(x, y, z, "minecraft:chest", {"facing": facing, "type": "single"}, {"id": Tag(nbt.STRING, "minecraft:chest"), "LootTable": Tag(nbt.STRING, table)})


def mid_room(room, r=(ROOM_IN + ROOM_OUT) / 2.0):
    lo, hi = next((lo, hi) for n, lo, hi in ROOMS if n == room)
    a = math.radians((lo + hi) / 2.0)
    return int(round(C + math.cos(a) * r)), int(round(C + math.sin(a) * r))


def furnish():
    # the smithy: anvil, smithing table, blast furnace, grindstone, a chest
    spots = along_back("smithy", 6)
    kit = [("minecraft:anvil", {"facing": "north"}), ("minecraft:smithing_table", {}), ("minecraft:blast_furnace", {"facing": "east", "lit": "false"}),
           ("minecraft:grindstone", {"face": "floor", "facing": "east"}), ("minecraft:stonecutter", {"facing": "east"}), None]
    for (x, z), what in zip(spots, kit):
        if what is None:
            chest(x, 1, z, "east")
        else:
            put(x, 1, z, what[0], what[1])
    # the sleeping quarters: beds against the back, head to the wall
    for x, z in along_back("sleep", 7, 1.2):
        out = outward(x, z)
        dx, dz = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}[out]
        if room_of(x + dx, z + dz) != "sleep":
            dx, dz = -dx, -dz
            out = OPP[out]
        put(x, 1, z, "minecraft:gray_bed", {"facing": out, "part": "foot", "occupied": "false"}, {"id": Tag(nbt.STRING, "minecraft:bed")})
        put(x + dx, 1, z + dz, "minecraft:gray_bed", {"facing": out, "part": "head", "occupied": "false"}, {"id": Tag(nbt.STRING, "minecraft:bed")})
    x, z = along_back("sleep", 9, 3.0)[0]
    chest(x, 1, z, "north")
    # the prep area: targets, straw dummies, barrels, a chest
    for i, (x, z) in enumerate(along_back("prep", 6)):
        if i in (0, 5):
            put(x, 1, z, "minecraft:barrel", {"facing": "up", "open": "false"}, {"id": Tag(nbt.STRING, "minecraft:barrel"), "LootTable": Tag(nbt.STRING, JUNK)})
        elif i == 3:
            chest(x, 1, z, "west")
        else:
            put(x, 1, z, "minecraft:hay_block", {"axis": "y"})
            put(x, 2, z, "minecraft:target", {"power": "0"})
    # the armory: chests and barrels, a fletching table
    for i, (x, z) in enumerate(along_back("armory", 5)):
        if i % 2 == 0:
            chest(x, 1, z, "south")
        elif i == 1:
            put(x, 1, z, "minecraft:fletching_table")
        else:
            put(x, 1, z, "minecraft:barrel", {"facing": "up", "open": "false"}, {"id": Tag(nbt.STRING, "minecraft:barrel"), "LootTable": Tag(nbt.STRING, JUNK)})
    # the pens: straw on the floor, a trough
    for x, z in along_back("pens", 3):
        put(x, 1, z, "minecraft:hay_block", {"axis": "y"})
    x, z = along_back("pens", 4, 3.0)[0]
    put(x, 1, z, "minecraft:cauldron")


def way_in():
    # the corridor from the north gate to the pit's edge, under the stands
    for z in range(0, C):
        r = r_of(C, z)
        if r > WALL or r < PIT_WALL + 0.5:
            continue
        for x in range(C - 1, C + 2):
            put(x, GROUND - 1, z, "minecraft:cut_sandstone")
            for y in range(GROUND, GROUND + 3):
                put(x, y, z, "minecraft:air")
            if r <= STANDS:
                put(x, GROUND + 3, z, "minecraft:cut_sandstone")
        # torches on the corridor's walls
        if z % 4 == 0 and r <= STANDS:
            put(C - 2, GROUND + 1, z, "minecraft:sandstone")
            put(C + 2, GROUND + 1, z, "minecraft:sandstone")
            put(C - 1, GROUND + 1, z, "minecraft:wall_torch", {"facing": "east"})
            put(C + 1, GROUND + 1, z, "minecraft:wall_torch", {"facing": "west"})
    # down into the pit
    pit_edge = C - int(PIT_WALL)  # z of the pit wall on the north side
    for i, y in enumerate((3, 2, 1, 0)):
        z = pit_edge + i
        for x in range(C - 1, C + 2):
            if y > 0:
                put(x, y, z, "minecraft:sandstone_stairs", {"facing": "north", "half": "bottom", "shape": "straight"})
                for below in range(1, y):
                    put(x, below, z, "minecraft:sandstone")
            for up in range(y + 1, GROUND + 3):
                put(x, up, z, "minecraft:air")
    # the gate: a dark oak frame
    for z in range(0, C):
        if WALL - 1.0 < r_of(C, z) <= WALL:
            for y in range(GROUND - 1, GROUND + 4):
                put(C - 2, y, z, "minecraft:dark_oak_log", {"axis": "y"})
                put(C + 2, y, z, "minecraft:dark_oak_log", {"axis": "y"})
            for x in range(C - 2, C + 3):
                put(x, GROUND + 3, z, "minecraft:dark_oak_log", {"axis": "x"})


def caster_box():
    """The caster's box over the way in: a dark oak room looking down on the pit, the arena's best chest and its caged
    allays inside, a ladder up from the corridor."""
    floor = GROUND + 3
    zs = [z for z in range(0, C) if 14.0 <= r_of(C, z) <= 20.0]
    z0, z1 = min(zs), max(zs)
    for z in range(z0, z1 + 1):
        for x in range(C - 4, C + 5):
            edge = x in (C - 4, C + 4) or z in (z0, z1)
            put(x, floor, z, "minecraft:dark_oak_planks")
            # the stands under it filled solid
            for y in range(GROUND, floor):
                if grid.get((x, y, z), ("",))[0] == "minecraft:air" and not (abs(x - C) <= 1 and y < GROUND + 3):
                    put(x, y, z, "minecraft:sandstone")
            corner = x in (C - 4, C + 4) and z in (z0, z1)
            for y in range(floor + 1, floor + 4):
                if corner:
                    put(x, y, z, "minecraft:dark_oak_log", {"axis": "y"})
                elif edge:
                    if z == z1 and y < floor + 3 and abs(x - C) <= 3:
                        # open on the pit's side behind a railing
                        put(x, y, z, "minecraft:dark_oak_fence" if y == floor + 1 else "minecraft:air")
                    elif y == floor + 2:
                        put(x, y, z, "minecraft:glass_pane")
                    else:
                        put(x, y, z, "minecraft:dark_oak_planks")
                else:
                    put(x, y, z, "minecraft:air")
            put(x, floor + 4, z, "minecraft:dark_oak_slab", {"type": "bottom"})
    # the vault chests and the allays' cage at the back
    chest(C - 2, floor + 1, z0 + 1, "south", VAULT)
    chest(C + 2, floor + 1, z0 + 1, "south", VAULT)
    for x in range(C - 1, C + 2):
        for y in range(floor + 1, floor + 3):
            put(x, y, z0 + 1, "minecraft:iron_bars" if x != C else "minecraft:air")
        put(x, floor + 1, z0 + 2, "minecraft:iron_bars")
        put(x, floor + 2, z0 + 2, "minecraft:iron_bars")
        put(x, floor + 3, z0 + 1, "minecraft:glass")
        put(x, floor + 3, z0 + 2, "minecraft:glass")
    put(C, floor + 1, z0 + 1, "minecraft:air")
    posts.append((C, floor + 1, z0 + 1, "A"))
    posts.append((C, floor + 2, z0 + 1, "A"))
    put(C, floor + 3, (z0 + z1) // 2, "minecraft:lantern", {"hanging": "true"})
    # the ladder: a shaft beside the corridor up through the box's floor
    lz = (z0 + z1) // 2
    for y in range(GROUND, floor + 1):
        put(C - 2, y, lz, "minecraft:ladder", {"facing": "east"})
        put(C - 3, y, lz, "minecraft:sandstone")
    posts.append((C + 1, floor + 1, lz, "C"))


def lights():
    # lanterns on the parapet and over the rooms
    for a in range(0, 360, 30):
        x = int(round(C + math.cos(math.radians(a)) * (PIT + 1.0)))
        z = int(round(C + math.sin(math.radians(a)) * (PIT + 1.0)))
        if grid.get((x, GROUND, z), ("",))[0] == "minecraft:sandstone_wall":
            put(x, GROUND + 1, z, "minecraft:lantern", {"hanging": "false"})
    for name, lo, hi in ROOMS:
        x, z = mid_room(name)
        put(x, 3, z, "minecraft:lantern", {"hanging": "true"})


def people():
    # the warlord in the pit, a ravager kept for the fights, vindicators about it
    posts.append((C, 1, C + 2, "L"))
    posts.append((C + 5, 1, C + 5, "B"))
    for a in (45, 135, 225, 315):
        posts.append((int(round(C + math.cos(math.radians(a)) * 7)), 1, int(round(C + math.sin(math.radians(a)) * 7)), "M"))
    # pillagers along the top of the stands (not over the way in)
    for a in range(0, 360, 36):
        if in_sector(a, -110, -70):
            continue
        r = STANDS - 0.6
        x, z = int(round(C + math.cos(math.radians(a)) * r)), int(round(C + math.sin(math.radians(a)) * r))
        top = GROUND - 1 + tier(r_of(x, z))
        posts.append((x, top + 1, z, "R"))
    # the rooms' keepers, the penned villagers
    for room, kind in (("prep", "M"), ("prep", "C"), ("smithy", "M"), ("sleep", "M"), ("armory", "R")):
        x, z = mid_room(room, ROOM_IN + 1.0 + (1.0 if kind == "C" else 0.0))
        posts.append((x, 1, z, kind))
    for i, (x, z) in enumerate(along_back("pens", 3, 2.4)):
        posts.append((x, 1, z, "P"))
    # one at the gate
    posts.append((C, GROUND, C - int(STANDS) + 1, "M"))


# ---- connections (walls, bars, panes and fences join their neighbours) ---------------------------------------------------

JOINS = ("_wall", "iron_bars", "glass_pane", "_fence")


def solid(name):
    return name not in ("minecraft:air", "minecraft:ladder", "minecraft:lantern", "minecraft:wall_torch") and "stairs" not in name and "slab" not in name


def connect():
    for (x, y, z), (name, props, data) in list(grid.items()):
        if not any(j in name for j in JOINS):
            continue
        sides = {}
        for d, (dx, dz) in {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}.items():
            n = grid.get((x + dx, y, z + dz), ("minecraft:air", {}, None))[0]
            sides[d] = solid(n)
        p = dict(props)
        if name.endswith("_wall"):
            for d, j in sides.items():
                p[d] = "low" if j else "none"
            p["up"] = "false" if (sides["north"] and sides["south"] and not sides["east"] and not sides["west"]) or (
                sides["east"] and sides["west"] and not sides["north"] and not sides["south"]) else "true"
        else:
            for d, j in sides.items():
                p[d] = "true" if j else "false"
        grid[(x, y, z)] = (name, p, data)


# ---- writing --------------------------------------------------------------------------------------------------------------

def template():
    palette, index, blocks = [], {}, []
    for (x, y, z) in sorted(grid, key=lambda p: (p[1], p[2], p[0])):
        name, props, data = grid[(x, y, z)]
        key = (name, tuple(sorted(props.items())))
        if key not in index:
            index[key] = len(palette)
            st = {"Name": Tag(nbt.STRING, name)}
            if props:
                st["Properties"] = Tag(nbt.COMPOUND, {k: Tag(nbt.STRING, v) for k, v in sorted(props.items())})
            palette.append(Tag(nbt.COMPOUND, st))
        b = {"pos": Tag(nbt.LIST, (nbt.INT, [Tag(nbt.INT, x), Tag(nbt.INT, y), Tag(nbt.INT, z)])), "state": Tag(nbt.INT, index[key])}
        if data is not None:
            b["nbt"] = Tag(nbt.COMPOUND, dict(data))
        blocks.append(Tag(nbt.COMPOUND, b))
    return {
        "size": Tag(nbt.LIST, (nbt.INT, [Tag(nbt.INT, SIDE), Tag(nbt.INT, HIGH), Tag(nbt.INT, SIDE)])),
        "entities": Tag(nbt.LIST, (nbt.COMPOUND, [])),
        "blocks": Tag(nbt.LIST, (nbt.COMPOUND, blocks)),
        "palette": Tag(nbt.LIST, (nbt.COMPOUND, palette)),
        "DataVersion": Tag(nbt.INT, DATA_VERSION),
    }


def write_java():
    entries = ", ".join("new SitePosts.Post(%d, %d, %d, '%s')" % p for p in posts)
    text = """package com.jaymoskingdoms;

import java.util.Map;

/**
 * Generated by {@code tools/arena_site.py}: don't edit by hand. Where the people of the illagers' desert arena (our own
 * design) stand, as {@link SitePosts} has them for the ported sites: {@code R} a pillager, {@code M} a vindicator,
 * {@code L} the warlord, {@code C} an evoker, {@code B} a ravager, {@code P} a villager penned to fight, {@code A} a
 * caged allay.
 */
final class ArenaPosts {
	private ArenaPosts() {
	}

	/** Structure path (under {@code illager_sites/}) → its template's posts. */
	static final Map<String, SitePosts.Post[]> POSTS = Map.of(
		"%s", new SitePosts.Post[] {%s});
}
""" % (NAME, entries)
    with open(JAVA, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)


def main():
    build()
    connect()
    nbt.save(os.path.join(DATA, "structure", "illager_sites", NAME + ".nbt"), template())
    nbt.write_json(os.path.join(DATA, "worldgen", "structure", "illager_sites", NAME + ".json"), {
        "type": "minecraft:jigsaw",
        "biomes": "#%s:illager_sites/%s" % (NS, NAME),
        "step": "surface_structures",
        "terrain_adaptation": "beard_thin",
        "spawn_overrides": {},
        "start_pool": "%s:illager_sites/%s" % (NS, NAME),
        "size": 1,
        "start_height": {"absolute": -GROUND},
        "project_start_to_heightmap": "WORLD_SURFACE_WG",
        "max_distance_from_center": 80,
        "use_expansion_hack": False,
    })
    nbt.write_json(os.path.join(DATA, "worldgen", "template_pool", "illager_sites", NAME + ".json"), {
        "name": "%s:illager_sites/%s" % (NS, NAME),
        "fallback": "minecraft:empty",
        "elements": [{"element": {"element_type": "minecraft:single_pool_element", "processors": "minecraft:empty", "projection": "rigid",
                                  "location": "%s:illager_sites/%s" % (NS, NAME)}, "weight": 1}],
    })
    nbt.write_json(os.path.join(DATA, "worldgen", "structure_set", "illager_sites", "arenas.json"), {
        "structures": [{"structure": "%s:illager_sites/%s" % (NS, NAME), "weight": 1}],
        "placement": {"type": "minecraft:random_spread", "spacing": 64, "separation": 28, "spread_type": "triangular", "salt": 73841701},
    })
    nbt.write_json(os.path.join(DATA, "tags", "worldgen", "biome", "illager_sites", NAME + ".json"), {"values": ["minecraft:desert"]})
    write_java()
    print("%s: %d blocks, %d posts" % (NAME, sum(1 for v in grid.values() if v[0] != "minecraft:air"), len(posts)))


if __name__ == "__main__":
    main()
