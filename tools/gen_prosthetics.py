"""(0.18.82) Wooden and iron limbs (core/Prosthetics, client/wounds/ProstheticLayer): their textures, item icons, item
definitions and models, the wooden limbs' carpenter recipes and lang lines. All 16x16, vanilla style (docs/Vanilla-Style.md),
painted in vanilla's own wood and iron ramps (oak planks / stick browns, the iron ingot's greys):

    python -I tools/gen_prosthetics.py

The ids must match com.jaymospowers.core.Prosthetics. The iron limbs have no grid recipe: they are forged at the
Smithing Anvil (tinkering/smithing/ProstheticForging). Lang goes to lang/parts/prosthetics.json.

The entity sheets are 16x16 boxes' UVs for a 4x12x4 limb (top 4,0 / bottom 8,0 / sides 0,4 4,4 8,4 12,4, each 4x12), with
the sleeve end (shoulder or hip) at the top of each side.
"""
import json
import os
import random

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "jaymospowers")
DATA = os.path.join(RES, "data", "jaymospowers")

# vanilla oak planks / sticks, light to dark
WOOD = [(0xB8, 0x94, 0x58), (0xA1, 0x7F, 0x4A), (0x8A, 0x6B, 0x3C), (0x6B, 0x50, 0x2B), (0x4A, 0x37, 0x1C)]
LEATHER = [(0xB0, 0x6A, 0x3C), (0x8E, 0x50, 0x2A), (0x6A, 0x39, 0x1C)]
# vanilla iron ingot / iron block ramp, light to dark
IRON = [(0xF2, 0xF2, 0xF2), (0xD8, 0xD8, 0xD8), (0xB4, 0xB4, 0xB4), (0x8C, 0x8C, 0x8C), (0x5E, 0x5E, 0x5E), (0x3C, 0x3C, 0x3C)]

