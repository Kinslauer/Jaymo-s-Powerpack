"""Builds the horse coats for MocHorseModel (0.5.40), DrZhark's Mo' Creatures horse (GPL-3, see
src/main/resources/licenses/mocreatures): 256x256 textures, the model's 128x128 box layout drawn at twice the size.

- Our 36 breed coats (the six breeds' six coats, specs from tools/gen_equestrian.py): each painted per model texel
  (base colour, mane and tail, points, socks, blazes, dapples, roan, pinto, leopard, dun stripes, feathering), then
  shaded with Mo' Creatures' white horse (horsewhite), so every coat keeps its hand-drawn shading, eyes, nostrils,
  mouth, hooves and saddle, bridle and bags as they are.
- Skeleton horses: Mo' Creatures' skeleton as it is (bone), sooted (sooty) and mossed (mossy). Zombie horses: its
  undead horse as it is (rotten), blanched (pale) and mottled (mottled).
- Horse armour overlays (textures/entity/moc_horse/armor/<namespace>/<asset>.png), drawn over the coat on a slightly
  larger model: Mo' Creatures' metal, gold and diamond armour (each lifted off horsewhite<armour>), and from the metal
  one: copper, netherite, leather (greyscale, tinted by its dye), and our chain and plate barding.

The originals are in tools/mocreatures/. Seeded, so every run draws the same pixels. A preview sheet goes to
/tmp/claude-0/moc-horses-preview.png. Run from kingdoms/: python3 -I tools/gen_moc_horses.py"""
import colorsys
import os
import random
import sys

from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
sys.dont_write_bytecode = True
sys.path.insert(0, HERE)
import gen_equestrian as ge  # noqa: E402  (the coat specs and the noise helpers)

SRC = os.path.join(HERE, "mocreatures")
OUT = os.path.normpath(os.path.join(HERE, "..", "src/main/resources/assets/jaymoskingdoms/textures/entity/moc_horse"))
PREVIEW = "/tmp/claude-0/moc-horses-preview.png"
SIDES = ge.SIDES


def load(name):
    return Image.open(os.path.join(SRC, name + ".png")).convert("RGBA")


WHITE_HORSE = load("horsewhite")

# ---- the model's boxes (u, v, w, h, d) in its 128x128 layout, by part ---------------------------------------------------
LEG_A = [(78, 29, 4, 9, 5), (96, 29, 4, 9, 5), (44, 29, 3, 8, 4), (60, 29, 3, 8, 4)]
LEG_B = [(78, 43, 3, 5, 3), (96, 43, 3, 5, 3), (44, 41, 3, 5, 3), (60, 41, 3, 5, 3)]
LEG_C = [(78, 51, 4, 3, 4), (96, 51, 4, 3, 4), (44, 51, 4, 3, 4), (60, 51, 4, 3, 4)]
PARTS = [
    ("body", [(0, 34, 10, 10, 24)]),
    ("neck", [(0, 12, 4, 14, 8)]),
    ("head", [(0, 0, 5, 5, 7)]),
    ("muzzle", [(24, 18, 4, 3, 6), (24, 27, 4, 2, 5)]),
    ("mane", [(58, 0, 2, 16, 4)]),
    ("tail", [(44, 0, 2, 2, 3), (38, 7, 3, 4, 7), (24, 3, 3, 4, 7)]),
    ("leg_a", LEG_A),
    ("leg_b", LEG_B),
    ("leg_c", LEG_C),
    ("ear", [(0, 0, 2, 3, 1)]),  # in the head's unused corner, so last
]


def faces(box):
    return ge.Box(*box).faces


def texels():
    """Every texel of the coat's boxes: (part, box index, face, i, j, face width, face height) by (x, y)."""
    out = {}
    for part, boxes in PARTS:
        for bi, box in enumerate(boxes):
            for face, (x0, y0, fw, fh) in faces(box).items():
                for j in range(fh):
                    for i in range(fw):
                        out[(x0 + i, y0 + j)] = (part, bi, face, i, j, fw, fh)
    return out


TEXELS = texels()


def lum(p):
    return 0.299 * p[0] + 0.587 * p[1] + 0.114 * p[2]


def sat(p):
    return colorsys.rgb_to_hsv(*(c / 255 for c in p[:3]))[1]


