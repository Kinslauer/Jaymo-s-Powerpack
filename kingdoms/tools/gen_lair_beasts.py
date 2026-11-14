"""
The sand worm's and the thing under the wreck's own bodies, kingdoms/art/sand_worm.bbmodel and wreck_thing.bbmodel: our
own cubes, textures and animations, all made here (docs/Lair-Beasts-Model-Spec.md is the spec they follow). They take
the place of the two plain box towers the beasts were first drawn as.

The sand worm: eight armoured rings, each riding the one under it and a little narrower, a plate band, a back spine and
side bristles on every ring, a pale belly, and a head that opens like a flower: four plated jaws hinged on a collar
round a toothed gullet, four hooks between them.

The thing under the wreck: a squat spotted body with two lamp eyes under heavy brows, a mantle lying out behind it
with two fins, a beak on top in a ring of eight jointed arms (pale and suckered on the inside), and two long thin
feeders ending in hooked clubs.

Both are drawn by our own loader (client BbMobRenderer), which plays an animation by the name of the move the beast is
making, so the moves are named for beasts/SandWorm's and beasts/WreckThing's actions and last as long as they do there.

Blockbench space: y up, the ground at 0, the front at -z. Keyframes as Blockbench shows them: +x tips an upright part
forward (toward -z).

Run from the project root: python3 -I kingdoms/tools/gen_lair_beasts.py [sand_worm|wreck_thing] [--preview <dir>]
(needs Pillow)
"""
import json
import math
import os
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import gen_tomb_undead as kit  # noqa: E402
from gen_tomb_undead import Builder, anim, add_animations, bone_uuids_of, shade  # noqa: E402

ROOT = kit.ROOT
ASSETS = os.path.join(ROOT, "kingdoms", "src", "main", "resources", "assets", "jaymoskingdoms")
Z = (0, 0, 0)
SIDES = ("north", "south", "east", "west")


def px(img, x, y, c, r=None):
	if r is None or (r[0] <= x < r[2] and r[1] <= y < r[3]):
		img.putpixel((x, y), tuple(c) + (255,))


def mul(img, x, y, k):
	c = img.getpixel((x, y))
	img.putpixel((x, y), shade(c[:3], k) + (255,))


def on_sides(fx):
	return {s: fx for s in SIDES}


def wave(amp, period, phase, times, base=0.0, fn=math.sin):
	return [base + amp * fn(2 * math.pi * t / period + phase) for t in times]


# ================================================================ the sand worm

WORM = {"hide": (196, 174, 124), "plate": (126, 102, 68), "belly": (228, 212, 168), "maw": (84, 24, 28), "tooth": (238, 232, 214),
	"spine": (92, 72, 48), "gum": (150, 62, 60)}
SEGS, SEG_H = 8, 11.0
HALF = [10.0, 9.5, 9.5, 9.0, 9.0, 8.5, 8.5, 8.0]
NECK_Y = SEGS * SEG_H  # 88
RIM_Y = NECK_Y + 5.0
# how far in a jaw leans shut, a hook leans in (degrees)
JAW_SHUT, HOOK_IN = -24.0, -14.0


def worm_hide(img, r):
	"""A ring's side: creases running round it, darker toward the ring under it, grit."""
	x0, y0, x1, y1 = r
	for y in range(y0, y1):
		for x in range(x0, x1):
			ly = y - y0
			if ly in (3, 7):
				mul(img, x, y, 0.84)
			elif ly == y1 - y0 - 1:
				mul(img, x, y, 0.72)
			elif (x * 13 + y * 7) % 11 == 0:
				mul(img, x, y, 1.12)


def worm_plate(img, r):
	"""A band of plate: a bright upper edge, a dark seam under it, rivet-like knobs."""
	x0, y0, x1, y1 = r
	for x in range(x0, x1):
		mul(img, x, y0, 1.3)
		mul(img, x, y1 - 1, 0.7)
		if (x - x0) % 4 == 1 and y1 - y0 >= 3:
			mul(img, x, y0 + 1, 1.18)


