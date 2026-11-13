"""The grave ghost's skin and the goblin scout's and ghost's spawn eggs.
The ghost (GraveGhost, drawn on the ghost Blockbench model's player-shaped body): the model's own flat pale blue, made a
little less thin so it reads at night, with two bright eyes and legs that thin away toward the feet, as the life story's
ki ghost is drawn (client/KiGhost). Run from kingdoms/: python3 tools/gen_scout_ghost.py"""
import os
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'jaymoskingdoms', 'textures')
BLUE, EYES = (138, 206, 255), (236, 248, 255)
# the base layer's and the outer layer's light (alpha), and how thin the feet end up (share of the leg's own light)
BASE, OUTER, FEET = 96, 40, 0.15
# box-UV boxes (u, v, w, h, d, outer?) as on a player's skin: head, hat, body, jacket, arms, sleeves
BOXES = [(0, 0, 8, 8, 8, False), (32, 0, 8, 8, 8, True), (16, 16, 8, 12, 4, False), (16, 32, 8, 12, 4, True),
         (40, 16, 4, 12, 4, False), (40, 32, 4, 12, 4, True), (32, 48, 4, 12, 4, False), (48, 48, 4, 12, 4, True)]
LEGS = [(0, 16, 4, 12, 4, False), (0, 32, 4, 12, 4, True), (16, 48, 4, 12, 4, False), (0, 48, 4, 12, 4, True)]


def box(px, u, v, w, h, d, outer, fade):
    light = OUTER if outer else BASE
    # top and bottom caps
    for x in range(u + d, u + d + w):
        for y in range(v, v + d):
            px[x, y] = BLUE + (light,)
    for x in range(u + d + w, u + d + 2 * w):
        for y in range(v, v + d):
            px[x, y] = BLUE + (int(light * (FEET if fade else 1.0)),)
    # the four sides, thinning down the leg
    for x in range(u, u + 2 * (w + d)):
        for row in range(h):
            share = 1.0 - (1.0 - FEET) * row / (h - 1) if fade else 1.0
            px[x, v + d + row] = BLUE + (int(light * share),)


def ghost():
    img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    px = img.load()
    for b in BOXES:
        box(px, *b, False)
    for b in LEGS:
        box(px, *b, True)
    # two eyes on the face (the head's front is at u 8-15, v 8-15)
    for x in (10, 13):
        for y in (11, 12):
            px[x, y] = EYES + (230,)
    img.save(os.path.join(ASSETS, 'entity', 'bb', 'grave_ghost.png'))


def egg(name, shell, spots):
    """A vanilla-shaped spawn egg: the shape of one we already ship, in this creature's colours."""
    src = Image.open(os.path.join(ASSETS, 'item', 'scalebane_spawn_egg.png')).convert('RGBA')
    tones = sorted({p[:3] for p in src.getdata() if p[3] > 0}, key=sum)
    out = Image.new('RGBA', src.size, (0, 0, 0, 0))
    for y in range(src.height):
        for x in range(src.width):
            r, g, b, a = src.getpixel((x, y))
            if a == 0:
                continue
            t = tones.index((r, g, b)) / max(1, len(tones) - 1)
            base = spots if (x * 7 + y * 13) % 11 < 2 and 3 < x < 12 and 3 < y < 13 else shell
            out.putpixel((x, y), tuple(int(c * (0.55 + 0.55 * t)) if c * (0.55 + 0.55 * t) < 255 else 255 for c in base) + (a,))
    out.save(os.path.join(ASSETS, 'item', name + '_spawn_egg.png'))


if __name__ == '__main__':
    ghost()
    egg('goblin_scout', (104, 140, 60), (74, 52, 36))
    egg('grave_ghost', (138, 206, 255), (236, 248, 255))
