"""Draws the pink fairy (0.20.11, 2026-11-04): her flying sprite and the Bottled Pink Fairy bauble icon, both 16x16
two-frame animated item textures (wings flapping; the glow in the bottle breathing). Our own art in the pack's vanilla
style (gen_baubles.py's outline and three tones lit from the top left); nothing from Paxo's Pink Fairy (ARR).
Run it from the root:

    python -I tools/gen_fairy.py [preview.png]

With a path it also writes an 8x preview of both frames of each there.
"""
import json
import os
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEXTURES = os.path.join(ROOT, "src", "main", "resources", "assets", "jaymospowers", "textures", "item", "bauble")

OUTLINE = (40, 28, 20, 255)
ROSE = ((255, 214, 238, 255), (255, 138, 208, 255), (196, 70, 150, 255))  # light, mid, dark
SKIN = ((255, 236, 228, 255), (250, 206, 196, 255))
HAIR = ((255, 170, 220, 255), (220, 96, 176, 255))
WING = ((255, 250, 254, 190), (255, 214, 240, 160), (255, 170, 222, 130))
HALO = (255, 150, 215, 60)
GLASS = ((250, 254, 255, 230), (206, 230, 246, 110), (140, 170, 204, 190))  # glint, pane, edge
CORK = ((214, 168, 112, 255), (168, 120, 70, 255), (112, 76, 40, 255))


def blank():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def put(img, x, y, color):
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), color)


