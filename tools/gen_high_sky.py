"""(0.20.36) Paints the high sky's atlas: textures/environment/high_sky.png (64x64).

  0,0  32x32  the planet's surface far below (tiles both ways): sea, land, ice, cloud
 32,0  16x16  the Pale Moon        48,0  16x16  Rustfall
 32,16 16x16  Glacia               48,16 16x16  the sun
  0,32  8x8   plain white (the dome, the stars, the haze: tinted by vertex colour)
Run: python3 tools/gen_high_sky.py
"""
import math, random, os
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), '..', 'src/main/resources/assets/jaymospowers/textures/environment/high_sky.png')
img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
px = img.load()
rnd = random.Random(7)

# --- the surface: tiled value noise so the land wraps round
def noise(seed, cells):
    r = random.Random(seed)
    g = [[r.random() for _ in range(cells)] for _ in range(cells)]
    def at(x, y):
        fx, fy = x * cells / 32.0, y * cells / 32.0
        x0, y0 = int(fx) % cells, int(fy) % cells
        x1, y1 = (x0 + 1) % cells, (y0 + 1) % cells
        tx, ty = fx - int(fx), fy - int(fy)
        tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
        a = g[y0][x0] * (1 - tx) + g[y0][x1] * tx
        b = g[y1][x0] * (1 - tx) + g[y1][x1] * tx
        return a * (1 - ty) + b * ty
    return at
land = [noise(1, 4), noise(2, 8)]
cloud = [noise(3, 4), noise(4, 16)]
SEA = [(24, 58, 122), (30, 70, 140), (36, 84, 156)]
SHORE = (58, 112, 170)
GRASS = [(70, 120, 52), (84, 136, 60), (98, 146, 68)]
SAND = (176, 160, 104)
for y in range(32):
    for x in range(32):
        h = land[0](x, y) * 0.7 + land[1](x, y) * 0.3
        c = cloud[0](x, y) * 0.6 + cloud[1](x, y) * 0.4
        if h < 0.54:
            col = SEA[min(2, int(h / 0.54 * 3))]
        elif h < 0.57:
            col = SHORE
        elif h < 0.59:
            col = SAND
        else:
            col = GRASS[rnd.randrange(3)]
        if c > 0.62:
            k = min(1.0, (c - 0.62) / 0.12)
            col = tuple(int(col[i] * (1 - k) + 236 * k) for i in range(3))
        px[x, y] = col + (255,)

# --- discs: a ball lit from the upper left, with a pattern of its own
def disc(ox, oy, base, dark, spots, seed):
    r = random.Random(seed)
    marks = [(r.uniform(3, 13), r.uniform(3, 13), r.uniform(1.0, 2.4)) for _ in range(spots)]
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            d = math.hypot(dx, dy)
            if d > 7.4:
                continue
            light = 1.0 - max(0.0, (dx + dy) / 15.0) * 0.55
            col = base
            for mx, my, mr in marks:
                if math.hypot(x - mx, y - my) < mr:
                    col = dark
            px[ox + x, oy + y] = tuple(max(0, min(255, int(v * light))) for v in col) + (255,)
disc(32, 0, (226, 230, 240), (176, 180, 196), 7, 11)   # the Pale Moon
disc(48, 0, (212, 104, 60), (150, 62, 40), 6, 12)      # Rustfall
disc(32, 16, (170, 222, 245), (230, 246, 255), 5, 13)  # Glacia
# the sun: a white-hot heart in a warm rim
for y in range(16):
    for x in range(16):
        d = math.hypot(x - 7.5, y - 7.5)
        if d <= 7.4:
            k = d / 7.4
            col = (255, int(255 - 40 * k), int(240 - 120 * k))
            px[48 + x, 16 + y] = col + (255,)
for y in range(32, 40):
    for x in range(0, 8):
        px[x, y] = (255, 255, 255, 255)
os.makedirs(os.path.dirname(OUT), exist_ok=True)
img.save(OUT)
print('wrote', OUT)
