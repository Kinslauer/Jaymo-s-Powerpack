#!/usr/bin/env python3
"""The building library's shipped files and index (docs/BuildingLibrary-Plan.md, patch 2).

Andy picked 88 rows of medieval buildings (2026-11-19). This script:

* downloads each open-licence mod at the exact Modrinth version pinned below (nothing else is fetched),
* takes only the picked structure files, joins the jigsaw-built ones into whole buildings (Towny and Village's town
  houses, The Lost Castle), swaps Sohatuy's modded blocks for vanilla stand-ins, and writes them as plain structure
  files under kingdoms/src/main/resources/data/jaymoskingdoms/structure/library/<source>/,
* writes the library index kingdoms/src/main/resources/jaymoskingdoms/library.json: those files, plus the in-pack
  buildings (Towns and Towers, Moog's Voyager Structures) that Jaymo's Kingdoms reads from the installed mods in the
  game and never copies, plus the world-only builds Andy downloads himself (matched by file name),
* writes kingdoms/src/main/resources/LIBRARY-CREDITS.md and the licence texts the licences ask for.

Run from the repository root:  python3 tools/library_import.py
Re-running gives the same files (the jigsaw joins use fixed seeds).
"""
import gzip
import io
import json
import os
import random
import struct
import sys
import urllib.request
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "kingdoms", "src", "main", "resources")
OUT = os.path.join(RES, "data", "jaymoskingdoms", "structure", "library")
INDEX = os.path.join(RES, "jaymoskingdoms", "library.json")
CREDITS = os.path.join(RES, "LIBRARY-CREDITS.md")
LICENCES = os.path.join(RES, "library-licences")
CACHE = os.path.join(ROOT, "build", "library-cache")

# ---- the sources ------------------------------------------------------------------------------------------------
# slug: (pinned file URL on Modrinth's CDN, title, authors, licence, page)
SOURCES = {
    "cinchs": ("https://cdn.modrinth.com/data/seVlw9gZ/versions/CgkQLjrv/cinchsabandonedhouses-%5BUNIVERSAL-26.3%5D-2.1.0.jar",
               "Cinch's Abandoned Houses 2.1.0", "Cinchtail", "MIT", "https://modrinth.com/mod/cinchs-abandoned-houses"),
    "lone_houses": ("https://cdn.modrinth.com/data/Nv4GECHn/versions/WNF9MAWd/lone_house-1.0.2%2B26.2.jar",
                    "Lone Houses 1.0.2", "Andper", "MIT", "https://modrinth.com/mod/lone-houses"),
    "towny": ("https://cdn.modrinth.com/data/xXClL8T5/versions/WwWlOjqI/kogtyv-TownyAndVillage-1.8-NeoForge-26.2.jar",
              "Towny and Village 1.8", "kogtyv", "MIT", "https://modrinth.com/mod/kogtyv-tav"),
    "berezka": ("https://cdn.modrinth.com/data/Rl5nnsUI/versions/Ht5m5G6q/berezkas_castle-1.0.1-fix-forge-1.20.1.jar",
                "Berezka's Castle 1.0.1", "Berezka", "MIT", "https://modrinth.com/mod/berezkas-castle"),
    "castles_outposts": ("https://cdn.modrinth.com/data/WPzbX0Eu/versions/dcpsWJD1/castles_and_outposts-1.0.1-fabric-1.21.8.jar",
                         "Castles and Outposts 1.0.1", "kilkavakilka", "MIT", "https://modrinth.com/mod/castles-and-outposts"),
    "battle_castles": ("https://cdn.modrinth.com/data/k5Ug8GZY/versions/JCYuUzxm/battlecastles1211neoforge.jar",
                       "Battle Castles 1.0.0", "Creamers inc. (mautip, creamtip)", "Apache-2.0 (Modrinth page; the jar says AFL-3.0)",
                       "https://modrinth.com/mod/battle-castles"),
    "lost_castle": ("https://cdn.modrinth.com/data/FGlHZl7X/versions/EOLSzg5l/tlc-neoforge-26.1-2.1.1.jar",
                    "The Lost Castle 2.1.1", "jack-bagel", "LGPL-3.0", "https://modrinth.com/mod/the-lost-castle"),
    "little_structures": ("https://cdn.modrinth.com/data/oi0lZhzZ/versions/aKVKu0FQ/little-structures-mc1.21.x-v2.1.jar",
                          "Little Structures 2.1", "MatBayern, SumiiRing", "LGPL-3.0", "https://modrinth.com/mod/little-structures"),
    "sohatuy": ("https://cdn.modrinth.com/data/fr53Fo1t/versions/aWnZYxtc/medieval_structures-1.1.4.jar",
                "Sohatuy's Medieval Structures 1.1.4", "Sohatuy", "Apache-2.0", "https://modrinth.com/mod/sohatuys-medieval-structures"),
}

# The licences' own words for the notices, taken from each jar; else the SPDX text (with the author's notice for MIT).
SPDX = "https://raw.githubusercontent.com/spdx/license-list-data/main/text/%s.txt"
SPDX_IDS = {"cinchs": ["MIT"], "lone_houses": ["MIT"], "towny": ["MIT"], "berezka": ["MIT"], "castles_outposts": ["MIT"],
            "battle_castles": ["Apache-2.0", "AFL-3.0"], "lost_castle": ["LGPL-3.0-only", "GPL-3.0-only"],
            "little_structures": ["LGPL-3.0-only", "GPL-3.0-only"], "sohatuy": ["Apache-2.0"]}
LICENCE_FILES = ["LICENSE", "LICENSE.txt", "LICENSE.md", "license.txt", "COPYING", "COPYING.LESSER", "LICENSE-MIT"]

# ---- the picks --------------------------------------------------------------------------------------------------
# Each building: id, name, kinds (catalogue entries it may stand in for; [] = mason's table only), lands (Palette names),
# biomes (biome id parts it's kept to, or none), flags: ruin (built whole: cobwebs, weeds dropped), castle (a seat
# for a kingdom raised from nothing), side (largest across it may be for the builders, default a plot's 32).

SHIPPED = []


def ship(slug, ident, name, path, kinds, lands, biomes=(), **flags):
    SHIPPED.append(dict(slug=slug, id=ident, name=name, path=path, kinds=list(kinds), lands=list(lands), biomes=list(biomes), **flags))


