"""
The flintlock guns' GeckoLib models and animations: WeaponMod's musket (and its bayonet muskets), its blunderbuss and its
flintlock pistol, and Arsenal's Void Flintlock. Each gun is drawn in the hand by GeckoLib (client/armory/guns), so its
hammer, ramrod and muzzle flash move: drawn, held, aimed, fired, reloaded, spent.

- musket: 9greenworks' "Flintlock Musket Rifle" (Blockbench Workshop, CC BY 4.0; tools/bbmodels/), its bones renamed,
  its cartridge left out, a socket bayonet added (shown on the bayonet muskets only, a blade of the tier's metal), and
  its own reload kept (sped up to WeaponMod's reload). Fire, draw, aim and spent are ours.
- flintlock: the "Dueling Pistol" (Blockbench Workshop, CC BY 4.0), cut into body, hammer and ramrod. The Void Flintlock
  draws the same pistol in void colours.
- blunderbuss: the Firecannon from cleannrooster's Frontier Armaments (MIT; tools/bbmodels/frontier-armaments/), a
  brass-banded barrel with a bell mouth, its hammer cut into its own bone, its breech gem painted steel, a ramrod added.

Each gun gets a muzzle flash bone (our flash picture, drawn full-bright by a glow layer) that only shows while it fires.

Writes for each gun:
  art/guns/<gun>.bbmodel                      the editable Blockbench project (open it in Blockbench)
  assets/jaymospowers/geckolib/models/entity/gun_<gun>.geo.json, .../animations/entity/gun_<gun>.animation.json
  assets/jaymospowers/textures/entity/gun_<gun>.png (+ _glow.png, and the variants: musket bayonet tiers, void pistol)
  the held_guns item definitions that draw them (animated_gun, with the matrix that lays the model where the old static
  model lay, so the hand-held placements tuned for it still fit), and the blunderbuss's static model (its base).

Keyframes are the game's way (degrees as ModelPart angles), as a "free" project stores them; tools/bbmodel_geckolib.py
turns them round for GeckoLib. Check poses with tools/bbmodel_bake.py and tools/bbmodel_preview.py.

Run from the project root: python3 -I tools/gen_guns_geckolib.py
"""
import base64
import copy
import io
import json
import math
import os
import random
import subprocess
import sys
import uuid

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "tools", "blockbench"))
from freeform import convert as freeform_convert  # noqa: E402

BBMODELS = os.path.join(ROOT, "tools", "bbmodels")
ART = os.path.join(ROOT, "art", "guns")
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "jaymospowers")
HELD = os.path.join(ROOT, "src", "main", "resources", "resourcepacks", "held_guns", "assets")
R2 = 1 / math.sqrt(2)

# WeaponMod's reload times (its config's defaults, seconds): each reload animation is this long, so it ends as the gun does
RELOAD = {"musket": 1.5, "blunderbuss": 1.0, "flintlock": 0.75}
BAYONET_TIERS = {
	# blade light, mid, dark
	"wood": ((176, 132, 80), (139, 100, 56), (96, 66, 34)),
	"stone": ((160, 160, 160), (122, 122, 122), (84, 84, 84)),
	"copper": ((236, 152, 112), (196, 108, 72), (138, 70, 44)),
	"iron": ((236, 236, 236), (198, 198, 204), (140, 140, 150)),
	"gold": ((255, 240, 128), (238, 196, 52), (180, 130, 20)),
	"diamond": ((176, 255, 244), (74, 222, 204), (32, 150, 140)),
	"netherite": ((110, 98, 104), (74, 64, 70), (44, 38, 42)),
}


def uid():
	return str(uuid.uuid4())


# ---- textures ---------------------------------------------------------------------------------------------------------

def data_url(img):
	buf = io.BytesIO()
	img.save(buf, "PNG")
	return "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()


def texture_entry(name, img, uv=None):
	w, h = uv or img.size
	return {"path": "", "name": name, "folder": "", "namespace": "", "id": name, "particle": False, "render_mode": "default",
		"visible": True, "mode": "bitmap", "saved": True, "uuid": uid(), "relative_path": "", "source": data_url(img),
		"width": img.width, "height": img.height, "uv_width": w, "uv_height": h}


def image_of(entry):
	return Image.open(io.BytesIO(base64.b64decode(entry["source"].split(",", 1)[1]))).convert("RGBA")


def shade(c, f):
	return tuple(max(0, min(255, round(v * f))) for v in c[:3]) + (255,)


def flame_colour(t):
	if t < 0.28:
		return (255, 255, 236, 255)
	if t < 0.5:
		return (255, 236, 120, 255)
	if t < 0.75:
		return (255, 170, 48, 255)
	return (232, 84, 24, 255)


