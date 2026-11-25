"""Writes the leech's kit's art and data (Kingdoms, Visceral track 7): the item sprites, item definitions, models, recipes
and the advancements that teach the mixes.

    python -I tools/gen_leech_kit.py

Needs only Pillow. Every shape and pixel is ours (vanilla-style 16px icons); nothing is copied from Visceral or anyone else.
The ids must match com.jaymoskingdoms.LeechKit.
"""
import json
import os

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "kingdoms", "src", "main", "resources")
NS = "jaymoskingdoms"
ASSETS = os.path.join(ROOT, "assets", NS)
DATA = os.path.join(ROOT, "data", NS)

# thorn id -> (liquid colour, group)
THORNS = {
    "staunching_thorn": ("c8453c", "heal"),
    "numbing_thorn": ("8fc8e8", "heal"),
    "quickening_thorn": ("f0d040", "heal"),
    "restoring_thorn": ("e0509a", "heal"),
    "knockout_thorn": ("7a6cc0", "knock"),
    "corroding_thorn": ("78b030", "dark"),
    "stripping_thorn": ("d8d4b8", "dark"),
    "rotting_thorn": ("5a6a28", "dark"),
    "raising_thorn": ("5a2a78", "dark"),
    "frenzy_thorn": ("d8481c", "mix"),
    "unyielding_thorn": ("c89a38", "mix"),
    "bonewash_thorn": ("a8d8c0", "mix"),
}
OUTLINE = (0x3a, 0x40, 0x48, 255)
GLASS = (0xb8, 0xdc, 0xe8, 255)
GLASS_HI = (0xea, 0xf8, 0xfc, 255)
BONE = (0xe8, 0xe2, 0xcc, 255)
BONE_SH = (0xb8, 0xb0, 0x94, 255)
CORK = (0x8a, 0x5e, 0x34, 255)
CORK_SH = (0x62, 0x40, 0x22, 255)


def rgb(h, k=1.0):
    return tuple(min(255, int(int(h[i:i + 2], 16) * k)) for i in (0, 2, 4)) + (255,)


def put(img, x, y, c):
    if 0 <= x < 16 and 0 <= y < 16:
        img.putpixel((x, y), c)


