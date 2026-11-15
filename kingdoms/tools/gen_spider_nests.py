"""
The spider nests' art (wild/nest): the silk deeps' blocks (spider stone, nest silk, shed skin, egg sacs, the brood egg in
its four states), the silk gland, and the brood queen's Blockbench project (kingdoms/art/brood_queen.bbmodel: our own
cubes, texture and animations), from which the game's GeckoLib files are written (tools/bbmodel_geckolib.py), and her
spawn egg. All our own pixels; Derelict's arachne was only the reference for what such a boss does.

The queen: a broad dark chitin forebody with a ring of red eyes, two downturned fangs and two feelers, a great swollen
abdomen with her brood riding on it in pale lumps, and eight jointed legs (each a thigh and a shin) splayed wide.

Blockbench space: y up, feet at 0, the front at -z, the model's right at +x. Rotations as Blockbench shows them (gen_imp's
notes): +z lifts a right leg (at +x) up, +y sweeps it back; x: - lifts the head. Legs rest splayed (their bones' own
rotation); animation keys add to that.

Run from the project root: python3 -I kingdoms/tools/gen_spider_nests.py   (needs Pillow)
"""
import json
import os
import random
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import gen_tomb_undead as kit  # noqa: E402
from gen_tomb_undead import Builder, anim, add_animations, bone_uuids_of, mirror, shade  # noqa: E402
from PIL import Image  # noqa: E402

ROOT = kit.ROOT
ASSETS = os.path.join(ROOT, "kingdoms", "src", "main", "resources", "assets", "jaymoskingdoms")
BLOCKS = os.path.join(ASSETS, "textures", "block")
ITEMS = os.path.join(ASSETS, "textures", "item")
Z = (0, 0, 0)

# ---------------------------------------------------------------- colours (ours)

QUEEN = {
	"q_shell": (54, 40, 58), "q_shell_dark": (36, 26, 40), "q_abdomen": (74, 52, 70), "q_leg": (44, 32, 46),
	"q_shin": (62, 46, 60), "q_fang": (28, 18, 20), "q_egg": (222, 212, 186), "q_palp": (88, 64, 80),
}
EYE, EYE_SMALL = (224, 38, 44), (170, 30, 40)
EGG = {"brood_queen": ((52, 38, 56), (226, 220, 206))}
STONE, STONE_DARK, STONE_LIGHT = (58, 54, 62), (40, 37, 44), (78, 74, 84)
SILK, SILK_DARK, SILK_LIGHT = (222, 218, 206), (186, 180, 166), (244, 242, 234)
SKIN, SKIN_DARK = (176, 150, 112), (128, 104, 72)
EGG_SHELL, EGG_DARK, EGG_LIGHT = (226, 214, 188), (190, 172, 140), (244, 238, 222)


# ---------------------------------------------------------------- blocks

def noise_tile(seed, base, dark, light, dark_share=0.22, light_share=0.12):
	rnd = random.Random(seed)
	img = Image.new("RGBA", (16, 16))
	for y in range(16):
		for x in range(16):
			r = rnd.random()
			c = dark if r < dark_share else light if r > 1.0 - light_share else base
			img.putpixel((x, y), shade(c, 0.94 + rnd.random() * 0.12) + (255,))
	return img, rnd


def strand(img, rnd, colour, n, alpha=255):
	"""Thin silk threads: wandering lines that wrap round the tile."""
	for _ in range(n):
		x, y = rnd.randrange(16), rnd.randrange(16)
		dx, dy = rnd.choice(((1, 0), (0, 1), (1, 1), (1, -1)))
		for _ in range(rnd.randint(5, 12)):
			img.putpixel((x % 16, y % 16), colour + (alpha,))
			x += dx
			y += dy
			if rnd.random() < 0.3:
				dx, dy = rnd.choice(((1, 0), (0, 1), (1, 1), (1, -1)))


