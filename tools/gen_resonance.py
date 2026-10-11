"""Generates Resonance's item textures: the Tuning Fork and the Resonant Fork (16x16, held diagonally).

All original pixel art, drawn procedurally: a two-tined fork lying along the item diagonal, the handle bottom-left,
the tines top-right, with a one-pixel darker outline.
Run from the project root:  python -I tools/gen_resonance.py

  textures/item/resonance_tuning_fork.png    copper handle, amethyst tines
  textures/item/resonance_resonant_fork.png  deepslate handle, sculk-teal tines with echo-bright tips
"""
from pathlib import Path

from PIL import Image

ASSETS = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "assets" / "jaymospowers"
OUT = ASSETS / "textures" / "item"


def line(a, b):
    """Pixels on the straight line from a to b (inclusive), one per step along the longer axis."""
    (x0, y0), (x1, y1) = a, b
    steps = max(abs(x1 - x0), abs(y1 - y0))
    return [(round(x0 + (x1 - x0) * i / steps), round(y0 + (y1 - y0) * i / steps)) for i in range(steps + 1)]


def fork(handle, handle_light, handle_dark, tine, tine_light, tine_dark, tip, outline):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = {}
    # handle: from the yoke down to the bottom-left, with a lit edge
    for p in line((7, 8), (2, 13)):
        px[p] = handle
    for p in line((6, 8), (2, 12)):
        px.setdefault(p, handle_light)
    px[(1, 14)] = handle_dark
    px[(2, 14)] = handle_dark
    px[(1, 13)] = handle_dark
    # the yoke, across the diagonal
    for p in line((5, 6), (9, 10)):
        px[p] = tine_dark
    for p in [(6, 7), (8, 9)]:
        px[p] = tine
    # two tines, parallel to the diagonal
    for start, end in (((5, 6), (10, 1)), ((9, 10), (14, 5))):
        pts = line(start, end)
        for i, p in enumerate(pts):
            px[p] = tine_light if i % 2 == 0 else tine
        px[pts[-1]] = tip
        # a shadow line under each tine
        for p in line((start[0] + 1, start[1]), (end[0], end[1] + 1)):
            px.setdefault(p, tine_dark)
    # outline: every empty pixel touching the fork
    for (x, y) in list(px):
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            q = (x + dx, y + dy)
            if 0 <= q[0] < 16 and 0 <= q[1] < 16 and q not in px:
                px[q] = None
    for (x, y), c in px.items():
        if 0 <= x < 16 and 0 <= y < 16:
            img.putpixel((x, y), outline if c is None else c)
    return img


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    fork(
        handle=(196, 106, 70, 255), handle_light=(232, 150, 108, 255), handle_dark=(120, 60, 40, 255),
        tine=(169, 127, 224, 255), tine_light=(214, 180, 252, 255), tine_dark=(110, 75, 168, 255),
        tip=(255, 236, 255, 255), outline=(40, 22, 48, 200),
    ).save(OUT / "resonance_tuning_fork.png")
    fork(
        handle=(72, 74, 84, 255), handle_light=(104, 108, 120, 255), handle_dark=(40, 40, 48, 255),
        tine=(16, 120, 128, 255), tine_light=(41, 200, 210, 255), tine_dark=(8, 70, 80, 255),
        tip=(190, 255, 250, 255), outline=(6, 20, 26, 210),
    ).save(OUT / "resonance_resonant_fork.png")


if __name__ == "__main__":
    main()
