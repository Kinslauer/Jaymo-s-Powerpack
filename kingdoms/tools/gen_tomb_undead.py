"""
The Blockbench projects for Kingdoms' GeckoLib mobs, written to kingdoms/art/ (open and edit them in Blockbench; rerun
tools/bbmodel_geckolib.py on a project to update the game's files):

- draugr.bbmodel and barrow_beetle.bbmodel: our own models, cubes and textures made here (the texture painted face by
  face from each cube's material), with their animations.
- silver_skeleton.bbmodel: polyjo's Dead Traveler Skeleton (CC BY 4.0, tools/bbmodels/dead-traveler-skeleton.bbmodel)
  with our own "rise" (climbing up out of a coffin) added.
- skeleton_lord.bbmodel: the Blockbench Workshop's Skeleton Lord (CC BY 4.0, tools/bbmodels/skeleton-lord.bbmodel)
  with our own "staff_swing", "slam" and "grave_call" added.

Blockbench space: y up, feet at 0, the front at -z, the model's right at +x. Rotations as Blockbench shows them: +x
swings a hanging limb forward and up (and tips an upright part back), +z lifts the right arm (at +x) out to the side.

Run from the project root: py -3 -I kingdoms/tools/gen_tomb_undead.py [draugr ...]   (needs Pillow; names: only those)
"""
import base64
import io
import json
import math
import os
import random
import sys
import uuid

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ART = os.path.join(ROOT, "kingdoms", "art")
SRC = os.path.join(ROOT, "tools", "bbmodels")


def uid():
	return str(uuid.uuid4())


# ---------------------------------------------------------------- animations

def anim(name, length, loop, tracks):
	"""tracks: {bone: {"rotation"|"position"|"scale": [(time, (x, y, z)), ...]}}, all catmullrom-smoothed."""
	return {"name": name, "length": length, "loop": loop, "tracks": tracks}


def add_animations(model, anims, bone_uuids):
	for a in anims:
		animators = {}
		for bone, channels in a["tracks"].items():
			keys = []
			for ch, frames in channels.items():
				for t, v in frames:
					keys.append({"channel": ch, "data_points": [{"x": v[0], "y": v[1], "z": v[2]}], "uuid": uid(), "time": t,
								 "color": -1, "interpolation": "catmullrom"})
			animators[bone_uuids[bone]] = {"name": bone, "type": "bone", "keyframes": keys}
		model.setdefault("animations", []).append({"uuid": uid(), "name": a["name"], "loop": a["loop"], "override": False,
												   "length": a["length"], "snapping": 24, "selected": False, "anim_time_update": "",
												   "blend_weight": "", "start_delay": "", "loop_delay": "", "animators": animators})


def bone_uuids_of(model):
	out = {}

	def walk(n):
		if isinstance(n, dict):
			if "name" in n:
				out[n["name"]] = n["uuid"]
			for c in n.get("children", []):
				walk(c)
	for n in model["outliner"]:
		walk(n)
	for g in model.get("groups", []) or []:
		out.setdefault(g["name"], g["uuid"])
	return out


def bend(anims, keep=()):
	"""Elbows and knees for moves keyed on the shoulders and hips alone: a forearm follows its arm (a loose crook hanging,
	folded more the higher the arm is, thrown straight on the way down in a blow) and a shin its leg (the knee gives more
	the further forward the thigh is). A move that keys a forearm or shin itself keeps its own; {@code keep} names moves
	to leave alone."""
	for a in anims:
		if a["name"] in keep:
			continue
		t = a["tracks"]
		for side in ("right", "left"):
			arm = t.get(side + "_arm", {}).get("rotation")
			if arm and side + "_forearm" not in t:
				keys, last, lf = [], None, 0
				for time, (x, _, _) in arm:
					if last is not None and abs(x - last) < 5:
						f = lf
					elif last is not None and last - x > 60:
						f = 6
					else:
						f = 22 + 0.3 * max(0, min(180, x))
					keys.append((time, (round(f, 1), 0, 0)))
					last, lf = x, f
				t[side + "_forearm"] = {"rotation": keys}
			leg = t.get(side + "_leg", {}).get("rotation")
			if leg and side + "_shin" not in t:
				t[side + "_shin"] = {"rotation": [(time, (round(-(6 + 0.8 * max(0, x)), 1), 0, 0)) for time, (x, _, _) in leg]}
	return anims


Z = (0, 0, 0)

# ---------------------------------------------------------------- painting

PALETTE = {
	"skin": (108, 104, 86), "bone": (214, 202, 160), "iron": (104, 110, 116), "mail": (82, 88, 96), "fur": (98, 78, 60),
	"cloth": (58, 82, 104), "leather": (88, 62, 40), "wood": (104, 72, 42), "gold": (184, 145, 58), "horn": (205, 191, 156),
	"rust": (122, 74, 44), "shell": (36, 50, 44), "shell_hi": (64, 88, 76), "leg": (22, 26, 24), "eye": (160, 232, 255),
	"steel": (168, 176, 184), "rime": (178, 196, 206), "hair": (38, 34, 36), "tail": (150, 112, 74),
	"shroud": (46, 52, 62), "hoar": (206, 210, 214),
}


def shade(c, k):
	return tuple(max(0, min(255, int(v * k))) for v in c)


def paint_face(img, box, mat, face, rng):
	x0, y0, x1, y1 = box
	base = PALETTE[mat]
	light = {"up": 1.12, "down": 0.72, "north": 1.0, "south": 0.9, "east": 0.94, "west": 0.94}[face]
	for y in range(y0, y1):
		for x in range(x0, x1):
			k = light * (0.9 + rng.random() * 0.18)
			c = base
			lx, ly = x - x0, y - y0
			if mat == "mail" and (lx + ly) % 2 == 0:
				k *= 1.25
			elif mat in ("fur", "tail") and rng.random() < 0.35:
				k *= 0.78 if lx % 2 else 1.2
			elif mat in ("wood", "leather") and lx % 3 == 0:
				k *= 0.85
			elif mat in ("iron", "steel", "gold") and (ly == 0 or lx == 0):
				k *= 1.25
			elif mat == "skin" and rng.random() < 0.12:
				c = PALETTE["bone"] if rng.random() < 0.3 else shade(base, 0.7)
			elif mat == "shell" and ly == 0:
				c = PALETTE["shell_hi"]
			elif mat == "rime" and rng.random() < 0.45:
				c = PALETTE["tail"] if rng.random() < 0.6 else (226, 238, 244)  # fur under the frost, and the frost's glitter
			img.putpixel((x, y), shade(c, k) + (255,))


class Builder:
	"""Cubes and bones in Blockbench space; a box-UV region for each cube packed on the sheet and painted."""

	def __init__(self, tex_w, tex_h, seed):
		self.img = Image.new("RGBA", (tex_w, tex_h), (0, 0, 0, 0))
		self.w, self.h = tex_w, tex_h
		self.elements, self.outliner, self.bones = [], [], {}
		self.cx = self.cy = self.row = 0
		self.rng = random.Random(seed)
		self.extras = []

	def bone(self, name, parent, origin, rotation=Z):
		node = {"name": name, "origin": list(origin), "rotation": list(rotation), "uuid": uid(), "export": True, "isOpen": True,
				"color": 0, "children": []}
		self.bones[name] = node
		(self.bones[parent]["children"] if parent else self.outliner).append(node)
		return node

	def alloc(self, w, h):
		if self.cx + w > self.w:
			self.cx, self.cy, self.row = 0, self.cy + self.row, 0
		x, y = self.cx, self.cy
		self.cx += w
		self.row = max(self.row, h)
		if y + h > self.h:
			raise SystemExit("texture sheet full")
		return x, y

	def cube(self, bone, frm, to, mat, inflate=0.0, rotation=Z, origin=None, faces_paint=None):
		size = [to[i] - frm[i] for i in range(3)]
		sx, sy, sz = (max(1, round(s)) for s in size)
		u, v = self.alloc(2 * (sx + sz), sy + sz)
		# the box layout: up/down along the top row, then east, north, west, south along the bottom
		rects = {
			"up": (u + sz, v, u + sz + sx, v + sz), "down": (u + sz + sx, v, u + sz + 2 * sx, v + sz),
			"east": (u, v + sz, u + sz, v + sz + sy), "north": (u + sz, v + sz, u + sz + sx, v + sz + sy),
			"west": (u + sz + sx, v + sz, u + 2 * sz + sx, v + sz + sy), "south": (u + 2 * sz + sx, v + sz, u + 2 * sz + 2 * sx, v + sz + sy),
		}
		faces = {}
		for fn, r in rects.items():
			paint_face(self.img, r, mat, fn, self.rng)
			uv = list(r)
			if fn == "down":
				uv = [r[0], r[3], r[2], r[1]]
			faces[fn] = {"uv": uv, "texture": 0}
		if faces_paint:
			for fn, fx in faces_paint.items():
				fx(self.img, rects[fn])
		e = {"name": bone + "_" + mat, "box_uv": False, "rescale": False, "locked": False, "render_order": "default",
			 "allow_mirror_modeling": True, "from": list(frm), "to": list(to), "autouv": 0, "color": 0, "inflate": inflate,
			 "origin": list(origin or self.bones[bone]["origin"]), "rotation": list(rotation), "faces": faces, "type": "cube",
			 "uuid": uid()}
		self.elements.append(e)
		self.bones[bone]["children"].append(e["uuid"])
		return e

	def project(self, name):
		buf = io.BytesIO()
		self.img.save(buf, "PNG")
		src = "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()
		return {"meta": {"format_version": "4.10", "model_format": "free", "box_uv": False}, "name": name,
				"model_identifier": name, "resolution": {"width": self.w, "height": self.h}, "elements": self.elements,
				"outliner": self.outliner, "textures": [{"path": "", "name": name + ".png", "folder": "", "namespace": "",
				"id": "0", "width": self.w, "height": self.h, "uv_width": self.w, "uv_height": self.h, "particle": False,
				"render_mode": "default", "visible": True, "mode": "bitmap", "saved": True, "uuid": uid(), "source": src}],
				"animations": []}


