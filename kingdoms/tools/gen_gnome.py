"""
The gnome's Blockbench project, kingdoms/art/gnome.bbmodel: our own cubes, texture and animations, all made here (Andy,
2026-11-19: "can we give gnomes real models?"). The gnomes in the pack were baby zombies in player heads placed by ATi
Structures and Grim Kingdoms (both All Rights Reserved), so nothing of theirs is used, only the idea. Then the game's
GeckoLib files are written from it (tools/bbmodel_geckolib.py).

The ground's own small folk (Lore.md, "The gnomes"): knee-high, a big head under a tall red cap whose tip flops back, a
white beard to the belt, a round nose, a blue tunic over a belly, brown breeches and boots with the toes turned up. A
stick in the right hand, and a plank shield on the left arm that only some carry (GnomeRenderer hides it). Real size: 14.5
units to the crown, 21 to the cap's tip.

Bone names are the game's (head, body, right_arm, left_leg...) so a lost limb hides (Powers' Dismembered).

Blockbench space: y up, feet at 0, the front at -z, the model's right at +x.

Run from the project root: python3 -I kingdoms/tools/gen_gnome.py [--preview <dir>]   (needs Pillow)
"""
import json
import os
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import gen_tomb_undead as kit  # noqa: E402
from gen_tomb_undead import Builder, anim, add_animations, bone_uuids_of, mirror, shade  # noqa: E402

ROOT = kit.ROOT
ASSETS = os.path.join(ROOT, "kingdoms", "src", "main", "resources", "assets", "jaymoskingdoms")
Z = (0, 0, 0)

# ---------------------------------------------------------------- colours (ours)

COLOURS = {"flesh": (230, 178, 146), "nose": (214, 120, 104), "fur": (236, 232, 222), "cloth": (52, 84, 150),
		   "breeches": (96, 72, 52), "leather": (66, 44, 30), "cap": (178, 36, 34), "wood": (122, 88, 52), "iron": (110, 114, 118)}
EYE, BROW, CHEEK = (34, 30, 30), (244, 242, 236), (226, 140, 124)
EGGS = {"gnome": ((52, 84, 150), (178, 36, 34))}

HIP_Y, SHOULDER_Y, NECK_Y, CROWN_Y = 4.0, 8.5, 9.0, 14.5


def face(img, r):
	"""The face (6 by 6): beady eyes under bushy white brows knitted in a scowl, rosy cheeks. The beard hides the mouth."""
	x0, y0, x1, y1 = r
	for ex, inner in ((x0 + 1, 1), (x1 - 2, -1)):
		img.putpixel((ex, y0 + 2), EYE + (255,))
		img.putpixel((ex, y0 + 1), BROW + (255,))
		img.putpixel((ex + inner, y0 + 1), BROW + (255,))
		img.putpixel((ex - inner, y0 + 0), BROW + (255,))
		img.putpixel((ex, y0 + 3), CHEEK + (255,))


def plank_face(img, r):
	"""The shield's boards and its iron rim."""
	x0, y0, x1, y1 = r
	for y in range(y0, y1):
		for x in range(x0, x1):
			if y in (y0, y1 - 1) or x in (x0, x1 - 1):
				img.putpixel((x, y), shade(COLOURS["iron"], 1.1) + (255,))
			elif (x - x0) % 2 == 0:
				img.putpixel((x, y), shade(COLOURS["wood"], 0.8) + (255,))


