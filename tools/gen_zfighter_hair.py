"""Super hair strands (textures/entity/zfighter_hair.png): pale, tinted the form's colour in game. 64x64 in 4 bands of
16 rows, darker toward the roots (band 0) and brighter toward the tips (band 3); strands run down each band."""
import random
from PIL import Image

OUT = 'src/main/resources/assets/jaymospowers/textures/entity/zfighter_hair.png'
BANDS = [0.74, 0.84, 0.93, 1.0]

rnd = random.Random(7)
im = Image.new('RGBA', (64, 64))
for b, base in enumerate(BANDS):
    cols = [0.88 + 0.12 * rnd.random() for _ in range(64)]
    for y in range(16):
        for x in range(64):
            f = base * cols[x] * (0.97 + 0.03 * (y / 15))
            if rnd.random() < 0.08:
                f *= 0.86
            v = max(0, min(255, round(255 * f)))
            im.putpixel((x, b * 16 + y), (v, v, round(v * 0.97), 255))
im.save(OUT)
print('wrote', OUT)
