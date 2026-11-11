"""Draws textures/entity/draugr_living.png from draugr.png: the same horned helm, mail and fur, on a living Warrior-born
in better days (The Long Defence film's host). Grave-grey skin becomes warm living skin with the grave specks gone; the
mail and helm are brighter and cleaner (less pitting); the fur and leather are richer; the pale dead eyes go dark.
Run from the kingdoms project root: python -I tools/gen_draugr_living.py"""
from PIL import Image

TEX = "src/main/resources/assets/jaymoskingdoms/textures/entity/"
SKIN = (196, 150, 112)
EYES = (44, 32, 24)


def clamp(v):
    return max(0, min(255, int(round(v))))


def lum(c):
    return (c[0] + c[1] + c[2]) / 3.0


def dead_skin(c):
    r, g, b = c
    return abs(r - g) <= 7 and r - b >= 12 and 60 <= r <= 215


def metal(c):
    r, g, b = c
    return b >= r + 4 and b - r <= 40


def brown(c):
    r, g, b = c
    return r > g > b and r - b >= 25


def pale_eye(c):
    r, g, b = c
    return b >= r + 30 and lum(c) > 150


def main():
    src = Image.open(TEX + "draugr.png").convert("RGBA")
    out = src.copy()
    w, h = src.size
    px = src.load()
    # the skin's own average, to grade against (the specks are the bright outliers)
    skins = [lum(px[x, y][:3]) for x in range(w) for y in range(h) if px[x, y][3] and dead_skin(px[x, y][:3])]
    avg = sum(skins) / max(1, len(skins))
    o = out.load()
    skin_at, keep = set(), set()
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if not a:
                continue
            c = (r, g, b)
            if pale_eye(c):
                o[x, y] = EYES + (a,)
                keep.add((x, y))
            elif dead_skin(c):
                skin_at.add((x, y))
                k = min(1.1, max(0.86, lum(c) / avg))
                if lum(c) > avg + 22:
                    k = 1.0  # a grave speck: living skin there
                o[x, y] = tuple(clamp(v * k) for v in SKIN) + (a,)
            elif metal(c):
                m = 112.0
                o[x, y] = (clamp(m + (r - m) * 0.75 + 16), clamp(m + (g - m) * 0.75 + 18), clamp(m + (b - m) * 0.75 + 20), a)
            elif brown(c):
                o[x, y] = (clamp(r * 1.08 + 6), clamp(g * 1.02), clamp(b * 0.9), a)
    # the grave's dark specks left among the skin (not skin, not eyes, mostly skin around): living skin too
    for y in range(h):
        for x in range(w):
            if (x, y) in skin_at or (x, y) in keep or not px[x, y][3]:
                continue
            around = sum((x + dx, y + dy) in skin_at for dx in (-1, 0, 1) for dy in (-1, 0, 1) if dx or dy)
            if around >= 5:
                o[x, y] = tuple(clamp(v * 0.95) for v in SKIN) + (px[x, y][3],)
    out.save(TEX + "draugr_living.png")


if __name__ == "__main__":
    main()
