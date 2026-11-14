"""
The satyr's Blockbench project, kingdoms/art/satyr.bbmodel: our own cubes, texture and animations, all made here (Andy,
2026-11-14, asked for Mythika's satyrs; Mythika is All Rights Reserved, so nothing of it is used, only the idea). Then
the game's GeckoLib files are written from it (tools/bbmodel_geckolib.py), with the grove's other coats painted on the
same layout beside the first.

A man to the waist and a goat below it: shaggy goat legs that bend back at the hock, cloven hooves, a short tail; a
lean bare chest, pointed ears, curled horns swept back, a goatee, curly hair, a wineskin at the hip, and the reed pipes
in the right hand. Real size: it stands 29 units (a little under two blocks).

Bone names are the game's (head, body, right_arm, left_leg...) so a lost limb hides (Powers' Dismembered).

Blockbench space: y up, feet at 0, the front at -z, the model's right at +x. Rest rotations as Blockbench shows them:
+x swings a hanging limb forward and up.

Run from the project root: python3 -I kingdoms/tools/gen_satyr.py [--preview <dir>]   (needs Pillow)
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

# Three coats (Satyr#coat): chestnut, black, and the grey of an old one.
COATS = {
	"satyr": {"hide": (198, 152, 114), "fur": (124, 88, 58), "hair": (96, 64, 42), "horn": (196, 182, 150)},
	"satyr_black": {"hide": (150, 108, 80), "fur": (52, 44, 40), "hair": (40, 32, 30), "horn": (170, 156, 128)},
	"satyr_grey": {"hide": (188, 150, 120), "fur": (150, 146, 140), "hair": (196, 194, 188), "horn": (150, 134, 108)},
}
COMMON = {"hoof": (54, 42, 36), "reed": (196, 170, 96), "cord": (120, 40, 36), "skin_bag": (124, 84, 52), "hide_dark": (0, 0, 0)}
EYE, WHITE, MOUTH = (204, 150, 30), (236, 228, 204), (120, 62, 54)
EGGS = {"satyr": ((124, 88, 58), (196, 182, 150))}

HIP_Y, SHOULDER_Y, NECK_Y = 12.0, 21.0, 22.0


def face(img, r):
	"""The face (7 by 7): goat's gold eyes, a long nose, a sly mouth."""
	x0, y0, x1, y1 = r
	for ex in (x0 + 1, x1 - 3):
		inner = ex + (1 if ex == x0 + 1 else 0)
		img.putpixel((ex, y0 + 3), WHITE + (255,))
		img.putpixel((ex + 1, y0 + 3), WHITE + (255,))
		img.putpixel((inner, y0 + 3), EYE + (255,))
		img.putpixel((ex, y0 + 2), shade(kit.PALETTE["hide"], 0.8) + (255,))
		img.putpixel((ex + 1, y0 + 2), shade(kit.PALETTE["hide"], 0.8) + (255,))
	cx = (x0 + x1) // 2
	img.putpixel((cx, y0 + 4), shade(kit.PALETTE["hide"], 0.78) + (255,))
	for x in range(cx - 1, cx + 2):
		img.putpixel((x, y0 + 5), MOUTH + (255,))
	img.putpixel((cx + 2, y0 + 4), MOUTH + (255,))


def ridges(img, r):
	"""A horn's rings."""
	x0, y0, x1, y1 = r
	for y in range(y0, y1):
		if (y - y0) % 2 == 0:
			for x in range(x0, x1):
				img.putpixel((x, y), shade(kit.PALETTE["horn"], 0.8) + (255,))


