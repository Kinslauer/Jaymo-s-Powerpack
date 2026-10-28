"""Draws the iron arrow, steel arrow and lingering arrow (our own 16x16 art) and writes their models and item definitions.

All three lie on the diagonal like vanilla's arrow: fletching bottom-left, a stick shaft, the head top-right. The iron
head is plain grey, the steel head a bluer, brighter grey with a pale edge. The lingering arrow is two layers like vanilla's
tipped arrow: the base (shaft and feathers) and a head layer drawn in white and grey that the potion's colour tints.
Run it from the root:

    python -I tools/gen_fletching.py
"""
import json
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "jaymospowers")


def rgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


STICK = [rgb("896727"), rgb("684e1e")]  # light, shaded (vanilla stick)
FEATHER = [rgb("f2f2f2"), rgb("cfcfcf"), rgb("a0a0a0")]
IRON = [rgb("e0e0e0"), rgb("b4b4b4"), rgb("8a8a8a"), rgb("4a4a4a")]
STEEL = [rgb("eef4fa"), rgb("b8cade"), rgb("8196b0"), rgb("3c4a5e")]
BLOB = [rgb("ffffff"), rgb("dcdcdc"), rgb("b0b0b0"), rgb("808080")]

# the shaft runs from the tail (2, 13) to the neck (11, 4), x + y = 15
SHAFT = [(x, 15 - x) for x in range(3, 12)]
TAIL_FEATHERS = [(1, 12), (2, 12), (1, 13), (2, 11), (3, 11), (2, 14), (3, 13), (4, 13), (0, 14), (1, 14)]
FEATHER_SHADE = {(1, 12): 1, (2, 12): 0, (1, 13): 2, (2, 11): 0, (3, 11): 1, (2, 14): 2, (3, 13): 1, (4, 13): 2, (0, 14): 2, (1, 14): 1}
# head: tip, then the body and the barbs, as (x, y, ramp index)
HEAD = [(14, 1, 0), (13, 2, 0), (12, 3, 1), (13, 1, 1), (14, 2, 1), (12, 2, 1), (13, 3, 2), (12, 4, 3), (11, 3, 2), (11, 2, 3), (14, 3, 3),
        (13, 0, 3), (15, 0, 3)]


def shaft(img):
    for i, (x, y) in enumerate(SHAFT):
        img.putpixel((x, y), STICK[0] if i % 2 == 0 else STICK[1])
    # a shaded pixel beside each shaft pixel gives it body
    for x, y in SHAFT[:-1]:
        if img.getpixel((x + 1, y)) == (0, 0, 0, 0):
            img.putpixel((x + 1, y), STICK[1])


def feathers(img):
    for p in TAIL_FEATHERS:
        img.putpixel(p, FEATHER[FEATHER_SHADE[p]])


def head(img, ramp):
    for x, y, i in HEAD:
        img.putpixel((x, y), ramp[i])


def blank():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def save(img, name):
    folder = os.path.join(ASSETS, "textures", "item", "fletching")
    os.makedirs(folder, exist_ok=True)
    img.save(os.path.join(folder, name + ".png"))


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", newline="\n") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def model(name, layers):
    write(os.path.join(ASSETS, "models", "item", "fletching", name + ".json"),
          {"parent": "minecraft:item/generated", "textures": {f"layer{i}": f"jaymospowers:item/fletching/{layer}" for i, layer in enumerate(layers)}})


def definition(item, name, tinted=False):
    node = {"type": "minecraft:model", "model": f"jaymospowers:item/fletching/{name}"}
    if tinted:
        node["tints"] = [{"type": "minecraft:potion", "default": -13083194}]
    write(os.path.join(ASSETS, "items", item + ".json"), {"model": node})


def main():
    for name, ramp in (("iron_arrow", IRON), ("steel_arrow", STEEL)):
        img = blank()
        shaft(img)
        feathers(img)
        head(img, ramp)
        save(img, name)
        model(name, [name])
        definition(name, name)
    base = blank()
    shaft(base)
    feathers(base)
    save(base, "lingering_arrow_base")
    cloud = blank()
    head(cloud, BLOB)
    # a puff round the tip, which the potion tints
    for x, y, i in ((15, 2, 2), (14, 0, 2), (12, 1, 3), (15, 4, 3), (10, 3, 3)):
        cloud.putpixel((x, y), BLOB[i])
    save(cloud, "lingering_arrow_head")
    model("lingering_arrow", ["lingering_arrow_head", "lingering_arrow_base"])
    definition("lingering_arrow", "lingering_arrow", tinted=True)


if __name__ == "__main__":
    main()