# Cinch's Abandoned Houses: plains (7), taiga (3), birch (5); empty cottages, built whole
_C = "data/cinchsabandonedhouses/structure/abandoned_houses/"
for f, k in [("abandoned_fancy_brick_house", "house"), ("abandoned_farm_house", "farmhouse"), ("abandoned_furnace_house", "smithy"),
             ("abandoned_house_basic_1", "house"), ("abandoned_house_basic_2", "house"), ("abandoned_house_small_ruined", "house"),
             ("abandoned_masons_house", "mason")]:
    ship("cinchs", f.replace("abandoned_", "cinch_"), "Cinch's " + f.replace("abandoned_", "").replace("_", " "),
         _C + "normal_abandoned_houses/" + f + ".nbt", [k], ["PLAINS"], ruin=True)
for i in (1, 2, 3):
    ship("cinchs", "cinch_taiga_house_%d" % i, "Cinch's taiga house %d" % i,
         _C + "abandoned_taiga_houses/abandoned_taiga_house_%d.nbt" % i, ["house"], ["TAIGA", "SNOWY"], ruin=True)
for i in (1, 2, 3, 4, 5):
    ship("cinchs", "cinch_birch_house_%d" % i, "Cinch's birch house %d" % i,
         _C + "abandoned_birch_forest_houses/abandoned_birch_forest_house_%d.nbt" % i, ["house"], ["PLAINS"], ["birch"], ruin=True)
# Lone Houses: 3 houses, 2 ruins
for i in (1, 2, 3):
    ship("lone_houses", "lone_house_%d" % i, "Lone house %d" % i, "data/lone_house/structure/lonestructure%d.nbt" % i,
         ["house"], ["PLAINS", "TAIGA"], ruin=True)
for i in (1, 2):
    ship("lone_houses", "lone_ruin_%d" % i, "Lone ruin %d" % i, "data/lone_house/structure/loneruins%d.nbt" % i, [], ["PLAINS", "TAIGA"])
# Berezka's castle, its four versions: castles for kingdoms raised from nothing
for f, n in [("caste_villagers", "Berezka's castle"), ("caste_evil", "Berezka's dark castle"), ("caste_ruins", "Berezka's castle, ruined"),
             ("caste_ruins2", "Berezka's castle, ruined (2)")]:
    ship("berezka", "berezka_" + f.replace("caste_", ""), n, "data/berezkas_castle/structures/%s.nbt" % f, [], ["PLAINS", "TAIGA", "SNOWY"],
         ruin=True, castle=True)
ship("castles_outposts", "tangled_castle", "Tangled castle", "data/castles_and_outposts/structure/tangled_castle.nbt", [],
     ["PLAINS", "TAIGA", "SWAMP"], ruin=True, castle=True)
ship("battle_castles", "battle_castle", "Battle castle", "data/battle_castles/structure/battlecastlebutgood.nbt", [], ["PLAINS", "TAIGA"],
     castle=True)
# Little Structures: the knight statue and the graveyard
ship("little_structures", "knight_statue", "Knight statue", "data/littlestructure/structure/knight_statue.nbt", [], ["PLAINS", "TAIGA"])
ship("little_structures", "little_graveyard", "Little graveyard", "data/littlestructure/structure/graveyard.nbt", ["graveyard"],
     ["PLAINS", "TAIGA", "SNOWY", "SWAMP"], ruin=True)
# Sohatuy's: the tavern in four woods, and the castle in two (modded blocks swapped for vanilla ones)
for land, lands in [("plains", ["PLAINS"]), ("taiga", ["TAIGA", "SNOWY"]), ("jungle", ["JUNGLE"]), ("savanna", ["SAVANNA"])]:
    ship("sohatuy", "sohatuy_tavern_" + land, "Sohatuy's %s tavern" % land, "data/medieval_structures/structures/tavern_%s.nbt" % land,
         ["tavern"], lands, swap=True)
for land, lands in [("plains", ["PLAINS"]), ("taiga", ["TAIGA", "SNOWY"])]:
    ship("sohatuy", "sohatuy_castle_" + land, "Sohatuy's %s castle" % land, "data/medieval_structures/structures/castle_%s.nbt" % land,
         [], lands, swap=True, castle=True)

# Jigsaw-joined: Towny and Village's European town houses (12 shapes, 3 each with their own roof and facades) and the
# market square well; The Lost Castle (nine pieces, joined if the whole fits the mason's table's 128 across).
TOWNY_HOUSES = ["square", "three", "two", "square_thirdes", "three_medium", "two_medium", "square_smallerth", "two_small", "three_small",
                "corner_square", "longer_medium", "longer"]
TOWNY_EACH = 3
LOST_CASTLE_MAX = 128

# The buildings already in the pack, read in the game from the installed mod (never copied).
IN_PACK = []


def have(ident, name, template, kinds, lands, biomes=(), author="", licence="", link="", **flags):
    IN_PACK.append(dict(id=ident, name=name, template=template, kinds=list(kinds), lands=list(lands), biomes=list(biomes), author=author,
                        licence=licence, link=link, **flags))


TT = dict(author="Kubek, Biban_Auriu, Cristelknight999 (Towns and Towers)", licence="CC BY-NC-ND 4.0 (in the pack, read unchanged)",
          link="https://modrinth.com/mod/towns-and-towers")


def tt_kind(name):
    for key, kind in [("small_house", "house"), ("medium_house", "house"), ("large_house", "house"), ("farmer", "farmhouse"),
                      ("farm", "farmhouse"), ("butcher", "butcher"), ("armorer", "smithy"), ("toolsmith", "smithy"), ("weaponsmith", "smithy"),
                      ("blacksmith", "smithy"), ("mason", "mason"), ("fisher", "fishery"), ("temple", "chapel"), ("church", "chapel"),
                      ("barn", "barn")]:
        if key in name:
            return kind
    return "house"


