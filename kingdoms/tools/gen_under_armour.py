"""(Kingdoms 0.7.478) Under-armour textures for client/UnderArmourLayer: our own pixel art, nothing from Armor Reimagined.

64x32 classic armour layout, body (uv 16,16, 8x12x4) and arms (uv 40,16, 4x12x4) only.
Run from kingdoms/: python -I tools/gen_under_armour.py
"""
import os
import random
from PIL import Image

OUT = os.path.join("src", "main", "resources", "assets", "jaymoskingdoms", "textures", "entity", "under_armour")


def box_faces(u, v, w, h, d):
    """The six faces of a box at (u, v): name -> (x0, y0, x1, y1)."""
    return {
        "top": (u + d, v, u + d + w, v + d),
        "bottom": (u + d + w, v, u + d + w + w, v + d),
        "right": (u, v + d, u + d, v + d + h),
        "front": (u + d, v + d, u + d + w, v + d + h),
        "left": (u + d + w, v + d, u + d + w + d, v + d + h),
        "back": (u + d + w + d, v + d, u + d + w + d + w, v + d + h),
    }


BODY = box_faces(16, 16, 8, 12, 4)
ARM = box_faces(40, 16, 4, 12, 4)


def shade(c, f):
    return tuple(max(0, min(255, int(x * f))) for x in c[:3]) + (255,)


def fill(img, rect, colour_at):
    x0, y0, x1, y1 = rect
    for x in range(x0, x1):
        for y in range(y0, y1):
            img.putpixel((x, y), colour_at(x - x0, y - y0, x1 - x0, y1 - y0))


def quilted(base, rng, stitch=0.82, step=3):
    def at(x, y, w, h):
        c = base
        if (x + y) % step == 0 or (x - y) % step == 0:
            c = shade(base, stitch)
        return shade(c, 0.95 + rng.random() * 0.1)
    return at


def mail(light, dark, rng):
    def at(x, y, w, h):
        c = light if (x + (y // 1)) % 2 == 0 else dark
        if y % 2 == 1:
            c = dark if (x % 2 == 0) else light
        return shade(c, 0.92 + rng.random() * 0.12)
    return at


def doublet():
    """Greyscale (tinted by the leggings' dye): a laced arming doublet, quilted, darker collar and cuffs."""
    rng = random.Random(478)
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    base = (226, 226, 226)
    for name, rect in BODY.items():
        fill(img, rect, quilted(base, rng, 0.8, 4))
    # the lacing down the front, and the collar
    x0, y0, x1, y1 = BODY["front"]
    for y in range(y0 + 1, y1 - 1, 2):
        img.putpixel((x0 + 3, y), shade(base, 0.55))
        img.putpixel((x0 + 4, y + 1), shade(base, 0.55))
    for face in ("front", "back", "right", "left"):
        fx0, fy0, fx1, _ = BODY[face]
        for x in range(fx0, fx1):
            img.putpixel((x, fy0), shade(base, 0.6))
    for name, rect in ARM.items():
        fill(img, rect, quilted(base, rng, 0.8, 4))
    for face in ("front", "back", "right", "left"):
        fx0, _, fx1, fy1 = ARM[face]
        for x in range(fx0, fx1):
            img.putpixel((x, fy1 - 1), shade(base, 0.6))
            img.putpixel((x, fy1 - 2), shade(base, 0.7))
    return img


def gambeson(sleeves):
    rng = random.Random(4780 + len(sleeves))
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    if sleeves == "mail":
        linen = (196, 178, 140)
        for rect in BODY.values():
            fill(img, rect, quilted(linen, rng, 0.78, 3))
        for rect in ARM.values():
            fill(img, rect, mail((150, 155, 162), (92, 97, 106), rng))
        # linen showing at the shoulder, and the belt line of the gambeson's skirt
        for face in ("front", "back", "right", "left"):
            fx0, fy0, fx1, fy1 = ARM[face]
            for x in range(fx0, fx1):
                img.putpixel((x, fy0), shade(linen, 0.9))
            bx0, _, bx1, by1 = BODY[face]
            for x in range(bx0, bx1):
                img.putpixel((x, by1 - 4), shade(linen, 0.6))
    else:
        padded = (122, 92, 62)
        stud = (186, 190, 196)
        for rect in list(BODY.values()) + list(ARM.values()):
            fill(img, rect, quilted(padded, rng, 0.75, 4))
        for faces in (BODY, ARM):
            for face in ("front", "back", "right", "left"):
                fx0, fy0, fx1, fy1 = faces[face]
                for x in range(fx0 + 1, fx1, 3):
                    for y in range(fy0 + 2, fy1 - 1, 4):
                        img.putpixel((x, y), stud)
        # an iron collar
        for face in ("front", "back", "right", "left"):
            fx0, fy0, fx1, _ = BODY[face]
            for x in range(fx0, fx1):
                img.putpixel((x, fy0), shade(stud, 0.85))
                img.putpixel((x, fy0 + 1), shade(stud, 0.65))
    return img


def main():
    os.makedirs(OUT, exist_ok=True)
    doublet().save(os.path.join(OUT, "doublet.png"))
    gambeson("mail").save(os.path.join(OUT, "gambeson_mail.png"))
    gambeson("iron").save(os.path.join(OUT, "gambeson_iron.png"))
    print("wrote", OUT)


if __name__ == "__main__":
    main()
