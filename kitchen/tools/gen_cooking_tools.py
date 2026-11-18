"""Art and data for the hands-on cooking tools: a wooden spoon, kitchen tongs, a smoker's fan and a baker's peel.

Original vanilla-style 16x16 sprites, drawn with a few lines each. Run from the repo root:
    python3 -I kitchen/tools/gen_cooking_tools.py
Writes the textures, the item models and the item definitions under kitchen/src/main/resources/assets/jaymoskitchen.
"""
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "assets" / "jaymoskitchen"

OUT = (26, 18, 12, 255)
WOOD = (176, 128, 74, 255)
WOOD_LIGHT = (206, 160, 98, 255)
WOOD_DARK = (122, 84, 46, 255)
IRON = (176, 180, 188, 255)
IRON_LIGHT = (214, 218, 226, 255)
IRON_DARK = (110, 114, 124, 255)
STRAW = (222, 196, 104, 255)
STRAW_DARK = (176, 148, 70, 255)
CORD = (150, 60, 50, 255)


def canvas():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def px(img, x, y, c):
    if 0 <= x < 16 and 0 <= y < 16:
        img.putpixel((x, y), c)


def line(img, x0, y0, x1, y1, c):
    n = max(abs(x1 - x0), abs(y1 - y0))
    for i in range(n + 1):
        t = i / n if n else 0
        px(img, round(x0 + (x1 - x0) * t), round(y0 + (y1 - y0) * t), c)


def outline(img):
    """A dark edge round every opaque pixel, like vanilla tools have."""
    src = img.copy()
    for y in range(16):
        for x in range(16):
            if src.getpixel((x, y))[3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < 16 and 0 <= ny < 16 and src.getpixel((nx, ny))[3] > 0:
                        px(img, x, y, OUT)
                        break


def spoon():
    img = canvas()
    line(img, 3, 13, 10, 6, WOOD)
    line(img, 2, 13, 9, 6, WOOD_DARK)
    for x, y in ((10, 5), (11, 5), (11, 4), (12, 4), (12, 3), (11, 3), (10, 4), (13, 4), (12, 2), (13, 3), (13, 2)):
        px(img, x, y, WOOD_LIGHT)
    for x, y in ((11, 4), (12, 3)):
        px(img, x, y, WOOD)
    outline(img)
    return img


def tongs():
    img = canvas()
    line(img, 2, 14, 10, 5, IRON)
    line(img, 4, 14, 11, 6, IRON_DARK)
    line(img, 10, 5, 13, 2, IRON_LIGHT)
    line(img, 11, 6, 14, 3, IRON)
    px(img, 3, 13, CORD)
    px(img, 4, 12, CORD)
    px(img, 5, 13, CORD)
    outline(img)
    return img


def fan():
    img = canvas()
    # a woven palm-leaf fan on a handle
    line(img, 2, 14, 7, 9, WOOD_DARK)
    line(img, 3, 14, 8, 9, WOOD)
    for r in range(0, 6):
        for dx in range(-r, r + 1):
            x, y = 9 + dx, 8 - r
            px(img, x, y, STRAW if (dx + r) % 2 == 0 else STRAW_DARK)
    for r in range(2, 6):
        px(img, 9, 8 - r, WOOD_LIGHT)
    outline(img)
    return img


def peel():
    img = canvas()
    # a long handle and a flat wooden blade
    line(img, 1, 14, 8, 7, WOOD_DARK)
    line(img, 2, 14, 9, 7, WOOD)
    for y in range(2, 8):
        for x in range(8, 14):
            if abs((x - 11) * 0.8) + abs(y - 5) * 0.55 < 3.4:
                px(img, x, y, WOOD_LIGHT if (x + y) % 3 else WOOD)
    outline(img)
    return img


TOOLS = {
    "wooden_spoon": spoon,
    "kitchen_tongs": tongs,
    "smokers_fan": fan,
    "bakers_peel": peel,
}


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def main():
    for name, draw in TOOLS.items():
        tex = ROOT / "textures" / "item" / f"{name}.png"
        tex.parent.mkdir(parents=True, exist_ok=True)
        draw().save(tex)
        write_json(ROOT / "models" / "item" / f"{name}.json", {
            "parent": "minecraft:item/handheld",
            "textures": {"layer0": f"jaymoskitchen:item/{name}"},
        })
        write_json(ROOT / "items" / f"{name}.json", {
            "model": {"type": "minecraft:model", "model": f"jaymoskitchen:item/{name}"},
        })


if __name__ == "__main__":
    main()
