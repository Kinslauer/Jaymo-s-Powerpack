"""The food journal and the lunch box (GT New Horizons picks, patch 2): their icons, item models and recipes. Original
pixel art drawn here with Pillow; nothing is copied.

Vanilla style (lit from the top-left, darker outline on the shaded sides). The journal is a small brown book with a
wheat ear tooled on its cover; the lunch box a hemp-cloth pouch drawn shut with string.
Run from anywhere: python -I kitchen/tools/gen_gtnh_farms.py
"""
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent / "src" / "main" / "resources"
ASSETS = ROOT / "assets" / "jaymoskitchen"
DATA = ROOT / "data" / "jaymoskitchen"

JOURNAL_P = {
    ".": (0, 0, 0, 0),
    "D": (58, 34, 20, 255),     # cover outline, shaded
    "d": (86, 52, 30, 255),     # cover, shade
    "m": (118, 74, 42, 255),    # cover
    "l": (146, 96, 56, 255),    # cover, lit
    "p": (226, 214, 184, 255),  # page edges
    "P": (186, 172, 140, 255),  # page edges, shaded
    "w": (222, 190, 92, 255),   # tooled wheat
    "W": (172, 138, 58, 255),   # tooled wheat, shaded
}

JOURNAL = [
    "................",
    "................",
    "...Dddddddddd...",
    "..Dlllllllllmd..",
    "..Dlmmmmmmmmmdp.",
    "..Dlmmmmwmmmmdp.",
    "..Dlmmmwmwmmmdp.",
    "..DlmmmmwWmmmdp.",
    "..DlmmmwmWmmmdP.",
    "..DlmmmmWmmmmdP.",
    "..DlmmmmWmmmmdP.",
    "..DlmmmmWmmmmdP.",
    "..Dlmmmmmmmmmdp.",
    "..DdddddddddddP.",
    "...DDDDDDDDDDPP.",
    "................",
]

POUCH_P = {
    ".": (0, 0, 0, 0),
    "D": (92, 74, 44, 255),     # cloth outline, shaded
    "d": (128, 106, 66, 255),   # cloth, shade
    "m": (168, 142, 94, 255),   # cloth
    "l": (200, 176, 124, 255),  # cloth, lit
    "h": (224, 204, 156, 255),  # cloth, highlight
    "s": (232, 228, 216, 255),  # string
    "S": (170, 164, 150, 255),  # string, shaded
}

POUCH = [
    "................",
    "................",
    "......s..s......",
    ".......ss.......",
    "......DSSD......",
    ".....DlmmdD.....",
    ".....SsssSS.....",
    "....DlhlmmdD....",
    "...DlhllmmmdD...",
    "..DlhlllmmmmdD..",
    "..DllllmmmmmdD..",
    "..DlllmmmmmddD..",
    "..DdlmmmmmmddD..",
    "...DddmmmmddD...",
    "....DDDDDDDD....",
    "................",
]


def render(rows, palette):
    assert len(rows) == 16 and all(len(r) == 16 for r in rows)
    img = Image.new("RGBA", (16, 16))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            img.putpixel((x, y), palette[ch])
    return img


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def item(name, rows, palette):
    tex = ASSETS / "textures" / "item" / f"{name}.png"
    tex.parent.mkdir(parents=True, exist_ok=True)
    render(rows, palette).save(tex)
    write_json(ASSETS / "models" / "item" / f"{name}.json",
               {"parent": "minecraft:item/generated", "textures": {"layer0": f"jaymoskitchen:item/{name}"}})
    write_json(ASSETS / "items" / f"{name}.json",
               {"model": {"type": "minecraft:model", "model": f"jaymoskitchen:item/{name}"}})


def main():
    item("food_journal", JOURNAL, JOURNAL_P)
    item("lunch_box", POUCH, POUCH_P)
    write_json(DATA / "recipe" / "food_journal.json", {
        "type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": ["minecraft:book", "minecraft:feather", "minecraft:wheat"],
        "result": {"id": "jaymoskitchen:food_journal"}})
    write_json(DATA / "recipe" / "lunch_box.json", {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "key": {"S": "minecraft:string", "C": "jaymoskitchen:hemp_cloth"},
        "pattern": [" S ", "C C", "CCC"],
        "result": {"id": "jaymoskitchen:lunch_box"}})


if __name__ == "__main__":
    main()