def spider_stone():
	# dark stone, near black with a violet cast, cracked, with old silk threads pressed into it
	img, rnd = noise_tile(101, STONE, STONE_DARK, STONE_LIGHT)
	for _ in range(3):
		x, y = rnd.randrange(16), rnd.randrange(16)
		for _ in range(rnd.randint(3, 6)):
			img.putpixel((x % 16, y % 16), shade(STONE_DARK, 0.7) + (255,))
			x += rnd.choice((-1, 0, 1))
			y += 1
	strand(img, rnd, (150, 144, 150), 3)
	return img


def nest_silk():
	# matted silk: off-white, layered, darker where it's packed, with loose threads over it
	img, rnd = noise_tile(102, SILK, SILK_DARK, SILK_LIGHT, 0.18, 0.2)
	for y in range(16):
		if y % 4 == 2:
			for x in range(16):
				if rnd.random() < 0.6:
					img.putpixel((x, y), shade(SILK_DARK, 0.95) + (255,))
	strand(img, rnd, SILK_LIGHT, 6)
	return img


def shed_skin():
	# a moulted skin lying flat: a hollow husk of a spider, legs out, see-through between
	rnd = random.Random(103)
	img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
	body = [(x, y) for y in range(5, 12) for x in range(6, 10)] + [(x, y) for y in range(3, 6) for x in range(7, 9)]
	for x, y in body:
		c = SKIN if rnd.random() > 0.25 else SKIN_DARK
		img.putpixel((x, y), c + (200,))
	for side in (-1, 1):
		for i, y in enumerate((4, 6, 8, 10)):
			x = 7.5 + side * 2
			ly = y
			for step in range(5):
				x += side
				ly += (-1 if i < 2 else 1) if step in (1, 3) else 0
				if 0 <= int(x) < 16 and 0 <= ly < 16:
					img.putpixel((int(x), ly), SKIN_DARK + (210,))
	return img


def egg_sac():
	# a silk sac: pale, soft and lumpy, the eggs showing faintly through it
	img, rnd = noise_tile(104, (230, 222, 200), (204, 194, 168), (246, 242, 230), 0.15, 0.2)
	for _ in range(7):
		x, y = rnd.randrange(1, 15), rnd.randrange(1, 15)
		img.putpixel((x, y), (196, 180, 140, 255))
		img.putpixel((x + 1, y), (210, 196, 158, 255))
	strand(img, rnd, (250, 248, 240), 4)
	return img


def brood_egg(cracks):
	# the queen's egg: a great pearly egg, faint veins under its shell; cracked by each blow
	img, rnd = noise_tile(105, EGG_SHELL, EGG_DARK, EGG_LIGHT, 0.12, 0.2)
	vr = random.Random(205)
	for _ in range(4):
		x, y = vr.randrange(16), vr.randrange(16)
		for _ in range(6):
			img.putpixel((x % 16, y % 16), (200, 168, 160, 255))
			x += vr.choice((-1, 1))
			y += vr.choice((0, 1))
	cr = random.Random(305)
	for n in range(cracks * 2):
		x, y = cr.randrange(3, 13), 0 if n % 2 == 0 else cr.randrange(4, 10)
		for _ in range(cr.randint(6, 10)):
			if 0 <= x < 16 and 0 <= y < 16:
				img.putpixel((x, y), (60, 44, 40, 255))
				if x + 1 < 16:
					img.putpixel((x + 1, y), (110, 92, 80, 255))
			x += cr.choice((-1, 0, 1))
			y += 1
	return img


def brood_egg_inside():
	# split open: the shell's torn rim round a wet, yellowed hollow
	rnd = random.Random(106)
	img = Image.new("RGBA", (16, 16))
	for y in range(16):
		for x in range(16):
			d = max(abs(x - 7.5), abs(y - 7.5))
			if d > 6:
				c = EGG_SHELL
			elif d > 5:
				c = (150, 130, 100)
			else:
				c = (196, 178, 96) if rnd.random() > 0.3 else (168, 150, 70)
			img.putpixel((x, y), shade(c, 0.92 + rnd.random() * 0.14) + (255,))
	return img


