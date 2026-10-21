"""Jaymo's Kitchen shaft-driven machines' art (Better With Mods' turntable and saw, MIT; our own pixels): the potter's
wheel's stone wheel top, and the bloody chopper's banded body, its slotted front and its saw blade (still and turning).

Run from the repository root with vanilla 26.3's textures folder (the one holding item/ and block/):
    python3 -I tools/vanilla_style/power_machines.py <vanilla textures folder>
Writes into kitchen/src/main/resources/assets/jaymoskitchen/textures/block.
The wheel is vanilla's smooth stone with a turned rim, a groove and one dark notch (so its turning shows); its stand is
drawn with vanilla's spruce planks and stripped spruce log as they are (the models point at them). The chopper is
vanilla's spruce planks with two iron bands; its blade is vanilla's iron ramp with teeth, and the turning blade is a
4-frame strip with the teeth stepped along.
"""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from vstyle import MAT
from PIL import Image

OUT = 'kitchen/src/main/resources/assets/jaymoskitchen/textures/block'
BLADE_FRAMES = 4    # the turning blade: frames, teeth stepped a pixel each
FRAME_TIME = 1      # ticks per frame
IRON = MAT['iron']
STONE = MAT['stone']


def van(textures, path):
    return Image.open(os.path.join(textures, path + '.png')).convert('RGBA')


def scaled(c, f):
    r, g, b, a = c
    return (min(255, int(r * f)), min(255, int(g * f)), min(255, int(b * f)), 255)


def wheel_top(textures):
    """smooth stone, a lighter turned rim, a dark groove two in, and one dark notch from the hub out (the turn shows)."""
    im = van(textures, 'block/smooth_stone').copy(); px = im.load()
    for y in range(16):
        for x in range(16):
            dx, dy = x + .5 - 8, y + .5 - 8
            d = (dx * dx + dy * dy) ** .5
            if 6.6 <= d < 7.6:
                px[x, y] = scaled(px[x, y], 1.12 if dx + dy < 0 else .82)
            elif 4.2 <= d < 5.0:
                px[x, y] = STONE[2] + (255,)
            elif d < 1.5:
                px[x, y] = STONE[3] + (255,)
    for x in range(9, 15):
        px[x, 7] = STONE[1] + (255,)
        px[x, 8] = STONE[2] + (255,)
    return im


def band(px, y, light):
    """an iron band across a plank face at row y (lit side above, a rivet every 5)."""
    for x in range(16):
        px[x, y] = IRON[4 if light else 2] + (255,)
        px[x, y + 1] = IRON[3 if light else 1] + (255,)
        if x % 5 == 2:
            px[x, y] = IRON[6] + (255,)


def chopper_side(textures):
    im = van(textures, 'block/spruce_planks').copy(); px = im.load()
    band(px, 1, True); band(px, 13, False)
    return im


def chopper_front(textures):
    """the side, with the dark slot the blade turns in."""
    im = chopper_side(textures); px = im.load()
    for y in range(3, 13):
        px[6, y] = scaled(px[6, y], .6)
        px[7, y] = (24, 18, 10, 255); px[8, y] = (24, 18, 10, 255)
        px[9, y] = scaled(px[9, y], .75)
    return im


def blade(shift):
    """iron, lit from the top; teeth (two bright, two dark) down both long edges, stepped by shift."""
    im = Image.new('RGBA', (16, 16)); px = im.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = IRON[5 if y < 2 else 4 if y < 8 else 3] + (255,)
        for x in (0, 15):
            tooth = (y + shift) % 4 < 2
            px[x, y] = (IRON[6] if tooth else IRON[1]) + (255,)
        px[1, y] = IRON[2] + (255,); px[14, y] = IRON[2] + (255,)
    # a little old blood in the teeth
    for (x, y) in ((0, 5), (1, 6), (15, 10), (0, 12), (14, 3)):
        px[x, y] = (110, 18, 14, 255)
    return im


def main(textures):
    os.makedirs(OUT, exist_ok=True)
    wheel_top(textures).save(f'{OUT}/potters_wheel_top.png')
    chopper_side(textures).save(f'{OUT}/bloody_chopper_side.png')
    chopper_front(textures).save(f'{OUT}/bloody_chopper_front.png')
    blade(0).save(f'{OUT}/bloody_chopper_blade.png')
    strip = Image.new('RGBA', (16, 16 * BLADE_FRAMES))
    for f in range(BLADE_FRAMES):
        strip.paste(blade(f), (0, 16 * f))
    strip.save(f'{OUT}/bloody_chopper_blade_turning.png')
    with open(f'{OUT}/bloody_chopper_blade_turning.png.mcmeta', 'w') as f:
        f.write('{\n  "animation": {\n    "frametime": %d\n  }\n}\n' % FRAME_TIME)
    print('power machine art: 5')


if __name__ == '__main__':
    if len(sys.argv) < 2:
        sys.exit('usage: power_machines.py <vanilla 26.3 textures folder, holding item/ and block/>')
    main(sys.argv[1])
