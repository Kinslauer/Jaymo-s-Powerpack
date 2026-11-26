"""Paints the four skins of the cave band, the barbaric Warrior-born of the Primordial Caves (ZFighterCaveBand; spec:
docs/Cave-Brute-Skin-Spec.md). Every pixel is painted here onto the vanilla 64x64 wide player skin layout; nothing is
read from any other mod or skin.

    python -I tools/gen_cave_brute_skins.py              writes textures/entity/rogue_fighter/brute_0..3.png
    python -I tools/gen_cave_brute_skins.py --sheet F    also writes a front/back sheet of all four to F

Needs Pillow. Each skin is seeded from its own number, so a rerun changes nothing.
"""
import os
import random
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "jaymospowers", "textures", "entity", "rogue_fighter")

# ---- the player skin layout: part -> (u, v, w, h, d) of its box; overlay offsets --------------------------------------
PARTS = {
    "head": (0, 0, 8, 8, 8), "body": (16, 16, 8, 12, 4), "rarm": (40, 16, 4, 12, 4), "larm": (32, 48, 4, 12, 4),
    "rleg": (0, 16, 4, 12, 4), "lleg": (16, 48, 4, 12, 4),
}
OVER = {"head": (32, 0), "body": (16, 32), "rarm": (40, 32), "larm": (48, 48), "rleg": (0, 32), "lleg": (0, 48)}


def faces(u, v, w, h, d):
    """Each face's rectangle (x, y, width, height) in the vanilla box layout."""
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
            "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}


def hx(s):
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def shade(c, k):
    return tuple(max(0, min(255, int(ch * k))) for ch in c[:3]) + (c[3],)


class Skin:
    def __init__(self, seed):
        self.im = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
        self.px = self.im.load()
        self.r = random.Random(seed)

    def rect(self, x, y, w, h, colour, jitter=0.0):
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                k = 1.0 + self.r.uniform(-jitter, jitter) if jitter else 1.0
                self.px[xx, yy] = shade(colour, k)

    def face(self, part, side, over=False):
        u, v, w, h, d = PARTS[part]
        if over:
            u, v = OVER[part]
        return faces(u, v, w, h, d)[side]

    def paint(self, part, colour, jitter=0.06, over=False, rows=None, sides=None):
        """Fills a part's faces (or only some), optionally only rows [a, b) of the side faces (top of the box is row 0)."""
        u, v, w, h, d = PARTS[part]
        for side, (x, y, fw, fh) in faces(*(OVER[part] if over else (u, v)), w, h, d).items():
            if sides and side not in sides:
                continue
            if rows is None or side in ("top", "bottom"):
                if rows is not None and side == "bottom" and rows[1] < h:
                    continue
                if rows is not None and side == "top" and rows[0] > 0:
                    continue
                self.rect(x, y, fw, fh, colour, jitter)
            else:
                a, b = rows
                self.rect(x, y + a, fw, max(0, min(b, fh) - a), colour, jitter)

    def dot(self, part, side, x, y, colour, over=False):
        fx, fy, fw, fh = self.face(part, side, over)
        if 0 <= x < fw and 0 <= y < fh:
            self.px[fx + x, fy + y] = colour

    def save(self, path):
        self.im.save(path)


# ---- the four looks ----------------------------------------------------------------------------------------------------
TONES = [hx("b07a52"), hx("7a4e32"), hx("c48a62"), hx("a8805a")]
HAIRS = [hx("1e1a18"), hx("3a2618"), hx("241c1a"), hx("4a3020")]
HIDE = hx("8a5a34")
HIDE_DARK = hx("5e3c22")
FUR = hx("6e6258")
FUR_LIGHT = hx("a49684")
BONE = hx("e6dcc4")
CORD = hx("4a3424")
OCHRE = hx("b4502c")


