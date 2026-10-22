"""Fish Bait (batch 5): its icon, item model and recipes. Original pixel art drawn here with Pillow; nothing is copied.

A lump of mashed scraps rolled in grain: vanilla style (no black outline, lit from the top-left).
Run from anywhere: python -I tools/gen_bait.py
"""
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent / "src" / "main" / "resources"
ASSETS = ROOT / "assets" / "jaymoskitchen"
DATA = ROOT / "data" / "jaymoskitchen"

P = {
    ".": (0, 0, 0, 0),
    "D": (82, 40, 38, 255),     # outline, shaded side
    "d": (112, 58, 52, 255),    # outline, lit side / shade band
    "m": (150, 84, 72, 255),    # body
    "l": (184, 112, 94, 255),   # lit body
    "h": (214, 148, 122, 255),  # glint
    "w": (222, 192, 98, 255),   # grain fleck
    "W": (176, 142, 62, 255),   # grain fleck in shade
}

ICON = [
    "................",
    "................",
    "................",
    "......dddd......",
    "....ddhlllmD....",
    "...dhllwlmmmD...",
    "..dhlllllmwmmD..",
    "..dllwllmmmmmD..",
    "..dlllmmmmmWmD..",
    "..dmlmmmwmmmdD..",
    "...dmmWmmmmddD..",
    "...DmmmmmdddD...",
    "....DDmddddD....",
    "......DDDD......",
    "................",
    "................",
]


def render(rows):
    assert len(rows) == 16 and all(len(r) == 16 for r in rows)
    img = Image.new("RGBA", (16, 16))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            img.putpixel((x, y), P[ch])
    return img


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def shapeless(name, ingredients, count):
    write_json(DATA / "recipe" / f"{name}.json", {"type": "minecraft:crafting_shapeless", "category": "misc",
                                                  "ingredients": ingredients,
                                                  "result": {"id": "jaymoskitchen:fish_bait", "count": count}})


def main():
    tex = ASSETS / "textures" / "item" / "fish_bait.png"
    tex.parent.mkdir(parents=True, exist_ok=True)
    render(ICON).save(tex)
    write_json(ASSETS / "models" / "item" / "fish_bait.json",
               {"parent": "minecraft:item/generated", "textures": {"layer0": "jaymoskitchen:item/fish_bait"}})
    write_json(ASSETS / "items" / "fish_bait.json",
               {"model": {"type": "minecraft:model", "model": "jaymoskitchen:item/fish_bait"}})
    grain = ["minecraft:wheat", "minecraft:bread"]
    shapeless("fish_bait", ["minecraft:rotten_flesh", grain], 4)
    shapeless("fish_bait_from_scraps", [["minecraft:beef", "minecraft:porkchop", "minecraft:chicken", "minecraft:mutton",
                                         "minecraft:rabbit", "minecraft:cod", "minecraft:salmon"], grain], 4)


if __name__ == "__main__":
    main()
