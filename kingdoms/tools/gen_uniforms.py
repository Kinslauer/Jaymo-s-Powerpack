"""Draws the set outfits our named characters wear as MCA villagers (MCA clothing: 64x64 in the humanoid layout, the head
left bare for the villager's own face and hair, and no skin painted, so each villager's own skin shows at the hands and
any bare feet). Andy (2026-10-27): the clothing library's player-made skins "have no place in the pack"; captains,
crews, rulers and guards wear a set library of our own instead ({@code Uniforms}).

Most are our folk's and sailors' skins from before they were MCA villagers (tools/gen_pirates.py, tools/gen_folk.py),
drawn again without their faces: each is drawn twice with two different skin tones, and every pixel that changes (the
skin) is cleared, then the head. New here: plain seamen for navy, merchant and whaler crews (a knitted cap, a striped
or smock shirt, breeches and shoes), a merchant or whaling master's brown coat, a second king's robe (royal blue), and
gowns for queens and ladies. Original art. Run from the kingdoms project root: python -I tools/gen_uniforms.py
(also writes a front-view contact sheet to build/uniforms-preview.png)."""
from PIL import Image
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_folk as folk  # noqa: E402
import gen_pirates as sea  # noqa: E402

OUT = "src/main/resources/assets/jaymoskingdoms/skins/clothing/{}.png"
# two skin tones nothing in the clothes is painted in, so every pixel that changes between them is skin
TONE_A, TONE_B = (201, 77, 199), (61, 203, 87)
DRAWN = {}


def capture(name):
    def save(img, path):
        DRAWN[name] = img.copy()
    return save


def bare(img_a, img_b):
    """{img_a} with the skin (the pixels {img_b} drew differently) and the whole head (not the hat layer) cleared."""
    out = img_a.copy()
    for y in range(64):
        for x in range(64):
            if img_a.getpixel((x, y)) != img_b.getpixel((x, y)) or y < 16 and x < 32:
                out.putpixel((x, y), (0, 0, 0, 0))
    return out


def twice(draw):
    """{draw(tone)} for both tones, the skin cleared."""
    return bare(draw(TONE_A), draw(TONE_B))


def save(img, name):
    path = OUT.format(name)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    print("drew", path)


# ---- the sailors (tools/gen_pirates.py) -----------------------------------------------------------------------------

def pirate(fn, *args):
    def draw(tone):
        sea.TONES[:] = [tone] * 4
        sea.face = lambda *a, **k: None
        sea.save = capture("x")
        fn(*args)
        return DRAWN["x"]
    return twice(draw)


def seaman(i):
    """A navy, merchant or whaler's hand: a knitted cap, a striped shirt (or a linen smock), breeches and shoes."""
    caps = [(34, 48, 82), (120, 116, 108), (118, 26, 24), (60, 52, 44)]
    stripes = [(sea.WHITE, sea.NAVY), (sea.WHITE, (118, 26, 24)), None, (sea.WHITE, (60, 60, 64))]

    def draw(tone):
        rnd = random.Random(7700 + i)
        img = sea.blank()
        sea.cap(img, rnd, caps[i])
        if stripes[i]:
            sea.striped(img, rnd, *stripes[i])
        else:
            # a whaleman's linen smock, its sleeves rolled
            sea.box(img, sea.BODY, (196, 184, 156), 8, rnd)
            for arm in sea.ARMS:
                sea.box(img, arm, (196, 184, 156), 8, rnd)
                sea.rows(img, arm, 7, 5, tone, 6, rnd)
        sea.hands(img, rnd, tone)
        sea.legs(img, rnd, [(74, 66, 56), sea.NAVY, (88, 76, 60), sea.TAR][i], (40, 30, 24))
        sea.belt(img, rnd, sea.LEATHER, (150, 150, 156))
        return img
    return twice(draw)


def master():
    """A merchantman's or whaler's master: a brown felt tricorn, a long brown coat with brass buttons over a waistcoat."""
    def draw(tone):
        rnd = random.Random(7800)
        img = sea.blank()
        sea.tricorn(img, rnd, (70, 50, 34), (176, 140, 70))
        sea.box(img, sea.BODY, (120, 96, 52), 5, rnd)
        for arm in sea.ARMS:
            sea.box(img, arm, sea.WHITE, 5, rnd)
        sea.hands(img, rnd, tone)
        sea.coat(img, rnd, (92, 62, 38), (130, 96, 60), buttons=(196, 160, 80))
        sea.belt(img, rnd, (30, 24, 20), (196, 160, 80))
        sea.legs(img, rnd, (70, 60, 50), (24, 18, 16))
        return img
    return twice(draw)


