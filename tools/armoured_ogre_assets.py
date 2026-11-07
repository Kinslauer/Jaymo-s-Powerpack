#!/usr/bin/env python3
"""Kingdoms 0.7.623 (Rivals patch 8): the armoured ogre's skins.

Each is 128x256. The top half is the green ogre's skin (Mo' Creatures, GPL-3, as the rest of the ogre) with its green
recoloured to one of the half-orc skin tones (GreenskinLife.TONES). The bottom half is our own plate and shield, laid
out for ArmouredOgreModel's cubes (the UVs below must match it).

Run from the repo root: python3 tools/armoured_ogre_assets.py
"""
import random
from PIL import Image

ROOT = "kingdoms/src/main/resources/assets/jaymoskingdoms/textures/entity/legend/"
# lime, orc green, moss, grey-green, ruddy brown, umber (GreenskinLife.TONES 2, 3, 4, 5, 7, 8)
TONES = [0x93B256, 0x6F8C45, 0x56703A, 0x7C8572, 0x845A3E, 0x5C4430]
REF_LUM = 87.0

# (name, u, v, w, h, d): every armour cube, as ArmouredOgreModel lays it out
CUBES = {
    "chest": (0, 128, 19, 11, 13),
    "stomach": (0, 152, 22, 11, 14),
    "pauldron_l": (64, 128, 8, 7, 8),
    "pauldron_r": (96, 128, 8, 7, 8),
    "vambrace_l": (72, 152, 8, 12, 8),
    "vambrace_r": (72, 172, 8, 12, 8),
    "greave_l": (0, 177, 8, 11, 8),
    "greave_r": (32, 177, 8, 11, 8),
    "helm": (0, 196, 12, 5, 12),
    "nasal": (48, 196, 2, 4, 1),
    "shield": (0, 213, 16, 22, 2),
    "boss": (36, 213, 4, 4, 2),
}


def lum(c):
    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]


def rgb(hexv):
    return ((hexv >> 16) & 255, (hexv >> 8) & 255, hexv & 255)


def skin(base, tone):
    t = rgb(tone)
    out = base.copy()
    px = out.load()
    for y in range(base.height):
        for x in range(base.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            if g > r * 1.4 and g > b * 1.15 and g > 30:
                k = lum((r, g, b)) / REF_LUM
                px[x, y] = tuple(max(0, min(255, int(c * k))) for c in t) + (a,)
    return out


def faces(u, v, w, h, d):
    """The six faces of a cube's UV box: (x, y, width, height)."""
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + 2 * d + w, v + d, w, h),
    }


def fill(px, rect, shade, rng, edge=True, rivets=True):
    x0, y0, w, h = rect
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            n = rng.randint(-9, 9)
            c = shade(x - x0, y - y0, w, h)
            c = [max(0, min(255, ch + n)) for ch in c]
            if edge and (x == x0 or y == y0 or x == x0 + w - 1 or y == y0 + h - 1):
                c = [int(ch * 0.62) for ch in c]
            elif edge and (x == x0 + 1 or y == y0 + 1) and w > 3 and h > 3:
                c = [min(255, int(ch * 1.18)) for ch in c]
            px[x, y] = tuple(c) + (255,)
    if rivets and w >= 6 and h >= 6:
        for rx, ry in ((x0 + 2, y0 + 2), (x0 + w - 3, y0 + 2), (x0 + 2, y0 + h - 3), (x0 + w - 3, y0 + h - 3)):
            px[rx, ry] = (205, 205, 200, 255)


def steel(dx, dy, w, h):
    # dull blackened iron, lighter toward the top, a band across the middle of the bigger plates
    base = 92 - int(26 * dy / max(1, h))
    if h >= 10 and abs(dy - h // 2) <= 0:
        base -= 22
    return [base, base + 2, base + 6]


def rusty(dx, dy, w, h):
    c = steel(dx, dy, w, h)
    if (dx * 7 + dy * 13) % 11 == 0:
        return [c[0] + 30, c[1] + 6, c[2] - 10]
    return c


def wood(dx, dy, w, h):
    # dark planks, a seam every four pixels
    shade = 70 if dx % 4 == 0 else 92 + (dx * 5 % 7)
    return [shade, int(shade * 0.62), int(shade * 0.38)]


def paint_armour(px, rng):
    for name, (u, v, w, h, d) in CUBES.items():
        for side, rect in faces(u, v, w, h, d).items():
            if rect[2] <= 0 or rect[3] <= 0:
                continue
            if name == "shield":
                if side == "front":
                    fill(px, rect, wood, rng, edge=False, rivets=False)
                    x0, y0, fw, fh = rect
                    # an iron rim, and a red tusk mark of the orc camps across it
                    for y in range(y0, y0 + fh):
                        for x in range(x0, x0 + fw):
                            if x < x0 + 1 or x > x0 + fw - 2 or y < y0 + 1 or y > y0 + fh - 2:
                                px[x, y] = (58, 58, 62, 255)
                    for i in range(fh - 6):
                        for off in (-1, 0):
                            xl = x0 + 3 + i * (fw - 8) // (fh - 6) + off
                            xr = x0 + fw - 4 - i * (fw - 8) // (fh - 6) + off
                            for xx in (xl, xr):
                                if x0 + 1 < xx < x0 + fw - 2:
                                    px[xx, y0 + 3 + i] = (128, 24, 20, 255)
                else:
                    fill(px, rect, wood if side == "back" else steel, rng)
            elif name == "boss":
                fill(px, rect, lambda dx, dy, w, h: [150, 150, 146], rng, rivets=False)
            else:
                fill(px, rect, rusty if name in ("stomach", "greave_l", "greave_r") else steel, rng)


def main():
    base = Image.open(ROOT + "ogre_green.png").convert("RGBA")
    rng = random.Random(623)
    armour = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    shifted = Image.new("RGBA", (128, 256), (0, 0, 0, 0))
    shifted.paste(armour, (0, 128))
    paint_armour(shifted.load(), rng)
    plate = shifted.crop((0, 128, 128, 256))
    for i, tone in enumerate(TONES):
        out = Image.new("RGBA", (128, 256), (0, 0, 0, 0))
        out.paste(skin(base, tone), (0, 0))
        out.paste(plate, (0, 128))
        out.save(ROOT + "ogre_armoured_%d.png" % i)
    print("wrote %d skins" % len(TONES))


if __name__ == "__main__":
    main()