def build(coat, seed=29):
	kit.PALETTE.update(COMMON)
	kit.PALETTE.update(COATS[coat])
	kit.PALETTE["hide_dark"] = shade(kit.PALETTE["hide"], 0.86)
	b = Builder(128, 64, seed)
	b.bone("root", None, Z)
	b.bone("waist", "root", (0, HIP_Y, 0))
	# the shaggy goat's haunches, and the tail
	b.cube("waist", (-4, HIP_Y - 2, -2.5), (4, HIP_Y + 2.5, 2.5), "fur", 0.3)
	b.bone("tail", "waist", (0, HIP_Y + 1, 2.5), (40, 0, 0))
	b.cube("tail", (-1, HIP_Y - 2, 2.5), (1, HIP_Y + 1, 3.5), "fur")
	# the wineskin on a cord at the left hip
	b.cube("waist", (-5.6, HIP_Y - 2.5, -1.5), (-4.1, HIP_Y + 0.5, 1.5), "skin_bag")
	b.bone("body", "waist", (0, HIP_Y + 2, 0))
	b.cube("body", (-3.5, HIP_Y + 2, -2), (3.5, NECK_Y, 2), "hide")
	b.cube("body", (-3.6, HIP_Y + 2, -2.1), (3.6, HIP_Y + 3, 2.1), "cord")
	b.bone("head", "body", (0, NECK_Y, 0))
	b.cube("head", (-3.5, NECK_Y, -3.5), (3.5, NECK_Y + 7, 3.5), "hide", faces_paint={"north": face})
	b.cube("head", (-3.6, NECK_Y + 6, -3.6), (3.6, NECK_Y + 7.6, 3.1), "hair", 0.1)
	b.cube("head", (-3.6, NECK_Y + 2, -1), (3.6, NECK_Y + 6, 3.1), "hair", 0.1)
	b.cube("head", (-1.5, NECK_Y - 2, -3.9), (1.5, NECK_Y + 1, -3.4), "hair")
	mirror(b, "ear", "head", (3.5, NECK_Y + 4.5, 0), [((3.5, NECK_Y + 4, -0.5), (6.5, NECK_Y + 5, 0.5), "hide_dark")], (0, -20, -24))
	# the horns: up from the brow, then curling back
	mirror(b, "horn", "head", (2.0, NECK_Y + 7, -1.5), [((1.3, NECK_Y + 7, -2.2), (2.7, NECK_Y + 10, -0.8), "horn", 0.0, Z, None,
															{"east": ridges, "west": ridges, "north": ridges, "south": ridges})], (-35, 0, -12))
	mirror(b, "horn_tip", ("horn",), (2.0, NECK_Y + 10, -1.5), [((1.5, NECK_Y + 10, -2.0), (2.5, NECK_Y + 12.5, -1.0), "horn")], (-55, 0, 0))
	# arms with elbows
	mirror(b, "arm", "body", (4.5, SHOULDER_Y - 0.5, 0), [((3.5, SHOULDER_Y - 5.5, -1.3), (5.8, SHOULDER_Y + 0.5, 1.3), "hide")])
	mirror(b, "forearm", ("arm",), (4.6, SHOULDER_Y - 5.5, 0), [((3.6, SHOULDER_Y - 10.5, -1.2), (5.7, SHOULDER_Y - 5.5, 1.2), "hide_dark")])
	# the reed pipes in the right hand: seven reeds, longest outermost
	b.bone("pipes", "right_forearm", (4.6, SHOULDER_Y - 10, -1.2))
	for i in range(7):
		length = 4.5 - i * 0.45
		b.cube("pipes", (4.0 - 0.0, SHOULDER_Y - 10 - length / 2, -1.6 - i * 0.6), (5.2, SHOULDER_Y - 10 + length / 2, -1.1 - i * 0.6), "reed")
	b.cube("pipes", (3.9, SHOULDER_Y - 9.4, -5.8), (5.3, SHOULDER_Y - 8.9, -1.1), "cord")
	# goat's legs: the thigh forward, the shank back from the knee, the cannon straight down to the hoof
	mirror(b, "leg", "waist", (2.0, HIP_Y, 0), [((0.4, HIP_Y - 5.5, -1.6), (3.6, HIP_Y, 1.6), "fur")], (25, 0, 0))
	mirror(b, "shin", ("leg",), (2.0, HIP_Y - 5.5, 0), [((0.7, HIP_Y - 9.5, -1.2), (3.3, HIP_Y - 5.5, 1.2), "fur")], (-55, 0, 0))
	mirror(b, "hoof", ("shin",), (2.0, HIP_Y - 9.5, 0), [((0.9, 1.2, -1.0), (3.1, HIP_Y - 9.5, 1.0), "fur"),
														  ((0.6, 0.0, -1.6), (3.4, 1.4, 1.2), "hoof")], (30, 0, 0))
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
STAND = {"arm": (0, 0, 4), "forearm": (-12, 0, 0)}
# both hands up at the mouth, the pipes held across it
PIPING = {"right_arm": (-90, -30, 0), "right_forearm": (-60, 0, 0), "left_arm": (-85, 35, 0), "left_forearm": (-65, 0, 0),
		  "pipes": (0, -60, 0)}


