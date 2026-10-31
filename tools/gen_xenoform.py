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
    """Riveted steel plate, amber visor, the insignia stamped on the breastplate."""
    img = new_skin()
    steel = (112, 122, 136)
    dark = shade(steel, 0.55)
    rivet = shade(steel, 1.45)
    parts(img, steel, edge=0.8)
    # plate seams on every face, bricked, with a rivet where they cross
    for part in BASE_PARTS:
        for name, (x0, y0, x1, y1) in faces(*part).items():
            if name in ("top", "bottom"):
                continue
            for y in range(y0 + 3, y1 - 1, 4):
                rect(img, (x0, y, x1, y + 1), dark)
                off = ((y - y0) // 4) % 2 * 2
                for x in range(x0 + 1 + off, x1 - 1, 4):
                    img.putpixel((x, y), rivet)
                    if y + 1 < y1 - 1 and rng.random() < 0.5:
                        img.putpixel((x, y - 1), rivet)
    # heavier iron on the forearms and shins, black at the knuckles and toes
    for part in LIMBS:
        for name, (x0, y0, x1, y1) in faces(*part).items():
            if name in ("front", "back", "left", "right"):
                noise_fill(img, shade(steel, 0.78), 0.15, (x0, y1 - 5, x1, y1 - 2))
                rect(img, (x0, y1 - 5, x1, y1 - 4), rivet)
    claws(img, (36, 38, 44, 255))
    # head: a jaw guard, a vertical seam down the brow, and a wide amber visor
    hx0, hy0, hx1, hy1 = front(HEAD)
    noise_fill(img, shade(steel, 0.7), 0.15, (hx0, hy1 - 2, hx1, hy1))
    rect(img, (11, 8, 13, 10), dark)
    rect(img, (9, 11, 15, 13), (40, 32, 20, 255))
    eyes(img, (255, 200, 60, 255), 11, wide=True)
    img.putpixel((10, 11), (255, 240, 170, 255))
    img.putpixel((14, 11), (255, 240, 170, 255))
    # neck bolts on the head's sides
    for (x0, y0, x1, y1) in (faces(*HEAD)["right"], faces(*HEAD)["left"]):
        rect(img, (x0 + 3, y1 - 3, x0 + 5, y1 - 1), dark)
        img.putpixel((x0 + 3, y1 - 3), rivet)
    # the breastplate (the model's plates cover the top of the body): a central ridge and the insignia under it
    bx0, by0, bx1, by1 = front(BODY)
    rect(img, (23, by0, 25, by1), shade(steel, 0.9))
    chest_insignia(img)
    # extras: brow 10x3x3 at (0,64); plates 12x4x7 at (0,72); pauldrons 5x3x5 at (40,64)
    heavy = shade(steel, 0.72)
    box(img, (0, 64, 10, 3, 3), heavy, 0.15, edge=0.8)
    box(img, (0, 72, 12, 4, 7), heavy, 0.15, edge=0.8)
    box(img, (40, 64, 5, 3, 5), heavy, 0.15, edge=0.8)
    for part in ((0, 64, 10, 3, 3), (0, 72, 12, 4, 7), (40, 64, 5, 3, 5)):
        for name, (x0, y0, x1, y1) in faces(*part).items():
            if name != "bottom":
                for x in range(x0 + 1, x1 - 1, 3):
                    img.putpixel((x, y0 + 1), rivet)
    # a bright rim along the top of the plates
    rect(img, (7, 72, 19, 73), shade(steel, 1.2))
    clear_hat(img)
    save(img, "entity/xenoform_bulwark.png")


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
    """Velox: a dark blue-black hide with pale chevron stripes down the back, arms and legs, a pale cyan glass visor,
    glossy near-black ball feet, a swept crest, and the insignia on the breast."""
    img = new_skin()
    hide = (28, 40, 72)
    pale = (222, 234, 250)
    glass = (150, 226, 242)
    parts(img, hide, 0.16)
    # the chevrons, lit like the face they're on
    for part in (BODY, RARM, LARM, RLEG, LLEG):
        for name, b in faces(*part).items():
            if name in ("top", "bottom") or (part is BODY and name == "front"):
                continue
            step = 4 if part is BODY else 3
            chevrons(img, b, shade(pale, LIGHT[name]), step, 1 if name == "front" else 0)
    # a pale line down the throat and belly
    bx0, by0, bx1, by1 = front(BODY)
    rect(img, (bx0 + 3, by0, bx0 + 5, by0 + 3), shade(pale, 0.9))
    # head: a pale blaze, and cool cyan eyes for when the visor is up
    hx0, hy0, hx1, hy1 = front(HEAD)
    rect(img, (hx0 + 3, hy0, hx0 + 5, hy0 + 2), shade(pale, 0.85))
    eyes(img, (190, 250, 255, 255), 11, wide=True, pupil=(20, 28, 50, 255))
    # dark hands
    claws(img, (14, 18, 34, 255), ARMS, rows=2)
    chest_insignia(img, rim=(10, 16, 36, 255))
    # extras: crest 2x3x5 at (0,64); sweep 2x3x8 at (14,64); visor 9x3x1 at (34,64); snout 4x2x3 at (34,68);
    # feet 4x3x4 at (0,76) and (16,76); tail 3x3x10 at (0,84); tip 2x2x10 at (26,84)
    for part in ((0, 64, 2, 3, 5), (14, 64, 2, 3, 8)):
        for name, (x0, y0, x1, y1) in faces(*part).items():
            vgrad(img, (x0, y0, x1, y1), shade(pale, 0.95), shade(hide, 1.1), 0.12)
            if name in ("left", "right"):
                for x in range(x0, x1, 2):
                    rect(img, (x, y0, x + 1, y0 + 1), CLEAR)
    visor = (34, 64, 9, 3, 1)
    box(img, visor, glass, 0.06, edge=0.7)
    vx0, vy0, vx1, vy1 = front(visor)
    for i in range(3):
        img.putpixel((vx0 + 2 + i * 2, vy1 - 1 - i % 2 - (1 if i == 0 else 0)), (240, 255, 255, 255))
    rect(img, (vx0 + 1, vy0, vx0 + 4, vy0 + 1), (240, 255, 255, 255))
    snout = (34, 68, 4, 2, 3)
    box(img, snout, shade(hide, 1.15), 0.12)
    sx0, sy0, sx1, sy1 = front(snout)
    img.putpixel((sx0, sy0), (8, 10, 20, 255))
    img.putpixel((sx1 - 1, sy0), (8, 10, 20, 255))
    # glossy near-black ball feet: a lighter rim and a highlight
    for foot in ((0, 76, 4, 3, 4), (16, 76, 4, 3, 4)):
        box(img, foot, (16, 18, 28), 0.08, edge=1.9)
        fx0, fy0, fx1, fy1 = front(foot)
        img.putpixel((fx0 + 1, fy0 + 1), (120, 140, 190, 255))
        tx0, ty0, tx1, ty1 = faces(*foot)["top"]
        img.putpixel((tx0 + 1, ty0 + 1), (150, 170, 215, 255))
    for part in ((0, 84, 3, 3, 10), (26, 84, 2, 2, 10)):
        box(img, part, hide, 0.16)
        for name, (x0, y0, x1, y1) in faces(*part).items():
            if name in ("top", "bottom"):
                continue
            for y in range(y0 + 1, y1, 3):
                rect(img, (x0, y, x1, y + 1), shade(pale, LIGHT[name]))
    # the tail's tip is bright
    rect(img, (46, 94, 48, 96), (190, 250, 255, 255))
    clear_hat(img)
    save(img, "entity/xenoform_blur.png")


def prism():
    """Faceted violet crystal, white-hot tips, fracture lines, and the insignia set in the chest like an inclusion."""
    img = new_skin()
    crystal = (146, 98, 208)
    bright = (236, 220, 255, 255)
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
    # extras: crown 2x6x2 at (0,64); crystals 2x5x2 at (8,64); back 6x6x3 at (16,64); spine 2x5x2 at (34,64);
    # shoulder crystals 2x4x2 at (42,64)
    for part in ((0, 64, 2, 6, 2), (8, 64, 2, 5, 2), (16, 64, 6, 6, 3), (34, 64, 2, 5, 2), (42, 64, 2, 4, 2)):
        for name, (x0, y0, x1, y1) in faces(*part).items():
            vgrad(img, (x0, y0, x1, y1), (246, 236, 255), shade(crystal, LIGHT[name]), 0.1)
            if name == "top":
                noise_fill(img, (250, 245, 255), 0.05, (x0, y0, x1, y1))
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


def gremlin():
    """A grey, warty imp: mottled hide, a pale belly, a spiny back, black claws, a huge skull with yellow eyes and
    a jagged grin, notched ears, and the insignia on its chest."""
    img = new_skin()
    grey = (118, 120, 126)
    belly = (168, 166, 164)
    wart = shade(grey, 0.72)
    spine = (60, 58, 62, 255)
    parts(img, grey, 0.26)
    # warts and mottling everywhere; a pale belly; a row of spines down the back
    for part in BASE_PARTS:
        for name, b in faces(*part).items():
            speckle(img, b, wart, (b[2] - b[0]) * (b[3] - b[1]) // 6)
            speckle(img, b, shade(grey, 1.2), (b[2] - b[0]) * (b[3] - b[1]) // 10)
    bx0, by0, bx1, by1 = front(BODY)
    vgrad(img, (bx0 + 1, by0 + 1, bx1 - 1, by1 - 1), belly, shade(belly, 0.85), 0.12)
    kx0, ky0, kx1, ky1 = faces(*BODY)["back"]
    for y in range(ky0, ky1, 2):
        rect(img, (kx0 + 3, y, kx0 + 5, y + 1), spine)
    # knobbly joints at the elbows and knees, black claws
    for part in LIMBS:
        for name, (x0, y0, x1, y1) in faces(*part).items():
            if name in ("front", "back", "left", "right"):
                rect(img, (x0, y0 + 5, x1, y0 + 6), wart)
    claws(img, (28, 26, 30, 255))
    # the head under the skull barely shows: keep it grey with a dark throat
    rect(img, (10, 14, 14, 16), wart)
    chest_insignia(img, rim=(40, 40, 44, 255))
    # extras: skull 10x10x10 at (0,64); ears 4x5x1 at (40,64); tail 1x1x6 at (40,72)
    skull = (0, 64, 10, 10, 10)
    box(img, skull, shade(grey, 1.04), 0.22)
    for name, b in faces(*skull).items():
        speckle(img, b, wart, 12)
        speckle(img, b, shade(grey, 1.25), 6)
    sx0, sy0, sx1, sy1 = front(skull)
    # brow ridge, huge yellow eyes with slit pupils, nostrils, a wide grin of white teeth
    rect(img, (sx0 + 1, sy0 + 1, sx1 - 1, sy0 + 2), wart)
    rect(img, (sx0 + 1, sy0 + 2, sx0 + 4, sy0 + 6), (250, 230, 80, 255))
    rect(img, (sx1 - 4, sy0 + 2, sx1 - 1, sy0 + 6), (250, 230, 80, 255))
    rect(img, (sx0 + 2, sy0 + 3, sx0 + 3, sy0 + 6), (20, 20, 20, 255))
    rect(img, (sx1 - 3, sy0 + 3, sx1 - 2, sy0 + 6), (20, 20, 20, 255))
    img.putpixel((sx0 + 1, sy0 + 2), (255, 250, 200, 255))
    img.putpixel((sx1 - 2, sy0 + 2), (255, 250, 200, 255))
    img.putpixel((sx0 + 4, sy0 + 7), (40, 36, 40, 255))
    img.putpixel((sx1 - 5, sy0 + 7), (40, 36, 40, 255))
    rect(img, (sx0 + 1, sy0 + 8, sx1 - 1, sy0 + 10), (50, 22, 26, 255))
    for x in range(sx0 + 1, sx1 - 1):
        if x % 2 == 0:
            img.putpixel((x, sy0 + 8), (240, 236, 220, 255))
        else:
            img.putpixel((x, sy0 + 9), (240, 236, 220, 255))
    # warty crown on the skull's top
    speckle(img, faces(*skull)["top"], wart, 14)
    ears = (40, 64, 4, 5, 1)
    box(img, ears, shade(grey, 0.95), 0.2)
    ex0, ey0, ex1, ey1 = front(ears)
    rect(img, (ex0 + 1, ey0 + 1, ex1 - 1, ey1 - 1), (214, 150, 150, 255))
    rect(img, (ex0, ey0, ex0 + 1, ey0 + 1), CLEAR)
    rect(img, (ex0 + 2, ey1 - 2, ex0 + 3, ey1 - 1), CLEAR)
    tail = (40, 72, 1, 1, 6)
    box(img, tail, grey, 0.2)
    for name, (x0, y0, x1, y1) in faces(*tail).items():
        if name in ("left", "right", "top", "bottom"):
            for x in range(x0, x1, 2):
                img.putpixel((x, y0), wart)
    clear_hat(img)
    save(img, "entity/xenoform_gremlin.png")


def knell():
    """Sculk-dark hide threaded with cyan light, bone horns, a glowing ribcage with the insignia at the sternum,
    a blind face, and claws of bone."""
    img = new_skin()
    sculk = (20, 44, 52)
    cold = (31, 184, 200)
    hot = (120, 240, 250)
    bone = (206, 196, 176)
    parts(img, sculk, 0.3)
    # veins of light everywhere, thickest down the limbs; sculk growths (darker, with a bright centre) on the back
    for part in BASE_PARTS:
        for name, b in faces(*part).items():
            n = 1 if name in ("top", "bottom") else 3
            veins(img, b, cold, hot, n + (2 if part in LIMBS else 0), (2, 6))
    kx0, ky0, kx1, ky1 = faces(*BODY)["back"]
    for _ in range(4):
        x, y = rng.randrange(kx0 + 1, kx1 - 1), rng.randrange(ky0 + 1, ky1 - 1)
        rect(img, (x - 1, y - 1, x + 2, y + 2), shade(sculk, 0.6))
        img.putpixel((x, y), hot + (255,) if len(hot) == 3 else hot)
    # bone plates at the knuckles and shins, claw tips
    for part in LIMBS:
        for name, (x0, y0, x1, y1) in faces(*part).items():
            if name in ("front", "back", "left", "right"):
                for x in range(x0, x1, 2):
                    rect(img, (x, y1 - 3, x + 1, y1), bone)
            elif name == "bottom":
                noise_fill(img, bone, 0.15, (x0, y0, x1, y1))
    # the face: blind and smooth, a faint glow where the eyes would be, sculk tendrils at the jaw
    hx0, hy0, hx1, hy1 = front(HEAD)
    noise_fill(img, shade(sculk, 0.9), 0.2, (hx0, hy0, hx1, hy1))
    rect(img, (9, 10, 15, 12), (18, 90, 100, 255))
    rect(img, (10, 11, 14, 12), (30, 140, 150, 255))
    for x in (9, 11, 13, 15):
        rect(img, (x, 14, x + 1, 16), shade(sculk, 0.6))
        img.putpixel((x, 15), cold + (255,))
    # the ribs cover the chest; the chest behind is dark, a glow at its heart
    bx0, by0, bx1, by1 = front(BODY)
    noise_fill(img, shade(sculk, 0.7), 0.2, (bx0, by0, bx1, by1))
    veins(img, (bx0, by0, bx1, by1), cold, hot, 3, (2, 5))
    # extras: horns 2x7x2 at (0,64); ribs 10x8x2 at (8,64); shoulders 12x3x5 at (0,76); tendrils 1x4x1 at (34,64)
    horns = (0, 64, 2, 7, 2)
    for name, (x0, y0, x1, y1) in faces(*horns).items():
        vgrad(img, (x0, y0, x1, y1), (236, 230, 214), shade(bone, 0.7), 0.1)
        for y in range(y0 + 2, y1, 2):
            rect(img, (x0, y, x1, y + 1), shade(bone, 0.6))
    ribs = (8, 64, 10, 8, 2)
    box(img, ribs, shade(sculk, 0.8), 0.2)
    for name, (x0, y0, x1, y1) in faces(*ribs).items():
        if name in ("front", "left", "right", "back"):
            for y in range(y0 + 1, y1, 2):
                rect(img, (x0, y, x1, y + 1), lerp(cold, hot, 0.3))
                rect(img, (x0, y, x1, y + 1), lerp(cold, hot, 0.3))
    rx0, ry0, rx1, ry1 = front(ribs)
    rect(img, (rx0 + 4, ry0, rx0 + 6, ry1), shade(sculk, 0.6))
    insignia(img, rx0 + 5, ry0 + 1, rim=(8, 24, 30, 255))
    shoulders = (0, 76, 12, 3, 5)
    box(img, shoulders, shade(sculk, 0.75), 0.25)
    for name, b in faces(*shoulders).items():
        veins(img, b, cold, hot, 2, (2, 5))
        if name == "top":
            speckle(img, b, shade(sculk, 0.5), 8)
    tendril = (34, 64, 1, 4, 1)
    box(img, tendril, shade(sculk, 0.7), 0.2)
    rect(img, (34, 68, 38, 69), cold + (255,))
    clear_hat(img)
    save(img, "entity/xenoform_knell.png")


def wyrm():
    """Dark violet scales, pale belly plates, bone horns, bat wings with bones and torn edges, a finned tail, and the
    insignia on the breast plates."""
    img = new_skin()
    scale = (32, 24, 44)
    plate = (120, 100, 130)
    membrane = (112, 50, 152)
    bone = (190, 180, 170)
    parts(img, scale, 0.25)
    # scales: a brick pattern of lighter crescents on every face, belly plates on the front of the body and legs
    for part in BASE_PARTS:
        for name, (x0, y0, x1, y1) in faces(*part).items():
            if name == "front" and part in (BODY,) + LEGS:
                for y in range(y0 + 1, y1 - 1, 3):
                    rect(img, (x0 + 1, y, x1 - 1, y + 2), shade(plate, 0.9 + ((y - y0) // 3) % 2 * 0.12))
                    rect(img, (x0 + 1, y + 2, x1 - 1, y + 3), shade(scale, 0.8))
                continue
            for y in range(y0, y1, 3):
                for x in range(x0 + (y // 3) % 2 * 2, x1, 4):
                    rect(img, (x, y, min(x + 2, x1), y + 1), shade(scale, 1.35 * LIGHT[name]))
                    if x + 1 < x1 and y + 1 < y1:
                        img.putpixel((x + 1, y + 1), shade(scale, 0.7))
    # spines down the back of the body and the head
    for face_box in (faces(*BODY)["back"], faces(*HEAD)["back"]):
        x0, y0, x1, y1 = face_box
        for y in range(y0, y1, 2):
            rect(img, (x0 + 3, y, x0 + 5, y + 1), bone)
    # talons
    claws(img, bone)
    # head: a horned brow, violet slit eyes, nostrils and a jaw line of teeth
    hx0, hy0, hx1, hy1 = front(HEAD)
    rect(img, (9, 9, 15, 10), shade(scale, 0.7))
    rect(img, (9, 10, 15, 13), shade(scale, 0.85))
    eyes(img, (210, 90, 240, 255), 11, wide=True, pupil=(20, 8, 24, 255))
    img.putpixel((11, 13), shade(scale, 0.5))
    img.putpixel((12, 13), shade(scale, 0.5))
    rect(img, (9, 14, 15, 15), shade(scale, 0.6))
    for x in range(9, 15, 2):
        img.putpixel((x, 14), bone)
    chest_insignia(img, rim=(30, 14, 40, 255))
    # extras: horns 2x2x7 at (0,64); tail 3x3x10 at (18,64); tip 2x2x8 at (0,78); wings 14x1x8 at (20,80);
    # tail fin 1x4x3 at (44,64)
    horns = (0, 64, 2, 2, 7)
    box(img, horns, bone, 0.15)
    for name, (x0, y0, x1, y1) in faces(*horns).items():
        if name in ("top", "bottom"):
            for x in range(x0 + 1, x1, 2):
                rect(img, (x, y0, x + 1, y1), shade(bone, 0.75))
        else:
            for y in range(y0, y1):
                for x in range(x0 + 1, x1, 2):
                    img.putpixel((x, y), shade(bone, 0.75))
        if name in ("front",):
            noise_fill(img, shade(bone, 0.6), 0.1, (x0, y0, x1, y1))
    for part in ((18, 64, 3, 3, 10), (0, 78, 2, 2, 8)):
        box(img, part, scale, 0.25)
        for name, (x0, y0, x1, y1) in faces(*part).items():
            if name == "bottom":
                for x in range(x0 + 1, x1, 3):
                    rect(img, (x, y0, x + 2, y1), plate)
            elif name == "top":
                for x in range(x0, x1, 2):
                    rect(img, (x, y0, x + 1, y1), bone)
            else:
                for x in range(x0, x1, 3):
                    rect(img, (x, y0, x + 1, y1), shade(scale, 1.3))
    wing = (20, 80, 14, 1, 8)
    for name, (x0, y0, x1, y1) in faces(*wing).items():
        vgrad(img, (x0, y0, x1, y1), shade(membrane, 1.15), shade(membrane, 0.75), 0.2)
        if name in ("top", "bottom"):
            # bones fanning from the shoulder (the inner edge), a torn trailing edge, veins between
            for x in range(x0, x1, 4):
                rect(img, (x, y0, x + 1, y1), (26, 14, 36, 255))
            rect(img, (x0, y0, x1, y0 + 1), (26, 14, 36, 255))
            veins(img, (x0, y0 + 1, x1, y1), shade(membrane, 0.7), shade(membrane, 0.55), 6, (3, 7))
            for x in range(x0 + 1, x1, 4):
                if rng.random() < 0.7:
                    rect(img, (x, y1 - 1 - rng.randrange(0, 2), x + 2, y1), CLEAR)
    fin = (44, 64, 1, 4, 3)
    for name, (x0, y0, x1, y1) in faces(*fin).items():
        vgrad(img, (x0, y0, x1, y1), shade(membrane, 1.1), shade(membrane, 0.7), 0.15)
        if name in ("left", "right"):
            rect(img, (x0, y0, x0 + 1, y1), (26, 14, 36, 255))
    # (0.10.29) bigger two-part wings that wrap round it on the ground: each half 10x1x14 at (0,90), the inner and
    # outer halves sharing it; a longer tail: base 3x3x13 at (0,106), middle 2x2x10 at (32,106), then the old tip.
    # Its own random stream, so the forms drawn after it come out as before.
    saved = rng.getstate()
    rng.seed(2629)
    wrng = rng
    big_wing = (0, 90, 10, 1, 14)
    for name, (x0, y0, x1, y1) in faces(*big_wing).items():
        vgrad(img, (x0, y0, x1, y1), shade(membrane, 1.15), shade(membrane, 0.72), 0.2)
        if name in ("top", "bottom"):
            # a bone along the leading edge, finger bones fanning back, veins between, a torn trailing edge
            rect(img, (x0, y0, x1, y0 + 1), (26, 14, 36, 255))
            for x in range(x0, x1, 3):
                for y in range(y0, y1 - 2):
                    if (y - y0) < (y1 - y0) * (0.55 + 0.4 * ((x - x0) % 6 == 0)):
                        img.putpixel((x, y), (40, 20, 52, 255))
            veins(img, (x0, y0 + 1, x1, y1), shade(membrane, 0.7), shade(membrane, 0.55), 8, (3, 8))
            for x in range(x0 + 1, x1, 3):
                if wrng.random() < 0.7:
                    rect(img, (x, y1 - 1 - wrng.randrange(0, 3), x + 2, y1), CLEAR)
    for part in ((0, 106, 3, 3, 13), (32, 106, 2, 2, 10)):
        box(img, part, scale, 0.25)
        for name, (x0, y0, x1, y1) in faces(*part).items():
            if name == "bottom":
                for x in range(x0 + 1, x1, 3):
                    rect(img, (x, y0, x + 2, y1), plate)
            elif name == "top":
                for x in range(x0, x1, 2):
                    rect(img, (x, y0, x + 1, y1), bone)
            else:
                for x in range(x0, x1, 3):
                    rect(img, (x, y0, x + 1, y1), shade(scale, 1.3))
    rng.setstate(saved)
    clear_hat(img)
    save(img, "entity/xenoform_wyrm.png")


def nadir():
    """The void given shape: near-black with faint stars and nebula dust, a face of deep nothing, and the green xeno
    insignia burning on the chest; the shoulders and mantle are starfields too."""
    img = new_skin()
    void = (9, 7, 16)
    parts(img, void, 0.25, light={"top": 1.4, "bottom": 0.8, "right": 1.0, "left": 1.0, "front": 1.0, "back": 0.9})
    # nebula dust: faint patches of violet and blue, then stars, a few bright
    for part in BASE_PARTS:
        for name, b in faces(*part).items():
            x0, y0, x1, y1 = b
            for _ in range(2):
                cx, cy = rng.randrange(x0, x1), rng.randrange(y0, y1)
                tint = rng.choice(((50, 30, 90), (24, 40, 90), (70, 24, 70)))
                for y in range(y0, y1):
                    for x in range(x0, x1):
                        d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
                        if d < 3.5 and rng.random() < 0.7:
                            img.putpixel((x, y), lerp(img.getpixel((x, y)), tint, 0.5 * (1 - d / 3.5)))
            speckle(img, b, (90, 70, 140, 255), (x1 - x0) * (y1 - y0) // 10)
            speckle(img, b, (160, 140, 220, 255), (x1 - x0) * (y1 - y0) // 24)
            speckle(img, b, (230, 225, 255, 255), 1)
    # a faint rim of violet light along the top edge of every face: the shape reads against the dark
    for part in BASE_PARTS:
        for name, (x0, y0, x1, y1) in faces(*part).items():
            if name in ("front", "back", "left", "right"):
                for x in range(x0, x1):
                    if rng.random() < 0.6:
                        img.putpixel((x, y0), (60, 40, 110, 255))
    # the face (8,8)-(16,16): a deeper void, violet at the edges, a single pale point low in it
    for y in range(8, 16):
        for x in range(8, 16):
            d = max(abs(x - 11.5), abs(y - 11.5)) / 3.5
            img.putpixel((x, y), lerp((2, 1, 6), (60, 30, 110), d * d))
    img.putpixel((11, 13), (210, 200, 250, 255))
    img.putpixel((12, 13), (160, 140, 220, 255))
    # the insignia: a green glow bleeding into the chest around it, then the hourglass itself
    bx0, by0, bx1, by1 = front(BODY)
    for y in range(by0 + 1, by1):
        for x in range(bx0, bx1):
            d = max(abs(x - 24), abs(y - 27) * 0.8)
            if d < 4 and rng.random() < 0.8:
                img.putpixel((x, y), lerp(img.getpixel((x, y)), (30, 90, 50), 0.6 * (1 - d / 4)))
    chest_insignia(img, rim=(8, 40, 24, 255))
    # extras: shoulders 12x4x6 at (0,64); mantle 10x14x1 at (36,64); the mantle ragged at its foot
    shoulders = (0, 64, 12, 4, 6)
    box(img, shoulders, (6, 4, 12), 0.2)
    for name, b in faces(*shoulders).items():
        speckle(img, b, (90, 70, 140, 255), 8)
        speckle(img, b, (200, 190, 240, 255), 2)
        if name == "top":
            x0, y0, x1, y1 = b
            rect(img, (x0, y0, x1, y0 + 1), (70, 50, 120, 255))
    mantle = (36, 64, 10, 14, 1)
    box(img, mantle, (14, 10, 26), 0.3)
    for name, b in faces(*mantle).items():
        if name in ("front", "back"):
            x0, y0, x1, y1 = b
            speckle(img, b, (60, 44, 100, 255), 30)
            speckle(img, b, (140, 120, 200, 255), 12)
            speckle(img, b, (230, 225, 255, 255), 3)
            for x in range(x0, x1):
                if rng.random() < 0.5:
                    rect(img, (x, y1 - 1 - rng.randrange(0, 3), x + 1, y1), CLEAR)
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
    """(0.18.95) Rivet, living metal: chrome that runs like quicksilver (bright streaks down every face, a blue-grey
    sheen), green circuit lines down the limbs, a smooth face with two green slit eyes, the insignia on the chest."""
    img = new_skin()
    metal = (158, 176, 190)
    green = GREEN
    dark_line = (40, 70, 52, 255)
    parts(img, metal, 0.08)
    for part in BASE_PARTS:
        for name, (x0, y0, x1, y1) in faces(*part).items():
            vgrad(img, (x0, y0, x1, y1), (188, 204, 216), shade(metal, LIGHT[name] * 0.6), 0.08)
            # quicksilver runs: bright drips down the face
            if name in ("front", "back", "left", "right"):
                for _ in range(max(1, (x1 - x0) // 3)):
                    x = rng.randrange(x0, x1)
                    top = rng.randrange(y0, max(y0 + 1, y1 - 3))
                    for y in range(top, min(y1, top + rng.randint(2, 5))):
                        img.putpixel((x, y), (240, 248, 255, 255))
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
    # the body: a dark seam down each side
    x0, y0, x1, y1 = faces(*BODY)["front"]
    for y in range(y0, y1):
        img.putpixel((x0, y), dark_line)
        img.putpixel((x1 - 1, y), dark_line)
    # the face: smooth, two slit eyes, no mouth
    rect(img, (9, 11, 11, 12), green)
    rect(img, (13, 11, 15, 12), green)
    img.putpixel((9, 11), PALE_GREEN)
    img.putpixel((14, 11), PALE_GREEN)
    chest_insignia(img, rim=(30, 40, 52, 255))
    # extras: crest 1x2x6 at (0,64); drips 2x3x2 at (16,64)
    for part in ((0, 64, 1, 2, 6), (16, 64, 2, 3, 2)):
        for name, (x0, y0, x1, y1) in faces(*part).items():
            vgrad(img, (x0, y0, x1, y1), (236, 244, 250), shade(metal, LIGHT[name]), 0.05)
    x0, y0, x1, y1 = faces(0, 64, 1, 2, 6)["right"]
    for x in range(x0, x1):
        img.putpixel((x, y0), green)
    clear_hat(img)
    save(img, "entity/xenoform_rivet.png")


def gorger():
    """(0.18.96) Gorger, the armadillo alien: dusky pink-brown hide, a banded shell of scutes over the back (each band
    edged dark), a bone horn on the snout, little round ears, beady black eyes, the insignia on its chest."""
    img = new_skin()
    hide = (176, 112, 92)
    scute = (150, 96, 84)
    dark = (70, 40, 36, 255)
    parts(img, hide, 0.16)
    # banded plates on the arms, legs and back: a dark rule every third row
    for part in LIMBS + (BODY,):
        for name, (x0, y0, x1, y1) in faces(*part).items():
            if name in ("front", "back", "left", "right"):
                for y in range(y0 + 2, y1, 3):
                    for x in range(x0, x1):
                        img.putpixel((x, y), shade(hide + (255,), 0.72))
    # a softer belly
    bx0, by0, bx1, by1 = front(BODY)
    vgrad(img, (bx0 + 1, by0 + 1, bx1 - 1, by1 - 1), (214, 168, 150), (192, 146, 128), 0.08)
    claws(img, (54, 36, 30, 255))
    # the face: a long pale snout, beady eyes
    rect(img, (10, 12, 14, 16), (206, 156, 138, 255))
    rect(img, (11, 14, 13, 15), dark)
    img.putpixel((9, 11), (20, 14, 14, 255))
    img.putpixel((14, 11), (20, 14, 14, 255))
    chest_insignia(img, rim=(60, 36, 32, 255))
    # extras: horn 2x4x2 at (0,64); ears 2x3x1 at (8,64); shell 10x10x4 at (0,72)
    box(img, (0, 64, 2, 4, 2), (226, 214, 190), 0.1)
    for name, (x0, y0, x1, y1) in faces(0, 64, 2, 4, 2).items():
        if name not in ("top", "bottom"):
            img.putpixel((x0, y0), (250, 244, 230, 255))
    box(img, (8, 64, 2, 3, 1), hide, 0.12)
    ex0, ey0, ex1, ey1 = front((8, 64, 2, 3, 1))
    rect(img, (ex0, ey0 + 1, ex1, ey1), (214, 150, 140, 255))
    shell = (0, 72, 10, 10, 4)
    box(img, shell, scute, 0.14)
    for name, (x0, y0, x1, y1) in faces(*shell).items():
        # scute bands across the shell, each with a pale top edge and a dark seam
        for y in range(y0, y1, 3):
            for x in range(x0, x1):
                img.putpixel((x, y), dark if (y - y0) % 3 == 0 else img.getpixel((x, y)))
                if y + 1 < y1:
                    img.putpixel((x, y + 1), shade(scute + (255,), 1.2))
    sx0, sy0, sx1, sy1 = faces(*shell)["back"]
    insignia(img, (sx0 + sx1) // 2, sy0 + 2, small=True, rim=dark)
    clear_hat(img)
    save(img, "entity/xenoform_gorger.png")


def slipfin():
    """(0.18.96) Slipfin, the axolotl alien: smooth pink skin with a wet sheen, darker spots, a pale belly, feathery
    magenta gills, a fin down its back, a flat tail, small black eyes and a little smile."""
    img = new_skin()
    pink = (238, 150, 186)
    spot = shade(pink + (255,), 0.78)
    gill = (214, 56, 132)
    parts(img, pink, 0.08)
    for part in BASE_PARTS:
        for name, (x0, y0, x1, y1) in faces(*part).items():
            vgrad(img, (x0, y0, x1, y1), shade(pink + (255,), LIGHT[name] * 1.08), shade(pink + (255,), LIGHT[name] * 0.9), 0.06)
            speckle(img, (x0, y0, x1, y1), spot, (x1 - x0) * (y1 - y0) // 14)
            # a wet shine near the top of each face
            if name in ("front", "back", "left", "right") and y1 - y0 > 3:
                rect(img, (x0 + 1, y0 + 1, min(x1, x0 + 2), y0 + 2), (255, 236, 244, 255))
    bx0, by0, bx1, by1 = front(BODY)
    vgrad(img, (bx0 + 1, by0 + 2, bx1 - 1, by1), (252, 210, 222), (240, 186, 204), 0.05)
    # the face: wide-set black eyes, a small smile, pink cheeks
    img.putpixel((9, 11), (16, 12, 20, 255))
    img.putpixel((14, 11), (16, 12, 20, 255))
    rect(img, (11, 14, 13, 15), (150, 60, 96, 255))
    img.putpixel((10, 13), (150, 60, 96, 255))
    img.putpixel((13, 13), (150, 60, 96, 255))
    chest_insignia(img, rim=(120, 40, 80, 255))
    # extras: gills 4x1x1 at (0,64); fin 1x10x3 at (12,64); tail 3x1x7 at (24,64)
    box(img, (0, 64, 4, 1, 1), gill, 0.18)
    for name, (x0, y0, x1, y1) in faces(0, 64, 4, 1, 1).items():
        for x in range(x0, x1, 2):
            img.putpixel((x, y0), shade(gill + (255,), 1.3))
    box(img, (12, 64, 1, 10, 3), shade(pink + (255,), 1.05)[:3], 0.08)
    for name, (x0, y0, x1, y1) in faces(12, 64, 1, 10, 3).items():
        if name in ("left", "right"):
            for y in range(y0, y1, 2):
                rect(img, (x0, y, x1, y + 1), (250, 196, 216, 255))
    box(img, (24, 64, 3, 1, 7), pink, 0.08)
    for name, (x0, y0, x1, y1) in faces(24, 64, 3, 1, 7).items():
        speckle(img, (x0, y0, x1, y1), spot, 3)
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
    """(0.18.96) Lurk, the stalker: a bruise-purple dark that drinks the light, faint violet veins, long grey fingers,
    and a face that is almost all eyes: six pale ones, the two largest ringed in violet."""
    img = new_skin()
    dark = (40, 32, 56)
    vein = (110, 82, 176)
    parts(img, dark, 0.18)
    for part in BASE_PARTS:
        for name, b in faces(*part).items():
            veins(img, b, shade(dark + (255,), 0.7), (vein[0], vein[1], vein[2], 255), (b[2] - b[0]) // 3 + 1)
    # the face: six eyes, two big ones ringed in violet, four small ones round them
    head = faces(*HEAD)["front"]
    hx0, hy0 = head[0], head[1]
    rect(img, (hx0, hy0, hx0 + 8, hy0 + 8), shade(dark + (255,), 0.8))
    for ex in (hx0 + 1, hx0 + 5):
        rect(img, (ex, hy0 + 3, ex + 2, hy0 + 5), (110, 82, 176, 255))
        img.putpixel((ex, hy0 + 3), (236, 232, 248, 255))
        img.putpixel((ex + 1, hy0 + 4), (236, 232, 248, 255))
    for ex, ey in ((hx0 + 1, hy0 + 1), (hx0 + 6, hy0 + 1), (hx0 + 3, hy0 + 2), (hx0 + 4, hy0 + 6)):
        img.putpixel((ex, ey), (214, 206, 236, 255))
    chest_insignia(img, rim=(24, 18, 34, 255))
    # extras: the long hands 3x6x3 at (0,64), grey fingers with dark tips
    hand = (0, 64, 3, 6, 3)
    box(img, hand, (96, 92, 108), 0.12)
    for name, (x0, y0, x1, y1) in faces(*hand).items():
        if name in ("front", "back", "left", "right"):
            for x in range(x0, x1):
                img.putpixel((x, y1 - 1), (20, 16, 26, 255))
                if x % 2 == 0:
                    rect(img, (x, y0 + 2, x + 1, y1 - 1), (72, 68, 84, 255))
    clear_hat(img)
    save(img, "entity/xenoform_lurk.png")


# (0.18.93, patch 2) the Ultimates: each base skin reworked into an armoured hide, darker and plated, its plate seams
# glowing in the alien's colour; a second skin holds only the seams (and the base's brightest marks), drawn fullbright.
# Nadir has no Ultimate form (its C is a film).
ULTIMATE_COLORS = {
    "bulwark": (184, 196, 208), "pyre": (255, 138, 42), "blur": (76, 141, 255), "prism": (196, 139, 240),
    "wisp": (184, 232, 248), "gremlin": (154, 220, 90), "knell": (31, 184, 200), "wyrm": (192, 96, 224),
    "duplex": (224, 123, 74), "rivet": (159, 180, 192), "gorger": (176, 106, 84), "slipfin": (240, 140, 184),
    "polestar": (216, 74, 74), "lurk": (110, 82, 176),
}
ULTIMATE_PLATE, ULTIMATE_WIDTH = 5, 8
PATCH4 = ("gorger", "slipfin", "polestar", "lurk")


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


FORMS = ("bulwark", "pyre", "blur", "prism", "wisp", "gremlin", "knell", "wyrm", "nadir", "duplex", "rivet", "gorger", "slipfin", "polestar", "lurk")
# for the preview: extra parts drawn over the front view, (part, where on the 24x36 card)
EXTRAS_IN_FRONT = {
    "bulwark": (((0, 64, 10, 3, 3), (7, 1)), ((0, 72, 12, 4, 7), (6, 7))),
    "pyre": (((12, 78, 5, 5, 1), (10, 10)),),
    "blur": (((34, 68, 4, 2, 3), (10, 6)), ((34, 64, 9, 3, 1), (7, 3)), ((0, 76, 4, 3, 4), (8, 29)), ((16, 76, 4, 3, 4), (12, 29))),
    "wisp": (((0, 64, 10, 10, 10), (7, 0)), ((0, 86, 8, 7, 4), (8, 20))),
    "gremlin": (((0, 64, 10, 10, 10), (7, 0)), ((40, 64, 4, 5, 1), (3, 2)), ((40, 64, 4, 5, 1), (17, 2))),
    "knell": (((8, 64, 10, 8, 2), (7, 9)), ((0, 76, 12, 3, 5), (6, 6))),
    "nadir": (((0, 64, 12, 4, 6), (6, 7)),),
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
        if form not in PATCH4:
            ultimate(form)
    ultimate_spikes()
    # (0.18.96) drawn last, so the older skins' random speckle comes out as it always has
    gorger()
    slipfin()
    polestar()
    lurk()
    for form in PATCH4:
        ultimate(form)
    print("Xenoform assets written under", ROOT)
    if len(sys.argv) > 1:
        preview(sys.argv[1])
        print("preview sheet:", sys.argv[1])