# (style dir, its houses, lands, biomes)
TT_STYLES = [
    ("village/exclusives/tudor/houses", ["tudor_butcher_and_armorer_1", "tudor_cartographer_and_fletcher_1", "tudor_farmer_1", "tudor_fisher_1",
                                         "tudor_large_house_1", "tudor_leatherworker_1", "tudor_library_1", "tudor_mason_1", "tudor_shepherd_1",
                                         "tudor_small_house_1", "tudor_small_house_2", "tudor_small_house_3", "tudor_small_house_4", "tudor_temple_1",
                                         "tudor_toolsmith_and_weaponsmith_1"], ["PLAINS"], []),
    ("village/exclusives/swedish/houses", ["swedish_armorer_1", "swedish_butcher_1", "swedish_cartographer_1", "swedish_farmer_and_fisher_1",
                                           "swedish_fletcher_1", "swedish_leatherworker_1", "swedish_library_and_temple_1", "swedish_mason_1",
                                           "swedish_shepherd_1", "swedish_small_house_1", "swedish_small_house_2", "swedish_small_house_3",
                                           "swedish_small_house_4", "swedish_small_house_5", "swedish_small_house_6", "swedish_small_house_7",
                                           "swedish_toolsmith_1", "swedish_weaponsmith_1"], ["TAIGA", "SNOWY"], []),
    ("village/exclusives/iberian/houses", ["iberian_large_house_1", "iberian_large_house_2", "iberian_medium_house_1", "iberian_medium_house_2",
                                           "iberian_medium_house_3", "iberian_medium_house_4", "iberian_medium_house_5", "iberian_medium_house_6",
                                           "iberian_medium_house_7", "iberian_medium_house_8", "iberian_small_house_1", "iberian_small_house_2",
                                           "iberian_small_house_3", "iberian_temple_1"], ["SAVANNA", "DESERT", "BADLANDS"], []),
    ("village/old_growth_taiga_polish/houses", ["old_growth_taiga_armorer_and_butcher_1", "old_growth_taiga_cartographer_1",
                                                "old_growth_taiga_fisher_1", "old_growth_taiga_fletcher_1", "old_growth_taiga_leatherworker_1",
                                                "old_growth_taiga_library_1", "old_growth_taiga_mason_1", "old_growth_taiga_medium_house_1",
                                                "old_growth_taiga_medium_house_2", "old_growth_taiga_shepherd_1", "old_growth_taiga_small_house_1",
                                                "old_growth_taiga_small_house_2", "old_growth_taiga_small_house_3", "old_growth_taiga_small_house_4",
                                                "old_growth_taiga_small_house_5", "old_growth_taiga_small_house_6", "old_growth_taiga_temple_1",
                                                "old_growth_taiga_toolsmith_and_weaponsmith_1"], ["TAIGA"], ["old_growth"]),
    ("village/birch_forest_romanian/houses", ["birch_forest_armorer_and_toolsmith_1", "birch_forest_butcher_and_leatherworker_1",
                                              "birch_forest_cartographer_and_library_1", "birch_forest_fisher_1", "birch_forest_fletcher_1",
                                              "birch_forest_large_house_1", "birch_forest_mason_1", "birch_forest_medium_house_1",
                                              "birch_forest_medium_house_2", "birch_forest_shepherd_1", "birch_forest_small_house_1",
                                              "birch_forest_small_house_2", "birch_forest_small_house_3", "birch_forest_small_house_4",
                                              "birch_forest_small_house_5", "birch_forest_small_house_6", "birch_forest_weaponsmith_1"],
     ["PLAINS"], ["birch"]),
    ("village/meadow_swiss/houses", ["meadow_armorer_and_leatherworker_1", "meadow_butcher_and_mason_1", "meadow_cartographer_and_library_1",
                                     "meadow_farmer_1", "meadow_fisher_1", "meadow_fletcher_1", "meadow_large_house_1", "meadow_medium_house_1",
                                     "meadow_medium_house_2", "meadow_medium_house_3", "meadow_medium_house_4", "meadow_medium_house_5",
                                     "meadow_shepherd_1", "meadow_small_house_1", "meadow_small_house_2", "meadow_temple_1",
                                     "meadow_toolsmith_and_weaponsmith_1"], ["PLAINS", "SNOWY", "TAIGA"], ["meadow"]),
    ("village/exclusives/rustic/houses", ["rustic_barn_professions_1", "rustic_large_house_1", "rustic_large_house_2", "rustic_large_house_3",
                                          "rustic_large_house_4", "rustic_small_house_1", "rustic_small_house_2", "rustic_small_house_3",
                                          "rustic_small_house_4", "rustic_temple_1"], ["PLAINS", "TAIGA"], []),
    ("village/exclusives/classic/houses", ["classic_blacksmith_1", "classic_butcher_1", "classic_church_1", "classic_large_farm_1",
                                           "classic_large_house_1", "classic_library_1", "classic_medium_house_1", "classic_medium_house_2",
                                           "classic_small_farm_1", "classic_small_house_1", "classic_small_house_2"], ["PLAINS"], []),
]
for d, houses, lands, biomes in TT_STYLES:
    for h in houses:
        have("tt_" + h, h.replace("_1", "").replace("_", " "), "kaisyn:%s/%s" % (d, h), [tt_kind(h)], lands, biomes, **TT)
have("tt_sunflower_windmill", "Sunflower farm windmill", "kaisyn:village/sunflower_plains_farm/side/sunflower_plains_windmill_1", ["windmill"],
     ["PLAINS"], **TT)
have("tt_mountain_inn", "Mountain inn", "kaisyn:village/snowy_slopes_inn/town_centers/snowy_slopes_meeting_point_1", ["tavern"], ["SNOWY"], **TT)
have("tt_tudor_watchtower", "Tudor watchtower", "kaisyn:outpost/towers/exclusives/tudor/base_plate", ["firewatch_tower"], ["PLAINS"],
     outpost=True, **TT)
have("tt_swedish_watchtower", "Swedish watchtower", "kaisyn:outpost/towers/exclusives/swedish/base_plate", ["firewatch_tower"], ["TAIGA", "SNOWY"],
     outpost=True, **TT)
for f in ("classic", "iberian", "mediterranean", "rustic"):
    have("tt_%s_fort" % f, "%s palisade fort" % f.capitalize(), "kaisyn:outpost/forts/exclusives/%s/base_plate" % f, [], ["PLAINS"], outpost=True, **TT)

MVS = dict(author="FinnDog, Phantax (Moog's Voyager Structures)", licence="MIT (in the pack, read unchanged)",
           link="https://modrinth.com/mod/moogs-voyager-structures")
for ident, name, t, kinds, lands, flags in [
    ("mvs_cottage", "Timber cottage", "mvs:1_21_5/houses/house", ["house"], ["PLAINS"], {}),
    ("mvs_tall_cottage", "Tall timber cottage", "mvs:1_21_4/houses/tall_house", ["house"], ["PLAINS", "TAIGA"], {}),
    ("mvs_azalea_cottage", "Azalea cottage", "mvs:1_21_4/houses/azelea_house", ["house"], ["PLAINS"], {}),
    ("mvs_deepslate_hall", "Deepslate hall", "mvs:1_21_9/houses/deepslate_house", ["house"], ["TAIGA", "SNOWY", "PLAINS"], {}),
    ("mvs_barn", "Barn", "mvs:1_21_9/houses/barn", ["barn"], ["PLAINS", "TAIGA"], {}),
    ("mvs_stone_tower", "Stone tower house", "mvs:1_21_9/houses/diorite_tower", ["firewatch_tower"], ["PLAINS"], {}),
    ("mvs_castle_ruins", "Castle ruins", "mvs:1_21_0/ruins/castle_ruins", [], ["PLAINS", "TAIGA"], {"ruin": True}),
    ("mvs_cartographer_tower", "Cartographer tower", "mvs:1_21_9/cartographer_tower/base", [], ["PLAINS"], {}),
    ("mvs_red_tower", "Red tower", "mvs:1_21_0/houses/red_tower", [], ["PLAINS"], {}),
    ("mvs_walled_keep", "Walled keep", "mvs:1_21_9/cathedral/base/base", [], ["PLAINS", "TAIGA"], {"castle": True}),
]:
    have(ident, name, t, kinds, lands, **MVS, **flags)

