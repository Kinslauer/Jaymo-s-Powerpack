"""Generates the Pocket Arsenal assets: the Dimensional Key item texture and its item model files. Hand-drawn 16x16
pixel grid, one char per pixel, so no vanilla art is copied.
Run from the project root:  python tools/gen_arsenal.py
"""
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "assets" / "jaymospowers"

PALETTE = {
    ".": (0, 0, 0, 0),
    "o": (24, 14, 34, 255),     # outline, near-black violet
    "d": (58, 46, 72, 255),     # dark blackened iron
    "m": (96, 84, 112, 255),    # iron
    "l": (150, 140, 166, 255),  # iron highlight
    "w": (214, 206, 226, 255),  # glint
    "v": (16, 4, 28, 255),      # the void in the bow
    "p": (110, 40, 170, 255),   # void purple
    "P": (190, 110, 255, 255),  # bright void spark
    "s": (232, 210, 255, 255),  # a star in the void
}

# an old key, its bow a ring around a pocket of starry void, its bit cut in an odd stepped pattern
DIMENSIONAL_KEY = [
    "................",
    "....oooo........",
    "...odmmdo.......",
    "..odpvvpdo......",
    "..ompvsvpmo.....",
    "..omvPvvvlo.....",
    "..odpvvpPdo.....",
    "...odmmldo......",
    "....ooldmo......",
    "......omlo......",
    "......omdo......",
    "......omloooo...",
    "......omdmddo...",
    "......omlooo....",
    "......omdmo.....",
    "......oooo......",
]


def draw(grid):
    img = Image.new("RGBA", (16, 16))
    for y, row in enumerate(grid):
        assert len(row) == 16, (y, row)
        for x, ch in enumerate(row):
            img.putpixel((x, y), PALETTE[ch])
    return img


def item(name, grid):
    tex = ROOT / "textures" / "item" / f"{name}.png"
    tex.parent.mkdir(parents=True, exist_ok=True)
    draw(grid).save(tex)
    model = ROOT / "models" / "item" / f"{name}.json"
    model.parent.mkdir(parents=True, exist_ok=True)
    model.write_text(json.dumps({"parent": "minecraft:item/generated", "textures": {"layer0": f"jaymospowers:item/{name}"}}, indent=2) + "\n")
    definition = ROOT / "items" / f"{name}.json"
    definition.parent.mkdir(parents=True, exist_ok=True)
    definition.write_text(json.dumps({"model": {"type": "minecraft:model", "model": f"jaymospowers:item/{name}"}}, indent=2) + "\n")


if __name__ == "__main__":
    item("arsenal_dimensional_key", DIMENSIONAL_KEY)
    print("wrote arsenal_dimensional_key")
