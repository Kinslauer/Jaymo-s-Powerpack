#!/usr/bin/env python3
"""Writes the class item tags, data/jaymoskingdoms/tags/item/class/<class>.json (Andy, 2026-11-25: "all items related to
classes should get some type of tag so typing the class name in jei gets you all the related items").

Each class's tag holds its casting focus, the weapons it is trained with (ClassPerks.trainedAs), the armour made for it,
and the things only it uses. The JEI plugin (client/compat/ClassJei) gives every item of a class's tag the class name as
a search alias, so typing "cleric" in JEI lists them all; "$class/cleric" finds the tag itself.

Our own items (jaymoskingdoms:) must exist; everything else (vanilla too) is optional, so a missing mod drops out quietly. The
scrolls follow ClassCasters.ALLOWED (a Mage learns every scroll). Run it again after changing a list:
    python3 kingdoms/tools/class_item_tags.py
"""
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources")
OUT = os.path.join(RES, "data", "jaymoskingdoms", "tags", "item", "class")
OURS = os.path.join(RES, "assets", "jaymoskingdoms", "items")
CASTERS = os.path.join(ROOT, "src", "main", "java", "com", "jaymoskingdoms", "ClassCasters.java")

PIECES = ("helmet", "chestplate", "leggings", "boots")
TIERS = ("wooden", "stone", "iron", "golden", "diamond", "netherite")


def set_of(prefix, ns="jaymoskingdoms", pieces=PIECES):
    return [f"{ns}:{prefix}_{p}" for p in pieces]


def rpg_armour(ns, name):
    return [f"{ns}:{name}_{p}" for p in ("head", "chest", "legs", "feet")]


def allowed_spells():
    """ClassCasters.ALLOWED, read from the source so the scrolls keep up with it."""
    src = open(CASTERS, encoding="utf-8").read()
    out = {}
    for cls, body in re.findall(r"ALLOWED\.put\(PlayerClass\.(\w+), new HashSet<>\(Set\.of\((.*?)\)\)\);", src, re.S):
        names = re.findall(r'"([a-z_]+)"', body)
        if "REVIVE" in body:
            names.append("calling_back")
        if "GreenReturn.SPELL" in body:
            names.append("green_return")
        out[cls.lower()] = names
    return out


ALL_SCROLLS = sorted(f"jaymoskingdoms:{f[:-5]}" for f in os.listdir(OURS) if f.startswith("scroll_of_"))


def scrolls(spells):
    have = set(ALL_SCROLLS)
    out = []
    for spell in spells:
        sid = "jaymoskingdoms:scroll_of_" + ("arcane_bolt" if spell == "arcane" else spell)
        if sid in have:
            out.append(sid)
    return sorted(out)


TOMES = [f"jaymoskingdoms:{e}_tome" for e in ("water", "fire", "earth", "air", "lightning", "ice", "restoration", "death", "blood")]

SPELLS = allowed_spells()

