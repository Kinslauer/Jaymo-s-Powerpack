"""Draws the pack's "Reason" title logo over vanilla's title textures.

Writes into src/main/resources/assets/minecraft/textures/gui/title/:
  minecraft.png  - the logo (1024x256; the game shows its top 176 rows as 256x44 GUI units)
  minceraft.png  - the 1-in-10000 "Minceraft" easter egg, the same logo so it never shows the old name
  edition.png    - blank, so no "JAVA EDITION" strip shows under it
Optional: python3 tools/reason_logo.py --preview out.png  (also writes a sky-blue preview of the title screen top).

Our own letters (no Mojang art): chunky 7x7 block glyphs with a stone face, carved seams, a dark extruded underside and
a black outline, to sit with vanilla's blocky look.
"""
import os
import random
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src/main/resources/assets/minecraft/textures/gui/title")

W, H = 1024, 256       # texture size (vanilla's)
SHOWN = 176            # rows the game draws (44 of 64 GUI units)
CELL = 20              # pixels per glyph cell
GAP = 1                # cells between letters
DEPTH = 14             # extrusion, pixels straight down
OUTLINE = 6            # black outline, pixels
SEAM_CHANCE = 0.38     # how often a cell edge inside a letter is carved as a block seam
SEED = 1104

GLYPHS = {
    "R": ["######.", "##...##", "##...##", "##...##", "######.", "##..##.", "##...##"],
    "E": ["#######", "##.....", "##.....", "######.", "##.....", "##.....", "#######"],
    "A": [".#####.", "##...##", "##...##", "##...##", "#######", "##...##", "##...##"],
    "S": [".######", "##.....", "##.....", ".#####.", ".....##", ".....##", "######."],
    "O": [".#####.", "##...##", "##...##", "##...##", "##...##", "##...##", ".#####."],
    "N": ["##...##", "###..##", "####.##", "##.####", "##..###", "##...##", "##...##"],
}
WORD = "REASON"


def cells():
    """Grid of the word: 1 where a block is, with each block's letter index."""
    cols = len(WORD) * 7 + (len(WORD) - 1) * GAP
    grid = np.zeros((7, cols), dtype=np.int8)
    owner = np.full((7, cols), -1, dtype=np.int8)
    for i, ch in enumerate(WORD):
        x0 = i * (7 + GAP)
        for r, row in enumerate(GLYPHS[ch]):
            for c, v in enumerate(row):
                if v == "#":
                    grid[r, x0 + c] = 1
                    owner[r, x0 + c] = i
    return grid, owner


