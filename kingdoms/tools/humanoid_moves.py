"""
The humanoid move library: one set of animations for every GeckoLib body of ours that has these bones (the film actor,
tools/gen_film_actor.py; the draugr, tools/gen_tomb_undead.py):

  root > right_leg > right_shin, left_leg > left_shin
  root > waist > body > head, right_arm > right_forearm, left_arm > left_forearm
  (the film actor's cloak, if the body has one: body > cape_0 > cape_1 > cape_2 > cape_3)

Loops for the "move" controller (idle, guard, walk, run, sprint, shamble, flee, claw_up), one-off moves for the "action"
controller (swings, a block, a stagger, deaths, casts), and held poses a scene strings together (kneel, overhead, chop,
thrust, the three finishers' two sides, Varro's disarm). A move keys only the bones it needs, so the legs keep walking
under a swing.

Rotations as Blockbench shows them for a free-format project (degrees): +x swings a hanging limb forward and up and
tips an upright part back (so the body leans forward with -x, the head looks down with -x, a knee bends with the
shin's -x, an elbow with the forearm's +x); +y turns to the body's right; +z lifts the right arm out to the side (-z
the left). Root positions are in model units for a 12-unit leg and scaled to the body's own (`leg`).

Each move is a list of (seconds, pose); a pose names only what it sets. Smoothed (catmullrom); a pose held is given twice.
"""

Z = (0, 0, 0)
SHORT = {"ra": "right_arm", "rf": "right_forearm", "la": "left_arm", "lf": "left_forearm", "rl": "right_leg", "rs": "right_shin",
		 "ll": "left_leg", "ls": "left_shin", "b": "body", "w": "waist", "h": "head", "c0": "cape_0", "c1": "cape_1", "c2": "cape_2",
		 "c3": "cape_3"}
SHORT.update({"k%d%d" % (j, i): "ribbon_%d_%d" % (j, i) for j in range(3) for i in range(4)})
# the cloak in ribbons: each follows what the whole cloak does in a move, the outer two swung a little more and splayed
# apart, more toward their ends (degrees a link), and further by a pose's "fan"
RIBBON_SWING, RIBBON_SPLAY = 0.18, (3.0, 5.0, 7.0, 9.0)


def anim(name, length, loop, tracks):
	return {"name": name, "length": length, "loop": loop, "tracks": tracks}


def v(x):
	return (x, 0, 0) if isinstance(x, (int, float)) else tuple(x)


def move(name, loop, frames, leg=12.0, cape=True):
	"""frames: [(time, {short: x | (x, y, z), "pos": (x, y, z), "rr": root rotation})]."""
	tracks = {}
	for t, pose in frames:
		for k, val in pose.items():
			if k == "pos":
				val = tuple(c * leg / 12.0 for c in val)
				tracks.setdefault("root", {}).setdefault("position", []).append((t, val))
			elif k == "rr":
				tracks.setdefault("root", {}).setdefault("rotation", []).append((t, v(val)))
			elif k == "fan" or (k[0] in "ck" and not cape):
				continue
			else:
				tracks.setdefault(SHORT[k], {}).setdefault("rotation", []).append((t, v(val)))
	if cape:
		for t, pose in frames:
			for i in range(4):
				if "c%d" % i not in pose or "k0%d" % i in pose:
					continue
				x, y, z = v(pose["c%d" % i])
				for j in range(3):
					side = j - 1
					splay = side * (RIBBON_SPLAY[i] * (1.0 + abs(x) / 60.0) + (pose.get("fan", 0.0) if i == 0 else 0.0))
					tracks.setdefault("ribbon_%d_%d" % (j, i), {}).setdefault("rotation", []).append(
						(t, (x * (1.0 + RIBBON_SWING * side), y, z + splay)))
	return anim(name, frames[-1][0], loop, tracks)


def flip(pose):
	"""The same pose on the other hand."""
	out = {}
	swap = {"ra": "la", "la": "ra", "rf": "lf", "lf": "rf", "rl": "ll", "ll": "rl", "rs": "ls", "ls": "rs"}
	for k, val in pose.items():
		x, y, z = v(val)
		out[swap.get(k, k)] = (x, -y, -z) if k not in ("pos",) else (-x, y, z)
	return out


def cycle(length, half_a, half_b):
	"""A loop from a half-stride's poses: at 0, a quarter, a half (mirrored) and three quarters, back to the start."""
	q = length / 4.0
	return [(0, half_a), (q, half_b), (2 * q, flip(half_a)), (3 * q, flip(half_b)), (length, half_a)]


# ---- stances ----
REST = {"ra": 0, "rf": 0, "la": 0, "lf": 0, "b": 0, "w": 0, "h": 0}
# weapon hand up and ready, the off hand (or shield) across the body, the left foot leading, the knees soft
GUARD = {"ra": (38, 0, 10), "rf": 62, "la": (46, 0, -14), "lf": 74, "b": (-7, 14, 0), "w": (0, 6, 0), "h": (4, -16, 0),
		 "ll": (22, 0, 0), "ls": -26, "rl": (-16, 0, 0), "rs": -14, "pos": (0, -0.7, 0)}
# sat back on its heels: the thighs forward, the shins flat on the ground under it
KNEEL = {"rl": 50, "rs": -140, "ll": 50, "ls": -140, "pos": (0, -6.0, 0)}