def flash_picture():
	"""The muzzle flash, 32 by 16: on the left the burst seen down the barrel (a white-hot core, yellow and orange flame,
	red spikes, see-through between them), on the right the tongue of fire seen from the side, for the fins."""
	img = Image.new("RGBA", (32, 16), (0, 0, 0, 0))
	rnd = random.Random(7)
	for y in range(16):
		for x in range(16):
			dx, dy = x - 7.5, y - 7.5
			r = math.hypot(dx, dy) / 8.0
			a = math.atan2(dy, dx)
			spikes = 0.62 + 0.38 * abs(math.cos(a * 4)) ** 3 + rnd.uniform(-0.06, 0.06)
			if r > spikes:
				continue
			img.putpixel((x, y), flame_colour(r / spikes))
	for y in range(16):
		for x in range(16):
			# a lens of fire along u, widest a third of the way in, ragged at the edges
			u = (x + 0.5) / 16
			half = 7.5 * math.sin(math.pi * u ** 0.75) + rnd.uniform(-0.8, 0.8)
			d = abs(y - 7.5)
			if d <= half:
				img.putpixel((16 + x, y), flame_colour(max(d / max(half, 0.5), 1 - math.sin(math.pi * u ** 0.75))))
	return img


IRON, IRON_LIGHT = (98, 100, 108), (168, 172, 182)


def bayonet_picture(tier):
	"""The bayonet's 16 square: the top half its iron socket, the bottom half the blade in the tier's metal."""
	light, mid, dark = BAYONET_TIERS[tier]
	img = Image.new("RGBA", (16, 16))
	rnd = random.Random(3)
	for y in range(16):
		for x in range(16):
			if y < 8:
				c = shade(IRON_LIGHT if y == 1 else IRON, 1 + rnd.uniform(-0.04, 0.04))
			else:
				c = (light if y == 9 else dark if y == 15 else mid) + (255,)
			img.putpixel((x, y), c)
	return img


# ---- building a project -----------------------------------------------------------------------------------------------

def face_size(frm, to, face):
	dx, dy, dz = (abs(to[i] - frm[i]) for i in range(3))
	return {"north": (dx, dy), "south": (dx, dy), "east": (dz, dy), "west": (dz, dy), "up": (dx, dz), "down": (dx, dz)}[face]


def cube(name, frm, to, tiles, texture=0, rotation=None, origin=None, tile_px=16):
	"""A cube whose faces take their picture from a tile of `tile_px` (dict face -> (u, v) tile corner in uv units, or one
	corner for every face), as much of the tile as the face is big."""
	faces = {}
	for f in ("north", "east", "south", "west", "up", "down"):
		corner = tiles.get(f, tiles.get("all")) if isinstance(tiles, dict) else tiles
		if corner is None:
			continue
		w, h = face_size(frm, to, f)
		u, v = corner
		faces[f] = {"uv": [u, v, u + max(1, min(tile_px, round(w))), v + max(1, min(tile_px, round(h)))], "texture": texture}
	e = {"name": name, "box_uv": False, "rescale": False, "locked": False, "render_order": "default", "allow_mirror_modeling": True,
		"from": list(frm), "to": list(to), "autouv": 0, "color": 0, "origin": list(origin or frm), "faces": faces, "type": "cube",
		"uuid": uid()}
	if rotation:
		e["rotation"] = list(rotation)
	return e


def group(name, origin, children, rotation=(0, 0, 0)):
	return {"name": name, "origin": list(origin), "rotation": list(rotation), "color": 0, "uuid": uid(), "export": True,
		"mirror_uv": False, "isOpen": True, "locked": False, "visibility": True, "autouv": 0, "children": children}


def project(name, nodes, textures, resolution):
	"""A free-format project from a tree of groups (dicts with children) and cubes (element dicts)."""
	elements, groups = [], []

	def walk(node):
		if "children" in node:
			g = {k: v for k, v in node.items() if k != "children"}
			groups.append(g)
			return {"uuid": g["uuid"], "isOpen": True, "children": [walk(c) for c in node["children"]]}
		elements.append(node)
		return node["uuid"]

	outliner = [walk(n) for n in nodes]
	return {"meta": {"format_version": "5.0", "model_format": "free", "box_uv": False}, "name": name, "model_identifier": "",
		"visible_box": [1, 1, 0], "variable_placeholders": "", "variable_placeholder_buttons": [],
		"timeline_setups": [], "unhandled_root_fields": {}, "resolution": {"width": resolution[0], "height": resolution[1]},
		"elements": elements, "groups": groups, "outliner": outliner, "textures": textures, "animations": []}


