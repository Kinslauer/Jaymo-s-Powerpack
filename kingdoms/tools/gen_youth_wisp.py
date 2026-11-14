"""
The Blockbench project for Youth, the wood-wisp (kingdoms/art/youth_wisp.bbmodel): a small, half-there Sylvan boy with two
pairs of fae wings, who flies like a cocky lost boy rather than drifts like a fairy light (Andy: "think peter pan, less
navi"; quick moves, he's so small), and his animations (8 idles he wanders between, plus appear, vanish and talk). Our own model, cubes and texture made here. Then run tools/bbmodel_geckolib.py on it.

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
	"trail_tip": (226, 255, 240, 90), "boot": (96, 132, 70, 245), "belt": (110, 84, 52, 245), "eye": (255, 250, 200, 255), "orb": (255, 246, 196, 230),
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
	# everything hangs off the hips, so a flip turns the whole boy about his middle
	b.bone("body", "root", (0, 6, 0))
	b.cube("body", (-2, 6, -1.2), (2, 10.5, 1.2), "robe")
	b.cube("body", (-2.1, 6.4, -1.3), (2.1, 7.0, 1.3), "belt")
	# a tunic of leaves cut ragged at the hem, over two thin legs and soft leaf boots
	b.cube("body", (-2.2, 5.0, -1.4), (2.2, 6.4, 1.4), "robe")
	mirror(b, "leg", "body", (1, 6, 0), [((0.2, 1.4, -0.6), (1.7, 6.0, 0.6), "skin"),
										  ((0.1, 0, -1.2), (1.8, 1.6, 0.7), "boot")])
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
	return b


# ---------------------------------------------------------------- animations
# Quick: he's small, so everything is short and snappy, and the wings never stop (a hummingbird's blur).

def beat(period, length, amp_y=18.0, amp_z=6.0, side=1):
	"""A wing beat: frames for one wing swinging about its resting angle, `period` seconds a beat, over `length`."""
	frames, t, up = [], 0.0, True
	while t <= length + 1e-6:
		k = 1 if up else -1
		frames.append((round(t, 3), (0, side * k * amp_y, side * k * amp_z)))
		t += period / 2
		up = not up
	frames[-1] = (length, frames[0][1])
	return frames


def wings(length, period=0.12, amp=22.0):
	return {
		"right_upper_wing": {"rotation": beat(period, length, amp, amp / 3, 1)},
		"left_upper_wing": {"rotation": beat(period, length, amp, amp / 3, -1)},
		"right_lower_wing": {"rotation": beat(period, length, amp * 0.7, amp / 4, 1)},
		"left_lower_wing": {"rotation": beat(period, length, amp * 0.7, amp / 4, -1)},
	}


def tracks(*parts):
	out = {}
	for p in parts:
		for bone, chans in p.items():
			out.setdefault(bone, {}).update(chans)
	return out


def hold(bone, length, pose, inn=0.12, out=0.12, channel="rotation"):
	"""Snap into `pose`, hold it, snap back: quick in and out."""
	return {bone: {channel: [(0, Z), (inn, pose), (length - out, pose), (length, Z)]}}


def kick(length, amp=14.0, period=0.4):
	"""Legs paddling the air, one against the other."""
	f_r, f_l, t, up = [], [], 0.0, True
	while t <= length + 1e-6:
		k = amp if up else -amp
		f_r.append((round(t, 3), (k, 0, 0)))
		f_l.append((round(t, 3), (-k, 0, 0)))
		t += period / 2
		up = not up
	f_r[-1] = (length, f_r[0][1])
	f_l[-1] = (length, f_l[0][1])
	return {"right_leg": {"rotation": f_r}, "left_leg": {"rotation": f_l}}


def animations():
	return [
		# 1. the default: hovering with his legs paddling, a little bob
		anim("idle.hover", 1.2, "loop", tracks(wings(1.2), kick(1.2, 12, 0.6), {
			"body": {"position": [(0, Z), (0.3, (0, 0.5, 0)), (0.6, Z), (0.9, (0, 0.5, 0)), (1.2, Z)]},
			"right_arm": {"rotation": [(0, (0, 0, 6)), (0.6, (0, 0, 14)), (1.2, (0, 0, 6))]},
			"left_arm": {"rotation": [(0, (0, 0, -6)), (0.6, (0, 0, -14)), (1.2, (0, 0, -6))]}})),
		# 2. fists on hips, chest out, head back: he crows
		anim("idle.crow", 1.3, "once", tracks(wings(1.3, 0.1, 26), {
			"right_arm": {"rotation": [(0, Z), (0.12, (-25, 0, 45)), (1.15, (-25, 0, 45)), (1.3, Z)]},
			"left_arm": {"rotation": [(0, Z), (0.12, (-25, 0, -45)), (1.15, (-25, 0, -45)), (1.3, Z)]},
			"head": {"rotation": [(0, Z), (0.2, (-5, 0, 0)), (0.4, (28, 0, 0)), (0.6, (22, 0, 0)), (0.8, (28, 0, 0)), (1.0, (22, 0, 0)), (1.3, Z)]},
			"body": {"rotation": [(0, Z), (0.2, (8, 0, 0)), (1.1, (8, 0, 0)), (1.3, Z)],
					 "position": [(0, Z), (0.2, (0, 1.5, 0)), (1.1, (0, 1.5, 0)), (1.3, Z)]},
			"right_leg": {"rotation": [(0, Z), (0.15, (0, 0, 8)), (1.15, (0, 0, 8)), (1.3, Z)]},
			"left_leg": {"rotation": [(0, Z), (0.15, (0, 0, -8)), (1.15, (0, 0, -8)), (1.3, Z)]}})),
		# 3. a quick back somersault, tucked
		anim("idle.flip", 0.7, "once", tracks(wings(0.7, 0.1), {
			"body": {"rotation": [(0, Z), (0.175, (90, 0, 0)), (0.35, (180, 0, 0)), (0.525, (270, 0, 0)), (0.7, (360, 0, 0))],
					 "position": [(0, Z), (0.175, (0, 4, 0)), (0.35, (0, 6, 0)), (0.525, (0, 4, 0)), (0.7, Z)]},
			"right_leg": {"rotation": [(0, Z), (0.15, (80, 0, 0)), (0.55, (80, 0, 0)), (0.7, Z)]},
			"left_leg": {"rotation": [(0, Z), (0.15, (80, 0, 0)), (0.55, (80, 0, 0)), (0.7, Z)]},
			"right_arm": {"rotation": [(0, Z), (0.15, (70, 0, 10)), (0.55, (70, 0, 10)), (0.7, Z)]},
			"left_arm": {"rotation": [(0, Z), (0.15, (70, 0, -10)), (0.55, (70, 0, -10)), (0.7, Z)]}})),
		# 4. sits cross-legged on nothing, hands behind his head, cocky
		anim("idle.sit", 1.8, "once", tracks(wings(1.8, 0.16, 14),
			hold("right_leg", 1.8, (80, 25, -30)), hold("left_leg", 1.8, (80, -25, 30)),
			hold("right_arm", 1.8, (-160, 0, 40)), hold("left_arm", 1.8, (-160, 0, -40)),
			hold("body", 1.8, (12, 0, 0)), hold("body", 1.8, (0, -1.5, 0), channel="position"),
			{"head": {"rotation": [(0, Z), (0.15, (10, 0, 0)), (0.9, (10, 0, 8)), (1.65, (10, 0, 0)), (1.8, Z)]}})),
		# 5. darts off to one side and back, leaning into it
		anim("idle.swoop", 0.8, "once", tracks(wings(0.8, 0.08, 28), {
			"root": {"position": [(0, Z), (0.25, (-9, 2, -2)), (0.5, (-5, 3, 3)), (0.8, Z)],
					 "rotation": [(0, Z), (0.25, (0, 0, 35)), (0.5, (0, 0, -20)), (0.8, Z)]},
			"right_leg": {"rotation": [(0, Z), (0.25, (-30, 0, 0)), (0.8, Z)]},
			"left_leg": {"rotation": [(0, Z), (0.25, (-20, 0, 0)), (0.8, Z)]}})),
		# 6. lies back on the air, one ankle on the other knee
		anim("idle.lounge", 1.8, "once", tracks(wings(1.8, 0.16, 12),
			hold("body", 1.8, (70, 0, 0), 0.18, 0.18), hold("body", 1.8, (0, 1, 0), 0.18, 0.18, "position"),
			hold("right_leg", 1.8, (30, 0, 0), 0.18, 0.18), hold("left_leg", 1.8, (80, 0, 35), 0.18, 0.18),
			hold("right_arm", 1.8, (-150, 0, 30), 0.18, 0.18), hold("left_arm", 1.8, (-150, 0, -30), 0.18, 0.18),
			hold("head", 1.8, (-30, 0, 0), 0.18, 0.18))),
		# 7. a fast spin on the spot, arms flung out
		anim("idle.spin", 0.6, "once", tracks(wings(0.6, 0.08), {
			"root": {"rotation": [(0, Z), (0.15, (0, 90, 0)), (0.3, (0, 180, 0)), (0.45, (0, 270, 0)), (0.6, (0, 360, 0))]},
			"right_arm": {"rotation": [(0, Z), (0.1, (0, 0, 85)), (0.5, (0, 0, 85)), (0.6, Z)]},
			"left_arm": {"rotation": [(0, Z), (0.1, (0, 0, -85)), (0.5, (0, 0, -85)), (0.6, Z)]},
			"right_leg": {"rotation": [(0, Z), (0.1, (0, 0, 20)), (0.5, (0, 0, 20)), (0.6, Z)]},
			"left_leg": {"rotation": [(0, Z), (0.1, (0, 0, -20)), (0.5, (0, 0, -20)), (0.6, Z)]}})),
		# 8. laughs at something: doubled up, shaking, kicking his heels
		anim("idle.laugh", 1.2, "once", tracks(wings(1.2, 0.12, 18), kick(1.2, 22, 0.2), {
			"body": {"rotation": [(0, Z), (0.15, (-25, 0, 0)), (0.3, (-18, 0, 0)), (0.45, (-25, 0, 0)), (0.6, (-18, 0, 0)),
								  (0.75, (-25, 0, 0)), (0.9, (-18, 0, 0)), (1.2, Z)]},
			"head": {"rotation": [(0, Z), (0.15, (20, 0, 0)), (0.3, (8, 0, 0)), (0.45, (20, 0, 0)), (0.6, (8, 0, 0)),
								  (0.75, (20, 0, 0)), (0.9, (8, 0, 0)), (1.2, Z)]},
			"right_arm": {"rotation": [(0, Z), (0.12, (40, -30, 0)), (1.05, (40, -30, 0)), (1.2, Z)]},
			"left_arm": {"rotation": [(0, Z), (0.12, (40, 30, 0)), (1.05, (40, 30, 0)), (1.2, Z)]}})),
		# coming and going: zips in out of nothing, zips off upward
		anim("appear", 0.5, "once", {
			"root": {"scale": [(0, Z), (0.3, (1.25, 1.25, 1.25)), (0.5, (1, 1, 1))],
					 "rotation": [(0, (0, -540, 0)), (0.5, Z)],
					 "position": [(0, (0, 10, 0)), (0.5, Z)]}}),
		anim("vanish", 0.5, "hold", {
			"root": {"scale": [(0, (1, 1, 1)), (0.15, (1.2, 1.2, 1.2)), (0.5, Z)],
					 "rotation": [(0, Z), (0.5, (0, 540, 0))],
					 "position": [(0, Z), (0.5, (0, 14, 0))]}}),
		# talking: a quick point, a nod, hands going
		anim("talk", 0.8, "once", {
			"right_arm": {"rotation": [(0, Z), (0.15, (80, 0, 10)), (0.4, (60, 0, 25)), (0.6, (80, 0, 10)), (0.8, Z)]},
			"left_arm": {"rotation": [(0, Z), (0.25, (25, 0, -20)), (0.8, Z)]},
			"head": {"rotation": [(0, Z), (0.2, (-8, 0, 0)), (0.4, (4, 0, 0)), (0.6, (-8, 0, 0)), (0.8, Z)]}}),
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
