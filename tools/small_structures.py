"""The small structures of village mods patch 6 (docs/VillageMods-Plan.md, "Patch 6"), all our own designs (Recurrent
Complex gave only the ideas). Each design is drawn once here as a block grid, and everything is written from that one
grid, so the pictures Andy approves are the blocks the game places:

- structure/smalls/<name>.nbt: the template (DataVersion 5023, 26.3's WORLD_VERSION; 26.3 block names).
- for a worldgen design: worldgen/structure/smalls/<site>.json (a jigsaw of one piece, sunk or raised by `offset`),
  worldgen/template_pool/smalls/<site>.json (its variants), worldgen/structure_set/smalls/<site>.json and
  tags/worldgen/biome/has_structure/smalls/<site>.json. A design placed by our own code (the wayside inn) gets only its
  template.
- a preview picture of each variant (isometric, flat block colours, on a flat lawn) in the vault's Assets folder.

Where a design stands in the world (who built it, why there) is in its Java class and Lore.md; the placement rules
(PlacementReasons) read the structure tags written by hand in tags/worldgen/structure/.

    python -I tools/small_structures.py [preview_dir]

Every block name is checked against the 26.3 sources (E:/mcsrc-26.3, when they're there). Needs Pillow for the pictures.

Template layers: the jigsaw puts layer y=1 at the first free block over the ground plus `offset`, so with offset -k the
ground's own top block is layer k (see GROUND below).
"""
import importlib.util
import math
import os
import random
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA = os.path.join(ROOT, "kingdoms", "src", "main", "resources", "data", "jaymoskingdoms")
NS = "jaymoskingdoms"
DATA_VERSION = 5023
SOURCES = "E:/mcsrc-26.3/common/net/minecraft/world/level/block/Blocks.java"
PREVIEWS = "E:/Jaymo Vault Live/Assets/village-mods-6"

_spec = importlib.util.spec_from_file_location("illager_sites_port", os.path.join(os.path.dirname(os.path.abspath(__file__)), "illager_sites_port.py"))
nbt = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(nbt)
Tag = nbt.Tag


# ---- the grid ---------------------------------------------------------------------------------------------------------

class Grid:
    """(x, y, z) -> (block, {property: value}, block entity data or None). Unlisted spots are left as the world has them."""

    def __init__(self, w, h, d):
        self.w, self.h, self.d = w, h, d
        self.b = {}

    def put(self, x, y, z, name, props=None, data=None):
        if 0 <= x < self.w and 0 <= y < self.h and 0 <= z < self.d:
            self.b[(x, y, z)] = ("minecraft:" + name if ":" not in name else name, dict(props or {}), data)

    def get(self, x, y, z):
        v = self.b.get((x, y, z))
        return v[0].split(":")[1] if v else None

    def clear(self, x, y, z):
        """Air that's placed: it digs out whatever stood there."""
        self.put(x, y, z, "air")

    def chest(self, x, y, z, loot, facing="north"):
        self.put(x, y, z, "chest", {"facing": facing, "type": "single", "waterlogged": "false"},
                 {"LootTable": Tag(nbt.STRING, NS + ":" + loot), "id": Tag(nbt.STRING, "minecraft:chest")})

    def barrel(self, x, y, z, loot, facing="up"):
        self.put(x, y, z, "barrel", {"facing": facing, "open": "false"},
                 {"LootTable": Tag(nbt.STRING, NS + ":" + loot), "id": Tag(nbt.STRING, "minecraft:barrel")})


def template(g):
    palette, index, blocks = [], {}, []
    for (x, y, z) in sorted(g.b, key=lambda p: (p[1], p[2], p[0])):
        name, props, data = g.b[(x, y, z)]
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
        "size": Tag(nbt.LIST, (nbt.INT, [Tag(nbt.INT, g.w), Tag(nbt.INT, g.h), Tag(nbt.INT, g.d)])),
        "entities": Tag(nbt.LIST, (nbt.COMPOUND, [])),
        "blocks": Tag(nbt.LIST, (nbt.COMPOUND, blocks)),
        "palette": Tag(nbt.LIST, (nbt.COMPOUND, palette)),
        "DataVersion": Tag(nbt.INT, DATA_VERSION),
    }


def known_blocks():
    if not os.path.exists(SOURCES):
        return None
    with open(SOURCES, encoding="utf8") as f:
        return {m.lower() for m in re.findall(r"public static final Block (\w+)\s*=", f.read())}


