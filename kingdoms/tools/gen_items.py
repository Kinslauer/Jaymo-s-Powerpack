"""Draws Jaymo's Kingdoms' item sprites (original art). Run from kingdoms/: python -I tools/gen_items.py

plot_stake: a pointed wooden stake with a little red pennant tied on near the top.
awareness_eye (0.7.103): the stealth gauge's eye, four frames.
hidden_blade (0.7.101): a brown leather bracer with a spring blade run out of it, vanilla style (handle bottom-left, blade
top-right, no black outlines, light from the top-left, vanilla's own iron ramp).
"""
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


GUI = os.path.join("src", "main", "resources", "assets", "jaymoskingdoms", "textures", "gui")


def awareness_eye():
    """awareness_eye (0.7.103): the stealth gauge, a 16x64 sheet of four 16x16 eyes (original art, vanilla GUI style:
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


if __name__ == "__main__":
    plot_stake()
    hidden_blade()
    awareness_eye()
    print("drew", OUT, GUI)
