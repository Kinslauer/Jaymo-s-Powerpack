"""Generates the Xenoform assets: the Xeno-Band item, the Fallen Star block, and the aliens' 64x128 skins.
Rows 0-64 are the vanilla skin layout for the head, body, arms and legs (the hat layer is left clear); rows 64-128
hold each form's extra parts (see client/power/xenoform/XenoFormModels for the exact offsets). Every box is painted
face by face (lit from above, dark underneath, a darker back) and then decorated, and every alien wears the xeno
insignia, the band's green hourglass, somewhere on it. Everything is drawn from code (noise and simple shapes);
nothing is copied. Run from the project root:  python tools/gen_xenoform.py [preview]
"""
import json
import random
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "assets" / "jaymospowers"
rng = random.Random(2603)

W, H = 64, 128
CLEAR = (0, 0, 0, 0)
GREEN = (92, 240, 122, 255)        # the band's green
PALE_GREEN = (190, 255, 205, 255)
DEEP_GREEN = (12, 48, 24, 255)

# the vanilla humanoid parts: (u, v, width, height, depth)
HEAD = (0, 0, 8, 8, 8)
BODY = (16, 16, 8, 12, 4)
RARM = (40, 16, 4, 12, 4)
LARM = (32, 48, 4, 12, 4)
RLEG = (0, 16, 4, 12, 4)
LLEG = (16, 48, 4, 12, 4)
LIMBS = (RARM, LARM, RLEG, LLEG)
ARMS = (RARM, LARM)
LEGS = (RLEG, LLEG)
BASE_PARTS = (HEAD, BODY) + LIMBS

# how each face of a box is lit
LIGHT = {"top": 1.18, "bottom": 0.6, "right": 0.86, "left": 0.86, "front": 1.0, "back": 0.78}


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n")


def save(img, rel):
    path = ROOT / "textures" / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)


def shade(color, amount):
    return tuple(max(0, min(255, int(c * amount))) for c in color[:3]) + (255,)


def lerp(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)


def faces(u, v, w, h, d):
    """The six faces of a box in the vanilla UV layout, as (x0, y0, x1, y1) boxes (x1, y1 exclusive)."""
    return {
        "top": (u + d, v, u + d + w, v + d),
        "bottom": (u + d + w, v, u + d + 2 * w, v + d),
        "right": (u, v + d, u + d, v + d + h),
        "front": (u + d, v + d, u + d + w, v + d + h),
        "left": (u + d + w, v + d, u + 2 * d + w, v + d + h),
        "back": (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),
    }


def front(part):
    return faces(*part)["front"]


def noise_fill(img, color, spread=0.22, box=None):
    x0, y0, x1, y1 = box or (0, 0, W, H)
    for y in range(y0, y1):
        for x in range(x0, x1):
            img.putpixel((x, y), shade(color, 1.0 - spread / 2 + rng.random() * spread))


def rect(img, box, color):
    if box[2] > box[0] and box[3] > box[1]:
        ImageDraw.Draw(img).rectangle([box[0], box[1], box[2] - 1, box[3] - 1], fill=color)


def outline(img, box, color):
    x0, y0, x1, y1 = box
    rect(img, (x0, y0, x1, y0 + 1), color)
    rect(img, (x0, y1 - 1, x1, y1), color)
    rect(img, (x0, y0, x0 + 1, y1), color)
    rect(img, (x1 - 1, y0, x1, y1), color)


def vgrad(img, box, top, bottom, spread=0.12):
    """A vertical gradient with a little grain."""
    x0, y0, x1, y1 = box
    for y in range(y0, y1):
        t = (y - y0) / max(1, y1 - y0 - 1)
        c = lerp(top, bottom, t)
        for x in range(x0, x1):
            img.putpixel((x, y), shade(c, 1.0 - spread / 2 + rng.random() * spread))


def speckle(img, box, color, n):
    x0, y0, x1, y1 = box
    for _ in range(n):
        img.putpixel((rng.randrange(x0, x1), rng.randrange(y0, y1)), color)


def veins(img, box, cold, hot, n, length=(3, 8)):
    """Wandering one-pixel veins kept inside the box."""
    x0, y0, x1, y1 = box
    for _ in range(n):
        x, y = rng.randrange(x0, x1), rng.randrange(y0, y1)
        heat = rng.random()
        for _ in range(rng.randrange(*length)):
            img.putpixel((x, y), lerp(cold, hot, heat * (0.6 + rng.random() * 0.4)))
            x = min(x1 - 1, max(x0, x + rng.choice((-1, 0, 1))))
            y = min(y1 - 1, max(y0, y + rng.choice((-1, 0, 1))))


def box(img, part, base, spread=0.2, edge=None, light=LIGHT):
    """Paints all six faces of a part, lit per face; `edge` darkens a one-pixel rim on each face (plated look)."""
    for name, b in faces(*part).items():
        c = shade(base, light[name])
        noise_fill(img, c, spread, b)
        if edge:
            outline(img, b, shade(c, edge))


def parts(img, base, spread=0.2, which=BASE_PARTS, **kw):
    for part in which:
        box(img, part, base, spread, **kw)


def clear_hat(img):
    rect(img, (32, 0, 64, 16), CLEAR)


def eyes(img, color, y=11, wide=False, pupil=None):
    """On the head's front face (8,8)-(16,16)."""
    if wide:
        rect(img, (9, y, 11, y + 2), color)
        rect(img, (13, y, 15, y + 2), color)
        if pupil:
            img.putpixel((10, y + 1), pupil)
            img.putpixel((13, y + 1), pupil)
    else:
        rect(img, (10, y, 11, y + 1), color)
        rect(img, (13, y, 14, y + 1), color)


