"""Draws the assassin costume (0.14.3) on a default player silhouette, from the generated textures.

    python -I tools/costume_preview.py [output.png]

Orthographic front, side (the wearer's left) and back views, in white/red and in black/grey: a small z-buffered box
renderer that mirrors the boxes of com.jaymospowers.client.costume.CostumeModels (the Assassin cut with its mantle,
bracers and coat tails; the Peaked Hood with its brim and cowl). Model space is Minecraft's: x to the wearer's left,
y down, the wearer facing -z; the texture of a face is read the way Minecraft lays a box out (tools/gen_costume.py).
"""
import os
import sys

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
COSTUME = os.path.join(ROOT, "src", "main", "resources", "assets", "jaymospowers", "textures", "entity", "costume")
DEFAULT_OUT = "/mnt/project-files/art-overhaul/assassin/assassin-preview.png"
SCALE = 9

# CostumeModels' numbers (SUIT_GROW, HOOD_GROW, ...), kept in step by hand
SUIT_GROW, HOOD_GROW = 0.35, 0.8
BRACER_GROW, MANTLE_GROW, HOOD_BRIM_GROW, COWL_GROW = 0.15, 0.2, 0.4, 0.6
TAIL_TOP, TAIL_LENGTH, TAIL_GAP = 9, 11, 0.05

DYES = {"white": (249, 255, 254), "red": (176, 46, 38), "black": (29, 29, 33), "grey": (71, 79, 82)}
LOOKS = [("white", "red"), ("black", "grey")]


class Box:
    """A cuboid at model-space (x0, y0, z0) of size (w, h, d), grown by `grow`; `sample(face, tx, ty)` gives its colour."""

    def __init__(self, pos, size, grow, sample):
        self.x0, self.y0, self.z0 = pos
        self.w, self.h, self.d = size
        self.grow = grow
        self.sample = sample


