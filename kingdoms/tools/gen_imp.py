"""
The Nether imp's Blockbench project, kingdoms/art/imp.bbmodel: our own cubes, texture and animations, all made here
(docs/Imp-Model-Spec.md is the spec they follow), with the zombified imp's skin painted on the same layout. Then the
game's GeckoLib files are written from it (tools/bbmodel_geckolib.py) and the zombified skin beside the plain one.

A small flying demon of a piglin-like make: a wide head with a snout, tusks, ears and two short horns, a hunched body,
jointed arms and legs (elbows and knees), two-part bat wings, a three-part tail with a spade tip, and the fireball it
gathers over its head (a bone the renderer hides unless it is charging).

Blockbench space: y up, feet at 0, the front at -z, the model's right at +x. Rotations as Blockbench shows them: +x
swings a hanging limb forward and up (and tips an upright part back), +z lifts the right arm (at +x) out to the side.

Run from the project root: py -3 -I kingdoms/tools/gen_imp.py [--preview <dir>]   (needs Pillow)
--preview also renders every animation's key poses into <dir> (tools/bbmodel_bake.py + tools/bbmodel_preview.py).
"""
import json
import math
import os
import random
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import gen_tomb_undead as kit  # noqa: E402
from gen_tomb_undead import Builder, anim, add_animations, bone_uuids_of, mirror, shade  # noqa: E402

ROOT = kit.ROOT
ASSETS = os.path.join(ROOT, "kingdoms", "src", "main", "resources", "assets", "jaymoskingdoms")
Z = (0, 0, 0)

# ---------------------------------------------------------------- colours (ours)

# The living imp: ember-red hide, darker limbs' ends, a paler belly and snout, soot-black wing fingers, a dull red
# membrane, bone tusks and horns, a scorched loincloth. The zombified imp: the same hide gone grey-green, bone showing.
LIVING = {
	"hide": (150, 58, 44), "hide_dark": (112, 40, 34), "belly": (184, 98, 70), "snout": (196, 112, 86),
	"wing_bone": (52, 30, 30), "membrane": (124, 36, 40), "tusk": (226, 214, 180), "horn": (64, 48, 44),
	"claw": (40, 30, 28), "hoof": (48, 36, 32), "loin": (72, 52, 36), "fire": (255, 170, 40), "fire_core": (255, 240, 170),
}
ZOMBIFIED = {
	"hide": (122, 136, 108), "hide_dark": (88, 102, 82), "belly": (150, 150, 118), "snout": (158, 140, 122),
	"wing_bone": (58, 58, 50), "membrane": (92, 96, 78), "tusk": (214, 206, 176), "horn": (70, 64, 56),
	"claw": (44, 42, 36), "hoof": (52, 48, 40), "loin": (60, 50, 40), "fire": (255, 170, 40), "fire_core": (255, 240, 170),
}
EYE, EYE_DEAD = (255, 214, 64), (236, 240, 226)
ROT, BONE = (168, 82, 96), (212, 204, 170)  # the zombified imp's raw flesh and bare bone
EGGS = {"imp": ((150, 58, 44), (255, 170, 40)), "zombified_imp": ((122, 136, 108), (168, 82, 96))}

# ---------------------------------------------------------------- the model

# Sizes in Blockbench units (16 to a block). The imp stands 22 high to the crown, 25 to the horn tips.
HIP_Y, SHOULDER_Y, NECK_Y = 8.0, 14.5, 15.0
WING_SPAN = 8.0  # each of a wing's two parts
TAIL = [(1.6, 3.5), (1.2, 3.5), (0.9, 3.0)]  # width, length of each part
FIRE = 6.0  # the fireball's side at full size