def insignia(img, cx, top, small=False, fill=GREEN, rim=DEEP_GREEN, heart=PALE_GREEN):
    """The xeno insignia: an hourglass of band green with a pale heart and a dark rim, 5x7 (or 3x5 when small),
    its top-left pixel column centred on cx."""
    widths = (3, 1, 1, 1, 3) if small else (5, 3, 1, 1, 1, 3, 5)
    for i, w in enumerate(widths):
        y = top + i
        rect(img, (cx - w // 2 - 1, y, cx - w // 2 + w + 1, y + 1), rim)
    rect(img, (cx - widths[0] // 2, top - 1, cx + widths[0] // 2 + 1, top), rim)
    rect(img, (cx - widths[0] // 2, top + len(widths), cx + widths[0] // 2 + 1, top + len(widths) + 1), rim)
    for i, w in enumerate(widths):
        y = top + i
        rect(img, (cx - w // 2, y, cx - w // 2 + w, y + 1), fill)
    mid = top + len(widths) // 2
    img.putpixel((cx, mid), heart)
    if not small:
        img.putpixel((cx - 1, top), heart)
        img.putpixel((cx + 1, top + len(widths) - 1), heart)


def chest_insignia(img, **kw):
    """On the body's front face (20,20)-(28,32), low on the chest."""
    insignia(img, 24, 24, **kw)


def claws(img, color, which=LIMBS, rows=1):
    """Dark tips at the bottom of each limb's faces (hands and feet)."""
    for part in which:
        for name, (x0, y0, x1, y1) in faces(*part).items():
            if name in ("front", "back", "left", "right"):
                for x in range(x0, x1, 2):
                    rect(img, (x, y1 - rows, x + 1, y1), color)
            elif name == "bottom":
                noise_fill(img, color, 0.2, (x0, y0, x1, y1))


def new_skin():
    return Image.new("RGBA", (W, H))


# ---- the band (item, 16x16) ----
BAND = [
    "................",
    "................",
    ".....dddddd.....",
    "....dmmmmmmd....",
    "...dmggggggmd...",
    "...dmg.ll.gmd...",
    "..dmg.lGGl.gmd..",
    "..dmg.lGGl.gmd..",
    "..dmg.lGGl.gmd..",
    "..dmg.lGGl.gmd..",
    "...dmg.ll.gmd...",
    "...dmggggggmd...",
    "....dmmmmmmd....",
    ".....dddddd.....",
    "................",
    "................",
]
BAND_PALETTE = {
    ".": (0, 0, 0, 0),
    "d": (34, 40, 48, 255),      # dark rim
    "m": (120, 132, 148, 255),   # alien metal
    "g": (176, 186, 200, 255),   # bright metal
    "l": (28, 80, 44, 255),      # dial surround
    "G": (92, 240, 122, 255),    # the green dial face
}


def band():
    img = Image.new("RGBA", (16, 16))
    for y, row in enumerate(BAND):
        for x, ch in enumerate(row):
            img.putpixel((x, y), BAND_PALETTE[ch])
    save(img, "item/xeno_band.png")
    write_json(ROOT / "models" / "item" / "xeno_band.json",
               {"parent": "minecraft:item/generated", "textures": {"layer0": "jaymospowers:item/xeno_band"}})
    write_json(ROOT / "items" / "xeno_band.json", {"model": {"type": "minecraft:model", "model": "jaymospowers:item/xeno_band"}})


# (0.18.125, Xeno-Band patch 8) the rival band: the same band, its metal blackened red and its dial lit red
RIVAL_PALETTE = dict(BAND_PALETTE, d=(30, 12, 14, 255), m=(96, 36, 38, 255), g=(140, 60, 60, 255), l=(70, 16, 16, 255), G=(255, 60, 50, 255))


def rival_band():
    img = Image.new("RGBA", (16, 16))
    for y, row in enumerate(BAND):
        for x, ch in enumerate(row):
            img.putpixel((x, y), RIVAL_PALETTE[ch])
    save(img, "item/rival_band.png")
    write_json(ROOT / "models" / "item" / "rival_band.json",
               {"parent": "minecraft:item/generated", "textures": {"layer0": "jaymospowers:item/rival_band"}})
    write_json(ROOT / "items" / "rival_band.json", {"model": {"type": "minecraft:model", "model": "jaymospowers:item/rival_band"}})


# ---- the band on the wrist (entity, 32x32): a 5x3x5 box and a 1x5x5 face plate on its outer face, glowing in a second texture ----
# Close up in first person every texel shows, so it's kept plain: no outlines or grain, just a lit top row and a
# shaded bottom row on each side, and one small round dial.
def band_worn():
    img = Image.new("RGBA", (32, 32))
    glow = Image.new("RGBA", (32, 32))
    metal = (120, 132, 148)
    rim = (34, 40, 48, 255)
    for name, (x0, y0, x1, y1) in faces(0, 0, 5, 3, 5).items():
        rect(img, (x0, y0, x1, y1), shade(metal, LIGHT[name]))
        if name in ("front", "back", "left", "right"):
            rect(img, (x0, y0, x1, y0 + 1), shade(metal, LIGHT[name] * 1.15))
            rect(img, (x0, y1 - 1, x1, y1), shade(metal, LIGHT[name] * 0.7))
    # the outer face of a left wrist is the box's +x ("left") face: a round 3x3 dial, dark corners, a green ring, a pale heart
    x0, y0, x1, y1 = faces(0, 0, 5, 3, 5)["left"]
    rect(img, (x0 + 1, y0, x0 + 4, y1), (28, 80, 44, 255))
    for cx, cy in ((x0 + 1, y0), (x0 + 3, y0), (x0 + 1, y0 + 2), (x0 + 3, y0 + 2)):
        img.putpixel((cx, cy), rim)
    img.putpixel((x0 + 2, y0 + 1), PALE_GREEN)
    for cx, cy in ((x0 + 2, y0), (x0 + 1, y0 + 1), (x0 + 3, y0 + 1), (x0 + 2, y0 + 2)):
        glow.putpixel((cx, cy), (60, 200, 90, 255))
    glow.putpixel((x0 + 2, y0 + 1), PALE_GREEN)
    # the face plate (a 1x5x5 box at 0,10, proud of the band's outer face): the watch face you look at. A dark rim with
    # bright metal studs at the corners round a 3x3 green face; the face glows, and while the band is raised the dialled
    # alien's silhouette stands on it (XenoBand)
    for name, box in faces(0, 10, 1, 5, 5).items():
        rect(img, box, shade(metal, LIGHT[name]))
    x0, y0, x1, y1 = faces(0, 10, 1, 5, 5)["left"]
    rect(img, (x0, y0, x1, y1), rim)
    for cx, cy in ((x0, y0), (x1 - 1, y0), (x0, y1 - 1), (x1 - 1, y1 - 1)):
        img.putpixel((cx, cy), shade(metal, 1.3))
    rect(img, (x0 + 1, y0 + 1, x1 - 1, y1 - 1), (40, 150, 70, 255))
    rect(glow, (x0 + 1, y0 + 1, x1 - 1, y1 - 1), (92, 240, 122, 255))
    glow.putpixel((x0 + 2, y0 + 2), PALE_GREEN)
    save(img, "entity/xeno_band.png")
    save(glow, "entity/xeno_band_glow.png")


# ---- the hologram's glow (entity, 64x32): light fanning up out of the band, white so it takes the hologram's tint ----
# The left 32x32 is the quad's front face, the right half its back (the same), for a zero-depth box at texOffs(0, 0).
def holo_glow():
    img = Image.new("RGBA", (64, 32))
    for y in range(32):
        for x in range(32):
            # from the bottom middle: brightest at the band, fading up and out, a little narrower low down
            dx = (x + 0.5 - 16.0) / 16.0
            dy = (32.0 - (y + 0.5)) / 32.0
            r = (dx * dx / (0.35 + 0.65 * dy) + dy * dy) ** 0.5
            a = max(0.0, 1.0 - r) ** 1.8
            px = (255, 255, 255, int(round(255 * a)))
            img.putpixel((x, y), px)
            img.putpixel((x + 32, y), px)
    save(img, "entity/xeno_holo_glow.png")


# ---- the fallen star (block, 16x16) ----
def fallen_star():
    img = Image.new("RGBA", (16, 16))
    base = (38, 34, 46)
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), shade(base, 0.8 + rng.random() * 0.4))
    # veins of green light
    for _ in range(5):
        x, y = rng.randrange(16), rng.randrange(16)
        for _ in range(rng.randrange(4, 9)):
            img.putpixel((x % 16, y % 16), lerp((92, 240, 122), (220, 255, 230), rng.random() * 0.5))
            x += rng.choice((-1, 0, 1))
            y += rng.choice((-1, 0, 1))
    save(img, "block/fallen_star.png")
    write_json(ROOT / "models" / "block" / "fallen_star.json",
               {"parent": "minecraft:block/cube_all", "textures": {"all": "jaymospowers:block/fallen_star"}})
    write_json(ROOT / "blockstates" / "fallen_star.json", {"variants": {"": {"model": "jaymospowers:block/fallen_star"}}})
    write_json(ROOT / "models" / "item" / "fallen_star.json", {"parent": "jaymospowers:block/fallen_star"})
    write_json(ROOT / "items" / "fallen_star.json", {"model": {"type": "minecraft:model", "model": "jaymospowers:item/fallen_star"}})


# ---- the aliens (entity skins, 64x128) ----
def bulwark():
    """(0.19.84 a four-armed brute) Riveted steel plate, amber visor, the insignia stamped on the breastplate. Nothing
    is left of the man's layout: every box has its own place (see the table in the code), the chest, head, four arms,
    legs and feet each plated and bricked, darker iron on the forearms and shins, black knuckles and toes. Drawn on its
    own random stream, then the shared one is left exactly where the old skin left it (`stream_words` draws on), so the
    forms drawn after it come out as before."""
    # how many 32-bit draws the shared stream advanced across the old skin (the forms drawn after it keep their speckle)
    stream_words = 5008
    saved = rng.getstate()
    rng.seed(2683)
    img = new_skin()
    steel = (112, 122, 136)
    dark = shade(steel, 0.55)
    rivet = shade(steel, 1.45)
    heavy = shade(steel, 0.72)
    iron = shade(steel, 0.78)
    # every box: name -> (u, v, w, h, d); none overlaps another, all inside 64x128
    B = {
        "chest": (0, 0, 10, 8, 5), "head": (30, 0, 6, 6, 6), "spine": (0, 25, 2, 9, 2),
        "uaUR": (0, 13, 4, 8, 4), "uaUL": (16, 13, 4, 8, 4), "uaLR": (32, 13, 4, 8, 4), "uaLL": (48, 13, 4, 8, 4),
        "faUR": (8, 25, 5, 6, 5), "faUL": (28, 25, 5, 6, 5), "faLR": (0, 36, 5, 6, 5), "faLL": (20, 36, 5, 6, 5),
        "fiUR": (40, 36, 6, 4, 6), "fiUL": (0, 47, 6, 4, 6), "fiLR": (24, 47, 6, 4, 6), "fiLL": (0, 57, 6, 4, 6),
        "thighR": (24, 57, 3, 7, 3), "thighL": (36, 57, 3, 7, 3), "shinR": (48, 57, 3, 7, 3), "shinL": (0, 67, 3, 7, 3),
        "pauldR": (12, 67, 6, 3, 6), "pauldL": (36, 67, 6, 3, 6), "footR": (0, 77, 4, 3, 6), "footL": (20, 77, 4, 3, 6),
        "waist": (40, 77, 4, 5, 3), "belt": (0, 86, 6, 2, 4), "brow": (20, 86, 8, 2, 3), "jaw": (42, 86, 6, 2, 3),
        "tuskR": (60, 86, 1, 3, 1), "tuskL": (0, 92, 1, 3, 1),
    }
    plated = [n for n in B if not n.startswith(("tusk", "spine"))]
    for name in B:
        if name.startswith(("fa", "fi", "shin")):
            base = iron
        elif name.startswith(("pauld", "belt", "brow", "jaw", "spine", "foot")):
            base = heavy
        elif name.startswith("tusk"):
            base = shade(steel, 1.5)
        else:
            base = steel
        box(img, B[name], base, 0.15, edge=0.8)
    # plate seams on every side face, bricked, with a rivet where they cross
    for name in plated:
        for fname, (x0, y0, x1, y1) in faces(*B[name]).items():
            if fname in ("top", "bottom") or y1 - y0 < 4:
                continue
            for y in range(y0 + 3, y1 - 1, 4):
                rect(img, (x0, y, x1, y + 1), dark)
                off = ((y - y0) // 4) % 2 * 2
                for x in range(x0 + 1 + off, x1 - 1, 4):
                    img.putpixel((x, y), rivet)
                    if y + 1 < y1 - 1 and rng.random() < 0.5:
                        img.putpixel((x, y - 1), rivet)
    # rivets along the rims of the pauldrons, belt and brow
    for name in ("pauldR", "pauldL", "belt", "brow"):
        for fname, (x0, y0, x1, y1) in faces(*B[name]).items():
            if fname not in ("bottom", "top"):
                for x in range(x0 + 1, x1 - 1, 3):
                    img.putpixel((x, y0 + 1), rivet)
    # a bright rim along the top of the pauldrons and the belt
    for name in ("pauldR", "pauldL", "belt"):
        tx0, ty0, tx1, ty1 = faces(*B[name])["top"]
        rect(img, (tx0, ty0, tx1, ty1), shade(steel, 1.2))
    # black knuckles on the fists, black toes and claws on the feet
    for name in ("fiUR", "fiUL", "fiLR", "fiLL", "footR", "footL"):
        for fname, (x0, y0, x1, y1) in faces(*B[name]).items():
            if fname == "bottom":
                noise_fill(img, (36, 38, 44, 255), 0.2, (x0, y0, x1, y1))
            elif fname in ("front", "back", "left", "right"):
                for x in range(x0, x1, 2):
                    rect(img, (x, y1 - 1, x + 1, y1), (36, 38, 44, 255))
    for name in ("fiUR", "fiUL", "fiLR", "fiLL"):
        fx0, fy0, fx1, fy1 = faces(*B[name])["front"]
        for x in range(fx0 + 1, fx1 - 1, 2):
            img.putpixel((x, fy0 + 1), rivet)
    # a band of darker iron at the wrist, above the fist
    for name in ("faUR", "faUL", "faLR", "faLL"):
        for fname, (x0, y0, x1, y1) in faces(*B[name]).items():
            if fname in ("front", "back", "left", "right"):
                rect(img, (x0, y1 - 2, x1, y1 - 1), dark)
    # head: a jaw guard, a vertical seam down the brow, and a wide amber visor
    hx0, hy0, hx1, hy1 = faces(*B["head"])["front"]
    noise_fill(img, shade(steel, 0.7), 0.15, (hx0, hy1 - 1, hx1, hy1))
    rect(img, (hx0 + 2, hy0, hx0 + 4, hy0 + 2), dark)
    rect(img, (hx0, hy0 + 2, hx1, hy0 + 4), (40, 32, 20, 255))
    rect(img, (hx0 + 1, hy0 + 2, hx0 + 3, hy0 + 4), (255, 200, 60, 255))
    rect(img, (hx1 - 3, hy0 + 2, hx1 - 1, hy0 + 4), (255, 200, 60, 255))
    img.putpixel((hx0 + 1, hy0 + 2), (255, 240, 170, 255))
    img.putpixel((hx1 - 3, hy0 + 2), (255, 240, 170, 255))
    # neck bolts on the head's sides
    for fname in ("right", "left"):
        x0, y0, x1, y1 = faces(*B["head"])[fname]
        rect(img, (x0 + 2, y1 - 3, x0 + 4, y1 - 1), dark)
        img.putpixel((x0 + 2, y1 - 3), rivet)
    # the jaw guard's dark mouth line under the visor
    jx0, jy0, jx1, jy1 = faces(*B["jaw"])["front"]
    rect(img, (jx0 + 1, jy0, jx1 - 1, jy0 + 1), (30, 24, 16, 255))
    # the breastplate: a central ridge and the insignia under it; the back plate has a ridge too
    cx0, cy0, cx1, cy1 = faces(*B["chest"])["front"]
    rect(img, (cx0 + 4, cy0, cx0 + 6, cy1), shade(steel, 0.9))
    insignia(img, cx0 + 5, cy0 + 2, small=True)
    bx0, by0, bx1, by1 = faces(*B["chest"])["back"]
    rect(img, (bx0 + 4, by0, bx0 + 6, by1), dark)
    save(img, "entity/xenoform_bulwark.png")
    rng.setstate(saved)
    for _ in range(stream_words):
        rng.getrandbits(32)


# the vanilla fire ramp (campfire flames, blaze powder), hottest first
FIRE_WHITE = (254, 254, 251)
FIRE_PALE = (249, 235, 171)
FIRE_YELLOW = (239, 205, 86)
FIRE_GOLD = (233, 189, 57)
FIRE_AMBER = (223, 162, 27)
FIRE_ORANGE = (201, 108, 3)
FIRE_RED = (177, 63, 0)
FIRE_EMBER = (139, 52, 1)


def tongues(img, b, n, length=(3, 7)):
    """Licks of flame rising up a face: a pale root, yellow, gold at the tip, wavering a pixel side to side."""
    x0, y0, x1, y1 = b
    for _ in range(n):
        x = rng.randrange(x0, x1)
        y = rng.randrange(y0 + (y1 - y0) // 2, y1)
        size = rng.randrange(*length)
        for i in range(size):
            if y < y0:
                break
            t = i / max(1, size - 1)
            c = lerp(FIRE_PALE, FIRE_YELLOW, t * 2) if t < 0.5 else lerp(FIRE_YELLOW, FIRE_GOLD, t * 2 - 1)
            img.putpixel((x, y), c)
            y -= 1
            if rng.random() < 0.3:
                x = min(x1 - 1, max(x0, x + rng.choice((-1, 1))))


def fire_body(img, part):
    """A limb of living flame: gold at the heart of each face, orange to the sides, tongues licking up it, a ragged
    red edge so the shape still reads, and the hottest flame at the hands and feet."""
    for name, (x0, y0, x1, y1) in faces(*part).items():
        light = 1.0 + (LIGHT[name] - 1.0) * 0.4   # fire is its own light: only a hint of shading
        w = x1 - x0
        for y in range(y0, y1):
            for x in range(x0, x1):
                edge = abs((x - x0 + 0.5) / w - 0.5) * 2.0   # 0 at the middle, 1 at the sides
                c = lerp(FIRE_AMBER, FIRE_RED, edge * 1.2 + rng.random() * 0.45 - 0.15)
                img.putpixel((x, y), shade(c, light))
        if name in ("top", "bottom"):
            continue
        speckle(img, (x0, y0, x1, y1), FIRE_EMBER + (255,), w * (y1 - y0) // 12)
        tongues(img, (x0, y0, x1, y1), max(2, w // 2 + (y1 - y0) // 4))
        for y in range(y0, y1):
            for x in (x0, x1 - 1):
                if rng.random() < 0.35:
                    img.putpixel((x, y), shade(FIRE_RED, 1.0 - rng.random() * 0.2))


def flame_part(img, part):
    """A loose flame (hair, plumes, the licks off the arms): white-hot at the root, red at ragged tips; its
    top and bottom faces see-through."""
    for name, (x0, y0, x1, y1) in faces(*part).items():
        if name in ("top", "bottom"):
            rect(img, (x0, y0, x1, y1), CLEAR)
            continue
        h = y1 - y0
        for y in range(y0, y1):
            t = (y - y0) / max(1, h - 1)   # 0 at the tip, 1 at the root
            for x in range(x0, x1):
                c = lerp(FIRE_RED, FIRE_GOLD, t * 1.6 + rng.random() * 0.3 - 0.15) if t < 0.6 else lerp(FIRE_GOLD, FIRE_PALE, (t - 0.6) * 2.5)
                img.putpixel((x, y), c)
        # ragged tips: the top row or two eaten away at random, a spark of white low down
        for x in range(x0, x1):
            if rng.random() < 0.5:
                rect(img, (x, y0, x + 1, y0 + 1 + (rng.random() < 0.4)), CLEAR)
            if rng.random() < 0.3:
                img.putpixel((x, y1 - 1 - rng.randrange(0, 2)), FIRE_WHITE + (255,))


def pyre():
    """A body of living flame (vanilla's fire colours): gold-hearted limbs licked by tongues of fire, white-hot eyes,
    hair of fire streaming off the head, flames off the shoulders and forearms, and the insignia burning on the
    core. A second, emissive skin (xenoform_pyre_glow) makes it shine in the dark."""
    global rng
    shared, rng = rng, random.Random(26034)
    img = new_skin()
    for part in BASE_PARTS:
        fire_body(img, part)
    # hands and feet: the hottest flame
    for part in LIMBS:
        for name, (x0, y0, x1, y1) in faces(*part).items():
            if name == "bottom":
                noise_fill(img, FIRE_PALE, 0.08, (x0, y0, x1, y1))
            elif name != "top":
                for x in range(x0, x1):
                    img.putpixel((x, y1 - 1), lerp(FIRE_PALE, FIRE_WHITE, rng.random()))
                    if rng.random() < 0.5:
                        img.putpixel((x, y1 - 2), FIRE_YELLOW + (255,))
    # the face: a darker mask of flame round white-hot eyes, a brow, a faint mouth
    hx0, hy0, hx1, hy1 = front(HEAD)
    for y in range(hy0 + 2, hy1):
        for x in range(hx0 + 1, hx1 - 1):
            img.putpixel((x, y), lerp(FIRE_RED, FIRE_ORANGE, rng.random() * 0.8))
    rect(img, (9, 10, 15, 11), FIRE_EMBER + (255,))
    rect(img, (9, 11, 11, 12), FIRE_WHITE + (255,))
    rect(img, (13, 11, 15, 12), FIRE_WHITE + (255,))
    rect(img, (9, 12, 11, 13), FIRE_PALE + (255,))
    rect(img, (13, 12, 15, 13), FIRE_PALE + (255,))
    rect(img, (11, 14, 13, 15), FIRE_EMBER + (255,))
    # the head's crown of flame burns down over the brow
    for x in range(hx0, hx1):
        if rng.random() < 0.6:
            img.putpixel((x, hy0 + 1), FIRE_YELLOW + (255,))
    # the chest round the core glows paler, as if the fire burns hottest there
    bx0, by0, bx1, by1 = front(BODY)
    for y in range(by0 + 1, by0 + 9):
        for x in range(bx0 + 1, bx1 - 1):
            if rng.random() < 0.55:
                img.putpixel((x, y), lerp(FIRE_YELLOW, FIRE_PALE, rng.random()))
    # extras: crown 8x2x8 (0,64), hair 4x8x4 (32,64), hair tip 2x5x2 (48,64), plumes 3x6x3 (0,78),
    # core 5x5x1 (12,78), forearm flames 2x5x2 (24,78)
    for part in ((0, 64, 8, 2, 8), (32, 64, 4, 8, 4), (48, 64, 2, 5, 2), (0, 78, 3, 6, 3), (24, 78, 2, 5, 2)):
        flame_part(img, part)
    core = (12, 78, 5, 5, 1)
    box(img, core, FIRE_PALE, 0.08)
    cx0, cy0, cx1, cy1 = front(core)
    insignia(img, cx0 + 2, cy0, small=True, fill=GREEN, rim=FIRE_RED + (255,), heart=(240, 255, 240, 255))
    clear_hat(img)
    save(img, "entity/xenoform_pyre.png")
    # the glow: drawn again over the body, additive and fullbright, so the hot flame shines and the rest smoulders
    glow = Image.new("RGBA", (W, H), CLEAR)
    for y in range(H):
        for x in range(W):
            r, g, b, a = img.getpixel((x, y))
            if a:
                lum = (r + g + b) / 3
                glow.putpixel((x, y), shade((r, g, b), 0.85 if lum > 175 else 0.35))
    save(glow, "entity/xenoform_pyre_glow.png")
    rng = shared
    _retired_pyre_noise()


def _retired_pyre_noise():
    """Pyre's old rock skin, painted and thrown away: it keeps the shared noise where it was, so the aliens drawn
    after Pyre come out exactly as before. The new Pyre (below) draws from its own noise."""
    img = new_skin()
    rock = (50, 28, 22)
    char = (24, 14, 12, 255)
    ember = (255, 90, 20)
    white = (255, 230, 120)
    parts(img, rock, 0.3)
    # magma cracks: dense on the chest and forearms, sparse elsewhere; a char rim around them
    for part in BASE_PARTS:
        for name, b in faces(*part).items():
            n = 2 if name in ("top", "bottom") else 4
            if part is BODY or part in ARMS:
                n += 3
            veins(img, b, ember, white, n, (2, 6))
    # obsidian plates on the shoulders and thighs (the top rows of the limbs), glassy highlights
    for part in LIMBS:
        for name, (x0, y0, x1, y1) in faces(*part).items():
            if name in ("front", "back", "left", "right"):
                noise_fill(img, (28, 18, 32), 0.2, (x0, y0, x1, y0 + 3))
                img.putpixel((x0, y0), (110, 80, 120, 255))
    # hands and feet glow from inside
    for part in LIMBS:
        for name, (x0, y0, x1, y1) in faces(*part).items():
            if name == "bottom":
                noise_fill(img, ember, 0.3, (x0, y0, x1, y1))
            elif name != "top":
                veins(img, (x0, y1 - 3, x1, y1), ember, white, 2, (2, 4))
    # head: a cracked mask, the cracks running to pale wide eyes and a glowing mouth
    hx0, hy0, hx1, hy1 = front(HEAD)
    rect(img, (hx0 + 3, hy0, hx0 + 5, hy0 + 3), char)
    veins(img, (hx0, hy0, hx1, hy1), ember, white, 3, (2, 4))
    rect(img, (9, 11, 15, 13), char)
    eyes(img, (255, 250, 200, 255), 11, wide=True)
    rect(img, (10, 14, 14, 15), (255, 150, 40, 255))
    img.putpixel((12, 14), (255, 240, 170, 255))
    for (x0, y0, x1, y1) in (faces(*HEAD)["right"], faces(*HEAD)["left"]):
        veins(img, (x0, y0, x1, y1), ember, white, 3, (3, 6))
    # the core sits in front of the chest: the chest around it is burnt black with cracks radiating out
    bx0, by0, bx1, by1 = front(BODY)
    noise_fill(img, (30, 16, 14), 0.2, (bx0 + 1, by0 + 2, bx1 - 1, by0 + 9))
    veins(img, (bx0, by0 + 1, bx1, by0 + 10), ember, white, 5, (2, 5))
    # extras: crest 3x7x4 at (0,64) + bar 8x3x2 at (14,64); plumes 3x6x3 at (0,76); core 5x5x1 at (12,76)
    for part in ((0, 64, 3, 7, 4), (14, 64, 8, 3, 2), (0, 76, 3, 6, 3)):
        for name, (x0, y0, x1, y1) in faces(*part).items():
            # flame: a yellow heart low down, orange, red tips at the top, ragged
            vgrad(img, (x0, y0, x1, y1), (230, 60, 20), (255, 220, 110), 0.25)
            if name in ("front", "back", "left", "right"):
                for x in range(x0, x1):
                    if rng.random() < 0.4:
                        rect(img, (x, y0, x + 1, y0 + 1 + rng.randrange(0, 2)), CLEAR)
                    if rng.random() < 0.3:
                        img.putpixel((x, y1 - 1 - rng.randrange(0, 2)), (255, 250, 200, 255))
    core = (12, 76, 5, 5, 1)
    box(img, core, (255, 236, 170), 0.1)
    cx0, cy0, cx1, cy1 = front(core)
    insignia(img, cx0 + 2, cy0, small=True, fill=GREEN, rim=(255, 140, 40, 255), heart=(240, 255, 240, 255))
    clear_hat(img)


def chevrons(img, face, color, step=3, skip=0):
    """Pale chevrons (V shapes, point down) stacked down a face."""
    x0, y0, x1, y1 = face
    mid = (x0 + x1 - 1) / 2.0
    for top in range(y0 + 1 + skip, y1 - 1, step):
        for x in range(x0, x1):
            y = top + int(abs(x - mid))
            if y0 <= y < y1 - 1:
                img.putpixel((x, y), color)


def blur():
    """(0.19.62) Velox, the raptor: a dark blue-black hide with pale chevron stripes down the back, legs and neck, a pale
    belly and throat, a pale cyan glass visor, glossy near-black clawed feet, a swept crest, and the insignia on its back."""
    img = new_skin()
    hide = (28, 40, 72)
    pale = (222, 234, 250)
    glass = (150, 226, 242)
    dark = (14, 18, 34)
    bone = (232, 230, 214)
    parts(img, hide, 0.16)
    # the hat has no cubes now, and the visor and crests sit in its old corner: clear it before they're painted
    clear_hat(img)

    def chev(part, step, skip_faces=("top", "bottom"), skip=0):
        for name, b in faces(*part).items():
            if name in skip_faces:
                continue
            chevrons(img, b, shade(pale, LIGHT[name]), step, skip)

    # extras, as (u, v, w, h, d): skull (0,0) 4x4x6; visor (20,0) 5x2x4; crest (38,0) 1x2x4; sweep (48,0) 1x2x6;
    # snout (0,10) 3x2x5; jaw (0,17) 3x1x7; torso (0,26) 5x5x8; chest (26,26) 4x4x5; neck (44,26) 3x5x3 and
    # (56,26) 2x5x2; tail (0,40) 4x3x7, (22,40) 3x2x7, (42,40) 2x2x6, (0,51) 2x1x4; arms (20,51) and (28,51) 2x5x2;
    # hands (36,51) and (44,51) 2x3x2; claws (52,51) and (56,51) 1x2x1; thighs (0,64) and (14,64) 3x7x4; shins (28,64)
    # and (38,64) 2x8x3; tarsi (48,64) and (56,64) 2x6x2; feet (0,76) and (18,76) 4x2x5; sickles (36,76) and (44,76) 1x1x3
    skull = (0, 0, 4, 4, 6)
    box(img, skull, hide, 0.14)
    sx0, sy0, sx1, sy1 = faces(*skull)["top"]
    rect(img, (sx0 + 1, sy0, sx0 + 3, sy1), shade(pale, 0.8))
    rect(img, (faces(*skull)["bottom"][0], faces(*skull)["bottom"][1], faces(*skull)["bottom"][2], faces(*skull)["bottom"][3]), shade(pale, 0.7))
    # cool cyan eyes on the sides of the skull, for when the visor is up
    for name, ex in (("right", 3), ("left", 11)):
        fx0, fy0, fx1, fy1 = faces(*skull)[name]
        rect(img, (ex, fy0 + 1, ex + 2, fy0 + 2), (190, 250, 255, 255))
        img.putpixel((ex + (1 if name == "right" else 0), fy0 + 1), (20, 28, 50, 255))
    visor = (20, 0, 5, 2, 4)
    box(img, visor, glass, 0.06, edge=0.7)
    vx0, vy0, vx1, vy1 = faces(*visor)["front"]
    for i in range(2):
        img.putpixel((vx0 + 1 + i * 2, vy1 - 1), (240, 255, 255, 255))
    rect(img, (vx0, vy0, vx0 + 2, vy0 + 1), (240, 255, 255, 255))
    for part in ((38, 0, 1, 2, 4), (48, 0, 1, 2, 6)):
        for name, (x0, y0, x1, y1) in faces(*part).items():
            vgrad(img, (x0, y0, x1, y1), shade(pale, 0.95), shade(hide, 1.1), 0.12)
            if name in ("left", "right"):
                for x in range(x0, x1, 2):
                    rect(img, (x, y0, x + 1, y0 + 1), CLEAR)
    snout = (0, 10, 3, 2, 5)
    box(img, snout, shade(hide, 1.15), 0.12)
    nx0, ny0, nx1, ny1 = faces(*snout)["front"]
    img.putpixel((nx0, ny0), (8, 10, 20, 255))
    img.putpixel((nx1 - 1, ny0), (8, 10, 20, 255))
    for name in ("left", "right"):
        x0, y0, x1, y1 = faces(*snout)[name]
        for x in range(x0 + 1, x1, 2):
            img.putpixel((x, y1 - 1), bone + (255,))
    jaw = (0, 17, 3, 1, 7)
    box(img, jaw, shade(pale, 0.75), 0.1)
    jx0, jy0, jx1, jy1 = faces(*jaw)["top"]
    rect(img, (jx0, jy0, jx1, jy1), (90, 30, 44, 255))
    for name in ("left", "right"):
        x0, y0, x1, y1 = faces(*jaw)[name]
        for x in range(x0 + 1, x1, 2):
            img.putpixel((x, y0), bone + (255,))
    # the torso: a dark back with the insignia, a pale belly
    torso = (0, 26, 5, 5, 8)
    box(img, torso, hide, 0.16)
    chev(torso, 4, skip_faces=("top", "bottom", "front"), skip=0)
    tx0, ty0, tx1, ty1 = faces(*torso)["top"]
    noise_fill(img, shade(hide, 0.8), 0.14, (tx0, ty0, tx1, ty1))
    insignia(img, tx0 + 2, ty0 + 1, small=True, rim=(10, 16, 36, 255))
    bx0, by0, bx1, by1 = faces(*torso)["bottom"]
    noise_fill(img, shade(pale, 0.85), 0.1, (bx0, by0, bx1, by1))
    for name in ("left", "right"):
        x0, y0, x1, y1 = faces(*torso)[name]
        rect(img, (x0, y1 - 1, x1, y1), shade(pale, 0.8))
    chest = (26, 26, 4, 4, 5)
    box(img, chest, hide, 0.14)
    for name, (x0, y0, x1, y1) in faces(*chest).items():
        if name in ("front", "bottom"):
            vgrad(img, (x0, y0, x1, y1), shade(pale, 0.9), shade(pale, 0.7), 0.08)
        elif name in ("left", "right"):
            rect(img, (x0, y1 - 1, x1, y1), shade(pale, 0.8))
    for part in ((44, 26, 3, 5, 3), (56, 26, 2, 5, 2)):
        box(img, part, hide, 0.14)
        chev(part, 3, skip_faces=("top", "bottom"), skip=0)
        for name in ("front", "bottom"):
            x0, y0, x1, y1 = faces(*part)[name]
            noise_fill(img, shade(pale, 0.8 if name == "front" else 0.6), 0.1, (x0, y0, x1, y1))
    # the tail: banded in pale, the tip bright
    for part in ((0, 40, 4, 3, 7), (22, 40, 3, 2, 7), (42, 40, 2, 2, 6), (0, 51, 2, 1, 4)):
        box(img, part, hide, 0.16)
        for name, (x0, y0, x1, y1) in faces(*part).items():
            if name in ("top", "bottom"):
                continue
            for y in range(y0 + 1, y1, 3):
                rect(img, (x0, y, x1, y + 1), shade(pale, LIGHT[name]))
    tip = (0, 51, 2, 1, 4)
    rect(img, faces(*tip)["back"], (190, 250, 255, 255))
    for name in ("left", "right"):
        x0, y0, x1, y1 = faces(*tip)[name]
        rect(img, (x1 - 1, y0, x1, y1), (190, 250, 255, 255))
    # arms: hide above, dark hands, bone claws
    for arm, hand, claw in (((20, 51, 2, 5, 2), (36, 51, 2, 3, 2), (52, 51, 1, 2, 1)), ((28, 51, 2, 5, 2), (44, 51, 2, 3, 2), (56, 51, 1, 2, 1))):
        box(img, arm, hide, 0.14)
        chev(arm, 3)
        box(img, hand, (26, 30, 48), 0.1)
        box(img, claw, bone, 0.08, edge=0.85)
    # legs: hide thigh and shin with pale chevrons, scaly dark tarsus, glossy near-black feet with bone claws
    for thigh, shin, tars, foot, sick in (((0, 64, 3, 7, 4), (28, 64, 2, 8, 3), (48, 64, 2, 6, 2), (0, 76, 4, 2, 5), (36, 76, 1, 1, 3)),
                                           ((14, 64, 3, 7, 4), (38, 64, 2, 8, 3), (56, 64, 2, 6, 2), (18, 76, 4, 2, 5), (44, 76, 1, 1, 3))):
        box(img, thigh, hide, 0.16)
        chev(thigh, 3, skip=1)
        box(img, shin, hide, 0.16)
        chev(shin, 3)
        box(img, tars, (36, 44, 72), 0.12)
        for name, (x0, y0, x1, y1) in faces(*tars).items():
            if name in ("top", "bottom"):
                continue
            for y in range(y0 + 1, y1, 2):
                rect(img, (x0, y, x1, y + 1), shade(pale, 0.5 * LIGHT[name]))
        box(img, foot, (16, 18, 28), 0.08, edge=1.9)
        fx0, fy0, fx1, fy1 = faces(*foot)["front"]
        for x in range(fx0, fx1, 2):
            rect(img, (x, fy1 - 1, x + 1, fy1), bone + (255,))
        tx0, ty0, tx1, ty1 = faces(*foot)["top"]
        img.putpixel((tx0 + 1, ty0 + 1), (150, 170, 215, 255))
        box(img, sick, bone, 0.08, edge=0.85)
    save(img, "entity/xenoform_blur.png")


def prism():
    """Faceted violet crystal, white-hot tips, fracture lines. (0.19.82) A crystal cluster on shard legs: a core slab with
    a glow in its sides and the insignia set in its top like an inclusion, amethyst prisms tilted up out of it, and thin
    pointed legs."""
    img = new_skin()
    crystal = (146, 98, 208)
    bright = (236, 220, 255, 255)
    glow = (222, 190, 255)
    fracture = shade(crystal, 0.5)
    parts(img, crystal, 0.12)
    # facets: diagonal bands of light and shade over every face, each face lit its own way
    for part in BASE_PARTS:
        for name, (x0, y0, x1, y1) in faces(*part).items():
            base = shade(crystal, LIGHT[name])
            for y in range(y0, y1):
                for x in range(x0, x1):
                    b = ((x - x0) + (y - y0)) // 4 % 3
                    img.putpixel((x, y), shade(base, (1.25, 1.0, 0.72)[b] * (0.95 + rng.random() * 0.1)))
            # a fracture line and a glint on each big face
            if name in ("front", "back", "left", "right") and (y1 - y0) >= 8:
                x = rng.randrange(x0, x1)
                for y in range(y0 + 1, y1 - 1):
                    img.putpixel((x, y), fracture)
                    x = min(x1 - 1, max(x0, x + rng.choice((-1, 0, 0, 1))))
                img.putpixel((rng.randrange(x0, x1), rng.randrange(y0, y1)), bright)
    # gauntlets and greaves of paler crystal, pointed tips at the fingers and toes
    for part in LIMBS:
        for name, (x0, y0, x1, y1) in faces(*part).items():
            if name in ("front", "back", "left", "right"):
                noise_fill(img, shade(crystal, 1.3), 0.1, (x0, y1 - 4, x1, y1 - 1))
                for x in range(x0, x1, 2):
                    img.putpixel((x, y1 - 1), bright)
                    img.putpixel((x, y1 - 4), fracture)
    # head: a geometric face, diamond white eyes, a dark fracture down one cheek
    rect(img, (9, 10, 15, 13), shade(crystal, 0.75))
    eyes(img, (255, 255, 255, 255), 11, wide=True)
    img.putpixel((10, 10), bright)
    img.putpixel((13, 10), bright)
    for y in range(13, 16):
        img.putpixel((14 - (y - 13) // 2, y), fracture)
    rect(img, (11, 14, 13, 15), shade(crystal, 0.6))
    chest_insignia(img, rim=(50, 24, 90, 255))

    def facets(part, base, step=3, bands=(1.25, 1.0, 0.72)):
        """Diagonal bands of light over each face of a part, lit per face."""
        for name, (x0, y0, x1, y1) in faces(*part).items():
            c = shade(base, LIGHT[name])
            for y in range(y0, y1):
                for x in range(x0, x1):
                    img.putpixel((x, y), shade(c, bands[((x - x0) + (y - y0)) // step % 3] * (0.95 + rng.random() * 0.1)))

    def paler(part, amount, sides=("front", "back", "left", "right")):
        """Blends the side faces toward white-violet, more toward the top of the box."""
        for name in sides:
            x0, y0, x1, y1 = faces(*part)[name]
            for y in range(y0, y1):
                t = 1.0 - (y - y0) / max(1, y1 - y0 - 1)
                for x in range(x0, x1):
                    r, g, b, a = img.getpixel((x, y))
                    img.putpixel((x, y), lerp((r, g, b), (246, 236, 255), t * amount))

    # extras (rows 64+): core slab 8x6x12 at (0,64); shard leg 3x4x3 at (40,64), 2x4x2 at (52,64), 1x4x1 at (60,64);
    # crystal bodies 4x11x4 (0,82), 3x8x3 (32,82), 3x7x3 (32,93), 3x6x3 (0,97); small crystal body 2x4x2 (40,76);
    # tips 2x3x2 (40,71), 1x3x1 (48,71), 1x2x1 (52,71). The six small crystals and the six legs share their boxes.
    # core: deep violet facets with a glow in the middle of every side, fracture lines, the insignia in the top
    core = (0, 64, 8, 6, 12)
    deep = (86, 54, 142)
    facets(core, deep, 4)
    for name, (x0, y0, x1, y1) in faces(*core).items():
        if name in ("top", "bottom"):
            continue
        cx, cy = (x0 + x1 - 1) / 2.0, (y0 + y1 - 1) / 2.0
        rx, ry = max(1.0, (x1 - x0) / 2.0), max(1.0, (y1 - y0) / 2.0)
        for y in range(y0, y1):
            for x in range(x0, x1):
                d = (((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2) ** 0.5
                k = max(0.0, 1.0 - d * 1.15) * 0.85
                if k > 0.05:
                    r, g, b, a = img.getpixel((x, y))
                    img.putpixel((x, y), lerp((r, g, b), glow, k))
        veins(img, (x0, y0, x1, y1), shade(deep, 1.1), (255, 245, 255), 2, (3, 6))
    # two glowing facets on its front, like eyes
    fx0, fy0, fx1, fy1 = faces(*core)["front"]
    rect(img, (fx0 + 1, fy0 + 2, fx0 + 3, fy0 + 4), (255, 255, 255, 255))
    rect(img, (fx1 - 3, fy0 + 2, fx1 - 1, fy0 + 4), (255, 255, 255, 255))
    # the underside dark, the top a clean facet for the insignia
    bx0, by0, bx1, by1 = faces(*core)["bottom"]
    noise_fill(img, shade(deep, 0.55), 0.15, (bx0, by0, bx1, by1))
    tx0, ty0, tx1, ty1 = faces(*core)["top"]
    noise_fill(img, shade(deep, 1.25), 0.1, (tx0, ty0, tx1, ty1))
    outline(img, (tx0, ty0, tx1, ty1), shade(deep, 0.8))
    insignia(img, (tx0 + tx1) // 2, ty0 + 3, rim=(50, 24, 90, 255))
    # crystal bodies and tips: pale-lit amethyst prisms, fracture lines, white tips
    bodies = ((0, 82, 4, 11, 4), (32, 82, 3, 8, 3), (32, 93, 3, 7, 3), (0, 97, 3, 6, 3), (40, 76, 2, 4, 2))
    tips = ((40, 71, 2, 3, 2), (48, 71, 1, 3, 1), (52, 71, 1, 2, 1))
    for part in bodies:
        facets(part, crystal, 3)
        paler(part, 0.35)
        for name in ("front", "back", "left", "right"):
            x0, y0, x1, y1 = faces(*part)[name]
            if y1 - y0 >= 6:
                x = rng.randrange(x0, x1)
                for y in range(y0 + 2, y1 - 1):
                    img.putpixel((x, y), fracture)
                    x = min(x1 - 1, max(x0, x + rng.choice((-1, 0, 0, 1))))
        bx0, by0, bx1, by1 = faces(*part)["bottom"]
        noise_fill(img, shade(crystal, 0.5), 0.1, (bx0, by0, bx1, by1))
    for part in tips:
        facets(part, (226, 210, 252), 2, (1.05, 0.95, 0.82))
        x0, y0, x1, y1 = faces(*part)["top"]
        noise_fill(img, (252, 248, 255), 0.04, (x0, y0, x1, y1))
        x0, y0, x1, y1 = faces(*part)["bottom"]
        noise_fill(img, shade(crystal, 0.7), 0.1, (x0, y0, x1, y1))
    # legs: dark violet at the hip, amethyst in the middle, a pale white-hot point
    facets((40, 64, 3, 4, 3), deep, 3)
    facets((52, 64, 2, 4, 2), crystal, 3)
    facets((60, 64, 1, 4, 1), (214, 190, 250), 2, (1.05, 0.95, 0.85))
    for part in ((40, 64, 3, 4, 3), (52, 64, 2, 4, 2), (60, 64, 1, 4, 1)):
        x0, y0, x1, y1 = faces(*part)["bottom"]
        noise_fill(img, (250, 244, 255), 0.04, (x0, y0, x1, y1))
        if part[2] > 1:
            for name in ("front", "back", "left", "right"):
                x0, y0, x1, y1 = faces(*part)[name]
                rect(img, (x0, y1 - 1, x1, y1), shade(crystal, 0.6))
    clear_hat(img)
    save(img, "entity/xenoform_prism.png")


def wisp():
    """A shroud of pale mist with folds, a deep hood with hollow eyes, sleeves and hem fraying away, and the insignia
    showing faintly through the chest."""
    img = new_skin()
    mist = (176, 216, 232)
    deep = (84, 124, 146)
    fold = shade(mist, 0.8)
    parts(img, mist, 0.2)
    # folds: wavering vertical streaks of shadow down the body and limbs
    for part in BASE_PARTS:
        for name, (x0, y0, x1, y1) in faces(*part).items():
            if name in ("top", "bottom"):
                continue
            for x in range(x0 + 1, x1, 3):
                xx = x
                for y in range(y0, y1):
                    img.putpixel((xx, y), fold if rng.random() < 0.8 else shade(mist, 0.9))
                    if rng.random() < 0.25:
                        xx = min(x1 - 1, max(x0, xx + rng.choice((-1, 1))))
    # the sleeves and the hem fray into nothing
    for part in LIMBS + (BODY,):
        for name, (x0, y0, x1, y1) in faces(*part).items():
            if name == "bottom":
                rect(img, (x0, y0, x1, y1), CLEAR)
            elif name != "top":
                for x in range(x0, x1):
                    if rng.random() < 0.6:
                        rect(img, (x, y1 - 1 - rng.randrange(0, 3), x + 1, y1), CLEAR)
    # a pale glow in the breast where the insignia shows through the cloth
    bx0, by0, bx1, by1 = front(BODY)
    for y in range(by0 + 3, by0 + 11):
        for x in range(bx0 + 1, bx1 - 1):
            if rng.random() < 0.5:
                img.putpixel((x, y), lerp(mist, (170, 240, 200), 0.4))
    chest_insignia(img, fill=(120, 230, 160, 255), rim=(90, 140, 150, 255), heart=(220, 255, 230, 255))
    # the head under the hood: a dim, featureless face, two dark pits
    noise_fill(img, deep, 0.2, front(HEAD))
    eyes(img, (16, 24, 36, 255), 11, wide=True)
    # extras: hood 10x10x10 at (0,64); tatters 8x7x4 at (0,86)
    hood = (0, 64, 10, 10, 10)
    for name, (x0, y0, x1, y1) in faces(*hood).items():
        vgrad(img, (x0, y0, x1, y1), shade(deep, LIGHT[name] * 1.1), shade(deep, LIGHT[name] * 0.8), 0.15)
        if name in ("front", "back", "left", "right"):
            for x in range(x0 + 1, x1, 3):
                rect(img, (x, y0 + 2, x + 1, y1), shade(deep, LIGHT[name] * 0.7))
            for x in range(x0, x1):
                if rng.random() < 0.4:
                    rect(img, (x, y1 - 1 - rng.randrange(0, 2), x + 1, y1), CLEAR)
    # the hood's front (10,74)-(20,84): a cavern with hollow eyes, a cold light deep in each
    hx0, hy0, hx1, hy1 = front(hood)
    noise_fill(img, (26, 40, 54), 0.25, (hx0 + 2, hy0 + 3, hx1 - 2, hy1))
    rect(img, (hx0 + 2, hy0 + 4, hx0 + 4, hy0 + 7), (8, 10, 18, 255))
    rect(img, (hx1 - 4, hy0 + 4, hx1 - 2, hy0 + 7), (8, 10, 18, 255))
    img.putpixel((hx0 + 3, hy0 + 6), (150, 230, 240, 255))
    img.putpixel((hx1 - 3, hy0 + 6), (150, 230, 240, 255))
    tatters = (0, 86, 8, 7, 4)
    for name, (x0, y0, x1, y1) in faces(*tatters).items():
        vgrad(img, (x0, y0, x1, y1), shade(mist, LIGHT[name]), shade(mist, LIGHT[name] * 0.85), 0.2)
        if name == "bottom":
            rect(img, (x0, y0, x1, y1), CLEAR)
        elif name != "top":
            for x in range(x0, x1):
                if rng.random() < 0.65:
                    rect(img, (x, y1 - 1 - rng.randrange(0, 4), x + 1, y1), CLEAR)
    clear_hat(img)
    save(img, "entity/xenoform_wisp.png")


GREMLIN_HEAD = (0, 0, 8, 7, 6)
GREMLIN_EYE = (32, 0, 4, 4, 3)
GREMLIN_JAW = (32, 8, 6, 1, 5)
GREMLIN_BODY = (16, 16, 5, 7, 3)
GREMLIN_ARM = (40, 16, 2, 7, 2)
GREMLIN_LEG = (0, 16, 2, 6, 2)
GREMLIN_FOOT = (0, 32, 3, 1, 4)


def gremlin():
    """A tiny grey bug-eyed imp that stands upright (gremlin_paint); its eyes' lit pixels are saved again as
    xenoform_gremlin_glow (drawn fullbright)."""
    img = new_skin()
    glow = new_skin()
    gremlin_paint(img, glow)
    save(img, "entity/xenoform_gremlin.png")
    save(glow, "entity/xenoform_gremlin_glow.png")


def gremlin_paint(img, glow):
    """Gremlin's skin onto `img` and its eyes' lit pixels onto `glow`: a mottled grey hide with a pale belly, thin arms
    and legs with black claws, broad pale feet, and a wide flat head with a thin toothy mouth and two great eyes that
    bulge out past the sides of it, yellow-green with a slit pupil lying on its side. The insignia is on its chest.
    Boxes: head 8x7x6 at (0,0), eye 4x4x3 at (32,0), jaw 6x1x5 at (32,8), body 5x7x3 at (16,16), arm 2x7x2 at (40,16),
    leg 2x6x2 at (0,16), foot 3x1x4 at (0,32); the left limbs and eye mirror the right's. Seeded by itself, so it paints
    the same skin alone or in a full run. Kingdoms' Ocellan (kingdoms/tools/gen_ocellan.py) are painted over this."""
    rng.seed("gremlin")
    grey = (118, 120, 126)
    belly = (168, 166, 164)
    pale = (150, 152, 150)
    wart = shade(grey, 0.72)
    rim = (34, 36, 40, 255)
    lit = (196, 240, 58, 255)
    lit_pale = (236, 255, 150, 255)
    lit_deep = (150, 200, 40, 255)
    pupil = (22, 28, 14, 255)
    mouth = (50, 22, 26, 255)
    tooth = (240, 236, 220, 255)
    head, eye, jaw = GREMLIN_HEAD, GREMLIN_EYE, GREMLIN_JAW
    body, arm, leg, foot = GREMLIN_BODY, GREMLIN_ARM, GREMLIN_LEG, GREMLIN_FOOT
    hide = (body, arm, leg)
    # warts and mottling everywhere
    parts(img, grey, 0.12, which=hide)
    for part in hide:
        for name, b in faces(*part).items():
            speckle(img, b, shade(grey, 0.84), (b[2] - b[0]) * (b[3] - b[1]) // 9)
    # a pale belly, the insignia on the chest over it, a dark line down the spine
    x0, y0, x1, y1 = front(body)
    vgrad(img, (x0, y0 + 2, x1, y1), belly, shade(belly, 0.86), 0.08)
    insignia(img, x0 + 2, y0 + 1, small=True, rim=shade(grey, 0.5))
    x0, y0, x1, y1 = faces(*body)["back"]
    rect(img, (x0 + 2, y0, x0 + 3, y1), shade(grey, 0.6))
    # knobbly elbows and knees, black claws on the hands, broad pale feet with dark toes
    for part, row in ((arm, 3), (leg, 2)):
        for name, (x0, y0, x1, y1) in faces(*part).items():
            if name in ("front", "back", "left", "right"):
                rect(img, (x0, y0 + row, x1, y0 + row + 1), wart)
    claws(img, (28, 26, 30, 255), which=(arm,))
    box(img, foot, pale, 0.16)
    x0, y0, x1, y1 = faces(*foot)["top"]
    for x in range(x0, x1):
        img.putpixel((x, y1 - 1), shade(pale, 0.55) if (x - x0) % 2 == 0 else shade(pale, 0.9))
    x0, y0, x1, y1 = front(foot)
    for x in range(x0, x1, 2):
        img.putpixel((x, y0), shade(pale, 0.5))
    # the head: a shade lighter, mottled, a dark brow line, two nostrils and a wide thin mouth with small teeth
    box(img, head, shade(grey, 1.08), 0.1)
    for name, b in faces(*head).items():
        speckle(img, b, shade(grey, 0.86), 5)
    x0, y0, x1, y1 = front(head)
    img.putpixel((x0 + 3, y0 + 3), shade(grey, 0.5))
    img.putpixel((x0 + 4, y0 + 3), shade(grey, 0.5))
    rect(img, (x0 + 1, y0 + 5, x1 - 1, y0 + 6), mouth)
    img.putpixel((x0 + 2, y0 + 5), tooth)
    img.putpixel((x0 + 5, y0 + 5), tooth)
    img.putpixel((x0, y0 + 4), shade(grey, 0.6))
    img.putpixel((x1 - 1, y0 + 4), shade(grey, 0.6))
    rect(img, (x0, y1 - 1, x1, y1), shade(belly, 0.95))
    # the jaw that drops on a bite: a pale chin, the dark of the mouth on top, lower teeth along its front
    box(img, jaw, shade(belly, 0.9), 0.14)
    rect(img, faces(*jaw)["top"], mouth)
    x0, y0, x1, y1 = front(jaw)
    for x in range(x0 + 1, x1, 2):
        img.putpixel((x, y0), tooth)
    # the eyes: dark bulbs, lit across the front (and round onto the outer side and the top), a slit pupil on its side
    box(img, eye, rim[:3], 0.12)
    f = faces(*eye)
    x0, y0, x1, y1 = f["front"]
    rect(img, (x0, y0, x1, y1), lit)
    rect(img, (x0, y1 - 1, x1, y1), lit_deep)
    img.putpixel((x0, y0), lit_pale)
    img.putpixel((x0 + 1, y0), lit_pale)
    rect(img, (x0 + 1, y0 + 2, x0 + 3, y0 + 3), pupil)
    sx0, sy0, sx1, sy1 = f["right"]
    rect(img, (sx1 - 2, sy0, sx1, sy1), lit)
    rect(img, (sx1 - 2, sy1 - 1, sx1, sy1), lit_deep)
    img.putpixel((sx1 - 2, sy0 + 2), pupil)
    tx0, ty0, tx1, ty1 = f["top"]
    rect(img, (tx0, ty1 - 1, tx1, ty1), lit_deep)
    for b in (f["front"], (sx1 - 2, sy0, sx1, sy1), (tx0, ty1 - 1, tx1, ty1)):
        for y in range(b[1], b[3]):
            for x in range(b[0], b[2]):
                px = img.getpixel((x, y))
                if px != pupil:
                    glow.putpixel((x, y), px)


def knell():
    """(0.19.84) Knell, the hunched deep-dark beast: sculk-dark hide threaded with cyan light, bone horns, a brow and
    claws of bone, a blind face with a faint glow, a glowing chest cavity behind two bone rib plates (the insignia
    at its heart, in the gap between the plates), sculk spurs on the hump and tendrils at the chin. Every box is its
    own (the man's old head/body/limb regions are reused for them, nothing shares a texel): the layout is the UV
    table in XenoFormModels.Knell's mesh (texOffs)."""
    img = new_skin()
    sculk = (20, 44, 52)
    cold = (31, 184, 200)
    hot = (120, 240, 250)
    bone = (206, 196, 176)
    deep = (8, 28, 34)
    uv = {
        "head": (0, 0, 8, 6, 8), "trunk": (0, 14, 11, 16, 7), "hump": (0, 37, 15, 6, 9), "neck": (36, 0, 6, 11, 6),
        "fore_r": (36, 17, 6, 10, 6), "fore_l": (0, 52, 6, 10, 6), "arm_r": (24, 52, 5, 11, 5), "arm_l": (44, 52, 5, 11, 5),
        "fist_r": (0, 68, 6, 4, 7), "fist_l": (26, 68, 6, 4, 7), "thigh_r": (0, 79, 5, 8, 5), "thigh_l": (20, 79, 5, 8, 5),
        "jaw": (0, 92, 7, 3, 6), "foot_r": (40, 79, 5, 2, 7), "foot_l": (40, 88, 5, 2, 7), "shin_r": (48, 33, 4, 7, 4),
        "shin_l": (26, 97, 4, 7, 4), "brow": (0, 101, 9, 2, 3), "growth_r": (48, 44, 3, 5, 3), "growth_l": (52, 68, 3, 5, 3),
        "rib_r": (42, 97, 4, 8, 1), "rib_l": (52, 97, 4, 8, 1), "horn_rb": (0, 106, 2, 6, 2), "horn_lb": (8, 106, 2, 6, 2),
        "horn_rt": (16, 106, 2, 5, 2), "horn_lt": (42, 106, 2, 5, 2), "tendril0": (32, 0, 1, 4, 1), "tendril1": (60, 0, 1, 4, 1),
        "tendril2": (32, 5, 1, 4, 1),
    }

    def hide(name, base=sculk, spread=0.3, n=2):
        """A box of sculk hide with wandering veins of light (fewer on its top and bottom)."""
        box(img, uv[name], base, spread)
        for face, b in faces(*uv[name]).items():
            veins(img, b, cold, hot, 1 if face in ("top", "bottom") else n, (2, 5))

    def banded(name, rows, color=bone):
        """Bone plates round the bottom `rows` of a limb box's four sides, in stripes, with a dark claw tip row; bone underneath."""
        for face, (x0, y0, x1, y1) in faces(*uv[name]).items():
            if face in ("front", "back", "left", "right"):
                for x in range(x0, x1, 2):
                    rect(img, (x, y1 - rows, x + 1, y1), shade(color, 0.95))
                    rect(img, (x + 1, y1 - rows, x + 2, y1), shade(color, 0.75))
                rect(img, (x0, y1 - 1, x1, y1), shade(color, 0.5))
            elif face == "bottom":
                noise_fill(img, color, 0.15, (x0, y0, x1, y1))

    # ---- the head: blind and smooth, a faint glow where the eyes would be; the mouth's roof glows ----
    hide("head", shade(sculk, 0.9), 0.2, 2)
    hx0, hy0, hx1, hy1 = faces(*uv["head"])["front"]
    noise_fill(img, shade(sculk, 0.9), 0.2, (hx0, hy0, hx1, hy1))
    rect(img, (hx0 + 1, hy0 + 2, hx1 - 1, hy0 + 4), (18, 90, 100, 255))
    rect(img, (hx0 + 2, hy0 + 3, hx1 - 2, hy0 + 4), (30, 140, 150, 255))
    rect(img, (hx0 + 1, hy1 - 1, hx1 - 1, hy1), shade(sculk, 0.55))
    noise_fill(img, (18, 90, 100), 0.4, faces(*uv["head"])["bottom"])
    # ---- the brow: a bone ridge, cracked ----
    box(img, uv["brow"], bone, 0.2, edge=0.7)
    for face, b in faces(*uv["brow"]).items():
        speckle(img, b, shade(bone, 0.55), 3)
    # ---- the jaw: sculk with bone teeth along its front, the inside glowing ----
    hide("jaw", shade(sculk, 0.8), 0.25, 1)
    for face, (x0, y0, x1, y1) in faces(*uv["jaw"]).items():
        if face in ("front", "left", "right"):
            for x in range(x0, x1, 2):
                rect(img, (x, y0, x + 1, y0 + 1), shade(bone, 1.05))
    vgrad(img, faces(*uv["jaw"])["top"], cold, hot, 0.2)
    # ---- the tendrils hanging from the chin: a lit tip ----
    for i in range(3):
        name = "tendril%d" % i
        box(img, uv[name], shade(sculk, 0.7), 0.2)
        for face, (x0, y0, x1, y1) in faces(*uv[name]).items():
            if face in ("front", "back", "left", "right"):
                rect(img, (x0, y1 - 1, x1, y1), cold + (255,))
    # ---- the horns: bone, ringed, pale at the tips ----
    for name in ("horn_rb", "horn_lb", "horn_rt", "horn_lt"):
        for face, (x0, y0, x1, y1) in faces(*uv[name]).items():
            top, low = ((236, 230, 214), shade(bone, 0.7)) if name.endswith("t") else (shade(bone, 1.05), shade(bone, 0.6))
            vgrad(img, (x0, y0, x1, y1), top, low, 0.1)
            for y in range(y0 + 2, y1, 2):
                rect(img, (x0, y, x1, y + 1), shade(bone, 0.6))
    # ---- the neck: thick sculk hide, banded with darker folds ----
    hide("neck", shade(sculk, 0.85), 0.3, 3)
    for face, (x0, y0, x1, y1) in faces(*uv["neck"]).items():
        if face in ("front", "back", "left", "right"):
            for y in range(y0 + 3, y1, 4):
                rect(img, (x0, y, x1, y + 1), shade(sculk, 0.55))
    # ---- the trunk: hide; the chest a glowing cavity (hidden behind the rib plates but for the gap between them) with the insignia at its heart ----
    hide("trunk", shade(sculk, 0.8), 0.3, 3)
    # (the face's top 5 rows lie inside the hump's overhang; the ribs hinge over rows 5-12, the plate's middle on row 9)
    fx0, fy0, fx1, fy1 = faces(*uv["trunk"])["front"]
    for y in range(fy0 + 5, fy0 + 13):
        for x in range(fx0, fx1):
            d = ((x - (fx0 + 5.0)) ** 2 + ((y - (fy0 + 9.0)) / 1.2) ** 2) ** 0.5
            t = max(0.0, 1.0 - d / 6.0)
            img.putpixel((x, y), lerp(deep, hot, t ** 1.6 * 0.8 + rng.random() * 0.08))
    rect(img, (fx0, fy0 + 4, fx1, fy0 + 5), shade(sculk, 0.6))
    rect(img, (fx0, fy0 + 13, fx1, fy0 + 14), shade(sculk, 0.55))
    # the belly below the chest: bone plates in bands
    for y in range(fy0 + 14, fy1, 3):
        rect(img, (fx0 + 1, y, fx1 - 1, y + 1), shade(bone, 0.75))
        rect(img, (fx0 + 1, y + 1, fx1 - 1, y + 2), shade(bone, 0.45))
    insignia(img, fx0 + 5, fy0 + 7, small=True, rim=(8, 24, 30, 255))
    # sculk growths (darker, with a bright centre) on the back
    kx0, ky0, kx1, ky1 = faces(*uv["trunk"])["back"]
    for _ in range(4):
        x, y = rng.randrange(kx0 + 1, kx1 - 1), rng.randrange(ky0 + 1, ky1 - 1)
        rect(img, (x - 1, y - 1, x + 2, y + 2), shade(sculk, 0.6))
        img.putpixel((x, y), hot + (255,))
    # ---- the hump: heavy shoulders, veined, with growths on top ----
    hide("hump", shade(sculk, 0.75), 0.25, 4)
    tx0, ty0, tx1, ty1 = faces(*uv["hump"])["top"]
    speckle(img, (tx0, ty0, tx1, ty1), shade(sculk, 0.5), 14)
    for _ in range(5):
        x, y = rng.randrange(tx0 + 1, tx1 - 1), rng.randrange(ty0 + 1, ty1 - 1)
        rect(img, (x - 1, y - 1, x + 2, y + 2), shade(sculk, 0.55))
        img.putpixel((x, y), cold + (255,))
    # ---- the sculk spurs on the hump: lit at the tip ----
    for name in ("growth_r", "growth_l"):
        for face, b in faces(*uv[name]).items():
            if face == "top":
                noise_fill(img, hot, 0.1, b)
            elif face == "bottom":
                noise_fill(img, shade(sculk, 0.6), 0.2, b)
            else:
                vgrad(img, b, lerp(cold, hot, 0.3), shade(sculk, 0.65), 0.15)
    # ---- the rib plates: bone bars with the glow seeping between ----
    for name in ("rib_r", "rib_l"):
        box(img, uv[name], bone, 0.15, edge=0.75)
        for face, (x0, y0, x1, y1) in faces(*uv[name]).items():
            if face in ("front", "back"):
                for y in range(y0 + 1, y1, 2):
                    rect(img, (x0, y, x1, y + 1), lerp(cold, hot, 0.3))
    # ---- the arms: long and heavy, veined, bone plates at the wrists and on the knuckles ----
    for name in ("arm_r", "arm_l"):
        hide(name, sculk, 0.3, 3)
    for name in ("fore_r", "fore_l"):
        hide(name, shade(sculk, 0.9), 0.3, 3)
        banded(name, 3)
    for name in ("fist_r", "fist_l"):
        hide(name, shade(sculk, 0.85), 0.25, 1)
        banded(name, 2)
        # knuckle studs along the front
        fx0, fy0, fx1, fy1 = faces(*uv[name])["front"]
        for x in range(fx0, fx1, 2):
            rect(img, (x, fy0, x + 1, fy0 + 2), shade(bone, 1.0))
    # ---- the legs: short and thick, veined, bone shins and clawed feet ----
    for name in ("thigh_r", "thigh_l"):
        hide(name, sculk, 0.3, 3)
    for name in ("shin_r", "shin_l"):
        hide(name, shade(sculk, 0.9), 0.3, 2)
        banded(name, 3)
    for name in ("foot_r", "foot_l"):
        box(img, uv[name], shade(sculk, 0.8), 0.25)
        for face, (x0, y0, x1, y1) in faces(*uv[name]).items():
            if face == "bottom":
                noise_fill(img, shade(bone, 0.6), 0.2, (x0, y0, x1, y1))
        # claws along the toes (the front face and the toe ends of the sides)
        fx0, fy0, fx1, fy1 = faces(*uv[name])["front"]
        rect(img, (fx0, fy0, fx1, fy1), bone)
        for x in range(fx0, fx1, 2):
            rect(img, (x, fy1 - 1, x + 1, fy1), shade(bone, 0.5))
    save(img, "entity/xenoform_knell.png")


def wyrm():
    """(0.19.75) Wyrm, a small four-legged dragon: dark violet scales in a brick pattern, pale belly plates under the
    chest, belly, neck and tail, bone horns, teeth and claws, violet slit eyes, a ridge of bone spines, a tail ending in a
    violet spade, and the insignia on the front of the chest. The man's parts are still painted (the first-person hand
    is his arm). Drawn on its own random stream, then the shared one is left exactly where the old skin left it
    (`stream_words` draws on), so the forms drawn after it come out as before."""
    # how many 32-bit draws the shared stream advanced across the old skin (the forms drawn after it keep their speckle)
    stream_words = 4902
    saved = rng.getstate()
    rng.seed(2629)
    img = new_skin()
    scale = (32, 24, 44)
    plate = (120, 100, 130)
    membrane = (112, 50, 152)
    bone = (190, 180, 170)
    dark = (26, 14, 36, 255)
    mouth = (104, 36, 66, 255)
    violet = (210, 90, 240, 255)

    def scales(part, belly=()):
        """The brick of lighter crescents on every face of a box; the faces named in `belly` are pale plates instead."""
        box(img, part, scale, 0.25)
        for name, (x0, y0, x1, y1) in faces(*part).items():
            if name in belly:
                for y in range(y0, y1, 2):
                    rect(img, (x0, y, x1, y + 1), shade(plate, (0.9 + ((y - y0) // 2) % 2 * 0.14) * LIGHT[name] ** 0.5))
                    rect(img, (x0, y + 1, x1, min(y + 2, y1)), shade(scale, 0.8))
                continue
            for y in range(y0, y1, 3):
                for x in range(x0 + (y // 3) % 2 * 2, x1, 4):
                    rect(img, (x, y, min(x + 2, x1), y + 1), shade(scale, 1.35 * LIGHT[name]))
                    if x + 1 < x1 and y + 1 < y1:
                        img.putpixel((x + 1, y + 1), shade(scale, 0.7))

    def ridge(part):
        """A darker band down the middle of a box's top, under the spines."""
        x0, y0, x1, y1 = faces(*part)["top"]
        rect(img, ((x0 + x1 - 1) // 2, y0, (x0 + x1 - 1) // 2 + 1, y1), shade(scale, 0.55))

    # ---- the man's parts, still painted (the first-person hand is his arm) ----
    for part in BASE_PARTS:
        scales(part, ("front",) if part in (BODY,) + LEGS else ())
    for face_box in (faces(*BODY)["back"], faces(*HEAD)["back"]):
        x0, y0, x1, y1 = face_box
        for y in range(y0, y1, 2):
            rect(img, (x0 + 3, y, x0 + 5, y + 1), bone)
    claws(img, bone)
    rect(img, (9, 9, 15, 10), shade(scale, 0.7))
    rect(img, (9, 10, 15, 13), shade(scale, 0.85))
    eyes(img, (210, 90, 240, 255), 11, wide=True, pupil=(20, 8, 24, 255))
    img.putpixel((11, 13), shade(scale, 0.5))
    img.putpixel((12, 13), shade(scale, 0.5))
    rect(img, (9, 14, 15, 15), shade(scale, 0.6))
    for x in range(9, 15, 2):
        img.putpixel((x, 14), bone)
    chest_insignia(img, rim=(30, 14, 40, 255))

    # ---- the dragon's boxes (u, v, w, h, d), all in rows 64-127 so nothing shares texels with the man's parts:
    # torso 7x6x11 at (0,64); chest 7x7x4 (36,64); tail 4x4x7 (0,81); tail 3x3x7 (22,81); spade 1x5x5 (42,81);
    # neck 3x3x6 (0,92); skull 5x4x5 (18,92); neck 3x3x5 (38,92); tail tip 2x2x6 (0,101); thigh 3x5x3 (16,101);
    # jaw 3x1x6 (28,101); horn 2x2x5 (46,101); shin 2x5x2 (0,109); snout 3x2x4 (8,109); foot 3x2x4 (22,109);
    # spade 5x1x4 (36,109); spike 1x2x2 (54,109). The four legs, the two horns and the nine spines each share one box's
    # texels (same shape, same skin) ----
    torso = (0, 64, 7, 6, 11)
    chest = (36, 64, 7, 7, 4)
    tail1 = (0, 81, 4, 4, 7)
    tail2 = (22, 81, 3, 3, 7)
    spade_v = (42, 81, 1, 5, 5)
    neck1 = (0, 92, 3, 3, 6)
    skull = (18, 92, 5, 4, 5)
    neck2 = (38, 92, 3, 3, 5)
    tail3 = (0, 101, 2, 2, 6)
    thigh = (16, 101, 3, 5, 3)
    jaw = (28, 101, 3, 1, 6)
    horn = (46, 101, 2, 2, 5)
    shin = (0, 109, 2, 5, 2)
    snout = (8, 109, 3, 2, 4)
    foot = (22, 109, 3, 2, 4)
    spade_h = (36, 109, 5, 1, 4)
    spike = (54, 109, 1, 2, 2)

    # torso, chest, neck and tail: scaled above, pale plates on the belly side
    for part in (torso, chest, neck1, neck2, tail1, tail2, tail3):
        scales(part, ("bottom",))
    for part in (torso, neck1, neck2, tail1, tail2):
        ridge(part)
    # the chest's front: plates across, and the insignia on it
    cx0, cy0, cx1, cy1 = faces(*chest)["front"]
    for y in range(cy0, cy1, 2):
        rect(img, (cx0, y, cx1, y + 1), shade(plate, 0.95 + ((y - cy0) // 2) % 2 * 0.14))
        rect(img, (cx0, y + 1, cx1, min(y + 2, cy1)), shade(scale, 0.8))
    insignia(img, (cx0 + cx1) // 2, cy0 + 1, small=True, rim=(30, 14, 40, 255))
    # a lighter stripe along each flank
    for part in (torso,):
        for name in ("left", "right"):
            x0, y0, x1, y1 = faces(*part)[name]
            rect(img, (x0, y0 + 2, x1, y0 + 3), shade(plate, 0.85))

    # the head: scales, a darker brow, violet slit eyes at the sides of the face, a bone crest
    scales(skull)
    scales(snout)
    sx0, sy0, sx1, sy1 = faces(*skull)["front"]
    rect(img, (sx0, sy0, sx1, sy0 + 1), dark)
    img.putpixel((sx0, sy0 + 1), violet)
    img.putpixel((sx1 - 1, sy0 + 1), violet)
    rx0, ry0, rx1, ry1 = faces(*skull)["right"]
    rect(img, (rx0, ry0, rx1, ry0 + 1), shade(scale, 0.6))
    rect(img, (rx1 - 3, ry0 + 1, rx1, ry0 + 2), violet)
    img.putpixel((rx1 - 2, ry0 + 1), dark)
    lx0, ly0, lx1, ly1 = faces(*skull)["left"]
    rect(img, (lx0, ly0, lx1, ly0 + 1), shade(scale, 0.6))
    rect(img, (lx0, ly0 + 1, lx0 + 3, ly0 + 2), violet)
    img.putpixel((lx0 + 1, ly0 + 1), dark)
    tx0, ty0, tx1, ty1 = faces(*skull)["top"]
    rect(img, ((tx0 + tx1 - 1) // 2, ty0, (tx0 + tx1 - 1) // 2 + 1, ty1), shade(bone, 0.7))
    # the skull's bottom row is the lip, dark
    for name in ("front", "left", "right", "back"):
        x0, y0, x1, y1 = faces(*skull)[name]
        rect(img, (x0, y1 - 1, x1, y1), shade(scale, 0.55))
    # the snout: nostrils, and a row of teeth along the upper lip
    nx0, ny0, nx1, ny1 = faces(*snout)["front"]
    img.putpixel((nx0, ny0), dark)
    img.putpixel((nx1 - 1, ny0), dark)
    for x in range(nx0, nx1, 2):
        img.putpixel((x, ny1 - 1), bone)
    for name in ("left", "right"):
        x0, y0, x1, y1 = faces(*snout)[name]
        for x in range(x0, x1, 2):
            img.putpixel((x, y1 - 1), bone)
    bx0, by0, bx1, by1 = faces(*snout)["bottom"]
    rect(img, (bx0, by0, bx1, by1), mouth)
    # the jaw: the inside of the mouth when it opens, with teeth along both edges; plates under it
    scales(jaw, ("bottom",))
    jx0, jy0, jx1, jy1 = faces(*jaw)["top"]
    rect(img, (jx0, jy0, jx1, jy1), mouth)
    for y in range(jy0 + 1, jy1, 2):
        img.putpixel((jx0, y), bone)
        img.putpixel((jx1 - 1, y), bone)
    # the horns: bone, ridged
    box(img, horn, bone, 0.15)
    for name, (x0, y0, x1, y1) in faces(*horn).items():
        if name in ("top", "bottom"):
            for x in range(x0 + 1, x1, 2):
                rect(img, (x, y0, x + 1, y1), shade(bone, 0.75))
        else:
            for y in range(y0, y1):
                for x in range(x0 + 1, x1, 2):
                    img.putpixel((x, y), shade(bone, 0.75))
        if name == "front":
            noise_fill(img, shade(bone, 0.6), 0.1, (x0, y0, x1, y1))
    # the spine ridge
    box(img, spike, bone, 0.12)
    for name, (x0, y0, x1, y1) in faces(*spike).items():
        if name in ("front", "back", "left", "right"):
            rect(img, (x0, y1 - 1, x1, y1), shade(bone, 0.65))

    # the legs: scaled, a pale patch on the front of the thigh, claws and dark soles on the feet
    for part in (thigh, shin, foot):
        scales(part)
    x0, y0, x1, y1 = faces(*thigh)["front"]
    rect(img, (x0, y0 + 1, x1, y1 - 1), shade(plate, 0.8))
    x0, y0, x1, y1 = faces(*shin)["front"]
    rect(img, (x0, y0 + 1, x1, y1 - 1), shade(plate, 0.7))
    for name, (x0, y0, x1, y1) in faces(*foot).items():
        if name in ("front", "left", "right", "back"):
            for x in range(x0, x1, 2):
                img.putpixel((x, y1 - 1), bone)
            for x in range(x0 + 1, x1, 2):
                img.putpixel((x, y1 - 1), shade(scale, 0.5))
        elif name == "bottom":
            noise_fill(img, (36, 28, 42, 255), 0.2, (x0, y0, x1, y1))

    # the tail's spade: a violet blade, bone along its edge, veined
    for part in (spade_v, spade_h):
        for name, (x0, y0, x1, y1) in faces(*part).items():
            vgrad(img, (x0, y0, x1, y1), shade(membrane, 1.1), shade(membrane, 0.7), 0.15)
            if x1 - x0 > 1 and y1 - y0 > 1:
                veins(img, (x0, y0, x1, y1), shade(membrane, 0.7), shade(membrane, 0.55), 2, (2, 4))
                rect(img, (x0, y0, x1, y0 + 1), dark)
            else:
                rect(img, (x0, y0, x1, y1), shade(bone, 0.5))
    clear_hat(img)
    save(img, "entity/xenoform_wyrm.png")
    rng.setstate(saved)
    for _ in range(stream_words):
        rng.getrandbits(32)


def nadir():
    """The void given shape: near-black with faint stars and nebula dust, and the green xeno insignia burning on the
    chest. (0.19.84) A mass of dark cubes drifting round a dim core: four sizes of cube (A 6, B 4, C 3, D 2 cubed, at
    (0,64) (0,76) (0,84) (0,90)) of star-dusted dark, and an 8x8x6 core at (0,96) carrying the insignia on its front.
    The base humanoid parts are still painted (the first-person hand)."""
    img = new_skin()
    void = (9, 7, 16)
    lit = {"top": 1.4, "bottom": 0.8, "right": 1.0, "left": 1.0, "front": 1.0, "back": 0.9}
    parts(img, void, 0.25, light=lit)
    for part in BASE_PARTS:
        for name, b in faces(*part).items():
            x0, y0, x1, y1 = b
            speckle(img, b, (90, 70, 140, 255), (x1 - x0) * (y1 - y0) // 10)
            speckle(img, b, (160, 140, 220, 255), (x1 - x0) * (y1 - y0) // 24)
    chest_insignia(img, rim=(8, 40, 24, 255))

    def nebula(b):
        x0, y0, x1, y1 = b
        for _ in range(2):
            cx, cy = rng.randrange(x0, x1), rng.randrange(y0, y1)
            tint = rng.choice(((50, 30, 90), (24, 40, 90), (70, 24, 70)))
            for y in range(y0, y1):
                for x in range(x0, x1):
                    d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
                    if d < 3.0 and rng.random() < 0.7:
                        img.putpixel((x, y), lerp(img.getpixel((x, y)), tint, 0.5 * (1 - d / 3.0)))

    # the drifting cubes: A 6x6x6, B 4x4x4, C 3x3x3, D 2x2x2, each a different shade of dark with a violet rim on top
    for part, base in (((0, 64, 6, 6, 6), (8, 6, 15)), ((0, 76, 4, 4, 4), (11, 8, 20)),
                       ((0, 84, 3, 3, 3), (14, 10, 26)), ((0, 90, 2, 2, 2), (18, 12, 32))):
        box(img, part, base, 0.3, light=lit)
        for name, b in faces(*part).items():
            x0, y0, x1, y1 = b
            nebula(b)
            speckle(img, b, (90, 70, 140, 255), max(1, (x1 - x0) * (y1 - y0) // 8))
            speckle(img, b, (200, 190, 240, 255), 1)
            if name == "top":
                rect(img, (x0, y0, x1, y0 + 1), (60, 40, 110, 255))
    # the core: 8x8x6 at (0,96), a deeper void with a violet haze, the insignia glowing on its front (8x8 at (6,102))
    core = (0, 96, 8, 8, 6)
    box(img, core, (4, 3, 10), 0.2, light=lit)
    for name, b in faces(*core).items():
        nebula(b)
        speckle(img, b, (90, 70, 140, 255), 6)
        if name == "top":
            rect(img, (b[0], b[1], b[2], b[1] + 1), (70, 50, 120, 255))
    fx0, fy0, fx1, fy1 = faces(*core)["front"]
    for y in range(fy0, fy1):
        for x in range(fx0, fx1):
            d = max(abs(x - (fx0 + fx1 - 1) / 2), abs(y - (fy0 + fy1 - 1) / 2) * 0.9)
            if d < 4.5:
                img.putpixel((x, y), lerp(img.getpixel((x, y)), (30, 90, 50), 0.6 * (1 - d / 4.5)))
    insignia(img, (fx0 + fx1) // 2, fy0 + 1, small=True, rim=(8, 40, 24, 255))
    clear_hat(img)
    save(img, "entity/xenoform_nadir.png")


# Duplex is built as a copper golem (XenoFormModels.Duplex), so it has its own layout in place of the vanilla one
DUPLEX_HEAD = (0, 0, 8, 5, 10)
DUPLEX_NOSE = (40, 0, 2, 3, 2)
DUPLEX_ROD = (48, 0, 2, 4, 2)
DUPLEX_KNOB = (40, 8, 4, 4, 4)
DUPLEX_BODY = (0, 16, 8, 6, 6)
DUPLEX_RARM = (28, 16, 3, 10, 4)
DUPLEX_LARM = (42, 16, 3, 10, 4)
DUPLEX_RLEG = (0, 32, 4, 5, 4)
DUPLEX_LLEG = (16, 32, 4, 5, 4)
DUPLEX_BELT = (0, 64, 8, 2, 6)
DUPLEX_PARTS = (DUPLEX_HEAD, DUPLEX_BODY, DUPLEX_RARM, DUPLEX_LARM, DUPLEX_RLEG, DUPLEX_LLEG)


def duplex():
    """Riveted copper gone green at the edges, on a copper golem's boxes: seams and rivets, verdigris creeping in
    patches, pale cyan eyes in a dark visor slot over the golem's nose, a darker copper belt with a bright buckle, the
    lightning rod with a polished ball tip, and a small insignia on its chest."""
    img = new_skin()
    copper = (190, 104, 66)
    seam = shade(copper, 0.62)
    rivet = (238, 170, 120, 255)
    verdigris = (88, 168, 136)
    parts(img, copper, 0.14, which=DUPLEX_PARTS)
    for part in DUPLEX_PARTS:
        for name, b in faces(*part).items():
            x0, y0, x1, y1 = b
            # a seam round each face, a rivet at its corners, and verdigris creeping in from an edge or two
            outline(img, b, seam)
            for (x, y) in ((x0, y0), (x1 - 1, y0), (x0, y1 - 1), (x1 - 1, y1 - 1)):
                if x1 - x0 > 2 and y1 - y0 > 2:
                    img.putpixel((x, y), rivet)
            # (smaller patches on the golem's small faces, so the copper still shows)
            small = min(x1 - x0, y1 - y0) < 5
            reach = 1.6 if small else 2.6
            for _ in range(1 if small else rng.randrange(1, 3)):
                cx, cy = rng.choice(((x0, rng.randrange(y0, y1)), (x1 - 1, rng.randrange(y0, y1)), (rng.randrange(x0, x1), y1 - 1)))
                for y in range(y0, y1):
                    for x in range(x0, x1):
                        d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
                        if d < reach and rng.random() < 0.75:
                            img.putpixel((x, y), lerp(img.getpixel((x, y)), verdigris + (255,), 0.85 * (1 - d / (reach + 0.6))))
            speckle(img, b, shade(copper, 1.18), max(1, (x1 - x0) * (y1 - y0) // 14))
    # a seam across the forearms, where the golem's hands begin
    for part in (DUPLEX_RARM, DUPLEX_LARM):
        for name, (x0, y0, x1, y1) in faces(*part).items():
            if name in ("front", "back", "left", "right"):
                rect(img, (x0, y0 + 6, x1, y0 + 7), seam)
    # the face (8x5): a dark visor slot with two pale cyan eyes above the nose
    fx0, fy0, fx1, fy1 = front(DUPLEX_HEAD)
    rect(img, (fx0 + 1, fy0 + 1, fx1 - 1, fy0 + 3), (40, 22, 16, 255))
    for ex in (fx0 + 2, fx0 + 5):
        rect(img, (ex, fy0 + 1, ex + 1, fy0 + 3), (170, 245, 240, 255))
        img.putpixel((ex, fy0 + 1), (235, 255, 255, 255))
    rect(img, (fx0 + 1, fy1 - 1, fx1 - 1, fy1), seam)
    box(img, DUPLEX_NOSE, shade(copper, 1.05), 0.1)
    # the insignia, small enough for the golem's short chest: a green hourglass with a pale heart
    cx0, cy0, cx1, cy1 = front(DUPLEX_BODY)
    mx = cx0 + 3
    rim = (60, 30, 18, 255)
    rect(img, (mx - 1, cy0 + 1, mx + 4, cy0 + 4), rim)
    rect(img, (mx, cy0 + 1, mx + 3, cy0 + 2), GREEN)
    img.putpixel((mx + 1, cy0 + 2), PALE_GREEN)
    rect(img, (mx, cy0 + 3, mx + 3, cy0 + 4), GREEN)
    # the rod and its ball tip
    box(img, DUPLEX_ROD, shade(copper, 0.9), 0.1)
    box(img, DUPLEX_KNOB, (232, 150, 96), 0.08)
    for name, (x0, y0, x1, y1) in faces(*DUPLEX_KNOB).items():
        img.putpixel((x0, y0), (255, 214, 170, 255))
    # the belt round the bottom of the body, a buckle at the front
    box(img, DUPLEX_BELT, shade(copper, 0.7), 0.1)
    for name, b in faces(*DUPLEX_BELT).items():
        speckle(img, b, verdigris + (255,), 2)
    lx0, ly0, lx1, ly1 = front(DUPLEX_BELT)
    rect(img, (lx0 + 2, ly0, lx0 + 6, ly1), (240, 196, 110, 255))
    rect(img, (lx0 + 3, ly0, lx0 + 5, ly1), (120, 70, 40, 255))
    save(img, "entity/xenoform_duplex.png")


def rivet():
    """(0.18.95; 0.19.84 a living puddle) Rivet, living metal: chrome that runs like quicksilver (bright streaks down every
    face, a blue-grey sheen), green circuit lines, rippling rings on the top of its puddle, a smooth squashed face with
    two green slit eyes, the insignia on the torso's front (it lies face-up on the puddle). The head (6x5x6 at 0,0) and
    torso (8x9x4 at 16,16) sit where the man's did, smaller, so those regions are cleared first; the arms and legs keep
    the vanilla regions; the puddle's slab, lobes, wake and mound are in the extras rows."""
    img = new_skin()
    metal = (158, 176, 190)
    green = GREEN
    dark_line = (40, 70, 52, 255)
    head = (0, 0, 6, 5, 6)
    body = (16, 16, 8, 9, 4)
    pool = (0, 64, 14, 2, 16)
    lobes = [(0, 82, 4, 3, 4), (16, 82, 4, 3, 4), (32, 82, 4, 3, 4), (48, 82, 4, 3, 4)]
    wake = (0, 90, 6, 2, 8)
    mound = (0, 102, 10, 3, 10)
    for region in ((0, 0, 32, 16), (16, 16, 40, 32)):
        rect(img, region, CLEAR)

    def liquid(part, bright=(188, 204, 216), lit=0.6):
        for name, (x0, y0, x1, y1) in faces(*part).items():
            vgrad(img, (x0, y0, x1, y1), bright, shade(metal, LIGHT[name] * lit), 0.08)
            # quicksilver runs: bright drips down the face
            if name in ("front", "back", "left", "right"):
                for _ in range(max(1, (x1 - x0) // 3)):
                    x = rng.randrange(x0, x1)
                    top = rng.randrange(y0, max(y0 + 1, y1 - 3))
                    for y in range(top, min(y1, top + rng.randint(2, 5))):
                        img.putpixel((x, y), (240, 248, 255, 255))

    for part in LIMBS + (head, body):
        liquid(part)
    # green circuit lines down the outside of each limb, a ring at the wrist and ankle
    for part in LIMBS:
        f = faces(*part)
        for name in ("left", "right"):
            x0, y0, x1, y1 = f[name]
            cx = (x0 + x1) // 2
            for y in range(y0 + 1, y1 - 1):
                img.putpixel((cx, y), green)
            for x in range(x0, x1):
                img.putpixel((x, y1 - 3), dark_line)
        x0, y0, x1, y1 = f["front"]
        for x in range(x0, x1):
            img.putpixel((x, y1 - 3), green)
    # the torso: a dark seam down each side of its front, green lines down its flanks
    bf = faces(*body)
    x0, y0, x1, y1 = bf["front"]
    for y in range(y0, y1):
        img.putpixel((x0, y), dark_line)
        img.putpixel((x1 - 1, y), dark_line)
    for name in ("left", "right"):
        x0, y0, x1, y1 = bf[name]
        for y in range(y0 + 1, y1 - 1):
            img.putpixel((x0 + 1, y), green)
    # the face (6x5 front at 6,6): smooth, two slit eyes with a pale glint, no mouth
    rect(img, (7, 8, 9, 9), green)
    rect(img, (10, 8, 12, 9), green)
    img.putpixel((7, 8), PALE_GREEN)
    img.putpixel((11, 8), PALE_GREEN)
    # the insignia, small, on the torso's front (20,20)-(28,29): rows 21-27
    insignia(img, 24, 22, small=True, rim=(30, 40, 52, 255))
    # extras: the slab 14x2x16 at (0,64), four lobes 4x3x4 at (0,82) (16,82) (32,82) (48,82), the wake 6x2x8 at (0,90),
    # the mound 10x3x10 at (0,102)
    for part in [pool, wake, mound] + lobes:
        liquid(part, bright=(236, 244, 250), lit=0.7)
    # rings rippling out across the top of the slab and the mound
    x0, y0, x1, y1 = faces(*pool)["top"]
    for inset, col in ((1, (222, 234, 244, 255)), (3, (112, 134, 150, 255)), (5, (222, 234, 244, 255))):
        outline(img, (x0 + inset, y0 + inset, x1 - inset, y1 - inset), col)
    speckle(img, (x0, y0, x1, y1), green, 6)
    x0, y0, x1, y1 = faces(*mound)["top"]
    outline(img, (x0 + 1, y0 + 1, x1 - 1, y1 - 1), (222, 234, 244, 255))
    outline(img, (x0 + 3, y0 + 3, x1 - 3, y1 - 3), (112, 134, 150, 255))
    speckle(img, (x0, y0, x1, y1), green, 3)
    # a green circuit line round the slab's sides and the wake's top streaming back
    for name in ("front", "back", "left", "right"):
        x0, y0, x1, y1 = faces(*pool)[name]
        for x in range(x0, x1):
            img.putpixel((x, y1 - 1), green)
    x0, y0, x1, y1 = faces(*wake)["top"]
    for y in range(y0, y1, 2):
        img.putpixel(((x0 + x1) // 2, y), green)
    for lobe in lobes:
        x0, y0, x1, y1 = faces(*lobe)["top"]
        img.putpixel((x0 + 1, y0 + 1), PALE_GREEN)
    clear_hat(img)
    save(img, "entity/xenoform_rivet.png")


def gorger():
    """(0.18.96; 0.19.62 a real armadillo) Gorger, the armadillo alien: dusky pink-brown hide, a pale belly, a shell of
    six scute bands (each edged dark, ruled across, the tips laid in bricks), a bone horn on a pale snout, little round
    ears, beady black eyes, the insignia on the back of the rump shield."""
    img = new_skin()
    hide = (176, 112, 92)
    scute = (150, 96, 84)
    dark = (70, 40, 36, 255)
    pale = (206, 156, 138, 255)
    claw = (54, 36, 30, 255)
    # the base humanoid parts (the first-person arm uses the arm; the rest are hidden or replaced)
    parts(img, hide, 0.16)
    claws(img, claw)
    # the new boxes (texOffs): head 5x5x3 (0,0), snout 3x3x3 (16,0), horn 1x3x1 (18,8), ears 2x2x1 (24,8) (24,12),
    # torso 8x5x12 (0,64), shield 10x8x3 (0,82), rump 10x8x3 (28,82), bands 10x7x2 (0,94) (26,94) and 10x8x2 (0,104) (26,104),
    # tail 3x3x5 (0,116), tail tip 2x2x4 (18,116), legs 4x5x4 at (0,16) (16,48) (32,116) (48,116)
    head = (0, 0, 5, 5, 3)
    snout = (16, 0, 3, 3, 3)
    horn = (18, 8, 1, 3, 1)
    ear_r = (24, 8, 2, 2, 1)
    ear_l = (24, 12, 2, 2, 1)
    torso = (0, 64, 8, 5, 12)
    shells = ((0, 82, 10, 8, 3), (0, 94, 10, 7, 2), (0, 104, 10, 8, 2), (26, 104, 10, 8, 2), (26, 94, 10, 7, 2), (28, 82, 10, 8, 3))
    tail1 = (0, 116, 3, 3, 5)
    tail2 = (18, 116, 2, 2, 4)
    legs = ((0, 16, 4, 5, 4), (16, 48, 4, 5, 4), (32, 116, 4, 5, 4), (48, 116, 4, 5, 4))
    # the soft flanks and belly under the shell
    box(img, torso, hide, 0.14)
    for name, (x0, y0, x1, y1) in faces(*torso).items():
        if name in ("front", "back", "left", "right"):
            vgrad(img, (x0, y0, x1, y1), (170, 108, 90), (206, 160, 142), 0.1)
        elif name == "bottom":
            vgrad(img, (x0, y0, x1, y1), (214, 168, 150), (192, 146, 128), 0.08)
    # the legs: darker hide, a dark rule across, dark claws
    for leg in legs:
        box(img, leg, hide, 0.16)
        for name, (x0, y0, x1, y1) in faces(*leg).items():
            if name in ("front", "back", "left", "right"):
                rect(img, (x0, y0 + 2, x1, y0 + 3), shade(hide + (255,), 0.72))
    claws(img, claw, which=legs)
    # the shell: scute plates. Sides ruled across every third row with a pale line under; tips laid in bricks with a
    # dark seam on the last row (the tip becomes the ball's skin when it rolls up)
    for shell in shells:
        box(img, shell, scute, 0.14, edge=0.82)
        for name, (x0, y0, x1, y1) in faces(*shell).items():
            if name in ("front", "back", "left", "right"):
                for y in range(y0 + 1, y1, 3):
                    rect(img, (x0, y, x1, y + 1), dark)
                    if y + 1 < y1:
                        for x in range(x0, x1):
                            img.putpixel((x, y + 1), shade(scute + (255,), 1.22))
            elif name == "top":
                for y in range(y0, y1):
                    if y == y1 - 1:
                        rect(img, (x0, y, x1, y + 1), dark)
                    else:
                        for x in range(x0 + (y - y0) % 2 * 2, x1, 4):
                            rect(img, (x, y, x + 1, y + 1), dark)
                        for x in range(x0, x1):
                            if img.getpixel((x, y)) != dark:
                                img.putpixel((x, y), shade(scute + (255,), 1.12))
    # the insignia on the back of the rump shield (the last of the six): 5x7 incl. rim, high on its 10x8 back face
    # (rows 0-6, its heart on row 3: where the plate sits, clear of the tail below it)
    bx0, by0, bx1, by1 = faces(*shells[5])["back"]
    insignia(img, (bx0 + bx1) // 2, by0 + 1, small=True, rim=dark)
    # the head: hide, the two eyes on the front face either side of the snout and on the cheeks, nothing between
    box(img, head, hide, 0.14)
    fx0, fy0, fx1, fy1 = faces(*head)["front"]
    black = (16, 12, 12, 255)
    img.putpixel((fx0, fy0 + 1), black)
    img.putpixel((fx1 - 1, fy0 + 1), black)
    rx0, ry0, rx1, ry1 = faces(*head)["right"]
    lx0, ly0, lx1, ly1 = faces(*head)["left"]
    img.putpixel((rx1 - 1, ry0 + 1), black)
    img.putpixel((lx0, ly0 + 1), black)
    img.putpixel((fx0 + 1, fy0 + 3), shade(hide + (255,), 0.7))
    img.putpixel((fx1 - 2, fy0 + 3), shade(hide + (255,), 0.7))
    # the snout: long, pale, with two nostrils on its tip
    box(img, snout, (206, 156, 138), 0.1)
    sx0, sy0, sx1, sy1 = faces(*snout)["front"]
    img.putpixel((sx0, sy0 + 1), dark)
    img.putpixel((sx1 - 1, sy0 + 1), dark)
    rect(img, (sx0, sy1 - 1, sx1, sy1), shade(pale, 0.8))
    # the horn: bone, paler at the tip, rings across
    box(img, horn, (226, 214, 190), 0.1)
    for name, (x0, y0, x1, y1) in faces(*horn).items():
        if name in ("front", "back", "left", "right"):
            img.putpixel((x0, y0), (250, 244, 230, 255))
            if y1 - y0 > 2:
                rect(img, (x0, y0 + 2, x1, y0 + 3), shade((226, 214, 190), 0.8))
    # the ears: hide, pink inside on the front
    for ear in (ear_r, ear_l):
        box(img, ear, hide, 0.12)
        ex0, ey0, ex1, ey1 = faces(*ear)["front"]
        rect(img, (ex0, ey0 + 1, ex1, ey1), (214, 150, 140, 255))
    # the tail: ringed scutes
    for tail in (tail1, tail2):
        box(img, tail, scute, 0.14, edge=0.85)
        for name, (x0, y0, x1, y1) in faces(*tail).items():
            if name in ("front", "back", "left", "right"):
                for x in range(x0 + 1, x1, 2):
                    rect(img, (x, y0, x + 1, y1), dark)
            elif name == "top":
                for y in range(y0, y1, 2):
                    rect(img, (x0, y, x1, y + 1), dark)
    clear_hat(img)
    save(img, "entity/xenoform_gorger.png")


def slipfin():
    """(0.18.96; 0.19.66 axolotl) Slipfin, the axolotl alien: smooth pink skin with a wet sheen and darker spots, a pale
    belly, feathery magenta gills, a deep flat tail, small black eyes and a little smile. The man's parts keep the
    old paint (the first-person hand); the animal's boxes are in rows 64-97."""
    img = new_skin()
    pink = (238, 150, 186)
    pinka = pink + (255,)
    spot = shade(pinka, 0.78)
    gill = (214, 56, 132)
    ink = (16, 12, 20, 255)
    rose = (150, 60, 96, 255)
    parts(img, pink, 0.08)
    for part in BASE_PARTS:
        for name, (x0, y0, x1, y1) in faces(*part).items():
            vgrad(img, (x0, y0, x1, y1), shade(pinka, LIGHT[name] * 1.08), shade(pinka, LIGHT[name] * 0.9), 0.06)
            speckle(img, (x0, y0, x1, y1), spot, (x1 - x0) * (y1 - y0) // 14)
            # a wet shine near the top of each face
            if name in ("front", "back", "left", "right") and y1 - y0 > 3:
                rect(img, (x0 + 1, y0 + 1, min(x1, x0 + 2), y0 + 2), (255, 236, 244, 255))
    bx0, by0, bx1, by1 = front(BODY)
    vgrad(img, (bx0 + 1, by0 + 2, bx1 - 1, by1), (252, 210, 222), (240, 186, 204), 0.05)
    # the face: wide-set black eyes, a small smile, pink cheeks
    img.putpixel((9, 11), ink)
    img.putpixel((14, 11), ink)
    rect(img, (11, 14, 13, 15), rose)
    img.putpixel((10, 13), rose)
    img.putpixel((13, 13), rose)
    chest_insignia(img, rim=(120, 40, 80, 255))

    # (0.19.66) the axolotl, every box in rows 64-97: front trunk 6x4x8 (0,64), rear trunk 6x4x8 (28,64), skull 8x3x9
    # (0,76), jaw 6x1x5 (34,76), gill 5x1x2 (34,82), leg 2x3x3 (48,82), tail 4x5x5 (0,88), 3x5x5 (18,88), 2x4x5 (34,88),
    # 2x3x4 (48,88)
    def skin(part, tint=1.0, spots=14):
        """Pink, lit per face, with a wet gradient and darker spots."""
        for name, (x0, y0, x1, y1) in faces(*part).items():
            vgrad(img, (x0, y0, x1, y1), shade(pinka, LIGHT[name] * 1.08 * tint), shade(pinka, LIGHT[name] * 0.9 * tint), 0.06)
            speckle(img, (x0, y0, x1, y1), spot, max(1, (x1 - x0) * (y1 - y0) // spots))

    def belly(part, rows=1):
        """The pale underside: the bottom face and the lowest rows of the sides."""
        f = faces(*part)
        vgrad(img, f["bottom"], (252, 210, 222), (240, 186, 204), 0.05)
        for name in ("front", "back", "left", "right"):
            x0, y0, x1, y1 = f[name]
            vgrad(img, (x0, y1 - rows, x1, y1), (248, 200, 216), (252, 214, 226), 0.04)

    def shine(part):
        for name in ("left", "right", "front", "back"):
            x0, y0, x1, y1 = faces(*part)[name]
            if x1 - x0 > 2:
                rect(img, (x0 + 1, y0, x0 + 2, y0 + 1), (255, 236, 244, 255))

    fore, rear = (0, 64, 6, 4, 8), (28, 64, 6, 4, 8)
    for trunk in (fore, rear):
        skin(trunk)
        belly(trunk)
        shine(trunk)
        # a darker stripe down the back
        tx0, ty0, tx1, ty1 = faces(*trunk)["top"]
        rect(img, ((tx0 + tx1) // 2 - 1, ty0, (tx0 + tx1) // 2 + 1, ty1), shade(pinka, 0.8))
        speckle(img, (tx0, ty0, tx1, ty1), spot, 5)
    # the insignia on the front trunk's back (the hidden body's plate lies there)
    tx0, ty0, tx1, ty1 = faces(*fore)["top"]
    insignia(img, (tx0 + tx1) // 2, ty0 + 1, small=True, rim=(120, 40, 80, 255))

    skull, jaw = (0, 76, 8, 3, 9), (34, 76, 6, 1, 5)
    skin(skull, spots=18)
    shine(skull)
    sf = faces(*skull)
    # eyes: two black beads on top by the front edge (the lower rows of the top face run to the front), a ringed lid
    ex0, ey0, ex1, ey1 = sf["top"]
    for ex in (ex0 + 1, ex1 - 2):
        img.putpixel((ex, ey1 - 3), shade(pinka, 0.62))
        img.putpixel((ex, ey1 - 2), ink)
    # nostrils on the snout, a blush on each cheek
    fx0, fy0, fx1, fy1 = sf["front"]
    img.putpixel((fx0 + 2, fy0 + 1), rose)
    img.putpixel((fx1 - 3, fy0 + 1), rose)
    for name in ("left", "right"):
        x0, y0, x1, y1 = sf[name]
        rect(img, (x1 - 3, y0 + 1, x1 - 1, y0 + 2), (232, 110, 150, 255))
    # the roof of the mouth under the skull
    rect(img, sf["bottom"], shade((170, 70, 100), 1.0))
    speckle(img, sf["bottom"], (196, 96, 122, 255), 8)
    skin(jaw, spots=10)
    belly(jaw, 0)
    jf = faces(*jaw)
    # inside the jaw: dark rose with a pale tongue
    rect(img, jf["top"], (170, 70, 100, 255))
    jx0, jy0, jx1, jy1 = jf["top"]
    rect(img, (jx0 + 1, jy0 + 1, jx1 - 1, jy1 - 1), (220, 112, 134, 255))

    # gills: magenta plumes, the frills alternating paler along their length
    plume = (34, 82, 5, 1, 2)
    box(img, plume, gill, 0.16)
    for name, (x0, y0, x1, y1) in faces(*plume).items():
        for x in range(x0, x1):
            if (x - x0) % 2 == 1:
                rect(img, (x, y0, x + 1, y1), shade((255, 110, 176), LIGHT[name] * 0.95))
    # legs: pink paddles with darker toes underneath
    paw = (48, 82, 2, 3, 3)
    skin(paw, spots=8)
    px0, py0, px1, py1 = faces(*paw)["bottom"]
    rect(img, (px0, py0, px1, py1), (196, 100, 140, 255))
    for x in range(px0, px1):
        img.putpixel((x, py0), rose)
    # the tail: deep and flat, a paler fin edge along the top and bottom, darkening toward the tip
    tails = ((0, 88, 4, 5, 5), (18, 88, 3, 5, 5), (34, 88, 2, 4, 5), (48, 88, 2, 3, 4))
    for i, seg in enumerate(tails):
        skin(seg, tint=1.0 - 0.04 * i, spots=12)
        for name, (x0, y0, x1, y1) in faces(*seg).items():
            if name in ("left", "right"):
                rect(img, (x0, y0, x1, y0 + 1), (250, 196, 216, 255))
                rect(img, (x0, y1 - 1, x1, y1), (250, 196, 216, 255))
                for y in range(y0 + 2, y1 - 1, 2):
                    img.putpixel((x0 + (y % 3), y), shade(pinka, 0.86))
        if i == 3:
            for name, b in faces(*seg).items():
                speckle(img, b, shade((214, 90, 140), 1.0), 3)
    clear_hat(img)
    save(img, "entity/xenoform_slipfin.png")


def polestar():
    """(0.18.96) Polestar, the lodestone alien: dark lodestone grey with a sheen of iron, a magnet's red and white bands
    on the shoulders and forearms, the prongs of its horseshoe red and silver-tipped, eyes like sparks."""
    img = new_skin()
    stone = (78, 80, 88)
    red = (204, 58, 58, 255)
    silver = (214, 220, 228, 255)
    parts(img, stone, 0.2)
    for part in BASE_PARTS:
        for name, b in faces(*part).items():
            speckle(img, b, shade(stone + (255,), 1.35), (b[2] - b[0]) * (b[3] - b[1]) // 12)
    # the magnet's bands: red on the upper arms and thighs, silver at the wrists and ankles
    for part in LIMBS:
        for name, (x0, y0, x1, y1) in faces(*part).items():
            if name in ("front", "back", "left", "right"):
                rect(img, (x0, y0, x1, y0 + 4), red)
                rect(img, (x0, y1 - 3, x1, y1 - 1), silver)
    # field lines across the chest
    bx0, by0, bx1, by1 = front(BODY)
    for y in range(by0 + 1, by1 - 1, 3):
        for x in range(bx0, bx1):
            if (x + y) % 4 == 0:
                img.putpixel((x, y), (150, 160, 190, 255))
    # the face: two spark eyes, a hard jaw line
    rect(img, (9, 11, 11, 12), (170, 220, 255, 255))
    rect(img, (13, 11, 15, 12), (170, 220, 255, 255))
    rect(img, (9, 14, 15, 15), (40, 42, 48, 255))
    chest_insignia(img, rim=(30, 30, 36, 255))
    # extras: prongs 2x6x2 at (0,64), red with silver tips; shoulder plates 12x3x6 at (0,74)
    prong = (0, 64, 2, 6, 2)
    box(img, prong, red[:3], 0.1)
    for name, (x0, y0, x1, y1) in faces(*prong).items():
        if name == "top":
            rect(img, (x0, y0, x1, y1), silver)
        elif name != "bottom":
            rect(img, (x0, y0, x1, y0 + 2), silver)
    plates = (0, 74, 12, 3, 6)
    box(img, plates, stone, 0.14, edge=0.7)
    for name, (x0, y0, x1, y1) in faces(*plates).items():
        if name in ("front", "back"):
            rect(img, (x0, y0, x0 + (x1 - x0) // 2, y1), red)
            rect(img, (x0 + (x1 - x0) // 2, y0, x1, y1), silver)
    clear_hat(img)
    save(img, "entity/xenoform_polestar.png")


def lurk():
    """(0.18.96; 0.19.84 on all fours) Lurk, the stalker: a bruise-purple dark that drinks the light, faint violet veins,
    long grey fingers, and a face that is almost all eyes: six pale ones, the two largest domed and ringed in violet.
    Its boxes: head 6x6x6 (0,0), eye domes 3x3x1 (24,0) and (24,4), neck 3x4x3 (0,12), torso 5x11x3 (16,16), upper arms
    2x8x2 (40,16) and (32,48), forearms 2x9x2 (48,16) and (40,48), hands 3x6x2 (0,76) and (10,76), thighs 2x9x2 (0,64)
    and (16,64), shins 2x8x2 (8,64) and (24,64), feet 3x2x5 (32,64) and (48,64). The head, torso, arms and legs reuse
    the vanilla regions with their own sizes, so the paint under each box is cleared first."""
    img = new_skin()
    dark = (40, 32, 56)
    vein = (110, 82, 176, 255)
    pale = (236, 232, 248, 255)
    soft = (214, 206, 236, 255)
    grey = (96, 92, 108)
    tip = (20, 16, 26, 255)
    parts(img, dark, 0.18)
    for part in BASE_PARTS:
        for name, b in faces(*part).items():
            veins(img, b, shade(dark + (255,), 0.7), vein, (b[2] - b[0]) // 3 + 1)
    chest_insignia(img, rim=(24, 18, 34, 255))
    # the man's body is only the plate's hidden anchor now; the torso lies over its region, so its old paint goes
    rect(img, (16, 16, 40, 32), CLEAR)

    def lay(part, base, spread=0.18):
        """Clears the old paint under a box, paints it lit per face and runs faint violet veins over it."""
        u, v, w, h, d = part
        rect(img, (u, v, u + 2 * (d + w), v + d + h), CLEAR)
        box(img, part, base, spread)
        for name, b in faces(*part).items():
            veins(img, b, shade(base, 0.7), vein, max(1, (b[2] - b[0]) * (b[3] - b[1]) // 12))

    # head 6x6x6 at (0,0): the front face is six rows; the two domes cover rows 1-3, so a small eye sits above and two
    # below them, and a thin mouth under those
    head = (0, 0, 6, 6, 6)
    lay(head, dark)
    fx0, fy0, fx1, fy1 = faces(*head)["front"]
    rect(img, (fx0, fy0, fx1, fy1), shade(dark + (255,), 0.8))
    img.putpixel((fx0 + 1, fy0), soft)
    img.putpixel((fx1 - 2, fy0), soft)
    img.putpixel((fx0 + 2, fy0 + 4), soft)
    img.putpixel((fx1 - 3, fy0 + 4), soft)
    rect(img, (fx0 + 1, fy0 + 5, fx1 - 1, fy0 + 6), tip)
    # a small pale eye on each side too
    for name in ("right", "left"):
        x0, y0, x1, y1 = faces(*head)[name]
        img.putpixel(((x0 + x1) // 2, y0 + 2), soft)
    # eye domes 3x3x1: a violet iris, darker at the corners, a pale glint in the middle
    for dome in ((24, 0, 3, 3, 1), (24, 4, 3, 3, 1)):
        box(img, dome, vein[:3], 0.1)
        for name, b in faces(*dome).items():
            if name != "front":
                noise_fill(img, shade(vein, 0.5), 0.1, b)
        x0, y0, x1, y1 = faces(*dome)["front"]
        rect(img, (x0, y0, x1, y1), vein)
        for cx, cy in ((x0, y0), (x1 - 1, y0), (x0, y1 - 1), (x1 - 1, y1 - 1)):
            img.putpixel((cx, cy), shade(vein, 0.7))
        img.putpixel(((x0 + x1) // 2, (y0 + y1) // 2), pale)
    # neck 3x4x3 at (0,12)
    lay((0, 12, 3, 4, 3), dark)
    # torso 5x11x3 at (16,16): thin ribs down the sides, the insignia on the chest and, turned over, on the back (the plate
    # sits on the back when it is on all fours)
    torso = (16, 16, 5, 11, 3)
    lay(torso, dark)
    tf = faces(*torso)
    for name in ("right", "left"):
        x0, y0, x1, y1 = tf[name]
        for y in range(y0 + 1, y1 - 2, 2):
            rect(img, (x0, y, x1, y + 1), shade(dark + (255,), 1.7))
    for name in ("front", "back"):
        x0, y0, x1, y1 = tf[name]
        rect(img, (x0, y0, x1, y1), shade(dark + (255,), 0.85 if name == "front" else 0.7))
        insignia(img, (x0 + x1) // 2, y0 + 3, small=True, rim=(24, 18, 34, 255))
    # arms: dark upper arms, forearms fading to grey toward the wrist, grey hands with dark fingertips
    for upper, fore, hand in (((40, 16, 2, 8, 2), (48, 16, 2, 9, 2), (0, 76, 3, 6, 2)),
                              ((32, 48, 2, 8, 2), (40, 48, 2, 9, 2), (10, 76, 3, 6, 2))):
        lay(upper, dark)
        lay(fore, dark)
        for name, (x0, y0, x1, y1) in faces(*fore).items():
            if name in ("front", "back", "left", "right"):
                vgrad(img, (x0, y1 - 4, x1, y1), dark, grey, 0.12)
        rect(img, (hand[0], hand[1], hand[0] + 2 * (hand[4] + hand[2]), hand[1] + hand[4] + hand[3]), CLEAR)
        box(img, hand, grey, 0.12)
        for name, (x0, y0, x1, y1) in faces(*hand).items():
            if name in ("front", "back", "left", "right"):
                for x in range(x0, x1):
                    rect(img, (x, y1 - 2, x + 1, y1), tip)
                    if x % 2 == 0:
                        rect(img, (x, y0 + 2, x + 1, y1 - 2), (72, 68, 84, 255))
    # legs: dark thighs and shins, the shins greying toward the ankle, flat dark-grey feet with toes
    for thigh, shin, foot in (((0, 64, 2, 9, 2), (8, 64, 2, 8, 2), (32, 64, 3, 2, 5)),
                              ((16, 64, 2, 9, 2), (24, 64, 2, 8, 2), (48, 64, 3, 2, 5))):
        lay(thigh, dark)
        lay(shin, dark)
        for name, (x0, y0, x1, y1) in faces(*shin).items():
            if name in ("front", "back", "left", "right"):
                vgrad(img, (x0, y1 - 3, x1, y1), dark, grey, 0.12)
        box(img, foot, (72, 68, 84), 0.12)
        ff = faces(*foot)
        x0, y0, x1, y1 = ff["top"]
        for x in range(x0 + 1, x1, 1):
            rect(img, (x, y0 + 1, x + 1, y1), shade((72, 68, 84), 0.7))
        for name in ("front", "left", "right"):
            x0, y0, x1, y1 = ff[name]
            rect(img, (x0, y1 - 1, x1, y1), tip)
        noise_fill(img, tip, 0.2, ff["bottom"])
    clear_hat(img)
    save(img, "entity/xenoform_lurk.png")


def gyre():
    """(0.18.103; 0.19.62 a turtle on four flippers) Gyre, the wind-shell turtle: sea-green hide with pale scales, a
    domed shell of dark plates edged in moss, wind vents (pale slots) along its rim, a cream plastron, a small hooked
    beak, the insignia on the shell's crown."""
    img = new_skin()
    hide = (94, 150, 112)
    plate = (54, 92, 64)
    moss = (120, 170, 96, 255)
    vent = (210, 236, 226, 255)
    cream = (214, 206, 150)
    dark = (40, 50, 40, 255)
    pale = (150, 196, 158, 255)
    parts(img, hide, 0.16)
    for part in BASE_PARTS:
        for name, b in faces(*part).items():
            speckle(img, b, pale, (b[2] - b[0]) * (b[3] - b[1]) // 10 + 1)
    # the man's body and arms are hidden (the arm shows in first person): keep the old belly and claws on them
    bx0, by0, bx1, by1 = front(BODY)
    vgrad(img, (bx0 + 1, by0 + 1, bx1 - 1, by1 - 1), (214, 206, 150), (188, 178, 120), 0.08)
    for y in range(by0 + 3, by1, 3):
        rect(img, (bx0 + 1, y, bx1 - 1, y + 1), (150, 140, 96, 255))
    claws(img, dark, which=ARMS)
    # the turtle's own boxes reuse the head and leg regions: clear them first
    rect(img, (0, 0, 32, 16), CLEAR)
    rect(img, (0, 16, 16, 32), CLEAR)
    rect(img, (16, 48, 32, 64), CLEAR)

    def skin(part, spread=0.16):
        box(img, part, hide, spread)
        for name, b in faces(*part).items():
            speckle(img, b, pale, (b[2] - b[0]) * (b[3] - b[1]) // 9 + 1)

    # head 5x5x6 at (0,0): front (6,6)-(11,11)
    head = (0, 0, 5, 5, 6)
    skin(head)
    rect(img, (6, 7, 7, 9), dark)
    rect(img, (10, 7, 11, 9), dark)
    img.putpixel((6, 7), (230, 230, 200, 255))
    img.putpixel((10, 7), (230, 230, 200, 255))
    rect(img, (7, 10, 10, 11), (60, 96, 70, 255))
    rect(img, (5, 7, 6, 9), dark)       # each eye wraps the corner onto the side
    rect(img, (11, 7, 12, 9), dark)
    rect(img, (7, 8, 10, 9), shade(hide, 0.8))
    # neck 3x3x4 at (0,108): wrinkle rings
    neck = (0, 108, 3, 3, 4)
    skin(neck, 0.12)
    for name, (x0, y0, x1, y1) in faces(*neck).items():
        if name in ("right", "left", "front", "back"):
            for y in range(y0 + 1, y1, 2):
                rect(img, (x0, y, x1, y + 1), shade(hide, 0.78))
    # front flippers 3x6x4 at (0,16) and (16,48); hind 3x6x3 at (16,108) and (28,108); tail 2x1x3 at (40,108)
    flippers = [(0, 16, 3, 6, 4), (16, 48, 3, 6, 4), (16, 108, 3, 6, 3), (28, 108, 3, 6, 3)]
    for part in flippers:
        skin(part)
        for name, (x0, y0, x1, y1) in faces(*part).items():
            if name in ("front", "back", "left", "right"):
                rect(img, (x0, y0 + 2, x1, y0 + 3), shade(hide, 0.82))
    claws(img, dark, which=tuple(flippers))
    skin((40, 108, 2, 1, 3), 0.12)
    # beak 3x2x2 at (40,112)
    box(img, (40, 112, 3, 2, 2), (204, 190, 120), 0.1)
    for name, (x0, y0, x1, y1) in faces(40, 112, 3, 2, 2).items():
        if name in ("front", "left", "right", "back"):
            rect(img, (x0, y1 - 1, x1, y1), (150, 136, 80, 255))
    # carapace 12x4x14 at (0,64): sides 4 tall, vents along them
    lower = (0, 64, 12, 4, 14)
    box(img, lower, plate, 0.14)
    for name, (x0, y0, x1, y1) in faces(*lower).items():
        outline(img, (x0, y0, x1, y1), moss)
        if name == "top":
            for x in range(x0 + 4, x1 - 2, 4):
                rect(img, (x, y0 + 1, x + 1, y1 - 1), shade(plate, 1.3))
            for y in range(y0 + 4, y1 - 2, 5):
                rect(img, (x0 + 1, y, x1 - 1, y + 1), shade(plate, 1.3))
        elif name in ("left", "right", "front", "back"):
            for x in range(x0 + 2, x1 - 2, 3):
                rect(img, (x, y0 + 2, x + 2, y0 + 3), vent)
    # crown 8x2x10 at (0,82): the insignia on its top face (10,82)-(18,92)
    top = (0, 82, 8, 2, 10)
    box(img, top, plate, 0.14)
    for name, (x0, y0, x1, y1) in faces(*top).items():
        outline(img, (x0, y0, x1, y1), moss)
    tx0, ty0, tx1, ty1 = faces(*top)["top"]
    insignia(img, (tx0 + tx1) // 2, ty0 + 1, rim=(20, 36, 24, 255))
    # plastron 10x2x12 at (0,94): cream with segment lines
    plast = (0, 94, 10, 2, 12)
    box(img, plast, cream, 0.08)
    for name, (x0, y0, x1, y1) in faces(*plast).items():
        outline(img, (x0, y0, x1, y1), (150, 140, 96, 255))
        if name == "bottom":
            for y in range(y0 + 3, y1 - 1, 3):
                rect(img, (x0 + 1, y, x1 - 1, y + 1), (150, 140, 96, 255))
            rect(img, ((x0 + x1) // 2, y0 + 1, (x0 + x1) // 2 + 1, y1 - 1), (150, 140, 96, 255))
    clear_hat(img)
    save(img, "entity/xenoform_gyre.png")


def cortex():
    """(0.18.103; 0.19.82 an eye fish) Cortex, the thinking eye: a coral-red plated hull with a cream belly, one big
    cream eye with a teal iris and a black pupil, a cracked dome on its back where a pink brain laced with glowing teal
    shows, bone-pale spikes with teal tips, and a tail of three shrinking plated segments ending in a coral fin. The man's
    parts (hidden in the model) keep the old coral look for the first-person arm.
    Boxes (rows 64-127; left and right spikes share one): hull 10x9x13 at (0,64); spike shaft 6x2x2 at (46,64); spike
    tip 3x1x1 at (46,68); tail fin 1x8x4 at (46,70); eye 6x6x6 at (0,86); dome 6x3x6 at (24,86); brain 4x2x4 at (48,86);
    spark 1x1x1 at (48,92); tail segments 7x6x5 at (0,98), 5x4x5 at (24,98), 3x3x5 at (44,98)."""
    img = new_skin()
    shell = (196, 78, 62)
    teal = (110, 240, 230, 255)
    dark = (80, 26, 22, 255)
    cream = (236, 212, 180)
    parts(img, shell, 0.14, edge=0.7)
    bx0, by0, bx1, by1 = front(BODY)
    vgrad(img, (bx0 + 1, by0 + 2, bx1 - 1, by1 - 1), (236, 212, 180), (214, 186, 150), 0.08)
    claws(img, (90, 30, 26, 255))
    eyes(img, (16, 12, 12, 255), y=12)
    rect(img, (10, 14, 14, 15), (120, 40, 34, 255))
    chest_insignia(img, rim=(80, 26, 22, 255))

    # the hull: plated coral, a cream belly, seams along the sides, dark sockets where the spikes grow
    hull = (0, 64, 10, 9, 13)
    box(img, hull, shell, 0.14, edge=0.7)
    hf = faces(*hull)
    x0, y0, x1, y1 = hf["bottom"]
    noise_fill(img, cream, 0.1, (x0 + 1, y0 + 1, x1 - 1, y1 - 1))
    for name in ("left", "right"):
        x0, y0, x1, y1 = hf[name]
        vgrad(img, (x0 + 1, y0 + 6, x1 - 1, y1 - 1), (214, 140, 112), cream, 0.08)
        for y in (y0 + 3, y0 + 6):
            rect(img, (x0 + 1, y, x1 - 1, y + 1), shade(shell + (255,), 0.62))
        # a spike's socket: a 3x3 dark patch where its pivot sits (z -3.5, 0.5, 4.5; y -0.5, 0.8, -0.3; the right face has the front at its right edge, the left face at its left)
        for z, y in ((-3.5, -0.5), (0.5, 0.8), (4.5, -0.3)):
            col = int(round(x1 - (z + 6.5) - 1.5)) if name == "right" else int(round(x0 + (z + 6.5) - 1.5))
            row = int(round(y0 + (y + 4.5) - 1.5))
            rect(img, (col, row, col + 3, row + 3), dark)
            img.putpixel((col + 1, row + 1), shade(teal, 0.7))
    # the front: the eye's socket, a dark ring round where the eye comes out
    x0, y0, x1, y1 = hf["front"]
    noise_fill(img, shade(shell + (255,), 0.8), 0.12, (x0, y0, x1, y1))
    outline(img, (x0, y0, x1, y1), shade(shell + (255,), 0.55))
    rect(img, (x0 + 1, y0 + 1, x1 - 1, y1 - 1), dark)
    # the back: darker, with a dark socket where the tail grows
    x0, y0, x1, y1 = hf["back"]
    noise_fill(img, shade(shell + (255,), 0.74), 0.12, (x0, y0, x1, y1))
    outline(img, (x0, y0, x1, y1), shade(shell + (255,), 0.5))
    rect(img, (x0 + 2, y0 + 2, x1 - 2, y1 - 2), dark)
    # the top: plate seams across it, the insignia at the rear (the top face's first rows are the hull's tail end; its
    # rows 0-6 lie behind the dome, which covers rows 6-12, so the insignia's rim sits in rows 0 and 6, centred 3 behind the middle)
    x0, y0, x1, y1 = hf["top"]
    for y in (y0 + 8, y0 + 10):
        rect(img, (x0 + 1, y, x1 - 1, y + 1), shade(shell + (255,), 0.7))
    insignia(img, (x0 + x1) // 2, y0 + 1, small=True, rim=dark)

    # the eye: cream, a teal iris ringed dark, a black pupil with a glint, red veins in the corners; the lid is dark coral
    eye = (0, 86, 6, 6, 6)
    box(img, eye, (150, 56, 46), 0.12, edge=0.7)
    x0, y0, x1, y1 = faces(*eye)["front"]
    noise_fill(img, (240, 234, 216), 0.06, (x0, y0, x1, y1))
    outline(img, (x0, y0, x1, y1), (90, 34, 30, 255))
    rect(img, (x0 + 2, y0 + 1, x1 - 2, y1 - 1), (110, 240, 230, 255))
    rect(img, (x0 + 1, y0 + 2, x1 - 1, y1 - 2), (110, 240, 230, 255))
    rect(img, (x0 + 2, y0 + 2, x1 - 2, y1 - 2), (12, 10, 14, 255))
    img.putpixel((x0 + 2, y0 + 2), (255, 255, 255, 255))
    for px, py in ((x0, y0 + 1), (x1 - 1, y1 - 2), (x0, y1 - 3)):
        img.putpixel((px, py), (200, 60, 60, 255))

    # the dome: lighter coral, cracked, glowing teal in the cracks, its top an open ring round the brain
    dome = (24, 86, 6, 3, 6)
    box(img, dome, (214, 90, 70), 0.12, edge=0.7)
    for name, b in faces(*dome).items():
        if name != "bottom":
            veins(img, b, (60, 20, 18, 255), teal, (b[2] - b[0]) // 2 + 1, length=(2, 5))
    x0, y0, x1, y1 = faces(*dome)["top"]
    rect(img, (x0 + 1, y0 + 1, x1 - 1, y1 - 1), (70, 20, 26, 255))

    # the brain: pink, veined with glowing teal
    brain = (48, 86, 4, 2, 4)
    box(img, brain, (232, 150, 170), 0.12)
    for name, b in faces(*brain).items():
        veins(img, b, (200, 110, 140, 255), teal, (b[2] - b[0]) // 2 + 1, length=(2, 4))

    # a spark: one bright texel on every face
    rect(img, (48, 92, 52, 94), (214, 255, 250, 255))

    # the spikes: pale bone shafts ringed darker, glowing teal tips
    shaft = (46, 64, 6, 2, 2)
    box(img, shaft, (232, 222, 196), 0.1, edge=0.8)
    for name, (x0, y0, x1, y1) in faces(*shaft).items():
        if name in ("front", "back", "top", "bottom", "left", "right") and x1 - x0 > 2:
            for x in range(x0 + 2, x1, 3):
                rect(img, (x, y0, x + 1, y1), shade((232, 222, 196), 0.62))
    tip = (46, 68, 3, 1, 1)
    box(img, tip, (110, 240, 230), 0.08)
    rect(img, (46, 68, 54, 70), shade(teal, 0.9))

    # the tail fin: coral, with pale ribs down its broad sides
    fin = (46, 70, 1, 8, 4)
    box(img, fin, (214, 90, 70), 0.14, edge=0.75)
    for name in ("left", "right"):
        x0, y0, x1, y1 = faces(*fin)[name]
        for x in range(x0 + 1, x1, 2):
            rect(img, (x, y0 + 1, x + 1, y1 - 1), (244, 190, 160, 255))

    # the tail: three plated segments, cream underneath, seamed across
    for seg, base in (((0, 98, 7, 6, 5), (196, 78, 62)), ((24, 98, 5, 4, 5), (188, 72, 58)), ((44, 98, 3, 3, 5), (178, 66, 54))):
        box(img, seg, base, 0.14, edge=0.7)
        sf = faces(*seg)
        x0, y0, x1, y1 = sf["bottom"]
        noise_fill(img, cream, 0.1, (x0 + 1, y0 + 1, x1 - 1, y1 - 1))
        for name in ("top", "left", "right"):
            x0, y0, x1, y1 = sf[name]
            for k in (2, 4):
                if name == "top":
                    rect(img, (x0 + 1, y1 - 1 - k, x1 - 1, y1 - k), shade(base + (255,), 0.62))
                else:
                    rect(img, (x0 + k, y0 + 1, x0 + k + 1, y1 - 1), shade(base + (255,), 0.62))
    clear_hat(img)
    save(img, "entity/xenoform_cortex.png")


def locust():
    """(0.18.103; 0.19.66 a grasshopper) Locust, the crashing hopper: grass-green with a yellow belly banded in segments,
    darker green wing cases veined lighter, long thin feelers, big amber compound eyes, red-brown spurs on the hind shins.
    Boxes (rows 64-127; left and right of a pair share one): head 6x7x6 at (0,0); thorax 6x5x8 at (0,64); abdomen 5x5x7 at
    (28,64); feeler 1x7x1 at (52,64) and its tip 1x6x1 at (56,64); tail 4x4x6 at (0,78); wing 3x1x9 at (20,78); wing tip
    3x1x6 at (44,78); hind thigh 3x10x3 at (0,90); hind shin 2x14x2 at (12,90); femur 2x5x2 at (20,90); tibia 1x9x1 at (28,90)."""
    img = new_skin()
    green = (120, 176, 58)
    # the man's parts are hidden, but the first-person hand is his right arm
    parts(img, green, 0.14)
    for part in LIMBS:
        for name, (x0, y0, x1, y1) in faces(*part).items():
            if name in ("front", "back", "left", "right"):
                for y in range(y0 + 3, y1, 4):
                    rect(img, (x0, y, x1, y + 1), shade(green + (255,), 0.7))
    claws(img, (60, 44, 24, 255))
    dark = (60, 80, 30, 255)
    pale = (226, 214, 96, 255)
    amber = (232, 160, 40, 255)
    pupil = (90, 50, 10, 255)
    ring = shade(green + (255,), 0.72)
    # the head: a tall face, big amber compound eyes on its sides, mandibles at the bottom
    head = (0, 0, 6, 7, 6)
    box(img, head, green, 0.14, edge=0.85)
    hf = faces(*head)
    fx0, fy0, fx1, fy1 = hf["front"]
    rect(img, (fx0, fy0 + 1, fx0 + 2, fy0 + 4), amber)
    rect(img, (fx1 - 2, fy0 + 1, fx1, fy0 + 4), amber)
    img.putpixel((fx0 + 1, fy0 + 2), pupil)
    img.putpixel((fx1 - 2, fy0 + 2), pupil)
    rect(img, (fx0 + 2, fy0 + 4, fx1 - 2, fy0 + 6), pale)
    rect(img, (fx0 + 1, fy0 + 6, fx1 - 1, fy0 + 7), (92, 56, 30, 255))
    img.putpixel((fx0 + 2, fy0), dark)
    img.putpixel((fx1 - 3, fy0), dark)
    for nm, x0 in (("right", hf["right"][0] + 2), ("left", hf["left"][0])):
        y0 = hf[nm][1] + 1
        rect(img, (x0, y0, x0 + 3, y0 + 4), amber)
        img.putpixel((x0 + 1, y0 + 1), pupil)
        img.putpixel((x0 + 1, y0 + 2), pupil)
    tx0, ty0, tx1, ty1 = hf["top"]
    rect(img, ((tx0 + tx1) // 2 - 1, ty0, (tx0 + tx1) // 2 + 1, ty1), shade(green + (255,), 0.7))
    bx0, by0, bx1, by1 = hf["bottom"]
    noise_fill(img, (92, 56, 30, 255), 0.2, (bx0, by0, bx1, by1))
    # the thorax: the pronotum's saddle on top with the insignia, a pale line down each side
    thorax = (0, 64, 6, 5, 8)
    box(img, thorax, green, 0.14, edge=0.82)
    tf = faces(*thorax)
    for nm in ("right", "left", "front", "back"):
        x0, y0, x1, y1 = tf[nm]
        if nm in ("right", "left"):
            rect(img, (x0, y0 + 1, x1, y0 + 2), (214, 226, 110, 255))
        for y in range(y0 + 2, y1, 2):
            img.putpixel((x0, y), ring)
    tx0, ty0, tx1, ty1 = tf["top"]
    rect(img, (tx0, ty0, tx0 + 1, ty1), shade(green + (255,), 0.78))
    rect(img, (tx1 - 1, ty0, tx1, ty1), shade(green + (255,), 0.78))
    insignia(img, (tx0 + tx1) // 2, ty0 + 2, small=True, rim=(40, 56, 20, 255))
    noise_fill(img, pale, 0.18, tf["bottom"])
    # the abdomen and tail: green on top and sides ringed in segments, a yellow belly
    for seg in ((28, 64, 5, 5, 7), (0, 78, 4, 4, 6)):
        box(img, seg, green, 0.14, edge=0.82)
        sf = faces(*seg)
        for nm in ("top", "right", "left"):
            x0, y0, x1, y1 = sf[nm]
            if nm == "top":
                for y in range(y0 + 1, y1, 2):
                    rect(img, (x0, y, x1, y + 1), ring)
            else:
                for x in range(x0 + 1, x1, 2):
                    rect(img, (x, y0, x + 1, y1), ring)
                rect(img, (x0, y1 - 2, x1, y1 - 1), pale)
        x0, y0, x1, y1 = sf["bottom"]
        noise_fill(img, pale, 0.16, (x0, y0, x1, y1))
        for y in range(y0 + 1, y1, 2):
            rect(img, (x0, y, x1, y + 1), (184, 168, 64, 255))
    ax0, ay0, ax1, ay1 = faces(0, 78, 4, 4, 6)["back"]
    noise_fill(img, (92, 56, 30, 255), 0.2, (ax0, ay0, ax1, ay1))
    # the feelers: dark thin bases, lighter ringed tips
    box(img, (52, 64, 1, 7, 1), (70, 96, 40), 0.1)
    box(img, (56, 64, 1, 6, 1), (96, 124, 52), 0.1)
    for nm, (x0, y0, x1, y1) in faces(52, 64, 1, 7, 1).items():
        if nm in ("front", "back", "left", "right"):
            for y in range(y0 + 1, y1, 3):
                rect(img, (x0, y, x1, y + 1), (150, 170, 80, 255))
    # the wing cases: dark green, veined lighter, a brown speckle at the tips
    for wing in ((20, 78, 3, 1, 9), (44, 78, 3, 1, 6)):
        box(img, wing, (82, 130, 48), 0.12)
        for nm, b in faces(*wing).items():
            veins(img, b, (60, 100, 36, 255), (150, 200, 96, 255), 3, length=(3, 8))
    wx0, wy0, wx1, wy1 = faces(44, 78, 3, 1, 6)["top"]
    speckle(img, (wx0, wy0, wx1, wy1), (92, 70, 36, 255), 6)
    # the walking legs: banded green femurs, pale-green tibias with dark tips and a row of spines
    femur = (20, 90, 2, 5, 2)
    box(img, femur, green, 0.12, edge=0.85)
    for nm, (x0, y0, x1, y1) in faces(*femur).items():
        if nm in ("front", "back", "left", "right"):
            rect(img, (x0, y0 + 1, x1, y0 + 2), ring)
            rect(img, (x0, y1 - 1, x1, y1), (60, 44, 24, 255))
    tibia = (28, 90, 1, 9, 1)
    box(img, tibia, (150, 164, 76), 0.1)
    for nm, (x0, y0, x1, y1) in faces(*tibia).items():
        if nm in ("front", "back", "left", "right"):
            for y in range(y0 + 1, y1 - 1, 3):
                rect(img, (x0, y, x1, y + 1), (96, 70, 44, 255))
            rect(img, (x0, y1 - 1, x1, y1), (60, 44, 24, 255))
    # the hind legs: a thick green thigh chevroned along its length, a long shin with red-brown spurs
    thigh = (0, 90, 3, 10, 3)
    box(img, thigh, green, 0.14, edge=0.85)
    for nm, (x0, y0, x1, y1) in faces(*thigh).items():
        if nm in ("front", "back", "left", "right"):
            for y in range(y0 + 1, y1 - 1, 3):
                for x in range(x0, x1):
                    img.putpixel((x, y + (1 if (x - x0) % 2 else 0)), ring)
            rect(img, (x0, y1 - 2, x1, y1), (150, 80, 50, 255))
    shin = (12, 90, 2, 14, 2)
    box(img, shin, (140, 152, 64), 0.12)
    for nm, (x0, y0, x1, y1) in faces(*shin).items():
        if nm in ("front", "back", "left", "right"):
            for y in range(y0 + 1, y1 - 2, 2):
                img.putpixel((x0, y), (150, 80, 50, 255))
                img.putpixel((x1 - 1, y), (150, 80, 50, 255))
            rect(img, (x0, y1 - 2, x1, y1), (60, 44, 24, 255))
    clear_hat(img)
    save(img, "entity/xenoform_locust.png")


def croaker():
    """(0.18.103; 0.19.66 a hopping frog) Croaker, the war frog: deep green, warty (dark and pale bumps), a pale yellow
    belly and throat sac, gold eyes with black slits on top of the head, webbed feet. The frog's boxes reuse the head
    (0,0) and front leg (0,16) / (16,48) regions of the vanilla layout with their own smaller sizes, so those old
    regions are cleared first; the rest are in the extras rows."""
    img = new_skin()
    green = (84, 132, 52)
    dark = (54, 92, 32, 255)
    pale = (140, 186, 84, 255)
    belly = (236, 226, 150)
    line = (40, 60, 24, 255)
    parts(img, green, 0.16)
    for part in BASE_PARTS:
        for name, b in faces(*part).items():
            speckle(img, b, dark, (b[2] - b[0]) * (b[3] - b[1]) // 8 + 1)
            speckle(img, b, pale, (b[2] - b[0]) * (b[3] - b[1]) // 14 + 1)
    bx0, by0, bx1, by1 = front(BODY)
    vgrad(img, (bx0 + 1, by0, bx1 - 1, by1 - 1), (236, 226, 150), (214, 200, 120), 0.08)
    claws(img, line, which=ARMS)
    # the frog's head and front legs sit where the man's did, smaller: clear the old paint under them
    for region in ((0, 0, 32, 16), (0, 16, 16, 32), (16, 48, 32, 64)):
        rect(img, region, CLEAR)

    def skin(part, base, spec=6):
        box(img, part, base, 0.16)
        for name, b in faces(*part).items():
            area = (b[2] - b[0]) * (b[3] - b[1])
            speckle(img, b, dark, area // spec + 1)
            speckle(img, b, pale, area // (spec * 2) + 1)

    def banded(part, base):
        """A limb segment with dark bands across its sides."""
        skin(part, base)
        for name, (x0, y0, x1, y1) in faces(*part).items():
            if name in ("front", "back", "left", "right"):
                for x in range(x0 + 1, x1, 3):
                    rect(img, (x, y0, x + 1, y1), shade(base, 0.7))

    # head 7x3x5 at (0,0): a wide mouth line, two nostrils, the inside of the mouth pink
    head = (0, 0, 7, 3, 5)
    skin(head, green)
    hf = faces(*head)
    fx0, fy0, fx1, fy1 = hf["front"]
    rect(img, (fx0, fy1 - 1, fx1, fy1), line)
    img.putpixel((fx0 + 2, fy0), line)
    img.putpixel((fx1 - 3, fy0), line)
    for name in ("right", "left"):
        x0, y0, x1, y1 = hf[name]
        rect(img, (x0, y1 - 1, x1, y1), line)
    noise_fill(img, (200, 96, 104), 0.1, hf["bottom"])
    # jaw 6x1x4 at (0,64): pale, the pink mouth and a tongue on top
    jaw = (0, 64, 6, 1, 4)
    box(img, jaw, (206, 202, 120), 0.1)
    jf = faces(*jaw)
    noise_fill(img, (214, 104, 112), 0.1, jf["top"])
    tx0, ty0, tx1, ty1 = jf["top"]
    rect(img, ((tx0 + tx1) // 2 - 1, ty0 + 1, (tx0 + tx1) // 2 + 1, ty1), (232, 72, 96, 255))
    # eyes 2x2x2 at (24,64) and (32,64): gold, a black slit down the front, a green lid on top
    for eye, slit in (((24, 64, 2, 2, 2), 1), ((32, 64, 2, 2, 2), 0)):
        box(img, eye, (214, 170, 40), 0.1)
        ef = faces(*eye)
        x0, y0, x1, y1 = ef["front"]
        rect(img, (x0 + slit, y0, x0 + slit + 1, y1), (16, 12, 8, 255))
        noise_fill(img, green, 0.14, ef["top"])
    # throat sac 4x2x3 at (40,64): pale yellow with faint veins
    sac = (40, 64, 4, 2, 3)
    box(img, sac, (236, 222, 150), 0.08)
    for name, b in faces(*sac).items():
        speckle(img, b, (208, 184, 112, 255), 3)
    # torso 6x4x7 at (0,72): green back, pale belly and flanks, the insignia flush on the back
    torso = (0, 72, 6, 4, 7)
    skin(torso, green)
    tf = faces(*torso)
    noise_fill(img, belly, 0.1, tf["bottom"])
    for name in ("front", "back", "left", "right"):
        x0, y0, x1, y1 = tf[name]
        vgrad(img, (x0, y1 - 2, x1, y1), (200, 196, 110), belly, 0.1)
    x0, y0, x1, y1 = tf["top"]
    insignia(img, (x0 + x1) // 2, y0 + 1, small=True, rim=line)
    # hind legs: thighs 2x2x6 at (0,88) and (16,88), shins 2x2x6 at (0,96) and (16,96)
    for seg in ((0, 88, 2, 2, 6), (16, 88, 2, 2, 6), (0, 96, 2, 2, 6), (16, 96, 2, 2, 6)):
        banded(seg, green)
    # webbed feet 3x2x4 at (0,106) and (14,106), hands 3x1x4 at (28,106) and (42,106): olive, toes divided, pale soles
    for foot in ((0, 106, 3, 2, 4), (14, 106, 3, 2, 4), (28, 106, 3, 1, 4), (42, 106, 3, 1, 4)):
        box(img, foot, (70, 112, 44), 0.12)
        ff = faces(*foot)
        x0, y0, x1, y1 = ff["top"]
        for x in range(x0 + 1, x1, 1 if x1 - x0 > 3 else 2):
            rect(img, (x, y0 + 1, x + 1, y1), line)
        noise_fill(img, (150, 170, 90), 0.12, ff["bottom"])
    # front legs 2x4x2 at (0,16) and (16,48)
    for leg in ((0, 16, 2, 4, 2), (16, 48, 2, 4, 2)):
        skin(leg, green, 4)
    clear_hat(img)
    save(img, "entity/xenoform_croaker.png")


def dud():
    """(0.18.103) Dud, the one that can't be hurt: soft pink, round, pale belly, big worried eyes under slanted
    brows, a wobbly little mouth, two nubs on top of its head; 0.19.84 a soft round ball of a body with the worried
    face on a plate at its front, nubby hands and stubby feet)."""
    img = new_skin()
    pink = (232, 176, 204)
    deep = (214, 150, 186)
    soft = {"top": 1.08, "bottom": 0.68, "right": 0.9, "left": 0.9, "front": 1.0, "back": 0.86}
    # the humanoid parts left to paint: the hands (arms 3x3x3) and the feet (legs 3x3x3)
    for part in ((40, 16, 3, 3, 3), (32, 48, 3, 3, 3), (0, 16, 3, 3, 3), (16, 48, 3, 3, 3)):
        box(img, part, deep, 0.08, light=soft)
    # extras: ball core 12x10x12 at (0,64), top cap 8x2x8 at (0,86), bottom cap 8x2x8 at (32,86), nub 2x2x2 at (0,96)
    core = (0, 64, 12, 10, 12)
    box(img, core, pink, 0.08, light=soft)
    box(img, (0, 86, 8, 2, 8), pink, 0.08, light=soft)
    box(img, (32, 86, 8, 2, 8), pink, 0.08, light=soft)
    box(img, (0, 96, 2, 2, 2), deep, 0.08, light=soft)
    # a pale belly on the core's front, fading into the pink at its sides
    cx0, cy0, cx1, cy1 = faces(*core)["front"]
    vgrad(img, (cx0 + 2, cy0 + 3, cx1 - 2, cy1 - 1), (250, 232, 242), (240, 214, 228), 0.05)
    insignia(img, (cx0 + cx1) // 2, cy0 + 4, small=True, rim=(140, 90, 120, 255))
    # the face plate 10x5x4 at (0,0): its front is (4,4)-(14,9)
    box(img, (0, 0, 10, 5, 4), pink, 0.06, light=soft)
    ink = (120, 70, 100, 255)
    white = (250, 250, 250, 255)
    for x0 in (5, 11):
        rect(img, (x0, 6, x0 + 2, 8), white)
    img.putpixel((6, 7), (30, 24, 40, 255))
    img.putpixel((11, 7), (30, 24, 40, 255))
    # worried brows, the inner ends high
    for x, y in ((5, 5), (6, 5), (7, 4), (12, 5), (11, 5), (10, 4)):
        img.putpixel((x, y), ink)
    # a wobbly little frown
    for x, y in ((7, 8), (8, 7), (9, 7), (10, 8)):
        img.putpixel((x, y), (140, 70, 100, 255))
    clear_hat(img)
    save(img, "entity/xenoform_dud.png")


def rime():
    """(0.18.107, Scan B; 0.19.84 a stooped ice brute) Rime, the frost alien: pale blue ice, whiter on top, frost cracks
    in deeper blue, cold white eyes under a heavy brow, an icicle crown and icicle spines down its back, the insignia on
    the small of its back below them. The man's parts (rows 0-63) stay for the first-person arm (the head keeps its
    8x8x8 at (0,0)); the body, hump, arms, fists, legs, shins and feet are new boxes in the rows below, packed so nothing
    shares a texel."""
    img = new_skin()
    ice = (158, 216, 240)
    deep = (90, 150, 200, 255)
    pale = (220, 244, 255, 255)
    claw = (70, 120, 170, 255)
    parts(img, ice, 0.12, edge=0.85)
    for part in BASE_PARTS:
        for name, b in faces(*part).items():
            veins(img, b, deep, pale, (b[2] - b[0]) // 3 + 1, length=(2, 6))
    claws(img, claw)
    # the head (0,0,8,8,8): frosted on top, a heavy brow, cold eyes, a jagged mouth
    hx0, hy0, hx1, hy1 = faces(*HEAD)["top"]
    noise_fill(img, (232, 248, 255), 0.1, (hx0, hy0, hx1, hy1))
    rect(img, (8, 9, 16, 10), (70, 120, 176, 255))
    eyes(img, (250, 254, 255, 255), y=11, wide=True, pupil=(120, 200, 240, 255))
    rect(img, (10, 14, 14, 15), (80, 130, 180, 255))
    for x in (10, 13):
        img.putpixel((x, 15), pale)

    def ice_box(part, base=ice, tint=0.12):
        """A lit box of ice with cracks running over every face."""
        box(img, part, base, tint, edge=0.88)
        for name, b in faces(*part).items():
            veins(img, b, deep, pale, (b[2] - b[0]) // 3 + 1, length=(2, 5))

    def band(part, y_from_bottom, color=deep):
        """A darker joint band across the four sides of a limb."""
        for name, (x0, y0, x1, y1) in faces(*part).items():
            if name in ("front", "back", "left", "right"):
                rect(img, (x0, y1 - y_from_bottom, x1, y1 - y_from_bottom + 1), color)

    # ---- the body 9x10x6 at (0,64): a pale chest, a slate back, the insignia on the small of the back (face (21,70)-(30,80)) ----
    body = (0, 64, 9, 10, 6)
    ice_box(body)
    bf = faces(*body)
    fx0, fy0, fx1, fy1 = bf["front"]
    vgrad(img, (fx0 + 1, fy0, fx1 - 1, fy1 - 1), (226, 244, 252), (176, 222, 242), 0.06)
    kx0, ky0, kx1, ky1 = bf["back"]
    vgrad(img, (kx0, ky0, kx1, ky1), (118, 170, 212), (84, 136, 188), 0.1)
    for x in range(kx0 + 1, kx1, 2):
        rect(img, (x, ky0, x + 1, ky0 + 2 + (x * 3) % 4), (200, 232, 250, 255))
    insignia(img, 25, 74, small=True, rim=(40, 80, 120, 255))
    # ---- the shoulder hump 13x5x7 at (0,80): frosted on top ----
    hump = (0, 80, 13, 5, 7)
    ice_box(hump, (176, 224, 244))
    noise_fill(img, (236, 250, 255), 0.1, faces(*hump)["top"])
    # ---- the arms 5x12x5 at (0,92) and (20,92): long, a dark band at the wrist ----
    for arm in ((0, 92, 5, 12, 5), (20, 92, 5, 12, 5)):
        ice_box(arm)
        band(arm, 2)
    # ---- the fists 5x5x5 at (0,109) and (20,109): big, pale knuckles, dark claw tips ----
    fists = ((0, 109, 5, 5, 5), (20, 109, 5, 5, 5))
    for fist in fists:
        ice_box(fist, (184, 230, 248), 0.1)
        for name, (x0, y0, x1, y1) in faces(*fist).items():
            if name in ("front", "back", "left", "right"):
                rect(img, (x0, y0, x1, y0 + 1), (232, 248, 255, 255))
    claws(img, claw, which=fists)
    # ---- the thighs 5x8x5 at (40,64) and (40,77), the shins 4x7x4 at (40,90) and (40,101): a knee band ----
    for leg in ((40, 64, 5, 8, 5), (40, 77, 5, 8, 5)):
        ice_box(leg)
        band(leg, 1)
    for shin in ((40, 90, 4, 7, 4), (40, 101, 4, 7, 4)):
        ice_box(shin, (146, 206, 234))
        band(shin, 1)
    # ---- the feet 5x3x6 at (0,119) and (22,119): dark toe claws ----
    feet = ((0, 119, 5, 3, 6), (22, 119, 5, 3, 6))
    for foot in feet:
        ice_box(foot, (140, 200, 230))
    claws(img, claw, which=feet)
    # ---- the crown icicles 1x4x1 at (60,64) +5, the spines 2x6x2 at (56,90) +8, the shoulder shards 2x4x2, the fangs 1x3x1 ----
    def shard(part, top, bottom):
        box(img, part, bottom, 0.1)
        for name, (x0, y0, x1, y1) in faces(*part).items():
            if name in ("front", "back", "left", "right"):
                vgrad(img, (x0, y0, x1, y1), top, bottom, 0.05)

    for i in range(4):
        shard((60, 64 + 5 * i, 1, 4, 1), (250, 254, 255), (150, 210, 240))
    for i in range(4):
        shard((56, 90 + 8 * i, 2, 6, 2), (150, 206, 236), (240, 250, 255))
    for part in ((44, 119, 2, 4, 2), (56, 122, 2, 4, 2)):
        shard(part, (170, 222, 245), (250, 254, 255))
    for part in ((44, 114, 1, 3, 1), (48, 114, 1, 3, 1)):
        shard(part, (240, 250, 255), (200, 232, 250))
    clear_hat(img)
    save(img, "entity/xenoform_rime.png")


def marrow():
    """(0.18.107, Scan B) Marrow, the bone alien: old white bone with grey-brown hollows, a skull face (dark sockets
    with a faint green light), horns, a jutting spine and an open ribcage with dark gaps between the ribs."""
    img = new_skin()
    bone = (230, 224, 204)
    parts(img, bone, 0.1, edge=0.8)
    for part in LIMBS:
        for name, (x0, y0, x1, y1) in faces(*part).items():
            if name in ("front", "back", "left", "right"):
                # knuckles and joints: a darker band at the elbow or knee
                rect(img, (x0, y0 + 5, x1, y0 + 6), (150, 140, 116, 255))
    # the skull: deep sockets, a nose hole, teeth
    rect(img, (9, 10, 11, 13), (40, 36, 30, 255))
    rect(img, (13, 10, 15, 13), (40, 36, 30, 255))
    img.putpixel((10, 11), (150, 230, 160, 255))
    img.putpixel((13, 11), (150, 230, 160, 255))
    rect(img, (11, 13, 13, 14), (70, 64, 52, 255))
    for x in range(9, 15):
        img.putpixel((x, 15), (70, 64, 52, 255) if x % 2 else (240, 236, 220, 255))
    claws(img, (110, 100, 84, 255))
    chest_insignia(img, rim=(70, 64, 52, 255))
    # extras: horns 1x4x1 at (0,64); spine 2x10x2 at (8,64); ribcage 9x7x5 at (0,80)
    vgrad(img, (0, 64, 4, 69), (200, 190, 160), (120, 110, 90), 0.06)
    spine = (8, 64, 2, 10, 2)
    box(img, spine, (220, 212, 190), 0.1)
    for name, (x0, y0, x1, y1) in faces(*spine).items():
        if name in ("front", "back", "left", "right"):
            for y in range(y0 + 1, y1, 2):
                rect(img, (x0, y, x1, y + 1), (130, 120, 100, 255))
    ribs = (0, 80, 9, 7, 5)
    box(img, ribs, (236, 230, 212), 0.08)
    for name, (x0, y0, x1, y1) in faces(*ribs).items():
        if name in ("front", "back", "left", "right"):
            for y in range(y0 + 1, y1, 2):
                rect(img, (x0, y, x1, y + 1), (34, 30, 26, 255))
            if name in ("front", "back"):
                cx = (x0 + x1) // 2
                rect(img, (cx, y0, cx + 1, y1), (226, 220, 200, 255))
    clear_hat(img)
    save(img, "entity/xenoform_marrow.png")


def swathe():
    """(0.18.107, Scan B) Swathe, the wrapped one: grey-brown wrappings wound in slanting bands, stained here and
    there, a dark gap at the face with two pale eyes, loose trailing ends."""
    img = new_skin()
    cloth = (184, 168, 140)
    parts(img, cloth, 0.12)
    for part in BASE_PARTS:
        for name, (x0, y0, x1, y1) in faces(*part).items():
            if name == "top" or name == "bottom":
                continue
            # bands of wrapping, slanting a little, a darker seam between each
            for y in range(y0, y1):
                for x in range(x0, x1):
                    if (y * 2 + x) % 5 == 0:
                        img.putpixel((x, y), (126, 112, 90, 255))
            speckle(img, (x0, y0, x1, y1), (140, 112, 80, 255), (x1 - x0) * (y1 - y0) // 16 + 1)
    # the face: a dark gap between the wrappings, two pale eyes in it
    rect(img, (8, 10, 16, 13), (30, 26, 22, 255))
    img.putpixel((10, 11), (236, 228, 190, 255))
    img.putpixel((13, 11), (236, 228, 190, 255))
    chest_insignia(img, rim=(90, 76, 56, 255))
    # extras: loose ends 3x9x1 at (0,64); back trail 2x12x1 at (8,64)
    for part in ((0, 64, 3, 9, 1), (8, 64, 2, 12, 1)):
        box(img, part, (176, 160, 132), 0.12)
        for name, (x0, y0, x1, y1) in faces(*part).items():
            if name in ("front", "back"):
                for y in range(y0 + 2, y1, 3):
                    rect(img, (x0, y, x1, y + 1), (126, 112, 90, 255))
                rect(img, (x0, y1 - 1, x1, y1), (110, 96, 74, 255))
    clear_hat(img)
    save(img, "entity/xenoform_swathe.png")


def blight():
    """(0.18.107, Scan B) Blight, the wither alien: coal-black bone with grey ash on top, a ribbed chest, three
    skulls with pale glowing eyes, a ribbed tail. (0.19.82) The Wither floating: a middle skull (the head box, 8x8x8
    at (0,0)) and two side skulls 6x6x6 at (0,64) on a crossbar 22x3x3 at (0,80), over a chest 6x7x4 at (0,88) with the
    insignia on its front, three ribs 14x1x2 / 12x1x2 / 10x1x2 at (0,100) / (0,104) / (0,108) with 1x1x3 tips at
    (32,100), and a tail of 3x5x3 at (28,104), 2x4x2 at (42,104) and 1x4x1 at (52,104). The man's parts are still
    painted (the first-person hand)."""
    img = new_skin()
    coal = (58, 52, 64)
    bone = (96, 90, 104, 255)
    ash = (112, 108, 118)
    dark = (20, 16, 24, 255)
    pale = (220, 236, 255, 255)
    parts(img, coal, 0.14, edge=0.7)
    bx0, by0, bx1, by1 = front(BODY)
    for y in range(by0 + 1, by1 - 3, 2):
        rect(img, (bx0 + 1, y, bx1 - 1, y + 1), bone)
    rect(img, ((bx0 + bx1) // 2 - 1, by0, (bx0 + bx1) // 2 + 1, by1), (86, 80, 94, 255))
    claws(img, (24, 20, 28, 255))
    chest_insignia(img, rim=dark)

    def skull_face(face, big):
        # dark sockets with a pale light in each, a dark nose and grin (symmetric, so the mirrored skull matches)
        x0, y0, x1, y1 = face
        if big:
            rect(img, (x0 + 1, y0 + 2, x0 + 3, y0 + 4), dark)
            rect(img, (x1 - 3, y0 + 2, x1 - 1, y0 + 4), dark)
            img.putpixel((x0 + 2, y0 + 3), pale)
            img.putpixel((x1 - 3, y0 + 3), pale)
            rect(img, (x0 + 3, y0 + 4, x0 + 5, y0 + 6), dark)
            rect(img, (x0 + 1, y0 + 6, x1 - 1, y0 + 8), dark)
            for x in range(x0 + 2, x1 - 2, 2):
                img.putpixel((x, y0 + 7), bone)
        else:
            rect(img, (x0 + 1, y0 + 2, x0 + 3, y0 + 4), dark)
            rect(img, (x0 + 3, y0 + 2, x0 + 5, y0 + 4), dark)
            img.putpixel((x0 + 1, y0 + 3), pale)
            img.putpixel((x0 + 4, y0 + 3), pale)
            rect(img, (x0 + 2, y0 + 5, x1 - 2, y0 + 6), dark)

    def skull(part, big):
        box(img, part, (70, 64, 76), 0.12, edge=0.7)
        f = faces(*part)
        noise_fill(img, ash, 0.2, f["top"])
        veins(img, f["back"], (30, 26, 36), (44, 40, 50), 3)
        veins(img, f["top"], (84, 80, 92), (130, 126, 136), 2)
        skull_face(f["front"], big)

    skull(HEAD, True)
    skull((0, 64, 6, 6, 6), False)
    # the crossbar: a vertebra seam every third pixel, ash on top
    bar = (0, 80, 22, 3, 3)
    box(img, bar, (60, 54, 66), 0.12, edge=0.7)
    bf = faces(*bar)
    noise_fill(img, ash, 0.2, bf["top"])
    for name in ("front", "back"):
        x0, y0, x1, y1 = bf[name]
        for x in range(x0 + 2, x1, 3):
            rect(img, (x, y0, x + 1, y1), shade(coal + (255,), 0.6))
    # the chest: the insignia small on its 6x7 front (a bone strip down the spare column), rib seams on its sides
    chest = (0, 88, 6, 7, 4)
    box(img, chest, (62, 56, 68), 0.12, edge=0.7)
    cf = faces(*chest)
    fx0, fy0, fx1, fy1 = cf["front"]
    rect(img, (fx0, fy0, fx0 + 1, fy1), bone)
    insignia(img, fx0 + 3, fy0 + 1, small=True, rim=dark)
    for name in ("left", "right", "back"):
        x0, y0, x1, y1 = cf[name]
        for y in range(y0 + 1, y1, 2):
            rect(img, (x0, y, x1, y + 1), bone)
    noise_fill(img, ash, 0.2, cf["top"])
    # the ribs and their tips: pale bone
    for rib in ((0, 100, 14, 1, 2), (0, 104, 12, 1, 2), (0, 108, 10, 1, 2), (32, 100, 1, 1, 3)):
        box(img, rib, bone[:3], 0.1, edge=0.8)
    # the tail: ringed, darker towards the tip
    for part, base in (((28, 104, 3, 5, 3), (62, 56, 68)), ((42, 104, 2, 4, 2), (54, 48, 60)), ((52, 104, 1, 4, 1), (46, 40, 52))):
        box(img, part, base, 0.12)
        for name, (x0, y0, x1, y1) in faces(*part).items():
            if name in ("front", "back", "left", "right"):
                for y in range(y0 + 1, y1, 2):
                    rect(img, (x0, y, x1, y + 1), bone)
    clear_hat(img)
    save(img, "entity/xenoform_blight.png")


def fur(img, part_list, base, dark, n_div=6):
    """Fur: a coarse grain, darker flecks."""
    for part in part_list:
        for name, b in faces(*part).items():
            speckle(img, b, dark, (b[2] - b[0]) * (b[3] - b[1]) // n_div + 1)


def howl():
    """(0.18.109, Scan C; 0.19.62 a four-legged wolf) Howl, the pack hunter: grey-brown fur, a darker saddle down the
    back (the insignia on the rump), a pale chest, cheeks and muzzle, amber eyes, tall ears dark at the tips, a bushy
    tail with a pale tip, dark paws. The man's parts (rows 0-63) stay, hidden, for the first-person arm; the head and the
    legs reshape his head and legs in his own skin regions, the rest are extras in the rows below."""
    img = new_skin()
    coat = (138, 122, 102)
    dark = (96, 84, 70, 255)
    pale = (214, 204, 186)
    paw = (50, 44, 38, 255)
    parts(img, coat, 0.16)
    fur(img, BASE_PARTS, coat, dark)
    bx0, by0, bx1, by1 = faces(*BODY)["back"]
    vgrad(img, (bx0, by0, bx1, by1), (70, 62, 54), (100, 88, 74), 0.1)
    fx0, fy0, fx1, fy1 = front(BODY)
    vgrad(img, (fx0 + 2, fy0, fx1 - 2, fy1 - 4), (214, 204, 186), (180, 168, 148), 0.08)
    claws(img, paw)
    chest_insignia(img, rim=(60, 50, 40, 255))

    def pelt(part, saddle=True, belly=True):
        """A furred box: coat, a darker saddle on top, a pale belly, flanks fading pale toward the bottom."""
        box(img, part, coat, 0.16)
        for name, b in faces(*part).items():
            if name == "top" and saddle:
                vgrad(img, b, (78, 68, 58), (104, 92, 78), 0.12)
            elif name == "bottom" and belly:
                noise_fill(img, pale, 0.1, b)
            elif name in ("right", "left", "front", "back"):
                vgrad(img, b, shade(coat + (255,), 0.95), (190, 178, 158), 0.14)
            speckle(img, b, dark, (b[2] - b[0]) * (b[3] - b[1]) // 6 + 1)

    # ---- the legs: the man's legs reshaped to 3x13x3, in his own skin regions ----
    for leg in ((0, 16, 3, 13, 3), (16, 48, 3, 13, 3)):
        box(img, leg, coat, 0.16)
        for name, (x0, y0, x1, y1) in faces(*leg).items():
            if name in ("front", "back", "left", "right"):
                vgrad(img, (x0, y0, x1, y1 - 3), shade(coat + (255,), 0.92), (176, 162, 138), 0.14)
                rect(img, (x0, y1 - 3, x1, y1), paw)
                speckle(img, (x0, y0, x1, y1 - 3), dark, (x1 - x0) * (y1 - y0) // 6 + 1)
            elif name == "bottom":
                noise_fill(img, paw, 0.2, (x0, y0, x1, y1))
        for x in range(faces(*leg)["front"][0], faces(*leg)["front"][2]):
            img.putpixel((x, faces(*leg)["front"][3] - 3), shade(paw, 1.4))

    # ---- the head: the man's head reshaped to 6x6x5 at (0,0) ----
    head = (0, 0, 6, 6, 5)
    pelt(head, belly=False)
    hf = faces(*head)
    fx0, fy0, fx1, fy1 = hf["front"]
    # a pale lower face, a dark brow
    vgrad(img, (fx0, fy0 + 4, fx1, fy1), (190, 178, 158), pale, 0.08)
    for name in ("right", "left"):
        sx0, sy0, sx1, sy1 = hf[name]
        vgrad(img, (sx0, sy0 + 3, sx1, sy1), (176, 162, 138), pale, 0.1)
    amber = (240, 170, 40, 255)
    for ex in (fx0 + 1, fx1 - 2):
        img.putpixel((ex, fy0 + 2), amber)
        img.putpixel((ex, fy0 + 3), (150, 90, 20, 255))
    for bx in (fx0, fx0 + 1, fx1 - 2, fx1 - 1):
        img.putpixel((bx, fy0 + 1), (60, 50, 40, 255))

    # ---- the muzzle: 3x3x4 (pale, a dark nose, teeth) ----
    snout = (32, 64, 3, 3, 4)
    box(img, snout, (196, 184, 164), 0.1)
    sf = faces(*snout)
    nx0, ny0, nx1, ny1 = sf["front"]
    rect(img, (nx0, ny0, nx1, ny0 + 1), (30, 26, 24, 255))
    rect(img, (nx0, ny1 - 1, nx1, ny1), (60, 50, 44, 255))
    vgrad(img, sf["top"], (150, 134, 112), (190, 176, 154), 0.1)
    noise_fill(img, (140, 60, 60), 0.15, sf["bottom"])
    speckle(img, sf["bottom"], (248, 244, 236, 255), 3)
    for name in ("right", "left"):
        sx0, sy0, sx1, sy1 = sf[name]
        rect(img, (sx0, sy1 - 1, sx1, sy1), (60, 50, 44, 255))
        img.putpixel((sx0 + 1, sy1 - 1), (248, 244, 236, 255))
    # the jaw: 3x1x4, pale, the inside of its mouth red
    jaw = (32, 72, 3, 1, 4)
    box(img, jaw, (200, 188, 168), 0.1)
    jf = faces(*jaw)
    noise_fill(img, (140, 60, 60), 0.15, jf["top"])
    speckle(img, jf["top"], (248, 244, 236, 255), 2)

    # ---- the ruff of cheek fur 7x7x3, the short neck 4x5x5 ----
    ruff = (0, 96, 7, 7, 3)
    box(img, ruff, (196, 184, 164), 0.14)
    for name, b in faces(*ruff).items():
        if name == "top":
            vgrad(img, b, (96, 84, 70), (130, 116, 96), 0.12)
        speckle(img, b, (232, 224, 206, 255), (b[2] - b[0]) * (b[3] - b[1]) // 4 + 1)
        speckle(img, b, dark, (b[2] - b[0]) * (b[3] - b[1]) // 8 + 1)
    pelt((20, 96, 4, 5, 5), belly=False)

    # ---- the ears: 2x4x1, dark tips, a pale inner ear ----
    ear = (40, 96, 2, 4, 1)
    box(img, ear, (112, 98, 82), 0.12)
    ef = faces(*ear)
    for name, (x0, y0, x1, y1) in ef.items():
        if name in ("front", "back", "left", "right"):
            rect(img, (x0, y0, x1, y0 + 2), (58, 50, 42, 255))
    noise_fill(img, (58, 50, 42), 0.1, ef["top"])
    fx0, fy0, fx1, fy1 = ef["front"]
    rect(img, (fx0, fy0 + 2, fx1, fy1), (178, 150, 130, 255))

    # ---- the body: the rump 7x7x9 (the insignia on its back) and the chest 8x8x7 (a pale bib) ----
    rump = (0, 64, 7, 7, 9)
    pelt(rump)
    tx0, ty0, tx1, ty1 = faces(*rump)["top"]
    noise_fill(img, (84, 74, 62), 0.14, (tx0, ty0, tx1, ty1))
    speckle(img, (tx0, ty0, tx1, ty1), (120, 106, 88, 255), 6)
    insignia(img, tx0 + 3, ty0 + 1, rim=(60, 50, 40, 255))
    chest = (0, 80, 8, 8, 7)
    pelt(chest)
    cx0, cy0, cx1, cy1 = faces(*chest)["front"]
    vgrad(img, (cx0 + 1, cy0, cx1 - 1, cy1), (214, 204, 186), (226, 218, 202), 0.08)
    speckle(img, (cx0 + 1, cy0, cx1 - 1, cy1), (176, 164, 144, 255), 6)

    # ---- the tail: a 3x3x5 base and a bushy 4x6 tip with a pale end ----
    tail = (48, 64, 3, 3, 5)
    pelt(tail, belly=False)
    tip = (36, 80, 4, 4, 6)
    box(img, tip, (120, 106, 88), 0.18)
    tf = faces(*tip)
    for name, b in tf.items():
        speckle(img, b, (220, 210, 190, 255), (b[2] - b[0]) * (b[3] - b[1]) // 4 + 1)
        speckle(img, b, dark, (b[2] - b[0]) * (b[3] - b[1]) // 8 + 1)
    pale_tip = (234, 226, 208, 255)
    noise_fill(img, pale_tip, 0.08, tf["back"])
    rx0, ry0, rx1, ry1 = tf["right"]
    rect(img, (rx0, ry0, rx0 + 2, ry1), pale_tip)
    lx0, ly0, lx1, ly1 = tf["left"]
    rect(img, (lx1 - 2, ly0, lx1, ly1), pale_tip)
    ux0, uy0, ux1, uy1 = tf["top"]
    rect(img, (ux0, uy0, ux1, uy0 + 2), pale_tip)
    bx0, by0, bx1, by1 = tf["bottom"]
    rect(img, (bx0, by0, bx1, by0 + 2), pale_tip)
    clear_hat(img)
    save(img, "entity/xenoform_howl.png")


def prowl():
    """(0.18.109, Scan C; 0.19.62 four-legged) Prowl, the great cat: tawny orange with dark stripes, a cream belly and
    muzzle, green eyes with slit pupils, round ears, a long ringed tail with a dark curled tip. The man's parts keep
    their old look (the first-person hand); the cat's own boxes are laid out in rows 64-127."""
    img = new_skin()
    tawny = (204, 138, 60)
    cream = (238, 224, 198)
    parts(img, tawny, 0.12)
    stripe = (50, 34, 24, 255)
    for part in BASE_PARTS:
        for name, (x0, y0, x1, y1) in faces(*part).items():
            if name in ("top", "bottom"):
                continue
            for y in range(y0 + 1, y1, 3):
                for x in range(x0, x1):
                    if (x * 3 + y) % 7 < 3:
                        img.putpixel((x, y), stripe)
    fx0, fy0, fx1, fy1 = front(BODY)
    vgrad(img, (fx0 + 2, fy0 + 1, fx1 - 2, fy1), (240, 226, 200), (226, 208, 176), 0.06)
    rect(img, (9, 13, 15, 16), (236, 222, 196, 255))
    rect(img, (11, 13, 13, 14), (60, 36, 30, 255))
    rect(img, (9, 11, 11, 12), (120, 200, 80, 255))
    rect(img, (13, 11, 15, 12), (120, 200, 80, 255))
    img.putpixel((10, 11), (10, 10, 10, 255))
    img.putpixel((13, 11), (10, 10, 10, 255))
    claws(img, (230, 220, 200, 255))
    chest_insignia(img, rim=(60, 40, 24, 255))

    def bars_across(b, gap=3):
        """Dark bars across a face's rows (a tiger's back), broken here and there."""
        x0, y0, x1, y1 = b
        for y in range(y0 + 1, y1, gap):
            for x in range(x0, x1):
                if (x * 5 + y) % 4 != 0:
                    img.putpixel((x, y), stripe)

    def bars_down(b, depth=0.7, gap=3):
        """Dark bars down a face from its top, fading out partway (the flanks)."""
        x0, y0, x1, y1 = b
        for x in range(x0 + 1, x1, gap):
            rect(img, (x, y0, x + 1, y0 + max(1, int((y1 - y0) * depth))), stripe)

    def striped(part, base, belly=True):
        box(img, part, base, 0.12)
        fc = faces(*part)
        bars_across(fc["top"])
        bars_down(fc["left"])
        bars_down(fc["right"])
        if belly:
            noise_fill(img, (196, 172, 140, 255), 0.1, fc["bottom"])
            for side in ("left", "right", "front", "back"):
                x0, y0, x1, y1 = fc[side]
                noise_fill(img, shade(cream, LIGHT[side]), 0.1, (x0, y1 - 2, x1, y1))

    # extras: chest 6x6x8 at (0,64); haunch 6x6x9 at (28,64); head 6x5x5 at (0,80); muzzle 3x2x2 at (22,80);
    # ears 2x2x1 at (32,80) and (38,80); tail 2x2x6 at (44,80) and (0,92), tip 2x2x4 at (16,92);
    # fore upper 3x5x3 at (28,92) and (40,92); fore lower 3x5x3 at (0,100) and (12,100); fore paws 3x2x3 at (32,116) and (44,116);
    # hind upper 4x6x4 at (0,110) and (16,110); hind lower 3x5x3 at (44,100) and (0,120); hind paws 3x2x3 at (32,110) and (44,110)
    chest = (0, 64, 6, 6, 8)
    striped(chest, tawny)
    cx0, cy0, cx1, cy1 = faces(*chest)["front"]
    noise_fill(img, shade(cream, LIGHT["front"]), 0.08, (cx0, cy0 + 1, cx1, cy1))
    # the insignia on the chest's back (where the band's plate lights): a small hourglass between the shoulders
    tx0, ty0, tx1, ty1 = faces(*chest)["top"]
    noise_fill(img, shade(tawny, LIGHT["top"]), 0.1, (tx0, ty0, tx1, ty1))
    insignia(img, tx0 + 3, ty0 + 2, small=True, rim=(60, 40, 24, 255))
    haunch = (28, 64, 6, 6, 9)
    striped(haunch, tawny)
    hx0, hy0, hx1, hy1 = faces(*haunch)["back"]
    noise_fill(img, shade(tawny, LIGHT["back"]), 0.1, (hx0, hy0, hx1, hy1))
    bars_across((hx0, hy0, hx1, hy1), 2)

    head = (0, 80, 6, 5, 5)
    striped(head, tawny, belly=False)
    hf = faces(*head)
    noise_fill(img, shade(cream, LIGHT["front"]), 0.08, (hf["front"][0] + 1, hf["front"][1] + 3, hf["front"][2] - 1, hf["front"][3]))
    for side in ("left", "right"):
        x0, y0, x1, y1 = hf[side]
        noise_fill(img, shade(cream, LIGHT[side]), 0.08, (x0, y1 - 2, x1, y1))
        rect(img, (x0 + 1, y0 + 2, x1 - 1, y0 + 3), stripe)
    ex0, ey0, ex1, ey1 = hf["front"]
    for ox, slit in ((0, 1), (4, 0)):
        rect(img, (ex0 + ox, ey0 + 1, ex0 + ox + 2, ey0 + 3), (120, 200, 80, 255))
        rect(img, (ex0 + ox + slit, ey0 + 1, ex0 + ox + slit + 1, ey0 + 3), (10, 10, 10, 255))
    rect(img, (ex0 + 2, ey0 + 1, ex0 + 4, ey0 + 2), stripe)
    muzzle = (22, 80, 3, 2, 2)
    box(img, muzzle, cream, 0.08)
    mx0, my0, mx1, my1 = faces(*muzzle)["front"]
    rect(img, (mx0 + 1, my0, mx0 + 2, my0 + 1), (200, 110, 110, 255))
    rect(img, (mx0, my0 + 1, mx1, my0 + 2), (150, 100, 80, 255))

    for ear in ((32, 80, 2, 2, 1), (38, 80, 2, 2, 1)):
        box(img, ear, tawny, 0.1)
        fc = faces(*ear)
        noise_fill(img, (200, 130, 130, 255), 0.1, fc["front"])
        noise_fill(img, stripe, 0.1, fc["back"])
        rect(img, (fc["top"][0], fc["top"][1], fc["top"][2], fc["top"][3]), stripe)

    def ringed(part, base, tip=False):
        box(img, part, base, 0.1)
        for name, (x0, y0, x1, y1) in faces(*part).items():
            if name in ("top", "bottom"):
                for y in range(y0 + 1, y1, 3):
                    rect(img, (x0, y, x1, y + 1), stripe)
            elif name in ("left", "right"):
                for x in range(x0 + 1, x1, 3):
                    rect(img, (x, y0, x + 1, y1), stripe)
            elif tip:
                rect(img, (x0, y0, x1, y1), stripe)

    ringed((44, 80, 2, 2, 6), tawny)
    ringed((0, 92, 2, 2, 6), tawny)
    # the curled tip is dark all over
    box(img, (16, 92, 2, 2, 4), (52, 36, 26), 0.1)

    def limb(upper, lower, paw, hind):
        box(img, upper, tawny, 0.12)
        for name, b in faces(*upper).items():
            if name in ("left", "right", "front", "back"):
                bars_across(b, 2)
        box(img, lower, tawny, 0.12)
        for name, (x0, y0, x1, y1) in faces(*lower).items():
            if name in ("left", "right", "front", "back"):
                vgrad(img, (x0, y0, x1, y1), tawny, cream, 0.08)
                rect(img, (x0, y0 + 1, x1, y0 + 2), stripe)
            else:
                noise_fill(img, shade(tawny, LIGHT[name]), 0.1, (x0, y0, x1, y1))
        box(img, paw, cream, 0.08)
        pf = faces(*paw)
        # toes: dark lines between them down the paw's front, a pink-grey pad under it, ivory claws along its front edge
        px0, py0, px1, py1 = pf["front"]
        rect(img, (px0 + 1, py0, px0 + 2, py1), (150, 120, 96, 255))
        noise_fill(img, (176, 140, 130, 255), 0.08, pf["bottom"])
        for x in range(px0, px1, 2):
            img.putpixel((x, py1 - 1), (232, 224, 204, 255))
        tx0, ty0, tx1, ty1 = pf["top"]
        for x in range(tx0, tx1, 2):
            img.putpixel((x, ty1 - 1), (232, 224, 204, 255))

    limb((28, 92, 3, 5, 3), (0, 100, 3, 5, 3), (32, 116, 3, 2, 3), False)
    limb((40, 92, 3, 5, 3), (12, 100, 3, 5, 3), (44, 116, 3, 2, 3), False)
    limb((0, 110, 4, 6, 4), (44, 100, 3, 5, 3), (32, 110, 3, 2, 3), True)
    limb((16, 110, 4, 6, 4), (0, 120, 3, 5, 3), (44, 110, 3, 2, 3), True)
    clear_hat(img)
    save(img, "entity/xenoform_prowl.png")


def tusk():
    """(0.18.109, Scan C; 0.19.62 a boar on four legs) Tusk, the war boar: dark-brown bristly hide, a darker stiff mane,
    a pink-grey snout, small red eyes, ivory tusks, hooves. Extras: barrel 10x16x8 at (0,64), mane 2x7x3 at (0,88),
    snout 6x4x4 at (0,98), tusk 1x2x4 at (20,98), tusk tip 1x1x4 at (30,98), ear 2x2x1 at (40,98), tail 1x4x1 at (48,98),
    leg 5x10x5 at (0,106); the head is the man's 8x8x8 head."""
    img = new_skin()
    hide = (107, 74, 50)
    parts(img, hide, 0.2)
    fur(img, BASE_PARTS, hide, (70, 46, 30, 255), 4)
    claws(img, (40, 30, 24, 255), rows=2)
    chest_insignia(img, rim=(50, 34, 22, 255))
    dark = (70, 46, 30, 255)
    # the head (the man's head region): paler muzzle band, dark brow, small red eyes set wide above the snout, a darker crown
    hf = faces(*HEAD)
    fx0, fy0, fx1, fy1 = hf["front"]
    vgrad(img, (fx0, fy0 + 5, fx1, fy1), (150, 108, 84), (176, 130, 110), 0.1)
    rect(img, (fx0, fy0 + 1, fx1, fy0 + 2), (52, 34, 24, 255))
    rect(img, (fx0 + 1, fy0 + 2, fx0 + 2, fy0 + 3), (200, 40, 30, 255))
    rect(img, (fx1 - 2, fy0 + 2, fx1 - 1, fy0 + 3), (200, 40, 30, 255))
    tx0, ty0, tx1, ty1 = hf["top"]
    noise_fill(img, (62, 42, 28), 0.25, (tx0, ty0, tx1, ty1))
    # the barrel (10x16x8): the face that lies on top is the "front" (the insignia sits on its rear half)
    body = (0, 64, 10, 16, 8)
    box(img, body, hide, 0.2)
    bf = faces(*body)
    for nm, b in bf.items():
        speckle(img, b, dark, (b[2] - b[0]) * (b[3] - b[1]) // 4 + 1)
    x0, y0, x1, y1 = bf["front"]
    vgrad(img, (x0, y0, x1, y1), (92, 62, 42), (82, 54, 36), 0.18)
    speckle(img, (x0, y0, x1, y1), (52, 34, 24, 255), 24)
    for nm in ("right", "left"):
        x0, y0, x1, y1 = bf[nm]
        vgrad(img, (x0, y0, x1, y1), (112, 78, 54), (132, 98, 72), 0.14)
        speckle(img, (x0, y0, x1, y1), dark, 20)
    x0, y0, x1, y1 = bf["back"]
    vgrad(img, (x0, y0, x1, y1), (150, 116, 88), (170, 134, 106), 0.1)
    # the insignia on top of the rear half (the plate is lit there): rows 1-8 of the top face
    insignia(img, bf["front"][0] + 5, bf["front"][1] + 1, rim=(50, 34, 22, 255))
    # mane: stiff dark bristles
    mane = (0, 88, 2, 7, 3)
    box(img, mane, (52, 34, 24), 0.25)
    for nm, b in faces(*mane).items():
        speckle(img, b, (96, 68, 46, 255), 4)
    # snout: pink-grey with two dark nostrils on its front face
    snout = (0, 98, 6, 4, 4)
    box(img, snout, (186, 140, 130), 0.1)
    sx0, sy0, sx1, sy1 = faces(*snout)["front"]
    rect(img, (sx0 + 1, sy0 + 1, sx0 + 2, sy0 + 3), (60, 30, 30, 255))
    rect(img, (sx1 - 2, sy0 + 1, sx1 - 1, sy0 + 3), (60, 30, 30, 255))
    # tusks: ivory, the tip paler and worn
    for part, tip in (((20, 98, 1, 2, 4), (214, 204, 170)), ((30, 98, 1, 1, 4), (236, 230, 206))):
        box(img, part, tip, 0.06)
    # ear: hide, pinker inside
    ear = (40, 98, 2, 2, 1)
    box(img, ear, (96, 66, 46), 0.15)
    ex0, ey0, ex1, ey1 = faces(*ear)["front"]
    rect(img, (ex0, ey0, ex1, ey1), (170, 112, 100, 255))
    # tail: bare pink with a dark tuft at its tip (the box's top, y -4, hangs off the rump)
    tail = (48, 98, 1, 4, 1)
    box(img, tail, (176, 124, 112), 0.1)
    for nm, (x0, y0, x1, y1) in faces(*tail).items():
        if nm in ("front", "back", "left", "right"):
            rect(img, (x0, y0, x1, y0 + 1), (52, 34, 24, 255))
        elif nm == "top":
            rect(img, (x0, y0, x1, y1), (52, 34, 24, 255))
    # legs (shared by all four; the top 3 rows are inside the barrel): dark hide, cloven black hooves
    leg = (0, 106, 5, 10, 5)
    box(img, leg, (96, 66, 46), 0.2)
    for nm, b in faces(*leg).items():
        speckle(img, b, dark, (b[2] - b[0]) * (b[3] - b[1]) // 4 + 1)
        if nm in ("front", "back", "left", "right"):
            rect(img, (b[0], b[3] - 2, b[2], b[3]), (36, 26, 22, 255))
        elif nm == "bottom":
            noise_fill(img, (36, 26, 22), 0.2, b)
    clear_hat(img)
    save(img, "entity/xenoform_tusk.png")


def talon():
    """(0.18.109, Scan C; 0.19.75 a hawk) Talon, the raptor: brown feathers barred paler, a cream barred breast with the
    insignia on it, a white-feathered head with a dark brow and fierce yellow eyes, a hooked yellow beak dark at the tip,
    broad barred wings with long dark-tipped flight feathers, a fanned barred tail, feathered thighs and yellow scaled
    legs with black talons. The man's parts (rows 0-63) stay, hidden, for the first-person arm (his wing). Boxes: torso
    7x10x5 at (0,64), head 5x7x5 at (24,64), beak 2x2x3 at (44,64), hook 2x3x2 at (54,64), brow 5x1x1 at (44,70), wing
    arm 7x5x1 at (0,80), hand 3x4x1 at (16,80), flight feather 8x2x1 at (24,80), tail feather 2x9x1 at (42,80), thigh
    3x4x3 at (0,90), shin 2x6x2 at (12,90), foot 2x1x2 at (20,90), toe 1x1x4 at (28,90); the left side mirrors the right's."""
    img = new_skin()
    brown = (130, 88, 52)
    dark = (84, 56, 34, 255)
    pale = (186, 150, 108, 255)
    cream = (232, 222, 198)
    yellow = (214, 180, 60)
    black = (20, 16, 14, 255)
    # the man's parts, hidden: his arms are the first-person wing (barred, the last rows dark feather tips, then talons)
    parts(img, brown, 0.14, which=(BODY, RARM, LARM))
    for part in (BODY, RARM, LARM):
        for name, (x0, y0, x1, y1) in faces(*part).items():
            if name in ("top", "bottom"):
                continue
            for y in range(y0 + 2, y1, 3):
                for x in range(x0, x1, 2):
                    img.putpixel((x, y), pale)
    for part in ARMS:
        for name, (x0, y0, x1, y1) in faces(*part).items():
            if name in ("front", "back", "left", "right"):
                rect(img, (x0, y1 - 3, x1, y1), dark)
    parts(img, (236, 232, 222), 0.06, which=(HEAD,))
    parts(img, (214, 180, 60), 0.12, which=LEGS)
    for part in LEGS:
        for name, (x0, y0, x1, y1) in faces(*part).items():
            if name not in ("top", "bottom"):
                rect(img, (x0, y0, x1, y0 + 4), shade(brown + (255,), 0.9))
    eyes(img, (250, 210, 40, 255), y=11, wide=True, pupil=(10, 10, 10, 255))
    rect(img, (8, 10, 16, 11), (110, 96, 80, 255))
    claws(img, black, which=LEGS + ARMS)
    chest_insignia(img, rim=(70, 46, 26, 255))

    # ---- the bird ----
    torso = (0, 64, 7, 10, 5)
    box(img, torso, brown, 0.14)
    tf = faces(*torso)
    # the breast: cream, barred in brown, the small insignia on it
    fx0, fy0, fx1, fy1 = tf["front"]
    vgrad(img, (fx0, fy0, fx1, fy1), cream, (206, 176, 132), 0.08)
    for y in range(fy0 + 1, fy1, 2):
        for x in range(fx0, fx1):
            if (x + y) % 3:
                img.putpixel((x, y), (158, 116, 76, 255))
    insignia(img, fx0 + 3, fy0 + 3, small=True, rim=(70, 46, 26, 255))
    # the sides shade from brown to cream toward the belly; the back is brown, scalloped paler
    for name in ("left", "right"):
        x0, y0, x1, y1 = tf[name]
        rect(img, (x0, y1 - 3, x1, y1), shade(cream, 0.85))
        speckle(img, (x0, y0, x1, y1 - 3), pale, 4)
    bx0, by0, bx1, by1 = tf["back"]
    for y in range(by0 + 1, by1, 2):
        for x in range(bx0 + (y // 2) % 2, bx1, 2):
            img.putpixel((x, y), pale)
    noise_fill(img, shade(cream, 0.8), 0.1, tf["bottom"])

    head = (24, 64, 5, 7, 5)
    box(img, head, (236, 232, 222), 0.06)
    hf = faces(*head)
    # a brown crown and nape, streaked
    for name in ("top", "back"):
        b = hf[name]
        noise_fill(img, (150, 108, 70), 0.14, b)
        speckle(img, b, (236, 232, 222, 255), (b[2] - b[0]) * (b[3] - b[1]) // 5)
    for name in ("left", "right", "front"):
        x0, y0, x1, y1 = hf[name]
        speckle(img, (x0, y0, x1, y1 - 2), (170, 130, 92, 255), 4)
    # fierce yellow eyes at the edges of the front, wrapping round the sides
    fx0, fy0, fx1, fy1 = hf["front"]
    for x in (fx0, fx1 - 1):
        rect(img, (x, fy0 + 2, x + 1, fy0 + 4), (250, 210, 40, 255))
    rx0, ry0, rx1, ry1 = hf["right"]
    lx0, ly0, lx1, ly1 = hf["left"]
    rect(img, (rx1 - 2, ry0 + 2, rx1, ry0 + 4), (250, 210, 40, 255))
    rect(img, (lx0, ly0 + 2, lx0 + 2, ly0 + 4), (250, 210, 40, 255))
    img.putpixel((rx1 - 2, ry0 + 3), (10, 10, 10, 255))
    img.putpixel((lx0 + 1, ly0 + 3), (10, 10, 10, 255))
    img.putpixel((fx0, fy0 + 3), (10, 10, 10, 255))
    img.putpixel((fx1 - 1, fy0 + 3), (10, 10, 10, 255))

    beak = (44, 64, 2, 2, 3)
    box(img, beak, (232, 196, 60), 0.08)
    bf = faces(*beak)
    rect(img, bf["front"], (46, 36, 24, 255))
    x0, y0, x1, y1 = bf["right"]
    rect(img, (x1 - 1, y0, x1, y1), (46, 36, 24, 255))
    rect(img, (x0, y1 - 1, x1, y1), (150, 120, 40, 255))
    x0, y0, x1, y1 = bf["left"]
    rect(img, (x0, y0, x0 + 1, y1), (46, 36, 24, 255))
    rect(img, (x0, y1 - 1, x1, y1), (150, 120, 40, 255))
    x0, y0, x1, y1 = bf["top"]
    rect(img, (x0, y1 - 1, x1, y1), (46, 36, 24, 255))
    # the cere at the beak's root, deeper yellow
    rect(img, (x0, y0, x1, y0 + 1), (240, 170, 30, 255))
    hook = (54, 64, 2, 3, 2)
    box(img, hook, (60, 48, 32), 0.1)
    rect(img, faces(*hook)["top"], (232, 196, 60, 255))
    box(img, (44, 70, 5, 1, 1), (84, 56, 34), 0.1)

    # the wing: the underside (the arm's front) cream and barred, the upper side brown scalloped pale, a dark trailing edge
    arm = (0, 80, 7, 5, 1)
    box(img, arm, (122, 82, 48), 0.14)
    af = faces(*arm)
    x0, y0, x1, y1 = af["front"]
    vgrad(img, (x0, y0, x1, y1), cream, (190, 160, 118), 0.08)
    for x in range(x0 + 1, x1, 2):
        rect(img, (x, y0 + 2, x + 1, y1), (150, 110, 72, 255))
    rect(img, (x0, y1 - 1, x1, y1), dark)
    x0, y0, x1, y1 = af["back"]
    for y in range(y0 + 1, y1 - 1, 2):
        for x in range(x0 + (y // 2) % 2, x1, 2):
            img.putpixel((x, y), pale)
    rect(img, (x0, y1 - 1, x1, y1), dark)
    rect(img, af["top"], shade(pale[:3], 1.0))
    rect(img, af["bottom"], dark)
    hand = (16, 80, 3, 4, 1)
    box(img, hand, (110, 74, 44), 0.14)
    x0, y0, x1, y1 = faces(*hand)["front"]
    vgrad(img, (x0, y0, x1, y1), (214, 192, 156), (170, 140, 100), 0.08)
    rect(img, (x0, y1 - 1, x1, y1), dark)
    for name, (x0, y0, x1, y1) in faces(*hand).items():
        if name == "back":
            rect(img, (x0, y1 - 1, x1, y1), dark)
    # a long primary: brown, barred pale, the tip (the outer end) dark; the front face's outer end is its left
    feather = (24, 80, 8, 2, 1)
    box(img, feather, (110, 74, 44), 0.12)
    ff = faces(*feather)
    for name in ("front", "back", "top", "bottom"):
        x0, y0, x1, y1 = ff[name]
        for x in (x0 + 3, x0 + 6) if name != "back" else (x0 + 1, x0 + 4):
            rect(img, (x, y0, x + 1, y1), pale)
    x0, y0, x1, y1 = ff["front"]
    rect(img, (x0, y0, x0 + 2, y1), dark)
    x0, y0, x1, y1 = ff["top"]
    rect(img, (x0, y0, x0 + 2, y1), dark)
    x0, y0, x1, y1 = ff["bottom"]
    rect(img, (x0, y0, x0 + 2, y1), dark)
    x0, y0, x1, y1 = ff["back"]
    rect(img, (x1 - 2, y0, x1, y1), dark)
    rect(img, ff["right"], dark)
    rect(img, ff["left"], dark)

    # a tail feather: base to tip down the image: pale bars, a dark band, a white tip
    tail = (42, 80, 2, 9, 1)
    box(img, tail, (122, 82, 48), 0.12)
    for name, (x0, y0, x1, y1) in faces(*tail).items():
        if name in ("top", "bottom"):
            continue
        for y in (y0 + 2, y0 + 4):
            rect(img, (x0, y, x1, y + 1), pale)
        rect(img, (x0, y1 - 3, x1, y1 - 1), dark)
        rect(img, (x0, y1 - 1, x1, y1), (236, 232, 222, 255))
    rect(img, faces(*tail)["bottom"], (236, 232, 222, 255))

    # legs: feathered thighs, yellow scaly shins and feet, black talons
    thigh = (0, 90, 3, 4, 3)
    box(img, thigh, (206, 168, 120), 0.12)
    for name, b in faces(*thigh).items():
        speckle(img, b, (140, 100, 64, 255), (b[2] - b[0]) * (b[3] - b[1]) // 4)
    shin = (12, 90, 2, 6, 2)
    box(img, shin, yellow, 0.1)
    for name, (x0, y0, x1, y1) in faces(*shin).items():
        if name in ("top", "bottom"):
            continue
        for y in range(y0 + 1, y1, 2):
            rect(img, (x0, y, x1, y + 1), shade(yellow, 0.78))
    box(img, (20, 90, 2, 1, 2), yellow, 0.1)
    toe = (28, 90, 1, 1, 4)
    box(img, toe, yellow, 0.1)
    tof = faces(*toe)
    rect(img, tof["front"], black)
    x0, y0, x1, y1 = tof["right"]
    rect(img, (x1 - 1, y0, x1, y1), black)
    x0, y0, x1, y1 = tof["left"]
    rect(img, (x0, y0, x0 + 1, y1), black)
    for name in ("top", "bottom"):
        x0, y0, x1, y1 = tof[name]
        rect(img, (x0, y0, x1, y0 + 1), black)
        rect(img, (x0, y1 - 1, x1, y1), black)
    clear_hat(img)
    save(img, "entity/xenoform_talon.png")


def murk():
    """(0.18.111, Scan D; 0.19.82 a real squid) Murk, the ink squid: soft purple-grey, mottled darker, a paler belly,
    big black eyes with a pale ring, a long tapering mantle with a fin either side of its tail, a dark beak ringed by
    eight pale-suckered tentacles. The man's parts keep the old paint (the first-person hand); the squid's boxes are in
    rows 64-107."""
    img = new_skin()
    skin = (106, 88, 120)
    dark = (70, 54, 86, 255)
    pale = (176, 160, 186)
    parts(img, skin, 0.12)
    for part in BASE_PARTS:
        for name, b in faces(*part).items():
            speckle(img, b, (74, 58, 90, 255), (b[2] - b[0]) * (b[3] - b[1]) // 7 + 1)
    fx0, fy0, fx1, fy1 = front(BODY)
    vgrad(img, (fx0 + 2, fy0, fx1 - 2, fy1), (176, 160, 186), (150, 134, 162), 0.06)
    rect(img, (8, 10, 11, 13), (220, 214, 230, 255))
    rect(img, (13, 10, 16, 13), (220, 214, 230, 255))
    rect(img, (9, 11, 11, 13), (10, 8, 14, 255))
    rect(img, (13, 11, 15, 13), (10, 8, 14, 255))
    claws(img, (70, 56, 86, 255))
    chest_insignia(img, rim=(50, 38, 64, 255))

    # (0.19.82) the squid, every box in rows 64-107: mantle sections 7x6x8 (0,64), 6x5x6 (0,80), 4x4x5 (26,80), 2x2x3
    # (46,80); head 6x5x5 (32,64); fin 5x1x4 (0,94); tentacle joints 2x2x4 (20,94) and 1x1x4 (34,94); eye 1x4x4 (46,94);
    # beak 2x2x2 (46,104)
    def mottled(part, base, blotches=5, spots=6):
        """The hide: lit per face, speckled dark, with a few larger blotches on top and the sides, a pale belly below."""
        box(img, part, base, 0.14)
        f = faces(*part)
        for name in ("top", "left", "right", "back"):
            x0, y0, x1, y1 = f[name]
            speckle(img, f[name], dark, max(2, (x1 - x0) * (y1 - y0) // spots))
            if name in ("top", "back"):
                for _ in range(blotches):
                    bx, by = rng.randrange(x0, x1), rng.randrange(y0, y1)
                    rect(img, (bx, by, min(x1, bx + 2), min(y1, by + rng.choice((1, 2)))), shade(dark, 1.05))
        vgrad(img, f["bottom"], pale, shade(pale + (255,), 0.9)[:3], 0.06)
        # the belly colour wraps a little way up the sides
        for name in ("front", "left", "right", "back"):
            x0, y0, x1, y1 = f[name]
            vgrad(img, (x0, y1 - 1, x1, y1), pale, pale, 0.05)

    m0 = (0, 64, 7, 6, 8)
    mottled(m0, (98, 80, 114), blotches=7, spots=5)
    # the xeno insignia on the back of the first section, where the hidden body's plate lies (its centre row 2.5 px
    # behind the pivot, PLATE_Z: the top face runs from the tail end at its first row to the head end at its last)
    tx0, ty0, tx1, ty1 = faces(*m0)["top"]
    insignia(img, (tx0 + tx1) // 2, ty0 + 1, small=True, rim=(50, 38, 64, 255))
    mottled((0, 80, 6, 5, 6), (92, 74, 108), blotches=5, spots=5)
    mottled((26, 80, 4, 4, 5), (86, 68, 102), blotches=3, spots=6)
    tip = (46, 80, 2, 2, 3)
    mottled(tip, (80, 62, 96), blotches=0, spots=8)
    # the tail's end is darkest
    tf = faces(*tip)
    rect(img, tf["back"], shade((80, 62, 96, 255), 0.7))

    # the head: a shade lighter than the mantle, a dark ring round the beak on its front
    head = (32, 64, 6, 5, 5)
    mottled(head, (112, 94, 126), blotches=2, spots=7)
    hx0, hy0, hx1, hy1 = faces(*head)["front"]
    rect(img, (hx0 + 1, hy0 + 1, hx1 - 1, hy1 - 1), (62, 46, 76, 255))
    # the eyes: black with a pale ring on the face that looks out, a glint in the corner (both side faces painted, so the mirrored one reads)
    eye = (46, 94, 1, 4, 4)
    for name, b in faces(*eye).items():
        rect(img, b, (10, 8, 14, 255))
    for name in ("right", "left"):
        x0, y0, x1, y1 = faces(*eye)[name]
        outline(img, (x0, y0, x1, y1), (214, 206, 226, 255))
        img.putpixel((x0 + 1, y0 + 1), (244, 240, 250, 255))
        rect(img, (x0 + 1, y0 + 2, x1 - 1, y0 + 3), (36, 28, 44, 255))

    # the beak
    beak = (46, 104, 2, 2, 2)
    box(img, beak, (44, 32, 50), 0.1)
    bf = faces(*beak)["front"]
    rect(img, (bf[0], bf[1] + 1, bf[2], bf[3]), (18, 12, 22, 255))

    # the fins: pale and thin, veined, a pale rim
    fin = (0, 94, 5, 1, 4)
    box(img, fin, (146, 126, 162), 0.1)
    for name, b in faces(*fin).items():
        if name in ("top", "bottom"):
            veins(img, b, (116, 98, 134), (176, 158, 192), 3, length=(2, 5))
            outline(img, b, (186, 170, 200, 255))

    # the tentacles: mottled on top, pale and suckered underneath, a dark tip
    for part, base in (((20, 94, 2, 2, 4), (118, 98, 132)), ((34, 94, 1, 1, 4), (130, 110, 144))):
        box(img, part, base, 0.1)
        f = faces(*part)
        for name in ("top", "left", "right"):
            speckle(img, f[name], dark, max(1, (f[name][2] - f[name][0]) * (f[name][3] - f[name][1]) // 5))
        bx0, by0, bx1, by1 = f["bottom"]
        vgrad(img, f["bottom"], (206, 192, 216), (190, 174, 202), 0.04)
        # suckers: a pale dot every other row, staggered across the two
        for i, y in enumerate(range(by0, by1, 2)):
            img.putpixel((bx0 + (i % 2) * (bx1 - bx0 - 1), y), (244, 234, 248, 255))
        if part[2] == 1:
            rect(img, f["front"], (60, 44, 74, 255))
    clear_hat(img)
    save(img, "entity/xenoform_murk.png")


def gnasher():
    """(0.18.111, Scan D) Gnasher, the shark: grey-blue on top, sharp white underneath, gill slits, flat black eyes, a
    jaw of white teeth, a tall fin and tail. (0.19.82) A shark, not a man: the man's parts keep the old paint (the
    first-person hand); the animal's boxes are in rows 64-115: chest 8x8x11 (0,64), rear 5x5x8 (38,64), small dorsal
    1x3x3 (38,77), lower tail lobe 1x4x3 (46,77), belly 7x7x8 (0,84), skull 7x5x9 (30,84), jaw 6x2x7 (0,99), dorsal
    fin 1x8x4 (26,99), upper tail lobe 1x8x4 (36,99), pectoral fin 6x1x4 (0,111)."""
    img = new_skin()
    top = (92, 116, 136)
    topa = top + (255,)
    white = (226, 230, 232, 255)
    dark = (50, 64, 78, 255)
    finc = (82, 104, 124, 255)
    mouth = (104, 26, 38, 255)
    tooth = (250, 250, 246, 255)
    parts(img, top, 0.1)
    for part in BASE_PARTS:
        fx0, fy0, fx1, fy1 = faces(*part)["front"]
        rect(img, (fx0 + 1, fy0 + (fy1 - fy0) // 3, fx1 - 1, fy1), (226, 230, 232, 255))
    rect(img, (8, 8, 16, 12), shade(top + (255,), 1.0))
    rect(img, (9, 10, 10, 11), (8, 8, 10, 255))
    rect(img, (14, 10, 15, 11), (8, 8, 10, 255))
    rect(img, (8, 14, 16, 15), (40, 20, 24, 255))
    for x in range(8, 16, 2):
        img.putpixel((x, 13), (250, 250, 250, 255))
        img.putpixel((x + 1, 15), (250, 250, 250, 255))
    for x in (16, 17, 18):
        rect(img, (x * 1, 22, x + 1, 26), (50, 64, 78, 255))
    claws(img, (60, 76, 90, 255))
    chest_insignia(img, rim=(50, 64, 78, 255))

    def shark(part, ytop, line=1.0):
        """Grey-blue above the waterline (y `line`, in the chest's pivot space; `ytop` is the box's top there), white
        below it with a jagged edge, a white underside, a darker back."""
        f = faces(*part)
        noise_fill(img, shade(topa, LIGHT["top"]), 0.1, f["top"])
        noise_fill(img, shade(white, 0.88), 0.05, f["bottom"])
        for name in ("front", "back", "left", "right"):
            x0, y0, x1, y1 = f[name]
            for y in range(y0, y1):
                for x in range(x0, x1):
                    wl = line + (0.0, 0.5, -0.5)[(x * 5) % 3]
                    if ytop + (y - y0) + 0.5 < wl:
                        c = shade(topa, LIGHT[name] * (0.94 + rng.random() * 0.12))
                    else:
                        c = shade(white, (0.78 + LIGHT[name] * 0.22) * (0.97 + rng.random() * 0.06))
                    img.putpixel((x, y), c)
        # a few darker flecks on the back
        x0, y0, x1, y1 = f["top"]
        speckle(img, (x0, y0, x1, y1), shade(topa, 0.8), (x1 - x0) * (y1 - y0) // 10)

    def fin(part, root, tip):
        """A fin's big faces: `root` at the bottom fading to `tip` at the top, its trailing edge darker."""
        f = faces(*part)
        for name in ("top", "bottom", "front", "back"):
            noise_fill(img, shade(root, LIGHT[name]), 0.06, f[name])
        for name in ("right", "left"):
            x0, y0, x1, y1 = f[name]
            vgrad(img, (x0, y0, x1, y1), tip, root, 0.07)
            # the trailing edge: the back of the right face is its left column, of the left face its right column
            tx = x0 if name == "right" else x1 - 1
            rect(img, (tx, y0, tx + 1, y1), shade(tip, 0.8))

    # the chest (the waterline at y +1 of the pivot space), its gill slits just behind the head, the insignia on the middle
    # of its back (its plate centred at z 0: the top face's row 69), the dorsal fin standing just behind it
    fore = (0, 64, 8, 8, 11)
    shark(fore, -4)
    rx0, ry0, rx1, ry1 = faces(*fore)["right"]
    lx0, ly0, lx1, ly1 = faces(*fore)["left"]
    for k in range(4):
        for (sx, sy0, sy1) in ((rx1 - 3 - 2 * k, ry0 + 1, ry0 + 5 - (k // 3)), (lx0 + 2 + 2 * k, ly0 + 1, ly0 + 5 - (k // 3))):
            rect(img, (sx, sy0, sx + 1, sy1), dark)
    tx0, ty0, tx1, ty1 = faces(*fore)["top"]
    insignia(img, (tx0 + tx1) // 2, ty0 + 3, small=True, rim=dark)

    # the belly and rear: the same grey over white, darker towards the tail
    shark((0, 84, 7, 7, 8), -3)
    shark((38, 64, 5, 5, 8), -1)
    for part in ((38, 64, 5, 5, 8),):
        for name in ("top", "left", "right"):
            x0, y0, x1, y1 = faces(*part)[name]
            for y in range(y0, y1):
                for x in range(x0, x1):
                    img.putpixel((x, y), shade(img.getpixel((x, y)), 0.94))

    # the skull: a blunt snout, the eyes flat black on its sides well forward, nostrils on the nose, a row of teeth along its lower edge
    skull = (30, 84, 7, 5, 9)
    shark(skull, -3)
    sf = faces(*skull)
    sx0, sy0, sx1, sy1 = sf["front"]
    rect(img, (sx0, sy0, sx1, sy0 + 1), shade(topa, 1.1))
    img.putpixel((sx0 + 2, sy0 + 1), dark)
    img.putpixel((sx1 - 3, sy0 + 1), dark)
    ex0, ey0, ex1, ey1 = sf["right"]
    rect(img, (ex1 - 3, ey0 + 1, ex1 - 1, ey0 + 3), (8, 8, 10, 255))
    ex0, ey0, ex1, ey1 = sf["left"]
    rect(img, (ex0 + 1, ey0 + 1, ex0 + 3, ey0 + 3), (8, 8, 10, 255))
    # the mouth line and the upper teeth: the lowest row of the front and sides, white points over dark
    for name in ("front", "right", "left"):
        x0, y0, x1, y1 = sf[name]
        for x in range(x0, x1):
            img.putpixel((x, y1 - 1), tooth if x % 2 == 0 else (40, 20, 24, 255))
    # the roof of the mouth (under the skull): deep red, a ring of teeth
    bx0, by0, bx1, by1 = sf["bottom"]
    rect(img, (bx0, by0, bx1, by1), mouth)
    for x in range(bx0, bx1):
        for y in (by0, by1 - 1):
            img.putpixel((x, y), tooth if x % 2 == 0 else (200, 196, 190, 255))
    for y in range(by0, by1):
        for x in (bx0, bx1 - 1):
            img.putpixel((x, y), tooth if y % 2 == 0 else (200, 196, 190, 255))
    noise_fill(img, (130, 40, 54), 0.2, (bx0 + 1, by0 + 1, bx1 - 1, by1 - 1))

    # the jaw: white, its top row a bite of teeth, its top face the floor of the mouth
    jaw = (0, 99, 6, 2, 7)
    jf = faces(*jaw)
    for name in ("front", "back", "left", "right", "bottom"):
        noise_fill(img, shade(white, 0.8 + LIGHT[name] * 0.2), 0.05, jf[name])
    for name in ("front", "left", "right"):
        x0, y0, x1, y1 = jf[name]
        for x in range(x0, x1):
            img.putpixel((x, y0), tooth if x % 2 == 0 else (214, 210, 204, 255))
    jx0, jy0, jx1, jy1 = jf["top"]
    rect(img, (jx0, jy0, jx1, jy1), (120, 34, 48, 255))
    noise_fill(img, (130, 40, 54), 0.2, (jx0 + 1, jy0 + 1, jx1 - 1, jy1 - 1))
    for x in range(jx0, jx1):
        for y in (jy0, jy1 - 1):
            img.putpixel((x, y), tooth)
    for y in range(jy0, jy1):
        for x in (jx0, jx1 - 1):
            img.putpixel((x, y), tooth if y % 2 == 0 else (214, 210, 204, 255))

    # the fins: the tall dorsal and the small one, the two tail lobes, the pectoral paddle
    fin((26, 99, 1, 8, 4), finc, (58, 74, 90, 255))
    fin((38, 77, 1, 3, 3), finc, (58, 74, 90, 255))
    fin((36, 99, 1, 8, 4), (86, 108, 128, 255), (56, 72, 88, 255))
    fin((46, 77, 1, 4, 3), (96, 118, 138, 255), (70, 88, 104, 255))
    pec = (0, 111, 6, 1, 4)
    pf = faces(*pec)
    noise_fill(img, shade(finc, LIGHT["top"]), 0.06, pf["top"])
    noise_fill(img, shade(white, 0.85), 0.05, pf["bottom"])
    for name in ("front", "back", "left", "right"):
        noise_fill(img, shade(finc, LIGHT[name]), 0.06, pf[name])
    for name in ("top", "bottom"):
        x0, y0, x1, y1 = pf[name]
        rect(img, (x0, y0, x0 + 1, y1), (58, 74, 90, 255))
        rect(img, (x0, y1 - 1, x1, y1), shade(img.getpixel((x0 + 2, y0)), 0.85))
    clear_hat(img)
    save(img, "entity/xenoform_gnasher.png")


def lumen():
    """(0.18.111, Scan D) Lumen, the light: pale sky blue, lighter towards the edges, a bright white-gold core in its
    chest, soft white eyes, thin pale fins. (0.19.75) An orb of light: a white-gold core (a 7x7x8 head box with two
    crossed 8x7x7 / 7x8x7 boxes to round its corners) inside a dithered pale-blue halo shell whose front is left clear,
    and eight tiny motes. The base humanoid parts are still painted (the first-person hand)."""
    img = new_skin()
    blue = (154, 220, 244)
    parts(img, blue, 0.06, edge=1.12)
    claws(img, (210, 240, 252, 255))
    chest_insignia(img, rim=(70, 140, 180, 255))
    glow = {"top": 1.0, "bottom": 0.93, "right": 0.97, "left": 0.97, "front": 1.0, "back": 0.96}
    gold = (250, 236, 186, 255)
    # the core: the head box (0,0) 7x7x8 (overpaints the old head), crossed boxes 8x7x7 at (0,94) and 7x8x7 at (30,94)
    core = (0, 0, 7, 7, 8)
    for part, base in ((core, (255, 252, 228)), ((0, 94, 8, 7, 7), (252, 246, 210)), ((30, 94, 7, 8, 7), (252, 246, 210))):
        box(img, part, base, 0.03, light=glow)
        for b in faces(*part).values():
            outline(img, b, gold)
    fx0, fy0, fx1, fy1 = faces(*core)["front"]
    rect(img, (fx0 + 1, fy0 + 1, fx1 - 1, fy1 - 1), (255, 255, 250, 255))
    # the insignia on the front of the core: its 5x7 rim fills the 7x7 face's height
    insignia(img, (fx0 + fx1) // 2, fy0 + 1, small=True, rim=(70, 140, 180, 255))
    # the motes: 1x1x1 at (0,64) bright and (8,64) pale
    box(img, (0, 64, 1, 1, 1), (255, 255, 240), 0.02, light=glow)
    box(img, (8, 64, 1, 1, 1), (200, 236, 250), 0.02, light=glow)
    # the halo shell: 11x11x11 at (0,70), a one-pixel pale rim and a dither inside, the front face clear so the
    # core and its insignia show through
    shell = (0, 70, 11, 11, 11)
    for name, (x0, y0, x1, y1) in faces(*shell).items():
        rect(img, (x0, y0, x1, y1), CLEAR)
        if name == "front":
            continue
        for y in range(y0, y1):
            for x in range(x0, x1):
                if x in (x0, x1 - 1) or y in (y0, y1 - 1):
                    img.putpixel((x, y), (214, 240, 252, 255))
                elif (x + y) % 2 == 0:
                    img.putpixel((x, y), (186, 230, 250, 255))
    clear_hat(img)
    save(img, "entity/xenoform_lumen.png")


def keen():
    """(0.18.111, Scan D; 0.19.75 a real bat) Keen, the sonar bat: dark brown-grey fur, a paler belly (it hangs belly to
    the front), the insignia on its back, tiny red eyes, a pink pug snout over a pale jaw with two white fangs, huge ears
    with pink insides, hind legs, and near-black leathery wings: bones in a dark fur brown, veined membrane between
    them, a dark thumb claw at each wrist. The man's parts (hidden in the model) keep the old look for the first-person arm."""
    img = new_skin()
    fur_c = (74, 60, 64)
    dark = (50, 40, 44, 255)
    ink = (30, 22, 28, 255)
    parts(img, fur_c, 0.16)
    fur(img, BASE_PARTS, fur_c, dark, 5)
    fx0, fy0, fx1, fy1 = front(BODY)
    vgrad(img, (fx0 + 2, fy0, fx1 - 2, fy1 - 3), (128, 108, 104), (100, 84, 84), 0.08)
    rect(img, (10, 11, 11, 12), (220, 40, 40, 255))
    rect(img, (13, 11, 14, 12), (220, 40, 40, 255))
    rect(img, (11, 13, 13, 15), (200, 140, 140, 255))
    claws(img, (30, 24, 26, 255))
    chest_insignia(img, rim=(40, 30, 32, 255))
    # (0.19.75) the bat, every box in rows 64-127: torso 6x7x5 at (0,64), head 5x3x4 at (22,64), nose 3x2x1 at (40,64),
    # jaw 4x1x3 at (48,64), ear 3x6x1 at (0,76), fang 1x2x1 at (8,76), hind leg 2x5x2 at (12,76), foot 2x1x3 at (20,76),
    # humerus 6x2x2 at (30,76), forearm 11x1x1 at (0,84), fingers 11x1x1 at (24,84), 10x1x1 at (0,86) and 8x1x1 at (22,86),
    # wrist knob 2x2x2 at (40,86), thumb 1x3x1 at (48,84), tail membrane 7x5x1 at (0,90), wing membrane 16x9x1 at
    # (16,90), finger membranes 10x6x1 at (0,100) and 9x6x1 at (22,100)
    torso = (0, 64, 6, 7, 5)
    box(img, torso, fur_c, 0.16)
    fur(img, (torso,), fur_c, dark, 4)
    tf = faces(*torso)
    # the belly ("back" face, +z) pale and warm, the back ("front" face, -z) a darker saddle with the insignia
    vgrad(img, tf["back"], (140, 118, 112), (108, 90, 90), 0.08)
    bx0, by0, bx1, by1 = tf["front"]
    vgrad(img, tf["front"], (66, 54, 58), (58, 46, 50), 0.1)
    insignia(img, (bx0 + bx1) // 2, by0 + 1, small=True, rim=(30, 22, 26, 255))
    # the neck end (the chest's ruff) paler
    noise_fill(img, (150, 126, 118), 0.14, tf["bottom"])
    head = (22, 64, 5, 3, 4)
    box(img, head, (84, 70, 74), 0.14)
    fur(img, (head,), (84, 70, 74), dark, 4)
    hx0, hy0, hx1, hy1 = faces(*head)["front"]
    # tiny red eyes high on the face either side of the snout, paler cheeks under them
    img.putpixel((hx0 + 1, hy0), (230, 40, 40, 255))
    img.putpixel((hx1 - 2, hy0), (230, 40, 40, 255))
    rect(img, (hx0, hy0 + 1, hx0 + 1, hy1), (118, 96, 98, 255))
    rect(img, (hx1 - 1, hy0 + 1, hx1, hy1), (118, 96, 98, 255))
    nose = (40, 64, 3, 2, 1)
    box(img, nose, (196, 132, 138), 0.1)
    nx0, ny0, nx1, ny1 = faces(*nose)["front"]
    img.putpixel((nx0, ny0 + 1), (60, 30, 36, 255))
    img.putpixel((nx1 - 1, ny0 + 1), (60, 30, 36, 255))
    jaw = (48, 64, 4, 1, 3)
    box(img, jaw, (150, 112, 114), 0.1)
    jx0, jy0, jx1, jy1 = faces(*jaw)["front"]
    rect(img, (jx0, jy0, jx1, jy1), (112, 54, 64, 255))
    box(img, (8, 76, 1, 2, 1), (238, 234, 222), 0.05)
    ear = (0, 76, 3, 6, 1)
    box(img, ear, (70, 56, 60), 0.1)
    ex0, ey0, ex1, ey1 = faces(*ear)["front"]
    rect(img, (ex0 + 1, ey0 + 1, ex1 - 1, ey1), (196, 136, 138, 255))
    rect(img, (ex0, ey0, ex1, ey0 + 1), (46, 36, 40, 255))
    box(img, (12, 76, 2, 5, 2), fur_c, 0.14)
    foot = (20, 76, 2, 1, 3)
    box(img, foot, (62, 50, 54), 0.12)
    rect(img, faces(*foot)["front"], (28, 22, 24, 255))
    # the wing's bones
    box(img, (30, 76, 6, 2, 2), (70, 56, 60), 0.12)
    fur(img, ((30, 76, 6, 2, 2),), (70, 56, 60), dark, 3)
    box(img, (0, 84, 11, 1, 1), (62, 48, 52), 0.1)
    for bone in ((24, 84, 11, 1, 1), (0, 86, 10, 1, 1), (22, 86, 8, 1, 1)):
        box(img, bone, (46, 36, 40), 0.1)
    box(img, (40, 86, 2, 2, 2), (88, 72, 72), 0.12)
    box(img, (48, 84, 1, 3, 1), (28, 22, 24), 0.08)

    def membrane(part, base, step):
        # leathery membrane: a dark grain, veins running out along its length, a darker edge on the leading (lower) side
        box(img, part, base, 0.12)
        for name, (x0, y0, x1, y1) in faces(*part).items():
            if name in ("front", "back"):
                for x in range(x0 + 1, x1, step):
                    rect(img, (x, y0, x + 1, y1), ink)
                rect(img, (x0, y1 - 1, x1, y1), (40, 30, 36, 255))
                # fur creeping out from the body end
                rect(img, (x0, y0, x0 + 2, y1), (74, 60, 64, 255))
                speckle(img, (x0, y0, x1, y1), (86, 66, 72, 255), (x1 - x0) * (y1 - y0) // 14 + 1)

    membrane((0, 90, 7, 5, 1), (54, 42, 50), 2)
    membrane((16, 90, 16, 9, 1), (58, 44, 52), 3)
    membrane((0, 100, 10, 6, 1), (52, 40, 48), 3)
    membrane((22, 100, 9, 6, 1), (52, 40, 48), 3)
    clear_hat(img)
    save(img, "entity/xenoform_keen.png")


def hive():
    """(0.18.113, Scan E; 0.19.66 a flying insect) Hive, the swarm: a fuzzy golden thorax with the insignia on its
    back, a banded amber and black abdomen ending in a dark stinger, a black fuzzy head with big faceted compound eyes,
    black antennae, two pairs of pale veined wings and six thin black legs. The man's parts (hidden in the model) keep
    the old amber and black look for the first-person arm."""
    img = new_skin()
    amber = (224, 164, 40)
    black = (36, 30, 24)
    ink = black + (255,)
    parts(img, amber, 0.12)
    for part in (BODY,) + LIMBS:
        for name, (x0, y0, x1, y1) in faces(*part).items():
            if name in ("top", "bottom"):
                continue
            for y in range(y0 + 2, y1, 4):
                rect(img, (x0, y, x1, min(y + 2, y1)), ink)
    box(img, HEAD, black, 0.12)
    fur(img, (HEAD,), black, (60, 50, 40, 255), 4)
    claws(img, (20, 16, 12, 255))
    chest_insignia(img, rim=(60, 40, 10, 255))
    # the flying body: thorax 6x6x7 at (0,64), abdomen 6x6x8 at (28,64), head 5x5x4 at (0,80), compound eye 2x4x4 at
    # (20,80), antenna 1x1x5 at (34,80), proboscis 2x1x2 at (48,80), stinger 1x1x3 at (0,92), forewing 9x1x4 at
    # (0,100), hindwing 6x1x3 at (28,100), leg 1x5x1 at (0,108)
    thorax = (0, 64, 6, 6, 7)
    gold = (206, 146, 36)
    box(img, thorax, gold, 0.16)
    fur(img, (thorax,), gold, (120, 80, 20, 255), 3)
    for name, b in faces(*thorax).items():
        speckle(img, b, (244, 206, 96, 255), (b[2] - b[0]) * (b[3] - b[1]) // 6 + 1)
    # a dark collar band behind the head on the front face
    fx0, fy0, fx1, fy1 = faces(*thorax)["front"]
    rect(img, (fx0, fy0, fx1, fy0 + 1), (90, 60, 16, 255))
    # the insignia on its back (the top face), where the band's plate lights
    tx0, ty0, tx1, ty1 = faces(*thorax)["top"]
    insignia(img, (tx0 + tx1) // 2, ty0 + 1, small=True, rim=(60, 40, 10, 255))
    # the abdomen: amber with black bands across it (k counts from its front), a black tip
    abd = (28, 64, 6, 6, 8)
    box(img, abd, amber, 0.12)
    bands = (2, 3, 6)
    for name, (x0, y0, x1, y1) in faces(*abd).items():
        if name == "back":
            noise_fill(img, black, 0.14, (x0, y0, x1, y1))
            continue
        if name == "front":
            continue
        for k in bands:
            if name == "top":
                rect(img, (x0, y1 - 1 - k, x1, y1 - k), shade(black, 1.1))
            elif name == "bottom":
                rect(img, (x0, y0 + k, x1, y0 + k + 1), shade(black, 0.9))
            elif name == "right":
                rect(img, (x1 - 1 - k, y0, x1 - k, y1), ink)
            else:
                rect(img, (x0 + k, y0, x0 + k + 1, y1), ink)
        speckle(img, (x0, y0, x1, y1), (250, 214, 110, 255), (x1 - x0) * (y1 - y0) // 10 + 1)
    # the head: black and fuzzy, a pale face patch and a dark mouth on the front
    head = (0, 80, 5, 5, 4)
    box(img, head, black, 0.12)
    fur(img, (head,), black, (64, 54, 42, 255), 3)
    hx0, hy0, hx1, hy1 = faces(*head)["front"]
    rect(img, (hx0 + 1, hy0 + 1, hx1 - 1, hy0 + 3), (190, 140, 50, 255))
    rect(img, (hx0 + 2, hy1 - 2, hx1 - 2, hy1 - 1), (14, 10, 8, 255))
    # the compound eyes: dark red-brown facets in a checker with a bright glint
    eye = (20, 80, 2, 4, 4)
    box(img, eye, (74, 38, 26), 0.1)
    for name, (x0, y0, x1, y1) in faces(*eye).items():
        for y in range(y0, y1):
            for x in range(x0, x1):
                if (x + y) % 2 == 0:
                    img.putpixel((x, y), (112, 62, 40, 255))
    rx0, ry0, rx1, ry1 = faces(*eye)["right"]
    img.putpixel((rx0 + 1, ry0 + 1), (230, 190, 150, 255))
    lx0, ly0, lx1, ly1 = faces(*eye)["left"]
    img.putpixel((lx0 + 2, ly0 + 1), (230, 190, 150, 255))
    # antennae, proboscis, stinger, legs
    box(img, (34, 80, 1, 1, 5), black, 0.1)
    ax0, ay0, ax1, ay1 = faces(34, 80, 1, 1, 5)["front"]
    rect(img, (ax0, ay0, ax1, ay1), (200, 150, 60, 255))
    box(img, (48, 80, 2, 1, 2), (150, 100, 40), 0.1)
    sting = (0, 92, 1, 1, 3)
    box(img, sting, (46, 30, 24), 0.1)
    sx0, sy0, sx1, sy1 = faces(*sting)["back"]
    rect(img, (sx0, sy0, sx1, sy1), (150, 120, 90, 255))
    leg = (0, 108, 1, 5, 1)
    box(img, leg, black, 0.1)
    for name, (x0, y0, x1, y1) in faces(*leg).items():
        if name in ("front", "back", "left", "right"):
            rect(img, (x0, y0 + 2, x1, y0 + 3), (150, 110, 40, 255))
    # wings: pale blue-white with darker veins along the length and a dark leading edge
    for wing in ((0, 100, 9, 1, 4), (28, 100, 6, 1, 3)):
        box(img, wing, (214, 228, 236), 0.04)
        for name, (x0, y0, x1, y1) in faces(*wing).items():
            if name in ("top", "bottom"):
                for y in range(y0 + 1, y1, 2):
                    rect(img, (x0, y, x1, y + 1), (170, 190, 204, 255))
                rect(img, (x0, y1 - 1, x1, y1), (120, 138, 150, 255))
            else:
                rect(img, (x0, y0, x1, y1), (150, 170, 184, 255))
    clear_hat(img)
    save(img, "entity/xenoform_hive.png")


def skitter():
    """(0.18.113, Scan E) Skitter, the eight-legged: black-brown and hairy, a paler hourglass on the abdomen, a cluster
    of red eyes, banded legs."""
    img = new_skin()
    hide = (58, 42, 34)
    parts(img, hide, 0.14)
    fur(img, BASE_PARTS, hide, (34, 24, 20, 255), 4)
    for part in LIMBS:
        for name, (x0, y0, x1, y1) in faces(*part).items():
            if name in ("front", "back", "left", "right"):
                rect(img, (x0, y0 + 5, x1, y0 + 6), (96, 70, 52, 255))
    for x, y in ((9, 10), (14, 10), (10, 11), (13, 11), (9, 12), (14, 12), (11, 12), (12, 12)):
        img.putpixel((x, y), (220, 30, 30, 255))
    rect(img, (10, 14, 14, 15), (20, 12, 10, 255))
    claws(img, (20, 14, 12, 255))
    chest_insignia(img, rim=(30, 20, 16, 255))
    # upright extras (as before 0.19.34): back leg 8x1x1 at (0,64); small abdomen 7x6x7 at (20,64)
    leg = (0, 64, 8, 1, 1)
    box(img, leg, hide, 0.12)
    for name, (x0, y0, x1, y1) in faces(*leg).items():
        for x in range(x0 + 2, x1, 3):
            rect(img, (x, y0, x + 1, y1), (96, 70, 52, 255))
    small = (20, 64, 7, 6, 7)
    box(img, small, (48, 36, 30), 0.14)
    for name, b in faces(*small).items():
        speckle(img, b, (28, 20, 16, 255), 8)
    bx0, by0, bx1, by1 = faces(*small)["top"]
    cx = (bx0 + bx1) // 2
    for i, w in enumerate((3, 1, 3)):
        rect(img, (cx - w // 2, by0 + 2 + i, cx - w // 2 + w, by0 + 3 + i), (190, 60, 40, 255))
    # (0.19.62) the prone spider: abdomen 10x8x12 at (0,78), fang 1x2x1 at (44,78), head 8x8x8 at (0,98), thorax
    # 6x6x6 at (32,98), legs 16x2x2 at (0,114) (right) and (0,118) (left)
    thorax = (32, 98, 6, 6, 6)
    box(img, thorax, hide, 0.14)
    head = (0, 98, 8, 8, 8)
    box(img, head, hide, 0.14)
    for name, b in faces(*head).items():
        speckle(img, b, (34, 24, 20, 255), 6)
    fx0, fy0, fx1, fy1 = faces(*head)["front"]
    # two big red eyes, a row of small ones above, a pair below
    rect(img, (fx0 + 2, fy0 + 3, fx0 + 3, fy0 + 5), (230, 30, 30, 255))
    rect(img, (fx0 + 5, fy0 + 3, fx0 + 6, fy0 + 5), (230, 30, 30, 255))
    for x in (fx0 + 1, fx0 + 3, fx0 + 4, fx0 + 6):
        img.putpixel((x, fy0 + 1), (200, 24, 24, 255))
    img.putpixel((fx0 + 1, fy0 + 5), (180, 20, 20, 255))
    img.putpixel((fx0 + 6, fy0 + 5), (180, 20, 20, 255))
    rect(img, (fx0 + 2, fy0 + 7, fx0 + 6, fy0 + 8), (20, 12, 10, 255))
    box(img, (44, 78, 1, 2, 1), (40, 26, 22), 0.1)
    for leg in ((0, 114, 16, 2, 2), (0, 118, 16, 2, 2)):
        box(img, leg, hide, 0.12)
        for name, (x0, y0, x1, y1) in faces(*leg).items():
            if name in ("top", "bottom"):
                continue
            # banded, the joints paler
            for x in range(x0 + 3, x1, 5):
                rect(img, (x, y0, x + 1, y1), (96, 70, 52, 255))
    belly = (0, 78, 10, 8, 12)
    box(img, belly, (48, 36, 30), 0.14)
    for name, b in faces(*belly).items():
        speckle(img, b, (28, 20, 16, 255), 10)
    # the insignia on the abdomen's back (where the band's plate lights), a red hourglass underneath
    tx0, ty0, tx1, ty1 = faces(*belly)["top"]
    insignia(img, (tx0 + tx1) // 2, ty0 + 3, rim=(30, 20, 16, 255))
    bx0, by0, bx1, by1 = faces(*belly)["bottom"]
    cx = (bx0 + bx1) // 2
    for i, w in enumerate((3, 1, 3)):
        rect(img, (cx - w // 2, by0 + 4 + i, cx - w // 2 + w, by0 + 5 + i), (190, 40, 30, 255))
    clear_hat(img)
    save(img, "entity/xenoform_skitter.png")


def shade_form():
    """(0.18.113, Scan E; 0.19.84 a crawling shadow that unfolds tall) Shade, the stealer: near-black with a faint
    violet sheen, and long narrow glowing violet eyes. The vanilla layout (rows 0-63) is still painted for the
    first-person arm; the real parts are, in rows 64-111: head 5x6x5 at (0,64), torso 5x11x3 at (0,76), the eight 2x10x2
    limb segments in a row at v=92 (right arm 0, forearm 8, left arm 16, forearm 24, right thigh 32, shin 40, left
    thigh 48, shin 56), hands 2x4x2 at (0,106) and (8,106), feet 2x1x4 at (16,106) and (28,106)."""
    img = new_skin()
    dark = (26, 20, 32)
    sheen = (44, 32, 58, 255)
    joint = (54, 40, 72, 255)
    violet = (210, 120, 255, 255)
    core = (250, 220, 255, 255)
    parts(img, dark, 0.08)
    for part in BASE_PARTS:
        for name, b in faces(*part).items():
            speckle(img, b, sheen, (b[2] - b[0]) * (b[3] - b[1]) // 9 + 1)
    rect(img, (8, 11, 11, 12), violet)
    rect(img, (13, 11, 16, 12), violet)
    img.putpixel((9, 11), core)
    img.putpixel((14, 11), core)
    claws(img, (10, 8, 14, 255))
    chest_insignia(img, rim=(20, 14, 30, 255))

    def seg(part, tip=None):
        """A thin limb segment: black, flecked, its ends a shade paler (the joints), the fingertips faintly violet."""
        box(img, part, dark, 0.08)
        for name, b in faces(*part).items():
            speckle(img, b, sheen, (b[2] - b[0]) * (b[3] - b[1]) // 9 + 1)
            if name in ("front", "back", "left", "right"):
                rect(img, (b[0], b[1], b[2], b[1] + 1), joint)
                if tip:
                    rect(img, (b[0], b[3] - 1, b[2], b[3]), tip)
                else:
                    rect(img, (b[0], b[3] - 1, b[2], b[3]), joint)

    # head 5x6x5 at (0,64): the long narrow violet eyes slant down toward the nose
    head = (0, 64, 5, 6, 5)
    box(img, head, dark, 0.08)
    for name, b in faces(*head).items():
        speckle(img, b, sheen, (b[2] - b[0]) * (b[3] - b[1]) // 9 + 1)
    for x, y in ((5, 71), (6, 71), (6, 72), (9, 71), (8, 71), (8, 72)):
        img.putpixel((x, y), violet)
    img.putpixel((6, 71), core)
    img.putpixel((8, 71), core)
    # torso 5x11x3 at (0,76): faint ribs on the sides, the insignia on the front (standing) and the back (crawling)
    torso = (0, 76, 5, 11, 3)
    box(img, torso, dark, 0.08)
    fr = faces(*torso)
    for name, b in fr.items():
        speckle(img, b, sheen, (b[2] - b[0]) * (b[3] - b[1]) // 9 + 1)
    for name in ("left", "right"):
        x0, y0, x1, y1 = fr[name]
        for y in range(y0 + 1, y1, 2):
            rect(img, (x0, y, x1, y + 1), joint)
    x0, y0, x1, y1 = fr["front"]
    insignia(img, x0 + 2, y0 + 3, small=True, rim=(20, 14, 30, 255))
    x0, y0, x1, y1 = fr["back"]
    insignia(img, x0 + 2, y0 + 3, small=True, rim=(20, 14, 30, 255))
    for y in list(range(y0, y0 + 2)) + list(range(y1 - 2, y1)):
        img.putpixel((x0 + 2, y), joint)
    # the eight limb segments, then the hands (long fingers, faint violet tips) and the flat feet
    for u in range(0, 64, 8):
        seg((u, 92, 2, 10, 2))
    for hand in ((0, 106, 2, 4, 2), (8, 106, 2, 4, 2)):
        seg(hand, tip=(96, 60, 130, 255))
    for foot in ((16, 106, 2, 1, 4), (28, 106, 2, 1, 4)):
        box(img, foot, dark, 0.08)
        for name, b in faces(*foot).items():
            speckle(img, b, sheen, 2)
    clear_hat(img)
    save(img, "entity/xenoform_shade.png")


def mirage():
    """(0.18.113, Scan E) Mirage, the illusionist: a deep blue robe trimmed in gold, a pale grey face with bright blue
    eyes in the hood's shadow."""
    img = new_skin()
    robe = (46, 74, 160)
    gold = (220, 180, 70, 255)
    parts(img, robe, 0.1)
    rect(img, (8, 8, 16, 16), (150, 150, 160, 255))
    rect(img, (8, 8, 16, 10), (30, 40, 80, 255))
    eyes(img, (120, 200, 255, 255), y=11)
    rect(img, (11, 14, 13, 15), (100, 100, 110, 255))
    fx0, fy0, fx1, fy1 = front(BODY)
    rect(img, (fx0 + 3, fy0, fx0 + 5, fy1), gold)
    for part in ARMS:
        for name, (x0, y0, x1, y1) in faces(*part).items():
            if name in ("front", "back", "left", "right"):
                rect(img, (x0, y1 - 3, x1, y1 - 2), gold)
        rect(img, front(part)[:1] + (front(part)[3] - 2,) + front(part)[2:3] + (front(part)[3],), (150, 150, 160, 255))
    chest_insignia(img, rim=(20, 30, 70, 255))
    # extras: hood 8x8x8 at (0,64); robe skirt 8x8x4 at (0,82)
    hood = (0, 64, 8, 8, 8)
    box(img, hood, (40, 64, 144), 0.1)
    hx0, hy0, hx1, hy1 = faces(*hood)["front"]
    rect(img, (hx0 + 1, hy0 + 2, hx1 - 1, hy1), CLEAR)
    for name, (x0, y0, x1, y1) in faces(*hood).items():
        if name != "front":
            rect(img, (x0, y1 - 1, x1, y1), gold)
    rect(img, (hx0, hy0 + 1, hx1, hy0 + 2), gold)
    skirt = (0, 82, 8, 8, 4)
    box(img, skirt, robe, 0.1)
    for name, (x0, y0, x1, y1) in faces(*skirt).items():
        if name in ("front", "back", "left", "right"):
            rect(img, (x0, y1 - 1, x1, y1), gold)
    sx0, sy0, sx1, sy1 = faces(*skirt)["front"]
    rect(img, (sx0 + 3, sy0, sx0 + 5, sy1), gold)
    clear_hat(img)
    save(img, "entity/xenoform_mirage.png")


def jelly_pane(img, b, base):
    """A see-through side of the jelly (the skin is cut out, not blended): clear but for a lighter frame, a few bright
    bubbles and a gooey floor, so the core shows through."""
    x0, y0, x1, y1 = b
    rect(img, b, CLEAR)
    speckle(img, (x0 + 1, y0 + 1, x1 - 1, y1 - 2), lerp(base, (255, 255, 255), 0.45), (x1 - x0) * (y1 - y0) // 20)
    noise_fill(img, shade(base, 0.9), 0.1, (x0, y1 - 2, x1, y1))
    outline(img, b, shade(base, 1.15))


def ooze_skin(name, base, core, dark, eye, magma=False):
    """(0.18.114, Scan F) Ooze: soft jelly lighter at the edges, a darker core in the chest, two dark eyes, a blob on top.
    (0.19.82) Rows 0-63 stay the humanoid skin (the first-person hand); the cube is in rows 64+. Ooze: the jelly shell
    10x8x10 at (0,64), its sides cut out so the core shows through, and the core 6x5x6 at (0,84) with the eyes.
    Magma Ooze: three crust slabs 10x2x10 at (0,64), (0,76), (0,88) with glowing seams, and the glowing core 8x8x8 at (0,100)."""
    img = new_skin()
    dk = dark + (255,)
    parts(img, base, 0.08, edge=1.12)
    fx0, fy0, fx1, fy1 = front(BODY)
    rect(img, (fx0 + 2, fy0 + 2, fx1 - 2, fy0 + 8), core + (255,))
    rect(img, (9, 10, 11, 12), eye)
    rect(img, (13, 10, 15, 12), eye)
    rect(img, (10, 14, 14, 15), dk)
    for part in BASE_PARTS:
        for nm, b in faces(*part).items():
            speckle(img, b, dk, (b[2] - b[0]) * (b[3] - b[1]) // 14 + 1)
    if magma:
        # glowing cracks across the dark crust
        for part in BASE_PARTS:
            for nm, b in faces(*part).items():
                if nm in ("front", "back", "left", "right"):
                    for y in range(b[1] + 2, b[3], 4):
                        rect(img, (b[0], y, b[2], y + 1), (240, 120, 30, 255))
    rim = (60, 20, 10, 255) if magma else shade(dk, 0.8)
    chest_insignia(img, rim=rim)
    if magma:
        hot = (240, 120, 30)
        slabs = [(0, 64 + 12 * i, 10, 2, 10) for i in range(3)]
        for slab in slabs:
            box(img, slab, base, 0.12, edge=0.8)
            fc = faces(*slab)
            veins(img, fc["top"], base, hot, 4, length=(2, 5))
            for nm in ("front", "back", "left", "right"):
                x0, y0, x1, y1 = fc[nm]
                # light leaking from the seams above and below the slab
                for x in range(x0, x1):
                    if (x + slab[1]) % 3:
                        img.putpixel((x, y1 - 1), lerp(base, hot, 0.8))
                    else:
                        img.putpixel((x, y0), lerp(base, hot, 0.5))
        lava = (250, 150, 40)
        glow = (0, 100, 8, 8, 8)
        box(img, glow, lava, 0.15, light={"top": 1.1, "bottom": 0.9, "right": 0.95, "left": 0.95, "front": 1.0, "back": 0.9})
        gf = faces(*glow)
        for nm, b in gf.items():
            speckle(img, b, (255, 214, 90, 255), (b[2] - b[0]) * (b[3] - b[1]) // 8)
        ex0, ey0, ex1, ey1 = gf["front"]
        rect(img, (ex0 + 1, ey0 + 2, ex0 + 3, ey0 + 4), dk)
        rect(img, (ex0 + 5, ey0 + 2, ex0 + 7, ey0 + 4), dk)
        rect(img, (ex0 + 3, ey0 + 5, ex0 + 5, ey0 + 6), dk)
        tx0, ty0, tx1, ty1 = faces(*slabs[2])["top"]
        insignia(img, (tx0 + tx1) // 2, ty0 + 1, rim=rim)
    else:
        shell = (0, 64, 10, 8, 10)
        box(img, shell, base, 0.08, edge=1.12)
        for nm, b in faces(*shell).items():
            if nm in ("front", "back", "left", "right"):
                jelly_pane(img, b, base)
        tx0, ty0, tx1, ty1 = faces(*shell)["top"]
        insignia(img, (tx0 + tx1) // 2, ty0 + 1, rim=rim)
        inner = (0, 84, 6, 5, 6)
        box(img, inner, core, 0.1)
        ex0, ey0, ex1, ey1 = faces(*inner)["front"]
        # eyes and mouth kept above the front pane's 2px gooey floor, so the whole face shows through the window
        rect(img, (ex0, ey0, ex0 + 2, ey0 + 2), eye)
        rect(img, (ex0 + 4, ey0, ex0 + 6, ey0 + 2), eye)
        rect(img, (ex0 + 2, ey0 + 3, ex0 + 4, ey0 + 4), dk)
    clear_hat(img)
    save(img, f"entity/xenoform_{name}.png")


def ooze():
    ooze_skin("ooze", (108, 192, 96), (60, 130, 56), (40, 90, 40), (20, 40, 20, 255))


def magma_ooze():
    ooze_skin("magma_ooze", (70, 30, 24), (250, 150, 40), (30, 12, 10), (255, 200, 60, 255), magma=True)


def fuse():
    """(0.18.114, Scan F) Fuse: mottled creeper greens, a paler chest, the dark hollow eyes and frown."""
    img = new_skin()
    green = (88, 160, 72)
    parts(img, green, 0.1)
    for part in BASE_PARTS:
        for nm, b in faces(*part).items():
            speckle(img, b, (60, 120, 52, 255), (b[2] - b[0]) * (b[3] - b[1]) // 5 + 1)
            speckle(img, b, (150, 210, 130, 255), (b[2] - b[0]) * (b[3] - b[1]) // 12 + 1)
    rect(img, (9, 10, 11, 12), (16, 20, 16, 255))
    rect(img, (13, 10, 15, 12), (16, 20, 16, 255))
    rect(img, (11, 12, 13, 15), (16, 20, 16, 255))
    rect(img, (10, 13, 11, 16), (16, 20, 16, 255))
    rect(img, (13, 13, 14, 16), (16, 20, 16, 255))
    claws(img, (40, 70, 36, 255))
    # (0.19.34) four stubby legs use the top 6 rows of the leg skin: dark feet at their bottom
    for part in LEGS:
        for nm, (x0, y0, x1, y1) in faces(part[0], part[1], 4, 6, 4).items():
            if nm in ("front", "back", "left", "right"):
                rect(img, (x0, y1 - 1, x1, y1), (40, 70, 36, 255))
    chest_insignia(img, rim=(30, 60, 26, 255))
    clear_hat(img)
    save(img, "entity/xenoform_fuse.png")


def thornwild():
    """(0.18.114, Scan F; 0.19.84 a treant) Thornwild: grey-brown bark in vertical grain, moss on the shoulders, amber eyes, pale thorns; the trunk's chest burl and base, root feet, leafy twigs."""
    img = new_skin()
    bark = (92, 72, 52)
    dark = (66, 50, 36, 255)
    moss = (84, 124, 54, 255)
    amber = (250, 170, 40, 255)
    parts(img, bark, 0.12)
    for part in BASE_PARTS:
        for nm, (x0, y0, x1, y1) in faces(*part).items():
            if nm in ("top", "bottom"):
                continue
            for x in range(x0, x1, 3):
                rect(img, (x, y0, x + 1, y1), dark)
    for part in ARMS:
        for nm, (x0, y0, x1, y1) in faces(*part).items():
            if nm == "top":
                noise_fill(img, moss, 0.2, (x0, y0, x1, y1))
            elif nm in ("front", "back", "left", "right"):
                rect(img, (x0, y0, x1, y0 + 2), moss)
    # head: moss on top and the cheeks' tops, amber eyes under the brow, a dark mouth with pale thorn teeth
    x0, y0, x1, y1 = faces(*HEAD)["top"]
    noise_fill(img, moss, 0.25, (x0, y0, x1, y1))
    for nm in ("right", "left", "back"):
        x0, y0, x1, y1 = faces(*HEAD)[nm]
        rect(img, (x0, y0, x1, y0 + 2), moss)
    rect(img, (9, 11, 11, 13), amber)
    rect(img, (13, 11, 15, 13), amber)
    img.putpixel((10, 11), (255, 230, 140, 255))
    img.putpixel((13, 11), (255, 230, 140, 255))
    rect(img, (9, 13, 11, 14), dark)
    rect(img, (13, 13, 15, 14), dark)
    rect(img, (9, 15, 15, 16), (40, 30, 22, 255))
    for x in (10, 12, 14):
        img.putpixel((x, 15), (220, 210, 170, 255))
    claws(img, (50, 38, 28, 255))
    # extras (see XenoFormModels.Thornwild): chest burl 10x9x6 at (0,64) is the body; the plate sits on its front (6,70)-(16,79)
    burl = (0, 64, 10, 9, 6)
    box(img, burl, bark, 0.14)
    for nm, (x0, y0, x1, y1) in faces(*burl).items():
        if nm == "bottom":
            continue
        if nm == "top":
            noise_fill(img, moss, 0.22, (x0, y0, x1, y1))
            continue
        for x in range(x0 + 1, x1, 3):
            rect(img, (x, y0, x + 1, y1), dark)
        rect(img, (x0, y0, x1, y0 + 1), moss)
    for kx, ky in ((7, 72), (14, 77), (2, 75), (20, 70), (25, 74)):
        outline(img, (kx, ky, kx + 3, ky + 3), dark)
    insignia(img, 11, 71, rim=(50, 38, 26, 255))
    # base 12x5x7 at (0,80): darker, rooty, mossy ledge on top
    base = (0, 80, 12, 5, 7)
    box(img, base, (74, 56, 40), 0.16)
    for nm, (x0, y0, x1, y1) in faces(*base).items():
        if nm == "top":
            noise_fill(img, moss, 0.22, (x0, y0, x1, y1))
        elif nm != "bottom":
            for x in range(x0, x1, 2):
                rect(img, (x, y0 + 1, x + 1, y1), (56, 42, 30, 255))
    # hump 8x6x3 at (32,64): bark with bracket fungus and moss
    hump = (32, 64, 8, 6, 3)
    box(img, hump, (80, 62, 44), 0.14)
    x0, y0, x1, y1 = faces(*hump)["top"]
    noise_fill(img, moss, 0.2, (x0, y0, x1, y1))
    x0, y0, x1, y1 = faces(*hump)["back"]
    speckle(img, (x0, y0, x1, y1), (214, 170, 110, 255), 6)
    # brow 9x2x2 at (32,73), nose 2x3x2 at (54,64): dark knotted bark
    box(img, (32, 73, 9, 2, 2), (60, 46, 34), 0.12)
    box(img, (54, 64, 2, 3, 2), (76, 58, 42), 0.1)
    # thorn 1x1x2 at (54,69), branch 2x7x2 at (54,72), twig 1x5x1 at (44,97)
    box(img, (54, 69, 1, 1, 2), (220, 210, 170), 0.05)
    box(img, (54, 72, 2, 7, 2), (84, 66, 46), 0.1)
    speckle(img, (54, 74, 62, 81), moss, 4)
    box(img, (44, 97, 1, 5, 1), (80, 62, 44), 0.1)
    # mossy pad 5x2x6 at (38,81)
    pad = (38, 81, 5, 2, 6)
    box(img, pad, moss[:3], 0.25)
    for nm, (x0, y0, x1, y1) in faces(*pad).items():
        if nm in ("front", "back", "left", "right"):
            rect(img, (x0, y1 - 1, x1, y1), (60, 100, 40, 255))
    speckle(img, (38, 81, 60, 89), (230, 230, 160, 255), 5)
    # heel 7x4x7 at (0,92), toe 2x3x4 at (28,92), rootlet 1x2x3 at (40,92): earthy roots, paler toward the tips
    box(img, (0, 92, 7, 4, 7), (60, 46, 34), 0.18)
    box(img, (28, 92, 2, 3, 4), (74, 56, 40), 0.14)
    box(img, (40, 92, 1, 2, 3), (104, 82, 58), 0.12)
    # leaf 4x4 at (28,99): green with a pale midrib and a few amber flecks
    box(img, (28, 99, 4, 1, 4), (70, 130, 50), 0.25)
    x0, y0, x1, y1 = faces(28, 99, 4, 1, 4)["top"]
    rect(img, (x0 + 1, y0, x0 + 2, y1), (130, 180, 90, 255))
    speckle(img, (x0, y0, x1, y1), (210, 160, 50, 255), 2)
    clear_hat(img)
    save(img, "entity/xenoform_thornwild.png")


def billow():
    """(0.18.114, Scan F) Billow: soft white cloud, grey underneath, a calm face of dark round eyes and a small smile."""
    img = new_skin()
    white = (236, 240, 246)
    parts(img, white, 0.05, light={"top": 1.05, "bottom": 0.72, "right": 0.92, "left": 0.92, "front": 1.0, "back": 0.86})
    rect(img, (9, 11, 11, 13), (60, 70, 90, 255))
    rect(img, (13, 11, 15, 13), (60, 70, 90, 255))
    rect(img, (11, 14, 13, 15), (150, 160, 180, 255))
    chest_insignia(img, rim=(150, 160, 180, 255))
    # (0.19.34) the cloud: mass 18x7x14 at (0,64), big puffs 10x8x10 at (0,86), small puffs 8x6x8 at (0,104)
    soft = {"top": 1.05, "bottom": 0.66, "right": 0.9, "left": 0.9, "front": 0.98, "back": 0.86}
    for part in ((0, 64, 18, 7, 14), (0, 86, 10, 8, 10), (0, 104, 8, 6, 8)):
        box(img, part, white, 0.05, light=soft)
        for nm, (x0, y0, x1, y1) in faces(*part).items():
            if nm in ("front", "back", "left", "right"):
                # greyer toward the bottom, like a real cloud's belly
                for y in range(y0 + (y1 - y0) // 2, y1):
                    t = (y - y0) / max(1, y1 - y0)
                    for x in range(x0, x1):
                        r, g, b_, a_ = img.getpixel((x, y))
                        k = 1.0 - (t - 0.5) * 0.35
                        img.putpixel((x, y), (int(r * k), int(g * k), int(b_ * k * 1.02) if b_ * k * 1.02 < 255 else 255, 255))
    mx0, my0, mx1, my1 = faces(0, 64, 18, 7, 14)["front"]
    insignia(img, (mx0 + mx1) // 2, my0 + 1, small=True, rim=(150, 160, 180, 255))
    clear_hat(img)
    save(img, "entity/xenoform_billow.png")


def towerback():
    """(0.18.119, Scan G) Towerback: grey stone in rough courses, cracks, moss on the shoulders and boulders, a heavy brow
    over pale glowing eyes, and a broken watchtower on its back with dark arrow slits."""
    img = new_skin()
    stone = (128, 132, 138)
    parts(img, stone, 0.14)
    moss = (86, 124, 58, 255)
    for part in BASE_PARTS:
        for nm, (x0, y0, x1, y1) in faces(*part).items():
            if nm in ("top", "bottom"):
                continue
            # rough courses of stone
            for y in range(y0 + 3, y1, 4):
                rect(img, (x0, y, x1, y + 1), (96, 100, 106, 255))
            speckle(img, (x0, y0, x1, y1), (80, 84, 90, 255), 3)
    veins(img, faces(*BODY)["front"], (70, 74, 80), (70, 74, 80), 3)
    for part in ARMS + (BODY,):
        for nm, (x0, y0, x1, y1) in faces(*part).items():
            if nm == "top":
                noise_fill(img, moss, 0.2, (x0, y0, x1, y1))
            elif nm in ("front", "back", "left", "right"):
                rect(img, (x0, y0, x1, y0 + 1), moss)
    # brow shadow, eyes
    rect(img, (8, 10, 16, 11), (70, 74, 80, 255))
    rect(img, (9, 11, 11, 12), (200, 240, 255, 255))
    rect(img, (13, 11, 15, 12), (200, 240, 255, 255))
    rect(img, (10, 14, 14, 15), (60, 62, 66, 255))
    claws(img, (90, 94, 100, 255))
    chest_insignia(img, rim=(70, 74, 80, 255))
    # extras: brow 10x2x3 at (0,64); boulder 5x4x5 at (28,64); tower 6x11x5 at (0,74); merlon 2x2x2 at (24,74)
    box(img, (0, 64, 10, 2, 3), (112, 116, 122), 0.1)
    boulder = (28, 64, 5, 4, 5)
    box(img, boulder, (118, 122, 126), 0.18)
    for nm, (x0, y0, x1, y1) in faces(*boulder).items():
        if nm == "top":
            noise_fill(img, moss, 0.25, (x0, y0, x1, y1))
        elif nm != "bottom":
            rect(img, (x0, y0, x1, y0 + 1), moss)
    tower = (0, 74, 6, 11, 5)
    box(img, tower, (150, 146, 136), 0.1)
    for nm, (x0, y0, x1, y1) in faces(*tower).items():
        if nm in ("top", "bottom"):
            continue
        for y in range(y0 + 2, y1, 3):
            rect(img, (x0, y, x1, y + 1), (118, 114, 106, 255))
        cx = (x0 + x1) // 2
        rect(img, (cx, y0 + 3, cx + 1, y0 + 6), (30, 30, 34, 255))
        noise_fill(img, moss, 0.2, (x0, y1 - 2, x1, y1))
    box(img, (24, 74, 2, 2, 2), (150, 146, 136), 0.1)
    clear_hat(img)
    save(img, "entity/xenoform_towerback.png")


# (0.18.93, patch 2) the Ultimates: each base skin reworked into an armoured hide, darker and plated, its plate seams
# glowing in the alien's colour; a second skin holds only the seams (and the base's brightest marks), drawn fullbright.
# Nadir has no Ultimate form (its C is a film).
ULTIMATE_COLORS = {
    "bulwark": (184, 196, 208), "pyre": (255, 138, 42), "blur": (76, 141, 255), "prism": (196, 139, 240),
    "wisp": (184, 232, 248), "gremlin": (154, 220, 90), "knell": (31, 184, 200), "wyrm": (192, 96, 224),
    "duplex": (224, 123, 74), "rivet": (159, 180, 192), "gorger": (176, 106, 84), "slipfin": (240, 140, 184),
    "polestar": (216, 74, 74), "lurk": (110, 82, 176), "gyre": (95, 163, 122), "cortex": (79, 184, 176),
    "locust": (141, 179, 58), "croaker": (110, 158, 60),
    "rime": (158, 216, 240), "marrow": (230, 224, 204), "swathe": (196, 170, 120), "blight": (120, 100, 150),
    "howl": (176, 160, 140), "prowl": (224, 150, 60), "tusk": (150, 100, 70), "talon": (214, 180, 60),
    "murk": (150, 120, 190), "gnasher": (110, 150, 180), "lumen": (200, 240, 255), "keen": (200, 60, 70),
    "hive": (240, 180, 50), "skitter": (200, 40, 40), "shade": (200, 110, 255), "mirage": (110, 170, 255),
    "ooze": (120, 230, 100), "magma_ooze": (255, 140, 40), "fuse": (140, 230, 110), "thornwild": (240, 180, 60), "billow": (150, 200, 255),
    "towerback": (200, 240, 255),
}
ULTIMATE_PLATE, ULTIMATE_WIDTH = 5, 8
# (0.20.9) the Ultimates with their own model and skin, painted by gen_xeno_ultimates.py (never recoloured from the base skin here)
OWN_ULTIMATES = ("bulwark", "pyre", "blur", "prism", "wisp", "gremlin", "knell",
                 "wyrm", "duplex", "rivet", "gorger", "slipfin", "polestar", "lurk")  # (0.21.37)
PATCH4 = ("gorger", "slipfin", "polestar", "lurk")
PATCH5 = ("gyre", "cortex", "locust", "croaker")
SCAN_B = ("rime", "marrow", "swathe", "blight")
SCAN_C = ("howl", "prowl", "tusk", "talon")
SCAN_D = ("murk", "gnasher", "lumen", "keen")
SCAN_E = ("hive", "skitter", "shade", "mirage")
SCAN_F = ("ooze", "magma_ooze", "fuse", "thornwild", "billow")
SCAN_G = ("towerback",)


def ultimate(form):
    base = Image.open(ROOT / "textures" / "entity" / f"xenoform_{form}.png").convert("RGBA")
    color = ULTIMATE_COLORS[form]
    metal = tuple(int(c * 0.28) + 18 for c in color)
    skin = Image.new("RGBA", base.size, CLEAR)
    glow = Image.new("RGBA", base.size, CLEAR)
    src, out, lit = base.load(), skin.load(), glow.load()
    seam = lerp(color, (255, 255, 255), 0.35)
    for y in range(base.size[1]):
        for x in range(base.size[0]):
            r, g, b, a = src[x, y]
            if a == 0:
                continue
            bright = max(r, g, b)
            # plates: the hide darkened toward the alien's metal, a little of the old marks showing through
            mixed = tuple(int(metal[i] * 0.5 + (r, g, b)[i] * 0.5) for i in range(3))
            # a bevel on each plate: lighter on its top-left rows, darker on its bottom-right
            # laid like bricks: a seam along every fourth row, and across each row every sixth pixel, staggered
            py = y % ULTIMATE_PLATE
            px = (x + (y // ULTIMATE_PLATE) % 2 * 4) % ULTIMATE_WIDTH
            if bright > 225:
                # eyes, insignia, the brightest marks: they burn through
                out[x, y] = (r, g, b, 255)
                lit[x, y] = lerp((r, g, b), color, 0.3)
                continue
            if py == 0 or px == 0:
                out[x, y] = seam
                if (x // ULTIMATE_WIDTH + y // ULTIMATE_PLATE) % 3 != 0:
                    # most seams glow; every third plate's edge lies dark, so it reads as armour, not a grid
                    lit[x, y] = seam
                else:
                    out[x, y] = shade(metal + (255,), 0.55)
                continue
            k = 1.15 if py == 1 else 0.82 if py == ULTIMATE_PLATE - 1 else 1.0
            out[x, y] = shade(mixed + (255,), k)
    save(skin, f"entity/xenoform_{form}_ultimate.png")
    save(glow, f"entity/xenoform_{form}_ultimate_glow.png")


def ultimate_spikes():
    """The Ultimates' spikes and crests: one plain bone-and-metal strip, tinted per alien when drawn."""
    img = Image.new("RGBA", (64, 32), CLEAR)
    vgrad(img, (0, 0, 64, 32), (255, 255, 255, 255), (150, 150, 160, 255))
    save(img, "entity/xenoform_ultimate_spikes.png")


FORMS = ("bulwark", "pyre", "blur", "prism", "wisp", "gremlin", "knell", "wyrm", "nadir", "duplex", "rivet", "gorger", "slipfin", "polestar", "lurk", "gyre", "cortex", "locust", "croaker", "dud", "rime", "marrow", "swathe", "blight", "howl", "prowl", "tusk", "talon", "murk", "gnasher", "lumen", "keen", "hive", "skitter", "shade", "mirage", "ooze", "magma_ooze", "fuse", "thornwild", "billow", "towerback")
# for the preview: extra parts drawn over the front view, (part, where on the 24x36 card)
EXTRAS_IN_FRONT = {
    "pyre": (((12, 78, 5, 5, 1), (10, 10)),),
    "blur": (((34, 68, 4, 2, 3), (10, 6)), ((34, 64, 9, 3, 1), (7, 3)), ((0, 76, 4, 3, 4), (8, 29)), ((16, 76, 4, 3, 4), (12, 29))),
    "wisp": (((0, 64, 10, 10, 10), (7, 0)), ((0, 86, 8, 7, 4), (8, 20))),
}


def duplex_card(skin, card):
    """Duplex's front view: the golem's parts stood on the card's feet line, rod and all."""
    def put(part, x, y):
        face = skin.crop(front(part))
        card.paste(face, (x, y), face)
    put(DUPLEX_KNOB, 10, 4)
    put(DUPLEX_ROD, 11, 8)
    put(DUPLEX_HEAD, 8, 12)
    put(DUPLEX_BODY, 8, 17)
    put(DUPLEX_BELT, 8, 21)
    put(DUPLEX_RARM, 5, 17)
    put(DUPLEX_LARM, 16, 17)
    put(DUPLEX_RLEG, 8, 23)
    put(DUPLEX_LLEG, 12, 23)
    put(DUPLEX_NOSE, 11, 15)


def gremlin_card(skin, card):
    """Gremlin's front view: its own small parts stood on the card's feet line, the eyes out past the head."""
    def put(part, x, y, flip=False):
        face = skin.crop(front(part))
        if flip:
            face = face.transpose(Image.FLIP_LEFT_RIGHT)
        card.paste(face, (x, y), face)
    put(GREMLIN_LEG, 9, 29)
    put(GREMLIN_LEG, 12, 29, True)
    put(GREMLIN_FOOT, 8, 35)
    put(GREMLIN_FOOT, 12, 35, True)
    put(GREMLIN_BODY, 9, 22)
    put(GREMLIN_ARM, 7, 23)
    put(GREMLIN_ARM, 14, 23, True)
    put(GREMLIN_HEAD, 8, 15)
    put(GREMLIN_EYE, 5, 15)
    put(GREMLIN_EYE, 15, 15, True)


def preview(out):
    """A contact sheet: each alien's front view assembled from its skin's front faces (no extras), 8x scale."""
    k = 8
    sheet = Image.new("RGBA", (len(FORMS) * 24 * k, 36 * k), (40, 40, 48, 255))
    for i, form in enumerate(FORMS):
        skin = Image.open(ROOT / "textures" / "entity" / f"xenoform_{form}.png")
        card = Image.new("RGBA", (24, 36))
        if form == "duplex":
            duplex_card(skin, card)
            sheet.paste(card.resize((24 * k, 36 * k), Image.NEAREST), (i * 24 * k, 0))
            continue
        if form == "gremlin":
            gremlin_card(skin, card)
            sheet.paste(card.resize((24 * k, 36 * k), Image.NEAREST), (i * 24 * k, 0))
            continue
        card.paste(skin.crop(front(HEAD)), (8, 0))
        card.paste(skin.crop(front(BODY)), (8, 8))
        card.paste(skin.crop(front(RARM)), (4, 8))
        card.paste(skin.crop(front(LARM)), (16, 8))
        card.paste(skin.crop(front(RLEG)), (8, 20))
        card.paste(skin.crop(front(LLEG)), (12, 20))
        # the extras that sit in front of the body or head
        for part, at in EXTRAS_IN_FRONT.get(form, ()):
            face = skin.crop(front(part))
            card.paste(face, at, face)
        sheet.paste(card.resize((24 * k, 36 * k), Image.NEAREST), (i * 24 * k, 0))
    sheet.save(out)


if __name__ == "__main__":
    band()
    rival_band()
    fallen_star()
    bulwark()
    pyre()
    blur()
    prism()
    wisp()
    gremlin()
    knell()
    wyrm()
    nadir()
    duplex()
    rivet()
    band_worn()
    holo_glow()
    for form in ULTIMATE_COLORS:
        if form not in OWN_ULTIMATES + PATCH4 + PATCH5 + SCAN_B + SCAN_C + SCAN_D + SCAN_E + SCAN_F + SCAN_G:
            ultimate(form)
    ultimate_spikes()
    # (0.18.96) drawn last, so the older skins' random speckle comes out as it always has
    gorger()
    slipfin()
    polestar()
    lurk()
    for form in PATCH4:
        if form not in OWN_ULTIMATES:  # (0.21.37)
            ultimate(form)
    # (0.18.103) patch 5, after patch 4 for the same reason; Dud has no Ultimate
    gyre()
    cortex()
    locust()
    croaker()
    dud()
    for form in PATCH5:
        ultimate(form)
    # (0.18.107) Scan B, last again so nothing older changes
    rime()
    marrow()
    swathe()
    blight()
    for form in SCAN_B:
        ultimate(form)
    # (0.18.109) Scan C, last again
    howl()
    prowl()
    tusk()
    talon()
    for form in SCAN_C:
        ultimate(form)
    # (0.18.111) Scan D, last again
    murk()
    gnasher()
    lumen()
    keen()
    for form in SCAN_D:
        ultimate(form)
    # (0.18.113) Scan E, last again
    hive()
    skitter()
    shade_form()
    mirage()
    for form in SCAN_E:
        ultimate(form)
    # (0.18.114) Scan F, last again
    ooze()
    magma_ooze()
    fuse()
    thornwild()
    billow()
    for form in SCAN_F:
        ultimate(form)
    # (0.18.119) Scan G, last again
    towerback()
    for form in SCAN_G:
        ultimate(form)
    print("Xenoform assets written under", ROOT)
    if len(sys.argv) > 1:
        preview(sys.argv[1])
        print("preview sheet:", sys.argv[1])
