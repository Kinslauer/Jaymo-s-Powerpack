"""
The cyclops's Blockbench project, kingdoms/art/cyclops.bbmodel: our own cubes, texture and animations, all made here
(Andy, 2026-11-14, asked for Mythika's cyclops; Mythika is All Rights Reserved, so nothing of it is used, only the idea).
Then the game's GeckoLib files are written from it (tools/bbmodel_geckolib.py).

A one-eyed giant shepherd of the high stony places: a heavy brow over the single eye, a dark beard and shaggy hair, a
fleece about the loins on a leather belt, broad bare shoulders and long arms that hang near the knee, sandalled feet,
and an olive-wood club in the right hand. The model is built at half size (it stands 34 units) and drawn twice that
(CyclopsRenderer), so it stands over four blocks.

Bone names are the game's (head, body, right_arm, left_leg...) so a lost limb hides (Powers' Dismembered).

Blockbench space: y up, feet at 0, the front at -z, the model's right at +x. Rotations as Blockbench shows them: +x
swings a hanging limb forward and up (and tips an upright part back), +z lifts the right arm (at +x) out to the side.

Run from the project root: python3 -I kingdoms/tools/gen_cyclops.py [--preview <dir>]   (needs Pillow)
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

COLOURS = {
	"hide": (176, 128, 96), "hide_dark": (150, 104, 78), "belly": (190, 144, 110), "hair": (58, 42, 32),
	"fleece": (214, 206, 184), "belt": (92, 62, 38), "sandal": (110, 76, 46), "club": (112, 84, 52), "club_dark": (82, 60, 36),
	"nail": (198, 170, 140),
}
EYE_WHITE, IRIS, PUPIL, BROW, MOUTH = (236, 230, 212), (176, 120, 40), (24, 16, 10), (96, 66, 50), (70, 34, 30)
EGGS = {"cyclops": ((176, 128, 96), (236, 230, 212))}

# Heights (Blockbench units, half size): hips, shoulders, neck.
HIP_Y, SHOULDER_Y, NECK_Y = 13.0, 24.0, 25.0


def face(img, r):
	"""The face on the head's front (9 by 9): one great eye under a single heavy brow, a broad nose, a wide mouth."""
	x0, y0, x1, y1 = r
	cx = (x0 + x1) // 2
	for x in range(x0 + 1, x1 - 1):
		img.putpixel((x, y0 + 2), BROW + (255,))
	for x in range(cx - 2, cx + 3):
		for y in (y0 + 3, y0 + 4, y0 + 5):
			if not (abs(x - cx) == 2 and y != y0 + 4):
				img.putpixel((x, y), EYE_WHITE + (255,))
	for x in (cx - 1, cx, cx + 1):
		for y in (y0 + 3, y0 + 4, y0 + 5):
			img.putpixel((x, y), (IRIS if abs(x - cx) + abs(y - (y0 + 4)) <= 1 else EYE_WHITE) + (255,))
	img.putpixel((cx, y0 + 4), PUPIL + (255,))
	img.putpixel((cx, y0 + 6), shade(COLOURS["hide"], 0.72) + (255,))
	for x in range(cx - 2, cx + 3):
		img.putpixel((x, y0 + 7), MOUTH + (255,))


def scarred(img, r):
	"""Old scars across a broad back: pale ridges."""
	x0, y0, x1, y1 = r
	for i in range(3):
		y = y0 + 2 + i * 3
		for x in range(x0 + 2 + i, min(x1 - 1, x0 + 7 + i)):
			if y < y1:
				img.putpixel((x, y), shade(COLOURS["belly"], 1.12) + (255,))


def knots(img, r):
	"""The club's knots: dark rings and stubs."""
	x0, y0, x1, y1 = r
	for y in range(y0, y1):
		if (y - y0) % 4 == 1:
			for x in range(x0, x1):
				img.putpixel((x, y), shade(COLOURS["club_dark"], 0.9) + (255,))


