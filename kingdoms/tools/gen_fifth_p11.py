#!/usr/bin/env python3
"""Fifth edition picks, patch 11a: the new classes' items.

Draws the holy symbol, the druid's sprig and the pact seal (16x16, Pillow), and writes their item definitions, models and
recipes, and the recipes of the gadgets a Tinkerer learns to make. Run from kingdoms/: python3 tools/gen_fifth_p11.py
"""
import json
import os

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
ASSETS = os.path.join(ROOT, "assets", "jaymoskingdoms")
DATA = os.path.join(ROOT, "data", "jaymoskingdoms")


def canvas():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def plot(img, pixels, colour):
    for x, y in pixels:
        img.putpixel((x, y), colour)


def holy_symbol():
    """A golden sunburst on a short chain: the symbol of whatever god."""
    img = canvas()
    gold, light, dark, chain = (233, 190, 64, 255), (255, 233, 140, 255), (160, 112, 28, 255), (150, 150, 160, 255)
    plot(img, [(7, 0), (8, 0), (7, 1), (8, 1)], chain)
    plot(img, [(6, 2), (9, 2)], chain)
    # rays
    plot(img, [(7, 4), (8, 4), (7, 11), (8, 11), (4, 7), (4, 8), (11, 7), (11, 8)], gold)
    plot(img, [(5, 5), (10, 5), (5, 10), (10, 10)], gold)
    plot(img, [(7, 3), (8, 3), (7, 12), (8, 12), (3, 7), (3, 8), (12, 7), (12, 8)], dark)
    # the disc
    for y in range(5, 11):
        for x in range(5, 11):
            if (x - 7.5) ** 2 + (y - 7.5) ** 2 <= 8.5:
                img.putpixel((x, y), gold)
    plot(img, [(6, 6), (7, 6), (6, 7)], light)
    plot(img, [(9, 9), (9, 8), (8, 9)], dark)
    plot(img, [(7, 7), (8, 7), (7, 8), (8, 8)], (255, 250, 210, 255))
    return img


def druid_sprig():
    """A crooked oak twig with leaves and an acorn."""
    img = canvas()
    bark, bark_dark = (122, 84, 48, 255), (84, 56, 30, 255)
    leaf, leaf_light, leaf_dark = (82, 150, 54, 255), (130, 196, 84, 255), (52, 104, 40, 255)
    stem = [(3, 14), (4, 13), (5, 12), (6, 11), (7, 10), (8, 9), (8, 8), (9, 7), (10, 6), (11, 5), (12, 4)]
    plot(img, stem, bark)
    plot(img, [(4, 14), (6, 12), (8, 10), (9, 8), (11, 6)], bark_dark)
    # leaves along the twig
    for cx, cy in [(5, 9), (10, 10), (7, 6), (12, 8), (11, 2), (14, 5)]:
        plot(img, [(cx, cy), (cx + 1, cy), (cx, cy + 1), (cx - 1, cy), (cx, cy - 1)], leaf)
        plot(img, [(cx, cy - 1)], leaf_light)
        plot(img, [(cx, cy + 1)], leaf_dark)
    plot(img, [(5, 10), (10, 9), (7, 7), (12, 7)], bark_dark)
    # an acorn
    plot(img, [(3, 11), (4, 11), (3, 12)], (190, 140, 70, 255))
    plot(img, [(3, 10), (4, 10)], (100, 70, 36, 255))
    return img


def pact_seal():
    """A disc of dark violet wax with a sigil, on a ribbon."""
    img = canvas()
    wax, wax_light, wax_dark = (94, 36, 130, 255), (150, 82, 196, 255), (52, 18, 78, 255)
    rune = (232, 200, 255, 255)
    for y in range(16):
        for x in range(16):
            d = (x - 7.5) ** 2 + (y - 8.5) ** 2
            if d <= 30:
                img.putpixel((x, y), wax)
            if 24 <= d <= 30:
                img.putpixel((x, y), wax_dark)
    plot(img, [(4, 5), (5, 4), (6, 4), (4, 6)], wax_light)
    # the sigil: a crooked eye over a flame
    plot(img, [(5, 7), (6, 6), (7, 6), (8, 6), (9, 6), (10, 7), (6, 8), (7, 8), (8, 8), (9, 8)], rune)
    plot(img, [(7, 7), (8, 7)], (255, 90, 90, 255))
    plot(img, [(7, 10), (8, 10), (7, 11), (8, 11), (8, 9)], rune)
    # ribbon
    plot(img, [(7, 0), (8, 0), (6, 1), (9, 1), (7, 2), (8, 2), (6, 3), (9, 3)], (170, 30, 40, 255))
    return img