def moves(leg=12.0, cape=True):
	def m(name, loop, frames):
		return move(name, loop, frames, leg, cape)

	out = []
	# ---------------------------------------------------------------- loops
	out.append(m("idle", "loop", [
		(0, {"b": -1, "h": 1, "ra": (2, 0, 3), "rf": 8, "la": (2, 0, -3), "lf": 8, "c0": -3, "c1": -2, "c2": -2, "c3": -1}),
		(1.6, {"b": -3, "h": 3, "ra": (4, 0, 5), "rf": 12, "la": (4, 0, -5), "lf": 12, "c0": -5, "c1": -4, "c2": -1, "c3": 2}),
		(3.2, {"b": -1, "h": 1, "ra": (2, 0, 3), "rf": 8, "la": (2, 0, -3), "lf": 8, "c0": -3, "c1": -2, "c2": -2, "c3": -1})]))
	g2 = dict(GUARD, b=(-10, 10, 0), ra=(42, 0, 12), rf=68, la=(50, 0, -12), lf=80, pos=(0, -1.1, 0), ls=-32, rs=-20)
	out.append(m("guard", "loop", [(0, GUARD), (0.55, g2), (1.1, GUARD)]))
	# a walk: the heel out in front, the knee folding as the leg comes through, the arms against the legs
	walk_a = {"rl": 28, "rs": -4, "ll": -26, "ls": -14, "ra": (-22, 0, 3), "rf": 14, "la": (22, 0, -3), "lf": 26, "b": (-3, -5, 0),
			  "w": (0, 4, 0), "h": (2, 3, 0), "pos": (0, -0.3, 0), "c0": -8, "c1": -6, "c2": -5, "c3": -4}
	walk_b = {"rl": 4, "rs": -2, "ll": 8, "ls": -58, "ra": (0, 0, 3), "rf": 18, "la": (0, 0, -3), "lf": 18, "b": (-4, 0, 0),
			  "w": 0, "h": 2, "pos": (0, 0.35, 0), "c0": -12, "c1": -8, "c2": -4, "c3": -6}
	out.append(m("walk", "loop", cycle(1.0, walk_a, walk_b)))
	run_a = {"rl": 46, "rs": -18, "ll": -38, "ls": -42, "ra": (-48, 0, 6), "rf": 78, "la": (44, 0, -6), "lf": 86, "b": (-13, -9, 0),
			 "w": (0, 6, 0), "h": (8, 5, 0), "pos": (0, -0.5, 0), "c0": -32, "c1": -16, "c2": -10, "c3": -8}
	run_b = {"rl": 6, "rs": -6, "ll": 22, "ls": -96, "ra": (0, 0, 6), "rf": 84, "la": (0, 0, -6), "lf": 84, "b": (-15, 0, 0),
			 "w": 0, "h": 9, "pos": (0, 0.9, 0), "c0": -40, "c1": -20, "c2": -6, "c3": -12}
	out.append(m("run", "loop", cycle(0.6, run_a, run_b)))
	# a sprint, low, the blade hand trailing behind and the other cutting the air; a cloak streams out flat
	sp_a = {"rl": 58, "rs": -22, "ll": -46, "ls": -50, "ra": (-58, 0, 16), "rf": 22, "la": (52, 0, -8), "lf": 96, "b": (-30, -10, 0),
			"w": (-6, 8, 0), "h": (26, 6, 0), "pos": (0, -1.2, 0), "c0": -62, "c1": -18, "c2": -8, "c3": -4}
	sp_b = {"rl": 4, "rs": -8, "ll": 30, "ls": -112, "ra": (-50, 0, 18), "rf": 18, "la": (6, 0, -8), "lf": 92, "b": (-32, 0, 0),
			"w": (-6, 0, 0), "h": 27, "pos": (0, 0.6, 0), "c0": -68, "c1": -12, "c2": -14, "c3": 8}
	out.append(m("sprint", "loop", cycle(0.44, sp_a, sp_b)))
	# the dead: dragging one leg, the arms hung out in front, the head lolling
	sh_a = {"rl": 24, "rs": -6, "ll": -20, "ls": -6, "ra": (64, 0, 4), "rf": 18, "la": (48, 0, -6), "lf": 30, "b": (-14, -6, 5),
			"w": (0, 4, 0), "h": (-6, 8, -12), "pos": (0, -0.5, 0)}
	sh_b = {"rl": 6, "rs": -4, "ll": 2, "ls": -30, "ra": (70, 0, 6), "rf": 24, "la": (54, 0, -4), "lf": 22, "b": (-18, 0, 2),
			"w": 0, "h": (-10, 0, -8), "pos": (0, 0.2, 0)}
	sh_c = {"rl": -18, "rs": -10, "ll": 20, "ls": -4, "ra": (60, 0, 4), "rf": 20, "la": (58, 0, -8), "lf": 26, "b": (-12, 6, -4),
			"w": (0, -4, 0), "h": (-4, -6, 10), "pos": (0, -0.5, 0)}
	sh_d = {"rl": -2, "rs": -44, "ll": 6, "ls": -2, "ra": (66, 0, 6), "rf": 16, "la": (50, 0, -4), "lf": 34, "b": (-16, 0, -2),
			"w": 0, "h": (-8, 0, 6), "pos": (0, 0.2, 0)}
	out.append(m("shamble", "loop", [(0, sh_a), (0.35, sh_b), (0.7, sh_c), (1.05, sh_d), (1.4, sh_a)]))
	# running for its life: the arms thrown about, looking back
	fl_a = dict(run_a, ra=(-30, 0, 40), rf=60, la=(70, 0, -30), lf=50, h=(4, 40, 0), b=(-18, -12, 0))
	fl_b = dict(run_b, ra=(20, 0, 50), rf=70, la=(30, 0, -44), lf=64, h=(6, 30, 0), b=(-20, 0, 0))
	out.append(m("flee", "loop", [(0, fl_a), (0.14, fl_b), (0.28, flip(dict(fl_a, h=(4, -40, 0)))), (0.42, flip(dict(fl_b, h=(6, -30, 0)))),
								  (0.56, fl_a)]))
	# the dead coming up out of the ground: one arm over, then the other, hauling
	cl_a = {"ra": (172, 0, 14), "rf": 20, "la": (118, 0, -24), "lf": 82, "b": (-12, -14, 0), "h": (22, -8, 0), "rl": 6, "rs": -14, "ll": 18,
			"ls": -40}
	cl_b = {"ra": (112, 0, 22), "rf": 88, "la": (170, 0, -12), "lf": 22, "b": (-12, 14, 0), "h": (22, 8, 0), "rl": 20, "rs": -44, "ll": 4,
			"ls": -12}
	out.append(m("claw_up", "loop", [(0, cl_a), (0.45, cl_b), (0.9, cl_a)]))

	# ---------------------------------------------------------------- blows (the legs are left to the stance)
	UP = {"ra": GUARD["ra"], "rf": GUARD["rf"], "la": GUARD["la"], "lf": GUARD["lf"], "b": GUARD["b"], "w": GUARD["w"], "h": GUARD["h"]}
	# a cut across, right to left: drawn back over the shoulder, through, and round
	out.append(m("swing_across", "once", [
		(0, UP), (0.16, {"ra": (118, -20, 52), "rf": 96, "la": (30, 0, -22), "lf": 60, "b": (-2, 34, 0), "w": (0, 12, 0), "h": (2, -28, 0)}),
		(0.3, {"ra": (84, 0, 4), "rf": 14, "la": (16, 0, -30), "lf": 50, "b": (-12, -20, 0), "w": (0, -8, 0), "h": (4, 16, 0)}),
		(0.4, {"ra": (58, 0, -44), "rf": 22, "la": (10, 0, -34), "lf": 44, "b": (-14, -38, 0), "w": (0, -14, 0), "h": (4, 30, 0)}),
		(0.75, UP)]))
	# a cut down from overhead
	out.append(m("swing_down", "once", [
		(0, UP), (0.2, {"ra": (168, 0, 12), "rf": 52, "la": (40, 0, -20), "lf": 70, "b": (10, 8, 0), "w": (4, 4, 0), "h": (6, -8, 0)}),
		(0.32, {"ra": (58, 0, -6), "rf": 10, "la": (20, 0, -26), "lf": 60, "b": (-22, -8, 0), "w": (-8, -4, 0), "h": (14, 6, 0)}),
		(0.42, {"ra": (34, 0, -8), "rf": 6, "la": (16, 0, -26), "lf": 56, "b": (-26, -10, 0), "w": (-10, -4, 0), "h": (16, 8, 0)}),
		(0.8, UP)]))
	# a backhand, left to right
	out.append(m("swing_back", "once", [
		(0, UP), (0.16, {"ra": (74, 0, -58), "rf": 84, "la": (30, 0, -30), "lf": 50, "b": (-8, -32, 0), "w": (0, -10, 0), "h": (2, 26, 0)}),
		(0.3, {"ra": (82, 0, 30), "rf": 10, "la": (34, 0, -18), "lf": 64, "b": (-8, 22, 0), "w": (0, 8, 0), "h": (2, -14, 0)}),
		(0.4, {"ra": (70, 0, 62), "rf": 16, "la": (36, 0, -16), "lf": 66, "b": (-6, 36, 0), "w": (0, 12, 0), "h": (2, -24, 0)}),
		(0.75, UP)]))
	# a shield (or a forearm) thrown up into a blow
	out.append(m("block", "once", [
		(0, UP), (0.12, {"la": (96, 0, 26), "lf": 96, "ra": (20, 0, 16), "rf": 70, "b": (4, -20, 0), "w": (0, -6, 0), "h": (-8, 14, 0)}),
		(0.4, {"la": (92, 0, 24), "lf": 100, "ra": (22, 0, 16), "rf": 72, "b": (8, -18, 0), "w": (2, -6, 0), "h": (-6, 12, 0)}),
		(0.65, UP)]))
	# struck: the head and shoulder thrown back, a step to catch itself
	out.append(m("stagger", "once", [
		(0, {}), (0.1, {"b": (22, 16, 6), "w": (8, 6, 0), "h": (26, 10, 0), "ra": (-30, 0, 56), "rf": 20, "la": (-12, 0, -64), "lf": 30,
						"rl": -22, "rs": -8, "ll": 26, "ls": -30, "pos": (0, -0.6, 1.6)}),
		(0.3, {"b": (10, 8, 2), "w": (2, 2, 0), "h": (8, 4, 0), "ra": (10, 0, 30), "rf": 40, "la": (14, 0, -34), "lf": 46, "rl": -30,
			   "rs": -20, "ll": 12, "ls": -18, "pos": (0, -1.0, 2.4)}),
		(0.6, dict(GUARD, pos=(0, -0.7, 0)))]))

	# ---------------------------------------------------------------- held poses a scene strings together
	# weapon drawn up overhead in both hands, the back arched
	out.append(m("overhead", "hold", [
		(0, UP), (0.22, {"ra": (176, 0, -10), "rf": 38, "la": (176, 0, 10), "lf": 38, "b": (12, 0, 0), "w": (6, 0, 0), "h": (10, 0, 0),
						 "ll": 24, "ls": -20, "rl": -18, "rs": -8, "pos": (0, -0.4, 0)}),
		(0.4, {"ra": (182, 0, -10), "rf": 44, "la": (182, 0, 10), "lf": 44, "b": (15, 0, 0), "w": (7, 0, 0), "h": (12, 0, 0),
			   "ll": 24, "ls": -20, "rl": -18, "rs": -8, "pos": (0, -0.3, 0)})]))
	# and brought down through whatever is in front, the knees giving with it
	out.append(m("chop", "hold", [
		(0, {"ra": (182, 0, -10), "rf": 44, "la": (182, 0, 10), "lf": 44, "b": (15, 0, 0), "w": (7, 0, 0), "h": (12, 0, 0), "pos": (0, -0.3, 0)}),
		(0.1, {"ra": (70, 0, -12), "rf": 14, "la": (70, 0, 12), "lf": 14, "b": (-24, 0, 0), "w": (-10, 0, 0), "h": (18, 0, 0),
			   "ll": 30, "ls": -44, "rl": -20, "rs": -22, "pos": (0, -1.6, 0)}),
		(0.18, {"ra": (40, 0, -12), "rf": 8, "la": (40, 0, 12), "lf": 8, "b": (-32, 0, 0), "w": (-14, 0, 0), "h": (22, 0, 0),
				"ll": 32, "ls": -50, "rl": -22, "rs": -26, "pos": (0, -2.0, 0)}),
		(0.5, {"ra": (44, 0, -12), "rf": 12, "la": (44, 0, 12), "lf": 12, "b": (-28, 0, 0), "w": (-12, 0, 0), "h": (20, 0, 0),
			   "ll": 30, "ls": -46, "rl": -22, "rs": -24, "pos": (0, -1.8, 0)})]))
	# run through: the arm drawn back, a long step, the point driven home and held there
	out.append(m("thrust", "hold", [
		(0, UP), (0.12, {"ra": (20, 0, 14), "rf": 104, "la": (60, 0, -20), "lf": 30, "b": (-4, 30, 0), "w": (0, 10, 0), "h": (2, -26, 0)}),
		(0.24, {"ra": (88, 0, 2), "rf": 4, "la": (-30, 0, -30), "lf": 20, "b": (-18, -24, 0), "w": (-6, -8, 0), "h": (10, 20, 0),
				"ll": 48, "ls": -52, "rl": -34, "rs": -6, "pos": (0, -2.2, -1.5)}),
		(0.6, {"ra": (86, 0, 2), "rf": 6, "la": (-28, 0, -30), "lf": 22, "b": (-16, -22, 0), "w": (-6, -8, 0), "h": (10, 18, 0),
			   "ll": 46, "ls": -50, "rl": -34, "rs": -6, "pos": (0, -2.1, -1.5)})]))
	# on its knees, beaten there: the head down, the hands loose
	out.append(m("kneel", "hold", [
		(0, {}), (0.14, {"rl": 40, "rs": -70, "ll": 30, "ls": -60, "b": (-16, 0, 4), "w": (-6, 0, 0), "h": (-6, 0, 0), "ra": (20, 0, 20),
						 "rf": 30, "la": (24, 0, -24), "lf": 30, "pos": (0, -2.4, 0)}),
		(0.3, dict(KNEEL, b=(-20, 0, 3), w=(-8, 0, 0), h=(-18, 0, 0), ra=(10, 0, 8), rf=22, la=(12, 0, -10), lf=24)),
		(0.6, dict(KNEEL, b=(-16, 0, 2), w=(-6, 0, 0), h=(-24, 0, 0), ra=(8, 0, 6), rf=18, la=(10, 0, -8), lf=20))]))
	# on its knees, the head wrenched back to look up at who holds it
	out.append(m("kneel_up", "hold", [
		(0, dict(KNEEL, b=(-16, 0, 2), w=(-6, 0, 0), h=(-24, 0, 0), ra=(8, 0, 6), rf=18, la=(10, 0, -8), lf=20)),
		(0.3, dict(KNEEL, b=(8, 0, 0), w=(4, 0, 0), h=(34, 0, 0), ra=(-14, 0, 16), rf=10, la=(-14, 0, -16), lf=10)),
		(0.6, dict(KNEEL, b=(10, 0, 0), w=(4, 0, 0), h=(36, 0, 0), ra=(-12, 0, 14), rf=12, la=(-12, 0, -14), lf=12))]))
	# and the head bowed to him
	out.append(m("kneel_bow", "hold", [
		(0, dict(KNEEL, b=(10, 0, 0), w=(4, 0, 0), h=(36, 0, 0), ra=(-12, 0, 14), rf=12, la=(-12, 0, -14), lf=12)),
		(0.5, dict(KNEEL, b=(-26, 0, 0), w=(-10, 0, 0), h=(-30, 0, 0), ra=(30, 0, 4), rf=40, la=(30, 0, -4), lf=40)),
		(0.8, dict(KNEEL, b=(-24, 0, 0), w=(-10, 0, 0), h=(-28, 0, 0), ra=(30, 0, 4), rf=42, la=(30, 0, -4), lf=42))]))
	# up off its knees: one foot planted, a hand on the thigh, pushed up
	out.append(m("rise", "once", [
		(0, dict(KNEEL, b=(-24, 0, 0), w=(-10, 0, 0), h=(-28, 0, 0), ra=(30, 0, 4), rf=42, la=(30, 0, -4), lf=42)),
		(0.3, {"rl": 84, "rs": -92, "ll": 10, "ls": -100, "b": (-20, 0, 0), "w": (-8, 0, 0), "h": (-6, 0, 0), "ra": (62, 0, 0), "rf": 30,
			   "la": (20, 0, -8), "lf": 30, "pos": (0, -3.9, 0)}),
		(0.55, {"rl": 40, "rs": -50, "ll": -14, "ls": -30, "b": (-14, 0, 0), "w": (-4, 0, 0), "h": (4, 0, 0), "ra": (30, 0, 4), "rf": 30,
				"la": (16, 0, -6), "lf": 26, "pos": (0, -1.5, 0)}),
		(0.8, {"rl": 0, "rs": 0, "ll": 0, "ls": 0, "b": 0, "w": 0, "h": 0, "ra": 0, "rf": 0, "la": 0, "lf": 0, "pos": Z})]))
	# the weapon arm taken off: reeling, the stump clutched
	out.append(m("maimed", "hold", [
		(0, {}), (0.12, {"b": (16, 26, 10), "w": (6, 8, 0), "h": (24, 20, 0), "ra": (-30, 0, 70), "la": (10, 0, -50), "lf": 40, "rl": -24,
						 "rs": -10, "ll": 24, "ls": -30, "pos": (0, -0.8, 1.4)}),
		(0.4, {"b": (-22, 30, 12), "w": (-8, 10, 0), "h": (-14, 16, 6), "ra": (-10, 0, 30), "la": (64, 0, 54), "lf": 104, "rl": -30,
			   "rs": -26, "ll": 20, "ls": -40, "pos": (0, -1.8, 2.0)}),
		(0.75, {"b": (-26, 28, 12), "w": (-10, 10, 0), "h": (-18, 14, 6), "ra": (-8, 0, 28), "la": (66, 0, 56), "lf": 108, "rl": -30,
				"rs": -30, "ll": 22, "ls": -46, "pos": (0, -2.1, 2.0)})]))
	# run through: folded over the blade, the hands coming to it
	out.append(m("impaled", "hold", [
		(0, {}), (0.1, {"b": (14, 0, 0), "w": (6, 0, 0), "h": (20, 0, 0), "ra": (-20, 0, 40), "rf": 20, "la": (-20, 0, -40), "lf": 20,
						"pos": (0, 0.4, 0.8)}),
		(0.3, {"b": (-30, 0, 0), "w": (-14, 0, 0), "h": (-20, 0, 0), "ra": (50, 0, -22), "rf": 82, "la": (50, 0, 22), "lf": 82, "rl": 14,
			   "rs": -34, "ll": 22, "ls": -44, "pos": (0, -1.6, 0.6)}),
		(0.6, {"b": (-34, 0, 2), "w": (-16, 0, 0), "h": (-26, 0, 0), "ra": (48, 0, -22), "rf": 88, "la": (48, 0, 22), "lf": 88, "rl": 18,
			   "rs": -42, "ll": 26, "ls": -52, "pos": (0, -2.1, 0.6)})]))

	# ---------------------------------------------------------------- deaths (held where they land)
	# thrown onto its back
	out.append(m("death_back", "hold", [
		(0, {"rr": 0, "pos": Z}),
		(0.18, {"rr": 26, "pos": (0, -0.5, 0), "b": (14, 0, 0), "h": (22, 0, 0), "ra": (-40, 0, 50), "rf": 20, "la": (-30, 0, -60), "lf": 30,
				"rl": 34, "rs": -40, "ll": 14, "ls": -20}),
		(0.42, {"rr": 86, "pos": (0, 1.2, 0), "b": (-6, 4, 0), "h": (-8, 10, 0), "ra": (-20, 0, 86), "rf": 30, "la": (-50, 0, -70), "lf": 40,
				"rl": 26, "rs": -30, "ll": -4, "ls": -8}),
		(0.52, {"rr": 92, "pos": (0, 2.3, 0), "b": (2, 4, 0), "h": (4, 14, 0), "ra": (-10, 0, 80), "rf": 34, "la": (-60, 0, -64), "lf": 44,
				"rl": 10, "rs": -20, "ll": -6, "ls": -2}),
		(0.9, {"rr": 90, "pos": (0, 2.0, 0), "b": (0, 4, 0), "h": (2, 16, 0), "ra": (-8, 0, 80), "rf": 36, "la": (-62, 0, -64), "lf": 46,
			   "rl": 6, "rs": -22, "ll": -6, "ls": 0})]))
	# to its knees, and onto its face
	out.append(m("death_front", "hold", [
		(0, {"rr": 0, "pos": Z}),
		(0.2, dict(KNEEL, rr=-6, b=(-10, 0, 4), h=(-20, 0, 0), ra=(10, 0, 20), rf=20, la=(10, 0, -20), lf=20)),
		(0.4, {"rr": -10, "rl": 14, "rs": -104, "ll": 12, "ls": -100, "pos": (0, -4.2, 0), "b": (-30, 0, 6), "w": (-12, 0, 0), "h": (-10, 0, 0),
			   "ra": (40, 0, 30), "rf": 30, "la": (50, 0, -30), "lf": 30}),
		(0.62, {"rr": -84, "rl": -6, "rs": -30, "ll": -4, "ls": -14, "pos": (0, 2.2, -3.0), "b": (-4, 0, 4), "w": 0, "h": (10, 30, 0),
				"ra": (120, 0, 40), "rf": 60, "la": (150, 0, -30), "lf": 40}),
		(1.0, {"rr": -90, "rl": -4, "rs": -16, "ll": 0, "ls": -4, "pos": (0, 2.0, -3.0), "b": (0, 0, 4), "w": 0, "h": (14, 34, 0),
			   "ra": (124, 0, 44), "rf": 64, "la": (156, 0, -30), "lf": 36})]))
	# whatever held it up let go: straight down in a heap, over onto its side
	out.append(m("death_drop", "hold", [
		(0, {"rr": 0, "pos": Z}),
		(0.16, dict(KNEEL, rr=(0, 0, 0), b=(-24, 0, 0), w=(-10, 0, 0), h=(-30, 0, 0), ra=(6, 0, 6), rf=10, la=(6, 0, -6), lf=10)),
		(0.34, {"rr": (0, 0, 30), "rl": 20, "rs": -110, "ll": 30, "ls": -112, "pos": (-2, -4.4, 0), "b": (-34, 0, 14), "w": (-14, 0, 8),
				"h": (-34, 0, 10), "ra": (10, 0, 4), "rf": 20, "la": (20, 0, -30), "lf": 30}),
		(0.56, {"rr": (0, 0, 88), "rl": 40, "rs": -84, "ll": 60, "ls": -100, "pos": (-7, -9.6, 0), "b": (-30, 0, 6), "w": (-12, 0, 4),
				"h": (-20, 0, 14), "ra": (40, 0, -10), "rf": 50, "la": (50, 0, -20), "lf": 60}),
		(0.9, {"rr": (0, 0, 90), "rl": 44, "rs": -88, "ll": 62, "ls": -104, "pos": (-7, -9.9, 0), "b": (-30, 0, 4), "w": (-12, 0, 4),
			   "h": (-18, 0, 16), "ra": (44, 0, -10), "rf": 54, "la": (54, 0, -22), "lf": 64})]))
	# its head gone: the body stays on its knees a breath, then pitches forward
	out.append(m("death_headless", "hold", [
		(0, dict(KNEEL, rr=0, b=(-16, 0, 2), w=(-6, 0, 0), ra=(8, 0, 6), rf=18, la=(10, 0, -8), lf=20)),
		(0.25, dict(KNEEL, rr=4, b=(-6, 0, 0), w=(-2, 0, 0), ra=(-10, 0, 14), rf=10, la=(-10, 0, -14), lf=10)),
		(0.6, {"rr": -80, "rl": -4, "rs": -40, "ll": -2, "ls": -30, "pos": (0, 2.2, -3.0), "b": (-4, 0, 0), "w": 0, "ra": (30, 0, 30),
			   "rf": 20, "la": (30, 0, -30), "lf": 20}),
		(0.9, {"rr": -90, "rl": -2, "rs": -14, "ll": 0, "ls": -8, "pos": (0, 2.0, -3.0), "b": 0, "w": 0, "ra": (20, 0, 36), "rf": 24,
			   "la": (24, 0, -36), "lf": 22})]))

	# ---------------------------------------------------------------- casting
	# both arms up to the sky and the head thrown back, held (a summons)
	out.append(m("summon", "hold", [
		(0, REST), (0.5, {"ra": (60, 0, 30), "rf": 80, "la": (60, 0, -30), "lf": 80, "b": (-10, 0, 0), "w": (-4, 0, 0), "h": (-10, 0, 0)}),
		(1.1, {"ra": (160, 0, 34), "rf": 24, "la": (160, 0, -34), "lf": 24, "b": (12, 0, 0), "w": (6, 0, 0), "h": (26, 0, 0),
			   "c0": -24, "c1": -14, "c2": -8, "c3": 6}),
		(1.6, {"ra": (166, 0, 40), "rf": 16, "la": (166, 0, -40), "lf": 16, "b": (15, 0, 0), "w": (7, 0, 0), "h": (30, 0, 0),
			   "c0": -34, "c1": -8, "c2": -14, "c3": 10})]))
	# a bolt thrown from one hand: drawn back by the ear, flung, the fingers left pointing
	cast = [(0, REST), (0.12, {"ra": (40, 0, 40), "rf": 120, "b": (0, 26, 0), "w": (0, 8, 0), "h": (0, -22, 0), "la": (30, 0, -10), "lf": 30}),
			(0.22, {"ra": (96, 0, 0), "rf": 6, "b": (-10, -22, 0), "w": (-2, -6, 0), "h": (4, 18, 0), "la": (-20, 0, -24), "lf": 20}),
			(0.4, {"ra": (90, 0, 0), "rf": 8, "b": (-8, -18, 0), "w": (-2, -6, 0), "h": (4, 14, 0), "la": (-16, 0, -22), "lf": 22}),
			(0.7, REST)]
	out.append(m("cast_right", "once", cast))
	out.append(m("cast_left", "once", [(t, flip(p)) for t, p in cast]))
	# a working gathered in both hands at the chest, then pushed out and held
	out.append(m("cast_both", "hold", [
		(0, REST), (0.3, {"ra": (50, 0, -20), "rf": 110, "la": (50, 0, 20), "lf": 110, "b": (-8, 0, 0), "h": (-10, 0, 0), "w": (-2, 0, 0)}),
		(0.55, {"ra": (56, 0, -24), "rf": 116, "la": (56, 0, 24), "lf": 116, "b": (-12, 0, 0), "h": (-14, 0, 0), "w": (-4, 0, 0)}),
		(0.68, {"ra": (94, 0, -6), "rf": 6, "la": (94, 0, 6), "lf": 6, "b": (-14, 0, 0), "h": (8, 0, 0), "w": (-4, 0, 0), "ll": 30, "ls": -34,
				"rl": -20, "rs": -10, "pos": (0, -1.0, -0.8)}),
		(1.2, {"ra": (92, 0, -8), "rf": 8, "la": (92, 0, 8), "lf": 8, "b": (-12, 0, 0), "h": (8, 0, 0), "w": (-4, 0, 0), "ll": 30, "ls": -34,
			   "rl": -20, "rs": -10, "pos": (0, -1.0, -0.8)})]))
	# a ward raised: the palms pushed up and out to either side, held against a weight
	out.append(m("cast_ward", "hold", [
		(0, REST), (0.3, {"ra": (30, 0, 10), "rf": 100, "la": (30, 0, -10), "lf": 100, "b": (-14, 0, 0), "h": (-12, 0, 0), "rl": 10, "rs": -24,
						  "ll": 10, "ls": -24, "pos": (0, -0.8, 0)}),
		(0.6, {"ra": (120, 0, 46), "rf": 30, "la": (120, 0, -46), "lf": 30, "b": (6, 0, 0), "h": (10, 0, 0), "rl": -10, "rs": -6, "ll": 16,
			   "ls": -14, "pos": (0, -0.3, 0)}),
		(1.2, {"ra": (124, 0, 50), "rf": 24, "la": (124, 0, -50), "lf": 24, "b": (8, 0, 0), "h": (12, 0, 0), "rl": -10, "rs": -6, "ll": 16,
			   "ls": -14, "pos": (0, -0.3, 0)})]))
	# a staff (or a blade) lifted straight up over the head in one hand, the other out for balance, held
	out.append(m("cast_high", "hold", [
		(0, REST), (0.35, {"ra": (70, 0, 10), "rf": 90, "la": (10, 0, -20), "lf": 30, "b": (-8, 10, 0), "h": (-6, -8, 0)}),
		(0.7, {"ra": (176, 0, 6), "rf": 4, "la": (20, 0, -60), "lf": 16, "b": (10, 0, 0), "w": (4, 0, 0), "h": (24, 0, 0)}),
		(1.2, {"ra": (180, 0, 6), "rf": 2, "la": (24, 0, -64), "lf": 12, "b": (12, 0, 0), "w": (5, 0, 0), "h": (27, 0, 0)})]))
	# the free hand raised, the working called up round it, then let go down the blade in one long cut
	out.append(m("cast_blade", "hold", [
		(0, REST), (0.25, {"la": (130, 0, -20), "lf": 40, "ra": (30, 0, 30), "rf": 60, "b": (4, -12, 0), "h": (10, 10, 0)}),
		(0.6, {"la": (140, 0, -24), "lf": 30, "ra": (120, -20, 50), "rf": 90, "b": (2, 30, 0), "w": (0, 10, 0), "h": (6, -26, 0)}),
		(0.72, {"la": (10, 0, -40), "lf": 30, "ra": (80, 0, -30), "rf": 8, "b": (-16, -34, 0), "w": (-4, -12, 0), "h": (8, 28, 0), "ll": 34,
				"ls": -40, "rl": -24, "rs": -10, "pos": (0, -1.4, -1.0)}),
		(1.2, {"la": (8, 0, -40), "lf": 32, "ra": (76, 0, -34), "rf": 10, "b": (-14, -34, 0), "w": (-4, -12, 0), "h": (8, 28, 0), "ll": 34,
			   "ls": -40, "rl": -24, "rs": -10, "pos": (0, -1.4, -1.0)})]))
	# an arm out, pointing: go
	out.append(m("point", "hold", [
		(0, REST), (0.2, {"ra": (96, 0, 4), "rf": 4, "b": (-4, -14, 0), "h": (2, 10, 0), "la": (-8, 0, -10), "lf": 14}),
		(0.6, {"ra": (92, 0, 4), "rf": 6, "b": (-4, -12, 0), "h": (2, 8, 0), "la": (-6, 0, -10), "lf": 16})]))

	# ---------------------------------------------------------------- Varro's disarm (his alone), and the orc's side of it
	# 0.0 slips inside the chop and catches the wrist in his left hand; 0.2 wrenches it down and round, the axe gone;
	# 0.5 kicks the knee out from behind; 0.85 steps in, his right hand on its brow, and holds it there
	out.append(m("disarm", "hold", [
		(0, UP),
		(0.12, {"la": (132, 0, 24), "lf": 62, "ra": (16, 0, 20), "rf": 70, "b": (-10, -30, -8), "w": (-2, -10, 0), "h": (-6, 24, 0),
				"ll": 40, "ls": -46, "rl": -30, "rs": -12, "pos": (-1.5, -1.6, -1.0)}),
		(0.22, {"la": (128, 0, 20), "lf": 70, "ra": (18, 0, 20), "rf": 72, "b": (-12, -28, -8), "w": (-2, -10, 0), "h": (-6, 22, 0),
				"ll": 40, "ls": -46, "rl": -30, "rs": -12, "pos": (-1.5, -1.6, -1.0)}),
		(0.36, {"la": (44, 0, 44), "lf": 30, "ra": (60, 0, -10), "rf": 60, "b": (-22, 36, 6), "w": (-6, 14, 0), "h": (10, -24, 0),
				"ll": 26, "ls": -40, "rl": -20, "rs": -20, "pos": (-1.0, -2.0, -0.6)}),
		(0.5, {"la": (20, 0, 50), "lf": 20, "ra": (40, 0, 10), "rf": 80, "b": (-8, 30, 4), "w": (-2, 10, 0), "h": (6, -20, 0),
			   "ll": 10, "ls": -20, "rl": 30, "rs": -96, "pos": (-0.6, -0.8, 0)}),
		(0.6, {"la": (10, 0, 40), "lf": 30, "ra": (30, 0, 14), "rf": 70, "b": (10, 20, 0), "w": (4, 6, 0), "h": (-6, -14, 0),
			   "ll": 4, "ls": -14, "rl": 78, "rs": -18, "pos": (-0.4, -0.4, 0.4)}),
		(0.75, {"la": (14, 0, -14), "lf": 40, "ra": (40, 0, 10), "rf": 70, "b": (-6, 8, 0), "w": 0, "h": (-10, -6, 0),
				"ll": -10, "ls": -10, "rl": 30, "rs": -30, "pos": (0, -0.6, -0.4)}),
		(0.95, {"la": (10, 0, -10), "lf": 30, "ra": (74, 0, -4), "rf": 22, "b": (-10, 0, 0), "w": (-2, 0, 0), "h": (-22, 0, 0),
				"ll": -14, "ls": -8, "rl": 24, "rs": -22, "pos": (0, -0.5, -0.4)}),
		(1.6, {"la": (8, 0, -10), "lf": 28, "ra": (72, 0, -4), "rf": 26, "b": (-12, 0, 0), "w": (-2, 0, 0), "h": (-24, 0, 0),
			   "ll": -14, "ls": -8, "rl": 24, "rs": -22, "pos": (0, -0.5, -0.4)})]))
	# its axe hand caught and wrenched round behind it, the knee kicked out, down
	out.append(m("disarmed", "hold", [
		(0, {"ra": (40, 0, -12), "rf": 8, "la": (40, 0, 12), "lf": 8, "b": (-32, 0, 0), "w": (-14, 0, 0), "h": (22, 0, 0), "pos": (0, -2.0, 0)}),
		(0.14, {"ra": (60, 0, 30), "rf": 30, "la": (30, 0, -40), "lf": 30, "b": (-20, -20, 6), "w": (-8, -6, 0), "h": (10, 20, 0),
				"pos": (0, -1.4, 0)}),
		(0.3, {"ra": (-50, 0, 60), "rf": 70, "la": (40, 0, -70), "lf": 20, "b": (-30, 40, 14), "w": (-10, 14, 0), "h": (-10, 30, 0),
			   "rl": -10, "rs": -20, "ll": 30, "ls": -40, "pos": (0, -1.8, 0)}),
		(0.5, {"ra": (-40, 0, 40), "rf": 50, "la": (50, 0, -60), "lf": 30, "b": (-24, 20, 8), "w": (-8, 8, 0), "h": (-6, 10, 0),
			   "rl": 30, "rs": -80, "ll": 40, "ls": -60, "pos": (0, -3.0, 0)}),
		(0.65, dict(KNEEL, b=(-24, 0, 4), w=(-8, 0, 0), h=(-24, 0, 0), ra=(20, 0, 14), rf=40, la=(30, 0, -20), lf=50)),
		(1.0, dict(KNEEL, b=(-20, 0, 3), w=(-8, 0, 0), h=(-26, 0, 0), ra=(14, 0, 10), rf=30, la=(22, 0, -14), lf=40))]))

	# ---------------------------------------------------------------- the fifth
	# the lunge: off the back foot, the blade arm thrown out its whole length, the cloak snapped out behind, held
	out.append(m("lunge", "hold", [
		(0, {"ra": (-58, 0, 16), "rf": 22, "la": (52, 0, -8), "lf": 96, "b": (-30, 0, 0), "w": (-6, 0, 0), "h": (26, 0, 0)}),
		(0.1, {"ra": (10, 0, 10), "rf": 110, "la": (60, 0, -16), "lf": 40, "b": (-26, 24, 0), "w": (-6, 8, 0), "h": (22, -20, 0),
			   "ll": 20, "ls": -70, "rl": -10, "rs": -50, "pos": (0, -3.0, 0), "c0": -50, "c1": -20, "c2": -10, "c3": 0}),
		(0.2, {"ra": (104, 0, 0), "rf": 2, "la": (-50, 0, -26), "lf": 10, "b": (-40, -26, 0), "w": (-10, -8, 0), "h": (36, 22, 0),
			   "ll": 66, "ls": -70, "rl": -44, "rs": -4, "pos": (0, -3.4, -2.4), "c0": -84, "c1": -6, "c2": -4, "c3": -2}),
		(0.7, {"ra": (100, 0, 0), "rf": 4, "la": (-48, 0, -26), "lf": 12, "b": (-38, -24, 0), "w": (-10, -8, 0), "h": (34, 20, 0),
			   "ll": 64, "ls": -68, "rl": -44, "rs": -4, "pos": (0, -3.3, -2.4), "c0": -70, "c1": -16, "c2": 6, "c3": 8})]))
	# off a tree: a foot planted on the trunk, the body turned over sideways in the air, down running
	out.append(m("tree_flip", "once", [
		(0, {"rr": Z, "pos": Z, "b": (-30, 0, 0), "h": (26, 0, 0)}),
		(0.08, {"rr": (0, 0, 20), "pos": (0, 3, 0), "rl": 60, "rs": -80, "ll": -20, "ls": -30, "ra": (60, 0, 60), "rf": 30, "la": (20, 0, -80),
				"lf": 20, "b": (-20, -20, 0), "h": (16, 10, 0), "c0": -40, "c1": -20, "c2": -10, "c3": 0}),
		(0.2, {"rr": (0, 0, 120), "pos": (-4, 14, 0), "rl": 80, "rs": -110, "ll": 70, "ls": -100, "ra": (80, 0, 80), "rf": 40,
			   "la": (80, 0, -80), "lf": 40, "b": (-30, 0, 0), "h": (10, 0, 0), "c0": -70, "c1": -30, "c2": -10, "c3": 10}),
		(0.32, {"rr": (0, 0, 250), "pos": (-2, 16, 0), "rl": 70, "rs": -100, "ll": 80, "ls": -110, "ra": (60, 0, 70), "rf": 50,
				"la": (60, 0, -70), "lf": 50, "b": (-30, 0, 0), "h": (10, 0, 0), "c0": -80, "c1": -20, "c2": -20, "c3": 0}),
		(0.44, {"rr": (0, 0, 340), "pos": (0, 5, 0), "rl": 40, "rs": -40, "ll": 10, "ls": -30, "ra": (-30, 0, 40), "rf": 30, "la": (30, 0, -40),
				"lf": 60, "b": (-26, 0, 0), "h": (22, 0, 0), "c0": -50, "c1": -20, "c2": 0, "c3": 10}),
		(0.54, {"rr": (0, 0, 360), "pos": (0, -2.0, 0), "rl": 50, "rs": -80, "ll": -10, "ls": -60, "ra": (-50, 0, 16), "rf": 22, "la": (50, 0, -8),
				"lf": 90, "b": (-34, 0, 0), "h": (28, 0, 0), "c0": -30, "c1": -30, "c2": -10, "c3": 0}),
		(0.64, {"rr": (0, 0, 360), "pos": Z, "rl": 0, "rs": 0, "ll": 0, "ls": 0, "ra": (-58, 0, 16), "rf": 22, "la": (52, 0, -8), "lf": 96,
				"b": (-30, 0, 0), "h": (26, 0, 0), "c0": -62, "c1": -18, "c2": -8, "c3": -4})]))
	# a bolt slipped: a roll forward under it, over the shoulder and up
	out.append(m("dodge_roll", "once", [
		(0, {"rr": Z, "pos": Z}),
		(0.1, {"rr": -50, "pos": (0, -5, -2), "rl": 70, "rs": -110, "ll": 60, "ls": -100, "b": (-50, 0, 0), "w": (-20, 0, 0), "h": (-40, 0, 0),
			   "ra": (80, 0, 10), "rf": 90, "la": (80, 0, -10), "lf": 90, "c0": -20, "c1": -10, "c2": 0, "c3": 0}),
		(0.24, {"rr": -180, "pos": (0, 9, -6), "rl": 100, "rs": -130, "ll": 100, "ls": -130, "b": (-60, 0, 0), "w": (-30, 0, 0), "h": (-50, 0, 0),
				"ra": (60, 0, 10), "rf": 110, "la": (60, 0, -10), "lf": 110, "c0": 10, "c1": 10, "c2": 0, "c3": 0}),
		(0.38, {"rr": -310, "pos": (0, -1, -4), "rl": 90, "rs": -120, "ll": 80, "ls": -120, "b": (-50, 0, 0), "w": (-20, 0, 0), "h": (-30, 0, 0),
				"ra": (40, 0, 10), "rf": 90, "la": (40, 0, -10), "lf": 90, "c0": -40, "c1": -10, "c2": 0, "c3": 0}),
		(0.5, {"rr": -360, "pos": (0, -2.5, 0), "rl": 60, "rs": -90, "ll": -10, "ls": -70, "b": (-34, 0, 0), "w": (-8, 0, 0), "h": (26, 0, 0),
			   "ra": (-40, 0, 16), "rf": 30, "la": (40, 0, -8), "lf": 80, "c0": -50, "c1": -20, "c2": -10, "c3": 0}),
		(0.6, {"rr": -360, "pos": Z, "rl": 0, "rs": 0, "ll": 0, "ls": 0, "b": (-30, 0, 0), "w": (-6, 0, 0), "h": (26, 0, 0),
			   "ra": (-58, 0, 16), "rf": 22, "la": (52, 0, -8), "lf": 96, "c0": -62, "c1": -18, "c2": -8, "c3": -4})]))
	# drawn back on his slingshot: low, the arms wide, the cloak pulled out ahead of him toward the trees that have it
	out.append(m("sling", "hold", [
		(0, {"b": (-30, 0, 0), "h": (26, 0, 0)}),
		(0.3, {"ra": (-10, 0, 70), "rf": 14, "la": (-10, 0, -70), "lf": 14, "b": (-22, 0, 0), "w": (-10, 0, 0), "h": (16, 0, 0),
			   "rl": 44, "rs": -84, "ll": -24, "ls": -50, "pos": (0, -3.2, 1.0), "c0": 30, "c1": 16, "c2": 10, "c3": 6}),
		(0.6, {"ra": (-16, 0, 78), "rf": 10, "la": (-16, 0, -78), "lf": 10, "b": (-18, 0, 0), "w": (-12, 0, 0), "h": (12, 0, 0),
			   "rl": 50, "rs": -96, "ll": -30, "ls": -56, "pos": (0, -4.0, 2.0), "c0": 40, "c1": 20, "c2": 12, "c3": 8})]))
	# let fly: laid out head first, turning over twice about the way he flies, the arms swept back and the cloak
	# streaming behind in a twist; then it opens out, he comes level and the blade arm goes out its whole length. Slow:
	# the scene carries him through the air while this plays (2.7 s)
	dive = {"ra": (-36, 0, 14), "rf": 6, "la": (-36, 0, -14), "lf": 6, "b": (6, 0, 0), "w": (4, 0, 0), "h": (62, 0, 0), "rl": -6, "rs": -4,
			"ll": -2, "ls": -10}
	out.append(m("glide", "hold", [
		(0, {"rr": Z, "pos": (0, -4.0, 2.0), "b": (-18, 0, 0), "h": (12, 0, 0)}),
		(0.4, dict(dive, rr=(-88, 0, 100), pos=(0, 5.0, 14.0), c0=(-6, 0, 14), c1=(4, 0, 20), c2=(6, 0, 24), c3=(8, 0, 26))),
		(0.9, dict(dive, rr=(-90, 0, 300), pos=(0, 5.0, 14.0), c0=(-10, 0, -14), c1=(8, 0, -22), c2=(-6, 0, 26), c3=(10, 0, -28))),
		(1.4, dict(dive, rr=(-90, 0, 520), pos=(0, 5.0, 14.0), c0=(-4, 0, 16), c1=(-8, 0, 24), c2=(8, 0, -26), c3=(-10, 0, 30))),
		(1.95, dict(dive, rr=(-88, 0, 720), pos=(0, 5.0, 14.0), c0=(-12, 0, 0), c1=(6, 0, -10), c2=(-6, 0, 12), c3=(4, 0, -8))),
		(2.3, {"rr": (-50, 0, 720), "pos": (0, 1.0, 6.0), "ra": (40, 0, 10), "rf": 90, "la": (-30, 0, -40), "lf": 20, "b": (-10, 20, 0),
			   "w": (-4, 6, 0), "h": (40, -14, 0), "rl": 30, "rs": -70, "ll": -10, "ls": -40, "c0": -70, "c1": -30, "c2": -16, "c3": -8, "fan": 34}),
		(2.56, {"rr": (-18, 0, 720), "pos": (0, -3.0, -1.0), "ra": (104, 0, 0), "rf": 2, "la": (-50, 0, -26), "lf": 10, "b": (-36, -26, 0),
				"w": (-10, -8, 0), "h": (36, 22, 0), "ll": 66, "ls": -70, "rl": -44, "rs": -4, "c0": -96, "c1": -10, "c2": -4, "c3": -2, "fan": 40}),
		(2.7, {"rr": (-14, 0, 720), "pos": (0, -3.3, -2.4), "ra": (100, 0, 0), "rf": 4, "la": (-48, 0, -26), "lf": 12, "b": (-38, -24, 0),
			   "w": (-10, -8, 0), "h": (34, 20, 0), "ll": 64, "ls": -68, "rl": -44, "rs": -4, "c0": -80, "c1": -16, "c2": 6, "c3": 8})]))
	# the cloak comes apart and sets again (the "cloak" controller; only the ribbons are keyed). Andy: "it forms over him
	# like the suit does to form the full cloak, and falls away the same way". Hung together as the whole cloak, the
	# ribbons wind round his body (one round each side at the chest, one lower), then fly loose; and the same back
	hung = {"k%d%d" % (j, i): Z for j in range(3) for i in range(4)}
	wound = {"k00": (0, 0, 82), "k01": (86, 0, 0), "k02": (88, 0, 0), "k03": (80, 0, 0),
			 "k20": (0, 0, -82), "k21": (86, 0, 0), "k22": (88, 0, 0), "k23": (80, 0, 0),
			 "k10": (0, 0, 48), "k11": (70, 0, 0), "k12": (76, 0, 0), "k13": (70, 0, 0)}
	loose = {"k00": (-46, 0, 12), "k01": (-20, 0, 8), "k02": (-10, 0, 10), "k03": (-6, 0, 12),
			 "k10": (-40, 0, 0), "k11": (-18, 0, 0), "k12": (-8, 0, 0), "k13": (-4, 0, 0),
			 "k20": (-46, 0, -12), "k21": (-20, 0, -8), "k22": (-10, 0, -10), "k23": (-6, 0, -12)}
	if cape:
		out.append(m("cloak_part", "once", [(0, hung), (0.3, wound), (0.4, wound), (0.6, loose)]))
		out.append(m("cloak_form", "hold", [(0, loose), (0.3, wound), (0.45, wound), (0.7, hung)]))
	# stood up out of it, the blade lowered, looking round for someone who isn't there (the root stays the two turns round
	# the glide left it, so nothing spins back)
	out.append(m("look_about", "hold", [
		(0, {"rr": (-14, 0, 720), "pos": (0, -3.3, -2.4), "ra": (100, 0, 0), "rf": 4, "b": (-38, -24, 0), "h": (34, 20, 0), "ll": 64, "ls": -68,
			 "rl": -44, "rs": -4}),
		(0.5, {"rr": (0, 0, 720), "pos": Z, "ll": 0, "ls": 0, "rl": 0, "rs": 0, "ra": (20, 0, 10), "rf": 20, "la": (10, 0, -10), "lf": 20, "b": (-4, 0, 0), "w": 0, "h": (0, 40, 0), "c0": -6, "c1": -4, "c2": -2,
			   "c3": 0}),
		(1.3, {"ra": (16, 0, 10), "rf": 18, "la": (10, 0, -10), "lf": 20, "b": (-2, -16, 0), "w": 0, "h": (4, -56, 0), "c0": -4, "c1": -6,
			   "c2": 0, "c3": 2}),
		(2.2, {"ra": (14, 0, 8), "rf": 16, "la": (8, 0, -8), "lf": 18, "b": (-2, 10, 0), "w": 0, "h": (-6, 30, 0), "c0": -6, "c1": -2,
			   "c2": -2, "c3": 0})]))
	return out


LOOPS = ("idle", "guard", "walk", "run", "sprint", "shamble", "flee", "claw_up")
