"""Jaymo's Kitchen butcher's shop art (Andy's Butchery picks; the Butchery mod's ideas only, our own pixels).

Run from the repository root with vanilla 26.3's textures folder (the one holding item/ and block/):
    py -3 -I tools/vanilla_style/butcher_shop.py <vanilla textures folder>
Writes into kitchen/src/main/resources/assets/jaymoskitchen/textures:
- item/mince.png: Kitchen's minced beef (Farmer's Delight's art, MIT) in a paler, mixed-meat ramp.
- block/bone_barrel_top.png: vanilla's open barrel top with vanilla's bone, crossed, piled in it.
The mincer, the head mount and the butcher's display use vanilla's own block textures in their models.
"""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from vstyle import hx
from PIL import Image

OUT = 'kitchen/src/main/resources/assets/jaymoskitchen/textures'


def recolour(im, ramp):
    """each colour swapped by brightness rank for the same rank of a new ramp (dark to light)."""
    px = im.load()
    cols = sorted({px[x, y][:3] for x in range(im.width) for y in range(im.height) if px[x, y][3]}, key=lambda c: (sum(c), c))
    ramp = [hx(c) for c in ramp]
    to = {c: ramp[round(i * (len(ramp) - 1) / max(1, len(cols) - 1))] for i, c in enumerate(cols)}
    out = Image.new('RGBA', im.size); op = out.load()
    for y in range(im.height):
        for x in range(im.width):
            if px[x, y][3]:
                op[x, y] = to[px[x, y][:3]] + (px[x, y][3],)
    return out


def main(van):
    mince = recolour(Image.open(f'{OUT}/item/minced_beef.png').convert('RGBA'),
                     ['5a2422', '7d3430', 'a2483f', 'c0675a', 'd88a7a', 'e9ad9c', 'f4d2c4'])
    mince.save(f'{OUT}/item/mince.png')

    top = Image.open(f'{van}/block/barrel_top_open.png').convert('RGBA')
    bone = Image.open(f'{van}/item/bone.png').convert('RGBA')
    tp = top.load()
    # the bones lie inside the rim (vanilla's open top has a 2-pixel rim)
    for piece, dx, dy in ((bone, 0, 0), (bone.transpose(Image.FLIP_LEFT_RIGHT), 0, 1), (bone.rotate(90), 1, -1)):
        pp = piece.load()
        for y in range(16):
            for x in range(16):
                tx, ty = x + dx, y + dy
                if 2 <= tx <= 13 and 2 <= ty <= 13 and pp[x, y][3] > 0:
                    tp[tx, ty] = pp[x, y]
    top.save(f'{OUT}/block/bone_barrel_top.png')


if __name__ == '__main__':
    main(sys.argv[1])