# ---- the pictures -----------------------------------------------------------------------------------------------------

COL = {
    "grass_block": (95, 150, 60), "dirt": (120, 85, 55), "coarse_dirt": (110, 80, 55), "rooted_dirt": (130, 95, 65),
    "stone": (125, 125, 125), "cobblestone": (110, 110, 110), "mossy_cobblestone": (95, 120, 85), "andesite": (135, 135, 132),
    "tuff": (110, 112, 100), "gravel": (130, 125, 120), "stone_bricks": (135, 135, 135), "mossy_stone_bricks": (105, 130, 95),
    "cracked_stone_bricks": (115, 115, 115), "cobblestone_wall": (105, 105, 105), "mossy_cobblestone_wall": (95, 115, 85),
    "chest": (160, 110, 40), "barrel": (130, 95, 55), "air": None,
}


def colour(name):
    if name in COL:
        return COL[name]
    for k, c in (("leaves", (60, 115, 40)), ("log", (100, 78, 46)), ("wood", (100, 78, 46)), ("planks", (165, 130, 80)),
                 ("stairs", (150, 120, 75)), ("slab", (155, 125, 78)), ("fence", (140, 110, 65)), ("lantern", (255, 200, 90)),
                 ("torch", (255, 210, 100)), ("chain", (70, 70, 80)), ("moss", (90, 130, 50)), ("mushroom", (190, 70, 55)),
                 ("azalea", (90, 135, 55)), ("vine", (70, 120, 50)), ("bed", (170, 40, 40)), ("glass", (190, 220, 230)),
                 ("wool", (200, 200, 200)), ("skull", (210, 205, 190)), ("candle", (240, 230, 200)), ("hay", (200, 170, 60)),
                 ("water", (60, 90, 200)), ("sign", (170, 135, 85)), ("door", (150, 115, 70)), ("trapdoor", (150, 115, 70)),
                 ("ladder", (150, 115, 70)), ("bricks", (130, 130, 130)), ("carpet", (150, 60, 60)), ("flower", (230, 200, 70)),
                 ("fern", (70, 130, 60)), ("grass", (90, 145, 60)), ("berries", (220, 170, 60)), ("lichen", (140, 180, 140))):
        if k in name:
            return c
    return (200, 0, 200)


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c)


def picture(g, path, title, ground_layer, scale=12):
    """The grid on a flat lawn at ground_layer (layers below it are cut away, so a buried chest shows through)."""
    from PIL import Image, ImageDraw
    blocks = {}
    for (x, y, z), (name, _, _) in g.b.items():
        n = name.split(":")[1]
        if n not in ("air", "cave_air", "structure_void"):
            blocks[(x, y, z)] = n
    lawn = 4
    for x in range(-lawn, g.w + lawn):
        for z in range(-lawn, g.d + lawn):
            if (x, ground_layer, z) not in g.b:
                blocks[(x, ground_layer, z)] = "grass_block"
    if not blocks:
        return
    w2, h2 = scale, scale // 2

    def proj(x, y, z):
        return ((x - z) * w2, (x + z) * h2 - y * scale)

    xs = [k[0] for k in blocks]; ys = [k[1] for k in blocks]; zs = [k[2] for k in blocks]
    cs = [proj(x, y, z) for x in (min(xs), max(xs) + 1) for y in (min(ys), max(ys) + 1) for z in (min(zs), max(zs) + 1)]
    minx, maxx = min(c[0] for c in cs) - 20, max(c[0] for c in cs) + 20
    miny, maxy = min(c[1] for c in cs) - 40, max(c[1] for c in cs) + 20
    img = Image.new("RGB", (int(maxx - minx), int(maxy - miny)), (236, 239, 243))
    d = ImageDraw.Draw(img)
    ox, oy = -minx, -miny
    for (x, y, z) in sorted(blocks, key=lambda k: (k[0] + k[2], k[1], k[0])):
        c = colour(blocks[(x, y, z)])
        if c is None:
            continue
        p = lambda a, b, cc: (proj(a, b, cc)[0] + ox, proj(a, b, cc)[1] + oy)
        if (x, y + 1, z) not in blocks:
            d.polygon([p(x, y + 1, z), p(x + 1, y + 1, z), p(x + 1, y + 1, z + 1), p(x, y + 1, z + 1)], fill=c, outline=shade(c, 0.8))
        if (x, y, z + 1) not in blocks:
            d.polygon([p(x, y + 1, z + 1), p(x + 1, y + 1, z + 1), p(x + 1, y, z + 1), p(x, y, z + 1)], fill=shade(c, 0.75), outline=shade(c, 0.6))
        if (x + 1, y, z) not in blocks:
            d.polygon([p(x + 1, y + 1, z), p(x + 1, y + 1, z + 1), p(x + 1, y, z + 1), p(x + 1, y, z)], fill=shade(c, 0.6), outline=shade(c, 0.45))
    d.text((10, 8), title, fill=(30, 30, 30))
    img.save(path)
    print("  picture", path)


