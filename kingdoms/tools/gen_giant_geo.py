"""The Aether Giants' Blockbench project and GeckoLib files: the body GiantModel built in code, with its moves as keyframes.

Andy (2026-11-12): "is it possible to animate our giants with blockbench and geckolib?" This writes the same body
(the same boxes on the same 128x128 layout, so tools/gen_giants.py's four skins fit it unchanged) as
kingdoms/art/aether_giant.bbmodel, with the moves the old model worked out in code as animations he can open and change
in Blockbench: `idle` (breathing), `ready` (fists half raised), `blow_right`, `blow_left`, `stamp`, `grab` (wind-up,
snatch, hold, hurl, straighten), `grab_recover` (straightening from a grab cut short), and `death_right` / `death_left`
(toppling onto its face). Then tools/bbmodel_geckolib.py writes the game's files from it.

Left in code (client/remnants/GiantRenderer), because they follow the game frame by frame: its stride (the legs come
down on the server's steps), its head turning where it looks, its flinch, and its break-up after the fall.

Boxes are given the way the old model had them (a ModelPart's: pixels, y down, each from its part's pivot) and turned
into Blockbench's space here (a half turn about z: x and y turned round, feet at y 0). Keyframes are the game's way
(degrees as a ModelPart's angles, added to the resting pose; positions with x and y turned round).

After changing the animations in Blockbench: python3 -I tools/bbmodel_geckolib.py kingdoms/art/aether_giant.bbmodel
kingdoms/src/main/resources/assets/jaymoskingdoms aether_giant, then delete textures/entity/aether_giant.png (the
skins are textures/entity/giant/<element>.png). Running this script again writes the animations below over them.
Run from the project root: python3 -I kingdoms/tools/gen_giant_geo.py   (needs Pillow)
"""
import base64
import json
import math
import os
import subprocess
import sys
import uuid

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ART = os.path.join(ROOT, "kingdoms", "art", "aether_giant.bbmodel")
ASSETS = os.path.join(ROOT, "kingdoms", "src", "main", "resources", "assets", "jaymoskingdoms")
SKIN = os.path.join(ASSETS, "textures", "entity", "giant", "highfield.png")
NAME = "aether_giant"
TEX = 128

# ---- the body (GiantModel.createBodyLayer) -----------------------------------------------------------------------------
HUNCH = 0.22
# bone: (parent, pivot from the model's root (pixels, y down), resting x angle (radians), mirrored, boxes)
# a box: (u, v, x, y, z, w, h, d) from the bone's pivot
BONES = [
	("body", None, (0, 10, 0), HUNCH, False, [(0, 0, -10, -20, -6, 20, 14, 12), (0, 26, -8, -6, -5, 16, 6, 10)]),
	("shards", "body", (0, 10, 0), 0.0, False, [(96, 16, -6, -27, 2, 4, 10, 4), (96, 16, 3, -25, 3, 4, 10, 4), (96, 16, -1, -23, 5, 3, 7, 3)]),
	("head", "body", (0, -9, -4), -HUNCH, False, [(64, 0, -4, -7, -6, 8, 8, 8), (96, 0, -4.5, -6, -6.5, 9, 2, 2)]),
	("crown", "head", (0, -9, -4), 0.0, False, [(112, 16, -3.5, -11, -3, 2, 5, 2), (112, 16, -1, -12.5, -2, 2, 6, 2), (112, 16, 1.5, -11, -3, 2, 5, 2)]),
	("right_arm", "body", (-10, -7, 0), 0.0, False, [(0, 42, -6, -2, -4, 6, 26, 8), (28, 42, -6.5, 22, -4.5, 7, 6, 9), (0, 76, -7, -4, -4.5, 8, 5, 9)]),
	("left_arm", "body", (10, -7, 0), 0.0, True, [(0, 42, 0, -2, -4, 6, 26, 8), (28, 42, -0.5, 22, -4.5, 7, 6, 9), (0, 76, -1, -4, -4.5, 8, 5, 9)]),
	("right_leg", None, (-5, 10, 0), 0.0, False, [(60, 42, -3.5, 0, -3.5, 7, 14, 7)]),
	("left_leg", None, (5, 10, 0), 0.0, True, [(60, 42, -3.5, 0, -3.5, 7, 14, 7)]),
]

