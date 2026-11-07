"""Writes the Banes' weapons' art and models (Kingdoms Rivals patch 6b; our own pixel art, drawn here): the Gravesworn,
the Grudge and the Hornscythe (armory/BaneWeapons, 32x32 like the Armory's). Their iron heads are drawn by
weapon_parts_assets.py's DRAWN.

    python -I tools/bane_weapons_assets.py [preview.png]
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import patron_weapons_assets as pwa  # noqa: E402
from patron_weapons_assets import canvas, thick, outline, shine, hexc, STEEL, IRON, IRON_LIGHT, WOOD_DARK, LEATHER  # noqa: E402

BONE, GRAVE, SOUL = hexc("#D8D0B8"), hexc("#4A4E58"), hexc("#5FE0E8")
RUST, BLOOD = hexc("#8A4A2A"), hexc("#9A1A1A")
HORN, CINDER, FIRE = hexc("#3A2A22"), hexc("#5A2020"), hexc("#FF6A1A")


def gravesworn():
    """A long black blade with a bone grip and a soul-light down its fuller."""
    img, d = canvas()
    thick(d, (3, 29), (8, 24), 3, BONE)
    d.polygon([(4, 20), (6, 18), (14, 26), (12, 28)], fill=GRAVE)  # a heavy crossguard
    thick(d, (9, 23), (27, 5), 4, GRAVE)
    d.polygon([(26, 3), (30, 2), (29, 6)], fill=GRAVE)
    thick(d, (11, 21), (26, 6), 1, SOUL)  # the cold light in it
    d.point([(9, 23)], fill=SOUL)
    img = outline(img, 0.35)
    return img


def grudge():
    """A rough bearded axe, a notch for every name it took, its haft wrapped red."""
    img, d = canvas()
    thick(d, (4, 29), (20, 9), 3, WOOD_DARK)
    for t in (0.15, 0.3):
        x, y = int(4 + 16 * t), int(29 - 20 * t)
        thick(d, (x - 1, y - 1), (x + 1, y + 1), 2, BLOOD)
    d.polygon([(16, 6), (22, 3), (29, 8), (30, 18), (25, 20), (20, 13)], fill=STEEL)  # the bearded head
    d.line([(29, 9), (29, 17)], fill=IRON_LIGHT)
    d.point([(24, 6), (26, 8), (28, 10)], fill=RUST)  # the notches
    img = outline(img, 0.35)
    shine(img, STEEL, IRON_LIGHT)
    return img


def hornscythe():
    """A scythe whose blade is a black horn, cinders glowing in its cracks."""
    img, d = canvas()
    thick(d, (5, 30), (21, 6), 2, HORN)
    thick(d, (6, 29), (8, 26), 3, LEATHER)
    d.arc([10, 1, 31, 22], 190, 340, fill=CINDER, width=3)  # the horn blade
    d.line([(21, 6), (16, 4)], fill=CINDER, width=2)
    d.point([(14, 6), (19, 3), (25, 3), (29, 6)], fill=FIRE)
    img = outline(img, 0.3)
    return img


def main(preview=None):
    old = pwa.WEAPONS, pwa.HEADS
    pwa.WEAPONS = {"gravesworn": gravesworn, "grudge": grudge, "hornscythe": hornscythe}
    pwa.HEADS = ["gravesworn_blade", "grudge_head", "hornscythe_blade"]
    try:
        pwa.main(None)
        if preview:
            from PIL import Image
            sheet = Image.new("RGBA", (96, 32), (60, 90, 120, 255))
            for i, fn in enumerate(pwa.WEAPONS.values()):
                sheet.alpha_composite(fn(), (i * 32, 0))
            sheet.resize((384, 128), Image.NEAREST).save(preview)
    finally:
        pwa.WEAPONS, pwa.HEADS = old


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else None)
