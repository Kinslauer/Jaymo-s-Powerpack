"""Assets for batch 4's building blocks (plot stakes, path and grass slabs, wind chimes, the rope lift).

Blockstates, block and item models, item definitions and loot tables; the few textures that aren't vanilla's own are
painted here as 16x16 pixel grids in vanilla's colours (docs/Vanilla-Style.md). Blocks cut from a vanilla material use
vanilla's texture.

Run from kingdoms/: python -I tools/gen_building.py
"""
import json
import os
import struct
import zlib

ASSETS = os.path.join("src", "main", "resources", "assets", "jaymoskingdoms")
DATA = os.path.join("src", "main", "resources", "data", "jaymoskingdoms")
NS = "jaymoskingdoms:"


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", newline="\n") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def blockstate(name, obj):
    write(os.path.join(ASSETS, "blockstates", name + ".json"), obj)


def block_model(name, obj):
    write(os.path.join(ASSETS, "models", "block", name + ".json"), obj)


def item_model(name, obj):
    write(os.path.join(ASSETS, "models", "item", name + ".json"), obj)


def item_def(name, model, tints=None):
    m = {"type": "minecraft:model", "model": model}
    if tints:
        m["tints"] = tints
    write(os.path.join(ASSETS, "items", name + ".json"), {"model": m})


def loot(name, pools):
    write(os.path.join(DATA, "loot_table", "blocks", name + ".json"),
          {"type": "minecraft:block", "pools": pools, "random_sequence": NS + "blocks/" + name})


def drops_self(name, item=None):
    loot(name, [{"rolls": 1, "condition": {"type": "minecraft:survives_explosion"},
                 "entries": [{"type": "minecraft:item", "name": item or NS + name}]}])


def recipe(name, obj):
    write(os.path.join(DATA, "recipe", name + ".json"), obj)


def face(tex, uv, cull=None, tint=None):
    f = {"uv": uv, "texture": tex}
    if cull:
        f["cullface"] = cull
    if tint is not None:
        f["tintindex"] = tint
    return f


def box(frm, to, tex, cull=False, tint=None, sides=("down", "up", "north", "south", "west", "east")):
    """An element from..to with each face's uv cut from where it sits in the block (as vanilla's own models do)."""
    x0, y0, z0 = frm
    x1, y1, z1 = to
    uvs = {
        "down": [x0, 16 - z1, x1, 16 - z0], "up": [x0, z0, x1, z1],
        "north": [16 - x1, 16 - y1, 16 - x0, 16 - y0], "south": [x0, 16 - y1, x1, 16 - y0],
        "west": [z0, 16 - y1, z1, 16 - y0], "east": [16 - z1, 16 - y1, 16 - z0, 16 - y0],
    }
    tex = tex if isinstance(tex, dict) else {s: tex for s in sides}
    faces = {}
    for s in sides:
        edge = {"down": y0 == 0, "up": y1 == 16, "north": z0 == 0, "south": z1 == 16, "west": x0 == 0, "east": x1 == 16}[s]
        faces[s] = face(tex[s], uvs[s], s if cull and edge else None, tint)
    return {"from": list(frm), "to": list(to), "faces": faces}


# ---- PNG painting ---------------------------------------------------------------------------------------------------

def png(path, rows):
    """Writes a 16x16 RGBA PNG from 16 rows of (r, g, b, a) tuples."""
    os.makedirs(os.path.dirname(path), exist_ok=True)
    raw = b"".join(b"\x00" + b"".join(struct.pack("BBBB", *p) for p in row) for row in rows)

    def chunk(kind, data):
        c = struct.pack(">I", len(data)) + kind + data
        return c + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n")
        f.write(chunk(b"IHDR", struct.pack(">IIBBBBB", len(rows[0]), len(rows), 8, 6, 0, 0, 0)))
        f.write(chunk(b"IDAT", zlib.compress(raw, 9)))
        f.write(chunk(b"IEND", b""))


def paint(grid, palette):
    """Rows of characters, each looked up in the palette ('.' is clear)."""
    rows = []
    for line in grid:
        assert len(line) == 16, line
        rows.append([(0, 0, 0, 0) if c == "." else palette[c] + (255,) for c in line])
    assert len(rows) == 16
    return rows


def hexrgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def texture(kind, name, grid, palette):
    png(os.path.join(ASSETS, "textures", kind, name + ".png"), paint(grid, {k: hexrgb(v) for k, v in palette.items()}))


# ---- 1. plot stakes -------------------------------------------------------------------------------------------------

def plot_stake():
    # a stripped spruce post, pointed into the ground, with a band of white wool where the lines are tied
    post = "minecraft:block/stripped_spruce_log"
    wool = "minecraft:block/white_wool"
    block_model("plot_stake", {
        "parent": "minecraft:block/block",
        "textures": {"particle": post, "post": post, "wool": wool},
        "elements": [
            box((7, 0, 7), (9, 2, 9), "#post", sides=("north", "south", "west", "east")),
            box((6.5, 2, 6.5), (9.5, 12, 9.5), "#post"),
            box((6.25, 9.5, 6.25), (9.75, 11.5, 9.75), "#wool", sides=("north", "south", "west", "east", "up", "down")),
        ],
    })
    blockstate("plot_stake", {"variants": {"": {"model": NS + "block/plot_stake"}}})
    drops_self("plot_stake")