# ---- the designs ------------------------------------------------------------------------------------------------------
# Each returns a Site: its variants (name -> Grid), the layer the ground's top block is (so `offset` = -ground), and its
# worldgen (None: placed by our own code).

class Site:
    def __init__(self, name, variants, ground, worldgen=None, template=True):
        """template False: a picture only (a design our own code lays out each time, like the mazes: one sample)."""
        self.name, self.variants, self.ground, self.worldgen, self.template = name, variants, ground, worldgen, template


SITES = []


# ---- writing ----------------------------------------------------------------------------------------------------------

def write_site(s, known, previews):
    for vname, g in s.variants.items():
        bad = sorted({v[0].split(":")[1] for v in g.b.values()} - known) if known else []
        assert not bad, f"{vname}: not 26.3 blocks: {bad}"
        if s.template:
            nbt.save(os.path.join(DATA, "structure", "smalls", vname + ".nbt"), template(g))
            print("  template", vname, f"{g.w}x{g.h}x{g.d}", len(g.b), "blocks")
        if previews:
            picture(g, os.path.join(previews, vname + ".png"), f"{vname} ({g.w} x {g.d}, {g.h} high)", s.ground)
    wg = s.worldgen
    if wg is None:
        return
    pool = f"{NS}:smalls/{s.name}"
    nbt.write_json(os.path.join(DATA, "worldgen", "structure", "smalls", s.name + ".json"), {
        "type": "minecraft:jigsaw",
        "biomes": f"#{NS}:has_structure/smalls/{s.name}",
        "step": wg["step"],
        "terrain_adaptation": wg["terrain_adaptation"],
        "spawn_overrides": {},
        "start_pool": pool,
        "size": 1,
        "start_height": {"absolute": -s.ground},
        "project_start_to_heightmap": wg.get("heightmap", "WORLD_SURFACE_WG"),
        "max_distance_from_center": 32,
        "use_expansion_hack": False,
    })
    nbt.write_json(os.path.join(DATA, "worldgen", "template_pool", "smalls", s.name + ".json"), {
        "name": pool,
        "fallback": "minecraft:empty",
        "elements": [{"element": {"element_type": "minecraft:single_pool_element", "processors": "minecraft:empty",
                                  "projection": "rigid", "location": f"{NS}:smalls/{v}"}, "weight": 1} for v in s.variants],
    })
    nbt.write_json(os.path.join(DATA, "worldgen", "structure_set", "smalls", s.name + "s.json"), {
        "structures": [{"structure": f"{NS}:smalls/{s.name}", "weight": 1}],
        "placement": {"type": "minecraft:random_spread", "spacing": wg["spacing"], "separation": wg["separation"], "salt": wg["salt"]},
    })
    nbt.write_json(os.path.join(DATA, "tags", "worldgen", "biome", "has_structure", "smalls", s.name + ".json"),
                   {"replace": False, "values": wg["biomes"]})


def load_designs():
    """Every design file in tools/small_designs/ (one per row): each has sites(api) giving its Site(s), api being this module."""
    here = os.path.join(os.path.dirname(os.path.abspath(__file__)), "small_designs")
    me = sys.modules[__name__]
    for f in sorted(os.listdir(here)):
        if f.endswith(".py") and not f.startswith("_"):
            spec = importlib.util.spec_from_file_location("small_designs_" + f[:-3], os.path.join(here, f))
            mod = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(mod)
            SITES.extend(mod.sites(me))


def main():
    previews = sys.argv[1] if len(sys.argv) > 1 else PREVIEWS
    try:
        import PIL  # noqa: F401
        os.makedirs(previews, exist_ok=True)
    except ImportError:
        print("no Pillow: templates only")
        previews = None
    known = known_blocks()
    if known is None:
        print("26.3 sources not found: block names not checked")
    load_designs()
    for s in SITES:
        print(s.name)
        write_site(s, known, previews)


if __name__ == "__main__":
    main()
