"""Draws the set outfits our named characters wear as MCA villagers (MCA clothing: 64x64 in the humanoid layout, the head
left bare for the villager's own face and hair, and no skin painted, so each villager's own skin shows at the hands and
any bare feet). Andy (2026-10-27): the clothing library's player-made skins "have no place in the pack"; captains,
crews, rulers and guards wear a set library of our own instead ({@code Uniforms}).

Most are our folk's and sailors' skins from before they were MCA villagers (tools/gen_pirates.py, tools/gen_folk.py),
drawn again without their faces: each is drawn twice with two different skin tones, and every pixel that changes (the
skin) is cleared, then the head. New here: plain seamen for navy, merchant and whaler crews (a knitted cap, a striped
or smock shirt, breeches and shoes), a merchant or whaling master's brown coat, a second king's robe (royal blue), and
gowns for queens and ladies; at least four of each outfit. Original art. Run from the kingdoms project root: python -I tools/gen_uniforms.py
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

def pirate(fn, *args, colours=None):
    """{fn(*args)} from gen_pirates without its face, with any of its colours ({colours}: name to colour) swapped."""
    def draw(tone):
        kept = {k: getattr(sea, k) for k in (colours or {})}
        for k, c in (colours or {}).items():
            setattr(sea, k, c)
        sea.TONES[:] = [tone] * 4
        sea.face = lambda *a, **k: None
        sea.save = capture("x")
        fn(*args)
        for k, c in kept.items():
            setattr(sea, k, c)
        return DRAWN["x"]
    return twice(draw)


SILVER = (186, 188, 196)
SAND = (200, 184, 140)
GREEN_CLOTH = (36, 66, 44)
BURGUNDY = (96, 26, 40)


def pirate_captains():
    """A pirate captain's long coat: red with gold, black with silver, burgundy with gold, green with brass."""
    return [pirate(sea.captain), pirate(sea.captain, colours={"COAT_RED": (28, 26, 30), "GOLD": SILVER}),
            pirate(sea.captain, colours={"COAT_RED": BURGUNDY, "WHITE": SAND}),
            pirate(sea.captain, colours={"COAT_RED": GREEN_CLOTH, "GOLD": (190, 150, 80)})]


def sea_captains():
    """A navy captain: blue coats of four cuts and shades, white or buff breeches and waistcoat."""
    return [pirate(sea.sea_captain), pirate(sea.sea_captain, colours={"NAVY": (24, 34, 62), "WHITE": SAND}),
            pirate(sea.sea_captain, colours={"NAVY": (44, 62, 104), "GOLD": SILVER}),
            pirate(sea.sea_captain, colours={"NAVY": (30, 40, 70), "WHITE": (226, 222, 210), "GOLD": (200, 150, 50)})]


def deckhands():
    """A pirate's deckhand: bandanas, striped shirts and tarred breeches (six, a crew is big)."""
    out = [pirate(sea.deckhand, i) for i in range(4)]
    out.append(pirate(sea.deckhand, 0, colours={"RED": (40, 90, 60), "NAVY": (110, 40, 30)}))
    out.append(pirate(sea.deckhand, 1, colours={"WHITE": SAND, "BLACK_CLOTH": (70, 46, 30)}))
    return out


def gunners():
    return [pirate(sea.gunner, 0), pirate(sea.gunner, 1),
            pirate(sea.gunner, 0, colours={"LEATHER": (70, 48, 30), "BLACK_CLOTH": (118, 26, 24)}),
            pirate(sea.gunner, 1, colours={"WHITE": SAND, "RED": (34, 48, 82), "TAR": (70, 60, 50)})]


def powder_monkeys():
    return [pirate(sea.powder_monkey, 0), pirate(sea.powder_monkey, 1),
            pirate(sea.powder_monkey, 0, colours={"WHITE": (226, 200, 160)}),
            pirate(sea.powder_monkey, 1, colours={"WHITE": (176, 196, 214)})]


def bombardiers():
    return [pirate(sea.bombardier, 0), pirate(sea.bombardier, 1),
            pirate(sea.bombardier, 0, colours={"RED": (40, 90, 60), "BLACK_CLOTH": (118, 26, 24)}),
            pirate(sea.bombardier, 1, colours={"NAVY": (118, 26, 24), "BLACK_CLOTH": (70, 46, 30)})]


