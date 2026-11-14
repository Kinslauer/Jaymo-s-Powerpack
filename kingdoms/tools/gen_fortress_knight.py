"""
The fortress knight's plate, kingdoms/art/vigil_plate.bbmodel: our own cubes and texture, all made here
(docs/Fortress-Knight-Model-Spec.md is the spec they follow). He was a wither skeleton in plain netherite.

He keeps his own body and the way he fights (nether/FortressKnight: a wither skeleton on MobCombat's weapon strings), so
this is armour hung on a humanoid's bones the way Armory's raised sets are (client ReverentLayer): a horned great helm
with one lit slit, a ribbed cuirass under a gorget, a belt with tassets, a torn cape, stacked pauldrons with a spike
each, vambraces and gauntlets, cuisses with knee cops, greaves and sabatons. Blackened plate with worn edges, cold
soul-light in the visor, a cape the red of the Nether's brick.

The bones are named as that layer wants them: bipedHead/armorHead, bipedBody/armorBody, bipedRightArm/armorRightArm
(and Left), bipedRightLeg/armorRightLeg and armorRightBoot (and Left). Blockbench space: y up, feet at 0, the front at
-z, the wearer's right at +x.

Run from the project root: python3 -I kingdoms/tools/gen_fortress_knight.py [--preview <dir>]   (needs Pillow)
The preview stands the plate on a wither skeleton's bones, which are not in the game's file.
"""
import json
import os
import subprocess
import sys
import tempfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import gen_tomb_undead as kit  # noqa: E402
from gen_tomb_undead import Builder, shade  # noqa: E402

ROOT = kit.ROOT
ASSETS = os.path.join(ROOT, "kingdoms", "src", "main", "resources", "assets", "jaymoskingdoms")
NAME = "vigil_plate"
Z = (0, 0, 0)
SIDES = ("north", "south", "east", "west")

# Blackened plate and its worn edge, the darker mail under it, old horn, the cape, the light in the visor, his bones
LOOK = {"plate": (44, 42, 50), "edge": (104, 100, 114), "under": (24, 22, 28), "horn": (74, 62, 56), "cape": (92, 24, 26),
	"trim": (132, 96, 44), "soul": (110, 226, 236), "wither": (40, 40, 42)}


def put(img, x, y, c, a=255):
	img.putpixel((x, y), tuple(c) + (a,))


def mul(img, x, y, k):
	c = img.getpixel((x, y))
	img.putpixel((x, y), shade(c[:3], k) + (c[3],))


def plate(img, r):
	"""A plate: a worn bright upper and leading edge, a dark lower edge, a rivet in each upper corner, a few scratches."""
	x0, y0, x1, y1 = r
	w, h = x1 - x0, y1 - y0
	for x in range(x0, x1):
		mul(img, x, y0, 1.7)
		mul(img, x, y1 - 1, 0.6)
	for y in range(y0, y1):
		mul(img, x0, y, 1.3)
	if w >= 5 and h >= 4:
		for x in (x0 + 1, x1 - 2):
			put(img, x, y0 + 1, LOOK["edge"])
	for y in range(y0 + 2, y1 - 1):
		for x in range(x0 + 1, x1 - 1):
			if (x * 29 + y * 13) % 37 == 0:
				mul(img, x, y, 1.45)


def visor(img, r):
	"""The helm's face: a ridge down the middle, one level slit lit from inside, breaths under it."""
	plate(img, r)
	x0, y0, x1, y1 = r
	mid = (x0 + x1) // 2
	for y in range(y0 + 1, y1 - 1):
		mul(img, mid, y, 1.5)
	for x in range(x0 + 1, x1 - 1):
		put(img, x, y0 + 3, LOOK["under"])
	for x in (mid - 3, mid - 2, mid + 1, mid + 2):
		put(img, x, y0 + 3, LOOK["soul"])
	for x in (x0 + 2, mid - 2, mid + 2, x1 - 3):
		for y in (y0 + 5, y0 + 6):
			put(img, x, y, LOOK["under"])


