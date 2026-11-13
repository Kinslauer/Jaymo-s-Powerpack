"""
The snow giant's own body, kingdoms/art/snow_giant.bbmodel: our own cubes, texture and animations, all made here
(docs/Snow-Giant-Model-Spec.md is the spec they follow). Andy (2026-11-12, about the white grizzly it was first drawn
as): "add it to the giants being worked on, as its own upright model."

An upright, shaggy white giant of the cold plains: a heavy chest under a mantle of longer coat, a small head sunk
forward with a bare slate face, pale eyes, two short horns and a beard of icicles under a jaw that opens, long jointed
arms (elbows) ending in bare knuckled paws that hang to its shins, and short thick jointed legs (knees).

It is drawn by our own loader (client BbMobRenderer), which plays an animation by the name of whatever the beast is
doing, so the moves here are named for wild/Grizzly's actions and last as long as they do there: `rear` (34 ticks: up by
12, the ice it throws leaves its paws at 18, the slam lands at 26), `swipe_left` / `swipe_right` (14, landing at 7),
`charge` (30), `maul` (46, bites at 10, 22 and 34), `roar` (40), `stagger` (24), with `idle`, `walk`, `run` and `death`.

Blockbench space: y up, feet at 0, the front at -z, the model's right at +x. Keyframes as Blockbench shows them: -x
swings a hanging limb forward and up, +x tips an upright part forward, +z lifts the right arm (at +x) out to the side.

Run from the project root: python3 -I kingdoms/tools/gen_snow_giant.py [--preview <dir>]   (needs Pillow)
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
NAME = "snow_giant"
Z = (0, 0, 0)

# ---------------------------------------------------------------- colours (ours)

# A white coat shadowed blue-grey, a longer greyer mantle, a paler chest, bare slate skin on the face, paws and feet
# (frost freckles on it), horn the grey of old ice, icicles, dark claws.
COAT = {"fur": (224, 231, 236), "tail": (190, 202, 212), "belly": (238, 240, 236), "skin": (88, 106, 126), "bone": (150, 176, 198),
	"horn": (142, 162, 180), "ice": (150, 206, 236), "claw": (52, 62, 76)}
EYE, MOUTH = (150, 226, 255), (44, 52, 70)

# ---------------------------------------------------------------- the model

# Sizes in Blockbench units (16 to a block). It stands 55 to the crown (3.4 blocks), 61 to the horn tips.
KNEE_Y, HIP_Y, WAIST_Y, SHOULDER_Y, ELBOW_Y, NECK_Y = 10.0, 20.0, 26.0, 42.0, 26.0, 45.0


def face(img, r):
	"""The bare face on the head's front: a slate mask in the white, a heavy brow's shadow, two pale eyes."""
	x0, y0, x1, y1 = r
	for y in range(y0 + 3, y1 - 1):
		for x in range(x0 + 2, x1 - 2):
			img.putpixel((x, y), shade(COAT["skin"], 0.9 + ((x * 7 + y * 3) % 5) * 0.04) + (255,))
	for x in range(x0 + 2, x1 - 2):
		img.putpixel((x, y0 + 3), shade(COAT["skin"], 0.55) + (255,))
	for ex in (x0 + 3, x1 - 5):
		for dx in range(2):
			img.putpixel((ex + dx, y0 + 4), EYE + (255,))


def mouth(img, r):
	"""Inside the jaw (its top, seen when it opens): dark, a row of teeth along the front."""
	x0, y0, x1, y1 = r
	for y in range(y0, y1):
		for x in range(x0, x1):
			img.putpixel((x, y), MOUTH + (255,))
	for x in range(x0 + 1, x1 - 1, 2):
		img.putpixel((x, y0), (232, 236, 232, 255))


def build(seed=57):
	kit.PALETTE.update(COAT)
	b = Builder(256, 256, seed)
	b.bone("root", None, (0, 0, 0))
	b.bone("waist", "root", (0, HIP_Y + 2, 0))
	b.cube("waist", (-10, HIP_Y - 2, -6), (10, WAIST_Y, 7), "fur")
	# the chest, hunched a little forward, a paler front, the mantle over the shoulders and down the back
	b.bone("body", "waist", (0, WAIST_Y, 0), (-8, 0, 0))
	b.cube("body", (-14, WAIST_Y, -8), (14, 44, 8), "fur")
	b.cube("body", (-9, WAIST_Y + 1, -8.6), (9, 38, -8), "belly")
	b.cube("body", (-15, 40, -7), (15, 47, 10), "tail")
	b.cube("body", (-10, 33, 8), (10, 41, 11), "tail")
	# the head: sunk forward between the shoulders, a brow over the bare face, a muzzle, two short horns
	b.bone("head", "body", (0, NECK_Y, -4), (8, 0, 0))
	b.cube("head", (-6, NECK_Y, -12), (6, NECK_Y + 10, -1), "fur", faces_paint={"north": face})
	b.cube("head", (-6.5, NECK_Y + 6.2, -12.7), (6.5, NECK_Y + 8.2, -11), "tail")
	b.cube("head", (-3, NECK_Y + 2, -13.5), (3, NECK_Y + 5, -12), "skin")
	mirror(b, "horn", "head", (5, NECK_Y + 9, -6), [((4, NECK_Y + 9, -8), (7, NECK_Y + 13, -5), "horn"),
													((5, NECK_Y + 13, -7.5), (7, NECK_Y + 16, -5.5), "horn")], (0, 0, -16))
	# the jaw (it drops to roar), its beard of icicles
	b.bone("jaw", "head", (0, NECK_Y + 1.5, -4))
	b.cube("jaw", (-4.5, NECK_Y - 1, -12.5), (4.5, NECK_Y + 1.5, -3), "fur", faces_paint={"up": mouth})
	b.cube("jaw", (-3.5, NECK_Y - 4.5, -12), (-1.5, NECK_Y - 1, -10.5), "ice")
	b.cube("jaw", (-1, NECK_Y - 6, -12.2), (1, NECK_Y - 1, -10.3), "ice")
	b.cube("jaw", (1.5, NECK_Y - 4, -12), (3.5, NECK_Y - 1, -10.5), "ice")
	# the arms: upper arm under a shoulder of mantle, forearm, the bare paw and its claws
	mirror(b, "arm", "body", (16, SHOULDER_Y, 0), [((13.5, ELBOW_Y, -4.5), (22.5, 44, 4.5), "fur"),
												   ((13, 41, -5.5), (23.5, 46, 5.5), "tail")])
	mirror(b, "forearm", ("arm",), (18, ELBOW_Y, 0), [((12.5, 11, -5), (23.5, ELBOW_Y, 5), "fur"),
													  ((12, 5, -5.5), (24, 11, 5.5), "skin"),
													  ((13, 3, -6.5), (23, 5, -3.5), "claw")])
	# the legs: thigh, shin, the bare foot and its claws
	mirror(b, "leg", "waist", (6, HIP_Y, 0), [((1.5, KNEE_Y, -5), (10.5, HIP_Y + 1, 5), "fur")])
	mirror(b, "shin", ("leg",), (6, KNEE_Y, 0), [((1, 2, -4.5), (11, KNEE_Y, 5.5), "fur"),
												 ((0.5, 0, -8), (11.5, 3, 6), "skin"),
												 ((1.5, 0, -9.5), (10.5, 1.5, -8), "claw")])
	return b


# ---------------------------------------------------------------- animations

# how it hangs at rest: arms a little forward and out, elbows a little bent
H = {"arm": (-8, 0, 5), "forearm": (-12, 0, 0), "leg": Z, "shin": Z, "waist": Z, "body": Z, "head": Z, "jaw": Z, "root": Z}


def both(name, right, left=None):
	"""A pair of bones: the left one mirrors the right (y and z turned round) unless given."""
	if left is None:
		left = [(t, (v[0], -v[1], -v[2])) for t, v in right]
	return {f"right_{name}": {"rotation": right}, f"left_{name}": {"rotation": left}}


def side(s, name, keys):
	"""One side's bone ("right" as given, "left" mirrored)."""
	return {f"{s}_{name}": {"rotation": keys if s == "right" else [(t, (v[0], -v[1], -v[2])) for t, v in keys]}}