LANG = {
    "item.jaymospowers.wooden_arm": "Wooden Arm",
    "item.jaymospowers.wooden_leg": "Wooden Leg",
    "item.jaymospowers.iron_arm": "Iron Arm",
    "item.jaymospowers.iron_leg": "Iron Leg",
    "tooltip.jaymospowers.prosthetic.wood": "Pegged planks and leather straps. Slow to swing and a poor grip, but it is a limb.",
    "tooltip.jaymospowers.prosthetic.iron": "Forged at the smithing anvil. Sturdier than wood, and heavy.",
    "tooltip.jaymospowers.prosthetic.fit": "Use it while you are missing that limb to fit it. It drops off if the real one grows back.",
    "message.jaymospowers.prosthetic.nothing_to_fit.arm": "You have no missing arm to fit it to.",
    "message.jaymospowers.prosthetic.nothing_to_fit.leg": "You have no missing leg to fit it to.",
}


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def save(im, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im.save(path)


# ---- the limbs on the body ------------------------------------------------------------------------------------------------


def wood_sheet():
    """Planks run down the limb, a leather cuff at the shoulder or hip, a peg and a worn band at the joint."""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    rng = random.Random(821)
    # the four 4x12 sides
    for sx in (0, 4, 8, 12):
        for y in range(4, 16):
            for x in range(sx, sx + 4):
                col = x - sx
                c = WOOD[0] if col == 0 else WOOD[3] if col == 3 else WOOD[1] if (y // 3 + col) % 2 == 0 else WOOD[2]
                if col in (1, 2) and rng.random() < 0.15:
                    c = WOOD[3]
                px[x, y] = c + (255,)
            # the cuff at the top
            for x in range(sx, sx + 4):
                px[x, 4] = LEATHER[0] + (255,)
                px[x, 5] = LEATHER[1] + (255,)
                px[x, 6] = LEATHER[2] + (255,)
            # the joint: a dark seam and a peg
            for x in range(sx, sx + 4):
                px[x, 10] = WOOD[4] + (255,)
            px[sx + 1, 11] = WOOD[4] + (255,)
    # the ends: end-grain rings
    for (ox, oy) in ((4, 0), (8, 0)):
        for y in range(4):
            for x in range(4):
                ring = 0 if (x in (1, 2) and y in (1, 2)) else 1
                px[ox + x, oy + y] = (WOOD[3] if ring == 0 else WOOD[1]) + (255,)
    return im


def iron_sheet():
    """Plates down the limb with rivets, a dark cuff at the shoulder or hip, a hinge at the joint."""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    rng = random.Random(822)
    for sx in (0, 4, 8, 12):
        for y in range(4, 16):
            for x in range(sx, sx + 4):
                col = x - sx
                c = IRON[1] if col == 0 else IRON[4] if col == 3 else IRON[2]
                if col in (1, 2) and rng.random() < 0.2:
                    c = IRON[3]
                px[x, y] = c + (255,)
            for x in range(sx, sx + 4):
                px[x, 4] = IRON[3] + (255,)
                px[x, 5] = IRON[4] + (255,)
                px[x, 6] = IRON[5] + (255,)
            # rivets on the cuff and a hinge seam
            px[sx, 5] = IRON[0] + (255,)
            px[sx + 3, 5] = IRON[0] + (255,)
            for x in range(sx, sx + 4):
                px[x, 10] = IRON[5] + (255,)
            px[sx + 1, 11] = IRON[0] + (255,)
            px[sx + 2, 11] = IRON[0] + (255,)
    for (ox, oy) in ((4, 0), (8, 0)):
        for y in range(4):
            for x in range(4):
                px[ox + x, oy + y] = (IRON[4] if x in (0, 3) or y in (0, 3) else IRON[2]) + (255,)
    return im


# ---- the item icons ---------------------------------------------------------------------------------------------------------

def paint(rows, pal):
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), [len(r) for r in rows]
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    for y, row in enumerate(rows):
        for x, k in enumerate(row):
            if k != ".":
                px[x, y] = pal[k] + (255,)
    return im


# keys: a light, b mid, c shade, d dark outline, s strap light, t strap dark, r rivet
ARM = [
    "....dddd........",
    "...dsssstd......",
    "...dttttdd......",
    "...dabbcdd......",
    "...dabbcdd......",
    "...dabbcd.......",
    "...dddddd.......",
    "...dabbcd.......",
    "...dabbcd.......",
    "...dabbcd.......",
    "..ddabbcdd......",
    "..dabbbbccd.....",
    "..dabbbbccd.....",
    "..ddabbccdd.....",
    "...dddddddd.....",
    "................",
]
LEG = [
    "......dddd......",
    ".....dsssstd....",
    ".....dttttdd....",
    ".....dabbcdd....",
    ".....dabbcd.....",
    ".....dabbcd.....",
    ".....dddddd.....",
    ".....dabbcd.....",
    ".....dabbcd.....",
    ".....dabbcd.....",
    ".....dabbcd.....",
    "....ddabbcdd....",
    "...dabbbbbbcd...",
    "...dabbbbbbcd...",
    "...dddddddddd...",
    "................",
]


def icon(rows, wood):
    pal_wood = {"a": WOOD[0], "b": WOOD[1], "c": WOOD[2], "d": WOOD[4], "s": LEATHER[0], "t": LEATHER[2], "r": WOOD[3]}
    pal_iron = {"a": IRON[0], "b": IRON[1], "c": IRON[3], "d": IRON[5], "s": IRON[3], "t": IRON[4], "r": IRON[0]}
    im = paint(rows, pal_wood if wood else pal_iron)
    px = im.load()
    # a peg or a rivet at the joint
    px[6, 6] = (WOOD[3] if wood else IRON[0]) + (255,)
    return im


def recipe(pattern, key, result):
    return {"type": "minecraft:crafting_shaped", "category": "equipment", "key": key, "pattern": pattern,
            "result": {"id": "jaymospowers:" + result, "count": 1}}


def main():
    wounds = os.path.join(ASSETS, "textures", "entity", "wounds")
    save(wood_sheet(), os.path.join(wounds, "wooden_limb.png"))
    save(iron_sheet(), os.path.join(wounds, "iron_limb.png"))
    for name, rows, wood in (("wooden_arm", ARM, True), ("wooden_leg", LEG, True), ("iron_arm", ARM, False), ("iron_leg", LEG, False)):
        save(icon(rows, wood), os.path.join(ASSETS, "textures", "item", "prosthetic", name + ".png"))
        write_json(os.path.join(ASSETS, "models", "item", "prosthetic", name + ".json"),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": "jaymospowers:item/prosthetic/" + name}})
        write_json(os.path.join(ASSETS, "items", name + ".json"),
                   {"model": {"type": "minecraft:model", "model": "jaymospowers:item/prosthetic/" + name}})
    # a carpenter's work: planks, sticks and a leather strap (the iron ones are forged, never a grid recipe)
    key = {"P": "#minecraft:planks", "S": "minecraft:stick", "L": "minecraft:leather"}
    write_json(os.path.join(DATA, "recipe", "wooden_arm.json"), recipe(["LP", "PP", " S"], key, "wooden_arm"))
    write_json(os.path.join(DATA, "recipe", "wooden_leg.json"), recipe(["LP", "PS", "PP"], key, "wooden_leg"))
    write_json(os.path.join(ASSETS, "lang", "parts", "prosthetics.json"), LANG)


if __name__ == "__main__":
    main()
