"""Writes the data and models of the Verdant World's strange growth (com.jaymospowers.eden, Andy's picks from Eden Ring):
blockstates, block and item models, loot tables, recipes, tags, lang, the features and their placements, the four biomes
and the Verdant World's biome layout. Run from the repository root:

    python3 -I tools/gen_eden.py

The textures (assets/jaymospowers/textures/block/eden, textures/environment/eden_*.png) and the pulse tree's shapes are
Eden Ring's own (paulevs, MIT: tools/eden_ring_license.txt), copied in by hand from the 0.6.4 jar; this script does not
need the jar. The ids must match Eden, PulseTreeBlock and EdenFeature.
"""
import copy
import json
import os
import sys

sys.dont_write_bytecode = True
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
os.chdir(ROOT)

NS = "jaymospowers"
ASSETS = f"src/main/resources/assets/{NS}"
DATA = f"src/main/resources/data/{NS}"
MC = "src/main/resources/data/minecraft"
TEX = f"{NS}:block/eden/"
COLOURS = ["white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green",
           "red", "black"]
LANG = {}


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", newline="\n") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def tag(path, values):
    """Adds values to a tag file that other generators write to as well."""
    old = json.load(open(path))["values"] if os.path.exists(path) else []
    write(path, {"values": old + [v for v in values if v not in old]})


def title(block_id):
    return " ".join(w.capitalize() for w in block_id.split("_"))


# ---------------------------------------------------------------- models

def block(block_id, model, name=None, states=None, item_model=None, drop=True):
    """One block: its blockstate, block model(s), item model, loot table and name."""
    if isinstance(model, dict) and "parent" in model or isinstance(model, dict) and "elements" in model:
        write(f"{ASSETS}/models/block/eden/{block_id}.json", model)
    write(f"{ASSETS}/blockstates/{block_id}.json", states or {"variants": {"": {"model": f"{NS}:block/eden/{block_id}"}}})
    if item_model:
        write(f"{ASSETS}/models/item/eden/{block_id}.json", item_model)
        ref = f"{NS}:item/eden/{block_id}"
    else:
        ref = f"{NS}:block/eden/{block_id}"
    write(f"{ASSETS}/items/{block_id}.json", {"model": {"type": "minecraft:model", "model": ref}})
    if drop:
        write(f"{DATA}/loot_table/blocks/{block_id}.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "condition": {
            "type": "minecraft:survives_explosion"}, "entries": [{"type": "minecraft:item", "name": f"{NS}:{block_id}"}]}],
            "random_sequence": f"{NS}:blocks/{block_id}"})
    LANG[f"block.{NS}.{block_id}"] = name or title(block_id)


def cube(all_):
    return {"parent": "minecraft:block/cube_all", "textures": {"all": TEX + all_}}


def column(side, end):
    return {"parent": "minecraft:block/cube_column", "textures": {"side": TEX + side, "end": TEX + end}}


def cross(tex):
    return {"parent": "minecraft:block/cross", "textures": {"cross": TEX + tex}}


def flat(tex):
    return {"parent": "minecraft:item/generated", "textures": {"layer0": TEX + tex}}


def pillar_states(block_id):
    m = f"{NS}:block/eden/{block_id}"
    return {"variants": {"axis=y": {"model": m}, "axis=z": {"model": m, "x": 90}, "axis=x": {"model": m, "x": 90, "y": 90}}}


def glowing(model, glow):
    """Adds, over every part of the model drawn with a texture in {@code glow}, the same part a hair larger in that
    texture's glow layer at full brightness (the glow layers animate with their bases, so the glow beats with them)."""
    model = copy.deepcopy(model)
    extra = []
    for el in model["elements"]:
        faces = {side: face for side, face in el["faces"].items() if face["texture"][1:] in glow}
        if not faces:
            continue
        lit = {"from": [v - 0.02 for v in el["from"]], "to": [v + 0.02 for v in el["to"]], "shade": False, "light_emission": 15, "faces": {}}
        for side, face in faces.items():
            face = {k: v for k, v in face.items() if k != "cullface"}
            face["texture"] += "_e"
            lit["faces"][side] = face
        extra.append(lit)
    for var in glow:
        model["textures"][var + "_e"] = model["textures"][var] + "_e"
    model["elements"] += extra
    return model


def box(frm, to, faces):
    return {"from": frm, "to": to, "faces": faces}


def pulse_part(head):
    """The pulse tree's stem, bare or under a cap 16, 12 or 8 across (Eden Ring's shapes)."""
    tex = {"particle": TEX + "pulse_tree_stem", "texture": TEX + "pulse_tree_stem"}
    side = lambda uv: {"uv": uv, "texture": "#texture"}  # noqa: E731
    if head is None:
        stem = box([6, 0, 6], [10, 16, 10], {"down": {**side([4, 0, 8, 4]), "cullface": "down"}, "up": {**side([4, 0, 8, 4]), "cullface": "up"},
                                             **{d: side([0, 0, 4, 16]) for d in ("north", "south", "west", "east")}})
        return glowing({"parent": "minecraft:block/block", "textures": tex, "elements": [stem]}, ["texture"])
    size, inset = head
    tex.update({"side": TEX + f"pulse_tree_side_{size}", "top": TEX + f"pulse_tree_top_{size}", "bottom": TEX + f"pulse_tree_bottom_{size}"})
    lo, hi = inset, 16 - inset
    stem = box([6, 0, 6], [10, 12, 10], {"down": {**side([4, 0, 8, 4]), "cullface": "down"},
                                         **{d: side([0, 4, 4, 16]) for d in ("north", "south", "west", "east")}})
    cap = box([lo, 12, lo], [hi, 16, hi], {"down": {"uv": [lo, lo, hi, hi], "texture": "#bottom"}, "up": {"uv": [lo, lo, hi, hi], "texture": "#top"},
                                           **{d: {"uv": [lo, 0, hi, 4], "texture": "#side"} for d in ("north", "south", "west", "east")}})
    # the fringe hanging under the cap: two planes each way, a little in from its rim
    a, b = lo + 2, hi - 2
    fringe = [box([x, 7, 0], [x, 12, 16], {d: {"uv": [0, 4, 16, 9], "texture": "#side"} for d in ("west", "east")}) for x in (a, b)] + [
        box([0, 7, z], [16, 12, z], {d: {"uv": [0, 4, 16, 9], "texture": "#side"} for d in ("north", "south")}) for z in (a, b)]
    return glowing({"parent": "minecraft:block/block", "textures": tex, "elements": [stem, cap] + fringe}, ["texture", "side", "bottom"])


