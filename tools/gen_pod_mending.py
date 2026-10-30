"""Writes the assets for mending a space pod (0.18.21, power/zfighter/pod/SalvageRack and PodGrades):

* the Salvage Rack: a block model (a low bench of planks on log legs, vanilla textures), one overlay model for each pod part lying
  on it (the part's own boxes at half size, worn or mended: salvage_rack_<part> and salvage_rack_<part>_worn), the blockstate that
  picks them from the rack's `laid` and `mended` properties, its item definition, its loot table and its grid recipe;
* the three world parts, plain 16x16 items: rustfall_plate (Rustfall, grade I), glacia_ice_glass (Glacia, II) and
  cinder_bell_alloy (Cinder, III): a texture, an item model and an item definition each.

    python3 -I tools/gen_pod_mending.py

Run tools/gen_pod_parts.py first (the overlays reuse its part boxes and textures). Model faces reference #variables, never a raw
sprite id (26.3 draws a raw id purple). Vanilla style: flat colours, a darker edge, a few flecks, fixed seeds so reruns match.
"""
import json
import os
import random
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import gen_pod_parts as parts  # noqa: E402  (BOXES, FACES, PARTS and the writers)

ASSETS = parts.ASSETS
DATA = parts.DATA

# the bench: top slab and four legs (pixels of 16); must match SalvageRack.shape
BENCH = [(0, 10, 0, 16, 13, 16, "top"), (1, 0, 1, 3, 10, 3, "legs"), (13, 0, 1, 15, 10, 3, "legs"), (1, 0, 13, 3, 10, 15, "legs"),
         (13, 0, 13, 15, 10, 15, "legs")]
# a part on the bench is drawn this share of its size, centred, standing on the top slab
SCALE, LIFT = 0.5, 13.0


def rack_model():
    return {"textures": {"top": "minecraft:block/oak_planks", "legs": "minecraft:block/stripped_oak_log", "particle": "minecraft:block/oak_planks"},
            "elements": [{"from": list(b[:3]), "to": list(b[3:6]), "faces": {f: {"texture": "#" + b[6]} for f in parts.FACES}} for b in BENCH]}


def overlay(part, worn):
    """The part's own model, shrunk onto the bench top."""
    base = parts.model(part, worn)
    elements = []
    for el in base["elements"]:
        lo, hi = el["from"], el["to"]
        elements.append({
            "from": [4 + lo[0] * SCALE, LIFT + lo[1] * SCALE, 4 + lo[2] * SCALE],
            "to": [4 + hi[0] * SCALE, LIFT + hi[1] * SCALE, 4 + hi[2] * SCALE],
            "faces": el["faces"]})
    return {"textures": base["textures"], "elements": elements}


def rack():
    parts.write(os.path.join(ASSETS, "models", "block", "salvage_rack.json"), rack_model())
    multipart = [{"apply": {"model": "jaymospowers:block/salvage_rack"}}]
    for part in parts.PARTS:
        for worn in (True, False):
            name = "salvage_rack_%s%s" % (part, "_worn" if worn else "")
            parts.write(os.path.join(ASSETS, "models", "block", name + ".json"), overlay(part, worn))
            multipart.append({"when": {"laid": part, "mended": "false" if worn else "true"}, "apply": {"model": "jaymospowers:block/" + name}})
    parts.write(os.path.join(ASSETS, "blockstates", "salvage_rack.json"), {"multipart": multipart})
    parts.write(os.path.join(ASSETS, "items", "salvage_rack.json"), {"model": {"type": "minecraft:model", "model": "jaymospowers:block/salvage_rack"}})
    parts.write(os.path.join(DATA, "loot_table", "blocks", "salvage_rack.json"), {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "condition": {"type": "minecraft:survives_explosion"},
                   "entries": [{"type": "minecraft:item", "name": "jaymospowers:salvage_rack"}]}],
        "random_sequence": "jaymospowers:blocks/salvage_rack"})
    # two planks, two sticks and a chain: furniture, not a metal part, so a grid recipe is fine
    parts.write(os.path.join(DATA, "recipe", "salvage_rack.json"), {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "key": {"P": "#minecraft:planks", "C": "minecraft:iron_chain", "S": "minecraft:stick"},
        "pattern": ["PCP", "S S"],
        "result": {"id": "jaymospowers:salvage_rack"}})