# ---- its moves (GiantModel's numbers; radians and ticks, as the code had them) -----------------------------------------
BLOW_LIFT, BLOW_TICKS, STAMP_PITCH = 2.4, 12, 0.35
ARM_FLARE, READY_LIFT, BREATH, BREATH_RATE = 0.2, 0.35, 0.025, 0.09
FALL_TICKS, FALL_PITCH, FALL_ROLL, FALL_DROP, LEG_DROP, HEAD_TWIST, REACH, FLAIL_RATE = 9, 1.3, 0.12, 8.0, 10.0, 0.4, 2.9, 1.3
GRAB_WINDUP, GRAB_SNATCH, GRAB_HOLD, GRAB_SLAM, GRAB_RECOVER, LIFT_TICKS = 30, 8, 40, 8, 20, 10
GRAB_RAISED, GRAB_FLARE, GRAB_DOWN, GRAB_HELD, GRAB_HURLED = -2.8, 0.5, -0.35, -1.35, 0.3
GRAB_LEAN_BACK, GRAB_LEAN_FORWARD, GRAB_TREMBLE = -0.3, 0.45, 0.04


def uid():
	return str(uuid.uuid4())


def faces(u, v, w, h, d, mirror):
	"""A box's faces on the vanilla box layout, as Blockbench lays a box out."""
	w, h, d = math.floor(w), math.floor(h), math.floor(d)
	r = {"east": [u, v + d, u + d, v + d + h], "north": [u + d, v + d, u + d + w, v + d + h],
		"west": [u + d + w, v + d, u + 2 * d + w, v + d + h], "south": [u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h],
		"up": [u + d + w, v + d, u + d, v], "down": [u + d + 2 * w, v, u + d + w, v + d]}
	if mirror:
		r["east"], r["west"] = r["west"], r["east"]
		r = {k: [x[2], x[1], x[0], x[3]] for k, x in r.items()}
	return {k: {"uv": x, "texture": 0} for k, x in r.items()}


def model():
	elements, groups, nodes = [], [], {}
	outliner = []
	for name, parent, pivot, rest, mirror, boxes in BONES:
		gid = uid()
		node = {"name": name, "uuid": gid, "origin": [-pivot[0], 24 - pivot[1], pivot[2]], "rotation": [-math.degrees(rest), 0, 0],
			"export": True, "isOpen": True, "visibility": True, "children": []}
		groups.append({k: v for k, v in node.items() if k != "children"})
		for i, (u, v, x, y, z, w, h, d) in enumerate(boxes):
			ax, ay, az = pivot[0] + x, pivot[1] + y, pivot[2] + z
			eid = uid()
			elements.append({"name": name + ("_%d" % i if i else ""), "type": "cube", "uuid": eid, "box_uv": False, "rescale": False,
				"locked": False, "from": [-(ax + w), 24 - (ay + h), az], "to": [-ax, 24 - ay, az + d], "autouv": 0, "color": 0,
				"origin": [-pivot[0], 24 - pivot[1], pivot[2]], "faces": faces(u, v, w, h, d, mirror)})
			node["children"].append(eid)
		nodes[name] = node
		(nodes[parent]["children"] if parent else outliner).append(node)
	with open(SKIN, "rb") as f:
		source = "data:image/png;base64," + base64.b64encode(f.read()).decode("ascii")
	return {"meta": {"format_version": "4.10", "model_format": "free", "box_uv": False}, "name": NAME, "model_identifier": NAME,
		"visible_box": [4, 4, 1], "resolution": {"width": TEX, "height": TEX}, "elements": elements, "groups": groups,
		"outliner": outliner, "textures": [{"name": "highfield.png", "id": "0", "width": TEX, "height": TEX, "uv_width": TEX,
			"uv_height": TEX, "particle": False, "visible": True, "mode": "bitmap", "uuid": uid(), "source": source}],
		"animations": []}, {g["name"]: g["uuid"] for g in groups}


