"""Jaymo's Kingdoms bounty board (0.7.168): a 3-wide, 2-tall board with one notice spot per block. Paints the board
face (vanilla's spruce planks, stained darker), three wanted notices, two town notices, the notice and board item icons, and writes the
block models, the multipart blockstate and the item models.

Usage: python -I tools/vanilla_style/bounty_board.py <vanilla 26.3 textures folder (holds block/ and item/)>"""
import json, os, sys
from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'kingdoms', 'src', 'main', 'resources', 'assets', 'jaymoskingdoms')
VAN = sys.argv[1] if len(sys.argv) > 1 else None
W, H = 3, 2

def hx(s):
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4)) + (255,)

# the poster colours of the old one-block board: red band, nail, parchment ramp, ink
PAL = {'R': hx('962822'), 'r': hx('6e1c18'), 'N': hx('c8c8cd'), 'L': hx('e4d6b0'), 'P': hx('dbcda7'), 'S': hx('cfc19b'),
       'D': hx('b3a47e'), 'I': hx('3c3028'), 'M': hx('786854'), 'O': hx('8c7c5a')}

NOTICES = [
    # a crook's face
    ['RRRRNRRRR',
     'LLPPPPPPS',
     'LPPMMMPPS',
     'LPMIMIMPS',
     'LPPMMMPPS',
     'LPPPMPPPS',
     'LPMMMMMPS',
     'LPPPPPPPS',
     'LMIMIMMPS',
     'SSSSSSSSD'],
    # a masked bandit, red cloth over the mouth
    ['RRRRNRRRR',
     'LLPPPPPPS',
     'LPPMMMPPS',
     'LPIIIIIPS',
     'LPPMMMPPS',
     'LPRRRRRPS',
     'LPMrrrMPS',
     'LPPPPPPPS',
     'LPMIMMIPS',
     'SSSSSSSSD'],
    # written out, under a red seal
    ['RRRRNRRRR',
     'LLPPPPPPS',
     'LPIMIMIPS',
     'LPPPPPPPS',
     'LMIMMIMPS',
     'LPPPPPPPS',
     'LMMIMIMPS',
     'LPPPPPPPS',
     'LPPRrRPPS',
     'SSSSSSSSD'],
]
NW, NH = 9, 10

# (town notices: errands and news) vanilla's paper ramp, grey ink, a brass pin, a green seal
TPAL = {'W': hx('fcfcf2'), 'w': hx('e9eaeb'), 'g': hx('d6d6d6'), 'G': hx('c1c1c1'), 'O': hx('9a9aa2'), 'K': hx('4a4a5a'), 'k': hx('8a8a96'),
        'B': hx('b87333'), 'V': hx('4a8a3a')}
TOWN = [
    # an errand, a sheep drawn on it
    ['WWWBWWWg',
     'WwwwwwwG',
     'WKkKKkwG',
     'WwwwwwwG',
     'WwwkkkwG',
     'WwkkkKwG',
     'WwwKwKwG',
     'WKkKkKwG',
     'gGGGGGGG'],
    # news, under a green seal
    ['WWWBWWWg',
     'WKKkKkwG',
     'WwwwwwwG',
     'WkKkKKwG',
     'WwwwwwwG',
     'WKkkKkwG',
     'WwwwwwwG',
     'WwwwVVwG',
     'gGGGGGGG'],
]
TW, TH = 8, 9

def save(im, *path):
    p = os.path.join(ROOT, 'textures', *path)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    im.save(p)

def notice_textures():
    for i, rows in enumerate(NOTICES):
        im = Image.new('RGBA', (16, 16), PAL['P'])
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                im.putpixel((x, y), PAL[ch])
        save(im, 'block', f'bounty_notice_{i}.png')
    for i, rows in enumerate(TOWN):
        im = Image.new('RGBA', (16, 16), TPAL['w'])
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                im.putpixel((x, y), TPAL[ch])
        save(im, 'block', f'town_notice_{i}.png')

def panel_texture():
    """vanilla's spruce planks, stained dark so the notices stand out, with old nail holes"""
    src = Image.open(os.path.join(VAN, 'block', 'spruce_planks.png')).convert('RGBA')
    im = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            r, g, b, a = src.getpixel((x, y))
            im.putpixel((x, y), (int(r * 0.6), int(g * 0.58), int(b * 0.55), 255))
    for x, y in [(2, 2), (9, 6), (13, 10), (5, 13)]:
        im.putpixel((x, y), hx('1e1208'))
    save(im, 'block', 'bounty_board_panel.png')

