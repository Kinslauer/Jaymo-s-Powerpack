"""Draws the pack's "Reason" title logo: src/main/resources/assets/jaymospowers/textures/gui/title/reason.png.

The texture is 1024x320, drawn by ReasonLogoMixin as 256x80 GUI units (4 texture pixels a unit), starting TOP_GUI (28)
units above where vanilla's 256x44 logo starts, so the letters sit where MINECRAFT did and the silhouettes rise above.
Optional: python3 tools/reason_logo.py --preview out.png  (a title-screen mock at GUI scale 2, day and night).

All our own art (no Mojang or other pack art): chunky 7x7 block letters with a stone face, carved seams, a deep
perspective extrusion and a black outline; ivy grown over them; behind them one warrior with sword and shield and
a crowd of mob shadows with faint red eyes.
"""
import math
import os
import random
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src/main/resources/assets/jaymospowers/textures/gui/title/reason.png")

W, H = 1024, 320       # texture size; the game draws it 256x80
TOP_GUI = 28           # GUI units above vanilla's logo position (keep in step with ReasonLogoMixin.TOP)
CELL = 21              # pixels per glyph cell
GAP = 1                # cells between letters
FACE_Y = TOP_GUI * 4 + 2
DEPTH = 40             # extrusion, pixels down
PERSPECTIVE = 0.0021   # how much each pixel of depth shrinks toward the vanishing point (below the middle)
OUTLINE = 6
SEAM_CHANCE = 0.38
SEED = 1104
G = 2                  # silhouettes and ivy are drawn on a grid of G texture pixels, so they read as chunky pixels

GLYPHS = {
    "R": ["######.", "##...##", "##...##", "##...##", "######.", "##..##.", "##...##"],
    "E": ["#######", "##.....", "##.....", "######.", "##.....", "##.....", "#######"],
    "A": [".#####.", "##...##", "##...##", "##...##", "#######", "##...##", "##...##"],
    "S": [".######", "##.....", "##.....", ".#####.", ".....##", ".....##", "######."],
    "O": [".#####.", "##...##", "##...##", "##...##", "##...##", "##...##", ".#####."],
    "N": ["##...##", "###..##", "####.##", "##.####", "##..###", "##...##", "##...##"],
}
WORD = "REASON"


def cells():
    cols = len(WORD) * 7 + (len(WORD) - 1) * GAP
    grid = np.zeros((7, cols), dtype=np.int8)
    owner = np.full((7, cols), -1, dtype=np.int8)
    for i, ch in enumerate(WORD):
        x0 = i * (7 + GAP)
        for r, row in enumerate(GLYPHS[ch]):
            for c, v in enumerate(row):
                if v == "#":
                    grid[r, x0 + c] = 1
                    owner[r, x0 + c] = i
    return grid, owner


# ---------------------------------------------------------------- silhouettes (grid of G pixels: 512x160)

GW, GH = W // G, H // G
LETTER_TOP = FACE_Y // G       # grid row where the letters start; everything below is hidden by them


def warrior(d, eyes, rim):
    cx = GW // 2
    # helmet (rounded top), a plume streaming back, neck
    d.rectangle([cx - 6, 11, cx + 6, 25], fill=255)
    d.rectangle([cx - 5, 9, cx + 5, 11], fill=255)
    d.line([(cx, 9), (cx - 3, 4), (cx - 10, 3), (cx - 16, 7)], fill=255, width=3)
    d.rectangle([cx - 3, 25, cx + 3, 28], fill=255)
    # cape behind, flaring out to the right
    d.polygon([(cx + 6, 28), (cx + 24, 32), (cx + 38, LETTER_TOP + 6), (cx + 8, LETTER_TOP + 6)], fill=255)
    # torso narrowing to the waist, round pauldrons
    d.polygon([(cx - 14, 28), (cx + 14, 28), (cx + 10, LETTER_TOP + 8), (cx - 10, LETTER_TOP + 8)], fill=255)
    d.ellipse([cx - 21, 25, cx - 7, 37], fill=255)
    d.ellipse([cx + 7, 25, cx + 21, 37], fill=255)
    # sword arm raised up and out, the blade angled to the sky
    d.line([(cx + 16, 32), (cx + 26, 25), (cx + 31, 17)], fill=255, width=6)
    d.line([(cx + 25, 12), (cx + 37, 20)], fill=255, width=3)      # crossguard
    d.line([(cx + 31, 16), (cx + 29, 21)], fill=255, width=3)      # grip in the fist
    d.polygon([(cx + 30, 14), (cx + 33, 17), (cx + 54, 2), (cx + 55, -1), (cx + 51, 0)], fill=255)  # blade
    # heater shield on the left arm, in front of the chest
    shield = [(cx - 31, 30), (cx - 5, 30), (cx - 5, 46), (cx - 18, LETTER_TOP + 3), (cx - 31, 46)]
    d.polygon(shield, fill=255)
    rim.line(shield + [shield[0]], fill=255, width=1)
    rim.ellipse([cx - 21, 37, cx - 15, 43], outline=255)
    rim.line([(cx - 20, 21), (cx + 1, 21)], fill=0)
    # the visor slit, the faintest cold light, so he isn't one of the mobs
    eyes.append((cx - 4, 18, cx + 4, 18, (150, 170, 190)))


