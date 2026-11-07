"""Heritage looks (Kingdoms 0.7.79), in the Emberborn's style (gen_ember_look.py): overlays on the 64x64 player skin
layout for Stoneborn, Tideborn, Sylvan, Verdant, Frostborn, Nightborn and Earthling, as Andy approved them from the
mockups in the art overhaul project (art-overhaul/lineages/).

<key>_skin.png - marks lit like skin (stone, scales, gills, bark, patches, rime, shadow, wraps)
<key>_glow.png - marks drawn emissive over them, so they shine in the dark
<key>_eyes.png - the stock eye spots in the heritage's eye colour (the fallback; each skin gets its own eyes in code)
nightborn_blood_* (0.7.88) - a Nightborn vampire's: the shadow deep red, the eyes red-silver at night
Sylvan's vine winds its own way on each player (4 drawings, sylvan_<n>), Tideborn's gills come in 3 sets with their
fins (tideborn_<n>); fins.png and fins_glow.png are the Tideborn fins' model texture (client HeritageLooks).
(Kingdoms 0.7.97) Stormborn, Dawnborn, Endborn, Dragonblood and Deepborn, from the five new heritages Andy picked.
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


def nightborn(rng, solid, glow, blood=False):
    INK = [(18, 14, 28, 255), (34, 26, 50, 255), (52, 42, 72, 255)]
    STAR = [(240, 242, 255, 255), (170, 176, 230, 255)]
    if blood:  # (0.7.88) a Nightborn vampire's: the same shadow gone deep red, its stars blood-bright
        INK = [(30, 4, 8, 255), (58, 8, 14, 255), (86, 16, 22, 255)]
        STAR = [(255, 196, 196, 255), (222, 64, 64, 255)]

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


def stormborn(rng, solid, glow):
    """Forked lightning scars climbing the forearms from the hand: pale scar by day, pale blue light."""
    SCAR = [(206, 218, 232, 255), (176, 192, 212, 255)]
    BOLT = [(214, 240, 255, 255), (130, 190, 255, 255)]
    for x0, x1, y0 in ARMS:
        w = x1 - x0
        for start in (1 + rng.randrange(3), 9 + rng.randrange(3)):  # two bolts round each arm
            x, r = start, 0
            while r < 10:
                y = y0 + 11 - r
                put(solid, x0 + x % w, y, SCAR[r % 2])
                put(glow, x0 + x % w, y, BOLT[0] if r < 4 else BOLT[1])
                if r in (3, 6) and rng.random() < 0.7:  # a fork off to the side
                    fx = x + rng.choice((-1, 1))
                    for f in range(1, 3):
                        put(solid, x0 + (fx + (f - 1) * (fx - x)) % w, y - f, SCAR[1])
                        put(glow, x0 + (fx + (f - 1) * (fx - x)) % w, y - f, BOLT[1])
                x += rng.choice((-1, 0, 1, 1)) if rng.random() < 0.6 else 0
                r += 1

    def hand(x, y):
        if rng.random() < 0.3:
            put(glow, x, y, BOLT[1])
    hands(hand)


def dawnborn(rng, solid, glow):
    """A gold sun-ring on the back of each hand, short rays above it."""
    GOLD = [(232, 180, 56, 255), (196, 140, 36, 255)]
    SUN = [(255, 226, 120, 255), (250, 190, 70, 255)]
    RING = [".##.", "#..#", "#..#", ".##."]
    for x0, x1, y0 in ARMS:
        for row, line in enumerate(RING):
            y = y0 + 11 - 4 + row  # rows 4 up to 1 above the hand
            for col, ch in enumerate(line):
                if ch == "#":
                    put(solid, x0 + 4 + col, y, GOLD[row % 2])
                    put(glow, x0 + 4 + col, y, SUN[0])
        for col in (4, 7):  # rays from the ring's top corners, and one up the middle
            put(solid, x0 + col, y0 + 11 - 5, GOLD[1])
            put(glow, x0 + col, y0 + 11 - 5, SUN[1])
        put(glow, x0 + 5, y0 + 11 - 6, SUN[1])
        put(glow, x0 + 6, y0 + 11 - 6, SUN[1])


def endborn(rng, solid, glow):
    """End-stone dark specks scattered up the forearms, some alight in the End's purple."""
    SPECK = [(40, 20, 56, 255), (64, 34, 88, 255)]
    LIGHT = [(214, 140, 255, 255), (160, 90, 230, 255)]

    def specks(x, y, r, x0, x1):
        if rng.random() < max(0.0, 0.42 - r * 0.045):
            put(solid, x, y, rng.choice(SPECK))
            if rng.random() < 0.45:
                put(glow, x, y, LIGHT[0] if r < 3 else LIGHT[1])
    arm_pixels(specks)
    hands(lambda x, y: put(solid, x, y, SPECK[0]))