def flash_bone(centre, axis, size, texture):
	"""Three crossed planes of the flash picture at the muzzle, `axis` the barrel's ('x' or 'z'): a disc facing out of
	the muzzle and two fins along the shot, so it reads from any side."""
	cx, cy, cz = centre
	s = size / 2
	along = size * 1.6
	if axis == "z":  # muzzle toward -z
		disc = cube("flash_disc", (cx - s, cy - s, cz), (cx + s, cy + s, cz), {"north": (0, 0), "south": (0, 0)}, texture)
		fin_v = cube("flash_fin_v", (cx, cy - s, cz - along), (cx, cy + s, cz), {"east": (0, 0), "west": (0, 0)}, texture)
		fin_h = cube("flash_fin_h", (cx - s, cy, cz - along), (cx + s, cy, cz), {"up": (0, 0), "down": (0, 0)}, texture)
	else:  # muzzle toward +x
		disc = cube("flash_disc", (cx, cy - s, cz - s), (cx, cy + s, cz + s), {"east": (0, 0), "west": (0, 0)}, texture)
		fin_v = cube("flash_fin_v", (cx, cy - s, cz), (cx + along, cy + s, cz), {"north": (0, 0), "south": (0, 0)}, texture)
		fin_h = cube("flash_fin_h", (cx, cy, cz - s), (cx + along, cy, cz + s), {"up": (0, 0), "down": (0, 0)}, texture)
	for f in disc["faces"].values():
		f["uv"] = [0, 0, 16, 16]
	for c in (fin_v, fin_h):
		for f in c["faces"].values():
			f["uv"] = [16, 0, 32, 16]
	return group("flash", centre, [disc, fin_v, fin_h])


# ---- animations -------------------------------------------------------------------------------------------------------

class Anim:
	"""Keyframes bone by bone, the game's way: (time, (x, y, z), interpolation) per channel."""

	def __init__(self, name, length, loop="once"):
		self.name, self.length, self.loop = name, length, loop
		self.keys = {}

	def k(self, bone, channel, time, v, interp="linear"):
		self.keys.setdefault(bone, {}).setdefault(channel, []).append((time, v, interp))
		return self

	def rot(self, bone, *keys, interp="catmullrom"):
		for t, v in keys:
			self.k(bone, "rotation", t, v, interp)
		return self

	def pos(self, bone, *keys, interp="catmullrom"):
		for t, v in keys:
			self.k(bone, "position", t, v, interp)
		return self

	def scale(self, bone, *keys, interp="linear"):
		for t, v in keys:
			self.k(bone, "scale", t, v if isinstance(v, tuple) else (v, v, v), interp)
		return self

	def blockbench(self, gun, uuids):
		animators = {}
		for bone, chans in self.keys.items():
			kfs = []
			for ch, ks in chans.items():
				for t, v, interp in ks:
					kfs.append({"channel": ch, "data_points": [{"x": round(v[0], 4), "y": round(v[1], 4), "z": round(v[2], 4)}],
						"uuid": uid(), "time": round(t, 4), "color": -1, "interpolation": interp})
			animators[uuids[bone]] = {"name": bone, "type": "bone", "keyframes": kfs}
		return {"uuid": uid(), "name": f"animation.{gun}.{self.name}", "loop": self.loop, "override": False,
			"length": self.length, "snapping": 24, "selected": False, "anim_time_update": "", "blend_weight": "",
			"start_delay": "", "loop_delay": "", "animators": animators}


def held(name, bone, hammer=None, hammer_at=(0, 0, 0)):
	"""A held pose that loops: the gun still, the hammer where it rests."""
	a = Anim(name, 0.5, "loop").rot(bone, (0, (0, 0, 0)), interp="linear")
	if hammer:
		a.rot(hammer, (0, hammer_at), interp="linear")
	return a


