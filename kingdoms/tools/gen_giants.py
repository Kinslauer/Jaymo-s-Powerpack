"""Skins for the Aether's Giants (kingdoms/src/main/java/com/jaymoskingdoms/remnants/AetherGiant.java, model
client/remnants/GiantModel.java, 128x128): our own, painted here in vanilla's manner (flat shade ramps, a little noise,
no gradients across a face). One per element: Highfield (pale sky-stone and moss, quartz shards), Magnetic (dark
lodestone with rust veins, violet ore shards), Arctic (packed ice and frost, deep blue ice shards), Irradiated (mossy
stone split by glowing Ambrosium cracks, lime shards).
Run from kingdoms/: python3 tools/gen_giants.py"""
import os
import random
from PIL import Image

OUT = 'src/main/resources/assets/jaymoskingdoms/textures/entity/giant/'
os.makedirs(OUT, exist_ok=True)

# per element: skin ramp (dark to light), accent (moss, veins, frost, cracks), shard ramp, eyes, how often the accent shows
ELEMENTS = {
    'highfield': dict(skin=[(150, 152, 140), (178, 180, 168), (204, 206, 194), (226, 228, 216)],
                      accent=[(70, 110, 46), (96, 140, 58), (122, 166, 72)], shard=[(150, 196, 204), (196, 230, 232), (238, 252, 250)],
                      eyes=(255, 214, 92), accent_odds=0.09),
    'magnetic': dict(skin=[(46, 44, 56), (62, 60, 74), (80, 78, 92), (98, 96, 110)],
                     accent=[(128, 66, 40), (160, 92, 54), (190, 120, 72)], shard=[(86, 52, 140), (126, 86, 186), (176, 140, 228)],
                     eyes=(196, 120, 255), accent_odds=0.03),
    'arctic': dict(skin=[(122, 156, 196), (148, 182, 218), (174, 204, 234), (206, 226, 246)],
                   accent=[(232, 242, 252), (246, 250, 255), (255, 255, 255)], shard=[(40, 82, 168), (62, 120, 210), (120, 176, 240)],
                   eyes=(140, 236, 255), accent_odds=0.07),
    'irradiated': dict(skin=[(52, 62, 46), (70, 82, 60), (88, 100, 74), (108, 118, 90)],
                       accent=[(150, 210, 40), (190, 236, 70), (226, 255, 120)], shard=[(120, 170, 20), (176, 222, 48), (232, 255, 130)],
                       eyes=(214, 255, 90), accent_odds=0.02),
}

# the model's boxes: (u, v, w, h, d, what)
BOXES = [
    (0, 0, 20, 14, 12, 'skin'),      # chest
    (0, 26, 16, 6, 10, 'skin'),      # hips
    (64, 0, 8, 8, 8, 'head'),        # head
    (96, 0, 9, 2, 2, 'brow'),        # brow
    (0, 42, 6, 26, 8, 'skin'),       # arm
    (28, 42, 7, 6, 9, 'fist'),       # fist
    (0, 76, 8, 5, 9, 'shoulder'),    # shoulder stone
    (60, 42, 7, 14, 7, 'skin'),      # leg
    (96, 16, 4, 10, 4, 'shard'),     # back shards
    (112, 16, 2, 5, 2, 'shard'),     # crown shards
]


def faces(u, v, w, h, d):
    """Each face of a box's UV layout: name -> (x, y, width, height)."""
    return {'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d), 'east': (u, v + d, d, h), 'north': (u + d, v + d, w, h),
            'west': (u + d + w, v + d, d, h), 'south': (u + d + w + d, v + d, w, h)}


def pick(ramp, k):
    return ramp[max(0, min(len(ramp) - 1, k))] + (255,)


def paint(element, seed):
    e = ELEMENTS[element]
    rnd = random.Random(seed)
    img = Image.new('RGBA', (128, 128), (0, 0, 0, 0))
    px = img.load()
    for (u, v, w, h, d, what) in BOXES:
        for face, (fx, fy, fw, fh) in faces(u, v, w, h, d).items():
            # a crack or vein running down this face (Irradiated's glow, Magnetic's rust...)
            vein = rnd.randrange(fw) if fw > 2 else -1
            for y in range(fh):
                vein_x = vein + (rnd.choice((-1, 0, 0, 1)) if vein >= 0 else 0)
                for x in range(fw):
                    if what == 'shard':
                        # light at the top and the leading edge, dark at the root
                        k = 2 if y < fh // 3 else 1 if y < 2 * fh // 3 else 0
                        if x == 0 and k < 2:
                            k += 1
                        c = pick(e['shard'], k)
                    else:
                        base = 1 if face in ('north', 'south', 'east', 'west') else 2 if face == 'top' else 0
                        if what in ('fist', 'brow'):
                            base -= 1
                        if what == 'shoulder':
                            base += 1
                        k = base + rnd.choice((-1, 0, 0, 0, 1))
                        # weathering: lighter near the top of a face, darker near its foot
                        if y == 0 and face != 'top':
                            k += 1
                        if y == fh - 1 and face != 'bottom':
                            k -= 1
                        c = pick(e['skin'], k)
                        if what not in ('head',) and (rnd.random() < e['accent_odds'] or (x == vein_x and face in ('north', 'south') and what == 'skin')):
                            c = pick(e['accent'], rnd.randrange(len(e['accent'])))
                    px[fx + x, fy + y] = c
    # the face: on the head's north face (8x8 at 72,8): a dark brow shadow, glowing eyes, a grim mouth
    hx, hy = 64 + 8, 0 + 8
    dark = pick(e['skin'], -1)
    for x in range(8):
        px[hx + x, hy + 2] = dark
    for ex in (1, 2, 5, 6):
        px[hx + ex, hy + 3] = e['eyes'] + (255,)
    px[hx + 1, hy + 4] = dark
    px[hx + 6, hy + 4] = dark
    for x in range(2, 6):
        px[hx + x, hy + 6] = (24, 22, 26, 255)
    return img


for i, element in enumerate(ELEMENTS):
    paint(element, 9100 + i).save(OUT + element + '.png')
print('painted', ', '.join(ELEMENTS))
