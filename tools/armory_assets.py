"""Writes the Armory's assets and data: the weapons rebuilt from Age of Weapons - Reforged (by XxRexRaptorxX, MIT).

Its textures (and the hand poses of its item models) are reused under its MIT license; see
assets/jaymospowers/textures/item/armory/LICENSE-AgeOfWeapons.txt. Point it at the unpacked mod jar (the folder that
holds assets/ageofweapons):

    python -I tools/armory_assets.py <unpacked AgeOfWeapons jar>

The weapon list must match com.jaymospowers.armory.Armory (same ids).
"""
import json
import os
import shutil
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "jaymospowers")
DATA = os.path.join(RES, "data", "jaymospowers")
MC_DATA = os.path.join(RES, "data", "minecraft")
NS = "jaymospowers"

# our id prefix, AoW's texture suffix, the name prefix, the crafting material
TIERS = [
    ("wooden", "wood", "Wooden", "#minecraft:wooden_tool_materials"),
    ("stone", "stone", "Stone", "#minecraft:stone_tool_materials"),
    ("iron", "iron", "Iron", "#minecraft:iron_tool_materials"),
    ("golden", "gold", "Golden", "#minecraft:gold_tool_materials"),
    ("diamond", "diamond", "Diamond", "#minecraft:diamond_tool_materials"),
    ("netherite", "netherite", "Netherite", None),  # smithing: diamond one + netherite ingot
]

KEYS = {"S": "minecraft:stick", "L": "minecraft:leather", "C": "minecraft:string", "B": "minecraft:bone",
        "F": "minecraft:flint", "I": "minecraft:iron_ingot", "N": "minecraft:iron_nugget", "W": "#minecraft:logs"}

# weapon id, name, AoW folder/name, Better Combat preset (None: no attributes; jaymospowers:combo_* are our own, from
# tools/combat_animations.py), vanilla tag, recipe (X = the tier's material)
TIERED = [
    ("dagger", "Dagger", "antiquity/dagger", "jaymospowers:combo_dagger", "swords", ["X", "L"]),
    ("hatchet", "Hatchet", "antiquity/hatchet", "jaymospowers:combo_hatchet", "axes", ["XX", "SL"]),
    ("skewer", "Skewer", "antiquity/skewer", "jaymospowers:combo_spear", "swords", ["  X", " S ", "L  "]),
    ("round_shield", "Round Shield", "middle_ages/shield", None, None, ["XLX", "XXX", " X "]),
    ("longsword", "Longsword", "middle_ages/longsword", "jaymospowers:combo_longsword", "swords", [" X ", " X ", "LSL"]),
    ("glaive", "Glaive", "middle_ages/glaive", "jaymospowers:combo_glaive", "swords", [" XX", " S ", "S  "]),
    ("lance", "Lance", "middle_ages/lance", "lance", "swords", ["  X", " S ", "SL "]),
    ("war_axe", "War Axe", "middle_ages/waraxe", "jaymospowers:combo_war_axe", "axes", ["XSX", " S ", " S "]),
    ("war_scythe", "War Scythe", "middle_ages/scythe", "jaymospowers:combo_war_scythe", "swords", ["XXS", "  S", "  S"]),
    ("flanged_mace", "Flanged Mace", "middle_ages/heavy_staff", "jaymospowers:combo_mace", "swords", [" XX", " XX", "S  "]),
    ("broadsword", "Broadsword", "fantasy/broadsword", "jaymospowers:combo_broadsword", "swords", [" X ", " X ", "XSX"]),
    ("claymore", "Claymore", "fantasy/claymore", "jaymospowers:combo_claymore", "swords", [" X ", "XXX", "LSL"]),
    ("cleaver", "Cleaver", "fantasy/cleaver", "jaymospowers:combo_cleaver", "swords", ["XX", "XX", " S"]),
    ("ninjato", "Ninjato", "edo_period/ninjato", "jaymospowers:combo_ninjato", "swords", ["  X", " X ", "SC "]),
    ("saber", "Saber", "piracy/saber", "jaymospowers:combo_saber", "swords", ["  X", " X ", "LS "]),
    ("machete", "Machete", "piracy/machete", "jaymospowers:combo_machete", "swords", [" X", " X", "SL"]),
]

