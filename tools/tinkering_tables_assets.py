"""Writes the assets and data of the extra Tinkers tables (com.jaymospowers.tinkering.table): the Tool Forge, the Crafting
Station, the Pattern Chest and the Part Chest, after Tinkers' Construct (SlimeKnights, MIT; see
assets/jaymospowers/textures/item/tinkering/license_tinkers_construct.txt). Point it at the unpacked Tinkers' Construct
1.20.1 sources' resources (the folder that holds assets/tconstruct, with models and textures):

    python3 -I tools/tinkering_tables_assets.py /home/user/ref/tc/src/main/resources

Separate from tinkering_assets.py on purpose. It does not wipe anything, so the two can run in either order, except that
tinkering_assets.py writes data/minecraft/tags/block/mineable/axe.json outright: run this one after it (this merges into
whatever is there).
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
T = NS + ":block/tinkering/"

# Tinkers' chest folder -> our block
CHESTS = {"pattern_chest": "cast", "part_chest": "part"}
CHEST_TEXTURES = ("side", "front", "top", "drawer_front", "drawer_side")
FORGE_METALS = ["minecraft:iron_block", "minecraft:copper_block", NS + ":steel_block", NS + ":silver_block"]

LANG = {
    "block.jaymospowers.tool_forge": "Tool Forge",
    "block.jaymospowers.crafting_station": "Crafting Station",
    "block.jaymospowers.pattern_chest": "Pattern Chest",
    "block.jaymospowers.part_chest": "Part Chest",
    "container.jaymospowers.tool_forge": "Tool Forge",
    "container.jaymospowers.crafting_station": "Crafting Station",
    "container.jaymospowers.pattern_chest": "Pattern Chest",
    "container.jaymospowers.part_chest": "Part Chest",
    "gui.jaymospowers.tables.side": "Nearby chest",
    "gui.jaymospowers.tool_station.needs_forge": "Needs a Tool Forge",
    "gui.jaymospowers.tool_station.forge_hint": "Tools of more than 3 parts are made at a Tool Forge",
}


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        if isinstance(data, str):
            f.write(data)
        else:
            json.dump(data, f, indent=2)
            f.write("\n")


def copy_png(src, dest_rel):
    dest = os.path.join(ASSETS, "textures", dest_rel + ".png")
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    shutil.copyfile(src, dest)


def merge_tag(path, values):
    """Adds values to a tag file, keeping what is there."""
    old = json.load(open(path, encoding="utf-8")) if os.path.exists(path) else {"values": []}
    for v in values:
        if v not in old["values"]:
            old["values"].append(v)
    write(path, old)


def load(tcon, *rel):
    return json.load(open(os.path.join(tcon, "assets", "tconstruct", "models", "block", *rel), encoding="utf-8"))


def block_item(name):
    write(os.path.join(ASSETS, "items", name + ".json"), {"model": {"type": "minecraft:model", "model": T + name}})


def blocks(tcon):
    tex = os.path.join(tcon, "assets", "tconstruct", "textures", "block")
    # the Tool Forge: Tinkers' anvil, with iron on top where Tinkers has the metal it was made of
    for name in ("anvil_base", "anvil_base_upper", "crafting_station_top", "crafting_station_side"):
        copy_png(os.path.join(tex, "table", name + ".png"), "block/tinkering/" + name)
    anvil = load(tcon, "table", "tinkers_anvil.json")
    for key in ("loader", "retextured"):
        anvil.pop(key, None)
    anvil["textures"] = {"particle": "minecraft:block/iron_block", "top": "minecraft:block/iron_block",
                         "base": T + "anvil_base", "upper": T + "anvil_base_upper"}
    write(os.path.join(ASSETS, "models", "block", "tinkering", "tool_forge.json"), anvil)
    write(os.path.join(ASSETS, "blockstates", "tool_forge.json"), {"variants": {"": {"model": T + "tool_forge"}}})
    block_item("tool_forge")
    # the Crafting Station: our table model (the Part Builder's, from tinkering_assets.py) in Tinkers' crafting station textures
    write(os.path.join(ASSETS, "models", "block", "tinkering", "crafting_station.json"), {
        "parent": T + "table",
        "textures": {"particle": T + "crafting_station_top", "top": T + "crafting_station_top", "side": T + "crafting_station_side",
                     "leg": T + "crafting_station_side", "bottom": T + "bottom", "legBottom": T + "bottom"}})
    write(os.path.join(ASSETS, "blockstates", "crafting_station.json"), {"variants": {"": {"model": T + "crafting_station"}}})
    block_item("crafting_station")
    # the chests: Tinkers' drawer chest model, its cast chest's textures for patterns and casts and its part chest's for parts
    display = load(tcon, "table", "chest", "chest_display.json")
    write(os.path.join(ASSETS, "models", "block", "tinkering", "chest_display.json"), display)
    chest = load(tcon, "table", "chest", "chest.json")
    chest["parent"] = T + "chest_display"
    write(os.path.join(ASSETS, "models", "block", "tinkering", "chest.json"), chest)
    for block, folder in CHESTS.items():
        textures = {}
        for name in CHEST_TEXTURES:
            src = os.path.join(tex, "chest", folder, name + ".png")
            if not os.path.exists(src):
                src = os.path.join(tex, "chest", folder, "side.png")  # the cast chest has no separate top
            copy_png(src, "block/tinkering/" + block + "_" + name)
            textures[name] = T + block + "_" + name
        write(os.path.join(ASSETS, "models", "block", "tinkering", block + ".json"), {"parent": T + "chest", "textures": textures})
        # the front is the model's north face; facing is where the front looks
        write(os.path.join(ASSETS, "blockstates", block + ".json"), {"variants": {
            "facing=north": {"model": T + block},
            "facing=east": {"model": T + block, "y": 90},
            "facing=south": {"model": T + block, "y": 180},
            "facing=west": {"model": T + block, "y": 270}}})
        block_item(block)


def shaped(name, pattern, key, result):
    write(os.path.join(DATA, "recipe", "tinkering", name + ".json"), {
        "type": "minecraft:crafting_shaped", "category": "misc", "key": key, "pattern": pattern, "result": {"id": NS + ":" + result}})


def data():
    # Tinkers' anvil: three blocks of metal over the station on seared bricks
    shaped("tool_forge", ["mmm", " t ", "sss"],
           {"m": "#" + NS + ":forge_metals", "t": NS + ":tool_station", "s": NS + ":seared_bricks"}, "tool_forge")
    # not a shaped pattern over a crafting table: that would also be the Tool Station's recipe (a pattern and a crafting table)
    write(os.path.join(DATA, "recipe", "tinkering", "crafting_station.json"), {
        "type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": ["minecraft:crafting_table", NS + ":pattern", "#minecraft:planks"],
        "result": {"id": NS + ":crafting_station"}})
    # Tinkers' cast chest and part chest, the cast chest with a pattern where it has a gold cast
    shaped("pattern_chest", [" p ", "bCb", "bBb"],
           {"p": NS + ":pattern", "b": NS + ":seared_brick", "C": "minecraft:chest", "B": NS + ":seared_bricks"}, "pattern_chest")
    shaped("part_chest", [" p ", "sCs", "sws"],
           {"p": NS + ":pattern", "s": "minecraft:stick", "C": "minecraft:chest", "w": "#minecraft:planks"}, "part_chest")
    write(os.path.join(DATA, "tags", "item", "forge_metals.json"), {"values": FORGE_METALS})
    for block in ("tool_forge", "crafting_station", "pattern_chest", "part_chest"):
        write(os.path.join(DATA, "loot_table", "blocks", block + ".json"), {
            "type": "minecraft:block", "random_sequence": NS + ":blocks/" + block,
            "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": NS + ":" + block}],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    merge_tag(os.path.join(MC_DATA, "tags", "block", "mineable", "pickaxe.json"), [NS + ":tool_forge"])
    merge_tag(os.path.join(MC_DATA, "tags", "block", "mineable", "axe.json"),
              [NS + ":crafting_station", NS + ":pattern_chest", NS + ":part_chest"])


def lang():
    write(os.path.join(ASSETS, "lang", "parts", "tinkering_tables.json"), LANG)


def main():
    if len(sys.argv) != 2 or not os.path.isdir(os.path.join(sys.argv[1], "assets", "tconstruct", "models", "block", "table")):
        sys.exit(__doc__)
    blocks(sys.argv[1])
    data()
    lang()


if __name__ == "__main__":
    main()
