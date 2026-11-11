"""Draws the lore films' actors' skins, all original (64x64, the humanoid layout). The Long Defence's five mages are the
living people the spirits were (SpiritFigure's makes, with faces and legs), and the sorcerer is the Grue before it was
the Grue:
- Ambrose: an elder, a long white beard under bushy brows, a blue-grey hooded robe with a gold stole, a satchel strap.
- Grimsby: a lean traveller, dark hair and stubble, a brown cowl and a long cloak gone ragged at the hem, a scabbard at his hip.
- Thessaly: slim (the slim arms), long dark hair down her back and in side locks under a silver circlet, a high collar,
  a teal gown with bell sleeves, a grey shawl.
- Varro: a warlord, cropped grey hair, a short beard and a scar, a red tunic and a war belt (he wears iron plate over it).
- The sorcerer: a hood and robe of near-black purple, a grey face, eyes that glow violet, clawed grey hands.
- The fifth: a bone-white hooded cloak over grey wrappings, a face in shadow, so that he looks like none of the others.
- Soldiers (soldier_0-2): hearthlanders in padded coats under iron harness and open helms (painted on, so it bends with them).
The sorcerer's living host, and the dead he raises:
- Orcs (orc_0-2): an in-game orc's green under an orc war camp grunt's clothes, the real orcs' marks and scars painted
  in, and the patches the film actor's tusks, brow and ears are painted from.
(The Warrior-born of the host are draugr drawn alive: tools/gen_draugr_living.py.)
- Dead orcs (dead_orc_0-2): the same, gone ashen and rotten, the clothes in rags, ribs showing, pale dead eyes.
Run from the kingdoms project root: python -I tools/gen_film_skins.py"""
from PIL import Image
import os
import random

ROOT = "src/main/resources/assets/jaymoskingdoms/textures/entity/film/"


def faces(ox, oy, w, h, d):
    return {"top": (ox + d, oy, w, d), "bottom": (ox + d + w, oy, w, d), "right": (ox, oy + d, d, h), "front": (ox + d, oy + d, w, h),
            "left": (ox + d + w, oy + d, d, h), "back": (ox + d + w + d, oy + d, w, h)}


HEAD = faces(0, 0, 8, 8, 8)
HAT = faces(32, 0, 8, 8, 8)
BODY = faces(16, 16, 8, 12, 4)
JACKET = faces(16, 32, 8, 12, 4)
LEGS = [faces(0, 16, 4, 12, 4), faces(16, 48, 4, 12, 4)]
TROUSERS = [faces(0, 32, 4, 12, 4), faces(0, 48, 4, 12, 4)]
SIDES = ("front", "back", "right", "left")

WHITE = (214, 208, 192)
GOLD = (206, 168, 64)
SILVER = (196, 200, 208)
LEATHER = (96, 66, 40)


def arms(slim):
    w = 3 if slim else 4
    return [faces(40, 16, w, 12, 4), faces(32, 48, w, 12, 4)]


def sleeves(slim):
    w = 3 if slim else 4
    return [faces(40, 32, w, 12, 4), faces(48, 48, w, 12, 4)]


def clamp(c):
    return tuple(max(0, min(255, v)) for v in c)


def shade(c, by):
    return clamp(tuple(v + by for v in c))


def px(img, x, y, colour):
    img.putpixel((x, y), clamp(colour) + (255,))


def paint(img, rect, base, spread, rnd):
    x0, y0, w, h = rect
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            px(img, x, y, shade(base, rnd.randint(-spread, spread)))


def box(img, b, base, spread, rnd):
    for rect in b.values():
        paint(img, rect, base, spread, rnd)


def rows(img, b, top, count, base, spread, rnd, sides=SIDES):
    for side in sides:
        x0, y0, w, h = b[side]
        paint(img, (x0, y0 + top, w, max(0, min(count, h - top))), base, spread, rnd)


def clear(img, rect):
    x0, y0, w, h = rect
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            img.putpixel((x, y), (0, 0, 0, 0))


