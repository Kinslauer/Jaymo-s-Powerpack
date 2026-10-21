"""Steel plate armour (Smithing): vanilla's iron armour, icons and worn layers, recoloured to cold blue-grey steel
(the same steel as Jaymo's Powers' tools/gen_steel.py), with darker rivets down the plates. Writes the textures, item
models and definitions, the equipment asset, recipes, the #c:leather_straps tag, the heavy_armor tag entries and the
smeltery melting entries (steel only; the straps don't melt). Point it at a 26.3 jar that holds assets/minecraft:

    python -I tools/gen_steel_plate.py ~/.gradle/caches/fabric-loom/26.3/minecraft-client.jar   (from kingdoms/)

The ids must match com.jaymoskingdoms.Armory (steel_plate_*). Lang is in lang/en_us.json.
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
ASSETS = os.path.join(RES, "assets", "jaymoskingdoms")
DATA = os.path.join(RES, "data", "jaymoskingdoms")
NS = "jaymoskingdoms"
PIECES = ["helmet", "chestplate", "leggings", "boots"]
# steel's hue, how dark it is next to iron, and how much colour its shadows take (as in tools/gen_steel.py)
HUE, DARKEN, SAT_LIGHT, SAT_DARK = 0.6, 0.78, 0.07, 0.2
# icons: rivets (x, y) on each piece, a shade darker than the plate under them
RIVETS = {"helmet": [(4, 4), (11, 4)], "chestplate": [(5, 5), (10, 5), (7, 10), (8, 10)],
          "leggings": [(5, 3), (10, 3)], "boots": [(4, 9), (11, 9)]}
# ingots in each piece (the cheaper strapped recipe, so melting never pays more than it took)
STRAPPED_INGOTS = {"helmet": 4, "chestplate": 6, "leggings": 5, "boots": 2}
INGOT_MB = 90
# recipes: S steel, L strap (or leather); strapped is vanilla's pattern with straps for some plates, the leather
# fallback vanilla's full pattern plus a leather lining
STRAPPED = {"helmet": ["SLS", "S S"], "chestplate": ["L L", "SSS", "SSS"], "leggings": ["SSS", "L L", "S S"],
            "boots": ["L L", "S S"]}
LEATHER = {"helmet": ["SSS", "SLS"], "chestplate": ["SLS", "SSS", "SSS"], "leggings": ["SSS", "SLS", "S S"],
           "boots": ["S S", "SLS"]}
STRAPS = [{"id": "jaymoskitchen:leather_straps", "required": False}, {"id": "jaymoskitchen:straps", "required": False}]


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def steeled(im, darken=DARKEN):
    im = im.convert("RGBA")
    px = im.load()
    for y in range(im.size[1]):
        for x in range(im.size[0]):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            _, _, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            v = min(1.0, v * darken)
            r2, g2, b2 = colorsys.hsv_to_rgb(HUE, SAT_LIGHT + SAT_DARK * (1 - v), v)
            px[x, y] = (round(r2 * 255), round(g2 * 255), round(b2 * 255), a)
    return im


def rivet(im, spots):
    px = im.load()
    for x, y in spots:
        r, g, b, a = px[x, y]
        if a:
            px[x, y] = (r * 3 // 5, g * 3 // 5, b * 3 // 5, a)
    return im


def textures(jar):
    with zipfile.ZipFile(jar) as z:
        def vanilla(rel):
            return Image.open(io.BytesIO(z.read("assets/minecraft/textures/" + rel + ".png"))).convert("RGBA")

        for piece in PIECES:
            icon = rivet(steeled(vanilla("item/iron_" + piece)), RIVETS[piece])
            icon.save(os.path.join(ASSETS, "textures", "item", "steel_plate_" + piece + ".png"))
        for layer in ("humanoid", "humanoid_leggings"):
            dest = os.path.join(ASSETS, "textures", "entity", "equipment", layer, "steel_plate.png")
            os.makedirs(os.path.dirname(dest), exist_ok=True)
            steeled(vanilla("entity/equipment/" + layer + "/iron")).save(dest)


def assets():
    for piece in PIECES:
        name = "steel_plate_" + piece
        write(os.path.join(ASSETS, "models", "item", name + ".json"),
              {"parent": "minecraft:item/generated", "textures": {"layer0": NS + ":item/" + name}})
        write(os.path.join(ASSETS, "items", name + ".json"), {"model": {"type": "minecraft:model", "model": NS + ":item/" + name}})
    write(os.path.join(ASSETS, "equipment", "steel_plate.json"), {"layers": {
        "humanoid": [{"texture": NS + ":steel_plate"}], "humanoid_leggings": [{"texture": NS + ":steel_plate"}]}})


def data():
    for piece in PIECES:
        name = "steel_plate_" + piece
        for suffix, pattern, lining in (("", STRAPPED[piece], "#c:leather_straps"), ("_with_leather", LEATHER[piece], "minecraft:leather")):
            write(os.path.join(DATA, "recipe", name + suffix + ".json"), {
                "type": "minecraft:crafting_shaped", "category": "equipment", "group": name,
                "key": {"S": "#c:ingots/steel", "L": lining}, "pattern": pattern, "result": {"id": NS + ":" + name}})
    write(os.path.join(RES, "data", "c", "tags", "item", "leather_straps.json"), {"replace": False, "values": STRAPS})
    path = os.path.join(DATA, "tags", "item", "heavy_armor.json")
    tag = json.load(open(path, encoding="utf-8"))
    for piece in PIECES:
        if NS + ":steel_plate_" + piece not in tag["values"]:
            tag["values"].append(NS + ":steel_plate_" + piece)
    write(path, tag)
    path = os.path.join(DATA, "tinkering_melting", "armour.json")
    melt = json.load(open(path, encoding="utf-8"))
    for piece in PIECES:
        melt["items"][NS + ":steel_plate_" + piece] = {"steel": STRAPPED_INGOTS[piece] * INGOT_MB}
    # one item a line, as the file is written by hand
    lines = ["    %s: %s" % (json.dumps(k), json.dumps(v)) for k, v in melt["items"].items()]
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write('{\n  "items": {\n' + ",\n".join(lines) + "\n  }\n}\n")


def main():
    if len(sys.argv) != 2 or not os.path.isfile(sys.argv[1]):
        sys.exit(__doc__)
    textures(sys.argv[1])
    assets()
    data()


if __name__ == "__main__":
    main()
