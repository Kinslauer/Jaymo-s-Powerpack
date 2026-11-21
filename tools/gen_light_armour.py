"""Writes the assets, data and lang of the light armour sets (docs/LightArmour-Plan.md, patch 1) for Jaymo's Powers:

* mailed leather (Mailed Cap, Jack, Chausses, Boots): a leather piece, mail sheets and string, shapeless;
* studded leather (Studded Cap, Brigandine, Leggings, Boots): a leather piece, iron nuggets and a hammer;
* both dyeable: the item icon and the worn layer are vanilla leather's own (referenced, not copied), dyed, with our own
  overlay drawn on top (mail links, iron studs), laid out over the shape of vanilla's leather;
* their dye recipes, tags (armour kinds, repair items, enchanting slots, the cauldron) and names.

The overlays are stencilled on vanilla leather's shapes, so point it at the 26.3 client jar:

    python3 -I tools/gen_light_armour.py ~/.gradle/caches/fabric-loom/26.3/minecraft-client.jar

The ids must match com.jaymospowers.tinkering.smithing.LightArmour. Safe to run again (every file is overwritten, every
edit to a shared tag is idempotent).
"""
import io
import json
import os
import sys
import zipfile

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "jaymospowers")
DATA = os.path.join(RES, "data")
KITCHEN_DATA = os.path.join(ROOT, "kitchen", "src", "main", "resources", "data")
NS = "jaymospowers"

# ---- tuning ----
# (slot, vanilla leather piece, mailed id, mailed name, mail sheets, studded id, studded name, iron nuggets)
PIECES = [
    ("head", "helmet", "mailed_cap", "Mailed Cap", 2, "studded_cap", "Studded Cap", 3),
    ("chest", "chestplate", "mailed_jack", "Mailed Jack", 4, "studded_brigandine", "Studded Brigandine", 6),
    ("legs", "leggings", "mailed_chausses", "Mailed Chausses", 3, "studded_leggings", "Studded Leggings", 5),
    ("feet", "boots", "mailed_boots", "Mailed Boots", 2, "studded_boots", "Studded Boots", 2),
]
SETS = {"mailed_leather": "mailed", "studded_leather": "studded"}
UNDYED = -6265536  # vanilla leather's undyed colour

# mail links (light, mid, dark) and iron studs (bright, shadow)
LINK = ((196, 200, 208, 255), (126, 130, 140, 255), (84, 86, 94, 255))
STUD = ((226, 228, 234, 255), (96, 98, 106, 255))


def write_json(path, data, indent=2):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", newline="\n") as f:
        json.dump(data, f, indent=indent)
        f.write("\n")


def add_to_tag(path, values):
    """Adds values to a tag file (created if it isn't there), keeping what it has and how it is indented."""
    have, indent = {"replace": False, "values": []}, 2
    if os.path.exists(path):
        with open(path) as f:
            text = f.read()
        have = json.loads(text)
        indent = "\t" if "\n\t" in text else 2

    def key(v):
        return v["id"] if isinstance(v, dict) else v

    for v in values:
        if key(v) not in [key(h) for h in have["values"]]:
            have["values"].append(v)
    write_json(path, have, indent)


