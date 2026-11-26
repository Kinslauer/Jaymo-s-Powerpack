#!/usr/bin/env python3
"""The four symbiote stage-card pictures (Attached, Integrated, Cooperative, Dominant): abstract goo and tendril motifs,
drawn in light grey on transparent so the client can tint them per strain. Our own art.
Run: python3 tools/gen_web_stage_cards.py"""
import math
import random
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/jaymospowers/textures/gui/web_stage"
S = 48          # final size
K = 8           # supersampling
STAGES = ["attached", "integrated", "cooperative", "dominant"]


def tendril(draw, start, angle, length, width, wobble, rng):
    x, y = start
    a = angle
    steps = 40
    for i in range(steps):
        t = i / steps
        a += rng.uniform(-wobble, wobble)
        x += math.cos(a) * length / steps
        y += math.sin(a) * length / steps
        r = max(1.0, width * (1 - t) * K)
        draw.ellipse((x - r, y - r, x + r, y + r), fill=255)


def drop(draw, x, y, r):
    draw.ellipse((x - r, y - r, x + r, y + r), fill=255)


def card(stage):
    rng = random.Random(113 + STAGES.index(stage))
    n = S * K
    mask = Image.new("L", (n, n), 0)
    d = ImageDraw.Draw(mask)
    cx = cy = n / 2
    if stage == "attached":
        # one small blob with a cord reaching out: a first hold
        drop(d, cx, cy + 6 * K, 9 * K)
        tendril(d, (cx, cy), -math.pi / 2, 20 * K, 2.4, 0.25, rng)
        tendril(d, (cx - 3 * K, cy + 4 * K), math.pi * 0.9, 12 * K, 1.6, 0.3, rng)
    elif stage == "integrated":
        # a blob with cords fanned from it: it has spread
        drop(d, cx, cy, 11 * K)
        for i in range(5):
            tendril(d, (cx, cy), math.radians(200 + i * 35), 20 * K, 2.2, 0.3, rng)
    elif stage == "cooperative":
        # two cords twining round each other: moving together
        for phase in (0, math.pi):
            for i in range(60):
                t = i / 59
                x = cx + (t - 0.5) * 40 * K
                y = cy + math.sin(t * math.pi * 3 + phase) * 9 * K
                r = (2.6 + 0.8 * math.cos(t * math.pi * 3 + phase)) * K
                drop(d, x, y, r)
        drop(d, cx - 20 * K, cy, 3 * K)
        drop(d, cx + 20 * K, cy, 3 * K)
    else:
        # a mass with cords in every direction and drops thrown off: little left between
        drop(d, cx, cy, 13 * K)
        for i in range(9):
            tendril(d, (cx, cy), math.radians(i * 40 + rng.uniform(-10, 10)), rng.uniform(16, 23) * K, 2.8, 0.35, rng)
        for _ in range(8):
            a = rng.uniform(0, math.tau)
            r = rng.uniform(17, 22) * K
            drop(d, cx + math.cos(a) * r, cy + math.sin(a) * r, rng.uniform(1.0, 2.0) * K)
    mask = mask.filter(ImageFilter.GaussianBlur(K * 0.5)).resize((S, S), Image.LANCZOS)
    img = Image.new("RGBA", (S, S), (255, 255, 255, 0))
    px = img.load()
    mp = mask.load()
    for y in range(S):
        for x in range(S):
            a = mp[x, y]
            if a:
                # a soft sheen, brighter toward the upper left, so the tint still reads as glossy
                shade = 215 + int(40 * (1 - (x + y) / (2 * S)))
                px[x, y] = (shade, shade, shade, a)
    return img


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    for stage in STAGES:
        card(stage).save(OUT / f"{stage}.png")
        print("wrote", OUT / f"{stage}.png")
