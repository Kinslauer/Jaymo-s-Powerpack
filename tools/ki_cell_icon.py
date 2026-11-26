"""Paints the Ki Cell's item icon (power/zfighter/ZFighterKiCell): a Tinkerer's brass-capped glass cell with ki
glowing inside, in the ki-drink cyan (ZFighterKiDrinkingBarrier.COLOR, 0x40E0F0). Hand-drawn 16x16 grid.

    python3 -I tools/ki_cell_icon.py            # writes src/main/resources/assets/jaymospowers/textures/item/ki_cell.png
    python3 -I tools/ki_cell_icon.py <file>     # writes it somewhere else (to preview)
"""
import os
import sys
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'jaymospowers',
                   'textures', 'item', 'ki_cell.png')

# Light from the top left; outlines are the darkest shade of what they wrap.
COLOURS = {
    'A': '3f2e0e', 'B': '7d5a17', 'C': 'b88a22', 'D': 'deb83c', 'E': 'f7e27a',   # brass
    'g': '1d4a55', 'h': '2f7f8f',                                                # glass rim
    'k': '137a8c', 'l': '22b0c4', 'm': '40e0f0', 'n': '9cf4fb', 'o': 'f0ffff',   # ki
}

GRID = '''
.....AAAAAA.....
....ABDEEDCA....
....ABCDDCBA....
.....AAAAAA.....
.....ghhhhg.....
....gnomllkg....
....gommllkg....
....gnmlllkg....
....gmmllkkg....
....gmlllkkg....
....gnmllkkg....
....gmllkkkg....
.....ghhhhg.....
.....AAAAAA.....
....ABDDDCBA....
.....AAAAAA.....
'''


def paint():
    rows = GRID.strip('\n').split('\n')
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), [len(r) for r in rows]
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            if c != '.':
                img.putpixel((x, y), tuple(int(COLOURS[c][i:i + 2], 16) for i in (0, 2, 4)) + (255,))
    return img


if __name__ == '__main__':
    out = sys.argv[1] if len(sys.argv) > 1 else OUT
    paint().save(out)
    print('wrote', out)
