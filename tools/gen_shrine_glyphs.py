"""(0.7.509) Glyph tiles for the dedicated shrines: one 16x16 sign per patron on a dark stone ground.
Run from the repo root: python -I tools/gen_shrine_glyphs.py"""
from PIL import Image

OUT = 'kingdoms/src/main/resources/assets/jaymoskingdoms/textures/block/shrine_glyph_%s.png'
GROUND = (0x3A, 0x3A, 0x40, 255)

def shade(c, f):
    return (int(c[0] * f), int(c[1] * f), int(c[2] * f), 255)

def canvas():
    img = Image.new('RGBA', (16, 16), GROUND)
    return img

def put(img, pts, c):
    for x, y in pts:
        if 0 <= x < 16 and 0 <= y < 16:
            img.putpixel((x, y), c)

def rect(img, x0, y0, x1, y1, c):
    put(img, [(x, y) for x in range(x0, x1 + 1) for y in range(y0, y1 + 1)], c)

def edge(img, c):
    d = shade(c, 0.45)
    for i in range(16):
        for p in ((i, 0), (i, 15), (0, i), (15, i)):
            img.putpixel(p, d)

def save(name, img, c):
    edge(img, c)
    img.save(OUT % name)

# hearthkeeper: flame over an arch
c = (0xE0, 0xA0, 0x50, 255)
i = canvas()
put(i, [(8, 2), (8, 3), (7, 4), (8, 4), (9, 4), (7, 5), (8, 5), (9, 5), (6, 6), (7, 6), (8, 6), (9, 6), (6, 7), (7, 7), (8, 7), (9, 7), (7, 8), (8, 8)], c)
put(i, [(4, 13), (4, 12), (4, 11), (5, 10), (6, 9), (7, 9), (8, 9), (9, 9), (10, 10), (11, 11), (11, 12), (11, 13)], shade(c, 0.8))
save('hearthkeeper', i, c)

# anvil father: anvil silhouette
c = (0xB0, 0xB4, 0xBC, 255)
i = canvas()
rect(i, 2, 4, 13, 6, c)
put(i, [(14, 5), (1, 5)], c)
rect(i, 5, 7, 10, 8, c)
rect(i, 6, 9, 9, 11, c)
rect(i, 4, 12, 11, 13, c)
save('anvil_father', i, c)

# deep mother: waves over a whale tail
c = (0x3A, 0x8F, 0xB7, 255)
i = canvas()
for row in (2, 4, 6):
    put(i, [(x, row + (1 if (x // 2) % 2 else 0)) for x in range(3, 13)], c)
put(i, [(8, 13), (8, 12), (8, 11), (8, 10), (7, 9), (6, 8), (5, 8), (9, 9), (10, 8), (11, 8)], shade(c, 1.15))
save('deep_mother', i, c)

# green huntsman: antlers
c = (0x5E, 0x9E, 0x3A, 255)
i = canvas()
rect(i, 7, 9, 8, 13, c)
put(i, [(6, 8), (5, 7), (4, 6), (4, 5), (4, 4), (3, 6), (2, 5), (5, 5), (9, 8), (10, 7), (11, 6), (11, 5), (11, 4), (12, 6), (13, 5), (10, 5)], c)
save('green_huntsman', i, c)

# lantern warden: lantern with a ring of light
c = (0x9A, 0xA8, 0xFF, 255)
i = canvas()
rect(i, 6, 4, 9, 4, c)
rect(i, 5, 5, 10, 10, shade(c, 0.6))
rect(i, 6, 6, 9, 9, (0xE8, 0xEC, 0xFF, 255))
rect(i, 6, 11, 9, 11, c)
put(i, [(7, 2), (8, 2), (7, 3), (8, 3)], c)
for p in ((3, 7), (3, 8), (12, 7), (12, 8), (4, 4), (11, 4), (4, 12), (11, 12)):
    put(i, [p], shade(c, 0.7))
save('lantern_warden', i, c)

# crowned sun: rayed sun under a three-point crown
c = (0xFF, 0xC2, 0x1A, 255)
i = canvas()
rect(i, 5, 6, 10, 11, c)
rect(i, 6, 5, 9, 12, c)
rect(i, 4, 7, 11, 10, c)
for p in ((8, 13), (7, 13), (2, 8), (3, 8), (12, 8), (13, 8), (3, 12), (12, 12), (3, 5), (12, 5)):
    put(i, [p], shade(c, 0.8))
put(i, [(5, 2), (5, 3), (8, 1), (8, 2), (8, 3), (10, 2), (10, 3)], (0xFF, 0xE0, 0x70, 255))
rect(i, 5, 4, 10, 4, (0xFF, 0xE0, 0x70, 255))
save('crowned_sun', i, c)

# ash whisperer: a closed eye over an ember
c = (0x8A, 0x5A, 0x4A, 255)
i = canvas()
put(i, [(x, 5) for x in range(3, 13)], c)
put(i, [(x, 6) for x in (4, 5, 6, 7, 8, 9, 10, 11)], shade(c, 0.8))
put(i, [(x, 7) for x in (6, 7, 8, 9)], shade(c, 0.6))
put(i, [(8, 10), (7, 11), (8, 11), (9, 11), (8, 12)], (0xE0, 0x60, 0x2A, 255))
put(i, [(7, 12), (9, 12)], (0xFF, 0xA0, 0x40, 255))
save('ash_whisperer', i, c)

# truth: an open eye in a plain square
c = (0xF2, 0xF2, 0xF2, 255)
i = canvas()
for k in range(3, 13):
    put(i, [(k, 3), (k, 12), (3, k), (12, k)], shade(c, 0.7))
put(i, [(5, 8), (6, 7), (7, 6), (8, 6), (9, 7), (10, 8), (9, 9), (8, 9), (7, 9), (6, 9)], c)
rect(i, 7, 7, 8, 8, shade(c, 0.35))
save('truth', i, c)
