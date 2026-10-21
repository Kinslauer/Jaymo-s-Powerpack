"""Paints Kingdoms' surveyor's rod (16x16, vanilla stick style): a ranging pole banded red and white, a brass sighting
cross on top. Run: python3 tools/gen_surveyor_rod.py"""
from PIL import Image

OUT = 'kingdoms/src/main/resources/assets/jaymoskingdoms/textures/item/surveyor_rod.png'
# ramps from the plot stake and vanilla's stick and gold
RED, RED_DARK = (186, 38, 36, 255), (128, 24, 24, 255)
WHITE, WHITE_DARK = (222, 214, 190, 255), (170, 160, 136, 255)
WOOD, WOOD_DARK = (140, 98, 56, 255), (92, 62, 34, 255)
BRASS, BRASS_LIGHT, BRASS_DARK = (220, 170, 50, 255), (252, 238, 120, 255), (150, 100, 20, 255)

img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
px = img.load()
# the pole: two pixels wide on the diagonal, from (1, 14) up to (11, 4); bands of three steps
for i in range(11):
    x, y = 1 + i, 14 - i
    if i < 2:
        light, dark = WOOD, WOOD_DARK
    else:
        light, dark = (RED, RED_DARK) if (i - 2) // 3 % 2 == 0 else (WHITE, WHITE_DARK)
    px[x, y] = light
    px[x + 1, y] = dark if x + 1 < 16 else light
px[0, 15] = WOOD_DARK
# the brass sighting cross on top
for x, y, c in [(12, 3, BRASS), (13, 2, BRASS_LIGHT), (14, 1, BRASS), (11, 2, BRASS_DARK), (12, 1, BRASS),
                (13, 4, BRASS_DARK), (14, 3, BRASS), (13, 3, BRASS_LIGHT), (15, 0, BRASS_DARK), (11, 0, BRASS_DARK),
                (15, 4, BRASS_DARK)]:
    px[x, y] = c
img.save(OUT)
for y in range(16):
    print(''.join('#' if px[x, y][3] else '.' for x in range(16)))