def build(seed=31):
	kit.PALETTE.update(COLOURS)
	b = Builder(64, 64, seed)
	b.bone("root", None, Z)
	b.bone("waist", "root", (0, HIP_Y, 0))
	b.bone("body", "waist", (0, HIP_Y, 0))
	b.cube("body", (-3, HIP_Y, -2), (3, NECK_Y, 2), "cloth")
	b.cube("body", (-2.5, HIP_Y + 0.5, -2.7), (2.5, HIP_Y + 3.5, -2), "cloth")  # the belly
	b.cube("body", (-3.1, HIP_Y + 0.6, -2.8), (3.1, HIP_Y + 1.3, 2.1), "leather")  # the belt
	b.cube("body", (-0.6, HIP_Y + 0.5, -2.95), (0.6, HIP_Y + 1.4, -2.75), "iron")  # its buckle
	b.bone("head", "body", (0, NECK_Y, 0))
	b.cube("head", (-3, NECK_Y, -3), (3, CROWN_Y, 3), "flesh", faces_paint={"north": face})
	b.cube("head", (-0.8, NECK_Y + 1.8, -4), (0.8, NECK_Y + 3.3, -3), "nose")
	# the sideburns and the beard, its tip on a bone of its own so it swings
	b.cube("head", (-3.2, NECK_Y + 0.5, -3.1), (-2.6, NECK_Y + 3.5, 0.5), "fur")
	b.cube("head", (2.6, NECK_Y + 0.5, -3.1), (3.2, NECK_Y + 3.5, 0.5), "fur")
	b.cube("head", (-3.15, NECK_Y + 1, 0.5), (3.15, CROWN_Y, 3.15), "fur")  # white hair round the back under the cap
	b.bone("beard", "head", (0, NECK_Y + 2, -3.2))
	b.cube("beard", (-3.1, NECK_Y - 1, -3.6), (3.1, NECK_Y + 2, -2.8), "fur")
	b.bone("beard_tip", "beard", (0, NECK_Y - 1, -3.2))
	b.cube("beard_tip", (-2, NECK_Y - 3.6, -3.5), (2, NECK_Y - 1, -2.7), "fur")
	mirror(b, "ear", "head", (3, NECK_Y + 3, 0), [((3, NECK_Y + 2.2, -0.6), (4.2, NECK_Y + 3.8, 0.4), "flesh")], (0, -15, 0))
	# the cap: a brim, a crown, and a tip that flops back
	b.bone("hat", "head", (0, CROWN_Y, 0), (-6, 0, 0))
	b.cube("hat", (-3.4, CROWN_Y - 0.2, -3.4), (3.4, CROWN_Y + 1, 3.4), "cap")
	b.cube("hat", (-2.6, CROWN_Y + 1, -2.6), (2.6, CROWN_Y + 3, 2.6), "cap")
	b.bone("hat_top", "hat", (0, CROWN_Y + 3, 0), (-12, 0, 0))
	b.cube("hat_top", (-1.7, CROWN_Y + 3, -1.7), (1.7, CROWN_Y + 4.5, 1.7), "cap")
	b.bone("hat_tip", "hat_top", (0, CROWN_Y + 4.5, 0), (-30, 0, 0))
	b.cube("hat_tip", (-0.9, CROWN_Y + 4.5, -0.9), (0.9, CROWN_Y + 6.3, 0.9), "cap")
	# arms: a sleeve and a hand
	mirror(b, "arm", "body", (3.6, SHOULDER_Y, 0), [((3, SHOULDER_Y - 3, -1), (4.8, SHOULDER_Y + 0.5, 1), "cloth"),
												   ((3.1, SHOULDER_Y - 4.2, -0.9), (4.7, SHOULDER_Y - 3, 0.9), "flesh")])
	# the stick in the right fist, held forward; the plank shield strapped on the left forearm
	b.bone("stick", "right_arm", (3.9, SHOULDER_Y - 3.6, 0))
	b.cube("stick", (3.5, SHOULDER_Y - 4.0, -7.0), (4.3, SHOULDER_Y - 3.2, 0.6), "wood")
	b.bone("shield", "left_arm", (-4.9, SHOULDER_Y - 2, 0))
	b.cube("shield", (-5.6, SHOULDER_Y - 5, -2.5), (-4.8, SHOULDER_Y + 0.5, 2.5), "wood", faces_paint={"west": plank_face})
	# legs: breeches and boots with the toes turned up
	mirror(b, "leg", "waist", (1.3, HIP_Y, 0), [((0.3, 1.2, -1), (2.3, HIP_Y, 1), "breeches"),
											   ((0.1, 0, -1.6), (2.5, 1.4, 1.1), "leather"),
											   ((0.6, 0.6, -2.4), (2.0, 1.6, -1.6), "leather")])
	return b


