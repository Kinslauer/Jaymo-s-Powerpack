"""Town golems (Kingdoms 0.7.529): our own rune golem, painted in three materials (gilt, verdant and the
throne golem's diamond-set stone), plus their item icons. Ideas from Special Golems (ideas only, its files are not used); every pixel here is painted
from the ramps below.

Run from the repository root:  python -I kingdoms/tools/gen_town_golems.py

Texture layout (128x64) matches TownGolemModel's cubes (box UV: top, bottom; right, front, left, back):
  head    8x8x8   at (0,0)     crest 2x2x2 at (46,0)   core 4x4x1 at (34,0)
  chest  14x10x8  at (0,16)    waist 10x6x6 at (0,34)  (64,46 unused)
  arm     4x18x5  at (64,0)    leg   5x10x5 at (64,24)
"""
import os
import random
from PIL import Image

OUT = 'kingdoms/src/main/resources/assets/jaymoskingdoms/textures'
W, H = 128, 64


def hx(s):
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))


def ramp(*s):
    return [hx(c) for c in s]


# 7 steps each: 0 outline .. 6 glint, light from the top-left (as the vanilla-style icons)
KINDS = {
    'gilt':    dict(body=ramp('3f2e0e', '825d16', 'b47a12', 'dc9613', 'e9b115', 'f2d23c', 'fdff76'),
                    trim=hx('fff3b0'), glow=hx('fff07a'), dark=hx('2c1f08')),
    'verdant': dict(body=ramp('0a2e16', '0f4f22', '17712f', '1fa03f', '41cd5c', '7ff09a', 'c8ffd6'),
                    trim=hx('eaee57'), glow=hx('9dffb0'), dark=hx('05200e')),
    # the throne golem: dark polished stone set with diamond, not a block of diamond (Andy)
    'diamond': dict(body=ramp('16161b', '25252c', '33333c', '41414b', '50505b', '64646f', '7d7d8a'),
                    trim=hx('33ebcb'), glow=hx('a4fdf0'), dark=hx('0a0a0e')),
}
STONE = ramp('212121', '494949', '5a5a5a', '787777', '95918d', 'b3b1af', 'c9c7c4')


def box(u, v, w, h, d):
    """The six faces of a box-UV cube: name -> (x, y, width, height)."""
    return {
        'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d),
        'right': (u, v + d, d, h), 'front': (u + d, v + d, w, h),
        'left': (u + d + w, v + d, d, h), 'back': (u + 2 * d + w, v + d, w, h),
    }


PARTS = {
    'head': box(0, 0, 8, 8, 8), 'crest': box(46, 0, 2, 2, 2), 'core': box(34, 0, 4, 4, 1),
    'chest': box(0, 16, 14, 10, 8), 'waist': box(0, 34, 10, 6, 6), 'pack': box(64, 46, 10, 9, 4),
    'arm': box(64, 0, 4, 18, 5), 'leg': box(64, 24, 5, 10, 5),
}


def fill(px, rect, r, base, rnd, face):
    """A stony face: mottled mid-tones, a lit top-left edge and a shaded bottom-right edge."""
    x0, y0, w, h = rect
    lift = {'top': 1, 'bottom': -2, 'back': -1, 'right': 0, 'left': -1, 'front': 0}[face]
    for y in range(h):
        for x in range(w):
            i = base + lift + rnd.choice((0, 0, 0, 0, 0, 1, -1))
            if y == 0 or x == 0:
                i += 1
            if y == h - 1 or x == w - 1:
                i -= 1
            # cracks between stones: a dark seam every few rows on tall faces
            if h >= 8 and face not in ('top', 'bottom') and (y % 6 == 5) and rnd.random() < 0.7:
                i -= 1
            px[x0 + x, y0 + y] = r[max(1, min(6, i))] + (255,)


