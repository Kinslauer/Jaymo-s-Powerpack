"""Draws the outfits Jaymo's Powers' Z Fighter characters wear as MCA villagers (Kingdoms 0.7.319, ZFighterFolk; Andy
2026-10-28: "it destroys immersion seeing one of those things instead of a humanoid"). MCA clothing, as in
tools/gen_uniforms.py: 64x64 in the humanoid layout, the head left bare for the villager's own face and hair (only the hat
layer is drawn on), and no skin painted (each is drawn with two skin tones and the pixels that change are cleared).
Four of each, all original:

- keeper: the Lookout's keeper, a long temple robe with a sash and a mantle at the shoulders;
- hermit: the Tower Hermit, a loud short-sleeved beach shirt, knee shorts, sandals and a straw hat;
- tinkerer: the Mad Tinkerer, a long lab coat over a dark shirt, brass goggles up on the brow, gloves and a tool belt;
- judge: the Other World's judge, a black robe with white bands at the throat and a coloured stole;
- road_master: the master at the road's end, a travelling gi (a wrapped top, a wide belt, loose trousers, shin wraps);
- teller: the fortune teller, a long robe under a fringed shawl, beads, a headscarf;
- demon_master: a demon master, a dark gi with a sash and spiked pauldrons.

Run from the kingdoms project root: python -I tools/gen_zfighter_outfits.py (also writes build/zfighter-outfits-preview.png)."""
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_pirates as sea  # noqa: E402
import gen_uniforms as uni  # noqa: E402

BODY, JACKET, ARMS, SLEEVES, LEGS, HAT, SIDES = sea.BODY, sea.JACKET, sea.ARMS, sea.SLEEVES, sea.LEGS, sea.HAT, sea.SIDES
GOLD = (214, 172, 60)


def drawn(draw, seed):
    """{draw(img, rnd, tone)} on a blank skin for both tones, the skin cleared."""
    def one(tone):
        rnd = random.Random(seed)
        img = sea.blank()
        draw(img, rnd, tone)
        return img
    return uni.twice(one)


def sleeves_short(img, rnd, cloth, tone, rows_down=4):
    """Arms bare below the first {rows_down} rows (short sleeves)."""
    for arm in ARMS:
        sea.box(img, arm, tone, 4, rnd)
        sea.rows(img, arm, 0, rows_down, cloth, 6, rnd)
        sea.paint(img, arm["top"], cloth, 6, rnd)


def robe(img, rnd, cloth, spread=6):
    """A robe to the floor: body, arms and legs all in {cloth} (the hands left for the skin)."""
    sea.box(img, BODY, cloth, spread, rnd)
    for arm in ARMS:
        sea.box(img, arm, cloth, spread, rnd)
    for leg in LEGS:
        sea.box(img, leg, cloth, spread, rnd)
        sea.paint(img, leg["bottom"], sea.shade(cloth, -40), 4, rnd)


def skirt_layer(img, rnd, cloth):
    """The legs' outer layer as the robe's hem, standing a little off the legs."""
    for x0, y0 in ((0, 32), (0, 48)):
        f = sea.faces(x0, y0, 4, 12, 4)
        for side in SIDES:
            x, y, w, h = f[side]
            sea.paint(img, (x, y, w, h - 1), cloth, 6, rnd)


# ---- the Keeper ----------------------------------------------------------------------------------------------------

def keeper(cloth, sash, trim, seed):
    def draw(img, rnd, tone):
        robe(img, rnd, cloth, 5)
        sea.hands(img, rnd, tone)
        # a mantle over the shoulders: the jacket layer's top rows and the sleeves' tops
        for side in SIDES:
            x, y, w, h = JACKET[side]
            sea.paint(img, (x, y, w, 3), sash, 5, rnd)
            sea.paint(img, (x, y + 3, w, 1), trim, 3, rnd)
        sea.paint(img, JACKET["top"], sash, 5, rnd)
        for sleeve in SLEEVES:
            for side in SIDES:
                x, y, w, h = sleeve[side]
                sea.paint(img, (x, y, w, 2), sash, 5, rnd)
            sea.paint(img, sleeve["top"], sash, 5, rnd)
        # a sash at the waist, its ends hanging down the front, a sign stitched on the chest
        sea.rows(img, BODY, 7, 2, sash, 5, rnd)
        fx, fy, fw, fh = BODY["front"]
        sea.paint(img, (fx + 5, fy + 9, 2, 3), sash, 5, rnd)
        for x, y in ((3, 4), (4, 4), (3, 5), (4, 5)):
            sea.px(img, fx + x, fy + y, trim)
        for arm in ARMS:
            sea.rows(img, arm, 8, 1, trim, 3, rnd)
        for leg in LEGS:
            sea.rows(img, leg, 11, 1, trim, 3, rnd)
    return drawn(draw, seed)


