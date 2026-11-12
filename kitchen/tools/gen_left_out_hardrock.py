"""Art and data for the Hardrock parts PR #1253 left out, for Jaymo's Kitchen: the three furs (wolf, bear, wild), the bottles of
brine and vinegar, the two pickling crocks, and the flame and snowflake pips for the lining tooltip (a little bitmap font).
Painted from scratch with seeded noise on our own ramps (nothing taken from vanilla or any other mod), plus the model,
blockstate, item, loot table and recipe JSON.
Run from the kitchen root:  python -I tools/gen_left_out_hardrock.py
"""
import json
import random
from pathlib import Path

from PIL import Image

MAIN = Path(__file__).resolve().parent.parent / "src" / "main" / "resources"
ASSETS = MAIN / "assets" / "jaymoskitchen"
DATA = MAIN / "data" / "jaymoskitchen"
TEX = ASSETS / "textures"

WOLF = [(96, 92, 90), (122, 118, 114), (150, 146, 140), (178, 172, 164)]
BEAR = [(54, 38, 28), (72, 52, 38), (92, 68, 48), (112, 84, 60)]
WILD = [(150, 100, 44), (176, 124, 58), (200, 150, 76), (222, 176, 104)]
GLASS = [(120, 170, 180), (150, 200, 210), (186, 226, 232)]
BRINE = [(130, 190, 214), (160, 212, 232), (196, 232, 246)]
VINEGAR = [(150, 96, 40), (184, 124, 56), (214, 156, 80)]
CORK = [(120, 86, 52), (150, 110, 70)]
CLAY = [(150, 82, 52), (172, 98, 62), (192, 116, 74), (208, 134, 90)]
GLAZE = [(96, 56, 40), (120, 70, 48)]


def save(img, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)


def write(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + "\n")


def fur(rng, ramp):
    """A pelt: an oval of short hair strokes with a darker edge and the four legs' stubs."""
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            dx, dy = (x - 7.5) / 6.0, (y - 7.5) / 5.5
            d = dx * dx + dy * dy
            stub = (x in (2, 3, 12, 13)) and (y in (1, 2, 13, 14)) and abs(dx) > 0.5
            if d > 1.0 and not stub:
                continue
            v = min(len(ramp) - 1, max(0, int(rng.random() * len(ramp) + (0.5 if (x + y * 2) % 3 == 0 else -0.3))))
            if d > 0.78 or stub:
                v = max(0, v - 1)
            img.putpixel((x, y), ramp[v] + (255,))
    return img


def bottle(liquid):
    img = Image.new("RGBA", (16, 16))
    for y in range(5, 15):
        w = 3 if y < 8 else 5
        for x in range(8 - w, 8 + w):
            edge = x in (8 - w, 8 + w - 1) or y in (5, 14)
            c = GLASS[0 if edge else 1] if y < 8 else (GLASS[0] if edge else liquid[1 if (x + y) % 3 else 0])
            img.putpixel((x, y), c + (255,))
    for y in range(2, 5):
        for x in range(6, 10):
            img.putpixel((x, y), CORK[(x + y) % 2] + (255,))
    for x in range(6, 10):
        img.putpixel((x, 5), GLASS[0] + (255,))
    for y in range(9, 13):
        img.putpixel((5, y), liquid[2] + (255,))
    return img


