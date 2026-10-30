"""Valor coatings (Kingdoms 0.7.409): what a Valor's Reinforcement and Armament look like on the arms (Valor.Look), drawn by
ValorLook over the 64x64 player skin layout (only the base arms, hands and, at Unbroken, the shoulders: the coat model is a
humanoid mesh grown just past the sleeves, or past armour).

  coat/<look>_<rank>.png    the coat, rank 0-4 covering more: the hands, half the forearm, the forearm, the whole arm, then the
                            shoulders too; ragged at its edge
  coat/<look>_veins_<rank>.png   the same coat's lines (scale edges, cracks, bands...) in white, drawn emissive in the soul
                            colour while King's Valor Coating is up
  coat/bent_<bent>.png      the second quirk's mark at the wrist (Tempered on), white, tinted by ValorLook

  iron     gunmetal plates in bands, rivets          stone    cracked grey-brown rock, flecks
  scale    overlapping dark green scales             ash      grey char, flaking at the edge, ember-dark spots
  haze     a see-through orange heat shimmer in waves      lacquer  glossy black shell, diagonal shine

Everything is our own: plain colour ramps and patterns. Run from the kingdoms folder:  python -I tools/gen_valor_coats.py
"""
import math
import os
import random
from PIL import Image

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "jaymoskingdoms",
                   "textures", "entity", "valor")
os.makedirs(OUT, exist_ok=True)

# the arms' side faces: (x0, y0) of the four sides (16 across), 12 rows from the shoulder down (classic and slim)
ARMS = ((40, 20), (32, 52))
# the hands' bottom faces and the arms' tops (classic and slim columns both)
HANDS = ((47, 16, 52, 20), (39, 48, 44, 52))
TOPS = ((44, 16, 48, 20), (36, 48, 40, 52))
# rows covered up from the hand, by rank
COVER = (3, 6, 8, 12, 12)
LOOKS = ("iron", "stone", "scale", "ash", "haze", "lacquer")
BENTS = ("gate", "duel", "hunt", "magebane", "survivor")


def blank():
    return Image.new("RGBA", (64, 64), (0, 0, 0, 0))


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c[:3])


def mix(a, b, k):
    return tuple(int(a[i] + (b[i] - a[i]) * k) for i in range(3))


def jitter(c, r, n):
    d = r.randint(-n, n)
    return tuple(max(0, min(255, v + d)) for v in c[:3])


