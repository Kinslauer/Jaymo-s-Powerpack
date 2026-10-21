"""Jaymo's Kitchen 0.5.0 brewing art: block textures, drink icons and block models for the cask and the still.

Run from the repository root:  python3 -I tools/vanilla_style/brewing.py
Writes into kitchen/src/main/resources/assets/jaymoskitchen (textures/block, textures/item, models/block).
Colours are vanilla's own ramps (vstyle.MAT: iron, copper; oak planks' tones); shapes are drawn on a 16x16 grid.
"""
import json, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from vstyle import MAT, hx
from PIL import Image

ROOT = 'kitchen/src/main/resources/assets/jaymoskitchen'
OAK = [hx(c) for c in ('5e4a2a', '7a6038', '9c7e4a', 'b4955b', 'c9aa6c')]   # oak planks, dark to light


def save(im, kind, name):
    d = f'{ROOT}/textures/{kind}'
    os.makedirs(d, exist_ok=True)
    im.save(f'{d}/{name}.png')


def canvas(fill=None):
    im = Image.new('RGBA', (16, 16))
    if fill:
        im.paste(fill + (255,), (0, 0, 16, 16))
    return im


# ---- block textures ----

def cask_wood():
    """Vertical staves, a lit edge on each and a dark seam between them."""
    im = canvas(); px = im.load()
    for x in range(16):
        stave, col = divmod(x, 4)
        for y in range(16):
            tone = 2 + (1 if stave % 2 else 0)
            if col == 0:
                tone = 0           # seam
            elif col == 1:
                tone += 1          # lit edge
            if (y * 7 + x * 3 + stave * 5) % 11 == 0 and col not in (0, 1):
                tone -= 1          # grain fleck
            px[x, y] = OAK[max(0, min(4, tone))] + (255,)
    return im


