"""(0.19.86) Item art for the fibre kit: grass bandage (the linen bandage's shape in grass colours), plant mesh, wet
paper pulp, grass bed item. Run from the repo root: python -I tools/fibre_kit_art.py"""
from PIL import Image

ROOT = 'src/main/resources/assets/jaymospowers/textures/'
DARK, MID, LIGHT, PALE = (61, 81, 39, 255), (81, 106, 53, 255), (142, 175, 84, 255), (176, 199, 120, 255)

# grass bandage: recolour the linen bandage by brightness
lin = Image.open(ROOT + 'item/linen_bandage.png').convert('RGBA')
out = Image.new('RGBA', lin.size)
for x in range(16):
    for y in range(16):
        r, g, b, a = lin.getpixel((x, y))
        if a == 0:
            continue
        v = (r + g + b) / 3
        out.putpixel((x, y), DARK if v < 110 else MID if v < 185 else LIGHT if v < 215 else PALE)
out.save(ROOT + 'item/ground/grass_bandage.png')

# plant mesh: a woven square of fibre, over and under
mesh = Image.new('RGBA', (16, 16))
for x in range(2, 14):
    for y in range(2, 14):
        warp, weft = x % 3 == 0, y % 3 == 0
        if warp or weft:
            over = (x // 3 + y // 3) % 2 == 0
            mesh.putpixel((x, y), (LIGHT if over == warp else MID) if (warp and weft) else (LIGHT if warp else MID))
for i in range(2, 14):
    for p in ((i, 2), (i, 13), (2, i), (13, i)):
        mesh.putpixel(p, DARK)
mesh.save(ROOT + 'item/ground/plant_mesh.png')

# wet paper pulp: a soft grey-green sheet with darker wet spots
pulp = Image.new('RGBA', (16, 16))
SHEET, WET, EDGE = (196, 201, 178, 255), (160, 170, 140, 255), (120, 128, 104, 255)
for x in range(3, 13):
    for y in range(3, 13):
        c = SHEET
        if (x * 7 + y * 5) % 11 == 0 or (x * 3 + y * 13) % 17 == 0:
            c = WET
        if x in (3, 12) or y in (3, 12):
            c = EDGE
        pulp.putpixel((x, y), c)
for p in ((3, 3), (12, 3), (3, 12), (12, 12)):
    pulp.putpixel(p, (0, 0, 0, 0))
pulp.save(ROOT + 'item/ground/wet_paper_pulp.png')

# grass bed: a woven top and a straw edge, 16x16 block faces
STRAW, STRAW_D = (186, 160, 92, 255), (140, 118, 64, 255)
top = Image.new('RGBA', (16, 16))
for x in range(16):
    for y in range(16):
        c = LIGHT if (x + (y // 2)) % 4 < 2 else MID
        if (x * 5 + y * 3) % 13 == 0:
            c = PALE
        if x in (0, 15) or y in (0, 15):
            c = STRAW_D
        top.putpixel((x, y), c)
top.save(ROOT + 'block/grass_bed_top.png')
side = Image.new('RGBA', (16, 16))
for x in range(16):
    for y in range(16):
        if y < 13:
            continue
        side.putpixel((x, y), STRAW if (x + y) % 3 else STRAW_D)
        if y == 13:
            side.putpixel((x, y), MID if x % 2 else LIGHT)
side.save(ROOT + 'block/grass_bed_side.png')
