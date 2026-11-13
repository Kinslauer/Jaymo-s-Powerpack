"""Draws the textures of the more-spells patch: sixteen spell scrolls, a lock of hair, a familiar's ash and the five familiar skins.

Run from the repository root: python3 tools/gen_fifth_p7.py [path to an unpacked vanilla assets folder]
Each scroll is the pack's diagonal paper with its own seal colour and four-by-four glyph. The familiar skins are
colour ramps laid over the matching vanilla animal's layout (so the stand-in models fit them): they are only drawn when
the vanilla assets folder is given, and are otherwise left as they are.
"""
import json
import os
import sys

from PIL import Image

ASSETS = "kingdoms/src/main/resources/assets/jaymoskingdoms"
PAPER = [
    "................",
    "...........##...",
    "..........####..",
    ".........######.",
    "........#######.",
    ".......#######..",
    "......#######...",
    ".....#######....",
    "....#######.....",
    "...########.....",
    "..#######.##....",
    ".#######...#....",
    ".######.........",
    "..####..........",
    "...##...........",
    "................",
]

SCROLLS = {
    "charm": ((235, 110, 160), ["0110", "1111", "0110", "0010"]),
    "command": ((235, 190, 70), ["0100", "0100", "0100", "0100"]),
    "suggestion": ((200, 180, 255), ["0110", "1000", "0110", "0001"]),
    "hold": ((190, 170, 90), ["1111", "1001", "1001", "1111"]),
    "zone_of_truth": ((250, 235, 170), ["0110", "1001", "1001", "0110"]),
    "geas": ((130, 60, 170), ["1001", "0110", "0110", "1111"]),
    "modify_memory": ((150, 120, 230), ["1101", "0110", "1011", "0100"]),
    "locate_object": ((70, 190, 180), ["0010", "0111", "1110", "0100"]),
    "locate_creature": ((220, 100, 220), ["0110", "1111", "0110", "1001"]),
    "augury": ((225, 215, 190), ["1010", "0100", "0010", "1101"]),
    "scrying": ((90, 160, 240), ["0110", "1111", "1111", "0110"]),
    "plant_growth": ((100, 200, 90), ["0100", "0110", "1111", "0110"]),
    "control_weather": ((140, 190, 235), ["0110", "1111", "0000", "1010"]),
    "moonbeam": ((200, 215, 255), ["0011", "0110", "0110", "0011"]),
    "heroes_feast": ((240, 160, 60), ["1111", "0110", "0110", "1111"]),
    "find_familiar": ((160, 130, 190), ["0101", "1111", "1111", "0110"]),
}

# name: (dark, mid, light) ramps laid over a vanilla layout (relative path under textures/entity)
SKINS = {
    "owl": ("parrot/parrot_grey.png", (60, 40, 25), (150, 110, 70), (238, 222, 190)),
    "raven": ("parrot/parrot_grey.png", (12, 12, 22), (50, 52, 72), (110, 115, 145)),
    "cat": ("cat/cat_tabby.png", (45, 45, 62), (120, 125, 150), (215, 220, 235)),
    "rat": ("rabbit/rabbit_brown.png", (60, 55, 58), (130, 118, 118), (205, 190, 186)),
    "frog": ("frog/frog_temperate.png", (30, 50, 90), (80, 150, 170), (190, 240, 225)),
}


def scroll(name, seal, glyph):
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    mask = set()
    for y, row in enumerate(PAPER):
        for x, c in enumerate(row):
            if c == "#":
                mask.add((x, y))
                shade = 232 - (x + y) % 4 * 4 - (6 if x - y < -3 else 0)
                im.putpixel((x, y), (shade, shade - 34, shade - 78, 255))
    for (x, y) in [(2, 10), (3, 11), (4, 12), (11, 1), (12, 2)]:
        if (x, y) in mask:
            im.putpixel((x, y), (190, 150, 110, 255))
    dark = tuple(max(0, c - 60) for c in seal)
    for gy, row in enumerate(glyph):
        for gx, bit in enumerate(row):
            p = (7 + gx, 5 + gy)
            if bit == "1" and p in mask:
                im.putpixel(p, seal + (255,))
            elif p in mask and bit == "0" and gx in (0, 3) and gy in (0, 3):
                im.putpixel(p, dark + (255,))
    im.save(f"{ASSETS}/textures/item/scroll_of_{name}.png")


