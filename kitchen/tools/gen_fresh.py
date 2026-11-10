"""Generates the art for Jaymo's Kitchen's food that doesn't keep (fresh/): Spoiled Food (a grey-green mush with mould
spots), the cheese wheel (young and aged, whole to a quarter left) and its slices. Our own pixels in vanilla's 16x16
style, plus the model, blockstate and item JSON.
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


RIND = {"young": [(214, 178, 74), (230, 196, 92), (196, 160, 62)], "aged": [(170, 118, 46), (150, 100, 38), (186, 134, 58)]}
PASTE = {"young": [(250, 232, 150), (244, 224, 138), (252, 238, 166)], "aged": [(238, 196, 96), (230, 186, 84), (244, 206, 110)]}


def speckled(rng, ramp, holes=0):
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), ramp[int(rng.random() * len(ramp))] + (255,))
    for _ in range(holes):
        x, y = rng.randrange(1, 15), rng.randrange(1, 15)
        img.putpixel((x, y), tuple(max(0, c - 40) for c in ramp[0]) + (255,))
    return img


def top(rng, age):
    img = speckled(rng, PASTE[age], 5 if age == "young" else 9)
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15):
                img.putpixel((x, y), RIND[age][0] + (255,))
    return img


def save_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n")


QUARTERS = [([2, 0, 2], [8, 6, 8]), ([8, 0, 2], [14, 6, 8]), ([8, 0, 8], [14, 6, 14]), ([2, 0, 8], [8, 6, 14])]


def element(frm, to):
    x0, y0, z0 = frm
    x1, y1, z1 = to

    def side(outer):
        return "#side" if outer else "#paste"
    return {"from": frm, "to": to, "faces": {
        "north": {"uv": [16 - x1, 10, 16 - x0, 16], "texture": side(z0 == 2)},
        "south": {"uv": [x0, 10, x1, 16], "texture": side(z1 == 14)},
        "west": {"uv": [z0, 10, z1, 16], "texture": side(x0 == 2)},
        "east": {"uv": [16 - z1, 10, 16 - z0, 16], "texture": side(x1 == 14)},
        "up": {"uv": [x0, z0, x1, z1], "texture": "#top"},
        "down": {"uv": [x0, 16 - z1, x1, 16 - z0], "texture": "#top", "cullface": "down"}}}


def cheese():
    rng = random.Random(9031)
    tex = ROOT / "textures"
    for age in ("young", "aged"):
        speckled(rng, RIND[age]).save(tex / "block" / f"cheese_wheel_{age}_side.png")
        top(rng, age).save(tex / "block" / f"cheese_wheel_{age}_top.png")
        speckled(rng, PASTE[age], 3).save(tex / "block" / f"cheese_wheel_{age}_paste.png")
        for cut in range(4):
            save_json(ROOT / "models" / "block" / f"cheese_wheel_{age}_{cut}.json", {
                "textures": {"side": f"jaymoskitchen:block/cheese_wheel_{age}_side", "top": f"jaymoskitchen:block/cheese_wheel_{age}_top",
                             "paste": f"jaymoskitchen:block/cheese_wheel_{age}_paste", "particle": f"jaymoskitchen:block/cheese_wheel_{age}_side"},
                "elements": [element(*q) for q in QUARTERS[cut:]]})
    variants = {}
    for age in range(4):
        for cut in range(4):
            variants[f"age={age},cut={cut}"] = {"model": f"jaymoskitchen:block/cheese_wheel_{'aged' if age == 3 else 'young'}_{cut}"}
    save_json(ROOT / "blockstates" / "cheese_wheel.json", {"variants": variants})
    save_json(ROOT / "items" / "cheese_wheel.json", {"model": {"type": "minecraft:model", "model": "jaymoskitchen:block/cheese_wheel_young_0"}})
    for name, age in (("cheese", "young"), ("aged_cheese", "aged")):
        img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        # a wedge: a right triangle of paste with a rind edge on its long side
        for y in range(4, 13):
            for x in range(3, 3 + (y - 3) + 4):
                if x > 13:
                    continue
                c = RIND[age][0] if x == 3 + (y - 3) + 3 or y == 12 else PASTE[age][int(rng.random() * 3)]
                img.putpixel((x, y), c + (255,))
        for _ in range(3):
            x, y = rng.randrange(5, 9), rng.randrange(8, 12)
            img.putpixel((x, y), tuple(max(0, c - 40) for c in PASTE[age][0]) + (255,))
        img.save(tex / "item" / f"{name}.png")
        save_json(ROOT / "models" / "item" / f"{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"jaymoskitchen:item/{name}"}})
        save_json(ROOT / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": f"jaymoskitchen:item/{name}"}})


if __name__ == "__main__":
    main()
    cheese()
