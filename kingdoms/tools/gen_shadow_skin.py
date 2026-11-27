"""The shadow's skin (Andy, 2026-11-26: "shadows should be transparent, like our ghost texture"): the grave ghost's
see-through player skin (textures/entity/bb/grave_ghost.png, the same sheet as Powers' player ghost), its every pixel kept
where it is and as faint, a little less, but painted the cold near-black of a shadow instead of the ghost's pale blue. The
eyes stay pale. ShadowRenderer draws it on a player's shape, with legs or with the ghost's wispy tail. Run from kingdoms/:
python3 tools/gen_shadow_skin.py"""
import os
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
TEX = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'jaymoskingdoms', 'textures', 'entity')
GHOST = os.path.join(TEX, 'bb', 'grave_ghost.png')
OUT = os.path.join(TEX, 'creatures', 'shadow.png')
# the body's colour, the eyes' colour, and how much darker (more solid) than the ghost each pixel is: a near-black at the
# ghost's own faintness hardly shows by day, so it is thickened a little, still well short of solid
BODY = (16, 14, 24)
EYES = (214, 224, 240)
THICKER = 1.5
# the ghost's eyes are its only near-solid pixels
EYE_ALPHA = 200


def main():
    ghost = Image.open(GHOST).convert('RGBA')
    out = Image.new('RGBA', ghost.size, (0, 0, 0, 0))
    px, src = out.load(), ghost.load()
    for y in range(ghost.height):
        for x in range(ghost.width):
            a = src[x, y][3]
            if a == 0:
                continue
            if a >= EYE_ALPHA:
                px[x, y] = EYES + (a,)
            else:
                px[x, y] = BODY + (min(EYE_ALPHA - 1, round(a * THICKER)),)
    out.save(OUT)


if __name__ == '__main__':
    main()