def one(name, keys):
	return {name: {"rotation": keys}}


def swipe(s):
	"""A paw swung across its front (14 ticks, landing at 7): wound out wide, swept through, the shoulders going with it."""
	t = -1 if s == "right" else 1  # which way the body turns into it
	o = "left" if s == "right" else "right"
	return anim("swipe_" + s, 0.7, "once", {
		**side(s, "arm", [(0, H["arm"]), (0.18, (-70, 0, 62)), (0.35, (-82, 0, -30)), (0.5, (-50, 0, -20)), (0.7, H["arm"])]),
		**side(s, "forearm", [(0, H["forearm"]), (0.18, (-50, 0, 0)), (0.35, (-8, 0, 0)), (0.7, H["forearm"])]),
		**side(o, "arm", [(0, H["arm"]), (0.18, (12, 0, 14)), (0.35, (20, 0, 18)), (0.7, H["arm"])]),
		**one("body", [(0, Z), (0.18, (-4, 20 * t, 0)), (0.35, (12, -26 * t, 0)), (0.7, Z)]),
		**one("head", [(0, Z), (0.18, (0, -12 * t, 0)), (0.35, (4, 14 * t, 0)), (0.7, Z)]),
	})


def animations():
	# idle: a slow breath, the arms hanging and swaying, the head turning a little
	idle = anim("idle", 4.0, "loop", {
		**both("arm", [(0, H["arm"]), (2.0, (-5, 0, 7)), (4.0, H["arm"])]),
		**both("forearm", [(0, H["forearm"]), (2.0, (-16, 0, 0)), (4.0, H["forearm"])]),
		**one("body", [(0, Z), (2.0, (2.5, 0, 0)), (4.0, Z)]),
		**one("head", [(0, Z), (1.0, (-2, 5, 0)), (3.0, (1, -5, 0)), (4.0, Z)]),
	})
	# walk: two heavy strides a loop, the arms swinging against the legs, the shoulders rolling
	walk = anim("walk", 1.6, "loop", {
		**side("right", "leg", [(0, (-24, 0, 0)), (0.8, (22, 0, 0)), (1.6, (-24, 0, 0))]),
		**side("left", "leg", [(0, (22, 0, 0)), (0.8, (-24, 0, 0)), (1.6, (22, 0, 0))]),
		**side("right", "shin", [(0, (6, 0, 0)), (0.4, (4, 0, 0)), (0.8, (10, 0, 0)), (1.2, (42, 0, 0)), (1.6, (6, 0, 0))]),
		**side("left", "shin", [(0, (10, 0, 0)), (0.4, (42, 0, 0)), (0.8, (6, 0, 0)), (1.2, (4, 0, 0)), (1.6, (10, 0, 0))]),
		**side("right", "arm", [(0, (14, 0, 6)), (0.8, (-26, 0, 6)), (1.6, (14, 0, 6))]),
		**side("left", "arm", [(0, (-26, 0, 6)), (0.8, (14, 0, 6)), (1.6, (-26, 0, 6))]),
		**both("forearm", [(0, (-14, 0, 0)), (1.6, (-14, 0, 0))]),
		**one("body", [(0, (5, 5, 2)), (0.8, (5, -5, -2)), (1.6, (5, 5, 2))]),
		**one("head", [(0, (-3, -4, -1)), (0.8, (-3, 4, 1)), (1.6, (-3, -4, -1))]),
		"root": {"position": [(0, (0, -0.6, 0)), (0.4, (0, 0.6, 0)), (0.8, (0, -0.6, 0)), (1.2, (0, 0.6, 0)), (1.6, (0, -0.6, 0))]},
	})
	# run: leaning into it, long strides, the arms driving
	run = anim("run", 1.0, "loop", {
		**side("right", "leg", [(0, (-40, 0, 0)), (0.5, (34, 0, 0)), (1.0, (-40, 0, 0))]),
		**side("left", "leg", [(0, (34, 0, 0)), (0.5, (-40, 0, 0)), (1.0, (34, 0, 0))]),
		**side("right", "shin", [(0, (8, 0, 0)), (0.5, (20, 0, 0)), (0.75, (70, 0, 0)), (1.0, (8, 0, 0))]),
		**side("left", "shin", [(0, (20, 0, 0)), (0.25, (70, 0, 0)), (0.5, (8, 0, 0)), (1.0, (20, 0, 0))]),
		**side("right", "arm", [(0, (30, 0, 8)), (0.5, (-48, 0, 8)), (1.0, (30, 0, 8))]),
		**side("left", "arm", [(0, (-48, 0, 8)), (0.5, (30, 0, 8)), (1.0, (-48, 0, 8))]),
		**both("forearm", [(0, (-40, 0, 0)), (1.0, (-40, 0, 0))]),
		**one("body", [(0, (20, 7, 2)), (0.5, (20, -7, -2)), (1.0, (20, 7, 2))]),
		**one("head", [(0, (-16, -5, 0)), (0.5, (-16, 5, 0)), (1.0, (-16, -5, 0))]),
		"root": {"position": [(0, (0, -1, 0)), (0.25, (0, 1.2, 0)), (0.5, (0, -1, 0)), (0.75, (0, 1.2, 0)), (1.0, (0, -1, 0))]},
	})
	# rear: up to its full height, both arms over its head (the ice it throws leaves them at 0.9 s), then its weight down
	# on its fists at 1.3 s
	rear = anim("rear", 1.7, "once", {
		**both("arm", [(0, H["arm"]), (0.6, (-168, 0, 12)), (1.1, (-176, 0, 8)), (1.3, (-42, 0, 2)), (1.7, H["arm"])]),
		**both("forearm", [(0, H["forearm"]), (0.6, (-24, 0, 0)), (0.9, (-6, 0, 0)), (1.1, (-30, 0, 0)), (1.3, (-6, 0, 0)), (1.7, H["forearm"])]),
		**one("waist", [(0, Z), (0.6, (-8, 0, 0)), (1.1, (-10, 0, 0)), (1.3, (16, 0, 0)), (1.7, Z)]),
		**one("body", [(0, Z), (0.6, (-12, 0, 0)), (1.1, (-14, 0, 0)), (1.3, (30, 0, 0)), (1.7, Z)]),
		**one("head", [(0, Z), (0.6, (-22, 0, 0)), (1.1, (-18, 0, 0)), (1.3, (-14, 0, 0)), (1.7, Z)]),
		**one("jaw", [(0, Z), (0.6, (26, 0, 0)), (1.1, (26, 0, 0)), (1.3, (6, 0, 0)), (1.7, Z)]),
		**both("leg", [(0, Z), (1.1, Z), (1.3, (-22, 0, 0)), (1.7, Z)]),
		**both("shin", [(0, Z), (1.1, Z), (1.3, (34, 0, 0)), (1.7, Z)]),
		"root": {"position": [(0, Z), (1.1, Z), (1.3, (0, -2.5, 0)), (1.7, Z)]},
	})
	# charge: head down behind a shoulder, driving forward at a run
	charge = anim("charge", 1.5, "once", {
		**side("right", "leg", [(0, Z), (0.25, (-40, 0, 0)), (0.75, (34, 0, 0)), (1.25, (-40, 0, 0)), (1.5, Z)]),
		**side("left", "leg", [(0, Z), (0.25, (34, 0, 0)), (0.75, (-40, 0, 0)), (1.25, (34, 0, 0)), (1.5, Z)]),
		**side("right", "shin", [(0, Z), (0.25, (8, 0, 0)), (0.75, (30, 0, 0)), (1.0, (66, 0, 0)), (1.25, (8, 0, 0)), (1.5, Z)]),
		**side("left", "shin", [(0, Z), (0.25, (30, 0, 0)), (0.5, (66, 0, 0)), (0.75, (8, 0, 0)), (1.25, (30, 0, 0)), (1.5, Z)]),
		**side("right", "arm", [(0, H["arm"]), (0.25, (-58, 0, -14)), (1.25, (-62, 0, -14)), (1.5, H["arm"])]),
		**side("right", "forearm", [(0, H["forearm"]), (0.25, (-84, 0, 0)), (1.25, (-84, 0, 0)), (1.5, H["forearm"])]),
		**side("left", "arm", [(0, H["arm"]), (0.25, (36, 0, 16)), (1.25, (36, 0, 16)), (1.5, H["arm"])]),
		**one("body", [(0, Z), (0.25, (30, 12, 0)), (1.25, (32, 12, 0)), (1.5, Z)]),
		**one("head", [(0, Z), (0.25, (-12, -10, 0)), (1.25, (-12, -10, 0)), (1.5, Z)]),
	})
	# maul: bent over what it has pinned, a paw on it, biting down at 0.5, 1.1 and 1.7 s
	bite = [(0, Z), (0.3, (26, 0, 0))]
	jaw = [(0, Z), (0.3, (30, 0, 0))]
	for at in (0.5, 1.1, 1.7):
		bite += [(at - 0.12, (14, 0, 0)), (at, (40, 0, 0)), (at + 0.2, (30, 6 if at == 1.1 else -6, 0))]
		jaw += [(at - 0.12, (34, 0, 0)), (at, (2, 0, 0)), (at + 0.2, (22, 0, 0))]
	maul = anim("maul", 2.3, "once", {
		**both("arm", [(0, H["arm"]), (0.3, (-52, 0, -8)), (2.0, (-52, 0, -8)), (2.3, H["arm"])]),
		**both("forearm", [(0, H["forearm"]), (0.3, (-30, 0, 0)), (2.0, (-30, 0, 0)), (2.3, H["forearm"])]),
		**one("waist", [(0, Z), (0.3, (22, 0, 0)), (2.0, (22, 0, 0)), (2.3, Z)]),
		**one("body", [(0, Z), (0.3, (30, 0, 0)), (2.0, (30, 0, 0)), (2.3, Z)]),
		**one("head", bite + [(2.3, Z)]),
		**one("jaw", jaw + [(2.3, Z)]),
		**both("leg", [(0, Z), (0.3, (-30, 0, 4)), (2.0, (-30, 0, 4)), (2.3, Z)]),
		**both("shin", [(0, Z), (0.3, (48, 0, 0)), (2.0, (48, 0, 0)), (2.3, Z)]),
		"root": {"position": [(0, Z), (0.3, (0, -3.5, 0)), (2.0, (0, -3.5, 0)), (2.3, Z)]},
	})
	# roar: arms flung wide and back, chest out, head thrown up and shaken, the jaw wide
	roar = anim("roar", 2.0, "once", {
		**both("arm", [(0, H["arm"]), (0.4, (24, 0, 64)), (1.6, (28, 0, 70)), (2.0, H["arm"])]),
		**both("forearm", [(0, H["forearm"]), (0.4, (-52, 0, 0)), (1.6, (-56, 0, 0)), (2.0, H["forearm"])]),
		**one("body", [(0, Z), (0.25, (10, 0, 0)), (0.5, (-16, 0, 0)), (1.6, (-18, 0, 0)), (2.0, Z)]),
		**one("head", [(0, Z), (0.25, (10, 0, 0)), (0.5, (-34, 8, 0)), (0.8, (-36, -10, 0)), (1.1, (-34, 10, 0)), (1.4, (-36, -8, 0)),
					   (1.6, (-30, 0, 0)), (2.0, Z)]),
		**one("jaw", [(0, Z), (0.4, (38, 0, 0)), (1.6, (40, 0, 0)), (2.0, Z)]),
	})
	# stagger: knocked back onto a heel, arms thrown up, the head snapped aside
	stagger = anim("stagger", 1.2, "once", {
		**both("arm", [(0, H["arm"]), (0.15, (-40, 0, 50)), (0.6, (-10, 0, 30)), (1.2, H["arm"])]),
		**one("waist", [(0, Z), (0.15, (-10, 0, 0)), (1.2, Z)]),
		**one("body", [(0, Z), (0.15, (-20, 8, 4)), (0.6, (-8, 4, 2)), (1.2, Z)]),
		**one("head", [(0, Z), (0.15, (-18, 26, 8)), (0.6, (6, 10, 0)), (1.2, Z)]),
		**side("right", "leg", [(0, Z), (0.2, (24, 0, 0)), (0.7, (16, 0, 0)), (1.2, Z)]),
		**side("left", "leg", [(0, Z), (0.2, (-12, 0, 0)), (0.7, (-6, 0, 0)), (1.2, Z)]),
		**side("left", "shin", [(0, Z), (0.2, (20, 0, 0)), (1.2, Z)]),
	})
	# death: to its knees, then over onto its face (the game does not also lay it on its side)
	death = anim("death", 1.0, "hold", {
		"root": {"rotation": [(0, Z), (0.35, (6, 0, 0)), (0.75, (78, 0, 0)), (1.0, (88, 0, 0))],
				 "position": [(0, Z), (0.35, (0, -8, 0)), (1.0, (0, -4, 0))]},
		**both("leg", [(0, Z), (0.35, (-30, 0, 0)), (1.0, (-10, 0, 6))]),
		**both("shin", [(0, Z), (0.35, (86, 0, 0)), (1.0, (14, 0, 0))]),
		**both("arm", [(0, H["arm"]), (0.35, (-20, 0, 24)), (0.75, (-110, 0, 40)), (1.0, (-150, 0, 34))]),
		**both("forearm", [(0, H["forearm"]), (0.75, (-30, 0, 0)), (1.0, (-8, 0, 0))]),
		**one("body", [(0, Z), (0.35, (14, 0, 0)), (1.0, (4, 0, 0))]),
		**one("head", [(0, Z), (0.35, (16, 0, 0)), (0.75, (-20, 0, 0)), (1.0, (-10, 24, 0))]),
		**one("jaw", [(0, Z), (0.35, (20, 0, 0)), (1.0, (10, 0, 0))]),
	})
	return [idle, walk, run, rear, swipe("left"), swipe("right"), charge, maul, roar, stagger, death]


