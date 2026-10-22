"""(0.7.171) Rulers' crowns (Regalia): each worn on the head (the humanoid armour layer, 64x32: a band round the sides
of the head, some with a velvet cap on top) and in hand (16x16, the front of it drawn twice the size). Also writes each
crown's equipment asset and item model. Run from the kingdoms project root: python -I tools/gen_regalia.py"""
from PIL import Image
import json
import os
import random

ROOT = "src/main/resources/assets/jaymoskingdoms/"


def faces(ox, oy, w, h, d):
    return {"top": (ox + d, oy, w, d), "bottom": (ox + d + w, oy, w, d), "right": (ox, oy + d, d, h), "front": (ox + d, oy + d, w, h),
            "left": (ox + d + w, oy + d, d, h), "back": (ox + d + w + d, oy + d, w, h)}


HEAD = faces(0, 0, 8, 8, 8)
SIDES = ("front", "right", "back", "left")

GOLD = [(255, 236, 128), (240, 200, 70), (205, 150, 40), (150, 100, 25)]
SILVER = [(240, 244, 250), (205, 210, 222), (160, 166, 182), (110, 114, 130)]
IRON = [(120, 120, 128), (90, 90, 98), (62, 62, 70), (40, 40, 46)]
RUBY, SAPPHIRE, EMERALD, DIAMOND, PEARL, AMETHYST = (200, 30, 40), (40, 70, 200), (30, 170, 80), (170, 240, 250), (245, 240, 230), (150, 70, 200)
VELVET_RED, VELVET_PURPLE = (130, 20, 30), (80, 30, 110)
LEAF = [(235, 210, 90), (200, 165, 50), (150, 120, 30)]


def clamp(c):
    return tuple(max(0, min(255, v)) for v in c)


def jitter(c, rnd, by=6):
    return clamp(tuple(v + rnd.randint(-by, by) for v in c))


# A crown's look on one side of the head: a function (side, x, y) -> colour or None, y 0 (top) to 7.
def royal(side, x, y):
    if y == 0 and x in (0, 4):
        return GOLD[0]
    if y == 1 and x % 2 == 0:
        return GOLD[1]
    if y == 2:
        if side == "front" and x in (3, 4):
            return RUBY
        return SAPPHIRE if x in (1, 6) else GOLD[1]
    if y == 3:
        return GOLD[2]
    return None


def jeweled(side, x, y):
    if y == 0 and x % 2 == 1:
        return PEARL
    if y == 1:
        return GOLD[1] if x % 2 == 1 else GOLD[0]
    if y == 2:
        return [RUBY, GOLD[1], EMERALD, GOLD[1], SAPPHIRE, GOLD[1], AMETHYST, GOLD[1]][x]
    if y == 3:
        return GOLD[2]
    return None


def iron(side, x, y):
    if y in (0, 1) and x in (1, 4, 6):
        return IRON[1] if y == 1 else IRON[0]
    if y == 2:
        return IRON[1]
    if y == 3:
        return GOLD[1] if x % 3 == 1 else IRON[2]
    return None


def laurel(side, x, y):
    if side == "front" and y == 2 and x in (3, 4):
        return GOLD[0]
    if y == 2 and x % 2 == 0:
        return LEAF[0]
    if y == 3:
        return LEAF[1] if x % 2 == 1 else LEAF[2]
    if y == 1 and x % 4 == 1:
        return LEAF[0]
    return None


def tiara(side, x, y):
    if side == "front":
        if y == 0 and x in (3, 4):
            return SILVER[0]
        if y == 1 and x in (2, 5):
            return SILVER[1]
        if y == 1 and x in (3, 4):
            return DIAMOND
        if y == 2 and 1 <= x <= 6:
            return PEARL if x in (1, 6) else SILVER[1]
        if y == 3:
            return SILVER[2]
        return None
    if side in ("right", "left") and y == 3:
        return SILVER[2]
    return None


def circlet(side, x, y):
    if y == 3:
        if side == "front" and x in (3, 4):
            return EMERALD
        return GOLD[1]
    if side == "front" and y == 2 and x in (3, 4):
        return GOLD[0]
    return None


def coronet(side, x, y):
    if y == 1 and x % 2 == 1:
        return PEARL
    if y == 2:
        return GOLD[0] if x % 2 == 1 else GOLD[1]
    if y == 3:
        return GOLD[2]
    return None


CROWNS = {
    "royal_crown": (royal, VELVET_RED),
    "jeweled_crown": (jeweled, VELVET_PURPLE),
    "iron_crown": (iron, None),
    "laurel_crown": (laurel, None),
    "tiara": (tiara, None),
    "circlet": (circlet, None),
    "coronet": (coronet, None),
}


def worn(name, look, cap):
    rnd = random.Random(name)
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    for side in SIDES:
        x0, y0, w, h = HEAD[side]
        for y in range(h):
            for x in range(w):
                c = look(side, x, y)
                if c is not None:
                    img.putpixel((x0 + x, y0 + y), jitter(c, rnd) + (255,))
    if cap is not None:
        # a velvet cap inside the band, crossed by two gold arches
        x0, y0, w, h = HEAD["top"]
        for y in range(h):
            for x in range(w):
                c = GOLD[1] if x in (3, 4) or y in (3, 4) else jitter(cap, rnd, 8)
                if (x, y) in ((3, 3), (4, 4), (3, 4), (4, 3)):
                    c = GOLD[0]
                img.putpixel((x0 + x, y0 + y), jitter(c, rnd, 4) + (255,))
    save(img, "textures/entity/equipment/humanoid/" + name)


def item(name, look, cap):
    rnd = random.Random(name + "-item")
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    top = 5
    if cap is not None:
        # the cap's dome, behind the band
        for x in range(3, 13):
            for y in range(top - 2 + (1 if x in (3, 12) else 0), top + 3):
                img.putpixel((x, y), jitter(cap, rnd, 8) + (255,))
        for y in range(top - 3, top + 3):
            img.putpixel((7, y), GOLD[1] + (255,))
            img.putpixel((8, y), GOLD[1] + (255,))
    for y in range(4):
        for x in range(8):
            c = look("front", x, y)
            if c is not None:
                for dx in (0, 1):
                    for dy in (0, 1):
                        img.putpixel((2 * x + dx, top + 2 * y + dy), jitter(c, rnd) + (255,))
    # a dark edge under it, so it reads against the slot
    for x in range(16):
        y = top + 8
        if img.getpixel((x, y - 1))[3]:
            img.putpixel((x, y), (60, 40, 20, 255))
    save(img, "textures/item/" + name)


def save(img, name):
    path = ROOT + name + ".png"
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def write(path, data):
    path = ROOT + path
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


for name, (look, cap) in CROWNS.items():
    worn(name, look, cap)
    item(name, look, cap)
    write("equipment/" + name + ".json", {"layers": {"humanoid": [{"texture": "jaymoskingdoms:" + name}]}})
    write("items/" + name + ".json", {"model": {"type": "minecraft:model", "model": "jaymoskingdoms:item/" + name}})
    write("models/item/" + name + ".json", {"parent": "minecraft:item/generated", "textures": {"layer0": "jaymoskingdoms:item/" + name}})
