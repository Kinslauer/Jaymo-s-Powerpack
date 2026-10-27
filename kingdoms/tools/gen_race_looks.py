"""Race looks (Kingdoms 0.7.262, Andy's NOEA picks): the Divine-born, the Bio-built and the Rubber-folk, in the same way as
tools/gen_heritage_looks.py in the project root (overlays on the 64x64 player skin layout; <name>_skin.png lit like skin,
<name>_glow.png drawn emissive over it, <name>_eyes.png the stock eye spots in the heritage's eye colour).

  Divine-born   divine_*            gold filigree up the forearms and a gem on the brow; halo.png is the ring over the head
  Bio-built     biobuilt_0..2_*     steel seams and amber nodes, then plating to the elbow, then to the shoulder and the cheeks
                biobuilt_plates_1/2 the second and third form's plates alone, laid over a variant's own marks
  Rubber-folk   rubberfolk_*        glossy highlights over the pink skin (the pink is code: HeritageVariants#skin)
  variants      variant/<key>_*     starlit, seraphic, brassbound, opaline, candystripe, berrygloss

Everything is our own: nothing is copied, colours are plain ramps. Run from the kingdoms folder:  python -I tools/gen_race_looks.py
"""
import os
import random
from PIL import Image

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "jaymoskingdoms",
                   "textures", "entity", "heritage")
VOUT = os.path.join(OUT, "variant")
os.makedirs(VOUT, exist_ok=True)

# the arms' side faces: (x0, x1, y0) across all four sides, from the arm's top row (classic and slim arms both)
ARMS = ((40, 56, 20), (32, 48, 52))
# the hands' bottom faces (classic; slim's is a column to the left, so both get it)
HANDS = ((47, 16, 52, 20), (39, 48, 44, 52))
# stock eye spots
EYES = ((9, 12), (10, 12), (13, 12), (14, 12))


def blank():
    return Image.new("RGBA", (64, 64), (0, 0, 0, 0))


def put(img, x, y, c):
    if 0 <= x < 64 and 0 <= y < 64:
        img.putpixel((x, y), c)


def arm_pixels(fn):
    """fn(x, y, rows up from the hand, column across the four sides, x0, x1) for every pixel of both arms' side faces."""
    for x0, x1, y0 in ARMS:
        for y in range(y0, y0 + 12):
            for x in range(x0, x1):
                fn(x, y, y0 + 11 - y, x - x0, x0, x1)


def hands(fn):
    for x0, y0, x1, y1 in HANDS:
        for x in range(x0, x1):
            for y in range(y0, y1):
                fn(x, y)


def save(name, solid, glow, folder=OUT):
    solid.save(os.path.join(folder, name + "_skin.png"))
    glow.save(os.path.join(folder, name + "_glow.png"))


def eyes(name, colour, folder=OUT):
    img = blank()
    for x, y in EYES:
        img.putpixel((x, y), tuple(colour) + (255,))
    img.save(os.path.join(folder, name + "_eyes.png"))


# ---- Divine-born ---------------------------------------------------------------------------------------------------------

GOLD = [(255, 226, 138, 255), (232, 190, 90, 255), (196, 150, 60, 255)]
GOLD_GLOW = [(255, 244, 190, 255), (255, 226, 140, 255)]


def divine(rng, solid, glow):
    """Gold filigree: a fine line up each forearm that curls and sprouts, brightest at the knuckles; a gem on the brow."""
    for x0, x1, y0 in ARMS:
        w = x1 - x0
        for start in (1, 5, 9, 13):  # one thread on each side of the arm
            x = start
            for r in range(0, 9):
                y = y0 + 11 - r
                put(solid, x0 + x % w, y, GOLD[1] if r % 3 else GOLD[0])
                put(glow, x0 + x % w, y, GOLD_GLOW[1] if r < 5 else GOLD_GLOW[0])
                if r % 4 == 2:  # a curl to one side
                    side = rng.choice((-1, 1))
                    put(solid, x0 + (x + side) % w, y, GOLD[2])
                    put(glow, x0 + (x + side) % w, y, GOLD_GLOW[1])
                x += rng.choice((0, 0, 1, -1))
    hands(lambda x, y: (put(solid, x, y, GOLD[1]), put(glow, x, y, GOLD_GLOW[1]) if rng.random() < 0.5 else None))
    # the gem on the brow (the head's front face, above the eyes)
    for x, y in ((11, 9), (12, 9), (11, 10), (12, 10)):
        put(solid, x, y, GOLD[0])
        put(glow, x, y, GOLD_GLOW[0])
    for x, y in ((10, 9), (13, 9), (11, 8), (12, 8)):
        put(solid, x, y, GOLD[2])
        put(glow, x, y, GOLD_GLOW[1])


def halo_texture():
    """A solid gold ring: brightest on top, the model's bars all sample inside it."""
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    for y in range(32):
        for x in range(32):
            t = (x + y) % 5
            img.putpixel((x, y), ((255, 236, 150, 255), (255, 224, 120, 255), (255, 244, 190, 255), (255, 230, 130, 255), (250, 214, 100, 255))[t])
    return img


