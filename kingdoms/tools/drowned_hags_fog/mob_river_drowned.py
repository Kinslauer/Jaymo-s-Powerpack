"""Row B2: river_drowned — original river-bank and swamp-water ambush monster.
Low amphibious riverbank crouch (~1.72 blocks tall at rest, ~2.1 blocks extended in swim/lunge),
sloped pike-toothed skull with flared river-fin ears and glowing amber eyes, ribbed waterlogged chest
draped in river-kelp, serrated dorsal fin running into a sculling eel-tail fluke, splayed webbed hind feet,
and long hooked grappling claws that lunge from the bank, swim fast, and drag the fallen under."""
from __future__ import annotations

from .common import (
	IndexedSheet,
	RigBuilder,
	Z,
	closed_loop,
	make_anim,
	mirror_rot,
	paint_amphibian_skull,
	paint_claw_tip,
	paint_fin_membrane,
	paint_jaw_maw,
	paint_mud_caked,
	paint_ribbed_torso,
	paint_tattered_cloth,
)


def build_river_drowned() -> tuple[RigBuilder, list[dict]]:
	name = "river_drowned"
	sheet = IndexedSheet(64, 64)
	uv = {
		"head": sheet.alloc_box("head", 6, 5, 7, "hide", detail_fn=paint_amphibian_skull),
		"snout": sheet.alloc_box("snout", 5, 3, 4, "hide", hi=5, mid=4, side=3, back=3, lo=2, dark=1),
		"jaw": sheet.alloc_box("jaw", 5, 2, 7, "hide", detail_fn=paint_jaw_maw()),
		"fin_ear": sheet.alloc_box("fin_ear", 1, 3, 5, "filth", detail_fn=paint_fin_membrane("bone", "filth")),
		"neck": sheet.alloc_box("neck", 4, 4, 4, "hide", hi=4, mid=3, side=3, back=2, lo=2, dark=1),
		"chest": sheet.alloc_box("chest", 8, 7, 6, "hide", detail_fn=paint_ribbed_torso()),
		"body": sheet.alloc_box("body", 6, 5, 5, "hide", hi=4, mid=3, side=3, back=2, lo=2, dark=1),
		"waist": sheet.alloc_box("waist", 6, 4, 4, "hide", detail_fn=paint_mud_caked("hide")),
		"dorsal": sheet.alloc_box("dorsal", 1, 3, 7, "filth", detail_fn=paint_fin_membrane("bone", "filth")),
		"kelp": sheet.alloc_box("kelp", 2, 6, 1, "filth", detail_fn=paint_tattered_cloth("filth")),
		"tail_base": sheet.alloc_box("tail_base", 3, 3, 6, "hide", detail_fn=paint_mud_caked("hide")),
		"tail_fluke": sheet.alloc_box("tail_fluke", 1, 5, 6, "filth", detail_fn=paint_fin_membrane("bone", "filth")),
		"arm": sheet.alloc_box("arm", 2, 7, 3, "hide", hi=5, mid=4, side=3, back=3, lo=2, dark=1),
		"forearm": sheet.alloc_box("forearm", 2, 8, 3, "hide", detail_fn=paint_mud_caked("hide")),
		"arm_fin": sheet.alloc_box("arm_fin", 1, 5, 2, "filth", detail_fn=paint_fin_membrane("bone", "filth")),
		"hand": sheet.alloc_box("hand", 3, 2, 4, "filth", hi=4, mid=3, side=2, back=2, lo=1, dark=1),
		"leg": sheet.alloc_box("leg", 3, 7, 3, "hide", hi=5, mid=4, side=3, back=3, lo=2, dark=1),
		"shin": sheet.alloc_box("shin", 2, 7, 2, "hide", detail_fn=paint_mud_caked("hide")),
		"foot": sheet.alloc_box("foot", 4, 2, 6, "filth", hi=4, mid=3, side=2, back=2, lo=1, dark=1),
		"claw": sheet.alloc_box("claw", 1, 3, 1, "bone", detail_fn=paint_claw_tip),
		"fang": sheet.alloc_box("fang", 1, 2, 1, "bone", detail_fn=paint_claw_tip),
	}

	b = RigBuilder(name, sheet)
	b.bone("root", None, (0, 0, 0))

	# Amphibious crouched pelvis set over wide webbed hind legs
	hip_y, hip_z = 13.0, 2.2
	b.bone("waist", "root", (0, hip_y, hip_z))
	b.cube("waist", "pelvis", (-3.0, hip_y - 1.8, hip_z - 2.0), (3.0, hip_y + 1.8, hip_z + 2.2), uv["waist"])

	# Muscular sculling river-tail & vertical paddle fluke (drives fast underwater swimming!)
	b.bone("tail_base", "waist", (0, hip_y - 0.2, hip_z + 2.0), (-20, 0, 0))
	b.cube("tail_base", "caudal", (-1.5, hip_y - 1.6, hip_z + 1.8), (1.5, hip_y + 1.2, hip_z + 7.6), uv["tail_base"])
	b.cube("tail_base", "crest", (-0.4, hip_y + 1.0, hip_z + 2.2), (0.4, hip_y + 2.8, hip_z + 7.2), uv["arm_fin"])

	fluke_y, fluke_z = hip_y - 0.3, hip_z + 7.4
	b.bone("tail_fluke", "tail_base", (0, fluke_y, fluke_z), (10, 0, 0))
	b.cube("tail_fluke", "paddle", (-0.5, fluke_y - 2.4, fluke_z - 0.2), (0.5, fluke_y + 2.6, fluke_z + 5.8), uv["tail_fluke"])

	# Splayed frog/otter-like webbed hind legs (soles flat on y=0)
	for side, sx, mir in (("right", 1, False), ("left", -1, True)):
		lx = 2.9 * sx
		knee_y = 7.2
		b.bone(f"{side}_leg", "waist", (lx, hip_y - 0.5, hip_z), (24, -8 * sx, 8 * sx))
		b.cube(f"{side}_leg", "thigh", (lx - 1.5, knee_y, hip_z - 1.6), (lx + 1.5, hip_y + 0.4, hip_z + 1.6), uv["leg"], mirror=mir)

		b.bone(f"{side}_shin", f"{side}_leg", (lx, knee_y, hip_z - 0.2), (-44, 0, -4 * sx))
		b.cube(f"{side}_shin", "calf", (lx - 1.1, 1.5, hip_z - 1.2), (lx + 1.1, knee_y + 0.4, hip_z + 1.2), uv["shin"], mirror=mir)
		b.cube(f"{side}_shin", "calf_fin", (lx - 0.4, 2.2, hip_z + 1.0), (lx + 0.4, knee_y, hip_z + 2.6), uv["arm_fin"], mirror=mir)

		# Counter-rotate foot (+20) against thigh(+24)+shin(-44)=-20 so webbed sole lies flat on y=0
		b.bone(f"{side}_foot", f"{side}_shin", (lx, 1.5, hip_z - 0.2), (20, 4 * sx, -4 * sx))
		b.cube(f"{side}_foot", "web_pad", (lx - 2.0, 0.0, hip_z - 4.8), (lx + 2.0, 1.5, hip_z + 1.2), uv["foot"], mirror=mir)
		for ci, ox in enumerate((-1.2, 0.0, 1.2)):
			cx = lx + ox
			b.cube(f"{side}_foot", f"web_claw_{ci}", (cx - 0.35, 0.0, hip_z - 6.0), (cx + 0.35, 1.0, hip_z - 4.6), uv["fang"], mirror=mir)

	# Forward-slung waterlogged torso & broad gill-ribbed chest draped in river-kelp
	abd_y, abd_z = 14.2, 1.6
	b.bone("body", "waist", (0, abd_y, abd_z), (-22, 0, 0))
	b.cube("body", "abdomen", (-2.8, abd_y - 0.4, abd_z - 2.4), (2.8, abd_y + 5.0, abd_z + 2.2), uv["body"])

	chest_y, chest_z = 18.6, 1.2
	b.bone("chest", "body", (0, chest_y, chest_z), (-26, 0, 0))
	b.cube("chest", "ribcage", (-4.2, chest_y - 0.4, chest_z - 3.2), (4.2, chest_y + 6.8, chest_z + 2.8), uv["chest"])
	# Tangled river-kelp strands hanging off the waterlogged shoulders & chest
	b.cube("chest", "kelp_r", (2.2, chest_y - 2.6, chest_z - 3.5), (4.3, chest_y + 3.6, chest_z - 2.5), uv["kelp"], rotation=(18, 0, -6), origin=(3.2, chest_y + 3.4, chest_z - 3.0))
	b.cube("chest", "kelp_l", (-4.3, chest_y - 1.8, chest_z + 1.8), (-2.1, chest_y + 4.4, chest_z + 2.8), uv["kelp"], mirror=True, rotation=(28, 0, 8), origin=(-3.2, chest_y + 4.2, chest_z + 2.3))

	# Serrated dorsal river-crest along the spine
	b.bone("dorsal_fin", "chest", (0, chest_y + 3.2, chest_z + 2.6))
	b.cube("dorsal_fin", "sail", (-0.5, chest_y - 0.2, chest_z + 2.4), (0.5, chest_y + 6.6, chest_z + 5.6), uv["dorsal"], rotation=(-8, 0, 0), origin=(0, chest_y + 3.0, chest_z + 2.6))

	# Low forward-thrusting neck (+38 counteracts -48 torso pitch to -10) & sloped pike-skull
	neck_y, neck_z = chest_y + 6.0, chest_z - 0.8
	b.bone("neck", "chest", (0, neck_y, neck_z), (38, 0, 0))
	b.cube("neck", "throat", (-2.0, neck_y - 1.6, neck_z - 4.0), (2.0, neck_y + 2.0, neck_z + 0.6), uv["neck"])

	head_y, head_z = neck_y - 0.2, neck_z - 3.6
	b.bone("head", "neck", (0, head_y, head_z), (14, 0, 0))
	b.cube("head", "cranium", (-3.1, head_y - 2.0, head_z - 6.0), (3.1, head_y + 3.0, head_z + 0.8), uv["head"])
	b.cube("head", "snout", (-2.4, head_y - 2.1, head_z - 9.4), (2.4, head_y + 0.6, head_z - 5.6), uv["snout"], rotation=(6, 0, 0), origin=(0, head_y - 0.8, head_z - 5.8))
	for side, sx, mir in (("r", 1, False), ("l", -1, True)):
		fx0, fx1 = sorted((1.3 * sx, 2.1 * sx))
		b.cube("head", f"pike_fang_{side}", (fx0, head_y - 3.5, head_z - 9.0), (fx1, head_y - 1.9, head_z - 8.1), uv["fang"], mirror=mir)
		fx2, fx3 = sorted((1.5 * sx, 2.2 * sx))
		b.cube("head", f"rear_fang_{side}", (fx2, head_y - 3.2, head_z - 6.8), (fx3, head_y - 1.9, head_z - 6.0), uv["fang"], mirror=mir)

	# Flared webbed river-fin ears (flare outward on bank_lunge!)
	for side, sx, mir in (("right", 1, False), ("left", -1, True)):
		ex0, ex1 = sorted((2.8 * sx, 3.6 * sx))
		b.bone(f"{side}_fin_ear", "head", (3.0 * sx, head_y + 0.6, head_z - 1.8), (10, -26 * sx, -8 * sx))
		b.cube(f"{side}_fin_ear", "frill", (ex0, head_y - 1.0, head_z - 2.0), (ex1, head_y + 2.2, head_z + 3.0), uv["fin_ear"], mirror=mir)

	# Wide snapping pike-jaw
	jaw_y, jaw_z = head_y - 1.8, head_z - 1.4
	b.bone("jaw", "head", (0, jaw_y, jaw_z), (-10, 0, 0))
	b.cube("jaw", "mandible", (-2.2, jaw_y - 1.5, jaw_z - 7.8), (2.2, jaw_y + 0.2, jaw_z + 0.2), uv["jaw"])
	for side, sx, mir in (("r", 1, False), ("l", -1, True)):
		tx0, tx1 = sorted((1.2 * sx, 1.9 * sx))
		b.cube("jaw", f"lower_fang_{side}", (tx0, jaw_y + 0.0, jaw_z - 7.5), (tx1, jaw_y + 1.4, jaw_z - 6.7), uv["fang"], mirror=mir)

	# Long grappling arms with forearm fin-ridges & wide webbed dragging claws
	sh_y, sh_z = chest_y + 5.2, chest_z - 0.6
	arm_len, fore_len = 6.8, 7.2
	for side, sx, mir in (("right", 1, False), ("left", -1, True)):
		ax = 4.7 * sx
		# Rest arm compensates for -48 chest pitch so arms hang forward-and-down ready to grapple
		b.bone(f"{side}_arm", "chest", (ax, sh_y, sh_z), (54, 0, 12 * sx))
		b.cube(f"{side}_arm", "humerus", (ax - 1.2, sh_y - arm_len, sh_z - 1.4), (ax + 1.2, sh_y + 0.8, sh_z + 1.4), uv["arm"], mirror=mir)

		elb_y = sh_y - arm_len
		b.bone(f"{side}_forearm", f"{side}_arm", (ax, elb_y, sh_z), (22, 0, -5 * sx))
		b.cube(f"{side}_forearm", "ulna", (ax - 1.1, elb_y - fore_len, sh_z - 1.3), (ax + 1.1, elb_y + 0.4, sh_z + 1.3), uv["forearm"], mirror=mir)
		fx0, fx1 = sorted((ax + 0.6 * sx, ax + 1.4 * sx))
		b.cube(f"{side}_forearm", "ulna_fin", (fx0, elb_y - 5.6, sh_z + 1.0), (fx1, elb_y - 0.4, sh_z + 3.0), uv["arm_fin"], mirror=mir)

		wr_y = elb_y - fore_len
		b.bone(f"{side}_hand", f"{side}_forearm", (ax, wr_y, sh_z), (-14, 0, -4 * sx))
		b.cube(f"{side}_hand", "web_palm", (ax - 1.6, wr_y - 2.0, sh_z - 2.0), (ax + 1.6, wr_y + 0.2, sh_z + 1.4), uv["hand"], mirror=mir)
		for ci, (ox, oz, rx_c, rz_c) in enumerate(((-1.05, -1.5, 24, -12), (0.0, -1.8, 20, 0), (1.05, -1.3, 16, 12))):
			cx = ax + ox * sx
			cz = sh_z + oz
			x0, x1 = sorted((cx - 0.4, cx + 0.4))
			b.cube(
				f"{side}_hand", f"hook_claw_{ci}",
				(x0, wr_y - 4.6, cz - 0.45), (x1, wr_y - 1.7, cz + 0.45),
				uv["claw"], mirror=mir, rotation=(rx_c, 0, rz_c * sx), origin=(cx, wr_y - 1.8, cz),
			)

	# ---------------------------------------------------------------- Animations (8)

	# 1. idle (loop): low riverbank ambush crouch, gill-ribs flaring, tail sculling gently
	idle = make_anim(name, "idle", 2.8, "loop", {
		"root": {"position": closed_loop(2.8, (0.0, Z), (0.5, (0, -0.5, 0)))},
		"waist": {"rotation": closed_loop(2.8, (0.0, Z), (0.5, (3, 0, 0)))},
		"chest": {"rotation": closed_loop(2.8, (0.0, Z), (0.5, (-5, 0, 0)))},
		"dorsal_fin": {"rotation": closed_loop(2.8, (0.0, (0, -4, 0)), (0.5, (4, 4, 0)))},
		"neck": {"rotation": closed_loop(2.8, (0.0, Z), (0.35, (4, -6, 0)), (0.7, (-3, 6, 0)))},
		"head": {"rotation": closed_loop(2.8, (0.0, (0, -6, 0)), (0.35, (5, 9, 3)), (0.7, (-4, -7, -3)))},
		"jaw": {"rotation": closed_loop(2.8, (0.0, (3, 0, 0)), (0.5, (15, 0, 0)))},
		"tail_base": {"rotation": closed_loop(2.8, (0.0, (0, -10, 0)), (0.5, (0, 10, 0)))},
		"tail_fluke": {"rotation": closed_loop(2.8, (0.0, (0, 14, 0)), (0.5, (0, -14, 0)))},
		**mirror_rot("fin_ear", closed_loop(2.8, (0.0, Z), (0.5, (0, 10, 4)))),
		**mirror_rot("arm", closed_loop(2.8, (0.0, Z), (0.5, (-6, 0, 4)))),
		**mirror_rot("hand", closed_loop(2.8, (0.0, Z), (0.5, (12, 0, 6)))),
	})

	# 2. walk (loop): clumsy, heavy, side-lurching flat-footed waddle on dry land ("clumsy on land")
	walk = make_anim(name, "walk", 0.95, "loop", {
		"root": {"position": closed_loop(0.95, (0.0, (0, -0.4, 0)), (0.25, (0.9, 0.6, 0)), (0.5, (0, -0.4, 0)), (0.75, (-0.9, 0.6, 0)))},
		"waist": {"rotation": closed_loop(0.95, (0.0, (4, -10, -8)), (0.5, (4, 10, 8)))},
		"chest": {"rotation": closed_loop(0.95, (0.0, (8, 12, 9)), (0.5, (8, -12, -9)))},
		"head": {"rotation": closed_loop(0.95, (0.0, (-4, -8, -7)), (0.5, (-4, 8, 7)))},
		"jaw": {"rotation": closed_loop(0.95, (0.0, (6, 0, 0)), (0.5, (16, 0, 0)))},
		"tail_base": {"rotation": closed_loop(0.95, (0.0, (6, -18, 0)), (0.5, (6, 18, 0)))},
		"tail_fluke": {"rotation": closed_loop(0.95, (0.0, (0, 22, 0)), (0.5, (0, -22, 0)))},
		"right_leg": {"rotation": closed_loop(0.95, (0.0, (-28, 0, 6)), (0.5, (22, 0, -4)))},
		"left_leg": {"rotation": closed_loop(0.95, (0.0, (22, 0, 4)), (0.5, (-28, 0, -6)))},
		"right_shin": {"rotation": closed_loop(0.95, (0.0, (10, 0, 0)), (0.5, (24, 0, 0)))},
		"left_shin": {"rotation": closed_loop(0.95, (0.0, (24, 0, 0)), (0.5, (10, 0, 0)))},
		"right_foot": {"rotation": closed_loop(0.95, (0.0, (18, 0, 0)), (0.5, (-12, 0, 0)))},
		"left_foot": {"rotation": closed_loop(0.95, (0.0, (-12, 0, 0)), (0.5, (18, 0, 0)))},
		"right_arm": {"rotation": closed_loop(0.95, (0.0, (22, 0, 14)), (0.5, (-30, 0, 8)))},
		"left_arm": {"rotation": closed_loop(0.95, (0.0, (-30, 0, -8)), (0.5, (22, 0, -14)))},
	})

	# 3. run (loop): awkward lunging land scramble, slapping webbed feet and clawing ahead
	run = make_anim(name, "run", 0.65, "loop", {
		"root": {"position": closed_loop(0.65, (0.0, (0, -0.8, 0)), (0.35, (0, 1.8, -0.6)), (0.7, (0, -0.8, 0.4)))},
		"waist": {"rotation": closed_loop(0.65, (0.0, (10, -8, -6)), (0.5, (10, 8, 6)))},
		"chest": {"rotation": closed_loop(0.65, (0.0, (12, 10, 6)), (0.5, (12, -10, -6)))},
		"head": {"rotation": closed_loop(0.65, (0.0, (-12, 0, 0)), (0.5, (-4, 0, 0)))},
		"jaw": {"rotation": closed_loop(0.65, (0.0, (14, 0, 0)), (0.5, (26, 0, 0)))},
		"tail_base": {"rotation": closed_loop(0.65, (0.0, (-8, -16, 0)), (0.5, (10, 16, 0)))},
		**mirror_rot("leg", closed_loop(0.65, (0.0, (-32, 0, 4)), (0.45, (30, 0, 4)))),
		**mirror_rot("arm", closed_loop(0.65, (0.0, (26, 0, 14)), (0.45, (-48, 0, 10)))),
	})

	# 4. swim (loop): fast horizontal river torpedo — full S-wave undulation through body, tail_base & tail_fluke!
	swim = make_anim(name, "swim", 0.55, "loop", {
		"root": {"position": closed_loop(0.55, (0.0, (-0.8, -4.5, 0)), (0.5, (0.8, -4.5, 0)))},
		"waist": {"rotation": closed_loop(0.55, (0.0, (58, -16, -6)), (0.5, (58, 16, 6)))},
		"body": {"rotation": closed_loop(0.55, (0.0, (-8, 14, 0)), (0.5, (-8, -14, 0)))},
		"chest": {"rotation": closed_loop(0.55, (0.0, (-12, 12, 4)), (0.5, (-12, -12, -4)))},
		"neck": {"rotation": closed_loop(0.55, (0.0, (-26, -10, 0)), (0.5, (-26, 10, 0)))},
		"head": {"rotation": closed_loop(0.55, (0.0, (-22, -8, 0)), (0.5, (-22, 8, 0)))},
		"jaw": {"rotation": closed_loop(0.55, (0.0, (6, 0, 0)), (0.5, (14, 0, 0)))},
		"tail_base": {"rotation": closed_loop(0.55, (0.0, (14, 28, 0)), (0.5, (14, -28, 0)))},
		"tail_fluke": {"rotation": closed_loop(0.55, (0.0, (0, -36, 0)), (0.25, (0, 32, 0)), (0.5, (0, 36, 0)), (0.75, (0, -32, 0)))},
		**mirror_rot("arm", closed_loop(0.55, (0.0, (42, -12, 18)), (0.5, (54, -8, 12)))),
		**mirror_rot("forearm", closed_loop(0.55, (0.0, (-16, 0, 0)), (0.5, (-8, 0, 0)))),
		"right_leg": {"rotation": closed_loop(0.55, (0.0, (36, 0, 8)), (0.5, (16, 0, 8)))},
		"left_leg": {"rotation": closed_loop(0.55, (0.0, (16, 0, -8)), (0.5, (36, 0, -8)))},
		**mirror_rot("foot", closed_loop(0.55, (0.0, (32, 0, 0)), (0.5, (20, 0, 0)))),
	})

	# 5. attack (once): double webbed-claw slash + snapping pike bite
	attack = make_anim(name, "attack", 0.55, "once", {
		"chest": {"rotation": [(0.0, Z), (0.16, (-14, 18, 0)), (0.32, (18, -22, 0)), (0.55, Z)]},
		"neck": {"rotation": [(0.0, Z), (0.16, (-10, -8, 0)), (0.32, (16, 10, 0)), (0.55, Z)]},
		"head": {"rotation": [(0.0, Z), (0.16, (-12, -10, 0)), (0.32, (10, 12, 0)), (0.55, Z)]},
		"jaw": {"rotation": [(0.0, Z), (0.16, (32, 0, 0)), (0.32, (-6, 0, 0)), (0.55, Z)]},
		"right_arm": {"rotation": [(0.0, Z), (0.16, (-92, 18, 30)), (0.32, (-14, -30, -14)), (0.55, Z)]},
		"right_forearm": {"rotation": [(0.0, Z), (0.16, (-24, 0, 0)), (0.32, (10, 0, -8)), (0.55, Z)]},
		"left_arm": {"rotation": [(0.0, Z), (0.16, (20, -10, -12)), (0.32, (-74, 16, -24)), (0.55, Z)]},
	})

	# 6. bank_lunge (once — yellow tell!): coils flat into the bank shallows with fin-ears & dorsal flared (0.26s),
	# then explodes upward & forward out of the river with wide grappling claws & pike-jaws (0.50s)!
	bank_lunge = make_anim(name, "bank_lunge", 0.90, "once", {
		"root": {"position": [
			(0.0, Z), (0.26, (0, -4.2, 2.8)), (0.50, (0, 5.6, -4.8)),
			(0.72, (0, -1.2, -1.4)), (0.90, Z),
		]},
		"waist": {"rotation": [(0.0, Z), (0.26, (24, 0, 0)), (0.50, (-22, 0, 0)), (0.72, (12, 0, 0)), (0.90, Z)]},
		"chest": {"rotation": [(0.0, Z), (0.26, (18, 0, 0)), (0.50, (-18, 0, 0)), (0.72, (14, 0, 0)), (0.90, Z)]},
		"dorsal_fin": {"rotation": [(0.0, Z), (0.26, (10, 0, 0)), (0.50, (-14, 0, 0)), (0.90, Z)]},
		"head": {"rotation": [(0.0, Z), (0.26, (-26, 0, 0)), (0.50, (-12, 0, 0)), (0.72, (8, 0, 0)), (0.90, Z)]},
		"jaw": {"rotation": [(0.0, Z), (0.26, (18, 0, 0)), (0.50, (40, 0, 0)), (0.70, (-6, 0, 0)), (0.90, Z)]},
		"tail_base": {"rotation": [(0.0, Z), (0.26, (18, 0, 0)), (0.50, (-24, 0, 0)), (0.90, Z)]},
		**mirror_rot("fin_ear", [(0.0, Z), (0.26, (0, 22, 10)), (0.50, (0, 28, 14)), (0.90, Z)]),
		**mirror_rot("leg", [(0.0, Z), (0.26, (-36, 0, 6)), (0.50, (42, 0, 0)), (0.72, (-14, 0, 0)), (0.90, Z)]),
		**mirror_rot("shin", [(0.0, Z), (0.26, (38, 0, 0)), (0.50, (-22, 0, 0)), (0.72, (14, 0, 0)), (0.90, Z)]),
		**mirror_rot("arm", [(0.0, Z), (0.26, (32, 0, 20)), (0.50, (-94, 16, 32)), (0.72, (-22, 0, 10)), (0.90, Z)]),
		**mirror_rot("forearm", [(0.0, Z), (0.26, (-28, 0, 0)), (0.50, (-12, 0, 10)), (0.90, Z)]),
		**mirror_rot("hand", [(0.0, Z), (0.50, (-22, 0, 14)), (0.70, (24, 0, -10)), (0.90, Z)]),
	})

	# 7. drag_under (once, 1.1s): lunges low to clamp both hooked claws onto a fallen victim (0.24s),
	# wrenches backward onto haunches (0.54s), and hauls downward beneath the waterline (0.86..1.10s)!
	drag_under = make_anim(name, "drag_under", 1.10, "once", {
		"root": {"position": [
			(0.0, Z), (0.24, (0, -3.4, -2.6)), (0.54, (0, -1.8, 2.2)),
			(0.86, (0, -6.4, 3.8)), (1.10, Z),
		]},
		"waist": {"rotation": [(0.0, Z), (0.24, (22, 0, 0)), (0.54, (-18, 0, 0)), (0.86, (-10, 0, 0)), (1.10, Z)]},
		"chest": {"rotation": [(0.0, Z), (0.24, (28, 0, 0)), (0.54, (-20, 0, 0)), (0.86, (16, 0, 0)), (1.10, Z)]},
		"neck": {"rotation": [(0.0, Z), (0.24, (14, 0, 0)), (0.54, (-18, -8, 0)), (0.86, (-12, 8, 0)), (1.10, Z)]},
		"head": {"rotation": [(0.0, Z), (0.24, (10, 0, 0)), (0.54, (-16, 10, 6)), (0.86, (-10, -10, -6)), (1.10, Z)]},
		"jaw": {"rotation": [(0.0, Z), (0.22, (34, 0, 0)), (0.34, (-6, 0, 0)), (0.86, (-4, 0, 0)), (1.10, Z)]},
		"tail_base": {"rotation": [(0.0, Z), (0.54, (18, 20, 0)), (0.86, (24, -20, 0)), (1.10, Z)]},
		**mirror_rot("arm", [(0.0, Z), (0.24, (-54, -14, 10)), (0.54, (-26, -18, 6)), (0.86, (14, -12, 8)), (1.10, Z)]),
		**mirror_rot("forearm", [(0.0, Z), (0.24, (-10, 0, 0)), (0.54, (-36, 0, 8)), (0.86, (-42, 0, 10)), (1.10, Z)]),
		**mirror_rot("hand", [(0.0, Z), (0.24, (28, 0, -12)), (0.86, (34, 0, -16)), (1.10, Z)]),
		**mirror_rot("leg", [(0.0, Z), (0.24, (-22, 0, 0)), (0.54, (-32, 0, 8)), (0.86, (-40, 0, 10)), (1.10, Z)]),
	})

	# 8. emerge (once, 0.8s): surges up out of river water onto the bank, shaking water off head & fin-ears
	emerge = make_anim(name, "emerge", 0.80, "once", {
		"root": {"position": [(0.0, (0, -9.0, 2.0)), (0.32, (0, 2.2, -0.8)), (0.56, (0, -0.6, 0)), (0.80, Z)]},
		"waist": {"rotation": [(0.0, (-34, 0, 0)), (0.32, (-12, 0, 0)), (0.56, (10, 0, 0)), (0.80, Z)]},
		"chest": {"rotation": [(0.0, (-22, 0, 0)), (0.32, (-14, 0, 0)), (0.56, (8, 0, 0)), (0.80, Z)]},
		"head": {"rotation": [(0.0, (-18, 0, 0)), (0.36, (-10, -16, -10)), (0.56, (-6, 16, 10)), (0.80, Z)]},
		"jaw": {"rotation": [(0.0, (8, 0, 0)), (0.34, (30, 0, 0)), (0.80, Z)]},
		**mirror_rot("arm", [(0.0, (-56, 10, 18)), (0.34, (18, -10, 28)), (0.80, Z)]),
	})

	return b, [idle, walk, run, swim, attack, bank_lunge, drag_under, emerge]
