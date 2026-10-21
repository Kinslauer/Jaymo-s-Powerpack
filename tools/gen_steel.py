"""Writes steel's assets and data (Smithing): the Tinkering material (every part and tool sprite, model and item-model
case), molten steel, the steel ingot, nugget, block and crucible blend, their recipes, loot and tags, and the armory's
broadhead arrow. Steel is cold blue-grey: the textures are recoloured from the iron ones already in the repository
(Tinkers' iron sprites, which tools/tinkering_assets.py writes) and from vanilla's iron icons, so point it at a 26.3
jar that holds assets/minecraft (the client jar, or Loom's merged jar):

    python -I tools/gen_steel.py ~/.gradle/caches/fabric-loom/26.3/minecraft-client.jar

Run it again after tools/tinkering_assets.py (that one wipes the Tinkering textures and models and rewrites
lang/parts/tinkering.json). The ids must match com.jaymospowers.tinkering.Tinkering.registerSteel and
com.jaymospowers.armory.Armory (broadhead_arrow).
"""
import colorsys
import io
import json
import os
import sys
import zipfile

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "jaymospowers")
DATA = os.path.join(RES, "data", "jaymospowers")
NS = "jaymospowers"

# Tinkers' folders whose *_iron sprites are material layers (weapon heads come in iron, gold and copper only)
TINKER_DIRS = ["part", "pickaxe", "hand_axe", "mattock", "sword", "sledge_hammer", "sledge_hammer/large", "cleaver",
               "cleaver/large"]
# item definitions that pick a model by material
TINKER_ITEMS = ["pick_head", "small_axe_head", "adze_head", "small_blade", "hammer_head", "broad_blade", "large_plate",
                "tool_handle", "tough_handle", "tool_binding", "crossguard", "tinker_pickaxe", "tinker_hand_axe",
                "tinker_mattock", "tinker_sword", "tinker_sledge_hammer", "tinker_cleaver"]
# steel's hue (a cold blue), how dark it is next to iron, and how much colour its shadows take
HUE, DARKEN, SAT_LIGHT, SAT_DARK = 0.6, 0.78, 0.07, 0.2
# coal in the crucible blend
COAL = [(26, 26, 28), (43, 43, 46)]

LANG_TINKERING = {
    "material.jaymospowers.steel": "Steel",
    "molten.jaymospowers.steel": "Molten Steel",
    "item.jaymospowers.steel_ingot": "Steel Ingot",
    "item.jaymospowers.steel_nugget": "Steel Nugget",
    "item.jaymospowers.steel_blend": "Crucible Steel Blend",
    "block.jaymospowers.steel_block": "Block of Steel",
    "trait.jaymospowers.tempered": "Tempered",
    "trait.jaymospowers.tempered.desc": "Often takes a knock without wearing, and bites through armour.",
    "tooltip.jaymospowers.tinkering.steel_blend": "Fire it in a brick kiln over a forge hearth (heat 2) for a steel ingot.",
    "tooltip.jaymospowers.tinkering.forge_hearth": "Burns coal or charcoal hot enough for netherrack, grout and crucible steel.",
}
LANG_ARMORY = {
    "item.jaymospowers.broadhead_arrow": "Broadhead Arrow",
    "item.jaymospowers.broadhead_arrow.hint": "Cuts deep and draws blood; made for the recurve bow's draw.",
}


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def steel_px(r, g, b, darken=DARKEN):
    """One pixel of iron as steel: its shading kept, a little darker, with a cold blue cast deeper in the shadows."""
    _, _, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
    v = min(1.0, v * darken)
    r2, g2, b2 = colorsys.hsv_to_rgb(HUE, SAT_LIGHT + SAT_DARK * (1 - v), v)
    return round(r2 * 255), round(g2 * 255), round(b2 * 255)


def steeled(im, darken=DARKEN, keep=None):
    """Every opaque pixel recoloured to steel (except those {@code keep(x, y, rgb)} says to leave)."""
    im = im.convert("RGBA")
    px = im.load()
    for y in range(im.size[1]):
        for x in range(im.size[0]):
            r, g, b, a = px[x, y]
            if a == 0 or keep and keep(x, y, (r, g, b)):
                continue
            px[x, y] = steel_px(r, g, b, darken) + (a,)
    return im


def molten(im):
    """Molten iron as molten steel: white-hot at the bright end, orange in the dark."""
    im = im.convert("RGBA")
    px = im.load()
    for y in range(im.size[1]):
        for x in range(im.size[0]):
            r, g, b, a = px[x, y]
            _, _, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            r2, g2, b2 = colorsys.hsv_to_rgb(0.04 + 0.08 * v, 0.95 - 0.5 * v, 0.7 + 0.3 * v)
            px[x, y] = (round(r2 * 255), round(g2 * 255), round(b2 * 255), a)
    return im