def dragonblood(rng, solid, glow):
    """Dark scale patches on the forearms and the right cheek; their edges glow like embers in the dark."""
    SCALE = [(96, 34, 28, 255), (72, 24, 22, 255), (124, 48, 34, 255)]
    EMBER = [(255, 150, 50, 255), (220, 90, 24, 255)]

    def scales(x, y, r, x0, x1):
        if r < 6 or (r < 8 and rng.random() < 0.5):
            put(solid, x, y, SCALE[(x // 2 + y) % 2] if (x + r) % 2 == 0 else SCALE[2])
            if (x + y) % 3 == 0 and rng.random() < 0.6:
                put(glow, x, y, EMBER[0] if r < 3 else EMBER[1])
    arm_pixels(scales)
    hands(lambda x, y: put(solid, x, y, SCALE[1]))
    for x, y in ((8, 13), (9, 13), (8, 14), (9, 14), (10, 14), (9, 15)):  # the cheek
        put(solid, x, y, SCALE[(x + y) % 2])
    put(glow, 9, 14, EMBER[1])


def deepborn(rng, solid, glow):
    """Sculk veins branching up the forearms from the fingertips, dark teal, alight in the deep dark's cyan."""
    VEIN = [(14, 52, 60, 255), (8, 36, 44, 255)]
    PULSE = [(70, 230, 210, 255), (24, 150, 160, 255)]
    for x0, x1, y0 in ARMS:
        w = x1 - x0
        for start in (2, 6, 10, 14):
            x = start + rng.randrange(2)
            top = 6 + rng.randrange(4)
            for r in range(top):
                y = y0 + 11 - r
                put(solid, x0 + x % w, y, VEIN[r % 2])
                put(glow, x0 + x % w, y, PULSE[0] if r < 2 or r == top - 1 else PULSE[1])
                if rng.random() < 0.35:
                    x += rng.choice((-1, 1))

    def hand(x, y):
        put(solid, x, y, VEIN[1])
        if rng.random() < 0.5:
            put(glow, x, y, PULSE[1])
    hands(hand)


# ---- (Kingdoms 0.7.490) Beastfolk and Hollow, and the family's variants. Painted on the same UVs as the rest, so every mark is
# flat on the 64x64 skin. (0.7.490, later) The ears, tails, wings and legs that used to be painted here are real model parts now
# (client BeastParts, textures from tools/gen_heritage_parts.py); the skins keep the fur, the faces and the glow.
# Head: front (8-16, 8-16, eyes on row 12), top (8-16, 0-8, its front edge the bottom), sides (0-8 / 16-24, 8-16), back (24-32).
# Body: front (20-28, 20-32), back (32-40, 20-32). Legs' backs: (12-16, 20-32) and (28-32, 52-64).
HEAD_FRONT = (8, 8, 16, 16)
BODY_BACK = (32, 20, 40, 32)
LEG_BACKS = ((12, 20, 16, 32), (28, 52, 32, 64))


def rect(img, x0, y0, x1, y1, c):
    for x in range(x0, x1):
        for y in range(y0, y1):
            put(img, x, y, c)


def dots(img, pts, c):
    for x, y in pts:
        put(img, x, y, c)


def fur_arms(rng, solid, colours, reach=8, ragged=0.5):
    """Fur creeping up the forearms from the hand."""
    def fur(x, y, r, x0, x1):
        if r < reach - 3 or (r < reach and rng.random() < ragged):
            put(solid, x, y, rng.choice(colours))
    arm_pixels(fur)


def muzzle(solid, light, dark):
    dots(solid, ((10, 14), (11, 14), (12, 14), (13, 14), (10, 15), (11, 15), (12, 15), (13, 15)), light)
    dots(solid, ((11, 13), (12, 13)), dark)


def beastfolk(rng, solid, glow):
    """The plain beastfolk: short tawny fur up the forearms, dark claws and a nose (its ears and tail are model parts)."""
    FUR = [(166, 120, 72, 255), (146, 102, 60, 255), (184, 140, 88, 255)]
    CLAW = (52, 40, 34, 255)
    fur_arms(rng, solid, FUR)
    hands(lambda x, y: put(solid, x, y, CLAW if rng.random() < 0.6 else FUR[1]))
    muzzle(solid, (226, 200, 160, 255), (52, 36, 36, 255))
    # (0.7.490) the ears and the tail are model parts now (client BeastParts; tools/gen_heritage_parts.py)


def hollow_heritage(rng, solid, glow):
    """The Hollow: a drained, grey-blue pallor in patches up the forearms, dark rings round the eyes, a pale glow."""
    PALE = [(176, 186, 190, 255), (156, 168, 174, 255), (136, 148, 158, 255)]
    VEIN = (84, 96, 112, 255)
    RING = (60, 64, 80, 255)

    def drained(x, y, r, x0, x1):
        if r < 9 and (x + y) % 3 != 0 and rng.random() < 0.8:
            put(solid, x, y, rng.choice(PALE))
        if r in (2, 5) and (x - x0) % 4 == 1:
            put(solid, x, y, VEIN)
    arm_pixels(drained)
    hands(lambda x, y: put(solid, x, y, PALE[2]))
    dots(solid, ((9, 11), (10, 11), (9, 13), (10, 13), (13, 11), (14, 11), (13, 13), (14, 13), (8, 12), (11, 12), (12, 12), (15, 12)), RING)
    dots(solid, ((10, 15), (11, 15), (12, 15), (13, 15)), VEIN)
    for _ in range(5):  # a faint cold glow in the veins
        x, y = rng.randrange(40, 56), 20 + rng.randrange(4, 10)
        put(glow, x, y, (110, 190, 200, 255))


def write(name, fn, *args, seed=None):
    solid, glow = blank(), blank()
    fn(random.Random(sum(map(ord, seed or name)) * 31), solid, glow, *args)
    solid.save(os.path.join(OUT, name + "_skin.png"))
    glow.save(os.path.join(OUT, name + "_glow.png"))


# eye colours (the stock spots; client HeritageLooks has the same colours for each skin's own eyes)
EYE_COLOURS = {"stoneborn": (214, 170, 255), "tideborn": (110, 250, 235), "sylvan": (214, 245, 150),
               "frostborn": (200, 236, 255), "nightborn": (236, 238, 255), "nightborn_blood": (255, 184, 192),
               "dawnborn": (255, 210, 122), "endborn": (214, 156, 255), "dragonblood": (255, 176, 64),
               "deepborn": (184, 244, 236), "stormborn": (196, 228, 255),
               "beastfolk": (255, 210, 122), "hollow": (190, 235, 220)}

write("stoneborn", stoneborn)
for v in range(3):
    write("tideborn_%d" % v, tideborn, v)
for v in range(4):
    write("sylvan_%d" % v, sylvan)
write("verdant", verdant)
write("frostborn", frostborn)
write("nightborn", nightborn)
write("nightborn_blood", nightborn, True, seed="nightborn")  # the same shadow, so the stars stay put
write("earthling", earthling)
write("stormborn", stormborn)
write("dawnborn", dawnborn)
write("endborn", endborn)
write("dragonblood", dragonblood)
write("deepborn", deepborn)
write("beastfolk", beastfolk)  # (0.7.490)
write("hollow", hollow_heritage)  # (0.7.490) the heritage; hollow in variant/ is the Deepborn's
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

# ---- (Kingdoms 0.7.214) heritage variants: two more looks for every heritage (HeritageVariants), each its own marks,
# eyes and (Tideborn) fins. Their skin tints are in code (MCA's skin layer). variant/<key>_skin|_glow|_eyes.png
VOUT = os.path.join(OUT, "variant")
os.makedirs(VOUT, exist_ok=True)
CHEEKS = ((8, 13), (9, 13), (8, 14), (9, 14), (14, 13), (15, 13), (14, 14), (15, 14))


def scatter(rng, img, cells, colours, chance):
    for x, y in cells:
        if rng.random() < chance:
            put(img, x, y, rng.choice(colours))


def sunfreckled(rng, solid, glow):
    FRECKLE = [(150, 92, 56, 255), (132, 80, 48, 255)]
    arm_pixels(lambda x, y, r, x0, x1: put(solid, x, y, rng.choice(FRECKLE)) if rng.random() < 0.16 else None)
    scatter(rng, solid, [(x, 13) for x in range(10, 14)] + list(CHEEKS), FRECKLE, 0.45)


def skymarked(rng, solid, glow):
    """A pale cloud-swirl birthmark on the right forearm's outer face."""
    CLOUD = [(236, 244, 252, 255), (206, 222, 240, 255)]
    LIGHT = [(214, 240, 255, 255), (170, 214, 250, 255)]
    SWIRL = ["..##..", ".#..#.", "#..#.#", "#.##.#", ".#...#", "..###."]
    x0, x1, y0 = ARMS[0]
    for row, line in enumerate(SWIRL):
        for col, ch in enumerate(line):
            if ch == "#":
                put(solid, x0 + 1 + col, y0 + 3 + row, CLOUD[row % 2])
                put(glow, x0 + 1 + col, y0 + 3 + row, LIGHT[(row + col) % 2])


def obsidian(rng, solid, glow):
    """Glassy black-violet hide over the hands and up the forearms, sheen lines, crying-obsidian tears."""
    OBS = [(20, 14, 32, 255), (34, 22, 50, 255), (48, 30, 72, 255)]
    SHEEN = (110, 80, 160, 255)
    TEAR = [(170, 90, 255, 255), (120, 50, 220, 255)]

    def hide(x, y, r, x0, x1):
        if r < 8 or (r < 10 and rng.random() < 0.6):
            put(solid, x, y, SHEEN if (x - x0 + r) % 7 == 0 else OBS[(x * 3 + y) % 3])
            if rng.random() < 0.05:
                put(glow, x, y, rng.choice(TEAR))
    arm_pixels(hide)
    hands(lambda x, y: put(solid, x, y, OBS[0]))
    for x, y in CHEEKS[:4]:
        put(solid, x, y, OBS[1])
    put(glow, 9, 14, TEAR[0])  # a tear under one eye


def magmavein(rng, solid, glow):
    """Basalt over the hands and forearms, glowing magma in the cracks, one cracked cheek."""
    BASALT = [(64, 60, 62, 255), (52, 48, 50, 255), (78, 74, 74, 255)]
    MAGMA = [(255, 220, 110, 255), (255, 150, 40, 255), (210, 70, 20, 255)]

    def rock(x, y, r, x0, x1):
        if r < 7 or (r < 9 and rng.random() < 0.55):
            put(solid, x, y, rng.choice(BASALT))
    arm_pixels(rock)
    for x0, x1, y0 in ARMS:
        w = x1 - x0
        for _ in range(5):  # cracks branch up from the knuckles
            x, y = rng.randrange(w), y0 + 11
            for i in range(rng.randrange(4, 9)):
                put(glow, x0 + x % w, y, MAGMA[0] if i < 2 else MAGMA[1] if i < 5 else MAGMA[2])
                put(solid, x0 + x % w, y, (40, 18, 10, 255))
                y -= 1
                x += rng.choice((-1, 0, 0, 1))
    hands(lambda x, y: (put(solid, x, y, BASALT[1]), put(glow, x, y, MAGMA[1]) if rng.random() < 0.5 else None))
    for x, y in ((14, 13), (15, 14), (14, 15)):
        put(solid, x, y, (40, 18, 10, 255))
        put(glow, x, y, MAGMA[1])


def tide_variant(rng, solid, glow, scale, lights, extra):
    def scales(x, y, r, x0, x1):
        if r < 7 and (x + y) % 2 == 0 and rng.random() < (0.9 - r * 0.1):
            put(solid, x, y, rng.choice(scale))
        if r in (3, 6) and (x - x0) % 2 == (r // 3) % 2:
            put(glow, x, y, lights[1])
        if r == 0 and rng.random() < 0.7:
            put(glow, x, y, lights[0])
    arm_pixels(scales)
    hands(lambda x, y: put(glow, x, y, lights[rng.randrange(2)]))
    for i in range(2):
        for x in (8, 15, 2, 3, 4, 5, 18, 19, 20, 21):
            put(solid, x, 13 + i, scale[1])
            put(glow, x, 13 + i, lights[2])
    extra()


def reefcrowned(rng, solid, glow):
    CORAL = [(240, 110, 90, 255), (220, 80, 110, 255), (250, 170, 80, 255)]
    LIGHT = [(255, 190, 170, 255), (255, 130, 120, 255), (190, 80, 90, 255)]

    def crusts():  # coral knobs crusting the forearms
        for x0, x1, y0 in ARMS:
            for _ in range(7):
                x, y = rng.randrange(x0, x1), y0 + 4 + rng.randrange(6)
                for dx, dy in ((0, 0), (1, 0), (0, -1)):
                    put(solid, x + dx, y + dy, rng.choice(CORAL))
    tide_variant(rng, solid, glow, [(112, 84, 80, 255), (90, 64, 64, 255)], LIGHT, crusts)


def abyssal(rng, solid, glow):
    LIGHT = [(230, 255, 255, 255), (130, 220, 255, 255), (60, 110, 170, 255)]

    def lures():  # a few big lure-lights instead of rows
        for x0, x1, y0 in ARMS:
            for x in (x0 + 2, x0 + 7, x0 + 12):
                put(glow, x, y0 + 5, LIGHT[0])
                put(solid, x, y0 + 5, (40, 70, 100, 255))
    tide_variant(rng, solid, glow, [(30, 50, 80, 255), (20, 36, 60, 255)], LIGHT, lures)


def thornrose(rng, solid, glow):
    """Briar stems winding the arms, thorns off them, a red rose blooming at each wrist."""
    STEM = [(52, 82, 30, 255), (38, 62, 22, 255)]
    THORN = (196, 186, 150, 255)
    ROSE = [(200, 30, 50, 255), (150, 16, 36, 255), (236, 70, 90, 255)]
    ROSE_GLOW = [(255, 110, 130, 255), (220, 50, 80, 255)]
    for x0, x1, y0 in ARMS:
        w = x1 - x0
        for start in (rng.randrange(4), 8 + rng.randrange(4)):  # two briars round each arm
            x = start
            for y in range(y0 + 11, y0 - 1, -1):
                put(solid, x0 + x % w, y, STEM[y % 2])
                if rng.random() < 0.45:
                    put(solid, x0 + (x + rng.choice((-1, 1))) % w, y, THORN)
                x += rng.choice((1, 1, 0))
        for cx, cy in ((1, 8), (5, 6), (9, 8), (13, 6)):  # roses blooming round the wrist, one on each face
            for dx, dy, c in ((0, 0, 0), (1, 0, 1), (0, 1, 1), (1, 1, 2), (-1, 0, 2), (0, -1, 2)):
                put(solid, x0 + (cx + dx) % w, y0 + cy + dy, ROSE[c])
                put(glow, x0 + (cx + dx) % w, y0 + cy + dy, ROSE_GLOW[c % 2])
    # a rose in the hair over the right ear
    for x, y, c in ((1, 9, 0), (2, 9, 1), (1, 10, 2), (2, 10, 0)):
        put(solid, x, y, ROSE[c])


def blossomkin(rng, solid, glow):
    """Blossom twigs up the forearms, pale pink flowers alight, petals on the cheeks."""
    TWIG = [(92, 60, 64, 255), (74, 46, 52, 255)]
    PETAL = [(250, 200, 220, 255), (240, 160, 196, 255), (255, 230, 240, 255)]
    for x0, x1, y0 in ARMS:
        w = x1 - x0
        for start in (rng.randrange(4), 8 + rng.randrange(4)):  # two twigs round each arm
            x = start
            for y in range(y0 + 11, y0 + 1, -1):
                put(solid, x0 + x % w, y, TWIG[y % 2])
                if rng.random() < 0.5:
                    fx = x0 + (x + rng.choice((-1, 1))) % w
                    put(solid, fx, y, rng.choice(PETAL))
                    put(glow, fx, y, PETAL[1])
                x += rng.choice((1, 0, -1))
    scatter(rng, solid, CHEEKS, PETAL, 0.3)


def glowcap(rng, solid, glow):
    """Ambrosium-yellow round patches glowing up the forearms, and a glow round the fingertips."""
    PATCH_DAY = (190, 200, 60, 255)
    PATCH = [(250, 255, 140, 255), (200, 220, 60, 255)]
    for x0, x1, y0 in ARMS:
        for _ in range(6):
            x, y = rng.randrange(x0, x1 - 1), y0 + 3 + rng.randrange(7)
            for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)):
                put(solid, x + dx, y + dy, PATCH_DAY)
                put(glow, x + dx, y + dy, PATCH[(dx + dy) % 2])
    hands(lambda x, y: put(glow, x, y, PATCH[1]) if rng.random() < 0.5 else None)


def mosshide(rng, solid, glow):
    """Moss and lichen grown over the hands and up the forearms; the Verdant's pink patches, dimmer."""
    MOSS = [(70, 96, 36, 255), (56, 80, 30, 255), (90, 112, 44, 255)]
    LICHEN = (170, 178, 120, 255)

    def moss(x, y, r, x0, x1):
        if r < 5 or (r < 8 and rng.random() < 0.5):
            put(solid, x, y, LICHEN if rng.random() < 0.08 else rng.choice(MOSS))
    arm_pixels(moss)
    hands(lambda x, y: put(solid, x, y, rng.choice(MOSS)))
    for x0, x1, y0 in ARMS:
        for x in range(x0, x1, 4):
            put(glow, x + 1, y0 + 2, PINK[1])


def soulflame(rng, solid, glow):
    """The Emberborn's embers gone soul-blue."""
    EMBER = [(150, 250, 255, 255), (60, 200, 230, 255), (30, 120, 170, 255)]

    def embers(x, y, r, x0, x1):
        if r < 2 or rng.random() < max(0.0, 0.5 - r * 0.07):
            put(glow, x, y, EMBER[0] if r < 2 else EMBER[1] if r < 5 else EMBER[2])
    arm_pixels(embers)
    hands(lambda x, y: put(glow, x, y, EMBER[0]))


def ashen(rng, solid, glow):
    """Ash-grey cinders over the hands; a few still glowing, dim orange."""
    ASH = [(110, 104, 100, 255), (90, 86, 84, 255), (140, 134, 128, 255)]
    CINDER = [(255, 140, 60, 255), (180, 80, 40, 255)]

    def cinders(x, y, r, x0, x1):
        if r < 4 or (r < 7 and rng.random() < 0.45):
            put(solid, x, y, rng.choice(ASH))
            if rng.random() < 0.12:
                put(glow, x, y, rng.choice(CINDER))
    arm_pixels(cinders)
    hands(lambda x, y: put(solid, x, y, ASH[1]))


def rimeglass(rng, solid, glow):
    """Clear ice crystals grown into the forearms: white edges, cold blue cores."""
    EDGE = [(240, 250, 255, 255), (210, 234, 250, 255)]
    CORE = [(150, 210, 255, 255), (90, 160, 240, 255)]
    for x0, x1, y0 in ARMS:
        for _ in range(6):
            x, y = rng.randrange(x0, x1 - 2), y0 + 2 + rng.randrange(8)
            shard = ((1, 0), (0, 1), (1, 1), (2, 1), (1, 2))
            for dx, dy in shard:
                core = (dx, dy) == (1, 1)
                put(solid, x + dx, y + dy, CORE[1] if core else EDGE[(dx + dy) % 2])
                put(glow, x + dx, y + dy, CORE[0] if core else CORE[1])
    hands(lambda x, y: put(solid, x, y, EDGE[0]))


def aurora(rng, solid, glow):
    """Wavy lines of green and violet light up the forearms, faint by day."""
    DAY = [(150, 200, 180, 255), (170, 150, 210, 255)]
    LIGHT = [(120, 255, 180, 255), (90, 220, 210, 255), (190, 130, 255, 255)]
    for x0, x1, y0 in ARMS:
        w = x1 - x0
        for band in range(3):
            base = y0 + 2 + band * 3
            for x in range(w):
                y = base + (1 if (x + band * 2) % 6 < 3 else 0)
                put(solid, x0 + x, y, DAY[band % 2])
                put(glow, x0 + x, y, LIGHT[band])


def moonpale(rng, solid, glow):
    """A silver crescent on the back of each hand; a few silver specks."""
    SILVER = [(220, 226, 240, 255), (190, 196, 214, 255)]
    LIGHT = [(250, 252, 255, 255), (200, 210, 250, 255)]
    MOON = [".##.", "#...", "#...", ".##."]
    for x0, x1, y0 in ARMS:
        for row, line in enumerate(MOON):
            for col, ch in enumerate(line):
                if ch == "#":
                    put(solid, x0 + 4 + col, y0 + 6 + row, SILVER[row % 2])
                    put(glow, x0 + 4 + col, y0 + 6 + row, LIGHT[0])
    arm_pixels(lambda x, y, r, x0, x1: put(glow, x, y, LIGHT[1]) if rng.random() < 0.03 else None)


def umbral(rng, solid, glow):
    """The Nightborn's shadow climbing past the elbow to the shoulder, stars in it, the edge of the jaw shadowed."""
    INK = [(18, 14, 28, 255), (30, 24, 46, 255), (44, 36, 64, 255)]
    STAR = [(220, 210, 255, 255), (160, 140, 230, 255)]

    def shadow(x, y, r, x0, x1):
        if r < 10 or rng.random() < 0.6:
            put(solid, x, y, INK[min(2, r // 4)])
            if rng.random() < 0.06:
                put(glow, x, y, rng.choice(STAR))
    arm_pixels(shadow)
    hands(lambda x, y: put(solid, x, y, INK[0]))
    for x in range(8, 16):
        put(solid, x, 15, INK[1])


def battlescarred(rng, solid, glow):
    SCAR = [(214, 160, 140, 255), (190, 130, 116, 255)]
    for x0, x1, y0 in ARMS:  # a few long diagonal scars
        for _ in range(3):
            x, y = rng.randrange(x0, x1), y0 + 2 + rng.randrange(7)
            for i in range(rng.randrange(3, 6)):
                put(solid, min(x1 - 1, x + i), y + i // 2, SCAR[i % 2])
    for x, y in ((13, 9), (13, 10), (14, 11), (14, 12), (14, 13)):  # one across the eye and cheek
        put(solid, x, y, SCAR[0])


def warpainted(rng, solid, glow):
    OCHRE = [(176, 54, 30, 255), (150, 40, 24, 255)]
    for x0, x1, y0 in ARMS:  # three stripes round each forearm
        for row in (4, 6, 8):
            for x in range(x0, x1):
                put(solid, x, y0 + row, OCHRE[(x // 2) % 2])
    for x, y in ((8, 13), (9, 13), (10, 13), (13, 13), (14, 13), (15, 13), (8, 14), (15, 14)):  # stripes on the cheeks
        put(solid, x, y, OCHRE[0])


def ironknuckle(rng, solid, glow):
    TAPE = [(222, 214, 190, 255), (196, 188, 166, 255)]
    CALLUS = [(170, 120, 96, 255), (150, 100, 80, 255)]

    def tape(x, y, r, x0, x1):
        if r == 0 or (r == 1 and (x - x0) % 2 == 0):
            put(solid, x, y, TAPE[(x + y) % 2])
        elif r in (2, 3) and rng.random() < 0.3:
            put(solid, x, y, rng.choice(CALLUS))
    arm_pixels(tape)
    hands(lambda x, y: put(solid, x, y, TAPE[0] if (x + y) % 2 else CALLUS[0]))


def inkmarked(rng, solid, glow):
    INK = [(30, 34, 60, 255), (46, 50, 84, 255)]
    for x0, x1, y0 in ARMS:  # bands of ink, a dotted one between
        for x in range(x0, x1):
            put(solid, x, y0 + 3, INK[0])
            put(solid, x, y0 + 7, INK[0])
            if (x - x0) % 2 == 0:
                put(solid, x, y0 + 5, INK[1])


def lodeborn(rng, solid, glow):
    """Iron-dark lines curving up the arms like filings round a lodestone; violet sparks along them."""
    IRON = [(70, 70, 84, 255), (96, 96, 110, 255)]
    SPARK = [(220, 190, 255, 255), (160, 110, 240, 255)]
    for x0, x1, y0 in ARMS:
        w = x1 - x0
        for line in range(4):
            x = line * 4 + rng.randrange(2)
            for r in range(10):
                y = y0 + 11 - r
                put(solid, x0 + x % w, y, IRON[r % 2])
                if r % 3 == 0:
                    put(glow, x0 + x % w, y, SPARK[r % 2])
                x += 1 if r % 4 < 2 else 0


def galeborn(rng, solid, glow):
    CLOUD = [(244, 246, 250, 255), (214, 222, 234, 255)]
    LIGHT = [(240, 250, 255, 255), (190, 220, 250, 255)]
    for x0, x1, y0 in ARMS:
        w = x1 - x0
        for band in range(2):  # two swirls winding the arm
            for r in range(11):
                x = (band * 8 + r * 2) % w
                y = y0 + 11 - r
                put(solid, x0 + x, y, CLOUD[r % 2])
                put(glow, x0 + x, y, LIGHT[1])
                put(solid, x0 + (x + 1) % w, y, CLOUD[1])


def twilight(rng, solid, glow):
    """A gold sun-ring on the right hand, a silver crescent on the left."""
    GOLD, SUN = [(232, 180, 56, 255), (196, 140, 36, 255)], (255, 226, 120, 255)
    SILVER, MOONLIGHT = [(220, 226, 240, 255), (190, 196, 214, 255)], (240, 244, 255, 255)
    shapes = ([".##.", "#..#", "#..#", ".##."], [".##.", "#...", "#...", ".##."])
    for (x0, x1, y0), shape, solidc, light in zip(ARMS, shapes, (GOLD, SILVER), (SUN, MOONLIGHT)):
        for row, line in enumerate(shape):
            for col, ch in enumerate(line):
                if ch == "#":
                    put(solid, x0 + 4 + col, y0 + 7 + row, solidc[row % 2])
                    put(glow, x0 + 4 + col, y0 + 7 + row, light)


def gilded(rng, solid, glow):
    GOLD = [(250, 210, 80, 255), (220, 170, 50, 255)]
    arm_pixels(lambda x, y, r, x0, x1: (put(solid, x, y, rng.choice(GOLD)), put(glow, x, y, GOLD[0]) if rng.random() < 0.5 else None)
               if rng.random() < max(0.0, 0.3 - r * 0.025) else None)
    scatter(rng, solid, CHEEKS, GOLD, 0.4)


def chorusgrown(rng, solid, glow):
    STALK = [(150, 110, 150, 255), (120, 84, 124, 255)]
    BUD = [(240, 190, 250, 255), (210, 150, 230, 255)]
    for x0, x1, y0 in ARMS:
        for _ in range(11):  # knots of chorus, a bud on each
            x, y = rng.randrange(x0, x1), y0 + 3 + rng.randrange(8)
            put(solid, x, y, STALK[0])
            put(solid, x, y - 1, STALK[1])
            put(solid, x, y - 2, BUD[0])
            put(glow, x, y - 2, BUD[1])
    hands(lambda x, y: put(solid, x, y, STALK[1]))


def voidpale(rng, solid, glow):
    VOID = [(14, 8, 20, 255), (26, 16, 36, 255)]
    LIGHT = [(150, 90, 230, 255), (90, 50, 170, 255)]

    def specks(x, y, r, x0, x1):
        if rng.random() < 0.2:
            put(solid, x, y, rng.choice(VOID))
            if rng.random() < 0.3:
                put(glow, x, y, rng.choice(LIGHT))
    arm_pixels(specks)
    scatter(rng, solid, CHEEKS, VOID, 0.35)


def dragon_variant(rng, solid, glow, scale, light, night_glow):
    def scales(x, y, r, x0, x1):
        if r < 7 or (r < 9 and rng.random() < 0.5):
            put(solid, x, y, scale[(x // 2 + y) % 2] if (x + r) % 2 == 0 else scale[2])
            if (x + y) % 3 == 0 and rng.random() < 0.6:
                put(glow, x, y, light[0] if r < 3 else light[1])
    arm_pixels(scales)
    hands(lambda x, y: put(solid, x, y, scale[1]))
    for x, y in ((8, 13), (9, 13), (8, 14), (9, 14), (10, 14), (9, 15)):
        put(solid, x, y, scale[(x + y) % 2])
    put(glow, 9, 14, light[1])


def goldscale(rng, solid, glow):
    dragon_variant(rng, solid, glow, [(200, 150, 50, 255), (170, 120, 36, 255), (230, 190, 80, 255)], [(255, 236, 140, 255), (250, 200, 80, 255)], True)


def rimewyrm(rng, solid, glow):
    dragon_variant(rng, solid, glow, [(150, 190, 220, 255), (120, 160, 200, 255), (200, 226, 244, 255)], [(220, 246, 255, 255), (140, 200, 255, 255)], True)


def hollow(rng, solid, glow):
    """The Deepborn's veins, dimmer and greyer, and dark hollows under the eyes."""
    VEIN = [(60, 76, 80, 255), (44, 58, 62, 255)]
    PULSE = [(150, 210, 204, 255), (90, 150, 150, 255)]
    for x0, x1, y0 in ARMS:
        w = x1 - x0
        for start in (3, 11):
            x = start
            for r in range(8):
                put(solid, x0 + x % w, y0 + 11 - r, VEIN[r % 2])
                put(glow, x0 + x % w, y0 + 11 - r, PULSE[1] if r else PULSE[0])
                x += rng.choice((-1, 0, 1))
    for x in (9, 10, 13, 14):
        put(solid, x, 13, (70, 70, 74, 255))


def geode(rng, solid, glow):
    """Sculk veins up the forearms with amethyst crystals bursting from the knuckles."""
    deepborn(rng, solid, glow)
    AM = [(230, 196, 255, 255), (177, 123, 235, 255), (124, 72, 186, 255)]
    for x0, x1, y0 in ARMS:
        for cx in range(x0 + 1, x1, 4):
            for dy, c in ((0, 1), (-1, 0), (1, 2)):
                put(solid, cx, y0 + 10 + dy, AM[c])
                put(glow, cx, y0 + 10 + dy, AM[c])
    hands(lambda x, y: (put(solid, x, y, AM[1]), put(glow, x, y, AM[0])) if rng.random() < 0.5 else None)


# ---- (Kingdoms 0.7.490) the Sylvan fae and elves, the Beastfolk family and the Hollow dead. Each is a variant over its heritage's
# first look (beastfolk_skin / hollow_skin / sylvan_<n>_skin), so a variant paints only what is its own.

def dryad(rng, solid, glow):
    """Bark in rough patches up the forearms and over the shoulders, ringed knots, moss, and leaves for hair."""
    BARK = [(98, 72, 48, 255), (80, 58, 38, 255), (116, 88, 58, 255)]
    MOSS = [(78, 120, 44, 255), (104, 150, 54, 255)]
    LEAF = [(86, 150, 46, 255), (122, 184, 64, 255), (60, 110, 36, 255)]
    LEAF_GLOW = (168, 232, 96, 255)

    def bark(x, y, r, x0, x1):
        if r < 8 and (rng.random() < 0.85 - r * 0.04):
            put(solid, x, y, BARK[(x // 2 + y // 3) % 3])
        if r in (2, 6) and (x - x0) % 5 == 2:
            put(solid, x, y, BARK[1])  # a knot
    arm_pixels(bark)
    for _ in range(14):
        put(solid, rng.randrange(40, 56), 20 + rng.randrange(8), rng.choice(MOSS))
    for x0, y0, x1, y1 in ((20, 20, 28, 24),):  # bark creeping over the chest
        for x in range(x0, x1):
            for y in range(y0, y1):
                if rng.random() < 0.5:
                    put(solid, x, y, BARK[(x + y) % 3])
    hands(lambda x, y: put(solid, x, y, BARK[1]))
    for x in range(8, 16):  # a crown of leaves over the brow and across the top of the head
        for y in range(0, 8):
            if y > 3 or rng.random() < 0.45:
                put(solid, x, y, rng.choice(LEAF))
    for x in range(8, 16):
        if x % 3 != 1:
            put(solid, x, 8, rng.choice(LEAF))
    for _ in range(6):
        put(glow, rng.randrange(8, 16), rng.randrange(0, 8), LEAF_GLOW)
    for _ in range(4):
        put(glow, rng.randrange(40, 56), 20 + rng.randrange(10), LEAF_GLOW)


def changeling(rng, solid, glow):
    """Never quite the one they seem: mismatched patches of pale and grey skin, a seam across the brow, twig fingers."""
    PATCH = [(188, 190, 176, 255), (150, 156, 140, 255), (212, 208, 190, 255)]
    SEAM = (60, 66, 52, 255)
    TWIG = (96, 74, 52, 255)
    for x0, x1, y0 in ARMS:
        for _ in range(5):
            px_, py_ = rng.randrange(x0, x1 - 2), y0 + rng.randrange(0, 10)
            col = rng.choice(PATCH)
            for dx in range(rng.randrange(2, 4)):
                for dy in range(rng.randrange(2, 4)):
                    put(solid, px_ + dx, py_ + dy, col)
    for y in range(8, 16):  # half the face a shade off, a seam down the middle of it
        for x in range(8, 12):
            if rng.random() < 0.75:
                put(solid, x, y, PATCH[(x + y) % 2])
        put(solid, 11, y, SEAM if y % 2 == 0 else PATCH[1])
    dots(solid, ((8, 9), (9, 9), (10, 9), (13, 9), (14, 9), (15, 9)), SEAM)  # the brow seam
    hands(lambda x, y: put(solid, x, y, TWIG if rng.random() < 0.5 else PATCH[1]))
    for _ in range(6):
        put(glow, rng.randrange(40, 56), 20 + rng.randrange(10), (200, 230, 170, 255))


def sprite(rng, solid, glow):
    """Pollen glow and sparkles in the hair (the dragonfly wings are model parts)."""
    for _ in range(8):
        put(glow, rng.randrange(8, 16), rng.randrange(0, 8), (230, 255, 220, 255))
    for x0, x1, y0 in ARMS:
        for _ in range(5):
            put(glow, rng.randrange(x0, x1), y0 + rng.randrange(0, 11), (210, 255, 230, 255))
    hands(lambda x, y: put(glow, x, y, (210, 255, 230, 255)) if rng.random() < 0.5 else None)


def nymph(rng, solid, glow):
    """Pearled water on the skin: ripple lines up the forearms, droplets, a water lily in the hair."""
    RIPPLE = [(120, 206, 214, 255), (86, 168, 190, 255)]
    PEARL = (236, 248, 250, 255)
    LILY = [(250, 220, 236, 255), (240, 170, 200, 255), (250, 240, 120, 255)]

    def ripples(x, y, r, x0, x1):
        if r in (1, 4, 7) and (x - x0 + r) % 3 != 2:
            put(solid, x, y, RIPPLE[r // 3 % 2])
            if r == 4:
                put(glow, x, y, (150, 240, 240, 255))
    arm_pixels(ripples)
    for _ in range(12):
        x, y = rng.randrange(40, 56), 20 + rng.randrange(9)
        put(solid, x, y, PEARL)
        put(glow, x, y, (190, 250, 250, 255))
    hands(lambda x, y: put(glow, x, y, (150, 240, 240, 255)) if rng.random() < 0.5 else None)
    for (x, y, c) in ((9, 5, 0), (10, 5, 1), (9, 6, 1), (10, 6, 2), (8, 5, 1), (11, 6, 0), (8, 6, 0), (10, 4, 0), (9, 4, 1)):
        put(solid, x, y, LILY[c])
    # a drop under each eye
    dots(solid, ((9, 13), (14, 13)), PEARL)


def faerie(rng, solid, glow):
    """A flower crown, glitter on the cheeks and arms (the butterfly wings are model parts)."""
    FLOWER = [(250, 150, 190, 255), (250, 220, 120, 255), (190, 150, 240, 255)]
    for x in range(8, 16):
        put(solid, x, 8, FLOWER[x % 2 * 2 if x % 3 else 1])
    for x in (9, 12, 14):
        put(glow, x, 8, FLOWER[0])
    scatter(rng, glow, CHEEKS, [(255, 220, 250, 255)], 0.5)
    for x0, x1, y0 in ARMS:
        for _ in range(6):
            put(glow, rng.randrange(x0, x1), y0 + rng.randrange(0, 11), (255, 210, 250, 255))


def gold_lines(rng, solid, glow, gold, spacing=5, rows=(2, 6)):
    for x0, x1, y0 in ARMS:
        for r in rows:
            for x in range(x0, x1):
                if (x - x0) % spacing != 4:
                    put(solid, x, y0 + 11 - r, gold)
        for x in range(x0, x1, spacing):  # a rising line joining them
            for r in range(rows[0], rows[1] + 1):
                put(solid, x, y0 + 11 - r, gold)
    hands(lambda x, y: put(solid, x, y, gold) if (x + y) % 3 == 0 else None)


def elf_ears(solid, colour, tip):
    """Long pointed ears: swept back along the sides of the head, their tips on the crown's edges."""
    for sx, direction in ((0, 1), (23, -1)):
        for i in range(4):
            put(solid, sx + (i if direction > 0 else -i), 11 + (1 if i == 3 else 0) - (1 if i == 0 else 0), colour)
        put(solid, sx + (4 if direction > 0 else -4), 12, tip)
    dots(solid, ((8, 8), (15, 8)), colour)


def highelf(rng, solid, glow):
    """Gold: a circlet across the brow with a gem, filigree up the forearms, tall ears, flaxen shimmer."""
    GOLD = (232, 196, 96, 255)
    GEM = (120, 200, 255, 255)
    gold_lines(rng, solid, glow, GOLD)
    for x in range(8, 16):
        put(solid, x, 8, GOLD)
    dots(solid, ((11, 9), (12, 9)), GEM)
    dots(glow, ((11, 9), (12, 9), (11, 8), (12, 8)), (200, 240, 255, 255))
    elf_ears(solid, (236, 214, 176, 255), GOLD)
    for x0, x1, y0 in ARMS:
        for x in range(x0 + 2, x1, 5):
            put(glow, x, y0 + 5, (255, 230, 150, 255))


def moonelf(rng, solid, glow):
    """Silver-blue: a crescent on the brow, star freckles that shine at night, violet-tipped ears, silver lines."""
    SILVER = (176, 188, 232, 255)
    STAR = (230, 236, 255, 255)
    gold_lines(rng, solid, glow, SILVER, spacing=6, rows=(3, 7))
    dots(solid, ((11, 9), (12, 9), (10, 10), (13, 10), (10, 11), (13, 11)), SILVER)
    dots(glow, ((11, 9), (12, 9), (10, 10), (13, 10), (10, 11), (13, 11)), STAR)
    elf_ears(solid, (196, 190, 236, 255), (120, 100, 190, 255))
    for _ in range(16):
        x, y = rng.randrange(40, 56), 20 + rng.randrange(0, 11)
        put(solid, x, y, SILVER)
        put(glow, x, y, STAR)
    scatter(rng, glow, CHEEKS, [STAR], 0.5)


def bloodelf(rng, solid, glow):
    """Crimson sigils up the forearms and a red mark under each eye, gold-trimmed ears; they burn a fel red."""
    RED = (176, 24, 44, 255)
    FEL = (255, 70, 80, 255)
    GOLD = (222, 180, 90, 255)
    for x0, x1, y0 in ARMS:
        for x in range(x0, x1):
            if (x - x0) % 4 != 3:
                put(solid, x, y0 + 8, RED)
                put(glow, x, y0 + 8, FEL)
        for x in range(x0 + 1, x1, 4):  # sigil strokes rising from the line
            for r in range(3):
                put(solid, x, y0 + 7 - r, RED)
                put(glow, x, y0 + 7 - r, (210, 40, 60, 255))
    hands(lambda x, y: put(glow, x, y, FEL) if rng.random() < 0.4 else None)
    dots(solid, ((9, 14), (10, 15), (14, 14), (13, 15)), RED)
    dots(glow, ((9, 14), (14, 14)), FEL)
    elf_ears(solid, (240, 214, 196, 255), GOLD)
    for x in range(9, 15):
        put(solid, x, 8, GOLD if x % 2 else RED)


# ---- Beastfolk
def arachnid(rng, solid, glow):
    """Dark banded fur like a tarantula's, a ring of extra eyes across the brow, a patterned abdomen on the back."""
    DARK = [(48, 36, 52, 255), (66, 50, 60, 255), (34, 26, 40, 255)]
    BAND = (150, 116, 80, 255)

    def hairy(x, y, r, x0, x1):
        if r < 9 and rng.random() < 0.9:
            put(solid, x, y, BAND if r in (3, 6) else rng.choice(DARK))
    arm_pixels(hairy)
    hands(lambda x, y: put(solid, x, y, DARK[2]))
    # four extra eyes: a row of two small, two large above the real ones, glowing
    for (x, y) in ((8, 10), (9, 9), (14, 9), (15, 10), (10, 10), (13, 10)):
        put(solid, x, y, (20, 14, 24, 255))
        put(glow, x, y, (255, 50, 60, 255))
    # the back: an hourglass of pale hair on dark
    x0, y0, x1, y1 = BODY_BACK
    for y in range(y0, y1):
        for x in range(x0, x1):
            if rng.random() < 0.55:
                put(solid, x, y, rng.choice(DARK))
    for i in range(5):
        w = 3 - abs(i - 2)
        for dx in range(-w, w + 1):
            put(solid, 35 + dx, 23 + i, BAND)
    for x0_, y0_, x1_, y1_ in LEG_BACKS:
        rect(solid, x0_, y0_ + 6, x1_, y1_, DARK[0])
    dots(solid, ((10, 15), (13, 15)), (200, 190, 180, 255))  # fangs


def avian(rng, solid, glow):
    """Feathers in scalloped rows up the forearms, a beak (the folded wings, crest and tail feathers are model parts)."""
    PRIMARY = [(92, 70, 52, 255), (120, 92, 64, 255), (70, 52, 40, 255)]
    COVERT = [(188, 156, 110, 255), (214, 184, 134, 255)]
    TIP = (240, 232, 214, 255)

    def feathers(x, y, r, x0, x1):
        if r < 10:
            row = r // 2
            if (x + row) % 2 == 0 or r % 2 == 0:
                put(solid, x, y, COVERT[(x + row) % 2] if r % 2 else PRIMARY[(x // 2) % 3])
            if r % 2 == 1 and (x + row) % 2 == 0:
                put(solid, x, y, TIP)
    arm_pixels(feathers)
    hands(lambda x, y: put(solid, x, y, (214, 176, 70, 255)))  # yellow talons
    # (0.7.490) the folded wings, the crest and the tail feathers are model parts now (client BeastParts)
    dots(solid, ((11, 13), (12, 13), (11, 14), (12, 14)), (232, 188, 60, 255))  # beak
    dots(solid, ((11, 15), (12, 15)), (200, 150, 40, 255))


def canine(rng, solid, glow):
    """A wolf's grey fur with a pale muzzle and chest, dark paws (its ears and bushy tail are model parts)."""
    FUR = [(112, 112, 116, 255), (92, 92, 98, 255), (136, 134, 132, 255)]
    PALE = (208, 204, 196, 255)
    fur_arms(rng, solid, FUR, reach=9, ragged=0.6)
    hands(lambda x, y: put(solid, x, y, (50, 46, 46, 255)))
    muzzle(solid, PALE, (30, 28, 30, 255))
    for y in range(20, 26):  # pale ruff on the chest
        for x in range(22, 26):
            if rng.random() < 0.8:
                put(solid, x, y, PALE)
    for x in range(8, 16):  # a darker saddle across the brow
        if rng.random() < 0.6:
            put(solid, x, 8, FUR[1])


def feline(rng, solid, glow):
    """Tabby stripes on the forearms and back, whisker marks, a pink nose (ears and tail are model parts)."""
    FUR = (190, 144, 84, 255)
    STRIPE = (96, 64, 40, 255)
    CREAM = (234, 214, 176, 255)

    def tabby(x, y, r, x0, x1):
        if r < 9:
            put(solid, x, y, STRIPE if r % 3 == 1 else FUR)
    arm_pixels(tabby)
    hands(lambda x, y: put(solid, x, y, CREAM))
    muzzle(solid, CREAM, (222, 120, 132, 255))
    dots(solid, ((8, 13), (8, 14), (15, 13), (15, 14), (9, 9), (14, 9), (10, 8), (13, 8)), STRIPE)  # whiskers and brow stripes
    for y in range(20, 32, 3):  # stripes across the back
        for x in range(33, 39):
            put(solid, x, y, STRIPE)


def kitsune(rng, solid, glow):
    """A fox: russet fur with white socks, red whisker marks (the tall ears and the one to three tails are model parts),
    and blue fox-fire sparking up the forearms."""
    FUR = [(214, 106, 40, 255), (190, 84, 30, 255)]
    WHITE = (246, 238, 226, 255)
    MARK = (176, 40, 40, 255)
    FIRE = (120, 200, 255, 255)

    def fox(x, y, r, x0, x1):
        if r < 9:
            put(solid, x, y, WHITE if r < 2 else FUR[(x + r) % 2])
        if r in (4, 7) and (x - x0) % 4 == 1:
            put(solid, x, y, FIRE)
            put(glow, x, y, FIRE)
    arm_pixels(fox)
    hands(lambda x, y: put(solid, x, y, WHITE))
    muzzle(solid, WHITE, (30, 24, 24, 255))
    dots(solid, ((8, 13), (9, 14), (15, 13), (14, 14), (10, 8), (13, 8)), MARK)
    # the tails (one to three, by player) are model parts now (client BeastParts)


def serpen(rng, solid, glow):
    """Green scales in diamonds up the forearms, across the chest and over the cheeks and neck, a pale belly, slit eyes
    (the long scaled tail is a model part)."""
    SCALE = [(70, 130, 68, 255), (52, 104, 56, 255), (92, 154, 80, 255)]
    BELLY = (216, 208, 150, 255)
    EDGE = (34, 72, 44, 255)

    def scales(x, y, r, x0, x1):
        if r < 10 and (x + y) % 2 == 0:
            put(solid, x, y, SCALE[(x // 2 + r // 2) % 3])
        elif r < 10:
            put(solid, x, y, EDGE)
    arm_pixels(scales)
    hands(lambda x, y: put(solid, x, y, EDGE))
    for y in range(20, 27):  # chest scales
        for x in range(20, 28):
            if (x + y) % 2 == 0:
                put(solid, x, y, SCALE[(x + y) // 2 % 3])
    for y in range(26, 32):  # belly plates
        for x in range(22, 26):
            put(solid, x, y, BELLY if y % 2 else (188, 180, 120, 255))
    for x, y in ((8, 14), (8, 15), (9, 15), (15, 14), (15, 15), (14, 15), (8, 10), (15, 10), (9, 8), (14, 8), (11, 8), (12, 8)):
        put(solid, x, y, SCALE[(x + y) % 3])  # scales on the cheeks and brow
    dots(solid, ((9, 11), (14, 11), (9, 13), (14, 13)), EDGE)  # lids: slit pupils


# ---- Hollow
def zombie(rng, solid, glow):
    """Rotting green flesh in patches, torn open to the red meat in places, stitches across the forearms and the brow."""
    ROT = [(96, 132, 82, 255), (78, 112, 68, 255), (118, 150, 92, 255)]
    MEAT = [(136, 52, 56, 255), (108, 36, 44, 255)]
    THREAD = (40, 32, 30, 255)

    def rot(x, y, r, x0, x1):
        if r < 10 and rng.random() < 0.7:
            put(solid, x, y, rng.choice(ROT))
    arm_pixels(rot)
    for x0, x1, y0 in ARMS:
        for _ in range(3):
            cx, cy = rng.randrange(x0, x1 - 2), y0 + rng.randrange(2, 8)
            for dx in range(2):
                for dy in range(2):
                    put(solid, cx + dx, cy + dy, MEAT[(dx + dy) % 2])
        for x in range(x0 + 1, x1, 4):  # stitches across the forearm
            dots(solid, ((x, y0 + 4), (x, y0 + 6), (x + 1, y0 + 5)), THREAD)
    hands(lambda x, y: put(solid, x, y, ROT[1]))
    for x in range(8, 16):
        put(solid, x, 9, THREAD if x % 2 else ROT[1])
    rect(solid, 20, 22, 24, 26, ROT[0])
    dots(solid, ((21, 23), (22, 24), (23, 25)), MEAT[0])


def skeleton(rng, solid, glow):
    """Bare bone: ribs across the chest on dark, bone down the forearms with knuckled joints, cracks and teeth in the face."""
    BONE = [(232, 228, 210, 255), (200, 196, 176, 255), (170, 166, 150, 255)]
    DARK = (28, 26, 30, 255)

    def bones(x, y, r, x0, x1):
        if r < 10:
            put(solid, x, y, DARK)
        if 2 <= r < 9 and (x - x0) % 4 in (1, 2):
            put(solid, x, y, BONE[(x - x0) % 4 - 1] if r not in (5,) else BONE[2])
        if r in (9, 1):
            put(solid, x, y, BONE[0])
    arm_pixels(bones)
    hands(lambda x, y: put(solid, x, y, BONE[(x + y) % 3]))
    for y in range(20, 30):  # ribs
        for x in range(20, 28):
            put(solid, x, y, DARK)
        if y % 2 == 0 and y < 28:
            for x in range(20, 28):
                if x not in (23, 24):
                    put(solid, x, y, BONE[0] if y % 4 else BONE[1])
    for y in range(20, 29):  # spine
        put(solid, 23, y, BONE[1])
        put(solid, 24, y, BONE[2])
    for x in range(10, 14):  # teeth
        put(solid, x, 15, BONE[0] if x % 2 else BONE[1])
    dots(solid, ((9, 11), (10, 11), (9, 12), (10, 12), (13, 11), (14, 11), (13, 12), (14, 12)), DARK)  # sockets
    dots(solid, ((11, 13), (12, 13)), DARK)  # nose hole
    dots(solid, ((8, 9), (9, 10), (15, 9), (14, 10)), BONE[2])  # a cracked brow


def wraith(rng, solid, glow):
    """A body of pale cold mist: translucent patches fading toward the elbow, wisps trailing off the legs, a ghost-lit hollow face."""
    MIST = [(196, 222, 236, 150), (170, 204, 226, 120), (214, 236, 246, 90)]
    CHILL = (150, 230, 255, 255)

    def mist(x, y, r, x0, x1):
        if r < 10 and rng.random() < 0.9 - r * 0.04:
            put(solid, x, y, rng.choice(MIST))
        if rng.random() < 0.06:
            put(glow, x, y, CHILL)
    arm_pixels(mist)
    for x in range(20, 28):
        for y in range(20, 32):
            if rng.random() < 0.55 - (y - 20) * 0.02:
                put(solid, x, y, rng.choice(MIST))
    for lx0, ly0, lx1, ly1 in ((0, 20, 16, 32), (16, 52, 32, 64)):  # tatters trailing down the legs
        for x in range(lx0, lx1):
            for y in range(ly0 + 4, ly1):
                if rng.random() < 0.45 + (y - ly0) * 0.02:
                    put(solid, x, y, rng.choice(MIST))
    dots(solid, ((9, 11), (10, 11), (13, 11), (14, 11), (9, 13), (10, 13), (13, 13), (14, 13), (11, 14), (12, 14), (11, 15), (12, 15)), (40, 60, 86, 190))
    for _ in range(14):
        put(glow, rng.randrange(0, 64), rng.choice((rng.randrange(20, 32), rng.randrange(52, 64))), CHILL)


def reaper(rng, solid, glow):
    """Funeral black: a tattered shroud over the torso and legs, bandages up the arms, a grey skull-white jaw, soul-red sigils."""
    SHROUD = [(18, 16, 24, 255), (30, 26, 38, 255), (42, 36, 52, 255)]
    BAND = [(110, 100, 96, 255), (86, 78, 76, 255)]
    SOUL = (255, 60, 60, 255)

    def wraps(x, y, r, x0, x1):
        if r < 11:
            put(solid, x, y, SHROUD[(x + y) % 3] if r % 4 else BAND[(x + y) % 2])
    arm_pixels(wraps)
    hands(lambda x, y: put(solid, x, y, SHROUD[0]))
    for x in range(20, 28):  # robe over the front
        for y in range(20, 32):
            if y < 28 or rng.random() < 0.6:
                put(solid, x, y, SHROUD[(x + y) % 3])
    x0, y0, x1, y1 = BODY_BACK
    for x in range(x0, x1):
        for y in range(y0, y1):
            if y < y1 - 3 or rng.random() < 0.55:
                put(solid, x, y, SHROUD[(x + y) % 3])
    for lx0, ly0, lx1, ly1 in ((0, 20, 16, 32), (16, 52, 32, 64)):
        for x in range(lx0, lx1):
            for y in range(ly0, ly1):
                if y < ly0 + 6 or rng.random() < 0.7:
                    put(solid, x, y, SHROUD[(x + y) % 3])
    for x in range(8, 16):  # the cowl's edge across the brow
        put(solid, x, 8, SHROUD[0])
        put(solid, x, 9, SHROUD[1] if x % 2 else SHROUD[0])
    for x in range(9, 15):  # a bone-white jaw
        put(solid, x, 15, (214, 210, 196, 255))
    dots(solid, ((9, 11), (10, 11), (13, 11), (14, 11)), SHROUD[0])
    for x, y in ((23, 24), (24, 24), (23, 25), (24, 25), (23, 23), (24, 26), (22, 24), (25, 24)):  # a sigil on the chest, alight
        put(solid, x, y, (90, 12, 18, 255))
        put(glow, x, y, SOUL)


VARIANTS = {"sunfreckled": sunfreckled, "skymarked": skymarked, "obsidian": obsidian, "magmavein": magmavein,
            "reefcrowned": reefcrowned, "abyssal": abyssal, "thornrose": thornrose, "blossomkin": blossomkin,
            "glowcap": glowcap, "mosshide": mosshide, "soulflame": soulflame, "ashen": ashen,
            "rimeglass": rimeglass, "aurora": aurora, "moonpale": moonpale, "umbral": umbral,
            "battlescarred": battlescarred, "warpainted": warpainted, "ironknuckle": ironknuckle, "inkmarked": inkmarked,
            "lodeborn": lodeborn, "galeborn": galeborn, "twilight": twilight, "gilded": gilded,
            "chorusgrown": chorusgrown, "voidpale": voidpale, "goldscale": goldscale, "rimewyrm": rimewyrm,
            "hollow": hollow, "geode": geode,
            # (0.7.490) the Sylvan fae and elves, the Beastfolk, the Hollow dead
            "dryad": dryad, "changeling": changeling, "sprite": sprite, "nymph": nymph, "faerie": faerie, "highelf": highelf,
            "moonelf": moonelf, "bloodelf": bloodelf, "arachnid": arachnid, "avian": avian, "canine": canine, "feline": feline,
            "kitsune": kitsune, "serpen": serpen, "zombie": zombie, "skeleton": skeleton, "wraith": wraith, "reaper": reaper}
# their eyes' colours (client VariantLooks has the same); those missing keep their heritage's eyes
VARIANT_EYES = {"skymarked": (207, 239, 255), "obsidian": (196, 156, 255), "magmavein": (255, 160, 64),
                "reefcrowned": (255, 180, 156), "abyssal": (230, 255, 255), "thornrose": (255, 112, 136),
                "blossomkin": (255, 208, 228), "glowcap": (232, 245, 122), "soulflame": (124, 244, 255),
                "ashen": (255, 216, 176), "rimeglass": (232, 248, 255), "aurora": (156, 255, 208),
                "moonpale": (244, 246, 255), "umbral": (184, 168, 255), "lodeborn": (196, 168, 255),
                "galeborn": (240, 248, 255), "twilight": (255, 230, 200), "gilded": (255, 224, 138),
                "chorusgrown": (240, 180, 255), "voidpale": (42, 20, 64), "goldscale": (255, 208, 96),
                "rimewyrm": (184, 232, 255), "hollow": (240, 255, 252), "geode": (214, 170, 255),
                "dryad": (180, 224, 112), "changeling": (200, 224, 160), "sprite": (200, 255, 224), "nymph": (156, 240, 240),
                "faerie": (255, 192, 240), "highelf": (255, 232, 144), "moonelf": (176, 192, 255), "bloodelf": (255, 80, 96),
                "arachnid": (255, 48, 48), "avian": (255, 224, 112), "canine": (255, 208, 96), "feline": (200, 255, 96),
                "kitsune": (255, 160, 64), "serpen": (232, 240, 96), "zombie": (168, 255, 112), "skeleton": (240, 244, 236),
                "wraith": (156, 240, 255), "reaper": (255, 64, 64)}
for key, fn in VARIANTS.items():
    solid, glow = blank(), blank()
    fn(random.Random(sum(map(ord, key)) * 31), solid, glow)
    solid.save(os.path.join(VOUT, key + "_skin.png"))
    glow.save(os.path.join(VOUT, key + "_glow.png"))
for key, c in VARIANT_EYES.items():
    img = blank()
    for x, y in EYES:
        img.putpixel((x, y), c + (255,))
    img.save(os.path.join(VOUT, key + "_eyes.png"))

# the two Tideborn variants' fins: the same shapes, their own colours
for key, (membrane, spine, edge, edge_glow) in {
        "reefcrowned": ((236, 120, 110, 255), (190, 70, 80, 255), (255, 170, 140, 255), (255, 120, 110, 255)),
        "abyssal": ((40, 66, 104, 255), (24, 44, 76, 255), (90, 150, 200, 255), (120, 210, 255, 255))}.items():
    vf, vg = Image.new("RGBA", (32, 32), (0, 0, 0, 0)), Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    for kind, (u, v, d, h) in FINS.items():
        cells = fin_shape(kind, d, h)
        for face in (0, 1):
            ox, oy = u + face * d, v + d
            for c, r in cells:
                edge_px = any((c + dc, r + dr) not in cells for dc, dr in ((1, 0), (-1, 0), (0, -1), (0, 1))
                              if 0 <= c + dc < d and 0 <= r + dr < h) or r == 0
                vf.putpixel((ox + c, oy + r), edge if edge_px else spine if c % 2 == 0 else membrane)
                if edge_px:
                    vg.putpixel((ox + c, oy + r), edge_glow)
    vf.save(os.path.join(VOUT, key + "_fins.png"))
    vg.save(os.path.join(VOUT, key + "_fins_glow.png"))
print("Heritage looks written to", os.path.normpath(OUT))
