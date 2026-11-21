#!/usr/bin/env python3
"""Draws the tackle bench's art and writes its model and item JSON: the barbed hook, the braided and weighted lines, the
torn note (item textures) and the bench (top, side, bottom). Run from the repo root: py tools/gen_tackle.py"""
import json
import os
import random

from PIL import Image

ASSETS = "src/main/resources/assets/jaymospowers"

IRON = [(0x5C, 0x5E, 0x66), (0x8A, 0x8D, 0x96), (0xB8, 0xBB, 0xC4)]
PLANK = [(0x6B, 0x4F, 0x2E), (0x8B, 0x69, 0x3D), (0xA6, 0x82, 0x50)]


def put(img, x, y, c):
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), c + (255,))


def hook():
    img = Image.new("RGBA", (16, 16))
    # the shank, the bend and the point, with a barb on the point's inside
    shank = [(9, 2), (9, 3), (9, 4), (9, 5), (9, 6), (9, 7), (9, 8), (9, 9)]
    bend = [(8, 10), (7, 11), (6, 11), (5, 11), (4, 10), (4, 9), (4, 8)]
    for x, y in shank + bend:
        put(img, x, y, IRON[1])
        put(img, x + 1, y, IRON[0])
    put(img, 8, 2, IRON[2])
    put(img, 8, 3, IRON[2])
    put(img, 4, 7, IRON[2])
    put(img, 3, 6, IRON[1])
    put(img, 5, 8, IRON[2])
    put(img, 9, 1, IRON[0])
    put(img, 8, 1, IRON[1])
    put(img, 10, 1, IRON[0])
    return img


def coil(rope, weights):
    img = Image.new("RGBA", (16, 16))
    random.seed(7)
    for ring in range(3):
        r = 3 + ring * 2
        for a in range(0, 360, 6):
            import math
            x = int(round(8 + r * math.cos(math.radians(a))))
            y = int(round(8 + r * math.sin(math.radians(a)) * 0.8))
            shade = rope[(a // 30 + ring) % len(rope)]
            put(img, x, y, shade)
    if weights:
        for x, y in ((3, 8), (13, 9), (8, 12), (7, 3)):
            for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)):
                put(img, x + dx, y + dy, IRON[(dx + dy) % 3])
    return img


def note():
    img = Image.new("RGBA", (16, 16))
    random.seed(11)
    paper = [(0xD8, 0xC8, 0x9C), (0xC4, 0xB2, 0x84), (0xA8, 0x98, 0x70)]
    for y in range(2, 14):
        # a torn edge on the right and the bottom
        right = 13 - (1 if (y * 7) % 5 == 0 else 0) - (1 if y > 10 else 0)
        for x in range(3, right):
            wet = (x + y) % 5 == 0 and y > 6
            put(img, x, y, (0x8F, 0x9E, 0x9A) if wet else paper[(x * 3 + y) % 2])
    for y in (4, 6, 8):
        for x in range(5, 11 - (y // 4)):
            if random.random() < 0.8:
                put(img, x, y, (0x4A, 0x3B, 0x2A))
    return img


def plank_face(kind):
    img = Image.new("RGBA", (16, 16))
    random.seed(3)
    for y in range(16):
        for x in range(16):
            c = PLANK[(1 if (y % 4) else 0) + (1 if random.random() < 0.15 else 0)]
            put(img, x, y, c)
    if kind == "top":
        # a bench top: a rod laid across it, and a row of hooks
        for x in range(1, 15):
            put(img, x, 5, (0x3B, 0x2A, 0x1A))
            put(img, x, 4, (0xB5, 0x9A, 0x6A))
        for x in (3, 6, 9, 12):
            put(img, x, 10, IRON[2])
            put(img, x, 11, IRON[1])
        put(img, 2, 8, (0xC8, 0x4B, 0x3C))
        put(img, 13, 8, (0x3C, 0x7F, 0xC8))
    elif kind == "side":
        for x in range(2, 14, 4):
            put(img, x, 3, IRON[2])
            put(img, x, 4, IRON[1])
            put(img, x, 5, IRON[0])
    return img


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf8", newline="\n") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def item_json(name, model):
    write(f"{ASSETS}/items/{name}.json", {"model": {"type": "minecraft:model", "model": f"jaymospowers:{model}"}})


def main():
    textures = {"barbed_hook": hook(), "braided_line": coil([(0x9C, 0x8A, 0x5E), (0xB8, 0xA4, 0x72), (0x7A, 0x6A, 0x44)], False),
                "weighted_line": coil([(0x70, 0x78, 0x80), (0x8E, 0x98, 0xA2), (0x56, 0x5E, 0x66)], True), "torn_note": note()}
    for name, img in textures.items():
        os.makedirs(f"{ASSETS}/textures/item", exist_ok=True)
        img.save(f"{ASSETS}/textures/item/{name}.png")
        write(f"{ASSETS}/models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"jaymospowers:item/{name}"}})
        item_json(name, f"item/{name}")
    for kind in ("top", "side", "bottom"):
        os.makedirs(f"{ASSETS}/textures/block", exist_ok=True)
        plank_face(kind).save(f"{ASSETS}/textures/block/tackle_bench_{kind}.png")
    write(f"{ASSETS}/models/block/tackle_bench.json", {"parent": "minecraft:block/cube_bottom_top", "textures": {
        "top": "jaymospowers:block/tackle_bench_top", "side": "jaymospowers:block/tackle_bench_side",
        "bottom": "jaymospowers:block/tackle_bench_bottom"}})
    write(f"{ASSETS}/blockstates/tackle_bench.json", {"variants": {"": {"model": "jaymospowers:block/tackle_bench"}}})
    item_json("tackle_bench", "block/tackle_bench")


if __name__ == "__main__":
    main()
