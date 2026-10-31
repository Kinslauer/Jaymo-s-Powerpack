"""Writes the assets of bog iron as a Tinkering material (Havens patch 2; Powers 0.18.102, Kingdoms 0.7.429):

* every iron part and tool layer recoloured rust-brown, its models, and a bog_iron case beside iron's in each item definition;
* the ingot, molten bog iron (block frames and the smeltery gauge), the hot ingot's case;
* the four armour pieces (icons from vanilla's iron ones, the worn layers from vanilla's iron layers) and their equipment asset;
* the names (lang/parts/bog_iron.json).

Bog iron's raw lumps and ore are Jaymo's Kingdoms' (it melts them through data/jaymoskingdoms/tinkering_melting/bog_iron.json).
Run after tools/tinkering_assets.py and tools/gen_steel.py, pointing at a 26.3 client jar:

    python3 -I tools/gen_bog_iron.py /path/to/26.3.jar

Safe to run again (every file is overwritten, every edit is idempotent).
"""
import colorsys
import io
import json
import os
import sys
import zipfile

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import gen_steel  # noqa: E402

ROOT = os.path.dirname(HERE)
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "jaymospowers")
NS = "jaymospowers"
ID = "bog_iron"

# bog iron: iron's shading kept, darkened, turned rust brown (more orange in the light, near black-brown in the shadows)
HUE_DARK, HUE_LIGHT, SAT_DARK, SAT_LIGHT, DIM = 0.05, 0.075, 0.70, 0.45, 0.82

LANG = {
    "item.jaymospowers.bog_iron_ingot": "Bog Iron Ingot",
    "material.jaymospowers.bog_iron": "Bog Iron",
    "molten.jaymospowers.bog_iron": "Molten Bog Iron",
    "item.jaymospowers.bog_iron_helmet": "Bog Iron Helmet",
    "item.jaymospowers.bog_iron_chestplate": "Bog Iron Chestplate",
    "item.jaymospowers.bog_iron_leggings": "Bog Iron Leggings",
    "item.jaymospowers.bog_iron_boots": "Bog Iron Boots",
}


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def bog_px(r, g, b):
    _, _, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
    v = min(1.0, v * DIM)
    h = HUE_DARK + (HUE_LIGHT - HUE_DARK) * v
    s = SAT_DARK + (SAT_LIGHT - SAT_DARK) * v
    r2, g2, b2 = colorsys.hsv_to_rgb(h, s, v)
    return round(r2 * 255), round(g2 * 255), round(b2 * 255)


def bogged(im):
    im = im.convert("RGBA")
    px = im.load()
    for y in range(im.size[1]):
        for x in range(im.size[0]):
            r, g, b, a = px[x, y]
            if a:
                px[x, y] = bog_px(r, g, b) + (a,)
    return im


def ref(model):
    return {"model": {"type": "minecraft:model", "model": model}}


def tinker_material():
    tex = os.path.join(ASSETS, "textures", "item", "tinkering")
    models = os.path.join(ASSETS, "models", "item", "tinkering")
    count = 0
    for folder in gen_steel.TINKER_DIRS:
        for name in sorted(os.listdir(os.path.join(tex, folder))):
            if not name.endswith("_iron.png") or name.endswith("_pig_iron.png") or name.endswith("_rust_iron.png") \
                    or name.endswith("_ice_iron.png") or name.endswith("_bog_iron.png"):
                continue
            base = name[:-len("_iron.png")]
            bogged(Image.open(os.path.join(tex, folder, name))).save(os.path.join(tex, folder, base + "_" + ID + ".png"))
            model = json.load(open(os.path.join(models, folder, base + "_iron.json")))
            model["textures"]["layer0"] = model["textures"]["layer0"][:-len("_iron")] + "_" + ID
            write(os.path.join(models, folder, base + "_" + ID + ".json"), model)
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
            if "iron" in whens and ID not in whens:
                iron = cases[whens.index("iron")]
                text = json.dumps(iron["model"]).replace("_iron\"", "_" + ID + "\"")
                cases.append({"when": ID, "model": json.loads(text)})
        for value in node.values():
            add_case(value)

    for item in gen_steel.TINKER_ITEMS:
        path = os.path.join(ASSETS, "items", item + ".json")
        definition = json.load(open(path))
        add_case(definition)
        write(path, definition)
    print("bog iron sprites:", count)


