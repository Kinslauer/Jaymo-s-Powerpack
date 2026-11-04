"""RPG Series forged weapons redrawn in vanilla's style (our own paintings; their art is all rights reserved, so none of
its pixels are used). Rogues & Warriors' daggers, sickles, double axes and glaives and Archers' spears, in every vanilla
metal they come in, with flint and stone where the mod has them. Ruby, aether and aeternium keep the mod's own art.

Daggers and sickles are 16px like the mod's; double axes, glaives and spears are 32px because the mod draws them on
models scaled x2 in hand (vanilla's pixel size on screen), so they are painted at 32 with the same rules.

    python -I tools/vanilla_style/rpg_series.py            (writes into the vanilla_style pack)
    python -I tools/vanilla_style/rpg_series.py <sheet.png> (also a preview sheet)
"""
import os
import sys
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from vstyle import MAT, STICK, ramp  # noqa: E402

OUT = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources', 'resourcepacks', 'vanilla_style',
                   'assets')
RAMPS = dict(MAT)
RAMPS['flint'] = ramp('17171b', '2a2a30', '3b3b43', '4f4f58', '666670', '80808a', 'a8a8b2')
RAMPS['golden'] = MAT['gold']


def shade(mask, n):
    """mask: n x n list of chars ('#' metal, '/' grip, '.' clear). Returns levels: 0-6 for metal, 'a'-'d' for the grip,
    lit from the top-left like vanilla (see vstyle.shade)."""
    def at(x, y):
        return mask[y][x] if 0 <= x < n and 0 <= y < n else '.'
    out = [r[:] for r in mask]
    for y in range(n):
        for x in range(n):
            c = mask[y][x]
            if c == '/':
                lo, ro = at(x - 1, y) != '/', at(x + 1, y) != '/'
                up, dn = at(x, y - 1) != '/', at(x, y + 1) != '/'
                if (lo or up) and not (ro or dn):
                    out[y][x] = 'b'
                elif (ro or dn) and not (lo or up):
                    out[y][x] = 'a'
                elif lo and ro or up and dn:
                    out[y][x] = 'b' if up or lo else 'a'
                else:
                    out[y][x] = 'd' if (x + y) % 2 == 0 else 'c'
            elif c == '#':
                nb = [at(x, y - 1), at(x - 1, y), at(x, y + 1), at(x + 1, y)]
                if any(v != '#' for v in nb):
                    lit = nb[0] != '#' or nb[1] != '#'
                    dark = nb[2] != '#' or nb[3] != '#'
                    out[y][x] = 1 if lit and not dark else 0
                    continue
                a = 0
                while at(x - a - 1, y - a - 1) == '#':
                    a += 1
                b = 0
                while at(x + b + 1, y + b + 1) == '#':
                    b += 1
                out[y][x] = 5 if a == 0 else 2 if b == 0 else (4 if a / (a + b) < 0.5 else 3)
    return out


def render(layers, n, mat):
    """layers: list of (mask, kind) drawn in order; kind 'metal' or 'grip' or ('ramp', ramp)."""
    im = Image.new('RGBA', (n, n))
    px = im.load()
    for mask, kind in layers:
        lv = shade(mask, n)
        for y in range(n):
            for x in range(n):
                v = lv[y][x]
                if mask[y][x] == '.':
                    continue
                if kind == 'grip' and isinstance(v, str):
                    px[x, y] = STICK['abcd'.index(v)] + (255,)
                elif kind == 'grip':
                    px[x, y] = RAMPS[mat][v] + (255,)  # a pommel on the grip
                else:
                    r = RAMPS[mat] if kind == 'metal' else kind[1]
                    px[x, y] = r[v] + (255,)
    return im


def canvas(n):
    return Image.new('1', (n, n), 0)


def to_mask(img, ch):
    n = img.width
    p = img.load()
    return [[ch if p[x, y] else '.' for x in range(n)] for y in range(n)]


def diag_line(draw, x0, y0, x1, y1, w):
    """A thick diagonal from bottom-left (x0, y0) to top-right (x1, y1), w pixels across."""
    for i in range(w):
        draw.line([(x0 + i, y0), (x1 + i, y1)], fill=1)


def minus(a, b):
    n = len(a)
    return [['.' if b[y][x] != '.' else a[y][x] for x in range(n)] for y in range(n)]


# ---- shapes ------------------------------------------------------------------------------------------------------

def grid(text):
    return [list(r) for r in text.strip('\n').split('\n')]


def dagger():
    """16px: a leaf blade up and right, a crossguard, a wrapped grip and pommel."""
    return [(grid("""
................
................
................
................
................
................
................
................
................
................
...//...........
..//............
.//.............
.#..............
................
................"""), 'grip'), (grid("""
................
................
................
................
................
................
................
................
.....#..........
....##..........
...###..........
......##........
................
................
................
................"""), 'metal'), (grid("""
................
................
..........###...
.........#####..
........#####...
.......#####....
......#####.....
.....#####......
.....####.......
......##........
................
................
................
................
................
................"""), 'metal')]


