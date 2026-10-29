"""Generates the mantle textures (black, white and red twins) for the symbiote's cape: 64x32, the cape cube at the top-left."""
import random
from PIL import Image

OUT = '/home/user/jaymo-s-powerpack/.claude/worktrees/agent-a86176b3a757ffc96/src/main/resources/assets/jaymospowers/textures/entity/'
PALETTES = {
    '': ((14, 10, 20), (30, 20, 44), (70, 200, 190)),
    '_white': ((200, 200, 208), (236, 236, 242), (120, 150, 255)),
    '_red': ((60, 8, 12), (110, 16, 22), (255, 80, 60)),
}


def make(suffix, base, light, rim):
    rnd = random.Random(1717)
    img = Image.new('RGBA', (64, 32), (0, 0, 0, 0))
    px = img.load()
    # the cube: width 10, height 16, depth 1 -> u 0..22, v 0..17
    for x in range(22):
        for y in range(17):
            n = rnd.random()
            c = light if n > 0.82 else base
            px[x, y] = c + (255,)
    # veins running down
    for vx in (3, 7, 12, 16, 19):
        y = 1
        x = vx
        while y < 17:
            if 0 <= x < 22:
                px[x, y] = rim + (255,)
            y += 1
            x += rnd.choice((-1, 0, 0, 1))
    # a rim along the hem
    for x in range(22):
        px[x, 16] = rim + (255,)
    img.save(OUT + 'web_symbiote_mantle%s.png' % suffix)


for suffix, (base, light, rim) in PALETTES.items():
    make(suffix, base, light, rim)