def worm_gullet(img, r):
	"""Down the throat: rings of teeth closing on the dark."""
	x0, y0, x1, y1 = r
	cx, cy = (x0 + x1 - 1) / 2, (y0 + y1 - 1) / 2
	for y in range(y0, y1):
		for x in range(x0, x1):
			d = max(abs(x - cx), abs(y - cy))
			ring = int(d + 0.5)
			c = shade(WORM["maw"], 0.3 + ring * 0.12)
			if ring in (3, 5, 7) and (x + y) % 2 == 0:
				c = shade(WORM["tooth"], 0.62 + ring * 0.05)
			px(img, x, y, c)


def worm_jaw_inside(img, r):
	"""The inside of a jaw: gum, three rows of teeth pointing down the throat."""
	x0, y0, x1, y1 = r
	for y in range(y0, y1):
		for x in range(x0, x1):
			c = shade(WORM["gum"], 0.8 + ((x * 5 + y * 3) % 4) * 0.06)
			if (y - y0) % 3 == 1 and (x - x0) % 2 == (y - y0) // 3 % 2 and x0 < x < x1 - 1:
				c = WORM["tooth"]
			px(img, x, y, c)


def worm_jaw_outside(img, r):
	"""A jaw's plate from outside: a ridge down the middle, edges worn bright."""
	x0, y0, x1, y1 = r
	mid = (x0 + x1) // 2
	for y in range(y0, y1):
		mul(img, mid, y, 1.25)
		mul(img, mid - 1, y, 0.8)
		mul(img, x0, y, 1.15)
		mul(img, x1 - 1, y, 0.75)


def build_worm(seed=71):
	kit.PALETTE.update(WORM)
	b = Builder(512, 256, seed)
	b.bone("root", None, Z)
	for i in range(SEGS):
		w, y = HALF[i], i * SEG_H
		b.bone(f"seg{i}", f"seg{i - 1}" if i else "root", (0, y, 0))
		b.cube(f"seg{i}", (-w, y, -w), (w, y + SEG_H, w), "hide", faces_paint=on_sides(worm_hide))
		# the belly, a paler strip down the front
		b.cube(f"seg{i}", (-w * 0.6, y + 0.5, -w - 0.5), (w * 0.6, y + SEG_H - 0.5, -w), "belly", faces_paint={"north": worm_hide})
		# the plate band round the ring, a spine on its back and a bristle on either side
		b.cube(f"seg{i}", (-w - 1, y + 7, -w + 2), (w + 1, y + 10, w + 1), "plate", faces_paint=on_sides(worm_plate))
		b.cube(f"seg{i}", (-1.5, y + 7, w + 0.5), (1.5, y + 12, w + 3.5), "spine", rotation=(-28, 0, 0), origin=(0, y + 8, w + 1))
		for s in (1, -1):
			x0, x1 = sorted((s * (w + 0.5), s * (w + 3.5)))
			b.cube(f"seg{i}", (x0, y + 8, -1), (x1, y + 10, 1), "spine", rotation=(0, 0, s * 24), origin=(s * (w + 1), y + 9, 0))
	# the head: a collar, the gullet inside it
	b.bone("head", f"seg{SEGS - 1}", (0, NECK_Y, 0))
	b.cube("head", (-10, NECK_Y, -10), (10, RIM_Y, 10), "plate", faces_paint=on_sides(worm_plate))
	b.cube("head", (-7.5, RIM_Y, -7.5), (7.5, RIM_Y + 1, 7.5), "maw", faces_paint={"up": worm_gullet})
	# four jaws, each drawn at the front and turned to its side; they lean in to close as a bud
	for k, name in enumerate(("front", "left", "back", "right")):
		b.bone(f"jaw_{name}_turn", "head", (0, RIM_Y, 0), (0, 90 * k, 0))
		j = f"jaw_{name}"
		b.bone(j, f"jaw_{name}_turn", (0, RIM_Y, -8.5), (JAW_SHUT, 0, 0))
		b.cube(j, (-6.5, RIM_Y, -10.5), (6.5, RIM_Y + 9, -7), "plate", faces_paint={"south": worm_jaw_inside, "north": worm_jaw_outside})
		b.cube(j, (-4.5, RIM_Y + 9, -10), (4.5, RIM_Y + 15, -7), "plate", faces_paint={"south": worm_jaw_inside, "north": worm_jaw_outside})
		b.cube(j, (-2.5, RIM_Y + 15, -9.5), (2.5, RIM_Y + 18, -7), "plate", faces_paint={"south": worm_jaw_inside})
		b.cube(j, (-1.5, RIM_Y + 18, -9), (1.5, RIM_Y + 23, -7), "tooth")
	# four hooks between the jaws
	for k in range(4):
		b.bone(f"hook{k}_turn", "head", (0, RIM_Y, 0), (0, 45 + 90 * k, 0))
		b.bone(f"hook{k}", f"hook{k}_turn", (0, RIM_Y, -11), (HOOK_IN, 0, 0))
		b.cube(f"hook{k}", (-1, RIM_Y - 1, -12.5), (1, RIM_Y + 8, -10.5), "spine")
		b.cube(f"hook{k}", (-0.5, RIM_Y + 8, -12), (0.5, RIM_Y + 12, -11), "tooth")
	return b