def outline(img, color=OUTLINE):
    """Every empty pixel touching a painted one (up, down, left, right) becomes the outline (as gen_baubles.py)."""
    marks = []
    for y in range(img.height):
        for x in range(img.width):
            if img.getpixel((x, y))[3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < img.width and 0 <= ny < img.height and img.getpixel((nx, ny))[3] != 0 \
                            and img.getpixel((nx, ny)) != color:
                        marks.append((x, y))
                        break
    for x, y in marks:
        img.putpixel((x, y), color)


def halo(img, color=HALO):
    """A soft glow: every empty pixel near a painted one gets a faint rose wash (no dark outline: she is light)."""
    marks = []
    for y in range(img.height):
        for x in range(img.width):
            if img.getpixel((x, y))[3] == 0:
                for dx in (-1, 0, 1):
                    for dy in (-1, 0, 1):
                        nx, ny = x + dx, y + dy
                        if 0 <= nx < img.width and 0 <= ny < img.height and img.getpixel((nx, ny))[3] == 255:
                            marks.append((x, y))
                            break
                    else:
                        continue
                    break
    for x, y in marks:
        img.putpixel((x, y), color)


def wings(img, up):
    """Two pairs of dragonfly wings off the shoulders: raised high, or swept low."""
    if up:
        shapes = [(3, 2), (4, 2), (2, 3), (3, 3), (4, 3), (5, 3), (3, 4), (4, 4), (5, 4), (5, 5), (6, 5),
                  (4, 6), (5, 6), (4, 7)]
    else:
        shapes = [(2, 6), (3, 6), (4, 6), (5, 6), (1, 7), (2, 7), (3, 7), (4, 7), (5, 7), (6, 7), (2, 8), (3, 8),
                  (4, 8), (5, 8), (3, 9), (4, 9), (5, 9), (4, 10)]
    for x, y in shapes:
        tone = WING[0] if y <= (4 if up else 7) else (WING[1] if y <= (6 if up else 8) else WING[2])
        put(img, x, y, tone)
        put(img, 15 - x, y, tone)


def fairy(up):
    """Her sprite: a small rose-lit figure (pink hair, pale face, a petal dress) with wings, all in a faint glow."""
    img = blank()
    wings(img, up)
    # hair and head
    for x in range(6, 10):
        put(img, x, 2, HAIR[0] if x < 8 else HAIR[1])
    for x, y in ((5, 3), (5, 4), (10, 3), (10, 4)):
        put(img, x, y, HAIR[1] if x > 7 else HAIR[0])
    for y in (3, 4, 5):
        for x in range(6, 10):
            put(img, x, y, SKIN[0] if x < 8 else SKIN[1])
    put(img, 6, 3, HAIR[0])
    put(img, 9, 3, HAIR[1])
    put(img, 7, 4, (120, 60, 110, 255))  # eyes
    put(img, 8, 4, (120, 60, 110, 255))
    put(img, 7, 6, SKIN[1])
    put(img, 8, 6, SKIN[1])
    # petal dress, flared
    for y, (a, b) in zip(range(7, 11), ((7, 8), (6, 9), (6, 9), (5, 10))):
        for x in range(a, b + 1):
            tone = ROSE[0] if x <= a else (ROSE[2] if x >= b else ROSE[1])
            put(img, x, y, tone)
    # legs
    put(img, 7, 11, SKIN[1])
    put(img, 8, 11, SKIN[1])
    halo(img)
    # a spark of light at her heart
    put(img, 7, 8, (255, 255, 255, 255))
    return img


def bottle(bright):
    """A corked glass bottle with a fairy glowing inside (the glow breathes between the frames)."""
    img = blank()
    # the bulb (round) and neck
    cx, cy, r = 7.5, 10.0, 4.6
    for y in range(16):
        for x in range(16):
            if (x - cx) ** 2 + (y - cy) ** 2 <= r * r:
                put(img, x, y, GLASS[1])
    for y in range(3, 6):
        for x in range(6, 10):
            put(img, x, y, GLASS[1])
    # the cork
    for y in range(1, 3):
        for x in range(6, 10):
            put(img, x, y, CORK[0] if x == 6 else (CORK[2] if x == 9 else CORK[1]))
    # the glow inside: a rose wash, then her, bright at the middle
    glow = ROSE[0] if bright else ROSE[1]
    for y in range(7, 14):
        for x in range(4, 12):
            d = (x - cx) ** 2 + (y - 10.0) ** 2
            if d <= (3.6 if bright else 3.0) ** 2:
                put(img, x, y, (glow[0], glow[1], glow[2], 230 if bright else 190))
    # her wings and body
    for x, y in ((5, 8), (6, 8), (6, 9), (10, 8), (9, 8), (9, 9)):
        put(img, x, y, (255, 255, 255, 255))
    put(img, 7, 8, HAIR[0])
    put(img, 8, 8, HAIR[1])
    put(img, 7, 9, SKIN[0])
    put(img, 8, 9, SKIN[1])
    put(img, 7, 10, ROSE[0])
    put(img, 8, 10, ROSE[1])
    put(img, 7, 11, ROSE[1])
    put(img, 8, 11, ROSE[2])
    if bright:
        put(img, 7, 10, (255, 255, 255, 255))
    # glints on the glass, upper left
    put(img, 4, 8, GLASS[0])
    put(img, 4, 9, GLASS[0])
    put(img, 5, 7, GLASS[0])
    put(img, 6, 4, GLASS[0])
    # the glass edge, darker at the bottom right
    for y in range(16):
        for x in range(16):
            if img.getpixel((x, y))[3] and img.getpixel((x, y)) == GLASS[1]:
                inside = (x - cx) ** 2 + (y - cy) ** 2
                if inside > (r - 1.0) ** 2 and (x - cx) + (y - cy) > 2.0:
                    put(img, x, y, GLASS[2])
    outline(img)
    return img


def strip(frames):
    """Frames stacked top to bottom, as an animated item texture wants."""
    out = Image.new("RGBA", (16, 16 * len(frames)), (0, 0, 0, 0))
    for i, f in enumerate(frames):
        out.paste(f, (0, 16 * i))
    return out


def save(name, frames, frametime):
    os.makedirs(TEXTURES, exist_ok=True)
    strip(frames).save(os.path.join(TEXTURES, name + ".png"))
    with open(os.path.join(TEXTURES, name + ".png.mcmeta"), "w", encoding="utf-8") as f:
        json.dump({"animation": {"frametime": frametime, "interpolate": False}}, f, indent=2)
        f.write("\n")


def main():
    fairy_frames = [fairy(True), fairy(False)]
    bottle_frames = [bottle(False), bottle(True)]
    save("pink_fairy", fairy_frames, 2)
    save("bottled_fairy", bottle_frames, 12)
    if len(sys.argv) > 1:
        scale = 8
        sheet = Image.new("RGBA", (16 * 4 * scale + 5 * 8, 16 * scale + 16), (52, 60, 44, 255))
        for i, f in enumerate(fairy_frames + bottle_frames):
            sheet.alpha_composite(f.resize((16 * scale, 16 * scale), Image.NEAREST), (8 + i * (16 * scale + 8), 8))
        sheet.save(sys.argv[1])


if __name__ == "__main__":
    main()
