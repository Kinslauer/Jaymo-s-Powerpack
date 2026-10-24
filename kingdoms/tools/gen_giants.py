"""Skins for the Aether's Giants (kingdoms/src/main/java/com/jaymoskingdoms/remnants/AetherGiant.java, model
client/remnants/GiantModel.java, 256x128): our own, painted here in vanilla's manner (flat shade ramps, a little noise,
no gradients across a face). (0.7.181) Laid out for Andy's titan build (drawn with Gemini). One per element: Highfield (pale sky-stone and moss, quartz shards), Magnetic (dark
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
    (0, 0, 28, 30, 16, 'skin'),      # torso
    (88, 28, 26, 24, 4, 'shoulder'), # back plating
    (88, 0, 14, 14, 14, 'head'),     # skull
    (144, 0, 16, 4, 6, 'brow'),      # brow crest
    (144, 10, 12, 6, 10, 'fist'),    # jaw
    (0, 46, 10, 32, 10, 'skin'),     # arm
    (40, 46, 10, 22, 10, 'skin'),    # leg
]
# where each face is painted: the skull's front (14x14) and the jaw's front (12x6)
HEAD_FACE = (88 + 14, 0 + 14)
JAW_FACE = (144 + 10, 10 + 10)


def faces(u, v, w, h, d):
    """Each face of a box's UV layout: name -> (x, y, width, height)."""
    return {'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d), 'east': (u, v + d, d, h), 'north': (u + d, v + d, w, h),
            'west': (u + d + w, v + d, d, h), 'south': (u + d + w + d, v + d, w, h)}


def pick(ramp, k):
    return ramp[max(0, min(len(ramp) - 1, k))] + (255,)


def paint(element, seed):
    e = ELEMENTS[element]
    rnd = random.Random(seed)
    img = Image.new('RGBA', (256, 128), (0, 0, 0, 0))
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
    # the face, on the skull's front: a dark brow shadow over glowing eyes, deep cheeks; the mouth on the jaw's front (only its bottom 2 rows show below the skull)
    hx, hy = HEAD_FACE
    dark = pick(e['skin'], -1)
    for x in range(1, 13):
        px[hx + x, hy + 5] = dark
    for ex in (2, 3, 4, 9, 10, 11):
        px[hx + ex, hy + 6] = e['eyes'] + (255,)
    for ex in (2, 4, 9, 11):
        px[hx + ex, hy + 7] = dark
    jx, jy = JAW_FACE
    for x in range(2, 10):
        px[jx + x, jy + 4] = (24, 22, 26, 255)
    return img


for i, element in enumerate(ELEMENTS):
    paint(element, 9100 + i).save(OUT + element + '.png')
print('painted', ', '.join(ELEMENTS))
