"""Generates the Smoke power's puff texture: four soft, ragged puffs of smoke in a 2x2 sheet (128x128).

All original, drawn procedurally: a round falloff broken up by a few octaves of value noise, white, the shape in the
alpha channel (the renderer tints and fades it). Run from the project root:  python -I tools/gen_smoke.py

  textures/entity/smoke/puff.png
"""
import math
import random
from pathlib import Path

from PIL import Image

ASSETS = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "assets" / "jaymospowers"
OUT = ASSETS / "textures" / "entity" / "smoke"
SIZE = 64


def noise_grid(rng, cells):
    return [[rng.random() for _ in range(cells + 1)] for _ in range(cells + 1)]


def sample(grid, cells, x, y):
    """Smooth value noise at (x, y) in 0-1."""
    gx, gy = x * cells, y * cells
    x0, y0 = int(gx), int(gy)
    x0, y0 = min(x0, cells - 1), min(y0, cells - 1)
    fx, fy = gx - x0, gy - y0
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    a = grid[y0][x0] + (grid[y0][x0 + 1] - grid[y0][x0]) * fx
    b = grid[y0 + 1][x0] + (grid[y0 + 1][x0 + 1] - grid[y0 + 1][x0]) * fx
    return a + (b - a) * fy


def puff(seed):
    rng = random.Random(seed)
    octaves = [(4, 0.5), (8, 0.3), (16, 0.2)]
    grids = [(noise_grid(rng, c), c, w) for c, w in octaves]
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    for y in range(SIZE):
        for x in range(SIZE):
            u, v = (x + 0.5) / SIZE, (y + 0.5) / SIZE
            r = math.hypot(u - 0.5, v - 0.5) * 2.0
            n = sum(sample(g, c, u, v) * w for g, c, w in grids)
            # a soft ball, its edge eaten away by the noise
            edge = 1.0 - r * (0.75 + 0.5 * n)
            a = max(0.0, min(1.0, edge * 1.6)) ** 1.4
            a *= 0.55 + 0.45 * n
            shade = int(200 + 55 * n)
            img.putpixel((x, y), (shade, shade, shade, int(a * 255)))
    return img


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    sheet = Image.new("RGBA", (SIZE * 2, SIZE * 2), (0, 0, 0, 0))
    for i in range(4):
        sheet.paste(puff(1301 + i * 17), ((i % 2) * SIZE, (i // 2) * SIZE))
    sheet.save(OUT / "puff.png")
    print("wrote", OUT / "puff.png")


if __name__ == "__main__":
    main()