def item_notice():
    """the notice in hand: the crook's face on a slightly larger sheet, outlined in its darkest parchment"""
    rows = ['..DDDDDDDDD...',
            '.DRRRRNRRRRD..',
            '.DrrrrrrrrrD..',
            '.DLLPPPPPPSD..',
            '.DLPPMMMPPSD..',
            '.DLPMIMIMPSD..',
            '.DLPPMMMPPSD..',
            '.DLPPPMPPPSD..',
            '.DLPMMMMMPSD..',
            '.DLPPPPPPPSD..',
            '.DLMIMIMMPSD..',
            '.DLPPPPPPSSD..',
            '.DSSSSSSSOOD..',
            '..DDDDDDDDD...']
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != '.':
                im.putpixel((x + 1, y + 1), PAL[ch])
    save(im, 'item', 'bounty_notice.png')

def item_town_notice():
    """a town notice in hand: a sheet of vanilla's paper with lines of writing, a brass pin and a green seal"""
    rows = ['.OOOOOOOOOO.',
            'OWWWWBWWWWgO',
            'OWwwwwwwwwGO',
            'OWKKkKKkwwGO',
            'OWwwwwwwwwGO',
            'OWkKKkKKkwGO',
            'OWwwwwwwwwGO',
            'OWKkKKkKwwGO',
            'OWwwwwwwwwGO',
            'OWkKKkwwwwGO',
            'OWwwwwwVVwGO',
            'OgGGGGGGGGGO',
            '.OOOOOOOOOO.']
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != '.':
                im.putpixel((x + 2, y + 1), TPAL[ch])
    save(im, 'item', 'town_notice.png')

def item_board():
    """the board in hand: roof, posts of spruce log, the stained face with three notices"""
    log = Image.open(os.path.join(VAN, 'block', 'spruce_log.png')).convert('RGBA')
    planks = Image.open(os.path.join(VAN, 'block', 'spruce_planks.png')).convert('RGBA')
    panel = Image.open(os.path.join(ROOT, 'textures', 'block', 'bounty_board_panel.png')).convert('RGBA')
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for x in range(16):
        im.putpixel((x, 1), planks.getpixel((x, 2)))
        im.putpixel((x, 2), planks.getpixel((x, 5)))
    for y in range(3, 11):
        for x in range(2, 14):
            im.putpixel((x, y), panel.getpixel((x, y)))
    for x in range(2, 14):
        im.putpixel((x, 11), planks.getpixel((x, 9)))
    for y in range(3, 16):
        for x in (1, 2, 13, 14):
            im.putpixel((x, y), log.getpixel((x, y)))
    for ox, oy in [(3, 4), (7, 3), (10, 5)]:
        for dy in range(4):
            for dx in range(3):
                c = 'R' if dy == 0 else ('S' if dx == 2 or dy == 3 else 'L' if dx == 0 else 'P')
                if dy == 2 and dx == 1:
                    c = 'M'
                im.putpixel((ox + dx, oy + dy), PAL[c])
    save(im, 'item', 'bounty_board.png')

# ---- models -------------------------------------------------------------------------------------------------------------

def box(frm, to, tex, front=None, uvfront=None):
    faces = {}
    for f in ('north', 'south', 'east', 'west', 'up', 'down'):
        x0, y0, z0 = frm; x1, y1, z1 = to
        if f in ('north', 'south'):
            uv = [16 - x1, 16 - y1, 16 - x0, 16 - y0] if f == 'north' else [x0, 16 - y1, x1, 16 - y0]
        elif f in ('east', 'west'):
            uv = [z0, 16 - y1, z1, 16 - y0]
        else:
            uv = [x0, z0, x1, z1]
        faces[f] = {'texture': front if (f == 'north' and front) else tex, 'uv': uvfront if (f == 'north' and uvfront) else uv}
    return {'from': frm, 'to': to, 'faces': faces}

