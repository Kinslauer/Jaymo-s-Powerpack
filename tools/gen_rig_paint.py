"""The Rig's paintable sheets, for the Forge's four paints (client rig/RigSuit, rig/RigOctoClient).

    python -I tools/gen_rig_paint.py

Run after tools/gen_rig.py, tools/gen_rig_frames.py and tools/gen_rig_octo.py (this reads their sheets). Every sheet is
the same UVs as the one it comes from, in light greys for the client to multiply by a paint, so a paint shows as itself
(red is red, gold is gold) rather than the paint darkened by the gunmetal under it:

- suit_paint_<frame>.png, one per frame (standard is plain suit_paint.png): the frame's own sheet as greys, lifted so its
  plates sit at a light grey. Seams, ribs and scorch marks keep their shade, so the paint has the frame's wear on it.
- suit_under.png: the under-suit's weave alone (its glowing seams left out), in light greys, drawn over the plates in the
  under-suit's paint.
- suit_lights.png: every light on the suit (suit_glow.png's pixels) in white and greys by how bright each is, drawn lit
  in the lights' colour in place of the frame's own glow.
- octo_paint.png and octo_lights.png: the same two for the Octo Core's arms (they take the trim's paint and the lights').
"""
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_rig  # noqa: E402

RIG = os.path.join(gen_rig.ASSETS, "textures", "entity", "rig")
FRAMES = ("", "_scrap", "_shade", "_siege", "_hail", "_tinker")
# where a frame's plates land as greys (out of 255), and how bright the under-suit's weave is
PLATE_GREY = 190
UNDER_GREY = 185


def luma(r, g, b):
    return 0.3 * r + 0.59 * g + 0.11 * b


def lifted(im, glow):
    """A sheet as greys lifted so its middle plate shade sits at PLATE_GREY (the lights are left as they are)."""
    shades = sorted(luma(*im.getpixel((x, y))[:3]) for y in range(im.height) for x in range(im.width)
        if im.getpixel((x, y))[3] > 0 and glow.getpixel((x, y))[3] == 0)
    gain = PLATE_GREY / max(1.0, shades[len(shades) // 2])
    out = Image.new("RGBA", im.size, (0, 0, 0, 0))
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = im.getpixel((x, y))
            if a == 0:
                continue
            v = max(0, min(255, round(luma(r, g, b) * gain)))
            out.putpixel((x, y), (v, v, v, a))
    return out


def lights(glow):
    """The lit pixels as greys by brightness: the brightest white."""
    out = Image.new("RGBA", glow.size, (0, 0, 0, 0))
    for y in range(glow.height):
        for x in range(glow.width):
            r, g, b, a = glow.getpixel((x, y))
            if a == 0:
                continue
            v = max(r, g, b)
            out.putpixel((x, y), (v, v, v, a))
    return out


def under(suit, glow):
    """The under-suit boxes' pixels alone (not their glowing seams), as greys."""
    gen_rig.suit_pack()
    shades = {}
    for _, box in gen_rig.suit_boxes():
        if box["paint"] != "under":
            continue
        w, h, d = box["size"]
        u, v = box["uv"]
        for _face, (fx, fy, fw, fh) in gen_rig.box_faces(u, v, w, h, d).items():
            for y in range(fh):
                for x in range(fw):
                    p = (fx + x, fy + y)
                    if suit.getpixel(p)[3] > 0 and glow.getpixel(p)[3] == 0:
                        shades[p] = luma(*suit.getpixel(p)[:3])
    middle = sorted(shades.values())[len(shades) // 2]
    out = Image.new("RGBA", suit.size, (0, 0, 0, 0))
    for p, s in shades.items():
        v = max(0, min(255, round(s * UNDER_GREY / middle)))
        out.putpixel(p, (v, v, v, 255))
    return out


def main():
    glow = Image.open(os.path.join(RIG, "suit_glow.png")).convert("RGBA")
    for frame in FRAMES:
        sheet = Image.open(os.path.join(RIG, "suit" + frame + ".png")).convert("RGBA")
        frame_glow = Image.open(os.path.join(RIG, "suit_glow" + frame + ".png")).convert("RGBA")
        lifted(sheet, frame_glow).save(os.path.join(RIG, "suit_paint" + frame + ".png"))
    suit = Image.open(os.path.join(RIG, "suit.png")).convert("RGBA")
    under(suit, glow).save(os.path.join(RIG, "suit_under.png"))
    lights(glow).save(os.path.join(RIG, "suit_lights.png"))
    octo = Image.open(os.path.join(RIG, "octo.png")).convert("RGBA")
    octo_glow = Image.open(os.path.join(RIG, "octo_glow.png")).convert("RGBA")
    lifted(octo, octo_glow).save(os.path.join(RIG, "octo_paint.png"))
    lights(octo_glow).save(os.path.join(RIG, "octo_lights.png"))


if __name__ == "__main__":
    main()
