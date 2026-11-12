#!/usr/bin/env python3
"""Draws the water caustics frames (Kingdoms SunShade): a web of light that loops in time and tiles in space.

Each frame is 32x32 (two blocks each way at 16 pixels a block). Points drift round small circles; light gathers along
the lines midway between neighbouring points (where the two nearest are equally far), as it does under ripples.
Run: python3 tools/caustics_art.py
"""
import math
import os
import random

import numpy as np
from PIL import Image

SIZE, FRAMES, POINTS = 32, 16, 9
OUT = os.path.join(os.path.dirname(__file__), "..", "kingdoms", "src", "main", "resources", "assets", "jaymoskingdoms", "textures", "skies")
# alpha steps, so the web keeps hard pixel edges
LEVELS = (0, 70, 140, 215)


def main():
    rng = random.Random(1343)
    points = [(rng.random() * SIZE, rng.random() * SIZE, 1.2 + rng.random() * 1.6, rng.random() * math.tau, rng.choice((-1, 1)))
              for _ in range(POINTS)]
    ys, xs = np.mgrid[0:SIZE, 0:SIZE] + 0.5
    for frame in range(FRAMES):
        turn = math.tau * frame / FRAMES
        dists = []
        for px, py, radius, phase, way in points:
            cx = px + math.cos(phase + way * turn) * radius
            cy = py + math.sin(phase + way * turn) * radius
            dx = np.abs(xs - cx) % SIZE
            dy = np.abs(ys - cy) % SIZE
            dx = np.minimum(dx, SIZE - dx)
            dy = np.minimum(dy, SIZE - dy)
            dists.append(np.hypot(dx, dy))
        nearest = np.sort(np.stack(dists), axis=0)
        edge = nearest[1] - nearest[0]
        light = np.clip(1.0 - edge / 2.6, 0.0, 1.0) ** 1.6
        alpha = np.zeros((SIZE, SIZE), dtype=np.uint8)
        for step, level in enumerate(LEVELS):
            alpha[light >= step / len(LEVELS)] = level
        image = np.zeros((SIZE, SIZE, 4), dtype=np.uint8)
        image[..., 0], image[..., 1], image[..., 2], image[..., 3] = 235, 250, 255, alpha
        Image.fromarray(image, "RGBA").save(os.path.join(OUT, f"caustics_{frame}.png"), optimize=True)


if __name__ == "__main__":
    main()