def face(eye):
	"""The face on the head's front: two eyes under a heavy brow."""
	def fx(img, r):
		x0, y0, x1, y1 = r
		for x in range(x0, x1):
			img.putpixel((x, y0 + 1), shade(img.getpixel((x, y0 + 1))[:3], 0.7) + (255,))
		for ex in (x0 + 1, x1 - 3):
			for dx in range(2):
				img.putpixel((ex + dx, y0 + 2), eye + (255,))
			img.putpixel((ex + (1 if ex == x0 + 1 else 0), y0 + 2), shade(eye, 0.45) + (255,))
	return fx


def nostrils(img, r):
	x0, y0, x1, y1 = r
	for x in (x0 + 1, x1 - 2):
		img.putpixel((x, y0 + 1), (60, 24, 24, 255))


def veins(img, r):
	"""A wing membrane: darker ribs fanning down from the wing's finger, a ragged lower edge."""
	x0, y0, x1, y1 = r
	for x in range(x0, x1):
		for y in range(y0, y1):
			c = img.getpixel((x, y))
			if (x - x0) % 3 == 0:
				img.putpixel((x, y), shade(c[:3], 0.72) + (255,))
			if y == y1 - 1 and (x - x0) % 3 == 1:
				img.putpixel((x, y), (0, 0, 0, 0))


def glow(img, r):
	"""The fireball: a hot core fading to orange at the edges."""
	x0, y0, x1, y1 = r
	cx, cy = (x0 + x1 - 1) / 2, (y0 + y1 - 1) / 2
	for x in range(x0, x1):
		for y in range(y0, y1):
			d = max(abs(x - cx), abs(y - cy)) / max(1, (x1 - x0) / 2)
			a, b = kit.PALETTE["fire_core"], kit.PALETTE["fire"]
			k = min(1.0, d * 1.15)
			c = tuple(int(a[i] + (b[i] - a[i]) * k) for i in range(3))
			if d > 0.8 and (x + y) % 2:
				c = (232, 96, 28)
			img.putpixel((x, y), c + (255,))