CLASSES = {
    # light blades, lock picks, the knife school, the scoundrel's wrappings and the Rogues' kit
    "thief": ["#jaymoskingdoms:light_blades", "jaymoskingdoms:hidden_blade", "jaymoskingdoms:lockpick", "jaymoskingdoms:school_mask_knife",
              *set_of("scoundrel"), *rpg_armour("rogues", "rogue_armor"), *rpg_armour("rogues", "assassin_armor"),
              *rpg_armour("rogues", "netherite_assassin_armor"),
              *[f"rogues:{t}_dagger" for t in ("flint", "iron", "golden", "diamond", "netherite", "ruby", "aether", "aeternium")],
              *[f"rogues:{t}_sickle" for t in ("iron", "golden", "diamond", "netherite", "ruby", "aether", "aeternium")]],
    # two-handed weapons, the war horn, the raider chief's furs, the blade school and the Warriors' kit
    "warlord": ["#jaymoskingdoms:two_handed", "jaymoskingdoms:war_horn", *set_of("warlord"), "jaymoskingdoms:school_mask_blade",
                *rpg_armour("rogues", "warrior_armor"),
                *[f"rogues:{t}_double_axe" for t in ("stone", "iron", "golden", "diamond", "netherite", "ruby", "aether", "aeternium")],
                *[f"rogues:{t}_glaive" for t in ("iron", "golden", "diamond", "netherite", "ruby", "aether", "aeternium")]],
    # bludgeons, the fist school and the berserker's kit
    "brute": ["#jaymoskingdoms:bludgeons", "jaymoskingdoms:school_mask_fist", *rpg_armour("rogues", "berserker_armor"),
              *rpg_armour("rogues", "netherite_berserker_armor")],
    # guns and their shot, crossbows, and the musketeer's Ranger harness (guns load quicker in it)
    "marksman": ["#jaymoskingdoms:guns", "weaponmod:bullet", "weaponmod:cannonball", "minecraft:crossbow", "#minecraft:enchantable/crossbow",
                 "jaymospowers:heavy_crossbow", "jaymospowers:tinker_crossbow",
                 *[f"archers:{t}" for t in ("rapid_crossbow", "heavy_crossbow", "ruby_rapid_crossbow", "ruby_heavy_crossbow",
                                            "netherite_rapid_crossbow", "netherite_heavy_crossbow", "aether_rapid_crossbow", "aether_heavy_crossbow")],
                 *set_of("ranger"), *set_of("ranger_steel")],
    # bows, crossbows, arrows and quivers, axes, the net, the Archers' kit and the Ranger's harness
    "ranger": ["minecraft:bow", "#minecraft:enchantable/bow", "minecraft:crossbow", "#minecraft:enchantable/crossbow", "#minecraft:arrows",
               "#minecraft:axes", "jaymospowers:hand_axe", "jaymospowers:tinker_hand_axe", "jaymospowers:tinker_longbow", "jaymospowers:recurve_bow",
               "jaymospowers:echo_bow", "jaymoskingdoms:tattered_quiver", "jaymoskingdoms:net", "jaymoskingdoms:concussive_arrow",
               "jaymospowers:broadhead_arrow", "jaymospowers:headed_arrow", "jaymospowers:iron_arrow", "jaymospowers:steel_arrow",
               "jaymospowers:tinker_arrow", "archers:small_quiver", "archers:medium_quiver", "archers:large_quiver",
               *[f"archers:{t}" for t in ("composite_longbow", "crystal_longbow", "crystal_shortbow", "mechanic_shortbow", "royal_longbow",
                                          "netherite_longbow", "netherite_shortbow", "aether_longbow")],
               *rpg_armour("archers", "archer_armor"), *rpg_armour("archers", "ranger_armor"), *rpg_armour("archers", "netherite_ranger_armor"),
               *set_of("ranger"), *set_of("ranger_steel")],
    # the horse's tack, lances, the spear school
    "rider": ["minecraft:saddle", "jaymoskingdoms:bridle", "jaymoskingdoms:war_saddle", "jaymoskingdoms:racing_saddle",
              "jaymoskingdoms:chain_barding", "jaymoskingdoms:plate_barding", "jaymoskingdoms:horse_blanket", "jaymoskingdoms:horseshoes",
              "jaymoskingdoms:hoof_pick", "jaymoskingdoms:horse_medkit", "jaymoskingdoms:horse_handbook", "jaymoskingdoms:glide_harness",
              "jaymoskingdoms:oats", "jaymoskingdoms:school_mask_spear",
              *[f"minecraft:{t}_horse_armor" for t in ("leather", "copper", "iron", "golden", "diamond", "netherite")],
              *[f"jaymospowers:{t}_lance" for t in TIERS]],
    # the holy symbol (a Truth Cleric's blank book), the patron's book, the healer's staff, bludgeons and the prayers
    "cleric": ["jaymoskingdoms:holy_symbol", "minecraft:book", "minecraft:writable_book", "jaymoskingdoms:commandments",
               "jaymoskingdoms:healer_staff", "#jaymoskingdoms:bludgeons", *scrolls(SPELLS["cleric"])],
    # the holy symbol, the vow book, swords and bludgeons, the knights' plate
    "paladin": ["jaymoskingdoms:holy_symbol", "jaymoskingdoms:vow_book", "#minecraft:swords", "#jaymoskingdoms:bludgeons", "minecraft:shield",
                *set_of("paladin"), *set_of("reverent"), *set_of("templar"), *set_of("templar_steel")],
    # the druid's sprig and nature's rites
    "druid": ["jaymoskingdoms:druid_sprig", *scrolls(SPELLS["druid"])],
    # the pact seal and the pact's spells
    "warlock": ["jaymoskingdoms:pact_seal", *scrolls(SPELLS["warlock"])],
    # the element tomes and the blades a Sorcerer fights with
    "sorcerer": [*TOMES, "#minecraft:swords", "#jaymoskingdoms:light_blades"],
    # staffs, tomes, wands, every scroll, the hedge mage's garb and the Wizards' kit
    "mage": ["jaymoskingdoms:wizard_staff", "jaymoskingdoms:bone_staff", "jaymoskingdoms:totem_staff", "jaymoskingdoms:mana_wand", *TOMES,
             *set_of("hedge"), *set_of("hedge_elite"), *set_of("hedge_ashen"), *ALL_SCROLLS,
             *[f"wizards:{t}" for t in ("wand_novice", "wand_arcane", "wand_fire", "wand_frost", "wand_netherite_arcane", "wand_netherite_fire",
                                        "wand_netherite_frost", "staff_wizard", "staff_arcane", "staff_fire", "staff_frost", "staff_crystal_arcane",
                                        "staff_ruby_fire", "staff_smaragdant_frost", "staff_netherite_arcane", "staff_netherite_fire",
                                        "staff_netherite_frost", "aether_wizard_staff")],
             *rpg_armour("wizards", "wizard_robe"),
             *[x for k in ("arcane", "fire", "frost") for x in rpg_armour("wizards", f"{k}_robe") + rpg_armour("wizards", f"netherite_{k}_robe")]],
    # a boxer's fist gear
    "monk": ["#jaymospowers:boxing_gear", "jaymospowers:hand_wraps", "jaymospowers:leather_cestus", "jaymospowers:iron_cestus",
             "jaymospowers:steel_cestus", "jaymospowers:leather_bracers", "jaymospowers:iron_bracers", "jaymospowers:steel_bracers",
             "jaymospowers:iron_knuckles"],
    # the stations they infuse at, the gadgets they learn (Tinkerers.GADGETS) and the ones that mimic powers (tinker/Mimic)
    "tinkerer": [*[f"jaymospowers:{s}" for s in ("tool_station", "tool_forge", "part_builder", "crafting_station", "rig_forge", "star_forge")],
                 *[f"jaymoskingdoms:{g}" for g in ("immovable_rod", "climbing_rope", "endless_jug", "floating_light", "smoke_bottle",
                                                   "beam_lens", "bellows_cannon", "blink_coil", "chain_hook", "climbing_claws", "flame_spitter",
                                                   "frost_projector", "grapple_spinner", "growth_band", "gust_bellows", "hissing_charge", "jaw_rake",
                                                   "ki_blaster", "lift_rig", "parry_gauntlet", "potion_sling", "rewind_watch", "smother_pump",
                                                   "sonic_horn", "fire_cannon")]],
    # what the Artificer works with: amethyst for reforging, and the brewing stuff (Artificers)
    "artificer": ["minecraft:amethyst_shard", "minecraft:glass_bottle", "minecraft:blaze_powder", "minecraft:feather", "minecraft:phantom_membrane",
                  "minecraft:glistering_melon_slice", "minecraft:golden_carrot"],
}


def entry(item):
    # ours are checked below; anything else (vanilla's too, which moves between versions) may be missing
    return item if item.lstrip("#").startswith("jaymoskingdoms:") else {"id": item, "required": False}


def main():
    have = {f[:-5] for f in os.listdir(OURS) if f.endswith(".json")}
    missing = [i for items in CLASSES.values() for i in items if i.startswith("jaymoskingdoms:") and i.split(":")[1] not in have]
    if missing:
        sys.exit("not items of ours: " + ", ".join(sorted(set(missing))))
    os.makedirs(OUT, exist_ok=True)
    for cls, items in CLASSES.items():
        seen = []
        for i in items:
            if i not in seen:
                seen.append(i)
        with open(os.path.join(OUT, cls + ".json"), "w", encoding="utf-8", newline="\n") as f:
            json.dump({"replace": False, "values": [entry(i) for i in seen]}, f, indent=2)
            f.write("\n")
        print(f"{cls}: {len(seen)}")


if __name__ == "__main__":
    main()
