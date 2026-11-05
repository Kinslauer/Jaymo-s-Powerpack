"""Draws Jaymo's Kingdoms' bent ingots (0.7.490; original art). Run from kingdoms/: python -I tools/gen_bent.py

bent_copper: a copper ingot hammered into a shallow V, one kink in the middle.
bent_iron: an iron ingot hammered into a Z, two kinks.
Vanilla style: 16x16, no black outline, light from the top-left, a seven-step ramp per metal (darkest edge .. glint).
"""
import os

from PIL import Image

OUT = os.path.join("src", "main", "resources", "assets", "jaymoskingdoms", "textures", "item")

# seven-step ramps, darkest (edge) to brightest (glint)
COPPER = [(0x3b, 0x1c, 0x14), (0x6b, 0x2f, 0x1f), (0x9c, 0x4a, 0x2e), (0xc4, 0x6a, 0x43), (0xe0, 0x8c, 0x5c), (0xf2, 0xb0, 0x84), (0xff, 0xd2, 0xb0)]
IRON = [(0x18, 0x18, 0x18), (0x44, 0x44, 0x44), (0x6b, 0x6b, 0x6b), (0x96, 0x96, 0x96), (0xbe, 0xbe, 0xbe), (0xd8, 0xd8, 0xd8), (0xff, 0xff, 0xff)]

# the bar's spine as (x, y) points; it is drawn three pixels thick, straight up and down, so it reads as a flat bar
COPPER_SPINE = [(2, 11), (6, 12), (8, 10), (10, 6), (13, 4)]
IRON_SPINE = [(2, 5), (6, 4), (6, 8), (9, 9), (9, 12), (13, 11)]


def spine_pixels(points):
    """Every cell along the polyline, one per column step (and per row step on steep runs)."""
    cells = []
    for (x0, y0), (x1, y1) in zip(points, points[1:]):
        n = max(abs(x1 - x0), abs(y1 - y0))
        for i in range(n + 1):
            t = i / n if n else 0
            cells.append((round(x0 + (x1 - x0) * t), round(y0 + (y1 - y0) * t)))
    return cells


def bar(points):
    """The bar as {cell: depth}: 0 on its top edge, 1 in its body, 2 on its bottom edge (the lighter wins where cells overlap)."""
    body = {}
    for x, y in spine_pixels(points):
        for dy, depth in ((-1, 0), (0, 1), (1, 2)):
            body[(x, y + dy)] = min(body.get((x, y + dy), 9), depth)
    return body


def draw(name, ramp, spine):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    cells = bar(spine)
    for (x, y), depth in cells.items():
        if not (0 <= x < 16 and 0 <= y < 16):
            continue
        # light from the top-left: the top edge bright, the body mid, the bottom edge dark; a left-hand shift brightens
        shade = {0: 4, 1: 3, 2: 2}[depth]
        if (x - 1, y) not in cells:
            shade += 1
        if (x + 1, y) not in cells:
            shade -= 1
        px[x, y] = (*ramp[max(1, min(5, shade))], 255)
    # a dark edge under the bar, a glint on its first top-left cell
    for (x, y) in list(cells):
        if 0 <= x < 16 and 0 <= y + 1 < 16 and (x, y + 1) not in cells:
            px[x, y + 1] = (*ramp[0], 255)
    first = min(cells, key=lambda c: (c[0] + c[1]))
    if 0 <= first[0] < 16 and 0 <= first[1] < 16:
        px[first] = (*ramp[6], 255)
    os.makedirs(OUT, exist_ok=True)
    img.save(os.path.join(OUT, name + ".png"))


if __name__ == "__main__":
    draw("bent_copper", COPPER, COPPER_SPINE)
    draw("bent_iron", IRON, IRON_SPINE)
    print("drew", OUT)