def build(palette, eye, seed=23):
	kit.PALETTE.update(palette)
	b = Builder(128, 64, seed)
	b.bone("root", None, (0, 0, 0))
	b.bone("waist", "root", (0, HIP_Y, 0))
	b.cube("waist", (-3, HIP_Y - 1, -2), (3, HIP_Y + 1.5, 2), "loin", 0.15)
	b.cube("waist", (-1.5, HIP_Y - 3.5, -2.3), (1.5, HIP_Y - 1, -2.0), "loin")
	b.bone("body", "waist", (0, HIP_Y + 1, 0))
	b.cube("body", (-3.5, HIP_Y + 1, -2.5), (3.5, NECK_Y, 2.5), "hide")
	b.cube("body", (-2.5, HIP_Y + 1.5, -2.8), (2.5, NECK_Y - 1.5, -2.5), "belly")
	b.bone("head", "body", (0, NECK_Y, 0))
	b.cube("head", (-4, NECK_Y, -4), (4, NECK_Y + 7, 3), "hide", faces_paint={"north": face(eye)})
	b.cube("head", (-2, NECK_Y + 1, -5.5), (2, NECK_Y + 4, -4), "snout", faces_paint={"north": nostrils})
	mirror(b, "tusk", "head", (2.2, NECK_Y + 1.5, -4.6), [((1.8, NECK_Y + 1.2, -5.0), (2.6, NECK_Y + 3.0, -4.2), "tusk")], (0, 0, -12))
	mirror(b, "ear", "head", (4, NECK_Y + 4.5, 0), [((4, NECK_Y + 2.5, -0.5), (7.5, NECK_Y + 5.5, 0.5), "hide_dark")], (0, -25, -20))
	mirror(b, "horn", "head", (2.5, NECK_Y + 7, -1), [((1.8, NECK_Y + 7, -1.7), (3.2, NECK_Y + 9, -0.3), "horn"),
													  ((2.1, NECK_Y + 9, -1.4), (2.9, NECK_Y + 10.5, -0.6), "horn")], (-15, 0, -14))
	mirror(b, "arm", "body", (4.5, SHOULDER_Y, 0), [((3.5, SHOULDER_Y - 5, -1), (5.5, SHOULDER_Y + 0.5, 1), "hide")])
	mirror(b, "forearm", ("arm",), (4.5, SHOULDER_Y - 5, 0), [((3.6, SHOULDER_Y - 9.5, -0.9), (5.4, SHOULDER_Y - 5, 0.9), "hide_dark"),
															   ((3.6, SHOULDER_Y - 10.7, -1.1), (5.4, SHOULDER_Y - 9.5, 0.3), "claw")])
	mirror(b, "leg", "waist", (1.8, HIP_Y, 0), [((0.6, HIP_Y - 4, -1.2), (3.0, HIP_Y, 1.2), "hide")])
	mirror(b, "shin", ("leg",), (1.8, HIP_Y - 4, 0), [((0.8, 0, -1), (2.8, HIP_Y - 4, 1), "hide_dark"),
													  ((0.6, 0, -2.2), (3.0, 1, 1.2), "hoof")])
	# the wings: a finger along the top and the membrane hung under it, each wing in two parts (the outer folds on the inner)
	wy, wz = NECK_Y - 1.0, 2.5
	mirror(b, "wing", "body", (1.5, wy, wz), [((1.5, wy - 0.5, wz), (1.5 + WING_SPAN, wy + 0.5, wz + 0.8), "wing_bone"),
											  ((1.5, wy - 7.0, wz + 0.35), (1.5 + WING_SPAN, wy - 0.5, wz + 0.45), "membrane", 0.0, Z, None,
											   {"north": veins, "south": veins})])
	mirror(b, "wing_tip", ("wing",), (1.5 + WING_SPAN, wy, wz + 0.4), [
		((1.5 + WING_SPAN, wy - 0.5, wz), (1.5 + 2 * WING_SPAN, wy + 0.5, wz + 0.8), "wing_bone"),
		((1.5 + WING_SPAN, wy - 5.5, wz + 0.35), (1.5 + 2 * WING_SPAN, wy - 0.5, wz + 0.45), "membrane", 0.0, Z, None,
		 {"north": veins, "south": veins})])
	z, parent = 2.0, "waist"
	for i, (w, length) in enumerate(TAIL):
		b.bone(f"tail_{i}", parent, (0, HIP_Y, z), (35 if i == 0 else -22, 0, 0))
		b.cube(f"tail_{i}", (-w / 2, HIP_Y - w / 2, z), (w / 2, HIP_Y + w / 2, z + length), "hide_dark")
		z, parent = z + length, f"tail_{i}"
	b.cube("tail_2", (-1.5, HIP_Y - 0.3, z - 0.4), (1.5, HIP_Y + 0.3, z + 2.2), "horn")
	# the fireball it gathers over its head (hidden unless it is charging: ImpRenderer)
	fy = NECK_Y + 14
	b.bone("fireball", "root", (0, fy, -1))
	b.cube("fireball", (-FIRE / 2, fy - FIRE / 2, -1 - FIRE / 2), (FIRE / 2, fy + FIRE / 2, -1 + FIRE / 2), "fire",
		   faces_paint={f: glow for f in ("up", "down", "north", "south", "east", "west")})
	return b


def zombify(img):
	"""Rot on the zombified skin: patches of raw flesh and bare bone over the hide, the same on every run."""
	rng = random.Random(41)
	hide = {ZOMBIFIED[k] for k in ("hide", "hide_dark", "belly")}
	px = img.load()
	for cy in range(0, img.height, 3):
		for cx in range(0, img.width, 3):
			roll = rng.random()
			if roll > 0.16:
				continue
			for y in range(cy, min(img.height, cy + 3)):
				for x in range(cx, min(img.width, cx + 3)):
					r, g, b, a = px[x, y]
					if a == 0 or not any(abs(r - h[0]) < 40 and abs(g - h[1]) < 40 and abs(b - h[2]) < 40 for h in hide):
						continue
					px[x, y] = shade(BONE if roll < 0.05 else ROT, 0.85 + rng.random() * 0.25) + (255,)


