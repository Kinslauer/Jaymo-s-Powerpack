"""(0.5.29) Pmeh crop stages 0-3, Pmeh seeds and fresh Pmeh (the dried Pmeh icon, greener).
Run from kitchen/: python -I tools/gen_pmeh_crop.py"""
from PIL import Image

T = "src/main/resources/assets/jaymoskitchen/textures/"
stem, leaf, leaf2, dark = (70, 96, 40, 255), (84, 132, 58, 255), (110, 156, 72, 255), (48, 80, 36, 255)
bud, bud2 = (204, 178, 232, 255), (164, 128, 204, 255)


def stage(n):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    top = 15 - (3 + n * 4)  # taller each stage
    for x0, lean in [(4, 0), (11, 0), (7, -1), (8, 1)][: 2 + min(n, 2)]:
        x = x0
        for i, y in enumerate(range(15, top, -1)):
            if i % 3 == 2:
                x += lean
            px[max(0, min(15, x)), y] = stem
            if i % 2 == 1 and i > 0:
                for dx in (-1, 1):
                    xx = x + dx
                    if 0 <= xx < 16:
                        px[xx, y] = leaf2 if (i + dx) % 4 == 1 else leaf
            elif i > 2:
                for dx in (-1, 1):
                    xx = x + dx * 2
                    if 0 <= xx < 16 and i % 4 == 0:
                        px[xx, y] = dark
        if n == 3:
            for (bx, by) in [(x, top), (x - 1, top + 1), (x + 1, top + 1), (x, top + 2)]:
                if 0 <= bx < 16 and 0 <= by < 16:
                    px[bx, by] = bud if (bx + by) % 2 else bud2
    img.save(T + "block/pmeh_crop_stage%d.png" % n)


for n in range(4):
    stage(n)

# seeds: a few small dark-brown teardrop seeds
img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
px = img.load()
seed, seed2, hi = (92, 70, 44, 255), (66, 50, 32, 255), (140, 116, 80, 255)
for cx, cy in [(5, 6), (10, 5), (7, 10), (11, 11), (4, 12)]:
    for dx, dy, c in [(0, 0, seed), (1, 0, seed2), (0, 1, seed2), (1, 1, seed2), (0, -1, hi)]:
        px[cx + dx, cy + dy] = c
img.save(T + "item/pmeh_seeds.png")

# fresh: the Pmeh icon, shifted greener and brighter
src = Image.open(T + "item/pmeh.png").convert("RGBA")
out = src.copy()
p, q = src.load(), out.load()
for y in range(16):
    for x in range(16):
        r, g, b, a = p[x, y]
        if a:
            q[x, y] = (int(r * 0.7), min(255, int(g * 1.25) + 12), int(b * 0.7), a)
out.save(T + "item/fresh_pmeh.png")