def mail_overlay(mask):
    out = Image.new("RGBA", mask.size, (0, 0, 0, 0))
    px, mp = out.load(), mask.load()
    for y in range(mask.height):
        for x in range(mask.width):
            if mp[x, y][3] == 0:
                continue
            cx, cy = (x + (1 if (y // 3) % 2 else 0)) % 3, y % 3
            if (cx, cy) == (1, 1):
                continue
            px[x, y] = LINK[0] if cx + cy <= 1 else LINK[1] if cx + cy == 2 else LINK[2]
    return out


def stud_overlay(mask):
    out = Image.new("RGBA", mask.size, (0, 0, 0, 0))
    px, mp = out.load(), mask.load()
    for y in range(mask.height):
        for x in range(mask.width):
            if mp[x, y][3] and x % 4 == 1 and y % 4 == 1:
                px[x, y] = STUD[0]
                if x + 1 < mask.width and y + 1 < mask.height and mp[x + 1, y + 1][3]:
                    px[x + 1, y + 1] = STUD[1]
    return out


def main(jar_path):
    jar = zipfile.ZipFile(jar_path)

    def vanilla(path):
        return Image.open(io.BytesIO(jar.read("assets/minecraft/textures/" + path))).convert("RGBA")

    drawers = {"mailed": mail_overlay, "studded": stud_overlay}
    lang = {}
    mailed_tag, studded_tag, armour_slots = [], [], {"head": [], "chest": [], "legs": [], "feet": []}

    for set_id, kind in SETS.items():
        # worn layers: over vanilla leather's own two (body and leggings) shapes
        for folder in ("humanoid", "humanoid_leggings"):
            mask = vanilla("entity/equipment/%s/leather.png" % folder)
            path = os.path.join(ASSETS, "textures", "entity", "equipment", folder, set_id + "_overlay.png")
            os.makedirs(os.path.dirname(path), exist_ok=True)
            drawers[kind](mask).save(path)
        layers = [{"dyeable": {"color_when_undyed": UNDYED}, "texture": "minecraft:leather"},
                  {"texture": "%s:%s_overlay" % (NS, set_id)}]
        write_json(os.path.join(ASSETS, "equipment", set_id + ".json"),
                   {"layers": {"humanoid": layers, "humanoid_leggings": layers}})

    for slot, piece, m_id, m_name, sheets, s_id, s_name, nuggets in PIECES:
        leather = "minecraft:leather_" + piece
        mask = vanilla("item/leather_%s.png" % piece)
        for kind, item_id, name in (("mailed", m_id, m_name), ("studded", s_id, s_name)):
            lang["item.%s.%s" % (NS, item_id)] = name
            armour_slots[slot].append("%s:%s" % (NS, item_id))
            (mailed_tag if kind == "mailed" else studded_tag).append("%s:%s" % (NS, item_id))
            # icon: vanilla's leather piece, dyed, with our overlay over it
            path = os.path.join(ASSETS, "textures", "item", "armor", item_id + "_overlay.png")
            os.makedirs(os.path.dirname(path), exist_ok=True)
            drawers[kind](mask).save(path)
            write_json(os.path.join(ASSETS, "models", "item", "armor", item_id + ".json"), {
                "parent": "minecraft:item/generated",
                "textures": {"layer0": "minecraft:item/leather_" + piece, "layer1": "%s:item/armor/%s_overlay" % (NS, item_id)}})
            write_json(os.path.join(ASSETS, "items", item_id + ".json"), {"model": {
                "type": "minecraft:model", "model": "%s:item/armor/%s" % (NS, item_id),
                "tints": [{"type": "minecraft:dye", "default": UNDYED}]}})
            # dyeing, as vanilla's leather
            write_json(os.path.join(DATA, NS, "recipe", "light_armour", item_id + "_dyed.json"), {
                "type": "minecraft:crafting_dye", "dye": "#minecraft:dyes", "group": "dyed_armor",
                "result": {"id": "%s:%s" % (NS, item_id)}, "target": "%s:%s" % (NS, item_id)})
        write_json(os.path.join(DATA, NS, "recipe", "light_armour", m_id + ".json"), {
            "type": NS + ":light_armour_crafting", "category": "equipment",
            "ingredients": [leather] + ["%s:mail_sheet" % NS] * sheets + ["minecraft:string"],
            "result": {"id": "%s:%s" % (NS, m_id)}})
        write_json(os.path.join(DATA, NS, "recipe", "light_armour", s_id + ".json"), {
            "type": NS + ":light_armour_crafting", "category": "equipment",
            "ingredients": [leather] + ["minecraft:iron_nugget"] * nuggets + ["#%s:hammers" % NS],
            "result": {"id": "%s:%s" % (NS, s_id)}})

    tags = os.path.join(DATA, NS, "tags", "item")
    mc_tags = os.path.join(DATA, "minecraft", "tags", "item")
    add_to_tag(os.path.join(tags, "mail_armor.json"), mailed_tag)
    add_to_tag(os.path.join(tags, "cloth_armor.json"), mailed_tag + studded_tag)
    add_to_tag(os.path.join(tags, "studded_armor.json"), studded_tag)
    add_to_tag(os.path.join(tags, "repairs_mailed_leather_armor.json"), ["%s:mail_ring" % NS, "minecraft:leather"])
    add_to_tag(os.path.join(tags, "repairs_studded_leather_armor.json"), ["minecraft:iron_nugget", "minecraft:leather"])
    for slot, tag in (("head", "head_armor"), ("chest", "chest_armor"), ("legs", "leg_armor"), ("feet", "foot_armor")):
        add_to_tag(os.path.join(mc_tags, tag + ".json"), armour_slots[slot])
    add_to_tag(os.path.join(mc_tags, "cauldron_can_remove_dye.json"), mailed_tag + studded_tag)
    # Jaymo's Kitchen reads these as leather (optional entries: it loads without Powers)
    add_to_tag(os.path.join(KITCHEN_DATA, "jaymoskitchen", "tags", "item", "hide_armour.json"),
               [{"id": v, "required": False} for v in mailed_tag + studded_tag])

    write_json(os.path.join(ASSETS, "lang", "parts", "light_armour.json"), lang)
    print("light armour written")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