def build():
	kit.PALETTE.update(COLOURS)
	b = Builder(128, 128, 61)
	b.bone("root", None, Z)
	b.bone("waist", "root", (0, HIP_Y, 0))
	# the fleece kilt on its belt
	b.cube("waist", (-5.5, HIP_Y - 3.5, -3.5), (5.5, HIP_Y + 1.5, 3.5), "fleece", 0.2)
	b.cube("waist", (-5.5, HIP_Y + 1.0, -3.6), (5.5, HIP_Y + 2.0, 3.6), "belt", 0.25)
	b.bone("body", "waist", (0, HIP_Y + 1.5, 0))
	b.cube("body", (-6, HIP_Y + 2, -3.5), (6, NECK_Y, 3.5), "hide", faces_paint={"south": scarred})
	b.cube("body", (-4.5, HIP_Y + 2, -4.5), (4.5, HIP_Y + 7, -3.5), "belly")
	# a hunch of muscle behind the neck
	b.cube("body", (-4, NECK_Y - 2.5, -1), (4, NECK_Y + 0.5, 3.5), "hide_dark")
	b.bone("head", "body", (0, NECK_Y, -0.5))
	b.cube("head", (-4.5, NECK_Y, -5), (4.5, NECK_Y + 9, 3), "hide", faces_paint={"north": face})
	# the brow ridge jutting over the eye, the beard, the shaggy hair
	b.cube("head", (-4.5, NECK_Y + 6.5, -5.8), (4.5, NECK_Y + 7.5, -5), "hide_dark")
	b.cube("head", (-4, NECK_Y - 3, -5.6), (4, NECK_Y + 1.2, -4.6), "hair")
	b.cube("head", (-5, NECK_Y + 4, -1), (5, NECK_Y + 10, 4), "hair", 0.1)
	mirror(b, "ear", "head", (4.5, NECK_Y + 5, -1), [((4.5, NECK_Y + 3.5, -2), (5.5, NECK_Y + 6.5, 0), "hide_dark")])
	# long heavy arms; the right hand holds the club
	mirror(b, "arm", "body", (6.5, SHOULDER_Y - 1, 0), [((5.5, SHOULDER_Y - 8, -2), (9.5, SHOULDER_Y + 1, 2.2), "hide")])
	mirror(b, "forearm", ("arm",), (7.5, SHOULDER_Y - 8, 0), [((5.6, SHOULDER_Y - 15, -1.9), (9.4, SHOULDER_Y - 8, 2.0), "hide_dark"),
															   ((5.4, SHOULDER_Y - 18, -2.2), (9.6, SHOULDER_Y - 15, 2.2), "hide"),
															   ((5.4, SHOULDER_Y - 18.5, -2.4), (9.6, SHOULDER_Y - 17.5, -1.6), "nail")])
	b.bone("club", "right_forearm", (7.5, SHOULDER_Y - 16.5, 0), (-40, 0, 0))
	b.cube("club", (6.5, SHOULDER_Y - 16.5 - 1, -1), (8.5, SHOULDER_Y - 16.5 + 1, 1), "club")
	b.cube("club", (6.7, SHOULDER_Y - 16.5 - 1, -7), (8.3, SHOULDER_Y - 16.5 + 0.6, -1), "club")
	b.cube("club", (6.0, SHOULDER_Y - 18.5, -17), (9.0, SHOULDER_Y - 15.5, -7), "club", 0.0, Z, None, {"up": knots, "east": knots, "west": knots})
	b.cube("club", (6.5, SHOULDER_Y - 18.0, -20), (8.5, SHOULDER_Y - 16.0, -17), "club_dark")
	mirror(b, "leg", "waist", (3.0, HIP_Y - 1, 0), [((0.8, HIP_Y - 7, -2.2), (5.2, HIP_Y - 0.5, 2.2), "hide")])
	mirror(b, "shin", ("leg",), (3.0, HIP_Y - 7, 0), [((1.0, 1, -2), (5.0, HIP_Y - 7, 2), "hide_dark"),
													  ((0.8, 0, -3.4), (5.2, 1.5, 2.2), "sandal")])
	return b


# ---------------------------------------------------------------- animations

def loop(length, *keys):
	"""Keys given as (fraction of the loop, value); the first is repeated at the end so the loop closes."""
	out = [(round(length * f, 4), v) for f, v in keys]
	return out + [(length, keys[0][1])]


def both(name, right, left=None):
	if left is None:
		left = [(t, (v[0], -v[1], -v[2])) for t, v in right]
	return {f"right_{name}": {"rotation": right}, f"left_{name}": {"rotation": left}}


# Key values are the game's way, added to a bone's resting angle (every bone rests at 0):
# x: - swings a limb forward, lifts the head, leans the body back; + the other way. z: + lifts the right arm out.
STAND = {"arm": (-4, 0, 6), "forearm": (-14, 0, 0), "body": (8, 0, 0), "head": (-8, 0, 0)}