def animations():
	S, P = STAND, PIPING
	idle = anim("idle", 3.0, "loop", {
		**both("arm", loop(3.0, (0, S["arm"]), (0.5, (-3, 0, 6)))),
		**both("forearm", loop(3.0, (0, S["forearm"]), (0.5, (-18, 0, 0)))),
		**both("ear", loop(3.0, (0, Z), (0.4, Z), (0.45, (0, 0, 14)), (0.5, Z))),
		"tail": {"rotation": loop(3.0, (0, Z), (0.1, (0, 20, 0)), (0.2, (0, -20, 0)), (0.3, Z))},
		"head": {"rotation": loop(3.0, (0, (-4, -12, 0)), (0.35, (0, 14, 3)), (0.7, (-6, 0, -3)))},
		"root": {"position": loop(3.0, (0, Z), (0.5, (0, -0.2, 0)))},
	})
	# a springy goat's walk: the hocks flick back as each hoof lifts
	walk = anim("walk", 0.7, "loop", {
		"right_leg": {"rotation": loop(0.7, (0, (-28, 0, 0)), (0.5, (18, 0, 0)))},
		"left_leg": {"rotation": loop(0.7, (0, (18, 0, 0)), (0.5, (-28, 0, 0)))},
		"right_shin": {"rotation": loop(0.7, (0, (-6, 0, 0)), (0.5, (6, 0, 0)), (0.75, (-28, 0, 0)))},
		"left_shin": {"rotation": loop(0.7, (0, (6, 0, 0)), (0.25, (-28, 0, 0)), (0.5, (-6, 0, 0)))},
		"right_arm": {"rotation": loop(0.7, (0, (22, 0, 4)), (0.5, (-24, 0, 4)))},
		"left_arm": {"rotation": loop(0.7, (0, (-24, 0, -4)), (0.5, (22, 0, -4)))},
		"tail": {"rotation": loop(0.7, (0, (0, 16, 0)), (0.5, (0, -16, 0)))},
		"body": {"rotation": loop(0.7, (0, (4, -4, 0)), (0.5, (4, 4, 0)))},
		"root": {"position": loop(0.7, (0, Z), (0.25, (0, 0.6, 0)), (0.5, Z), (0.75, (0, 0.6, 0)))},
	})
	# the run: bounding, both arms pumping
	run = anim("run", 0.45, "loop", {
		"right_leg": {"rotation": loop(0.45, (0, (-50, 0, 0)), (0.5, (30, 0, 0)))},
		"left_leg": {"rotation": loop(0.45, (0, (30, 0, 0)), (0.5, (-50, 0, 0)))},
		"right_shin": {"rotation": loop(0.45, (0, (-10, 0, 0)), (0.75, (-50, 0, 0)))},
		"left_shin": {"rotation": loop(0.45, (0, (-50, 0, 0)), (0.25, (-10, 0, 0)))},
		"right_arm": {"rotation": loop(0.45, (0, (40, 0, 6)), (0.5, (-50, 0, 6)))},
		"left_arm": {"rotation": loop(0.45, (0, (-50, 0, -6)), (0.5, (40, 0, -6)))},
		**both("forearm", loop(0.45, (0, (-50, 0, 0)), (0.5, (-60, 0, 0)))),
		"body": {"rotation": loop(0.45, (0, (16, 0, 0)), (0.5, (14, 0, 0)))},
		"head": {"rotation": loop(0.45, (0, (-14, 0, 0)), (0.5, (-12, 0, 0)))},
		"root": {"position": loop(0.45, (0, Z), (0.3, (0, 1.6, 0)), (0.6, Z))},
	})
	# piping: the pipes held at the mouth, the head swaying with the tune, one hoof keeping time
	pipe = anim("pipe", 2.0, "loop", {
		**{bone: {"rotation": loop(2.0, (0, v), (0.5, (v[0] - 4, v[1], v[2])))} for bone, v in P.items()},
		"head": {"rotation": loop(2.0, (0, (6, -10, 4)), (0.25, (2, 0, 0)), (0.5, (6, 10, -4)), (0.75, (2, 0, 0)))},
		"body": {"rotation": loop(2.0, (0, (-4, 6, 2)), (0.5, (-4, -6, -2)))},
		"right_leg": {"rotation": loop(2.0, (0, Z), (0.125, (-18, 0, 0)), (0.25, Z), (0.375, (-18, 0, 0)), (0.5, Z), (0.625, (-18, 0, 0)),
									   (0.75, Z), (0.875, (-18, 0, 0)))},
		"tail": {"rotation": loop(2.0, (0, (0, 20, 0)), (0.25, (0, -20, 0)), (0.5, (0, 20, 0)), (0.75, (0, -20, 0)))},
	})
	# the dance: a hopping jig round the fire, arms up and clapping over the head
	dance = anim("dance", 1.0, "loop", {
		"right_leg": {"rotation": loop(1.0, (0, (-40, 0, 6)), (0.25, Z), (0.5, (10, 0, 0)), (0.75, Z))},
		"left_leg": {"rotation": loop(1.0, (0, (10, 0, 0)), (0.25, Z), (0.5, (-40, 0, -6)), (0.75, Z))},
		"right_shin": {"rotation": loop(1.0, (0, (-40, 0, 0)), (0.25, Z), (0.5, Z), (0.75, Z))},
		"left_shin": {"rotation": loop(1.0, (0, Z), (0.25, Z), (0.5, (-40, 0, 0)), (0.75, Z))},
		**both("arm", loop(1.0, (0, (-160, 0, 30)), (0.25, (-170, 0, 10)), (0.5, (-160, 0, 30)), (0.75, (-170, 0, 10)))),
		**both("forearm", loop(1.0, (0, (-20, 0, 0)), (0.25, (-40, 0, 0)))),
		"body": {"rotation": loop(1.0, (0, (-6, 0, 8)), (0.5, (-6, 0, -8)))},
		"head": {"rotation": loop(1.0, (0, (-12, 0, -10)), (0.5, (-12, 0, 10)))},
		"tail": {"rotation": loop(1.0, (0, (0, 24, 0)), (0.5, (0, -24, 0)))},
		"root": {"position": loop(1.0, (0, Z), (0.12, (0, 2.0, 0)), (0.25, Z), (0.5, Z), (0.62, (0, 2.0, 0)), (0.75, Z))},
	})
	# sitting by the fire: down on the haunches, legs folded, an arm on a knee
	sit = anim("sit", 4.0, "loop", {
		**both("leg", loop(4.0, (0, (-80, 0, 12)), (0.5, (-80, 0, 12)))),
		**both("shin", loop(4.0, (0, (-70, 0, 0)), (0.5, (-70, 0, 0)))),
		**both("hoof", loop(4.0, (0, (60, 0, 0)), (0.5, (60, 0, 0)))),
		"right_arm": {"rotation": loop(4.0, (0, (-30, 0, 4)), (0.5, (-34, 0, 4)))},
		"left_arm": {"rotation": loop(4.0, (0, (-20, 0, -8)), (0.5, (-22, 0, -8)))},
		**both("forearm", loop(4.0, (0, (-30, 0, 0)), (0.5, (-36, 0, 0)))),
		"body": {"rotation": loop(4.0, (0, (10, 0, 0)), (0.5, (14, 0, 0)))},
		"head": {"rotation": loop(4.0, (0, (-6, -10, 0)), (0.5, (-4, 10, 0)))},
		"root": {"position": loop(4.0, (0, (0, -6.5, 0)), (0.5, (0, -6.6, 0)))},
	})
	# the butt: rearing back, then the horns driven down and forward
	butt = anim("butt", 0.8, "once", {
		"body": {"rotation": [(0, Z), (0.3, (-24, 0, 0)), (0.45, (40, 0, 0)), (0.6, (34, 0, 0)), (0.8, Z)]},
		"head": {"rotation": [(0, Z), (0.3, (-20, 0, 0)), (0.45, (36, 0, 0)), (0.8, Z)]},
		**both("arm", [(0, S["arm"]), (0.3, (20, 0, 30)), (0.45, (40, 0, 20)), (0.8, S["arm"])]),
		**both("leg", [(0, Z), (0.3, (-10, 0, 0)), (0.45, (20, 0, 0)), (0.8, Z)]),
		"root": {"position": [(0, Z), (0.3, (0, 1.5, 2)), (0.45, (0, 0, -3)), (0.8, Z)]},
	})
	# the kick: weight on the left hoof, the right drawn up and lashed out
	kick = anim("kick", 0.6, "once", {
		"right_leg": {"rotation": [(0, Z), (0.2, (-80, 0, 0)), (0.32, (-60, 0, 0)), (0.6, Z)]},
		"right_shin": {"rotation": [(0, Z), (0.2, (-60, 0, 0)), (0.32, (40, 0, 0)), (0.6, Z)]},
		"body": {"rotation": [(0, Z), (0.2, (-16, 0, 0)), (0.32, (-24, 0, 0)), (0.6, Z)]},
		**both("arm", [(0, S["arm"]), (0.2, (-30, 0, 40)), (0.6, S["arm"])]),
	})
	hurt = anim("hurt", 0.35, "once", {
		"body": {"rotation": [(0, Z), (0.08, (-14, 10, 0)), (0.35, Z)]},
		"head": {"rotation": [(0, Z), (0.08, (-30, -20, 8)), (0.35, Z)]},
		**both("arm", [(0, S["arm"]), (0.08, (16, 0, 40)), (0.35, S["arm"])]),
		**both("ear", [(0, Z), (0.08, (0, 30, 0)), (0.35, Z)]),
	})
	death = anim("death", 1.0, "hold", {
		**both("arm", [(0, S["arm"]), (0.3, (-30, 0, 50)), (1.0, (-10, 0, 30))]),
		**both("leg", [(0, Z), (0.4, (-40, 0, 8)), (1.0, (-20, 0, 10))]),
		"body": {"rotation": [(0, Z), (0.3, (-20, 0, 0)), (1.0, (16, 0, 0))]},
		"head": {"rotation": [(0, Z), (0.3, (-40, 0, 10)), (1.0, (20, 0, 18))]},
	})
	return [idle, walk, run, pipe, dance, sit, butt, kick, hurt, death]


