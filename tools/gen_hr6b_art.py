#!/usr/bin/env python3
"""Hunter's Road 6 part B art: 16x16 item textures and lab block textures, drawn from plain shapes (our own, vanilla-sized).
Run from the repo root: python -I tools/gen_hr6b_art.py"""
import os
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
P = os.path.join(ROOT, 'src/main/resources/assets/jaymospowers/textures')
K = os.path.join(ROOT, 'kitchen/src/main/resources/assets/jaymoskitchen/textures')
OUT = (36, 28, 34, 255)


def img():
    return Image.new('RGBA', (16, 16), (0, 0, 0, 0))


def shade(c, f):
    return (max(0, min(255, int(c[0] * f))), max(0, min(255, int(c[1] * f))), max(0, min(255, int(c[2] * f))), 255)


def rgb(h):
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255, 255)


def bottle(colour, mark=None, big=False, sealed=True):
    im = img()
    px = im.load()
    c = rgb(colour)
    body = [(5, 6, 10, 14)] if not big else [(4, 5, 11, 14)]
    x0, y0, x1, y1 = body[0]
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            edge = x in (x0, x1) or y in (y0, y1)
            px[x, y] = OUT if edge else (shade(c, 1.15) if y < y0 + 3 else c)
    # corners rounded
    for x, y in ((x0, y1), (x1, y1)):
        px[x, y] = (0, 0, 0, 0)
    for y in range(2, y0):
        for x in (6, 9):
            px[x, y] = OUT
        for x in (7, 8):
            px[x, y] = (214, 226, 232, 255) if y > 3 else (150, 108, 70, 255)
    px[7, 2] = px[8, 2] = (120, 84, 54, 255)
    if sealed:
        px[7, 3] = px[8, 3] = (170, 126, 82, 255)
    px[x0 + 1, y0 + 1] = (255, 255, 255, 200)
    if mark:
        for (dx, dy) in mark:
            px[x0 + 1 + dx, y0 + 3 + dy] = shade(c, 0.55)
    return im


MARKS = {
    'cross': [(1, 0), (0, 1), (1, 1), (2, 1), (1, 2)],
    'dot': [(1, 1)],
    'bar': [(0, 1), (1, 1), (2, 1)],
    'tri': [(1, 0), (0, 1), (2, 1)],
    'wave': [(0, 1), (1, 0), (2, 1)],
    'eye': [(0, 1), (1, 0), (1, 2), (2, 1)],
}


def paste(colour, dark):
    im = img()
    px = im.load()
    c, d = rgb(colour), rgb(dark)
    blob = [(4, 9, 11), (5, 8, 12), (6, 7, 13), (7, 6, 13), (8, 5, 12), (9, 4, 11)]
    for y, x0, x1 in [(8, 4, 11), (9, 3, 12), (10, 3, 12), (11, 3, 12), (12, 4, 11), (7, 5, 10)]:
        for x in range(x0, x1 + 1):
            edge = x in (x0, x1) or y in (7, 12)
            px[x, y] = shade(d, 0.6) if edge else (shade(c, 1.2) if (x + y) % 5 == 0 else c)
    px[6, 8] = px[7, 9] = (255, 255, 255, 120)
    return im


def vial(colour):
    im = img()
    px = im.load()
    c = rgb(colour)
    for y in range(2, 15):
        for x in range(6, 10):
            edge = x in (6, 9) or y in (2, 14)
            px[x, y] = OUT if edge else (c if y > 6 else (200, 220, 230, 160))
    px[7, 1] = px[8, 1] = (140, 100, 62, 255)
    px[7, 3] = (255, 255, 255, 180)
    return im


def formula():
    im = img()
    px = im.load()
    for y in range(2, 14):
        for x in range(3, 13):
            edge = x in (3, 12) or y in (2, 13)
            px[x, y] = (96, 70, 44, 255) if edge else (226, 208, 166, 255)
    for i, y in enumerate((4, 6, 8, 10)):
        for x in range(5, 11 - (i % 2) * 2):
            px[x, y] = (110, 76, 52, 255)
    px[10, 11] = px[11, 11] = (150, 40, 40, 255)
    return im


