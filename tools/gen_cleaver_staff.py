"""Redraws the Armory's cleavers (short: a broad chopping blade on a stubby grip) and quarterstaffs (long: the stick
runs the whole diagonal of the sprite) in place of the Age of Weapons sprites tools/armory_assets.py copies, so the
staff reads as a long weapon and the cleaver as a short one (Andy, 0.9.0). Run it after armory_assets.py:

    python -I tools/gen_cleaver_staff.py
"""
import os
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "jaymospowers", "textures", "item", "armory")
SIZE = 32

# each tier's blade: highlight, light, mid, dark, outline (the tones of its AoW sprite)
BLADES = {
    "wooden": ((134, 101, 38), (117, 88, 33), (91, 68, 26), (55, 41, 16), (43, 32, 12)),
    "stone": ((154, 154, 154), (137, 137, 137), (119, 119, 119), (73, 73, 73), (60, 60, 60)),
    "iron": ((255, 255, 255), (216, 216, 216), (168, 168, 168), (68, 68, 68), (40, 40, 40)),
    "golden": ((255, 255, 255), (253, 255, 118), (234, 238, 87), (233, 177, 21), (63, 46, 14)),
    "diamond": ((51, 235, 203), (43, 199, 172), (35, 160, 137), (14, 63, 54), (8, 37, 32)),
    "netherite": ((134, 123, 134), (112, 103, 112), (81, 68, 78), (50, 39, 39), (35, 16, 18)),
}
# the grip: light, mid, dark, outline
WOOD = ((137, 103, 39), (104, 78, 30), (73, 54, 21), (40, 30, 11))
STAFFS = {
    "wooden_quarterstaff": {"stick": ((126, 92, 28), (62, 43, 19), (29, 19, 11)), "band": ((68, 68, 68), (40, 40, 40))},
    "iron_quarterstaff": {"stick": ((94, 94, 94), (45, 45, 45), (21, 21, 21)), "band": ((73, 54, 21), (40, 30, 11))},
}


def diagonal(x, y):
    """A pixel's place along the bottom-left to top-right diagonal (0..62) and across it (-31..31)."""
    return x + (SIZE - 1 - y), x - (SIZE - 1 - y)


def cleaver(tier, path):
    """A short cleaver: a stubby wooden grip (with a rivet) and a broad, squared-off blade that fills about two
    thirds of the sprite's diagonal, its edge on the lower-right side, a hole by the spine near the tip."""
    hi, light, mid, dark, outline = BLADES[tier]
    im = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))

    def inside(p, q):
        if 2 <= p <= 13 and -1 <= q <= 1:  # the grip
            return "grip"
        if 12 <= p <= 15 and -3 <= q <= 3:  # the bolster
            return "bolster"
        if 15 <= p <= 38 and -11 <= q <= 3:  # the blade
            if p >= 36 and q <= -9:  # a clipped spine corner at the tip
                return None
            if p <= 17 and q <= -9:  # and at the heel
                return None
            if 30 <= p <= 32 and -8 <= q <= -6:  # the hanging hole
                return "hole"
            return "blade"
        return None

    for y in range(SIZE):
        for x in range(SIZE):
            p, q = diagonal(x, y)
            part = inside(p, q)
            if part is None:
                continue
            edge = any(inside(p + dp, q + dq) is None for dp, dq in ((1, 1), (1, -1), (-1, 1), (-1, -1)))
            if part == "grip":
                colour = WOOD[3] if edge else (WOOD[0] if q < 0 else WOOD[1])
                if p in (6, 7) and q == 0:
                    colour = BLADES[tier][3]  # the rivet
            elif part == "bolster":
                colour = outline if edge else dark
            elif part == "hole":
                colour = outline
            else:
                if edge:
                    colour = outline if q < 2 else light  # the edge itself catches the light
                elif q >= 1:
                    colour = hi
                elif q >= -2:
                    colour = light
                elif q >= -8:
                    colour = mid if (p + q) % 6 else light  # a faint sheen
                else:
                    colour = dark
            im.putpixel((x, y), colour + (255,))
    im.save(path)


def quarterstaff(name, path):
    """A long quarterstaff: a three-pixel stick from the bottom-left corner to the top-right, with a band at either
    end and a wrapped grip round the middle."""
    stick = STAFFS[name]["stick"]
    band = STAFFS[name]["band"]
    im = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    for y in range(SIZE):
        x0 = SIZE - 1 - y
        for i in range(3):
            x = x0 + i - 1
            if 0 <= x < SIZE:
                if y <= 2 or y >= SIZE - 3 or 14 <= y <= 17:  # the caps and the grip
                    colour = band[0] if i == 0 else band[1]
                else:
                    colour = stick[i]
                im.putpixel((x, y), colour + (255,))
    im.save(path)


def main():
    for tier in BLADES:
        cleaver(tier, os.path.join(OUT, f"{tier}_cleaver.png"))
    for name in STAFFS:
        quarterstaff(name, os.path.join(OUT, f"{name}.png"))


if __name__ == "__main__":
    main()
