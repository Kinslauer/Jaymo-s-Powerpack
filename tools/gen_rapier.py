"""Draws the Armory's rapiers (0.15.2, after Just-In COMBAT's fencing weapon: its idea only, none of its art) and writes
their models, Better Combat attributes, tags, recipe and lang next to tools/armory_assets.py's (which needs the Age of
Weapons jar, so the rapier lives here instead). A thin thrusting blade on a swept hilt: a crossguard, a knuckle bow
curling down to the pommel, a leather grip. Run it from the root after armory_assets.py:

    python -I tools/gen_rapier.py

Then tools/weapon_parts_assets.py cuts the Rapier Blade (Tool Station) out of these sprites.
"""
import json
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "jaymospowers")
DATA = os.path.join(RES, "data", "jaymospowers")
MC_DATA = os.path.join(RES, "data", "minecraft")
NS = "jaymospowers"
SIZE = 16

# each tier's metal: highlight, light, mid, dark, outline (the same tones as tools/gen_cleaver_staff.py)
METAL = {
    "wooden": ((134, 101, 38), (117, 88, 33), (91, 68, 26), (55, 41, 16), (43, 32, 12)),
    "stone": ((154, 154, 154), (137, 137, 137), (119, 119, 119), (73, 73, 73), (60, 60, 60)),
    "iron": ((255, 255, 255), (216, 216, 216), (168, 168, 168), (68, 68, 68), (40, 40, 40)),
    "golden": ((255, 255, 255), (253, 255, 118), (234, 238, 87), (233, 177, 21), (63, 46, 14)),
    "diamond": ((51, 235, 203), (43, 199, 172), (35, 160, 137), (14, 63, 54), (8, 37, 32)),
    "netherite": ((134, 123, 134), (112, 103, 112), (81, 68, 78), (50, 39, 39), (35, 16, 18)),
}
# the hilt is always steel (a wooden or stone rapier still has an iron guard), but blackened on netherite
HILT = {"netherite": METAL["netherite"]}
STEEL = ((216, 216, 216), (168, 168, 168), (110, 110, 110), (68, 68, 68), (40, 40, 40))
# the grip: light, mid, dark (leather over wood)
GRIP = ((104, 78, 30), (73, 54, 21), (40, 30, 11))
NAMES = {"wooden": "Wooden", "stone": "Stone", "iron": "Iron", "golden": "Golden", "diamond": "Diamond", "netherite": "Netherite"}
# stone, iron and gold are forged; no diamond; netherite smiths; (0.16.18) Andy: no craftable wooden weapons either
MATERIAL = {}

# (x, y) -> part: "edge" (the blade's lit edge), "flat" (its shaded flat), "tip", "guard", "bow" (the knuckle bow),
# "grip", "pommel"
SHAPE = {}
for x in range(7, 15):
    SHAPE[(x, 15 - x)] = "edge"       # the blade runs corner to corner, one pixel and its shadow
    SHAPE[(x + 1, 15 - x)] = "flat"
SHAPE[(15, 0)] = "tip"
for x, y in ((3, 8), (4, 9), (6, 11), (7, 12)):
    SHAPE[(x, y)] = "guard"           # the quillons, across the blade
SHAPE[(5, 10)] = "ring"               # where blade, guard and grip meet
SHAPE[(6, 10)] = "ring"
for x, y in ((7, 13), (6, 14), (5, 14), (4, 14)):
    SHAPE[(x, y)] = "bow"             # the knuckle bow, curling down to the pommel
for x, y in ((4, 11), (3, 12), (2, 13)):
    SHAPE[(x, y)] = "grip"
for x, y in ((1, 14), (2, 14), (1, 15)):
    SHAPE[(x, y)] = "pommel"


def rapier(tier, path):
    hi, light, mid, dark, outline = METAL[tier]
    s_hi, s_light, s_mid, s_dark, s_outline = HILT.get(tier, STEEL)
    im = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    for (x, y), part in SHAPE.items():
        colour = {
            "edge": hi if (x + y) % 3 else light,  # a glint now and then along the edge
            "flat": dark,
            "tip": light,
            "guard": s_mid,
            "ring": s_hi if x == 5 else s_light,
            "bow": s_mid if x > 5 else s_dark,
            "grip": GRIP[0] if x % 2 else GRIP[1],
            "pommel": s_light if (x, y) == (1, 14) else s_dark,
        }[part]
        im.putpixel((x, y), colour + (255,))
    # the outline on the shaded side of the guard and grip, as vanilla's swords have
    for (x, y), part in list(SHAPE.items()):
        if part in ("guard", "grip", "bow"):
            nx, ny = x + 1, y + 1
            if 0 <= nx < SIZE and 0 <= ny < SIZE and (nx, ny) not in SHAPE:
                im.putpixel((nx, ny), (GRIP[2] if part == "grip" else s_outline) + (255,))
    im.save(path)


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", newline="\n") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def merge(path, update):
    """Adds `update`'s keys to a JSON object file (a lang part)."""
    with open(path) as f:
        obj = json.load(f)
    obj.update(update)
    write(path, obj)


def tag_add(path, values):
    with open(path) as f:
        obj = json.load(f)
    for v in values:
        if v not in obj["values"]:
            obj["values"].append(v)
    write(path, obj)


def main():
    lang = {"item.jaymospowers.rapier.lunge": "Right-click: lunge and run through the first foe ahead"}
    swords = []
    for tier, name in NAMES.items():
        item = f"{tier}_rapier"
        rapier(tier, os.path.join(ASSETS, "textures", "item", "armory", item + ".png"))
        write(os.path.join(ASSETS, "models", "item", "armory", item + ".json"),
              {"parent": "minecraft:item/handheld", "textures": {"layer0": f"{NS}:item/armory/{item}"}})
        write(os.path.join(ASSETS, "items", item + ".json"),
              {"model": {"type": "minecraft:model", "model": f"{NS}:item/armory/{item}"}})
        write(os.path.join(DATA, "weapon_attributes", item + ".json"), {"parent": "bettercombat:rapier"})
        lang[f"item.{NS}.{item}"] = f"{name} Rapier"
        swords.append(f"{NS}:{item}")
        if tier in MATERIAL:
            write(os.path.join(DATA, "recipe", "armory", item + ".json"), {
                "type": "minecraft:crafting_shaped", "category": "equipment",
                "key": {"X": MATERIAL[tier], "S": "minecraft:stick", "C": "minecraft:string"},
                "pattern": ["  X", " X ", "CS "], "result": {"id": f"{NS}:{item}"}})
        elif tier == "netherite":
            write(os.path.join(DATA, "recipe", "armory", item + "_smithing.json"), {
                "type": "minecraft:smithing_transform", "addition": "#minecraft:netherite_tool_materials",
                "base": f"{NS}:diamond_rapier", "result": {"id": f"{NS}:{item}"},
                "template": "minecraft:netherite_upgrade_smithing_template"})
    merge(os.path.join(ASSETS, "lang", "parts", "armory.json"), lang)
    tag_add(os.path.join(MC_DATA, "tags", "item", "swords.json"), swords)


if __name__ == "__main__":
    main()