def ease(t):
	t = min(1.0, max(0.0, t))
	return t * t * (3.0 - 2.0 * t)


def lerp(u, a, b):
	return a + (b - a) * u


def blow(t):
	return BLOW_LIFT * math.sin(min(t / BLOW_TICKS, 1.0) * math.pi)


def breathing(t, lift=0.0):
	b = math.sin(t * BREATH_RATE) * BREATH
	return {"body": ((b, 0, 0), None), "right_arm": ((-lift, 0, ARM_FLARE + b), None), "left_arm": ((-lift, 0, -ARM_FLARE - b), None)}


def grab(t):
	"""GiantModel.grab: the right arm, the body's lean, the head and the left arm's brace, `t` ticks in."""
	if t < GRAB_WINDUP:
		u = ease(t / GRAB_WINDUP)
		x, z, lean, head = lerp(u, -0.2, GRAB_RAISED), ARM_FLARE + u * GRAB_FLARE, u * GRAB_LEAN_BACK, -u * 0.4
	elif t < GRAB_WINDUP + GRAB_SNATCH:
		u = ease((t - GRAB_WINDUP) / GRAB_SNATCH)
		x, z = lerp(u, GRAB_RAISED, GRAB_DOWN), ARM_FLARE + (1.0 - u) * GRAB_FLARE
		lean, head = lerp(u, GRAB_LEAN_BACK, GRAB_LEAN_FORWARD), lerp(u, -0.4, 0.5)
	elif t < GRAB_WINDUP + GRAB_SNATCH + GRAB_HOLD:
		u = ease(min(1.0, (t - GRAB_WINDUP - GRAB_SNATCH) / LIFT_TICKS))
		x = lerp(u, GRAB_DOWN, GRAB_HELD) + u * GRAB_TREMBLE * math.sin(t * 2.1)
		z = ARM_FLARE + u * GRAB_TREMBLE * math.sin(t * 1.7)
		lean, head = lerp(u, GRAB_LEAN_FORWARD, 0.0), lerp(u, 0.5, 0.15)
	elif t < GRAB_WINDUP + GRAB_SNATCH + GRAB_HOLD + GRAB_SLAM:
		u = ((t - GRAB_WINDUP - GRAB_SNATCH - GRAB_HOLD) / GRAB_SLAM) ** 2
		x, z, lean, head = lerp(u, GRAB_HELD, GRAB_HURLED), ARM_FLARE, u * GRAB_LEAN_FORWARD, lerp(u, 0.15, 0.6)
	else:
		u = ease(min(1.0, (t - GRAB_WINDUP - GRAB_SNATCH - GRAB_HOLD - GRAB_SLAM) / GRAB_RECOVER))
		x, z, lean, head = lerp(u, GRAB_HURLED, 0.0), ARM_FLARE, (1.0 - u) * GRAB_LEAN_FORWARD, (1.0 - u) * 0.6
	return {"right_arm": ((x, 0, z), None), "body": ((lean, 0, 0), None), "head": ((head, 0, 0), None),
		"left_arm": ((0, 0, -ARM_FLARE - abs(lean) * 0.5), None)}


def death(t, right):
	"""GiantModel.fall: toppling forward onto its face, one arm thrown out ahead (`right`: which) and the other flung wide."""
	e = min(t / FALL_TICKS, 1.0) ** 2
	roll, out = (-1.0, 1.0) if right else (1.0, -1.0)
	flail = math.sin(t * FLAIL_RATE + 1.0) * (1.0 - e)
	reach = ((-e * REACH + flail * 0.45, 0, out * (ARM_FLARE + e * 0.25)), None)
	fling = ((-e * 0.4 - flail * 0.5, 0, -out * (ARM_FLARE + e * 1.25)), None)
	return {"body": ((e * FALL_PITCH, 0, roll * e * FALL_ROLL), (0, e * FALL_DROP, 0)),
		"head": ((e * (HUNCH - 0.6), out * e * HEAD_TWIST, roll * e * 0.3), None),
		"right_arm": reach if right else fling, "left_arm": fling if right else reach,
		"right_leg": ((e * 1.5 + flail * 0.35, -e * 0.25, 0), (0, e * LEG_DROP, 0)),
		"left_leg": ((e * 1.45 - flail * 0.45, e * 0.2, 0), (0, e * LEG_DROP, 0))}