def blocks():
    # ---- the mycotic forest ----
    block("eden_mycelium", {"parent": "minecraft:block/cube_bottom_top", "textures": {
        "top": TEX + "eden_mycelium_top", "side": TEX + "eden_mycelium_side", "bottom": "minecraft:block/dirt"}}, "Mycotic Mycelium", drop=False)
    # (like turf: dirt unless taken with silk touch)
    write(f"{DATA}/loot_table/blocks/eden_mycelium.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{
        "type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "condition": "minecraft:tool/can_silk_touch", "name": f"{NS}:eden_mycelium"},
            {"type": "minecraft:item", "condition": {"type": "minecraft:survives_explosion"}, "name": "minecraft:dirt"}]}]}],
        "random_sequence": f"{NS}:blocks/eden_mycelium"})
    block("mycotic_grass", cross("mycotic_grass"), item_model=flat("mycotic_grass"), drop=False)
    write(f"{DATA}/loot_table/blocks/mycotic_grass.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{
        "type": "minecraft:item", "condition": "minecraft:tool/can_shear", "name": f"{NS}:mycotic_grass"}]}],
        "random_sequence": f"{NS}:blocks/mycotic_grass"})
    block("balloon_mushroom_small", cross("balloon_mushroom_small"), "Small Balloon Mushroom", item_model=flat("balloon_mushroom_small"),
          states={"variants": {"stage=0": {"model": f"{NS}:block/eden/balloon_mushroom_small"},
                               "stage=1": {"model": f"{NS}:block/eden/balloon_mushroom_small"}}})
    stem = {"uv": [0, 0, 8, 16], "texture": "#texture"}
    end = {"uv": [8, 0, 16, 8], "texture": "#texture"}
    block("balloon_mushroom_stem", {"parent": "minecraft:block/block", "textures": {
        "particle": TEX + "balloon_mushroom_stem", "texture": TEX + "balloon_mushroom_stem"}, "elements": [box([4, 0, 4], [12, 16, 12], {
            "down": {**end, "cullface": "down"}, "up": {**end, "cullface": "up"}, "north": stem, "south": stem, "west": stem, "east": stem})]})
    block("balloon_mushroom_log", column("balloon_mushroom_log_side", "balloon_mushroom_log_top"), states=pillar_states("balloon_mushroom_log"))
    block("balloon_mushroom_block", {"parent": "minecraft:block/cube_bottom_top", "textures": {
        "top": TEX + "balloon_mushroom_block", "side": TEX + "balloon_mushroom_block", "bottom": TEX + "balloon_mushroom_block_bottom"}},
        "Balloon Mushroom Cap")
    block("balloon_mushroom_planks", cube("balloon_mushroom_planks"))
    for colour in COLOURS:
        name = f"mycotic_lantern_{colour}"
        faces = {d: {"uv": [0, 0, 16, 16], "texture": "#side", "cullface": d} for d in ("north", "south", "west", "east")}
        faces.update({d: {"uv": [0, 0, 16, 16], "texture": "#end", "cullface": d} for d in ("up", "down")})
        block(name, {"parent": "minecraft:block/block", "textures": {"particle": TEX + name + "_side", "side": TEX + name + "_side",
                                                                       "end": TEX + name + "_top"},
                     "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "shade": False, "light_emission": 15, "faces": faces}]},
              f"{title(colour)} Mycotic Lantern")

    # ---- the pulse forest ----
    parts = {"stem": None, "head_small": ("small", 4), "head_medium": ("medium", 2), "head_big": ("big", 0)}
    for shape, head in parts.items():
        write(f"{ASSETS}/models/block/eden/pulse_tree_{shape}.json", pulse_part(head))
    block("pulse_tree", None, states={"variants": {f"shape={s}": {"model": f"{NS}:block/eden/pulse_tree_{s}"} for s in parts}},
          item_model={"parent": f"{NS}:block/eden/pulse_tree_head_small"})
    faces = {d: {"uv": [0, 0, 16, 16], "texture": "#side", "cullface": d} for d in ("north", "south", "west", "east")}
    faces.update({d: {"uv": [0, 0, 16, 16], "texture": "#end", "cullface": d} for d in ("up", "down")})
    log = {"parent": "minecraft:block/block", "textures": {"particle": TEX + "pulse_tree_log_side", "side": TEX + "pulse_tree_log_side",
                                                           "end": TEX + "pulse_tree_log_top"},
           "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": faces}]}
    block("pulse_tree_log", glowing(log, ["side"]), states=pillar_states("pulse_tree_log"))
    block("pulse_tree_planks", cube("pulse_tree_planks"))
    block("pulse_tree_sapling", cross("pulse_tree_sapling"), item_model=flat("pulse_tree_sapling"),
          states={"variants": {"stage=0": {"model": f"{NS}:block/eden/pulse_tree_sapling"},
                               "stage=1": {"model": f"{NS}:block/eden/pulse_tree_sapling"}}})

    # ---- the stone garden ----
    block("eden_mossy_stone", {"parent": "minecraft:block/cube_bottom_top", "textures": {
        "top": TEX + "mossy_stone_top", "side": TEX + "mossy_stone_side", "bottom": "minecraft:block/stone"}}, "Moss-Capped Stone")


    # ---- the brainstorm ----
    block("brain_tree_log", column("brain_tree_log_side", "brain_tree_log_top"), states=pillar_states("brain_tree_log"))
    block("brain_tree_planks", cube("brain_tree_planks"))
    for metal in ("copper", "iron", "gold"):
        name = f"brain_tree_block_{metal}"
        write(f"{ASSETS}/models/block/eden/{name}_active.json", {"parent": "minecraft:block/block", "textures": {
            "particle": TEX + name + "_active", "all": TEX + name + "_active"}, "elements": [{
                "from": [0, 0, 0], "to": [16, 16, 16], "shade": False, "light_emission": 15,
                "faces": {d: {"uv": [0, 0, 16, 16], "texture": "#all", "cullface": d} for d in ("north", "south", "west", "east", "up", "down")}}]})
        block(name, cube(name), f"{title(metal)} Brain Tree Flesh", states={"variants": {
            "active=false": {"model": f"{NS}:block/eden/{name}"}, "active=true": {"model": f"{NS}:block/eden/{name}_active"}}})
        block(f"{metal}_grass", cross(f"{metal}_grass"), item_model=flat(f"{metal}_grass"), drop=False)
        write(f"{DATA}/loot_table/blocks/{metal}_grass.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{
            "type": "minecraft:alternatives", "children": [
                {"type": "minecraft:item", "condition": "minecraft:tool/can_shear", "name": f"{NS}:{metal}_grass"},
                {"type": "minecraft:item", "condition": {"type": "minecraft:random_chance", "chance": 0.1},
                 "name": f"minecraft:{metal}_nugget"}]}]}],
            "random_sequence": f"{NS}:blocks/{metal}_grass"})

    # ---- the lakeside desert ----
    block("aquatus_block", column("aquatus_block_side", "aquatus_block_top"), "Aquatus Head")
    block("aquatus_roots", {"parent": "minecraft:block/cross", "textures": {"cross": TEX + "aquatus_roots"}}, item_model=flat("aquatus_roots"))
    block("aquatus_sapling", cross("aquatus_sapling"), item_model=flat("aquatus_sapling"),
          states={"variants": {"stage=0": {"model": f"{NS}:block/eden/aquatus_sapling"}, "stage=1": {"model": f"{NS}:block/eden/aquatus_sapling"}}})