def thorn(liquid):
    """A hollow thorn on the diagonal: cork at the lower left, a glass tube holding the draught, a bone point at the upper right."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for i in range(1, 15):
        x, y = i, 15 - i
        if i <= 3:
            put(img, x, y, CORK)
            put(img, x + 1, y, CORK_SH)
            put(img, x, y - 1, CORK_SH)
        elif i <= 9:
            # the tube: outline, glass, and the draught inside
            put(img, x, y, rgb(liquid, 0.85) if liquid else GLASS_HI)
            put(img, x + 1, y, rgb(liquid) if liquid else GLASS)
            put(img, x, y - 1, rgb(liquid, 1.15) if liquid else GLASS_HI)
            put(img, x - 1, y, GLASS)
            put(img, x + 2, y, OUTLINE)
            put(img, x, y + 1, OUTLINE)
        elif i <= 12:
            put(img, x, y, BONE)
            put(img, x + 1, y, BONE_SH)
            put(img, x, y - 1, BONE_SH)
        else:
            put(img, x, y, BONE)
    put(img, 2, 12, OUTLINE)
    put(img, 1, 14, OUTLINE)
    put(img, 14, 1, BONE_SH)
    return img


def dart():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    wood = (0x9a, 0x74, 0x42, 255)
    for i in range(3, 13):
        put(img, i, 15 - i, wood)
    for x, y in ((2, 13), (3, 12), (2, 12), (3, 13), (4, 14), (1, 12), (3, 11)):
        put(img, x, y, (0xf2, 0xf0, 0xe8, 255))
    put(img, 2, 14, (0xc8, 0xc4, 0xb8, 255))
    put(img, 13, 2, BONE)
    put(img, 14, 1, BONE)
    put(img, 12, 3, rgb("7a6cc0"))
    return img


def blowgun():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for i in range(1, 15):
        x, y = i, 15 - i
        put(img, x, y, (0x7a, 0xa8, 0x48, 255))
        put(img, x + 1, y, (0x58, 0x84, 0x30, 255))
        put(img, x, y - 1, (0xa0, 0xcc, 0x68, 255))
        if i in (4, 9):
            put(img, x, y, (0x44, 0x62, 0x24, 255))
            put(img, x + 1, y, (0x44, 0x62, 0x24, 255))
            put(img, x, y - 1, (0x44, 0x62, 0x24, 255))
    put(img, 1, 14, (0x30, 0x40, 0x20, 255))
    put(img, 14, 1, (0x30, 0x40, 0x20, 255))
    return img


def scroll():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    paper, shade = (0xe8, 0xdc, 0xb0, 255), (0xc0, 0xb0, 0x80, 255)
    for x in range(2, 14):
        for y in range(4, 12):
            put(img, x, y, paper if y < 10 else shade)
    for y in range(3, 13):
        put(img, 1, y, shade)
        put(img, 14, y, shade)
    for x in range(1, 15):
        put(img, x, 3, (0x8a, 0x6a, 0x3a, 255))
        put(img, x, 12, (0x8a, 0x6a, 0x3a, 255))
    leaf = (0x4a, 0x9a, 0x3a, 255)
    for x, y in ((7, 6), (8, 6), (6, 7), (7, 7), (8, 7), (9, 7), (7, 8), (8, 8), (7, 9)):
        put(img, x, y, leaf)
    return img


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", newline="\n") as f:
        f.write(text)


def item_files(name, image):
    tex = os.path.join(ASSETS, "textures", "item", name + ".png")
    os.makedirs(os.path.dirname(tex), exist_ok=True)
    image.save(tex)
    write(os.path.join(ASSETS, "items", name + ".json"),
          json.dumps({"model": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}}, indent=2))
    write(os.path.join(ASSETS, "models", "item", name + ".json"),
          json.dumps({"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{name}"}}, indent=2))


def recipe(name, ingredients, count=1, group="leech_kit"):
    write(os.path.join(DATA, "recipe", name + ".json"), json.dumps({
        "type": "minecraft:crafting_shapeless", "category": "misc", "group": f"{NS}:{group}",
        "ingredients": ingredients, "result": {"count": count, "id": f"{NS}:{name}"}}, indent=2))


def teach(name, needs):
    """The recipe `name` is taught by an advancement, granted the first time any item of `needs` is held."""
    criteria = {}
    for n in needs:
        criteria["has_" + n] = {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": f"{NS}:{n}"}]}}
    criteria["has_the_recipe"] = {"trigger": "minecraft:recipe_unlocked", "conditions": {"recipes": f"{NS}:{name}"}}
    write(os.path.join(DATA, "advancement", "recipes", "leech_kit", name + ".json"), json.dumps({
        "parent": "minecraft:recipes/root", "criteria": criteria, "requirements": [list(criteria)],
        "rewards": {"recipes": [f"{NS}:{name}"]}}, indent=2))


def main():
    item_files("empty_thorn", thorn(None))
    for name, (liquid, _) in THORNS.items():
        item_files(name, thorn(liquid))
    item_files("sleep_dart", dart())
    item_files("blowgun", blowgun())
    item_files("scroll_of_green_return", scroll())

    m = lambda i: "minecraft:" + i
    t = lambda i: f"{NS}:{i}"
    recipe("empty_thorn", [m("bone"), m("glass_pane")], 2)
    recipe("staunching_thorn", [t("empty_thorn"), m("cobweb"), m("sweet_berries")])
    recipe("numbing_thorn", [t("empty_thorn"), m("poppy"), m("snowball")])
    recipe("quickening_thorn", [t("empty_thorn"), m("sugar"), m("glowstone_dust")])
    recipe("restoring_thorn", [t("empty_thorn"), m("glistering_melon_slice"), m("nether_wart")])
    recipe("knockout_thorn", [t("empty_thorn"), t("sleep_draught")])
    recipe("corroding_thorn", [t("empty_thorn"), m("fermented_spider_eye"), m("gunpowder")])
    recipe("stripping_thorn", [t("empty_thorn"), m("bone"), m("slime_ball")])
    recipe("rotting_thorn", [t("empty_thorn"), m("rotten_flesh"), m("fermented_spider_eye")])
    recipe("raising_thorn", [t("empty_thorn"), m("wither_rose"), m("soul_sand")])
    recipe("sleep_dart", [t("knockout_thorn"), m("feather")], 2)
    write(os.path.join(DATA, "recipe", "blowgun.json"), json.dumps({
        "type": "minecraft:crafting_shaped", "category": "equipment", "pattern": ["B", "B", "B"],
        "key": {"B": m("bamboo")}, "result": {"count": 1, "id": t("blowgun")}}, indent=2))
    # the mixes: two filled thorns make a third; one advancement each teaches it
    mixes = {
        "frenzy_thorn": ("quickening_thorn", "restoring_thorn"),
        "unyielding_thorn": ("restoring_thorn", "numbing_thorn"),
        "bonewash_thorn": ("corroding_thorn", "stripping_thorn"),
    }
    for name, (a, b) in mixes.items():
        recipe(name, [t(a), t(b)], 1, "leech_kit_mix")
        teach(name, [a, b])


if __name__ == "__main__":
    main()