# ---------------------------------------------------------------- the world parts

def put(img, x, y, colour):
    if 0 <= x < 16 and 0 <= y < 16:
        img.putpixel((x, y), colour if len(colour) == 4 else colour + (255,))


def rustfall_plate():
    """A scabbed, rust-red hull plate with four rivets and a bitten corner."""
    rng = random.Random("rustfall_plate")
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    base, edge, glint = (156, 84, 46), (62, 34, 24), (214, 140, 82)
    for y in range(3, 13):
        for x in range(2, 14):
            colour = parts.shade(base, rng.randint(-14, 10))
            if x in (2, 13) or y in (3, 12):
                colour = edge
            elif x == 3 or y == 4:
                colour = parts.shade(glint, rng.randint(-8, 8))
            put(img, x, y, colour)
    for _ in range(9):
        put(img, rng.randint(4, 12), rng.randint(5, 11), parts.shade((110, 56, 32), rng.randint(-6, 6)))
    for x, y in ((4, 5), (11, 5), (4, 10), (11, 10)):
        put(img, x, y, edge)
        put(img, x - 1, y - 1, parts.shade(glint, 12))
    for x, y in ((13, 12), (12, 12), (13, 11), (13, 10), (11, 12)):
        put(img, x, y, (0, 0, 0, 0))
    for x, y in ((12, 11), (11, 11), (12, 10)):
        put(img, x, y, edge)
    return img


def glacia_ice_glass():
    """A clear blue-white shard of ice-glass, faceted, with bright glints."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    widths = [2, 4, 6, 8, 10, 10, 10, 10, 8, 6, 4, 2]
    body, edge, light, deep = (176, 226, 248, 215), (86, 146, 196, 255), (236, 250, 255, 235), (132, 196, 232, 220)
    for row, width in enumerate(widths):
        y = 2 + row
        x0 = 8 - width // 2
        for x in range(x0, x0 + width):
            colour = body
            if x in (x0, x0 + width - 1) or row in (0, len(widths) - 1):
                colour = edge
            elif x - x0 < width // 3:
                colour = light
            elif x - x0 > width * 2 // 3:
                colour = deep
            put(img, x, y, colour)
    for x, y in ((6, 5), (6, 6), (7, 4)):
        put(img, x, y, (255, 255, 255, 255))
    for i in range(5):
        put(img, 8 + i // 2, 6 + i, edge)
    return img


def cinder_bell_alloy():
    """A dark charcoal-red ingot veined with live ember cracks."""
    rng = random.Random("cinder_bell_alloy")
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    top, front, edge = (88, 66, 70), (54, 40, 46), (22, 16, 20)
    # a long ingot seen from the front and above: a lighter top slab over a darker face
    for y in range(4, 13):
        shift = (12 - y) // 3
        for x in range(2 + shift, 14 - (3 - shift) if y < 7 else 14):
            colour = parts.shade(top if y < 7 else front, rng.randint(-8, 8))
            if y in (4, 12) or x in (2 + shift, 13):
                colour = edge
            put(img, x, y, colour)
    for x, y in ((5, 9), (6, 9), (6, 10), (7, 10), (8, 10), (9, 9), (10, 9), (10, 8), (11, 8)):
        put(img, x, y, (232, 98, 28))
    for x, y in ((6, 9), (8, 10), (10, 9)):
        put(img, x, y, (255, 190, 70))
    for x, y in ((5, 6), (9, 5), (11, 6)):
        put(img, x, y, (190, 70, 30))
    return img


WORLD_PARTS = {"rustfall_plate": rustfall_plate, "glacia_ice_glass": glacia_ice_glass, "cinder_bell_alloy": cinder_bell_alloy}


def world_parts():
    for name, paint in WORLD_PARTS.items():
        path = os.path.join(ASSETS, "textures", "item", name + ".png")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        paint().save(path)
        parts.write(os.path.join(ASSETS, "models", "item", name + ".json"),
                    {"parent": "minecraft:item/generated", "textures": {"layer0": "jaymospowers:item/" + name}})
        parts.write(os.path.join(ASSETS, "items", name + ".json"), {"model": {"type": "minecraft:model", "model": "jaymospowers:item/" + name}})


if __name__ == "__main__":
    rack()
    world_parts()