# ---- Bio-built -----------------------------------------------------------------------------------------------------------

STEEL = [(132, 154, 148, 255), (104, 128, 124, 255), (78, 100, 98, 255)]
SEAM = (44, 62, 64, 255)
AMBER = [(255, 214, 120, 255), (240, 160, 50, 255), (170, 96, 30, 255)]


def bio_seams(rng, solid, glow, nodes):
    """The first form: dark seams ringing the forearm every few rows, and a few amber nodes lit in them."""
    def seam(x, y, r, col, x0, x1):
        if r in (3, 7):
            put(solid, x, y, SEAM)
            if col % 8 == 2 and nodes:
                put(glow, x, y, AMBER[1])
        elif col % 8 == 0 and r in (1, 5):
            put(solid, x, y, SEAM)
    arm_pixels(seam)
    hands(lambda x, y: put(solid, x, y, SEAM) if (x + y) % 3 == 0 else None)


def plate(rng, solid, glow, rows, lit):
    """Steel plates over the arm's lowest {rows} rows, each 4x3 with a dark rivet in a corner and an amber node on some."""
    def plates(x, y, r, col, x0, x1):
        if r >= rows:
            return
        cell_r, cell_c = r % 4, col % 4
        if cell_r == 3 or cell_c == 3:
            put(solid, x, y, SEAM)  # the gaps between plates
            return
        put(solid, x, y, STEEL[0] if cell_r == 0 else STEEL[1] if cell_r == 1 else STEEL[2])
        if cell_r == 0 and cell_c == 0:
            put(solid, x, y, STEEL[2])  # rivet
        if lit and cell_r == 1 and cell_c == 1 and (col // 4 + r // 4) % 2 == 0:
            put(glow, x, y, AMBER[0] if lit > 1 else AMBER[1])
    arm_pixels(plates)
    hands(lambda x, y: put(solid, x, y, STEEL[1]) if (x + y) % 2 == 0 else None)


def cheeks(rng, solid, glow):
    """Plates on the cheeks (the head's front face sides) with a node each, for the third form."""
    for x in (8, 15):
        for y in (13, 14, 15):
            put(solid, x, y, STEEL[y - 13])
    put(glow, 8, 14, AMBER[0])
    put(glow, 15, 14, AMBER[0])


def biobuilt(rng, solid, glow, form):
    if form == 0:
        bio_seams(rng, solid, glow, True)
    elif form == 1:
        bio_seams(rng, solid, glow, False)
        plate(rng, solid, glow, 6, 1)
    else:
        plate(rng, solid, glow, 12, 2)
        cheeks(rng, solid, glow)


def plates_only(rng, solid, glow, form):
    """What a variant's marks wear over them for form {form} (1, 2): the plates alone, without the seams."""
    if form == 1:
        plate(rng, solid, glow, 6, 1)
    else:
        plate(rng, solid, glow, 12, 2)
        cheeks(rng, solid, glow)


# ---- Rubber-folk ---------------------------------------------------------------------------------------------------------

SHINE = [(255, 244, 250, 255), (255, 214, 232, 255)]


def rubberfolk(rng, solid, glow):
    """A glossy streak down each forearm's front and back, a shine on every knuckle: wet-looking rubber."""
    def streak(x, y, r, col, x0, x1):
        if col in (3, 4, 11, 12) and 1 <= r <= 9:
            put(solid, x, y, SHINE[0] if col in (3, 11) and r % 4 != 0 else SHINE[1])
    arm_pixels(streak)
    hands(lambda x, y: put(solid, x, y, SHINE[1]) if (x * 3 + y) % 4 == 0 else None)
    # a gloss on the brow and the cheek
    for x, y in ((9, 9), (10, 9), (14, 14)):
        put(solid, x, y, SHINE[0])


# ---- the variants --------------------------------------------------------------------------------------------------------

def starlit(rng, solid, glow):
    """Pale-blue stars scattered up the forearms, some with a cross of light, and a silver thread between."""
    STAR = [(214, 232, 255, 255), (150, 190, 250, 255)]
    for x0, x1, y0 in ARMS:
        for _ in range(11):
            x, y = rng.randrange(x0, x1), rng.randrange(y0 + 2, y0 + 12)
            put(solid, x, y, STAR[1])
            put(glow, x, y, STAR[0])
            if rng.random() < 0.35:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    put(glow, x + dx, y + dy, (170, 200, 255, 255))
    hands(lambda x, y: put(glow, x, y, STAR[0]) if rng.random() < 0.4 else None)


def seraphic(rng, solid, glow):
    """White feathers: rows of chevrons down the forearm, gold at the tips."""
    WHITE = [(252, 250, 244, 255), (232, 226, 214, 255)]
    TIP = (240, 200, 110, 255)

    def feathers(x, y, r, col, x0, x1):
        if r < 2 or r > 10:
            return
        d = abs((col % 4) - 2)  # a V
        if (r + d) % 3 == 0:
            put(solid, x, y, WHITE[0] if d < 2 else WHITE[1])
        if (r + d) % 3 == 0 and d == 2:
            put(solid, x, y, TIP)
            put(glow, x, y, (255, 226, 150, 255))
    arm_pixels(feathers)
    hands(lambda x, y: put(solid, x, y, WHITE[1]))


def brassbound(rng, solid, glow):
    """Copper plates with rivets, amber nodes, and a gear-tooth edge at the wrist."""
    COP = [(196, 128, 74, 255), (164, 100, 56, 255), (122, 72, 40, 255)]

    def brass(x, y, r, col, x0, x1):
        if r > 7:
            return
        cell_r, cell_c = r % 4, col % 4
        if cell_r == 3 or cell_c == 3:
            put(solid, x, y, (60, 38, 26, 255))
            return
        put(solid, x, y, COP[cell_r % 3])
        if cell_r == 0 and cell_c == 0:
            put(solid, x, y, COP[2])
        if cell_r == 1 and cell_c == 1 and (col // 4 + r // 4) % 2 == 1:
            put(glow, x, y, AMBER[1])
        if r == 7 and col % 2 == 0:
            put(solid, x, y - 1, COP[0])  # the teeth
    arm_pixels(brass)
    hands(lambda x, y: put(solid, x, y, COP[1]))


def opaline(rng, solid, glow):
    """Pearly plates in shifting pearl, mint and lilac, edged in a fine light."""
    PEARL = [(224, 236, 232, 255), (200, 224, 226, 255), (222, 208, 234, 255), (206, 236, 220, 255)]
    EDGE = (150, 196, 190, 255)

    def pearl(x, y, r, col, x0, x1):
        if r > 9:
            return
        cell_r, cell_c = r % 5, col % 5
        if cell_r == 4 or cell_c == 4:
            put(solid, x, y, EDGE)
            if (x + y) % 3 == 0:
                put(glow, x, y, (170, 240, 220, 255))
            return
        put(solid, x, y, PEARL[(col // 5 + r // 5 + cell_r // 2) % 4])
    arm_pixels(pearl)
    hands(lambda x, y: put(solid, x, y, PEARL[(x + y) % 4]))


def candystripe(rng, solid, glow):
    """Cream stripes slanting round the forearm, a white gloss at the knuckles."""
    CREAM, ROSE = (255, 246, 244, 255), (255, 190, 214, 255)

    def stripe(x, y, r, col, x0, x1):
        if r < 1 or r > 10:
            return
        d = (col + r) % 6
        if d < 2:
            put(solid, x, y, CREAM)
        elif d == 3 and r % 2 == 0:
            put(solid, x, y, ROSE)
    arm_pixels(stripe)
    hands(lambda x, y: put(solid, x, y, CREAM) if (x + y) % 3 == 0 else None)


def berrygloss(rng, solid, glow):
    """Deep berry spots with a bright gloss on each, scattered over the arms."""
    DEEP, GLOSS = (112, 28, 78, 255), (255, 200, 232, 255)
    for x0, x1, y0 in ARMS:
        for _ in range(14):
            x, y = rng.randrange(x0, x1), rng.randrange(y0 + 1, y0 + 12)
            put(solid, x, y, DEEP)
            put(solid, x + 1, y, DEEP)
            put(solid, x, y + 1, DEEP)
            put(solid, x, y, GLOSS if rng.random() < 0.5 else DEEP)
    hands(lambda x, y: put(solid, x, y, DEEP) if (x + 2 * y) % 5 == 0 else None)


# ---- write everything ----------------------------------------------------------------------------------------------------

def make(fn, *args, seed):
    solid, glow = blank(), blank()
    fn(random.Random(seed), solid, glow, *args)
    return solid, glow


s, g = make(divine, seed=1)
save("divine", s, g)
eyes("divine", (255, 244, 208))
halo_texture().save(os.path.join(OUT, "halo.png"))

for form in range(3):
    s, g = make(biobuilt, form, seed=10 + form)
    save("biobuilt_%d" % form, s, g)
eyes("biobuilt", (255, 180, 84))
for form in (1, 2):
    s, g = make(plates_only, form, seed=20 + form)
    save("biobuilt_plates_%d" % form, s, g)

s, g = make(rubberfolk, seed=30)
save("rubberfolk", s, g)

VARIANTS = {"starlit": (starlit, (207, 224, 255)), "seraphic": (seraphic, (255, 244, 208)), "brassbound": (brassbound, (255, 176, 80)),
            "opaline": (opaline, (232, 255, 248)), "candystripe": (candystripe, None), "berrygloss": (berrygloss, None)}
for key, (fn, eye) in VARIANTS.items():
    s, g = make(fn, seed=sum(map(ord, key)) * 31)
    save(key, s, g, VOUT)
    if eye:
        eyes(key, eye, VOUT)
print("Race looks written to", os.path.normpath(OUT))