def sickle():
    """16px: a crescent blade rising from the grip and hooking over to the right, its tip pointing down."""
    return [(grid("""
................
................
................
................
................
................
................
................
................
................
...//...........
..//............
.//.............
.#..............
................
................"""), 'grip'), (grid("""
................
................
................
................
................
................
................
................
...###..........
...###..........
................
................
................
................
................
................"""), 'metal'), (grid("""
................
........#####...
......########..
.....####..####.
....###......###
....###.......##
....###.......##
....##.........#
....##..........
................
................
................
................
................
................
................"""), 'metal')]


def uv(cx, cy, pts):
    """Points given along the haft (u: toward the top-right) and across it (v: toward the bottom-right), about (cx, cy)."""
    k = 0.7071
    return [(cx + (u + v) * k, cy + (v - u) * k) for u, v in pts]


def haft32(x0, y0, x1, y1, w=3):
    im = canvas(32)
    diag_line(ImageDraw.Draw(im), x0, y0, x1, y1, w)
    return to_mask(im, '/')


def poly_mask(*polys):
    im = canvas(32)
    d = ImageDraw.Draw(im)
    for pts in polys:
        d.polygon(pts, fill=1)
    return to_mask(im, '#')


def double_axe():
    """32px: two crescent bits across the haft's head, flaring at the edges, and a short top spike."""
    cx, cy = 21, 11
    bit = [(-2.5, 1.5), (-4, 4), (-7, 9), (-7, 11), (-4, 12.5), (0, 13), (4, 12.5), (7, 11), (7, 9), (4, 4), (2.5, 1.5)]
    head = poly_mask(uv(cx, cy, bit), uv(cx, cy, [(u, -v) for u, v in bit]), uv(cx, cy, [(-3, -2), (5, -2), (5, 2), (-3, 2)]))
    grip = minus(haft32(2, 29, 20, 11), head)
    return [(grip, 'grip'), (head, 'metal')]


def glaive():
    """32px: a long, curved single-edged blade on a pole, a spike at its back."""
    cx, cy = 19, 13
    blade = [(0, -1.5), (6, -2), (12, -3), (16, -2.5), (17, 0), (14, 2.5), (8, 3.5), (2, 2), (0, 1.5)]
    spike = [(1, -1.5), (3, -5), (4, -1.5)]
    head = poly_mask(uv(cx, cy, blade), uv(cx, cy, spike))
    fer = minus(poly_mask(uv(cx, cy, [(-3, -2), (0, -2), (0, 2), (-3, 2)])), head)
    grip = minus(minus(haft32(2, 29, 18, 13), head), fer)
    return [(grip, 'grip'), (fer, 'metal'), (head, 'metal')]


def spear():
    """32px: a long leaf point on a socket, a long shaft (vanilla's spear, lengthened)."""
    cx, cy = 21, 10
    point = [(0, -1.5), (3, -3), (8, -2.5), (13, 0), (8, 2.5), (3, 3), (0, 1.5)]
    head = poly_mask(uv(cx, cy, point))
    sock = minus(poly_mask(uv(cx, cy, [(-4, -1.6), (0, -1.6), (0, 1.6), (-4, 1.6)])), head)
    grip = minus(minus(haft32(2, 29, 19, 12, 2), head), sock)
    return [(grip, 'grip'), (sock, 'metal'), (head, 'metal')]


# ---- what is painted ---------------------------------------------------------------------------------------------

WEAPONS = {
    ('rogues', 'dagger'): (dagger, 16, ['flint', 'iron', 'golden', 'diamond', 'netherite']),
    ('rogues', 'sickle'): (sickle, 16, ['iron', 'golden', 'diamond', 'netherite']),
    ('rogues', 'double_axe'): (double_axe, 32, ['stone', 'iron', 'golden', 'diamond', 'netherite']),
    ('rogues', 'glaive'): (glaive, 32, ['iron', 'golden', 'diamond', 'netherite']),
    ('archers', 'spear'): (spear, 32, ['flint', 'iron', 'golden', 'diamond', 'netherite']),
}


def main():
    made = []
    for (ns, kind), (shape, n, mats) in WEAPONS.items():
        for mat in mats:
            im = render(shape(), n, mat)
            path = os.path.join(OUT, ns, 'textures', 'item', f'{mat}_{kind}.png')
            os.makedirs(os.path.dirname(path), exist_ok=True)
            im.save(path)
            made.append(im)
    print(len(made), 'icons')
    if len(sys.argv) > 1:
        s = 96
        sheet = Image.new('RGBA', (s * 6, s * ((len(made) + 5) // 6)), (60, 60, 70, 255))
        for i, im in enumerate(made):
            sheet.alpha_composite(im.resize((s, s), Image.NEAREST), ((i % 6) * s, (i // 6) * s))
        sheet.save(sys.argv[1])


if __name__ == '__main__':
    main()