def silk_gland():
	# a silk gland: a pale, glossy, pear-shaped sac with a thread drawn out of it
	img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
	for y in range(16):
		for x in range(16):
			dx, dy = (x - 7.5) / 4.6, (y - 9.0) / 5.2
			if dx * dx + dy * dy <= 1.0 and y > 4:
				light = 1.15 if dx < -0.2 and dy < -0.1 else 0.85 if dx > 0.4 else 1.0
				img.putpixel((x, y), shade((214, 200, 170), light) + (255,))
	for y in range(0, 5):
		img.putpixel((8 if y % 2 else 7, y), (246, 244, 236, 255))
	img.putpixel((6, 7), (252, 250, 244, 255))
	img.putpixel((6, 8), (252, 250, 244, 255))
	return img


def blocks():
	os.makedirs(BLOCKS, exist_ok=True)
	out = {"spider_stone": spider_stone(), "nest_silk": nest_silk(), "shed_skin": shed_skin(), "egg_sac": egg_sac(),
		   "brood_egg": brood_egg(0), "brood_egg_cracked_1": brood_egg(1), "brood_egg_cracked_2": brood_egg(2),
		   "brood_egg_inside": brood_egg_inside()}
	for name, img in out.items():
		img.save(os.path.join(BLOCKS, name + ".png"))
		print("wrote textures/block/" + name + ".png")
	silk_gland().save(os.path.join(ITEMS, "silk_gland.png"))
	print("wrote textures/item/silk_gland.png")


# ---------------------------------------------------------------- the queen

# Where each pair of legs meets the forebody (z), and how each rests: swept (y, + back) and lifted (z) at the hip, the shin
# bent down from the knee.
LEG_Z = (-11, -8, -5, -2)
LEG_SWEEP = (-40, -14, 12, 38)
LEG_LIFT, SHIN_BEND = 35, -100
THIGH, SHIN = 12, 18
HIP_X, HIP_Y = 6, 10


