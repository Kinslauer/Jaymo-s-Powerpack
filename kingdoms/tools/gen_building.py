"""Assets for batch 4's building blocks (plot stakes, path and grass slabs, wind chimes, the rope lift).

Blockstates, block and item models, item definitions, loot tables and recipes. Blocks cut from a vanilla material use
vanilla's texture; the few that aren't are painted here, 16x16, in vanilla's colours (docs/Vanilla-Style.md): the rope,
and the platform and pulley on vanilla's own oak planks.

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


def hexrgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


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


# ---- 3. wind chimes -------------------------------------------------------------------------------------------------

def chime(name, plate, rod, recipe_key, recipe_pattern):
    # a little plate under the block, four rods of different lengths and a clapper on a thread in the middle
    rods = [((4.5, 6, 4.5), (5.5, 14, 5.5)), ((10.5, 4, 4.5), (11.5, 14, 5.5)),
            ((4.5, 5, 10.5), (5.5, 14, 11.5)), ((10.5, 7, 10.5), (11.5, 14, 11.5))]
    elements = [box((4, 14, 4), (12, 15, 12), "#plate"), box((7.5, 15, 7.5), (8.5, 16, 8.5), "#plate")]
    elements += [box(a, b, "#rod") for a, b in rods]
    elements.append(box((7.75, 10, 7.75), (8.25, 14, 8.25), "#plate", sides=("north", "south", "west", "east")))
    elements.append(box((6.5, 8, 6.5), (9.5, 10, 9.5), "#plate"))
    block_model(name, {"parent": "minecraft:block/block", "textures": {"particle": rod, "plate": plate, "rod": rod},
                       "elements": elements, "display": {
                           "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.85, 0.85, 0.85]},
                           "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5]},
                           "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.75, 0.75, 0.75]},
                           "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
                           "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]}}})
    # (one model whether it's ringing or not: it rings, it doesn't light up)
    blockstate(name, {"variants": {"ringing=false": {"model": NS + "block/" + name}, "ringing=true": {"model": NS + "block/" + name}}})
    item_def(name, NS + "block/" + name)
    drops_self(name)
    recipe(name, {"type": "minecraft:crafting_shaped", "category": "misc", "key": recipe_key, "pattern": recipe_pattern,
                  "result": {"id": NS + name, "count": 1}})


def wind_chimes():
    chime("wind_chime", "minecraft:block/bamboo_planks", "minecraft:block/bamboo_block",
          {"S": "minecraft:string", "P": "minecraft:bamboo_slab", "B": "minecraft:bamboo"}, [" S ", "BPB", "B B"])
    chime("iron_wind_chime", "minecraft:block/dark_oak_planks", "minecraft:block/iron_block",
          {"S": "minecraft:string", "P": "#minecraft:wooden_slabs", "N": "minecraft:iron_nugget"}, [" S ", "NPN", "N N"])


# ---- 4. the rope lift -----------------------------------------------------------------------------------------------

# vanilla's oak planks (its 7 colours, darkest first, and its pixels), the base of the pulley and the platform
OAK = ["67502c", "7e6237", "967441", "9f844d", "af8f55", "b8945f", "c29d62"]
OAK_PIXELS = [
    "5456666656666652", "5544234555445533", "4555454333344552", "2112210011010010",
    "5636666366654435", "4554343234455554", "3345444255544333", "0011210000111210",
    "5665446666666653", "5455554333434332", "6544333344443353", "0012210001211000",
    "6356655366636566", "4455334335545444", "4334543255433333", "0110012221010000",
]
# iron, lightest first (vanilla's iron block and bars); hemp rope, lightest first
IRON = ["e6e6e6", "c1c1c1", "9c9c9c", "686868"]
HEMP = ["d8be8a", "bf9f68", "a07d4a", "7a5a32", "5c4224"]


def oak():
    return [[hexrgb(OAK[int(c)]) for c in row] for row in OAK_PIXELS]


def save(kind, name, pixels):
    """pixels: 16 rows of (r, g, b) or None (clear)."""
    png(os.path.join(ASSETS, "textures", kind, name + ".png"),
        [[(0, 0, 0, 0) if p is None else p + (255,) for p in row] for row in pixels])


def rope_textures():
    # the block: a twisted hemp strand, three strands laid diagonally, lit from the top-left
    px = [[hexrgb(HEMP[[0, 1, 2, 3][(x + y) % 4] if (x + 2 * y) % 8 else 3]) for x in range(16)] for y in range(16)]
    save("block", "rope", px)
    # the item: a coil seen from above, its loose end hanging off to the lower right
    px = [[None] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            dx, dy = (x - 7.0) / 6.5, (y - 7.0) / 5.5
            d = dx * dx + dy * dy
            if 0.12 <= d <= 1.0:
                # two turns of rope: a groove between them, the inner turn a shade darker
                lit = (-dx - dy) * 0.7 - (0.6 if d < 0.45 else 0.0)
                shade = 0 if lit > 0.7 else 1 if lit > 0.1 else 2 if lit > -0.5 else 3
                if 0.45 <= d <= 0.55:
                    shade = 4
                if (x + y) % 3 == 0:
                    shade = min(4, shade + 1)
                px[y][x] = hexrgb(HEMP[shade])
    for i, (x, y) in enumerate([(12, 11), (12, 12), (13, 13), (13, 14), (14, 15)]):
        px[y][x] = hexrgb(HEMP[2 if i % 2 else 1])
        if x + 1 < 16:
            px[y][x + 1] = hexrgb(HEMP[3])
    save("item", "rope", px)


def pulley_textures():
    # the side: oak planks, a wheel with an iron rim, four spokes and an iron hub, rope over the top of it
    px = oak()
    cx = cy = 7.5
    for y in range(16):
        for x in range(16):
            r = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if 4.6 <= r <= 5.9:
                px[y][x] = hexrgb(IRON[1 if x + y < 15 else 2])
            elif 5.9 < r <= 6.6:
                px[y][x] = hexrgb(IRON[3])
            elif r < 4.6:
                spoke = abs(x - cx) < 1 or abs(y - cy) < 1
                px[y][x] = hexrgb(OAK[4] if spoke else OAK[0])
    for y in (7, 8):
        for x in (7, 8):
            px[y][x] = hexrgb(IRON[0] if (x, y) == (7, 7) else IRON[2])
    save("block", "pulley_side", px)
    # the bottom: oak planks with the hole the rope runs through
    px = oak()
    for y in range(5, 11):
        for x in range(5, 11):
            edge = x in (5, 10) or y in (5, 10)
            px[y][x] = hexrgb(IRON[2] if edge else "2b1e10")
    for y in range(6, 10):
        for x in (7, 8):
            px[y][x] = hexrgb(HEMP[1 if x == 7 else 3])
    save("block", "pulley_bottom", px)


def platform_texture():
    # oak planks bound with two iron straps, a rivet where each crosses a board
    px = oak()
    for y in range(16):
        for x in (2, 3, 12, 13):
            px[y][x] = hexrgb(IRON[1] if x in (2, 12) else IRON[2])
    for y in (1, 5, 9, 13):
        for x in (2, 12):
            px[y][x] = hexrgb(IRON[0])
            px[y][x + 1] = hexrgb(IRON[3])
    save("block", "platform", px)


def rope_lift():
    rope_textures()
    pulley_textures()
    platform_texture()

    block_model("rope", {"parent": "minecraft:block/block", "textures": {"particle": NS + "block/rope", "rope": NS + "block/rope"},
                         "elements": [box((6.5, 0, 6.5), (9.5, 16, 9.5), "#rope", sides=("north", "south", "west", "east"))]})
    blockstate("rope", {"variants": {"": {"model": NS + "block/rope"}}})
    item_model("rope", {"parent": "minecraft:item/generated", "textures": {"layer0": NS + "item/rope"}})
    item_def("rope", NS + "item/rope")
    drops_self("rope")
    recipe("rope", {"type": "minecraft:crafting_shaped", "category": "misc", "key": {"S": "minecraft:string"},
                    "pattern": ["S", "S", "S"], "result": {"id": NS + "rope", "count": 2}})

    # the anchor: an iron plate fixed to a block, a hooked arm the rope hangs from (under a block: hanging straight down)
    tex = {"particle": "minecraft:block/iron_block", "plate": "minecraft:block/iron_block"}
    block_model("anchor", {"parent": "minecraft:block/block", "textures": tex, "elements": [
        box((4, 13, 4), (12, 16, 12), "#plate"), box((6.5, 8, 6.5), (9.5, 13, 9.5), "#plate")]})
    block_model("anchor_wall", {"parent": "minecraft:block/block", "textures": tex, "elements": [
        box((4, 4, 0), (12, 12, 3), "#plate"), box((6.5, 4, 3), (9.5, 7, 9.5), "#plate")]})
    variants = {"facing=up": {"model": NS + "block/anchor"}, "facing=down": {"model": NS + "block/anchor"}}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        v = {"model": NS + "block/anchor_wall"}
        if y:
            v["y"] = y
        variants["facing=" + facing] = v
    blockstate("anchor", {"variants": variants})
    item_def("anchor", NS + "block/anchor")
    drops_self("anchor")
    recipe("anchor", {"type": "minecraft:crafting_shaped", "category": "misc", "key": {"I": "minecraft:iron_ingot", "N": "minecraft:iron_nugget"},
                      "pattern": ["NIN", " N "], "result": {"id": NS + "anchor", "count": 1}})

    block_model("pulley", {"parent": "minecraft:block/cube_bottom_top", "textures": {
        "side": NS + "block/pulley_side", "top": "minecraft:block/oak_planks", "bottom": NS + "block/pulley_bottom"}})
    blockstate("pulley", {"variants": {"": {"model": NS + "block/pulley"}}})
    item_def("pulley", NS + "block/pulley")
    drops_self("pulley")
    recipe("pulley", {"type": "minecraft:crafting_shaped", "category": "misc", "key": {
        "P": "#minecraft:planks", "I": "minecraft:iron_ingot", "R": NS + "rope"},
        "pattern": ["PPP", "IRI", "PPP"], "result": {"id": NS + "pulley", "count": 1}})

    block_model("platform", {"parent": "minecraft:block/cube_all", "textures": {"all": NS + "block/platform"}})
    blockstate("platform", {"variants": {"": {"model": NS + "block/platform"}}})
    item_def("platform", NS + "block/platform")
    drops_self("platform")
    recipe("platform", {"type": "minecraft:crafting_shaped", "category": "building", "key": {
        "P": "#minecraft:planks", "I": "minecraft:iron_ingot"},
        "pattern": ["PPP", "I I"], "result": {"id": NS + "platform", "count": 3}})


plot_stake()
earth_slabs()
wind_chimes()
rope_lift()