def worm_tracks(bend, sway=1.0, twist=None):
	"""The rings' tracks: `bend` is [(time, degrees forward for each ring)], with a slow sway laid over it."""
	out = {}
	for i in range(SEGS):
		keys = []
		for t, deg in bend:
			# the upper rings carry a little more of a bend than the lower ones
			share = 0.75 + 0.5 * i / (SEGS - 1)
			keys.append((t, (deg * share + sway * 1.6 * math.sin(t * 1.9 + i * 0.6), twist(t, i) if twist else 0.0,
							 sway * 2.2 * math.sin(t * 1.5 + i * 0.7 + 1.0))))
		out[f"seg{i}"] = {"rotation": keys}
	return out


def jaws(keys, hooks=None):
	out = {f"jaw_{n}": {"rotation": [(t, (v, 0, 0)) for t, v in keys]} for n in ("front", "left", "back", "right")}
	for k in range(4):
		out[f"hook{k}"] = {"rotation": [(t, (v * 0.6, 0, 0)) for t, v in (hooks or keys)]}
	return out


def worm_sway(name, length, open_deg):
	"""Standing and swaying: a slow wave up the rings, the jaws breathing open and shut."""
	times = [length * n / 8 for n in range(9)]
	tracks = {}
	for i in range(SEGS):
		xs = wave(2.4, length, -i * 0.6, times)
		zs = wave(3.0, length, -i * 0.7 + 1.3, times)
		tracks[f"seg{i}"] = {"rotation": [(t, (xs[n], 0, zs[n])) for n, t in enumerate(times)]}
	tracks.update(jaws([(t, open_deg + 6 * math.sin(2 * math.pi * t / length * 2)) for t in times]))
	tracks["head"] = {"rotation": [(t, (0, 14 * math.sin(2 * math.pi * t / length), 0)) for t in times]}
	return anim(name, length, "loop", tracks)


def worm_animations():
	idle = worm_sway("idle", 4.0, 6.0)
	# "warn" is the sand boiling over it while it is still under (the renderer keeps it down): it has nothing to show
	warn = worm_sway("warn", 4.0, 6.0)
	up = worm_sway("up", 4.0, 16.0)
	# rise (10 ticks): through the sand with its jaws bursting open, arched back a little
	rise = anim("rise", 0.5, "hold", {
		**worm_tracks([(0, 0), (0.2, -2.5), (0.5, 0)], sway=0.0),
		**jaws([(0, 8), (0.15, 78), (0.35, 70), (0.5, 24)]),
	})
	# bite (30 ticks, landing at 16): reared back with the jaws spread, thrown down flat along its strike, the jaws
	# snapping shut on what is there, then drawn back up
	bite = anim("bite", 1.5, "once", {
		**worm_tracks([(0, 0), (0.45, -3.2), (0.65, -4.0), (0.8, 10.2), (1.0, 10.2), (1.5, 0)], sway=0.0),
		**jaws([(0, 16), (0.45, 70), (0.7, 84), (0.82, -4), (1.05, -4), (1.5, 16)]),
		"head": {"rotation": [(0, Z), (0.65, (-10, 0, 0)), (0.8, (12, 0, 0)), (1.0, (12, 0, 0)), (1.5, Z)]},
	})
	# sink (20 ticks): shut, turning as it draws itself back under
	sink = anim("sink", 1.0, "hold", {
		**worm_tracks([(0, 0), (0.5, 1.5), (1.0, 0)], sway=0.6),
		**jaws([(0, 16), (0.3, -3), (1.0, -3)]),
		"root": {"rotation": [(0, Z), (0.5, (0, 80, 0)), (1.0, (0, 200, 0))]},
	})
	# death: it comes down along its length and its jaws fall open
	death = anim("death", 1.0, "hold", {
		**worm_tracks([(0, 0), (0.25, -2.5), (0.7, 10.8), (1.0, 11.2)], sway=0.4),
		**jaws([(0, 16), (0.25, 80), (0.7, 40), (1.0, 58)]),
		"head": {"rotation": [(0, Z), (0.7, (8, 0, 14)), (1.0, (10, 0, 18))]},
	})
	return [idle, warn, up, rise, bite, sink, death]