def standard_anims(gun, body, hammer, cocked, fallen, lift_axis, kick_axis, heavy, flash_peak, ramrod=None, ramrod_out=None,
				   reload_tilt=50.0):
	"""idle (loaded, cocked), spent (hammer down), aim (cocked, the faintest sway), draw (swung up into the hand), fire
	(hammer snaps, flash, the kick and the muzzle lift, settling) and reload, for a gun whose muzzle lifts about
	`lift_axis` (+ is up) and kicks back along `kick_axis` (a unit vector, pixels per unit of kick). `heavy` scales how
	hard and how long: the pistol 1, the musket heavier and slower, the blunderbuss most."""
	def axis_vec(a, v):
		# a long gun's muzzle (-z) rises with a negative x turn, the pistol's (+x) with a positive z turn
		return tuple((-v if a == "x" else v) if c == a else 0.0 for c in "xyz")

	lift = lambda d: axis_vec(lift_axis, d)
	kick = lambda d: tuple(c * d for c in kick_axis)
	anims = [held("idle", body, hammer, cocked), held("spent", body, hammer, fallen)]

	aim = Anim("aim", 2.0, "loop").rot(hammer, (0, cocked), interp="linear")
	aim.rot(body, (0, lift(0)), (0.5, lift(0.35)), (1.0, lift(0)), (1.5, lift(-0.25)), (2.0, lift(0)))
	anims.append(aim)

	draw = Anim("draw", 0.45 * heavy).rot(hammer, (0, cocked), interp="linear")
	draw.rot(body, (0, lift(-55)), (0.25 * heavy, lift(6)), (0.36 * heavy, lift(-2)), (0.45 * heavy, lift(0)))
	draw.pos(body, (0, kick(3)), (0.25 * heavy, kick(0)), (0.45 * heavy, kick(0)))
	anims.append(draw)

	length = 0.4 * heavy
	fire = Anim("fire", length, "once")
	fire.rot(hammer, (0, cocked), (0.03, tuple(f * 1.33 for f in fallen)), (0.12, fallen), (length, fallen), interp="linear")
	fire.rot(body, (0, lift(0)), (0.05, lift(6 * heavy)), (0.11 * heavy, lift(12 * heavy)), (0.3 * heavy, lift(3 * heavy)), (length, lift(0)))
	fire.pos(body, (0, kick(0)), (0.04, kick(1.6 * heavy)), (0.2 * heavy, kick(0.4 * heavy)), (length, kick(0)))
	fire.scale("flash", (0, 0.2), (0.025, flash_peak), (0.07, flash_peak * 0.75), (0.14 * heavy, 0.0), (length, 0.0))
	anims.append(fire)

	t = RELOAD[gun]
	reload = Anim("reload", t, "loop")
	tilt = lift(reload_tilt)
	reload.rot(body, (0, lift(0)), (0.2 * t, tilt), (0.8 * t, tilt), (t, lift(0)))
	reload.rot(hammer, (0, fallen), (0.85 * t, fallen), (0.95 * t, cocked), (t, cocked), interp="linear")
	if ramrod:
		reload.pos(ramrod, (0, (0, 0, 0)), (0.25 * t, (0, 0, 0)), (0.4 * t, ramrod_out), (0.5 * t, tuple(c * 0.4 for c in ramrod_out)),
			(0.6 * t, ramrod_out), (0.75 * t, (0, 0, 0)), (t, (0, 0, 0)), interp="linear")
	anims.append(reload)
	for a in anims:
		if a.name != "fire":
			a.scale("flash", (0, 0.0))
	return anims


# ---- the guns ---------------------------------------------------------------------------------------------------------

def musket():
	data = json.load(open(os.path.join(BBMODELS, "flintlock-musket-rifle-commission.bbmodel"), encoding="utf-8"))
	names = {"Flintlock_rifle": "root", "flintlock_riflev1": "gun", "group4": "ramrod", "group5": "hammer", "bullet2": "ball"}
	drop = {"gun_flash2", "gunpowder_casing2"}
	for g in data["groups"]:
		g["name"] = names.get(g["name"], g["name"])
	dropped = {g["uuid"] for g in data["groups"] if g["name"] in drop}

	def keep(node):
		return not (isinstance(node, dict) and node["uuid"] in dropped)

	def prune(nodes):
		return [dict(n, children=prune(n["children"])) if isinstance(n, dict) else n for n in nodes if keep(n)]

	data["outliner"] = prune(data["outliner"])
	data["groups"] = [g for g in data["groups"] if g["uuid"] not in dropped]
	used = set()

	def collect(nodes):
		for n in nodes:
			if isinstance(n, dict):
				collect(n["children"])
			else:
				used.add(n)

	collect(data["outliner"])
	data["elements"] = [e for e in data["elements"] if e["uuid"] in used]

	data["textures"].append(texture_entry("flash", flash_picture()))
	data["textures"][-1]["uv_width"], data["textures"][-1]["uv_height"] = 32, 16
	data["textures"].append(texture_entry("bayonet", bayonet_picture("iron")))
	flash_tex, bayonet_tex = len(data["textures"]) - 2, len(data["textures"]) - 1
	# the socket bayonet: a ring round the muzzle, a neck, the blade under the barrel on the right
	socket, blade = (0, 0), (0, 8)
	bayonet = group("bayonet", (0, 14.75, -33), [
		cube("socket", (-1.6, 13.2, -34.6), (1.6, 16.3, -31.6), {"all": socket}, bayonet_tex, tile_px=8),
		cube("neck", (0.9, 12.6, -34.2), (1.7, 13.4, -33.2), {"all": socket}, bayonet_tex, tile_px=8),
		cube("blade", (1.0, 11.6, -45.5), (1.4, 13.2, -34.0), {"all": blade}, bayonet_tex, tile_px=8),
		cube("tip", (1.0, 12.1, -48.0), (1.4, 12.9, -45.5), {"all": blade}, bayonet_tex, tile_px=8),
	])
	flash = flash_bone((0, 14.75, -34.2), "z", 7.0, flash_tex)
	gun_uuid = next(g["uuid"] for g in data["groups"] if g["name"] == "gun")

	def find(nodes):
		for n in nodes:
			if isinstance(n, dict):
				if n["uuid"] == gun_uuid:
					return n
				hit = find(n["children"])
				if hit:
					return hit
	add_nodes(data, find(data["outliner"]), [bayonet, flash])

	# the source's own reload (ramrod down the barrel, the ball dropped in), kept and fitted to WeaponMod's reload time
	source_reload = next(a for a in data["animations"] if a["name"] == "reload")
	speed = RELOAD["musket"] / float(source_reload["length"])
	uuids = {g["name"]: g["uuid"] for g in data["groups"]}
	reload = Anim("reload", RELOAD["musket"], "loop")
	for an in source_reload["animators"].values():
		bone = names.get(an.get("name"), an.get("name"))
		if bone not in uuids:
			continue
		if bone == "ball":
			continue  # the ball's drop is left out: it fell outside the turned-up gun
		for k in an.get("keyframes", []):
			v = tuple(float(str(k["data_points"][0].get(a, 0)).strip() or 0) for a in "xyz")
			if bone == "gun" and k["channel"] == "rotation":
				v = (-v[0], v[1], v[2])  # the source tipped the muzzle down to ram; it goes up
			reload.k(bone, k["channel"], float(k["time"]) * speed, v, k.get("interpolation", "linear"))
	reload.length = RELOAD["musket"]
	anims = [a for a in standard_anims("musket", "gun", "hammer", (0, 0, 0), (-45, 0, 0), "x", (0, 0, 1), 1.75, 1.6)
		if a.name != "reload"] + [reload]
	for a in anims:
		a.scale("ball", (0, 0.0))
		if a.name == "reload":
			a.scale("flash", (0, 0.0))
	data["animations"] = [a.blockbench("musket", uuids) for a in anims]
	data["name"] = "musket"
	return data


