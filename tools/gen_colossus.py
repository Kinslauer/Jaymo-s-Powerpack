"""The patrons' colossi's skin (Kingdoms, ColossusRenderer): one 64x64 pale, faceless skin for the humanoid layout and every
patron's extra parts below row 32 (ChampionModel's), drawn in code, our own. Pale as cloud, a little lighter on top and
darker underneath, with a soft grain; no face, no clothes: the renderer leans it to each patron's colour.
Andy's reference was the blank figure in his "Commandment breaker" clip. Run from the repo root: python -I tools/gen_colossus.py"""
import random
from PIL import Image

OUT = 'kingdoms/src/main/resources/assets/jaymoskingdoms/textures/entity/patron_colossus.png'
BASE = (236, 238, 242)


def main():
    rnd = random.Random(26)
    img = Image.new('RGBA', (64, 64))
    for y in range(64):
        for x in range(64):
            # within each 16-pixel band, the top a touch lighter and the bottom a touch darker
            k = 1.03 - 0.07 * ((y % 16) / 15.0) + rnd.uniform(-0.025, 0.025)
            img.putpixel((x, y), tuple(max(0, min(255, int(c * k))) for c in BASE) + (255,))
    img.save(OUT)
    print('wrote', OUT)


if __name__ == '__main__':
    main()
