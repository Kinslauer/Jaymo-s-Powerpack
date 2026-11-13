"""Art and data for the Hardrock parts PR #1253 left out, for Jaymo's Kitchen: the three furs (wolf, bear, wild) and the flame and
snowflake pips for the lining tooltip (a little bitmap font). (Brine, vinegar and their crocks were taken out: Andy, 2026-11-12.)
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
    save(pips(), TEX / "font" / "pips.png")
    write(ASSETS / "font" / "pips.json", {"providers": [
        {"type": "bitmap", "file": "jaymoskitchen:font/pips.png", "ascent": 7, "height": 8, "chars": [""]}]})

    for item in ("wolf_fur", "bear_fur", "wild_fur"):
        write(ASSETS / "models" / "item" / f"{item}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"jaymoskitchen:item/{item}"}})
        write(ASSETS / "items" / f"{item}.json", {"model": {"type": "minecraft:model", "model": f"jaymoskitchen:item/{item}"}})

    print("done")


if __name__ == "__main__":
    main()