def add_nodes(data, parent_node, nodes):
	"""Adds new groups and cubes (as built by group/cube) under an existing outliner node."""
	def walk(node):
		if "children" in node:
			g = {k: v for k, v in node.items() if k != "children"}
			data["groups"].append(g)
			return {"uuid": g["uuid"], "isOpen": True, "children": [walk(c) for c in node["children"]]}
		data["elements"].append(node)
		return node["uuid"]

	for n in nodes:
		parent_node["children"].append(walk(n))


def flintlock():
	data = json.load(open(os.path.join(BBMODELS, "dueling-pistol.bbmodel"), encoding="utf-8"))
	data["meta"]["model_format"] = "free"
	els = data["elements"]
	# by their place in the file: the hammer (the cock, on the lock's side plane), the ramrod under the barrel
	hammer_ids, ramrod_ids = {els[10]["uuid"]}, {els[5]["uuid"]}
	data["textures"].append(texture_entry("flash", flash_picture()))
	flash_tex = len(data["textures"]) - 1
	body = group("gun", (1.0, 1.0, 8.0), [])
	hammer = group("hammer", (5.79, 3.87, 9.0), [e for e in els if e["uuid"] in hammer_ids])
	ramrod = group("ramrod", (13.8, 2.7, 8.0), [e for e in els if e["uuid"] in ramrod_ids])
	body["children"] = [e for e in els if e["uuid"] not in hammer_ids | ramrod_ids] + [hammer, ramrod,
		flash_bone((14.9, 4.16, 8.0), "x", 5.0, flash_tex)]
	out = project("flintlock", [group("root", (0, 0, 0), [body])], data["textures"],
		(data["resolution"]["width"], data["resolution"]["height"]))
	# the flash picture is 16 square in a 32 uv project: its own uv size keeps it whole
	out["textures"][-1]["uv_width"], out["textures"][-1]["uv_height"] = 32, 16
	uuids = {g["name"]: g["uuid"] for g in out["groups"]}
	anims = standard_anims("flintlock", "gun", "hammer", (0, 0, 0), (0, 0, -32), "z", (-1, 0, 0), 1.0, 1.25,
		ramrod="ramrod", ramrod_out=(7, 0, 0), reload_tilt=55.0)
	out["animations"] = [a.blockbench("flintlock", uuids) for a in anims]
	return out


def ramrod_picture():
	"""The blunderbuss's ramrod, 16 by 8: steel on the top half, the brass tip's on the bottom."""
	img = Image.new("RGBA", (16, 8))
	rnd = random.Random(5)
	for y in range(8):
		for x in range(16):
			base, light = ((128, 132, 140), (200, 204, 214)) if y < 4 else ((190, 146, 60), (240, 208, 122))
			img.putpixel((x, y), shade(light if y % 4 == 0 else base, 1 + rnd.uniform(-0.04, 0.04)))
	return img


