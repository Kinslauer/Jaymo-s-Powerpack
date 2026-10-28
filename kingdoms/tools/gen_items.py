"""Draws Jaymo's Kingdoms' item sprites (original art). Run from kingdoms/: python -I tools/gen_items.py

plot_stake: a pointed wooden stake with a little red pennant tied on near the top.
awareness_eye (0.7.111): the stealth gauge's eye, four frames.
hidden_blade (0.7.101): a brown leather bracer with a spring blade run out of it, vanilla style (handle bottom-left, blade
top-right, no black outlines, light from the top-left, vanilla's own iron ramp).
horse_handbook (0.7.305): the Horsekeeper's Handbook, a brown leather book with a gold horseshoe on its cover and the
pages' cream edge down its right side, lit from the top-left.
dynamite (0.7.107): a fat red stick wrapped in a paper band, its fuse lit at the top right; drawn as a capsule along the
diagonal and shaded across it (lit edge top-left), vanilla style.
"""
import math
import os

from PIL import Image

OUT = os.path.join("src", "main", "resources", "assets", "jaymoskingdoms", "textures", "item")

WOOD_DARK = (92, 62, 34, 255)
WOOD = (140, 98, 56, 255)
WOOD_LIGHT = (176, 132, 82, 255)
CLOTH = (186, 38, 36, 255)
CLOTH_DARK = (128, 24, 24, 255)
STRING = (222, 214, 190, 255)


# vanilla's iron ramp (tools/vanilla_style/vstyle.py MAT['iron'], steps 0-6: outline .. glint); leather browns A (darkest) .. E (lit)
IRON = [(0x18, 0x18, 0x18), (0x44, 0x44, 0x44), (0x6b, 0x6b, 0x6b), (0x96, 0x96, 0x96), (0xbe, 0xbe, 0xbe), (0xd8, 0xd8, 0xd8), (0xff, 0xff, 0xff)]
LEATHER = {"A": (0x35, 0x1e, 0x10), "B": (0x4f, 0x2f, 0x18), "C": (0x6e, 0x43, 0x24), "D": (0x8a, 0x58, 0x2e), "E": (0xa8, 0x72, 0x3e)}

# (first column, shades left to right) per row: the blade (iron steps) leaves the cuff at the top right
HIDDEN_BLADE = {
    1: (14, "4"),
    2: (12, "642"),
    3: (11, "542"),
    4: (10, "542"),
    5: (9, "542"),
    6: (8, "542"),
    7: (7, "542"),
    8: (4, "DC32BA"),
    9: (3, "EDCCBA"),
    10: (2, "EDCCBA"),
    11: (1, "EDCCBA"),
    12: (0, "EDCCBA"),
    13: (0, "DCCBA"),
    14: (1, "CBA"),
}
# the two straps across the cuff (cells where x - y is -6 and -9), a buckle on the first
STRAPS = ((3, 9), (4, 10), (5, 11), (1, 10), (2, 11), (3, 12))


def hidden_blade():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y, (x0, shades) in HIDDEN_BLADE.items():
        for i, ch in enumerate(shades):
            px[x0 + i, y] = (*IRON[int(ch)], 255) if ch.isdigit() else (*LEATHER[ch], 255)
    for x, y in STRAPS:
        px[x, y] = (*LEATHER["A"], 255)
    px[4, 10] = (*IRON[4], 255)
    os.makedirs(OUT, exist_ok=True)
    img.save(os.path.join(OUT, "hidden_blade.png"))


def plot_stake():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    # the stake: a diagonal shaft from the top right down to a point at the bottom left
    for i in range(12):
        x, y = 13 - i, 2 + i
        px[x, y] = WOOD
        if x - 1 >= 0:
            px[x - 1, y] = WOOD_DARK
        if y - 1 >= 0 and i < 11:
            px[x, y - 1] = WOOD_LIGHT
    px[1, 14] = WOOD_DARK
    px[0, 15] = WOOD_DARK
    # the cap at the top
    px[14, 1] = WOOD_DARK
    px[13, 1] = WOOD_LIGHT
    # the pennant, tied on with a twist of string
    px[11, 4] = STRING
    px[10, 3] = STRING
    for y, (x0, x1) in enumerate([(5, 9), (4, 9), (5, 8), (6, 8), (7, 8)]):
        for x in range(x0, x1 + 1):
            px[x, 1 + y] = CLOTH if (x + y) % 3 else CLOTH_DARK
    os.makedirs(OUT, exist_ok=True)
    img.save(os.path.join(OUT, "plot_stake.png"))