def tinker_material():
    """Every iron part and tool layer as steel, its model, and a steel case beside iron's in each item definition."""
    tex = os.path.join(ASSETS, "textures", "item", "tinkering")
    models = os.path.join(ASSETS, "models", "item", "tinkering")
    count = 0
    for folder in TINKER_DIRS:
        for name in sorted(os.listdir(os.path.join(tex, folder))):
            if not name.endswith("_iron.png"):
                continue
            base = name[:-len("_iron.png")]
            steeled(Image.open(os.path.join(tex, folder, name))).save(os.path.join(tex, folder, base + "_steel.png"))
            model = json.load(open(os.path.join(models, folder, base + "_iron.json")))
            model["textures"]["layer0"] = model["textures"]["layer0"][:-len("_iron")] + "_steel"
            write(os.path.join(models, folder, base + "_steel.json"), model)
            count += 1

    def add_case(node):
        if isinstance(node, list):
            for child in node:
                add_case(child)
            return
        if not isinstance(node, dict):
            return
        cases = node.get("cases")
        if node.get("property") == "minecraft:custom_model_data" and isinstance(cases, list):
            whens = [c.get("when") for c in cases]
            if "iron" in whens and "steel" not in whens:
                iron = cases[whens.index("iron")]
                text = json.dumps(iron["model"]).replace("_iron\"", "_steel\"")
                cases.append({"when": "steel", "model": json.loads(text)})
        for value in node.values():
            add_case(value)

    for item in TINKER_ITEMS:
        path = os.path.join(ASSETS, "items", item + ".json")
        definition = json.load(open(path))
        add_case(definition)
        write(path, definition)
    print("steel sprites:", count)


