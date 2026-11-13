#!/usr/bin/env python3
"""
The blossom lands' animals and night things (Blossom Lands patches 10 to 14): their shapes and sheets, drawn our own way
with the creature tools of tools/gen_creatures.py (a list of boxes each, packed and painted into a sheet, written as
assets/jaymoskingdoms/creature_models/<name>.json for CreatureModel.java), their spawn eggs and their names. Run from
kingdoms/:

    python3 -I tools/gen_blossom_creatures.py
"""
import json
import os
import sys
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "..", "tools"))
import gen_creatures as gc  # noqa: E402

P, hexc, shade, jitter = gc.P, gc.hexc, gc.shade, gc.jitter
ASSETS = os.path.join(gc.ROOT)

# name: (shape, egg colours, shown name)
CREATURES = {}
EGGS = {}
NAMES = {}


def creature(name, shown, egg, scale, palette, parts, translucent=False):
    CREATURES[name] = dict(scale=scale, translucent=translucent, palette=palette, parts=parts)
    EGGS[name] = egg
    NAMES[name] = shown


# ---- the animals -----------------------------------------------------------------------------------------------------
# Ground is y=24; boxes are [x, y, z, width, height, depth] from the pivot; a child's pivot is from its parent's pivot.

creature("koi", "Koi", ("#f2efe6", "#e8641e"), 1.0, ("#f2efe6", "#e8641e"), [
    P("body", [0, 21, 0], [-2, -2, -5, 4, 5, 9], style="koi"),
    P("head", [0, 0, -5], [-1.5, -1.5, -3, 3, 4, 3], "", "koi", "body", eyes=dict(c="#16161a", at=[1, 1], s=[1, 1])),
    P("dorsal", [0, -2, -1], [-0.5, -2, -2, 1, 2, 5], "", "fin", "body"),
    P("tail", [0, 0, 4], [-0.5, -2, 0, 1, 5, 4], "tail", "fin", "body"),
    P("finL", [2, 2, -3], [0, 0, -1, 2, 1, 2], "", "fin", "body"),
    P("finR", [-2, 2, -3], [-2, 0, -1, 2, 1, 2], "", "fin", "body"),
])

# the snow monkey: a stocky grey-brown macaque, bare pink face, short tail
creature("snow_monkey", "Snow Monkey", ("#9a9184", "#d9705a"), 0.85, ("#9a9184", "#6e665b"), [
    P("body", [0, 15, 0], [-3, -8, -2, 6, 8, 5], style="fur"),
    P("head", [0, -8, 0], [-3, -6, -3, 6, 6, 6], "head", "macaque", "body", eyes=dict(c="#2a1c14", at=[1, 2], s=[1, 1])),
    P("armR", [-4, -7, 0], [-1, 0, -1, 2, 9, 2], "armR", "fur", "body"),
    P("armL", [4, -7, 0], [-1, 0, -1, 2, 9, 2], "armL", "fur", "body"),
    P("tail", [0, -1, 3], [-0.5, -1, 0, 1, 1, 2], "", "fur", "body"),
    P("legR", [-1.5, 15, 0], [-1.5, 0, -1.5, 3, 9, 3], "legR", "fur"),
    P("legL", [1.5, 15, 0], [-1.5, 0, -1.5, 3, 9, 3], "legL", "fur"),
])

# the red-crowned crane: white, black neck and tail plumes, a red cap, long dark legs
creature("crane", "Crane", ("#f4f2ec", "#c8281e"), 0.9, ("#f4f2ec", "#1c1c20"), [
    P("body", [0, 12, 0], [-2.5, -3, -5, 5, 5, 10], style="#f4f2ec"),
    P("neck", [0, -2, -4], [-1, -9, -1, 2, 9, 2], "neck", "#1c1c20", "body"),
    P("head", [0, -9, 0], [-1.5, -3, -2.5, 3, 3, 4], "", "crown", "neck", eyes=dict(c="#101012", at=[1, 1], s=[1, 1])),
    P("beak", [0, -1, -2.5], [-0.5, -0.5, -4, 1, 1, 4], "", "#b8a25a", "head"),
    P("wingL", [2.5, -3, 0], [0, 0, -4, 8, 1, 9], "wingL", "wing", "body"),
    P("wingR", [-2.5, -3, 0], [-8, 0, -4, 8, 1, 9], "wingR", "wing", "body"),
    P("plumes", [0, -2, 5], [-2, -1, 0, 4, 4, 3], "", "#1c1c20", "body"),
    P("legR", [-1, 14, 0], [-0.5, 0, -0.5, 1, 10, 1], "legR", "#3a3a40"),
    P("legL", [1, 14, 0], [-0.5, 0, -0.5, 1, 10, 1], "legL", "#3a3a40"),
])

