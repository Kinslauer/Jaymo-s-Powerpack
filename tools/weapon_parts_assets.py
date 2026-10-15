"""Writes the weapon parts' textures, models and lang (0.11.71, tinkering/WeaponParts): a head or blade for each forged
weapon, in stone and in each metal the weapon comes in.

Each head is cut out of the weapon's own sprite: the pixels where that tier's weapon differs from its wooden one (the
handle is the same wood in every tier). Weapons with no wooden tier (kunai, throwing star, harpoon, iron quarterstaff)
keep their grey metal pixels, and their stone head is that recoloured. Armory sprites come from this repo (Age of
Weapons, MIT); Balkon's WeaponMod sprites (MIT) from its jar:

    python -I tools/weapon_parts_assets.py <unpacked weaponmod jar>

The part list must match com.jaymospowers.tinkering.smeltery.WeaponCasts (same part ids).
"""
import colorsys
import json
import os
import shutil
import sys

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
ASSETS = os.path.join(ROOT, "assets", "jaymospowers")
ARMORY = os.path.join(ASSETS, "textures", "item", "armory")
OUT = "item/tinkering/weapon_part/"

# part id, name, sprite source ("armory:<base>" tiered, "single:<item>", "balkon:<base>"), metals
PARTS = [
    ("dagger_blade", "Dagger Blade", "armory:dagger", ["iron", "gold"]),
    ("hatchet_head", "Hatchet Head", "armory:hatchet", ["iron", "gold"]),
    ("skewer_head", "Skewer Head", "armory:skewer", ["iron", "gold"]),
    ("longsword_blade", "Longsword Blade", "armory:longsword", ["iron", "gold"]),
    ("glaive_blade", "Glaive Blade", "armory:glaive", ["iron", "gold"]),
    ("lance_tip", "Lance Tip", "armory:lance", ["iron", "gold"]),
    ("war_axe_head", "War Axe Head", "armory:war_axe", ["iron", "gold"]),
    ("war_scythe_blade", "War Scythe Blade", "armory:war_scythe", ["iron", "gold"]),
    ("flanged_mace_head", "Flanged Mace Head", "armory:flanged_mace", ["iron", "gold"]),
    ("broadsword_blade", "Broadsword Blade", "armory:broadsword", ["iron", "gold"]),
    ("claymore_blade", "Claymore Blade", "armory:claymore", ["iron", "gold"]),
    ("cleaver_blade", "Cleaver Blade", "armory:cleaver", ["iron", "gold"]),
    ("ninjato_blade", "Ninjato Blade", "armory:ninjato", ["iron", "gold"]),
    ("saber_blade", "Saber Blade", "armory:saber", ["iron", "gold"]),
    ("machete_blade", "Machete Blade", "armory:machete", ["iron", "gold"]),
    ("kunai_blade", "Kunai Blade", "single:kunai", ["iron"]),
    ("throwing_star_blank", "Throwing Star Blank", "single:throwing_star", ["iron"]),
    ("harpoon_head", "Harpoon Head", "single:harpoon", ["iron"]),
    ("quarterstaff_caps", "Quarterstaff Caps", "single:iron_quarterstaff", ["iron"]),
    ("spear_tip", "Spear Tip", "balkon:spear", ["iron", "gold", "copper"]),
    ("knife_blade", "Knife Blade", "balkon:knife", ["iron", "gold", "copper"]),
    ("spear_head", "Spear Head", "balkon:spear", ["iron", "gold", "copper"]),
    ("halberd_head", "Halberd Head", "balkon:halberd", ["iron", "gold", "copper"]),
    ("battleaxe_head", "Battleaxe Head", "balkon:battleaxe", ["iron", "gold", "copper"]),
    ("warhammer_head", "Warhammer Head", "balkon:warhammer", ["iron", "gold", "copper"]),
    ("katana_blade", "Katana Blade", "balkon:katana", ["iron", "gold", "copper"]),
    ("flail_head", "Flail Head", "balkon:flail", ["iron", "gold", "copper"]),
    ("boomerang_edge", "Boomerang Edge", "balkon:boomerang", ["iron", "gold", "copper"]),
]
METAL_NAMES = {"stone": "Stone", "iron": "Iron", "gold": "Gold", "copper": "Copper"}
ARMORY_TIER = {"stone": "stone", "iron": "iron", "gold": "golden"}


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", newline="\n") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def load(path):
    return Image.open(path).convert("RGBA")


def differs(a, b):
    return a[3] > 0 and (b[3] == 0 or abs(a[0] - b[0]) + abs(a[1] - b[1]) + abs(a[2] - b[2]) > 24)


def cut(tier, wooden):
    """The pixels of `tier` that aren't the same as `wooden` (its head)."""
    out = Image.new("RGBA", tier.size, (0, 0, 0, 0))
    for y in range(tier.size[1]):
        for x in range(tier.size[0]):
            if differs(tier.getpixel((x, y)), wooden.getpixel((x, y))):
                out.putpixel((x, y), tier.getpixel((x, y)))
    return out


def grey_pixels(img):
    """Just the grey (metal) pixels of a sprite."""
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    for y in range(img.size[1]):
        for x in range(img.size[0]):
            r, g, b, a = img.getpixel((x, y))
            if a > 0 and max(r, g, b) - min(r, g, b) <= 24:
                out.putpixel((x, y), (r, g, b, a))
    return out


