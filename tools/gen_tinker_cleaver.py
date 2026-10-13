"""Redraws the Tinkering cleaver's layers (handle, head, shield, guard; 16px and 32px; the broken head) in the Armory
cleaver's short shape (tools/gen_cleaver_staff.py), so it reads as a short chopper rather than Tinkers' great blade
(Andy, 0.9.0). Each material's colours come from the Tinkers' sprites already in the repo: the iron sprite's six grey
tones map, pixel for pixel, onto that material's tones. Run it after tinkering_assets.py:

    python -I tools/gen_tinker_cleaver.py
"""
import glob
import os
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "jaymospowers", "textures", "item", "tinkering", "cleaver")
TONES = [53, 94, 130, 168, 216, 255]  # Tinkers' grey ramp: outline, dark, mid, light, highlight, white
OUTLINE, DARK, MID, LIGHT, HI, WHITE = TONES


def palette(material):
    """Grey tone -> this material's colour, read off the existing head sprites (the same shape in every material)."""
    out = {}
    for name in ("head", "head_broken", "guard", "handle", "shield"):
        iron = Image.open(os.path.join(DIR, "large", f"{name}_iron.png")).convert("RGBA")
        path = os.path.join(DIR, "large", f"{name}_{material}.png")
        if not os.path.exists(path):
            continue
        im = Image.open(path).convert("RGBA")
        for y in range(32):
            for x in range(32):
                g, c = iron.getpixel((x, y)), im.getpixel((x, y))
                if g[3] > 0 and c[3] > 0 and g[0] in TONES and g[0] not in out:
                    out[g[0]] = c[:3]
    return out


def colour(pal, tone):
    if tone in pal:
        return pal[tone]
    near = min(pal, key=lambda t: abs(t - tone))
    return pal[near]


def part(p, q, broken):
    """Which layer a point of the 32px design belongs to, in diagonal coordinates (p along, q across)."""
    if 12 <= p <= 15 and -3 <= q <= 3:
        return "guard"
    if 2 <= p <= 13 and -1 <= q <= 1:
        return "handle"
    if 15 <= p <= 38 and -11 <= q <= 3:
        if p >= 36 and q <= -9:
            return None
        if p <= 17 and q <= -9:
            return None
        if broken and (p + q >= 34 or (28 <= p <= 31 and -4 <= q - (p - 29) <= -2)):
            return None  # the tip gone, a crack running in from it
        if 30 <= p <= 32 and -8 <= q <= -6:
            return "hole"
        if 18 <= p <= 34 and q <= -8:
            return "shield"
        return "head"
    return None


def layer(size, which, pal, broken):
    im = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    k = 32 / size

    def at(p, q):
        return part(p * k, q * k, broken)

    for y in range(size):
        for x in range(size):
            p, q = x + (size - 1 - y), x - (size - 1 - y)
            here = at(p, q)
            if here is None:
                continue
            own = here == which or (which == "head" and here == "hole")
            if not own:
                continue
            edge = any(at(p + dp, q + dq) != here for dp, dq in ((1, 1), (1, -1), (-1, 1), (-1, -1)))
            if here == "hole":
                tone = OUTLINE
            elif which == "handle":
                tone = OUTLINE if edge else (LIGHT if q * k < 0 else DARK)
                if 6 <= p * k <= 7.5 and abs(q * k) < 1:
                    tone = MID
            elif which == "guard":
                tone = OUTLINE if edge else (MID if q * k < 0 else DARK)
            elif which == "shield":
                tone = OUTLINE if edge else (LIGHT if (p * k) % 6 < 3 else MID)
            else:  # the blade: its edge on the lower-right side catches the light
                qq, pp = q * k, p * k
                if edge:
                    tone = HI if qq >= 2 else OUTLINE
                elif qq >= 1:
                    tone = WHITE
                elif qq >= -2:
                    tone = HI
                else:
                    tone = LIGHT if (pp + qq) % 6 else HI
            im.putpixel((x, y), colour(pal, tone) + (255,))
    return im


def main():
    materials = sorted({os.path.basename(f)[len("head_"):-4] for f in glob.glob(os.path.join(DIR, "large", "head_*.png"))
                        if "broken" not in f})
    for material in materials:
        pal = palette(material)
        for size, sub in ((16, ""), (32, "large")):
            for which in ("handle", "head", "shield", "guard"):
                layer(size, which, pal, False).save(os.path.join(DIR, sub, f"{which}_{material}.png"))
            for which in ("head", "shield"):
                layer(size, which, pal, True).save(os.path.join(DIR, sub, f"{which}_broken_{material}.png"))


if __name__ == "__main__":
    main()