# the shrine deer: small, chestnut with white spots, white rump, short antlers
creature("shrine_deer", "Shrine Deer", ("#a8683a", "#f2ead8"), 0.9, ("#a8683a", "#7a4826"), [
    P("body", [0, 13, 0], [-2.5, -3, -6, 5, 6, 11], style="sika"),
    P("neck", [0, -2, -5], [-1.5, -6, -2, 3, 7, 3], "neck", "fur", "body"),
    P("head", [0, -6, 0], [-1.5, -3, -4, 3, 3, 5], "", "fur", "neck", eyes=dict(c="#1a120c", at=[1, 1], s=[1, 1])),
    P("earL", [1.5, -3, 0], [0, -2, 0, 1, 2, 1], "", "fur", "head"),
    P("earR", [-1.5, -3, 0], [-1, -2, 0, 1, 2, 1], "", "fur", "head"),
    P("antlerL", [1, -3, -1], [-0.5, -4, 0, 1, 4, 1], "", "bone", "head"),
    P("antlerR", [-1, -3, -1], [-0.5, -4, 0, 1, 4, 1], "", "bone", "head"),
    P("tail", [0, -2, 5], [-1, 0, 0, 2, 3, 1], "", "#f2ead8", "body"),
    P("legFR", [-1.5, 16, -4], [-1, 0, -1, 2, 8, 2], "legR", "fur"),
    P("legFL", [1.5, 16, -4], [-1, 0, -1, 2, 8, 2], "legL", "fur"),
    P("legBR", [-1.5, 16, 4], [-1, 0, -1, 2, 8, 2], "legL", "fur"),
    P("legBL", [1.5, 16, 4], [-1, 0, -1, 2, 8, 2], "legR", "fur"),
])

# the raccoon dog: low and round, grey-brown, a dark mask and dark legs, a short thick tail
creature("raccoon_dog", "Raccoon Dog", ("#8a7a66", "#2a2420"), 0.9, ("#8a7a66", "#5e5244"), [
    P("body", [0, 18, 0], [-3, -3, -5, 6, 5, 10], style="fur"),
    P("head", [0, 17, -5], [-3, -3, -4, 6, 5, 4], "neck", "mask", eyes=dict(c="#d8c8a0", at=[1, 2], s=[1, 1])),
    P("snout", [0, 1, -4], [-1, -1, -2, 2, 2, 2], "", "#2a2420", "head"),
    P("earL", [2, -3, -2], [0, -2, 0, 1, 2, 1], "", "#2a2420", "head"),
    P("earR", [-2, -3, -2], [-1, -2, 0, 1, 2, 1], "", "#2a2420", "head"),
    P("tail", [0, -1, 5], [-1.5, -1.5, 0, 3, 3, 6], "tail", "#5e5244", "body"),
    P("legFR", [-2, 20, -3], [-1, 0, -1, 2, 4, 2], "legR", "#2a2420"),
    P("legFL", [2, 20, -3], [-1, 0, -1, 2, 4, 2], "legL", "#2a2420"),
    P("legBR", [-2, 20, 3], [-1, 0, -1, 2, 4, 2], "legL", "#2a2420"),
    P("legBL", [2, 20, 3], [-1, 0, -1, 2, 4, 2], "legR", "#2a2420"),
])

