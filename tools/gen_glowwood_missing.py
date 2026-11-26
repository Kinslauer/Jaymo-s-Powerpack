"""Draws the glow-wood (inkcap, glowcap, bouncecap) textures that the glow-woods' models name but no file supplied, so
their mushroom trees, planks and items stopped rendering as purple and black squares. Also the snail slime icon.

    python3 -I tools/gen_glowwood_missing.py

Everything is drawn from the fungi's own art in textures/block/fungus (Spore & Stem, Apache-2.0): the stems and caps are
copied to the names the models use, and the planks, stripped stems, hanging gills and item icons are drawn in their
colours. Re-running overwrites the same files.
"""
import random
import shutil
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent / "kingdoms/src/main/resources/assets/jaymoskingdoms/textures"
BLOCK, ITEM, FUNGUS = ROOT / "block", ROOT / "item", ROOT / "block/fungus"
KINDS = ["inkcap", "glowcap", "bouncecap"]


def load(path):
    return Image.open(path).convert("RGBA")


def palette(img):
    """The image's opaque colours, darkest first (by brightness)."""
    cols = {c for c in img.getdata() if c[3] > 0}
    return sorted(cols, key=lambda c: c[0] * 0.3 + c[1] * 0.59 + c[2] * 0.11)


def mix(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)


def shade(c, f):
    return tuple(max(0, min(255, round(v * f))) for v in c[:3]) + (255,)


def planks(stem, rnd):
    """Four boards of the stem's flesh, joints staggered, a darker seam under each, a little grain."""
    pal = palette(stem)
    dark, mid, light = pal[len(pal) // 5], pal[len(pal) // 2], pal[-1]
    base = mix(mid, light, 0.35)
    img = Image.new("RGBA", (16, 16))
    for board in range(4):
        tint = 1.0 + (rnd.random() - 0.5) * 0.08
        joint = (board * 7 + 3) % 16
        for y in range(board * 4, board * 4 + 4):
            for x in range(16):
                c = shade(base, tint)
                if y == board * 4 + 3:
                    c = shade(mix(dark, mid, 0.4), tint)
                elif x == joint:
                    c = shade(mix(dark, mid, 0.6), tint)
                elif rnd.random() < 0.12:
                    c = shade(mix(mid, base, 0.5), tint)
                img.putpixel((x, y), c)
    return img


def stripped(stem):
    """The stem peeled: its streaks softened toward its palest flesh."""
    light = palette(stem)[-1]
    img = stem.copy()
    for y in range(16):
        for x in range(16):
            c = img.getpixel((x, y))
            if c[3]:
                img.putpixel((x, y), mix(c, light, 0.45))
    return img


def hanging(gills, rnd, tips):
    """Gill threads hanging from the top edge (cut out), for the cross models under a glowcap's cap."""
    pal = palette(gills)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for x in range(1, 15):
        if rnd.random() < 0.35:
            continue
        length = rnd.randint(4, 9) if tips else rnd.randint(9, 16)
        for y in range(length):
            img.putpixel((x, y), pal[min(len(pal) - 1, len(pal) // 3 + y * len(pal) // 24)])
        img.putpixel((x, length - 1), pal[-1])
    return img


def mushroom_icon(cap, cap_side, stem):
    """A small mushroom: a domed cap in the cap's colours over a pale stem."""
    cp, sp, stp = palette(cap), palette(cap_side), palette(stem)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    rows = [(6, 9), (4, 11), (3, 12), (3, 12), (3, 12)]
    for i, (a, b) in enumerate(rows):
        y = 3 + i
        for x in range(a, b + 1):
            if i < 2:
                c = cp[-1] if (x + y) % 5 == 0 else cp[len(cp) * 2 // 3]
            else:
                c = sp[len(sp) // 2] if x in (a, b) else cp[len(cp) // 2]
            img.putpixel((x, y), c)
    for x in range(4, 12):
        img.putpixel((x, 8), sp[1])
    for y in range(9, 14):
        for x in (7, 8):
            img.putpixel((x, y), stp[-1] if x == 7 else stp[len(stp) // 2])
    for x in range(6, 10):
        img.putpixel((x, 14), stp[len(stp) // 3])
    return img


def door_icon(kind):
    """The door's two halves, halved, one over the other (as the game draws its door items)."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    top = load(BLOCK / f"{kind}_door_top.png").resize((8, 8), Image.NEAREST)
    bottom = load(BLOCK / f"{kind}_door_bottom.png").resize((8, 8), Image.NEAREST)
    img.paste(top, (4, 0), top)
    img.paste(bottom, (4, 8), bottom)
    return img


def lantern_icon():
    side = load(BLOCK / "glowcap_lantern_side.png")
    plate = palette(load(BLOCK / "glowcap_lantern_plate.png"))
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    body = side.resize((6, 8), Image.NEAREST)
    img.paste(body, (5, 5), body)
    for x in range(4, 12):
        img.putpixel((x, 4), plate[len(plate) // 2])
        img.putpixel((x, 13), plate[len(plate) // 3])
    for x, y in ((6, 3), (9, 3), (6, 2), (9, 2), (7, 1), (8, 1)):
        img.putpixel((x, y), plate[1])
    return img


def snail_slime(rnd):
    """A glistening smear of snail slime."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    deep, body, light, shine = (82, 132, 74, 230), (118, 190, 109, 215), (168, 222, 150, 210), (236, 255, 228, 240)
    cx, cy = 7.5, 8.5
    for y in range(16):
        for x in range(16):
            d = ((x - cx) / 6.0) ** 2 + ((y - cy) / 4.2) ** 2
            if d > 1.0 - rnd.random() * 0.15:
                continue
            img.putpixel((x, y), deep if d > 0.7 else body if d > 0.3 else light)
    for x, y in ((5, 7), (6, 6), (7, 6), (10, 9)):
        img.putpixel((x, y), shine)
    for x, y in ((3, 12), (12, 4), (13, 5)):
        img.putpixel((x, y), body)
    return img


def main():
    rnd = random.Random(2179)
    for kind in KINDS:
        stem = load(FUNGUS / f"{kind}_stem.png")
        shutil.copy(FUNGUS / f"{kind}_stem.png", BLOCK / f"{kind}_stem.png")
        shutil.copy(FUNGUS / f"{kind}_cap.png", BLOCK / f"{kind}_cap.png")
        shutil.copy(FUNGUS / f"{kind}_gills.png", BLOCK / f"{kind}_cap_inside.png")
        stripped(stem).save(BLOCK / f"stripped_{kind}_stem.png")
        planks(stem, rnd).save(BLOCK / f"{kind}_planks.png")
        mushroom_icon(load(FUNGUS / f"{kind}_cap.png"), load(FUNGUS / f"{kind}_cap_side.png"), stem).save(ITEM / f"{kind}.png")
        door_icon(kind).save(ITEM / f"{kind}_door.png")
    gills = load(FUNGUS / "glowcap_gills.png")
    hanging(gills, rnd, False).save(BLOCK / "glowcap_gills.png")
    hanging(gills, rnd, True).save(BLOCK / "glowcap_gills_tip.png")
    lantern_icon().save(ITEM / "glowcap_lantern.png")
    snail_slime(rnd).save(ITEM / "snail_slime.png")
    print("drawn")


if __name__ == "__main__":
    main()