def molten(im):
    im = im.convert("RGBA")
    px = im.load()
    for y in range(im.size[1]):
        for x in range(im.size[0]):
            r, g, b, a = px[x, y]
            _, _, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            r2, g2, b2 = colorsys.hsv_to_rgb(0.06 + 0.03 * v, 0.85 - 0.35 * v, 0.45 + 0.5 * v)
            px[x, y] = (round(r2 * 255), round(g2 * 255), round(b2 * 255), a)
    return im


def items(jar):
    pieces = ["helmet", "chestplate", "leggings", "boots"]
    with zipfile.ZipFile(jar) as z:
        def vanilla(rel):
            return Image.open(io.BytesIO(z.read("assets/minecraft/textures/" + rel + ".png"))).convert("RGBA")

        tink = os.path.join(ASSETS, "textures", "item", "tinkering")
        bogged(vanilla("item/iron_ingot")).save(os.path.join(tink, ID + "_ingot.png"))
        armour = os.path.join(ASSETS, "textures", "item", "armor")
        os.makedirs(armour, exist_ok=True)
        for piece in pieces:
            bogged(vanilla("item/iron_" + piece)).save(os.path.join(armour, ID + "_" + piece + ".png"))
        eq = os.path.join(ASSETS, "textures", "entity", "equipment")
        for layer in ("humanoid", "humanoid_leggings"):
            os.makedirs(os.path.join(eq, layer), exist_ok=True)
            bogged(vanilla("entity/equipment/" + layer + "/iron")).save(os.path.join(eq, layer, ID + ".png"))
    block_dir = os.path.join(ASSETS, "textures", "block", "tinkering")
    src = os.path.join(block_dir, "molten", "iron.png")
    hot = molten(Image.open(src))
    hot.save(os.path.join(block_dir, "molten", ID + ".png"))
    with open(src + ".mcmeta") as f:
        meta = f.read()
    with open(os.path.join(block_dir, "molten", ID + ".png.mcmeta"), "w", encoding="utf-8", newline="\n") as f:
        f.write(meta)
    hot.crop((0, 0, 16, 16)).save(os.path.join(ASSETS, "textures", "gui", "tinkering", "molten", ID + ".png"))

    write(os.path.join(ASSETS, "models", "item", "tinkering", ID + "_ingot.json"),
          {"parent": "minecraft:item/generated", "textures": {"layer0": NS + ":item/tinkering/" + ID + "_ingot"}})
    write(os.path.join(ASSETS, "items", ID + "_ingot.json"), ref(NS + ":item/tinkering/" + ID + "_ingot"))
    for piece in pieces:
        write(os.path.join(ASSETS, "models", "item", "armor", ID + "_" + piece + ".json"),
              {"parent": "minecraft:item/generated", "textures": {"layer0": NS + ":item/armor/" + ID + "_" + piece}})
        write(os.path.join(ASSETS, "items", ID + "_" + piece + ".json"), ref(NS + ":item/armor/" + ID + "_" + piece))
    write(os.path.join(ASSETS, "equipment", ID + ".json"),
          {"layers": {"humanoid": [{"texture": NS + ":" + ID}], "humanoid_leggings": [{"texture": NS + ":" + ID}]}})
    hot_path = os.path.join(ASSETS, "items", "hot_ingot.json")
    hot_def = json.load(open(hot_path))
    hot_cases = hot_def["model"]["cases"]
    if not any(c["when"] == ID for c in hot_cases):
        last = hot_cases[-1]
        hot_cases.append({"when": ID, "model": json.loads(json.dumps(last["model"]).replace(last["when"] + "_ingot", ID + "_ingot"))})
        write(hot_path, hot_def)


def main():
    if len(sys.argv) != 2 or not os.path.isfile(sys.argv[1]):
        sys.exit(__doc__)
    tinker_material()
    items(sys.argv[1])
    write(os.path.join(ASSETS, "lang", "parts", "bog_iron.json"), LANG)


if __name__ == "__main__":
    main()