# dynamite: red paper (TNT's reds) shadow .. glint, its paper band and cap, the fuse cord, the spark
DYN_RED = [(0x4a, 0x0e, 0x09), (0x7a, 0x18, 0x10), (0xa3, 0x24, 0x17), (0xc4, 0x33, 0x20), (0xdc, 0x4c, 0x2e), (0xef, 0x78, 0x58)]
DYN_PAPER = [(0x6f, 0x67, 0x55), (0x92, 0x89, 0x72), (0xb8, 0xae, 0x94), (0xd2, 0xc9, 0xb1), (0xe6, 0xde, 0xc9), (0xf4, 0xee, 0xdd)]
CORD, CORD_DARK = (0x6e, 0x67, 0x58), (0x3c, 0x37, 0x30)
EMBER, SPARK, SPARK_HOT = (0xd8, 0x6a, 0x1a), (0xff, 0xb1, 0x2e), (0xff, 0xf4, 0x9a)


def dynamite():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    # the stick's axis, bottom left to top right, and its radius
    ax, ay, bx, by, r = 3.0, 13.0, 11.0, 5.0, 2.3
    length = math.hypot(bx - ax, by - ay)
    ux, uy = (bx - ax) / length, (by - ay) / length
    nx, ny = uy, -ux  # across the stick, toward the lit top-left edge
    for y in range(16):
        for x in range(16):
            cx, cy = x + 0.5, y + 0.5
            t = (cx - ax) * ux + (cy - ay) * uy
            s = (cx - ax) * nx + (cy - ay) * ny
            if not (-r <= s <= r and -0.8 <= t <= length + 0.6):
                continue
            f = (s + r) / (2 * r)
            i = 1 if f < 0.2 else 2 if f < 0.4 else 3 if f < 0.6 else 5 if f < 0.8 else 4
            if t > length - 0.3:
                ramp, i = DYN_PAPER, min(5, i + 1)  # the cap
            elif 0.42 * length <= t <= 0.62 * length:
                ramp = DYN_PAPER  # the band
            else:
                ramp = DYN_RED
            if t < -0.2:
                i = max(0, i - 2)  # the far end in shadow
            px[x, y] = ramp[i] + (255,)
    for x, y, c in ((12, 4, CORD), (12, 3, CORD_DARK), (13, 2, CORD), (13, 1, EMBER), (14, 1, SPARK), (14, 0, SPARK_HOT),
                    (12, 0, SPARK), (15, 2, SPARK)):
        px[x, y] = c + (255,)
    os.makedirs(OUT, exist_ok=True)
    img.save(os.path.join(OUT, "dynamite.png"))


GUI = os.path.join("src", "main", "resources", "assets", "jaymoskingdoms", "textures", "gui")


