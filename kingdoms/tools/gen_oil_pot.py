"""
The boiling oil pot for the walls (OilPotBlock), built from sprite stacks: paints a 16x16 x 13-slice stack for the cold
pot and the lit one (tools/sprite_stacks/oil_pot.png, oil_pot_lit.png), then turns each into a cube block model with
the repo's tools/sprite_stack/stack2model.py (models/block/oil_pot*.json, textures/block/oil_pot*.png).

To repaint the pot by hand, edit the stack PNGs and run stack2model.py on them instead of this script (it would paint
over them). Seeded, so every run draws the same pixels. Run from the repo root: python3 -I kingdoms/tools/gen_oil_pot.py
"""
import math
import os
import random
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..", "..")
ASSETS = os.path.join(HERE, "..", "src", "main", "resources", "assets", "jaymoskingdoms")
STACKS = os.path.join(HERE, "sprite_stacks")
sys.path.insert(0, os.path.join(ROOT, "tools", "sprite_stack"))
import stack2model  # noqa: E402

N, S = 13, 16  # slices, slice size

IRON, IRON_HI, BAND, RIVET = (78, 76, 82), (104, 102, 110), (54, 52, 58), (138, 134, 140)
BRAZ, OIL, SHEEN = (44, 39, 37), (30, 22, 15), (66, 52, 32)
ASH = ((58, 56, 54), (40, 38, 37), (74, 70, 66))
COALS = ((70, 24, 16), (150, 44, 20), (214, 98, 34), (150, 44, 20))
FLAME = ((255, 170, 50), (240, 110, 30))
BUBBLE = (92, 74, 44)

# pot radius by slice (0-2 are the brazier)
POT = {3: 3.4, 4: 5.2, 5: 6.1, 6: 6.6, 7: 6.6, 8: 6.4, 9: 5.9, 10: 5.1, 11: 4.7, 12: 5.3}


def paint(lit):
	rng = random.Random(7)
	img = Image.new("RGBA", (S * N, S), (0, 0, 0, 0))
	px = img.load()

	def jit(c, n=6):
		d = rng.randint(-n, n)
		return tuple(max(0, min(255, v + d)) for v in c) + (255,)

	def put(y, x, z, c):
		if 0 <= x < S and 0 <= z < S:
			px[y * S + x, z] = c

	for z in range(S):
		for x in range(S):
			d = math.hypot(x + 0.5 - 8, z + 0.5 - 8)
			# brazier: three legs, then a shallow bowl of coals wider than the pot's foot, so the ring shows
			for a in (90, 210, 330):
				lx, lz = 8 + 4.6 * math.cos(math.radians(a)), 8 + 4.6 * math.sin(math.radians(a))
				if math.hypot(x + 0.5 - lx, z + 0.5 - lz) < 1.0:
					put(0, x, z, jit(BRAZ))
					put(1, x, z, jit(BRAZ))
			if d < 5.4:
				put(2, x, z, jit(BRAZ) if d > 4.6 else jit(rng.choice(COALS if lit else ASH), 10))
				if lit and 3.4 <= d <= 4.6 and rng.random() < 0.3:
					put(3, x, z, jit(rng.choice(FLAME), 8))  # flames licking the pot's foot
			for y, rr in POT.items():
				if d >= rr:
					continue
				if y >= 10 and d < rr - 1.2:
					continue  # hollow neck and lip
				if y == 9 and d < rr - 1.2:
					c = SHEEN if rng.random() < 0.15 else OIL
					if lit and rng.random() < 0.18:
						c = BUBBLE  # it's on the boil
					put(y, x, z, jit(c, 4))  # the oil's surface
					continue
				c = IRON_HI if (y == 12 or (x - z) > 9 - y * 0.2 and d > rr - 1.5) else IRON
				if y == 7:
					c = RIVET if (x + z) % 4 == 0 and d > rr - 1 else BAND
				put(y, x, z, jit(c))
	# pouring lip out to the north (the side it tips over the wall), ring handles east and west
	for y in (11, 12):
		for x in (7, 8):
			for z in range(1, 4):
				put(y, x, z, jit(IRON_HI if y == 12 else IRON))
	for x in (6, 9):
		put(12, x, 2, jit(IRON_HI))
	for y in (8, 10):
		for x in (1, 14):
			put(y, x, 7, jit(BAND))
			put(y, x, 8, jit(BAND))
	for x, z in ((1, 6), (1, 9), (14, 6), (14, 9)):
		put(9, x, z, jit(BAND))
	return img


def main():
	os.makedirs(STACKS, exist_ok=True)
	for lit in (False, True):
		name = "oil_pot" + ("_lit" if lit else "")
		stack = os.path.join(STACKS, name + ".png")
		paint(lit).save(stack)
		info = stack2model.convert(stack, os.path.join(STACKS, "out"), "jaymoskingdoms:block/" + name,
								   model_dir=os.path.join(ASSETS, "models", "block"),
								   texture_dir=os.path.join(ASSETS, "textures", "block"))
		print(name, info)


if __name__ == "__main__":
	main()