def as_stone(img):
    """A metal head recoloured as stone: darker grey, a touch warm."""
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    for y in range(img.size[1]):
        for x in range(img.size[0]):
            r, g, b, a = img.getpixel((x, y))
            if a > 0:
                lum = (0.3 * r + 0.59 * g + 0.11 * b) / 255.0
                v = int(40 + lum * 110)
                out.putpixel((x, y), (v + 4, v + 2, v, a))
    return out


def tidy(img):
    """Drops stray specks (pieces of under 3 pixels, the handle's outline changing tier), then a head that's under half
    the sprite across is cut out and centred at its own pixel size (Andy: drawn bigger they looked too chunky)."""
    w, h = img.size
    seen, pieces = set(), []
    for y in range(h):
        for x in range(w):
            if (x, y) in seen or img.getpixel((x, y))[3] == 0:
                continue
            piece, todo = [], [(x, y)]
            seen.add((x, y))
            while todo:
                px, py = todo.pop()
                piece.append((px, py))
                for nx, ny in ((px + 1, py), (px - 1, py), (px, py + 1), (px, py - 1), (px + 1, py + 1), (px - 1, py - 1),
                               (px + 1, py - 1), (px - 1, py + 1)):
                    if 0 <= nx < w and 0 <= ny < h and (nx, ny) not in seen and img.getpixel((nx, ny))[3] > 0:
                        seen.add((nx, ny))
                        todo.append((nx, ny))
            pieces.append(piece)
    biggest = max((len(p) for p in pieces), default=0)
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    for piece in pieces:
        if len(piece) >= 3 or len(piece) == biggest:
            for px, py in piece:
                out.putpixel((px, py), img.getpixel((px, py)))
    box = out.getbbox()
    if box is None:
        return out
    bw, bh = box[2] - box[0], box[3] - box[1]
    if max(bw, bh) * 2 > w:
        return out
    head = out.crop(box)
    centred = Image.new("RGBA", img.size, (0, 0, 0, 0))
    centred.paste(head, ((w - head.width) // 2, (h - head.height) // 2))
    return centred


def sprites(source, balkon):
    return {metal: tidy(img) for metal, img in raw_sprites(source, balkon).items()}


def raw_sprites(source, balkon):
    """{metal: head sprite} for a part, stone first."""
    kind, base = source.split(":")
    if kind == "armory":
        wooden = load(os.path.join(ARMORY, "wooden_" + base + ".png"))
        return {m: cut(load(os.path.join(ARMORY, t + "_" + base + ".png")), wooden) for m, t in ARMORY_TIER.items()}
    if kind == "balkon":
        tex = os.path.join(balkon, "assets", "weaponmod", "textures", "item")
        wooden = load(os.path.join(tex, base + ".wood.png"))
        return {m: cut(load(os.path.join(tex, base + "." + m + ".png")), wooden) for m in ("stone", "iron", "gold", "copper")}
    item = load(os.path.join(ARMORY, base + ".png"))
    iron = item if base in ("kunai", "throwing_star") else grey_pixels(item)
    return {"stone": as_stone(iron), "iron": iron}


def main(balkon):
    tex_dir = os.path.join(ASSETS, "textures", OUT)
    shutil.rmtree(tex_dir, ignore_errors=True)
    shutil.rmtree(os.path.join(ASSETS, "models", OUT), ignore_errors=True)
    lang = {"weapon_metal.jaymospowers." + m: n for m, n in METAL_NAMES.items()}
    for part, name, source, metals in PARTS:
        heads = sprites(source, balkon)
        cases = []
        for metal in ["stone"] + metals:
            path = OUT + part + "_" + metal
            os.makedirs(tex_dir, exist_ok=True)
            heads[metal].save(os.path.join(ASSETS, "textures", path + ".png"))
            write(os.path.join(ASSETS, "models", path + ".json"),
                  {"parent": "minecraft:item/generated", "textures": {"layer0": "jaymospowers:" + path}})
            cases.append({"when": metal, "model": {"type": "minecraft:model", "model": "jaymospowers:" + path}})
        write(os.path.join(ASSETS, "items", part + ".json"), {"model": {
            "type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0, "cases": cases,
            "fallback": cases[0]["model"]}})
        lang["item.jaymospowers." + part] = name
    lang.update({
        "gui.jaymospowers.part_builder.tools": "Tool Parts",
        "gui.jaymospowers.part_builder.weapons": "Weapon Parts (%s/%s)",
        "gui.jaymospowers.part_builder.stone_only": "Weapon parts are shaped from stone; metal ones are cast.",
        "tooltip.jaymospowers.tinkering.weapon_part": "Tool Station: %s",
        "tooltip.jaymospowers.tinkering.weapon_part.rod_binding": "with a Tool Handle and a Tool Binding",
        "tooltip.jaymospowers.tinkering.weapon_part.binding": "with a Tool Binding",
        "tooltip.jaymospowers.tinkering.weapon_part.alone": "on its own",
        "tooltip.jaymospowers.tinkering.weapon_part.makes": "Makes: %s",
        "tooltip.jaymospowers.tinkering.weapon_part.cast_only": "Only for making its cast (pour gold over it).",
    })
    write(os.path.join(ASSETS, "lang", "parts", "weapon_parts.json"), lang)
    with open(os.path.join(tex_dir, "LICENSE-WeaponMod.txt"), "w", newline="\n") as f:
        f.write("The Balkon's WeaponMod parts here are cut from Balkon's WeaponMod: Legacy's sprites, under its MIT License:\n\n")
        with open(os.path.join(balkon, "LICENSE")) as src:
            f.write(src.read())
    # Armory parts are cut from Age of Weapons sprites (MIT); its notice is in textures/item/armory


if __name__ == "__main__":
    main(sys.argv[1])