WOODS = ["balloon_mushroom", "pulse_tree", "brain_tree"]
SIDES = ("north", "south", "west", "east")


def shaped_wood():
    """Stairs, slab, fence, gate, door, trapdoor, button and pressure plate of each wood: the shapes are the Aether woods'
    (gen_aether_flora), pointed at this folder."""
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import gen_aether_flora as F
    F.SUB = "eden"
    for w in WOODS:
        F.WOODS[w] = {"new": False}
        F.wood_assets(w)
        for shape in F.SHAPES:
            b = f"{w}_{shape}"
            LANG[f"block.{NS}.{b}"] = title(b)
            table = F.G.self_table(b)
            if shape == "slab":
                table = F.table(b, [{"rolls": 1, "entries": [F.item_entry(f"{NS}:{b}", modifier=[
                    {"type": "minecraft:set_count", "condition": F.match(b, type="double"), "count": 2}, F.EXPLODE])]}])
            elif shape == "door":
                table = F.table(b, [{"rolls": 1, "condition": F.SURVIVES, "entries": [
                    F.item_entry(f"{NS}:{b}", condition=F.match(b, half="lower"))]}])
            write(f"{DATA}/loot_table/blocks/{b}.json", table)
        planks = f"{NS}:{w}_planks"
        stick = "minecraft:stick"

        def shaped(shape, key, pattern, count, group, cat="building"):
            result = {"id": f"{NS}:{w}_{shape}", "count": count} if count > 1 else {"id": f"{NS}:{w}_{shape}"}
            write(f"{DATA}/recipe/eden/{w}_{shape}.json", {"type": "minecraft:crafting_shaped", "category": cat, "group": group, "key": key,
                                                           "pattern": pattern, "result": result})

        shaped("stairs", {"#": planks}, ["#  ", "## ", "###"], 4, "wooden_stairs")
        shaped("slab", {"#": planks}, ["###"], 6, "wooden_slab")
        shaped("fence", {"#": stick, "W": planks}, ["W#W", "W#W"], 3, "wooden_fence")
        shaped("fence_gate", {"#": stick, "W": planks}, ["#W#", "#W#"], 1, "wooden_fence_gate", "redstone")
        shaped("door", {"#": planks}, ["##", "##", "##"], 3, "wooden_door", "redstone")
        shaped("trapdoor", {"#": planks}, ["###", "###"], 2, "wooden_trapdoor", "redstone")
        shaped("pressure_plate", {"#": planks}, ["##"], 1, "wooden_pressure_plate", "redstone")
        write(f"{DATA}/recipe/eden/{w}_button.json", {"type": "minecraft:crafting_shapeless", "category": "redstone", "group": "wooden_button",
                                                      "ingredients": [planks], "result": {"id": f"{NS}:{w}_button"}})
    kinds = {"wooden_stairs": "stairs", "wooden_slabs": "slab", "wooden_fences": "fence", "fence_gates": "fence_gate", "wooden_doors": "door",
             "wooden_trapdoors": "trapdoor", "wooden_buttons": "button", "wooden_pressure_plates": "pressure_plate"}
    for kind in ("block", "item"):
        for name, shape in kinds.items():
            tag(f"{MC}/tags/{kind}/{name}.json", [f"{NS}:{w}_{shape}" for w in WOODS])
        tag(f"{MC}/tags/{kind}/logs_that_burn.json", [f"{NS}:{w}_log" for w in WOODS])
    tag(f"{MC}/tags/block/mineable/axe.json", [f"{NS}:{w}_{shape}" for w in WOODS for shape in F.SHAPES])