# the serow: dark and shaggy, a pale throat, short back-swept horns
creature("serow", "Serow", ("#3a3632", "#b8ac98"), 1.0, ("#3a3632", "#24211e"), [
    P("body", [0, 12, 0], [-3, -4, -6, 6, 8, 12], style="shag"),
    P("neck", [0, -3, -5], [-2, -5, -2, 4, 6, 4], "neck", "shag", "body"),
    P("head", [0, -5, 0], [-2, -3, -5, 4, 4, 6], "", "fur", "neck", eyes=dict(c="#c8a040", at=[1, 1], s=[1, 1])),
    P("throat", [0, 1, -2], [-1.5, 0, -1, 3, 2, 3], "", "#b8ac98", "head"),
    P("hornL", [1.5, -3, 0], [-0.5, -3, 0, 1, 3, 1], "", "#18161a", "head"),
    P("hornR", [-1.5, -3, 0], [-0.5, -3, 0, 1, 3, 1], "", "#18161a", "head"),
    P("legFR", [-2, 16, -4], [-1, 0, -1, 2, 8, 2], "legR", "fur"),
    P("legFL", [2, 16, -4], [-1, 0, -1, 2, 8, 2], "legL", "fur"),
    P("legBR", [-2, 16, 4], [-1, 0, -1, 2, 8, 2], "legL", "fur"),
    P("legBL", [2, 16, 4], [-1, 0, -1, 2, 8, 2], "legR", "fur"),
])

# the paper hornet: orange head, dark thorax, a banded abdomen, clear wings
creature("hornet", "Paper Hornet", ("#e89a1e", "#2a1c10"), 0.6, ("#e89a1e", "#2a1c10"), [
    P("thorax", [0, 19, 0], [-2, -2, -2, 4, 4, 4], style="#2a1c10"),
    P("head", [0, 0, -2], [-2, -2, -3, 4, 4, 3], "", "#e89a1e", "thorax", eyes=dict(c="#1a1008", at=[1, 1], s=[1, 2])),
    P("abdomen", [0, 0, 2], [-2, -2, 0, 4, 4, 7], "", "bands", "thorax"),
    P("sting", [0, 0, 7], [-0.5, -0.5, 0, 1, 1, 2], "", "#1a1008", "abdomen"),
    P("wingL", [2, -2, 0], [0, 0, -1, 7, 1, 4], "wingL", "clearwing", "thorax"),
    P("wingR", [-2, -2, 0], [-7, 0, -1, 7, 1, 4], "wingR", "clearwing", "thorax"),
])

# the giant salamander: long, flat and wide-headed, mottled brown, stub legs, a paddle tail
creature("giant_salamander", "Giant Salamander", ("#5e4a36", "#2e2218"), 0.75, ("#5e4a36", "#2e2218"), [
    P("body", [0, 21, 0], [-4, -2, -8, 8, 4, 16], style="mottle"),
    P("head", [0, 0, -8], [-4.5, -2, -6, 9, 4, 6], "", "mottle", "body", eyes=dict(c="#0c0a08", at=[3, 0], s=[1, 1])),
    P("tail1", [0, 0, 8], [-3, -2, 0, 6, 4, 8], "tail", "mottle", "body"),
    P("tail2", [0, 0, 8], [-1, -2.5, 0, 2, 5, 8], "tail", "mottle", "tail1"),
    P("legFR", [-4, 22, -5], [-2, 0, -1, 2, 2, 2], "legR", "mottle"),
    P("legFL", [4, 22, -5], [0, 0, -1, 2, 2, 2], "legL", "mottle"),
    P("legBR", [-4, 22, 5], [-2, 0, -1, 2, 2, 2], "legL", "mottle"),
    P("legBL", [4, 22, 5], [0, 0, -1, 2, 2, 2], "legR", "mottle"),
])


# ---- the night things ------------------------------------------------------------------------------------------------

