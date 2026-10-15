"""Writes the Costume Table's assets and data (0.11.66): the costume pieces' mask textures, the table's block and item
models and textures, the costume item's textures, the recipe and the loot table. Our own art, drawn here.

    python -I tools/gen_costume.py

Every costume piece is drawn twice, once per colour, from a pair of greyscale textures: `<piece>_primary.png` holds the
pixels the primary dye tints and `<piece>_secondary.png` the secondary's; the two never overlap. Suits use the player
skin's layout (64x64, the base layer's boxes only; wide and slim arms each have their own), masks and capes their own
small layouts (see com.jaymospowers.client.costume.CostumeModels, which must agree with the UVs here).
"""
import json
import os
import random

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "jaymospowers")
DATA = os.path.join(RES, "data", "jaymospowers")
NS = "jaymospowers"
COSTUME = os.path.join(ASSETS, "textures", "entity", "costume")

P, S = 1, 2  # primary, secondary


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def save(im, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im.save(path)


# ---- box UV layout ------------------------------------------------------------------------------------------------

def box_faces(u, v, w, h, d):
    """A model box's faces in its texture: name -> (x, y, width, height), as Minecraft lays them out."""
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),       # the box's -x... the side seen on the wearer's right for limbs
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + d + w + d, v + d, w, h),
    }


def paint_box(primary, secondary, u, v, w, h, d, part, pattern, rng):
    """Calls pattern(part, face, x, y, w, h, rng) for every pixel of a box; P or S pixels go to that colour's image."""
    for face, (fx, fy, fw, fh) in box_faces(u, v, w, h, d).items():
        for y in range(fh):
            for x in range(fw):
                which = pattern(part, face, x, y, fw, fh, rng)
                if which is None:
                    continue
                shade = cloth_shade(x, y, fw, fh, rng)
                (primary if which == P else secondary).putpixel((fx + x, fy + y), (shade, shade, shade, 255))


def cloth_shade(x, y, w, h, rng):
    """Near-white (the dye tints it), with a faint weave and darker seams at the face's edges."""
    base = 236 + ((x + y) % 2) * 6 + rng.randint(-5, 5)
    if x == 0 or y == 0 or x == w - 1 or y == h - 1:
        base -= 16
    return max(0, min(255, base))


# ---- the suits ----------------------------------------------------------------------------------------------------

def suit_boxes(slim):
    """The skin's base-layer boxes: part -> (u, v, w, h, d)."""
    aw = 3 if slim else 4
    return {
        "body": (16, 16, 8, 12, 4),
        "right_arm": (40, 16, aw, 12, 4),
        "left_arm": (32, 48, aw, 12, 4),
        "right_leg": (0, 16, 4, 12, 4),
        "left_leg": (16, 48, 4, 12, 4),
    }


def is_arm(part):
    return part.endswith("arm")


def is_leg(part):
    return part.endswith("leg")


def bodysuit(part, face, x, y, w, h, rng):
    """A clean bodysuit: gloves, boots, a belt and stripes down the sides in the secondary colour."""
    if is_arm(part):
        if face == "bottom" or (face != "top" and y >= 9):
            return S
        if face in ("right", "left") and x == w // 2 and y < 9 and part[0] == ("r" if face == "right" else "l"):
            return S
        return P
    if is_leg(part):
        if face == "bottom" or (face != "top" and y >= 8):
            return S
        return P
    # body
    if face in ("front", "back") and y in (10, 11):
        return S
    if face == "front" and 1 <= y <= 3 and abs(x - 3.5) < 4 - y:
        return S  # a chevron at the collar
    if face in ("right", "left") and x in (1, 2) and y < 10:
        return S
    return P