def frontier_cube(c, src, dst, k):
	"""One of Frontier Armaments' GeckoLib cubes as a project cube: x turned back (a geo file stores -x), then moved and
	scaled so the point `src` lands on `dst`, `k` times bigger. Its per-face uvs kept on texture 0."""
	o, s = c["origin"], c["size"]
	lo = [-(o[0] + s[0]), o[1], o[2]]
	hi = [-o[0], o[1] + s[1], o[2] + s[2]]
	lo, hi = [min(lo[i], hi[i]) for i in range(3)], [max(lo[i], hi[i]) for i in range(3)]
	at = lambda p: tuple(dst[i] + k * (p[i] - src[i]) for i in range(3))
	faces = {}
	for f, fc in c["uv"].items():
		(u, v), (du, dv) = fc["uv"], fc["uv_size"]
		uv = [u + du, v + dv, u, v] if f in ("up", "down") else [u, v, u + du, v + dv]
		faces[f] = {"uv": uv, "texture": 0}
		if fc.get("uv_rotation"):
			faces[f]["rotation"] = fc["uv_rotation"]
	e = cube("cube", at(lo), at(hi), {}, rotation=None)
	e["faces"] = faces
	if c.get("inflate"):
		e["inflate"] = c["inflate"] * k
	if c.get("rotation"):
		p = c.get("pivot", [0, 0, 0])
		e["origin"] = list(at((-p[0], p[1], p[2])))
		e["rotation"] = [-c["rotation"][0], -c["rotation"][1], c["rotation"][2]]
	return e