def cask_end():
    """The round head: rings of wood inside an iron rim."""
    im = canvas(); px = im.load()
    iron = MAT['iron']
    for y in range(16):
        for x in range(16):
            r = max(abs(x - 7.5), abs(y - 7.5))
            if r > 7:
                px[x, y] = iron[0] + (255,)
            elif r > 6:
                px[x, y] = iron[2] + (255,)
            else:
                ring = int(((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5)
                px[x, y] = OAK[(1, 3, 2)[ring % 3]] + (255,)
    return im


def cask_hoop():
    im = canvas(); px = im.load()
    iron = MAT['iron']
    for y in range(16):
        for x in range(16):
            tone = 2 if y in (0, 15) else 3 if y in (1, 14) else 2
            if y == 1 or y == 2:
                tone = 4
            if x % 8 == 3 and y in (5, 10):
                tone = 5          # rivet
            px[x, y] = iron[tone] + (255,)
    return im


def still_copper():
    """Hammered copper: soft bands with lit rivets."""
    im = canvas(); px = im.load()
    cu = MAT['copper']
    for y in range(16):
        for x in range(16):
            tone = 3
            if y % 8 in (0,):
                tone = 1
            elif y % 8 == 1:
                tone = 4
            elif (x + y * 2) % 7 == 0:
                tone = 4
            elif (x * 3 + y) % 9 == 0:
                tone = 2
            if y % 8 == 4 and x % 4 == 2:
                tone = 5          # rivet
            px[x, y] = cu[tone] + (255,)
    return im


# ---- drink icons ----
# One bottle shape for all of them; the liquid's three tones and the cork vary.

LIQUIDS = {
    'ale':          ('d9962a', 'b0701a', '7a470e'),
    'cider':        ('f1e08a', 'dcc65a', 'b5a03a'),
    'mead':         ('f6c84a', 'e8a428', 'b87814'),
    'berry_wine':   ('b0223a', '80142b', '520a1c'),
    'strong_spirits': ('f2f0dc', 'e2dcb6', 'c4b98a'),
}
GLASS_LINE = hx('3b4350')
GLASS_LIT = hx('dfe9f2')
GLASS_FILL = hx('aab8c6')
CORK = [hx('a87a46'), hx('7f5a30')]


def bottle(name, cork):
    light, mid, dark = (hx(c) for c in LIQUIDS[name])
    im = Image.new('RGBA', (16, 16)); px = im.load()

    def put(x, y, c):
        px[x, y] = c + (255,)

    # (row: (left outline x, right outline x)); the neck is two wide, the body ten
    spans = {2: (6, 9), 3: (6, 9), 4: (6, 9), 5: (6, 9), 6: (5, 10), 7: (4, 11)}
    for y in range(8, 14):
        spans[y] = (3, 12)
    spans[14] = (4, 11)
    for y, (l, r) in spans.items():
        for x in range(l, r + 1):
            edge = x in (l, r)
            if y == 14:
                put(x, y, GLASS_LINE)
            elif edge:
                put(x, y, GLASS_LINE)
            elif y < 7:
                put(x, y, GLASS_LIT if x == l + 1 else GLASS_FILL)
            else:
                # liquid, lit from the top left
                if y == 7:
                    put(x, y, light)
                elif x == l + 1:
                    put(x, y, light)
                elif y >= 12 or x == r - 1:
                    put(x, y, dark)
                else:
                    put(x, y, mid)
    # the neck's rim, and a glint on the glass
    for x in (6, 7, 8, 9):
        put(x, 2, GLASS_LINE)
    put(7, 3, GLASS_LIT)
    put(4, 9, GLASS_LIT)
    put(4, 10, GLASS_LIT)
    if cork:
        for y in (0, 1, 2):
            put(7, y, CORK[0]); put(8, y, CORK[1])
        put(6, 1, GLASS_LINE); put(9, 1, GLASS_LINE)
        put(6, 0, GLASS_LINE); put(9, 0, GLASS_LINE)
        put(7, 3, CORK[0]); put(8, 3, CORK[1])
    return im


# ---- block models (cuboids; uv left to the default) ----

def face_set(tex):
    return {d: {'texture': '#' + tex} for d in ('north', 'east', 'south', 'west', 'up', 'down')}


def box(name, a, b, tex, ends=None):
    faces = face_set(tex)
    if ends:   # west / east faces use another texture (a barrel's heads)
        faces['west'] = {'texture': '#' + ends}
        faces['east'] = {'texture': '#' + ends}
    return {'name': name, 'from': list(a), 'to': list(b), 'faces': faces}


def write_model(name, textures, elements):
    d = f'{ROOT}/models/block'
    os.makedirs(d, exist_ok=True)
    with open(f'{d}/{name}.json', 'w') as f:
        json.dump({'parent': 'minecraft:block/block', 'textures': textures, 'elements': elements}, f, indent=2)
        f.write('\n')


def cask_model():
    els = [
        box('Body', (1, 3, 2), (15, 13, 14), 'wood', 'end'),
        box('Body wide', (1, 2, 3), (15, 14, 13), 'wood', 'end'),
        box('Body tall', (1, 1, 5), (15, 15, 11), 'wood', 'end'),
        box('Body flat', (1, 5, 1), (15, 11, 15), 'wood', 'end'),
        box('Hoop 1', (3, 1.5, 4.5), (4.5, 14.5, 11.5), 'hoop'),
        box('Hoop 1 b', (3, 4.5, 1.5), (4.5, 11.5, 14.5), 'hoop'),
        box('Hoop 1 c', (3, 2.5, 2.5), (4.5, 13.5, 13.5), 'hoop'),
        box('Hoop 2', (11.5, 1.5, 4.5), (13, 14.5, 11.5), 'hoop'),
        box('Hoop 2 b', (11.5, 4.5, 1.5), (13, 11.5, 14.5), 'hoop'),
        box('Hoop 2 c', (11.5, 2.5, 2.5), (13, 13.5, 13.5), 'hoop'),
        box('Cradle west', (2, 0, 4), (4, 1, 12), 'wood'),
        box('Cradle east', (12, 0, 4), (14, 1, 12), 'wood'),
        box('Tap', (7, 5, 0), (9, 7, 1.2), 'hoop'),
        box('Spout', (7.5, 3.5, 0), (8.5, 5, 1), 'hoop'),
    ]
    write_model('cask', {'wood': 'jaymoskitchen:block/cask_wood', 'end': 'jaymoskitchen:block/cask_end',
                         'hoop': 'jaymoskitchen:block/cask_hoop', 'particle': 'jaymoskitchen:block/cask_wood'}, els)


def still_model():
    els = [
        box('Foot', (3, 0, 3), (13, 3, 13), 'copper'),
        box('Belly', (2, 3, 2), (14, 9, 14), 'copper'),
        box('Shoulder', (3, 9, 3), (13, 11, 13), 'copper'),
        box('Cap', (4, 11, 4), (12, 13, 12), 'copper'),
        box('Dome', (6, 13, 6), (10, 15, 10), 'copper'),
        box('Knob', (7.5, 15, 7.5), (8.5, 16, 8.5), 'copper'),
        box('Pipe out', (7.5, 12, 9), (8.5, 13, 15.5), 'copper'),
        box('Pipe down', (7.5, 6, 14.5), (8.5, 13, 15.5), 'copper'),
        box('Nozzle', (7.5, 5, 14.5), (8.5, 6, 16), 'copper'),
        box('Door', (6, 1, 1.5), (10, 3, 3), 'copper'),
    ]
    write_model('still', {'copper': 'jaymoskitchen:block/still_copper', 'particle': 'jaymoskitchen:block/still_copper'}, els)


def main():
    save(cask_wood(), 'block', 'cask_wood')
    save(cask_end(), 'block', 'cask_end')
    save(cask_hoop(), 'block', 'cask_hoop')
    save(still_copper(), 'block', 'still_copper')
    for name in LIQUIDS:
        save(bottle(name, name == 'strong_spirits'), 'item', name)
    cask_model()
    still_model()


main()
