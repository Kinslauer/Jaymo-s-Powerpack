"""
The film actor: the one GeckoLib body every person in a lore film is played by (client/films/LoreFilmClient), with
elbows and knees, wearing an ordinary 64x64 skin (textures/entity/film/<skin>.png, tools/gen_film_skins.py), so any
skin of the humanoid layout fits it. Our own cubes; the animations are humanoid_moves.py's (shared with the draugr).

Three bodies, one set of animations (the bones are named alike):
- film_actor: wide arms. An orc's features hang off its head as two bones, orc_0 (tusks up from the jaw, a heavy brow,
  small swept ears) and orc_1 (an underbite with long tusks, a heavier brow, one ear torn short), after
  client/GreenskinLooks' shapes; they're painted from two patches the orc skins carry in the layout's unused corner
  (flesh at 56,16 and ivory at 56,24) and hidden on everyone else.
Every body also has the fifth's cloak (cape_0-3), the same cloak in three ribbons (ribbon_0-2_0-3) and its five cords
(tether_0-4), hidden on everyone but him.
- film_actor_slim: 3-wide arms (Thessaly).
- film_actor_piglin: a piglin's head (10 wide, snout, tusks, ears), wearing the game's own zombified piglin texture.

Each limb is cut in two at the joint and takes the two halves of its part of the skin; the cut ends are capped from
the rows either side of the cut.

Blockbench space: y up, feet at 0, the front at -z, the model's right at +x (gen_tomb_undead.py's notes).

Run from the project root: py -3 -I kingdoms/tools/gen_film_actor.py   (needs Pillow)
Writes kingdoms/art/film_actor*.bbmodel, geckolib/models/entity/film_actor*.geo.json and
geckolib/animations/entity/film_actor.animation.json.
"""
import base64
import io
import json
import math
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(ROOT, "tools"))

import bbmodel_geckolib  # noqa: E402
import humanoid_moves  # noqa: E402
from gen_tomb_undead import add_animations, bone_uuids_of, uid  # noqa: E402

ART = os.path.join(ROOT, "kingdoms", "art")
ASSETS = os.path.join(ROOT, "kingdoms", "src", "main", "resources", "assets", "jaymoskingdoms")
SKIN = os.path.join(ASSETS, "textures", "entity", "film", "varro.png")
Z = (0, 0, 0)
# the orc skins' two patches (tools/gen_film_skins.py paints them): flesh, ivory
FLESH, IVORY = (56, 16), (56, 24)
# and the fifth's cloak cloth
CLOTH = (56, 32)


class Rig:
	def __init__(self):
		self.elements, self.outliner, self.bones = [], [], {}

	def bone(self, name, parent, origin, rotation=Z):
		node = {"name": name, "origin": list(origin), "rotation": list(rotation), "uuid": uid(), "export": True, "isOpen": True,
				"color": 0, "children": []}
		self.bones[name] = node
		(self.bones[parent]["children"] if parent else self.outliner).append(node)

	def cube(self, bone, frm, to, faces, inflate=0.0, rotation=Z, origin=None):
		out = {}
		for fn, (x0, y0, x1, y1) in faces.items():
			out[fn] = {"uv": [x0, y1, x1, y0] if fn == "down" else [x0, y0, x1, y1], "texture": 0}
		e = {"name": bone, "box_uv": False, "rescale": False, "locked": False, "render_order": "default",
			 "allow_mirror_modeling": True, "from": list(frm), "to": list(to), "autouv": 0, "color": 0, "inflate": inflate,
			 "origin": list(origin or self.bones[bone]["origin"]), "rotation": list(rotation), "faces": out, "type": "cube", "uuid": uid()}
		self.elements.append(e)
		self.bones[bone]["children"].append(e["uuid"])

	def project(self, name, skin):
		buf = io.BytesIO()
		skin.save(buf, "PNG")
		src = "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()
		return {"meta": {"format_version": "4.10", "model_format": "free", "box_uv": False}, "name": name, "model_identifier": name,
				"resolution": {"width": 64, "height": 64}, "elements": self.elements, "outliner": self.outliner,
				"textures": [{"path": "", "name": name + ".png", "folder": "", "namespace": "", "id": "0", "width": 64, "height": 64,
							  "uv_width": 64, "uv_height": 64, "particle": False, "render_mode": "default", "visible": True,
							  "mode": "bitmap", "saved": True, "uuid": uid(), "source": src}], "animations": []}