def mirror(b, name, parent, origin, cubes, rotation=Z):
	"""A right-hand bone and its cubes (at +x) and its left twin (x flipped)."""
	for side, s in (("right", 1), ("left", -1)):
		o = (origin[0] * s, origin[1], origin[2])
		b.bone(f"{side}_{name}", parent if not isinstance(parent, tuple) else f"{side}_{parent[0]}", o,
			   (rotation[0], rotation[1] * s, rotation[2] * s))
		for frm, to, mat, *rest in cubes:
			x0, x1 = sorted((frm[0] * s, to[0] * s))
			b.cube(f"{side}_{name}", (x0, frm[1], frm[2]), (x1, to[1], to[2]), mat, *(rest or []))


def eyes(color, at, px=1):
	def fx(img, r):
		x0, y0, x1, y1 = r
		w = x1 - x0
		for ex in (x0 + at[0], x1 - at[0] - px):
			for dx in range(px):
				img.putpixel((ex + dx, y0 + at[1]), color + (255,))
	return fx


# ---------------------------------------------------------------- the draugr

# The draugr is a long-dead Warrior-born, a Z Fighter of the old raids (Andy, 2026-11-10: "a long undead z fighter
# wearing the same armor, but with the tail wrapped around"): a fighter's build gone to sinew (broad shoulders, a narrow
# waist, long arms in wristbands), wild hair out from under the horned helm, the same mail, fur, leather and iron, and
# the tail wrapped round the waist the way a fighter keeps it, its tip hanging at the back.
# The tail ring: each segment's start and end corners (Blockbench units; back, right side, front, left side, the tip),
# the root of each its bone's pivot. Cut back at a segment (InhabitantsClient.DraugrRenderer) it ends there.
TAIL_Y, TAIL_W = 17.4, (1.8, 1.7, 1.6, 1.5, 1.3)
TAIL = [((0.0, 2.7), (5.2, 4.4)), ((4.3, 4.4), (6.0, -4.4)), ((6.0, -4.4), (-6.0, -2.7)), ((-6.0, -2.7), (-4.3, 4.4)),
		((-4.3, 2.7), (-0.6, 4.3))]


def gaunt_face(img, r):
	"""Pale lit eyes deep in the sockets, the cheeks fallen in, lips drawn back off the teeth."""
	x0, y0, x1, y1 = r
	for ex in (x0 + 1, x1 - 3):
		for dx in range(2):
			img.putpixel((ex + dx, y0 + 3), shade(PALETTE["skin"], 0.45) + (255,))
			img.putpixel((ex + dx, y0 + 4), PALETTE["eye"] + (255,) if dx == (0 if ex == x0 + 1 else 1) else shade(PALETTE["skin"], 0.5) + (255,))
	for cx in (x0 + 1, x1 - 2):
		for cy in (y0 + 5, y0 + 6):
			img.putpixel((cx, cy), shade(PALETTE["skin"], 0.62) + (255,))
	for x in range(x0 + 2, x1 - 2):
		img.putpixel((x, y0 + 6), shade(PALETTE["bone"], 0.95 if x % 2 else 0.75) + (255,))
		img.putpixel((x, y0 + 7), shade(PALETTE["skin"], 0.55) + (255,))