def animations():
	S = STAND
	idle = anim("idle", 4.0, "loop", {
		**both("arm", loop(4.0, (0, S["arm"]), (0.5, (-7, 0, 9)))),
		**both("forearm", loop(4.0, (0, S["forearm"]), (0.5, (-20, 0, 0)))),
		"body": {"rotation": loop(4.0, (0, S["body"]), (0.5, (11, 0, 0)))},
		"head": {"rotation": loop(4.0, (0, (-8, -14, 0)), (0.35, (-4, 16, 2)), (0.7, (-10, -2, -2)))},
		"root": {"position": loop(4.0, (0, Z), (0.5, (0, -0.25, 0)))},
	})
	# a slow heavy stride, the club swinging with the right arm
	walk = anim("walk", 1.2, "loop", {
		"right_leg": {"rotation": loop(1.2, (0, (-30, 0, 0)), (0.5, (22, 0, 0)))},
		"left_leg": {"rotation": loop(1.2, (0, (22, 0, 0)), (0.5, (-30, 0, 0)))},
		"right_shin": {"rotation": loop(1.2, (0, (10, 0, 0)), (0.25, (14, 0, 0)), (0.5, (18, 0, 0)), (0.75, (48, 0, 0)))},
		"left_shin": {"rotation": loop(1.2, (0, (18, 0, 0)), (0.25, (48, 0, 0)), (0.5, (10, 0, 0)), (0.75, (14, 0, 0)))},
		"right_arm": {"rotation": loop(1.2, (0, (18, 0, 6)), (0.5, (-22, 0, 6)))},
		"left_arm": {"rotation": loop(1.2, (0, (-22, 0, -6)), (0.5, (18, 0, -6)))},
		**both("forearm", loop(1.2, (0, (-12, 0, 0)), (0.5, (-26, 0, 0))), loop(1.2, (0, (-26, 0, 0)), (0.5, (-12, 0, 0)))),
		"waist": {"rotation": loop(1.2, (0, (0, 6, 2)), (0.5, (0, -6, -2)))},
		"body": {"rotation": loop(1.2, (0, (12, -5, -2)), (0.5, (12, 5, 2)))},
		"head": {"rotation": loop(1.2, (0, (-12, 3, 0)), (0.5, (-12, -3, 0)))},
		"root": {"position": loop(1.2, (0, Z), (0.25, (0, 0.5, 0)), (0.5, Z), (0.75, (0, 0.5, 0)))},
	})
	run = anim("run", 0.7, "loop", {
		"right_leg": {"rotation": loop(0.7, (0, (-48, 0, 0)), (0.5, (30, 0, 0)))},
		"left_leg": {"rotation": loop(0.7, (0, (30, 0, 0)), (0.5, (-48, 0, 0)))},
		"right_shin": {"rotation": loop(0.7, (0, (16, 0, 0)), (0.5, (30, 0, 0)), (0.75, (70, 0, 0)))},
		"left_shin": {"rotation": loop(0.7, (0, (30, 0, 0)), (0.25, (70, 0, 0)), (0.5, (16, 0, 0)))},
		"right_arm": {"rotation": loop(0.7, (0, (30, 0, 8)), (0.5, (-40, 0, 8)))},
		"left_arm": {"rotation": loop(0.7, (0, (-40, 0, -8)), (0.5, (30, 0, -8)))},
		**both("forearm", loop(0.7, (0, (-30, 0, 0)), (0.5, (-50, 0, 0)))),
		"body": {"rotation": loop(0.7, (0, (24, -6, 0)), (0.5, (24, 6, 0)))},
		"head": {"rotation": loop(0.7, (0, (-22, 0, 0)), (0.5, (-20, 0, 0)))},
		"root": {"position": loop(0.7, (0, Z), (0.25, (0, 1.0, 0)), (0.5, Z), (0.75, (0, 1.0, 0)))},
	})
	# the swing: the club hauled back over the right shoulder, then swept across the body at chest height
	swing = anim("swing", 1.0, "once", {
		"right_arm": {"rotation": [(0, S["arm"]), (0.35, (-130, -30, 60)), (0.55, (-70, 40, -20)), (0.7, (-40, 50, -30)), (1.0, S["arm"])]},
		"right_forearm": {"rotation": [(0, S["forearm"]), (0.35, (-60, 0, 0)), (0.55, (-6, 0, 0)), (1.0, S["forearm"])]},
		"left_arm": {"rotation": [(0, (-4, 0, -6)), (0.35, (-30, 0, -30)), (0.55, (20, 0, -20)), (1.0, (-4, 0, -6))]},
		"body": {"rotation": [(0, S["body"]), (0.35, (0, 34, 0)), (0.55, (14, -36, 0)), (0.7, (16, -40, 0)), (1.0, S["body"])]},
		"head": {"rotation": [(0, S["head"]), (0.35, (-10, -20, 0)), (0.55, (-12, 24, 0)), (1.0, S["head"])]},
		"waist": {"rotation": [(0, Z), (0.35, (0, 10, 0)), (0.55, (0, -12, 0)), (1.0, Z)]},
	})
	# the slam: both hands on the club, raised high over the head, the whole body arching back; then brought down to the
	# ground before its feet with the knees giving (the shockwave goes out at 1.1 s)
	slam = anim("slam", 2.0, "once", {
		**both("arm", [(0, S["arm"]), (0.7, (-168, 0, 14)), (0.95, (-176, 0, 10)), (1.1, (-40, 0, 12)), (1.6, (-36, 0, 12)), (2.0, S["arm"])]),
		**both("forearm", [(0, S["forearm"]), (0.7, (-40, 0, 0)), (0.95, (-44, 0, 0)), (1.1, (-4, 0, 0)), (2.0, S["forearm"])]),
		"club": {"rotation": [(0, Z), (0.7, (20, 0, 0)), (1.1, (-10, 0, 0)), (2.0, Z)]},
		"body": {"rotation": [(0, S["body"]), (0.7, (-18, 0, 0)), (0.95, (-22, 0, 0)), (1.1, (44, 0, 0)), (1.6, (40, 0, 0)), (2.0, S["body"])]},
		"head": {"rotation": [(0, S["head"]), (0.7, (-30, 0, 0)), (1.1, (-34, 0, 0)), (2.0, S["head"])]},
		**both("leg", [(0, Z), (0.7, (4, 0, 4)), (1.1, (-34, 0, 8)), (1.6, (-30, 0, 8)), (2.0, Z)]),
		**both("shin", [(0, Z), (1.1, (52, 0, 0)), (1.6, (48, 0, 0)), (2.0, Z)]),
		"root": {"position": [(0, Z), (0.7, (0, 0.6, 0)), (1.1, (0, -2.6, 0)), (1.6, (0, -2.4, 0)), (2.0, Z)]},
	})
	# the roar: it throws its head back and its arms wide
	roar = anim("roar", 1.5, "once", {
		**both("arm", [(0, S["arm"]), (0.3, (-40, 0, 70)), (1.1, (-46, 0, 76)), (1.5, S["arm"])]),
		"body": {"rotation": [(0, S["body"]), (0.3, (-16, 0, 0)), (1.1, (-18, 0, 0)), (1.5, S["body"])]},
		"head": {"rotation": [(0, S["head"]), (0.3, (-40, 0, 0)), (0.7, (-44, 6, 0)), (1.1, (-42, -6, 0)), (1.5, S["head"])]},
	})
	# blinded: the left hand clapped over the eye, the head shaking, the club swung blind from side to side
	blind = anim("blinded", 2.0, "loop", {
		"left_arm": {"rotation": loop(2.0, (0, (-105, 25, -28)), (0.5, (-101, 21, -24)))},
		"left_forearm": {"rotation": loop(2.0, (0, (-95, 0, 0)), (0.5, (-100, 0, 0)))},
		"right_arm": {"rotation": loop(2.0, (0, (-70, -40, 40)), (0.25, (-80, 30, -10)), (0.5, (-70, 50, -20)), (0.75, (-80, -30, 30)))},
		"right_forearm": {"rotation": loop(2.0, (0, (-20, 0, 0)), (0.5, (-30, 0, 0)))},
		"body": {"rotation": loop(2.0, (0, (14, 16, 0)), (0.25, (18, -10, 0)), (0.5, (14, -20, 0)), (0.75, (18, 10, 0)))},
		"head": {"rotation": loop(2.0, (0, (10, -20, 6)), (0.25, (14, 18, -6)), (0.5, (8, -16, 4)), (0.75, (14, 20, -4)))},
		**both("leg", loop(2.0, (0, (-8, 0, 6)), (0.5, (-4, 0, 8)))),
	})
	# asleep by its fire: sat down heavily, legs out before it, the head sunk on the chest, the club across its lap
	sleep = anim("sleep", 5.0, "loop", {
		**both("leg", loop(5.0, (0, (-82, 0, 10)), (0.5, (-82, 0, 10)))),
		**both("shin", loop(5.0, (0, (6, 0, 0)), (0.5, (6, 0, 0)))),
		"right_arm": {"rotation": loop(5.0, (0, (-28, 0, 10)), (0.5, (-30, 0, 10)))},
		"left_arm": {"rotation": loop(5.0, (0, (-24, 0, -10)), (0.5, (-26, 0, -10)))},
		**both("forearm", loop(5.0, (0, (-30, 0, 0)), (0.5, (-32, 0, 0)))),
		"club": {"rotation": loop(5.0, (0, (40, 0, 0)), (0.5, (40, 0, 0)))},
		"body": {"rotation": loop(5.0, (0, (16, 0, 0)), (0.5, (20, 0, 0)))},
		"head": {"rotation": loop(5.0, (0, (34, 0, 8)), (0.5, (38, 0, 8)))},
		"root": {"position": loop(5.0, (0, (0, -6.6, 0)), (0.5, (0, -6.7, 0)))},
	})
	hurt = anim("hurt", 0.4, "once", {
		"body": {"rotation": [(0, S["body"]), (0.1, (-10, 8, 0)), (0.4, S["body"])]},
		"head": {"rotation": [(0, S["head"]), (0.1, (-26, -14, 6)), (0.4, S["head"])]},
		**both("arm", [(0, S["arm"]), (0.1, (16, 0, 30)), (0.4, S["arm"])]),
	})
	death = anim("death", 1.6, "hold", {
		**both("arm", [(0, S["arm"]), (0.3, (-30, 0, 40)), (1.6, (-6, 0, 30))]),
		**both("leg", [(0, Z), (0.5, (-30, 0, 6)), (1.6, (-10, 0, 10))]),
		**both("shin", [(0, Z), (0.5, (60, 0, 0)), (1.6, (20, 0, 0))]),
		"body": {"rotation": [(0, S["body"]), (0.3, (-24, 0, 0)), (1.0, (30, 0, 0)), (1.6, (10, 0, 0))]},
		"head": {"rotation": [(0, S["head"]), (0.3, (-46, 0, 10)), (1.6, (20, 0, 20))]},
		"root": {"position": [(0, Z), (0.5, (0, -2.5, 0)), (1.6, (0, -1.0, 0))]},
	})
	return [idle, walk, run, swing, slam, roar, blind, sleep, hurt, death]