def awareness_eye():
    """awareness_eye (0.7.111): the stealth gauge, a 16x64 sheet of four 16x16 eyes (original art, vanilla GUI style:
    a dark outline, light from the top-left).
    frame 0: an empty eye, grey (a monster is noticing you; the fill is drawn over it from frame 3, bottom up)
    frame 1: half-closed, amber (a monster is searching for you)
    frame 2: wide open, red iris (a monster has spotted you)
    frame 3: the full eye, pale with an amber iris (the fill)
    """
    img = Image.new("RGBA", (16, 64), (0, 0, 0, 0))
    px = img.load()
    OUTLINE = (0x1a, 0x14, 0x14, 255)

    def half_height(x, h):
        # the almond: wide in the middle, pointed at the corners (x 1..14)
        t = (x - 7.5) / 6.8
        return h * max(0.0, 1.0 - t * t) ** 0.7

    def eye(frame, h, white, iris, pupil, lid=None):
        oy = frame * 16
        cy = 7.5
        for x in range(1, 15):
            hh = half_height(x, h)
            top = cy - hh
            bottom = cy + hh
            for y in range(16):
                if y < top - 0.6 or y > bottom + 0.6:
                    continue
                inside = top + 0.5 <= y <= bottom - 0.5
                if lid is not None and y < lid:
                    # the lid comes down over the upper half
                    px[x, oy + y] = OUTLINE if y >= lid - 1 and inside else (0, 0, 0, 0)
                    continue
                px[x, oy + y] = white if inside else OUTLINE
        if lid is not None:
            for x in range(1, 15):
                if half_height(x, h) > 0.6:
                    px[x, oy + int(lid) - 1] = OUTLINE
        # the iris and pupil, a round disc at the middle (clipped to the eye)
        for y in range(16):
            for x in range(16):
                d = ((x - 7.5) ** 2 + (y - cy) ** 2) ** 0.5
                if px[x, oy + y][3] != 255 or px[x, oy + y] == OUTLINE:
                    continue
                if d <= 1.6:
                    px[x, oy + y] = pupil
                elif d <= 3.4:
                    shade = iris if (x + y) % 5 else tuple(max(0, c - 28) for c in iris[:3]) + (255,)
                    px[x, oy + y] = shade
        # a glint on the cornea, top-left of the pupil
        gx, gy = 6, int(cy) - 2
        if lid is None or gy >= lid:
            if px[gx, oy + gy][3] == 255 and px[gx, oy + gy] != OUTLINE:
                px[gx, oy + gy] = (0xff, 0xff, 0xff, 255)

    eye(0, 5.2, (0x4a, 0x4a, 0x50, 255), (0x35, 0x35, 0x3b, 255), (0x22, 0x22, 0x26, 255))
    eye(1, 5.2, (0xe6, 0xdc, 0xc0, 255), (0xd9, 0x9a, 0x2a, 255), (0x2a, 0x1a, 0x10, 255), lid=8)
    eye(2, 5.8, (0xee, 0xe6, 0xd6, 255), (0xc0, 0x1e, 0x1e, 255), (0x1a, 0x08, 0x08, 255))
    eye(3, 5.2, (0xf2, 0xea, 0xd0, 255), (0xe0, 0xa2, 0x2e, 255), (0x2a, 0x1a, 0x10, 255))
    os.makedirs(GUI, exist_ok=True)
    img.save(os.path.join(GUI, "awareness_eye.png"))


def horse_handbook():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    cover = [(0x3a, 0x22, 0x12), (0x5a, 0x36, 0x1c), (0x74, 0x47, 0x25), (0x8c, 0x58, 0x2f), (0xa2, 0x6a, 0x3a)]
    page = [(0x9a, 0x90, 0x78), (0xc8, 0xbe, 0xa2), (0xe8, 0xe0, 0xc8)]
    gold = [(0x8a, 0x5c, 0x10), (0xc8, 0x92, 0x1e), (0xf0, 0xc8, 0x40), (0xff, 0xec, 0x90)]
    x0, x1, y0, y1 = 2, 12, 1, 14
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            edge = x in (x0, x1) or y in (y0, y1)
            shade = 0 if edge else 3 if x <= x0 + 1 or y <= y0 + 1 else 2
            if x == x0 + 1 and not edge:
                shade = 1  # the spine's crease
            px[x, y] = cover[shade] + (255,)
    # the pages' edge down the right, under the cover's lip
    for y in range(y0 + 1, y1 + 1):
        px[x1 + 1, y] = page[1 if y % 2 else 2] + (255,)
        px[x1 + 2, y] = page[0] + (255,) if y < y1 else (0, 0, 0, 0)
    px[x1 + 1, y1] = page[0] + (255,)
    # the horseshoe, open end down
    shoe = ["  .....  ",
            " ..   .. ",
            " .     . ",
            ".       .",
            ".       .",
            ".       .",
            "..     ..",
            " .     . "]
    sx, sy = 3, 4
    for j, row in enumerate(shoe):
        for i, c in enumerate(row):
            if c == ".":
                lit = 3 if j <= 1 and i <= 4 else 2 if i <= 4 else 1
                px[sx + i, sy + j] = gold[lit] + (255,)
    # nail holes
    for (i, j) in ((1, 3), (7, 3), (1, 5), (7, 5)):
        px[sx + i, sy + j] = gold[0] + (255,)
    img.save(os.path.join(OUT, "horse_handbook.png"))


if __name__ == "__main__":
    horse_handbook()
    plot_stake()
    hidden_blade()
    dynamite()
    awareness_eye()
    print("drew", OUT, GUI)
