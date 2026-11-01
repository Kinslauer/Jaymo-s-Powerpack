"""(0.7.462) The infirmary cot and the shrine: textures (16x16, painted per docs/Vanilla-Style.md), block models, blockstates,
item definitions, loot tables and recipes. Run from kingdoms/: python -I tools/gen_healing_blocks.py

Every face names a #variable (26.3 models: a raw sprite id renders purple). Each texture is a strip atlas: a face uses the whole
band it belongs to, so the bands are drawn to look right stretched."""
import json
import os
from PIL import Image

RES = "src/main/resources"
ASSETS = RES + "/assets/jaymoskingdoms"
DATA = RES + "/data/jaymoskingdoms"


def put(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", newline="\n") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


# ---------------------------------------------------------------- textures (no black outlines, light from the top left)
def cot_texture():
    img = Image.new("RGB", (16, 16))
    px = img.load()
    linen = [(238, 232, 216), (226, 218, 198), (208, 198, 176)]  # lit, plain, shade
    red = (168, 58, 52)
    planks = [(184, 148, 95), (162, 130, 80), (143, 114, 70), (120, 94, 56)]
    dark = [(110, 86, 52), (92, 70, 42), (74, 56, 34)]
    for y in range(0, 6):  # linen band
        for x in range(16):
            c = linen[1]
            if y == 0 or x == 0:
                c = linen[0]
            if y == 5 or x == 15:
                c = linen[2]
            if (x + y) % 4 == 0 and 0 < y < 5:
                c = linen[2] if (x // 2) % 2 else linen[0]
            if y == 3 and 3 <= x <= 12:
                c = red  # a red stripe, the healer's mark
            px[x, y] = c
    for y in range(6, 12):  # planks band
        for x in range(16):
            row = y - 6
            c = planks[1]
            if row == 0:
                c = planks[0]
            elif row == 5:
                c = planks[3]
            elif row in (2, 3) and (x * 3 + row) % 7 == 0:
                c = planks[2]
            elif row == 3:
                c = planks[2] if x % 5 else planks[1]
            px[x, y] = c
    for y in range(12, 16):  # legs band
        for x in range(16):
            row = y - 12
            c = dark[1]
            if row == 0:
                c = dark[0]
            elif row == 3:
                c = dark[2]
            px[x, y] = c
    img.save(ASSETS + "/textures/block/infirmary_cot.png")


def shrine_texture():
    img = Image.new("RGB", (16, 16))
    px = img.load()
    brick = [(150, 150, 150), (131, 131, 131), (112, 112, 112)]
    mortar = (97, 97, 97)
    smooth = [(166, 166, 166), (146, 146, 146), (124, 124, 124)]
    wax = [(244, 236, 208), (228, 218, 184), (200, 190, 156)]
    flame = [(255, 226, 120), (255, 176, 60)]
    for y in range(0, 8):  # stone bricks
        for x in range(16):
            row = y % 4
            offset = 0 if y < 4 else 4
            seam = row == 3 or (x + offset) % 8 == 7
            c = brick[1]
            if seam:
                c = mortar
            elif row == 0 or (x + offset) % 8 == 0:
                c = brick[0]
            elif (x * 7 + y * 3) % 11 == 0:
                c = brick[2]
            px[x, y] = c
    for y in range(8, 12):  # smooth stone slab
        for x in range(16):
            row = y - 8
            c = smooth[1]
            if row == 0 or x == 0:
                c = smooth[0]
            elif row == 3 or x == 15:
                c = smooth[2]
            px[x, y] = c
    for y in range(12, 15):  # candle wax
        for x in range(16):
            c = wax[1]
            if x % 4 == 0:
                c = wax[0]
            elif x % 4 == 3:
                c = wax[2]
            px[x, y] = c
    for x in range(16):  # the flame
        px[x, 15] = flame[0] if x % 4 in (1, 2) else flame[1]
    img.save(ASSETS + "/textures/block/shrine.png")


# ---------------------------------------------------------------- models
LINEN, WOOD, LEGS = [0, 0, 16, 6], [0, 6, 16, 12], [0, 12, 16, 16]
SIDES = ("north", "south", "east", "west")


def box(lo, hi, band, tex, faces=("north", "south", "east", "west", "up", "down")):
    return {"from": lo, "to": hi, "faces": {f: {"uv": band, "texture": tex} for f in faces}}


def cot_elements(head, dz=0):
    """One half of the cot; the head end is at the north edge (z = 0) of the head piece, the foot end at the south of the foot."""
    t = "#cot"
    def z(v):
        return v + dz
    out = []
    end = 0 if head else 14
    out.append(box([0, 0, z(end)], [2, 3, z(end + 2)], LEGS, t))
    out.append(box([14, 0, z(end)], [16, 3, z(end + 2)], LEGS, t))
    out.append(box([0, 3, z(0)], [16, 5, z(16)], WOOD, t))  # the frame
    out.append(box([1, 5, z(1 if head else 0)], [15, 6, z(16 if head else 15)], LINEN, t))  # the pad
    if head:
        out.append(box([0, 3, z(0)], [16, 7, z(1)], WOOD, t))  # the headboard
        out.append(box([3, 6, z(2)], [13, 6.5, z(7)], LINEN, t, ("north", "south", "east", "west", "up")))  # the pillow
    else:
        out.append(box([0, 3, z(15)], [16, 5.5, z(16)], WOOD, t))  # the footboard
    return out


def model(elements, tex, extra=None):
    m = {"parent": "minecraft:block/block", "textures": {"particle": tex, tex_key(tex): tex}, "elements": elements}
    if extra:
        m.update(extra)
    return m


def tex_key(tex):
    return "cot" if "cot" in tex else "shrine"


def shrine_elements():
    t = "#shrine"
    BRICK, SMOOTH, WAX, FLAME = [0, 0, 16, 8], [0, 8, 16, 12], [0, 12, 16, 15], [0, 15, 16, 16]
    out = [
        box([2, 0, 2], [14, 3, 14], BRICK, t),
        box([4, 3, 4], [12, 9, 12], BRICK, t),
        box([3, 9, 3], [13, 11, 13], SMOOTH, t),
    ]
    for x, z, h in ((4, 4, 3), (10, 5, 4), (6, 10, 2.5)):
        out.append(box([x, 11, z], [x + 2, 11 + h, z + 2], WAX, t))
        out.append(box([x + 0.5, 11 + h, z + 0.5], [x + 1.5, 11 + h + 1, z + 1.5], FLAME, t,
                       ("north", "south", "east", "west", "up")))
    return out


def main():
    cot_texture()
    shrine_texture()
    cot_tex = "jaymoskingdoms:block/infirmary_cot"
    put(ASSETS + "/models/block/infirmary_cot_head.json", model(cot_elements(True), cot_tex))
    put(ASSETS + "/models/block/infirmary_cot_foot.json", model(cot_elements(False), cot_tex))
    variants = {}
    rot = {"north": 0, "east": 90, "south": 180, "west": 270}
    for facing, y in rot.items():
        for part in ("head", "foot"):
            for occupied in ("false", "true"):
                v = {"model": "jaymoskingdoms:block/infirmary_cot_" + part}
                if y:
                    v["y"] = y
                variants["facing=%s,occupied=%s,part=%s" % (facing, occupied, part)] = v
    put(ASSETS + "/blockstates/infirmary_cot.json", {"variants": variants})
    # the item: both halves in one model, centred
    item_elems = cot_elements(True, -8) + cot_elements(False, 8)
    display = {
        "gui": {"rotation": [30, 225, 0], "translation": [0, 1, 0], "scale": [0.55, 0.55, 0.55]},
        "ground": {"translation": [0, 2, 0], "scale": [0.25, 0.25, 0.25]},
        "fixed": {"rotation": [0, 90, 0], "scale": [0.45, 0.45, 0.45]},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.3, 0.3, 0.3]},
        "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [0.35, 0.35, 0.35]},
    }
    put(ASSETS + "/models/item/infirmary_cot.json", model(item_elems, cot_tex, {"display": display}))
    put(ASSETS + "/items/infirmary_cot.json", {"model": {"type": "minecraft:model", "model": "jaymoskingdoms:item/infirmary_cot"}})

    shrine_tex = "jaymoskingdoms:block/shrine"
    put(ASSETS + "/models/block/shrine.json", model(shrine_elements(), shrine_tex))
    put(ASSETS + "/blockstates/shrine.json", {"variants": {"": {"model": "jaymoskingdoms:block/shrine"}}})
    put(ASSETS + "/items/shrine.json", {"model": {"type": "minecraft:model", "model": "jaymoskingdoms:block/shrine"}})

    # loot tables (the cot is two blocks: only the head drops it, as a bed does)
    def loot(name, cond=None):
        pool = {"rolls": 1, "entries": [{"type": "minecraft:item", "name": "jaymoskingdoms:" + name}]}
        pool["conditions"] = cond or [{"condition": "minecraft:survives_explosion"}]
        return {"type": "minecraft:block", "pools": [pool], "random_sequence": "jaymoskingdoms:blocks/" + name}

    put(DATA + "/loot_table/blocks/shrine.json", loot("shrine"))
    put(DATA + "/loot_table/blocks/infirmary_cot.json", loot("infirmary_cot", [
        {"condition": "minecraft:block_state_property", "block": "jaymoskingdoms:infirmary_cot", "properties": {"part": "head"}},
        {"condition": "minecraft:survives_explosion"}]))

    # recipes
    put(DATA + "/recipe/infirmary_cot.json", {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "pattern": ["WWW", "PSP"],
        "key": {"W": "#minecraft:wool", "P": "#minecraft:planks", "S": "minecraft:stick"},
        "result": {"count": 1, "id": "jaymoskingdoms:infirmary_cot"}})
    put(DATA + "/recipe/shrine.json", {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "pattern": ["C C", "BGB", "BBB"],
        "key": {"C": "#minecraft:candles", "B": "minecraft:stone_bricks", "G": "minecraft:gold_nugget"},
        "result": {"count": 1, "id": "jaymoskingdoms:shrine"}})


main()
