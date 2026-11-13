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
    return _base_pixel(style, face, fx, fy, w, h, pal, rng)


gc.pixel = pixel


def main():
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