ITEMS = {"holy_symbol": holy_symbol, "druid_sprig": druid_sprig, "pact_seal": pact_seal}

RECIPES = {
    "holy_symbol": {"type": "minecraft:crafting_shaped", "category": "equipment", "key": {"G": "minecraft:gold_ingot", "I": "minecraft:iron_ingot"},
                    "pattern": [" G ", "GIG", " G "], "result": {"id": "jaymoskingdoms:holy_symbol"}},
    "druid_sprig": {"type": "minecraft:crafting_shapeless", "category": "equipment",
                    "ingredients": ["minecraft:stick", "minecraft:oak_leaves", "minecraft:bone_meal"], "result": {"id": "jaymoskingdoms:druid_sprig"}},
    "pact_seal": {"type": "minecraft:crafting_shapeless", "category": "equipment",
                  "ingredients": ["minecraft:amethyst_shard", "minecraft:gold_nugget", "minecraft:black_dye"], "result": {"id": "jaymoskingdoms:pact_seal"}},
    "immovable_rod": {"type": "minecraft:crafting_shaped", "category": "equipment", "key": {"I": "minecraft:iron_ingot", "B": "minecraft:blaze_rod"},
                      "pattern": ["I", "B", "I"], "result": {"id": "jaymoskingdoms:immovable_rod"}},
    "climbing_rope": {"type": "minecraft:crafting_shaped", "category": "equipment", "key": {"S": "minecraft:string", "I": "minecraft:iron_ingot"},
                      "pattern": ["SSS", "SIS", "SSS"], "result": {"id": "jaymoskingdoms:climbing_rope"}},
    "endless_jug": {"type": "minecraft:crafting_shapeless", "category": "equipment",
                    "ingredients": ["minecraft:glass_bottle", "minecraft:water_bucket", "minecraft:ender_pearl"], "result": {"id": "jaymoskingdoms:endless_jug"}},
    "floating_light": {"type": "minecraft:crafting_shapeless", "category": "equipment",
                       "ingredients": ["minecraft:lantern", "minecraft:feather", "minecraft:glowstone_dust"], "result": {"id": "jaymoskingdoms:floating_light"}},
    "smoke_bottle": {"type": "minecraft:crafting_shapeless", "category": "equipment",
                     "ingredients": ["minecraft:glass_bottle", "minecraft:charcoal", "minecraft:gunpowder"], "result": {"id": "jaymoskingdoms:smoke_bottle"}},
}


def write_json(path, value):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(value, f, indent=2)
        f.write("\n")


def main():
    for name, draw in ITEMS.items():
        tex = os.path.join(ASSETS, "textures", "item", name + ".png")
        os.makedirs(os.path.dirname(tex), exist_ok=True)
        draw().save(tex)
        write_json(os.path.join(ASSETS, "items", name + ".json"), {"model": {"type": "minecraft:model", "model": "jaymoskingdoms:item/" + name}})
        write_json(os.path.join(ASSETS, "models", "item", name + ".json"),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": "jaymoskingdoms:item/" + name}})
    for name, recipe in RECIPES.items():
        write_json(os.path.join(DATA, "recipe", name + ".json"), recipe)
    tag = os.path.join(DATA, "tags", "item", "spell_foci.json")
    with open(tag) as f:
        values = json.load(f)
    for name in ITEMS:
        if "jaymoskingdoms:" + name not in [v if isinstance(v, str) else v["id"] for v in values["values"]]:
            values["values"].append("jaymoskingdoms:" + name)
    write_json(tag, values)


if __name__ == "__main__":
    main()
