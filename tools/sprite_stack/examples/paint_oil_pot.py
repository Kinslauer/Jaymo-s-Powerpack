"""
Paints a sample sprite stack (the walls' boiling oil pot: an iron pot on a little brazier) as a 16x16 x 13-slice strip,
the way a sprite-stack artist or SpriteCook would hand one over. Test input for stack2model.py.

python3 -I tools/sprite_stack/examples/paint_oil_pot.py <out.png>
"""
import math
import random
import sys

from PIL import Image

N, S = 13, 16
rng = random.Random(7)
img = Image.new("RGBA", (S * N, S), (0, 0, 0, 0))
P = img.load()


def jit(c, n=6):
	d = rng.randint(-n, n)
	return tuple(max(0, min(255, v + d)) for v in c) + (255,)


def put(y, x, z, c):
	if 0 <= x < S and 0 <= z < S:
		P[y * S + x, z] = c


def rad(x, z):
	return math.hypot(x + 0.5 - 8, z + 0.5 - 8)


IRON, IRON_HI, BAND, RIVET = (78, 76, 82), (104, 102, 110), (54, 52, 58), (138, 134, 140)
BRAZ, COAL, EMBER, HOT, OIL, SHEEN = (44, 39, 37), (70, 24, 16), (150, 44, 20), (214, 98, 34), (30, 22, 15), (66, 52, 32)

# pot radius by slice (0-2 are the brazier)
POT = {3: 4.2, 4: 5.4, 5: 6.1, 6: 6.6, 7: 6.6, 8: 6.4, 9: 5.9, 10: 5.1, 11: 4.7, 12: 5.3}

for z in range(S):
	for x in range(S):
		d = rad(x, z)
		# brazier: three legs, then a shallow bowl of coals
		for a in (90, 210, 330):
			lx, lz = 8 + 4.6 * math.cos(math.radians(a)), 8 + 4.6 * math.sin(math.radians(a))
			if math.hypot(x + 0.5 - lx, z + 0.5 - lz) < 1.0:
				put(0, x, z, jit(BRAZ))
				put(1, x, z, jit(BRAZ))
		if d < 5.2:
			put(2, x, z, jit(BRAZ) if d > 4.1 else jit(rng.choice((COAL, COAL, EMBER, HOT)), 10))
		for y, rr in POT.items():
			if d >= rr:
				continue
			if y >= 10 and d < rr - 1.2:
				continue  # hollow neck and lip
			if y == 9 and d < rr - 1.2:
				put(y, x, z, jit(SHEEN if rng.random() < 0.15 else OIL, 4))  # the oil's surface
				continue
			c = IRON_HI if (y == 12 or (x - z) > 9 - y * 0.2 and d > rr - 1.5) else IRON
			if y == 7:
				c = RIVET if (x + z) % 4 == 0 and d > rr - 1 else BAND
			put(y, x, z, jit(c))
# pouring lip out to the north, and ring handles east and west
for y in (11, 12):
	for x in (7, 8):
		for z in range(1, 4):
			put(y, x, z, jit(IRON_HI if y == 12 else IRON))
for x in (6, 9):
	put(12, x, 2, jit(IRON_HI))
for y in (8, 9, 10):
	for x in (1, 14):
		if y != 9:
			put(y, x, 7, jit(BAND))
			put(y, x, 8, jit(BAND))
	for x, z in ((1, 6), (1, 9), (14, 6), (14, 9)):
		put(9, x, z, jit(BAND))
img.save(sys.argv[1])
