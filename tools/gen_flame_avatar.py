"""Flame Avatar player looks (Powers 0.15.151): overlays on the 64x64 player skin layout (base layer faces only; classic
and slim arms both covered), each copied onto the outer layer too (hat, jacket, sleeves, trousers) so no clothes show.

avatar_char.png  - the body burnt to char: charcoal over the skin, mostly opaque (a hint of the skin shows through, a
                   little more on the face so a friend is still known), ash-grey flecks. Drawn lit like skin.
avatar_veins.png - lava veins glowing through the char (drawn emissive, they light the dark): branching cracks over every
                   face, a molten heart in the chest, white-gold hands, a burning crown on top of the head, ember eyes.
Nothing is copied; colours are vanilla's fire and lava ramps. Run from the project root:  python -I tools/gen_flame_avatar.py
"""
import os
import random
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "jaymospowers", "textures",
                   "entity", "ember")
# Kingdoms' Flame Avatar villagers (Nether avatar camps) wear the same look
OUT_KINGDOMS = os.path.join(os.path.dirname(__file__), "..", "kingdoms", "src", "main", "resources", "assets",
                            "jaymoskingdoms", "textures", "entity", "ember")
os.makedirs(OUT, exist_ok=True)
os.makedirs(OUT_KINGDOMS, exist_ok=True)

# vanilla's lava/fire ramp, hottest first (emissive: how much light each adds)
WHITE = (255, 236, 170, 255)
GOLD = (252, 190, 60, 255)
ORANGE = (230, 110, 20, 255)
RED = (150, 40, 4, 255)
CHAR = [(26, 20, 18), (34, 26, 22), (44, 32, 26), (20, 16, 15)]
ASH = (78, 72, 68)
rng = random.Random(15151)

# every base-layer face box of the player skin: (u, v, width, height, depth) of each cuboid
CUBES = {
    "head": (0, 0, 8, 8, 8),
    "body": (16, 16, 8, 12, 4),
    "right_arm": (40, 16, 4, 12, 4),
    "left_arm": (32, 48, 4, 12, 4),
    "right_leg": (0, 16, 4, 12, 4),
    "left_leg": (16, 48, 4, 12, 4),
}


# the outer layer (hat, jacket, sleeves, trousers) of each: the burnt crust goes over it too, so nobody's clothes show
OUTER = {"head": (32, 0), "body": (16, 32), "right_arm": (40, 32), "left_arm": (48, 48), "right_leg": (0, 32), "left_leg": (0, 48)}


def save(img, name):
    for out in (OUT, OUT_KINGDOMS):
        img.save(os.path.join(out, name))