# ---- 2. dirt, grass and path slabs ----------------------------------------------------------------------------------

def slab_element(y0, y1, textures, side_uv, tint_top=False, overlay=None):
    """A full-width slab element from y0 to y1; sides cut from side_uv, the overlay (grass sides) a tinted second skin."""
    faces = {
        "down": face(textures["bottom"], [0, 0, 16, 16], "down" if y0 == 0 else None),
        "up": face(textures["top"], [0, 0, 16, 16], "up" if y1 == 16 else None, 0 if tint_top else None),
    }
    for s in ("north", "south", "west", "east"):
        faces[s] = face(textures["side"], side_uv, s)
    out = [{"from": [0, y0, 0], "to": [16, y1, 16], "faces": faces}]
    if overlay:
        out.append({"from": [0, y0, 0], "to": [16, y1, 16],
                    "faces": {s: face(overlay, side_uv, s, 0) for s in ("north", "south", "west", "east")}})
    return out


def slab_states(name, full):
    blockstate(name, {"variants": {
        "type=bottom": {"model": NS + "block/" + name},
        "type=top": {"model": NS + "block/" + name + "_top"},
        "type=double": {"model": full},
    }})


def slab_loot(name, item=None, silk=False):
    """Drops one (two from a double): the slab itself, or item; with silk, the slab only by silk touch, else item."""
    def entry(what, condition=None):
        e = {"type": "minecraft:item", "modifier": [
            {"type": "minecraft:set_count", "condition": {"type": "minecraft:match_block", "blocks": NS + name,
                                                          "state": {"type": "double"}}, "count": 2},
            {"type": "minecraft:explosion_decay"}], "name": what}
        if condition:
            e["condition"] = condition
        return e
    if silk:
        entries = [{"type": "minecraft:alternatives", "children": [entry(NS + name, "minecraft:tool/can_silk_touch"), entry(item)]}]
    else:
        entries = [entry(item or NS + name)]
    loot(name, [{"rolls": 1, "entries": entries}])


def slab_recipe(name, of):
    recipe(name, {"type": "minecraft:crafting_shaped", "category": "building", "key": {"#": of}, "pattern": ["###"],
                  "result": {"id": NS + name, "count": 6}})


def earth_slabs():
    dirt = {"bottom": "minecraft:block/dirt", "top": "minecraft:block/dirt", "side": "minecraft:block/dirt"}
    block_model("dirt_slab", {"parent": "minecraft:block/slab", "textures": dirt})
    block_model("dirt_slab_top", {"parent": "minecraft:block/slab_top", "textures": dirt})
    slab_states("dirt_slab", "minecraft:block/dirt")
    item_def("dirt_slab", NS + "block/dirt_slab")
    slab_loot("dirt_slab")
    slab_recipe("dirt_slab", "minecraft:dirt")

    # grass: vanilla's grass block cut in half, its top and side overlay tinted (tint 0: the biome's grass)
    grass = {"bottom": "minecraft:block/dirt", "top": "minecraft:block/grass_block_top", "side": "minecraft:block/grass_block_side"}
    over = "minecraft:block/grass_block_side_overlay"
    block_model("grass_slab", {"parent": "minecraft:block/block", "textures": {"particle": "minecraft:block/dirt"},
                               "elements": slab_element(0, 8, grass, [0, 8, 16, 16], True, over)})
    block_model("grass_slab_top", {"parent": "minecraft:block/block", "textures": {"particle": "minecraft:block/dirt"},
                                   "elements": slab_element(8, 16, grass, [0, 0, 16, 8], True, over)})
    slab_states("grass_slab", "minecraft:block/grass_block")
    item_def("grass_slab", NS + "block/grass_slab", [{"type": "minecraft:grass", "downfall": 1.0, "temperature": 0.5}])
    slab_loot("grass_slab", NS + "dirt_slab", silk=True)
    slab_recipe("grass_slab", "minecraft:grass_block")

    # path: a pixel low, as vanilla's dirt path is (its side's top rows are the path's worn edge)
    path = {"bottom": "minecraft:block/dirt", "top": "minecraft:block/dirt_path_top", "side": "minecraft:block/dirt_path_side"}
    block_model("dirt_path_slab", {"parent": "minecraft:block/block", "textures": {"particle": "minecraft:block/dirt"},
                                   "elements": slab_element(0, 7, path, [0, 1, 16, 8])})
    block_model("dirt_path_slab_top", {"parent": "minecraft:block/block", "textures": {"particle": "minecraft:block/dirt"},
                                       "elements": slab_element(8, 15, path, [0, 1, 16, 8])})
    slab_states("dirt_path_slab", "minecraft:block/dirt_path")
    item_def("dirt_path_slab", NS + "block/dirt_path_slab")
    slab_loot("dirt_path_slab", NS + "dirt_slab")
    slab_recipe("dirt_path_slab", "minecraft:dirt_path")


plot_stake()
earth_slabs()