PREVIEWS = [("idle", 0.0), ("walk", 0.0), ("run", 0.25), ("rear", 0.9), ("rear", 1.3), ("swipe_right", 0.18), ("swipe_right", 0.35),
			("charge", 0.75), ("maul", 1.1), ("roar", 0.8), ("stagger", 0.15), ("death", 1.0)]


def main(argv):
	saved = dict(kit.PALETTE)
	model = build().project(NAME)
	kit.PALETTE.clear()
	kit.PALETTE.update(saved)
	add_animations(model, animations(), bone_uuids_of(model))
	art = os.path.join(kit.ART, NAME + ".bbmodel")
	with open(art, "w", encoding="utf-8", newline="\n") as f:
		json.dump(model, f)
	print("wrote kingdoms/art/" + NAME + ".bbmodel")
	subprocess.check_call([sys.executable, "-I", os.path.join(ROOT, "tools", "bbmodel_bake.py"), art, ASSETS, NAME])
	if "--preview" in argv:
		out = argv[argv.index("--preview") + 1]
		os.makedirs(out, exist_ok=True)
		for name, t in PREVIEWS:
			png = os.path.join(out, f"{NAME}-{name}-{t}.png")
			subprocess.check_call([sys.executable, "-I", os.path.join(ROOT, "tools", "bbmodel_preview.py"), ASSETS, NAME, png, name, str(t)])


if __name__ == "__main__":
	main(sys.argv[1:])
