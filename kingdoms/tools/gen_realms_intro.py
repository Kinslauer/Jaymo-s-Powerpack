"""Medieval patch 5 (Realms and the intro): our own textures.

- textures/item/tent.png: the tent item, a rolled wool A-frame on its poles.
- textures/entity/realm_cape.png: the realm cape's cloth (CapeLayer), near white so the realm's colours tint it, with
  soft vertical folds and a woven grain.

Run from the repository root: python3 kingdoms/tools/gen_realms_intro.py
"""
import math
import os
import random
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'jaymoskingdoms', 'textures')


def tent():
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = img.load()
    wool = [(236, 231, 218), (222, 215, 199), (205, 196, 178)]
    ridge = (112, 78, 48)
    pole = (92, 64, 38)
    dark = (70, 58, 48)
    # the A-frame: an isosceles triangle from (8, 2) down to (1..14, 13)
    for y in range(3, 14):
        half = (y - 2) * 6.5 / 11.0
        for x in range(16):
            dx = x + 0.5 - 8.0
            if abs(dx) <= half:
                shade = 0 if dx < -0.5 else (1 if dx < 2.5 else 2)
                px[x, y] = wool[shade] + (255,)
    # the doorway, a dark slit at the middle
    for y in range(8, 14):
        w = (y - 7) * 0.5
        for x in range(16):
            if abs(x + 0.5 - 8.0) <= w:
                px[x, y] = dark + (255,)
    # the outline and the ridge
    for y in range(3, 14):
        half = (y - 2) * 6.5 / 11.0
        lx, rx = int(round(8.0 - half - 0.5)), int(round(8.0 + half - 0.5))
        px[max(0, lx), y] = ridge + (255,)
        px[min(15, rx), y] = ridge + (255,)
    for x in range(1, 15):
        px[x, 13] = ridge + (255,)
    # the poles through the peak
    px[7, 1] = pole + (255,)
    px[8, 1] = pole + (255,)
    px[7, 2] = ridge + (255,)
    px[8, 2] = ridge + (255,)
    px[0, 14] = pole + (255,)
    px[15, 14] = pole + (255,)
    for x in range(1, 15):
        px[x, 14] = (60, 90, 40, 255) if x % 3 else (74, 108, 50, 255)
    img.save(os.path.join(ROOT, 'item', 'tent.png'))


def cape():
    random.seed(5)
    w, h = 16, 32
    img = Image.new('RGBA', (w, h), (0, 0, 0, 255))
    px = img.load()
    for y in range(h):
        for x in range(w):
            # folds: a soft wave across, a little deeper toward the hem
            fold = 0.5 + 0.5 * math.cos((x + 0.5) / w * 3.0 * 2 * math.pi)
            depth = 0.06 + 0.08 * (y / h)
            v = 1.0 - depth * fold
            # the weave
            v -= 0.035 if (x + y) % 2 == 0 else 0.0
            v -= random.random() * 0.03
            c = int(max(0, min(255, 246 * v)))
            px[x, y] = (c, c, c, 255)
    img.save(os.path.join(ROOT, 'entity', 'realm_cape.png'))


if __name__ == '__main__':
    tent()
    cape()
