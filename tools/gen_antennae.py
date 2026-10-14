# python -I tools/gen_antennae.py kingdoms/src/main/resources/assets/jaymoskingdoms/textures/entity/verdant_antennae.png src/main/resources/assets/jaymospowers/textures/entity/zfighter_antennae.png
# A 16x16 greyscale antenna texture (tinted the Verdant's green in game): stalks shaded along their length, a paler knob.
import struct, sys, zlib, random
random.seed(7)
W = H = 16
px = [[(0, 0, 0, 0)] * W for _ in range(H)]
def fill(x0, y0, w, h, top, bottom):
    for y in range(y0, y0 + h):
        t = (y - y0) / max(1, h - 1)
        v = round(top + (bottom - top) * t + random.randint(-6, 6))
        for x in range(x0, x0 + w):
            px[y][x] = (v, v, v, 255)
fill(0, 0, 4, 5, 205, 175)    # base stalk 1x4x1 at (0,0): 4 wide, 5 tall
fill(4, 0, 4, 5, 220, 200)    # upper stalk 1x4x1 at (4,0)
fill(0, 6, 8, 4, 245, 215)    # knob 2x2x2 at (0,6): 8 wide, 4 tall
raw = b''.join(b'\x00' + b''.join(bytes(p) for p in row) for row in px)
def chunk(t, d):
    return struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
png = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', W, H, 8, 6, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(raw)) + chunk(b'IEND', b'')
for out in sys.argv[1:]:
    open(out, 'wb').write(png)
