"""Draws the faction folk's skins (64x64, the humanoid layout): the guard (a soldier's face, cropped hair, a padded
gambeson under his armour); the king (a greying beard, a gold crown set with rubies, a crimson robe trimmed with
ermine); a fiefdom's lord in each of the 16 dye colours (a silver circlet, a surcoat in his overlord's colour); and the
builders, a foreman and a worker for each style: kingdom (a tabard in each dye colour over work clothes), village
(villager-style smocks with leather aprons), barbarian (furs and hide). Original art. Run from the kingdoms project
root: python -I tools/gen_folk.py [guard king lord builder] (all when none are named)."""
from PIL import Image
import os
import random
import sys
import zlib

OUT = "src/main/resources/assets/jaymoskingdoms/textures/entity/{}.png"

# (x, y, w, h) of each box's faces: top, bottom, right, front, left, back
def faces(ox, oy, w, h, d):
    return {"top": (ox + d, oy, w, d), "bottom": (ox + d + w, oy, w, d), "right": (ox, oy + d, d, h), "front": (ox + d, oy + d, w, h),
            "left": (ox + d + w, oy + d, d, h), "back": (ox + d + w + d, oy + d, w, h)}

HEAD = faces(0, 0, 8, 8, 8)
BODY = faces(16, 16, 8, 12, 4)
ARMS = [faces(40, 16, 4, 12, 4), faces(32, 48, 4, 12, 4)]
LEGS = [faces(0, 16, 4, 12, 4), faces(16, 48, 4, 12, 4)]


def noisy(img, rect, base, spread=10, seed=0):
    rnd = random.Random(seed)
    x0, y0, w, h = rect
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            n = rnd.randint(-spread, spread)
            img.putpixel((x, y), tuple(max(0, min(255, c + n)) for c in base) + (255,))


def fill_box(img, box, base, seed):
    for i, rect in enumerate(box.values()):
        noisy(img, rect, base, seed=seed + i)


def face(img, skin, hair, eyes, beard=None):
    fill_box(img, HEAD, skin, 1)
    x0, y0, w, h = HEAD["front"]
    # hair on top and the upper rows all round
    for name in ("top", "right", "left", "back"):
        rx, ry, rw, rh = HEAD[name]
        noisy(img, (rx, ry, rw, rh if name == "top" else 3 if name != "back" else 7), hair, seed=zlib.crc32(name.encode()) % 50)
    noisy(img, (x0, y0, w, 2), hair, seed=7)
    # eyes, brows, nose, mouth
    for ex in (x0 + 1, x0 + 5):
        img.putpixel((ex, y0 + 4), (255, 255, 255, 255))
        img.putpixel((ex + 1, y0 + 4), eyes + (255,))
        img.putpixel((ex, y0 + 3), tuple(c - 30 for c in hair) + (255,))
        img.putpixel((ex + 1, y0 + 3), tuple(c - 30 for c in hair) + (255,))
    img.putpixel((x0 + 3, y0 + 5), tuple(c - 25 for c in skin) + (255,))
    img.putpixel((x0 + 4, y0 + 5), tuple(c - 25 for c in skin) + (255,))
    for mx in range(x0 + 2, x0 + 6):
        img.putpixel((mx, y0 + 6), (120, 60, 50, 255))
    if beard:
        noisy(img, (x0, y0 + 6, w, 2), beard, seed=9)
        img.putpixel((x0 + 3, y0 + 6), (110, 55, 50, 255))
        img.putpixel((x0 + 4, y0 + 6), (110, 55, 50, 255))
        for name in ("right", "left"):
            rx, ry, rw, rh = HEAD[name]
            noisy(img, (rx, ry + 5, rw, 3), beard, seed=11)


def guard():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    face(img, (196, 150, 120), (70, 50, 35), (60, 90, 140))
    gambeson = (150, 128, 90)
    fill_box(img, BODY, gambeson, 20)
    for arm in ARMS:
        fill_box(img, arm, gambeson, 30)
        noisy(img, (arm["front"][0], arm["front"][1] + 9, 4, 3), (196, 150, 120), seed=31)
    for leg in LEGS:
        fill_box(img, leg, (80, 70, 60), 40)
        noisy(img, (leg["front"][0], leg["front"][1] + 9, 4, 3), (50, 40, 30), seed=41)
    # quilted seams on the gambeson
    x0, y0, w, h = BODY["front"]
    for y in range(y0, y0 + h, 3):
        for x in range(x0, x0 + w):
            img.putpixel((x, y), tuple(c - 25 for c in gambeson) + (255,))
    img.save(OUT.format("guard"))