# ---- the realm's folk (tools/gen_folk.py) ---------------------------------------------------------------------------

def folk_look(fn, *args, cloth=None):
    def draw(tone):
        folk.LOOK = {"skin": tone, "hair": (70, 50, 35), "eyes": (60, 90, 140)}
        folk.CLOTH = cloth
        folk.save = capture("x")
        fn(*args)
        folk.LOOK, folk.CLOTH = None, None
        return DRAWN["x"]
    return twice(draw)


def king_robe(robe, trim):
    """A king's robe trimmed with ermine (as tools/gen_folk.py's king, in {robe}), a gold belt."""
    def draw(tone):
        img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
        folk.fill_box(img, folk.BODY, robe, 70)
        x0, y0, w, h = folk.BODY["front"]
        folk.noisy(img, (x0 + 3, y0, 2, h), trim, spread=6, seed=71)
        for y in range(y0 + 1, y0 + h, 3):
            img.putpixel((x0 + 3, y), (20, 20, 20, 255))
        for arm in folk.ARMS:
            folk.fill_box(img, arm, robe, 80)
            folk.noisy(img, (arm["front"][0], arm["front"][1] + 8, 4, 1), trim, seed=81)
            folk.noisy(img, (arm["front"][0], arm["front"][1] + 9, 4, 3), tone, seed=82)
            bx, by, bw, bh = arm["bottom"]
            folk.noisy(img, (bx, by, bw, bh), tone, seed=83)
        for leg in folk.LEGS:
            folk.fill_box(img, leg, folk.shade(robe, -40), 90)
        folk.boots(img, (60, 40, 25), 91)
        folk.belt(img, (220, 175, 60), (190, 20, 40))
        return img
    return twice(draw)


# ---- gowns (new) ----------------------------------------------------------------------------------------------------

SKIRT = [folk.faces(0, 32, 4, 12, 4), folk.faces(0, 48, 4, 12, 4)]


def gown(cloth, trim, ermine=False, seed=0):
    """A long gown: a fitted bodice laced down the front, long sleeves with {trim} cuffs, and a skirt to the floor over
    both legs (and their outer layer, so it stands a little off them); a queen's has an ermine stole and hem."""
    def draw(tone):
        img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
        folk.fill_box(img, folk.BODY, cloth, seed + 10)
        x0, y0, w, h = folk.BODY["front"]
        # the neckline, the lacing and the waist
        folk.noisy(img, (x0 + 2, y0, 4, 1), tone, spread=4, seed=seed + 11)
        for y in range(y0 + 2, y0 + 8, 2):
            img.putpixel((x0 + 3, y), trim + (255,))
            img.putpixel((x0 + 4, y + 1), trim + (255,))
        for name in ("front", "back", "right", "left"):
            rx, ry, rw, rh = folk.BODY[name]
            for x in range(rx, rx + rw):
                img.putpixel((x, ry + 8), trim + (255,))
        for arm in folk.ARMS:
            folk.fill_box(img, arm, folk.shade(cloth, -12), seed + 20)
            for name in ("front", "back", "right", "left"):
                rx, ry, rw, rh = arm[name]
                folk.noisy(img, (rx, ry + 8, rw, 1), trim, spread=4, seed=seed + 21)
                folk.noisy(img, (rx, ry + 9, rw, 3), tone, spread=4, seed=seed + 22)
            bx, by, bw, bh = arm["bottom"]
            folk.noisy(img, (bx, by, bw, bh), tone, spread=4, seed=seed + 23)
        for legs in (folk.LEGS, SKIRT):
            for leg in legs:
                folk.fill_box(img, leg, folk.shade(cloth, -6), seed + 30)
                for name in ("front", "back", "right", "left"):
                    rx, ry, rw, rh = leg[name]
                    # folds down the skirt, and the hem
                    for y in range(ry, ry + rh):
                        img.putpixel((rx + (1 if name in ("front", "back") else 2), y), folk.shade(cloth, -30) + (255,))
                    folk.noisy(img, (rx, ry + rh - 1, rw, 1), (240, 238, 230) if ermine else trim, spread=4, seed=seed + 31)
        if ermine:
            # the stole over the shoulders, its black tails
            for name in ("top", "front", "back", "right", "left"):
                rx, ry, rw, rh = folk.BODY[name]
                folk.noisy(img, (rx, ry, rw, rh if name == "top" else 2), (240, 238, 230), spread=5, seed=seed + 40)
                for x in range(rx + 1, rx + rw, 3):
                    img.putpixel((x, ry + (1 if name != "top" else 0)), (20, 20, 20, 255))
            for arm in folk.ARMS:
                rx, ry, rw, rh = arm["top"]
                folk.noisy(img, (rx, ry, rw, rh), (240, 238, 230), spread=5, seed=seed + 41)
        return img
    return twice(draw)


