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
    "eden_stone_pillar": ("stone_pillar", ["rare:3", "surface"]),
    "eden_stone_boulder": ("stone_boulder", ["count:2", "surface"]),
    "eden_balloon_mushrooms": ("balloon_mushroom", ["count:5", "surface"]),
    "eden_balloon_mushrooms_sparse": ("balloon_mushroom", ["count:2", "surface"]),
    "eden_old_balloon_mushroom_rare": ("old_balloon_mushroom", ["rare:6", "surface"]),
    "eden_old_balloon_mushrooms": ("old_balloon_mushroom", ["count:3", "surface"]),
    "eden_pulse_trees": ("pulse_tree", ["count:8", "surface"]),
    # (after the trees: a plant on the turf would lift the surface a tree looks for)
    "eden_mycotic_plants": ("mycotic_plants", ["count:3", "surface"]),
}
# the saplings' trees
TREES = {"balloon_mushroom": "balloon_mushroom", "pulse_tree": "pulse_tree"}


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
                       ["eden_mycelium_ground"], ["eden_mycotic_plants", "eden_balloon_mushrooms", "eden_old_balloon_mushroom_rare"],
                       ["minecraft:glow_lichen", "minecraft:brown_mushroom_normal", "minecraft:red_mushroom_normal"]),
    "old_mycotic_forest": ("Old Mycotic Forest", "#b890d8", "#d4b4e6", "#b85aa6", "#a04a96", "#6aa0d0",
                           ["eden_mycelium_ground"], ["eden_mycotic_plants", "eden_balloon_mushrooms_sparse", "eden_old_balloon_mushrooms"],
                           ["minecraft:glow_lichen", "minecraft:brown_mushroom_normal", "minecraft:red_mushroom_normal"]),
    "pulse_forest": ("Pulse Forest", "#7fd8e0", "#a8ecf0", "#3fc6b0", "#35b0a8", "#2fb8d8",
                     [], ["eden_pulse_trees"],
                     ["minecraft:glow_lichen", "minecraft:patch_tall_grass_2", "minecraft:patch_grass_plain", "minecraft:patch_firefly_bush_near_water"]),
    "stone_garden": ("Stone Garden", "#9fdcb0", "#c4ecc8", "#6fb85a", "#5fa84e", "#3fc0a8",
                     ["eden_stone_ground", "eden_stone_pillar", "eden_stone_boulder"], [],
                     ["minecraft:glow_lichen", "minecraft:patch_grass_plain"]),
}

FULL = [-2.0, 2.0]
# biome: [(temperature, humidity)]; the boxes tile the whole temperature/humidity plane, so every place is one of them
LAYOUT = {
    "stone_garden": [((-2.0, -0.2), (-2.0, -0.3))],
    "verdant_plains": [((-0.2, 2.0), (-2.0, -0.3)), ((-2.0, 2.0), (-0.3, 0.12))],
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

    plains = json.load(open(f"{DATA}/worldgen/biome/verdant_plains.json"))
    for name, (display, sky, fog, grass, foliage, water, ground, ours, kept) in BIOMES.items():
        biome = copy.deepcopy(plains)
        biome["attributes"]["minecraft:visual/sky_color"] = sky
        biome["attributes"]["minecraft:visual/fog_color"] = fog
        biome["effects"].update({"grass_color": grass, "foliage_color": foliage, "water_color": water})
        steps = biome["features"]
        # our features go first in their step, always in FEATURES' order; vanilla's keep the order the plains have them in
        order = list(FEATURES)
        steps[2] = [f"{NS}:{f}" for f in sorted(ground, key=order.index)] + steps[2]
        steps[9] = [f"{NS}:{f}" for f in sorted(ours, key=order.index)] + [f for f in steps[9] if f in kept]
        write(f"{DATA}/worldgen/biome/{name}.json", biome)
        LANG[f"biome.{NS}.{name}"] = display
    LANG[f"biome.{NS}.verdant_plains"] = "Verdant Plains"
    write(f"{DATA}/tags/worldgen/biome/eden_mycelium_ground.json", {"values": [f"{NS}:mycotic_forest", f"{NS}:old_mycotic_forest"]})
    write(f"{DATA}/tags/worldgen/biome/eden_stone_ground.json", {"values": [f"{NS}:stone_garden"]})

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
    recipes_and_tags()
    worldgen()
    write(f"{ASSETS}/lang/parts/eden.json", LANG)
    print(f"eden: {len(LANG)} names")


main()