# item id, name, AoW texture, Better Combat preset, vanilla tag, recipe, recipe count
SINGLES = [
    ("primitive_spear", "Primitive Spear", "stone_age/caveman_spear", "jaymospowers:combo_spear", "swords", ["  F", " C ", "S  "], 1),
    ("primitive_knife", "Primitive Knife", "stone_age/caveman_knife", "jaymospowers:combo_dagger", "swords", ["F", "S"], 1),
    ("bone_knife", "Bone Knife", "stone_age/bone_knife", "jaymospowers:combo_dagger", "swords", ["B", "S"], 1),
    ("hand_axe", "Hand Axe", "stone_age/hand_ax", "jaymospowers:combo_hatchet", "axes", ["FF", "FS"], 1),
    ("wooden_cudgel", "Wooden Cudgel", "stone_age/cudgel_wood", "jaymospowers:combo_mace", "swords", ["W", "S"], 1),
    ("stone_cudgel", "Stone Cudgel", "stone_age/cudgel_stone", "jaymospowers:combo_mace", "swords", ["XX", "XS"], 1),
    ("throwing_stick", "Throwing Stick", "stone_age/throw_wood", "mace", "swords", ["S ", " S"], 1),
    ("wooden_quarterstaff", "Wooden Quarterstaff", "basic/longstick_wood", "jaymospowers:combo_quarterstaff", "swords", ["  S", " S ", "S  "], 1),
    ("iron_quarterstaff", "Iron Quarterstaff", "basic/longstick_iron", "jaymospowers:combo_quarterstaff", "swords", ["  I", " S ", "I  "], 1),
    ("harpoon", "Harpoon", "piracy/harpoon", "trident", "swords", ["  I", " S ", "SC "], 1),
    ("kunai", "Kunai", "edo_period/kunai", "jaymospowers:combo_dagger", None, ["I", "C"], 4),
    ("throwing_star", "Throwing Star", "edo_period/throwing_star", None, None, [" N ", "N N", " N "], 2),
]
SINGLE_KEYS = {"stone_cudgel": {"X": "#minecraft:stone_tool_materials"}}

ARMOR = [
    ("caveman_helmet", "Caveman Hood", "head_armor", ["LBL", "L L"]),
    ("caveman_chestplate", "Caveman Tunic", "chest_armor", ["L L", "LBL", "LLL"]),
    ("caveman_leggings", "Caveman Pants", "leg_armor", ["LBL", "L L", "L L"]),
    ("caveman_boots", "Caveman Boots", "foot_armor", ["L L", "B B"]),
]

NOTICE = """The textures in this folder (and the hand poses of the Armory's item models) come from
Age of Weapons - Reforged by XxRexRaptorxX (RexRaptor), https://www.curseforge.com/minecraft/mc-mods/age-of-weapons,
released under the MIT License:

Copyright (c) XxRexRaptorxX

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit
persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the
Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
"""


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", newline="\n") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def parent_of(preset):
    """A weapon's Better Combat parent: one of ours if it names a namespace, else Better Combat's own preset."""
    return preset if ":" in preset else f"bettercombat:{preset}"


def model_of(aow, name):
    """AoW's own item model for `name` (its hand poses), or None."""
    path = os.path.join(aow, "assets", "ageofweapons", "models", "item", name + ".json")
    if not os.path.exists(path):
        return None
    with open(path) as f:
        return json.load(f)


def copy_texture(aow, src, dest):
    path = os.path.join(aow, "assets", "ageofweapons", "textures", src + ".png")
    out = os.path.join(ASSETS, "textures", dest + ".png")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    shutil.copyfile(path, out)


# quarterstaffs are held by the middle (the grip sits on the stick's centre, not near its end) and drawn longer, so its
# twirls in tools/combat_animations.py spin round the middle
STAFF_DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 0, 0.5], "scale": [1.6, 1.6, 1.0]},
    "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 0, 0.5], "scale": [1.6, 1.6, 1.0]},
}


def handheld(aow, item, aow_model, parent="minecraft:item/handheld"):
    """A handheld model using the copied texture, keeping AoW's hand poses (its big two-handers are drawn 2x)."""
    src = model_of(aow, aow_model) or {}
    model = {"parent": parent, "textures": {"layer0": f"{NS}:item/armory/{item}"}}
    if "display" in src:
        model["display"] = src["display"]
    if item.endswith("quarterstaff"):
        model["display"] = STAFF_DISPLAY
    write(os.path.join(ASSETS, "models", "item", "armory", item + ".json"), model)
    write(os.path.join(ASSETS, "items", item + ".json"),
          {"model": {"type": "minecraft:model", "model": f"{NS}:item/armory/{item}"}})