def skin_box(u, v, w, h, d, r0=0, r1=None):
	"""
	The faces of rows r0..r1 (from the top) of the skin's box at (u, v), w wide, h high, d deep: the game's own layout
	(the top and bottom over the front, then the body's right side, front, left side and back in a row). A cut end takes
	the two rows of the front beside the cut.
	"""
	r1 = h if r1 is None else r1
	top, bot = v + d + r0, v + d + r1
	faces = {"east": (u, top, u + d, bot), "north": (u + d, top, u + d + w, bot), "west": (u + d + w, top, u + 2 * d + w, bot),
			 "south": (u + 2 * d + w, top, u + 2 * d + 2 * w, bot)}
	faces["up"] = (u + d, v, u + d + w, v + d) if r0 == 0 else (u + d, top - 1, u + d + w, top + 1)
	faces["down"] = (u + d + w, v, u + d + 2 * w, v + d) if r1 == h else (u + d, bot - 1, u + d + w, bot + 1)
	return faces


def patch(at, w=2, h=2):
	return {fn: (at[0] + 1, at[1] + 1, at[0] + 1 + w, at[1] + 1 + h) for fn in ("north", "south", "east", "west", "up", "down")}


def limb(r, name, lower, hand, parent, x0, x1, top, mid, base, overlay, w, pivot_x, sign):
	"""A limb in two halves: `name` from top down to mid, `lower` from mid down, a `hand` (or foot) bone at its end."""
	h = top - (mid - (top - mid))
	r.bone(name, parent, (pivot_x, top - (2 if hand else 0), 0))
	r.cube(name, (x0, mid, -2), (x1, top, 2), skin_box(*base, w, h, 4, 0, top - mid))
	r.cube(name, (x0, mid, -2), (x1, top, 2), skin_box(*overlay, w, h, 4, 0, top - mid), 0.25)
	r.bone(lower, name, ((x0 + x1) / 2, mid, 0))
	r.cube(lower, (x0, mid - (top - mid), -2), (x1, mid, 2), skin_box(*base, w, h, 4, top - mid, h))
	r.cube(lower, (x0, mid - (top - mid), -2), (x1, mid, 2), skin_box(*overlay, w, h, 4, top - mid, h), 0.25)
	if hand:
		# what it holds hangs off this: in the fist, a little above the arm's end
		r.bone(hand, lower, ((x0 + x1) / 2, mid - (top - mid) + 1.5, -0.5))