def gravilite():
    """The crystal that bends weight: wild shards, the block, the lamp, the lantern and the Gravity Compressor."""
    block("gravilite_shards", cross("gravilite_shards"), item_model=flat("gravilite_shards"))
    block("gravilite_block", column("gravilite_block_side", "gravilite_block_top"))
    all_faces = {d: {"uv": [0, 0, 16, 16], "texture": "#all", "cullface": d} for d in SIDES + ("up", "down")}
    block("gravilite_lamp", glowing({"parent": "minecraft:block/block", "textures": {
        "particle": TEX + "gravilite_lamp", "all": TEX + "gravilite_lamp"}, "elements": [box([0, 0, 0], [16, 16, 16], all_faces)]}, ["all"]))

    def lantern(y):
        face = {"uv": [8, 8, 16, 16], "texture": "#texture"}
        faces = {d: dict(face) for d in SIDES + ("up",)}
        faces["down"] = {"uv": [8, 0, 16, 8], "texture": "#texture"}
        return glowing({"parent": "minecraft:block/block", "textures": {"particle": TEX + "gravilite_lantern", "texture": TEX + "gravilite_lantern"},
                        "elements": [box([4, y, 4], [12, y + 8, 12], faces)]}, ["texture"])

    write(f"{ASSETS}/models/block/eden/gravilite_lantern_hanging.json", lantern(8))
    block("gravilite_lantern", lantern(0), states={"variants": {
        f"hanging={h},waterlogged={w}": {"model": f"{NS}:block/eden/gravilite_lantern" + ("_hanging" if h == "true" else "")}
        for h in ("false", "true") for w in ("false", "true")}})

    def compressor(side):
        faces = {d: {"uv": [0, 0, 16, 16], "texture": "#side", "cullface": d} for d in SIDES}
        faces.update({d: {"uv": [0, 0, 16, 16], "texture": "#end", "cullface": d} for d in ("up", "down")})
        return glowing({"parent": "minecraft:block/block", "textures": {
            "particle": TEX + "gravity_compressor_bottom", "side": TEX + side, "end": TEX + "gravity_compressor_bottom"},
            "elements": [box([0, 0, 0], [16, 16, 16], faces)]}, ["side"])

    write(f"{ASSETS}/models/block/eden/gravity_compressor_on.json", compressor("gravity_compressor_side_on"))
    block("gravity_compressor", compressor("gravity_compressor_side_off"), states={"variants": {
        f"power={p}": {"model": f"{NS}:block/eden/gravity_compressor" + ("_on" if p else "")} for p in range(16)}})

    def shaped(name, key, pattern, count=1, cat="building"):
        result = {"id": f"{NS}:{name}", "count": count} if count > 1 else {"id": f"{NS}:{name}"}
        write(f"{DATA}/recipe/eden/{name}.json", {"type": "minecraft:crafting_shaped", "category": cat, "key": key, "pattern": pattern, "result": result})

    shards = f"{NS}:gravilite_shards"
    shaped("gravilite_block", {"#": shards}, ["##", "##"])
    shaped("gravilite_lamp", {"#": shards, "G": "minecraft:glass"}, [" G ", "G#G", " G "])
    shaped("gravilite_lantern", {"#": shards, "N": "minecraft:iron_nugget"}, ["NNN", "N#N", "NNN"])
    shaped("gravity_compressor", {"#": f"{NS}:gravilite_block", "I": "minecraft:iron_ingot", "R": "minecraft:redstone", "S": "#minecraft:stone_crafting_materials"},
           ["I#I", "IRI", "SSS"], cat="redstone")
    tag(f"{MC}/tags/block/mineable/pickaxe.json", [f"{NS}:{b}" for b in ("gravilite_block", "gravilite_lantern", "gravity_compressor")])


GRASS = {"type": "minecraft:grass", "downfall": 1.0, "temperature": 0.5}
PLAIN = {"type": "minecraft:constant", "value": -1}


def crossed(texture, overlay=None, glow=None):
    """A cross of the plant's own colours, with its leaves over it in the country's grass colour and its glow, if any."""
    textures = {"particle": TEX + texture, "texture": TEX + texture}
    layers = [("#texture", {})]
    if overlay:
        textures["overlay"] = TEX + overlay
        layers.append(("#overlay", {"tintindex": 0}))
    if glow:
        textures["glow"] = TEX + glow
        layers.append(("#glow", None))
    elements = []
    for ref, extra in layers:
        for frm, to, sides in (([0.8, 0, 8], [15.2, 16, 8], ("north", "south")), ([8, 0, 0.8], [8, 16, 15.2], ("west", "east"))):
            el = {"from": frm, "to": to, "rotation": {"origin": [8, 8, 8], "axis": "y", "angle": 45, "rescale": True}, "shade": False,
                  "faces": {d: dict({"uv": [0, 0, 16, 16], "texture": ref}, **(extra or {})) for d in sides}}
            if extra is None:
                el["light_emission"] = 15
            elements.append(el)
    return {"ambientocclusion": False, "textures": textures, "elements": elements}


def layered(*layers):
    return {"parent": "minecraft:item/generated", "textures": {f"layer{i}": TEX + t for i, t in enumerate(layers)}}


def tinted_item(block_id, tints):
    write(f"{ASSETS}/items/{block_id}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/eden/{block_id}", "tints": tints}})


def loot(block_id, pools):
    write(f"{DATA}/loot_table/blocks/{block_id}.json", {"type": "minecraft:block", "pools": pools, "random_sequence": f"{NS}:blocks/{block_id}"})


def lower_half(block_id):
    return {"type": "minecraft:match_block", "blocks": f"{NS}:{block_id}", "state": {"half": "lower"}}