# ---------------------------------------------------------------- animations

def loop(length, *keys):
	"""Keys given as (fraction of the loop, value); the first is repeated at the end so the loop closes."""
	out = [(round(length * f, 4), v) for f, v in keys]
	return out + [(length, keys[0][1])]


def both(name, right, left=None):
	"""A pair of bones: the left one mirrors the right (y and z turned round) unless given."""
	if left is None:
		left = [(t, (v[0], -v[1], -v[2])) for t, v in right]
	return {f"right_{name}": {"rotation": right}, f"left_{name}": {"rotation": left}}


def flap(length, up, down, fold=0.0, beats=1):
	"""Wingbeats: the inner part leads, the tip follows a little later and bends on the upstroke."""
	inner, tip = [], []
	for i in range(beats):
		f0 = i / beats
		inner += [(f0, (0, fold, up)), (f0 + 0.45 / beats, (0, fold, down))]
		tip += [(f0, (0, 0, up * 0.3)), (f0 + 0.2 / beats, (0, 0, up * 0.9)), (f0 + 0.6 / beats, (0, 0, down * 0.7))]
	return {**both("wing", loop(length, *inner)), **both("wing_tip", loop(length, *tip))}


def tail(length, sway, lift=0):
	tracks = {}
	for i in range(len(TAIL)):
		lag = 0.14 * i
		tracks[f"tail_{i}"] = {"rotation": loop(length, *[(k, (lift if i == 0 else 0, sway * math.sin((k - lag) * 2 * math.pi), 0))
														  for k in (0, 0.25, 0.5, 0.75)])}
	return tracks


# Key values are the game's way, added to a bone's resting angle (every animated bone rests at 0, the tail apart):
# x: - swings a limb forward, lifts the head, leans the body back; + the other way.
# y: + sweeps the right wing back. z: + lifts the right arm or wing out and up. The left side mirrors y and z (both()).
STAND = {"arm": (-6, 0, 8), "forearm": (-25, 0, 0), "leg": (-16, 0, 0), "shin": (26, 0, 0), "wing": (0, 58, -24),
		 "wing_tip": (0, 104, 0), "body": (10, 0, 0), "head": (-8, 0, 0), "waist": Z}
HOVER = {"arm": (-15, 0, 18), "forearm": (-55, 0, 0), "leg": (-38, 0, 4), "shin": (72, 0, 0), "wing": (0, 0, 10),
		 "wing_tip": Z, "body": (6, 0, 0), "head": (-24, 0, 0), "waist": (20, 0, 0), "ear": Z, "tail_0": (-20, 0, 0)}


def add(a, b):
	return tuple(a[i] + b[i] for i in range(3))