PREVIEWS = [("idle", 0.0), ("walk", 0.3), ("swing", 0.35), ("swing", 0.55), ("slam", 0.95), ("slam", 1.2), ("blinded", 0.5),
			("roar", 0.7), ("sleep", 0.0), ("death", 1.6)]


def eggs():
	saved = dict(kit.EGGS)
	kit.EGGS.clear()
	kit.EGGS.update(EGGS)
	kit.eggs()
	kit.EGGS.clear()
	kit.EGGS.update(saved)


def main(argv):
	saved = dict(kit.PALETTE)
	model = build().project("cyclops")
	add_animations(model, animations(), bone_uuids_of(model))
	art = os.path.join(kit.ART, "cyclops.bbmodel")
	with open(art, "w", encoding="utf-8", newline="\n") as f:
		json.dump(model, f)
	print("wrote kingdoms/art/cyclops.bbmodel")
	subprocess.check_call([sys.executable, "-I", os.path.join(ROOT, "tools", "bbmodel_geckolib.py"), art, ASSETS, "cyclops"])
	kit.PALETTE.clear()
	kit.PALETTE.update(saved)
	eggs()
	if "--preview" in argv:
		out = argv[argv.index("--preview") + 1]
		os.makedirs(out, exist_ok=True)
		subprocess.check_call([sys.executable, "-I", os.path.join(ROOT, "tools", "bbmodel_bake.py"), art, out, "cyclops"])
		for name, t in PREVIEWS:
			png = os.path.join(out, f"cyclops-{name}-{t}.png")
			subprocess.check_call([sys.executable, "-I", os.path.join(ROOT, "tools", "bbmodel_preview.py"), out, "cyclops", png, name, str(t)])


if __name__ == "__main__":
	main(sys.argv[1:])