# the River Child: small and slick, dark green over a pale belly, a wide head under a fringe of reed, long arms
creature("river_child", "River Child", ("#3e5a3a", "#c8c890"), 0.8, ("#3e5a3a", "#263a26"), [
    P("body", [0, 16, 0], [-3, -7, -2, 6, 7, 4], style="slick"),
    P("head", [0, -7, 0], [-3.5, -5, -3.5, 7, 5, 7], "neck", "slick", "body", eyes=dict(c="#e8d85a", at=[1, 2], s=[2, 1])),
    P("fringe", [0, -5, 0], [-4, -1, -4, 8, 2, 8], "", "#55662c", "head"),
    P("armR", [-4, -6, 0], [-1, 0, -1, 2, 10, 2], "armR", "slick", "body"),
    P("armL", [4, -6, 0], [-1, 0, -1, 2, 10, 2], "armL", "slick", "body"),
    P("legR", [-1.5, 16, 0], [-1.5, 0, -1.5, 3, 8, 3], "legR", "slick"),
    P("legL", [1.5, 16, 0], [-1.5, 0, -1.5, 3, 8, 3], "legL", "slick"),
])

# the Snow Widow: tall, a white robe to the ground, a white face, black hair to the waist
creature("snow_widow", "Snow Widow", ("#f2f4f8", "#0e0e14"), 1.0, ("#f2f4f8", "#c8d2e6"), [
    P("body", [0, 12, 0], [-4, -12, -2, 8, 12, 4], style="robe"),
    P("head", [0, -12, 0], [-4, -8, -4, 8, 8, 8], "head", "#eef0f6", "body", eyes=dict(c="#1c2c5a", at=[1, 4], s=[2, 1])),
    P("hair", [0, 0, 0], [-4.5, -8.5, 2.5, 9, 19, 2], "", "#0e0e14", "head"),
    P("crown", [0, -8, 0], [-4.5, -1, -4.5, 9, 2, 7], "", "#0e0e14", "head"),
    P("armR", [-6, -10, 0], [-2, 0, -2, 4, 13, 4], "armR", "robe", "body"),
    P("armL", [6, -10, 0], [-2, 0, -2, 4, 13, 4], "armL", "robe", "body"),
    P("skirt", [0, 12, 0], [-4.5, 0, -2.5, 9, 12, 5], "", "robe"),
])

# the Unburied: a skeleton three times a man's height, drawn at a man's size and scaled
creature("unburied", "The Unburied", ("#d8d2bc", "#3a3630"), 2.6, ("#d8d2bc", "#3a3630"), [
    P("body", [0, 12, 0], [-4, -12, -2, 8, 12, 4], style="ribs"),
    P("head", [0, -12, 0], [-4, -8, -4, 8, 8, 8], "head", "bone", "body", eyes=dict(c="#101010", at=[1, 3], s=[2, 2])),
    P("jaw", [0, 0, -1], [-3, 0, -3, 6, 2, 5], "", "bone", "head"),
    P("armR", [-5, -10, 0], [-1, 0, -1, 2, 14, 2], "armR", "bone", "body"),
    P("armL", [5, -10, 0], [-1, 0, -1, 2, 14, 2], "armL", "bone", "body"),
    P("legR", [-2, 12, 0], [-1, 0, -1, 2, 12, 2], "legR", "bone"),
    P("legL", [2, 12, 0], [-1, 0, -1, 2, 12, 2], "legL", "bone"),
])

# the Black Swell: a smooth dark dome and a smaller one on it, two pale round eyes and nothing else
creature("black_swell", "Black Swell", ("#0c0e14", "#e8eef2"), 2.2, ("#0c0e14", "#06070a"), [
    P("body", [0, 24, 0], [-12, -22, -12, 24, 22, 24], "wobble", "swell"),
    P("head", [0, 2, 0], [-9, -32, -9, 18, 10, 18], "", "swell", eyes=dict(c="#e8eef2", at=[2, 4], s=[3, 3])),
])


# ---- painting --------------------------------------------------------------------------------------------------------

_base_pixel = gc.pixel


def blotch(face, fx, fy, w, h, size, salt):
    """a steady yes or no for a patch of the face (the same for every pixel of one blotch)"""
    cx, cy = int(fx * w) // size, int(fy * h) // size
    return (zlib.crc32(repr((face, cx, cy, salt)).encode()) & 0xffff) / 65535.0