def textured(images, tint, uv, size):
    """A sampler over a primary/secondary texture pair, tinted by the two dyes, laid out like Minecraft's box UVs."""
    u, v = uv
    w, h, d = (int(s) for s in size)
    faces = {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h),
    }

    def sample(face, tx, ty):
        fx, fy, fw, fh = faces[face]
        x, y = fx + min(fw - 1, max(0, tx)), fy + min(fh - 1, max(0, ty))
        for im, dye in zip(images, tint):
            r, g, b, a = im.getpixel((x, y))
            if a:
                return (r * dye[0] // 255, g * dye[1] // 255, b * dye[2] // 255)
        return None

    return sample


def flat(colour, eyes=False):
    """A plain-coloured piece (the silhouette), with eyes on the head's front if asked."""
    def sample(face, tx, ty):
        if eyes and face == "front" and ty == 4 and tx in (1, 2, 5, 6):
            return (70, 60, 140) if tx in (1, 6) else (240, 240, 240)
        return colour
    return sample


def load(kind, name, slim, dyes):
    tag = "_slim" if slim else ""
    images = [Image.open(os.path.join(COSTUME, kind, name + tag + suffix + ".png")).convert("RGBA")
              for suffix in ("_primary", "_secondary")]
    return images, (DYES[dyes[0]], DYES[dyes[1]])


def silhouette():
    """A default player (wide arms) in plain clothes, pivots as PlayerModel has them."""
    skin, shirt, trousers, shoe, hair = (198, 140, 110), (60, 140, 160), (50, 60, 140), (90, 90, 96), (70, 50, 36)
    boxes = [
        Box((-4, -8, -4), (8, 8, 8), 0, flat(skin, True)),
        Box((-4, -8.2, -4.2), (8, 2, 8.4), 0, flat(hair)),
        Box((-4, 0, -2), (8, 12, 4), 0, flat(shirt)),
    ]
    for px, arm in ((-5, -3), (5, -1)):
        boxes.append(Box((px + arm, 0, -2), (4, 12, 4), 0, flat(shirt)))
        boxes.append(Box((px + arm, 10, -2), (4, 2, 4), 0, flat(skin)))
    for px in (-1.9, 1.9):
        boxes.append(Box((px - 2, 12, -2), (4, 12, 4), 0, flat(trousers)))
        boxes.append(Box((px - 2, 21, -2), (4, 3, 4), 0, flat(shoe)))
    return boxes


def costume(dyes):
    """The Assassin cut and the Peaked Hood, box for box as CostumeModels builds them."""
    out = []
    g = SUIT_GROW
    suit, tint = load("suit", "assassin", False, dyes)
    pieces = [  # (position, size, grow, uv)
        ((-4, 0, -2), (8, 12, 4), g, (16, 16)),
        ((-8, 0, -2), (4, 12, 4), g, (40, 16)),
        ((4, 0, -2), (4, 12, 4), g, (32, 48)),
        ((-3.9, 12, -2), (4, 12, 4), g, (0, 16)),
        ((-0.1, 12, -2), (4, 12, 4), g, (16, 48)),
        ((-8.5, 1.2, -2.5), (5, 2, 5), g + MANTLE_GROW, (32, 8)),
        ((-8.5, -0.8, -2.5), (5, 3, 5), g, (32, 0)),
        ((3.5, 1.2, -2.5), (5, 2, 5), g + MANTLE_GROW, (32, 8)),
        ((3.5, -0.8, -2.5), (5, 3, 5), g, (32, 0)),
        ((-8, 7, -2), (4, 4, 4), g + BRACER_GROW, (40, 32)),
        ((4, 7, -2), (4, 4, 4), g + BRACER_GROW, (48, 48)),
        ((-4, TAIL_TOP, -2 - g - TAIL_GAP - 1), (8, TAIL_LENGTH, 1), 0, (0, 32)),
        ((-4, TAIL_TOP, 2 + g + TAIL_GAP), (8, TAIL_LENGTH, 1), 0, (20, 32)),
    ]
    for pos, size, grow, uv in pieces:
        out.append(Box(pos, size, grow, textured(suit, tint, uv, size)))
    hood, tint = load("mask", "peaked_hood", False, dyes)
    for pos, size, grow, uv in (((-4, -8, -4), (8, 8, 8), HOOD_GROW, (0, 0)), ((-4, -8, -7), (8, 2, 3), HOOD_BRIM_GROW, (0, 16)),
                                ((-4, 0, -3), (8, 2, 6), COWL_GROW, (24, 16))):
        out.append(Box(pos, size, grow, textured(hood, tint, uv, size)))
    return out


# view: (depth of a box's visible face, face name, texture x from model coordinate, screen x from model coordinate)
def render(boxes, view):
    """One orthographic view, upscaled, on a transparent background: z-buffered, nearest face only."""
    width, height = 24, 34
    cx, top = 12, 10
    img = Image.new("RGBA", (width * SCALE, height * SCALE), (0, 0, 0, 0))
    zbuf = {}
    for box in boxes:
        g = box.grow
        x0, x1 = box.x0 - g, box.x0 + box.w + g
        y0, y1 = box.y0 - g, box.y0 + box.h + g
        z0, z1 = box.z0 - g, box.z0 + box.d + g
        # flip: the model's axis runs right to left on screen; from_far: the face's texture starts at the far end
        if view == "front":
            face, depth, ax0, ax1, flip, from_far, span = "front", z0, x0, x1, False, False, box.w
        elif view == "back":
            face, depth, ax0, ax1, flip, from_far, span = "back", -z1, x0, x1, True, True, box.w
        else:  # from the wearer's left (+x): their front (-z) on the right, the left face's first column at the front
            face, depth, ax0, ax1, flip, from_far, span = "left", -x1, z0, z1, True, False, box.d
        shade = {"front": 1.0, "back": 0.86, "left": 0.8}[face]
        lo, hi = (cx - ax1, cx - ax0) if flip else (cx + ax0, cx + ax1)
        for sy in range(int((top + y0) * SCALE), int((top + y1) * SCALE)):
            ty = int((sy / SCALE - top - y0) / (y1 - y0) * box.h)
            for sx in range(int(lo * SCALE), int(hi * SCALE)):
                a = cx - sx / SCALE if flip else sx / SCALE - cx
                t = (ax1 - a) / (ax1 - ax0) if from_far else (a - ax0) / (ax1 - ax0)
                tx = min(span - 1, int(t * span))
                colour = box.sample(face, tx, ty)
                if colour is None or not (0 <= sx < img.width and 0 <= sy < img.height):
                    continue
                if depth < zbuf.get((sx, sy), 1e9):
                    zbuf[(sx, sy)] = depth
                    img.putpixel((sx, sy), tuple(int(c * shade) for c in colour) + (255,))
    return img


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_OUT
    views = ("front", "side", "back")
    cell_w, cell_h = 24 * SCALE, 34 * SCALE
    sheet = Image.new("RGBA", (cell_w * len(views), cell_h * len(LOOKS)), (176, 190, 200, 255))
    draw = ImageDraw.Draw(sheet)
    for row, dyes in enumerate(LOOKS):
        boxes = silhouette() + costume(dyes)
        for col, view in enumerate(views):
            panel = Image.new("RGBA", (cell_w, cell_h), (200, 210, 218, 255) if (row + col) % 2 == 0 else (190, 201, 210, 255))
            panel.alpha_composite(render(boxes, view))
            sheet.paste(panel, (col * cell_w, row * cell_h))
        draw.text((6, row * cell_h + 4), "%s / %s" % dyes, fill=(30, 30, 40, 255))
    for col, view in enumerate(views):
        draw.text((col * cell_w + 6, 16), view, fill=(30, 30, 40, 255))
    os.makedirs(os.path.dirname(out), exist_ok=True)
    sheet.convert("RGB").save(out)
    print(out)


if __name__ == "__main__":
    main()
