"""Night's Blood patch 6 art: the night-cloak (icon, hood worn on the head, cloak worn on the back), the coffin item and the
Elder's Blood bottle. Flat shades from small ramps, drawn lit from the top-left; no noise, no gradients, no half-transparent
pixels (docs/Vanilla-Style.md).

The back-cloak texture keeps the box layout of tools/gen_hooded_cloak.py (HoodedCloakLayer reads it), so it is the archer
cloak's own texture pulled onto the night ramp and smoothed to flat patches.

Run from the repository root:  python3 -I tools/vanilla_style/night_blood.py
"""
import os
from PIL import Image

POWERS = 'src/main/resources/assets/jaymospowers/textures'
KINGDOMS = 'kingdoms/src/main/resources/assets/jaymoskingdoms/textures/item'


def hx(s):
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))


# the night-cloak: five shades of deep violet-black (0 darkest), a dull silver clasp
NIGHT = [hx(c) for c in ('0a0812', '171326', '231d3a', '342b52', '4a3e70')]
WOOD = [hx(c) for c in ('1a0f0a', '2e1b12', '46291b', '5e3a26', '7a4d33')]


def icon(grid, pal):
    rows = grid.strip('\n').split('\n')
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), [(i, len(r)) for i, r in enumerate(rows) if len(r) != 16]
    im = Image.new('RGBA', (16, 16))
    px = im.load()
    for y, r in enumerate(rows):
        for x, ch in enumerate(r):
            if ch != '.':
                px[x, y] = pal[ch] + (255,)
    return im


CLOAK = "\n".join([
    "................",
    ".....000000.....",
    "....01222210....",
    "...0123333210...",
    "..012333333210..",
    "..0123kkkk3210..",
    "..012kkkkkk210..",
    "..012kkkkkk210..",
    ".01223kkkk32210.",
    ".01222333322210.",
    ".012222g3222210.",
    ".012223223222210",
    ".012223223222210",
    ".011222322222110",
    ".001112222221100",
    "..000000000000..",
])

COFFIN = "\n".join([
    "................",
    "................",
    ".....000000.....",
    "....01222210....",
    "...0122222210...",
    "..012233332210..",
    "..012233332210..",
    ".01222233222210.",
    ".01222233222210.",
    ".01222233222210.",
    "..012233332210..",
    "..012222222210..",
    "...0112222110...",
    "....00111100....",
    ".....000000.....",
    "................",
])

BOTTLE = "\n".join([
    "................",
    ".....0kkkk0.....",
    ".....0kkkk0.....",
    "......1551......",
    "......1551......",
    "....115qqq11....",
    "...15qrrrrss1...",
    "...15qrrrrss1...",
    "...15qrrrrss1...",
    "...15qrrrrss1...",
    "...15qrrrrss1...",
    "...15qrrrrss1...",
    "....1qrrrss1....",
    "....11111111....",
    "................",
    "................",
])


def worn_hood():
    """The hood on the head (humanoid layer, 64x32): a deep cowl with the face left clear in a window."""
    im = Image.new('RGBA', (64, 32))
    px = im.load()
    c = {0: NIGHT[0], 1: NIGHT[1], 2: NIGHT[2], 3: NIGHT[3]}

    def face(x0, y0, w, h, shade):
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                px[x, y] = c[shade] + (255,)
    # head box 8x8x8 at u0 v0: top, bottom, right, front, left, back
    face(8, 0, 8, 8, 3)
    face(16, 0, 8, 8, 0)
    face(0, 8, 8, 8, 2)
    face(8, 8, 8, 8, 1)
    face(16, 8, 8, 8, 2)
    face(24, 8, 8, 8, 1)
    # a darker rim around the front, then the clear window for the face
    for x in range(8, 16):
        px[x, 8] = c[3] + (255,)
        px[x, 15] = c[0] + (255,)
    for y in range(8, 16):
        px[8, y] = c[2] + (255,)
        px[15, y] = c[0] + (255,)
    for y in range(10, 15):
        for x in range(10, 14):
            px[x, y] = (0, 0, 0, 0)
    # the shadowed inside edge of the opening
    for x in range(10, 14):
        px[x, 9] = c[0] + (255,)
    for y in range(9, 15):
        px[9, y] = c[0] + (255,)
    # a hem line where the hood meets the neck
    for x in range(0, 32):
        if px[x, 15][3]:
            px[x, 15] = c[0] + (255,)
    return im


def worn_cloak(src):
    """The archer cloak's texture flattened onto the night ramp: four patch shades, noise smoothed away."""
    im = Image.open(src).convert('RGBA')
    w, h = im.size
    px = im.load()
    lums = sorted({round(0.3 * px[x, y][0] + 0.59 * px[x, y][1] + 0.11 * px[x, y][2]) for x in range(w) for y in range(h) if px[x, y][3] >= 128})
    lo, hi = lums[0], lums[-1]
    level = {}
    for x in range(w):
        for y in range(h):
            r, g, b, a = px[x, y]
            if a >= 128:
                lum = 0.3 * r + 0.59 * g + 0.11 * b
                level[(x, y)] = min(4, int((lum - lo) / (hi - lo + 1) * 5))
    out = Image.new('RGBA', (w, h))
    op = out.load()
    for (x, y), lv in level.items():
        near = [level[(x + dx, y + dy)] for dx in (-1, 0, 1) for dy in (-1, 0, 1) if (x + dx, y + dy) in level]
        mode = max(set(near), key=lambda v: (near.count(v), -v))
        op[x, y] = NIGHT[max(0, min(4, mode))] + (255,)
    return out


def save(im, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im.save(path)
    print('wrote', path)


def main():
    pal = {str(i): NIGHT[i] for i in range(5)}
    pal['k'] = hx('050408')
    pal['g'] = hx('b9b9c4')
    save(icon(CLOAK, pal), POWERS + '/item/night_cloak.png')
    save(worn_hood(), POWERS + '/entity/equipment/humanoid/night_cloak.png')
    save(worn_cloak(POWERS + '/entity/worn/hooded_cloak.png'), POWERS + '/entity/worn/hooded_cloak_night.png')
    save(icon(COFFIN, {str(i): WOOD[i] for i in range(5)}), KINGDOMS + '/coffin.png')
    glass = {'0': hx('181818'), '1': hx('6b6b6b'), '5': hx('d8d8d8'), 'k': hx('866526'),
             'p': hx('2a0505'), 'q': hx('6e0a0a'), 'r': hx('8a0303'), 's': hx('4f0707')}
    save(icon(BOTTLE, glass), KINGDOMS + '/elders_blood.png')


if __name__ == '__main__':
    main()