def part_model(c, r):
    els = []
    if r == 0:
        els.append(box([0, 4, 7], [16, 16, 9], '#planks', '#panel', [0, 0, 16, 12]))
        els.append(box([0, 3, 6.5], [16, 5, 9.5], '#planks'))
    else:
        els.append(box([0, 0, 7], [16, 11, 9], '#planks', '#panel', [0, 5, 16, 16]))
        els.append(box([0, 11, 6.5], [16, 12, 9.5], '#planks'))
        els.append(box([0, 12, 5], [16, 14, 11], '#planks'))
        els.append(box([0, 14, 6], [16, 15, 10], '#planks'))
    # (column 0 is on the viewer's left, which is +x on a board facing north)
    if c == 0:
        els.append(box([13, 0, 6.5], [16, 16 if r == 0 else 12, 9.5], '#post'))
    elif c == W - 1:
        els.append(box([0, 0, 6.5], [3, 16 if r == 0 else 12, 9.5], '#post'))
    return {'parent': 'minecraft:block/block', 'textures': {'particle': 'minecraft:block/spruce_planks', 'planks': 'minecraft:block/spruce_planks',
            'panel': 'jaymoskingdoms:block/bounty_board_panel', 'post': 'minecraft:block/spruce_log'}, 'elements': els}

NOTICE_X = {(0, 0): 3, (1, 0): 4, (2, 0): 5, (0, 1): 2, (1, 1): 3, (2, 1): 4}

def notice_model(c, r):
    x = NOTICE_X[(c, r)]
    y = 5 if r == 0 else 1
    el = {'from': [x, y, 6.75], 'to': [x + NW, y + NH, 7], 'faces': {'north': {'texture': '#notice', 'uv': [0, 0, NW, NH]}}}
    return {'textures': {'particle': f'jaymoskingdoms:block/bounty_notice_{(c + r) % 3}', 'notice': f'jaymoskingdoms:block/bounty_notice_{(c + r) % 3}'},
            'elements': [el]}

TOWN_X = {(0, 0): 4, (1, 0): 3, (2, 0): 6, (0, 1): 3, (1, 1): 5, (2, 1): 4}

def town_model(c, r):
    x = TOWN_X[(c, r)]
    y = 6 if r == 0 else 2
    tex = f'jaymoskingdoms:block/town_notice_{(c + r) % 2}'
    el = {'from': [x, y, 6.75], 'to': [x + TW, y + TH, 7], 'faces': {'north': {'texture': '#notice', 'uv': [0, 0, TW, TH]}}}
    return {'textures': {'particle': tex, 'notice': tex}, 'elements': [el]}

def write(obj, *path):
    p = os.path.join(ROOT, *path)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, 'w') as f:
        json.dump(obj, f, indent=2)
        f.write('\n')

def models():
    rot = {'north': 0, 'east': 90, 'south': 180, 'west': 270}
    multipart = []
    for c in range(W):
        for r in range(H):
            write(part_model(c, r), 'models', 'block', f'bounty_board_{c}_{r}.json')
            write(notice_model(c, r), 'models', 'block', f'bounty_board_notice_{c}_{r}.json')
            write(town_model(c, r), 'models', 'block', f'bounty_board_town_{c}_{r}.json')
            for facing, y in rot.items():
                when = {'facing': facing, 'column': str(c), 'row': str(r)}
                m = {'model': f'jaymoskingdoms:block/bounty_board_{c}_{r}'}
                n = {'model': f'jaymoskingdoms:block/bounty_board_notice_{c}_{r}'}
                t = {'model': f'jaymoskingdoms:block/bounty_board_town_{c}_{r}'}
                if y:
                    m['y'] = y; n['y'] = y; t['y'] = y
                multipart.append({'when': when, 'apply': m})
                multipart.append({'when': dict(when, notice='1'), 'apply': n})
                multipart.append({'when': dict(when, notice='2'), 'apply': t})
    write({'multipart': multipart}, 'blockstates', 'bounty_board.json')
    for name in ('bounty_board', 'bounty_notice', 'town_notice'):
        write({'parent': 'minecraft:item/generated', 'textures': {'layer0': f'jaymoskingdoms:item/{name}'}}, 'models', 'item', f'{name}.json')
        write({'model': {'type': 'minecraft:model', 'model': f'jaymoskingdoms:item/{name}'}}, 'items', f'{name}.json')

if __name__ == '__main__':
    notice_textures()
    if VAN:
        panel_texture()
        item_board()
    item_notice()
    item_town_notice()
    models()