PREVIEWS = [("idle", 0.0), ("walk", 0.17), ("run", 0.1), ("pipe", 0.5), ("dance", 0.12), ("sit", 0.0), ("butt", 0.45), ("kick", 0.32)]


def eggs():
	saved = dict(kit.EGGS)
	kit.EGGS.clear()
	kit.EGGS.update(EGGS)
	kit.eggs()
	kit.EGGS.clear()
	kit.EGGS.update(saved)


def main(argv):
	saved = dict(kit.PALETTE)
	model = build("satyr").project("satyr")
	add_animations(model, animations(), bone_uuids_of(model))
	art = os.path.join(kit.ART, "satyr.bbmodel")
	with open(art, "w", encoding="utf-8", newline="\n") as f:
		json.dump(model, f)
	print("wrote kingdoms/art/satyr.bbmodel")
	subprocess.check_call([sys.executable, "-I", os.path.join(ROOT, "tools", "bbmodel_geckolib.py"), art, ASSETS, "satyr"])
	for coat in ("satyr_black", "satyr_grey"):
		skin = os.path.join(ASSETS, "textures", "entity", coat + ".png")
		build(coat).img.save(skin)
		print("wrote textures/entity/" + coat + ".png")
	kit.PALETTE.clear()
	kit.PALETTE.update(saved)
	eggs()
	if "--preview" in argv:
		out = argv[argv.index("--preview") + 1]
		os.makedirs(out, exist_ok=True)
		subprocess.check_call([sys.executable, "-I", os.path.join(ROOT, "tools", "bbmodel_bake.py"), art, out, "satyr"])
		for name, t in PREVIEWS:
			png = os.path.join(out, f"satyr-{name}-{t}.png")
			subprocess.check_call([sys.executable, "-I", os.path.join(ROOT, "tools", "bbmodel_preview.py"), out, "satyr", png, name, str(t)])


if __name__ == "__main__":
	main(sys.argv[1:])