def logo():
    rng = random.Random(SEED)
    grid, owner = cells()
    rows, cols = grid.shape
    ox = (W - cols * CELL) // 2
    oy = OUTLINE + 2

    face = np.zeros((H, W), dtype=bool)
    for r in range(rows):
        for c in range(cols):
            if grid[r, c]:
                face[oy + r * CELL: oy + (r + 1) * CELL, ox + c * CELL: ox + (c + 1) * CELL] = True

    # extruded underside: the face slid down DEPTH pixels, drawn behind it
    side = np.zeros_like(face)
    for d in range(1, DEPTH + 1):
        side[d:, :] |= face[:-d, :]
    side &= ~face

    body = face | side
    mask = Image.fromarray((body * 255).astype(np.uint8))
    outline = np.array(mask.filter(ImageFilter.MaxFilter(OUTLINE * 2 + 1))) > 0

    img = np.zeros((H, W, 4), dtype=np.float32)
    img[outline] = (12, 10, 10, 255)

    # underside: darkening toward the bottom of each drop
    ys = np.arange(H)[:, None].repeat(W, 1)
    depth_at = np.zeros((H, W), dtype=np.float32)
    for d in range(1, DEPTH + 1):
        hit = np.zeros_like(face)
        hit[d:, :] = face[:-d, :]
        newly = hit & side & (depth_at == 0)
        depth_at[newly] = d
    t = np.clip(depth_at / DEPTH, 0, 1)
    for k, (a, b) in enumerate([(118, 52), (108, 46), (104, 44)]):
        img[..., k] = np.where(side, a + (b - a) * t, img[..., k])

    # stone face: warm grey, lighter at the top of the word, a little grain
    noise = np.random.default_rng(SEED).normal(0, 4.5, (H, W))
    v = (ys - oy) / (rows * CELL)
    top = np.array([214, 206, 200], dtype=np.float32)
    bot = np.array([168, 158, 152], dtype=np.float32)
    for k in range(3):
        col = top[k] + (bot[k] - top[k]) * v + noise
        img[..., k] = np.where(face, col, img[..., k])
    img[..., 3] = np.where(outline, 255, 0)

    # bevel: lit top/left edge, shaded bottom/right edge of every letter face
    up = np.zeros_like(face); up[1:, :] = face[:-1, :]
    left = np.zeros_like(face); left[:, 1:] = face[:, :-1]
    down = np.zeros_like(face); down[:-1, :] = face[1:, :]
    right = np.zeros_like(face); right[:, :-1] = face[:, 1:]
    for w in range(3):
        sh = lambda a, dy, dx: np.roll(np.roll(a, dy, 0), dx, 1)
        lit = face & ~(sh(face, w + 1, 0) & sh(face, 0, w + 1))
        dark = face & ~(sh(face, -(w + 1), 0) & sh(face, 0, -(w + 1)))
        for k in range(3):
            img[..., k] = np.where(lit, np.minimum(img[..., k] + 26 - w * 6, 240), img[..., k])
            img[..., k] = np.where(dark & ~lit, img[..., k] - 34 + w * 8, img[..., k])

    # carved seams between some blocks of the same letter: a dark groove with a light lip under/right of it
    pil = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGBA")
    draw = ImageDraw.Draw(pil)
    groove, lip = (92, 84, 80, 255), (232, 226, 220, 255)
    for r in range(rows):
        for c in range(cols):
            if not grid[r, c]:
                continue
            x, y = ox + c * CELL, oy + r * CELL
            if c + 1 < cols and grid[r, c + 1] and owner[r, c + 1] == owner[r, c] and rng.random() < SEAM_CHANCE:
                gx = x + CELL
                y0, y1 = y + rng.randint(0, 4), y + CELL - rng.randint(0, 4)
                draw.line([(gx - 1, y0), (gx - 1, y1)], fill=groove, width=3)
                draw.line([(gx + 1, y0), (gx + 1, y1)], fill=lip, width=1)
            if r + 1 < rows and grid[r + 1, c] and owner[r + 1, c] == owner[r, c] and rng.random() < SEAM_CHANCE:
                gy = y + CELL
                x0, x1 = x + rng.randint(0, 4), x + CELL - rng.randint(0, 4)
                draw.line([(x0, gy - 1), (x1, gy - 1)], fill=groove, width=3)
                draw.line([(x0, gy + 1), (x1, gy + 1)], fill=lip, width=1)
            # now and then a short crack, as vanilla's worn stone has
            if rng.random() < 0.12:
                cx, cy = x + rng.randint(4, CELL - 4), y + rng.randint(4, CELL - 4)
                pts = [(cx, cy)]
                for _ in range(3):
                    cx += rng.choice((-4, 0, 4)); cy += 4
                    pts.append((cx, cy))
                draw.line(pts, fill=(120, 112, 106, 255), width=2)
    # seams and cracks may only mark the face
    arr = np.array(pil)
    base = np.clip(img, 0, 255).astype(np.uint8)
    arr[~face] = base[~face]
    arr[SHOWN:, :] = 0
    return Image.fromarray(arr, "RGBA")


def preview(img, path):
    """The title screen's top at GUI scale 2: logo drawn 512x88 on a sky, with the yellow caption."""
    sky = Image.new("RGBA", (1100, 260), (122, 166, 255, 255))
    shown = img.crop((0, 0, W, SHOWN)).resize((512, 88), Image.LANCZOS)
    x, y = (sky.width - 512) // 2, 60
    sky.alpha_composite(shown, (x, y))
    # the caption sits where vanilla's splash does: right end of the logo, tilted -20 degrees
    try:
        font = ImageFont.truetype("DejaVuSans-Bold.ttf", 22)
    except OSError:
        font = ImageFont.load_default()
    text = "what's yours"
    tw = int(draw_len(font, text)) + 8
    lay = Image.new("RGBA", (tw, 36), (0, 0, 0, 0))
    d = ImageDraw.Draw(lay)
    d.text((6, 6), text, font=font, fill=(62, 62, 0, 255))
    d.text((4, 4), text, font=font, fill=(255, 255, 0, 255))
    lay = lay.rotate(20, expand=True, resample=Image.BICUBIC)
    sky.alpha_composite(lay, (x + 512 - lay.width // 2 + 10, y + 60 - lay.height // 2))
    sky.save(path)


def draw_len(font, text):
    return font.getlength(text) if hasattr(font, "getlength") else len(text) * 12


def main():
    img = logo()
    os.makedirs(OUT, exist_ok=True)
    img.save(os.path.join(OUT, "minecraft.png"), optimize=True)
    img.save(os.path.join(OUT, "minceraft.png"), optimize=True)
    Image.new("RGBA", (512, 64), (0, 0, 0, 0)).save(os.path.join(OUT, "edition.png"), optimize=True)
    if "--preview" in sys.argv:
        preview(img, sys.argv[sys.argv.index("--preview") + 1])


if __name__ == "__main__":
    main()