def seaman(i):
    """A navy, merchant or whaler's hand: a knitted cap, a striped shirt (or a linen smock), breeches and shoes."""
    caps = [(34, 48, 82), (120, 116, 108), (118, 26, 24), (60, 52, 44), (40, 70, 50), (150, 130, 90)]
    stripes = [(sea.WHITE, sea.NAVY), (sea.WHITE, (118, 26, 24)), None, (sea.WHITE, (60, 60, 64)), None, (SAND, (70, 50, 34))]
    smocks = {2: (196, 184, 156), 4: (150, 160, 170)}
    breeches = [(74, 66, 56), sea.NAVY, (88, 76, 60), sea.TAR, (60, 64, 70), (90, 70, 50)]

    def draw(tone):
        rnd = random.Random(7700 + i)
        img = sea.blank()
        sea.cap(img, rnd, caps[i])
        if stripes[i]:
            sea.striped(img, rnd, *stripes[i])
        else:
            # a whaleman's linen smock (or a grey wool one), its sleeves rolled
            sea.box(img, sea.BODY, smocks[i], 8, rnd)
            for arm in sea.ARMS:
                sea.box(img, arm, smocks[i], 8, rnd)
                sea.rows(img, arm, 7, 5, tone, 6, rnd)
        sea.hands(img, rnd, tone)
        sea.legs(img, rnd, breeches[i], (40, 30, 24))
        sea.belt(img, rnd, sea.LEATHER, (150, 150, 156))
        return img
    return twice(draw)


def master(coat, trim, waistcoat, felt, cap=False):
    """A merchantman's or whaler's master: a felt tricorn (or a flat cap), a long coat with brass buttons over a waistcoat."""
    def draw(tone):
        rnd = random.Random(7800)
        img = sea.blank()
        if cap:
            sea.cap(img, rnd, felt)
        else:
            sea.tricorn(img, rnd, felt, (176, 140, 70))
        sea.box(img, sea.BODY, waistcoat, 5, rnd)
        for arm in sea.ARMS:
            sea.box(img, arm, sea.WHITE, 5, rnd)
        sea.hands(img, rnd, tone)
        sea.coat(img, rnd, coat, trim, buttons=(196, 160, 80))
        sea.belt(img, rnd, (30, 24, 20), (196, 160, 80))
        sea.legs(img, rnd, (70, 60, 50), (24, 18, 16))
        return img
    return twice(draw)


def masters():
    return [master((92, 62, 38), (130, 96, 60), (120, 96, 52), (70, 50, 34)),
            master((132, 104, 66), (160, 130, 86), (90, 30, 30), (60, 44, 30)),
            master((60, 60, 64), (96, 96, 100), SAND, (40, 40, 44), cap=True),
            master((40, 60, 44), (70, 96, 70), (120, 96, 52), (50, 40, 30))]


def shipwright(shirt, apron, cap):
    def draw(tone):
        rnd = random.Random(7400)
        img = sea.blank()
        sea.cap(img, rnd, cap)
        sea.box(img, sea.BODY, shirt, 6, rnd)
        for arm in sea.ARMS:
            sea.box(img, arm, shirt, 6, rnd)
            sea.rows(img, arm, 6, 6, tone, 6, rnd)
        fx, fy, fw, fh = sea.BODY["front"]
        sea.paint(img, (fx + 1, fy + 2, 6, 10), apron, 8, rnd)
        sea.belt(img, rnd, (60, 40, 22), (150, 150, 156))
        sea.px(img, fx + 1, fy + 9, (150, 150, 156))
        sea.px(img, fx + 6, fy + 9, (110, 80, 50))
        sea.legs(img, rnd, (80, 72, 60), (54, 40, 28))
        return img
    return twice(draw)


def shipwrights():
    return [shipwright((150, 128, 96), sea.LEATHER, (74, 62, 50)), shipwright((190, 180, 160), (70, 50, 32), (40, 50, 70)),
            shipwright((120, 130, 140), sea.LEATHER, (100, 40, 30)), shipwright((160, 120, 80), (60, 44, 30), (60, 60, 60))]


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


def guards(cloth):
    """A guard's gambeson in their land's cloth: plain; a shade lighter with a sword belt; darker under a leather jerkin;
    and with a mail coif over the head and shoulders."""
    out = []
    for v, by in enumerate((0, 16, -16, 0)):
        img = folk_look(folk.guard, cloth=folk.shade(cloth, by))
        if v == 1:
            folk.belt(img, (70, 46, 26), (180, 180, 186))
        if v == 2:
            for name in ("front", "back", "right", "left", "top"):
                rx, ry, rw, rh = sea.JACKET[name]
                folk.noisy(img, (rx, ry, rw, rh if name == "top" else 9), (96, 64, 38), spread=8, seed=140 + len(name))
            fx, fy, fw, fh = sea.JACKET["front"]
            for y in range(fy + 1, fy + 9, 2):
                img.putpixel((fx + 3, y), (50, 34, 20, 255))
                img.putpixel((fx + 4, y), (50, 34, 20, 255))
        if v == 3:
            mail = (150, 152, 158)
            folk.noisy(img, sea.HAT["top"], mail, spread=14, seed=150)
            for name in ("right", "left", "back"):
                folk.noisy(img, sea.HAT[name], mail, spread=14, seed=151)
            hx, hy, hw, hh = sea.HAT["front"]
            folk.noisy(img, (hx, hy, hw, 2), mail, spread=14, seed=152)
            for name in ("front", "back", "right", "left"):
                rx, ry, rw, rh = sea.JACKET[name]
                folk.noisy(img, (rx, ry, rw, 3), mail, spread=14, seed=153)
            folk.belt(img, (60, 40, 25), (180, 180, 186))
        out.append(img)
    return out


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


