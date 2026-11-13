"""Draws the textures of the magic rules' new items: twenty spell scrolls, the rag gag and diamond dust.

Run from the repository root: python3 tools/gen_fifth_p6.py
Each scroll is the pack's diagonal paper with its own seal colour and its own four-by-four glyph, so no two are alike.
"""
import json
import os

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

# name: (seal colour, glyph rows)
SCROLLS = {
    "counterspell": ((120, 170, 230), ["1001", "0110", "0110", "1001"]),
    "dispel": ((230, 230, 245), ["0110", "1111", "1111", "0110"]),
    "ward_line": ((90, 190, 120), ["0000", "1111", "1111", "0000"]),
    "find_magic": ((200, 160, 255), ["0110", "1001", "1001", "0110"]),
    "identify": ((240, 200, 90), ["1111", "1001", "1011", "1111"]),
    "speak_animals": ((200, 130, 80), ["1010", "1111", "0110", "0101"]),
    "hut": ((110, 150, 255), ["0110", "1111", "1001", "1111"]),
    "create_food": ((235, 170, 70), ["0110", "1111", "1111", "0110"]),
    "purify_food": ((120, 230, 200), ["0100", "1110", "0111", "0010"]),
    "feather_fall": ((210, 225, 235), ["0011", "0110", "1100", "1000"]),
    "spider_climb": ((90, 60, 110), ["1010", "0100", "0100", "1010"]),
    "water_walk": ((70, 150, 235), ["0000", "1101", "0110", "1111"]),
    "long_jump": ((235, 130, 90), ["0001", "0010", "0100", "1111"]),
    "misty_step": ((190, 205, 225), ["1001", "0000", "0110", "1001"]),
    "web": ((225, 225, 225), ["1010", "0100", "1010", "0101"]),
    "grease": ((120, 170, 60), ["0100", "1110", "1110", "0100"]),
    "fog": ((170, 180, 190), ["1111", "0000", "1111", "0000"]),
    "darkness": ((50, 40, 70), ["1111", "1111", "1111", "1111"]),
    "silence": ((150, 120, 200), ["0110", "0110", "0000", "0110"]),
    "spike_growth": ((70, 140, 60), ["1001", "1101", "1111", "0110"]),
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
    # rolled ends
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


def rag():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    cloth = [(222, 214, 196), (206, 196, 176), (188, 178, 158)]
    for x in range(2, 14):
        for y in range(6, 10):
            im.putpixel((x, y), cloth[(x + y) % 3])
    for x in range(2, 14):
        im.putpixel((x, 6), cloth[2])
        im.putpixel((x, 9), cloth[2])
    for (x, y) in [(1, 7), (1, 8), (14, 7), (14, 8), (0, 8), (15, 7)]:
        im.putpixel((x, y), cloth[1])
    for (x, y) in [(7, 5), (8, 5), (7, 10), (8, 10), (6, 4), (9, 11)]:
        im.putpixel((x, y), (150, 120, 90, 255))
    for (x, y) in [(7, 7), (8, 7), (7, 8), (8, 8)]:
        im.putpixel((x, y), (130, 100, 70, 255))
    im.save(f"{ASSETS}/textures/item/rag_gag.png")


def dust():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    base = [(150, 235, 235), (120, 215, 225), (90, 190, 210)]
    pile = [(7, 9), (8, 9), (6, 10), (7, 10), (8, 10), (9, 10), (5, 11), (6, 11), (7, 11), (8, 11), (9, 11), (10, 11), (4, 12), (5, 12), (6, 12),
            (7, 12), (8, 12), (9, 12), (10, 12), (11, 12), (7, 8)]
    for i, p in enumerate(pile):
        im.putpixel(p, base[(i * 7) % 3] + (255,))
    for p in [(7, 8), (8, 9), (6, 10)]:
        im.putpixel(p, (235, 255, 255, 255))
    for p in [(3, 6), (12, 7), (9, 5), (5, 4)]:
        im.putpixel(p, (200, 250, 255, 255))
    im.save(f"{ASSETS}/textures/item/diamond_dust.png")


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
    rag()
    item_files("rag_gag")
    dust()
    item_files("diamond_dust")
    print("drew", len(SCROLLS) + 2, "items")
