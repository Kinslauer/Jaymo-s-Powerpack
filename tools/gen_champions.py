"""(0.7.518) The patron champions' art: eight 64x64 skins (the humanoid layout, then each patron's extra parts from row 32,
matching ChampionModel) and eight 16x16 spawn egg icons, with their models and item files. Kingdoms. Our own, drawn in code.
Run from the repo root: python -I tools/gen_champions.py"""
import json
import os
import random
from PIL import Image

OUT = 'kingdoms/src/main/resources/assets/jaymoskingdoms/'

# per patron (entity id): skin, robe, trim, hair, eye, leg colours; the patron's own colour for the egg
P = {
    'hearth_warden': dict(skin=(206, 150, 120), robe=(196, 128, 40), trim=(120, 60, 24), hair=(150, 150, 154), eye=(60, 40, 30), leg=(110, 70, 36), egg=(224, 160, 80), hood=False),
    'forge_thane': dict(skin=(190, 140, 110), robe=(120, 124, 132), trim=(60, 62, 70), hair=(84, 52, 30), eye=(40, 30, 30), leg=(70, 56, 44), egg=(176, 180, 188), hood=False),
    'tide_bride': dict(skin=(190, 214, 206), robe=(44, 130, 120), trim=(30, 84, 96), hair=(30, 70, 80), eye=(20, 60, 90), leg=(36, 100, 100), egg=(58, 143, 183), hood=False),
    'antlered_hunter': dict(skin=(176, 130, 100), robe=(110, 84, 50), trim=(70, 52, 30), hair=(54, 40, 28), eye=(30, 30, 20), leg=(80, 64, 40), egg=(94, 158, 58), hood=False),
    'grave_lamp': dict(skin=(120, 124, 130), robe=(92, 96, 104), trim=(56, 58, 66), hair=(60, 62, 70), eye=(154, 168, 255), leg=(70, 72, 80), egg=(154, 168, 255), hood=True),
    'sun_marshal': dict(skin=(214, 170, 140), robe=(232, 228, 216), trim=(255, 194, 26), hair=(200, 160, 40), eye=(40, 60, 120), leg=(200, 196, 184), egg=(255, 194, 26), hood=False),
    'cinder_shade': dict(skin=(40, 34, 32), robe=(34, 30, 30), trim=(138, 90, 74), hair=(26, 22, 22), eye=(255, 122, 42), leg=(28, 24, 24), egg=(138, 90, 74), hood=True),
    'inquisitor': dict(skin=(214, 180, 160), robe=(232, 232, 234), trim=(120, 122, 128), hair=(200, 200, 204), eye=(70, 70, 90), leg=(170, 170, 176), egg=(242, 242, 242), hood=False),
}

# extra parts: (u, v, w, h, d, colour key) in ChampionModel
EXTRA = {
    'hearth_warden': [(0, 32, 8, 10, 1, (236, 224, 190)), (20, 32, 4, 3, 3, (150, 150, 154))],
    'forge_thane': [(0, 32, 6, 6, 2, (84, 52, 30)), (16, 32, 5, 3, 5, (150, 154, 162))],
    'tide_bride': [(0, 32, 9, 9, 1, (150, 210, 190)), (24, 32, 2, 12, 1, (60, 110, 60))],
    'antlered_hunter': [(0, 32, 2, 6, 2, (214, 204, 176)), (8, 32, 2, 2, 2, (214, 204, 176)), (0, 44, 10, 2, 6, (120, 96, 64))],
    'grave_lamp': [(0, 32, 9, 9, 9, (84, 88, 96)), (40, 32, 3, 4, 3, (110, 120, 200))],
    'sun_marshal': [(0, 32, 9, 1, 9, (255, 194, 26)), (40, 32, 1, 3, 1, (255, 214, 80)), (0, 44, 10, 16, 1, (170, 30, 30))],
    'cinder_shade': [(0, 32, 9, 9, 9, (30, 26, 26)), (40, 32, 2, 8, 1, (90, 70, 62))],
    'inquisitor': [(0, 32, 6, 6, 4, (240, 240, 242)), (24, 32, 2, 14, 1, (130, 132, 138))],
}


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c)


