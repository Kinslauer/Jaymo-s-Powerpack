"""Jaymo's Kitchen wind and water power art (Better With Mods' ideas, MIT; our own pixels): the wooden shaft (still and
turning), the windmill's sailcloth, and the windmill and waterwheel item icons.

Run from the repository root with vanilla 26.3's textures folder (the one holding item/ and block/):
    python3 -I tools/vanilla_style/power.py <vanilla textures folder>
Writes into kitchen/src/main/resources/assets/jaymoskitchen/textures (item/, block/).
The shaft is vanilla's stripped spruce log with two dark seams added, so its turning (an 8-frame strip that slides
the side round) shows; the sailcloth is vanilla's white wool in a warm undyed canvas, framed in dark oak; the icons
are drawn on a 16x16 grid with vstyle.paint, lit from the top left like vanilla's items.
"""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from vstyle import paint, hx
from PIL import Image
import math
from canvas import Canvas
from vstyle import MAT

OUT = 'kitchen/src/main/resources/assets/jaymoskitchen/textures'
FRAMES = 8          # the turning shaft: frames, slid 2 pixels each
FRAME_TIME = 2      # ticks per frame
DARK_OAK = ['2a1a0b', '3c2814', '4c3319']   # dark oak planks' ramp, dark to light


def van(textures, path):
    return Image.open(os.path.join(textures, path + '.png')).convert('RGBA')


def shaft_side(textures):
    """vanilla's stripped spruce log, with two dark seams along it (at x 3 and 11) so a turn can be seen."""
    im = van(textures, 'block/stripped_spruce_log').copy(); px = im.load()
    for y in range(16):
        for x in (3, 11):
            r, g, b, a = px[x, y]
            px[x, y] = (int(r * .55), int(g * .55), int(b * .55), 255)
        r, g, b, a = px[4, y]
        px[4, y] = (int(r * .8), int(g * .8), int(b * .8), 255)
    return im


def turning(side):
    """FRAMES frames stacked downwards, the side slid sideways round the shaft a little more in each."""
    strip = Image.new('RGBA', (16, 16 * FRAMES))
    step = 16 // FRAMES
    for f in range(FRAMES):
        frame = Image.new('RGBA', (16, 16))
        s = f * step
        frame.paste(side.crop((s, 0, 16, 16)), (0, 0))
        if s:
            frame.paste(side.crop((0, 0, s, 16)), (16 - s, 0))
        strip.paste(frame, (0, 16 * f))
    return strip


def sailcloth(textures):
    """undyed canvas (vanilla's white wool, warmed and darkened a touch) in a dark oak frame, a batten across the middle."""
    wool = van(textures, 'block/white_wool'); wp = wool.load()
    im = Image.new('RGBA', (16, 16)); px = im.load()
    for y in range(16):
        for x in range(16):
            r, g, b, a = wp[x, y]
            px[x, y] = (min(255, int(r * .93)), min(255, int(g * .88)), min(255, int(b * .76)), 255)
    ramp = [hx(c) for c in DARK_OAK]
    for i in range(16):
        px[i, 0] = ramp[1] + (255,); px[i, 15] = ramp[0] + (255,)
        px[15, i] = ramp[0] + (255,)
        px[i, 8] = ramp[2] + (255,)
    return im


CLOTH = [hx(c) for c in ('5a5040', '8a7f6a', 'b9ad94', 'd6cbb2', 'e8dcc4', 'f4ead6')]


def windmill_icon():
    """four canvas sails on dark oak spars, crossed corner to corner on a hub; each sail trails its spar."""
    c = Canvas()
    for y in range(16):
        for x in range(16):
            px, py = x + .5 - 8, y + .5 - 8
            if px * px + py * py < 3.2:
                c.px(x, y, 'wood', 3 if px + py < 0 else 2)
                continue
            for a in (225, 315, 45, 135):
                d = (math.cos(math.radians(a)), math.sin(math.radians(a)))
                u = px * d[0] + py * d[1]; v = -px * d[1] + py * d[0]
                if abs(v) < .72 and 0 < u < 7.9:
                    c.px(x, y, 'wood', 4)
                elif .72 <= v < 4.4 and 1.4 < u < 7.7:
                    c.px(x, y, 'cloth', 4 if (x + y) % 5 else 3)
    return c.finish({'wood': MAT['wood'], 'cloth': CLOTH})


ICONS = {}

# a waterwheel: a spruce rim and spokes, paddles round the outside
ICONS['waterwheel'] = ("""
......aaaa......
....aa3443aa....
...a32a33a23a...
..a3a.a33a.a3a..
.a2a..a33a..a2a.
.a3aa.a33a.aa3a.
a43.aaa33aaa.34a
a433333bb333334a
a433333bb333334a
a43.aaa33aaa.34a
.a3aa.a33a.aa3a.
.a2a..a33a..a2a.
..a3a.a33a.a3a..
...a32a33a23a...
....aa3443aa....
......aaaa......""", None)


def main(textures):
    os.makedirs(f'{OUT}/block', exist_ok=True); os.makedirs(f'{OUT}/item', exist_ok=True)
    side = shaft_side(textures)
    side.save(f'{OUT}/block/shaft.png')
    turning(side).save(f'{OUT}/block/shaft_turning.png')
    with open(f'{OUT}/block/shaft_turning.png.mcmeta', 'w') as f:
        f.write('{\n  "animation": {\n    "frametime": %d\n  }\n}\n' % FRAME_TIME)
    van(textures, 'block/stripped_spruce_log_top').save(f'{OUT}/block/shaft_end.png')
    sailcloth(textures).save(f'{OUT}/block/sailcloth.png')
    ICONS_OUT = {'windmill': windmill_icon()}
    grid, _ = ICONS['waterwheel']
    ICONS_OUT['waterwheel'] = paint(grid, 'wood', {'a': '20180a', 'b': '9c7a32'})
    for name, im in ICONS_OUT.items():
        im.save(f'{OUT}/item/{name}.png')
    print('power art:', 4 + len(ICONS_OUT))


if __name__ == '__main__':
    if len(sys.argv) < 2:
        sys.exit('usage: power.py <vanilla 26.3 textures folder, holding item/ and block/>')
    main(sys.argv[1])