WORM_PREVIEWS = [("idle", 0.0), ("up", 1.0), ("rise", 0.15), ("bite", 0.65), ("bite", 0.8), ("bite", 0.9), ("sink", 0.5), ("death", 1.0)]


# ================================================================ the thing under the wreck

THING = {"hide": (112, 40, 66), "dark": (72, 24, 50), "pale": (212, 172, 180), "eye": (226, 198, 84), "beak": (40, 30, 28),
	"maw": (60, 16, 28), "tooth": (236, 226, 210)}
ARMS, JOINTS, LENGTH, RIM = 8, 5, 12.0, 9.5
ARM_Y = 22.0
ARM_HALF = [3.5, 3.0, 2.5, 2.0, 1.5]
FEED_JOINTS, FEED_LEN = 6, 13.0
# a joint's lean (degrees, outward above 0): at rest, opened for a blow, shut over it, curled round what it holds
REST, OPEN, SHUT, CURL = 6.0, 18.0, -11.5, -17.0


def thing_hide(img, r):
	"""Spotted hide: paler rings scattered on it, darker toward the bottom."""
	x0, y0, x1, y1 = r
	h = y1 - y0
	for y in range(y0, y1):
		for x in range(x0, x1):
			if y - y0 >= h - 2:
				mul(img, x, y, 0.8)
			v = (x * 31 + y * 17) % 23
			if v == 0:
				px(img, x, y, shade(THING["pale"], 0.72))
			elif v in (1, 8):
				mul(img, x, y, 1.22)


