"""(0.7.484) The building plan item and the mason's table's top and side: drawn here, our own art. Run from kingdoms/:
python -I tools/gen_mason_table.py"""
import random
from PIL import Image

OUT = 'src/main/resources/assets/jaymoskingdoms/textures/'
rnd = random.Random(484)


def jitter(c, n=8):
    return tuple(max(0, min(255, v + rnd.randint(-n, n))) for v in c) + (255,)


# --- building plan: a rolled sheet of blue drafting paper tied with twine -----------------------------------------
plan = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
p = plan.load()
BLUE, DARK, LINE, EDGE, TWINE = (54, 92, 156), (34, 60, 112), (196, 214, 240), (24, 40, 80), (150, 104, 60)
# the roll runs from bottom-left to top-right, 5 px thick
for i in range(2, 14):
    for t in range(-2, 3):
        x, y = i + t, 15 - i + t
        if 0 <= x < 16 and 0 <= y < 16:
            c = BLUE if abs(t) < 2 else DARK
            if t == -2:
                c = EDGE
            if t == 0 and i % 3 == 0:
                c = LINE
            p[x, y] = jitter(c, 5)
# the ends of the roll: paper curls showing
for (x, y) in [(1, 13), (2, 14), (13, 1), (14, 2), (12, 0), (15, 3)]:
    p[x, y] = jitter(LINE, 6)
# twine round its middle
for t in range(-2, 3):
    p[7 + t, 7 + t] = jitter(TWINE, 6)
    p[8 + t, 8 + t] = jitter((120, 80, 44), 6)
p[9, 11] = jitter(TWINE, 6)
p[10, 12] = jitter(TWINE, 6)
plan.save(OUT + 'item/building_plan.png')

# --- mason's table top: an oak frame round a pinned-down plan, a set square and chalk -----------------------------
top = Image.new('RGBA', (16, 16))
t = top.load()
for y in range(16):
    for x in range(16):
        if x in (0, 15) or y in (0, 15):
            t[x, y] = jitter((104, 76, 44), 6)
        elif x in (1, 14) or y in (1, 14):
            t[x, y] = jitter((150, 112, 66), 8)
        else:
            t[x, y] = jitter(BLUE, 4)
# the plan's drawing: a house front in white lines
for x in range(4, 12):
    t[x, 11] = jitter(LINE, 4)
for y in range(7, 12):
    t[4, y] = jitter(LINE, 4)
    t[11, y] = jitter(LINE, 4)
for i in range(4):
    t[4 + i, 6 - i] = jitter(LINE, 4)
    t[11 - i, 6 - i] = jitter(LINE, 4)
t[7, 9] = t[8, 9] = t[7, 10] = t[8, 10] = jitter(LINE, 4)
# pins in the corners
for (x, y) in [(2, 2), (13, 2), (2, 13), (13, 13)]:
    t[x, y] = (200, 60, 50, 255)
# a brass set square
for i in range(4):
    t[12, 9 + i] = jitter((196, 160, 70), 6)
    t[9 + i, 12] = jitter((196, 160, 70), 6)
top.save(OUT + 'block/mason_table_top.png')

# --- side: stone bricks below an oak lip, a chisel and mallet hung on it -----------------------------------------
side = Image.new('RGBA', (16, 16))
s = side.load()
for y in range(16):
    for x in range(16):
        if y < 3:
            s[x, y] = jitter((150, 112, 66) if y < 2 else (104, 76, 44), 6)
            continue
        row = (y - 3) // 4
        mortar = (y - 3) % 4 == 3 or (x + (4 if row % 2 else 0)) % 8 == 7
        s[x, y] = jitter((92, 92, 92) if mortar else (128, 128, 128), 6)
for y in range(4, 9):
    s[4, y] = jitter((110, 110, 118), 4)
s[4, 9] = jitter((80, 60, 40), 4)
for y in range(4, 10):
    s[11, y] = jitter((120, 86, 50), 6)
s[10, 4] = s[12, 4] = s[10, 5] = s[12, 5] = jitter((150, 112, 66), 6)
side.save(OUT + 'block/mason_table_side.png')
print('ok')