def king():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    face(img, (205, 160, 130), (150, 145, 140), (70, 60, 40), beard=(175, 170, 165))
    # the crown: a gold band round the head with points and rubies
    gold, ruby = (235, 190, 60), (190, 20, 40)
    for name in ("front", "right", "left", "back"):
        rx, ry, rw, rh = HEAD[name]
        noisy(img, (rx, ry, rw, 2), gold, spread=12, seed=60)
        for x in range(rx, rx + rw, 2):
            img.putpixel((x, ry), (0, 0, 0, 0) if name != "front" else gold + (255,))
        img.putpixel((rx + rw // 2, ry + 1), ruby + (255,))
    rx, ry, rw, rh = HEAD["top"]
    for x in range(rx, rx + rw):
        img.putpixel((x, ry), gold + (255,))
        img.putpixel((x, ry + rh - 1), gold + (255,))
    for y in range(ry, ry + rh):
        img.putpixel((rx, y), gold + (255,))
        img.putpixel((rx + rw - 1, y), gold + (255,))
    # the robe: crimson, trimmed with ermine
    robe, ermine = (140, 20, 35), (240, 238, 230)
    fill_box(img, BODY, robe, 70)
    x0, y0, w, h = BODY["front"]
    noisy(img, (x0 + 3, y0, 2, h), ermine, spread=6, seed=71)
    for y in range(y0 + 1, y0 + h, 3):
        img.putpixel((x0 + 3, y), (20, 20, 20, 255))
    for arm in ARMS:
        fill_box(img, arm, robe, 80)
        noisy(img, (arm["front"][0], arm["front"][1] + 8, 4, 1), ermine, seed=81)
        noisy(img, (arm["front"][0], arm["front"][1] + 9, 4, 3), (205, 160, 130), seed=82)
    for leg in LEGS:
        fill_box(img, leg, (90, 15, 25), 90)
    img.save(OUT.format("king"))


# the 16 dye colours (their names as the game's DyeColor names them), roughly as dyed cloth looks
DYES = {"white": (233, 236, 236), "orange": (240, 118, 19), "magenta": (189, 68, 179), "light_blue": (58, 175, 217),
        "yellow": (248, 197, 39), "lime": (112, 185, 25), "pink": (237, 141, 172), "gray": (62, 68, 71),
        "light_gray": (142, 142, 134), "cyan": (21, 137, 145), "purple": (121, 42, 172), "blue": (53, 57, 157),
        "brown": (114, 71, 40), "green": (84, 109, 27), "red": (161, 39, 34), "black": (30, 30, 34)}
HAT = faces(32, 0, 8, 8, 8)


def shade(c, by):
    return tuple(max(0, min(255, v + by)) for v in c)


def save(img, name):
    path = OUT.format(name)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def bare_forearms(img, skin, seed, rows=4):
    for arm in ARMS:
        for name in ("front", "back", "right", "left"):
            rx, ry, rw, rh = arm[name]
            noisy(img, (rx, ry + rh - rows, rw, rows), skin, seed=seed)
        bx, by, bw, bh = arm["bottom"]
        noisy(img, (bx, by, bw, bh), skin, seed=seed + 1)


def boots(img, colour, seed):
    for leg in LEGS:
        for name in ("front", "back", "right", "left"):
            rx, ry, rw, rh = leg[name]
            noisy(img, (rx, ry + rh - 3, rw, 3), colour, spread=6, seed=seed)
        bx, by, bw, bh = leg["bottom"]
        noisy(img, (bx, by, bw, bh), shade(colour, -15), spread=4, seed=seed + 1)


def belt(img, colour, buckle, y_off=8):
    for name in ("front", "back", "right", "left"):
        rx, ry, rw, rh = BODY[name]
        for x in range(rx, rx + rw):
            img.putpixel((x, ry + y_off), colour + (255,))
    fx, fy, fw, fh = BODY["front"]
    img.putpixel((fx + 3, fy + y_off), buckle + (255,))
    img.putpixel((fx + 4, fy + y_off), buckle + (255,))


def lord(colour_name, colour):
    """A fiefdom's lord: a trimmed dark beard, a silver circlet, a surcoat in his overlord's colour edged in gold."""
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    face(img, (200, 155, 125), (55, 40, 30), (50, 80, 60), beard=(70, 52, 40))
    silver, gem = (205, 210, 220), shade(colour, 30)
    for name in ("front", "right", "left", "back"):
        rx, ry, rw, rh = HEAD[name]
        noisy(img, (rx, ry + 1, rw, 1), silver, spread=8, seed=101)
    fx, fy, fw, fh = HEAD["front"]
    img.putpixel((fx + 3, fy + 1), gem + (255,))
    img.putpixel((fx + 4, fy + 1), gem + (255,))
    gold = (220, 175, 60)
    fill_box(img, BODY, colour, 110)
    fx, fy, fw, fh = BODY["front"]
    for y in range(fy, fy + fh):
        img.putpixel((fx, y), gold + (255,))
        img.putpixel((fx + fw - 1, y), gold + (255,))
    # a pale cross on the breast, the house's mark
    pale = shade(colour, 70) if sum(colour) < 450 else shade(colour, -70)
    for y in range(fy + 2, fy + 8):
        img.putpixel((fx + 3, y), pale + (255,))
        img.putpixel((fx + 4, y), pale + (255,))
    for x in range(fx + 2, fx + 6):
        img.putpixel((x, fy + 4), pale + (255,))
    belt(img, (60, 40, 25), gold)
    for arm in ARMS:
        fill_box(img, arm, shade(colour, -25), 120)
        noisy(img, (arm["front"][0], arm["front"][1] + 9, 4, 3), (200, 155, 125), seed=121)
    for leg in LEGS:
        fill_box(img, leg, (45, 40, 50), 130)
    boots(img, (60, 40, 25), 131)
    save(img, "lord/" + colour_name)


def kingdom_builder(role, colour_name, colour):
    """A kingdom's builder: work clothes (a rough shirt, breeches, boots) under a short tabard in the kingdom's colour;
    the foreman in a felt cap of the colour with a ruler's notches on his belt, the worker in a cloth coif, sleeves rolled."""
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    skin = (198, 152, 118) if role == "worker" else (186, 140, 108)
    face(img, skin, (95, 65, 40) if role == "worker" else (60, 45, 35), (70, 90, 120), beard=None if role == "worker" else (70, 52, 40))
    shirt, breeches = (150, 130, 100), (95, 80, 60)
    fill_box(img, BODY, shirt, 140)
    # the tabard: front and back panels in the colour, a darker hem
    for name in ("front", "back"):
        rx, ry, rw, rh = BODY[name]
        noisy(img, (rx + 1, ry, rw - 2, rh - 1), colour, spread=8, seed=141)
        for x in range(rx + 1, rx + rw - 1):
            img.putpixel((x, ry + rh - 2), shade(colour, -40) + (255,))
    belt(img, (70, 48, 28), (170, 170, 170))
    for arm in ARMS:
        fill_box(img, arm, shirt, 150)
    if role == "worker":
        bare_forearms(img, skin, 151, rows=5)
    else:
        bare_forearms(img, skin, 151, rows=2)
    for leg in LEGS:
        fill_box(img, leg, breeches, 160)
    boots(img, (70, 48, 28), 161)
    if role == "foreman":
        # a felt cap in the colour (hat layer), a band round it
        for name in ("top", "front", "right", "left", "back"):
            rx, ry, rw, rh = HAT[name]
            noisy(img, (rx, ry, rw, rh if name == "top" else 3), colour, spread=8, seed=171)
            if name != "top":
                for x in range(rx, rx + rw):
                    img.putpixel((x, ry + 2), shade(colour, -45) + (255,))
        fx, fy, fw, fh = BODY["front"]
        for y in range(fy + 9, fy + 12):
            img.putpixel((fx + 1, y), (200, 180, 120, 255))
    else:
        # a cloth coif
        coif = (200, 190, 165)
        for name in ("top", "right", "left", "back"):
            rx, ry, rw, rh = HEAD[name]
            noisy(img, (rx, ry, rw, rh if name == "top" else 3 if name != "back" else 6), coif, spread=6, seed=181)
        fx, fy, fw, fh = HEAD["front"]
        noisy(img, (fx, fy, fw, 1), coif, spread=6, seed=182)
    save(img, "builder/kingdom_{}_{}".format(role, colour_name))


def village_builder(role):
    """A village builder, dressed like its villagers: a long smock in earthy cloth under a leather apron; the foreman in
    a broad straw hat, the worker bareheaded with his sleeves rolled."""
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    skin = (190, 145, 110)
    face(img, skin, (110, 80, 50) if role == "worker" else (150, 140, 130), (60, 90, 50),
         beard=None if role == "worker" else (160, 150, 140))
    smock = (110, 120, 70) if role == "worker" else (125, 85, 55)
    apron = (150, 105, 65)
    fill_box(img, BODY, smock, 200)
    # the apron: bib and skirt, with its strap round the neck
    fx, fy, fw, fh = BODY["front"]
    noisy(img, (fx + 1, fy + 2, fw - 2, fh - 2), apron, spread=8, seed=201)
    img.putpixel((fx + 1, fy), shade(apron, -20) + (255,))
    img.putpixel((fx + 1, fy + 1), shade(apron, -20) + (255,))
    img.putpixel((fx + fw - 2, fy), shade(apron, -20) + (255,))
    img.putpixel((fx + fw - 2, fy + 1), shade(apron, -20) + (255,))
    # a pocket with a hammer's handle in it
    for x in range(fx + 2, fx + 6):
        img.putpixel((x, fy + 8), shade(apron, -30) + (255,))
    img.putpixel((fx + 5, fy + 7), (90, 60, 35, 255))
    img.putpixel((fx + 5, fy + 6), (90, 60, 35, 255))
    for arm in ARMS:
        fill_box(img, arm, smock, 210)
    bare_forearms(img, skin, 211, rows=5 if role == "worker" else 2)
    for leg in LEGS:
        fill_box(img, leg, shade(smock, -25), 220)
        noisy(img, (leg["front"][0], leg["front"][1], 4, 6), apron, spread=8, seed=221)
    boots(img, (80, 55, 35), 222)
    if role == "foreman":
        straw = (215, 185, 95)
        for name in ("top", "front", "right", "left", "back"):
            rx, ry, rw, rh = HAT[name]
            noisy(img, (rx, ry, rw, rh if name == "top" else 3), straw, spread=12, seed=231)
            if name != "top":
                for x in range(rx, rx + rw):
                    img.putpixel((x, ry + 2), (140, 60, 40, 255))
    save(img, "builder/village_{}".format(role))


def barbarian_builder(role):
    """A barbarian builder: wild hair, a painted face, hide breeches and a stitched hide jerkin, a pelt over the
    shoulders; the foreman wears a wolf-grey pelt hood, the worker goes bare-armed."""
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    skin = (180, 130, 95)
    hair = (120, 70, 35) if role == "worker" else (40, 30, 25)
    face(img, skin, hair, (110, 70, 40), beard=shade(hair, 10))
    # war paint: a dark band across the eyes
    fx, fy, fw, fh = HEAD["front"]
    for x in range(fx, fx + fw):
        if x not in (fx + 1, fx + 2, fx + 5, fx + 6):
            img.putpixel((x, fy + 4), (40, 50, 90, 255))
    hide, fur = (160, 120, 80), (110, 100, 90) if role == "foreman" else (95, 70, 45)
    fill_box(img, BODY, hide, 300)
    # stitching down the jerkin
    bx, by, bw, bh = BODY["front"]
    for y in range(by + 1, by + bh, 2):
        img.putpixel((bx + 3, y), (70, 45, 25, 255))
    # the pelt over the shoulders: top rows all round, ragged at the edge
    rnd = random.Random(301)
    for name in ("front", "back", "right", "left"):
        rx, ry, rw, rh = BODY[name]
        for x in range(rx, rx + rw):
            depth = 3 + rnd.randint(0, 1)
            for y in range(ry, ry + depth):
                img.putpixel((x, y), shade(fur, rnd.randint(-18, 18)) + (255,))
    noisy(img, BODY["top"], fur, spread=18, seed=302)
    for arm in ARMS:
        fill_box(img, arm, skin, 310)
        for name in ("front", "back", "right", "left"):
            rx, ry, rw, rh = arm[name]
            noisy(img, (rx, ry, rw, 4), fur, spread=18, seed=311)
            # a band of paint round the upper arm
            for x in range(rx, rx + rw):
                img.putpixel((x, ry + 6), (40, 50, 90, 255))
        noisy(img, arm["top"], fur, spread=18, seed=312)
    belt(img, (60, 40, 25), (190, 190, 180), y_off=9)
    for leg in LEGS:
        fill_box(img, leg, shade(hide, -30), 320)
    boots(img, fur, 321)
    if role == "foreman":
        for name in ("top", "right", "left", "back"):
            rx, ry, rw, rh = HAT[name]
            noisy(img, (rx, ry, rw, rh if name == "top" else 5 if name != "back" else 8), fur, spread=20, seed=331)
        hx, hy, hw, hh = HAT["front"]
        noisy(img, (hx, hy, hw, 2), fur, spread=20, seed=332)
        # the wolf's ears and eyes, up on the brow
        img.putpixel((hx, hy), shade(fur, -30) + (255,))
        img.putpixel((hx + hw - 1, hy), shade(fur, -30) + (255,))
        img.putpixel((hx + 2, hy + 1), (220, 200, 90, 255))
        img.putpixel((hx + 5, hy + 1), (220, 200, 90, 255))
    save(img, "builder/barbarian_{}".format(role))


def lords():
    for name, colour in DYES.items():
        lord(name, colour)


def builders():
    for role in ("foreman", "worker"):
        for name, colour in DYES.items():
            kingdom_builder(role, name, colour)
        village_builder(role)
        barbarian_builder(role)


if __name__ == "__main__":
    wanted = sys.argv[1:] or ["guard", "king", "lord", "builder"]
    for what in wanted:
        {"guard": guard, "king": king, "lord": lords, "builder": builders}[what]()
    print("folk drawn:", ", ".join(wanted))