def armoured(part, face, x, y, w, h, rng):
    """Plated: a chest plate, bracers, knee guards and greaves in the secondary colour over a primary undersuit."""
    if is_arm(part):
        if face == "top":
            return S
        if face == "bottom" or y >= 7:
            return S
        return P
    if is_leg(part):
        if face == "bottom" or y >= 9:
            return S
        if face == "front" and 3 <= y <= 5:
            return S
        return P
    if face == "front" and 1 <= y <= 7 and 1 <= x <= 6:
        return S
    if face == "back" and 1 <= y <= 6 and 2 <= x <= 5:
        return S
    if face in ("front", "back") and y == 10:
        return S
    return P


def robes(part, face, x, y, w, h, rng):
    """Long robes: wide cuffs, a sash and a deep hem in the secondary colour."""
    if is_arm(part):
        if face == "bottom" or y >= 9:
            return S
        return P
    if is_leg(part):
        if face == "bottom" or y >= 9:
            return S
        if face == "front" and ((part == "right_leg" and x == w - 1) or (part == "left_leg" and x == 0)):
            return S  # the robe's opening down the middle
        return P
    if face == "front" and x in (3, 4):
        return S
    if y in (7, 8) and face != "top":
        return S
    return P


def coat(part, face, x, y, w, h, rng):
    """A long coat over secondary trousers and shirt: lapels, cuffs, the coat's skirts to the knee."""
    if is_arm(part):
        if face != "top" and y in (9, 10):
            return S
        if y == 11 or face == "bottom":
            return None  # bare hands
        return P
    if is_leg(part):
        if face == "bottom":
            return S
        if y < 6 and face != "front":
            return P
        if y < 6 and face == "front":
            return P if ((part == "right_leg" and x < w - 1) or (part == "left_leg" and x > 0)) else S
        return S
    if face == "front" and 2 <= x <= 5 and y < 9:
        return S if (x in (3, 4) or y < 2) else P
    return P


def hash_of(*parts):
    """A small stable hash (Python's own changes between runs)."""
    h = 2166136261
    for c in "|".join(str(p) for p in parts):
        h = (h ^ ord(c)) * 16777619 & 0xFFFFFFFF
    return h