SHIELD_DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, 90, 0], "translation": [10, 6, -4]},
    "thirdperson_lefthand": {"rotation": [0, 90, 0], "translation": [10.5, 6, 12]},
    "firstperson_righthand": {"rotation": [0, 180, 5], "translation": [-10, 4, -10], "scale": [1.25, 1.25, 1.25]},
    "firstperson_lefthand": {"rotation": [0, 180, 5], "translation": [10, 2.25, -10], "scale": [1.25, 1.25, 1.25]},
    "ground": {"translation": [4, 4.5, 3.5], "scale": [0.5, 0.5, 0.5]},
    "gui": {"rotation": [15, -25, -5], "translation": [-0.5, 1, 0], "scale": [0.65, 0.65, 0.65]},
    "fixed": {"rotation": [0, 180, 0], "translation": [-7.25, 7.25, -8], "scale": [0.9, 0.9, 0.9]},
}
SHIELD_BLOCKING = {
    "thirdperson_righthand": {"rotation": [45, 155, 0], "translation": [-3.5, 11, -2]},
    "thirdperson_lefthand": {"rotation": [45, 155, 0], "translation": [11.5, 7, 2.5]},
    "firstperson_righthand": {"rotation": [0, 180, -5], "translation": [-15, 5, -11], "scale": [1.25, 1.25, 1.25]},
    "firstperson_lefthand": {"rotation": [0, 180, -5], "translation": [5, 5, -11], "scale": [1.25, 1.25, 1.25]},
    "gui": SHIELD_DISPLAY["gui"],
}


def shield(item):
    """The round shield: a 12x12 boss of a disc with a grip behind, laid out on AoW's 64x64 shield sheet."""
    tex = f"{NS}:item/armory/{item}"
    elements = [
        {"from": [-6, -6, 1], "to": [6, 6, 2], "faces": {
            "north": {"uv": [3.5, 13, 6.5, 16], "texture": "#t"}, "south": {"uv": [0.25, 13, 3.25, 16], "texture": "#t"},
            "east": {"uv": [3.25, 13, 3.5, 16], "texture": "#t"}, "west": {"uv": [0, 13, 0.25, 16], "texture": "#t"},
            "up": {"uv": [0.25, 12.75, 3.25, 13], "texture": "#t"}, "down": {"uv": [3.25, 12.75, 6.25, 13], "texture": "#t"}}},
        {"from": [-1, -3, -5], "to": [1, 3, 1], "faces": {
            "north": {"uv": [10, 1.5, 10.5, 3], "texture": "#t"}, "south": {"uv": [8, 1.5, 8.5, 3], "texture": "#t"},
            "east": {"uv": [8.5, 1.5, 10, 3], "texture": "#t"}, "west": {"uv": [6.5, 1.5, 8, 3], "texture": "#t"},
            "up": {"uv": [8, 0, 8.5, 1.5], "texture": "#t"}, "down": {"uv": [8.5, 0, 9, 1.5], "texture": "#t"}}},
    ]
    base = {"gui_light": "front", "textures": {"t": tex, "particle": tex}, "elements": elements}
    write(os.path.join(ASSETS, "models", "item", "armory", item + ".json"), dict(base, display=SHIELD_DISPLAY))
    write(os.path.join(ASSETS, "models", "item", "armory", item + "_blocking.json"), dict(base, display=SHIELD_BLOCKING))
    write(os.path.join(ASSETS, "items", item + ".json"), {"model": {
        "type": "minecraft:condition", "property": "minecraft:using_item",
        "on_false": {"type": "minecraft:model", "model": f"{NS}:item/armory/{item}"},
        "on_true": {"type": "minecraft:model", "model": f"{NS}:item/armory/{item}_blocking"}}})


def bow(aow):
    item = "recurve_bow"
    src = model_of(aow, "recurve_bow")
    for i, stage in enumerate(["standby", "pulling_0", "pulling_1", "pulling_2"]):
        name = item if i == 0 else f"{item}_{stage}"
        copy_texture(aow, f"item/antiquity/recurve_bow_{stage}", f"item/armory/{name}")
        model = {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/armory/{name}"},
                 "display": src["display"]}
        write(os.path.join(ASSETS, "models", "item", "armory", name + ".json"), model)
    m = lambda n: {"type": "minecraft:model", "model": f"{NS}:item/armory/{n}"}
    write(os.path.join(ASSETS, "items", item + ".json"), {"model": {
        "type": "minecraft:condition", "property": "minecraft:using_item",
        "on_false": m(item),
        "on_true": {"type": "minecraft:range_dispatch", "property": "minecraft:use_duration", "scale": 0.05,
                    "entries": [{"threshold": 0.65, "model": m(item + "_pulling_1")},
                                {"threshold": 0.9, "model": m(item + "_pulling_2")}],
                    "fallback": m(item + "_pulling_0")}}})


