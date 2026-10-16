"""Automatic pass that pulls a modded item texture toward vanilla's look:
- hi-res (32px+) icons come down to 16px;
- near-black outlines become a dark shade of the colour they wrap (vanilla never outlines in black);
- flat fills get vanilla's top-left light: a lit rim inside the upper-left edge, a shaded rim inside the lower-right.
Hand-painted textures (tools/gen/batch.py) take precedence over this pass."""
import colorsys
from PIL import Image

def lum(c): return (0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]) / 255

def to16(im):
    if im.width <= 16: return im
    f = im.width // 16
    out = Image.new('RGBA', (16, 16)); px = out.load(); src = im.load()
    for y in range(16):
        for x in range(16):
            cells = [src[x * f + i, y * f + j] for j in range(f) for i in range(f)]
            op = [c for c in cells if c[3] > 127]
            if len(op) * 2 < len(cells): continue      # mostly clear: stays clear
            # keep a real pixel colour (the most common), not a blend: pixel art stays crisp
            best = max(set(op), key=op.count) if len(set(op)) < len(op) else min(op, key=lambda c: lum(c))
            px[x, y] = best[:3] + (255,)
    return out

def hsv(c): return colorsys.rgb_to_hsv(*(v / 255 for v in c[:3]))
def rgb(h, s, v): return tuple(round(255 * t) for t in colorsys.hsv_to_rgb(h, min(s, 1), min(v, 1)))

def vanillaize(im, outline=True, light=True):
    im = to16(im.convert('RGBA'))
    if im.height > im.width: im = im.crop((0, 0, im.width, im.width))
    W = im.width; px = im.load()
    op = lambda x, y: 0 <= x < W and 0 <= y < W and px[x, y][3] > 0
    edge = {(x, y) for y in range(W) for x in range(W) if op(x, y) and
            not all(op(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))}
    orig = {(x, y): px[x, y] for y in range(W) for x in range(W)}
    if outline:
        for (x, y) in edge:
            c = orig[(x, y)]
            if lum(c) > 0.22: continue
            near = [orig[(x + dx, y + dy)] for dy in (-2, -1, 0, 1, 2) for dx in (-2, -1, 0, 1, 2)
                    if op(x + dx, y + dy) and (x + dx, y + dy) not in edge and lum(orig[(x + dx, y + dy)]) > 0.22]
            if not near: continue
            h, s, v = hsv(tuple(sum(n[i] for n in near) // len(near) for i in range(3)))
            lit = not op(x, y - 1) or not op(x - 1, y)
            px[x, y] = rgb(h, s * 1.1 + 0.1, v * (0.42 if lit else 0.28)) + (255,)
    if light:
        for y in range(W):
            for x in range(W):
                if not op(x, y) or (x, y) in edge: continue
                c = orig[(x, y)]
                if lum(c) < 0.12: continue
                ul = (x - 1, y) in edge or (x, y - 1) in edge or not op(x - 1, y - 1)
                dr = (x + 1, y) in edge or (x, y + 1) in edge or not op(x + 1, y + 1)
                if ul == dr: continue
                h, s, v = hsv(c)
                # only touch flat fills: a pixel that already differs from its neighbours is shaded already
                same = sum(1 for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)) if op(x + dx, y + dy) and orig[(x + dx, y + dy)][:3] == c[:3])
                if same < 2: continue
                px[x, y] = (rgb(h, s * 0.85, v * 1.18 + 0.04) if ul else rgb(h, s * 1.05, v * 0.8)) + (255,)
    return im
