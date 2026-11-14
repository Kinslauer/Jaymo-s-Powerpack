"""The insides of a body blown apart or eaten (corpse/Gibs, client GibRenderer): one 64x32 sheet, a region per piece.
The soft pieces (organ, gut) are painted in greys, since the renderer tints them with the body's own blood colour;
bone and cloth are painted as they are. Regions match the texOffs in GibRenderer.build().
Run from kitchen/: python3 -I tools/gen_gore.py   (writes textures/entity/gore.png)"""
import os, random
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'jaymoskitchen', 'textures', 'entity', 'gore.png')
ORGAN = [(255, 255, 255), (214, 214, 214), (168, 168, 168), (120, 120, 120)]
GUT = [(255, 255, 255), (232, 232, 232), (190, 190, 190), (150, 150, 150)]
BONE = [(252, 251, 237), (230, 226, 207), (197, 192, 165), (160, 154, 128)]
CLOTH = [(139, 122, 96), (116, 100, 78), (92, 78, 60)]
BLOOD = [(125, 31, 32), (90, 20, 22)]


def fill(img, box, palette, weights, rng, spots=None, spot_chance=0.0, bands=0):
    x0, y0, x1, y1 = box
    for y in range(y0, y1):
        for x in range(x0, x1):
            if spots and rng.random() < spot_chance:
                colour = rng.choice(spots)
            elif bands and (x + y) % bands == 0:
                # a gut's coils: a darker line every few pixels
                colour = palette[-1]
            else:
                colour = rng.choices(palette, weights)[0]
            img.putpixel((x, y), colour + (255,))


def main():
    rng = random.Random(1486)
    img = Image.new('RGBA', (64, 32), (0, 0, 0, 0))
    fill(img, (0, 0, 16, 16), ORGAN, [1, 4, 5, 2], rng)
    fill(img, (16, 0, 32, 16), GUT, [3, 5, 3, 0], rng, bands=3)
    fill(img, (32, 0, 48, 16), BONE, [3, 5, 3, 1], rng, BLOOD, 0.12)
    fill(img, (48, 0, 64, 16), BONE, [3, 5, 3, 1], rng, BLOOD, 0.08)
    fill(img, (0, 16, 16, 32), BONE, [3, 5, 3, 1], rng, BLOOD, 0.06)
    fill(img, (16, 16, 40, 32), CLOTH, [3, 5, 3], rng, BLOOD, 0.14)
    img.save(OUT)


if __name__ == '__main__':
    main()