def body(arm=4, piglin=False):
	r = Rig()
	r.bone("root", None, (0, 0, 0))
	limb(r, "right_leg", "right_shin", None, "root", 0, 4, 12, 6, (0, 16), (0, 32), 4, 2, 1)
	limb(r, "left_leg", "left_shin", None, "root", -4, 0, 12, 6, (16, 48), (0, 48), 4, -2, -1)
	# the trunk in two, so it can bend: the hips, and the chest over them
	r.bone("waist", "root", (0, 12, 0))
	r.cube("waist", (-4, 12, -2), (4, 15, 2), skin_box(16, 16, 8, 12, 4, 9, 12))
	r.cube("waist", (-4, 12, -2), (4, 15, 2), skin_box(16, 32, 8, 12, 4, 9, 12), 0.25)
	r.bone("body", "waist", (0, 15, 0))
	r.cube("body", (-4, 15, -2), (4, 24, 2), skin_box(16, 16, 8, 12, 4, 0, 9))
	r.cube("body", (-4, 15, -2), (4, 24, 2), skin_box(16, 32, 8, 12, 4, 0, 9), 0.25)
	r.bone("head", "body", (0, 24, 0))
	if piglin:
		# the game's piglin head (PiglinModel): 10 wide, a snout, two tusks, ears that hang out to the sides
		r.cube("head", (-5, 24, -4), (5, 32, 4), skin_box(0, 0, 10, 8, 8))
		r.cube("head", (-2, 24, -5), (2, 28, -4), skin_box(31, 1, 4, 4, 1))
		r.cube("head", (-3, 24, -5), (-2, 26, -4), skin_box(2, 4, 1, 2, 1))
		r.cube("head", (2, 24, -5), (3, 26, -4), skin_box(2, 0, 1, 2, 1))
		r.bone("left_ear", "head", (-4.5, 30, 0), (0, 0, -30))
		r.cube("left_ear", (-5.5, 25, -2), (-4.5, 30, 2), skin_box(51, 6, 1, 5, 4))
		r.bone("right_ear", "head", (4.5, 30, 0), (0, 0, 30))
		r.cube("right_ear", (4.5, 25, -2), (5.5, 30, 2), skin_box(39, 6, 1, 5, 4))
	else:
		r.cube("head", (-4, 24, -4), (4, 32, 4), skin_box(0, 0, 8, 8, 8))
		r.cube("head", (-4, 24, -4), (4, 32, 4), skin_box(32, 0, 8, 8, 8), 0.5)
		if arm == 4:
			sweep = 25.8
			r.bone("orc_0", "head", (0, 24, 0))
			r.cube("orc_0", (2, 25, -5), (3, 27, -4), patch(IVORY))
			r.cube("orc_0", (-3, 25, -5), (-2, 27, -4), patch(IVORY))
			r.cube("orc_0", (-4, 29, -5), (4, 30, -4), patch(FLESH, 6, 1))
			r.cube("orc_0", (4, 27, -0.5), (5, 30, 1.5), patch(FLESH), 0, (0, sweep, 0), (4, 28.5, -0.5))
			r.cube("orc_0", (-5, 27, -0.5), (-4, 30, 1.5), patch(FLESH), 0, (0, -sweep, 0), (-4, 28.5, -0.5))
			r.bone("orc_1", "head", (0, 24, 0))
			r.cube("orc_1", (-3, 23.5, -5), (3, 25.5, -4), patch(FLESH, 6, 2))
			r.cube("orc_1", (2, 25, -5.5), (3, 28, -4.5), patch(IVORY))
			r.cube("orc_1", (-3, 25, -5.5), (-2, 28, -4.5), patch(IVORY))
			r.cube("orc_1", (-4, 28.5, -5), (4, 30.5, -4), patch(FLESH, 6, 2))
			r.cube("orc_1", (4, 27.5, -0.5), (5, 29.5, 1.5), patch(FLESH), 0, (0, sweep, 0), (4, 28.5, -0.5))
			r.cube("orc_1", (-5, 27, -0.5), (-4, 30, 1.5), patch(FLESH), 0, (0, -sweep, 0), (-4, 28.5, -0.5))
	limb(r, "right_arm", "right_forearm", "right_hand", "body", 4, 4 + arm, 24, 18, (40, 16), (40, 32), arm, 5, 1)
	limb(r, "left_arm", "left_forearm", "left_hand", "body", -4 - arm, -4, 24, 18, (32, 48), (48, 48), arm, -5, -1)
	# the fifth's cloak (hidden on everyone else): four lengths down the back, each hinged on the one above, wider
	# toward the hem, painted from the cloth patch his skin carries (56,32, 8 by 16)
	parent, y = "body", 24.0
	for i, (drop, half) in enumerate(((6.0, 4.6), (6.0, 5.2), (6.0, 5.8), (5.0, 6.4))):
		r.bone("cape_%d" % i, parent, (0, y, 2.4))
		v0 = CLOTH[1] + i * 4
		cloth = {"north": (CLOTH[0], v0, CLOTH[0] + 8, v0 + 4), "south": (CLOTH[0], v0, CLOTH[0] + 8, v0 + 4)}
		for fn in ("east", "west", "up", "down"):
			cloth[fn] = (CLOTH[0], v0, CLOTH[0] + 1, v0 + 4)
		r.cube("cape_%d" % i, (-half, y - drop, 2.4), (half, y, 3.0), cloth)
		parent, y = "cape_%d" % i, y - drop
	# the same cloak come apart: three ribbons of it side by side, each hinged like the cloak (ribbon_<j>_<i>). The renderer
	# draws these in place of the whole cloak while it is at work (FilmActorRenderer)
	for j in range(3):
		parent, y = "body", 24.0
		for i, (drop, half) in enumerate(((6.0, 4.6), (6.0, 5.2), (6.0, 5.8), (5.0, 6.4))):
			x0, x1 = -half + j * half * 2.0 / 3.0 + 0.15, -half + (j + 1) * half * 2.0 / 3.0 - 0.15
			name = "ribbon_%d_%d" % (j, i)
			r.bone(name, parent, ((x0 + x1) / 2.0, y, 2.4))
			v0 = CLOTH[1] + i * 4
			cloth = {"north": (CLOTH[0] + j * 2, v0, CLOTH[0] + j * 2 + 3, v0 + 4), "south": (CLOTH[0] + j * 2, v0, CLOTH[0] + j * 2 + 3, v0 + 4)}
			for fn in ("east", "west", "up", "down"):
				cloth[fn] = (CLOTH[0], v0, CLOTH[0] + 1, v0 + 4)
			r.cube(name, (x0, y - drop, 2.4), (x1, y, 3.0), cloth)
			parent, y = name, y - drop
	# and its tethers: four cords that come off the shoulders of the cloak on their own. Each is one block long, lying
	# forward from its root (hung on a bone of their own that no move turns, so a flip or a kneel doesn't swing them off
	# what they hold); the renderer turns it at what it has hold of and stretches it there (FilmActorRenderer)
	r.bone("cords", None, (0, 0, 0))
	# (Andy: "Two or three strand of the cloak should extend at a time, so the line is thicker and corded, theres should
	# also be a maximum of five ... cords woven out of the robe".) Each cord is CORD_STRANDS strands wound round one
	# another, CORD_TURN degrees further round at each of its CORD_LINKS links, CORD_WIDE out from its middle
	for i, (x, y) in enumerate(CORDS):
		r.bone("tether_%d" % i, "cords", (x, y, 2.0))
		cord = {fn: (CLOTH[0], CLOTH[1], CLOTH[0] + 8, CLOTH[1] + 1) for fn in ("east", "west", "up", "down")}
		cord["north"] = cord["south"] = (CLOTH[0], CLOTH[1], CLOTH[0] + 1, CLOTH[1] + 1)
		link = 16.0 / CORD_LINKS
		for k in range(CORD_LINKS):
			for strand in range(CORD_STRANDS):
				a = math.radians(k * CORD_TURN + strand * 360.0 / CORD_STRANDS)
				cx, cy = x + CORD_WIDE * math.cos(a), y + CORD_WIDE * math.sin(a)
				r.cube("tether_%d" % i, (cx - CORD_STRAND / 2, cy - CORD_STRAND / 2, 2.0 - (k + 1) * link), (cx + CORD_STRAND / 2, cy + CORD_STRAND / 2, 2.0 - k * link), cord)
	return r