# The world-only builds Andy downloads into <world>/jaymoskingdoms_library/ (no licence to hand on, so never in git):
# sorted by the file's name (lower case, any extension), the words it must hold.
WORLD = [
    # (words in the file name, id, name, kinds, lands, author)
    ("half-timbered", "half_timbered_house", "Half-Timbered House", ["house"], ["PLAINS"]),
    ("spruce-dark-oak-starter", "spruce_dark_oak_starter_house", "Medieval Spruce & Dark Oak Starter House", ["house"], ["TAIGA", "PLAINS"]),
    ("spruce-and-stone-house", "spruce_and_stone_house", "Medieval Spruce And Stone House", ["house"], ["TAIGA", "PLAINS"]),
    ("house-model-3", "medieval_house_model_3", "Medieval House Model 3", ["house"], ["PLAINS"]),
    ("small-decorated-medieval-house", "small_decorated_house", "Small Decorated Medieval House", ["house"], ["PLAINS"]),
    ("village-dwelling", "village_dwelling", "Medieval Village Dwelling", ["house"], ["PLAINS"]),
    ("fisherman-house", "fisherman_house", "Medieval Fisherman House", ["fishery"], ["PLAINS", "TAIGA"]),
    ("compact-medieval-house", "compact_house", "Compact Medieval House", ["house"], ["PLAINS"]),
    ("spruce-and-brick", "spruce_and_brick_house", "Spruce and Brick Medieval House", ["house"], ["TAIGA", "PLAINS"]),
    ("double-storag", "double_storage_house", "Medium Medieval House with Double Storage", [], ["PLAINS"]),
    ("town-building-1", "town_building_1", "Medieval Town Building 1", [], ["PLAINS"]),
    ("town-building-5", "town_building_5", "Medieval Town Building 5", [], ["PLAINS"]),
    ("rustic-medieval-blacksmith", "rustic_blacksmith_house", "Rustic Medieval Blacksmith House", ["smithy"], ["PLAINS", "TAIGA"]),
    ("blacksmith-house-minimalist", "blacksmith_minimalist", "Village Blacksmith House Minimalist", ["smithy"], ["PLAINS"]),
    ("medieval-blacksmith-house", "blacksmith_house", "Medieval Blacksmith House", ["smithy"], ["PLAINS"]),
    ("metal-smithing-shop", "smithing_shop", "Metal Smithing Shop Building", ["smithy"], ["PLAINS"]),
    ("bakery", "bakery_house", "Rustic Medieval Bakery House", ["house"], ["PLAINS"]),
    ("village-shop", "village_shop", "Medieval Village Shop", ["stall"], ["PLAINS"]),
    ("market-stall", "market_stall", "Medieval Market Stall", ["market"], ["PLAINS"]),
    ("storage-house", "storage_house", "Medieval Storage House", ["house"], ["PLAINS"]),
    ("rustic-crane", "rustic_crane", "Medieval Rustic Crane", [], ["PLAINS"]),
    ("tavern-and-inn", "villager_tavern_inn", "Villager Tavern and Inn", ["tavern"], ["PLAINS"]),
    ("fantasy-tavern", "fantasy_tavern", "Fantasy Tavern Build", ["tavern"], ["PLAINS"]),
    ("tavern-building-2", "small_tavern", "Small Medieval Tavern", [], ["PLAINS"]),
    ("medieval-tavern-building", "tavern_building", "Medieval Tavern Building", ["tavern"], ["PLAINS"]),
    ("spruce-medieval-tavern", "spruce_tavern_house", "Spruce Medieval Tavern House", ["tavern"], ["TAIGA", "PLAINS"]),
    ("inn-and-farmhou", "colorful_inn_farmhouse", "Rustic Colorful Medieval Inn and Farmhouse", [], ["PLAINS"]),
    ("barn-house", "barn_house", "Medieval Barn House", ["barn"], ["PLAINS"]),
    ("horse-stable", "horse_stable", "Medieval Horse Stable", [], ["PLAINS"]),
    ("windmill-structure-2", "rustic_windmill", "Rustic Medieval Windmill Build", ["windmill"], ["PLAINS"]),
    ("windmill-structure-3", "cobblestone_windmill", "Medieval Windmill With Cobblestone Accents", ["windmill"], ["PLAINS"]),
    ("decorative-medieval-windmill", "decorative_windmill", "Decorative Medieval Windmill", ["windmill"], ["PLAINS"]),
    ("deepslate-village-church", "deepslate_church", "Medieval Deepslate Village Church", ["chapel"], ["PLAINS", "TAIGA"]),
    ("town-church", "town_church", "Medieval Town Church Building", [], ["PLAINS"]),
    ("ruined-church", "ruined_church", "Medieval Ruined Church Structure", [], ["PLAINS"]),
    ("bell-tower", "church_bell_tower", "Village Church Bell Tower", ["chapel"], ["PLAINS"]),
    ("medieval-village-church", "village_church", "Medieval Village Church", ["chapel"], ["PLAINS"]),
    ("dark-oak-deepslate-tower", "dark_oak_deepslate_tower", "Dark Oak Deepslate Tower", ["firewatch_tower"], ["PLAINS", "TAIGA"]),
    ("stone-brick-watchtower", "stone_brick_watchtower", "Medieval Stone Brick Watchtower", [], ["PLAINS"]),
    ("small-medieval-castle", "small_castle", "Small Medieval Castle", [], ["PLAINS"], True),
    ("compact-cobblestone-castle", "compact_cobblestone_castle", "Compact Cobblestone Castle", [], ["PLAINS", "TAIGA"], True),
    ("gatehouse", "great_gatehouse", "Great Gatehouse Fortress Entrance", [], ["PLAINS"]),
    ("castle-base", "castle_base", "Medieval Castle Base", [], ["PLAINS"], True),
    ("stone-bridge", "stone_bridge", "Medieval Stone Bridge", [], ["PLAINS"]),
    ("castle-structure-12", "classic_stone_castle", "Classic Medieval Stone Castle", [], ["PLAINS"], True),
    ("fantastic-medieval-castle", "fantastic_castle", "Fantastic Medieval Castle", [], ["PLAINS"], True),
    ("ivoryfalls", "ivoryfalls_tavern", "Ivoryfalls Tavern", ["tavern"], ["PLAINS"]),
    ("windmill", "planet_windmill", "Medieval Windmill", ["windmill"], ["PLAINS"]),
    ("tavern", "medieval_tavern", "Medieval Tavern", ["tavern"], ["PLAINS"]),
]