def face(img, rnd, skin, hair, eyes, brows=None, glow=False, shadow=False):
    box(img, HEAD, skin, 5, rnd)
    paint(img, HEAD["top"], hair, 6, rnd)
    for side in ("back", "right", "left"):
        x0, y0, w, h = HEAD[side]
        paint(img, (x0, y0, w, 3 if side != "back" else 6), hair, 6, rnd)
    fx, fy, fw, fh = HEAD["front"]
    paint(img, (fx, fy, fw, 1), hair, 6, rnd)
    if shadow:
        # a face lost in the hood's shadow: only the eyes catch any light
        paint(img, (fx, fy, fw, fh), shade(skin, -70), 4, rnd)
        px(img, fx + 2, fy + 4, shade(skin, -30))
        px(img, fx + 5, fy + 4, shade(skin, -30))
        return
    for ex in (fx + 1, fx + 5):
        px(img, ex, fy + 3, eyes if glow else WHITE)
        px(img, ex + 1, fy + 3, eyes)
    if brows:
        for x in (fx + 1, fx + 2, fx + 5, fx + 6):
            px(img, x, fy + 2, brows)
    px(img, fx + 3, fy + 4, shade(skin, -24))
    px(img, fx + 4, fy + 4, shade(skin, -18))
    for x in range(fx + 2, fx + 6):
        px(img, x, fy + 6, shade(skin, -46))


