"""
The Wither's own look (Kingdoms, client/wither/WitherRenderer): our own boxes, pixels and keyframes, nothing taken from
vanilla's art or from Wither: Reincarnated (All Rights Reserved, ideas only).

Andy (2026-11-19): "it should look more hulking like the large suit and broader shoulders for the heads to sit on, the
two side heads becoming pauldron like. and four ribcage like arms sticking out." Then, on the third try: charred black
like the Wither, not bone white; the centre skull sat on the body, not floating; the four rib arms plain to see.

Writes:
  kingdoms/art/wither.bbmodel                         the editable Blockbench project (texture and animations inside)
  kingdoms/.../bbmodels/wither.json                    baked for the game (tools/bbmodel_bake.py)
  kingdoms/.../textures/entity/bb/wither.png           its skin
  kingdoms/.../textures/entity/bb/wither_glow.png      what glows in the dark (eyes and the seams of its core)
  kingdoms/.../textures/entity/bb/wither_pale.png      its ash-pale flicker while it's being raised
  kingdoms/.../bbmodels/wither_armor.json              the same bones a little puffed out, for the half-health shield

python3 -I tools/gen_wither.py [--previews <dir>]
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

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
KINGDOMS = os.path.join(ROOT, "kingdoms")
ASSETS = os.path.join(KINGDOMS, "src", "main", "resources", "assets", "jaymoskingdoms")
ART = os.path.join(KINGDOMS, "art", "wither.bbmodel")
SEED = 1717
TEX = 256

# ---- colours ------------------------------------------------------------------------------------------------------------
# charred bone: soot black with ash-grey edges, the Wither's colour
CHAR = (38, 35, 35)
CHAR_DARK = (22, 20, 21)
CHAR_LIGHT = (66, 61, 59)
ASH = (98, 92, 88)
CRACK = (12, 10, 12)
CORE = (20, 14, 24)
CORE_VEIN = (64, 38, 92)
SOCKET = (6, 4, 8)
TOOTH = (118, 110, 102)
# what glows (drawn bright in the dark by the renderer's glow layer): the Wither's violet (WitherFight.VIOLET 0x6A3FA0)
# for its core, a paler violet-white for its eyes
GLOW_CORE = (106, 63, 160)
GLOW_EYE = (196, 168, 255)


def uid():
	return str(uuid.UUID(int=random.getrandbits(128)))


# ---- the shape --------------------------------------------------------------------------------------------------------
# Blockbench space: y up, the front at -z, 16 px a block. Groups: (name, parent, origin, rotation). Cubes: (group, from,
# to, material, extra) where material says how it's painted.
GROUPS = []
CUBES = []


def group(name, parent, origin, rotation=(0, 0, 0)):
	GROUPS.append({"name": name, "parent": parent, "origin": list(origin), "rotation": list(rotation)})


def cube(g, f, t, mat, **extra):
	CUBES.append({"group": g, "from": list(f), "to": list(t), "mat": mat, **extra})


def build():
	# the body: a hulking barrel of a ribcage round a dark core, hung from the shoulders
	group("body", None, (0, 40, 0))
	cube("body", (-13, 29, -5), (13, 48, 8), "core")
	for i, y in enumerate((31, 35, 39, 43)):
		w = 15 - (1 if i == 0 else 0)
		cube("body", (-w, y, -7), (w, y + 2, -5), "rib")
		cube("body", (-w - 1, y, -6), (-w + 1, y + 2, 7), "rib")
		cube("body", (w - 1, y, -6), (w + 1, y + 2, 7), "rib")
	cube("body", (-2, 30, -8), (2, 48, -6), "bone")  # the breastbone
	cube("body", (-3, 30, 7), (3, 48, 10), "bone")  # the backbone behind the ribs
	# the shoulders: a broad yoke the three skulls sit on, and the hump the centre skull sits in
	group("shoulders", "body", (0, 48, 0))
	cube("shoulders", (-27, 46, -6), (27, 53, 6), "bone")
	cube("shoulders", (-14, 48, -6), (14, 56, 6), "bone")
	# the centre skull: big, sat down in the hump (its jaw in front of it), crowned with two short horns
	group("head_c", "shoulders", (0, 55, 0))
	cube("head_c", (-8, 55, -9), (8, 70, 6), "skull", face="big")
	cube("head_c", (-6, 52, -10), (6, 56, -2), "jaw")
	cube("head_c", (-7, 70, -5), (-4, 74, -2), "bone")
	cube("head_c", (4, 70, -5), (7, 74, -2), "bone")
	# the side skulls: pauldrons, each on a flared plate on the end of the yoke
	for side, s in (("r", 1), ("l", -1)):
		g = "pauldron_" + side
		group(g, "shoulders", (s * 20, 52, 0), (0, 0, s * -10))
		lo, hi = sx(s, 11, 29)
		cube(g, (lo, 51, -9), (hi, 54, 7), "plate")
		lo, hi = sx(s, 27, 30)
		cube(g, (lo, 47, -9), (hi, 53, 7), "plate")
		h = "head_" + side
		group(h, g, (s * 20, 54, 0))
		lo, hi = sx(s, 14, 26)
		cube(h, (lo, 54, -7), (hi, 66, 5), "skull", face="small")
		lo, hi = sx(s, 15, 25)
		cube(h, (lo, 52, -8), (hi, 55, -2), "jaw")
	# four ribcage arms: long curved ribs out of the sides, each with a crooked forearm and three claws
	for side, s in (("r", 1), ("l", -1)):
		for tier, y, reach, fore, droop, sweep in (("u", 44, 16, 14, -18, 22), ("d", 35, 14, 12, -32, 12)):
			a = f"arm_{tier}{side}"
			group(a, "body", (s * 14, y, 0), (0, s * -sweep, s * droop))
			lo, hi = sx(s, 14, 14 + reach)
			cube(a, (lo, y - 2, -2), (hi, y + 2, 2), "rib")
			for k in range(3):
				px = 17 + k * 4
				lo, hi = sx(s, px, px + 2)
				cube(a, (lo, y + 2, -1), (hi, y + 4, 1), "bone")
			b = a + "_fore"
			ox = 14 + reach
			group(b, a, (s * ox, y, 0), (0, 0, s * -40))
			lo, hi = sx(s, ox, ox + fore)
			cube(b, (lo, y - 1.5, -1.5), (hi, y + 1.5, 1.5), "rib")
			c = a + "_claw"
			cx = ox + fore
			group(c, b, (s * cx, y, 0), (0, 0, s * -25))
			for dz in (-2, 0, 2):
				lo, hi = sx(s, cx, cx + 5 - abs(dz) // 2)
				cube(c, (lo, y - 1, dz - 0.5), (hi, y + 0.5, dz + 0.5), "bone")
	# the spine: vertebrae hanging down from the body, floating ribs on it, then the tail
	group("spine", "body", (0, 30, 1))
	for (y0, y1, w, rib) in ((25, 30, 3, 6), (20, 25, 2.5, 5), (15, 20, 2, 4)):
		cube("spine", (-w, y0, -w + 1), (w, y1, w + 1), "bone")
		cube("spine", (-rib, y0 + 2, 0), (rib, y0 + 3, 2), "rib")
	group("tail", "spine", (0, 15, 1))
	cube("tail", (-2, 7, -1), (2, 15, 3), "bone")
	group("tail_tip", "tail", (0, 7, 1))
	cube("tail_tip", (-1.5, 1, -0.5), (1.5, 7, 2.5), "bone")
	cube("tail_tip", (-0.5, -2, 0.5), (0.5, 1, 1.5), "bone")


def sx(s, a, b):
	"""x from a to b on the +x side, or mirrored onto the -x side."""
	return (a, b) if s > 0 else (-b, -a)


# ---- the skin -----------------------------------------------------------------------------------------------------------
def mix(a, b, t):
	t = max(0.0, min(1.0, t))
	return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def face_sizes(c):
	(x0, y0, z0), (x1, y1, z1) = c["from"], c["to"]
	w, h, d = x1 - x0, y1 - y0, z1 - z0
	return {"north": (w, h), "south": (w, h), "east": (d, h), "west": (d, h), "up": (w, d), "down": (w, d)}


def layout():
	"""Every face its own rectangle on the sheet, packed in shelves, a texel a pixel."""
	x = y = shelf = 0
	for c in CUBES:
		c["uv"] = {}
		for fn, (w, h) in face_sizes(c).items():
			pw, ph = max(1, math.ceil(w)), max(1, math.ceil(h))
			if x + pw > TEX:
				x, y, shelf = 0, y + shelf, 0
			c["uv"][fn] = (x, y, x + w, y + h, pw, ph)
			x += pw
			shelf = max(shelf, ph)
	if y + shelf > TEX:
		raise SystemExit("the sheet is full")


def paint():
	rnd = random.Random(SEED)
	skin = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 0))
	glow = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 0))
	px, gx = skin.load(), glow.load()
	for c in CUBES:
		for fn, (u0, v0, u1, v1, pw, ph) in c["uv"].items():
			for j in range(ph):
				for i in range(pw):
					px[u0 + i, v0 + j] = texel(rnd, c, fn, i, j, pw, ph) + (255,)
			mark(rnd, c, fn, u0, v0, pw, ph, px, gx)
	return skin, glow


def texel(rnd, c, fn, i, j, w, h):
	mat = c["mat"]
	n = rnd.random()
	if mat == "core":
		return mix(CORE, CHAR_DARK, n * 0.6)
	# soot: dark, with ash on the upper edges and the top faces, darker underneath
	t = j / max(1, h - 1)
	base = mix(CHAR_LIGHT, CHAR_DARK, t * 0.8) if fn not in ("up", "down") else (CHAR_LIGHT if fn == "up" else CHAR_DARK)
	if mat in ("rib", "plate", "jaw") and (j == 0 or (fn == "up" and n < 0.35)):
		base = mix(base, ASH, 0.55)
	if mat == "plate" and fn in ("north", "south", "east", "west") and j == h - 1:
		base = CHAR_DARK
	if n < (0.02 if mat == "skull" else 0.05):
		base = mix(base, CRACK, 0.6)
	elif n > 0.94:
		base = mix(base, ASH, 0.35)
	return mix(base, CHAR, 0.25 * rnd.random())


def put(px, x, y, col, w, h, u0, v0):
	if 0 <= x < w and 0 <= y < h:
		px[u0 + x, v0 + y] = col + (255,)


def mark(rnd, c, fn, u0, v0, w, h, px, gx):
	mat = c["mat"]
	if mat == "core" and fn in ("north", "east", "west"):
		# the core shows violet between the ribs, glowing
		for y in range(h):
			ry = (c["to"][1] - 1 - y)
			if int(ry - 29) % 4 in (0, 3):
				for x in range(w):
					if rnd.random() < 0.55:
						put(px, x, y, CORE_VEIN, w, h, u0, v0)
						if rnd.random() < 0.7:
							put(gx, x, y, GLOW_CORE, w, h, u0, v0)
	if mat == "skull" and fn == "north":
		big = c.get("face") == "big"
		ew, eh = (4, 4) if big else (3, 3)
		ey = 5 if big else 4
		gap = 2 if big else 1
		lx = w // 2 - gap - ew
		rx = w // 2 + gap
		for ex in (lx, rx):
			for y in range(eh):
				for x in range(ew):
					put(px, ex + x, ey + y, SOCKET, w, h, u0, v0)
			# a pupil of cold violet light, low in the socket
			cx, cy = ex + ew // 2 - (0 if big else 1), ey + eh - 2
			for (dx, dy) in ((0, 0), (1, 0)) if big else ((1, 0),):
				put(px, cx + dx, cy + dy, GLOW_EYE, w, h, u0, v0)
				put(gx, cx + dx, cy + dy, GLOW_EYE, w, h, u0, v0)
		# the brow, a hard ridge of ash over the eyes
		for x in range(w):
			put(px, x, ey - 1, mix(ASH, CHAR, 0.4), w, h, u0, v0)
		# the nose, a dark notch
		ny = ey + eh + 1
		for y in range(2 if big else 1):
			for x in range(w // 2 - 1, w // 2 + 1):
				put(px, x, ny + y, SOCKET, w, h, u0, v0)
		# the upper teeth along the bottom edge
		for x in range(1, w - 1):
			put(px, x, h - 2, TOOTH if x % 2 else SOCKET, w, h, u0, v0)
		# cracks
		for _ in range(2 if big else 1):
			x, y = rnd.choice((0, 1, w - 2, w - 1)), rnd.randrange(1, ey)
			for _ in range(4):
				put(px, x, y, CRACK, w, h, u0, v0)
				x += rnd.choice((-1, 0, 1))
				y += 1
	if mat == "skull" and fn in ("east", "west"):
		# the temples: a dark hollow where the ear would be
		for y in range(h // 3, h // 3 + 3):
			for x in range(w // 2 - 1, w // 2 + 2):
				put(px, x, y, mix(SOCKET, CHAR_DARK, 0.5), w, h, u0, v0)
	if mat == "jaw" and fn == "north":
		# the lower teeth along the top
		for x in range(1, w - 1):
			put(px, x, 0, TOOTH if x % 2 == 0 else SOCKET, w, h, u0, v0)
			put(px, x, 1, SOCKET, w, h, u0, v0)


# ---- the animations -----------------------------------------------------------------------------------------------------
# One per move of the fight (kingdoms wither/WitherMoves, WitherFight), keyed by bone and channel: [(time, (x, y, z))].
# Rotations in degrees as Blockbench shows them; positions in pixels. Each bone and channel interpolates smoothly
# (Catmull-Rom); "loop" anims end where they start.
def anims():
	A = {}
	wave = lambda n, amp, phase=0.0, length=3.0: [(length * k / n, amp * math.sin(2 * math.pi * k / n + phase)) for k in range(n + 1)]

	def rot(pairs, axis):
		return [(t, tuple(v if a == axis else 0 for a in "xyz")) for t, v in pairs]

	def pos(pairs):
		return [(t, (0, v, 0)) for t, v in pairs]

	both = lambda s, v: v * s
	arms = [("u", "r", 1), ("u", "l", -1), ("d", "r", 1), ("d", "l", -1)]

	# idle: it hangs in the air breathing slow, the ribs heave, the arms flex, the tail sways
	idle = {"body": {"position": pos(wave(4, 1.5)), "rotation": rot(wave(4, 2.5, 1.0), "x")},
			"tail": {"rotation": [(t, (8 + v, 0, v * 0.6)) for t, v in wave(4, 6, 0.6)]},
			"tail_tip": {"rotation": [(t, (6 + v, 0, v)) for t, v in wave(4, 8, 1.4)]},
			"spine": {"rotation": rot(wave(4, 3, 0.3), "x")}}
	for tier, side, s in arms:
		idle[f"arm_{tier}{side}"] = {"rotation": [(t, (v * 0.5, 0, s * v)) for t, v in wave(4, 5 if tier == "u" else 4, 0.5 if tier == "u" else 1.2)]}
		idle[f"arm_{tier}{side}_fore"] = {"rotation": [(t, (0, 0, s * v)) for t, v in wave(4, 6, 1.5)]}
	A["idle"] = ("loop", 3.0, idle)

	# orbit: circling its quarry, leant into the turn, arms swept back, the tail streaming out behind
	orbit = {"body": {"rotation": [(t, (14 + v, 0, 0)) for t, v in wave(4, 2, length=2.0)], "position": pos(wave(4, 1.0, length=2.0))},
			 "spine": {"rotation": [(t, (18 + v, 0, 0)) for t, v in wave(4, 4, 0.8, 2.0)]},
			 "tail": {"rotation": [(t, (30 + v, 0, v)) for t, v in wave(4, 6, 1.2, 2.0)]},
			 "tail_tip": {"rotation": [(t, (25 + v, 0, v)) for t, v in wave(4, 10, 1.8, 2.0)]}}
	for tier, side, s in arms:
		orbit[f"arm_{tier}{side}"] = {"rotation": [(t, (0, s * (28 + v), s * 10)) for t, v in wave(4, 4, 0.4, 2.0)]}
		orbit[f"arm_{tier}{side}_fore"] = {"rotation": [(t, (0, s * 15, s * (8 + v))) for t, v in wave(4, 6, 1.0, 2.0)]}
	A["orbit"] = ("loop", 2.0, orbit)
	# the same, angry (its last third): lower, harder, faster
	fast = copy.deepcopy(orbit)
	for bone in fast.values():
		for ch, keys in bone.items():
			bone[ch] = [(t * 0.6, tuple(v * 1.35 for v in val)) for t, val in keys]
	A["orbit_fast"] = ("loop", 1.2, fast)

	# ravage: it rears and spits a skull at a house, the centre skull lunging out with it
	rav = {"body": {"rotation": [(0, (0, 0, 0)), (0.25, (-12, 0, 0)), (0.4, (10, 0, 0)), (0.8, (0, 0, 0))]},
		   "head_c": {"rotation": [(0, (0, 0, 0)), (0.25, (-20, 0, 0)), (0.4, (18, 0, 0)), (0.8, (0, 0, 0))],
					  "position": [(0, (0, 0, 0)), (0.4, (0, 0, -3)), (0.8, (0, 0, 0))]}}
	for tier, side, s in arms:
		rav[f"arm_{tier}{side}"] = {"rotation": [(0, (0, 0, 0)), (0.25, (0, s * -12, s * 22)), (0.4, (0, s * 20, s * -8)), (0.8, (0, 0, 0))]}
	A["ravage"] = ("once", 0.8, rav)

	# the beam's tell (55 ticks): it draws back, spreads its four arms wide, the three skulls all bear on you, shaking
	tell = {"body": {"rotation": [(0, (0, 0, 0)), (0.6, (-16, 0, 0)), (2.75, (-18, 0, 0))],
					 "position": [(0, (0, 0, 0)), (0.6, (0, 2, 2)), (2.75, (0, 3, 3))]},
			"head_c": {"rotation": [(0, (0, 0, 0)), (0.6, (14, 0, 0))] + [(0.6 + k * 0.1, (14 + (1.5 if k % 2 else -1.5), 0, 0)) for k in range(1, 22)]},
			"pauldron_r": {"rotation": [(0, (0, 0, 0)), (0.6, (0, 0, -8)), (2.75, (0, 0, -8))]},
			"pauldron_l": {"rotation": [(0, (0, 0, 0)), (0.6, (0, 0, 8)), (2.75, (0, 0, 8))]},
			"tail": {"rotation": [(0, (0, 0, 0)), (0.6, (-15, 0, 0)), (2.75, (-15, 0, 0))]}}
	for tier, side, s in arms:
		tell[f"arm_{tier}{side}"] = {"rotation": [(0, (0, 0, 0)), (0.6, (0, s * -20, s * 38)), (2.75, (0, s * -22, s * 42))]}
		tell[f"arm_{tier}{side}_fore"] = {"rotation": [(0, (0, 0, 0)), (0.6, (0, 0, s * 30)), (2.75, (0, 0, s * 34))]}
	A["beam_tell"] = ("hold", 2.75, tell)
	# the beam: it throws its head forward into the shot and the arms snap back
	fire = {"body": {"rotation": [(0, (-18, 0, 0)), (0.1, (14, 0, 0)), (0.7, (0, 0, 0))],
					 "position": [(0, (0, 3, 3)), (0.1, (0, 0, -4)), (0.7, (0, 0, 0))]},
			"head_c": {"rotation": [(0, (14, 0, 0)), (0.1, (24, 0, 0)), (0.7, (0, 0, 0))]}}
	for tier, side, s in arms:
		fire[f"arm_{tier}{side}"] = {"rotation": [(0, (0, s * -22, s * 42)), (0.1, (0, s * 35, s * 5)), (0.7, (0, 0, 0))]}
		fire[f"arm_{tier}{side}_fore"] = {"rotation": [(0, (0, 0, s * 34)), (0.1, (0, 0, s * -10)), (0.7, (0, 0, 0))]}
	A["beam_fire"] = ("once", 0.7, fire)

	# the charge's tell (28 ticks): it coils, leaning back, the arms reaching forward with the claws together
	ct = {"body": {"rotation": [(0, (0, 0, 0)), (0.5, (-22, 0, 0)), (1.4, (-26, 0, 0))],
				   "position": [(0, (0, 0, 0)), (1.4, (0, 1, 4))]},
		  "spine": {"rotation": [(0, (0, 0, 0)), (0.5, (-20, 0, 0)), (1.4, (-24, 0, 0))]},
		  "head_c": {"rotation": [(0, (0, 0, 0)), (0.5, (22, 0, 0)), (1.4, (26, 0, 0))]}}
	for tier, side, s in arms:
		ct[f"arm_{tier}{side}"] = {"rotation": [(0, (0, 0, 0)), (0.5, (0, s * -55, s * 8)), (1.4, (0, s * -60, s * 10))]}
		ct[f"arm_{tier}{side}_fore"] = {"rotation": [(0, (0, 0, 0)), (0.5, (0, s * -20, 0)), (1.4, (0, s * -25, 0))]}
	A["charge_tell"] = ("hold", 1.4, ct)
	# the rush: thrown forward flat out, the arms swept right back, the tail straight behind
	rush = {"body": {"rotation": [(t, (38 + v, 0, v * 0.5)) for t, v in wave(4, 3, length=0.6)]},
			"spine": {"rotation": [(0, (35, 0, 0)), (0.6, (35, 0, 0))]},
			"tail": {"rotation": [(t, (40 + v, 0, 0)) for t, v in wave(4, 5, 1.0, 0.6)]},
			"head_c": {"rotation": [(0, (-28, 0, 0)), (0.6, (-28, 0, 0))]}}
	for tier, side, s in arms:
		rush[f"arm_{tier}{side}"] = {"rotation": [(t, (0, s * (55 + v), s * -5)) for t, v in wave(4, 4, 0.5, 0.6)]}
		rush[f"arm_{tier}{side}_fore"] = {"rotation": [(0, (0, s * 25, 0)), (0.6, (0, s * 25, 0))]}
	A["charge_rush"] = ("loop", 0.6, rush)

	# the stun after a shield stops the charge: slumped, the skulls hung, the arms limp, swaying dazed
	stun = {"body": {"rotation": [(t, (24 + v, 0, v * 1.5)) for t, v in wave(4, 3, length=2.0)],
					 "position": [(t, (0, -3 + v, 0)) for t, v in wave(4, 0.8, 0.5, 2.0)]},
			"head_c": {"rotation": [(t, (35, v * 3, v * 4)) for t, v in wave(4, 3, 1.0, 2.0)]},
			"head_r": {"rotation": [(t, (30, 0, 12 + v)) for t, v in wave(4, 3, 0.5, 2.0)]},
			"head_l": {"rotation": [(t, (30, 0, -12 + v)) for t, v in wave(4, 3, 1.5, 2.0)]},
			"spine": {"rotation": [(0, (-10, 0, 0)), (2.0, (-10, 0, 0))]},
			"tail": {"rotation": [(0, (-8, 0, 0)), (2.0, (-8, 0, 0))]}}
	for tier, side, s in arms:
		stun[f"arm_{tier}{side}"] = {"rotation": [(t, (0, 0, s * (-45 + v))) for t, v in wave(4, 4, 0.7, 2.0)]}
		stun[f"arm_{tier}{side}_fore"] = {"rotation": [(0, (0, 0, s * -40)), (2.0, (0, 0, s * -40))]}
	A["stun"] = ("loop", 2.0, stun)

	# the climb for the skull rain (40 ticks): it rises with its arms raised high, looking down on the marks
	climb = {"body": {"rotation": [(0, (0, 0, 0)), (0.6, (-10, 0, 0)), (2.0, (12, 0, 0))]},
			 "head_c": {"rotation": [(0, (0, 0, 0)), (2.0, (30, 0, 0))]},
			 "head_r": {"rotation": [(0, (0, 0, 0)), (2.0, (25, 0, 0))]},
			 "head_l": {"rotation": [(0, (0, 0, 0)), (2.0, (25, 0, 0))]},
			 "tail": {"rotation": [(0, (0, 0, 0)), (2.0, (-20, 0, 0))]}}
	for tier, side, s in arms:
		climb[f"arm_{tier}{side}"] = {"rotation": [(0, (0, 0, 0)), (0.6, (0, 0, s * 60)), (2.0, (0, s * -10, s * 55))]}
		climb[f"arm_{tier}{side}_fore"] = {"rotation": [(0, (0, 0, 0)), (0.6, (0, 0, s * 45)), (2.0, (0, 0, s * 40))]}
	A["climb"] = ("hold", 2.0, climb)

	# the blue skulls (one throw every 14 ticks): an upper arm sweeps forward to fling each, the side skulls spit
	blues = {"body": {"rotation": [(0, (0, 0, 0)), (0.25, (-6, 8, 0)), (0.45, (8, -6, 0)), (0.7, (0, 0, 0))]},
			 "head_r": {"rotation": [(0, (0, 0, 0)), (0.3, (-15, 0, 0)), (0.45, (15, 0, 0)), (0.7, (0, 0, 0))]},
			 "head_l": {"rotation": [(0, (0, 0, 0)), (0.3, (-15, 0, 0)), (0.45, (15, 0, 0)), (0.7, (0, 0, 0))]},
			 "arm_ur": {"rotation": [(0, (0, 0, 0)), (0.25, (0, 30, 45)), (0.45, (0, -50, -10)), (0.7, (0, 0, 0))]},
			 "arm_ul": {"rotation": [(0, (0, 0, 0)), (0.3, (0, -30, -45)), (0.5, (0, 50, 10)), (0.7, (0, 0, 0))]},
			 "arm_ur_fore": {"rotation": [(0, (0, 0, 0)), (0.25, (0, 0, 30)), (0.45, (0, -20, -10)), (0.7, (0, 0, 0))]},
			 "arm_ul_fore": {"rotation": [(0, (0, 0, 0)), (0.3, (0, 0, -30)), (0.5, (0, 20, 10)), (0.7, (0, 0, 0))]}}
	A["throw_blues"] = ("loop", 0.7, blues)

	# the spawn (its 11 seconds of build-up): curled tight with the arms folded in, then it unfolds and roars
	sp = {"body": {"rotation": [(0, (40, 0, 0)), (8.5, (36, 0, 0)), (10.2, (-18, 0, 0)), (11.0, (0, 0, 0))],
				   "position": [(0, (0, -6, 0)), (8.5, (0, -5, 0)), (10.2, (0, 2, 0)), (11.0, (0, 0, 0))]},
		  "head_c": {"rotation": [(0, (40, 0, 0)), (8.5, (38, 0, 0)), (10.2, (-30, 0, 0)), (11.0, (0, 0, 0))]},
		  "head_r": {"rotation": [(0, (35, 0, 15)), (8.5, (32, 0, 15)), (10.2, (-20, 0, 0)), (11.0, (0, 0, 0))]},
		  "head_l": {"rotation": [(0, (35, 0, -15)), (8.5, (32, 0, -15)), (10.2, (-20, 0, 0)), (11.0, (0, 0, 0))]},
		  "spine": {"rotation": [(0, (-45, 0, 0)), (8.5, (-42, 0, 0)), (10.2, (5, 0, 0)), (11.0, (0, 0, 0))]},
		  "tail": {"rotation": [(0, (-50, 0, 0)), (8.5, (-48, 0, 0)), (10.2, (10, 0, 0)), (11.0, (0, 0, 0))]}}
	for tier, side, s in arms:
		sp[f"arm_{tier}{side}"] = {"rotation": [(0, (0, s * -70, s * -50)), (8.5, (0, s * -65, s * -48)), (10.2, (0, s * -10, s * 50)), (11.0, (0, 0, 0))]}
		sp[f"arm_{tier}{side}_fore"] = {"rotation": [(0, (0, 0, s * -70)), (8.5, (0, 0, s * -65)), (10.2, (0, 0, s * 30)), (11.0, (0, 0, 0))]}
	A["spawn"] = ("hold", 11.0, sp)

	# the death: it shudders, the skulls loll, the arms fall and it sags, sinking (the game's last second of it)
	de = {"body": {"rotation": [(0, (0, 0, 0)), (0.3, (-12, 0, 6)), (1.0, (45, 0, -10))],
				   "position": [(0, (0, 0, 0)), (0.3, (0, 2, 0)), (1.0, (0, -10, 0))]},
		  "head_c": {"rotation": [(0, (0, 0, 0)), (0.3, (-25, 0, 0)), (1.0, (50, 15, 10))]},
		  "head_r": {"rotation": [(0, (0, 0, 0)), (1.0, (40, 0, 25))]},
		  "head_l": {"rotation": [(0, (0, 0, 0)), (1.0, (40, 0, -25))]},
		  "tail": {"rotation": [(0, (0, 0, 0)), (1.0, (-30, 0, 0))]}}
	for tier, side, s in arms:
		de[f"arm_{tier}{side}"] = {"rotation": [(0, (0, 0, 0)), (0.3, (0, 0, s * 40)), (1.0, (0, 0, s * -60))]}
		de[f"arm_{tier}{side}_fore"] = {"rotation": [(0, (0, 0, 0)), (1.0, (0, 0, s * -45))]}
	A["death"] = ("hold", 1.0, de)
	return A


# ---- writing ------------------------------------------------------------------------------------------------------------
def project(skin):
	buf = io.BytesIO()
	skin.save(buf, "PNG")
	tex_uuid = uid()
	groups = {}
	tree = []
	for g in GROUPS:
		node = {"name": g["name"], "origin": g["origin"], "rotation": g["rotation"], "uuid": uid(), "export": True,
				"isOpen": True, "children": []}
		groups[g["name"]] = node
		(groups[g["parent"]]["children"] if g["parent"] else tree).append(node)
	elements = []
	for c in CUBES:
		faces = {}
		for fn, (u0, v0, u1, v1, _, _) in c["uv"].items():
			faces[fn] = {"uv": [u0, v0, u1, v1], "texture": 0}
		e = {"name": c["mat"], "type": "cube", "uuid": uid(), "from": c["from"], "to": c["to"],
			 "origin": groups[c["group"]]["origin"], "rotation": [0, 0, 0], "box_uv": False, "faces": faces, "export": True}
		elements.append(e)
		groups[c["group"]]["children"].append(e["uuid"])
	animations = []
	for name, (loop, length, bones) in anims().items():
		animators = {}
		for bone, chans in bones.items():
			g = groups[bone]
			kfs = []
			for ch, keys in chans.items():
				for t, (x, y, z) in keys:
					kfs.append({"channel": ch, "data_points": [{"x": round(x, 3), "y": round(y, 3), "z": round(z, 3)}],
								"uuid": uid(), "time": round(t, 4), "interpolation": "catmullrom"})
			animators[g["uuid"]] = {"name": bone, "type": "bone", "keyframes": kfs}
		animations.append({"uuid": uid(), "name": "animation.wither." + name, "loop": loop, "length": length,
						   "snapping": 20, "animators": animators})
	return {
		"meta": {"format_version": "4.10", "model_format": "free", "box_uv": False},
		"name": "wither",
		"resolution": {"width": TEX, "height": TEX},
		"elements": elements,
		"outliner": tree,
		"textures": [{"name": "wither.png", "uuid": tex_uuid, "width": TEX, "height": TEX, "uv_width": TEX, "uv_height": TEX,
					  "source": "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()}],
		"animations": animations,
	}


def bake(path, name):
	subprocess.run([sys.executable, "-I", os.path.join(HERE, "bbmodel_bake.py"), path, ASSETS, name], check=True)


def main():
	random.seed(SEED)
	build()
	layout()
	skin, glow = paint()
	data = project(skin)
	os.makedirs(os.path.dirname(ART), exist_ok=True)
	with open(ART, "w", encoding="utf-8", newline="\n") as f:
		json.dump(data, f, indent="\t")
		f.write("\n")
	bake(ART, "wither")
	glow.save(os.path.join(ASSETS, "textures", "entity", "bb", "wither_glow.png"))
	# while it's being raised it flickers ash-pale (vanilla's does too): the same skin washed toward white-violet
	pale = skin.copy()
	pp = pale.load()
	for y in range(TEX):
		for x in range(TEX):
			r, g, b_, a_ = pp[x, y]
			if a_:
				pp[x, y] = mix((r, g, b_), (214, 206, 226), 0.62) + (a_,)
	pale.save(os.path.join(ASSETS, "textures", "entity", "bb", "wither_pale.png"))
	# the half-health shield: the same bones, every box puffed out half a pixel, drawn with the vanilla armour swirl
	armour = copy.deepcopy(data)
	for e in armour["elements"]:
		e["inflate"] = 0.6
	armour["animations"] = data["animations"]
	tmp = os.path.join(KINGDOMS, "build", "wither_armor.bbmodel")
	os.makedirs(os.path.dirname(tmp), exist_ok=True)
	with open(tmp, "w", encoding="utf-8") as f:
		json.dump(armour, f)
	bake(tmp, "wither_armor")
	os.remove(tmp)
	# (its own skin is never read: the shield draws the swirl texture)
	os.remove(os.path.join(ASSETS, "textures", "entity", "bb", "wither_armor.png"))


if __name__ == "__main__":
	main()
