"""Writes the patrons' weapons' art and models (Patrons part c; our own pixel art, drawn here): the eight weapons
(armory/PatronWeapons, 32x32 like the Armory's). Their iron heads are drawn by weapon_parts_assets.py's DRAWN.

    python -I tools/patron_weapons_assets.py [preview.png]
"""
import json
import os
import sys

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import weapon_parts_assets as wpa  # noqa: E402

POWERS = os.path.join(HERE, "..", "src", "main", "resources", "assets", "jaymospowers")


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", newline="\n") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def dark(c, f):
    return tuple(max(0, int(v * f)) for v in c[:3]) + (255,)


def outline(img, factor=0.35):
    """Every opaque pixel that touches a clear one (4 ways) darkens into its outline."""
    w, h = img.size
    src = img.copy()
    for y in range(h):
        for x in range(w):
            p = src.getpixel((x, y))
            if p[3] == 0:
                continue
            edge = any(not (0 <= x + dx < w and 0 <= y + dy < h) or src.getpixel((x + dx, y + dy))[3] == 0
                       for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            if edge:
                img.putpixel((x, y), dark(p, factor))
    return img


def shine(img, colour, light):
    """Pixels of `colour` with clear (or outline) to their upper left catch the light."""
    w, h = img.size
    src = img.copy()
    for y in range(1, h):
        for x in range(1, w):
            if src.getpixel((x, y))[:3] == colour[:3]:
                up = src.getpixel((x - 1, y - 1))
                if up[3] == 0 or sum(up[:3]) < sum(colour[:3]) * 0.6:
                    img.putpixel((x, y), light)


def thick(d, a, b, width, colour):
    d.line([a, b], fill=colour, width=width)


def hexc(h):
    return (int(h[1:3], 16), int(h[3:5], 16), int(h[5:7], 16), 255)


IRON, IRON_LIGHT = (168, 168, 172, 255), (222, 222, 226, 255)
STEEL = hexc("#B0B4BC")
WOOD, WOOD_DARK = (104, 78, 30, 255), (74, 52, 24, 255)
LEATHER = (122, 78, 46, 255)
AMBER, SEA, LEAF = hexc("#E0A050"), hexc("#3A8FB7"), hexc("#5E9E3A")
PALE, GOLD, ASH, EMBER, WHITE = hexc("#9AA8FF"), hexc("#FFC21A"), hexc("#8A5A4A"), hexc("#FF7A2A"), hexc("#F2F2F2")


def canvas():
    img = Image.new("RGBA", (32, 32))
    return img, ImageDraw.Draw(img)


def hearthguard():
    img, d = canvas()
    thick(d, (4, 28), (18, 14), 3, WOOD)
    thick(d, (4, 28), (6, 26), 3, LEATHER)
    d.polygon([(14, 15), (19, 8), (26, 7), (27, 14), (21, 20)], fill=STEEL)  # a flanged head
    for p in ((17, 6), (24, 4), (29, 9), (25, 19), (15, 20)):
        d.polygon([p, (p[0] + 2, p[1]), (p[0] + 1, p[1] - 2)], fill=STEEL)
    d.ellipse([20, 11, 24, 15], fill=EMBER)  # the ember in its heart
    d.point([(21, 12)], fill=AMBER)
    img = outline(img, 0.35)
    shine(img, STEEL, IRON_LIGHT)
    return img


def anvil_fathers_hammer():
    img, d = canvas()
    thick(d, (3, 29), (19, 13), 3, WOOD)
    d.polygon([(11, 13), (19, 5), (28, 14), (20, 22)], fill=STEEL)  # a broad block
    d.polygon([(14, 13), (20, 7), (26, 14), (20, 20)], fill=IRON)
    thick(d, (13, 11), (22, 20), 2, AMBER)  # bands of amber
    d.point([(18, 11), (22, 12)], fill=IRON_LIGHT)
    img = outline(img, 0.35)
    shine(img, STEEL, IRON_LIGHT)
    return img


def tidecaller():
    img, d = canvas()
    thick(d, (4, 28), (17, 15), 3, WOOD_DARK)
    d.polygon([(15, 17), (13, 9), (18, 3), (26, 2), (29, 4), (23, 6), (20, 10), (22, 16), (18, 19)], fill=STEEL)  # a crescent
    d.line([(17, 8), (25, 3)], fill=IRON_LIGHT)
    d.point([(14, 14), (16, 18)], fill=SEA)
    thick(d, (15, 18), (18, 15), 2, SEA)  # the sea-blue binding
    img = outline(img, 0.35)
    return img


def huntsmans_spear():
    img, d = canvas()
    thick(d, (2, 30), (22, 10), 2, WOOD)
    for t in (0.2, 0.35):
        x, y = int(2 + 20 * t), int(30 - 20 * t)
        d.point([(x, y), (x + 1, y)], fill=LEAF)
    d.polygon([(21, 11), (24, 14), (30, 2), (18, 8)], fill=STEEL)  # a leaf-shaped tip
    d.line([(21, 10), (28, 3)], fill=IRON_LIGHT)
    d.polygon([(19, 13), (22, 16), (20, 17)], fill=LEAF)
    img = outline(img, 0.35)
    return img


def gravelight():
    img, d = canvas()
    thick(d, (6, 29), (20, 11), 2, WOOD_DARK)
    d.arc([8, 1, 30, 22], 200, 330, fill=STEEL, width=3)  # a long curved blade
    d.line([(10, 9), (14, 4)], fill=STEEL, width=2)
    d.point([(14, 6), (18, 3), (23, 3)], fill=IRON_LIGHT)
    d.line([(4, 24), (4, 27)], fill=IRON)  # the lantern hook
    d.rectangle([2, 26, 6, 30], fill=PALE)
    d.point([(4, 28)], fill=WHITE)
    img = outline(img, 0.35)
    return img


def sunbrand():
    img, d = canvas()
    thick(d, (3, 29), (9, 23), 3, LEATHER)
    d.rectangle([7, 19, 14, 21], fill=GOLD)  # the guard
    thick(d, (11, 21), (26, 6), 3, STEEL)
    d.polygon([(25, 4), (29, 3), (28, 7)], fill=STEEL)
    thick(d, (12, 20), (24, 8), 1, GOLD)  # the sun-ray fuller
    d.point([(16, 16), (20, 12)], fill=WHITE)
    d.point([(9, 20)], fill=WHITE)
    img = outline(img, 0.35)
    shine(img, STEEL, IRON_LIGHT)
    return img


def ashfang():
    img, d = canvas()
    thick(d, (5, 28), (11, 22), 3, LEATHER)
    d.rectangle([9, 19, 13, 21], fill=ASH)
    d.polygon([(11, 20), (13, 22), (25, 14), (28, 5), (22, 9)], fill=ASH)  # a short curved blade
    d.line([(14, 20), (26, 8)], fill=STEEL)
    d.point([(16, 18), (21, 14), (25, 9)], fill=EMBER)
    img = outline(img, 0.35)
    return img


def plain_word():
    img, d = canvas()
    thick(d, (3, 29), (9, 23), 3, WOOD_DARK)
    d.rectangle([6, 18, 15, 20], fill=IRON)
    thick(d, (11, 20), (26, 5), 4, STEEL)  # a plain wide blade
    d.polygon([(25, 2), (30, 2), (30, 7), (26, 6)], fill=STEEL)
    thick(d, (12, 19), (25, 6), 1, IRON_LIGHT)
    img = outline(img, 0.35)
    shine(img, STEEL, IRON_LIGHT)
    return img


WEAPONS = {"hearthguard": hearthguard, "anvil_fathers_hammer": anvil_fathers_hammer, "tidecaller": tidecaller,
           "huntsmans_spear": huntsmans_spear, "gravelight": gravelight, "sunbrand": sunbrand, "ashfang": ashfang,
           "plain_word": plain_word}
HEADS = ["hearthguard_head", "anvil_fathers_hammer_head", "tidecaller_head", "huntsmans_spear_tip", "gravelight_blade",
         "sunbrand_blade", "ashfang_blade", "plain_word_blade"]


def heads():
    """Each iron head's sprite, model and item (the kings' heads' shape: iron and stone, iron as the fallback)."""
    for part in HEADS:
        sprites = wpa.sprites("drawn:" + part, None)
        cases = []
        for metal in ["stone", "iron"]:
            path = wpa.OUT + part + "_" + metal
            os.makedirs(os.path.join(POWERS, "textures", wpa.OUT), exist_ok=True)
            sprites[metal].save(os.path.join(POWERS, "textures", path + ".png"))
            write(os.path.join(POWERS, "models", path + ".json"),
                  {"parent": "minecraft:item/generated", "textures": {"layer0": "jaymospowers:" + path}})
            cases.append({"when": metal, "model": {"type": "minecraft:model", "model": "jaymospowers:" + path}})
        write(os.path.join(POWERS, "items", part + ".json"), {"model": wpa.hot({
            "type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0, "cases": cases,
            "fallback": cases[1]["model"]})})


def main(preview=None):
    armory = os.path.join(POWERS, "textures", "item", "armory")
    os.makedirs(armory, exist_ok=True)
    images = {}
    for name, fn in WEAPONS.items():
        img = fn()
        images[name] = img
        img.save(os.path.join(armory, name + ".png"))
        write(os.path.join(POWERS, "models", "item", "armory", name + ".json"),
              {"parent": "minecraft:item/handheld", "textures": {"layer0": "jaymospowers:item/armory/" + name}})
        write(os.path.join(POWERS, "items", name + ".json"),
              {"model": {"type": "minecraft:model", "model": "jaymospowers:item/armory/" + name}})
    heads()
    if preview:
        sheet = Image.new("RGBA", (32 * 8, 32), (60, 90, 120, 255))
        for i, img in enumerate(images.values()):
            sheet.alpha_composite(img, (i * 32, 0))
        sheet.resize((32 * 8 * 4, 128), Image.NEAREST).save(preview)


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else None)