def pixel(style, face, fx, fy, w, h, pal, rng):
    base, dark = hexc(pal[0]), hexc(pal[1])
    f = 1.1 if face == "top" else 0.8 if face == "bottom" else 1.0 - 0.14 * fy
    if style == "koi":
        c = jitter(base, rng, 5)
        if face != "bottom":
            b = blotch(face, fx, fy, w, h, 3, 1)
            if b < 0.38:
                c = jitter(dark, rng, 8)
            elif b > 0.9:
                c = jitter((30, 28, 34), rng, 5)
        return shade(c, f) + (255,)
    if style == "fin":
        c = jitter(shade(base, 0.96), rng, 5)
        if int(fy * h + fx * w) % 2 == 0:
            c = shade(c, 0.88)
        return c + (235,)
    if style.startswith("#"):
        return shade(jitter(hexc(style), rng, 6), f) + (255,)
    if style == "slick":
        c = jitter(base, rng, 6)
        if face == "front" and 0.2 < fx < 0.8 and fy > 0.3 or face == "bottom":
            c = jitter((0xc8, 0xc8, 0x90), rng, 6)
        elif rng.random() < 0.1:
            c = shade(c, 1.3)  # the wet shine
        return shade(c, f) + (255,)
    if style == "robe":
        c = jitter(base, rng, 3)
        if int(fx * w) % 3 == 0:
            c = jitter(dark, rng, 3)  # the folds
        return shade(c, 1.0 - 0.08 * fy) + (255,)
    if style == "ribs":
        # ribs with the dark between them, and the spine down the middle
        gap = int(fy * h) % 3 == 2 and abs(fx - 0.5) > 0.12 and fy < 0.8
        return (jitter(dark, rng, 4) if gap else shade(jitter(base, rng, 6), f)) + (255,)
    if style == "swell":
        c = jitter(base, rng, 3)
        if rng.random() < 0.04:
            c = (0x1c, 0x24, 0x30)
        return shade(c, 1.15 if face == "top" else 1.0) + (255,)
    if style == "fur":
        c = jitter(base, rng, 9)
        if face == "bottom" or (fy > 0.75 and face in ("left", "right", "front", "back")):
            c = jitter(shade(base, 1.12), rng, 6)
        if rng.random() < 0.12:
            c = jitter(dark, rng, 6)
        return shade(c, f) + (255,)
    if style == "shag":
        c = jitter(base, rng, 7)
        if int(fx * w) % 2 == 0 and rng.random() < 0.6:
            c = jitter(dark, rng, 5)
        return shade(c, f) + (255,)
    if style == "macaque":
        # a bare pink face set in the fur
        if face == "front" and 0.18 < fx < 0.82 and 0.25 < fy < 0.95:
            return jitter((0xd9, 0x70, 0x5a), rng, 6) + (255,)
        return shade(jitter(base, rng, 9), f) + (255,)
    if style == "crown":
        if face == "top":
            return jitter((0xc8, 0x28, 0x1e), rng, 6) + (255,)
        if face in ("left", "right") and fx > 0.45:
            return jitter((0xf4, 0xf2, 0xec), rng, 4) + (255,)
        return jitter((0x1c, 0x1c, 0x20), rng, 4) + (255,)
    if style == "wing":
        # white, with the black flight feathers along the trailing edge
        trailing = fy > 0.7 if face in ("top", "bottom") else fx > 0.7 if face in ("left", "right") else False
        return (jitter((0x1c, 0x1c, 0x20), rng, 4) if trailing else shade(jitter(base, rng, 4), f)) + (255,)
    if style == "sika":
        c = jitter(base, rng, 8)
        if face in ("top", "left", "right") and (int(fx * w) + 2 * int(fy * h)) % 5 == 0 and fy < 0.7:
            c = (0xf2, 0xea, 0xd8)
        if face == "bottom":
            c = jitter((0xe6, 0xdc, 0xc6), rng, 5)
        if face == "back":
            c = jitter((0xf2, 0xea, 0xd8), rng, 4)
        return shade(c, f) + (255,)
    if style == "mask":
        c = jitter(base, rng, 8)
        if face == "front" and 0.3 < fy < 0.75 and (fx < 0.42 or fx > 0.58):
            c = jitter((0x2a, 0x24, 0x20), rng, 4)
        return shade(c, f) + (255,)
    if style == "bands":
        along = fy if face in ("top", "bottom") else fx if face in ("left", "right") else 1.0
        c = jitter(dark if int(along * 7) % 2 else base, rng, 6)
        return shade(c, f) + (255,)
    if style == "clearwing":
        vein = int(fx * w) % 3 == 0 or int(fy * h) % 3 == 0
        return (0xd8, 0xd0, 0xb8, 255) if vein else (0xf0, 0xec, 0xdc, 0)
    if style == "mottle":
        c = jitter(base, rng, 7)
        b = blotch(face, fx, fy, w, h, 2, 3)
        if b < 0.3:
            c = jitter(dark, rng, 5)
        elif b > 0.92:
            c = jitter((0x8a, 0x70, 0x4c), rng, 5)
        if face == "bottom":
            c = jitter((0x7a, 0x66, 0x4e), rng, 5)
        return shade(c, f) + (255,)
    return _base_pixel(style, face, fx, fy, w, h, pal, rng)