def box(img, rnd, u, v, w, h, d, colour, front=None, noise=0.08):
    """Fills the six faces of a box's unfolded UV with a colour and noise; the front face may be given as a function."""
    faces = {'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d), 'right': (u, v + d, d, h), 'front': (u + d, v + d, w, h),
             'left': (u + d + w, v + d, d, h), 'back': (u + 2 * d + w, v + d, w, h)}
    for name, (x0, y0, fw, fh) in faces.items():
        k = 1.0 if name in ('front', 'top') else 0.86 if name != 'bottom' else 0.7
        for x in range(x0, x0 + fw):
            for y in range(y0, y0 + fh):
                if x < 0 or y < 0 or x >= 64 or y >= 64:
                    continue
                c = colour
                if front is not None and name == 'front':
                    c = front(x - x0, y - y0) or colour
                n = 1.0 + (rnd.random() - 0.5) * 2 * noise
                img.putpixel((x, y), shade(c, k * n) + (255,))


def skin(name):
    s = P[name]
    rnd = random.Random(name)
    img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))

    def face(x, y):
        # hair on top and sides, eyes, a mouth
        if s['hood']:
            if 2 <= x <= 5 and 3 <= y <= 5:
                return s['eye'] if y == 4 and x in (2, 5) else shade(s['skin'], 0.7)
            return s['robe'] if y < 2 or x < 2 or x > 5 else shade(s['skin'], 0.8)
        if y < 2:
            return s['hair']
        if y == 3 and x in (1, 2, 5, 6):
            return s['eye']
        if y == 6 and 3 <= x <= 4:
            return shade(s['skin'], 0.7)
        if name == 'antlered_hunter' and y == 4 and x in (0, 1, 6, 7):
            return (70, 140, 60)
        return None

    box(img, rnd, 0, 0, 8, 8, 8, s['skin'], face)
    box(img, rnd, 16, 16, 8, 12, 4, s['robe'], lambda x, y: s['trim'] if y in (10, 11) or (x in (3, 4) and name == 'inquisitor') else None)
    box(img, rnd, 40, 16, 4, 12, 4, s['robe'], lambda x, y: s['skin'] if y >= 10 else None)
    box(img, rnd, 32, 48, 4, 12, 4, s['robe'], lambda x, y: s['skin'] if y >= 10 else None)
    box(img, rnd, 0, 16, 4, 12, 4, s['leg'], lambda x, y: s['trim'] if y >= 10 else None)
    box(img, rnd, 16, 48, 4, 12, 4, s['leg'], lambda x, y: s['trim'] if y >= 10 else None)
    for (u, v, w, h, d, c) in EXTRA[name]:
        box(img, rnd, u, v, w, h, d, c)
    os.makedirs(OUT + 'textures/entity/champion', exist_ok=True)
    img.save(OUT + 'textures/entity/champion/%s.png' % name)


def egg(name):
    s = P[name]
    rnd = random.Random('egg' + name)
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for x in range(16):
        for y in range(16):
            dx, dy = (x - 7.5) / 5.2, (y - 8.0) / (6.8 if y < 8 else 7.6)
            if dx * dx + dy * dy <= 1.0:
                k = 1.0 - 0.25 * ((x - 5) ** 2 + (y - 5) ** 2) ** 0.5 / 8.0
                img.putpixel((x, y), shade(s['egg'], k) + (255,))
    for _ in range(14):
        x, y = rnd.randrange(16), rnd.randrange(16)
        if img.getpixel((x, y))[3]:
            img.putpixel((x, y), shade(s['egg'], 0.55) + (255,))
    os.makedirs(OUT + 'textures/item', exist_ok=True)
    img.save(OUT + 'textures/item/%s_spawn_egg.png' % name)
    os.makedirs(OUT + 'models/item', exist_ok=True)
    os.makedirs(OUT + 'items', exist_ok=True)
    with open(OUT + 'models/item/%s_spawn_egg.json' % name, 'w') as f:
        json.dump({'parent': 'minecraft:item/generated', 'textures': {'layer0': 'jaymoskingdoms:item/%s_spawn_egg' % name}}, f, indent='\t')
        f.write('\n')
    with open(OUT + 'items/%s_spawn_egg.json' % name, 'w') as f:
        json.dump({'model': {'type': 'minecraft:model', 'model': 'jaymoskingdoms:item/%s_spawn_egg' % name}}, f, indent='\t')
        f.write('\n')


for n in P:
    skin(n)
    egg(n)
print('champions done')