def animation(name, ticks, loop, pose, uuids, every=1, start=0):
	"""`pose(t)` sampled every `every` ticks: {bone: ((x, y, z) radians, the model's way, or None, (x, y, z) pixels y down, or None)}."""
	animators = {}
	steps = list(range(0, ticks, every)) + [ticks]
	for t in steps:
		for bone, (rot, pos) in pose(start + t).items():
			kfs = animators.setdefault(uuids[bone], {"name": bone, "type": "bone", "keyframes": []})["keyframes"]
			if rot is not None:
				kfs.append({"channel": "rotation", "data_points": [{"x": round(math.degrees(rot[0]), 3), "y": round(math.degrees(rot[1]), 3),
					"z": round(math.degrees(rot[2]), 3)}], "uuid": uid(), "time": round(t / 20.0, 4), "color": -1, "interpolation": "linear"})
			if pos is not None:
				kfs.append({"channel": "position", "data_points": [{"x": round(-pos[0], 3), "y": round(-pos[1], 3), "z": round(pos[2], 3)}],
					"uuid": uid(), "time": round(t / 20.0, 4), "color": -1, "interpolation": "linear"})
	return {"uuid": uid(), "name": name, "loop": loop, "override": False, "length": round(ticks / 20.0, 4), "snapping": 20,
		"selected": False, "anim_time_update": "", "blend_weight": "", "start_delay": "", "loop_delay": "", "animators": animators}


def main():
	data, uuids = model()
	breath_ticks = round(2 * math.pi / BREATH_RATE)
	total = GRAB_WINDUP + GRAB_SNATCH + GRAB_HOLD + GRAB_SLAM + GRAB_RECOVER
	# (the breath's rate is rounded to a whole loop)
	rate = 2 * math.pi / breath_ticks / BREATH_RATE
	data["animations"] = [
		animation("idle", breath_ticks, "loop", lambda t: breathing(t * rate), uuids, every=5),
		animation("ready", breath_ticks, "loop", lambda t: breathing(t * rate, READY_LIFT), uuids, every=5),
		animation("blow_right", BLOW_TICKS, "once", lambda t: {"right_arm": ((-blow(t), 0, ARM_FLARE), None)}, uuids),
		animation("blow_left", BLOW_TICKS, "once", lambda t: {"left_arm": ((-blow(t), 0, -ARM_FLARE), None)}, uuids),
		animation("stamp", BLOW_TICKS, "once", lambda t: {"body": ((STAMP_PITCH * math.sin(min(t / BLOW_TICKS, 1.0) * math.pi), 0, 0), None)}, uuids),
		animation("grab", total, "once", grab, uuids),
		animation("grab_recover", GRAB_RECOVER, "once", grab, uuids, start=total - GRAB_RECOVER),
		animation("death_right", FALL_TICKS, "hold", lambda t: death(t, True), uuids),
		animation("death_left", FALL_TICKS, "hold", lambda t: death(t, False), uuids),
	]
	os.makedirs(os.path.dirname(ART), exist_ok=True)
	with open(ART, "w", encoding="utf-8", newline="\n") as f:
		json.dump(data, f, indent="\t")
	print("wrote", os.path.relpath(ART, ROOT))
	subprocess.run([sys.executable, "-I", os.path.join(ROOT, "tools", "bbmodel_geckolib.py"), ART, ASSETS, NAME], check=True)
	sheet = os.path.join(ASSETS, "textures", "entity", NAME + ".png")
	if os.path.exists(sheet):
		os.remove(sheet)  # the skins are textures/entity/giant/<element>.png


if __name__ == "__main__":
	main()
