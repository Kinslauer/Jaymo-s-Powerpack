"""Hrunting's Frost Lance (jaymospowers:arsenal_frost_lance): a long spear of ice, drawn along the diagonal like the
relics (u from the butt, bottom left, to the tip, top right; v across it). Only the thrown lance shows it.
Run from the project root:  python -I tools/gen_frost_lance.py"""
import json
import math

from PIL import Image

ROOT = "src/main/resources/assets/jaymospowers"
NAME = "arsenal_frost_lance"
SIZE = 32
D = (1 / math.sqrt(2), -1 / math.sqrt(2))
P = (1 / math.sqrt(2), 1 / math.sqrt(2))
ORIGIN = (3.0, 29.0)
LENGTH = 36.0

COLOURS = {
    "core": (232, 250, 255),
    "ice": (150, 210, 240),
    "deep": (80, 150, 210),
    "edge": (40, 90, 150),
}


def lance(u, v):
    if not 0.0 <= u <= LENGTH:
        return None
    # a slim shaft that swells into a long faceted head
    head = LENGTH - 12.0
    half = 0.9 if u < head else 0.9 + 1.4 * math.sin(min(1.0, (u - head) / 4.0) * math.pi / 2) * min(1.0, (LENGTH - u) / 8.0)
    if abs(v) > half:
        return None
    if abs(v) > half - 0.6:
        return "edge"
    if u >= head and v < -0.2:
        return "core"
    # frost banding down the shaft
    return "deep" if u < head and int(u * 0.8) % 3 == 0 else "ice"


def main():
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    for y in range(SIZE):
        for x in range(SIZE):
            dx, dy = x + 0.5 - ORIGIN[0], y + 0.5 - ORIGIN[1]
            c = lance(dx * D[0] + dy * D[1], dx * P[0] + dy * P[1])
            if c:
                img.putpixel((x, y), (*COLOURS[c], 255))
    img.save(f"{ROOT}/textures/item/{NAME}.png")
    with open(f"{ROOT}/models/item/{NAME}.json", "w", encoding="utf-8", newline="\n") as f:
        json.dump({"parent": "minecraft:item/handheld", "textures": {"layer0": f"jaymospowers:item/{NAME}"}}, f, indent=2)
    with open(f"{ROOT}/items/{NAME}.json", "w", encoding="utf-8", newline="\n") as f:
        json.dump({"model": {"type": "minecraft:model", "model": f"jaymospowers:item/{NAME}"}}, f, indent=2)
    print("frost lance drawn")


main()