# ---- all of them ----------------------------------------------------------------------------------------------------

def all_outfits():
    out = {}
    out["crew/pirate_captain"] = pirate(sea.captain)
    out["crew/sea_captain"] = pirate(sea.sea_captain)
    out["crew/master"] = master()
    out["crew/shipwright"] = pirate(sea.shipwright)
    for i in range(4):
        out["crew/deckhand_%d" % i] = pirate(sea.deckhand, i)
        out["crew/seaman_%d" % i] = seaman(i)
    for i in range(2):
        out["crew/gunner_%d" % i] = pirate(sea.gunner, i)
        out["crew/powder_monkey_%d" % i] = pirate(sea.powder_monkey, i)
        out["crew/bombardier_%d" % i] = pirate(sea.bombardier, i)
    for land, cloth in folk.GAMBESONS.items():
        out["guard/" + land] = folk_look(folk.guard, cloth=cloth)
    out["royal/king_crimson"] = king_robe((140, 20, 35), (240, 238, 230))
    out["royal/king_blue"] = king_robe((34, 44, 120), (240, 238, 230))
    out["royal/queen_crimson"] = gown((140, 20, 35), (220, 175, 60), ermine=True, seed=1)
    out["royal/queen_purple"] = gown((88, 32, 120), (220, 175, 60), ermine=True, seed=2)
    for name, colour in folk.DYES.items():
        out["lord/" + name] = folk_look(folk.lord, name, colour)
        out["lady/" + name] = gown(colour, (220, 175, 60), seed=3)
    return out


def preview(outfits, path):
    """Front views on a plain body, eight to a row, for a look at them all."""
    body = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    plain = (196, 150, 120, 255)
    # the base layer only (head, body, arms, legs), not the outer one
    for x0, y0, x1, y1 in ((0, 0, 32, 16), (0, 16, 56, 32), (16, 48, 48, 64)):
        for y in range(y0, y1):
            for x in range(x0, x1):
                body.putpixel((x, y), plain)

    def front(skin):
        f = Image.new("RGBA", (16, 32), (0, 0, 0, 0))
        parts = [((8, 8, 16, 16), (4, 0)), ((40, 8, 48, 16), (4, 0)), ((20, 20, 28, 32), (4, 8)), ((20, 36, 28, 48), (4, 8)),
                 ((44, 20, 48, 32), (0, 8)), ((44, 36, 48, 48), (0, 8)), ((36, 52, 40, 64), (12, 8)), ((52, 52, 56, 64), (12, 8)),
                 ((4, 20, 8, 32), (4, 20)), ((4, 36, 8, 48), (4, 20)), ((20, 52, 24, 64), (8, 20)), ((4, 52, 8, 64), (8, 20))]
        for box, at in parts:
            piece = skin.crop(box)
            f.alpha_composite(piece, at)
        return f

    names = list(outfits)
    cols = 8
    rows = (len(names) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * 20 * 4, rows * 36 * 4), (48, 52, 60, 255))
    for i, n in enumerate(names):
        layered = body.copy()
        layered.alpha_composite(outfits[n])
        f = front(layered).resize((64, 128), Image.NEAREST)
        sheet.alpha_composite(f, ((i % cols) * 80 + 8, (i // cols) * 144 + 8))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    sheet.save(path)
    print("preview", path)


if __name__ == "__main__":
    outfits = all_outfits()
    for name, img in outfits.items():
        save(img, name)
    preview(outfits, "build/uniforms-preview.png")