def lords(name, colour):
    """A lord's surcoat in {colour} with the house's mark in a paler one: a cross (gen_folk's), a chevron, halved (per
    pale) and a band across (a bend)."""
    out = [folk_look(folk.lord, name, colour)]
    pale = folk.shade(colour, 70) if sum(colour) < 450 else folk.shade(colour, -70)
    for v in (1, 2, 3):
        img = out[0].copy()
        fx, fy, fw, fh = folk.BODY["front"]
        # the field again (inside the gold edges, above the belt), then the mark
        folk.noisy(img, (fx + 1, fy, fw - 2, 8), colour, seed=110 + v)
        for y in range(fy, fy + 8):
            for x in range(fx + 1, fx + fw - 1):
                dx, dy = x - fx, y - fy
                if v == 1 and (dy == 6 - abs(dx * 2 - 7) // 2 or dy == 7 - abs(dx * 2 - 7) // 2):
                    img.putpixel((x, y), pale + (255,))
                if v == 2 and dx >= 4:
                    img.putpixel((x, y), pale + (255,))
                if v == 3 and abs(dx - dy) <= 0:
                    img.putpixel((x, y), pale + (255,))
                    if dx + 1 < fw - 1:
                        img.putpixel((x + 1, y), pale + (255,))
        out.append(img)
    return out


# ---- gowns (new) ----------------------------------------------------------------------------------------------------

SKIRT = [folk.faces(0, 32, 4, 12, 4), folk.faces(0, 48, 4, 12, 4)]


def gown(cloth, trim, ermine=False, seed=0, sleeves=None, bodice=None, sash=None):
    """A long gown: a fitted bodice laced down the front, long sleeves with {trim} cuffs, and a skirt to the floor over
    both legs (and their outer layer, so it stands a little off them); a queen's has an ermine stole and hem. A bodice
    or sleeves of another colour, and a sash at the waist, if given."""
    def draw(tone):
        img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
        folk.fill_box(img, folk.BODY, bodice or cloth, seed + 10)
        x0, y0, w, h = folk.BODY["front"]
        # the neckline, the lacing and the waist
        folk.noisy(img, (x0 + 2, y0, 4, 1), tone, spread=4, seed=seed + 11)
        for y in range(y0 + 2, y0 + 8, 2):
            img.putpixel((x0 + 3, y), trim + (255,))
            img.putpixel((x0 + 4, y + 1), trim + (255,))
        for name in ("front", "back", "right", "left"):
            rx, ry, rw, rh = folk.BODY[name]
            for x in range(rx, rx + rw):
                img.putpixel((x, ry + 8), (sash or trim) + (255,))
                if sash:
                    img.putpixel((x, ry + 9), folk.shade(sash, -20) + (255,))
        for arm in folk.ARMS:
            folk.fill_box(img, arm, sleeves or folk.shade(cloth, -12), seed + 20)
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


GOLD_TRIM = (220, 175, 60)
LINEN = (232, 226, 210)


def ladies(colour):
    """A lady's gown in {colour}: gold trim; silver trim; white linen sleeves; a darker bodice with a gold sash."""
    return [gown(colour, GOLD_TRIM, seed=3), gown(colour, SILVER, seed=4), gown(colour, GOLD_TRIM, seed=5, sleeves=LINEN),
            gown(colour, GOLD_TRIM, seed=6, bodice=folk.shade(colour, -40), sash=GOLD_TRIM)]


# ---- all of them ----------------------------------------------------------------------------------------------------

def numbered(out, name, imgs):
    for i, img in enumerate(imgs):
        out["%s_%d" % (name, i)] = img


def all_outfits():
    out = {}
    numbered(out, "crew/pirate_captain", pirate_captains())
    numbered(out, "crew/sea_captain", sea_captains())
    numbered(out, "crew/master", masters())
    numbered(out, "crew/shipwright", shipwrights())
    numbered(out, "crew/deckhand", deckhands())
    numbered(out, "crew/seaman", [seaman(i) for i in range(6)])
    numbered(out, "crew/gunner", gunners())
    numbered(out, "crew/powder_monkey", powder_monkeys())
    numbered(out, "crew/bombardier", bombardiers())
    for land, cloth in folk.GAMBESONS.items():
        numbered(out, "guard/" + land, guards(cloth))
    numbered(out, "royal/king", [king_robe(r, (240, 238, 230)) for r in ((140, 20, 35), (34, 44, 120), (88, 32, 120), (30, 80, 46))])
    numbered(out, "royal/queen", [gown(c, GOLD_TRIM, ermine=True, seed=1 + i)
                                  for i, c in enumerate(((140, 20, 35), (88, 32, 120), (34, 44, 120), (30, 80, 46)))])
    for name, colour in folk.DYES.items():
        numbered(out, "lord/" + name, lords(name, colour))
        numbered(out, "lady/" + name, ladies(colour))
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