def eyes(img, r):
	"""Her face: two great red eyes and a ring of small ones over them."""
	x0, y0, x1, y1 = r
	w = x1 - x0
	for ex in (x0 + w // 2 - 3, x0 + w // 2 + 1):
		for dx in range(2):
			for dy in range(2):
				img.putpixel((ex + dx, y0 + 1 + dy), EYE + (255,))
		img.putpixel((ex, y0 + 1), (255, 160, 150, 255))
	for ex in (x0 + 1, x0 + w // 2 - 1, x0 + w // 2, x1 - 2):
		img.putpixel((ex, y0), EYE_SMALL + (255,))


def marks(img, r):
	"""Her abdomen's back: a pale broken stripe and chevrons down it."""
	x0, y0, x1, y1 = r
	cx = (x0 + x1) // 2
	for y in range(y0, y1):
		if (y - y0) % 3 != 2:
			img.putpixel((cx, y), (150, 118, 132, 255))
			img.putpixel((cx - 1, y), (150, 118, 132, 255))
		if (y - y0) % 5 == 0:
			for k in range(1, 5):
				for x in (cx - 1 - k, cx + k):
					if x0 <= x < x1 and y + k // 2 < y1:
						img.putpixel((x, y + k // 2), (120, 90, 108, 255))


def build():
	saved = dict(kit.PALETTE)
	kit.PALETTE.update(QUEEN)
	b = Builder(128, 128, 61)
	b.bone("root", None, Z)
	b.bone("body", "root", (0, 10, -6))
	b.cube("body", (-6, 6, -13), (6, 13, 0), "q_shell")
	b.cube("body", (-4, 13, -11), (4, 14, -2), "q_shell_dark")
	b.bone("head", "body", (0, 9, -13))
	b.cube("head", (-4.5, 6.5, -18), (4.5, 12, -13), "q_shell", faces_paint={"north": eyes})
	mirror(b, "fang", "head", (2, 7, -17.5), [((1, 2.5, -19.5), (3, 7, -17), "q_fang")], (-10, 0, 0))
	mirror(b, "palp", "head", (3.5, 7.5, -17.5), [((3, 7, -23), (4.5, 8.2, -17.5), "q_palp")], (15, -10, 0))
	b.bone("abdomen", "body", (0, 11, 0))
	b.cube("abdomen", (-8, 6, 0), (8, 19, 19), "q_abdomen", faces_paint={"up": marks})
	b.cube("abdomen", (-1.5, 8, 19), (1.5, 11, 21), "q_shell_dark")
	b.bone("brood", "abdomen", (0, 19, 9))
	rnd = random.Random(7)
	for _ in range(7):
		x, z = rnd.uniform(-6, 4), rnd.uniform(2, 15)
		s = rnd.choice((1.5, 2.0, 2.5))
		b.cube("brood", (x, 19, z), (x + s, 19 + s * 0.8, z + s), "q_egg")
	for i, z in enumerate(LEG_Z):
		mirror(b, f"leg{i}", "body", (HIP_X, HIP_Y, z), [((HIP_X, HIP_Y - 1, z - 1), (HIP_X + THIGH, HIP_Y + 1, z + 1), "q_leg")],
			   (0, LEG_SWEEP[i], LEG_LIFT))
		mirror(b, f"shin{i}", (f"leg{i}",), (HIP_X + THIGH, HIP_Y, z),
			   [((HIP_X + THIGH, HIP_Y - 0.8, z - 0.8), (HIP_X + THIGH + SHIN, HIP_Y + 0.8, z + 0.8), "q_shin")], (0, 0, SHIN_BEND))
	kit.PALETTE.clear()
	kit.PALETTE.update(saved)
	return b


# ---------------------------------------------------------------- animations

def loop(length, *keys):
	"""Keys as (fraction of the loop, value); the first is repeated at the end so the loop closes."""
	return [(round(length * f, 4), v) for f, v in keys] + [(length, keys[0][1])]


def legs(fn):
	"""Every leg's tracks from fn(side, i) -> {"leg": [...], "shin": [...]}: right side as given, left mirrored (y, z)."""
	out = {}
	for i in range(4):
		for side, s in (("right", 1), ("left", -1)):
			for part, keys in fn(side, i).items():
				out[f"{side}_{part}{i}"] = {"rotation": [(t, (v[0], v[1] * s, v[2] * s)) for t, v in keys]}
	return out


def gait_group(side, i):
	"""Spiders walk in alternating fours: right 0 and 2 with left 1 and 3, then the rest."""
	return (i % 2 == 0) == (side == "right")


# the sleeping crouch: body lowered onto the floor, legs drawn in and folded
SLEEP_LEG, SLEEP_SHIN, SLEEP_DOWN = (0, 0, -20), (0, 0, 40), (0, -4.5, 0)


def animations():
	idle = anim("idle", 3.0, "loop", {
		**legs(lambda side, i: {"leg": loop(3.0, (0, Z), (0.5, (0, 0, 2 + (i % 2))))}),
		"abdomen": {"rotation": loop(3.0, (0, Z), (0.5, (-2, 0, 0)))},
		"right_fang": {"rotation": loop(3.0, (0, Z), (0.4, (0, 0, 0)), (0.45, (8, -6, 0)), (0.5, Z))},
		"left_fang": {"rotation": loop(3.0, (0, Z), (0.4, (0, 0, 0)), (0.45, (8, 6, 0)), (0.5, Z))},
		"right_palp": {"rotation": loop(3.0, (0, Z), (0.5, (-10, 4, 0)))},
		"left_palp": {"rotation": loop(3.0, (0, (-10, -4, 0)), (0.5, Z))},
		"head": {"rotation": loop(3.0, (0, Z), (0.3, (0, 6, 0)), (0.7, (0, -6, 0)))},
	})

	def walk_legs(side, i):
		a = gait_group(side, i)
		swing = [(0, (0, -14, 0)), (0.25, (0, 0, 12)), (0.5, (0, 14, 0)), (0.75, (0, 0, 0))]
		if not a:
			swing = [(f, v) for f, v in [(0, (0, 14, 0)), (0.25, (0, 0, 0)), (0.5, (0, -14, 0)), (0.75, (0, 0, 12))]]
		lift = [(f, (0, 0, -14 if v[2] else 0)) for f, v in swing]
		return {"leg": loop(0.8, *swing), "shin": loop(0.8, *lift)}
	walk = anim("walk", 0.8, "loop", {
		**legs(walk_legs),
		"body": {"position": loop(0.8, (0, Z), (0.25, (0, 0.5, 0)), (0.5, Z), (0.75, (0, 0.5, 0)))},
		"abdomen": {"rotation": loop(0.8, (0, (0, 4, 0)), (0.5, (0, -4, 0)))},
	})
	sleep = anim("sleep", 4.0, "loop", {
		**legs(lambda side, i: {"leg": loop(4.0, (0, SLEEP_LEG)), "shin": loop(4.0, (0, SLEEP_SHIN))}),
		"body": {"position": loop(4.0, (0, SLEEP_DOWN))},
		"abdomen": {"rotation": loop(4.0, (0, (3, 0, 0)), (0.5, (1, 0, 0)))},
		"head": {"rotation": loop(4.0, (0, (14, 0, 0)))},
	})
	hold = anim("hold", 1.0, "loop", {
		**legs(lambda side, i: {"leg": loop(1.0, (0, (0, -22, 34) if i == 0 else (0, 0, -4))),
								 "shin": loop(1.0, (0, (0, 0, 40) if i == 0 else Z))}),
		"body": {"rotation": loop(1.0, (0, (-14, 0, 0)), (0.5, (-12, 0, 0)))},
		"head": {"rotation": loop(1.0, (0, (-18, 0, 0)), (0.5, (-20, 0, 0)))},
		"right_fang": {"rotation": loop(1.0, (0, (-20, 14, 0)), (0.5, (-24, 16, 0)))},
		"left_fang": {"rotation": loop(1.0, (0, (-20, -14, 0)), (0.5, (-24, -16, 0)))},
	})
	# a blow on her egg: she shifts in her sleep, legs twitching, the abdomen shivering
	stir = anim("stir", 1.0, "once", {
		**legs(lambda side, i: {"leg": [(0, SLEEP_LEG), (0.15 + 0.05 * i, (0, 6, -8)), (0.4 + 0.05 * i, SLEEP_LEG), (1.0, SLEEP_LEG)],
								 "shin": [(0, SLEEP_SHIN), (0.15 + 0.05 * i, (0, 0, 20)), (1.0, SLEEP_SHIN)]}),
		"body": {"position": [(0, SLEEP_DOWN), (0.2, (0, -3.5, 0)), (1.0, SLEEP_DOWN)]},
		"abdomen": {"rotation": [(0, Z), (0.1, (0, 0, 6)), (0.2, (0, 0, -6)), (0.3, (0, 0, 4)), (0.4, (0, 0, -3)), (0.6, Z), (1.0, Z)]},
		"head": {"rotation": [(0, (14, 0, 0)), (0.3, (4, 10, 0)), (1.0, (14, 0, 0))]},
	})
	# woken: she rises off the floor, rears up on her hind legs with the front ones high and the fangs wide, and screams
	wake = anim("wake", 2.0, "once", {
		**legs(lambda side, i: {"leg": [(0, SLEEP_LEG), (0.6, Z), (1.0, (0, -20, 60) if i < 2 else (0, 0, -10)), (1.6, (0, -24, 64) if i < 2 else (0, 0, -10)), (2.0, Z)],
								 "shin": [(0, SLEEP_SHIN), (0.6, Z), (1.0, (0, 0, 50) if i < 2 else Z), (2.0, Z)]}),
		"body": {"position": [(0, SLEEP_DOWN), (0.6, Z), (1.0, (0, 3, 0)), (1.6, (0, 3, 0)), (2.0, Z)],
				 "rotation": [(0, Z), (0.6, Z), (1.0, (-28, 0, 0)), (1.6, (-30, 0, 0)), (2.0, Z)]},
		"head": {"rotation": [(0, (14, 0, 0)), (0.6, Z), (1.0, (-16, 0, 0)), (1.3, (-22, 0, 0)), (2.0, Z)]},
		"right_fang": {"rotation": [(0, Z), (1.0, (-30, 20, 0)), (1.6, (-34, 24, 0)), (2.0, Z)]},
		"left_fang": {"rotation": [(0, Z), (1.0, (-30, -20, 0)), (1.6, (-34, -24, 0)), (2.0, Z)]},
		"abdomen": {"rotation": [(0, Z), (1.0, (18, 0, 0)), (1.6, (20, 0, 0)), (2.0, Z)]},
	})
	# the leap: down into a crouch, then the legs thrown straight as she springs
	leap = anim("leap", 0.9, "once", {
		**legs(lambda side, i: {"leg": [(0, Z), (0.55, (0, 0, -16)), (0.7, (0, 10 if i > 1 else -10, 22)), (0.9, (0, 10 if i > 1 else -10, 20))],
								 "shin": [(0, Z), (0.55, (0, 0, 24)), (0.7, (0, 0, -20)), (0.9, (0, 0, -18))]}),
		"body": {"position": [(0, Z), (0.55, (0, -3, 0)), (0.7, (0, 1, 0)), (0.9, (0, 1, 0))],
				 "rotation": [(0, Z), (0.55, (6, 0, 0)), (0.7, (-12, 0, 0)), (0.9, (-8, 0, 0))]},
	})
	# the landing: slammed down flat, legs splayed out, then gathered up
	land = anim("land", 0.7, "once", {
		**legs(lambda side, i: {"leg": [(0, (0, 0, 20)), (0.08, (0, 0, -24)), (0.3, (0, 0, -20)), (0.7, Z)],
								 "shin": [(0, Z), (0.08, (0, 0, 30)), (0.3, (0, 0, 26)), (0.7, Z)]}),
		"body": {"position": [(0, (0, 1, 0)), (0.08, (0, -4, 0)), (0.3, (0, -3.5, 0)), (0.7, Z)]},
		"abdomen": {"rotation": [(0, Z), (0.08, (10, 0, 0)), (0.2, (-6, 0, 0)), (0.4, Z)]},
	})
	# two leg slams: the front two pairs raised high and brought down (the shockwaves run out from each), body rocking
	def slam_legs(side, i):
		if i >= 2:
			return {"leg": [(0, Z), (0.9, (0, 0, -8)), (1.5, (0, 0, -8)), (1.8, Z)]}
		up, down = (0, -18, 70 - 10 * i), (0, -10, -14)
		return {"leg": [(0, Z), (0.75, up), (0.9, down), (1.2, up), (1.5, down), (1.8, Z)],
				"shin": [(0, Z), (0.75, (0, 0, 44)), (0.9, (0, 0, -6)), (1.2, (0, 0, 44)), (1.5, (0, 0, -6)), (1.8, Z)]}
	slam = anim("slam", 1.8, "once", {
		**legs(slam_legs),
		"body": {"rotation": [(0, Z), (0.75, (-24, 0, 0)), (0.9, (8, 0, 0)), (1.2, (-20, 0, 0)), (1.5, (8, 0, 0)), (1.8, Z)],
				 "position": [(0, Z), (0.75, (0, 2, 0)), (0.9, (0, -1.5, 0)), (1.2, (0, 2, 0)), (1.5, (0, -1.5, 0)), (1.8, Z)]},
	})
	# the spit: she rears her head back, fangs wide, and throws it forward as the silk leaves her
	spit = anim("spit", 0.9, "once", {
		"head": {"rotation": [(0, Z), (0.5, (-26, 0, 0)), (0.62, (16, 0, 0)), (0.9, Z)]},
		"body": {"rotation": [(0, Z), (0.5, (-10, 0, 0)), (0.62, (6, 0, 0)), (0.9, Z)]},
		"right_fang": {"rotation": [(0, Z), (0.5, (-30, 24, 0)), (0.62, (-10, 10, 0)), (0.9, Z)]},
		"left_fang": {"rotation": [(0, Z), (0.5, (-30, -24, 0)), (0.62, (-10, -10, 0)), (0.9, Z)]},
		"abdomen": {"rotation": [(0, Z), (0.5, (12, 0, 0)), (0.62, (-6, 0, 0)), (0.9, Z)]},
	})
	# the grab: a lunge with the front legs reaching, fangs open, then snapped shut on what she caught
	grab = anim("grab", 0.6, "once", {
		**legs(lambda side, i: {"leg": [(0, Z), (0.3, (0, -30, 26) if i == 0 else (0, -8, -6)), (0.45, (0, -22, 34) if i == 0 else (0, 0, -4)), (0.6, (0, -22, 34) if i == 0 else (0, 0, -4))],
								 "shin": [(0, Z), (0.3, (0, 0, -20) if i == 0 else Z), (0.45, (0, 0, 40) if i == 0 else Z), (0.6, (0, 0, 40) if i == 0 else Z)]}),
		"body": {"position": [(0, Z), (0.3, (0, -1, -4)), (0.6, Z)], "rotation": [(0, Z), (0.3, (8, 0, 0)), (0.6, (-14, 0, 0))]},
		"right_fang": {"rotation": [(0, Z), (0.3, (-36, 28, 0)), (0.4, (-20, 14, 0)), (0.6, (-20, 14, 0))]},
		"left_fang": {"rotation": [(0, Z), (0.3, (-36, -28, 0)), (0.4, (-20, -14, 0)), (0.6, (-20, -14, 0))]},
	})
	# her brood spilling off her: the abdomen raised and shaken, the lumps on it heaving
	brood = anim("brood", 1.2, "once", {
		"abdomen": {"rotation": [(0, Z), (0.2, (-16, 0, 0)), (0.35, (-16, 0, 8)), (0.5, (-16, 0, -8)), (0.65, (-16, 0, 8)), (0.8, (-16, 0, -6)), (1.2, Z)]},
		"brood": {"position": [(0, Z), (0.35, (0, 1, 0)), (0.5, (0, -0.5, 0)), (0.65, (0, 1, 0)), (0.8, Z), (1.2, Z)],
				  "scale": [(0, (1, 1, 1)), (0.5, (1.15, 1.15, 1.15)), (0.9, (0.8, 0.8, 0.8)), (1.2, (1, 1, 1))]},
		**legs(lambda side, i: {"leg": [(0, Z), (0.2, (0, 0, -6)), (1.0, (0, 0, -6)), (1.2, Z)]}),
	})
	# death: the legs curl in under her, as a dead spider's do, and she sinks
	death = anim("death", 1.5, "hold", {
		**legs(lambda side, i: {"leg": [(0, Z), (0.3, (0, 0, 20)), (1.5, (0, -LEG_SWEEP[i] * 0.5, -40))],
								 "shin": [(0, Z), (0.3, (0, 0, -20)), (1.5, (0, 0, 70))]}),
		"body": {"position": [(0, Z), (1.5, (0, -4, 0))]},
		"head": {"rotation": [(0, Z), (0.3, (-20, 0, 0)), (1.5, (16, 0, 0))]},
		"right_fang": {"rotation": [(0, Z), (0.3, (-30, 20, 0)), (1.5, Z)]},
		"left_fang": {"rotation": [(0, Z), (0.3, (-30, -20, 0)), (1.5, Z)]},
	})
	return [idle, walk, sleep, hold, stir, wake, leap, land, slam, spit, grab, brood, death]


def eggs():
	saved = dict(kit.EGGS)
	kit.EGGS.clear()
	kit.EGGS.update(EGG)
	kit.eggs()
	kit.EGGS.clear()
	kit.EGGS.update(saved)


def main():
	blocks()
	b = build()
	model = b.project("brood_queen")
	add_animations(model, animations(), bone_uuids_of(model))
	os.makedirs(kit.ART, exist_ok=True)
	art = os.path.join(kit.ART, "brood_queen.bbmodel")
	with open(art, "w", encoding="utf-8", newline="\n") as f:
		json.dump(model, f)
	print("wrote kingdoms/art/brood_queen.bbmodel")
	subprocess.check_call([sys.executable, "-I", os.path.join(ROOT, "tools", "bbmodel_geckolib.py"), art, ASSETS, "brood_queen"])
	eggs()


if __name__ == "__main__":
	main()