def shaped(item, pattern, keys, count=1):
    used = {c for row in pattern for c in row if c != " "}
    recipe = {"type": "minecraft:crafting_shaped", "category": "equipment",
              "key": {k: v for k, v in keys.items() if k in used}, "pattern": pattern,
              "result": {"id": f"{NS}:{item}"}}
    if count > 1:
        recipe["result"]["count"] = count
    write(os.path.join(DATA, "recipe", "armory", item + ".json"), recipe)


def main(aow):
    lang = {"itemGroup.jaymospowers.armory": "Jaymo's Armory"}
    tags = {"swords": [], "axes": [], "head_armor": [], "chest_armor": [], "leg_armor": [], "foot_armor": []}
    durability = []
    for weapon, name, src, preset, tag, pattern in TIERED:
        for tier, suffix, prefix, material in TIERS:
            item = f"{tier}_{weapon}"
            lang[f"item.{NS}.{item}"] = f"{prefix} {name}"
            copy_texture(aow, f"item/{src}_{suffix}", f"item/armory/{item}")
            if weapon == "round_shield":
                shield(item)
                durability.append(f"{NS}:{item}")
            else:
                handheld(aow, item, f"{src.split('/')[1]}_{suffix}")
            if preset:
                write(os.path.join(DATA, "weapon_attributes", item + ".json"), {"parent": parent_of(preset)})
            if tag:
                tags[tag].append(f"{NS}:{item}")
            if tier == "diamond":
                pass  # (0.8.124) Andy: no craftable diamond weapons; the netherite ones still smith from loot
            elif material:
                shaped(item, pattern, dict(KEYS, X=material))
            else:
                write(os.path.join(DATA, "recipe", "armory", item + "_smithing.json"), {
                    "type": "minecraft:smithing_transform", "addition": "#minecraft:netherite_tool_materials",
                    "base": f"{NS}:diamond_{weapon}", "result": {"id": f"{NS}:{item}"},
                    "template": "minecraft:netherite_upgrade_smithing_template"})
    for item, name, src, preset, tag, pattern, count in SINGLES:
        lang[f"item.{NS}.{item}"] = name
        copy_texture(aow, f"item/{src}", f"item/armory/{item}")
        handheld(aow, item, src.split("/")[1])
        if preset:
            write(os.path.join(DATA, "weapon_attributes", item + ".json"), {"parent": parent_of(preset)})
        if tag:
            tags[tag].append(f"{NS}:{item}")
        shaped(item, pattern, dict(KEYS, **SINGLE_KEYS.get(item, {})), count)
    bow(aow)
    lang[f"item.{NS}.recurve_bow"] = "Recurve Bow"
    shaped("recurve_bow", [" BC", "S C", " BC"], KEYS)
    for item, name, tag, pattern in ARMOR:
        lang[f"item.{NS}.{item}"] = name
        copy_texture(aow, f"item/stone_age/{item}", f"item/armory/{item}")
        handheld(aow, item, item, parent="minecraft:item/generated")
        tags[tag].append(f"{NS}:{item}")
        shaped(item, pattern, KEYS)
    copy_texture(aow, "models/armor/caveman_layer_1", "entity/equipment/humanoid/caveman")
    copy_texture(aow, "models/armor/caveman_layer_2", "entity/equipment/humanoid_leggings/caveman")
    write(os.path.join(ASSETS, "equipment", "caveman.json"), {"layers": {
        "humanoid": [{"texture": f"{NS}:caveman"}], "humanoid_leggings": [{"texture": f"{NS}:caveman"}]}})
    lang["entity.jaymospowers.thrown_weapon"] = "Thrown Weapon"
    lang["item.jaymospowers.lance.charge"] = "Couched on horseback, hits harder the faster you ride"
    for tag, values in tags.items():
        write(os.path.join(MC_DATA, "tags", "item", tag + ".json"), {"values": values})
    write(os.path.join(MC_DATA, "tags", "item", "enchantable", "bow.json"), {"values": [f"{NS}:recurve_bow"]})
    write(os.path.join(MC_DATA, "tags", "item", "enchantable", "durability.json"), {"values": durability})
    write(os.path.join(ASSETS, "lang", "parts", "armory.json"), lang)
    with open(os.path.join(ASSETS, "textures", "item", "armory", "LICENSE-AgeOfWeapons.txt"), "w", newline="\n") as f:
        f.write(NOTICE)


if __name__ == "__main__":
    main(sys.argv[1])
    # (0.9.0) our own short cleavers and long quarterstaffs over the copied sprites
    import gen_cleaver_staff
    gen_cleaver_staff.main()