def paint_kind(name, k):
    rnd = random.Random(hash(name) & 0xFFFF)
    tex = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    glow = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    px, gx = tex.load(), glow.load()
    r = k['body']
    for part, faces in PARTS.items():
        base = {'waist': 2, 'leg': 3, 'crest': 4}.get(part, 3)
        ramp_ = STONE if part == 'core' else r
        for face, rect in faces.items():
            fill(px, rect, ramp_, base, rnd, face)

    # the head: a brow ridge, two eyes, a dark mouth slit
    fx, fy, _, _ = PARTS['head']['front']
    for x in range(8):
        px[fx + x, fy + 1] = r[2] + (255,)
    for ex in (1, 2, 5, 6):
        px[fx + ex, fy + 3] = k['glow'] + (255,)
        gx[fx + ex, fy + 3] = k['glow'] + (255,)
    for x in range(2, 6):
        px[fx + x, fy + 6] = k['dark'] + (255,)
    # the crest: a rune-stone point
    for face, (x0, y0, w, h) in PARTS['crest'].items():
        for y in range(h):
            for x in range(w):
                px[x0 + x, y0 + y] = k['trim'] + (255,)

    # the core: a carved stone set in the chest, its rune lit
    cx, cy, cw, ch = PARTS['core']['front']
    for y in range(ch):
        for x in range(cw):
            edge = x in (0, cw - 1) or y in (0, ch - 1)
            c = STONE[3] if edge else k['glow']
            px[cx + x, cy + y] = c + (255,)
            if not edge:
                gx[cx + x, cy + y] = k['glow'] + (255,)
    # rune lines on the chest: a ring round where the core sits, lines to the shoulders
    fx, fy, fw, fh = PARTS['chest']['front']
    ring = [(4, 1), (5, 1), (6, 1), (7, 1), (8, 1), (9, 1), (3, 2), (10, 2), (3, 3), (10, 3), (3, 4), (10, 4), (3, 5), (10, 5),
            (4, 6), (5, 6), (6, 6), (7, 6), (8, 6), (9, 6), (1, 1), (2, 1), (11, 1), (12, 1), (6, 8), (7, 8)]
    for (x, y) in ring:
        px[fx + x, fy + y] = k['trim'] + (255,)
        if name in ('gilt', 'diamond'):
            gx[fx + x, fy + y] = k['trim'] + (160,)
    # bands round the forearms and shins
    for part, rows in (('arm', (11, 12)), ('leg', (1,))):
        for face in ('right', 'front', 'left', 'back'):
            x0, y0, w, h = PARTS[part][face]
            for row in rows:
                for x in range(w):
                    px[x0 + x, y0 + row] = k['trim'] + (255,)
    # fists: darker knuckles
    for face in ('right', 'front', 'left', 'back', 'bottom'):
        x0, y0, w, h = PARTS['arm'][face]
        y_from = 0 if face == 'bottom' else h - 3
        for y in range(y_from, h):
            for x in range(w):
                px[x0 + x, y0 + y] = r[2 if face == 'bottom' else 2 + (x + y) % 2] + (255,)
    os.makedirs(f'{OUT}/entity/golem', exist_ok=True)
    tex.save(f'{OUT}/entity/golem/{name}.png')
    glow.save(f'{OUT}/entity/golem/{name}_glow.png')
    return tex


# 16x16 icons: a small rune golem standing (its "heart" carried as an item); light top-left.
FIGURE = '''
......4444......
.....455553.....
.....5g55g3.....
.....45dd53.....
...3344cc4433...
..344445544443..
..34t3c##3t443..
..3443t##t3443..
..3443333334433.
..2442333334422.
..2d2.3333.2d2..
.....33..33.....
.....33..33.....
.....3t..3t.....
....233..332....
................
'''
DIAMOND = '''
................
....3444442.....
...346665432....
..34566655432...
.3t56666555t32..
.23t56g554t321..
..234455443210..
...2344543210...
....23443210....
.....233210.....
......2210......
.......10.......
................
................
................
................
'''


def icon(grid, r, trim, glow, core=None):
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = im.load()
    rows = grid.strip('\n').split('\n')
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == '.':
                continue
            if ch.isdigit():
                c = r[int(ch)]
            elif ch == 'g':
                c = glow
            elif ch == 't':
                c = trim
            elif ch == 'c' or ch == '#':
                c = core if core else glow
                if ch == 'c':
                    c = STONE[3]
            elif ch == 'd':
                c = r[0]
            else:
                c = r[3]
            px[x, y] = c + (255,)
    return im


def main():
    os.makedirs(f'{OUT}/item', exist_ok=True)
    for name, k in KINDS.items():
        paint_kind(name, k)
        icon(FIGURE, k['body'], k['trim'], k['glow']).save(f'{OUT}/item/{name}_golem.png')
    print('painted', len(KINDS), 'golems + icons')


if __name__ == '__main__':
    main()