def thing_suckers(img, r):
	"""The inside of an arm: pale, two rows of round suckers."""
	x0, y0, x1, y1 = r
	w = x1 - x0
	for y in range(y0, y1):
		for x in range(x0, x1):
			px(img, x, y, shade(THING["pale"], 0.86 + ((x + y) % 3) * 0.05))
	cols = (x0 + w // 2,) if w < 4 else (x0 + 1, x1 - 2)
	for y in range(y0 + 1, y1, 3):
		for x in cols:
			px(img, x, y, shade(THING["pale"], 1.16), r)
			px(img, x, y + 1, shade(THING["hide"], 1.25), r)


def thing_eye(img, r):
	"""A lamp eye: gold, darker at the rim, a level slit of a pupil."""
	x0, y0, x1, y1 = r
	for y in range(y0, y1):
		for x in range(x0, x1):
			edge = x in (x0, x1 - 1) or y in (y0, y1 - 1)
			px(img, x, y, shade(THING["eye"], 0.68 if edge else 1.0 + ((x + y) % 2) * 0.08))
	mid = (y0 + y1) // 2
	for x in range(x0 + 1, x1 - 1):
		px(img, x, mid, (18, 10, 16))
	px(img, x0 + 1, y0 + 1, (255, 244, 190))


def thing_mouth(img, r):
	"""The mound the beak stands in: dark flesh ringed with small hooked teeth."""
	x0, y0, x1, y1 = r
	for y in range(y0, y1):
		for x in range(x0, x1):
			edge = x in (x0, x1 - 1) or y in (y0, y1 - 1)
			px(img, x, y, THING["tooth"] if edge and (x + y) % 2 == 0 else shade(THING["maw"], 0.8 + ((x * 3 + y) % 3) * 0.12))


def thing_club(img, r):
	"""A feeder's club from inside: pale, set with hooks."""
	thing_suckers(img, r)
	x0, y0, x1, y1 = r
	for y in range(y0 + 1, y1 - 1, 2):
		for x in range(x0 + 1 + (y - y0) // 2 % 2, x1 - 1, 2):
			px(img, x, y, THING["tooth"])


def build_thing(seed=83):
	kit.PALETTE.update(THING)
	b = Builder(512, 256, seed)
	b.bone("root", None, Z)
	b.bone("body", "root", (0, 0, 0))
	hide = on_sides(thing_hide)
	# the body: a wide skirt on the bottom, the bulk, a shoulder the arms stand on
	b.cube("body", (-13, 0, -13), (13, 4, 13), "dark", faces_paint=hide)
	b.cube("body", (-12, 4, -12), (12, 18, 12), "hide", faces_paint=hide)
	b.cube("body", (-10.5, 18, -10.5), (10.5, 24, 10.5), "hide", faces_paint=hide)
	b.cube("body", (-5, 24, -5), (5, 26, 5), "dark", faces_paint={"up": thing_mouth})
	# the face: two lamp eyes standing out of the front under heavy brows, a siphon between and below them
	for s in (1, -1):
		x0, x1 = sorted((s * 3, s * 11))
		b.cube("body", (x0, 8, -14), (x1, 16, -12), "eye", faces_paint={"north": thing_eye})
		x0, x1 = sorted((s * 2, s * 12))
		b.cube("body", (x0, 16, -14.5), (x1, 18.5, -11.5), "dark", rotation=(0, 0, -s * 9), origin=(s * 7, 17, -13))
	b.cube("body", (-2.5, 2, -15.5), (2.5, 7, -12), "pale")
	# the mantle rising out behind, its two fins
	b.bone("mantle", "body", (0, 12, 10), (24, 0, 0))
	b.cube("mantle", (-10, 4, 10), (10, 22, 32), "hide", faces_paint=hide)
	b.cube("mantle", (-7.5, 6, 32), (7.5, 19, 46), "hide", faces_paint=hide)
	b.cube("mantle", (-4.5, 8, 46), (4.5, 16, 56), "dark", faces_paint=hide)
	for s, side in ((1, "right"), (-1, "left")):
		b.bone(f"{side}_fin", "mantle", (s * 7.5, 12, 42))
		x0, x1 = sorted((s * 7.5, s * 19))
		b.cube(f"{side}_fin", (x0, 11, 34), (x1, 13, 52), "pale")
	# the beak, standing up in the ring of arms: two halves that part
	b.bone("beak_back", "body", (0, 26, 1.5))
	b.cube("beak_back", (-2.5, 26, 0), (2.5, 32, 3), "beak")
	b.cube("beak_back", (-1.5, 31, -2), (1.5, 34, 1), "beak")
	b.bone("beak_front", "body", (0, 26, -1.5))
	b.cube("beak_front", (-2, 26, -3), (2, 30.5, 0), "beak")
	# the arms: each drawn at the front and turned to its place; pale and suckered on the side that faces the beak
	for k in range(ARMS):
		b.bone(f"arm{k}_turn", "body", (0, ARM_Y, 0), (0, (k + 0.5) * 360.0 / ARMS, 0))
		parent = f"arm{k}_turn"
		for j in range(JOINTS):
			name, y, hw = f"arm{k}_{j}", ARM_Y + j * LENGTH, ARM_HALF[j]
			b.bone(name, parent, (0, y, -RIM), (REST, 0, 0))
			b.cube(name, (-hw, y, -RIM - hw), (hw, y + LENGTH, -RIM + hw), "hide", faces_paint={"south": thing_suckers, "north": thing_hide})
			parent = name
		tip = ARM_Y + JOINTS * LENGTH
		b.cube(parent, (-0.75, tip, -RIM - 0.75), (0.75, tip + 3, -RIM + 0.75), "pale")
	# the two feeders: thin, longer than the arms, ending in hooked clubs
	for s, side in ((1, "right"), (-1, "left")):
		b.bone(f"{side}_feeder_turn", "body", (0, ARM_Y, 0), (0, -s * 90, 0))
		parent = f"{side}_feeder_turn"
		for j in range(FEED_JOINTS):
			name, y = f"{side}_feeder_{j}", ARM_Y + 2 + j * FEED_LEN
			b.bone(name, parent, (0, y, -5.5), (REST, 0, 0))
			if j < FEED_JOINTS - 1:
				b.cube(name, (-1, y, -6.5), (1, y + FEED_LEN, -4.5), "dark")
			else:
				b.cube(name, (-3.5, y, -6.5), (3.5, y + 10, -4.5), "hide", faces_paint={"south": thing_club, "north": thing_hide})
				b.cube(name, (-2, y + 10, -6.5), (2, y + 14, -4.5), "hide", faces_paint={"south": thing_club})
			parent = name
	return b


def arm_tracks(lean, sway=1.0, period=6.0):
	"""Every arm's and feeder's tracks: `lean` is [(time, degrees outward for each joint)], with their own slow waving
	laid over it (a wave running up each arm, every arm out of step with the next)."""
	out = {}

	def chain(names, phase, reach):
		for j, name in enumerate(names):
			keys = []
			for t, deg in lean:
				w = 2 * math.pi * t / period + phase - j * 0.8
				# the foot of an arm bends least, so a shut arm closes over the body and not through it
				keys.append((t, (deg * (0.5 if j == 0 else 1.0) * reach + sway * 5.0 * math.sin(w), 0, sway * 4.0 * math.cos(w * 0.8))))
			out[name] = {"rotation": keys}

	for k in range(ARMS):
		chain([f"arm{k}_{j}" for j in range(JOINTS)], k * 1.7, 1.0)
	for n, side in enumerate(("right", "left")):
		chain([f"{side}_feeder_{j}" for j in range(FEED_JOINTS)], 0.9 + n * 2.6, 0.8)
	return out


def fins(keys):
	return {"right_fin": {"rotation": [(t, (0, 0, v)) for t, v in keys]}, "left_fin": {"rotation": [(t, (0, 0, -v)) for t, v in keys]}}


def beak(keys):
	return {"beak_back": {"rotation": [(t, (-v, 0, 0)) for t, v in keys]}, "beak_front": {"rotation": [(t, (v, 0, 0)) for t, v in keys]}}


def thing_animations():
	six = [6.0 * n / 8 for n in range(9)]
	# idle: the arms waving slowly, the mantle breathing, the fins rippling, the beak working now and then
	idle = anim("idle", 6.0, "loop", {
		**arm_tracks([(t, REST) for t in six]),
		**fins([(t, 10 * math.sin(2 * math.pi * t / 3.0)) for t in six]),
		**beak([(0, 0), (2.25, 0), (2.6, 14), (3.0, 0), (6.0, 0)]),
		"mantle": {"rotation": [(t, (3 * math.sin(2 * math.pi * t / 3.0), 0, 0)) for t in six]},
		"body": {"position": [(t, (0, 1.5 * math.sin(2 * math.pi * t / 6.0), 0)) for t in six]},
	})
	# swim: the arms opened and clapped together to drive it, trailing between strokes, the fins beating
	two = [2.0 * n / 8 for n in range(9)]
	stroke = [(0, 14), (0.25, 18), (0.5, -6), (0.75, -4), (1.0, 2), (1.5, 9), (2.0, 14)]
	swim = anim("swim", 2.0, "loop", {
		**arm_tracks(stroke, sway=0.4, period=2.0),
		**fins([(t, 22 * math.sin(2 * math.pi * t / 1.0)) for t in two]),
		"mantle": {"rotation": [(0, Z), (0.5, (-6, 0, 0)), (1.0, (2, 0, 0)), (2.0, Z)]},
	})
	# lash (26 ticks, landing at 14): the arms opened wide, clapped shut over what is above it, eased back
	lash = anim("lash", 1.3, "once", {
		**arm_tracks([(0, REST), (0.55, OPEN), (0.7, SHUT), (0.85, SHUT), (1.3, REST)], sway=0.3),
		**beak([(0, 0), (0.55, 26), (0.75, 0), (1.3, 0)]),
		"body": {"position": [(0, Z), (0.55, (0, -2, 0)), (0.7, (0, 2.5, 0)), (1.3, Z)]},
		"mantle": {"rotation": [(0, Z), (0.55, (8, 0, 0)), (0.7, (-6, 0, 0)), (1.3, Z)]},
	})
	# grab (90 ticks, closing at 14): opened, closed, and kept curled over what it holds, tightening every 15 ticks
	hold = [(0, REST), (0.55, OPEN), (0.75, CURL)]
	at = 1.5
	while at < 4.4:
		hold += [(at - 0.2, CURL + 2.5), (at, CURL - 3.0)]
		at += 0.75
	hold.append((4.5, CURL))
	grab = anim("grab", 4.5, "hold", {
		**arm_tracks(hold, sway=0.35),
		**beak([(0, 0), (0.55, 24), (0.9, 8)] + [(0.75 * n + (0.0 if n % 2 else 0.2), 22 if n % 2 else 4) for n in range(2, 6)] + [(4.5, 10)]),
		"body": {"position": [(0, Z), (0.55, (0, -2, 0)), (0.75, (0, 1.5, 0)), (4.5, (0, 1.5, 0))]},
	})
	# ink (36 ticks): the arms trailed straight together, the mantle pumping, as it darts off behind its cloud
	ink = anim("ink", 1.8, "once", {
		**arm_tracks([(0, REST), (0.2, 12), (0.4, -2), (1.5, -1), (1.8, REST)], sway=0.5, period=1.8),
		**fins([(0, 0), (0.2, 30), (0.4, -26), (0.8, 22), (1.2, -22), (1.8, 0)]),
		"mantle": {"rotation": [(0, Z), (0.2, (10, 0, 0)), (0.4, (-8, 0, 0)), (0.8, (7, 0, 0)), (1.0, (-7, 0, 0)), (1.4, (6, 0, 0)), (1.8, Z)]},
	})
	# death: the arms fall slack and splay, the mantle sags, the beak hangs open
	death = anim("death", 1.0, "hold", {
		**arm_tracks([(0, REST), (0.2, -8), (0.6, 16), (1.0, 21)], sway=0.3),
		**beak([(0, 0), (0.2, 30), (1.0, 20)]),
		**fins([(0, 0), (1.0, -24)]),
		"mantle": {"rotation": [(0, Z), (1.0, (-9, 0, 6))]},
		"body": {"position": [(0, Z), (1.0, (0, -1.5, 0))], "rotation": [(0, Z), (1.0, (0, 0, 5))]},
	})
	return [idle, swim, lash, grab, ink, death]


THING_PREVIEWS = [("idle", 0.0), ("idle", 2.6), ("swim", 0.25), ("swim", 0.5), ("lash", 0.55), ("lash", 0.7), ("grab", 1.5), ("ink", 0.5),
	("death", 1.0)]

MODELS = {"sand_worm": (build_worm, worm_animations, WORM_PREVIEWS), "wreck_thing": (build_thing, thing_animations, THING_PREVIEWS)}


def main(argv):
	preview = None
	if "--preview" in argv:
		preview = argv[argv.index("--preview") + 1]
		argv = [a for a in argv if a not in ("--preview", preview)]
	for name in argv or list(MODELS):
		build, animations, previews = MODELS[name]
		saved = dict(kit.PALETTE)
		model = build().project(name)
		kit.PALETTE.clear()
		kit.PALETTE.update(saved)
		add_animations(model, animations(), bone_uuids_of(model))
		art = os.path.join(kit.ART, name + ".bbmodel")
		with open(art, "w", encoding="utf-8", newline="\n") as f:
			json.dump(model, f)
		print("wrote kingdoms/art/" + name + ".bbmodel")
		subprocess.check_call([sys.executable, "-I", os.path.join(ROOT, "tools", "bbmodel_bake.py"), art, ASSETS, name])
		if preview:
			os.makedirs(preview, exist_ok=True)
			for a, t in previews:
				png = os.path.join(preview, f"{name}-{a}-{t}.png")
				subprocess.check_call([sys.executable, "-I", os.path.join(ROOT, "tools", "bbmodel_preview.py"), ASSETS, name, png, a, str(t)])


if __name__ == "__main__":
	main(sys.argv[1:])
