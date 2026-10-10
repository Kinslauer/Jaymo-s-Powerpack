"""Generates the Void Crown: its 32x32 inventory icon, the worn layer (64x32 humanoid armour layout) and the item and
equipment JSON. A jagged circlet of void-black iron, violet light bleeding from its points, a slit eye on the brow.
Original art. Run from the project root."""
from PIL import Image
import json, math, os

ROOT = "src/main/resources/assets/jaymospowers"

VOID = (10, 5, 18)
DARK = (26, 13, 42)
IRON = (44, 26, 66)
EDGE = (70, 42, 104)
VIOLET = (118, 52, 196)
BRIGHT = (176, 112, 255)
GLOW = (228, 196, 255)


def px(img, x, y, c):
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), (*c, 255))


def outline(img):
    out = img.copy()
    for y in range(img.height):
        for x in range(img.width):
            if img.getpixel((x, y))[3]:
                continue
            for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
                if 0 <= nx < img.width and 0 <= ny < img.height and img.getpixel((nx, ny))[3]:
                    out.putpixel((x, y), (*VOID, 255))
                    break
    return out


# ---------------------------------------------------------------- the icon (32x32, seen from the front)
def icon():
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    band_top, band_bottom = 19, 25
    # jagged points: (centre x, height above the band, half width at the base)
    points = [(4, 7, 2.5), (9, 10, 2.5), (13, 6, 2.0), (16, 13, 3.0), (19, 6, 2.0), (23, 10, 2.5), (28, 7, 2.5)]
    for cx, h, hw in points:
        for y in range(band_top - h, band_top):
            t = (band_top - y) / h  # 0 at the base, 1 at the tip
            half = hw * (1.0 - t) + 0.35
            # a slight hook: the points lean outward from the middle
            lean = (cx - 16) * 0.05 * t * h / 4
            for x in range(32):
                d = x + 0.5 - (cx + lean)
                if abs(d) <= half:
                    c = IRON if d < 0 else DARK
                    if t > 0.72:
                        c = BRIGHT if t < 0.9 else GLOW
                    elif abs(d) > half - 0.8 and d > 0:
                        c = VOID
                    px(img, x, y, c)
    # the band, curving a little (the far side drops behind)
    for x in range(2, 30):
        sag = int(round(1.2 * ((x - 15.5) / 14) ** 2))
        for y in range(band_top + sag, band_bottom + sag):
            row = y - band_top - sag
            c = EDGE if row == 0 else VOID if row == band_bottom - band_top - 1 else IRON if (x + row) % 5 else DARK
            px(img, x, y, c)
    # violet seams leaking from the band, as if it were cracked onto something brighter
    for x, y in ((6, 22), (7, 23), (25, 21), (26, 22), (11, 23)):
        px(img, x, y, VIOLET)
    # the eye on the brow: an almond of light around a black slit
    ex, ey = 16, 22
    for y in range(ey - 3, ey + 4):
        for x in range(ex - 5, ex + 5):
            dx, dy = (x + 0.5 - ex) / 4.6, (y + 0.5 - ey) / 2.6
            if dx * dx + dy * dy <= 1.0:
                r = math.hypot(dx, dy * 1.4)
                px(img, x, y, GLOW if r < 0.35 else BRIGHT if r < 0.7 else VIOLET)
    # the slit: one pixel wide at its ends, two in the middle
    for y in range(ey - 2, ey + 2):
        px(img, ex - 1, y, VOID)
        if y in (ey - 1, ey):
            px(img, ex, y, VOID)
    # a rift splitting the tallest point
    for y, x in ((7, 16), (8, 16), (9, 15), (10, 16), (11, 16), (12, 17)):
        px(img, x, y, VIOLET if y > 8 else BRIGHT)
    return outline(img)


# ---------------------------------------------------------------- the worn layer (64x32)
# Head cube at (0,0): top (8..16, 0..8), sides on rows 8..16: right 0..8, front 8..16, left 16..24, back 24..32.
# The hat cube (a little larger) sits at (32,0) in the same layout.
def worn():
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    # head layer: the band, two rows below the crown of the head, with small teeth above it
    for x in range(32):
        for y in (10, 11, 12):
            row = y - 10
            c = EDGE if row == 0 else VOID if row == 2 else IRON if x % 3 else DARK
            px(img, x, y, c)
        if x % 4 in (1, 2):
            px(img, x, 9, IRON if x % 4 == 1 else DARK)
        if x % 4 == 1:
            px(img, x, 8, VIOLET)
    # violet seams in the band
    for x in (2, 5, 19, 22, 27, 30):
        px(img, x, 11, VIOLET)
    # the eye on the brow (front face, x 8..16)
    for x, c in ((10, VIOLET), (11, BRIGHT), (12, VOID), (13, BRIGHT), (14, VIOLET)):
        px(img, x, 11, c)
    px(img, 11, 10, VIOLET)
    px(img, 12, 10, GLOW)
    px(img, 13, 10, VIOLET)
    px(img, 12, 12, VIOLET)
    # the rim seen from above: a ring round the top face's edge
    for i in range(8):
        for x, y in ((8 + i, 0), (8 + i, 7), (8, i), (15, i)):
            px(img, x, y, DARK if (x + y) % 2 else IRON)
    # hat layer: the tall jagged points, standing a little off the head
    heights = [3, 2, 4, 2, 3, 2, 5, 2]  # per pair of columns around the head, from the right side round to the back
    for x in range(32):
        h = heights[(x // 2) % len(heights)]
        if 8 <= x < 16:
            h = [2, 3, 4, 6, 6, 4, 3, 2][x - 8]  # the front rises to a peak over the eye
        top = 12 - h
        for y in range(top, 12):
            c = (GLOW if h >= 4 else BRIGHT) if y == top else VIOLET if y == top + 1 and h >= 3 else IRON if x % 2 else DARK
            px(img, 32 + x, y, c)
    # the front peak is split by a rift
    px(img, 32 + 11, 7, VOID)
    px(img, 32 + 12, 8, VOID)
    px(img, 32 + 11, 9, VIOLET)
    return img


def main():
    os.makedirs(f"{ROOT}/textures/item", exist_ok=True)
    os.makedirs(f"{ROOT}/textures/entity/equipment/humanoid", exist_ok=True)
    os.makedirs(f"{ROOT}/equipment", exist_ok=True)
    icon().save(f"{ROOT}/textures/item/void_crown.png")
    worn().save(f"{ROOT}/textures/entity/equipment/humanoid/void_crown.png")
    json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "jaymospowers:item/void_crown"}},
              open(f"{ROOT}/models/item/void_crown.json", "w"), indent=2)
    json.dump({"model": {"type": "minecraft:model", "model": "jaymospowers:item/void_crown"}},
              open(f"{ROOT}/items/void_crown.json", "w"), indent=2)
    json.dump({"layers": {"humanoid": [{"texture": "jaymospowers:void_crown"}]}},
              open(f"{ROOT}/equipment/void_crown.json", "w"), indent=2)


if __name__ == "__main__":
    main()