# the cloak's cords: where each comes out of it (x, y), and how each is wound
CORDS = ((3.6, 24.6), (-3.6, 24.6), (4.4, 21.0), (-4.4, 21.0), (0.0, 25.0))
CORD_STRANDS, CORD_LINKS, CORD_TURN, CORD_WIDE, CORD_STRAND = 3, 8, 45.0, 0.62, 0.8


def main():
	skin = Image.open(SKIN).convert("RGBA")
	os.makedirs(ART, exist_ok=True)
	for name, rig in (("film_actor", body()), ("film_actor_slim", body(arm=3)), ("film_actor_piglin", body(piglin=True))):
		model = rig.project(name, skin)
		add_animations(model, humanoid_moves.moves(leg=12.0), bone_uuids_of(model))
		with open(os.path.join(ART, name + ".bbmodel"), "w", encoding="utf-8", newline="\n") as f:
			json.dump(model, f)
		geo, anim, _ = bbmodel_geckolib.convert(model, name)
		path = os.path.join(ASSETS, "geckolib", "models", "entity", name + ".geo.json")
		with open(path, "w", encoding="utf-8", newline="\n") as f:
			json.dump(geo, f, separators=(",", ":"))
		if name == "film_actor":
			with open(os.path.join(ASSETS, "geckolib", "animations", "entity", name + ".animation.json"), "w", encoding="utf-8", newline="\n") as f:
				json.dump(anim, f, indent="\t")
		print("wrote", name, "(%d animations)" % len(anim["animations"]))


if __name__ == "__main__":
	main()