# ---------------------------------------------------------------- animations

def loop(length, *keys):
	out = [(round(length * f, 4), v) for f, v in keys]
	return out + [(length, keys[0][1])]


def both(name, right, left=None):
	if left is None:
		left = [(t, (v[0], -v[1], -v[2])) for t, v in right]
	return {f"right_{name}": {"rotation": right}, f"left_{name}": {"rotation": left}}


# Key values are the game's way, added to a bone's rest: x: - swings a limb forward, lifts the head; + the other way.
def animations():
	idle = anim("idle", 3.0, "loop", {
		**both("arm", loop(3.0, (0, (0, 0, 3)), (0.5, (-4, 0, 6)))),
		"head": {"rotation": loop(3.0, (0, (0, -10, 0)), (0.35, (-3, 12, 2)), (0.7, (2, 0, -2)))},
		"beard_tip": {"rotation": loop(3.0, (0, Z), (0.5, (0, 0, 6)))},
		"hat_tip": {"rotation": loop(3.0, (0, Z), (0.5, (6, 0, 8)))},
		"root": {"position": loop(3.0, (0, Z), (0.5, (0, -0.15, 0)))},
	})
	# a stumpy, busy trot: the whole body rocks side to side and the cap tip swings
	walk = anim("walk", 0.6, "loop", {
		"right_leg": {"rotation": loop(0.6, (0, (-32, 0, 0)), (0.5, (28, 0, 0)))},
		"left_leg": {"rotation": loop(0.6, (0, (28, 0, 0)), (0.5, (-32, 0, 0)))},
		"right_arm": {"rotation": loop(0.6, (0, (26, 0, 4)), (0.5, (-26, 0, 4)))},
		"left_arm": {"rotation": loop(0.6, (0, (-26, 0, -4)), (0.5, (26, 0, -4)))},
		"body": {"rotation": loop(0.6, (0, (0, 0, 5)), (0.5, (0, 0, -5)))},
		"hat_tip": {"rotation": loop(0.6, (0, (0, 0, -12)), (0.5, (0, 0, 12)))},
		"beard_tip": {"rotation": loop(0.6, (0, (0, 0, 6)), (0.5, (0, 0, -6)))},
		"root": {"position": loop(0.6, (0, Z), (0.25, (0, 0.5, 0)), (0.5, Z), (0.75, (0, 0.5, 0)))},
	})
	# charging: bent forward, stick up, little legs going hard
	run = anim("run", 0.36, "loop", {
		"right_leg": {"rotation": loop(0.36, (0, (-50, 0, 0)), (0.5, (40, 0, 0)))},
		"left_leg": {"rotation": loop(0.36, (0, (40, 0, 0)), (0.5, (-50, 0, 0)))},
		"right_arm": {"rotation": loop(0.36, (0, (-120, 0, 10)), (0.5, (-110, 0, 10)))},
		"left_arm": {"rotation": loop(0.36, (0, (-40, 0, -10)), (0.5, (-30, 0, -10)))},
		"body": {"rotation": loop(0.36, (0, (18, 0, 4)), (0.5, (18, 0, -4)))},
		"head": {"rotation": loop(0.36, (0, (-16, 0, 0)), (0.5, (-14, 0, 0)))},
		"hat_tip": {"rotation": loop(0.36, (0, (30, 0, -10)), (0.5, (30, 0, 10)))},
		"root": {"position": loop(0.36, (0, Z), (0.3, (0, 1.2, 0)), (0.6, Z))},
	})
	# the bonk: the stick up over the head and brought down hard
	bonk = anim("bonk", 0.5, "once", {
		"right_arm": {"rotation": [(0, Z), (0.18, (-170, 0, 10)), (0.28, (-40, 0, 0)), (0.5, Z)]},
		"body": {"rotation": [(0, Z), (0.18, (-12, 0, 0)), (0.28, (22, 0, 0)), (0.5, Z)]},
		"head": {"rotation": [(0, Z), (0.18, (-10, 0, 0)), (0.28, (12, 0, 0)), (0.5, Z)]},
		"left_arm": {"rotation": [(0, Z), (0.18, (-50, 0, -20)), (0.5, Z)]},
		"hat_tip": {"rotation": [(0, Z), (0.28, (40, 0, 0)), (0.5, Z)]},
	})
	# down on its knees in the beds, patting the earth and working the bone meal in
	garden = anim("garden", 2.0, "loop", {
		**both("leg", loop(2.0, (0, (-80, 0, 8)), (0.5, (-80, 0, 8)))),
		"body": {"rotation": loop(2.0, (0, (34, 0, 0)), (0.5, (38, 0, 0)))},
		"head": {"rotation": loop(2.0, (0, (-10, -8, 0)), (0.5, (-6, 8, 0)))},
		"right_arm": {"rotation": loop(2.0, (0, (-60, 0, 4)), (0.25, (-30, 0, 4)), (0.5, (-60, 0, 4)), (0.75, (-30, 0, 4)))},
		"left_arm": {"rotation": loop(2.0, (0, (-30, 0, -4)), (0.25, (-60, 0, -4)), (0.5, (-30, 0, -4)), (0.75, (-60, 0, -4)))},
		"root": {"position": loop(2.0, (0, (0, -2.6, 0)), (0.5, (0, -2.7, 0)))},
	})
	# going down into the ground: crouch, claw at the earth, sink out of sight (the cap last)
	dig = anim("dig", 1.5, "hold", {
		**both("arm", [(0, Z), (0.2, (-100, 0, 20)), (0.4, (-20, 0, 10)), (0.6, (-100, 0, 20)), (0.8, (-20, 0, 10)), (1.0, (-100, 0, 20))]),
		**both("leg", [(0, Z), (0.3, (-60, 0, 8))]),
		"body": {"rotation": [(0, Z), (0.3, (30, 0, 0))]},
		"root": {"position": [(0, Z), (0.3, (0, -2, 0)), (1.5, (0, -24, 0))]},
	})
	hurt = anim("hurt", 0.3, "once", {
		"body": {"rotation": [(0, Z), (0.08, (-12, 8, 0)), (0.3, Z)]},
		"head": {"rotation": [(0, Z), (0.08, (-24, -16, 6)), (0.3, Z)]},
		"hat_tip": {"rotation": [(0, Z), (0.08, (-30, 0, 20)), (0.3, Z)]},
	})
	return [idle, walk, run, bonk, garden, dig, hurt]


