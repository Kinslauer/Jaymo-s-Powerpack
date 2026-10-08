"""Generates the Web Slinger assets: the web ball item texture (also what the Web Shot projectile looks like) and its
item model files. Hand-drawn 16x16 pixel grid, one char per pixel, so no vanilla art is copied.
Run from the project root:  python tools/gen_web.py
"""
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "assets" / "jaymospowers"

PALETTE = {
    ".": (0, 0, 0, 0),
    "w": (250, 250, 250, 255),  # bright strand
    "s": (214, 216, 222, 255),  # strand
    "g": (168, 172, 182, 255),  # shadowed strand
    "d": (120, 124, 136, 255),  # deep shadow / outline
    "f": (236, 238, 244, 170),  # translucent film between strands
}

# a clump of web: a ball of crossing strands with a few loose threads trailing off
WEB_BALL = [
    "................",
    ".......s........",
    "...s..dsd...s...",
    "....sdgwsgd.s...",
    "...dfswfwsfd....",
    "..dgwffsffwgd...",
    ".sswsfwswfswss..",
    "..dfsfswsfsfd...",
    "..gwffwswffwg.s.",
    "..dgsfswsfsgd...",
    "...dgwfsfwgd....",
    "....ddgwgdd.....",
    "...s..dsd..s....",
    "..s....s....s...",
    "................",
    "................",
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
    item("web_ball", WEB_BALL)
    print("web assets written")
