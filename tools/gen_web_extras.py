"""Generates the symbiote's wing, steed and jar art.

Painted pixel by pixel from code (nothing copied from any other mod or from vanilla):
  textures/entity/web_symbiote_wings.png      the wing membrane: raw-red, translucent, thick black veins running out from
                                              the wrist along each rib (WebSymbioteWingsLayer maps it by position, so the
                                              veins meet the ribs: keep WRIST and the TIPS in step with that class)
  textures/entity/web_symbiote_steed.png      the horse the symbiote has taken: glossy black with a blue sheen; any UV
                                              layout reads the same. _white and _red twins (WebStrainLook.texture)
  textures/item/jarred_symbiote.png           a glass bottle with a black symbiote shut in it, corked

Run from the project root:  python3 -I tools/gen_web_extras.py
"""
import math
import random
from pathlib import Path

from PIL import Image, ImageDraw

PROJECT = Path(__file__).resolve().parent.parent
ASSETS = PROJECT / "src" / "main" / "resources" / "assets" / "jaymospowers" / "textures"


def clamp(v):
    return max(0, min(255, int(round(v))))


# ---- the wing membrane ----------------------------------------------------------------------------------------------
# The layer maps a point (x, y) of the wing (pixels, +x out, +y down) to u = x / 64, v = (y + 32) / 72 of this 64x64
# picture, so a pixel here is (x, y + 32) * 64 / 72 vertically. The wrist and the rib tips below are the wing's own.
SIZE = 64


def uv(x, y):
    return x, (y + 32.0) * SIZE / 72.0


WRIST = uv(36, -26)
TIPS = [uv(58, -8), uv(50, 14), uv(36, 32), uv(20, 40)]
SHOULDER = uv(3, 3)
HIP = uv(3, 26)


def line_dist(px, py, a, b):
    ax, ay = a
    bx, by = b
    dx, dy = bx - ax, by - ay
    t = ((px - ax) * dx + (py - ay) * dy) / max(1e-6, dx * dx + dy * dy)
    t = max(0.0, min(1.0, t))
    cx, cy = ax + t * dx, ay + t * dy
    return math.hypot(px - cx, py - cy), t


def wings():
    rng = random.Random(1104)
    img = Image.new("RGBA", (SIZE, SIZE))
    px = img.load()
    for y in range(SIZE):
        for x in range(SIZE):
            # raw-meat mottling: two octaves of blocky noise
            n = rng.random() * 0.5 + 0.5 * math.sin(x * 0.7 + y * 0.35) * math.sin(y * 0.55 - x * 0.2)
            r = 150 + 55 * n
            g = 14 + 22 * max(0.0, n)
            b = 22 + 20 * max(0.0, n)
            a = 150 + 40 * n
            # the thick black veins: out along every rib, and in from the wrist to the shoulder and hip
            vein = 99.0
            for tip in TIPS:
                d, t = line_dist(x, y, WRIST, tip)
                vein = min(vein, d - (1.7 - 1.0 * t))
            d, t = line_dist(x, y, WRIST, SHOULDER)
            vein = min(vein, d - 1.2)
            d, t = line_dist(x, y, TIPS[-1], HIP)
            vein = min(vein, d - 1.0)
            if vein < 0.0:
                r, g, b, a = 14, 4, 8, 250
            elif vein < 1.0:
                # a dark wet rim round the veins
                r, g, b, a = r * 0.45, g * 0.5, b * 0.5, a + 25
            # thin capillaries: branches off the ribs, a coin-toss per cell so they don't read as a grid
            elif (x * 7 + y * 13) % 11 == 0 and rng.random() < 0.55:
                r, g, b = r * 0.55, g * 0.6, b * 0.6
            # lighter and thinner toward the trailing edge
            far = min(math.hypot(x - WRIST[0], y - WRIST[1]) / 48.0, 1.0)
            a = a * (1.0 - 0.28 * far)
            px[x, y] = (clamp(r), clamp(g), clamp(b), clamp(a))
    img.save(ASSETS / "entity" / "web_symbiote_wings.png")


# ---- the steed ------------------------------------------------------------------------------------------------------
def steed(name, base, sheen, vein=None, seed=7):
    rng = random.Random(seed)
    img = Image.new("RGBA", (64, 64))
    px = img.load()
    for y in range(64):
        for x in range(64):
            n = rng.random()
            # long glossy streaks, steep, wrapping the texture
            streak = 0.5 + 0.5 * math.sin((x * 0.9 + y * 0.45) * 0.55)
            wet = max(0.0, streak - 0.78) * 4.0
            r = base[0] + n * 6 + wet * (sheen[0] - base[0])
            g = base[1] + n * 6 + wet * (sheen[1] - base[1])
            b = base[2] + n * 8 + wet * (sheen[2] - base[2])
            if vein is not None and (x * 5 + y * 3) % 17 < 2:
                r, g, b = vein
            px[x, y] = (clamp(r), clamp(g), clamp(b), 255)
    return img


def steeds():
    out = ASSETS / "entity"
    steed("black", (9, 9, 13), (60, 74, 120)).save(out / "web_symbiote_steed.png")
    steed("white", (214, 210, 198), (255, 252, 240), seed=8).save(out / "web_symbiote_steed_white.png")
    steed("red", (96, 10, 18), (190, 40, 50), vein=(10, 4, 6), seed=9).save(out / "web_symbiote_steed_red.png")


# ---- the jar --------------------------------------------------------------------------------------------------------
def jar():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    glass = (170, 205, 220, 255)
    glass_dark = (110, 150, 170, 255)
    # cork
    d.rectangle([6, 1, 9, 2], fill=(150, 108, 62, 255))
    d.rectangle([6, 1, 9, 1], fill=(176, 132, 80, 255))
    # neck
    d.rectangle([6, 3, 6, 5], fill=glass_dark)
    d.rectangle([9, 3, 9, 5], fill=glass_dark)
    d.rectangle([7, 3, 8, 5], fill=(150, 185, 200, 120))
    # body
    for y in range(6, 15):
        w = 2 if y == 6 else 3 if y == 7 else 4
        d.line([(8 - w - 1, y), (8 - w - 1, y)], fill=glass_dark)
        d.line([(8 + w, y), (8 + w, y)], fill=glass_dark)
        d.rectangle([8 - w, y, 8 + w - 1, y], fill=(150, 185, 200, 130))
    d.rectangle([4, 15, 11, 15], fill=glass_dark)
    d.line([(4, 7), (4, 13)], fill=glass)
    # the symbiote, shut in: a black mass with a sheen and two pale eyes
    rng = random.Random(3)
    for y in range(9, 15):
        for x in range(4, 12):
            if (x - 7.5) ** 2 / 14.0 + (y - 12.0) ** 2 / 9.0 <= 1.0:
                shade = rng.random()
                d.point((x, y), fill=(10 + int(10 * shade), 10 + int(10 * shade), 14 + int(26 * shade), 255))
    d.point((6, 11), fill=(230, 235, 245, 255))
    d.point((9, 11), fill=(230, 235, 245, 255))
    d.point((5, 10), fill=(70, 84, 130, 255))
    img.save(ASSETS / "item" / "jarred_symbiote.png")


if __name__ == "__main__":
    wings()
    steeds()
    jar()
    print("wings, steed and jar art written")