def breast(img, r):
	"""The cuirass's front: a keel down the middle and three lames across the belly."""
	plate(img, r)
	x0, y0, x1, y1 = r
	mid = (x0 + x1) // 2
	for y in range(y0 + 1, y1 - 1):
		mul(img, mid, y, 1.5)
		mul(img, mid - 1, y, 0.75)
	for y in (y1 - 3, y1 - 5, y1 - 7):
		for x in range(x0 + 1, x1 - 1):
			mul(img, x, y, 0.62)
			mul(img, x, y + 1, 1.2)


def torn(img, r):
	"""The cape: folds down it, a dark hem, and the lower third torn away in ragged strips."""
	x0, y0, x1, y1 = r
	h = y1 - y0
	for x in range(x0, x1):
		fold = 0.82 if (x - x0) % 3 == 0 else 1.1 if (x - x0) % 3 == 1 else 1.0
		# how far down this strip still hangs
		keep = h - (0, 5, 2, 7, 3, 0, 6, 1, 4, 8)[(x - x0) % 10]
		for y in range(y0, y1):
			if y - y0 >= keep:
				put(img, x, y, (0, 0, 0), 0)
			else:
				mul(img, x, y, fold * (0.7 if y - y0 >= keep - 1 else 1.0))


def trimmed(img, r):
	"""The belt: plate with a line of old gilt along it."""
	plate(img, r)
	x0, y0, x1, y1 = r
	for x in range(x0, x1):
		put(img, x, (y0 + y1) // 2, shade(LOOK["trim"], 0.85 + (x % 3) * 0.1))


def on_sides(fx):
	return {s: fx for s in SIDES}


def build(with_bones, seed=91):
	kit.PALETTE.update(LOOK)
	b = Builder(128, 128, seed)
	P = on_sides(plate)
	P_ALL = {**P, "up": plate}

	# ---- the helm
	b.bone("bipedHead", None, (0, 24, 0))
	b.bone("armorHead", "bipedHead", (0, 24, 0))
	b.cube("armorHead", (-4.5, 24, -4.5), (4.5, 32.5, 4.5), "plate", faces_paint={**P_ALL, "north": visor})
	b.cube("armorHead", (-0.5, 32.5, -4.5), (0.5, 34, 4.5), "plate", faces_paint=P)
	b.cube("armorHead", (-5, 23.2, -5), (5, 25.2, -3.2), "plate", faces_paint=P)
	for s in (1, -1):
		def x(a, c):
			return tuple(sorted((s * a, s * c)))
		for (xa, xb), ya, yb, za, zb in ((x(4.5, 7.5), 28.5, 30.5, -1.5, 0.5), (x(6, 8), 30.5, 34.5, -1.5, 0.5), (x(7, 8), 34.5, 37.5, -1, 0)):
			b.cube("armorHead", (xa, ya, za), (xb, yb, zb), "horn", rotation=(0, 0, -s * 10), origin=(s * 4.5, 29.5, 0))

	# ---- the cuirass, gorget, belt and tassets, and the cape
	b.bone("bipedBody", None, (0, 24, 0))
	b.bone("armorBody", "bipedBody", (0, 24, 0))
	b.cube("armorBody", (-4.5, 12.5, -2.6), (4.5, 24, 2.6), "plate", faces_paint={**P, "north": breast})
	b.cube("armorBody", (-5, 22.4, -3.1), (5, 24.6, 3.1), "plate", faces_paint=P_ALL)
	b.cube("armorBody", (-4.8, 11.2, -2.9), (4.8, 13.2, 2.9), "plate", faces_paint=on_sides(trimmed))
	for s in (1, -1):
		x0, x1 = sorted((s * 0.4, s * 4.6))
		b.cube("armorBody", (x0, 6.5, -3.1), (x1, 11.5, -2.4), "plate", faces_paint=P, rotation=(6, 0, 0), origin=(0, 11.5, -2.7))
		x0, x1 = sorted((s * 4.3, s * 5.0))
		b.cube("armorBody", (x0, 7.5, -2.2), (x1, 11.5, 2.2), "plate", faces_paint=P)
	b.bone("cape", "armorBody", (0, 23.5, 3.1), (-9, 0, 0))
	b.cube("cape", (-5, 2.5, 3.1), (5, 23.5, 3.5), "cape", faces_paint={"north": torn, "south": torn})

	# ---- the arms: stacked pauldrons with a spike, vambrace, gauntlet
	for s, side in ((1, "Right"), (-1, "Left")):
		def x(a, c):
			return tuple(sorted((s * a, s * c)))
		b.bone(f"biped{side}Arm", None, (s * 5, 22, 0))
		arm = f"armor{side}Arm"
		b.bone(arm, f"biped{side}Arm", (s * 5, 22, 0))
		for (xa, xb), ya, yb, zh, mat, fx in ((x(3.6, 9.2), 21.5, 25.2, 3.2, "plate", P_ALL), (x(4.2, 9.0), 19.4, 21.5, 2.9, "plate", P),
											  (x(3.7, 8.3), 13.4, 17.4, 2.4, "plate", P), (x(3.5, 8.5), 11.4, 13.6, 2.6, "plate", P),
											  (x(4.4, 7.6), 17.4, 19.4, 2.1, "under", None)):
			b.cube(arm, (xa, ya, -zh), (xb, yb, zh), mat, faces_paint=fx)
		xa, xb = x(6.2, 8.2)
		b.cube(arm, (xa, 25.2, -1), (xb, 28, 1), "horn", rotation=(0, 0, -s * 14), origin=(s * 7, 25.2, 0))

	# ---- the legs: cuisse and knee cop; greave and sabaton
	for s, side in ((1, "Right"), (-1, "Left")):
		def x(a, c):
			return tuple(sorted((s * a, s * c)))
		b.bone(f"biped{side}Leg", None, (s * 1.9, 12, 0))
		leg, boot = f"armor{side}Leg", f"armor{side}Boot"
		b.bone(leg, f"biped{side}Leg", (s * 1.9, 12, 0))
		xa, xb = x(-0.3, 4.3)
		b.cube(leg, (xa, 6.4, -2.4), (xb, 12, 2.4), "plate", faces_paint=P)
		xa, xb = x(0.3, 3.7)
		b.cube(leg, (xa, 5.2, -3.3), (xb, 7.6, -2.2), "plate", faces_paint=P_ALL)
		b.bone(boot, f"biped{side}Leg", (s * 1.9, 12, 0))
		xa, xb = x(-0.2, 4.2)
		b.cube(boot, (xa, 1.8, -2.3), (xb, 5.6, 2.3), "plate", faces_paint=P)
		xa, xb = x(-0.4, 4.4)
		b.cube(boot, (xa, 0, -3.4), (xb, 2, 2.5), "plate", faces_paint=P_ALL)

	if with_bones:
		# (preview only) a wither skeleton's bones under the plate
		b.bone("preview", None, Z)
		for frm, to in (((-4, 24, -4), (4, 32, 4)), ((-4, 12, -1), (4, 24, 1)), ((4, 12, -1), (6, 24, 1)), ((-6, 12, -1), (-4, 24, 1)),
						((1, 0, -1), (3, 12, 1)), ((-3, 0, -1), (-1, 12, 1))):
			b.cube("preview", frm, to, "wither")
	return b


def write(with_bones, assets, art=None):
	saved = dict(kit.PALETTE)
	model = build(with_bones).project(NAME)
	kit.PALETTE.clear()
	kit.PALETTE.update(saved)
	path = art or os.path.join(tempfile.mkdtemp(), NAME + ".bbmodel")
	with open(path, "w", encoding="utf-8", newline="\n") as f:
		json.dump(model, f)
	subprocess.check_call([sys.executable, "-I", os.path.join(ROOT, "tools", "bbmodel_bake.py"), path, assets, NAME])


def main(argv):
	write(False, ASSETS, os.path.join(kit.ART, NAME + ".bbmodel"))
	print("wrote kingdoms/art/" + NAME + ".bbmodel")
	if "--preview" in argv:
		out = argv[argv.index("--preview") + 1]
		os.makedirs(out, exist_ok=True)
		scratch = tempfile.mkdtemp()
		write(True, scratch)
		subprocess.check_call([sys.executable, "-I", os.path.join(ROOT, "tools", "bbmodel_preview.py"), scratch, NAME, os.path.join(out, NAME + ".png")])


if __name__ == "__main__":
	main(sys.argv[1:])