def crust(img, softer_face):
    """Copies each cuboid's base faces onto its outer layer (the face a little thinner, if softer_face)."""
    for name, (u, v, w, h, d) in CUBES.items():
        ou, ov = OUTER[name]
        piece = img.crop((u, v, u + d * 2 + w * 2, v + d + h))
        if softer_face and name == "head":
            fx0, fy0, fx1, fy1 = faces(u, v, w, h, d)["front"]
            px = piece.load()
            for y in range(fy0 - v, fy1 - v):
                for x in range(fx0 - u, fx1 - u):
                    r, g, b, a = px[x, y]
                    px[x, y] = (r, g, b, a * 2 // 3)
        img.paste(piece, (ou, ov))


def faces(u, v, w, h, d):
    """The six face rects (x0, y0, x1, y1) of a cuboid laid out the vanilla way, named."""
    return {
        "top": (u + d, v, u + d + w, v + d),
        "bottom": (u + d + w, v, u + d + w * 2, v + d),
        "right": (u, v + d, u + d, v + d + h),
        "front": (u + d, v + d, u + d + w, v + d + h),
        "left": (u + d + w, v + d, u + d * 2 + w, v + d + h),
        "back": (u + d * 2 + w, v + d, u + d * 2 + w * 2, v + d + h),
    }


def side_band(u, v, w, h, d):
    """The four side faces of a cuboid as one band (x0, y0, x1, y1): cracks wrap round it."""
    return (u, v + d, u + d * 2 + w * 2, v + d + h)


def all_rects():
    for name, cube in CUBES.items():
        for face, rect in faces(*cube).items():
            yield name, face, rect


def char():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    for name, face, (x0, y0, x1, y1) in all_rects():
        alpha = 190 if name == "head" and face == "front" else 225
        for y in range(y0, y1):
            for x in range(x0, x1):
                if rng.random() < 0.06:
                    c = ASH
                else:
                    c = rng.choice(CHAR)
                img.putpixel((x, y), c + (alpha,))
    # slim arms are a column narrower; their faces sit inside the classic ones, so they're covered too
    crust(img, True)
    save(img, "avatar_char.png")


def crack(img, band, x, y, length, step_down=True):
    x0, y0, x1, y1 = band
    for i in range(length):
        t = i / max(1, length)
        c = GOLD if t < 0.2 else ORANGE if t < 0.6 else RED
        if 0 <= x < 64 and 0 <= y < 64 and x0 <= x < x1 and y0 <= y < y1:
            old = img.getpixel((x, y))
            if old[3] == 0 or old[0] < c[0]:
                img.putpixel((x, y), c)
        y += 1 if step_down else -1
        if rng.random() < 0.45:
            x += rng.choice((-1, 1))
            if x < x0:
                x = x1 - 1  # wrap round the limb
            elif x >= x1:
                x = x0
        if rng.random() < 0.12:
            # a branch
            crack(img, band, x, y, max(2, (length - i) // 2), step_down)


def veins():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    for name, cube in CUBES.items():
        band = side_band(*cube)
        x0, y0, x1, y1 = band
        n = {"head": 3, "body": 7, "right_arm": 6, "left_arm": 6, "right_leg": 5, "left_leg": 5}[name]
        for _ in range(n):
            if name.endswith("arm"):
                # from the hand up toward the shoulder
                crack(img, band, rng.randrange(x0, x1), y1 - 1, rng.randrange(6, 12), step_down=False)
            elif name.endswith("leg"):
                # up from the feet
                crack(img, band, rng.randrange(x0, x1), y1 - 1, rng.randrange(4, 9), step_down=False)
            else:
                crack(img, band, rng.randrange(x0, x1), y0, rng.randrange(4, 10))
        top = faces(*cube)["top"]
        for _ in range(2 if name != "head" else 0):
            img.putpixel((rng.randrange(top[0], top[2]), rng.randrange(top[1], top[3])), RED)
    # the molten heart: the middle of the chest, white at its core
    fx0, fy0 = faces(*CUBES["body"])["front"][:2]
    for dx in range(-2, 2):
        for dy in range(-1, 3):
            x, y = fx0 + 4 + dx, fy0 + 3 + dy
            core = dx in (-1, 0) and dy in (0, 1)
            img.putpixel((x, y), WHITE if core else GOLD)
    for x, y in ((fx0 + 1, fy0 + 3), (fx0 + 6, fy0 + 4), (fx0 + 4, fy0 + 7), (fx0 + 2, fy0 + 6)):
        img.putpixel((x, y), ORANGE)
    # the hands: white-gold, the fire held in them
    for arm in ("right_arm", "left_arm"):
        u, v, w, h, d = CUBES[arm]
        bx0, by0, bx1, by1 = faces(u, v, w, h, d)["bottom"]
        for x in range(bx0, bx1):
            for y in range(by0, by1):
                img.putpixel((x, y), WHITE if rng.random() < 0.5 else GOLD)
        sx0, sy0, sx1, sy1 = side_band(u, v, w, h, d)
        for x in range(sx0, sx1):
            img.putpixel((x, sy1 - 1), GOLD if rng.random() < 0.7 else WHITE)
            img.putpixel((x, sy1 - 2), ORANGE if rng.random() < 0.6 else GOLD)
    # the crown of the head burns
    tx0, ty0, tx1, ty1 = faces(*CUBES["head"])["top"]
    for x in range(tx0, tx1):
        for y in range(ty0, ty1):
            r = rng.random()
            img.putpixel((x, y), WHITE if r < 0.2 else GOLD if r < 0.6 else ORANGE)
    hx0, hy0 = faces(*CUBES["head"])["front"][:2]
    for x in range(hx0, hx0 + 8):
        # the hairline smoulders
        if rng.random() < 0.7:
            img.putpixel((x, hy0), ORANGE if rng.random() < 0.6 else GOLD)
    # ember eyes, white-hot
    for x in (9, 10, 13, 14):
        img.putpixel((x, 12), WHITE)
    crust(img, False)
    save(img, "avatar_veins.png")


char()
veins()
print("Flame Avatar looks written to", os.path.normpath(OUT))
