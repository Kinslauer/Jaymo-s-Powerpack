"""Writes the Rig frames' and Rig limbs' art (our own, drawn here):

    python -I tools/gen_rig_frames.py

- the Scrap, Shade and Siege suit textures and their glow passes (colour changes of the standard suit's, so every plate
  and UV stays where `tools/gen_rig.py` put it), and `suit_trim.png`, the trim plates alone, which the client tints with
  the trim paint picked at the Forge;
- the Rig arm and leg: the item icons, their item models and definitions, and the worn limb's texture;
- the Forge hologram's scanline texture.

Run `tools/gen_rig.py` first if the suit's plates change (this reads its `suit.png` and boxes).
"""
import json
import os
import random
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_rig  # noqa: E402

ASSETS = gen_rig.ASSETS
RIG = os.path.join(ASSETS, "textures", "entity", "rig")
PLATE, PLATE_HI, PLATE_LO, SEAM = gen_rig.PLATE, gen_rig.PLATE_HI, gen_rig.PLATE_LO, gen_rig.SEAM
GLOW, GLOW_DIM = gen_rig.GLOW, gen_rig.GLOW_DIM


def clamp(v):
    return max(0, min(255, int(v)))


def tinted(im, mul, rng, rust=0.0, scorch=0.0):
    """The suit's pixels multiplied by a colour, with a little rust and scorching sprinkled over."""
    out = im.copy()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = im.getpixel((x, y))
            if a == 0:
                continue
            r, g, b = clamp(r * mul[0]), clamp(g * mul[1]), clamp(b * mul[2])
            roll = rng.random()
            if roll < rust:
                r, g, b = gen_rig.RUST
            elif roll < rust + scorch:
                r, g, b = gen_rig.SCORCH
            out.putpixel((x, y), (r, g, b, a))
    return out


def recoloured_glow(im, mapping):
    out = im.copy()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = im.getpixel((x, y))
            if a == 0:
                continue
            out.putpixel((x, y), tuple(clamp(v) for v in mapping(r, g, b)) + (a,))
    return out


def suit_frames():
    suit = Image.open(os.path.join(RIG, "suit.png")).convert("RGBA")
    glow = Image.open(os.path.join(RIG, "suit_glow.png")).convert("RGBA")
    rng = random.Random(0xF4A3)
    # scrap: warm, pitted, patched
    tinted(suit, (1.12, 0.92, 0.72), rng, rust=0.14, scorch=0.05).save(os.path.join(RIG, "suit_scrap.png"))
    recoloured_glow(glow, lambda r, g, b: (b, g * 0.68, r * 0.5)).save(os.path.join(RIG, "suit_glow_scrap.png"))
    # shade: dark blue-black, a violet glow
    tinted(suit, (0.5, 0.54, 0.74), rng).save(os.path.join(RIG, "suit_shade.png"))
    recoloured_glow(glow, lambda r, g, b: (b * 0.62, r * 1.3, b)).save(os.path.join(RIG, "suit_glow_shade.png"))
    # siege: drab green armour, a hot orange glow
    tinted(suit, (0.84, 1.0, 0.72), rng, scorch=0.03).save(os.path.join(RIG, "suit_siege.png"))
    recoloured_glow(glow, lambda r, g, b: (b, g * 0.45, r * 0.4)).save(os.path.join(RIG, "suit_glow_siege.png"))
    # the trim alone, in plain light grey for the client to tint
    gen_rig.suit_pack()
    trim = Image.new("RGBA", suit.size, (0, 0, 0, 0))
    for _, box in gen_rig.suit_boxes():
        if box["paint"] not in ("trim", "ridge"):
            continue
        w, h, d = box["size"]
        u, v = box["uv"]
        for _face, (fx, fy, fw, fh) in gen_rig.box_faces(u, v, w, h, d).items():
            for y in range(fh):
                for x in range(fw):
                    shade = 255 if y == 0 else 215
                    trim.putpixel((fx + x, fy + y), (shade, shade, shade, 255))
    trim.save(os.path.join(RIG, "suit_trim.png"))