def animations():
	S, H = STAND, HOVER
	idle = anim("idle", 3.0, "loop", {
		**both("wing", loop(3.0, (0, S["wing"]), (0.5, add(S["wing"], (0, -5, 4))))),
		**both("wing_tip", loop(3.0, (0, S["wing_tip"]), (0.5, add(S["wing_tip"], (0, -8, 0))))),
		**both("arm", loop(3.0, (0, S["arm"]), (0.5, add(S["arm"], (-4, 0, 3))))),
		**both("forearm", loop(3.0, (0, S["forearm"]), (0.5, add(S["forearm"], (-8, 0, 0))))),
		**both("leg", loop(3.0, (0, S["leg"]), (0.5, add(S["leg"], (-3, 0, 0))))),
		**both("shin", loop(3.0, (0, S["shin"]), (0.5, add(S["shin"], (5, 0, 0))))),
		**both("ear", loop(3.0, (0, Z), (0.42, Z), (0.47, (0, 0, 16)), (0.52, Z), (0.57, (0, 0, 12)), (0.62, Z))),
		**tail(3.0, 12),
		"body": {"rotation": loop(3.0, (0, S["body"]), (0.5, add(S["body"], (3, 0, 0))))},
		"head": {"rotation": loop(3.0, (0, (-8, -10, 0)), (0.3, (-4, 12, 3)), (0.7, (-10, -4, -3)))},
		"root": {"position": loop(3.0, (0, Z), (0.5, (0, -0.3, 0)))},
	})
	walk = anim("walk", 0.6, "loop", {
		"right_leg": {"rotation": loop(0.6, (0, (-42, 0, 0)), (0.5, (20, 0, 0)))},
		"left_leg": {"rotation": loop(0.6, (0, (20, 0, 0)), (0.5, (-42, 0, 0)))},
		"right_shin": {"rotation": loop(0.6, (0, (14, 0, 0)), (0.25, (22, 0, 0)), (0.5, (30, 0, 0)), (0.75, (64, 0, 0)))},
		"left_shin": {"rotation": loop(0.6, (0, (30, 0, 0)), (0.25, (64, 0, 0)), (0.5, (14, 0, 0)), (0.75, (22, 0, 0)))},
		"right_arm": {"rotation": loop(0.6, (0, (26, 0, 8)), (0.5, (-32, 0, 8)))},
		"left_arm": {"rotation": loop(0.6, (0, (-32, 0, -8)), (0.5, (26, 0, -8)))},
		**both("forearm", loop(0.6, (0, (-24, 0, 0)), (0.5, (-40, 0, 0))), loop(0.6, (0, (-40, 0, 0)), (0.5, (-24, 0, 0)))),
		**both("wing", loop(0.6, (0, add(S["wing"], (0, -6, 6))), (0.5, S["wing"]))),
		**both("wing_tip", loop(0.6, (0, add(S["wing_tip"], (0, -10, 0))), (0.5, S["wing_tip"]))),
		**both("ear", loop(0.6, (0, (0, 0, 6)), (0.5, (0, 0, -6)))),
		**tail(0.6, 16),
		"waist": {"rotation": loop(0.6, (0, (0, 7, 0)), (0.5, (0, -7, 0)))},
		"body": {"rotation": loop(0.6, (0, (16, -6, 0)), (0.5, (16, 6, 0)))},
		"head": {"rotation": loop(0.6, (0, (-12, 0, 3)), (0.5, (-12, 0, -3)))},
		"root": {"position": loop(0.6, (0, Z), (0.25, (0, 0.7, 0)), (0.5, Z), (0.75, (0, 0.7, 0)))},
	})
	fly = anim("fly", 0.5, "loop", {
		**flap(0.5, 46, -38),
		**both("leg", loop(0.5, (0, H["leg"]), (0.5, add(H["leg"], (-8, 0, 0))))),
		"right_shin": {"rotation": loop(0.5, (0, H["shin"]), (0.5, add(H["shin"], (12, 0, 0))))},
		"left_shin": {"rotation": loop(0.5, (0, add(H["shin"], (10, 0, 0))), (0.5, add(H["shin"], (-4, 0, 0))))},
		**both("arm", loop(0.5, (0, H["arm"]), (0.5, add(H["arm"], (-8, 0, 6))))),
		**both("forearm", loop(0.5, (0, H["forearm"]), (0.5, add(H["forearm"], (-10, 0, 0))))),
		**both("ear", loop(0.5, (0, (0, 0, -8)), (0.5, (0, 0, 8)))),
		**tail(0.5, 9, H["tail_0"][0]),
		"waist": {"rotation": loop(0.5, (0, H["waist"]), (0.5, add(H["waist"], (4, 0, 0))))},
		"body": {"rotation": loop(0.5, (0, H["body"]), (0.45, add(H["body"], (5, 0, 0))))},
		"head": {"rotation": loop(0.5, (0, H["head"]), (0.45, add(H["head"], (-4, 0, 0))))},
		"root": {"position": loop(0.5, (0, (0, 0.9, 0)), (0.45, (0, -0.9, 0)))},
	})
	# the basic swing: the right claws drawn up and back, then raked down across the body; the left arm thrown back
	swing = anim("swing", 0.5, "once", {
		"right_arm": {"rotation": [(0, H["arm"]), (0.14, (-150, 0, 44)), (0.26, (-48, 0, -30)), (0.5, H["arm"])]},
		"right_forearm": {"rotation": [(0, H["forearm"]), (0.14, (-80, 0, 0)), (0.26, (-8, 0, 0)), (0.5, H["forearm"])]},
		"left_arm": {"rotation": [(0, (-15, 0, -18)), (0.14, (36, 0, -30)), (0.26, (14, 0, -24)), (0.5, (-15, 0, -18))]},
		"body": {"rotation": [(0, H["body"]), (0.14, (-8, -26, 0)), (0.26, (22, 30, 0)), (0.5, H["body"])]},
		"head": {"rotation": [(0, H["head"]), (0.14, (-28, 18, 0)), (0.26, (-10, -20, 0)), (0.5, H["head"])]},
	})
	# the swoop: wings folded down along the body, both arms reaching ahead, legs trailing, the body laid forward; then
	# both claws raked down and the wings thrown wide to brake
	swoop = anim("swoop", 1.0, "once", {
		**both("wing", [(0, H["wing"]), (0.15, (0, 18, -52)), (0.6, (0, 22, -56)), (0.72, (0, -12, 48)), (0.86, (0, 0, -30)), (1.0, H["wing"])]),
		**both("wing_tip", [(0, Z), (0.15, (0, 30, -22)), (0.6, (0, 34, -24)), (0.72, (0, -8, 22)), (0.86, (0, 0, -20)), (1.0, Z)]),
		**both("arm", [(0, H["arm"]), (0.15, (-160, 0, 12)), (0.6, (-166, 0, 8)), (0.72, (-56, 0, -14)), (1.0, H["arm"])]),
		**both("forearm", [(0, H["forearm"]), (0.15, (-12, 0, 0)), (0.6, (-8, 0, 0)), (0.72, (-44, 0, 0)), (1.0, H["forearm"])]),
		**both("leg", [(0, H["leg"]), (0.15, (28, 0, 6)), (0.6, (32, 0, 6)), (0.72, (-50, 0, 4)), (1.0, H["leg"])]),
		**both("shin", [(0, H["shin"]), (0.15, (12, 0, 0)), (0.6, (8, 0, 0)), (0.72, (58, 0, 0)), (1.0, H["shin"])]),
		**both("ear", [(0, Z), (0.15, (0, 30, 0)), (0.6, (0, 30, 0)), (0.8, Z), (1.0, Z)]),
		"waist": {"rotation": [(0, H["waist"]), (0.15, (62, 0, 0)), (0.6, (66, 0, 0)), (0.75, (-6, 0, 0)), (1.0, H["waist"])]},
		"body": {"rotation": [(0, H["body"]), (0.15, (4, 0, 0)), (0.6, (2, 0, 0)), (0.72, (26, 0, 0)), (1.0, H["body"])]},
		"head": {"rotation": [(0, H["head"]), (0.15, (-58, 0, 0)), (0.6, (-60, 0, 0)), (0.75, (-10, 0, 0)), (1.0, H["head"])]},
	})
	# the charge, 3 s: arms up and apart over the head, the fireball swelling between them, the body arching back,
	# slow heavy wingbeats, legs drawn up, the tail lashing; in the last half second the ball throbs (the opening)
	swell = [(0, (0.05, 0.05, 0.05)), (0.5, (0.3, 0.3, 0.3)), (1.5, (0.62, 0.62, 0.62)), (2.4, (0.92, 0.92, 0.92)),
			 (2.55, (1.12, 1.12, 1.12)), (2.7, (0.94, 0.94, 0.94)), (2.85, (1.16, 1.16, 1.16)), (3.0, (1.0, 1.0, 1.0))]
	beats = flap(3.0, 36, -30, beats=4)
	charge = anim("charge", 3.0, "hold", {
		**beats,
		**both("arm", [(0, H["arm"]), (0.4, (-140, 0, 40)), (1.5, (-148, 0, 46)), (2.4, (-152, 0, 50)), (3.0, (-158, 0, 54))]),
		**both("forearm", [(0, H["forearm"]), (0.4, (-30, 0, 0)), (2.4, (-22, 0, 0)), (3.0, (-14, 0, 0))]),
		**both("leg", [(0, H["leg"]), (0.5, (-62, 0, 8)), (3.0, (-72, 0, 10))]),
		**both("shin", [(0, H["shin"]), (0.5, (92, 0, 0)), (3.0, (100, 0, 0))]),
		**both("ear", [(0, Z), (0.4, (0, 20, 0)), (3.0, (0, 28, 0))]),
		**tail(3.0, 24, H["tail_0"][0]),
		"waist": {"rotation": [(0, H["waist"]), (0.5, (2, 0, 0)), (3.0, (-6, 0, 0))]},
		"body": {"rotation": [(0, H["body"]), (0.5, (-10, 0, 0)), (2.4, (-16, 0, 0)), (3.0, (-20, 0, 0))]},
		"head": {"rotation": [(0, H["head"]), (0.5, (-30, 0, 0)), (2.4, (-36, 0, 0)), (3.0, (-28, 0, 0))]},
		"fireball": {"scale": swell,
					 "rotation": [(0, Z), (1.0, (0, 180, 40)), (2.0, (0, 360, 80)), (3.0, (0, 540, 120))],
					 "position": [(0, (0, -3, 0)), (0.5, Z), (3.0, (0, 1.0, 0))]},
	})
	# the throw: both arms drawn back, then slammed forward and down, the body whipped over, the ball leaving the hands
	throw = anim("throw", 0.6, "once", {
		**both("arm", [(0, (-158, 0, 54)), (0.12, (-196, 0, 40)), (0.24, (-58, 0, 6)), (0.6, H["arm"])]),
		**both("forearm", [(0, (-14, 0, 0)), (0.12, (-40, 0, 0)), (0.24, (-4, 0, 0)), (0.6, H["forearm"])]),
		**both("wing", [(0, H["wing"]), (0.12, (0, -10, 48)), (0.26, (0, 0, -44)), (0.6, H["wing"])]),
		**both("wing_tip", [(0, Z), (0.12, (0, 0, 24)), (0.26, (0, 0, -30)), (0.6, Z)]),
		**both("leg", [(0, (-72, 0, 10)), (0.24, (14, 0, 6)), (0.6, H["leg"])]),
		**both("shin", [(0, (100, 0, 0)), (0.24, (30, 0, 0)), (0.6, H["shin"])]),
		"waist": {"rotation": [(0, (-6, 0, 0)), (0.24, (28, 0, 0)), (0.6, H["waist"])]},
		"body": {"rotation": [(0, (-20, 0, 0)), (0.12, (-28, 0, 0)), (0.24, (34, 0, 0)), (0.6, H["body"])]},
		"head": {"rotation": [(0, (-28, 0, 0)), (0.24, (-46, 0, 0)), (0.6, H["head"])]},
		"fireball": {"scale": [(0, (1, 1, 1)), (0.12, (1.1, 1.1, 1.1)), (0.24, (0.6, 0.6, 0.6)), (0.3, (0.0, 0.0, 0.0)), (0.6, (0.0, 0.0, 0.0))],
					 "position": [(0, (0, 1, 0)), (0.12, (0, 2, 1)), (0.24, (0, -8, -14)), (0.3, (0, -12, -22))]},
	})
	# hurt: a flinch, wings jolted up, arms thrown wide, the head snapped aside
	hurt = anim("hurt", 0.35, "once", {
		**both("wing", [(0, H["wing"]), (0.08, (0, 24, 50)), (0.35, H["wing"])]),
		**both("arm", [(0, H["arm"]), (0.08, (20, 0, 56)), (0.35, H["arm"])]),
		"body": {"rotation": [(0, H["body"]), (0.08, (-16, 10, 0)), (0.35, H["body"])]},
		"head": {"rotation": [(0, H["head"]), (0.08, (-40, -24, 10)), (0.35, H["head"])]},
	})
	# death: the wings fail and fold, it curls up and drops (the game lays it over as it dies)
	death = anim("death", 1.0, "hold", {
		**both("wing", [(0, H["wing"]), (0.2, (0, 20, 56)), (0.6, (0, 50, -30)), (1.0, (0, 64, -40))]),
		**both("wing_tip", [(0, Z), (0.2, (0, 0, 30)), (0.6, (0, 70, 0)), (1.0, (0, 110, 0))]),
		**both("arm", [(0, H["arm"]), (0.2, (30, 0, 60)), (1.0, (-12, 0, 30))]),
		**both("forearm", [(0, H["forearm"]), (0.2, (-10, 0, 0)), (1.0, (-80, 0, 0))]),
		**both("leg", [(0, H["leg"]), (0.3, (-8, 0, 14)), (1.0, (-64, 0, 10))]),
		**both("shin", [(0, H["shin"]), (0.3, (10, 0, 0)), (1.0, (90, 0, 0))]),
		"waist": {"rotation": [(0, H["waist"]), (1.0, Z)]},
		"body": {"rotation": [(0, H["body"]), (0.2, (-26, 0, 0)), (1.0, (24, 0, 0))]},
		"head": {"rotation": [(0, H["head"]), (0.2, (-46, 0, 12)), (1.0, (26, 0, 16))]},
	})
	return [idle, walk, fly, swing, swoop, charge, throw, hurt, death]