gc.pixel = pixel


# what each leaves when it dies: (item, least, most) each
DROPS = {
    "koi": [], "snow_monkey": [], "hornet": [], "giant_salamander": [("minecraft:slime_ball", 0, 1)],
    "crane": [("minecraft:feather", 1, 3)], "shrine_deer": [("minecraft:leather", 0, 2), ("minecraft:mutton", 1, 2)],
    "raccoon_dog": [("minecraft:rabbit_hide", 0, 2)], "serow": [("minecraft:leather", 1, 2), ("minecraft:mutton", 1, 3)],
    "river_child": [("minecraft:slime_ball", 0, 2), ("minecraft:kelp", 0, 2)],
    "snow_widow": [("minecraft:packed_ice", 2, 4), ("minecraft:white_wool", 1, 2), ("minecraft:diamond", 1, 2)],
    "unburied": [("minecraft:bone", 8, 16), ("minecraft:bone_block", 2, 4)], "black_swell": [],
}


def drops():
    data = os.path.join(gc.ROOT, "..", "..", "data", "jaymoskingdoms", "loot_table", "entities")
    for name, items in DROPS.items():
        pools = [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": item, "modifier": [
            {"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": least, "max": most}},
            {"type": "minecraft:enchanted_count_increase", "count": {"type": "minecraft:uniform", "min": 0.0, "max": 1.0},
             "enchantment": "minecraft:looting"}]}]} for item, least, most in items]
        with open(os.path.join(data, name + ".json"), "w") as f:
            json.dump({"type": "minecraft:entity", "pools": pools}, f, indent=2)
            f.write("\n")


def main():
    drops()
    import random
    random.seed(7)
    for name, spec in CREATURES.items():
        gc.build(name, spec)
    lang = {}
    for name, (a, b) in EGGS.items():
        gc.egg(name, a, b)
        with open(os.path.join(ASSETS, "items", name + "_spawn_egg.json"), "w") as f:
            json.dump({"model": {"type": "minecraft:model", "model": "jaymoskingdoms:item/" + name + "_spawn_egg"}}, f, indent=2)
        with open(os.path.join(ASSETS, "models", "item", name + "_spawn_egg.json"), "w") as f:
            json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "jaymoskingdoms:item/" + name + "_spawn_egg"}}, f, indent=2)
        lang["entity.jaymoskingdoms." + name] = NAMES[name]
        lang["item.jaymoskingdoms." + name + "_spawn_egg"] = NAMES[name] + " Spawn Egg"
    with open(os.path.join(ASSETS, "lang", "parts", "blossom_animals.json"), "w") as f:
        json.dump(lang, f, indent=2)
        f.write("\n")
    print("blossom creatures:", len(CREATURES))


if __name__ == "__main__":
    main()