# ---- NBT --------------------------------------------------------------------------------------------------------


class Tag:
    """A typed NBT value: kind is the tag id, value the Python value (lists: (element id, [values]))."""
    __slots__ = ("kind", "value")

    def __init__(self, kind, value):
        self.kind = kind
        self.value = value


def read_nbt(raw):
    try:
        raw = gzip.decompress(raw)
    except OSError:
        pass
    p = [0]

    def u(fmt, n):
        v = struct.unpack_from(fmt, raw, p[0])[0]
        p[0] += n
        return v

    def string():
        n = u(">H", 2)
        s = raw[p[0]:p[0] + n].decode("utf-8", "replace")
        p[0] += n
        return s

    def payload(t):
        if t == 1:
            return Tag(1, u(">b", 1))
        if t == 2:
            return Tag(2, u(">h", 2))
        if t == 3:
            return Tag(3, u(">i", 4))
        if t == 4:
            return Tag(4, u(">q", 8))
        if t == 5:
            return Tag(5, u(">f", 4))
        if t == 6:
            return Tag(6, u(">d", 8))
        if t == 7:
            n = u(">i", 4)
            v = raw[p[0]:p[0] + n]
            p[0] += n
            return Tag(7, v)
        if t == 8:
            return Tag(8, string())
        if t == 9:
            et = u(">b", 1)
            n = u(">i", 4)
            return Tag(9, (et, [payload(et) for _ in range(n)]))
        if t == 10:
            d = {}
            while True:
                tt = u(">b", 1)
                if tt == 0:
                    return Tag(10, d)
                k = string()
                d[k] = payload(tt)
        if t == 11:
            n = u(">i", 4)
            v = list(struct.unpack_from(">%di" % n, raw, p[0]))
            p[0] += 4 * n
            return Tag(11, v)
        if t == 12:
            n = u(">i", 4)
            v = list(struct.unpack_from(">%dq" % n, raw, p[0]))
            p[0] += 8 * n
            return Tag(12, v)
        raise ValueError("bad tag %d" % t)

    t = u(">b", 1)
    string()
    return payload(t)


def plain(tag):
    """A Tag as plain Python (compounds as dicts, lists as lists)."""
    if tag.kind == 10:
        return {k: plain(v) for k, v in tag.value.items()}
    if tag.kind == 9:
        return [plain(v) for v in tag.value[1]]
    return tag.value


def write_nbt(root):
    out = io.BytesIO()

    def string(s):
        b = s.encode("utf-8")
        out.write(struct.pack(">H", len(b)))
        out.write(b)

    def payload(tag):
        k, v = tag.kind, tag.value
        if k == 1:
            out.write(struct.pack(">b", v))
        elif k == 2:
            out.write(struct.pack(">h", v))
        elif k == 3:
            out.write(struct.pack(">i", v))
        elif k == 4:
            out.write(struct.pack(">q", v))
        elif k == 5:
            out.write(struct.pack(">f", v))
        elif k == 6:
            out.write(struct.pack(">d", v))
        elif k == 7:
            out.write(struct.pack(">i", len(v)))
            out.write(v)
        elif k == 8:
            string(v)
        elif k == 9:
            et, items = v
            out.write(struct.pack(">bi", et if items else (et or 0), len(items)))
            for it in items:
                payload(it)
        elif k == 10:
            for key, t in v.items():
                out.write(struct.pack(">b", t.kind))
                string(key)
                payload(t)
            out.write(b"\0")
        elif k == 11:
            out.write(struct.pack(">i", len(v)))
            out.write(struct.pack(">%di" % len(v), *v))
        elif k == 12:
            out.write(struct.pack(">i", len(v)))
            out.write(struct.pack(">%dq" % len(v), *v))

    out.write(b"\x0a")
    string("")
    payload(root)
    # a fixed time in the gzip header, so the same input gives the same bytes
    return gzip.compress(out.getvalue(), mtime=0)


def S(s):
    return Tag(8, s)


def I(i):
    return Tag(3, i)


def C(d):
    return Tag(10, d)


def L(kind, items):
    return Tag(9, (kind, items))

# ---- a building in memory: pos -> (name, props, jigsaw nbt or None) ---------------------------------------------


class Piece:
    """A structure file read: size, blocks (x, y, z, name, props dict, nbt dict-or-None), data version."""

    def __init__(self, raw):
        t = plain(read_nbt(raw))
        self.size = t["size"]
        pal = t.get("palette")
        if pal is None:
            pal = t["palettes"][0]
        self.dv = t.get("DataVersion", 0)
        self.blocks = []
        for b in t["blocks"]:
            s = pal[b["state"]]
            x, y, z = b["pos"]
            self.blocks.append((x, y, z, s["Name"], dict(s.get("Properties") or {}), b.get("nbt")))

    def jigsaws(self):
        return [b for b in self.blocks if b[3] == "minecraft:jigsaw"]