def keepers():
    return [keeper((226, 222, 208), (46, 120, 84), GOLD, 9100), keeper((214, 204, 176), (40, 110, 120), (196, 160, 80), 9101),
            keeper((200, 204, 210), (56, 80, 140), (220, 220, 230), 9102), keeper((232, 228, 220), (176, 132, 40), (120, 30, 40), 9103)]


# ---- the Tower Hermit ----------------------------------------------------------------------------------------------

def hermit(shirt, dots, shorts, seed):
    def draw(img, rnd, tone):
        sea.box(img, BODY, shirt, 6, rnd)
        sleeves_short(img, rnd, shirt, tone)
        # big flowers (or dots) all over the shirt
        for part, n in ((BODY, 9), (ARMS[0], 2), (ARMS[1], 2)):
            for side in SIDES:
                x0, y0, w, h = part[side]
                top = h if part is BODY else 4
                for _ in range(n if side == "front" or side == "back" else max(1, n // 3)):
                    x, y = x0 + rnd.randrange(w), y0 + rnd.randrange(top)
                    sea.px(img, x, y, dots)
                    if x + 1 < x0 + w and part is BODY:
                        sea.px(img, x + 1, y, sea.shade(dots, -30))
        # open at the neck, buttons down the front
        fx, fy, fw, fh = BODY["front"]
        sea.px(img, fx + 3, fy, tone)
        sea.px(img, fx + 4, fy, tone)
        for y in (fy + 3, fy + 6, fy + 9):
            sea.px(img, fx + 4, y, (230, 226, 210))
        # knee shorts, bare shins, sandals with a strap over the foot
        for leg in LEGS:
            sea.box(img, leg, tone, 4, rnd)
            sea.rows(img, leg, 0, 6, shorts, 6, rnd)
            sea.paint(img, leg["top"], shorts, 6, rnd)
            sea.rows(img, leg, 11, 1, (122, 84, 46), 4, rnd)
            sea.paint(img, leg["bottom"], (122, 84, 46), 4, rnd)
        # a straw hat: the hat layer's top and a band round its lower edge
        sea.paint(img, HAT["top"], (214, 190, 120), 10, rnd)
        for side in SIDES:
            x, y, w, h = HAT[side]
            sea.paint(img, (x, y, w, 2), (206, 180, 110), 10, rnd)
            sea.paint(img, (x, y + 2, w, 1), (150, 40, 34) if seed % 2 else (40, 60, 110), 4, rnd)
    return drawn(draw, seed)


def hermits():
    return [hermit((222, 110, 50), (250, 220, 120), (176, 156, 112), 9201), hermit((236, 200, 70), (220, 80, 60), (90, 110, 140), 9202),
            hermit((50, 150, 150), (240, 240, 230), (168, 146, 104), 9203), hermit((226, 112, 150), (250, 230, 160), (120, 130, 90), 9204)]


# ---- the Mad Tinkerer ----------------------------------------------------------------------------------------------

def tinkerer(coat, shirt, glove, lens, seed):
    def draw(img, rnd, tone):
        sea.box(img, BODY, shirt, 5, rnd)
        for arm in ARMS:
            sea.box(img, arm, shirt, 5, rnd)
        sea.hands(img, rnd, tone)
        # rubber gloves to the wrist
        for arm in ARMS:
            for side in SIDES:
                x, y, w, h = arm[side]
                sea.paint(img, (x, y + h - 3, w, 3), glove, 5, rnd)
            sea.paint(img, arm["bottom"], glove, 5, rnd)
        sea.legs(img, rnd, (58, 56, 62), (34, 30, 30))
        # the lab coat, long and open, with a scorch or two and a pen in the pocket
        sea.coat(img, rnd, coat, sea.shade(coat, -24), buttons=sea.shade(coat, -50))
        fx, fy, fw, fh = JACKET["front"]
        sea.px(img, fx + 1, fy + 3, (40, 70, 160))
        for _ in range(3):
            sea.px(img, fx + rnd.randrange(fw), fy + 6 + rnd.randrange(6), sea.shade(coat, -70))
        # a tool belt over the coat, a wrench hanging off it
        sea.rows(img, BODY, 8, 1, sea.LEATHER, 4, rnd)
        bx, by, bw, bh = BODY["front"]
        sea.px(img, bx + 1, by + 9, (170, 170, 176))
        sea.px(img, bx + 1, by + 10, (150, 150, 156))
        # brass goggles pushed up on the brow (the hat layer): a strap round, two round lenses at the front
        for side in SIDES:
            x, y, w, h = HAT[side]
            sea.paint(img, (x, y + 1, w, 1), (60, 46, 34), 4, rnd)
        hx, hy, hw, hh = HAT["front"]
        for lx in (hx + 1, hx + 5):
            sea.px(img, lx, hy, (176, 136, 60))
            sea.px(img, lx + 1, hy, (176, 136, 60))
            sea.px(img, lx, hy + 1, lens)
            sea.px(img, lx + 1, hy + 1, sea.shade(lens, 40))
            sea.px(img, lx, hy + 2, (150, 112, 48))
            sea.px(img, lx + 1, hy + 2, (150, 112, 48))
    return drawn(draw, seed)


def tinkerers():
    return [tinkerer((232, 232, 228), (52, 52, 64), (40, 40, 44), (80, 170, 220), 9301),
            tinkerer((210, 214, 206), (110, 40, 36), (200, 170, 60), (90, 210, 120), 9302),
            tinkerer((236, 230, 214), (40, 60, 70), (60, 80, 120), (220, 120, 60), 9303),
            tinkerer((200, 206, 210), (70, 60, 50), (30, 30, 30), (200, 60, 200), 9304)]


# ---- the Judge -----------------------------------------------------------------------------------------------------

def judge(cloth, stole, seed):
    def draw(img, rnd, tone):
        robe(img, rnd, cloth, 4)
        sea.hands(img, rnd, tone)
        skirt_layer(img, rnd, cloth)
        fx, fy, fw, fh = BODY["front"]
        # white bands at the throat, a stole down either side of the chest, a gold chain of office
        sea.paint(img, (fx + 3, fy, 2, 3), (236, 234, 228), 3, rnd)
        for x in (fx + 1, fx + 6):
            sea.paint(img, (x, fy, 1, fh), stole, 4, rnd)
        for x in range(fx + 1, fx + 7):
            if x not in (fx + 3, fx + 4):
                sea.px(img, x, fy + 3 + abs(x - fx - 3) // 2, GOLD)
        bx, by, bw, bh = BODY["back"]
        for x in (bx + 1, bx + 6):
            sea.paint(img, (x, by, 1, 4), stole, 4, rnd)
        for arm in ARMS:
            sea.rows(img, arm, 7, 2, sea.shade(cloth, 26), 3, rnd)
    return drawn(draw, seed)


def judges():
    return [judge((26, 24, 28), (150, 30, 36), 9401), judge((30, 28, 34), (96, 40, 130), 9402),
            judge((96, 20, 30), (210, 170, 70), 9403), judge((50, 52, 58), (40, 70, 140), 9404)]


# ---- the road's master ---------------------------------------------------------------------------------------------

def road_master(gi, under, belt, seed):
    def draw(img, rnd, tone):
        sea.box(img, BODY, gi, 6, rnd)
        for arm in ARMS:
            sea.box(img, arm, gi, 6, rnd)
            sea.rows(img, arm, 7, 1, sea.shade(gi, -26), 4, rnd)
        sea.hands(img, rnd, tone)
        # the wrapped front: a V of the undershirt, the left side over the right
        fx, fy, fw, fh = BODY["front"]
        for y in range(5):
            for x in range(fx + 3 - (4 - y) // 2, fx + 5 + (4 - y) // 2):
                if y < 4:
                    sea.px(img, x, fy + y, under)
        for y in range(7):
            sea.px(img, fx + 2 + y // 2, fy + y, sea.shade(gi, -30))
        # a wide belt, knotted at the front
        sea.rows(img, BODY, 7, 2, belt, 5, rnd)
        sea.paint(img, (fx + 2, fy + 9, 1, 3), belt, 5, rnd)
        sea.paint(img, (fx + 4, fy + 9, 1, 2), belt, 5, rnd)
        # loose trousers, shins wrapped, plain shoes
        for leg in LEGS:
            sea.box(img, leg, sea.shade(gi, -18), 6, rnd)
            sea.rows(img, leg, 7, 3, (210, 204, 186), 6, rnd)
            sea.rows(img, leg, 10, 2, (52, 40, 30), 4, rnd)
            sea.paint(img, leg["bottom"], (52, 40, 30), 4, rnd)
    return drawn(draw, seed)


def road_masters():
    return [road_master((80, 100, 130), (210, 204, 186), (40, 40, 44), 9501), road_master((196, 112, 52), (50, 60, 110), (40, 50, 100), 9502),
            road_master((120, 92, 64), (226, 220, 200), (60, 40, 26), 9503), road_master((96, 116, 90), (220, 214, 196), (150, 40, 34), 9504)]


# ---- the fortune teller --------------------------------------------------------------------------------------------

def teller(robe_c, shawl, scarf, seed):
    def draw(img, rnd, tone):
        robe(img, rnd, robe_c, 6)
        sea.hands(img, rnd, tone)
        skirt_layer(img, rnd, robe_c)
        # a fringed shawl over the shoulders and down to the waist (the jacket layer), beads at the throat
        for side in SIDES:
            x, y, w, h = JACKET[side]
            sea.paint(img, (x, y, w, 6), shawl, 6, rnd)
            for fxx in range(x, x + w, 2):
                sea.px(img, fxx, y + 6, sea.shade(shawl, -30))
        sea.paint(img, JACKET["top"], shawl, 6, rnd)
        fx, fy, fw, fh = JACKET["front"]
        for x in range(fx + 2, fx + 6):
            img.putpixel((x, fy), (0, 0, 0, 0))
        bx, by, bw, bh = BODY["front"]
        for x in range(bx + 2, bx + 6):
            sea.px(img, x, by + 1 + (1 if x in (bx + 3, bx + 4) else 0), GOLD if x % 2 else (60, 160, 170))
        for sleeve in SLEEVES:
            for side in SIDES:
                x, y, w, h = sleeve[side]
                sea.paint(img, (x, y, w, 3), shawl, 6, rnd)
        # bangles at the wrists
        for arm in ARMS:
            sea.rows(img, arm, 8, 1, GOLD, 10, rnd)
        # a headscarf tied over the hair, coins along its edge
        sea.bandana(img, rnd, scarf)
        hx, hy, hw, hh = HAT["front"]
        for x in range(hx, hx + hw, 2):
            sea.px(img, x, hy + 2, GOLD)
    return drawn(draw, seed)


def tellers():
    return [teller((92, 40, 120), (170, 60, 90), (200, 60, 70), 9601), teller((120, 24, 36), (60, 40, 90), (220, 170, 60), 9602),
            teller((30, 96, 100), (150, 110, 60), (90, 30, 110), 9603), teller((34, 36, 80), (120, 40, 110), (40, 120, 120), 9604)]


# ---- a demon master ------------------------------------------------------------------------------------------------

def demon_master(gi, sash, spike, seed):
    def draw(img, rnd, tone):
        sea.box(img, BODY, gi, 5, rnd)
        for arm in ARMS:
            sea.box(img, arm, gi, 5, rnd)
        sea.hands(img, rnd, tone)
        # a deep V to the belt, a red sash, wrapped forearms
        fx, fy, fw, fh = BODY["front"]
        for y in range(6):
            for x in range(fx + 3 - (5 - y) // 3, fx + 5 + (5 - y) // 3):
                img.putpixel((x, fy + y), (0, 0, 0, 0))
        sea.rows(img, BODY, 7, 2, sash, 5, rnd)
        sea.paint(img, (fx + 5, fy + 9, 2, 3), sash, 5, rnd)
        for arm in ARMS:
            sea.rows(img, arm, 6, 3, sea.shade(sash, -40), 4, rnd)
        for leg in LEGS:
            sea.box(img, leg, sea.shade(gi, 10), 5, rnd)
            sea.rows(img, leg, 9, 3, (24, 20, 20), 3, rnd)
            sea.paint(img, leg["bottom"], (24, 20, 20), 3, rnd)
        # spiked pauldrons: the sleeves' tops and upper rows, a bone-white spike on top
        for sleeve in SLEEVES:
            for side in SIDES:
                x, y, w, h = sleeve[side]
                sea.paint(img, (x, y, w, 3), (36, 32, 34), 4, rnd)
            sx, sy, sw, sh = sleeve["top"]
            sea.paint(img, (sx, sy, sw, sh), (36, 32, 34), 4, rnd)
            sea.px(img, sx + 1, sy + 1, spike)
            sea.px(img, sx + 2, sy + 2, spike)
            for side in ("front", "back"):
                x, y, w, h = sleeve[side]
                sea.px(img, x + 1, y, spike)
                sea.px(img, x + 2, y, spike)
    return drawn(draw, seed)


def demon_masters():
    return [demon_master((28, 24, 26), (150, 24, 24), (230, 220, 200), 9701), demon_master((80, 76, 74), (210, 100, 30), (240, 200, 120), 9702),
            demon_master((52, 28, 70), (24, 20, 24), (200, 120, 230), 9703), demon_master((110, 20, 24), (30, 26, 28), (230, 220, 200), 9704)]


def all_outfits():
    out = {}
    uni.numbered(out, "zfighter/keeper", keepers())
    uni.numbered(out, "zfighter/hermit", hermits())
    uni.numbered(out, "zfighter/tinkerer", tinkerers())
    uni.numbered(out, "zfighter/judge", judges())
    uni.numbered(out, "zfighter/road_master", road_masters())
    uni.numbered(out, "zfighter/teller", tellers())
    uni.numbered(out, "zfighter/demon_master", demon_masters())
    return out


if __name__ == "__main__":
    outfits = all_outfits()
    for name, img in outfits.items():
        uni.save(img, name)
    uni.preview(outfits, "build/zfighter-outfits-preview.png")
