"""Heritage looks (Kingdoms 0.7.79), in the Emberborn's style (gen_ember_look.py): overlays on the 64x64 player skin
layout for Stoneborn, Tideborn, Sylvan, Verdant, Frostborn, Nightborn and Earthling, as Andy approved them from the
mockups in the art overhaul project (art-overhaul/lineages/).

<key>_skin.png - marks lit like skin (stone, scales, gills, bark, patches, rime, shadow, wraps)
<key>_glow.png - marks drawn emissive over them, so they shine in the dark
<key>_eyes.png - the stock eye spots in the heritage's eye colour (the fallback; each skin gets its own eyes in code)
Sylvan's vine winds its own way on each player (4 drawings, sylvan_<n>), Tideborn's gills come in 3 sets with their
fins (tideborn_<n>); fins.png and fins_glow.png are the Tideborn fins' model texture (client HeritageLooks).
Nothing is copied; colours are vanilla's own ramps. Run from the project root:  python -I tools/gen_heritage_looks.py
"""
import os
import random
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "kingdoms", "src", "main", "resources", "assets",
                   "jaymoskingdoms", "textures", "entity", "heritage")
os.makedirs(OUT, exist_ok=True)

# the arms' side faces: (x0, x1) across all four sides, from the arm's top row (classic and slim arms both)
ARMS = ((40, 56, 20), (32, 48, 52))
# the hands' bottom faces (classic; slim's is a column to the left, so both get it)
HANDS = ((47, 16, 52, 20), (39, 48, 44, 52))
# stock eye spots
EYES = ((9, 12), (10, 12), (13, 12), (14, 12))
# the Verdant forearm patches' colours (the Frostborn share them): by day, and glowing
PINK_DAY = (196, 84, 146, 255)
PINK = [(226, 92, 166, 255), (160, 50, 120, 255)]


def blank():
    return Image.new("RGBA", (64, 64), (0, 0, 0, 0))


def put(img, x, y, c):
    if 0 <= x < 64 and 0 <= y < 64:
        img.putpixel((x, y), c)


def arm_pixels(fn):
    """fn(x, y, rows up from the hand, x0, x1) for every pixel of both arms' side faces."""
    for x0, x1, y0 in ARMS:
        for y in range(y0, y0 + 12):
            for x in range(x0, x1):
                fn(x, y, y0 + 11 - y, x0, x1)


def hands(fn):
    for x0, y0, x1, y1 in HANDS:
        for x in range(x0, x1):
            for y in range(y0, y1):
                fn(x, y)


def patches(solid, glow):
    """The Verdant's forearm patches: pink by day, glowing in the dark."""
    for x0, x1, y0 in ARMS:
        for y in range(y0 + 3, y0 + 9):
            for x in range(x0, x1):
                if (x - x0) % 4 in (1, 2) and not (y in (y0 + 3, y0 + 8) and (x - x0) % 4 == 2):
                    put(solid, x, y, PINK_DAY)
                    put(glow, x, y, PINK[0] if y0 + 4 <= y <= y0 + 7 else PINK[1])


def stoneborn(rng, solid, glow):
    GREY = [(125, 125, 125, 255), (104, 104, 104, 255), (86, 86, 86, 255)]  # stone
    AM = [(230, 196, 255, 255), (177, 123, 235, 255), (124, 72, 186, 255)]  # amethyst

    def stone(x, y, r, x0, x1):  # stone creeps up from the hand, ragged at the top
        if r < 5 or (r < 7 and rng.random() < 0.55):
            put(solid, x, y, rng.choice(GREY))
    arm_pixels(stone)
    for x0, x1, y0 in ARMS:
        for _ in range(4):  # amethyst seams in the cracks, brightest at the knuckles
            x, y = rng.randrange(x0, x1), y0 + 11
            for i in range(rng.randrange(4, 8)):
                put(glow, x, y, AM[0] if i < 2 else AM[1] if i < 4 else AM[2])
                y -= 1
                if rng.random() < 0.5:
                    x = min(x1 - 1, max(x0, x + rng.choice((-1, 1))))

    def hand(x, y):
        put(solid, x, y, rng.choice(GREY))
        if rng.random() < 0.4:
            put(glow, x, y, AM[1])
    hands(hand)