DIRS = {"north": (0, 0, -1), "south": (0, 0, 1), "west": (-1, 0, 0), "east": (1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}
OPP = {"north": "south", "south": "north", "west": "east", "east": "west", "up": "down", "down": "up"}
CW = {"north": "east", "east": "south", "south": "west", "west": "north", "up": "up", "down": "down"}


def turn_dir(d, r):
    for _ in range(r):
        d = CW[d]
    return d


def turn_pos(x, z, r):
    # clockwise looking down, as Minecraft's Rotation.CLOCKWISE_90 turns a template about its origin
    for _ in range(r):
        x, z = -z, x
    return x, z


SIDE_PROPS = ("north", "east", "south", "west")


def turn_state(name, props, r):
    if r == 0 or not props:
        return props
    p = dict(props)
    if "facing" in p and p["facing"] in CW:
        p["facing"] = turn_dir(p["facing"], r)
    if "axis" in p and r % 2 == 1 and p["axis"] in ("x", "z"):
        p["axis"] = "z" if p["axis"] == "x" else "x"
    if "rotation" in p and p["rotation"].isdigit():
        p["rotation"] = str((int(p["rotation"]) + 4 * r) % 16)
    if "orientation" in p:
        front, top = p["orientation"].split("_")
        p["orientation"] = turn_dir(front, r) + "_" + turn_dir(top, r)
    old = {k: p.pop(k) for k in SIDE_PROPS if k in p}
    for k, v in old.items():
        p[turn_dir(k, r)] = v
    return p


class Jar:
    def __init__(self, slug):
        url = SOURCES[slug][0]
        os.makedirs(CACHE, exist_ok=True)
        path = os.path.join(CACHE, slug + ".jar")
        if not os.path.isfile(path):
            print("downloading", url)
            with urllib.request.urlopen(url) as r, open(path, "wb") as f:
                f.write(r.read())
        self.zip = zipfile.ZipFile(path)
        self.names = set(self.zip.namelist())

    def read(self, path):
        return self.zip.read(path)

    def template(self, ident):
        ns, path = ident.split(":", 1)
        for folder in ("structure", "structures"):
            p = "data/%s/%s/%s.nbt" % (ns, folder, path)
            if p in self.names:
                return Piece(self.read(p))
        raise KeyError(ident)

    def pool(self, ident):
        ns, path = ident.split(":", 1)
        p = "data/%s/worldgen/template_pool/%s.json" % (ns, path)
        if p not in self.names:
            return []
        out = []
        for e in json.loads(self.read(p))["elements"]:
            el = e["element"]
            if el.get("element_type", "").endswith("single_pool_element") and el.get("location"):
                out.extend([el["location"]] * max(1, int(e.get("weight", 1))))
        return out

    def licence(self):
        for n in sorted(self.names):
            if os.path.basename(n) in LICENCE_FILES or os.path.basename(n).upper().startswith("LICENSE"):
                return self.read(n).decode("utf-8", "replace")
        return None

# ---- jigsaw joining ---------------------------------------------------------------------------------------------


def assemble(jar, start, seed, max_depth=12, skip_pools=()):
    """Joins a jigsaw structure from template {start} the way the game does (rigid pieces, a free-space check), with a
    fixed seed. Returns (blocks dict pos -> (name, props, nbt), data version)."""
    rnd = random.Random(seed)
    placed = {}
    boxes = []  # (min, max, parent index)
    dv = [10 ** 9]

    def box_of(piece, r, ox, oy, oz):
        xs, ys, zs = piece.size
        pts = [turn_pos(x, z, r) for x, z in ((0, 0), (xs - 1, 0), (0, zs - 1), (xs - 1, zs - 1))]
        return ((ox + min(p[0] for p in pts), oy, oz + min(p[1] for p in pts)),
                (ox + max(p[0] for p in pts), oy + ys - 1, oz + max(p[1] for p in pts)))

    def inside(a, b):
        return all(b[0][i] <= a[0][i] and a[1][i] <= b[1][i] for i in range(3))

    def overlap(a, b):
        return all(a[0][i] <= b[1][i] and b[0][i] <= a[1][i] for i in range(3))

    def fits(bx, parent):
        # a piece inside its parent may not overlap the parent's other children; outside, it may not overlap anything
        # but the pieces that hold it
        chain = set()
        i = parent
        while i is not None:
            if inside(bx, boxes[i][0:2]):
                chain.add(i)
            i = boxes[i][2]
        for j, b in enumerate(boxes):
            if j in chain:
                continue
            if overlap(bx, b[0:2]):
                return False
        return True

    def put(piece, r, ox, oy, oz):
        dv[0] = min(dv[0], piece.dv or dv[0])
        for x, y, z, name, props, nbt in piece.blocks:
            if name == "minecraft:structure_void":
                continue
            tx, tz = turn_pos(x, z, r)
            placed[(ox + tx, oy + y, oz + tz)] = (name, turn_state(name, props, r), nbt)

    first = jar.template(start)
    boxes.append((*box_of(first, 0, 0, 0, 0), None))
    put(first, 0, 0, 0, 0)
    queue = [(first, 0, (0, 0, 0), 0, 0)]
    while queue:
        piece, r, (ox, oy, oz), index, depth = queue.pop(0)
        if depth >= max_depth:
            continue
        for x, y, z, name, props, nbt in piece.jigsaws():
            nbt = nbt or {}
            pool = nbt.get("pool", "minecraft:empty")
            target = nbt.get("target", "minecraft:empty")
            if pool in ("minecraft:empty", "") or target == "minecraft:empty" or pool in skip_pools:
                continue
            front, top = turn_state(name, props, r)["orientation"].split("_")
            tx, tz = turn_pos(x, z, r)
            jx, jy, jz = ox + tx, oy + y, oz + tz
            dx, dy, dz = DIRS[front]
            want = (jx + dx, jy + dy, jz + dz)
            options = jar.pool(pool)
            rnd.shuffle(options)
            seen = set()
            done = False
            for loc in options:
                if loc in seen:
                    continue
                seen.add(loc)
                try:
                    child = jar.template(loc)
                except KeyError:
                    continue
                rots = [0, 1, 2, 3]
                rnd.shuffle(rots)
                for cr in rots:
                    for cx, cy, cz, cname, cprops, cnbt in child.jigsaws():
                        cnbt = cnbt or {}
                        if cnbt.get("name") != target:
                            continue
                        cfront, ctop = turn_state(cname, cprops, cr)["orientation"].split("_")
                        joint = nbt.get("joint", "rollable" if front in ("up", "down") else "aligned")
                        if cfront != OPP[front] or (front in ("up", "down") and joint == "aligned" and ctop != top):
                            continue
                        ctx, ctz = turn_pos(cx, cz, cr)
                        cox, coy, coz = want[0] - ctx, want[1] - cy, want[2] - ctz
                        bx = box_of(child, cr, cox, coy, coz)
                        if not fits(bx, index):
                            continue
                        boxes.append((*bx, index))
                        put(child, cr, cox, coy, coz)
                        queue.append((child, cr, (cox, coy, coz), len(boxes) - 1, depth + 1))
                        done = True
                        break
                    if done:
                        break
                if done:
                    break
    return placed, (dv[0] if dv[0] < 10 ** 9 else 0)

# ---- writing a building -----------------------------------------------------------------------------------------


def final_state(text):
    name = text.split("[", 1)[0].strip() or "minecraft:air"
    props = {}
    if "[" in text:
        for kv in text.split("[", 1)[1].rstrip("]").split(","):
            if "=" in kv:
                k, v = kv.split("=", 1)
                props[k.strip()] = v.strip()
    return name, props


# Sohatuy's modded blocks: the vanilla stand-in for each (Apache-2.0 lets us change them; the change is noted).
def sohatuy_swap(name, props):
    ns, path = name.split(":", 1)
    if ns == "minecraft":
        return name, props
    wood = None
    for w in ("stripped_spruce", "acacia", "jungle", "oak", "spruce", "birch", "dark_oak"):
        if path.startswith(w + "_"):
            wood = w.replace("stripped_", "")
            break
    if path.endswith("_vertical_slab"):
        base = path[:-len("_vertical_slab")]
        block = "stone_bricks" if base == "stone_brick" else base + "_planks"
        return "minecraft:" + block, {}
    if path == "spruce_ladder":
        return "minecraft:ladder", {"facing": props.get("facing", "north"), "waterlogged": "false"}
    if path.endswith("_stool") or path.endswith("_stool_chair"):
        return "minecraft:%s_slab" % (wood or "oak"), {"type": "bottom", "waterlogged": "false"}
    if path.endswith("_chair"):
        return "minecraft:%s_stairs" % (wood or "oak"), {"facing": props.get("facing", "north"), "half": "bottom", "shape": "straight",
                                                          "waterlogged": "false"}
    if path.endswith("_table"):
        return "minecraft:%s_slab" % (wood or "oak"), {"type": "top", "waterlogged": "false"}
    if path == "mining_bench":
        return "minecraft:stonecutter", {"facing": "north"}
    if path in ("decayed_workbench", "woodworking_table"):
        return "minecraft:crafting_table", {}
    raise SystemExit("Sohatuy's block with no stand-in: " + name)


def to_tag(blocks, dv, swap=False, keep_jigsaws=False):
    """Blocks (pos -> (name, props, nbt)) as a structure file: jigsaws turned to their final blocks (or kept, with only
    the entrance's name and final block, when {keep_jigsaws}), air dropped, trimmed to the corner, no block entity data."""
    cells = []
    for (x, y, z), (name, props, nbt) in blocks.items():
        jig = None
        if name == "minecraft:jigsaw":
            nbt = nbt or {}
            if keep_jigsaws and nbt.get("name") == "minecraft:building_entrance":
                jig = {"name": nbt.get("name"), "final_state": nbt.get("final_state", "minecraft:air")}
            else:
                name, props = final_state(nbt.get("final_state", "minecraft:air"))
        if swap:
            name, props = sohatuy_swap(name, props)
        if name in ("minecraft:air", "minecraft:cave_air", "minecraft:void_air", "minecraft:structure_void", "minecraft:structure_block"):
            continue
        cells.append((x, y, z, name, props, jig))
    if not cells:
        return None, None
    mx = min(c[0] for c in cells)
    my = min(c[1] for c in cells)
    mz = min(c[2] for c in cells)
    size = [max(c[0] for c in cells) - mx + 1, max(c[1] for c in cells) - my + 1, max(c[2] for c in cells) - mz + 1]
    palette, index, out = [], {}, []
    for x, y, z, name, props, jig in sorted(cells, key=lambda c: (c[1], c[2], c[0])):
        key = (name, tuple(sorted(props.items())))
        if key not in index:
            index[key] = len(palette)
            entry = {"Name": S(name)}
            if props:
                entry["Properties"] = C({k: S(str(v)) for k, v in sorted(props.items())})
            palette.append(C(entry))
        b = {"pos": L(3, [I(x - mx), I(y - my), I(z - mz)]), "state": I(index[key])}
        if jig:
            b["nbt"] = C({"id": S("minecraft:jigsaw"), "name": S(jig["name"]), "final_state": S(jig["final_state"]),
                          "target": S("minecraft:empty"), "pool": S("minecraft:empty"), "joint": S("rollable")})
        out.append(C(b))
    root = C({"size": L(3, [I(s) for s in size]), "palette": L(10, palette), "blocks": L(10, out), "entities": L(10, []),
              "DataVersion": I(dv)})
    return root, (size, len(out))


def piece_blocks(piece):
    return {(x, y, z): (name, props, nbt) for x, y, z, name, props, nbt in piece.blocks}


def write(slug, ident, root):
    folder = os.path.join(OUT, slug)
    os.makedirs(folder, exist_ok=True)
    with open(os.path.join(folder, ident + ".nbt"), "wb") as f:
        f.write(write_nbt(root))
    return "jaymoskingdoms:library/%s/%s" % (slug, ident)

# ---- main -------------------------------------------------------------------------------------------------------


def main():
    if os.path.isdir(OUT):
        for dirpath, _, files in os.walk(OUT):
            for f in files:
                os.remove(os.path.join(dirpath, f))
    entries = []
    jars = {}
    notes = {}

    def jar(slug):
        if slug not in jars:
            jars[slug] = Jar(slug)
        return jars[slug]

    def entry(slug, ident, name, template, kinds, lands, biomes=(), **flags):
        _, title, author, licence, link = SOURCES[slug]
        e = {"id": ident, "name": name, "source": template, "kinds": list(kinds), "lands": list(lands), "author": author, "licence": licence,
             "link": link, "from": title}
        if biomes:
            e["biomes"] = list(biomes)
        for k in ("ruin", "castle", "outpost"):
            if flags.get(k):
                e[k] = True
        entries.append(e)

    for s in SHIPPED:
        j = jar(s["slug"])
        piece = Piece(j.read(s["path"]))
        root, info = to_tag(piece_blocks(piece), piece.dv, swap=s.get("swap", False), keep_jigsaws=True)
        if root is None:
            raise SystemExit("empty: " + s["path"])
        template = write(s["slug"], s["id"], root)
        print("%-34s %s, %d blocks" % (s["id"], "x".join(map(str, info[0])), info[1]))
        entry(s["slug"], s["id"], s["name"], template, s["kinds"], s["lands"], s["biomes"], **{k: s.get(k) for k in ("ruin", "castle")})
        if s.get("swap"):
            notes["sohatuy"] = ("Modded blocks swapped for vanilla ones: Quark's vertical slabs for the full block, its spruce ladder for "
                                "a ladder, its stools and Macaw's stool chairs for slabs, Macaw's chairs for stairs, Macaw's and Sohatuy's "
                                "tables for top slabs, More Villagers' benches for a crafting table or stonecutter. Block contents left out.")

    # Towny and Village: whole town houses, three of each shape, each its own roof colour and facades
    tj = jar("towny")
    for shape in TOWNY_HOUSES:
        for n in range(1, TOWNY_EACH + 1):
            blocks, dv = assemble(tj, "minecraft:village/europe/house/%s/main" % shape, seed=hash_seed(shape, n))
            root, info = to_tag(blocks, dv)
            ident = "towny_%s_%d" % (shape, n)
            template = write("towny", ident, root)
            print("%-34s %s, %d blocks" % (ident, "x".join(map(str, info[0])), info[1]))
            entry("towny", ident, "Town house (%s) %d" % (shape.replace("_", " "), n), template, ["house"], ["PLAINS"])
    # (the square's well alone, without the streets and houses its village joins round it)
    piece = tj.template("minecraft:village/europe/europe_small/centers/rynek_well")
    root, info = to_tag(piece_blocks(piece), piece.dv)
    template = write("towny", "towny_market_well", root)
    print("%-34s %s, %d blocks" % ("towny_market_well", "x".join(map(str, info[0])), info[1]))
    entry("towny", "towny_market_well", "Market square well", template, ["well"], ["PLAINS"])
    notes["towny"] = ("Each town house is joined from the mod's parts (shell, rooms, facades, roof, foundation) as its villages join "
                      "them, then saved whole; furniture pieces come with them.")

    # The Lost Castle: joined, if the whole fits the mason's table; else its nine pieces as they are
    lj = jar("lost_castle")
    blocks, dv = assemble(lj, "tlc:lost_castle/castle_mm", seed=1, skip_pools=("tlc:mobs/evokers", "tlc:mobs/vindicators", "tlc:mobs/witches"))
    root, info = to_tag(blocks, dv)
    if root is not None and info[0][0] <= LOST_CASTLE_MAX and info[0][2] <= LOST_CASTLE_MAX and info[0][1] <= 128:
        template = write("lost_castle", "lost_castle", root)
        print("%-34s %s, %d blocks (joined)" % ("lost_castle", "x".join(map(str, info[0])), info[1]))
        entry("lost_castle", "lost_castle", "The Lost Castle", template, [], ["PLAINS", "TAIGA", "SNOWY"], castle=True)
        notes["lost_castle"] = "The castle's nine pieces joined into one building as the mod joins them in the world."
    else:
        print("lost_castle joined is %s: kept as nine pieces" % ("x".join(map(str, info[0])) if info else "empty"))
        for part in ("tl", "tm", "tr", "ml", "mm", "mr", "bl", "bm", "br"):
            piece = lj.template("tlc:lost_castle/castle_" + part)
            root, info = to_tag(piece_blocks(piece), piece.dv)
            ident = "lost_castle_" + part
            template = write("lost_castle", ident, root)
            print("%-34s %s, %d blocks" % (ident, "x".join(map(str, info[0])), info[1]))
            entry("lost_castle", ident, "The Lost Castle, piece " + part, template, [], ["PLAINS", "TAIGA", "SNOWY"])
        notes["lost_castle"] = "The castle's nine pieces, each its own building (joined, the castle is wider than the mason's table)."

    for e in IN_PACK:
        d = {"id": e["id"], "name": e["name"].capitalize(), "source": e["template"], "kinds": e["kinds"], "lands": e["lands"],
             "author": e["author"], "licence": e["licence"], "link": e["link"], "in_pack": True}
        if e["biomes"]:
            d["biomes"] = e["biomes"]
        for k in ("ruin", "castle", "outpost"):
            if e.get(k):
                d[k] = True
        entries.append(d)

    world = []
    for row in WORLD:
        words, ident, name, kinds, lands = row[:5]
        d = {"match": words, "id": ident, "name": name, "kinds": kinds, "lands": lands}
        if len(row) > 5 and row[5]:
            d["castle"] = True
        world.append(d)

    os.makedirs(os.path.dirname(INDEX), exist_ok=True)
    with open(INDEX, "w") as f:
        json.dump({"_comment": "Written by tools/library_import.py (docs/BuildingLibrary-Plan.md); edit the script, not this file.",
                   "books": entries, "world": world}, f, indent=1)
        f.write("\n")

    # credits and licences
    os.makedirs(LICENCES, exist_ok=True)
    lines = ["# Building library credits", "",
             "Jaymo's Kingdoms' masons' pattern books (the mason's table's \"Pattern books\" list, and the buildings the town builders",
             "draw from it). Written by tools/library_import.py.", "",
             "## Shipped with Jaymo's Kingdoms", "",
             "These buildings are copied from the mods below under their own licences, with the changes noted.", ""]
    for slug in SOURCES:
        used = [e for e in entries if e.get("from") == SOURCES[slug][1]]
        if not used:
            continue
        _, title, author, licence, link = SOURCES[slug]
        lines.append("- **%s** by %s (%s), %s: %s." % (title, author, licence, link, ", ".join(e["name"] for e in used)))
        lines.append("  Changed: saved as plain structure files, air and structure voids left out, block contents (chests, signs) left out."
                     + (" " + notes[slug] if slug in notes else ""))
        text = jar(slug).licence()
        if not text:
            parts = []
            for spdx in SPDX_IDS[slug]:
                body = spdx_text(spdx)
                if spdx == "MIT":
                    body = body.replace("Copyright (c) <year> <copyright holders>", "Copyright (c) " + author)
                parts.append(body)
            text = "\n\n".join(parts)
        with open(os.path.join(LICENCES, slug + ".txt"), "w") as f:
            f.write("%s, %s\n%s\n\n%s" % (title, author, link, text))
        lines.append("  Licence text: library-licences/%s.txt." % slug)
    lines += ["", "## Read from mods already in the pack (not copied)", "",
              "- **Towns and Towers** by Kubek, Biban_Auriu and Cristelknight999 (CC BY-NC-ND 4.0), https://modrinth.com/mod/towns-and-towers: "
              "its village houses, windmill, mountain inn, watchtowers and palisade forts, read from the installed mod in the game.",
              "- **Moog's Voyager Structures** by FinnDog and Phantax (MIT; LGPL-3.0 library), https://modrinth.com/mod/moogs-voyager-structures: "
              "its cottages, deepslate hall, barn, towers, castle ruins and walled keep, read from the installed mod in the game.", "",
              "## Each world's own", "",
              "Builds downloaded by the server's owner into the world's `jaymoskingdoms_library` folder (mc-mod.net, Planet Minecraft)",
              "stay on that world: they are never shipped, and the mason's table names their authors.", ""]
    with open(CREDITS, "w") as f:
        f.write("\n".join(lines))
    print("%d pattern-book entries, %d world names" % (len(entries), len(world)))


def spdx_text(ident):
    os.makedirs(CACHE, exist_ok=True)
    path = os.path.join(CACHE, ident + ".txt")
    if not os.path.isfile(path):
        with urllib.request.urlopen(SPDX % ident) as r, open(path, "wb") as f:
            f.write(r.read())
    with open(path, encoding="utf-8") as f:
        return f.read()


def hash_seed(shape, n):
    h = 0
    for ch in shape:
        h = (h * 31 + ord(ch)) & 0xFFFFFFFF
    return h * 7 + n


if __name__ == "__main__":
    sys.exit(main())