def needle():
    im = img()
    px = im.load()
    for i in range(11):
        px[3 + i, 12 - i] = (205, 208, 214, 255)
        px[4 + i, 12 - i] = (150, 156, 166, 255)
    px[2, 13] = px[3, 13] = px[2, 12] = (186, 120, 70, 255)
    px[12, 3] = px[13, 2] = (230, 90, 70, 255)
    return im


def casing():
    im = img()
    px = im.load()
    for y in range(5, 14):
        for x in range(5, 11):
            edge = x in (5, 10) or y in (5, 13)
            px[x, y] = shade((190, 140, 70), 0.6) if edge else (196, 146, 80, 255) if x < 8 else (166, 118, 58, 255)
    px[7, 4] = px[8, 4] = (120, 84, 54, 255)
    for x in range(6, 10):
        px[x, 9] = (60, 46, 36, 255)
    return im


def spent():
    im = bottle(0x9aa4aa, None)
    px = im.load()
    for y in range(8, 14):
        for x in range(6, 10):
            if px[x, y][3]:
                px[x, y] = (200, 214, 222, 150)
    return im


def flask(netherite):
    im = img()
    px = im.load()
    body = (88, 62, 52, 255) if netherite else (150, 108, 70, 255)
    metal = (66, 56, 62, 255) if netherite else (196, 150, 84, 255)
    for y in range(5, 15):
        for x in range(3, 13):
            edge = x in (3, 12) or y in (5, 14)
            px[x, y] = shade(body, 0.5) if edge else (body if y > 6 else shade(body, 1.2))
    for x in (4, 11):
        px[x, 5] = (0, 0, 0, 0)
        px[x, 14] = (0, 0, 0, 0)
    for y in range(2, 5):
        for x in range(6, 10):
            px[x, y] = metal if (x in (6, 9) or y == 2) else shade(metal, 0.8)
    for x in range(4, 12):
        px[x, 9] = metal
    px[8, 11] = px[7, 12] = (255, 255, 255, 120)
    return im