def beard(img, rnd, colour, long=False):
    fx, fy, fw, fh = HEAD["front"]
    for y in (fy + 5, fy + 6, fy + 7):
        for x in range(fx, fx + 8):
            if not (y == fy + 6 and fx + 3 <= x < fx + 5):
                px(img, x, y, shade(colour, rnd.randint(-8, 8)))
    for side in ("right", "left"):
        x0, y0, w, h = HEAD[side]
        paint(img, (x0, y0 + 5, w, 3), colour, 6, rnd)
    if long:
        # down the chest, on the jacket layer so it stands off the robe
        jx, jy, jw, jh = JACKET["front"]
        for y in range(jy, jy + 5):
            inset = max(0, y - jy - 2)
            paint(img, (jx + 2 + inset // 2, y, 4 - inset, 1), colour, 8, rnd)


def stubble(img, rnd, colour):
    fx, fy, fw, fh = HEAD["front"]
    for y in (fy + 5, fy + 6, fy + 7):
        for x in range(fx + 1, fx + 7):
            if rnd.random() < 0.45 and not (y == fy + 6 and fx + 2 <= x < fx + 6):
                px(img, x, y, shade(colour, rnd.randint(-6, 12)))


def hood(img, rnd, cloth, open_face=True):
    """A hood on the hat layer: over the top, back and sides, framing the face."""
    paint(img, HAT["top"], cloth, 6, rnd)
    for side in ("back", "right", "left"):
        paint(img, HAT[side], cloth, 6, rnd)
    fx, fy, fw, fh = HAT["front"]
    paint(img, (fx, fy, fw, 2), cloth, 6, rnd)
    paint(img, (fx, fy + 2, 1, 6), cloth, 6, rnd)
    paint(img, (fx + 7, fy + 2, 1, 6), cloth, 6, rnd)
    if not open_face:
        paint(img, (fx + 1, fy + 2, 6, 1), shade(cloth, -12), 4, rnd)


def robe(img, rnd, cloth, slim=False, ragged=False):
    """A long robe: body, sleeves to the wrist, and on down both legs (the trousers layer, so it hangs over them)."""
    box(img, BODY, cloth, 6, rnd)
    for arm in arms(slim):
        box(img, arm, cloth, 6, rnd)
    for leg in LEGS:
        box(img, leg, shade(cloth, -10), 6, rnd)
    for trouser in TROUSERS:
        for side in SIDES:
            x0, y0, w, h = trouser[side]
            paint(img, (x0, y0, w, h - 1), cloth, 6, rnd)
            if ragged:
                for x in range(x0, x0 + w):
                    cut = rnd.randint(0, 3)
                    for y in range(y0 + h - 1 - cut, y0 + h):
                        img.putpixel((x, y), (0, 0, 0, 0))
        paint(img, trouser["top"], cloth, 6, rnd)


def hands(img, rnd, skin, slim=False, claws=None):
    for arm in arms(slim):
        for side in SIDES:
            x0, y0, w, h = arm[side]
            paint(img, (x0, y0 + h - 2, w, 2), skin, 6, rnd)
            if claws:
                for x in range(x0, x0 + w, 2):
                    px(img, x, y0 + h - 1, claws)
        paint(img, arm["bottom"], claws or skin, 6, rnd)


def boots(img, rnd, colour):
    for leg in LEGS:
        rows(img, leg, 9, 3, colour, 5, rnd)
        paint(img, leg["bottom"], colour, 5, rnd)


def belt(img, rnd, colour, buckle):
    rows(img, BODY, 7, 1, colour, 4, rnd)
    fx, fy, fw, fh = BODY["front"]
    px(img, fx + 3, fy + 7, buckle)
    px(img, fx + 4, fy + 7, buckle)


def strap(img, rnd, colour):
    """A strap from the right shoulder to the left hip, front and back."""
    for side in ("front", "back"):
        x0, y0, w, h = BODY[side]
        for i in range(8):
            x = x0 + (i if side == "front" else w - 1 - i)
            px(img, x, y0 + i, shade(colour, rnd.randint(-6, 6)))


IRON = (150, 154, 160)
# three patches in the layout's unused corner that the film actor's extra bones are painted from (tools/gen_film_actor.py):
# an orc's flesh (brow, ears, jaw) and ivory (tusks), and the fifth's cloak cloth
FLESH_PATCH, IVORY_PATCH, CLOTH_PATCH = (56, 16, 8, 8), (56, 24, 8, 8), (56, 32, 8, 16)
MARKS = "src/main/resources/assets/jaymoskingdoms/textures/entity/greenskin/"


def plate(img, rnd, helm=True, tabard=None):
    """Iron harness over whatever is worn, on the outer layers so it stands off the body: a cuirass (a tabard's colour
    down its front, if any), pauldrons and vambraces, tassets and greaves, and an open helm with a nasal."""
    box(img, JACKET, IRON, 7, rnd)
    jx, jy, jw, jh = JACKET["front"]
    for x in range(jx, jx + jw):
        px(img, x, jy, shade(IRON, 22))
        px(img, x, jy + 7, shade(IRON, -34))
    if tabard:
        paint(img, (jx + 2, jy + 1, 4, 10), tabard, 6, rnd)
    for sleeve in sleeves(False):
        rows(img, sleeve, 0, 4, shade(IRON, 8), 7, rnd)
        rows(img, sleeve, 7, 3, shade(IRON, -8), 7, rnd)
        paint(img, sleeve["top"], shade(IRON, 16), 6, rnd)
    for trouser in TROUSERS:
        rows(img, trouser, 0, 3, shade(IRON, -6), 7, rnd)
        rows(img, trouser, 6, 6, IRON, 7, rnd, sides=("front", "right", "left"))
        rows(img, trouser, 9, 3, shade(IRON, -18), 6, rnd)
    if helm:
        paint(img, HAT["top"], shade(IRON, 12), 6, rnd)
        for side in ("back", "right", "left"):
            x0, y0, w, h = HAT[side]
            paint(img, (x0, y0, w, 6), IRON, 7, rnd)
        hx, hy, hw, hh = HAT["front"]
        paint(img, (hx, hy, hw, 3), IRON, 7, rnd)
        for y in range(hy + 3, hy + 6):
            px(img, hx + 3, y, shade(IRON, -16))
            px(img, hx + 4, y, shade(IRON, -22))
            px(img, hx, y, shade(IRON, -10))
            px(img, hx + 7, y, shade(IRON, -10))


def soldier(i):
    """A hearthlander of the line: a plain face, a padded coat in the Keep's colours, iron harness and helm over it."""
    rnd = random.Random(9400 + i)
    img = blank()
    skin = [(196, 150, 112), (222, 184, 150), (150, 104, 72)][i % 3]
    hair = [(70, 48, 30), (150, 110, 60), (30, 26, 24)][i % 3]
    coat = [(52, 70, 104), (96, 40, 36), (70, 84, 56)][i % 3]
    face(img, rnd, skin, hair, (50, 40, 34), brows=shade(hair, -10))
    if i == 1:
        stubble(img, rnd, shade(hair, -20))
    box(img, BODY, coat, 6, rnd)
    for arm in arms(False):
        box(img, arm, shade(coat, -8), 6, rnd)
    for leg in LEGS:
        box(img, leg, (66, 56, 46), 6, rnd)
    boots(img, rnd, (48, 38, 30))
    hands(img, rnd, LEATHER)
    plate(img, rnd, tabard=coat)
    save(img, "soldier_%d" % i)


def orc_patches(img, rnd, skin, marks=None, scars=None):
    """An orc's own: the patches its brow, ears and tusks are painted from, and one of the real orcs' sets of marks
    (tinted by its green) and scars (as they are), which GreenskinLooks draws on an orc in play."""
    paint(img, FLESH_PATCH, shade(skin, -8), 5, rnd)
    paint(img, IVORY_PATCH, (226, 214, 180), 6, rnd)
    if marks is not None:
        over = Image.open(MARKS + "orc_marks_%d.png" % marks).convert("RGBA").resize((64, 64), Image.NEAREST)
        o = over.load()
        for y in range(64):
            for x in range(64):
                r, g, b, a = o[x, y]
                o[x, y] = (r * skin[0] // 255, g * skin[1] // 255, b * skin[2] // 255, a)
        img.alpha_composite(over)
    if scars is not None:
        img.alpha_composite(Image.open(MARKS + "orc_scars_%d.png" % scars).convert("RGBA").resize((64, 64), Image.NEAREST))


def blank():
    return Image.new("RGBA", (64, 64), (0, 0, 0, 0))


def ambrose():
    rnd = random.Random(9101)
    img = blank()
    robe_c = (78, 92, 118)
    face(img, rnd, (204, 164, 132), (226, 224, 218), (60, 80, 120), brows=(238, 236, 230))
    beard(img, rnd, (232, 230, 224), long=True)
    robe(img, rnd, robe_c)
    hood(img, rnd, shade(robe_c, -8))
    # the stole: two gold bands from the collar down the front
    fx, fy, fw, fh = BODY["front"]
    for y in range(fy, fy + 12):
        px(img, fx + 1, y, shade(GOLD, rnd.randint(-10, 10)))
        px(img, fx + 6, y, shade(GOLD, rnd.randint(-10, 10)))
    strap(img, rnd, LEATHER)
    hands(img, rnd, (204, 164, 132))
    boots(img, rnd, (60, 46, 34))
    save(img, "ambrose")


def grimsby():
    rnd = random.Random(9102)
    img = blank()
    cloak = (92, 72, 52)
    skin = (190, 150, 118)
    face(img, rnd, skin, (44, 34, 28), (70, 90, 70), brows=(40, 30, 24))
    # a Sylvan elf (Andy: "Grimsby is a sylvan elf, thats why he lived so long"): beardless, and his ears (the film
    # actor's "elf" bone) are painted from this patch of his skin
    paint(img, FLESH_PATCH, shade(skin, -6), 4, rnd)
    # gaunt: hollows under the cheekbones
    fx, fy, fw, fh = HEAD["front"]
    px(img, fx + 1, fy + 5, shade(skin, -28))
    px(img, fx + 6, fy + 5, shade(skin, -28))
    box(img, BODY, (70, 74, 70), 6, rnd)
    for arm in arms(False):
        box(img, arm, (70, 74, 70), 6, rnd)
    for leg in LEGS:
        box(img, leg, (58, 52, 44), 6, rnd)
    boots(img, rnd, (52, 38, 26))
    belt(img, rnd, LEATHER, (150, 150, 150))
    # the scabbard down his left hip
    lx, ly, lw, lh = BODY["left"]
    paint(img, (lx + 1, ly + 7, 2, 5), (40, 30, 22), 4, rnd)
    hood(img, rnd, cloak)
    # the cloak, ragged at its hem, over his back and shoulders
    for side in ("back", "right", "left"):
        x0, y0, w, h = JACKET[side]
        paint(img, (x0, y0, w, h), cloak, 7, rnd)
        for x in range(x0, x0 + w):
            for y in range(y0 + h - rnd.randint(0, 3), y0 + h):
                img.putpixel((x, y), (0, 0, 0, 0))
    paint(img, JACKET["top"], cloak, 7, rnd)
    jx, jy, jw, jh = JACKET["front"]
    paint(img, (jx, jy, 2, jh - 2), cloak, 7, rnd)
    paint(img, (jx + 6, jy, 2, jh - 2), cloak, 7, rnd)
    hands(img, rnd, skin)
    save(img, "grimsby")


def thessaly():
    rnd = random.Random(9103)
    img = blank()
    gown = (46, 104, 108)
    hair = (36, 26, 30)
    skin = (222, 184, 158)
    face(img, rnd, skin, hair, (80, 120, 140))
    fx, fy, fw, fh = HEAD["front"]
    # lips
    px(img, fx + 3, fy + 6, (166, 92, 92))
    px(img, fx + 4, fy + 6, (166, 92, 92))
    # side locks down past the jaw, and long hair down the back (head and on down the body)
    for side in ("right", "left"):
        x0, y0, w, h = HEAD[side]
        paint(img, (x0, y0, w, h), hair, 6, rnd)
    paint(img, HEAD["back"], hair, 6, rnd)
    paint(img, (fx, fy, 1, 8), hair, 6, rnd)
    paint(img, (fx + 7, fy, 1, 8), hair, 6, rnd)
    bx, by, bw, bh = JACKET["back"]
    paint(img, (bx + 1, by, bw - 2, 9), hair, 6, rnd)
    # the circlet on the hat layer, a stone at the brow
    for side in SIDES:
        x0, y0, w, h = HAT[side]
        for x in range(x0, x0 + w):
            px(img, x, y0 + 1, shade(SILVER, rnd.randint(-10, 10)))
    hx, hy, hw, hh = HAT["front"]
    px(img, hx + 3, hy + 1, (90, 200, 200))
    px(img, hx + 4, hy + 1, (90, 200, 200))
    robe(img, rnd, gown, slim=True)
    # the high collar and the shawl over her shoulders
    rows(img, BODY, 0, 2, (150, 150, 156), 6, rnd)
    for side in ("front", "right", "left"):
        x0, y0, w, h = JACKET[side]
        paint(img, (x0, y0, w, 3), (128, 128, 134), 6, rnd)
    paint(img, JACKET["top"], (128, 128, 134), 6, rnd)
    # bell sleeves: the cuff widened onto the sleeve layer
    for sleeve in sleeves(True):
        for side in SIDES:
            x0, y0, w, h = sleeve[side]
            paint(img, (x0, y0 + h - 4, w, 3), shade(gown, 12), 5, rnd)
    hands(img, rnd, skin, slim=True)
    save(img, "thessaly")


def varro():
    rnd = random.Random(9104)
    img = blank()
    skin = (184, 136, 100)
    hair = (120, 116, 110)
    face(img, rnd, skin, hair, (90, 70, 50), brows=(90, 86, 80))
    beard(img, rnd, (104, 100, 94))
    fx, fy, fw, fh = HEAD["front"]
    # a scar down through the right brow
    for y in (fy + 1, fy + 2, fy + 3, fy + 4):
        px(img, fx + 1, y, (150, 96, 86))
    tunic = (128, 32, 30)
    box(img, BODY, tunic, 6, rnd)
    for arm in arms(False):
        box(img, arm, tunic, 6, rnd)
    for leg in LEGS:
        box(img, leg, (62, 50, 42), 6, rnd)
    boots(img, rnd, (44, 34, 28))
    belt(img, rnd, (52, 36, 24), GOLD)
    hands(img, rnd, skin)
    plate(img, rnd, helm=False)
    save(img, "varro")


def sorcerer():
    rnd = random.Random(9105)
    img = blank()
    cloth = (30, 20, 38)
    skin = (128, 124, 132)
    face(img, rnd, skin, (20, 16, 22), (186, 90, 255), glow=True)
    fx, fy, fw, fh = HEAD["front"]
    # cracks of shadow over the cheeks
    for (x, y) in ((1, 4), (1, 5), (2, 6), (6, 4), (6, 5), (5, 6)):
        px(img, fx + x, fy + y, (52, 40, 62))
    robe(img, rnd, cloth, ragged=True)
    hood(img, rnd, shade(cloth, -6), open_face=False)
    # a violet trim at the hood's edge and down the robe's front
    hx, hy, hw, hh = HAT["front"]
    for y in range(hy + 2, hy + 8):
        px(img, hx, y, (84, 40, 110))
        px(img, hx + 7, y, (84, 40, 110))
    bx, by, bw, bh = BODY["front"]
    for y in range(by, by + 12):
        px(img, bx + 3, y, (70, 34, 92))
        px(img, bx + 4, y, (70, 34, 92))
    hands(img, rnd, skin, claws=(20, 16, 20))
    save(img, "sorcerer")


def stranger():
    """The fifth (Andy, 2026-11-11): the white cloak's first wearer. A hood and cloak of bone white over grey wrappings,
    the face lost in the hood, nothing on him that any of the four spirits wears. The cloak itself (the film actor's
    cape and tethers) is painted from the cloth patch."""
    rnd = random.Random(9106)
    img = blank()
    cloth = (226, 224, 214)
    wrap = (128, 130, 134)
    face(img, rnd, (176, 140, 112), (60, 56, 52), (60, 60, 60), shadow=True)
    box(img, BODY, wrap, 6, rnd)
    for arm in arms(False):
        box(img, arm, wrap, 6, rnd)
        # bindings up the forearm
        for side in SIDES:
            x0, y0, w, h = arm[side]
            for y in (y0 + 6, y0 + 8):
                paint(img, (x0, y, w, 1), shade(cloth, -30), 4, rnd)
    for leg in LEGS:
        box(img, leg, shade(wrap, -20), 6, rnd)
    boots(img, rnd, (86, 86, 88))
    hands(img, rnd, shade(wrap, -30))
    # the cloak's mantle over the shoulders and chest, open down the front
    box(img, JACKET, cloth, 6, rnd)
    jx, jy, jw, jh = JACKET["front"]
    for y in range(jy + 3, jy + 12):
        for x in (jx + 3, jx + 4):
            img.putpixel((x, y), (0, 0, 0, 0))
    for y in range(jy + 6, jy + 12):
        for x in (jx + 2, jx + 5):
            img.putpixel((x, y), (0, 0, 0, 0))
    for sleeve in sleeves(False):
        rows(img, sleeve, 0, 5, cloth, 6, rnd)
        paint(img, sleeve["top"], cloth, 6, rnd)
    hood(img, rnd, cloth, open_face=False)
    hx, hy, hw, hh = HAT["front"]
    for y in range(hy + 2, hy + 8):
        px(img, hx, y, shade(cloth, -26))
        px(img, hx + 7, y, shade(cloth, -26))
    belt(img, rnd, (70, 72, 78), SILVER)
    # the cloth: brighter at the shoulders, greyer and ragged toward the hem
    x0, y0, w, h = CLOTH_PATCH
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            px(img, x, y, shade(cloth, -(y - y0) * 3 + rnd.randint(-6, 6)))
    for x in range(x0, x0 + w):
        for y in range(y0 + h - rnd.randint(0, 2), y0 + h):
            img.putpixel((x, y), (0, 0, 0, 0))
    save(img, "stranger")
    return img


def stranger_rags():
    """The fifth before his time comes (Andy: "Before then he is just in tattered cloths"): the same man with no hood and
    no cloak, in what is left of a drab tunic and trousers, torn through to the skin, barefoot, a rope for a belt."""
    rnd = random.Random(9107)
    img = blank()
    skin, hair = (176, 140, 112), (60, 56, 52)
    drab, dark = (112, 98, 80), (78, 70, 62)
    face(img, rnd, skin, hair, (60, 60, 60), brows=hair)
    stubble(img, rnd, hair)
    box(img, BODY, drab, 8, rnd)
    for arm in arms(False):
        box(img, arm, skin, 5, rnd)
        # what is left of a sleeve: to the elbow on one side, a strip of it lower
        rows(img, arm, 0, 4, drab, 8, rnd)
        for side in SIDES:
            x0, y0, w, h = arm[side]
            px(img, x0 + rnd.randrange(w), y0 + 4, drab)
            paint(img, (x0, y0 + 8, w, 1), shade(dark, 10), 6, rnd)
    for leg in LEGS:
        box(img, leg, dark, 8, rnd)
        # the hems gone, the shins and feet bare
        rows(img, leg, 9, 3, skin, 5, rnd)
        paint(img, leg["bottom"], shade(skin, -20), 4, rnd)
        for side in SIDES:
            x0, y0, w, h = leg[side]
            for x in range(x0, x0 + w):
                if rnd.random() < 0.5:
                    px(img, x, y0 + 8, skin)
    # torn through at the chest and side, and a rope round the waist
    for side, holes in (("front", 5), ("back", 4), ("right", 2), ("left", 2)):
        x0, y0, w, h = BODY[side]
        for _ in range(holes):
            x, y = x0 + rnd.randrange(w), y0 + 1 + rnd.randrange(h - 2)
            px(img, x, y, shade(skin, -10))
            if rnd.random() < 0.5 and x + 1 < x0 + w:
                px(img, x + 1, y + (1 if y + 1 < y0 + h else 0), shade(skin, -16))
        for x in range(x0, x0 + w):
            if rnd.random() < 0.45:
                px(img, x, y0 + h - 1, shade(skin, -14))
        paint(img, (x0, y0 + 8, w, 1), (150, 132, 96), 6, rnd)
    save(img, "stranger_rags")
    return img


def stranger_half(rags, suited):
    """The white suit come halfway over him: his legs and trunk already in it, his arms and head still as they were."""
    img = rags.copy()
    for part in LEGS + TROUSERS + [BODY, JACKET]:
        for x0, y0, w, h in part.values():
            img.paste(suited.crop((x0, y0, x0 + w, y0 + h)), (x0, y0))
    save(img, "stranger_half")


CLOTHES = "src/main/resources/assets/jaymoskingdoms/skins/clothing/greenskin/"
# the in-game orcs' greens (Greenskins.ORC_PALE to ORC_DEEP), a few shades along it; the dead ones greyed and darkened
ORC_SKINS = [(124, 138, 98), (104, 120, 84), (86, 102, 70)]


def orc_base(img, rnd, skin, eyes, glow=False):
    """The plain body an in-game orc has under its look: MCA's skin in an orc's green, its eyes. The tusks, brow, ears,
    marks and scars are drawn on it by GreenskinLooks, as on a real orc."""
    face(img, rnd, skin, (24, 22, 20), eyes, glow=glow)
    box(img, BODY, skin, 4, rnd)
    for arm in arms(False):
        box(img, arm, skin, 4, rnd)
    for leg in LEGS:
        box(img, leg, skin, 4, rnd)


def clothes(img, name, dead=False, rnd=None):
    """An in-game orc's clothes (MCA's clothing layout is the skin's), torn and greyed on the dead."""
    over = Image.open(CLOTHES + name + ".png").convert("RGBA")
    if dead:
        o = over.load()
        for y in range(64):
            for x in range(64):
                r, g, b, a = o[x, y]
                if not a:
                    continue
                if rnd.random() < 0.18:
                    o[x, y] = (0, 0, 0, 0)
                    continue
                m = (r + g + b) // 3
                o[x, y] = ((r + m) // 2 - 10, (g + m) // 2 - 10, (b + m) // 2 - 8, a)
    img.alpha_composite(over)


def orc(i):
    """An orc of the host, dressed as a grunt of an orc war camp (orc_grunt_<i>.png)."""
    rnd = random.Random(9200 + i)
    img = blank()
    orc_base(img, rnd, ORC_SKINS[i % len(ORC_SKINS)], (200, 60, 40) if i != 1 else (220, 180, 40))
    orc_patches(img, rnd, ORC_SKINS[i % len(ORC_SKINS)], marks=i, scars=i + 1)
    clothes(img, "orc_grunt_%d" % i)
    save(img, "orc_%d" % i)


def dead_orc(i):
    """A dead orc the sorcerer raises: ashen and rotten, the ribs showing, a grunt's clothes in rags, pale dead eyes."""
    rnd = random.Random(9300 + i)
    img = blank()
    skin = ORC_SKINS[(i + 1) % len(ORC_SKINS)]
    skin = tuple((v + (sum(skin) // 3)) // 2 - 16 for v in skin)
    orc_base(img, rnd, skin, (190, 210, 200), glow=True)
    fx, fy, fw, fh = HEAD["front"]
    for (x, y) in ((1, 2), (2, 2), (5, 2), (6, 2)):
        px(img, fx + x, fy + y, shade(skin, -45))
    for _ in range(30):
        b = rnd.choice([BODY, arms(False)[0], arms(False)[1], LEGS[0], LEGS[1]])
        x0, y0, w, h = b[rnd.choice(SIDES)]
        px(img, x0 + rnd.randrange(w), y0 + rnd.randrange(h), shade(skin, -40))
    bx, by, bw, bh = BODY["front"]
    for y in (by + 3, by + 5, by + 7):
        for x in range(bx + 1, bx + 7):
            if x not in (bx + 3, bx + 4):
                px(img, x, y, (196, 188, 164))
    orc_patches(img, rnd, skin, marks=(i + 2) % 4)
    clothes(img, "orc_grunt_%d" % ((i + 2) % 4), dead=True, rnd=rnd)
    save(img, "dead_orc_%d" % i)


def save(img, name):
    os.makedirs(ROOT, exist_ok=True)
    img.save(ROOT + name + ".png")


if __name__ == "__main__":
    ambrose()
    grimsby()
    thessaly()
    varro()
    sorcerer()
    stranger_half(stranger_rags(), stranger())
    for i in range(3):
        soldier(i)
    for i in range(3):
        orc(i)
    for i in range(3):
        dead_orc(i)
