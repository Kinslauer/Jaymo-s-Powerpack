"""Jaymo's Kingdoms parcel (0.7.256): a merchant order wrapped in brown paper, tied with twine and sealed, as a courier
brings it. Hand-placed pixels on vanilla's ramps: the paper is the bundle's tan, the twine the string's off-white, the
seal the bounty notices' red. Lit from the top left like vanilla's items: the top face lightest, the front, the side darkest.

Usage: python -I tools/vanilla_style/parcel.py"""
import os
from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'kingdoms', 'src', 'main', 'resources', 'assets', 'jaymoskingdoms')

def hx(s):
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4)) + (255,)

PAL = {
    'O': hx('3b2414'),  # outline
    'T': hx('d9b178'),  # top face
    't': hx('c99d63'),  # top face, shade
    'F': hx('b98a51'),  # front face
    'f': hx('a87a45'),  # front face, shade
    'S': hx('8a6034'),  # side face
    's': hx('754f2a'),  # side face, shade
    'W': hx('ece4d0'),  # twine, lit
    'w': hx('c9bfa6'),  # twine, shade
    'R': hx('962822'),  # seal
    'r': hx('6e1c18'),  # seal, shade
}

ROWS = [
    '................',
    '................',
    '.....OOOOOOOOO..',
    '....OTTTTWTTTtO.',
    '...OWWWWRRWWWwsO',
    '..OTTTTTrRTTtssO',
    '.OTtTTTTTwTttssO',
    '.OFFFFFFFwFFfssO',
    '.OFFFFFFFwFFfsO.',
    '.OFFFFFFFwFFfsO.',
    '.OWWWWWWWwWWwwO.',
    '.OFFFFFFFwFfssO.',
    '.OfFFFFFFwffsO..',
    '.OffffffffwfO...',
    '..OOOOOOOOOO....',
    '................',
]

def main():
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(ROWS):
        assert len(row) == 16, (y, len(row))
        for x, ch in enumerate(row):
            if ch != '.':
                im.putpixel((x, y), PAL[ch])
    p = os.path.join(ROOT, 'textures', 'item', 'parcel.png')
    im.save(p)
    print('wrote', p)

if __name__ == '__main__':
    main()