def ribs(img, r):
	"""The mail torn open over the ribs."""
	x0, y0, x1, y1 = r
	for y in range(y0, y1):
		for x in range(x0, x1):
			c = PALETTE["bone"] if (y - y0) % 2 == 0 and x not in (x0 + (x1 - x0) // 2 - 1, x0 + (x1 - x0) // 2) else shade(PALETTE["skin"], 0.6)
			img.putpixel((x, y), shade(c, 0.9) + (255,))


# The draugr's three looks (Andy, 2026-11-10: "up to 3 variations of each new model"): the same model and sheet, painted
# over in other metals, furs and hair. 0 is the project's own (iron, brown fur, black hair); 1 a chieftain's bronze, a
# grey wolf's fur, hair gone white (and its left horn broken off: InhabitantsClient.DraugrRenderer); 2 blackened iron,
# black fur, red-brown hair. Written as textures/entity/draugr_1.png and draugr_2.png beside the project's draugr.png.
DRAUGR_LOOKS = [{}, {"iron": (150, 112, 62), "steel": (196, 170, 120), "fur": (128, 126, 120), "hair": (206, 204, 198),
					 "gold": (200, 196, 190)},
				{"iron": (58, 60, 66), "mail": (60, 64, 70), "steel": (120, 124, 130), "fur": (40, 36, 34), "hair": (112, 50, 32),
				 "leather": (64, 40, 30)}]


def draugr_looks():
	"""Paints the draugr's other looks onto its sheet's layout (the same cubes, so the same UVs)."""
	assets = os.path.join(ROOT, "kingdoms", "src", "main", "resources", "assets", "jaymoskingdoms", "textures", "entity")
	base = dict(PALETTE)
	for i, look in enumerate(DRAUGR_LOOKS):
		if not look:
			continue
		PALETTE.update(look)
		try:
			src = draugr()["textures"][0]["source"].split(",", 1)[1]
		finally:
			PALETTE.clear()
			PALETTE.update(base)
		Image.open(io.BytesIO(base64.b64decode(src))).save(os.path.join(assets, "draugr_%d.png" % i))
		print("wrote draugr_%d.png" % i)


def draugr():
	# (the sheet was 128 x 128 before the other ranks' arms were added below the older cubes)
	b = Builder(128, 176, 11)
	b.bone("root", None, (0, 0, 0))
	# long legs gone to sinew, fur-wrapped boots, leather breeches
	# (each limb in two, hinged at the knee and the elbow, so it can take humanoid_moves.py's animations)
	mirror(b, "leg", "root", (2.2, 15, 0), [((0.4, 7.5, -1.8), (4.0, 15, 1.8), "skin"), ((0.3, 9, -2.0), (4.1, 15, 2.0), "leather")])
	mirror(b, "shin", ("leg",), (2.2, 7.5, 0), [((0.4, 0, -1.8), (4.0, 7.5, 1.8), "skin"), ((0.2, 0, -2.3), (4.2, 5, 2.1), "fur", 0.25)])
	b.bone("waist", "root", (0, 15, 0))
	b.cube("waist", (-4, 14, -2.5), (4, 17, 2.5), "leather", 0.1)
	b.cube("waist", (-1, 14.5, -3.1), (1, 16.5, -2.6), "gold")
	b.cube("waist", (-4.5, 9.5, -2.6), (4.5, 14, 2.6), "mail", 0.05)
	b.bone("body", "waist", (0, 17, 0))
	# a fighter's taper: the waist narrow, the chest broad
	b.cube("body", (-4.5, 17, -2.8), (4.5, 22, 2.8), "mail")
	b.cube("body", (-5.5, 22, -3.2), (5.5, 28, 3.2), "mail")
	# (its own bone, so the living Warrior-born of the lore film can leave it off: InhabitantsClient.DraugrRenderer)
	b.bone("ribs", "body", (0, 21, -2.9))
	b.cube("ribs", (-2, 19, -3.0), (2, 23, -2.7), "skin", faces_paint={"north": ribs})
	b.cube("body", (-6.5, 25.5, -4), (6.5, 29, 4), "fur", 0.2)
	b.bone("head", "body", (0, 28, 0))
	b.cube("head", (-4, 28, -4), (4, 36, 4), "skin", faces_paint={"north": gaunt_face})
	b.cube("head", (-4.5, 32, -4.5), (4.5, 37, 4.5), "iron", 0.1)
	b.cube("head", (-0.5, 29.5, -5.2), (0.5, 33, -4.6), "iron")
	# wild hair out from under the helm, at the back and over the ears
	for x0, x1, top, back in ((-3.2, -1.2, 33.5, 6.2), (-0.9, 0.9, 33.0, 6.8), (1.2, 3.2, 33.5, 6.2)):
		b.cube("head", (x0, 30, 4.2), (x1, top, back), "hair")
	mirror(b, "hair", "head", (4.2, 31, 0), [((4.2, 29.5, -0.5), (5.6, 33.0, 2.5), "hair")])
	mirror(b, "horn", "head", (4.5, 35, 0), [((4.5, 34, -1), (7, 36, 1), "horn"), ((6, 36, -0.75), (7.5, 40, 0.75), "horn")],
		   (0, 0, -15))
	# long arms, iron pauldrons, a fighter's wristbands
	mirror(b, "arm", "body", (6.5, 26, 0), [((5, 20, -1.8), (8.6, 27, 1.8), "skin"), ((4.6, 24, -2.6), (9.4, 29.5, 2.6), "iron", 0.1)])
	mirror(b, "forearm", ("arm",), (6.8, 20, 0), [((5, 13.5, -1.8), (8.6, 20, 1.8), "skin"), ((5, 13.5, -2.1), (8.6, 17.5, 2.1), "leather", 0.15)])
	b.bone("sword", "right_forearm", (6.8, 14.5, 0))
	b.cube("sword", (6.3, 13, -1.5), (7.3, 14.25, 1.5), "leather")
	b.cube("sword", (4.8, 13, -2.5), (8.8, 14.5, -1.5), "gold")
	b.cube("sword", (6.05, 13.25, -16), (7.55, 14.25, -2.5), "steel")
	b.bone("axe", "right_forearm", (6.8, 14.5, 0))
	b.cube("axe", (6.3, 13, -16), (7.3, 14, 4), "wood")
	b.cube("axe", (6.05, 14, -16), (7.55, 17.5, -12), "steel")
	b.cube("axe", (6.05, 9.5, -16), (7.55, 14, -13.5), "steel")
	b.bone("shield", "left_forearm", (-6.8, 19, 0))
	b.cube("shield", (-10.1, 11.5, -6), (-9.1, 24.5, 6), "wood")
	b.cube("shield", (-10.2, 11.5, -6), (-9.0, 12.5, 6), "iron")
	b.cube("shield", (-10.2, 23.5, -6), (-9.0, 24.5, 6), "iron")
	b.cube("shield", (-11.1, 16, -1.5), (-8.1, 19, 1.5), "iron")
	# the tail, wrapped round the waist over the belt, rimed with frost toward the tip; each draugr's own is cut short
	# its own way (InhabitantsClient.DraugrRenderer)
	parent = "waist"
	for i, ((ax, az), (bx, bz)) in enumerate(TAIL):
		w = TAIL_W[i]
		b.bone(f"tail_{i}", parent, (ax, TAIL_Y, az))
		y0 = TAIL_Y - w / 2 - (0.6 if i == len(TAIL) - 1 else 0.0)
		b.cube(f"tail_{i}", (min(ax, bx), y0, min(az, bz)), (max(ax, bx), y0 + w, max(az, bz)), "tail" if i < 3 else "rime")
		parent = f"tail_{i}"
	# the other ranks' arms and dress, each its own bone so the renderer shows only its rank's (added after everything else,
	# so the older cubes keep their places on the sheet): the archer's bow (its stave along the forearm's side, so it stands
	# upright when the arm is held out), the scourge's staff and hood, the wight's rimed cloak, the hall-guard's second
	# sword and the crest on its helm
	b.bone("bow", "left_forearm", (-6.8, 14.5, 0))
	b.cube("bow", (-7.2, 13, -8), (-6.4, 13.8, 8), "wood")
	b.cube("bow", (-7.2, 13.8, -10), (-6.4, 14.6, -8), "wood")
	b.cube("bow", (-7.2, 13.8, 8), (-6.4, 14.6, 10), "wood")
	b.cube("bow", (-6.9, 14.6, -10), (-6.7, 14.8, 10), "bone")
	b.bone("staff", "right_forearm", (6.8, 14.5, 0))
	b.cube("staff", (6.3, 2, -2.6), (7.3, 34, -1.6), "wood")
	b.cube("staff", (5.8, 34, -3.1), (7.8, 36.5, -1.1), "bone")
	b.cube("staff", (6.3, 36.5, -2.6), (7.3, 38.5, -1.6), "eye")
	b.bone("sword_left", "left_forearm", (-6.8, 14.5, 0))
	b.cube("sword_left", (-7.3, 13, -1.5), (-6.3, 14.25, 1.5), "leather")
	b.cube("sword_left", (-8.8, 13, -2.5), (-4.8, 14.5, -1.5), "gold")
	b.cube("sword_left", (-7.55, 13.25, -16), (-6.05, 14.25, -2.5), "steel")
	b.bone("cloak", "body", (0, 28, 3.3))
	b.cube("cloak", (-5.5, 12, 3.3), (5.5, 28, 4.1), "shroud")
	b.cube("cloak", (-5.5, 9, 3.3), (-1.5, 12, 4.1), "rime")
	b.cube("cloak", (1, 10, 3.3), (5.5, 12, 4.1), "rime")
	b.bone("hood", "head", (0, 28, 0))
	b.cube("hood", (-5.2, 37.2, -5.2), (5.2, 38.2, 5.2), "shroud")
	b.cube("hood", (-5.2, 27.5, 4.7), (5.2, 37.2, 5.5), "shroud")
	b.cube("hood", (4.7, 27.5, -5.0), (5.5, 37.2, 4.7), "shroud")
	b.cube("hood", (-5.5, 27.5, -5.0), (-4.7, 37.2, 4.7), "shroud")
	b.bone("crest", "head", (0, 37, 0))
	b.cube("crest", (-0.6, 37, -4.5), (0.6, 40, 4.5), "gold")
	model = b.project("draugr")
	R = "root"
	walk = {
		"right_leg": {"rotation": [(0, (25, 0, 0)), (0.5, (-25, 0, 0)), (1.0, (25, 0, 0))]},
		"left_leg": {"rotation": [(0, (-25, 0, 0)), (0.5, (25, 0, 0)), (1.0, (-25, 0, 0))]},
		# the knee folds as the leg swings through, and is all but straight when the foot lands
		"right_shin": {"rotation": [(0, (-4, 0, 0)), (0.5, (-12, 0, 0)), (0.75, (-50, 0, 0)), (1.0, (-4, 0, 0))]},
		"left_shin": {"rotation": [(0, (-12, 0, 0)), (0.25, (-50, 0, 0)), (0.5, (-4, 0, 0)), (1.0, (-12, 0, 0))]},
		"right_forearm": {"rotation": [(0, (25, 0, 0)), (0.5, (40, 0, 0)), (1.0, (25, 0, 0))]},
		"left_forearm": {"rotation": [(0, (40, 0, 0)), (0.5, (25, 0, 0)), (1.0, (40, 0, 0))]},
		"right_arm": {"rotation": [(0, (5, 0, 4)), (0.5, (25, 0, 4)), (1.0, (5, 0, 4))]},
		"left_arm": {"rotation": [(0, (25, 0, -4)), (0.5, (5, 0, -4)), (1.0, (25, 0, -4))]},
		"waist": {"rotation": [(0, (0, 6, 0)), (0.5, (0, -6, 0)), (1.0, (0, 6, 0))]},
		"body": {"rotation": [(0, (-4, -4, 0)), (0.5, (-4, 4, 0)), (1.0, (-4, -4, 0))]},
		R: {"position": [(0, Z), (0.25, (0, 0.6, 0)), (0.5, Z), (0.75, (0, 0.6, 0)), (1.0, Z)]},
	}
	anims = [
		anim("idle", 3.0, "loop", {
			"body": {"rotation": [(0, (-3, 0, 0)), (1.5, (-6, 0, 0)), (3.0, (-3, 0, 0))]},
			"head": {"rotation": [(0, (6, 0, 0)), (1.5, (10, 4, 2)), (3.0, (6, 0, 0))]},
			"right_arm": {"rotation": [(0, (25, 0, 6)), (1.5, (28, 0, 8)), (3.0, (25, 0, 6))]},
			"left_arm": {"rotation": [(0, (35, 0, -8)), (1.5, (38, 0, -10)), (3.0, (35, 0, -8))]},
			"right_forearm": {"rotation": [(0, (32, 0, 0)), (1.5, (38, 0, 0)), (3.0, (32, 0, 0))]},
			"left_forearm": {"rotation": [(0, (36, 0, 0)), (1.5, (41, 0, 0)), (3.0, (36, 0, 0))]},
			# a fighter's stand: the right foot forward, both knees soft
			"right_leg": {"rotation": [(0, (12, 0, 0)), (3.0, (12, 0, 0))]},
			"right_shin": {"rotation": [(0, (-20, 0, 0)), (1.5, (-23, 0, 0)), (3.0, (-20, 0, 0))]},
			"left_leg": {"rotation": [(0, (-6, 0, 0)), (3.0, (-6, 0, 0))]},
			"left_shin": {"rotation": [(0, (-8, 0, 0)), (1.5, (-11, 0, 0)), (3.0, (-8, 0, 0))]},
			"shield": {"rotation": [(0, (0, 40, 0)), (3.0, (0, 40, 0))]},
		}),
		anim("walk", 1.0, "loop", walk),
		anim("attack_sword", 0.8, "once", {
			"body": {"rotation": [(0, Z), (0.25, (0, 30, 0)), (0.45, (-12, -30, 0)), (0.8, Z)]},
			"right_arm": {"rotation": [(0, (25, 0, 6)), (0.25, (150, 0, 35)), (0.45, (35, 0, -10)), (0.8, (25, 0, 6))]},
			"left_arm": {"rotation": [(0, (35, 0, -8)), (0.45, (20, 0, -12)), (0.8, (35, 0, -8))]},
		}),
		anim("attack_axe", 1.2, "once", {
			"body": {"rotation": [(0, Z), (0.45, (12, 0, 0)), (0.65, (-28, 0, 0)), (1.2, Z)]},
			"right_arm": {"rotation": [(0, (25, 0, 6)), (0.45, (175, 0, 10)), (0.65, (45, 0, 0)), (1.2, (25, 0, 6))]},
			"left_arm": {"rotation": [(0, (35, 0, -8)), (0.45, (170, 0, -10)), (0.65, (45, 0, 0)), (1.2, (35, 0, -8))]},
			R: {"position": [(0, Z), (0.65, (0, -1.5, 0)), (1.2, Z)]},
		}),
		anim("block", 0.5, "hold", {
			"left_arm": {"rotation": [(0, (35, 0, -8)), (0.2, (85, 0, 0)), (0.5, (85, 0, 0))]},
			"left_forearm": {"rotation": [(0, (36, 0, 0)), (0.2, (20, 0, 0)), (0.5, (20, 0, 0))]},
			"shield": {"rotation": [(0, (0, 40, 0)), (0.2, (0, 0, 90)), (0.5, (0, 0, 90))]},
			"body": {"rotation": [(0, Z), (0.2, (-8, 12, 0)), (0.5, (-8, 12, 0))]},
		}),
		anim("guard_broken", 1.0, "once", {
			"left_arm": {"rotation": [(0, (85, 0, 0)), (0.2, (20, 0, -60)), (1.0, (35, 0, -8))]},
			"shield": {"rotation": [(0, (0, 0, 90)), (0.2, (0, 40, 0)), (1.0, (0, 40, 0))]},
			"body": {"rotation": [(0, Z), (0.2, (12, -15, 0)), (1.0, Z)]},
			"head": {"rotation": [(0, Z), (0.2, (15, 0, 0)), (1.0, Z)]},
		}),
		anim("shoot", 1.0, "once", {
			"left_arm": {"rotation": [(0, (35, 0, -8)), (0.3, (90, 0, 0)), (0.85, (90, 0, 0)), (1.0, (35, 0, -8))]},
			"left_forearm": {"rotation": [(0, (36, 0, 0)), (0.3, (6, 0, 0)), (0.85, (6, 0, 0)), (1.0, (36, 0, 0))]},
			"right_arm": {"rotation": [(0, (25, 0, 6)), (0.3, (90, 0, -18)), (0.75, (90, 0, -18)), (1.0, (25, 0, 6))]},
			"right_forearm": {"rotation": [(0, Z), (0.3, (20, 0, 0)), (0.7, (110, 0, 0)), (0.8, (30, 0, 0)), (1.0, Z)]},
			"body": {"rotation": [(0, Z), (0.3, (-4, 0, 0)), (0.75, (-8, 0, 0)), (0.85, (2, 0, 0)), (1.0, Z)]},
			"head": {"rotation": [(0, Z), (0.3, (0, 0, -6)), (0.8, (0, 0, -6)), (1.0, Z)]},
		}),
		anim("sidestep", 0.5, "once", {
			"body": {"rotation": [(0, Z), (0.15, (-10, 0, 18)), (0.5, Z)]},
			"head": {"rotation": [(0, Z), (0.15, (0, 0, -14)), (0.5, Z)]},
			"right_leg": {"rotation": [(0, Z), (0.15, (0, 0, 22)), (0.5, Z)]},
			"left_leg": {"rotation": [(0, Z), (0.15, (20, 0, 8)), (0.5, Z)]},
			R: {"position": [(0, Z), (0.15, (0, -1.5, 0)), (0.5, Z)]},
		}),
		anim("attack_twin", 0.7, "once", {
			"body": {"rotation": [(0, Z), (0.2, (4, 25, 0)), (0.4, (-10, -25, 0)), (0.55, (-10, 20, 0)), (0.7, Z)]},
			"right_arm": {"rotation": [(0, (25, 0, 6)), (0.2, (140, 0, 30)), (0.4, (30, 0, -25)), (0.7, (25, 0, 6))]},
			"left_arm": {"rotation": [(0, (35, 0, -8)), (0.3, (140, 0, -30)), (0.55, (30, 0, 25)), (0.7, (35, 0, -8))]},
		}),
	]
	# and every move of the humanoid library it has no move of its own for (the lore films' Warrior-born use them)
	bend(anims)
	import humanoid_moves
	own = {a["name"] for a in anims}
	anims += [a for a in humanoid_moves.moves(leg=15.0, cape=False) if a["name"] not in own]
	add_animations(model, anims, bone_uuids_of(model))
	return model


# ---------------------------------------------------------------- the Raid-King (the lord of the Stormborn Tomb)

# The draugr's chieftain (Andy, 2026-11-14: "use blockbench and fable, go all out for this"): the same dead Warrior-born
# in horned helm, mail and fur, a head and a half taller than his host (about 3.3 blocks to the helm), a king's gold on
# him: a crown round the helm, gold trim on the pauldrons and bracers, rings in a hoar-white beard braided to the belt.
# The Frostborn jailer's chain still crosses his chest with a piece of the seal hanging on it. A torn grave-shroud for a
# cape (cape_0..3, so it takes the move library's cloak), a great two-bearded axe, and his tail not wrapped as a
# fighter keeps it but loose and lashing, rimed at the tip.

def king_face(img, r):
	"""A skull's face under the helm: deep sockets with the pale lights in them, the nose a hole, bared teeth."""
	x0, y0, x1, y1 = r
	dark = shade(PALETTE["skin"], 0.38) + (255,)
	for sx in (x0 + 1, x1 - 4):
		for dx in range(3):
			for dy in range(3):
				img.putpixel((sx + dx, y0 + 4 + dy), dark)
		img.putpixel((sx + 1, y0 + 5), PALETTE["eye"] + (255,))
		img.putpixel((sx + (1 if sx == x0 + 1 else 1), y0 + 4), shade(PALETTE["eye"], 0.7) + (255,))
	for dy in range(2):
		img.putpixel((x0 + (x1 - x0) // 2 - 1, y0 + 7 + dy), dark)
		img.putpixel((x0 + (x1 - x0) // 2, y0 + 7 + dy), dark)
	for x in range(x0 + 2, x1 - 2):
		img.putpixel((x, y0 + 9), shade(PALETTE["bone"], 1.0 if (x - x0) % 2 else 0.7) + (255,))
		img.putpixel((x, y1 - 1), shade(PALETTE["skin"], 0.5) + (255,))


def seal_piece(img, r):
	"""The piece of the jailer's seal on his chain: rimed stone, a cut rune still lit."""
	x0, y0, x1, y1 = r
	for y in range(y0, y1):
		for x in range(x0, x1):
			img.putpixel((x, y), shade((178, 196, 206), 0.8 + ((x + y) % 2) * 0.15) + (255,))
	img.putpixel((x0 + 1, y0 + 1), PALETTE["eye"] + (255,))
	img.putpixel((x0 + 1, y0 + 2), PALETTE["eye"] + (255,))


def raid_king():
	b = Builder(256, 208, 23)
	b.bone("root", None, (0, 0, 0))
	mirror(b, "leg", "root", (3.2, 21, 0), [((0.6, 10.5, -2.6), (5.8, 21, 2.6), "skin"), ((0.5, 13, -2.8), (5.9, 21, 2.8), "leather")])
	mirror(b, "shin", ("leg",), (3.2, 10.5, 0), [((0.7, 0, -2.5), (5.7, 10.5, 2.5), "skin"), ((0.5, 2, -3.1), (5.9, 9, -2.5), "iron"),
		((0.3, 0, -3.2), (6.1, 5.5, 2.9), "fur", 0.25), ((1.7, 8.5, -3.5), (4.7, 10.5, -2.7), "gold")])
	b.bone("waist", "root", (0, 21, 0))
	b.cube("waist", (-6.2, 20, -3.4), (6.2, 24, 3.4), "leather", 0.1)
	b.cube("waist", (-2, 20.3, -4.0), (2, 23.7, -3.5), "gold")
	b.cube("waist", (-1, 21, -4.4), (1, 23, -4.0), "bone")
	b.cube("waist", (-6.4, 13, -3.3), (6.4, 20, 3.3), "mail", 0.05)
	b.cube("waist", (-5.6, 14, -3.9), (-2.3, 20, -3.4), "iron")
	b.cube("waist", (2.3, 14, -3.9), (5.6, 20, -3.4), "iron")
	b.cube("waist", (-2, 9.5, -3.8), (2, 20, -3.4), "shroud")
	b.cube("waist", (-2, 8, -3.8), (2, 9.5, -3.4), "rime")
	b.bone("body", "waist", (0, 24, 0))
	b.cube("body", (-6, 24, -3.4), (6, 31, 3.4), "mail")
	b.cube("body", (-8, 31, -4.2), (8, 40, 4.2), "mail")
	b.bone("ribs", "body", (0, 29, -3.5))
	b.cube("ribs", (-4, 26, -3.7), (0, 31, -3.4), "skin", faces_paint={"north": ribs})
	# the jailer's chain, shoulder to hip, and the piece of the seal on it
	b.cube("body", (-9, 34.2, -4.8), (9, 35.8, -4.2), "iron", 0.0, (0, 0, 28), (0, 35, -4.5))
	b.cube("body", (-1.5, 32, -5.4), (1.5, 36, -4.8), "rime", faces_paint={"north": seal_piece})
	b.cube("body", (-10, 37, -5.2), (10, 42, 5.2), "fur", 0.25)
	b.bone("head", "body", (0, 40, 0))
	b.cube("head", (-5, 40, -5), (5, 50, 5), "skin", faces_paint={"north": king_face})
	b.cube("head", (-5.5, 45.5, -5.5), (5.5, 51.5, 5.5), "iron", 0.1)
	b.cube("head", (-0.6, 42.5, -6.2), (0.6, 47, -5.6), "iron")
	b.cube("head", (4.6, 41.5, -5.4), (5.6, 45.5, -1.5), "iron")
	b.cube("head", (-5.6, 41.5, -5.4), (-4.6, 45.5, -1.5), "iron")
	b.cube("head", (-5.8, 49.5, -5.8), (5.8, 51, 5.8), "gold")
	for x0, x1, z0, z1, top in ((-5.8, -4.4, -5.8, -4.4, 54), (4.4, 5.8, -5.8, -4.4, 54), (-0.8, 0.8, -5.8, -4.6, 55.5), (-5.8, -4.4, 4.4, 5.8, 53.5),
							   (4.4, 5.8, 4.4, 5.8, 53.5), (-0.8, 0.8, 4.6, 5.8, 54)):
		b.cube("head", (x0, 51, z0), (x1, top, z1), "gold")
	b.cube("head", (-4, 38, 5), (4, 48, 6.8), "hoar")
	# the beard, in three braids, a gold ring on the middle one
	b.bone("beard", "head", (0, 41, -5))
	b.cube("beard", (-3.2, 34, -6.6), (-1.3, 41.5, -5.5), "hoar")
	b.cube("beard", (1.3, 34, -6.6), (3.2, 41.5, -5.5), "hoar")
	b.cube("beard", (-1.1, 31, -6.5), (1.1, 41.5, -5.4), "hoar")
	b.cube("beard", (-1.3, 33, -6.7), (1.3, 34.2, -5.2), "gold")
	mirror(b, "horn", "head", (5.5, 49, 0), [((5.5, 47.5, -1.5), (9.5, 50.5, 1.5), "horn"), ((8.5, 50.5, -1.2), (11, 56, 1.2), "horn"),
		((9, 55.5, -4.5), (10.5, 57.5, 1.0), "bone")], (0, 0, -12))
	mirror(b, "arm", "body", (10.5, 38, 0), [((8.2, 29, -2.6), (13, 39.5, 2.6), "skin"), ((7.5, 36, -3.8), (14.5, 42.5, 3.8), "iron", 0.1),
		((7.3, 36, -4.0), (14.7, 37.2, 4.0), "gold"), ((12, 42.5, -1), (14, 45.5, 1), "horn")])
	mirror(b, "forearm", ("arm",), (10.6, 29, 0), [((8.3, 19, -2.5), (12.9, 29, 2.5), "skin"), ((8.1, 20.5, -2.9), (13.1, 26.5, 2.9), "iron", 0.1),
		((8.0, 25.5, -3.1), (13.2, 26.7, 3.1), "gold")])
	b.bone("axe", "right_forearm", (10.6, 20.5, 0))
	b.cube("axe", (10.0, 19.6, -26), (11.2, 20.8, 10), "wood")
	b.cube("axe", (9.7, 19.3, 10), (11.5, 21.1, 11.5), "gold")
	b.cube("axe", (9.9, 20.8, -25), (11.3, 29, -17), "steel")
	b.cube("axe", (9.9, 12.5, -25), (11.3, 19.6, -19.5), "steel")
	b.cube("axe", (10.1, 12.5, -26), (11.1, 29, -25), "rime")
	b.cube("axe", (10.1, 19.6, -29.5), (11.1, 20.8, -26), "steel")
	b.bone("cape_0", "body", (0, 41, 5.2))
	b.cube("cape_0", (-9, 31, 5.2), (9, 41, 6), "shroud")
	b.bone("cape_1", "cape_0", (0, 31, 5.4))
	b.cube("cape_1", (-9.5, 21, 5.4), (9.5, 31, 6.2), "shroud")
	b.bone("cape_2", "cape_1", (0, 21, 5.6))
	b.cube("cape_2", (-10, 11, 5.6), (10, 21, 6.4), "shroud")
	b.bone("cape_3", "cape_2", (0, 11, 5.8))
	b.cube("cape_3", (-10, 4.5, 5.8), (-3, 11, 6.6), "shroud")
	b.cube("cape_3", (-1, 6, 5.8), (5, 11, 6.6), "shroud")
	b.cube("cape_3", (6.5, 3, 5.8), (10, 11, 6.6), "shroud")
	b.cube("cape_3", (-10, 3, 5.8), (-3, 4.5, 6.6), "rime")
	b.cube("cape_3", (6.5, 1.5, 5.8), (10, 3, 6.6), "rime")
	# the tail, loose: out from the right hip, drooping, the tip curled up
	b.bone("tail_0", "waist", (6, 22, 3.1), (0, 0, -40))
	b.cube("tail_0", (6, 20.9, 2), (13, 23.1, 4.2), "tail")
	b.bone("tail_1", "tail_0", (13, 22, 3.1), (0, 0, -25))
	b.cube("tail_1", (13, 21, 2.1), (19.5, 23, 4.1), "tail")
	b.bone("tail_2", "tail_1", (19.5, 22, 3.1), (0, 0, 20))
	b.cube("tail_2", (19.5, 21.1, 2.2), (25.5, 22.9, 4), "tail")
	b.bone("tail_3", "tail_2", (25.5, 22, 3.1), (0, 0, 30))
	b.cube("tail_3", (25.5, 21.2, 2.3), (30.5, 22.8, 3.9), "rime")
	model = b.project("raid_king")
	R = "root"
	RA, LA = (20, 0, 8), (15, 0, -8)
	hunched = {
		"body": (-40, 0, 0), "head": (-30, 0, 0), "right_arm": (10, 0, 4), "left_arm": (10, 0, -4), "right_leg": (40, 0, 0),
		"left_leg": (40, 0, 0), "right_shin": (-80, 0, 0), "left_shin": (-80, 0, 0),
	}
	spent = {
		"body": (-55, 0, 0), "head": (25, 0, 0), "right_arm": (15, 0, 0), "left_arm": (20, 0, -20), "right_leg": (-20, 0, 0),
		"left_leg": (30, 0, 0), "left_shin": (-50, 0, 0),
	}

	def held(pose, into, hold, out, drop):
		"""Into {@code pose} by {@code into}, held to {@code hold}, back out by {@code out} (seconds)."""
		tracks = {bone: {"rotation": [(0, Z), (into, rot), (hold, rot), (out, Z)]} for bone, rot in pose.items()}
		tracks[R] = {"position": [(0, Z), (into, (0, drop, 0)), (hold, (0, drop, 0)), (out, Z)]}
		return tracks

	anims = [
		anim("idle", 4.0, "loop", {
			"body": {"rotation": [(0, (-2, 0, 0)), (2.0, (-5, 0, 0)), (4.0, (-2, 0, 0))]},
			"head": {"rotation": [(0, (4, -6, 0)), (2.0, (7, 6, 1)), (4.0, (4, -6, 0))]},
			"right_arm": {"rotation": [(0, RA), (2.0, (23, 0, 10)), (4.0, RA)]},
			"left_arm": {"rotation": [(0, LA), (2.0, (18, 0, -10)), (4.0, LA)]},
			"right_forearm": {"rotation": [(0, (34, 0, 0)), (2.0, (40, 0, 0)), (4.0, (34, 0, 0))]},
			"left_forearm": {"rotation": [(0, (28, 0, 0)), (2.0, (33, 0, 0)), (4.0, (28, 0, 0))]},
			"right_leg": {"rotation": [(0, (12, 0, 0)), (4.0, (12, 0, 0))]},
			"right_shin": {"rotation": [(0, (-20, 0, 0)), (2.0, (-24, 0, 0)), (4.0, (-20, 0, 0))]},
			"left_leg": {"rotation": [(0, (-6, 0, 0)), (4.0, (-6, 0, 0))]},
			"left_shin": {"rotation": [(0, (-8, 0, 0)), (2.0, (-12, 0, 0)), (4.0, (-8, 0, 0))]},
			"cape_1": {"rotation": [(0, (-2, 0, 0)), (2.0, (-5, 0, 0)), (4.0, (-2, 0, 0))]},
			"cape_3": {"rotation": [(0, (-3, 0, 0)), (2.0, (-8, 0, 0)), (4.0, (-3, 0, 0))]},
			"tail_0": {"rotation": [(0, (0, -12, 0)), (2.0, (0, 14, 6)), (4.0, (0, -12, 0))]},
			"tail_2": {"rotation": [(0, (0, 10, -8)), (2.0, (0, -14, 10)), (4.0, (0, 10, -8))]},
			"tail_3": {"rotation": [(0, (0, 0, 10)), (1.0, (0, 0, -14)), (2.0, (0, 0, 12)), (3.0, (0, 0, -10)), (4.0, (0, 0, 10))]},
		}),
		anim("walk", 1.4, "loop", {
			"right_leg": {"rotation": [(0, (28, 0, 0)), (0.7, (-28, 0, 0)), (1.4, (28, 0, 0))]},
			"left_leg": {"rotation": [(0, (-28, 0, 0)), (0.7, (28, 0, 0)), (1.4, (-28, 0, 0))]},
			"right_shin": {"rotation": [(0, (-4, 0, 0)), (0.7, (-12, 0, 0)), (1.05, (-50, 0, 0)), (1.4, (-4, 0, 0))]},
			"left_shin": {"rotation": [(0, (-12, 0, 0)), (0.35, (-50, 0, 0)), (0.7, (-4, 0, 0)), (1.4, (-12, 0, 0))]},
			"right_arm": {"rotation": [(0, (10, 0, 8)), (0.7, (26, 0, 8)), (1.4, (10, 0, 8))]},
			"left_arm": {"rotation": [(0, (26, 0, -8)), (0.7, (4, 0, -8)), (1.4, (26, 0, -8))]},
			"waist": {"rotation": [(0, (0, 7, 0)), (0.7, (0, -7, 0)), (1.4, (0, 7, 0))]},
			"body": {"rotation": [(0, (-6, -5, 0)), (0.7, (-6, 5, 0)), (1.4, (-6, -5, 0))]},
			"cape_0": {"rotation": [(0, (-10, 0, 0)), (0.7, (-14, 0, 0)), (1.4, (-10, 0, 0))]},
			"cape_2": {"rotation": [(0, (-8, 0, 0)), (0.35, (-14, 0, 0)), (0.7, (-8, 0, 0)), (1.05, (-14, 0, 0)), (1.4, (-8, 0, 0))]},
			"tail_0": {"rotation": [(0, (0, -16, 0)), (0.7, (0, 16, 0)), (1.4, (0, -16, 0))]},
			"tail_2": {"rotation": [(0, (0, 14, 0)), (0.7, (0, -14, 0)), (1.4, (0, 14, 0))]},
			R: {"position": [(0, Z), (0.35, (0, 0.9, 0)), (0.7, Z), (1.05, (0, 0.9, 0)), (1.4, Z)]},
		}),
		# woken: hunched under the seal, he straightens, throws his arms wide and roars
		anim("wake", 3.0, "once", dict(
			{bone: {"rotation": [(0, rot), (1.2, rot), (2.0, Z), (3.0, Z)]} for bone, rot in hunched.items() if "arm" not in bone
				and bone not in ("body", "head")},
			**{
				"body": {"rotation": [(0, (-40, 0, 0)), (0.5, (-42, 2, 0)), (1.2, (-40, -2, 0)), (2.0, (0, 0, 0)), (2.3, (14, 0, 0)), (2.8, (14, 0, 0)), (3.0, Z)]},
				"head": {"rotation": [(0, (-30, 0, 0)), (1.2, (-30, 0, 0)), (2.0, (0, 0, 0)), (2.3, (28, 0, 0)), (2.5, (26, 6, 0)), (2.7, (28, -6, 0)), (3.0, Z)]},
				"right_arm": {"rotation": [(0, (10, 0, 4)), (1.2, (10, 0, 4)), (2.0, RA), (2.3, (50, 0, 75)), (2.8, (50, 0, 75)), (3.0, RA)]},
				"left_arm": {"rotation": [(0, (10, 0, -4)), (1.2, (10, 0, -4)), (2.0, LA), (2.3, (50, 0, -75)), (2.8, (50, 0, -75)), (3.0, LA)]},
				"cape_1": {"rotation": [(0, Z), (2.0, Z), (2.3, (-25, 0, 0)), (2.8, (-20, 0, 0)), (3.0, Z)]},
				"tail_0": {"rotation": [(0, Z), (2.0, Z), (2.3, (0, 0, 35)), (2.8, (0, 0, 30)), (3.0, Z)]},
				R: {"position": [(0, (0, -5, 0)), (1.2, (0, -5, 0)), (2.0, Z), (3.0, Z)]},
			})),
		# the cleave: both hands, up and over (lands at 0.65)
		anim("cleave", 1.2, "once", {
			"body": {"rotation": [(0, Z), (0.45, (14, 0, 0)), (0.65, (-32, 0, 0)), (0.9, (-30, 0, 0)), (1.2, Z)]},
			"head": {"rotation": [(0, Z), (0.45, (-10, 0, 0)), (0.65, (18, 0, 0)), (1.2, Z)]},
			"right_arm": {"rotation": [(0, RA), (0.45, (178, 0, 10)), (0.65, (42, 0, -6)), (0.9, (42, 0, -6)), (1.2, RA)]},
			"left_arm": {"rotation": [(0, LA), (0.45, (172, 0, -14)), (0.65, (48, 0, 16)), (0.9, (48, 0, 16)), (1.2, LA)]},
			"cape_1": {"rotation": [(0, Z), (0.65, (-30, 0, 0)), (1.2, Z)]},
			R: {"position": [(0, Z), (0.45, (0, 1, 0)), (0.65, (0, -2.5, 0)), (0.9, (0, -2.5, 0)), (1.2, Z)]},
		}),
		# the sweep: wound back to his right, through the whole front (lands at 0.5)
		anim("sweep", 1.0, "once", {
			"waist": {"rotation": [(0, Z), (0.3, (0, 18, 0)), (0.5, (0, -20, 0)), (1.0, Z)]},
			"body": {"rotation": [(0, Z), (0.3, (0, 50, 0)), (0.5, (-6, -60, 0)), (0.7, (-6, -55, 0)), (1.0, Z)]},
			"right_arm": {"rotation": [(0, RA), (0.3, (70, 0, 60)), (0.5, (72, 0, -30)), (0.7, (70, 0, -30)), (1.0, RA)]},
			"left_arm": {"rotation": [(0, LA), (0.3, (55, 0, -6)), (0.5, (62, 0, -40)), (1.0, LA)]},
			"tail_0": {"rotation": [(0, Z), (0.5, (0, 40, 0)), (1.0, Z)]},
		}),
		# three in a row: across, back across, then over (lands at 0.45, 1.05, 1.7)
		anim("combo", 2.3, "once", {
			"waist": {"rotation": [(0, Z), (0.25, (0, 18, 0)), (0.45, (0, -20, 0)), (0.85, (0, -22, 0)), (1.05, (0, 20, 0)), (1.4, Z), (2.3, Z)]},
			"body": {"rotation": [(0, Z), (0.25, (0, 50, 0)), (0.45, (-6, -60, 0)), (0.85, (-4, -62, 0)), (1.05, (-6, 55, 0)), (1.45, (14, 0, 0)), (1.7, (-32, 0, 0)),
				(2.0, (-30, 0, 0)), (2.3, Z)]},
			"right_arm": {"rotation": [(0, RA), (0.25, (70, 0, 60)), (0.45, (72, 0, -30)), (0.85, (74, 0, -34)), (1.05, (70, 0, 62)), (1.45, (178, 0, 10)),
				(1.7, (42, 0, -6)), (2.0, (42, 0, -6)), (2.3, RA)]},
			"left_arm": {"rotation": [(0, LA), (0.25, (55, 0, -6)), (0.45, (62, 0, -40)), (1.05, (50, 0, -10)), (1.45, (172, 0, -14)), (1.7, (48, 0, 16)),
				(2.0, (48, 0, 16)), (2.3, LA)]},
			"right_leg": {"rotation": [(0, Z), (0.45, (25, 0, 0)), (0.85, Z), (1.05, (-20, 0, 0)), (1.7, (25, 0, 0)), (2.3, Z)]},
			"left_leg": {"rotation": [(0, Z), (0.45, (-15, 0, 0)), (1.05, (25, 0, 0)), (1.7, (-15, 0, 0)), (2.3, Z)]},
			R: {"position": [(0, Z), (1.45, (0, 1, 0)), (1.7, (0, -2.5, 0)), (2.0, (0, -2.5, 0)), (2.3, Z)]},
		}),
		# set to rush: low, the shoulder forward, the axe trailing
		anim("rush_ready", 0.8, "hold", {
			"body": {"rotation": [(0, Z), (0.5, (-32, -14, 0)), (0.8, (-32, -14, 0))]},
			"head": {"rotation": [(0, Z), (0.5, (24, 10, 0)), (0.8, (24, 10, 0))]},
			"right_arm": {"rotation": [(0, RA), (0.5, (-35, 0, 22)), (0.8, (-35, 0, 22))]},
			"left_arm": {"rotation": [(0, LA), (0.5, (65, 0, 20)), (0.8, (65, 0, 20))]},
			"right_leg": {"rotation": [(0, Z), (0.5, (-25, 0, 0)), (0.8, (-25, 0, 0))]},
			"left_leg": {"rotation": [(0, Z), (0.5, (40, 0, 0)), (0.8, (40, 0, 0))]},
			"left_shin": {"rotation": [(0, Z), (0.5, (-50, 0, 0)), (0.8, (-50, 0, 0))]},
			R: {"position": [(0, Z), (0.5, (0, -3, 0)), (0.8, (0, -3, 0))]},
		}),
		anim("rush", 0.5, "loop", {
			"body": {"rotation": [(0, (-34, -14, 0)), (0.5, (-34, -14, 0))]},
			"head": {"rotation": [(0, (24, 10, 0)), (0.5, (24, 10, 0))]},
			"right_arm": {"rotation": [(0, (-35, 0, 22)), (0.5, (-35, 0, 22))]},
			"left_arm": {"rotation": [(0, (65, 0, 20)), (0.5, (65, 0, 20))]},
			"right_leg": {"rotation": [(0, (45, 0, 0)), (0.25, (-40, 0, 0)), (0.5, (45, 0, 0))]},
			"left_leg": {"rotation": [(0, (-40, 0, 0)), (0.25, (45, 0, 0)), (0.5, (-40, 0, 0))]},
			"right_shin": {"rotation": [(0, (-20, 0, 0)), (0.25, (-55, 0, 0)), (0.5, (-20, 0, 0))]},
			"left_shin": {"rotation": [(0, (-55, 0, 0)), (0.25, (-20, 0, 0)), (0.5, (-55, 0, 0))]},
			"cape_0": {"rotation": [(0, (-45, 0, 0)), (0.5, (-45, 0, 0))]},
			"cape_2": {"rotation": [(0, (-10, 0, 0)), (0.25, (-22, 0, 0)), (0.5, (-10, 0, 0))]},
			"tail_0": {"rotation": [(0, (0, -50, 30)), (0.5, (0, -50, 30))]},
			R: {"position": [(0, (0, -2.5, 0)), (0.12, (0, -1.5, 0)), (0.25, (0, -2.5, 0)), (0.37, (0, -1.5, 0)), (0.5, (0, -2.5, 0))]},
		}),
		anim("roar", 1.6, "once", {
			"body": {"rotation": [(0, Z), (0.3, (-10, 0, 0)), (0.6, (16, 0, 0)), (1.3, (14, 0, 0)), (1.6, Z)]},
			"head": {"rotation": [(0, Z), (0.3, (-14, 0, 0)), (0.6, (30, 0, 0)), (0.8, (28, 7, 0)), (1.0, (30, -7, 0)), (1.3, (28, 0, 0)), (1.6, Z)]},
			"right_arm": {"rotation": [(0, RA), (0.3, (30, 0, 0)), (0.6, (55, 0, 78)), (1.3, (55, 0, 75)), (1.6, RA)]},
			"left_arm": {"rotation": [(0, LA), (0.3, (30, 0, 0)), (0.6, (55, 0, -78)), (1.3, (55, 0, -75)), (1.6, LA)]},
			"beard": {"rotation": [(0, Z), (0.6, (12, 0, 0)), (0.8, (6, 0, 0)), (1.0, (12, 0, 0)), (1.3, (8, 0, 0)), (1.6, Z)]},
			"cape_1": {"rotation": [(0, Z), (0.6, (-28, 0, 0)), (1.3, (-22, 0, 0)), (1.6, Z)]},
			"tail_0": {"rotation": [(0, Z), (0.6, (0, 0, 38)), (1.3, (0, 0, 34)), (1.6, Z)]},
			"tail_2": {"rotation": [(0, Z), (0.6, (0, 0, -24)), (1.6, Z)]},
		}),
		# the windows: the axe buried after a rush, and spent after three blows
		anim("buried", 2.5, "once", held(spent, 0.25, 2.0, 2.5, -3)),
		anim("spent", 1.5, "once", held(spent, 0.25, 1.1, 1.5, -3)),
		# the tail's lash: a full turn, the tail straight out (lands at 0.4)
		anim("tail_lash", 0.8, "once", {
			R: {"rotation": [(0, Z), (0.15, (0, -50, 0)), (0.5, (0, 300, 0)), (0.8, (0, 360, 0))]},
			"body": {"rotation": [(0, Z), (0.15, (-10, 0, 0)), (0.5, (-14, 0, 0)), (0.8, Z)]},
			"tail_0": {"rotation": [(0, Z), (0.15, (0, 0, 40)), (0.5, (0, 0, 40)), (0.8, Z)]},
			"tail_1": {"rotation": [(0, Z), (0.15, (0, 0, 25)), (0.5, (0, 0, 25)), (0.8, Z)]},
			"tail_2": {"rotation": [(0, Z), (0.15, (0, 0, -20)), (0.5, (0, 0, -20)), (0.8, Z)]},
			"tail_3": {"rotation": [(0, Z), (0.15, (0, 0, -30)), (0.5, (0, 0, -30)), (0.8, Z)]},
			"right_arm": {"rotation": [(0, RA), (0.3, (30, 0, 40)), (0.8, RA)]},
			"left_arm": {"rotation": [(0, LA), (0.3, (30, 0, -40)), (0.8, LA)]},
		}),
		# down on his knees, then on his face
		anim("death", 2.6, "hold", {
			"body": {"rotation": [(0, Z), (0.9, (-28, 0, 0)), (1.6, (-34, 4, 0)), (2.3, (-10, 0, 0)), (2.6, (-10, 0, 0))]},
			"head": {"rotation": [(0, Z), (0.4, (20, 0, 0)), (0.9, (-30, 0, 0)), (2.6, (-20, 0, 14))]},
			"right_arm": {"rotation": [(0, RA), (0.9, (8, 0, 6)), (2.3, (60, 0, 30)), (2.6, (60, 0, 30))]},
			"left_arm": {"rotation": [(0, LA), (0.9, (8, 0, -6)), (2.3, (70, 0, -30)), (2.6, (70, 0, -30))]},
			"right_leg": {"rotation": [(0, Z), (0.9, (38, 0, 0)), (1.6, (38, 0, 0)), (2.3, (4, 0, 0)), (2.6, (4, 0, 0))]},
			"left_leg": {"rotation": [(0, Z), (0.9, (38, 0, 0)), (1.6, (38, 0, 0)), (2.3, (4, 0, 0)), (2.6, (4, 0, 0))]},
			"right_shin": {"rotation": [(0, Z), (0.9, (-100, 0, 0)), (1.6, (-100, 0, 0)), (2.3, (-10, 0, 0)), (2.6, (-10, 0, 0))]},
			"left_shin": {"rotation": [(0, Z), (0.9, (-100, 0, 0)), (1.6, (-100, 0, 0)), (2.3, (-10, 0, 0)), (2.6, (-10, 0, 0))]},
			"cape_0": {"rotation": [(0, Z), (0.9, (-20, 0, 0)), (1.6, (-25, 0, 0)), (2.3, (-4, 0, 0)), (2.6, (-2, 0, 0))]},
			"tail_0": {"rotation": [(0, Z), (0.9, (0, 0, -20)), (2.6, (0, 0, 30))]},
			R: {"position": [(0, Z), (0.9, (0, -9, 0)), (1.6, (0, -9, 0)), (2.3, (0, 3, 0)), (2.6, (0, 3, 0))],
				"rotation": [(0, Z), (1.6, Z), (2.3, (-86, 0, 0)), (2.6, (-86, 0, 0))]},
		}),
	]
	bend(anims)
	import humanoid_moves
	own = {a["name"] for a in anims}
	anims += [a for a in humanoid_moves.moves(leg=21.0, cape=True) if a["name"] not in own]
	# (the library's cloak has ribbons his shroud doesn't)
	for a in anims:
		a["tracks"] = {bone: t for bone, t in a["tracks"].items() if bone in b.bones}
	add_animations(model, anims, bone_uuids_of(model))
	return model


# ---------------------------------------------------------------- the barrow beetle (the example mob)

def beetle():
	b = Builder(64, 64, 5)
	b.bone("root", None, (0, 0, 0))
	b.bone("body", "root", (0, 3, 0))
	b.cube("body", (-3, 2, -3), (3, 5, 4), "shell")
	b.bone("head", "body", (0, 3.5, -3))
	b.cube("head", (-1.5, 2, -5.5), (1.5, 4.5, -3), "leg", faces_paint={"north": eyes(PALETTE["eye"], (0, 1))})
	mirror(b, "antenna", "head", (1, 4.5, -5.5), [((0.5, 4.5, -8.5), (1.0, 5.0, -5.5), "leg")], (20, 0, 0))
	b.bone("shell", "body", (0, 5, -2.5))
	mirror(b, "wing", "shell", (0.05, 5, -2.5), [((0.05, 4.8, -2.8), (3.4, 6.2, 4.6), "shell")])
	for i, z in enumerate((-2, 0.5, 3)):
		mirror(b, f"leg{i}", "body", (2.8, 2.6, z), [((2.8, 0, z - 0.35), (4.3, 2.6, z + 0.35), "leg")], (0, 0, 20))
	model = b.project("barrow_beetle")
	leg = {}
	for i, ph in enumerate((0, 0.15, 0.3)):
		for side, s in (("right", 1), ("left", -1)):
			sw = 25 if (i + (side == "left")) % 2 else -25
			leg[f"{side}_leg{i}"] = {"rotation": [(0, (0, sw, 20 * s)), (0.15, (0, -sw, 20 * s)), (0.3, (0, sw, 20 * s))]}
	anims = [
		anim("idle", 2.0, "loop", {
			"right_antenna": {"rotation": [(0, (20, 0, 0)), (1.0, (35, 10, 0)), (2.0, (20, 0, 0))]},
			"left_antenna": {"rotation": [(0, (20, 0, 0)), (1.0, (30, -10, 0)), (2.0, (20, 0, 0))]},
			"body": {"position": [(0, Z), (1.0, (0, 0.15, 0)), (2.0, Z)]},
		}),
		anim("walk", 0.3, "loop", dict(leg, body={"rotation": [(0, (0, 4, 0)), (0.15, (0, -4, 0)), (0.3, (0, 4, 0))]})),
		anim("flutter", 0.6, "once", {
			"right_wing": {"rotation": [(0, Z), (0.15, (-30, 0, -40)), (0.3, (-10, 0, -10)), (0.45, (-30, 0, -40)), (0.6, Z)]},
			"left_wing": {"rotation": [(0, Z), (0.15, (-30, 0, 40)), (0.3, (-10, 0, 10)), (0.45, (-30, 0, 40)), (0.6, Z)]},
		}),
	]
	add_animations(model, anims, bone_uuids_of(model))
	return model


# ---------------------------------------------------------------- new moves for the borrowed models

def silver_skeleton():
	with open(os.path.join(SRC, "dead-traveler-skeleton.bbmodel"), encoding="utf-8") as f:
		model = json.load(f)
	rise = anim("rise", 2.0, "once", {
		"root": {"rotation": [(0, (90, 0, 0)), (0.45, (88, 0, 0)), (0.95, (45, 0, 0)), (1.35, (8, 0, 0)), (1.6, (0, 0, 0)), (2.0, Z)],
				 "position": [(0, (0, 2, 0)), (0.95, (0, 1, 0)), (1.35, Z), (2.0, Z)]},
		"waist": {"rotation": [(0, Z), (0.95, (-25, 0, 0)), (1.35, (-15, 0, 0)), (1.6, (-5, 0, 0)), (2.0, Z)]},
		"head": {"rotation": [(0, (-20, 0, 0)), (0.45, (-35, 10, 0)), (0.95, (-10, 0, 0)), (1.6, (5, -20, 0)), (1.8, (5, 20, 0)), (2.0, Z)]},
		"lower_jaw": {"rotation": [(0, Z), (1.6, (25, 0, 0)), (1.8, (5, 0, 0)), (2.0, Z)]},
		"right_arm": {"rotation": [(0, (10, 0, 0)), (0.45, (155, 0, 10)), (0.95, (70, 0, 20)), (1.35, (20, 0, 10)), (2.0, Z)]},
		"left_arm": {"rotation": [(0, (10, 0, 0)), (0.3, (20, 0, -10)), (0.95, (60, 0, -25)), (1.35, (15, 0, -10)), (2.0, Z)]},
		"right_leg": {"rotation": [(0, Z), (0.95, (60, 0, 0)), (1.35, (20, 0, 0)), (2.0, Z)]},
		"left_leg": {"rotation": [(0, Z), (0.95, (45, 0, 0)), (1.35, (10, 0, 0)), (2.0, Z)]},
	})
	add_animations(model, [rise], bone_uuids_of(model))
	return model


def skeleton_lord():
	with open(os.path.join(SRC, "skeleton-lord.bbmodel"), encoding="utf-8") as f:
		model = json.load(f)
	swing = anim("staff_swing", 1.0, "once", {
		"chest": {"rotation": [(0, Z), (0.3, (5, 35, 0)), (0.45, (-15, -30, 0)), (1.0, Z)]},
		"waist": {"rotation": [(0, Z), (0.3, (0, 10, 0)), (0.45, (-5, -10, 0)), (1.0, Z)]},
		"L_arm": {"rotation": [(0, Z), (0.3, (150, 0, -25)), (0.45, (40, 0, -10)), (1.0, Z)]},
		"L_lowerarm": {"rotation": [(0, Z), (0.3, (40, 0, 0)), (0.45, (10, 0, 0)), (1.0, Z)]},
		"R_arm": {"rotation": [(0, Z), (0.3, (-20, 0, 20)), (0.45, (30, 0, 10)), (1.0, Z)]},
		"head": {"rotation": [(0, Z), (0.3, (0, -20, 0)), (0.45, (5, 15, 0)), (1.0, Z)]},
	})
	slam = anim("slam", 1.5, "once", {
		"skeleton": {"position": [(0, Z), (0.5, (0, 1, 0)), (0.75, (0, -5, 0)), (1.1, (0, -5, 0)), (1.5, Z)]},
		"chest": {"rotation": [(0, Z), (0.5, (15, 0, 0)), (0.75, (-35, 0, 0)), (1.1, (-30, 0, 0)), (1.5, Z)]},
		"waist": {"rotation": [(0, Z), (0.5, (5, 0, 0)), (0.75, (-12, 0, 0)), (1.1, (-10, 0, 0)), (1.5, Z)]},
		"L_arm": {"rotation": [(0, Z), (0.5, (150, 0, -5)), (0.75, (30, 0, 5)), (1.1, (28, 0, 5)), (1.5, Z)]},
		"R_arm": {"rotation": [(0, Z), (0.5, (150, 0, 5)), (0.75, (40, 0, -5)), (1.1, (38, 0, -5)), (1.5, Z)]},
		"L_leg": {"rotation": [(0, Z), (0.75, (-30, 0, 0)), (1.1, (-30, 0, 0)), (1.5, Z)]},
		"L_lowerleg": {"rotation": [(0, Z), (0.75, (45, 0, 0)), (1.1, (45, 0, 0)), (1.5, Z)]},
		"R_leg": {"rotation": [(0, Z), (0.75, (-30, 0, 0)), (1.1, (-30, 0, 0)), (1.5, Z)]},
		"R_lowerleg": {"rotation": [(0, Z), (0.75, (45, 0, 0)), (1.1, (45, 0, 0)), (1.5, Z)]},
		"jaw": {"rotation": [(0, Z), (0.75, (25, 0, 0)), (1.1, (10, 0, 0)), (1.5, Z)]},
	})
	call = anim("grave_call", 2.0, "once", {
		"chest": {"rotation": [(0, Z), (0.6, (15, 0, 0)), (1.2, (18, 0, 0)), (1.6, (-12, 0, 0)), (2.0, Z)]},
		"head": {"rotation": [(0, Z), (0.6, (25, 0, 0)), (1.2, (28, 0, 0)), (1.6, (-5, 0, 0)), (2.0, Z)]},
		"jaw": {"rotation": [(0, Z), (0.6, (30, 0, 0)), (0.9, (15, 0, 0)), (1.2, (30, 0, 0)), (1.6, (5, 0, 0)), (2.0, Z)]},
		"L_arm": {"rotation": [(0, Z), (0.6, (165, 0, -10)), (1.2, (168, 0, -12)), (1.6, (60, 0, -5)), (2.0, Z)]},
		"R_arm": {"rotation": [(0, Z), (0.6, (20, 0, 75)), (1.2, (-10, 0, 80)), (1.6, (40, 0, 30)), (2.0, Z)]},
		"R_hand": {"rotation": [(0, Z), (0.6, (0, 0, -30)), (1.2, (0, 0, -40)), (2.0, Z)]},
	})
	add_animations(model, [swing, slam, call], bone_uuids_of(model))
	return model


EGGS = {"draugr": ((84, 94, 104), (160, 232, 255)), "barrow_beetle": ((36, 50, 44), (120, 160, 140)),
		"bogre": ((92, 110, 70), (150, 112, 74)), "laughing_bones": ((200, 196, 180), (180, 140, 255)),
		"running_skull": ((224, 220, 204), (60, 40, 90)), "raid_king": ((70, 78, 90), (200, 160, 60))}


def eggs():
	"""Spawn eggs: the Skeleton Lord's egg's shape, shaded in each mob's two colours (base, spots)."""
	assets = os.path.join(ROOT, "kingdoms", "src", "main", "resources", "assets", "jaymoskingdoms")
	src = Image.open(os.path.join(assets, "textures", "item", "skeleton_lord_spawn_egg.png")).convert("RGBA")
	for name, (base, spots) in EGGS.items():
		img = Image.new("RGBA", src.size)
		for y in range(src.height):
			for x in range(src.width):
				r, g, b, a = src.getpixel((x, y))
				if a == 0:
					continue
				lum = (r * 0.3 + g * 0.59 + b * 0.11) / 255.0
				spot = (x * 7 + y * 13) % 11 < 2 and 0.35 < lum < 0.9
				img.putpixel((x, y), shade(spots if spot else base, 0.55 + lum * 0.9) + (a,))
		img.save(os.path.join(assets, "textures", "item", name + "_spawn_egg.png"))
		with open(os.path.join(assets, "items", name + "_spawn_egg.json"), "w", encoding="utf-8", newline="\n") as f:
			f.write('{\n\t"model": {\n\t\t"type": "minecraft:model",\n\t\t"model": "jaymoskingdoms:item/%s_spawn_egg"\n\t}\n}\n' % name)
		with open(os.path.join(assets, "models", "item", name + "_spawn_egg.json"), "w", encoding="utf-8", newline="\n") as f:
			f.write('{\n\t"parent": "minecraft:item/generated",\n\t"textures": {\n\t\t"layer0": "jaymoskingdoms:item/%s_spawn_egg"\n\t}\n}\n'
					% name)
		print("wrote the " + name + " spawn egg")


def main(only=()):
	"""Writes every project, or only those named on the command line (and the spawn eggs only with all of them)."""
	if not only:
		eggs()
	os.makedirs(ART, exist_ok=True)
	for name, make in (("draugr", draugr), ("raid_king", raid_king), ("barrow_beetle", beetle), ("silver_skeleton", silver_skeleton), ("skeleton_lord", skeleton_lord)):
		if only and name not in only:
			continue
		with open(os.path.join(ART, name + ".bbmodel"), "w", encoding="utf-8", newline="\n") as f:
			json.dump(make(), f)
		if name == "draugr":
			draugr_looks()
		print("wrote kingdoms/art/" + name + ".bbmodel")


if __name__ == "__main__":
	main(sys.argv[1:])