def crock_side(rng):
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            v = min(len(CLAY) - 1, max(0, int(rng.random() * 2 + (1 if 3 < y < 12 else 0) + (1 if x in (4, 5) else 0))))
            c = CLAY[v]
            if y in (0, 1, 14, 15):
                c = GLAZE[(x + y) % 2]
            elif y in (7, 8) and (x // 2) % 2:
                c = GLAZE[1]
            img.putpixel((x, y), c + (255,))
    return img


def crock_top(liquid):
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            d = (dx * dx + dy * dy) ** 0.5
            if d > 7.2:
                img.putpixel((x, y), GLAZE[0] + (255,))
            elif d > 5.8:
                img.putpixel((x, y), CLAY[3 - ((x + y) % 2)] + (255,))
            else:
                img.putpixel((x, y), liquid[(x * 3 + y) % 3] + (255,))
    return img


def pips():
    """Two 8x8 glyphs side by side: a flame (U+E000) and a snowflake (U+E001)."""
    img = Image.new("RGBA", (16, 8))
    flame = [
        "...##...",
        "...##...",
        "..###...",
        "..####..",
        ".#####.#",
        ".######.",
        ".######.",
        "..####..",
    ]
    colours = {"#": (255, 170, 40, 255)}
    for y, row in enumerate(flame):
        for x, ch in enumerate(row):
            if ch == "#":
                core = 2 <= x <= 5 and y >= 4
                img.putpixel((x, y), (255, 236, 130, 255) if core and y >= 5 else colours[ch])
    snow = [
        "...##...",
        ".#.##.#.",
        "..####..",
        "########",
        "########",
        "..####..",
        ".#.##.#.",
        "...##...",
    ]
    for y, row in enumerate(snow):
        for x, ch in enumerate(row):
            if ch == "#":
                img.putpixel((8 + x, y), (150, 214, 255, 255) if (x + y) % 2 else (214, 240, 255, 255))
    return img


def main():
    rng = random.Random(20261112)
    for name, ramp in (("wolf_fur", WOLF), ("bear_fur", BEAR), ("wild_fur", WILD)):
        save(fur(rng, ramp), TEX / "item" / f"{name}.png")
    save(bottle(BRINE), TEX / "item" / "brine.png")
    save(bottle(VINEGAR), TEX / "item" / "vinegar.png")
    save(crock_side(rng), TEX / "block" / "crock_side.png")
    save(crock_top(BRINE), TEX / "block" / "brine_crock_top.png")
    save(crock_top(VINEGAR), TEX / "block" / "vinegar_crock_top.png")
    save(pips(), TEX / "font" / "pips.png")
    write(ASSETS / "font" / "pips.json", {"providers": [
        {"type": "bitmap", "file": "jaymoskitchen:font/pips.png", "ascent": 7, "height": 8, "chars": [""]}]})

    for item in ("wolf_fur", "bear_fur", "wild_fur", "brine", "vinegar"):
        write(ASSETS / "models" / "item" / f"{item}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"jaymoskitchen:item/{item}"}})
        write(ASSETS / "items" / f"{item}.json", {"model": {"type": "minecraft:model", "model": f"jaymoskitchen:item/{item}"}})

    for crock in ("brine_crock", "vinegar_crock"):
        write(ASSETS / "models" / "block" / f"{crock}.json", {
            "textures": {"side": "jaymoskitchen:block/crock_side", "top": f"jaymoskitchen:block/{crock}_top", "particle": "jaymoskitchen:block/crock_side"},
            "elements": [
                {"from": [3, 0, 3], "to": [13, 11, 13], "faces": {
                    "down": {"uv": [3, 3, 13, 13], "texture": "#side"}, "north": {"uv": [3, 0, 13, 11], "texture": "#side"},
                    "south": {"uv": [3, 0, 13, 11], "texture": "#side"}, "east": {"uv": [3, 0, 13, 11], "texture": "#side"},
                    "west": {"uv": [3, 0, 13, 11], "texture": "#side"}}},
                {"from": [2, 11, 2], "to": [14, 13, 14], "faces": {
                    "up": {"uv": [2, 2, 14, 14], "texture": "#top"}, "down": {"uv": [2, 2, 14, 14], "texture": "#side"},
                    "north": {"uv": [2, 11, 14, 13], "texture": "#side"}, "south": {"uv": [2, 11, 14, 13], "texture": "#side"},
                    "east": {"uv": [2, 11, 14, 13], "texture": "#side"}, "west": {"uv": [2, 11, 14, 13], "texture": "#side"}}},
            ]})
        write(ASSETS / "blockstates" / f"{crock}.json", {"variants": {"": {"model": f"jaymoskitchen:block/{crock}"}}})
        write(ASSETS / "items" / f"{crock}.json", {"model": {"type": "minecraft:model", "model": f"jaymoskitchen:block/{crock}"}})
        write(DATA / "loot_table" / "blocks" / f"{crock}.json", {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "condition": {"type": "minecraft:survives_explosion"},
                       "entries": [{"type": "minecraft:item", "name": f"jaymoskitchen:{crock}"}]}],
            "random_sequence": f"jaymoskitchen:blocks/{crock}"})
        center = "jaymoskitchen:salt" if crock == "brine_crock" else "jaymoskitchen:vinegar"
        write(DATA / "recipe" / f"{crock}.json", {
            "type": "minecraft:crafting_shaped", "category": "misc",
            "pattern": ["B B", "BSB", "BBB"], "key": {"B": "minecraft:brick", "S": center},
            "result": {"id": f"jaymoskitchen:{crock}"}})

    write(DATA / "recipe" / "brine.json", {
        "type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": ["minecraft:water_bucket", "jaymoskitchen:salt", "jaymoskitchen:salt", "minecraft:glass_bottle", "minecraft:glass_bottle",
                        "minecraft:glass_bottle"],
        "result": {"id": "jaymoskitchen:brine", "count": 3}})
    write(DATA / "recipe" / "vinegar.json", {
        "type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": ["minecraft:honey_bottle", "minecraft:apple", "minecraft:apple", "minecraft:glass_bottle"],
        "result": {"id": "jaymoskitchen:vinegar", "count": 2}})
    print("done")


if __name__ == "__main__":
    main()