def medians():
    """The white horse's median brightness over each part's texels: what its shading is measured against."""
    by = {}
    for (x, y), (part, *_rest) in TEXELS.items():
        for dx in (0, 1):
            for dy in (0, 1):
                p = WHITE_HORSE.getpixel((2 * x + dx, 2 * y + dy))
                if p[3] and sat(p) < 0.25:
                    by.setdefault(part, []).append(lum(p))
    return {k: sorted(v)[len(v) // 2] for k, v in by.items()}


MEDIAN = medians()


def keep_as_is(part, p):
    """The white horse's own details kept on every coat: the eyes and the inside of the mouth, the nostrils."""
    if part in ("head", "muzzle"):
        return sat(p) > 0.22 or lum(p) < MEDIAN[part] * 0.45
    return False


def shaded(c, p, part):
    s = max(0.55, min(1.3, lum(p) / MEDIAN[part]))
    return ge.clamp(tuple(v * s + (s - 1) * 35 for v in c))


def finish(colours):
    """The coat at 256x256: each pixel's colour, shaded with the white horse's; everything else as it is."""
    img = WHITE_HORSE.copy()
    pix = img.load()
    for (X, Y), c in colours.items():
        part = TEXELS[(X // 2, Y // 2)][0]
        p = WHITE_HORSE.getpixel((X, Y))
        if p[3] and not keep_as_is(part, p):
            pix[X, Y] = shaded(c, p, part) + (p[3],)
    return img


# ---- the breed coats ----------------------------------------------------------------------------------------------------
def leg_row(part, bi, j):
    """A texel's row down the whole leg (upper, cannon) and the leg's length without the hoof."""
    upper = LEG_A[bi][3]
    return (j if part == "leg_a" else upper + j), upper + LEG_B[bi][3]


def living(breed, name, S):
    rnd = random.Random(f"moc/{breed}/{name}")
    marks = S["marks"] | ({"feather"} if breed == "draft" else set())
    hair = S["hair"]
    stripe = S["stripe"] or ge.shade(hair, -20)
    memo = {}

    def m(key, make):
        if key not in memo:
            memo[key] = make()
        return memo[key]

    def strand(part, face, i):
        return m(("strand", part, face, i), lambda: rnd.randint(-ge.HAIRN[breed], ge.HAIRN[breed]))

    def hairpx(c, part, face, i):
        return ge.shade(c, strand(part, face, i) // 2)

    def body_pattern(c, part, bi, face, i, j, fw, fh, I, J):
        key = (part, bi, face)
        if "dapple" in marks and face != "bottom":
            d = m(("dap",) + key, lambda: ge.dapples(rnd, 2 * fw, 2 * fh, 5))
            c = ge.mix(c, S["spot"], (0.7 if d[J][I] == 2 else 0.3 if d[J][I] == 1 else 0) * (0.45 if part == "neck" else 1.0))
        if "flea" in marks and rnd.random() < 0.11:
            c = ge.mix(c, S["spot"], 0.8)
        if "roan" in marks and rnd.random() < 0.38:
            c = ge.mix(c, S["spot"], 0.38 if rnd.random() < 0.7 else 0.6)
        if "pinto" in marks:
            n = m(("pin",) + key, lambda: ge.vnoise(rnd, 2 * fw, 2 * fh, 10))
            if n[J][I] > (0.36 if face == "bottom" else 0.62 if part == "neck" else 0.5):
                c = S["white"]
        if "leopard" in marks:
            bias = None
            if face == "b":
                bias = lambda x: 0.35 + 0.65 * (x / fw)  # noqa: E731
            elif face == "a":
                bias = lambda x: 0.35 + 0.65 * (1 - x / fw)  # noqa: E731
            spots = m(("leo",) + key, lambda: ge.ovals(rnd, fw, fh, max(2, fw * fh // 22), bias=bias))
            if spots[j][i]:
                c = S["spot"]
        return c

    def colour(part, bi, face, i, j, fw, fh, I, J):
        if part in ("mane", "tail"):
            c = hair
            if "pinto" in marks:
                n = m(("pinh", part), lambda: ge.vnoise(rnd, 12, 24, 4))
                if n[min(j, 23)][i % 12] > 0.55:
                    c = S["white"]
            if "leopard" in marks and strand(part, face, I) % 3 == 0:
                c = (226, 220, 210)
            if "dorsal" in marks and part == "mane" and face == "back":
                c = ge.mix(c, stripe, 0.7)
            return hairpx(c, part, face, I)
        c = S["base"]
        if part in ("head", "muzzle", "ear") and S["head"]:
            c = S["head"]
        if part.startswith("leg") and S["legs"]:
            c = S["legs"]
        if part in ("body", "neck"):
            c = body_pattern(c, part, bi, face, i, j, fw, fh, I, J)
        if part in ("head", "muzzle") and "leopard" in marks and rnd.random() < 0.06:
            c = S["spot"]
        if part in ("head", "muzzle") and "flea" in marks and rnd.random() < 0.08:
            c = ge.mix(c, S["spot"], 0.7)
        if part == "neck" and face == "top" and 0 < i < fw - 1 and j >= fh - 2:
            return hairpx(hair, part, face, I)  # the poll, where the mane starts
        if part == "ear":
            if S["points"] and (j == 0 and face in SIDES or face == "top"):
                c = S["points"]
            if face == "front" and j > 0:
                c = ge.mix(c, ge.DARK, 0.5)  # the inside of the ear (it points forward)
            return c
        if part == "leg_c":
            return S["hoof"] if face != "bottom" else ge.shade(S["hoof"], -18)
        if part in ("leg_a", "leg_b"):
            if face in ("top", "bottom"):
                return c
            row, length = leg_row(part, bi, j)
            if S["points"] and row >= S["points_from"] * length / 9:
                c = S["points"]
            elif "bars" in marks and row % 2 == 1:
                c = ge.mix(c, stripe, 0.35)
            if "leopard" in marks and part == "leg_a":
                spots = m(("leol", bi, face), lambda: ge.ovals(rnd, fw, fh, 3, rx=(0.5, 0.9), ry=(0.6, 1.0)))
                if spots[j][i]:
                    c = S["spot"]
            if "socks" in marks and row >= length - 4:
                c = S["white"]
            if "stockings" in marks and row >= 4 * length / 9:
                c = S["white"]
            if "feather" in marks and part == "leg_b" and j >= 2 and not (j == 2 and rnd.random() < 0.4):
                return hairpx(S["feather"], part, face, I)
            return c
        if part in ("head", "muzzle"):
            upper = part == "muzzle" and bi == 0
            if "blaze" in marks:
                if part == "head" and face == "top" and i == 2 and j >= 1:
                    c = S["white"]
                if part == "head" and face == "front" and i in (1, 2, 3):
                    c = S["white"]
                if upper and face in ("top", "front") and i in (1, 2):
                    c = S["white"]
            if "star" in marks and part == "head" and face == "top" and (i == 2 and j in (2, 3, 4) or i in (1, 3) and j == 3):
                c = S["white"]
            if part == "muzzle":
                tint = S["tint"] or (34, 28, 26)
                if face == "front":
                    c = ge.mix(c, tint, 0.35)
                elif face in ("a", "b") and (not upper or j >= fh - 1):
                    c = ge.mix(c, tint, 0.25)
                elif face == "bottom" or not upper:
                    c = ge.mix(c, tint, 0.3)
        if "dorsal" in marks and part == "body":
            mid = (fw // 2 - 1, fw // 2)
            if face == "top" and i in mid or face == "back" and i in mid and j < 3:
                c = ge.mix(c, stripe, 0.8)
        return c

    def post(part, face, i, j, fw, fh, c):
        hairy = part in ("mane", "tail")
        if "shimmer" in marks:
            if not hairy and (face == "top" or face in SIDES and j < fh * 0.45) and rnd.random() < 0.07:
                return ge.shade(c, 34)
            if hairy and rnd.random() < 0.05:
                return ge.shade(c, 26)
        if not hairy and part != "leg_c":
            c = ge.shade(c, rnd.randint(-ge.NOISE[breed], ge.NOISE[breed]))
        return c

    colours = {}
    for (x, y), (part, bi, face, i, j, fw, fh) in TEXELS.items():
        x0, y0 = x - i, y - j
        for dx in (0, 1):
            for dy in (0, 1):
                I, J = 2 * i + dx, 2 * j + dy
                colours[(2 * x0 + I, 2 * y0 + J)] = post(part, face, i, j, fw, fh, colour(part, bi, face, i, j, fw, fh, I, J))
    return finish(colours)


# ---- skeleton and zombie horses -------------------------------------------------------------------------------------------
def recolour(img, fn, seed):
    """Every opaque texel of the horse's own boxes (not its saddle and bags) through fn(texel colour, its noise, x, y)."""
    rnd = random.Random(seed)
    img = img.copy()
    pix = img.load()
    noise = ge.vnoise(rnd, 128, 128, 3)
    for (x, y), (part, *_rest) in TEXELS.items():
        for dx in (0, 1):
            for dy in (0, 1):
                X, Y = 2 * x + dx, 2 * y + dy
                p = pix[X, Y]
                if p[3]:
                    pix[X, Y] = ge.clamp(fn(p[:3], noise[y][x], rnd, part)) + (p[3],)
    return img


def toward(c, target, t):
    return ge.mix(c, target, t)


def skeletons():
    bone = load("horseskeleton")

    def sooty(c, n, rnd, part):
        c = tuple(v * 0.42 for v in c)
        return toward(c, (40, 36, 34), 0.6) if n > 0.6 else c

    def mossy(c, n, rnd, part):
        if lum(c) > 90 and n > (0.55 if part in ("body", "neck") else 0.62):
            return ge.shade((78, 110, 46), rnd.choice((-14, 0, 0, 12, 22)))
        return toward(c, (196, 188, 156), 0.2)

    return {"bone": bone, "sooty": recolour(bone, sooty, "moc/skeleton/sooty"), "mossy": recolour(bone, mossy, "moc/skeleton/mossy")}


def zombies():
    rotten = load("horseundead")

    def pale(c, n, rnd, part):
        if sat(c) > 0.45 and c[0] > c[1]:
            return c  # wounds stay red
        g = lum(c) / 120
        out = tuple(v * g for v in (150, 160, 172))
        return toward(out, (110, 116, 140), 0.35) if n > 0.6 else out

    def mottled(c, n, rnd, part):
        if sat(c) > 0.45 and c[0] > c[1]:
            return c
        g = lum(c) / 110
        out = tuple(v * g for v in (112, 86, 60))
        return toward(out, (106, 128, 66), 0.8) if n > 0.58 else out

    return {"rotten": rotten, "pale": recolour(rotten, pale, "moc/zombie/pale"), "mottled": recolour(rotten, mottled, "moc/zombie/mottled")}


# ---- armour overlays ------------------------------------------------------------------------------------------------------
def lifted(name):
    """What horsewhite<name> adds to horsewhite: the armour alone, everything else clear."""
    armoured = load("horsewhite" + name)
    out = Image.new("RGBA", armoured.size, (0, 0, 0, 0))
    a, b, o = WHITE_HORSE.load(), armoured.load(), out.load()
    for y in range(armoured.height):
        for x in range(armoured.width):
            p, q = a[x, y], b[x, y]
            if (x // 2, y // 2) not in TEXELS:
                continue  # the horse's own boxes only, not its saddle and bags
            if q[3] and (not p[3] or max(abs(p[k] - q[k]) for k in range(3)) > 10):
                o[x, y] = q
    return out


def ramp(img, stops):
    """Recolours by brightness along stops [(brightness 0-1, colour), ...]."""
    img = img.copy()
    pix = img.load()
    for y in range(img.height):
        for x in range(img.width):
            p = pix[x, y]
            if not p[3]:
                continue
            t = lum(p) / 255
            for (t0, c0), (t1, c1) in zip(stops, stops[1:]):
                if t <= t1 or (t1, c1) == stops[-1]:
                    k = 0 if t1 == t0 else max(0.0, min(1.0, (t - t0) / (t1 - t0)))
                    pix[x, y] = ge.mix(c0, c1, k) + (p[3],)
                    break
    return img


def armours():
    metal, gold, diamond = lifted("metal"), lifted("gold"), lifted("diamond")
    return {
        "minecraft/iron": metal,
        "minecraft/gold": gold,
        "minecraft/diamond": diamond,
        "minecraft/copper": ramp(metal, [(0.0, (60, 30, 18)), (0.35, (150, 78, 46)), (0.7, (214, 128, 84)), (1.0, (250, 196, 150))]),
        "minecraft/netherite": ramp(metal, [(0.0, (20, 16, 18)), (0.4, (58, 50, 54)), (0.75, (98, 88, 92)), (1.0, (150, 138, 140))]),
        "minecraft/leather": ramp(metal, [(0.0, (90, 90, 90)), (0.5, (200, 200, 200)), (1.0, (255, 255, 255))]),
        "jaymoskingdoms/chain_barding": ramp(metal, [(0.0, (40, 40, 44)), (0.4, (110, 114, 120)), (1.0, (200, 204, 210))]),
        "jaymoskingdoms/plate_barding": ramp(metal, [(0.0, (70, 72, 78)), (0.3, (150, 154, 160)), (0.7, (206, 210, 216)), (1.0, (245, 247, 250))]),
    }


# ---------------------------------------------------------------------------------------------------------------------------
def save(img, rel, sheet):
    path = os.path.join(OUT, rel + ".png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    sheet.append((rel, img))


def preview(sheet):
    font = ImageFont.load_default()
    cols, tile, label = 6, 256, 14
    rows = (len(sheet) + cols - 1) // cols
    out = Image.new("RGBA", (cols * tile, rows * (tile + label)), (40, 40, 46, 255))
    d = ImageDraw.Draw(out)
    for k, (rel, img) in enumerate(sheet):
        x, y = (k % cols) * tile, (k // cols) * (tile + label)
        d.text((x + 2, y), rel, fill=(230, 230, 230), font=font)
        out.alpha_composite(img, (x, y + label))
    os.makedirs(os.path.dirname(PREVIEW), exist_ok=True)
    out.save(PREVIEW)


def main():
    sheet = []
    for breed, coats in ge.COATS.items():
        for name, spec in coats:
            save(living(breed, name, spec), f"{breed}/{name}", sheet)
    for name, img in skeletons().items():
        save(img, f"skeleton/{name}", sheet)
    for name, img in zombies().items():
        save(img, f"zombie/{name}", sheet)
    for name, img in armours().items():
        save(img, f"armor/{name}", sheet)
    preview(sheet)
    print(f"wrote {len(sheet)} textures under {OUT}; preview at {PREVIEW}")


if __name__ == "__main__":
    main()