def items(jar):
    with zipfile.ZipFile(jar) as z:
        def vanilla(rel):
            return Image.open(io.BytesIO(z.read("assets/minecraft/textures/" + rel + ".png"))).convert("RGBA")

        out = os.path.join(ASSETS, "textures", "item", "tinkering")
        steeled(vanilla("item/iron_ingot")).save(os.path.join(out, "steel_ingot.png"))
        steeled(vanilla("item/iron_nugget")).save(os.path.join(out, "steel_nugget.png"))
        # the blend: raw iron gone grey, flecked with coal
        blend = steeled(vanilla("item/raw_iron"), 0.8)
        px = blend.load()
        for y in range(1, 15):
            for x in range(1, 15):
                inside = all(px[x + dx, y + dy][3] for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
                if inside and (x * 5 + y * 3) % 7 == 0:
                    px[x, y] = COAL[(x + y) % 2] + (255,)
        blend.save(os.path.join(out, "steel_blend.png"))
        block = os.path.join(ASSETS, "textures", "block", "tinkering", "steel_block.png")
        steeled(vanilla("block/iron_block"), 0.72).save(block)
        # the broadhead: vanilla's arrow, its head in steel and a barb wider each side (the feathers stay)
        arrow = vanilla("item/arrow")
        apx = arrow.load()
        head = {(x, y) for y in range(2, 8) for x in range(9, 15) if apx[x, y][3] and apx[x, y][0] == apx[x, y][1] == apx[x, y][2]}
        for x, y, c in ((9, 3, (150, 150, 150)), (9, 4, (68, 68, 68)), (13, 6, (150, 150, 150)), (12, 7, (68, 68, 68))):
            apx[x, y] = c + (255,)
            head.add((x, y))
        arrow = steeled(arrow, keep=lambda x, y, rgb: (x, y) not in head)
        armory = os.path.join(ASSETS, "textures", "item", "armory")
        os.makedirs(armory, exist_ok=True)
        arrow.save(os.path.join(armory, "broadhead_arrow.png"))

        # molten steel: molten iron's frames, white-hot
        src = os.path.join(ASSETS, "textures", "block", "tinkering", "molten", "iron.png")
        hot = molten(Image.open(src))
        hot.save(os.path.join(os.path.dirname(src), "steel.png"))
        with open(src + ".mcmeta") as f:
            meta = f.read()
        with open(os.path.join(os.path.dirname(src), "steel.png.mcmeta"), "w", encoding="utf-8", newline="\n") as f:
            f.write(meta)
        hot.crop((0, 0, 16, 16)).save(os.path.join(ASSETS, "textures", "gui", "tinkering", "molten", "steel.png"))

    ref = lambda name: {"model": {"type": "minecraft:model", "model": NS + ":" + name}}
    for item in ("steel_ingot", "steel_nugget", "steel_blend"):
        write(os.path.join(ASSETS, "models", "item", "tinkering", item + ".json"),
              {"parent": "minecraft:item/generated", "textures": {"layer0": NS + ":item/tinkering/" + item}})
        write(os.path.join(ASSETS, "items", item + ".json"), ref("item/tinkering/" + item))
    write(os.path.join(ASSETS, "models", "block", "tinkering", "steel_block.json"),
          {"parent": "minecraft:block/cube_all", "textures": {"all": NS + ":block/tinkering/steel_block"}})
    write(os.path.join(ASSETS, "blockstates", "steel_block.json"), {"variants": {"": {"model": NS + ":block/tinkering/steel_block"}}})
    write(os.path.join(ASSETS, "items", "steel_block.json"), ref("block/tinkering/steel_block"))
    write(os.path.join(ASSETS, "models", "item", "armory", "broadhead_arrow.json"),
          {"parent": "minecraft:item/generated", "textures": {"layer0": NS + ":item/armory/broadhead_arrow"}})
    write(os.path.join(ASSETS, "items", "broadhead_arrow.json"), ref("item/armory/broadhead_arrow"))


def data():
    out = os.path.join(DATA, "recipe", "tinkering")
    ingot, nugget, block = NS + ":steel_ingot", NS + ":steel_nugget", NS + ":steel_block"
    for small, big, name in ((ingot, block, "steel_block"), (nugget, ingot, "steel_ingot_from_nuggets")):
        write(os.path.join(out, name + ".json"), {
            "type": "minecraft:crafting_shaped", "category": "misc", "key": {"#": small}, "pattern": ["###", "###", "###"],
            "result": {"id": big}})
    for big, small, name in ((block, ingot, "steel_ingot_from_block"), (ingot, nugget, "steel_nugget")):
        write(os.path.join(out, name + ".json"), {
            "type": "minecraft:crafting_shapeless", "category": "misc", "ingredients": [big], "result": {"id": small, "count": 9}})
    write(os.path.join(out, "steel_blend.json"), {
        "type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": ["minecraft:iron_ingot", "minecraft:iron_ingot", "#minecraft:coals"], "result": {"id": NS + ":steel_blend"}})
    # three steel nuggets beaten into a broad head, on a stick, fletched: four arrows
    write(os.path.join(DATA, "recipe", "armory", "broadhead_arrow.json"), {
        "type": "minecraft:crafting_shaped", "category": "equipment",
        "key": {"N": "#c:nuggets/steel", "S": "minecraft:stick", "F": "minecraft:feather"},
        "pattern": ["NNN", " S ", " F "], "result": {"id": NS + ":broadhead_arrow", "count": 4}})
    write(os.path.join(DATA, "loot_table", "blocks", "steel_block.json"), {
        "type": "minecraft:block", "random_sequence": NS + ":blocks/steel_block", "pools": [{
            "rolls": 1, "entries": [{"type": "minecraft:item", "name": block}],
            "condition": {"type": "minecraft:survives_explosion"}}]})
    c = os.path.join(RES, "data", "c", "tags")
    write(os.path.join(c, "item", "ingots", "steel.json"), {"values": [ingot]})
    write(os.path.join(c, "item", "nuggets", "steel.json"), {"values": [nugget]})
    write(os.path.join(c, "item", "storage_blocks", "steel.json"), {"values": [block]})
    write(os.path.join(c, "block", "storage_blocks", "steel.json"), {"values": [block]})
    mc = os.path.join(RES, "data", "minecraft", "tags")
    for tag, value in (("block/mineable/pickaxe", block), ("block/needs_iron_tool", block), ("item/arrows", NS + ":broadhead_arrow")):
        path = os.path.join(mc, tag + ".json")
        old = json.load(open(path)) if os.path.exists(path) else {"values": []}
        if value not in old["values"]:
            old["values"].append(value)
        write(path, old)


def lang():
    for part, keys in (("tinkering", LANG_TINKERING), ("armory", LANG_ARMORY)):
        path = os.path.join(ASSETS, "lang", "parts", part + ".json")
        table = json.load(open(path, encoding="utf-8"))
        table.update(keys)
        write(path, table)


def main():
    if len(sys.argv) != 2 or not os.path.isfile(sys.argv[1]):
        sys.exit(__doc__)
    tinker_material()
    items(sys.argv[1])
    data()
    lang()


if __name__ == "__main__":
    main()
