"""Jaymo's Kitchen manure, fertilizer and fertile farmland art (from Better With Mods' ideas, MIT; our own pixels).

Run from the repository root with vanilla 26.3's textures folder (the one holding item/ and block/):
    python3 -I tools/vanilla_style/farm.py <vanilla textures folder>
Writes into kitchen/src/main/resources/assets/jaymoskitchen/textures (item/, block/).
Manure is drawn on a 16x16 grid with vstyle.paint (letters listed per icon), lit from the top left; fertilizer is
vanilla's bone meal in earthy browns; fertile farmland is vanilla's farmland (dry and moist) on a darker, richer ramp,
so its furrows and shading stay vanilla's own.
"""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from vstyle import paint
from tanning import recolour
from PIL import Image

OUT = 'kitchen/src/main/resources/assets/jaymoskitchen/textures'

# a soft brown heap, coiled a little, a straw or two in it
MANURE = ("""
................
................
................
................
................
.......AAA......
......ADDCA.....
.....ADEDCBA....
....AACCCCBAA...
...ADDDCDCCBBA..
..ADEDCCSCCCBA..
..ACDDCCCSCBBA..
..ABCCCCBBBBBA..
...AABBBBBBAA...
.....AAAAAA.....
................
""", 'iron', {'A': '2b1d0f', 'B': '45301a', 'C': '5c4223', 'D': '75562f', 'E': '8d6b3e', 'S': 'b39a4a'})


def richer(src, r=0.68, g=0.60, b=0.54):
    """vanilla's pixels, darker and warmer, as well-manured soil."""
    im = Image.open(src).convert('RGBA'); px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            c = px[x, y]
            px[x, y] = (round(c[0] * r), round(c[1] * g), round(c[2] * b), c[3])
    return im


def main(vanilla):
    os.makedirs(f'{OUT}/item', exist_ok=True)
    os.makedirs(f'{OUT}/block', exist_ok=True)
    grid, mat, extra = MANURE
    paint(grid, mat, extra).save(f'{OUT}/item/manure.png')
    # fertilizer: vanilla's bone meal, a crumbly earthy brown-grey with pale flecks
    recolour(f'{vanilla}/item/bone_meal.png', ('3a2e1e', '5a4a32', '7a6a4c', '988a6a', 'b8ad90', 'd8d0b8')).save(f'{OUT}/item/fertilizer.png')
    richer(f'{vanilla}/block/farmland.png').save(f'{OUT}/block/fertile_farmland.png')
    richer(f'{vanilla}/block/farmland_moist.png').save(f'{OUT}/block/fertile_farmland_moist.png')


if __name__ == '__main__':
    if len(sys.argv) < 2:
        sys.exit('usage: farm.py <vanilla 26.3 textures folder, holding item/ and block/>')
    main(sys.argv[1])