def small_plants():
    """Violum, lonlix, parignum, limphium, the Eden vine, Eden moss and the symbiotic moulds."""
    m = lambda name: {"model": f"{NS}:block/eden/{name}"}  # noqa: E731
    # violum: two blocks of glowing bloom
    write(f"{ASSETS}/models/block/eden/violum_top.json", crossed("violum_top_color", "violum_top_overlay", "violum_top_color_e"))
    block("violum", crossed("violum_bottom_color", "violum_bottom_overlay", "violum_bottom_color_e"),
          states={"variants": {"half=lower": m("violum"), "half=upper": m("violum_top")}},
          item_model=layered("violum_top_color", "violum_top_overlay"), drop=False)
    tinted_item("violum", [PLAIN, GRASS])
    loot("violum", [{"rolls": 1, "condition": {"type": "minecraft:survives_explosion"}, "entries": [
        {"type": "minecraft:item", "condition": lower_half("violum"), "name": f"{NS}:violum"}]}])
    # lonlix: a leafy tuft, taken with shears
    block("lonlix", crossed("lonlix_color", "lonlix_overlay"), item_model=layered("lonlix_color", "lonlix_overlay"), drop=False)
    tinted_item("lonlix", [PLAIN, GRASS])
    loot("lonlix", [{"rolls": 1, "entries": [{"type": "minecraft:item", "condition": "minecraft:tool/can_shear", "name": f"{NS}:lonlix"}]}])
    # parignum: a mat of leaves with glowing flowers, four ways
    for n in range(1, 5):
        quad = lambda y, ref, **extra: dict({"from": [0, y, 0], "to": [16, y, 16], "shade": False, "faces": {  # noqa: E731
            d: dict({"uv": [0, 0, 16, 16], "texture": ref}, **({"tintindex": 0} if ref == "#leaves" else {})) for d in ("up", "down")}}, **extra)
        write(f"{ASSETS}/models/block/eden/parignum_{n}.json", {"ambientocclusion": False, "textures": {
            "particle": TEX + "parignum", "leaves": TEX + "parignum", "flowers": TEX + f"parignum_flowers_{n}", "glow": TEX + f"parignum_flowers_{n}_e"},
            "elements": [quad(0.5, "#leaves"), quad(0.6, "#flowers"), quad(0.7, "#glow", light_emission=15)]})
    block("parignum", None, states={"variants": {"": [m(f"parignum_{n}") for n in range(1, 5)]}},
          item_model=layered("parignum", "parignum_flowers_1"))
    tinted_item("parignum", [GRASS, PLAIN])
    # limphium: a stem under a head of leaves (Eden Ring's shapes), which gives leaves to eat or dry
    def limphium(name, textures, elements):
        return {"ambientocclusion": False, "textures": dict({"particle": TEX + textures["stem"]}, **{k: TEX + v for k, v in textures.items()}), "elements": elements}

    plane = lambda x, angle, ref, extra=None: dict({"from": [x, 0, 0], "to": [x, 16, 22.5], "rotation": {"origin": [x, 0, 0], "axis": "y", "angle": angle},  # noqa: E731
                                                     "shade": False, "faces": {d: {"uv": [0, 0, 16, 16], "texture": ref} for d in ("west", "east")}},
                                                    **(extra or {}))
    stems = lambda ref, extra=None: [plane(0, 45, ref, extra), plane(16, -45, ref, extra)]  # noqa: E731

    def leaf(lo, hi, y, ref):
        side = {"uv": [lo, 16 - (hi - lo) // 6 - 2, hi, 16], "texture": ref, "tintindex": 0}
        side["uv"][1] = 14
        faces = {d: dict(side) for d in SIDES}
        faces["up"] = {"uv": [lo, lo, hi, hi], "texture": ref, "tintindex": 0}
        faces["down"] = {"uv": [lo, lo, hi, hi], "texture": ref, "tintindex": 0}
        return {"from": [lo, y, lo], "to": [hi, y + 2, hi], "faces": faces}

    write(f"{ASSETS}/models/block/eden/limphium_stem.json", limphium("limphium_stem", {"stem": "limphium_stem"}, stems("#stem")))
    heads = {1: [leaf(2, 14, 2, "#leaf_1"), leaf(3, 13, 6, "#leaf_2")], 2: [leaf(3, 13, 6, "#leaf_2")], 3: [leaf(2, 14, 6, "#leaf_1")]}
    for n, leaves in heads.items():
        write(f"{ASSETS}/models/block/eden/limphium_top_{n}.json", limphium(f"limphium_top_{n}", {
            "stem": "limphium_stem_top", "glow": "limphium_stem_top_e", "leaf_1": "limphium_leaf_1", "leaf_2": "limphium_leaf_2"},
            stems("#stem") + stems("#glow", {"light_emission": 15}) + leaves))
    block("limphium", None, states={"variants": {"half=lower": m("limphium_stem"), "half=upper": [m(f"limphium_top_{n}") for n in heads]}},
          item_model=layered("limphium_sapling_color", "limphium_sapling_overlay"), drop=False)
    tinted_item("limphium", [PLAIN, GRASS])
    loot("limphium", [
        {"rolls": 1, "condition": {"type": "minecraft:survives_explosion"}, "entries": [
            {"type": "minecraft:item", "condition": lower_half("limphium"), "name": f"{NS}:limphium"}]},
        {"rolls": 1, "condition": lower_half("limphium"), "entries": [{"type": "minecraft:item", "name": f"{NS}:limphium_leaf", "modifier": [
            {"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 2}}]}]}])
    for item, name in (("limphium_leaf", "Limphium Leaf"), ("dried_limphium_leaf", "Dried Limphium Leaf")):
        write(f"{ASSETS}/models/item/eden/{item}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/eden/{item}"}})
        write(f"{ASSETS}/items/{item}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/eden/{item}"}})
        LANG[f"item.{NS}.{item}"] = name
    for kind, ticks in (("smelting", 200), ("smoking", 100), ("campfire_cooking", 600)):
        write(f"{DATA}/recipe/eden/dried_limphium_leaf_{kind}.json", {"type": f"minecraft:{kind}", "category": "food", "ingredient": f"{NS}:limphium_leaf",
                                                                      "result": {"id": f"{NS}:dried_limphium_leaf"}, "experience": 0.1, "cookingtime": ticks})
    # the Eden vine: hangs in strings, the lowest block its tip
    write(f"{ASSETS}/models/block/eden/eden_vine_bottom.json", crossed("eden_vine_bottom_color", "eden_vine_bottom_overlay"))
    block("eden_vine", crossed("eden_vine_color", "eden_vine_overlay"),
          states={"variants": {"bottom=false": m("eden_vine"), "bottom=true": m("eden_vine_bottom")}},
          item_model=layered("eden_vine_bottom_color", "eden_vine_bottom_overlay"))
    tinted_item("eden_vine", [PLAIN, GRASS])
    # Eden moss: a thin mat
    block("eden_moss", {"ambientocclusion": False, "textures": {"particle": TEX + "eden_moss_top", "top": TEX + "eden_moss_top"}, "elements": [
        {"from": [0, 0, 0], "to": [16, 1, 16], "faces": dict(
            {d: {"uv": [0, 15, 16, 16], "texture": "#top", "tintindex": 0} for d in SIDES},
            up={"uv": [0, 0, 16, 16], "texture": "#top", "tintindex": 0}, down={"uv": [0, 0, 16, 16], "texture": "#top", "tintindex": 0, "cullface": "down"})}]})
    write(f"{ASSETS}/items/eden_moss.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/eden/eden_moss", "tints": [GRASS]}})
    # the symbiotic moulds: tufts under a roof, one kind glowing
    both = lambda name: {"variants": {"bottom=false": m(name), "bottom=true": m(name)}}  # noqa: E731
    block("symbiotic_mold", crossed("symbiotic_mold"), "Symbiotic Mould", states=both("symbiotic_mold"), item_model=flat("symbiotic_mold"))
    lit = crossed("symbiotic_mold_emissive")
    for el in lit["elements"]:
        el["light_emission"] = 15
    block("symbiotic_mold_emissive", lit, "Glowing Symbiotic Mould", states=both("symbiotic_mold_emissive"),
          item_model=flat("symbiotic_mold_emissive"))
    tag(f"{MC}/tags/block/climbable.json", [f"{NS}:eden_vine"])
    tag(f"{MC}/tags/block/replaceable.json", [f"{NS}:lonlix"])
    tag(f"{MC}/tags/block/replaceable_by_trees.json", [f"{NS}:{b}" for b in ("lonlix", "parignum", "eden_moss")])


def diskwing():
    """The diskwing's name, spawn egg and calls (its skins and sounds are Eden Ring's, copied in by hand)."""
    LANG[f"entity.{NS}.diskwing"] = "Diskwing"
    LANG[f"item.{NS}.diskwing_spawn_egg"] = "Diskwing Spawn Egg"
    LANG[f"subtitles.{NS}.eden.diskwing.ambient"] = "Diskwing calls"
    LANG[f"subtitles.{NS}.eden.diskwing.hurt"] = "Diskwing hurts"
    write(f"{ASSETS}/models/item/eden/diskwing_spawn_egg.json", {"parent": "minecraft:item/generated", "textures": {
        "layer0": f"{NS}:item/eden/diskwing_spawn_egg"}})
    write(f"{ASSETS}/items/diskwing_spawn_egg.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/eden/diskwing_spawn_egg"}})
    # sounds.json is shared and tab-indented: add our two events and leave the rest as it is
    path = f"{ASSETS}/sounds.json"
    sounds = json.load(open(path))
    sounds["eden.diskwing.ambient"] = {"subtitle": f"subtitles.{NS}.eden.diskwing.ambient",
                                       "sounds": [{"name": f"{NS}:eden/diskwing_{n}"} for n in range(1, 7)]}
    sounds["eden.diskwing.hurt"] = {"subtitle": f"subtitles.{NS}.eden.diskwing.hurt", "sounds": [{"name": f"{NS}:eden/diskwing_hurt"}]}
    with open(path, "w", newline="\n") as f:
        f.write(json.dumps(sounds, indent="\t") + "\n")


def recipes_and_tags():
    def shapeless(name, ingredients, result, count=1, group=None):
        r = {"type": "minecraft:crafting_shapeless", "category": "building", "ingredients": ingredients,
             "result": {"id": f"{NS}:{result}", "count": count}}
        if group:
            r["group"] = group
        write(f"{DATA}/recipe/eden/{name}.json", r)

    shapeless("balloon_mushroom_planks", [f"{NS}:balloon_mushroom_log"], "balloon_mushroom_planks", 4, "planks")
    shapeless("balloon_mushroom_planks_from_stems", [f"{NS}:balloon_mushroom_stem", f"{NS}:balloon_mushroom_stem"], "balloon_mushroom_planks", 1, "planks")
    shapeless("pulse_tree_planks", [f"{NS}:pulse_tree_log"], "pulse_tree_planks", 4, "planks")
    shapeless("pulse_tree_planks_from_stems", [f"{NS}:pulse_tree"] * 4, "pulse_tree_planks", 1, "planks")
    tag(f"{DATA}/tags/item/mycotic_lanterns.json", [f"{NS}:mycotic_lantern_{c}" for c in COLOURS])
    for colour in COLOURS:
        shapeless(f"mycotic_lantern_{colour}", [f"#{NS}:mycotic_lanterns", f"minecraft:{colour}_dye"], f"mycotic_lantern_{colour}", 1, "mycotic_lantern")

    shapeless("brain_tree_planks", [f"{NS}:brain_tree_log"], "brain_tree_planks", 4, "planks")
    armour = [f"minecraft:{m}_{p}" for m in ("iron", "golden", "chainmail", "copper", "netherite") for p in ("helmet", "chestplate", "leggings", "boots")]
    tag(f"{DATA}/tags/item/grounding_armor.json", armour)
    brains = [f"{NS}:brain_tree_block_{m}" for m in ("copper", "iron", "gold")]
    tag(f"{MC}/tags/block/mineable/axe.json", [f"{NS}:brain_tree_log", f"{NS}:brain_tree_planks", f"{NS}:aquatus_roots"])
    tag(f"{MC}/tags/block/mineable/hoe.json", brains + [f"{NS}:aquatus_block"])
    for kind in ("block", "item"):
        tag(f"{MC}/tags/{kind}/planks.json", [f"{NS}:brain_tree_planks"])
        tag(f"{MC}/tags/{kind}/logs.json", [f"{NS}:brain_tree_log"])
        tag(f"{MC}/tags/{kind}/saplings.json", [f"{NS}:aquatus_sapling"])

    lanterns = [f"{NS}:mycotic_lantern_{c}" for c in COLOURS]
    wood = [f"{NS}:{b}" for b in ("balloon_mushroom_stem", "balloon_mushroom_log", "balloon_mushroom_planks", "pulse_tree", "pulse_tree_log",
                                  "pulse_tree_planks")]
    tag(f"{MC}/tags/block/mineable/axe.json", wood)
    tag(f"{MC}/tags/block/mineable/hoe.json", lanterns + [f"{NS}:balloon_mushroom_block"])
    tag(f"{MC}/tags/block/mineable/shovel.json", [f"{NS}:eden_mycelium"])
    tag(f"{MC}/tags/block/mineable/pickaxe.json", [f"{NS}:eden_mossy_stone"])
    tag(f"{MC}/tags/block/dirt.json", [f"{NS}:eden_mycelium"])
    tag(f"{MC}/tags/block/supports_vegetation.json", [f"{NS}:eden_mycelium"])
    for kind in ("block", "item"):
        tag(f"{MC}/tags/{kind}/planks.json", [f"{NS}:balloon_mushroom_planks", f"{NS}:pulse_tree_planks"])
        tag(f"{MC}/tags/{kind}/logs.json", [f"{NS}:balloon_mushroom_log", f"{NS}:pulse_tree_log"])
        tag(f"{MC}/tags/{kind}/saplings.json", [f"{NS}:balloon_mushroom_small", f"{NS}:pulse_tree_sapling"])


# ---------------------------------------------------------------- worldgen

FEATURES = {
    # id: (kind, placement)
    "eden_mycelium_ground": ("mycelium_ground", []),
    "eden_stone_ground": ("stone_ground", []),
    "eden_sand_ground": ("sand_ground", []),
    "eden_pond": ("pond", ["rare:2", "surface"]),
    "eden_stone_pillar": ("stone_pillar", ["rare:3", "surface"]),
    "eden_stone_boulder": ("stone_boulder", ["count:2", "surface"]),
    "eden_balloon_mushrooms": ("balloon_mushroom", ["count:5", "surface"]),
    "eden_balloon_mushrooms_sparse": ("balloon_mushroom", ["count:2", "surface"]),
    "eden_old_balloon_mushroom_rare": ("old_balloon_mushroom", ["rare:6", "surface"]),
    "eden_old_balloon_mushrooms": ("old_balloon_mushroom", ["count:3", "surface"]),
    "eden_pulse_trees": ("pulse_tree", ["count:8", "surface"]),
    "eden_brain_trees": ("brain_tree", ["count:3", "surface"]),
    "eden_metal_grass": ("metal_grass", ["count:4", "surface"]),
    "eden_gravilite": ("gravilite", ["rare:5", "surface"]),
    # (after the trees: a plant on the turf would lift the surface a tree looks for)
    "eden_mycotic_plants": ("mycotic_plants", ["count:3", "surface"]),
    # the small growth: what hangs, then what stands, then the mats
    "eden_vines": ("eden_vines", ["count:3", "surface"]),
    "eden_mold": ("symbiotic_mold", ["count:2", "surface"]),
    "eden_limphium": ("limphium", ["rare:2", "surface"]),
    "eden_violum": ("violum", ["rare:2", "surface"]),
    "eden_lonlix": ("lonlix", ["count:2", "surface"]),
    "eden_parignum": ("parignum", ["rare:2", "surface"]),
    "eden_moss": ("eden_moss", ["count:2", "surface"]),
}
# the saplings' trees
TREES = {"balloon_mushroom": "balloon_mushroom", "pulse_tree": "pulse_tree", "aquatus": "aquatus"}


def placement(spec):
    out = []
    for part in spec:
        if part.startswith("count:"):
            out.append({"type": "minecraft:count", "count": int(part[6:])})
        elif part.startswith("rare:"):
            out.append({"type": "minecraft:rarity_filter", "chance": int(part[5:])})
        elif part == "surface":
            out += [{"type": "minecraft:in_square"}, {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"}, {"type": "minecraft:biome"}]
    return out


# name: (display, sky, fog, grass, foliage, water, step 2 features, step 9 features of ours, vanilla step 9 kept)
BIOMES = {
    "mycotic_forest": ("Mycotic Forest", "#c9a0e0", "#e2c4ee", "#c66bb0", "#b85aa6", "#7ab0d8",
                       ["eden_mycelium_ground"], ["eden_mycotic_plants", "eden_balloon_mushrooms", "eden_old_balloon_mushroom_rare", "eden_vines", "eden_mold", "eden_limphium", "eden_parignum"],
                       ["minecraft:glow_lichen", "minecraft:brown_mushroom_normal", "minecraft:red_mushroom_normal"]),
    "old_mycotic_forest": ("Old Mycotic Forest", "#b890d8", "#d4b4e6", "#b85aa6", "#a04a96", "#6aa0d0",
                           ["eden_mycelium_ground"], ["eden_mycotic_plants", "eden_balloon_mushrooms_sparse", "eden_old_balloon_mushrooms", "eden_vines", "eden_mold", "eden_limphium",
                            "eden_parignum"],
                           ["minecraft:glow_lichen", "minecraft:brown_mushroom_normal", "minecraft:red_mushroom_normal"]),
    "pulse_forest": ("Pulse Forest", "#7fd8e0", "#a8ecf0", "#3fc6b0", "#35b0a8", "#2fb8d8",
                     [], ["eden_pulse_trees", "eden_violum", "eden_lonlix"],
                     ["minecraft:glow_lichen", "minecraft:patch_tall_grass_2", "minecraft:patch_grass_plain", "minecraft:patch_firefly_bush_near_water"]),
    "stone_garden": ("Stone Garden", "#9fdcb0", "#c4ecc8", "#6fb85a", "#5fa84e", "#3fc0a8",
                     ["eden_stone_ground", "eden_stone_pillar", "eden_stone_boulder"], ["eden_vines", "eden_limphium", "eden_violum", "eden_moss"],
                     ["minecraft:glow_lichen", "minecraft:patch_grass_plain"]),
    "brainstorm": ("Brainstorm", "#5f6f80", "#7f8c98", "#7a9a6a", "#6a8a5e", "#4a7a90",
                   [], ["eden_brain_trees", "eden_metal_grass", "eden_mold", "eden_parignum"],
                   ["minecraft:glow_lichen", "minecraft:patch_grass_plain"]),
    "lakeside_desert": ("Lakeside Desert", "#a8dcc0", "#d8ecc8", "#b8c86a", "#a8b85e", "#3fc8b8",
                        ["eden_sand_ground", "eden_pond"], ["eden_lonlix"],
                        ["minecraft:glow_lichen", "minecraft:patch_sugar_cane"]),
    "wind_valley": ("Wind Valley", "#9fe8c0", "#c8f4d8", "#7fd86a", "#6fc85e", "#3fd0a0",
                    [], ["eden_violum", "eden_lonlix"],
                    ["minecraft:glow_lichen", "minecraft:patch_tall_grass_2", "minecraft:flower_plains", "minecraft:patch_grass_plain"]),
}

FULL = [-2.0, 2.0]
# biome: [(temperature, humidity)]; the boxes tile the whole temperature/humidity plane, so every place is one of them
LAYOUT = {
    # dry: the stone garden in the cold, the lakeside desert in the heat, the wind valley between
    "stone_garden": [((-2.0, -0.3), (-2.0, -0.3))],
    "wind_valley": [((-0.3, 0.25), (-2.0, -0.3))],
    "lakeside_desert": [((0.25, 2.0), (-2.0, -0.3))],
    # middling: the plains, and the brainstorm in the cold
    "brainstorm": [((-2.0, -0.4), (-0.3, 0.12))],
    "verdant_plains": [((-0.4, 2.0), (-0.3, 0.12))],
    # wet: the mycotic forests, and the pulse forest in the heat
    "mycotic_forest": [((-2.0, 0.15), (0.12, 0.42))],
    "old_mycotic_forest": [((-2.0, 0.15), (0.42, 2.0))],
    "pulse_forest": [((0.15, 2.0), (0.12, 2.0))],
}


def worldgen():
    for name, (kind, spec) in FEATURES.items():
        write(f"{DATA}/worldgen/feature/{name}.json", {"type": f"{NS}:eden", "kind": kind})
        write(f"{DATA}/worldgen/placed_feature/{name}.json", {"feature": f"{NS}:{name}", "placement": placement(spec)})
    for name, kind in TREES.items():
        write(f"{DATA}/worldgen/feature/{name}.json", {"type": f"{NS}:eden", "kind": kind})

    # gravilite grows in every country, the plains too (their file is otherwise hand-kept)
    plains_path = f"{DATA}/worldgen/biome/verdant_plains.json"
    plains = json.load(open(plains_path))
    if f"{NS}:eden_gravilite" not in plains["features"][9]:
        plains["features"][9].insert(0, f"{NS}:eden_gravilite")
        write(plains_path, plains)
    plains["features"][9].remove(f"{NS}:eden_gravilite")
    for name, (display, sky, fog, grass, foliage, water, ground, ours, kept) in BIOMES.items():
        biome = copy.deepcopy(plains)
        biome["attributes"]["minecraft:visual/sky_color"] = sky
        biome["attributes"]["minecraft:visual/fog_color"] = fog
        biome["effects"].update({"grass_color": grass, "foliage_color": foliage, "water_color": water})
        steps = biome["features"]
        # our features go first in their step, always in FEATURES' order; vanilla's keep the order the plains have them in
        order = list(FEATURES)
        steps[2] = [f"{NS}:{f}" for f in sorted(ground, key=order.index)] + steps[2]
        steps[9] = [f"{NS}:{f}" for f in sorted(ours + ["eden_gravilite"], key=order.index)] + [f for f in steps[9] if f in kept]
        write(f"{DATA}/worldgen/biome/{name}.json", biome)
        LANG[f"biome.{NS}.{name}"] = display
    LANG[f"biome.{NS}.verdant_plains"] = "Verdant Plains"
    write(f"{DATA}/tags/worldgen/biome/eden_mycelium_ground.json", {"values": [f"{NS}:mycotic_forest", f"{NS}:old_mycotic_forest"]})
    write(f"{DATA}/tags/worldgen/biome/eden_stone_ground.json", {"values": [f"{NS}:stone_garden"]})
    write(f"{DATA}/tags/worldgen/biome/eden_sand_ground.json", {"values": [f"{NS}:lakeside_desert"]})
    write(f"{DATA}/tags/worldgen/biome/eden_windy.json", {"values": [f"{NS}:wind_valley"]})

    entries = []
    for name, boxes in LAYOUT.items():
        for temperature, humidity in boxes:
            entries.append({"biome": f"{NS}:{name}", "parameters": {
                "temperature": list(temperature), "humidity": list(humidity), "continentalness": FULL, "erosion": FULL, "weirdness": FULL,
                "depth": FULL, "offset": 0.0}})
    write(f"{DATA}/dimension/verdant_world.json", {"type": f"{NS}:verdant_world", "generator": {
        "type": "minecraft:noise", "biome_source": {"type": "minecraft:multi_noise", "biomes": entries}, "settings": "minecraft:overworld"}})


def main():
    blocks()
    shaped_wood()
    gravilite()
    small_plants()
    diskwing()
    recipes_and_tags()
    worldgen()
    write(f"{ASSETS}/lang/parts/eden.json", LANG)
    print(f"eden: {len(LANG)} names")


main()
