"""
Paints the great goat's coat (with its riding saddle) and its spawn egg from nothing (Kingdoms beasts/GreatGoat), and
writes its egg's item model and its names (lang/parts/great_goat.json). Our own boxes and pixels only. BOXES below
mirrors kingdoms/src/client/java/com/jaymoskingdoms/client/beasts/GreatGoatModel.java; change both together.

python3 -I tools/gen_great_goat.py
"""
import json
import os
import random

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(os.path.dirname(HERE), "kingdoms", "src", "main", "resources", "assets", "jaymoskingdoms")
SEED = 2026
W, H = 64, 64

# (u, v, w, h, d, what) for each box on the sheet
BOXES = [
	(0, 0, 10, 10, 18, "body"),
	(24, 42, 11, 2, 8, "saddle"),
	(56, 9, 2, 3, 1, "tail"),
	(0, 28, 5, 9, 5, "neck"),
	(20, 28, 6, 6, 7, "skull"),
	(46, 28, 4, 4, 3, "muzzle"),
	(46, 35, 2, 4, 2, "beard"),
	(56, 0, 2, 3, 2, "horn"),
	(0, 54, 2, 2, 4, "horn"),
	(56, 5, 2, 2, 2, "horn_tip"),
	(0, 42, 3, 9, 3, "leg"),
	(12, 42, 3, 9, 3, "leg"),
]

COAT = (226, 220, 206)
COAT_DARK = (178, 170, 156)
COAT_LIGHT = (242, 238, 228)
GREY = (120, 116, 110)
HORN = (92, 84, 74)
HORN_LIGHT = (140, 130, 116)
HOOF = (46, 40, 36)
EYE = (30, 24, 18)
EYE_GOLD = (196, 156, 60)
NOSE = (70, 62, 58)
LEATHER = (110, 62, 32)
LEATHER_DARK = (72, 40, 20)
STEEL = (168, 174, 184)

LANG = {
	"entity.jaymoskingdoms.great_goat": "Great Goat",
	"item.jaymoskingdoms.great_goat_spawn_egg": "Great Goat Spawn Egg",
}


def faces(u, v, w, h, d):
	return {
		"top": (u + d, v, u + d + w, v + d),
		"bottom": (u + d + w, v, u + d + 2 * w, v + d),
		"right": (u, v + d, u + d, v + d + h),
		"front": (u + d, v + d, u + d + w, v + d + h),
		"left": (u + d + w, v + d, u + 2 * d + w, v + d + h),
		"back": (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),
	}


def mix(a, b, t):
	return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def fill(px, box, colour_at):
	x0, y0, x1, y1 = box
	for y in range(y0, y1):
		for x in range(x0, x1):
			px[x, y] = colour_at(x - x0, y - y0, x1 - x0, y1 - y0) + (255,)


def shaggy(rnd, base):
	"""Long hair: streaks running down, darker toward the bottom of a face."""
	def at(x, y, w, h):
		streak = ((x * 7 + rnd.randint(0, 2)) % 5) / 10.0
		t = min(1.0, y / max(1, h - 1)) * 0.6 + streak * 0.5
		c = mix(COAT_LIGHT, base, t)
		return mix(c, COAT_DARK, 0.15) if rnd.random() < 0.12 else c
	return at


def paint():
	rnd = random.Random(SEED)
	img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
	px = img.load()
	for u, v, w, h, d, what in BOXES:
		f = faces(u, v, w, h, d)
		for name, box in f.items():
			if what in ("horn", "horn_tip"):
				fill(px, box, lambda x, y, bw, bh: mix(HORN_LIGHT, HORN, ((x + y) % 3) / 3.0) if what == "horn" else HORN)
			elif what == "saddle":
				def leather(x, y, bw, bh, name=name):
					if name in ("top", "bottom"):
						edge = x == 0 or y == 0 or x == bw - 1 or y == bh - 1
						return LEATHER_DARK if edge else mix(LEATHER, LEATHER_DARK, 0.3 if (x + y) % 4 == 0 else 0.0)
					return STEEL if x % 4 == 1 else LEATHER_DARK
				fill(px, box, leather)
			elif what == "leg":
				fill(px, box, lambda x, y, bw, bh: HOOF if y >= bh - 2 else mix(COAT, GREY, y / max(1, bh - 1) * 0.5))
			elif what == "muzzle":
				fill(px, box, lambda x, y, bw, bh: mix(COAT_LIGHT, COAT_DARK, y / max(1, bh)))
			elif what == "beard":
				fill(px, box, lambda x, y, bw, bh: mix(COAT, GREY, y / max(1, bh) * 0.7))
			else:
				fill(px, box, shaggy(rnd, COAT))
	# the face: eyes on the skull's sides near the front, the nose on the muzzle's front
	skull = faces(20, 28, 6, 6, 7)
	for side in ("right", "left"):
		x0, y0, x1, y1 = skull[side]
		ex = x1 - 2 if side == "right" else x0 + 1
		px[ex, y0 + 2] = EYE_GOLD + (255,)
		px[ex + (1 if side == "right" else -1), y0 + 2] = EYE + (255,)
	mx0, my0, mx1, my1 = faces(46, 28, 4, 4, 3)["front"]
	for x in range(mx0, mx1):
		px[x, my0 + 1] = NOSE + (255,)
	return img


def egg():
	"""A spawn egg in vanilla's shape: a cream shell with grey-brown horn flecks."""
	rnd = random.Random(SEED + 1)
	img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
	px = img.load()
	for y in range(16):
		for x in range(16):
			cx, cy = 7.5, 8.6
			rx = 5.2 if y > 8 else 5.2 - (8.6 - y) * 0.28
			r = ((x - cx) / rx) ** 2 + ((y - cy) / 7.0) ** 2
			if r <= 1.0:
				col = mix(COAT, COAT_DARK, 0.7) if r > 0.72 else COAT
				px[x, y] = col + (255,)
	for sx, sy in ((5, 5), (9, 4), (10, 9), (6, 11), (8, 7)):
		for dx, dy in ((0, 0), (1, 0), (0, 1)):
			if px[sx + dx, sy + dy][3]:
				px[sx + dx, sy + dy] = mix(HORN, HORN_LIGHT, rnd.random()) + (255,)
	return img


def write(path, data):
	os.makedirs(os.path.dirname(path), exist_ok=True)
	with open(path, "w", encoding="utf-8", newline="\n") as f:
		json.dump(data, f, indent="\t", ensure_ascii=False)
		f.write("\n")


def main():
	tex = os.path.join(ASSETS, "textures")
	os.makedirs(os.path.join(tex, "entity"), exist_ok=True)
	paint().save(os.path.join(tex, "entity", "great_goat.png"))
	egg().save(os.path.join(tex, "item", "great_goat_spawn_egg.png"))
	write(os.path.join(ASSETS, "models", "item", "great_goat_spawn_egg.json"),
		{"parent": "minecraft:item/generated", "textures": {"layer0": "jaymoskingdoms:item/great_goat_spawn_egg"}})
	write(os.path.join(ASSETS, "items", "great_goat_spawn_egg.json"),
		{"model": {"type": "minecraft:model", "model": "jaymoskingdoms:item/great_goat_spawn_egg"}})
	write(os.path.join(ASSETS, "lang", "parts", "great_goat.json"), LANG)


if __name__ == "__main__":
	main()