def organ(base, dark, kind):
    im = img()
    px = im.load()
    c, d = rgb(base), shade(rgb(dark), 1.0)
    cells = []
    if kind == 'gall':
        cells = [(x, y) for y in range(5, 13) for x in range(4, 12) if (x - 7.5) ** 2 / 14 + (y - 8.5) ** 2 / 12 <= 1]
    elif kind == 'tongue':
        cells = [(x, y) for y in range(3, 14) for x in range(6, 10) if abs(x - 7.5 - (y - 8) * 0.12) <= 1.6 + (y < 5) * 0.3]
    elif kind == 'marrow':
        cells = [(x, y) for y in range(4, 13) for x in range(6, 10)] + [(x, y) for y in (3, 4, 12, 13) for x in (5, 6, 9, 10)]
    elif kind == 'sac':
        cells = [(x, y) for y in range(4, 14) for x in range(3, 13) if (x - 7.5) ** 2 / 20 + (y - 9) ** 2 / 20 <= 1]
    elif kind == 'gizzard':
        cells = [(x, y) for y in range(5, 13) for x in range(3, 13) if (x - 7.5) ** 2 / 24 + (y - 8.5) ** 2 / 10 <= 1]
    else:
        cells = [(x, y) for y in range(4, 14) for x in range(3, 13) if (x - 7.5) ** 2 / 22 + (y - 8.5) ** 2 / 17 <= 1 or (6 <= x <= 9 and y < 6)]
    s = set(cells)
    for (x, y) in cells:
        edge = any((x + dx, y + dy) not in s for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        px[x, y] = d if edge else (shade(c, 1.2) if (x * 3 + y * 5) % 7 == 0 else c)
    for (x, y) in cells:
        if (x + y) % 6 == 0 and px[x, y] == c:
            px[x, y] = shade(c, 0.8)
    return im


def tile(c1, c2, pat='plain'):
    im = Image.new('RGBA', (16, 16), c1)
    px = im.load()
    for y in range(16):
        for x in range(16):
            if pat == 'planks' and y % 4 == 3:
                px[x, y] = c2
            elif pat == 'copper' and (x + y * 2) % 11 == 0:
                px[x, y] = c2
            elif pat == 'paper' and (y % 3 == 1 and 2 < x < 13):
                px[x, y] = c2
            elif pat == 'glass' and (x in (0, 15) or y in (0, 15)):
                px[x, y] = c2
            elif pat == 'noise' and (x * 7 + y * 13) % 9 == 0:
                px[x, y] = c2
    return im


def save(im, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im.save(path)


def main():
    it = P + '/item/hunter/'
    draughts = {
        'draught_slow_mending': (0xc85a7a, 'cross'), 'draught_grey_sight': (0x9aa0a8, 'eye'),
        'draught_savagery': (0xb03a30, 'tri'), 'draught_keen_edge': (0xd8d2c0, 'bar'),
        'draught_deep_breath': (0x3a78c8, 'wave'), 'draught_thorned_blood': (0x7a1820, 'tri'),
        'draught_thin_venom': (0x7ab83a, 'dot'), 'draught_plenty': (0xd8a838, 'dot'),
        'draught_cleanser': (0xe8f0f4, 'cross'),
    }
    for name, (col, mark) in draughts.items():
        save(bottle(col, MARKS[mark]), it + name + '.png')
    brews = {
        'brew_marsh_fury': (0x5a7a30, 'tri'), 'brew_grave_hunger': (0x5a4a6a, 'eye'), 'brew_pack_marrow': (0xb8a888, 'bar'),
        'brew_fog_shroud': (0xa8b8c0, 'wave'), 'brew_imp_gallop': (0xb85a28, 'tri'), 'brew_troll_blood': (0x4a8a58, 'cross'),
    }
    for name, (col, mark) in brews.items():
        save(bottle(col, MARKS[mark], big=True), it + name + '.png')
    save(spent(), it + 'spent_phial.png')
    save(casing(), it + 'spent_casing.png')
    save(formula(), it + 'formula.png')
    save(needle(), it + 'testing_needle.png')
    save(paste(0x6a8a40, 0x3a5020), it + 'poison_paste.png')
    save(paste(0x9a6a28, 0x5a3a10), it + 'toxic_paste.png')
    save(vial(0x9ae04a), it + 'pure_toxin.png')
    save(flask(False), it + 'hunters_flask.png')
    save(flask(True), it + 'netherite_hunters_flask.png')
    b = P + '/block/hunter/'
    save(tile((120, 84, 52, 255), (88, 60, 36, 255), 'planks'), b + 'bench_wood.png')
    save(tile((196, 120, 76, 255), (150, 86, 52, 255), 'copper'), b + 'lab_copper.png')
    save(tile((200, 226, 232, 90), (240, 250, 252, 160), 'glass'), b + 'lab_glass.png')
    save(tile((226, 208, 166, 255), (120, 90, 60, 255), 'paper'), b + 'lab_paper.png')
    save(tile((140, 120, 96, 255), (100, 84, 64, 255), 'noise'), b + 'lab_cork.png')
    save(tile((120, 190, 80, 255), (80, 150, 50, 255), 'noise'), b + 'lab_liquid.png')
    k = K + '/item/'
    organs = {
        'mire_gall': (0x6a7a28, 0x3a4414, 'gall'), 'grave_tongue': (0x8a5060, 0x4a2430, 'tongue'),
        'pack_marrow': (0xd0c8a8, 0x7a7258, 'marrow'), 'wight_sac': (0xa8bcc8, 0x587080, 'sac'),
        'imp_gizzard': (0xa04a30, 0x502010, 'gizzard'), 'troll_liver': (0x4a6a3a, 0x20381a, 'liver'),
    }
    for name, (c, d, kind) in organs.items():
        save(organ(c, d, kind), k + name + '.png')
    print('art written')


main()