def zombie(d, eyes, x, top, s=1.0):
    h = int(13 * s)
    d.rectangle([x - h // 2, top, x + h // 2, top + h], fill=255)
    b = top + h
    d.rectangle([x - h // 2 - 1, b, x + h // 2 + 1, LETTER_TOP + 6], fill=255)
    # arms reaching forward, drawn raised and spread
    d.line([(x - h // 2, b + 3), (x - h - 4, b - 2)], fill=255, width=max(3, int(5 * s)))
    d.line([(x + h // 2, b + 3), (x + h + 4, b - 2)], fill=255, width=max(3, int(5 * s)))
    ey = top + h // 2 - 1
    eyes.append((x - h // 4 - 1, ey, x - h // 4, ey, None))
    eyes.append((x + h // 4, ey, x + h // 4 + 1, ey, None))


def creeper(d, eyes, x, top, s=1.0):
    h = int(12 * s)
    d.rectangle([x - h // 2, top, x + h // 2, top + h], fill=255)
    d.rectangle([x - h // 3, top + h, x + h // 3, LETTER_TOP + 6], fill=255)
    ey = top + h // 3
    eyes.append((x - h // 3, ey, x - h // 3 + 1, ey + 1, None))
    eyes.append((x + h // 3 - 1, ey, x + h // 3, ey + 1, None))


def skeleton(d, eyes, x, top, s=1.0, bow_left=True):
    h = int(11 * s)
    d.rectangle([x - h // 2, top, x + h // 2, top + h], fill=255)
    d.rectangle([x - 2, top + h, x + 2, LETTER_TOP + 6], fill=255)
    d.rectangle([x - 6, top + h + 2, x + 6, top + h + 4], fill=255)       # shoulders
    sx = -1 if bow_left else 1
    d.line([(x, top + h + 3), (x + sx * 14, top + h + 2)], fill=255, width=2)
    bx = x + sx * 15
    d.arc([bx - 6, top + h - 10, bx + 6, top + h + 14], 90 + sx * 90 - 70, 90 + sx * 90 + 70, fill=255, width=2)
    d.line([(bx, top + h - 9), (bx, top + h + 13)], fill=255, width=1)
    ey = top + h // 2
    eyes.append((x - h // 4 - 1, ey, x - h // 4, ey, None))
    eyes.append((x + h // 4, ey, x + h // 4 + 1, ey, None))


def spider(d, eyes, x, top, s=1.0):
    w, h = int(26 * s), int(9 * s)
    d.ellipse([x - w // 2, top + 2, x + w // 2, top + 2 + h * 2], fill=255)
    d.rectangle([x - int(7 * s), top, x + int(7 * s), top + int(8 * s)], fill=255)
    for side in (-1, 1):
        for k in range(4):
            bx = x + side * int((4 + k * 3) * s)
            kx = x + side * int((16 + k * 5) * s)
            ky = top - int((6 - k) * s)
            fx = x + side * int((20 + k * 6) * s)
            d.line([(bx, top + 5), (kx, ky), (fx, LETTER_TOP + 4)], fill=255, width=2)
    ey = top + int(3 * s)
    for ex in (-5, -2, 2, 5):
        eyes.append((x + int(ex * s), ey, x + int(ex * s), ey, None))
    eyes.append((x - int(3 * s), ey + 2, x - int(3 * s), ey + 2, None))
    eyes.append((x + int(3 * s), ey + 2, x + int(3 * s), ey + 2, None))


def enderman(d, eyes, x, top, s=1.0):
    h = int(10 * s)
    d.rectangle([x - h // 2, top, x + h // 2, top + h], fill=255)
    d.rectangle([x - 3, top + h, x + 3, LETTER_TOP + 6], fill=255)
    d.rectangle([x - 7, top + h + 1, x + 7, top + h + 3], fill=255)
    d.line([(x - 6, top + h + 2), (x - 9, LETTER_TOP + 6)], fill=255, width=2)
    d.line([(x + 6, top + h + 2), (x + 9, LETTER_TOP + 6)], fill=255, width=2)
    ey = top + h // 2 + 1
    eyes.append((x - h // 2 + 1, ey, x - 1, ey, None))
    eyes.append((x + 1, ey, x + h // 2 - 1, ey, None))


def silhouettes():
    """(far mobs, near mobs, the warrior, his shield's rim, eyes) as grid masks."""
    far, near, hero, rim = (Image.new("L", (GW, GH), 0) for _ in range(4))
    df, dn, dh = ImageDraw.Draw(far), ImageDraw.Draw(near), ImageDraw.Draw(hero)
    eyes = []
    warrior(dh, eyes, ImageDraw.Draw(rim))
    # the horde: smaller and paler further back, bigger in front; none crowds the warrior's sword
    skeleton(df, eyes, 58, 38, 0.9, bow_left=False)
    creeper(df, eyes, 128, 40, 0.85)
    zombie(df, eyes, 372, 37, 0.85)
    skeleton(df, eyes, 452, 37, 0.9, bow_left=True)
    enderman(df, eyes, 330, 16, 1.1)
    zombie(dn, eyes, 172, 33, 1.1)
    spider(dn, eyes, 96, 46, 0.95)
    creeper(dn, eyes, 214, 42, 1.0)
    spider(dn, eyes, 418, 47, 1.0)
    zombie(dn, eyes, 478, 39, 0.95)
    creeper(dn, eyes, 302, 42, 0.9)
    return far, near, hero, rim, eyes


def up(img):
    return img.resize((img.width * G, img.height * G), Image.NEAREST)


# ---------------------------------------------------------------- letters

def letters():
    """Returns (rgba array of the letters, face mask, body mask)."""
    rng = random.Random(SEED)
    grid, owner = cells()
    rows, cols = grid.shape
    ox = (W - cols * CELL) // 2
    oy = FACE_Y

    face = np.zeros((H, W), dtype=bool)
    for r in range(rows):
        for c in range(cols):
            if grid[r, c]:
                face[oy + r * CELL: oy + (r + 1) * CELL, ox + c * CELL: ox + (c + 1) * CELL] = True

    # perspective extrusion: each step down shrinks a little toward a vanishing point below the middle
    face_img = Image.fromarray((face * 255).astype(np.uint8))
    vx = W / 2
    depth_at = np.zeros((H, W), dtype=np.float32)
    for d in range(DEPTH, 0, -1):
        s = 1 - PERSPECTIVE * d
        m = np.array(face_img.transform((W, H), Image.AFFINE, (1 / s, 0, vx - vx / s, 0, 1, -d), Image.NEAREST)) > 0
        depth_at[m] = d
    side = (depth_at > 0) & ~face

    body = face | side
    outline = np.array(Image.fromarray((body * 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(OUTLINE * 2 + 1))) > 0

    img = np.zeros((H, W, 4), dtype=np.float32)
    img[outline] = (12, 10, 10, 255)

    # sides: lit near the face, dark at the far end, in blocky bands
    t = np.floor(np.clip(depth_at / DEPTH, 0, 1) * 8) / 8
    for k, (a, b) in enumerate([(132, 46), (122, 40), (116, 38)]):
        img[..., k] = np.where(side, a + (b - a) * t, img[..., k])
    # a little light on the left-facing walls, shade on the right-facing ones, so the depth reads as 3D
    xs = np.arange(W)[None, :].repeat(H, 0)
    lean = np.clip((vx - xs) / vx, -1, 1)
    for k in range(3):
        img[..., k] = np.where(side, img[..., k] + 6 * lean, img[..., k])

    ys = np.arange(H)[:, None].repeat(W, 1)
    noise = np.random.default_rng(SEED).normal(0, 4.5, (H, W))
    v = (ys - oy) / (rows * CELL)
    top = np.array([216, 208, 200], dtype=np.float32)
    bot = np.array([164, 156, 148], dtype=np.float32)
    for k in range(3):
        img[..., k] = np.where(face, top[k] + (bot[k] - top[k]) * v + noise, img[..., k])
    img[..., 3] = np.where(outline, 255, 0)

    for w in range(3):
        sh = lambda a, dy, dx: np.roll(np.roll(a, dy, 0), dx, 1)
        lit = face & ~(sh(face, w + 1, 0) & sh(face, 0, w + 1))
        dark = face & ~(sh(face, -(w + 1), 0) & sh(face, 0, -(w + 1)))
        for k in range(3):
            img[..., k] = np.where(lit, np.minimum(img[..., k] + 26 - w * 6, 240), img[..., k])
            img[..., k] = np.where(dark & ~lit, img[..., k] - 34 + w * 8, img[..., k])

    pil = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGBA")
    draw = ImageDraw.Draw(pil)
    groove, lip = (92, 84, 80, 255), (232, 226, 220, 255)
    for r in range(rows):
        for c in range(cols):
            if not grid[r, c]:
                continue
            x, y = ox + c * CELL, oy + r * CELL
            if c + 1 < cols and grid[r, c + 1] and owner[r, c + 1] == owner[r, c] and rng.random() < SEAM_CHANCE:
                gx = x + CELL
                y0, y1 = y + rng.randint(0, 4), y + CELL - rng.randint(0, 4)
                draw.line([(gx - 1, y0), (gx - 1, y1)], fill=groove, width=3)
                draw.line([(gx + 1, y0), (gx + 1, y1)], fill=lip, width=1)
            if r + 1 < rows and grid[r + 1, c] and owner[r + 1, c] == owner[r, c] and rng.random() < SEAM_CHANCE:
                gy = y + CELL
                x0, x1 = x + rng.randint(0, 4), x + CELL - rng.randint(0, 4)
                draw.line([(x0, gy - 1), (x1, gy - 1)], fill=groove, width=3)
                draw.line([(x0, gy + 1), (x1, gy + 1)], fill=lip, width=1)
            if rng.random() < 0.12:
                cx, cy = x + rng.randint(4, CELL - 4), y + rng.randint(4, CELL - 4)
                pts = [(cx, cy)]
                for _ in range(3):
                    cx += rng.choice((-4, 0, 4)); cy += 4
                    pts.append((cx, cy))
                draw.line(pts, fill=(120, 112, 106, 255), width=2)
    arr = np.array(pil)
    base = np.clip(img, 0, 255).astype(np.uint8)
    arr[~face] = base[~face]
    return arr, face, body | outline, grid, ox, oy


# ---------------------------------------------------------------- ivy

LEAF = [(40, 98, 34), (58, 124, 42), (78, 150, 52), (96, 170, 60)]
LEAF_HI = (138, 196, 84)
STEM = (46, 70, 30)
DARK = (16, 30, 12)


def leaf(px, x, y, size, rng):
    """A blocky ivy leaf: a three-lobed diamond on the grid, shaded light top-left."""
    col = rng.choice(LEAF)
    pts = set()
    r = size
    for dy in range(-r, r + 1):
        for dx in range(-r, r + 1):
            if abs(dx) + abs(dy) <= r:
                pts.add((x + dx, y + dy))
    if r >= 2:   # side lobes
        for sx in (-1, 1):
            for dy in range(-1, 1):
                pts.add((x + sx * (r + 1), y + dy))
    for (a, b) in pts:
        px[(a, b)] = col
    for (a, b) in pts:
        if (a - 1, b) not in pts or (a, b - 1) not in pts:
            px[(a, b)] = tuple(min(255, c + 30) for c in col)
    px[(x - 1, y - 1)] = LEAF_HI
    return pts


def ivy(face_grid_top, rng):
    """Ivy on the letter grid: vines from the tops of the letters hanging down their faces, with leaf clumps over the
    top edges. Returns {(gx, gy): rgb} on the G-pixel grid."""
    px, stems = {}, {}
    starts = rng.sample(face_grid_top, min(21, len(face_grid_top)))
    for sx, sy in starts:
        x, y = sx, sy - 1
        length = rng.randint(4, 30)
        for i in range(length):
            stems[(x, y)] = STEM
            if rng.random() < 0.36:
                side = rng.choice((-1, 1))
                leaf(px, x + side * 2, y, rng.choice((1, 2, 2)), rng)
            y += 1
            if rng.random() < 0.36:
                x += rng.choice((-1, 1))
        # a clump spilling over the top edge where the vine starts
        for _ in range(rng.randint(2, 4)):
            leaf(px, sx + rng.randint(-4, 4), sy + rng.randint(-3, 1), rng.choice((1, 2, 2)), rng)
    out = dict(stems)
    out.update(px)
    return out


# ---------------------------------------------------------------- compose

def logo():
    rng = random.Random(SEED + 7)
    canvas = Image.new("RGBA", (W, H), (0, 0, 0, 0))

    far, near, hero, rim, eyes = silhouettes()
    # a faint pale backlight behind the warrior, so he reads on a dark panorama too
    glow = Image.new("L", (GW, GH), 0)
    ImageDraw.Draw(glow).ellipse([GW // 2 - 34, 0, GW // 2 + 34, LETTER_TOP + 10], fill=120)
    glow = up(glow).filter(ImageFilter.GaussianBlur(26))
    canvas.alpha_composite(Image.merge("RGBA", (Image.new("L", (W, H), 255), Image.new("L", (W, H), 236),
                                                 Image.new("L", (W, H), 196), glow.point(lambda a: a * 0.55))))
    for mask, col, alpha in ((far, (30, 24, 36), 205), (near, (16, 12, 20), 235), (hero, (8, 6, 10), 250),
                             (rim, (58, 52, 66), 255)):
        m = up(mask).point(lambda a, al=alpha: al if a else 0)
        layer = Image.new("RGBA", (W, H), col + (0,))
        layer.putalpha(m)
        canvas.alpha_composite(layer)
    # faint red eyes, with a soft glow
    eye = Image.new("RGBA", (GW, GH), (0, 0, 0, 0))
    de = ImageDraw.Draw(eye)
    for x0, y0, x1, y1, col in eyes:
        de.rectangle([x0, y0, x1, y1], fill=(col or (226, 44, 36)) + (215,))
    eye = up(eye)
    halo = eye.filter(ImageFilter.GaussianBlur(5))
    halo.putalpha(halo.getchannel("A").point(lambda a: min(255, int(a * 1.6))))
    canvas.alpha_composite(halo)
    canvas.alpha_composite(eye)

    arr, face, body, grid, ox, oy = letters()
    canvas.alpha_composite(Image.fromarray(arr, "RGBA"))

    # ivy: start points on the top edge of each letter's top blocks
    tops = []
    rows, cols = grid.shape
    for c in range(cols):
        for r in range(rows):
            if grid[r, c] and (r == 0 or not grid[r - 1, c]):
                for k in range(0, CELL, 7):
                    tops.append(((ox + c * CELL + k) // G, (oy + r * CELL) // G))
    vines = ivy(tops, rng)
    ivy_img = Image.new("RGBA", (GW, GH), (0, 0, 0, 0))
    for (x, y), col in vines.items():
        if 0 <= x < GW and 0 <= y < GH:
            ivy_img.putpixel((x, y), col + (255,))
    # a dark rim around the ivy so it stands off the stone
    rim = ivy_img.getchannel("A").filter(ImageFilter.MaxFilter(3))
    rim_img = Image.new("RGBA", (GW, GH), DARK + (0,))
    rim_img.putalpha(rim.point(lambda a: 230 if a else 0))
    rim_img = up(rim_img)
    ivy_img = up(ivy_img)
    # ivy only grows on the letters (and a hair past their outline)
    allowed = Image.fromarray((body * 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(9))
    for layer in (rim_img, ivy_img):
        a = np.minimum(np.array(layer.getchannel("A")), np.array(allowed))
        layer.putalpha(Image.fromarray(a))
        canvas.alpha_composite(layer)
    return canvas


def preview(img, path):
    """Title screen tops at GUI scale 2, a day sky and a dark one, with the yellow caption where vanilla puts it."""
    def one(bg_top, bg_bot):
        sky = Image.new("RGBA", (1000, 300))
        for y in range(300):
            t = y / 299
            sky.paste(tuple(int(a + (b - a) * t) for a, b in zip(bg_top, bg_bot)) + (255,), (0, y, 1000, y + 1))
        cx = sky.width // 2
        offset = 30 * 2
        sky.alpha_composite(img.resize((512, 160), Image.LANCZOS), (cx - 256, offset - TOP_GUI * 2))
        try:
            font = ImageFont.truetype("DejaVuSans-Bold.ttf", 18)
        except OSError:
            font = ImageFont.load_default()
        text = "what's yours"
        lay = Image.new("RGBA", (220, 34), (0, 0, 0, 0))
        d = ImageDraw.Draw(lay)
        d.text((8, 8), text, font=font, fill=(62, 62, 0, 255))
        d.text((6, 6), text, font=font, fill=(255, 255, 0, 255))
        lay = lay.rotate(20, expand=True, resample=Image.BICUBIC)
        sx, sy = cx + 123 * 2, 69 * 2
        sky.alpha_composite(lay, (sx - lay.width // 2, sy - lay.height // 2))
        return sky
    a, b = one((112, 160, 250), (160, 196, 255)), one((20, 22, 40), (44, 40, 60))
    out = Image.new("RGBA", (1000, 600))
    out.paste(a, (0, 0)); out.paste(b, (0, 300))
    out.save(path)


def main():
    img = logo()
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    img.save(OUT, optimize=True)
    if "--preview" in sys.argv:
        preview(img, sys.argv[sys.argv.index("--preview") + 1])


if __name__ == "__main__":
    main()
