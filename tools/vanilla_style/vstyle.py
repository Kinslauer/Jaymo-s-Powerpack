"""Vanilla-style item painter: shapes are drawn once as shade grids, colours come from vanilla's own ramps."""
from PIL import Image

def hx(s): return tuple(int(s[i:i+2], 16) for i in (0, 2, 4))
def ramp(*s): return [hx(c) for c in s]

# 7-step material ramps (0 = outline, 6 = glint), taken from vanilla's swords
MAT = {
    'wood':      ramp('20180a', '372910', '473614', '594319', '755821', '866526', '9c7a32'),
    'stone':     ramp('212121', '494949', '5a5a5a', '787777', '95918d', 'b3b1af', 'c9c7c4'),
    'iron':      ramp('181818', '444444', '6b6b6b', '969696', 'bebebe', 'd8d8d8', 'ffffff'),
    'gold':      ramp('3f2e0e', '825d16', 'dc9613', 'e9b115', 'eaee57', 'fdff76', 'ffffff'),
    'diamond':   ramp('082520', '0e3f36', '156355', '1e8a77', '2bc7ac', '33ebcb', 'a4fdf0'),
    'netherite': ramp('160814', '322727', '4f3c3e', '5d565d', '706770', '867b86', 'a39aa3'),
    'copper':    ramp('421c11', '5f2c1c', '803921', '9c4e31', 'e3826c', 'fc9982', 'fdd4cb'),
}
STICK = ramp('281e0b', '493615', '684e1e', '896727')          # a b c d
BOWL = ramp('1f1502', '302109', '3f2b0b', '45320d', '533909', '714f0f')  # vanilla stew bowl

def paint(grid, mat='iron', extra=None):
    rows = [r for r in grid.strip('\n').split('\n')]
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), [len(r) for r in rows]
    im = Image.new('RGBA', (16, 16)); px = im.load()
    pal = {str(i): c for i, c in enumerate(MAT[mat])}
    pal.update({k: c for k, c in zip('abcd', STICK)})
    pal.update({k: c for k, c in zip('uvwxyz', BOWL)})
    if extra: pal.update({k: hx(v) if isinstance(v, str) else v for k, v in extra.items()})
    for y, r in enumerate(rows):
        for x, ch in enumerate(r):
            if ch != '.': px[x, y] = pal[ch] + (255,)
    return im

def shade(mask, glint=None):
    """mask: 16 rows; '#' = material, '/' = wooden handle, anything else ('.' clear, or an accent letter) kept.
    Returns a shade grid lit from the top-left like vanilla tools: lighter outline on the lit side, darkest
    outline on the shadow side, a bright band just inside the lit edge."""
    rows = [list(r) for r in mask.strip('\n').split('\n')]
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), [len(r) for r in rows]
    def at(x, y): return rows[y][x] if 0 <= x < 16 and 0 <= y < 16 else '.'
    out = [r[:] for r in rows]
    # handle: a 3-wide diagonal band; per row, left px b, middle c/d alternating, right a
    for y in range(16):
        xs = [x for x in range(16) if rows[y][x] == '/']
        for i, x in enumerate(xs):
            left_open = at(x - 1, y) != '/'
            right_open = at(x + 1, y) != '/'
            if right_open and not left_open: out[y][x] = 'a'
            elif left_open and not right_open: out[y][x] = 'b'
            elif left_open and right_open: out[y][x] = 'a' if at(x, y + 1) == '.' else 'b'
            else: out[y][x] = 'd' if y % 2 == 0 else 'c'
    def solid(c): return c not in '.'
    MAT = '#+*-='
    for y in range(16):
        for x in range(16):
            c0 = rows[y][x]
            if c0 in '+*-=':
                out[y][x] = {'+': '5', '*': '6', '-': '2', '=': '1'}[c0]; continue
            if c0 != '#': continue
            n = {d: at(x + dx, y + dy) for d, (dx, dy) in dict(u=(0, -1), l=(-1, 0), d=(0, 1), r=(1, 0)).items()}
            if not all(solid(c) for c in n.values()):
                lit = not solid(n['u']) or not solid(n['l'])
                dark = not solid(n['d']) or not solid(n['r'])
                out[y][x] = '1' if lit and not dark else '0'
                continue
            # interior: march toward the top-left and bottom-right
            a = 0
            while solid(at(x - a - 1, y - a - 1)) and at(x - a - 1, y - a - 1) in MAT: a += 1
            b = 0
            while solid(at(x + b + 1, y + b + 1)) and at(x + b + 1, y + b + 1) in MAT: b += 1
            if a == 0: lvl = 5
            elif b == 0: lvl = 2
            else:
                f = a / (a + b)
                lvl = 4 if f < 0.5 else 3
            out[y][x] = str(lvl)
    if glint:
        for x, y in glint: out[y][x] = '6'
    return '\n'.join(''.join(r) for r in out)

def rows(g):
    r = g.strip('\n').split('\n')
    assert len(r) == 16 and all(len(x) == 16 for x in r), [len(x) for x in r]
    return r

def parts(grid, ramps, accents=None, glints=None, grip=STICK, order=None):
    """grid: one letter per part ('/' = wooden grip shaded like vanilla's stick); each part is shaded on its own
    (outline in its darkest shade, lit top-left), later parts in `order` on top. accents: char -> (part, hex).
    A ramp can be a function (level, x, y) -> colour, for patterns such as kernels or two-tone roots."""
    accents = accents or {}; glints = glints or {}
    g = rows(grid)
    im = Image.new('RGBA', (16, 16)); px = im.load()
    letters = order or sorted({c for r in g for c in r if c not in '.'} - set(accents))
    for part in letters:
        mine = {part} | {a for a, (p, _) in accents.items() if p == part}
        if part == '/':
            mask = '\n'.join(''.join('/' if c in mine else '.' for c in r) for r in g)
        else:
            mask = '\n'.join(''.join('#' if c in mine else '.' for c in r) for r in g)
        lv = rows(shade(mask, glints.get(part)))
        for y in range(16):
            for x in range(16):
                c = g[y][x]
                if c not in mine: continue
                if c in accents: px[x, y] = hx(accents[c][1]) + (255,)
                elif part == '/': px[x, y] = grip['abcd'.index(lv[y][x])] + (255,)
                elif callable(ramps[part]): px[x, y] = ramps[part](int(lv[y][x]), x, y) + (255,)
                else: px[x, y] = ramps[part][int(lv[y][x])] + (255,)
    return im