class Pattern:
    """A look: colour(col, row) -> (rgb, alpha) or None, and vein(col, row) -> True where its lines are."""

    def __init__(self, look, seed):
        self.look = look
        self.r = random.Random(seed)
        r = self.r
        # stone's cracks: random walks down the arm
        self.cracks = set()
        for _ in range(5):
            c, row = r.randrange(16), r.randrange(12)
            for _ in range(9):
                self.cracks.add((c % 16, row))
                c += r.choice((-1, 0, 1))
                row += r.choice((-1, 1, 1))
                if not 0 <= row < 12:
                    break
        self.flecks = {(r.randrange(16), r.randrange(12)) for _ in range(14)}
        self.holes = {(r.randrange(16), r.randrange(12)) for _ in range(10)}
        self.spots = {(r.randrange(16), r.randrange(12)) for _ in range(9)}

    def color(self, c, row, up):
        """row: 0 at the shoulder; up: rows up from the hand (0 at the hand)."""
        r, look = self.r, self.look
        face = c % 4
        if look == "iron":
            base = (0x3A, 0x3F, 0x47)
            if up % 4 == 3:
                return shade(base, 0.6), 255
            if up % 4 == 2 and face == 1:
                return (0x8A, 0x92, 0xA0), 255
            if face == 0:
                return shade(base, 1.35), 255
            return jitter(base, r, 4), 255
        if look == "stone":
            base = (0x5A, 0x54, 0x4C)
            if (c, row) in self.cracks:
                return (0x2A, 0x26, 0x22), 255
            if (c, row) in self.flecks:
                return (0x80, 0x78, 0x6C), 255
            return jitter(base, r, 10), 255
        if look == "scale":
            base = (0x1F, 0x3A, 0x2E)
            off = 2 if (up // 3) % 2 else 0
            dx, dy = (c + off) % 4, up % 3
            if dy == 0 and dx in (1, 2) or dx in (0, 3) and dy == 1:
                return (0x0B, 0x18, 0x12), 255
            if dy == 2 and dx in (1, 2):
                return (0x3E, 0x6E, 0x54), 255
            return jitter(base, r, 4), 255
        if look == "ash":
            base = (0x6E, 0x6E, 0x6E)
            if (c, row) in self.spots:
                return (0x34, 0x30, 0x30), 255
            if (c, row) in self.flecks:
                return (0xAA, 0xA8, 0xA4), 255
            return jitter(base, r, 12), 235
        if look == "haze":
            wave = math.sin((up + c * 0.5) * 1.3)
            if wave > 0.75:
                return (0xFF, 0xF0, 0xC0), 215
            return mix((0xC8, 0x6A, 0x20), (0xFF, 0xC8, 0x60), (wave + 1) / 2), int(150 + 45 * wave)
        if look == "lacquer":
            base = (0x0D, 0x0D, 0x12)
            d = (c + up) % 7
            if d == 0:
                return (0x70, 0x70, 0x98), 255
            if d in (1, 6):
                return (0x2C, 0x2C, 0x3C), 255
            if face == 3:
                return (0x02, 0x02, 0x04), 255
            return base, 255
        raise ValueError(look)

    def vein(self, c, row, up):
        face = c % 4
        if self.look == "iron":
            return up % 4 == 3 or up % 4 == 2 and face == 1
        if self.look == "stone":
            return (c, row) in self.cracks
        if self.look == "scale":
            off = 2 if (up // 3) % 2 else 0
            dx, dy = (c + off) % 4, up % 3
            return dy == 0 and dx in (1, 2) or dx in (0, 3) and dy == 1
        if self.look == "ash":
            return (c, row) in self.spots or (c, row) in self.holes
        if self.look == "haze":
            return math.sin((up + c * 0.5) * 1.3) > 0.75
        if self.look == "lacquer":
            return (c + up) % 7 == 0
        raise ValueError(self.look)


def edge(rank, look, seed):
    """Each column's covered rows up from the hand: ragged by a row at the top (none ragged at Steeled and Unbroken)."""
    r = random.Random(seed * 31 + rank)
    n = COVER[rank]
    if n >= 12:
        return [12] * 16
    return [max(1, min(12, n + r.choice((-1, 0, 0, 1)))) for _ in range(16)]


def coat(look, rank, veins):
    img = blank()
    for arm, (x0, y0) in enumerate(ARMS):
        p = Pattern(look, 7 + arm)
        tops = edge(rank, look, arm)
        for c in range(16):
            for row in range(12):
                up = 11 - row
                if up >= tops[c]:
                    continue
                # ash flakes: holes near its edge
                if look == "ash" and (c, row) in p.holes and up >= tops[c] - 3 and not veins:
                    continue
                if veins:
                    if p.vein(c, row, up):
                        img.putpixel((x0 + c, y0 + row), (255, 255, 255, 255))
                    continue
                rgb, a = p.color(c, row, up)
                if up == tops[c] - 1 and tops[c] < 12:
                    # the edge: a darker rim
                    rgb = shade(rgb, 0.7)
                img.putpixel((x0 + c, y0 + row), (*rgb, a))
        # the hand's flat (knuckles' end) and, once the whole arm is coated, the shoulder's top
        flats = [HANDS[arm]] + ([TOPS[arm]] if rank >= 3 else [])
        for (fx0, fy0, fx1, fy1) in flats:
            for x in range(fx0, fx1):
                for y in range(fy0, fy1):
                    if veins:
                        if p.vein(x - fx0, 0, y - fy0):
                            img.putpixel((x, y), (255, 255, 255, 255))
                        continue
                    rgb, a = p.color(x - fx0, 0, y - fy0)
                    img.putpixel((x, y), (*rgb, a))
    if rank >= 4:
        shoulders(img, look, veins)
    return img


def shoulders(img, look, veins):
    """Unbroken: the coat creeps over the shoulders and down the collar, front and back (the body: 8x12x4 at 16,16)."""
    p = Pattern(look, 19)
    r = random.Random(23)
    cells = []
    # the top face (20-27, 16-19): the outer columns
    for x in (20, 21, 26, 27):
        for y in range(16, 20):
            cells.append((x, y))
    # the sides (16-19 and 28-31, rows 20-): three rows down
    for x in list(range(16, 20)) + list(range(28, 32)):
        for y in range(20, 23 + r.choice((0, 1))):
            cells.append((x, y))
    # front (20-27) and back (32-39): the two outer columns, three rows down, two more ragged
    for x0 in (20, 32):
        for dx in (0, 1, 6, 7):
            for y in range(20, 22 + r.choice((1, 2, 3))):
                cells.append((x0 + dx, y))
    for (x, y) in cells:
        c, row = x % 16, y % 12
        if veins:
            if p.vein(c, row, 11 - row):
                img.putpixel((x, y), (255, 255, 255, 255))
            continue
        rgb, a = p.color(c, row, 11 - row)
        img.putpixel((x, y), (*rgb, a))


def bent(name):
    """The mark round the wrist (rows 1-5 up from the hand), white: ValorLook tints it."""
    img = blank()
    W = (255, 255, 255, 255)
    G = (200, 200, 200, 255)
    for (x0, y0) in ARMS:
        def put(c, up, col=W):
            if 0 <= c < 16 and 0 <= up < 12:
                img.putpixel((x0 + c, y0 + 11 - up), col)
        if name == "gate":
            # a bracer band, notched on each face
            for c in range(16):
                put(c, 3, W)
                if c % 4 != 2:
                    put(c, 4, G)
        elif name == "duel":
            # two crossed slashes on the front and back faces
            for f in (4, 12):
                for i in range(4):
                    put(f + i, 1 + i)
                    put(f + 3 - i, 1 + i, G)
        elif name == "hunt":
            # three claw stripes down the outer face
            for c in (0, 2):
                for up in range(1, 6):
                    put(c, up)
            for up in range(2, 5):
                put(1, up, G)
            for c in (8, 10):
                for up in range(1, 5):
                    put(c, up)
        elif name == "magebane":
            # a ring of runes: a dotted band with ticks above and below
            for c in range(16):
                if c % 2 == 0:
                    put(c, 3)
                if c % 4 == 1:
                    put(c, 2, G)
                    put(c, 4, G)
        elif name == "survivor":
            # a stitched scar: a zigzag with cross-stitches
            for c in range(16):
                up = 2 + (c // 2) % 2 * 2
                put(c, up)
                if c % 3 == 0:
                    put(c, up - 1, G)
                    put(c, up + 1, G)
        else:
            raise ValueError(name)
    return img


for look in LOOKS:
    for rank in range(5):
        coat(look, rank, False).save(os.path.join(OUT, f"{look}_{rank}.png"))
        coat(look, rank, True).save(os.path.join(OUT, f"{look}_veins_{rank}.png"))
for b in BENTS:
    bent(b).save(os.path.join(OUT, f"bent_{b}.png"))
print("wrote", len(LOOKS) * 10 + len(BENTS), "textures to", os.path.normpath(OUT))
