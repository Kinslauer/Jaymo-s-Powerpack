# python -I tools/gen_antennae.py kingdoms/src/main/resources/assets/jaymoskingdoms/textures/entity/verdant_antennae.png src/main/resources/assets/jaymospowers/textures/entity/zfighter_antennae.png
# A 32x16 greyscale antenna texture (tinted the Verdant's green in game): stalks shaded along their length (UVs 0,0 /
# 4,0 / 0,8), a paler knob (8,0, up to 3x3x3).
import struct, sys, zlib, random
random.seed(7)
W, H = 32, 16
px = [[(0, 0, 0, 0)] * W for _ in range(H)]
def fill(x0, y0, w, h, top, bottom):
    for y in range(y0, y0 + h):
        t = (y - y0) / max(1, h - 1)
        v = round(top + (bottom - top) * t + random.randint(-6, 6))
        for x in range(x0, x0 + w):
            px[y][x] = (v, v, v, 255)
fill(0, 0, 4, 8, 210, 170)    # base stalk, up to 1x7x1
fill(4, 0, 4, 8, 225, 195)    # upper stalk, up to 1x7x1
fill(8, 0, 12, 6, 248, 212)   # knob, up to 3x3x3
fill(0, 8, 4, 8, 220, 185)    # side prong, up to 1x7x1
raw = b''.join(b'\x00' + b''.join(bytes(p) for p in row) for row in px)
def chunk(t, d):
    return struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
png = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', W, H, 8, 6, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(raw)) + chunk(b'IEND', b'')
for out in sys.argv[1:]:
    open(out, 'wb').write(png)