def tideborn(rng, solid, glow, variant):
    SCALE = [(58, 116, 112, 255), (44, 94, 96, 255)]
    CYAN = [(120, 255, 236, 255), (60, 205, 200, 255), (30, 130, 150, 255)]  # glow squid, prismarine
    GILL = (34, 70, 76, 255)

    def scales(x, y, r, x0, x1):
        if r < 7 and (x + y) % 2 == 0 and rng.random() < (0.9 - r * 0.1):
            put(solid, x, y, rng.choice(SCALE))
        if r in (3, 6) and (x - x0) % 2 == (r // 3) % 2:
            put(glow, x, y, CYAN[1])  # two rows of deep-sea lights
        if r == 0 and rng.random() < 0.7:
            put(glow, x, y, CYAN[0])  # glowing fingertips
    arm_pixels(scales)
    hands(lambda x, y: put(glow, x, y, CYAN[rng.randrange(2)]))
    # gills: slits on both cheeks and the sides of the head, below the eyes (three on the third set)
    for i in range((2, 2, 3)[variant]):
        y = 13 + i
        for x in (8, 15, 2, 3, 4, 5, 18, 19, 20, 21):
            put(solid, x, y, GILL)
            put(glow, x, y, CYAN[2])


def sylvan(rng, solid, glow):
    """A thick vine winding round each arm its own way, leaves budding off it, up over the shoulder."""
    STEM = [(70, 112, 34, 255), (52, 86, 26, 255)]
    VINE = [(170, 214, 66, 255), (110, 160, 36, 255)]
    LEAF_DAY, LEAF = (120, 176, 52, 255), (236, 248, 130, 255)
    for x0, x1, y0 in ARMS:
        w = x1 - x0
        x = 4 + rng.randrange(4)  # starts on the back of the hand
        step = rng.choice((1, -1))
        for y in range(y0 + 11, y0 - 1, -1):
            for dx in (0, 1):
                put(solid, x0 + (x + dx) % w, y, STEM[dx])
                put(glow, x0 + (x + dx) % w, y, VINE[dx])
            if rng.random() < 0.35:
                lx = x0 + (x + (2 if step > 0 else -1)) % w
                put(solid, lx, y, LEAF_DAY)
                put(glow, lx, y, LEAF)
            x += step * rng.choice((1, 1, 2, 0))
            if rng.random() < 0.15:
                step = -step  # it wanders back on itself now and then
        for x in range(x0 + 4, x0 + 8):  # over the top of the shoulder
            if rng.random() < 0.6:
                put(solid, x, y0 - 4 + rng.randrange(4), STEM[0])


def verdant(rng, solid, glow):
    patches(solid, glow)


def frostborn(rng, solid, glow):
    RIME = [(232, 244, 255, 255), (206, 226, 246, 255)]
    ICE = [(205, 236, 255, 255), (130, 186, 250, 255)]  # packed ice, blue ice

    def rime(x, y, r, x0, x1):  # frosted fingers
        if r < 2 or (r < 3 and rng.random() < 0.5):
            put(solid, x, y, rng.choice(RIME))
    arm_pixels(rime)
    patches(solid, glow)

    def hand(x, y):
        put(solid, x, y, RIME[0])
        put(glow, x, y, ICE[rng.randrange(2)])
    hands(hand)


def nightborn(rng, solid, glow):
    INK = [(18, 14, 28, 255), (34, 26, 50, 255), (52, 42, 72, 255)]
    STAR = [(240, 242, 255, 255), (170, 176, 230, 255)]

    def shadow(x, y, r, x0, x1):  # shadow soaks up from the fingers, thinning toward the elbow; stars in it
        if r < 4 or (r < 9 and rng.random() < 1.0 - (r - 3) * 0.17):
            put(solid, x, y, INK[min(2, r // 3)])
            if rng.random() < 0.09:
                put(glow, x, y, rng.choice(STAR))
    arm_pixels(shadow)
    hands(lambda x, y: put(solid, x, y, INK[0]))


def earthling(rng, solid, glow):
    WRAP = [(238, 234, 222, 255), (212, 206, 190, 255)]
    KI = [(240, 250, 255, 255), (170, 214, 255, 255)]

    def wraps(x, y, r, x0, x1):  # training wraps on the fists and wrists, a ki glow round the knuckles
        if r in (1, 2, 3, 4) and not (r == 4 and (x - x0) % 3 == 2):
            put(solid, x, y, WRAP[(x + y) % 2])
        if r == 0:
            put(glow, x, y, KI[(x - x0) % 2])
        elif r == 1 and rng.random() < 0.35:
            put(glow, x, y, KI[1])
    arm_pixels(wraps)

    def hand(x, y):
        put(solid, x, y, WRAP[0])
        put(glow, x, y, KI[1])
    hands(hand)


def write(name, fn, *args):
    solid, glow = blank(), blank()
    fn(random.Random(sum(map(ord, name)) * 31), solid, glow, *args)
    solid.save(os.path.join(OUT, name + "_skin.png"))
    glow.save(os.path.join(OUT, name + "_glow.png"))


# eye colours (the stock spots; client HeritageLooks has the same colours for each skin's own eyes)
EYE_COLOURS = {"stoneborn": (214, 170, 255), "tideborn": (110, 250, 235), "sylvan": (214, 245, 150),
               "frostborn": (200, 236, 255), "nightborn": (236, 238, 255)}

write("stoneborn", stoneborn)
for v in range(3):
    write("tideborn_%d" % v, tideborn, v)
for v in range(4):
    write("sylvan_%d" % v, sylvan)
write("verdant", verdant)
write("frostborn", frostborn)
write("nightborn", nightborn)
write("earthling", earthling)
for key, c in EYE_COLOURS.items():
    img = blank()
    for x, y in EYES:
        img.putpixel((x, y), c + (255,))
    img.save(os.path.join(OUT, key + "_eyes.png"))

# ---- Tideborn fins: flat (zero-width) boxes, so each shows two faces side by side, depth x height each, under a
# depth-tall strip (the box's top and bottom, empty). (u, v, depth, height) as HeritageLooks lays them out.
FINS = {"crest": (0, 0, 6, 4), "ear": (0, 12, 4, 5), "arm": (16, 12, 3, 6), "arm_long": (24, 0, 3, 8)}
MEMBRANE, SPINE, EDGE = (72, 156, 150, 255), (40, 100, 104, 255), (112, 196, 186, 255)
EDGE_GLOW = (40, 150, 150, 255)


def fin_shape(kind, d, h):
    """The fin's pixels (column across its depth, row down): lozenges, the same both ways round, so each face reads
    right whichever way the box's faces map."""
    cells = set()
    for r in range(h):
        if kind == "crest":  # a peak in the middle, widening to the head
            half = min(d // 2, 1 + r)
            cols = range(d // 2 - half, d // 2 + half)
        else:  # narrow at both ends
            cols = range(1, d - 1) if (r == 0 or r == h - 1) and d > 2 else range(d)
        for c in cols:
            cells.add((c, r))
    return cells


fins, glow = Image.new("RGBA", (32, 32), (0, 0, 0, 0)), Image.new("RGBA", (32, 32), (0, 0, 0, 0))
for kind, (u, v, d, h) in FINS.items():
    cells = fin_shape(kind, d, h)
    for face in (0, 1):
        ox, oy = u + face * d, v + d
        for c, r in cells:
            edge = any((c + dc, r + dr) not in cells for dc, dr in ((1, 0), (-1, 0), (0, -1), (0, 1))
                       if 0 <= c + dc < d and 0 <= r + dr < h) or r == 0
            fins.putpixel((ox + c, oy + r), EDGE if edge else SPINE if c % 2 == 0 else MEMBRANE)
            if edge:
                glow.putpixel((ox + c, oy + r), EDGE_GLOW)
fins.save(os.path.join(OUT, "fins.png"))
glow.save(os.path.join(OUT, "fins_glow.png"))
print("Heritage looks written to", os.path.normpath(OUT))
