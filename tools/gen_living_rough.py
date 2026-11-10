"""Living rough art (Hardrock patch 7): the sleeping bag faces and the paraglider item."""
from PIL import Image

ROOT = 'src/main/resources/assets/jaymospowers/textures/block/'
LEATHER, LEATHER_D, LEATHER_L = (124, 82, 50, 255), (92, 58, 34, 255), (150, 104, 66, 255)
STITCH, WOOL = (214, 196, 150, 255), (226, 220, 204, 255)

top = Image.new('RGBA', (16, 16))
for x in range(16):
    for y in range(16):
        c = LEATHER if (x * 7 + y * 3) % 11 else LEATHER_L
        if (x + y * 5) % 17 == 0:
            c = LEATHER_D
        if x in (0, 15) or y in (0, 15):
            c = LEATHER_D
        elif x in (1, 14) and y % 2 == 0:
            c = STITCH
        if x == 11:
            c = LEATHER_D if y % 3 else STITCH
        top.putpixel((x, y), c)
top.save(ROOT + 'sleeping_bag_foot.png')
# the wool lining shows at the opened mouth (the pillow's corner of the texture)
for x in range(2, 14):
    for y in range(1, 6):
        top.putpixel((x, y), WOOL if (x + y) % 4 else STITCH)
top.save(ROOT + 'sleeping_bag_top.png')

side = Image.new('RGBA', (16, 16))
for x in range(16):
    for y in range(13, 16):
        side.putpixel((x, y), LEATHER if (x + y) % 3 else LEATHER_D)
    side.putpixel((x, 13), STITCH if x % 2 else LEATHER_L)
side.save(ROOT + 'sleeping_bag_side.png')

# paraglider item: a red-and-cream cloth wing arched over two crossed sticks
ITEM = 'src/main/resources/assets/jaymospowers/textures/item/'
RED, RED_D, CREAM, STICK, STICK_D, CORD = (176, 52, 44, 255), (120, 32, 30, 255), (230, 214, 176, 255), (138, 104, 62, 255), (96, 70, 40, 255), (200, 190, 160, 255)
g = Image.new('RGBA', (16, 16))
for x in range(16):
    # the canopy: an arch from y=6 at the tips to y=2 in the middle, three rows deep
    top = 2 + round(abs(x - 7.5) ** 2 / 14.0)
    for y in range(top, top + 3):
        c = RED if (x // 3) % 2 == 0 else CREAM
        if y == top + 2:
            c = RED_D
        g.putpixel((x, y), c)
# cords from the tips to the grip
for x, y in ((2, 8), (3, 9), (4, 10), (13, 8), (12, 9), (11, 10)):
    g.putpixel((x, y), CORD)
# the grip bar and the frame sticks
for x in range(5, 11):
    g.putpixel((x, 11), STICK)
for x, y in ((5, 12), (4, 13), (10, 12), (11, 13)):
    g.putpixel((x, y), STICK_D)
for x in range(6, 10):
    g.putpixel((x, 5 + (1 if x in (6, 9) else 0)), STICK_D)
g.save(ITEM + 'paraglider.png')