PREVIEWS = [("idle", 0.0), ("walk", 0.15), ("fly", 0.0), ("fly", 0.25), ("swing", 0.14), ("swing", 0.26), ("swoop", 0.4),
			("swoop", 0.72), ("charge", 1.5), ("charge", 3.0), ("throw", 0.24), ("death", 1.0)]


def eggs():
	saved = dict(kit.EGGS)
	kit.EGGS.clear()
	kit.EGGS.update(EGGS)
	kit.eggs()
	kit.EGGS.clear()
	kit.EGGS.update(saved)


def main(argv):
	saved = dict(kit.PALETTE)
	living = build(LIVING, EYE)
	model = living.project("imp")
	add_animations(model, animations(), bone_uuids_of(model))
	art = os.path.join(kit.ART, "imp.bbmodel")
	with open(art, "w", encoding="utf-8", newline="\n") as f:
		json.dump(model, f)
	print("wrote kingdoms/art/imp.bbmodel")
	subprocess.check_call([sys.executable, "-I", os.path.join(ROOT, "tools", "bbmodel_geckolib.py"), art, ASSETS, "imp"])
	dead = build(ZOMBIFIED, EYE_DEAD)
	zombify(dead.img)
	skin = os.path.join(ASSETS, "textures", "entity", "zombified_imp.png")
	dead.img.save(skin)
	print("wrote textures/entity/zombified_imp.png")
	kit.PALETTE.clear()
	kit.PALETTE.update(saved)
	eggs()
	if "--preview" in argv:
		out = argv[argv.index("--preview") + 1]
		os.makedirs(out, exist_ok=True)
		subprocess.check_call([sys.executable, "-I", os.path.join(ROOT, "tools", "bbmodel_bake.py"), art, out, "imp"])
		for name, t in PREVIEWS:
			png = os.path.join(out, f"imp-{name}-{t}.png")
			subprocess.check_call([sys.executable, "-I", os.path.join(ROOT, "tools", "bbmodel_preview.py"), out, "imp", png, name, str(t)])
		subprocess.check_call([sys.executable, "-I", os.path.join(ROOT, "tools", "bbmodel_preview.py"), out, "imp",
							   os.path.join(out, "zombified_imp-fly.png"), "fly", "0.25", skin])


if __name__ == "__main__":
	main(sys.argv[1:])