def blunderbuss():
	"""The Firecannon of cleannrooster's Frontier Armaments (MIT, tools/bbmodels/frontier-armaments/): a brass-banded
	barrel with a bell mouth on a short stock. Its bone's display tilt dropped, then laid as our old blunderbuss lay:
	muzzle toward -z, top up, about 29 pixels long. Its hammer (the slanted cock behind the barrel) gets its own bone.
	Ours on top: the red gem on the breech plate painted over in the plate's steel (Andy), a steel ramrod with a brass tip
	under the barrel, the muzzle flash and the animations."""
	src_dir = os.path.join(BBMODELS, "frontier-armaments")
	geo = json.load(open(os.path.join(src_dir, "firecannon.geo.json"), encoding="utf-8"))["minecraft:geometry"][0]
	cubes = geo["bones"][0]["cubes"]
	# the muzzle's middle (the barrel's line, the front of the lip ring) to where our old muzzle was
	src, dst, k = (-1.43281, 0.02014, -10.50296), (0.0, 11.55, -18.4), 1.6
	parts = [frontier_cube(c, src, dst, k) for c in cubes]
	sheet = Image.open(os.path.join(src_dir, "firecannon.png")).convert("RGBA")
	px = sheet.load()
	# the breech plate's top face (84-88, 23-27): its red gem takes the plate's own steel
	steel = [px[x, y] for x in range(84, 88) for y in range(23, 27) if not (px[x, y][0] > 150 and px[x, y][1] < 80)]
	steel = tuple(sum(c[i] for c in steel) // len(steel) for i in range(4))
	for x in range(84, 88):
		for y in range(23, 27):
			if px[x, y][0] > 150 and px[x, y][1] < 80:
				px[x, y] = steel
	textures = [texture_entry("blunderbuss", sheet), texture_entry("flash", flash_picture()),
		texture_entry("ramrod", ramrod_picture())]
	hammer_at = parts[13]["origin"]
	hammer = group("hammer", hammer_at, [parts[13]])
	# the ramrod under the barrel, through the front bands and into the forestock; its tip toward the muzzle
	ramrod = group("ramrod", (0, 9.04, -17.4), [
		cube("ramrod", (-0.4, 8.64, -17.0), (0.4, 9.44, -6.0), {"all": (0, 0)}, 2),
		cube("ramrod_tip", (-0.5, 8.54, -17.6), (0.5, 9.54, -17.0), {"all": (0, 4)}, 2),
	])
	flash = flash_bone((0, dst[1], dst[2] - 0.1), "z", 9.0, 1)
	gun = group("gun", (0, 9.5, 5.0), parts[:13] + [hammer, ramrod, flash])
	data = project("blunderbuss", [group("root", (0, 0, 0), [gun])], textures, (128, 128))
	data["textures"][1]["uv_width"], data["textures"][1]["uv_height"] = 32, 16
	data["textures"][2]["uv_width"], data["textures"][2]["uv_height"] = 16, 8
	uuids = {g["name"]: g["uuid"] for g in data["groups"]}
	# reload: drawn out forward and up to the bore's line (2.5 up, 12.5 out puts it just past the muzzle), rammed, put back
	anims = standard_anims("blunderbuss", "gun", "hammer", (0, 0, 0), (-40, 0, 0), "x", (0, 0, 1), 2.1, 2.3,
		ramrod="ramrod", ramrod_out=(0, 2.5, -12.5), reload_tilt=48.0)
	data["animations"] = [a.blockbench("blunderbuss", uuids) for a in anims]
	return data


# ---- where each model lies in the item ------------------------------------------------------------------------------

def frame_matrix(frame, pivot, centre, scale):
	"""The item-space matrix (row-major, 16 floats, blocks) that lays a model point p (pixels) at centre + scale * frame *
	(p - pivot), as the old static models were laid: GeckoLib draws the model in blocks, the item model counts in 16ths."""
	rows = []
	for i in range(3):
		r = [scale * frame[i][j] for j in range(3)]
		t = (centre[i] - sum(scale * frame[i][j] * pivot[j] for j in range(3))) / 16
		rows.append([round(v, 6) for v in r] + [round(t, 6)])
	rows.append([0, 0, 0, 1])
	return [v for r in rows for v in r]


# the long guns (musket, blunderbuss): their muzzle (-z) up-left, their top (+y) up-right, their side (x) out of the
# sprite, as tools/gen_flintlock_rifle.py lays the musket
_EZ, _EY = (R2, -R2, 0.0), (R2, R2, 0.0)
_EX = (_EY[1] * _EZ[2] - _EY[2] * _EZ[1], _EY[2] * _EZ[0] - _EY[0] * _EZ[2], _EY[0] * _EZ[1] - _EY[1] * _EZ[0])
LONG_GUN = tuple((_EX[i], _EY[i], _EZ[i]) for i in range(3))
MUSKET_FRAME = frame_matrix(LONG_GUN, (0.0, 14.0, -13.75), (7.5, 9.5, 8.0), 0.6)
# the blunderbuss a touch shorter on the same diagonal; its middle (barrel line) at the sprite's middle
BLUNDERBUSS_PIVOT, BLUNDERBUSS_CENTRE, BLUNDERBUSS_SCALE = (0.0, 11.0, -3.5), (7.8, 8.6, 8.0), 0.56
BLUNDERBUSS_FRAME = frame_matrix(LONG_GUN, BLUNDERBUSS_PIVOT, BLUNDERBUSS_CENTRE, BLUNDERBUSS_SCALE)
# the pistol as tools/blockbench/dueling_pistol.py lays it (flintlock_3d)
PISTOL = ((-R2, R2, 0), (R2, R2, 0), (0, 0, -1))
FLINTLOCK_FRAME = frame_matrix(PISTOL, (7.3, 2.8, 8.16), (7.6, 8.4, 8.0), 0.85)


def animated(gun, base, frame, texture=None, hide=None, pose=None):
	m = {"type": "jaymospowers:animated_gun", "gun": gun, "base": base, "transformation": frame}
	if texture:
		m["texture"] = texture
	if hide:
		m["hide"] = hide
	if pose:
		m["pose"] = pose
	return m


def weaponmod_item(gun, base, frame, sprite, sprite_reload, **kw):
	"""WeaponMod's own picture in the inventory, on the ground and in frames; in a hand our animated gun, loaded or spent
	(WeaponMod's weaponmod:reloaded), or reloading (weaponmod:reload)."""
	def flat(model):
		return {"type": "minecraft:model", "model": model}

	def in_hand(still, moving):
		return {"type": "minecraft:select", "property": "minecraft:display_context",
			"cases": [{"when": ["gui", "ground", "fixed"], "model": flat(still)}], "fallback": moving}

	loaded = {"type": "minecraft:condition", "property": "weaponmod:reloaded",
		"on_true": animated(gun, base, frame, pose="loaded", **kw), "on_false": animated(gun, base, frame, pose="spent", **kw)}
	return {"model": {"type": "minecraft:condition", "property": "weaponmod:reload",
		"on_false": in_hand(sprite, loaded), "on_true": in_hand(sprite_reload, animated(gun, base, frame, pose="reload", **kw))}}


def write(path, obj, indent=1):
	os.makedirs(os.path.dirname(path), exist_ok=True)
	with open(path, "w", encoding="utf-8", newline="\n") as f:
		json.dump(obj, f, indent=indent)
		f.write("\n")


def geckolib(data, gun):
	"""Writes the project and the GeckoLib files, then the glow sheet (only the flash's pixels). Returns the sheet path
	and where the flash's and (if any) the bayonet's pictures sit on it (y offset, height)."""
	path = os.path.join(ART, gun + ".bbmodel")
	os.makedirs(ART, exist_ok=True)
	with open(path, "w", encoding="utf-8", newline="\n") as f:
		json.dump(data, f, indent="\t")
	name = "gun_" + gun
	subprocess.run([sys.executable, "-I", os.path.join(ROOT, "tools", "bbmodel_geckolib.py"), path, ASSETS, name], check=True)
	sheet_path = os.path.join(ASSETS, "textures", "entity", name + ".png")
	sheet = Image.open(sheet_path).convert("RGBA")
	# the stacking bbmodel_geckolib.py does: every texture a face uses, top to bottom in the file's order
	used = sorted({f["texture"] for e in data["elements"] for f in e["faces"].values() if f.get("texture") is not None})
	where, y = {}, 0
	for i in used:
		img = image_of(data["textures"][i])
		where[data["textures"][i]["name"]] = (y, img.height)
		y += img.height
	glow = Image.new("RGBA", sheet.size, (0, 0, 0, 0))
	fy, fh = where["flash"]
	glow.paste(sheet.crop((0, fy, sheet.width, fy + fh)), (0, fy))
	glow.save(os.path.join(ASSETS, "textures", "entity", name + "_glow.png"))
	return sheet_path, where


def main():
	# the musket, and a sheet per bayonet tier
	sheet_path, where = geckolib(musket(), "musket")
	sheet = Image.open(sheet_path).convert("RGBA")
	by, bh = where["bayonet"]
	for tier in BAYONET_TIERS:
		variant = sheet.copy()
		variant.paste(bayonet_picture(tier), (0, by))
		variant.save(os.path.join(ASSETS, "textures", "entity", f"gun_musket_bayonet_{tier}.png"))
	sheet.paste(Image.new("RGBA", (16, bh), (0, 0, 0, 0)), (0, by))
	sheet.save(sheet_path)

	# the flintlock, and the Void Flintlock's sheet: the same pistol steeped in the void
	sheet_path, where = geckolib(flintlock(), "flintlock")
	sheet = Image.open(sheet_path).convert("RGBA")
	fy, fh = where["flash"]
	void = sheet.copy()
	px = void.load()
	for y in range(fy):
		for x in range(void.width):
			r, g, b, a = px[x, y]
			if a:
				lum = (r * 0.3 + g * 0.59 + b * 0.11) / 255
				px[x, y] = (round(40 + 120 * lum), round(14 + 40 * lum), round(70 + 150 * lum), a)
	void.save(os.path.join(ASSETS, "textures", "entity", "gun_flintlock_void.png"))

	# the blunderbuss, and its static model (the base whose hand-held placements the animated one keeps)
	data = blunderbuss()
	geckolib(data, "blunderbuss")
	elements, atlas = freeform_convert(data, LONG_GUN, BLUNDERBUSS_PIVOT, BLUNDERBUSS_CENTRE, BLUNDERBUSS_SCALE, drop=("flash",))
	atlas.save(os.path.join(HELD, "jaymospowers", "textures", "item", "gun", "blunderbuss.png"))
	for name in ("blunderbuss", "blunderbuss_reload"):
		path = os.path.join(HELD, "jaymospowers", "models", "item", "gun", name + ".json")
		model = json.load(open(path, encoding="utf-8"))
		model.pop("parent", None)
		model["textures"] = {"0": "jaymospowers:item/gun/blunderbuss", "particle": "jaymospowers:item/gun/blunderbuss"}
		model["elements"] = elements
		model["display"] = {**model.get("display", {}),
			"firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
			"firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]}}
		write(os.path.join(path), model)

	# the item definitions
	items = os.path.join(HELD, "weaponmod", "items")
	write(os.path.join(items, "musket.json"), weaponmod_item("musket", "jaymospowers:item/gun/musket", MUSKET_FRAME,
		"weaponmod:item/musket", "weaponmod:item/musket_reload", hide=["bayonet"]), 2)
	for tier in BAYONET_TIERS:
		write(os.path.join(items, f"musketbayonet.{tier}.json"), weaponmod_item("musket", "jaymospowers:item/gun/musket", MUSKET_FRAME,
			f"weaponmod:item/musketbayonet.{tier}", f"weaponmod:item/musketbayonet.{tier}_reload",
			texture=f"jaymospowers:textures/entity/gun_musket_bayonet_{tier}.png"), 2)
	write(os.path.join(items, "blunderbuss.json"), weaponmod_item("blunderbuss", "jaymospowers:item/gun/blunderbuss",
		BLUNDERBUSS_FRAME, "weaponmod:item/blunderbuss", "weaponmod:item/blunderbuss_reload"), 2)
	flint = weaponmod_item("flintlock", "jaymospowers:item/flintlock_3d", FLINTLOCK_FRAME, "weaponmod:item/flintlock",
		"weaponmod:item/flintlock_reload")
	write(os.path.join(items, "flintlock.json"), flint, 2)
	# the Void Flintlock: its own model in the inventory, the animated pistol in the hand (loaded or spent by its own mark)
	write(os.path.join(ASSETS, "items", "flintlock.json"), {"model": {"type": "minecraft:select", "property": "minecraft:display_context",
		"cases": [{"when": ["gui", "ground", "fixed"], "model": {"type": "minecraft:model", "model": "jaymospowers:item/flintlock_3d"}}],
		"fallback": animated("flintlock", "jaymospowers:item/flintlock_3d", FLINTLOCK_FRAME,
			texture="jaymospowers:textures/entity/gun_flintlock_void.png")}}, "\t")


if __name__ == "__main__":
	main()