def body_and_face(s, tone, hair, beard):
    for part in PARTS:
        s.paint(part, tone, 0.05)
    # the face: heavy brow, dark eyes, a broad nose, maybe a beard; wild hair all round
    s.paint("head", hair, 0.12, sides=("top", "back"))
    for side in ("right", "left"):
        fx, fy, fw, fh = s.face("head", side)
        s.rect(fx, fy, fw, 3, hair, 0.12)
        s.rect(fx + (0 if side == "left" else 4), fy + 3, 4, 3, hair, 0.12)
    fx, fy, fw, fh = s.face("head", "front")
    s.rect(fx, fy, 8, 2, hair, 0.12)
    s.rect(fx, fy + 2, 1, 2, hair, 0.1)
    s.rect(fx + 7, fy + 2, 1, 2, hair, 0.1)
    brow = shade(tone, 0.7)
    s.rect(fx + 1, fy + 3, 6, 1, brow)
    for ex in (1, 5):
        s.px[fx + ex, fy + 4] = (240, 232, 220, 255)
        s.px[fx + ex + 1, fy + 4] = (36, 26, 20, 255)
    s.px[fx + 2, fy + 4] = (36, 26, 20, 255)
    s.px[fx + 5, fy + 4] = (36, 26, 20, 255)
    s.px[fx + 1, fy + 4] = (240, 232, 220, 255)
    s.px[fx + 6, fy + 4] = (240, 232, 220, 255)
    s.rect(fx + 3, fy + 5, 2, 1, shade(tone, 0.8))
    s.rect(fx + 2, fy + 6, 4, 1, shade(tone, 0.6))
    if beard:
        s.rect(fx + 1, fy + 6, 6, 2, hair, 0.12)
        s.rect(fx + 3, fy + 6, 2, 1, shade(tone, 0.55))
        s.rect(fx, fy + 5, 1, 3, hair, 0.1)
        s.rect(fx + 7, fy + 5, 1, 3, hair, 0.1)
    # hair that stands out: the hat layer, shaggy along the top and down the back
    s.paint("head", hair, 0.14, over=True, sides=("top",))
    for side in ("back", "left", "right", "front"):
        x, y, w, h = s.face("head", side, over=True)
        for xx in range(w):
            depth = s.r.randint(1, 3) if side != "front" else s.r.choice((0, 1, 1, 2))
            if side == "back":
                depth = s.r.randint(4, 8)
            for yy in range(depth):
                s.px[x + xx, y + yy] = shade(hair, 1.0 + s.r.uniform(-0.15, 0.15))


def loincloth(s, colour, long_back=True):
    """A hide wrap round the hips: the bottom of the body and the tops of the legs, ragged at the hem."""
    s.paint("body", colour, 0.1, rows=(10, 12), sides=("front", "back", "left", "right"))
    for leg in ("rleg", "lleg"):
        for side in ("front", "back", "left", "right"):
            x, y, w, h = s.face(leg, side)
            for xx in range(w):
                hem = s.r.randint(3, 5) if side != "back" or not long_back else s.r.randint(5, 7)
                for yy in range(hem):
                    s.px[x + xx, y + yy] = shade(colour, 1.0 + s.r.uniform(-0.1, 0.1))
    # a cord belt with a bone toggle
    x, y, w, h = s.face("body", "front")
    s.rect(x, y + 10, 8, 1, CORD)
    s.px[x + 5, y + 10] = BONE


def wraps(s, part, colour, a, b):
    """Leather bands bound round a limb, rows a..b, every other row."""
    for side in ("front", "back", "left", "right"):
        x, y, w, h = s.face(part, side)
        for yy in range(a, b, 2):
            s.rect(x, y + yy, w, 1, colour, 0.08)


def foot_wraps(s, colour):
    for leg in ("rleg", "lleg"):
        s.paint(leg, colour, 0.1, rows=(9, 12))
        s.paint(leg, colour, 0.1, sides=("bottom",))


def scar(s, part, side, x, y, n, tone):
    for i in range(n):
        s.dot(part, side, x + i, y + i, shade(tone, 1.25))


