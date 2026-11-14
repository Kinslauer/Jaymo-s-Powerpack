"""
The Blockbench project for Youth, the wood-wisp (kingdoms/art/youth_wisp.bbmodel): a small, half-there Sylvan with two
pairs of fae wings and a trail of light where its legs would be, and its animations (8 idles it wanders between, plus
appear, vanish and talk). Our own model, cubes and texture made here. Then run tools/bbmodel_geckolib.py on it.

Blockbench space: y up, the front at -z, the model's right at +x. Rotations as Blockbench shows them: +x swings a hanging
limb forward and up (and tips an upright part back), +z lifts the right arm (at +x) out to the side.

Run from the project root: py -3 -I kingdoms/tools/gen_youth_wisp.py   (needs Pillow)
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import gen_tomb_undead as gtu  # noqa: E402  (the Builder, the animation writer)
from gen_tomb_undead import Builder, Z, anim, add_animations, bone_uuids_of, mirror  # noqa: E402

ART = gtu.ART

# The wisp's own colours (RGBA): pale bark-and-moss skin, a leaf robe, wings and trail of see-through light.
COLOURS = {
	"skin": (222, 236, 214, 235), "robe": (120, 186, 120, 225), "robe_hi": (170, 222, 150, 225), "hair": (88, 150, 82, 240),
	"sprig": (150, 214, 96, 255), "wing": (196, 244, 228, 96), "vein": (236, 255, 244, 190), "trail": (186, 240, 214, 150),
	"trail_tip": (226, 255, 240, 90), "eye": (255, 250, 200, 255), "orb": (255, 246, 196, 230),
}


def paint(img, box, mat, face, rng):
	x0, y0, x1, y1 = box
	r, g, b, a = COLOURS[mat]
	light = {"up": 1.08, "down": 0.8, "north": 1.0, "south": 0.92, "east": 0.95, "west": 0.95}[face]
	for y in range(y0, y1):
		for x in range(x0, x1):
			k = light * (0.93 + rng.random() * 0.12)
			al = a
			lx, ly = x - x0, y - y0
			if mat == "wing":
				# veins along the long edge and a brighter rim, the rest faint
				if lx == 0 or ly == 0 or lx == x1 - x0 - 1 or (lx * 2 + ly) % 7 == 0:
					r2, g2, b2, al = COLOURS["vein"]
					img.putpixel((x, y), (min(255, int(r2 * k)), min(255, int(g2 * k)), min(255, int(b2 * k)), al))
					continue
				al = int(a * (0.8 + rng.random() * 0.4))
			elif mat == "robe" and rng.random() < 0.25:
				r2, g2, b2, _ = COLOURS["robe_hi"]
				img.putpixel((x, y), (min(255, int(r2 * k)), min(255, int(g2 * k)), min(255, int(b2 * k)), al))
				continue
			elif mat in ("trail", "trail_tip"):
				al = int(a * (0.7 + rng.random() * 0.5))
			img.putpixel((x, y), (min(255, int(r * k)), min(255, int(g * k)), min(255, int(b * k)), min(255, al)))


gtu.paint_face = paint  # the Builder paints every face through this


def eyes():
	def fx(img, rect):
		x0, y0, x1, y1 = rect
		c = COLOURS["eye"]
		for x in (x0 + 1, x1 - 2):
			img.putpixel((x, y0 + 2), c)
	return fx


def model():
	b = Builder(64, 64, 11)
	b.bone("root", None, (0, 0, 0))
	b.bone("body", "root", (0, 6, 0))
	b.cube("body", (-2, 6, -1.2), (2, 10.5, 1.2), "robe")
	# the trail of light where the legs would be: three tapering pieces, the lower two on their own bones so it swishes
	b.bone("trail", "body", (0, 6, 0))
	b.cube("trail", (-1.6, 3.6, -1), (1.6, 6, 1), "trail")
	b.bone("trail_mid", "trail", (0, 3.6, 0))
	b.cube("trail_mid", (-1, 1.6, -0.6), (1, 3.6, 0.6), "trail")
	b.bone("trail_tip", "trail_mid", (0, 1.6, 0))
	b.cube("trail_tip", (-0.5, 0, -0.3), (0.5, 1.6, 0.3), "trail_tip")
	b.bone("head", "body", (0, 10.5, 0))
	b.cube("head", (-2, 10.5, -2), (2, 14.5, 2), "skin", faces_paint={"north": eyes()})
	b.cube("head", (-2.2, 13.4, -2.2), (2.2, 15, 2.2), "hair")
	b.cube("head", (-2.2, 11, 0.8), (2.2, 13.4, 2.3), "hair")  # hair down the back
	# Sylvan ears, long and pointed, and two sprigs growing from the crown
	mirror(b, "ear", "head", (2, 12.5, 0), [((2, 12, -0.4), (3.8, 13, 0.4), "skin")], (0, 0, -20))
	mirror(b, "sprig", "head", (1, 15, 0), [((0.7, 15, -0.3), (1.3, 17.2, 0.3), "sprig")], (0, 0, -15))
	mirror(b, "arm", "body", (2.4, 10, 0), [((2, 6.6, -0.7), (3.4, 10.2, 0.7), "skin")], (0, 0, 8))
	# two pairs of thin fae wings off the back (+z), angled back from the spine
	b.bone("wings", "body", (0, 9, 1.2))
	mirror(b, "upper_wing", "wings", (0.4, 9.4, 1.3), [((0.4, 9.2, 1.2), (6.0, 15.8, 1.5), "wing")], (0, -25, -10))
	mirror(b, "lower_wing", "wings", (0.4, 8.6, 1.3), [((0.4, 5.0, 1.2), (4.4, 8.8, 1.5), "wing")], (0, -20, 15))
	# the light the wisp cups in its hands (idle.cup); every other animation keeps it at scale 0
	b.bone("orb", "body", (0, 8, -3))
	b.cube("orb", (-0.8, 7.2, -3.8), (0.8, 8.8, -2.2), "orb")
	return b


# ---------------------------------------------------------------- animations

def beat(period, length, rest_y, rest_z, amp_y=18.0, amp_z=6.0, side=1):
	"""A wing beat: frames for one wing swinging about its resting angle, `period` seconds a beat, over `length`."""
	frames, t, up = [], 0.0, True
	while t <= length + 1e-6:
		k = 1 if up else -1
		frames.append((round(t, 3), (0, side * k * amp_y, side * k * amp_z)))
		t += period / 2
		up = not up
	frames[-1] = (length, frames[0][1])
	return frames


def wings(period, length, amp=18.0):
	return {
		"right_upper_wing": {"rotation": beat(period, length, 0, 0, amp, amp / 3, 1)},
		"left_upper_wing": {"rotation": beat(period, length, 0, 0, amp, amp / 3, -1)},
		"right_lower_wing": {"rotation": beat(period, length, 0, 0, amp * 0.7, amp / 4, 1)},
		"left_lower_wing": {"rotation": beat(period, length, 0, 0, amp * 0.7, amp / 4, -1)},
	}


def swish(length, amp=8.0):
	q = length / 4
	return {
		"trail_mid": {"rotation": [(0, (0, 0, amp)), (q, Z), (2 * q, (0, 0, -amp)), (3 * q, Z), (length, (0, 0, amp))]},
		"trail_tip": {"rotation": [(0, (0, 0, -amp)), (q, (0, 0, amp)), (2 * q, (0, 0, amp)), (3 * q, (0, 0, -amp)), (length, (0, 0, -amp))]},
	}


ORB_OFF = {"orb": {"scale": [(0, Z)]}}


def tracks(*parts):
	out = {}
	for p in parts:
		for bone, chans in p.items():
			out.setdefault(bone, {}).update(chans)
	return out


def bob(length, height=0.6):
	return {"body": {"position": [(0, Z), (length / 2, (0, height, 0)), (length, Z)]}}


def animations():
	return [
		# 1. the default: a gentle bob, a slow wing beat
		anim("idle.hover", 2.4, "loop", tracks(wings(0.8, 2.4), swish(2.4), bob(2.4), ORB_OFF)),
		# 2. a quick burst of wingbeats lifts it half a block, then it settles
		anim("idle.flutter", 1.6, "once", tracks(wings(0.16, 1.6, 26), swish(1.6, 12), ORB_OFF, {
			"body": {"position": [(0, Z), (0.5, (0, 7, 0)), (0.9, (0, 6, 0)), (1.6, Z)]}})),
		# 3. a little loop-the-loop about its middle
		anim("idle.loop", 2.0, "once", tracks(wings(0.25, 2.0, 22), swish(2.0, 14), ORB_OFF, {
			"root": {"rotation": [(0, Z), (0.5, (-90, 0, 0)), (1.0, (-180, 0, 0)), (1.5, (-270, 0, 0)), (2.0, (-360, 0, 0))],
					 "position": [(0, Z), (0.5, (0, 5, -3)), (1.0, (0, 10, 0)), (1.5, (0, 5, 3)), (2.0, Z)]}})),
		# 4. sits cross-legged on the air, wings folded, chin on its hand
		anim("idle.sit", 3.2, "once", tracks(swish(3.2, 3), ORB_OFF, {
			"trail": {"rotation": [(0, Z), (0.4, (-70, 0, 0)), (2.8, (-70, 0, 0)), (3.2, Z)]},
			"trail_mid": {"rotation": [(0, Z), (0.4, (-40, 0, 0)), (2.8, (-40, 0, 0)), (3.2, Z)]},
			"right_arm": {"rotation": [(0, Z), (0.4, (100, -20, 0)), (2.8, (100, -20, 0)), (3.2, Z)]},
			"head": {"rotation": [(0, Z), (0.4, (-8, 0, 10)), (1.6, (-8, 0, 14)), (2.8, (-8, 0, 10)), (3.2, Z)]},
			"right_upper_wing": {"rotation": [(0, Z), (0.4, (0, 35, 0)), (2.8, (0, 35, 0)), (3.2, Z)]},
			"left_upper_wing": {"rotation": [(0, Z), (0.4, (0, -35, 0)), (2.8, (0, -35, 0)), (3.2, Z)]},
			"right_lower_wing": {"rotation": [(0, Z), (0.4, (0, 30, 0)), (2.8, (0, 30, 0)), (3.2, Z)]},
			"left_lower_wing": {"rotation": [(0, Z), (0.4, (0, -30, 0)), (2.8, (0, -30, 0)), (3.2, Z)]},
			"body": {"position": [(0, Z), (0.4, (0, -1, 0)), (2.8, (0, -1, 0)), (3.2, Z)]}})),
		# 5. a big stretch and a yawn: arms and wings wide, head back
		anim("idle.stretch", 2.4, "once", tracks(swish(2.4, 6), ORB_OFF, {
			"right_arm": {"rotation": [(0, Z), (0.8, (0, 0, 150)), (1.6, (0, 0, 150)), (2.4, Z)]},
			"left_arm": {"rotation": [(0, Z), (0.8, (0, 0, -150)), (1.6, (0, 0, -150)), (2.4, Z)]},
			"head": {"rotation": [(0, Z), (0.8, (18, 0, 0)), (1.6, (18, 0, 0)), (2.4, Z)]},
			"right_upper_wing": {"rotation": [(0, Z), (0.8, (0, -30, -20)), (1.6, (0, -30, -20)), (2.4, Z)]},
			"left_upper_wing": {"rotation": [(0, Z), (0.8, (0, 30, 20)), (1.6, (0, 30, 20)), (2.4, Z)]},
			"right_lower_wing": {"rotation": [(0, Z), (0.8, (0, -25, 15)), (1.6, (0, -25, 15)), (2.4, Z)]},
			"left_lower_wing": {"rotation": [(0, Z), (0.8, (0, 25, -15)), (1.6, (0, 25, -15)), (2.4, Z)]}})),
		# 6. leans in toward whoever is nearest, head tilted, curious
		anim("idle.peer", 2.6, "once", tracks(wings(0.9, 2.6, 10), swish(2.6, 5), ORB_OFF, {
			"body": {"rotation": [(0, Z), (0.6, (-22, 0, 0)), (2.0, (-22, 0, 0)), (2.6, Z)],
					 "position": [(0, Z), (0.6, (0, 0, -2)), (2.0, (0, 0, -2)), (2.6, Z)]},
			"head": {"rotation": [(0, Z), (0.6, (12, 0, 22)), (1.3, (12, 0, -16)), (2.0, (12, 0, 22)), (2.6, Z)]}})),
		# 7. a slow pirouette, arms out, the trail flaring
		anim("idle.twirl", 2.4, "once", tracks(wings(0.4, 2.4, 14), ORB_OFF, {
			"root": {"rotation": [(0, Z), (0.6, (0, 90, 0)), (1.2, (0, 180, 0)), (1.8, (0, 270, 0)), (2.4, (0, 360, 0))]},
			"right_arm": {"rotation": [(0, Z), (0.4, (0, 0, 80)), (2.0, (0, 0, 80)), (2.4, Z)]},
			"left_arm": {"rotation": [(0, Z), (0.4, (0, 0, -80)), (2.0, (0, 0, -80)), (2.4, Z)]},
			"trail": {"rotation": [(0, Z), (1.2, (0, 0, 14)), (2.4, Z)]},
			"trail_mid": {"rotation": [(0, Z), (1.2, (0, 0, 20)), (2.4, Z)]}})),
		# 8. cups its hands; a little light grows in them and floats up and away
		anim("idle.cup", 3.0, "once", tracks(wings(0.8, 3.0, 10), swish(3.0, 4), {
			"right_arm": {"rotation": [(0, Z), (0.5, (60, 25, 0)), (2.4, (60, 25, 0)), (3.0, Z)]},
			"left_arm": {"rotation": [(0, Z), (0.5, (60, -25, 0)), (2.4, (60, -25, 0)), (3.0, Z)]},
			"head": {"rotation": [(0, Z), (0.5, (-20, 0, 0)), (1.8, (-20, 0, 0)), (2.4, (10, 0, 0)), (3.0, Z)]},
			"orb": {"scale": [(0, Z), (0.6, Z), (1.2, (1, 1, 1)), (2.2, (1.2, 1.2, 1.2)), (2.8, Z), (3.0, Z)],
					"position": [(0, Z), (1.2, Z), (2.8, (0, 10, 0)), (3.0, (0, 10, 0))]}})),
		# coming and going: unfolds out of a swirl, folds back into it
		anim("appear", 1.0, "once", tracks(ORB_OFF, {
			"root": {"scale": [(0, Z), (0.6, (1.2, 1.2, 1.2)), (1.0, (1, 1, 1))],
					 "rotation": [(0, (0, -360, 0)), (1.0, Z)]}})),
		anim("vanish", 1.0, "hold", tracks(ORB_OFF, {
			"root": {"scale": [(0, (1, 1, 1)), (0.4, (1.2, 1.2, 1.2)), (1.0, Z)],
					 "rotation": [(0, Z), (1.0, (0, 360, 0))]}})),
		# talking: a gesture and a nod while an answer goes out
		anim("talk", 1.4, "once", tracks(ORB_OFF, {
			"right_arm": {"rotation": [(0, Z), (0.3, (50, 0, 20)), (0.7, (30, 0, 35)), (1.1, (50, 0, 20)), (1.4, Z)]},
			"left_arm": {"rotation": [(0, Z), (0.5, (20, 0, -15)), (1.4, Z)]},
			"head": {"rotation": [(0, Z), (0.35, (-8, 0, 0)), (0.7, (4, 0, 0)), (1.05, (-8, 0, 0)), (1.4, Z)]}})),
	]


def main():
	b = model()
	project = b.project("youth_wisp")
	add_animations(project, animations(), bone_uuids_of(project))
	os.makedirs(ART, exist_ok=True)
	with open(os.path.join(ART, "youth_wisp.bbmodel"), "w", encoding="utf-8", newline="\n") as f:
		json.dump(project, f)
	print("wrote kingdoms/art/youth_wisp.bbmodel")


if __name__ == "__main__":
	main()