PREVIEWS = [("idle", 0.0), ("walk", 0.15), ("run", 0.1), ("bonk", 0.18), ("garden", 0.5), ("dig", 0.6)]


def eggs():
	saved = dict(kit.EGGS)
	kit.EGGS.clear()
	kit.EGGS.update(EGGS)
	kit.eggs()
	kit.EGGS.clear()
	kit.EGGS.update(saved)


def main(argv):
	saved = dict(kit.PALETTE)
	model = build().project("gnome")
	add_animations(model, animations(), bone_uuids_of(model))
	art = os.path.join(kit.ART, "gnome.bbmodel")
	with open(art, "w", encoding="utf-8", newline="\n") as f:
		json.dump(model, f)
	print("wrote kingdoms/art/gnome.bbmodel")
	subprocess.check_call([sys.executable, "-I", os.path.join(ROOT, "tools", "bbmodel_geckolib.py"), art, ASSETS, "gnome"])
	kit.PALETTE.clear()
	kit.PALETTE.update(saved)
	eggs()
	if "--preview" in argv:
		out = argv[argv.index("--preview") + 1]
		os.makedirs(out, exist_ok=True)
		subprocess.check_call([sys.executable, "-I", os.path.join(ROOT, "tools", "bbmodel_bake.py"), art, out, "gnome"])
		for name, t in PREVIEWS:
			png = os.path.join(out, f"gnome-{name}-{t}.png")
			subprocess.check_call([sys.executable, "-I", os.path.join(ROOT, "tools", "bbmodel_preview.py"), out, "gnome", png, name, str(t)])


if __name__ == "__main__":
	main(sys.argv[1:])