def brute_0(s):
    """Shirtless: a fur strap from the right shoulder to the left hip, a long hide loincloth, wrapped feet."""
    tone, hair = TONES[0], HAIRS[0]
    body_and_face(s, tone, hair, beard=True)
    loincloth(s, HIDE)
    foot_wraps(s, HIDE_DARK)
    x, y, w, h = s.face("body", "front")
    for i in range(10):
        s.rect(x + 7 - (i * 7) // 9, y + i, 2 if i < 9 else 1, 1, FUR, 0.12)
    x, y, w, h = s.face("body", "back")
    for i in range(10):
        s.rect(x + (i * 7) // 9, y + i, 2 if i < 9 else 1, 1, FUR, 0.12)
    s.paint("body", FUR, 0.12, over=True, rows=(0, 2), sides=("top", "right"))
    scar(s, "body", "front", 2, 3, 3, tone)
    wraps(s, "rarm", HIDE_DARK, 8, 12)


def brute_1(s):
    """A hide tunic stitched down one side with fur at the neck and hem; bare arms with ochre stripes; hide leggings."""
    tone, hair = TONES[1], HAIRS[1]
    body_and_face(s, tone, hair, beard=False)
    s.paint("body", HIDE, 0.1, rows=(0, 12))
    for leg in ("rleg", "lleg"):
        s.paint(leg, HIDE_DARK, 0.1, rows=(0, 10))
    foot_wraps(s, FUR)
    x, y, w, h = s.face("body", "front")
    s.rect(x, y, 8, 1, FUR_LIGHT, 0.12)
    s.rect(x, y + 11, 8, 1, FUR, 0.12)
    for yy in range(1, 11, 2):
        s.px[x + 6, y + yy] = CORD
    s.paint("body", FUR_LIGHT, 0.12, over=True, sides=("top",))
    for arm in ("rarm", "larm"):
        x, y, w, h = s.face(arm, "front")
        s.rect(x, y + 3, w, 1, OCHRE)
        s.rect(x, y + 5, w, 1, OCHRE)


def brute_2(s):
    """A grey fur mantle over the shoulders and upper arms, bare belly, a hide kilt; a bone at the throat."""
    tone, hair = TONES[2], HAIRS[2]
    body_and_face(s, tone, hair, beard=True)
    s.paint("body", FUR, 0.14, rows=(0, 4))
    s.paint("body", FUR, 0.16, over=True, rows=(0, 3))
    for arm in ("rarm", "larm"):
        s.paint(arm, FUR, 0.14, rows=(0, 4))
        s.paint(arm, FUR_LIGHT, 0.16, over=True, rows=(0, 2))
    x, y, w, h = s.face("body", "front")
    s.px[x + 3, y + 4] = BONE
    s.px[x + 4, y + 4] = BONE
    loincloth(s, HIDE_DARK, long_back=False)
    wraps(s, "rleg", HIDE, 6, 12)
    wraps(s, "lleg", HIDE, 6, 12)
    scar(s, "larm", "front", 0, 6, 3, tone)


def brute_3(s):
    """An open hide vest over a bare chest, forearms bound in leather, a loincloth and fur-wrapped shins."""
    tone, hair = TONES[3], HAIRS[3]
    body_and_face(s, tone, hair, beard=False)
    for side in ("back", "left", "right"):
        x, y, w, h = s.face("body", side)
        s.rect(x, y, w, 10, HIDE_DARK, 0.1)
    x, y, w, h = s.face("body", "front")
    s.rect(x, y, 2, 10, HIDE_DARK, 0.1)
    s.rect(x + 6, y, 2, 10, HIDE_DARK, 0.1)
    s.rect(x + 2, y, 4, 1, HIDE_DARK, 0.1)
    s.paint("body", HIDE_DARK, 0.1, over=True, sides=("top",))
    for arm in ("rarm", "larm"):
        wraps(s, arm, HIDE_DARK, 7, 12)
    loincloth(s, FUR)
    for leg in ("rleg", "lleg"):
        s.paint(leg, FUR, 0.14, rows=(7, 12))
    x, y, w, h = s.face("body", "front")
    s.rect(x + 3, y + 3, 2, 1, OCHRE)


LOOKS = [brute_0, brute_1, brute_2, brute_3]


def main():
    os.makedirs(OUT, exist_ok=True)
    skins = []
    for i, look in enumerate(LOOKS):
        s = Skin(0xB207E + i)
        look(s)
        s.save(os.path.join(OUT, "brute_%d.png" % i))
        skins.append(s.im)
    if "--sheet" in sys.argv:
        sheet = Image.new("RGBA", (len(skins) * 72, 72), (40, 44, 52, 255))
        for i, im in enumerate(skins):
            sheet.paste(im, (i * 72 + 4, 4), im)
        sheet.resize((sheet.width * 6, sheet.height * 6), Image.NEAREST).save(sys.argv[sys.argv.index("--sheet") + 1])


if __name__ == "__main__":
    main()
