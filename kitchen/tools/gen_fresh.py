"""Generates Spoiled Food's art for Jaymo's Kitchen (fresh/Freshness): a grey-green mush with mould spots, our own
pixels in vanilla's 16x16 style, plus its item model JSON.
Run from the kitchen root:  python -I tools/gen_fresh.py
"""
import json
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "assets" / "jaymoskitchen"
MUSH = [(70, 74, 46), (92, 96, 58), (112, 112, 70), (128, 124, 84)]
MOULD = [(196, 204, 180), (150, 176, 120)]
OUTLINE = (44, 40, 28)


def main():
    rng = random.Random(4417)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    cx, cy = 7.5, 9.0
    for y in range(16):
        for x in range(16):
            d = ((x - cx) / 6.2) ** 2 + ((y - cy) / 4.4) ** 2
            if d <= 1.0:
                c = OUTLINE if d > 0.78 else MUSH[min(3, int(rng.random() * 3) + (1 if y < cy - 1 else 0))]
                img.putpixel((x, y), c + (255,))
    for _ in range(7):
        x, y = rng.randrange(4, 12), rng.randrange(6, 12)
        if img.getpixel((x, y))[3] and img.getpixel((x, y))[:3] != OUTLINE:
            img.putpixel((x, y), MOULD[rng.random() < 0.5] + (255,))
    out = ROOT / "textures" / "item" / "spoiled_food.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    img.save(out)
    (ROOT / "models" / "item" / "spoiled_food.json").write_text(json.dumps(
        {"parent": "minecraft:item/generated", "textures": {"layer0": "jaymoskitchen:item/spoiled_food"}}, indent=2) + "\n")
    (ROOT / "items" / "spoiled_food.json").write_text(json.dumps(
        {"model": {"type": "minecraft:model", "model": "jaymoskitchen:item/spoiled_food"}}, indent=2) + "\n")


if __name__ == "__main__":
    main()