def ragged(part, face, x, y, w, h, rng):
    """Worn-out rags: torn sleeves and trouser legs, holes and patches."""
    # patches in squares of 3, holes single pixels, both fixed per spot (not noise)
    patch = hash_of(part, face, x // 3, y // 3) % 6 == 0
    hole = hash_of(part, face, x, y) % 23 == 0
    if is_arm(part):
        if face == "bottom":
            return None
        if face != "top" and y >= 5 + (x * 7 + 3) % 3:
            return None
        return None if hole else (S if patch else P)
    if is_leg(part):
        if face == "bottom":
            return None
        if face != "top" and y >= 8 + (x * 5 + 1) % 3:
            return None
        return None if hole else (S if patch else P)
    if face in ("front", "back") and y >= 10 and (x + y) % 3 == 0:
        return None
    if face in ("front", "back") and y in (8,) :
        return S  # a rope belt
    return None if hole else (S if patch else P)


CUTS = {"bodysuit": bodysuit, "armoured": armoured, "robes": robes, "coat": coat, "ragged": ragged}


def suits():
    for name, pattern in CUTS.items():
        for slim in (False, True):
            rng = random.Random(name)
            primary = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
            secondary = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
            for part, (u, v, w, h, d) in suit_boxes(slim).items():
                paint_box(primary, secondary, u, v, w, h, d, part, pattern, rng)
            if name == "armoured":
                # the shoulder pads (CostumeModels: texOffs 32,0, 5x3x5): secondary plates, a primary rim
                for face, (fx, fy, fw, fh) in box_faces(32, 0, 5, 3, 5).items():
                    for y in range(fh):
                        for x in range(fw):
                            rim = face != "top" and face != "bottom" and y == fh - 1
                            img = primary if rim else secondary
                            shade = cloth_shade(x, y, fw, fh, rng) - 10
                            img.putpixel((fx + x, fy + y), (shade, shade, shade, 255))
            tag = "_slim" if slim else ""
            save(primary, os.path.join(COSTUME, "suit", name + tag + "_primary.png"))
            save(secondary, os.path.join(COSTUME, "suit", name + tag + "_secondary.png"))


# ---- masks --------------------------------------------------------------------------------------------------------
# The head box at 0,0 (8x8x8) in a 64x32 texture; the helmet's crest (1x2x9) at 32,0. On the front face the eyes are
# on row 4 (the skin's usual eye row), columns 1-2 and 5-6.

EYES = {(1, 4), (2, 4), (5, 4), (6, 4)}


def half_mask(face, x, y):
    if face == "front":
        if (x, y) in EYES:
            return None
        if 3 <= y <= 5:
            return S if (y in (3, 5) and x in (1, 2, 5, 6)) else P
        return None
    if face in ("left", "right", "back") and y == 4:
        return P  # the strap
    return None


def full_mask(face, x, y):
    if face == "front" and (x, y) in EYES:
        return S
    if face == "front" and y == 6 and 2 <= x <= 5:
        return S
    if face == "back" and x in (3, 4):
        return S  # the lacing
    if face == "bottom":
        return None
    return P


def hood(face, x, y):
    if face == "front":
        if x == 0 or x == 7 or y == 0:
            return S  # the hood's edge
        return None
    if face == "bottom":
        return None
    if face == "back" and y >= 6:
        return S
    return P


def helmet(face, x, y):
    if face == "front":
        if y in (3, 4) and 1 <= x <= 6 or y in (5, 6) and x in (3, 4):
            return S  # the visor
        return P
    if face == "bottom":
        return None
    if face in ("left", "right") and y == 7:
        return S
    return P


def goggles(face, x, y):
    if face == "front":
        if y in (3, 4, 5) and x in (1, 2, 3, 4, 5, 6):
            lens = y == 4 and x in (1, 2, 5, 6)
            return S if lens else P
        return None
    if face in ("left", "right", "back") and y in (3, 4):
        return P
    return None


MASKS = {"half_mask": half_mask, "full_mask": full_mask, "hood": hood, "helmet": helmet, "goggles": goggles}


def masks():
    for name, pattern in MASKS.items():
        rng = random.Random(name)
        primary = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
        secondary = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
        for face, (fx, fy, fw, fh) in box_faces(0, 0, 8, 8, 8).items():
            for y in range(fh):
                for x in range(fw):
                    which = pattern(face, x, y)
                    if which is None:
                        continue
                    shade = cloth_shade(x, y, fw, fh, rng)
                    if name in ("helmet", "goggles") and which == S:
                        shade = min(255, shade + 10)  # polished
                    (primary if which == P else secondary).putpixel((fx + x, fy + y), (shade, shade, shade, 255))
        if name == "helmet":
            for face, (fx, fy, fw, fh) in box_faces(32, 0, 1, 2, 9).items():
                for y in range(fh):
                    for x in range(fw):
                        shade = cloth_shade(x, y, fw, fh, rng)
                        secondary.putpixel((fx + x, fy + y), (shade, shade, shade, 255))
        save(primary, os.path.join(COSTUME, "mask", name + "_primary.png"))
        save(secondary, os.path.join(COSTUME, "mask", name + "_secondary.png"))


# ---- capes --------------------------------------------------------------------------------------------------------
# One box (10 wide, `length` long, 1 deep) at 0,0 in a 64x32 texture: its north face is the outside (primary, with a
# secondary trim along the hem), its south face the lining (secondary).

CAPES = {"short": 11, "long": 20, "tattered": 19}


def capes():
    for name, length in CAPES.items():
        rng = random.Random(name)
        primary = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
        secondary = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
        # tattered: a ragged hem (how many rows each column loses) and a few holes
        torn = [rng.randint(0, 5) if name == "tattered" else 0 for _ in range(10)]
        holes = {(rng.randint(1, 8), rng.randint(6, length - 4)) for _ in range(5)} if name == "tattered" else set()
        for face, (fx, fy, fw, fh) in box_faces(0, 0, 10, length, 1).items():
            for y in range(fh):
                for x in range(fw):
                    which = P
                    if face in ("front", "back"):
                        col = x if face == "front" else 9 - x
                        if y >= length - torn[col] or (col, y) in holes:
                            continue
                        if face == "back":
                            which = S
                        elif y >= length - torn[col] - 1 and name != "tattered":
                            which = S
                    shade = cloth_shade(x, y, fw, fh, rng)
                    # folds: soft vertical shading
                    if face in ("front", "back"):
                        shade -= 8 if x % 3 == 1 else 0
                    (primary if which == P else secondary).putpixel((fx + x, fy + y), (shade, shade, shade, 255))
        save(primary, os.path.join(COSTUME, "cape", name + "_primary.png"))
        save(secondary, os.path.join(COSTUME, "cape", name + "_secondary.png"))


# ---- the table and the item ---------------------------------------------------------------------------------------

OAK = [(162, 130, 78), (150, 118, 70), (128, 98, 56), (184, 148, 92)]
DARK = (92, 68, 40)
CLOTH = [(150, 64, 120), (170, 80, 140), (128, 50, 102)]
THREADS = [(200, 60, 60), (60, 120, 200), (230, 200, 60), (70, 170, 90), (240, 240, 240)]


def planks(rng, size=16):
    im = Image.new("RGBA", (size, size))
    for y in range(size):
        for x in range(size):
            c = OAK[0] if (y // 4) % 2 == 0 else OAK[1]
            if y % 4 == 3:
                c = OAK[2]
            jitter = rng.randint(-6, 6)
            im.putpixel((x, y), tuple(max(0, min(255, v + jitter)) for v in c) + (255,))
    return im


def table_textures():
    rng = random.Random("costume_table")
    top = planks(rng)
    # a bolt of cloth across the top, a tape measure along it, and shears
    for y in range(3, 13):
        for x in range(1, 15):
            c = CLOTH[(x + y) % 2] if 4 <= y <= 11 else CLOTH[2]
            top.putpixel((x, y), c + (255,))
    for x in range(1, 15):
        top.putpixel((x, 13), (230, 200, 70, 255) if x % 2 else (40, 40, 40, 255))
    for (x, y) in [(10, 5), (11, 6), (12, 7), (12, 5), (11, 6), (10, 7), (9, 8), (13, 4), (9, 4), (13, 8)]:
        top.putpixel((x, y), (190, 195, 205, 255))
    for (x, y) in [(13, 4), (9, 8)]:
        top.putpixel((x, y), (60, 60, 70, 255))
    for x in range(16):
        top.putpixel((x, 0), DARK + (255,))
        top.putpixel((x, 15), DARK + (255,))
        top.putpixel((0, x), DARK + (255,))
        top.putpixel((15, x), DARK + (255,))

    def framed(rng):
        im = planks(rng)
        for i in range(16):
            for (x, y) in [(i, 0), (i, 15), (0, i), (15, i)]:
                im.putpixel((x, y), DARK + (255,))
        return im

    side = framed(rng)
    # a shelf of spools
    for x in range(1, 15):
        side.putpixel((x, 9), DARK + (255,))
    for i, c in enumerate(THREADS):
        sx = 2 + i * 3 - (1 if i > 3 else 0)
        for y in range(4, 9):
            for x in range(sx, sx + 2):
                end = y in (4, 8)
                side.putpixel((x, y), (OAK[3] if end else c) + (255,))
    front = framed(rng)
    # bolts of fabric hanging from a rail
    for x in range(1, 15):
        front.putpixel((x, 2), (120, 120, 130, 255))
    for i, c in enumerate(THREADS[:4]):
        sx = 2 + i * 3
        for y in range(3, 12 - (i % 2) * 2):
            for x in range(sx, sx + 3):
                shade = -18 if x == sx + 2 else 0
                front.putpixel((x, y), tuple(max(0, v + shade) for v in c) + (255,))
    bottom = planks(rng)
    tex = os.path.join(ASSETS, "textures", "block", "costume_table")
    for name, im in [("top", top), ("side", side), ("front", front), ("bottom", bottom)]:
        save(im, os.path.join(tex, name + ".png"))


def item_textures():
    """A folded costume: the cloth (tinted primary) and its trim, collar and belt (tinted secondary)."""
    cloth = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    trim = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    rng = random.Random("costume_item")
    # a folded shirt seen from the front: shoulders, sleeves folded in, the body square
    for y in range(3, 14):
        for x in range(2, 14):
            if y < 5 and not (4 <= x <= 11):
                continue
            edge = x in (2, 13) or y in (3, 13)
            shade = (196 if edge else 236) + rng.randint(-6, 6)
            cloth.putpixel((x, y), (shade, shade, shade, 255))
    for (x, y) in [(6, 3), (7, 4), (8, 4), (9, 3), (7, 3), (8, 3)]:
        cloth.putpixel((x, y), (0, 0, 0, 0))
        trim.putpixel((x, y), (240, 240, 240, 255))
    for (x, y) in [(5, 3), (10, 3), (6, 4), (9, 4), (7, 5), (8, 5)]:
        trim.putpixel((x, y), (225, 225, 225, 255))
    for x in range(3, 13):
        trim.putpixel((x, 10), (232, 232, 232, 255))
        trim.putpixel((x, 11), (210, 210, 210, 255))
    for y in range(5, 13):
        trim.putpixel((2, y), (220, 220, 220, 255))
        trim.putpixel((13, y), (220, 220, 220, 255))
    tex = os.path.join(ASSETS, "textures", "item")
    save(cloth, os.path.join(tex, "costume.png"))
    save(trim, os.path.join(tex, "costume_trim.png"))


def json_files():
    write(os.path.join(ASSETS, "blockstates", "costume_table.json"),
          {"variants": {"": {"model": NS + ":block/costume_table"}}})
    t = NS + ":block/costume_table/"
    write(os.path.join(ASSETS, "models", "block", "costume_table.json"), {
        "parent": "minecraft:block/cube",
        "textures": {"particle": t + "front", "north": t + "front", "south": t + "front", "east": t + "side",
                     "west": t + "side", "up": t + "top", "down": t + "bottom"}})
    write(os.path.join(ASSETS, "items", "costume_table.json"),
          {"model": {"type": "minecraft:model", "model": NS + ":block/costume_table"}})
    write(os.path.join(ASSETS, "models", "item", "costume.json"), {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": NS + ":item/costume", "layer1": NS + ":item/costume_trim"}})
    write(os.path.join(ASSETS, "items", "costume.json"), {"model": {
        "type": "minecraft:model", "model": NS + ":item/costume",
        "tints": [{"type": "minecraft:custom_model_data", "index": 0, "default": 0x3C44AA},
                  {"type": "minecraft:custom_model_data", "index": 1, "default": 0xF9FFFE}]}})
    write(os.path.join(DATA, "recipe", "costume_table.json"), {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "key": {"W": "#minecraft:wool", "L": "minecraft:leather", "M": "minecraft:loom", "C": "minecraft:crafting_table"},
        "pattern": ["WLW", "LML", "WCW"],
        "result": {"id": NS + ":costume_table"}})
    write(os.path.join(DATA, "loot_table", "blocks", "costume_table.json"), {
        "type": "minecraft:block", "random_sequence": NS + ":blocks/costume_table",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": NS + ":costume_table"}],
                   "condition": {"type": "minecraft:survives_explosion"}}]})


if __name__ == "__main__":
    suits()
    masks()
    capes()
    table_textures()
    item_textures()
    json_files()