def hologram():
    """A scanline wash for the Forge hologram: bright lines, dim between, a brighter edge on every 16th row."""
    size = 64
    im = Image.new("RGBA", (size, size), (255, 255, 255, 255))
    for y in range(size):
        alpha = 235 if y % 4 == 0 else 150 if y % 2 == 0 else 95
        if y % 16 == 0:
            alpha = 255
        for x in range(size):
            edge = 20 if x % 16 == 0 else 0
            im.putpixel((x, y), (255, 255, 255, min(255, alpha + edge)))
    im.save(os.path.join(RIG, "holo.png"))


def limb_texture():
    """The worn Rig limb: a gunmetal 4x12x4 box (the 16x16 net of the vanilla limb box), a lit seam down its front."""
    rng = random.Random(0xA11B)
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            if y < 4 and (x < 4 or x >= 12):
                continue
            base = PLATE_HI if y in (0, 4) else PLATE_LO if y in (3, 15) else PLATE
            if y >= 4 and y % 4 == 3:
                base = SEAM
            jit = rng.randint(-4, 4)
            c = tuple(clamp(v + jit) for v in base)
            im.putpixel((x, y), c + (255,))
    # the front face (x 4..7, y 4..15): a lit seam
    for y in range(5, 15):
        if y % 4 != 3:
            im.putpixel((5, y), GLOW + (255,))
            im.putpixel((6, y), GLOW_DIM + (255,))
    # the cuff / hip ring
    for x in range(16):
        if x < 4 or x >= 4:
            im.putpixel((x, 14), PLATE_HI + (255,)) if 4 <= x <= 7 else None
    folder = os.path.join(ASSETS, "textures", "entity", "wounds")
    gen_rig.save(im, os.path.join(folder, "rig_limb.png"))


def icon(arm):
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    rng = random.Random(0x1C0 + (1 if arm else 2))
    if arm:
        # an arm laid on the diagonal, a lit palm at its end
        for i in range(11):
            for t in range(-2, 3):
                x, y = 2 + i + (1 if t > 0 else 0), 13 - i + (t if t != 0 else 0) // 2 * 0
                x2, y2 = 2 + i, 13 - i + t
                if 0 <= x2 < 16 and 0 <= y2 < 16:
                    base = PLATE_HI if t == -2 else PLATE_LO if t == 2 else PLATE
                    im.putpixel((x2, y2), tuple(clamp(v + rng.randint(-4, 4)) for v in base) + (255,))
        for (x, y) in ((12, 2), (13, 3), (12, 3), (13, 2)):
            im.putpixel((x, y), GLOW + (255,))
        im.putpixel((11, 2), GLOW_DIM + (255,))
    else:
        # a leg: thigh, knee, shin and a lit sole
        for y in range(2, 13):
            width = 4 if y < 8 else 3
            for x in range(6, 6 + width):
                base = PLATE_HI if x == 6 else PLATE_LO if x == 5 + width else PLATE
                if y == 8:
                    base = SEAM
                im.putpixel((x, y), tuple(clamp(v + rng.randint(-4, 4)) for v in base) + (255,))
        for x in range(5, 11):
            im.putpixel((x, 13), PLATE_LO + (255,))
        for x in range(6, 10):
            im.putpixel((x, 14), GLOW + (255,))
    folder = os.path.join(ASSETS, "textures", "item", "prosthetic")
    gen_rig.save(im, os.path.join(folder, "rig_arm.png" if arm else "rig_leg.png"))


def item_files():
    for name in ("rig_arm", "rig_leg"):
        gen_rig.write(os.path.join(ASSETS, "models", "item", "prosthetic", name + ".json"),
                      {"parent": "minecraft:item/generated", "textures": {"layer0": "jaymospowers:item/prosthetic/" + name}})
        gen_rig.write(os.path.join(ASSETS, "items", name + ".json"),
                      {"model": {"type": "minecraft:model", "model": "jaymospowers:item/prosthetic/" + name}})


if __name__ == "__main__":
    suit_frames()
    hologram()
    limb_texture()
    icon(True)
    icon(False)
    item_files()