def hair():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    strands = [(7, 3), (8, 3), (6, 4), (7, 4), (8, 4), (9, 4), (6, 5), (7, 5), (8, 5), (9, 5), (6, 6), (7, 6), (8, 6), (9, 6), (7, 7), (8, 7),
               (5, 8), (7, 8), (8, 8), (10, 8), (5, 9), (6, 9), (9, 9), (10, 9), (4, 10), (10, 10), (4, 11), (11, 11)]
    tones = [(92, 58, 34), (120, 78, 46), (150, 104, 62)]
    for i, p in enumerate(strands):
        im.putpixel(p, tones[(p[0] + p[1]) % 3] + (255,))
    # the thread that ties it
    for p in [(6, 7), (7, 7), (8, 7), (9, 7)]:
        im.putpixel(p, (200, 60, 70, 255))
    im.putpixel((9, 8), (200, 60, 70, 255))
    im.save(f"{ASSETS}/textures/item/lock_of_hair.png")


def ash():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    greys = [(96, 96, 104), (128, 128, 136), (70, 70, 80)]
    pile = [(7, 9), (8, 9), (6, 10), (7, 10), (8, 10), (9, 10), (5, 11), (6, 11), (7, 11), (8, 11), (9, 11), (10, 11), (4, 12), (5, 12), (6, 12),
            (7, 12), (8, 12), (9, 12), (10, 12), (11, 12), (7, 8)]
    for i, p in enumerate(pile):
        im.putpixel(p, greys[(i * 5) % 3] + (255,))
    for p in [(7, 6), (8, 4), (6, 7), (9, 5)]:
        im.putpixel(p, (200, 180, 255, 255))
    for p in [(7, 5), (8, 7)]:
        im.putpixel(p, (150, 130, 220, 255))
    im.save(f"{ASSETS}/textures/item/familiar_ash.png")


def ramp(lum, dark, mid, light):
    if lum < 0.5:
        t = lum / 0.5
        a, b = dark, mid
    else:
        t = (lum - 0.5) / 0.5
        a, b = mid, light
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def skin(name, src, dark, mid, light, vanilla):
    im = Image.open(os.path.join(vanilla, "textures/entity", src)).convert("RGBA")
    out = Image.new("RGBA", im.size)
    for y in range(im.size[1]):
        for x in range(im.size[0]):
            r, g, b, a = im.getpixel((x, y))
            if a == 0:
                continue
            lum = (0.3 * r + 0.59 * g + 0.11 * b) / 255.0
            out.putpixel((x, y), ramp(lum, dark, mid, light) + (a,))
    os.makedirs(f"{ASSETS}/textures/entity/familiar", exist_ok=True)
    out.save(f"{ASSETS}/textures/entity/familiar/{name}.png")


def item_files(name):
    with open(f"{ASSETS}/items/{name}.json", "w") as f:
        json.dump({"model": {"type": "minecraft:model", "model": f"jaymoskingdoms:item/{name}"}}, f, indent=2)
        f.write("\n")
    with open(f"{ASSETS}/models/item/{name}.json", "w") as f:
        json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": f"jaymoskingdoms:item/{name}"}}, f, indent=2)
        f.write("\n")


if __name__ == "__main__":
    for n, (seal, glyph) in SCROLLS.items():
        scroll(n, seal, glyph)
        item_files(f"scroll_of_{n}")
    hair()
    item_files("lock_of_hair")
    ash()
    item_files("familiar_ash")
    if len(sys.argv) > 1:
        for n, (src, d, m, l) in SKINS.items():
            skin(n, src, d, m, l, sys.argv[1])
    print("drew", len(SCROLLS) + 2, "items")
