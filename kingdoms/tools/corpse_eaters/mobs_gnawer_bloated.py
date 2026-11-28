"""Rows B1 (grave-gnawer & spined pack leader) and B5 (bloated dead & spined variant)."""
from __future__ import annotations

from .common import (
	IndexedSheet,
	RigBuilder,
	Z,
	closed_loop,
	make_anim,
	mirror_rot,
	paint_claw_tip,
	paint_feral_skull,
	paint_filth_caked,
	paint_jaw_maw,
	paint_ribbed_torso,
	paint_swollen_belly,
)


# =====================================================================
# B1: GRAVE-GNAWER & GRAVE-GNAWER PACK LEADER (shared 64x64 UV sheet)
# =====================================================================

def make_gnawer_sheet() -> tuple[IndexedSheet, dict]:
	sheet = IndexedSheet(64, 64)
	uv = {
		"head": sheet.alloc_box("head", 6, 5, 7, "hide", detail_fn=paint_feral_skull(eye_row=2)),
		"snout": sheet.alloc_box("snout", 4, 3, 4, "hide", hi=5, mid=4, side=3, back=3, lo=2, dark=1),
		"jaw": sheet.alloc_box("jaw", 4, 2, 7, "hide", detail_fn=paint_jaw_maw),
		"ear": sheet.alloc_box("ear", 1, 2, 4, "hide", hi=4, mid=3, side=3, back=2, lo=2, dark=1),
		"neck": sheet.alloc_box("neck", 4, 4, 5, "sinew", hi=4, mid=3, side=3, back=2, lo=2, dark=1),
		"chest": sheet.alloc_box("chest", 8, 6, 7, "hide", detail_fn=paint_ribbed_torso),
		"body": sheet.alloc_box("body", 5, 5, 5, "sinew", hi=4, mid=3, side=2, back=2, lo=1, dark=1),
		"waist": sheet.alloc_box("waist", 6, 4, 4, "hide", detail_fn=paint_filth_caked("hide")),
		"tail": sheet.alloc_box("tail", 2, 2, 5, "filth", hi=4, mid=3, side=2, back=2, lo=1, dark=1),
		"arm": sheet.alloc_box("arm", 2, 8, 3, "hide", hi=5, mid=4, side=3, back=3, lo=2, dark=1),
		"forearm": sheet.alloc_box("forearm", 2, 9, 2, "hide", detail_fn=paint_filth_caked("hide")),
		"hand": sheet.alloc_box("hand", 3, 2, 3, "filth", hi=4, mid=3, side=2, back=2, lo=1, dark=1),
		"leg": sheet.alloc_box("leg", 3, 7, 3, "hide", hi=5, mid=4, side=3, back=3, lo=2, dark=1),
		"shin": sheet.alloc_box("shin", 2, 7, 2, "hide", detail_fn=paint_filth_caked("hide")),
		"foot": sheet.alloc_box("foot", 3, 2, 5, "filth", hi=4, mid=3, side=2, back=2, lo=1, dark=1),
		"claw": sheet.alloc_box("claw", 1, 3, 1, "bone", detail_fn=paint_claw_tip),
		"fang": sheet.alloc_box("fang", 1, 2, 1, "bone", detail_fn=paint_claw_tip),
		"vertebra": sheet.alloc_box("vertebra", 1, 2, 2, "bone", hi=6, mid=5, side=4, back=4, lo=3, dark=2),
		"spine_tall": sheet.alloc_box("spine_tall", 1, 6, 2, "bone", detail_fn=paint_claw_tip),
		"spine_mid": sheet.alloc_box("spine_mid", 1, 4, 2, "bone", detail_fn=paint_claw_tip),
	}
	return sheet, uv


def _build_gnawer_rig(name: str, sheet: IndexedSheet, uv: dict, scale: float, leader: bool) -> RigBuilder:
	S = lambda v: round(v * scale, 4)
	b = RigBuilder(name, sheet)
	b.bone("root", None, (0, 0, 0))

	# Hunched feral scavenger pelvis set back (+z) over crouched digitigrade hind legs
	hip_y, hip_z = S(12.2), S(3.4)
	b.bone("waist", "root", (0, hip_y, hip_z))
	b.cube("waist", "pelvis", (-S(2.8), hip_y - S(1.6), hip_z - S(2.0)), (S(2.8), hip_y + S(1.8), hip_z + S(2.2)), uv["waist"])
	b.cube("waist", "iliac_r", (S(2.3), hip_y + S(0.2), hip_z - S(1.4)), (S(3.3), hip_y + S(2.2), hip_z + S(1.0)), uv["vertebra"])
	b.cube("waist", "iliac_l", (-S(3.3), hip_y + S(0.2), hip_z - S(1.4)), (-S(2.3), hip_y + S(2.2), hip_z + S(1.0)), uv["vertebra"], mirror=True)

	# Ragged scavenger tail
	b.bone("tail", "waist", (0, hip_y + S(0.4), hip_z + S(2.0)), (-24, 0, 0))
	b.cube("tail", "stump", (-S(0.9), hip_y - S(0.5), hip_z + S(1.8)), (S(0.9), hip_y + S(1.1), hip_z + S(6.6)), uv["tail"])

	# Digitigrade crouching hind legs (feet flat on y=0)
	for side, sx, mir in (("right", 1, False), ("left", -1, True)):
		lx = S(2.7) * sx
		knee_y, knee_z = S(6.8), S(0.6)
		hock_y, hock_z = S(3.4), S(4.2)
		b.bone(f"{side}_leg", "waist", (lx, hip_y - S(0.4), hip_z), (28, 0, 4 * sx))
		b.cube(
			f"{side}_leg", "thigh",
			(lx - S(1.4), knee_y, hip_z - S(1.6)), (lx + S(1.4), hip_y + S(0.4), hip_z + S(1.5)),
			uv["leg"], mirror=mir,
		)
		b.bone(f"{side}_shin", f"{side}_leg", (lx, knee_y, hip_z - S(0.2)), (-52, 0, 0))
		b.cube(
			f"{side}_shin", "calf",
			(lx - S(1.0), S(1.6), hip_z - S(1.2)), (lx + S(1.0), knee_y + S(0.4), hip_z + S(1.0)),
			uv["shin"], mirror=mir,
		)
		b.cube(
			f"{side}_shin", "hock_spur",
			(lx - S(0.5), S(1.8), hip_z + S(0.8)), (lx + S(0.5), S(3.4), hip_z + S(2.2)),
			uv["fang"], mirror=mir,
		)
		# Counter-rotate foot (+24) against thigh(+28)+shin(-52)=-24 so sole sits dead level on y=0
		foot_y = S(1.6)
		b.bone(f"{side}_foot", f"{side}_shin", (lx, foot_y, hip_z - S(0.2)), (24, 0, -4 * sx))
		b.cube(
			f"{side}_foot", "pad",
			(lx - S(1.4), 0.0, hip_z - S(3.8)), (lx + S(1.4), S(1.6), hip_z + S(1.0)),
			uv["foot"], mirror=mir,
		)
		for ci, ox in enumerate((-0.85, 0.0, 0.85)):
			cx = lx + S(ox)
			b.cube(
				f"{side}_foot", f"toe_claw_{ci}",
				(cx - S(0.35), 0.0, hip_z - S(5.1)), (cx + S(0.35), S(1.1), hip_z - S(3.6)),
				uv["fang"], mirror=mir,
			)

	# Starved abdomen pitched steeply forward into a high, arched thoracic hump
	abd_y, abd_z = S(13.4), S(2.4)
	b.bone("body", "waist", (0, abd_y, abd_z), (-34, 0, 0))
	b.cube("body", "abdomen", (-S(2.4), abd_y - S(0.4), abd_z - S(2.4)), (S(2.4), abd_y + S(4.8), abd_z + S(2.0)), uv["body"])

	chest_y, chest_z = S(17.4), S(1.8)
	b.bone("chest", "body", (0, chest_y, chest_z), (-36, 0, 0))
	# Because body(-34)+chest(-36) = -70 deg forward pitch, "up" along local +y projects almost straight forward (-z)!
	b.cube("chest", "ribcage", (-S(4.1), chest_y - S(0.6), chest_z - S(3.4)), (S(4.1), chest_y + S(6.8), chest_z + S(3.2)), uv["chest"])
	# Sculpted protruding rib-bars wrapping the sides
	for ri, ry in enumerate((1.0, 3.0, 5.0)):
		for side, sx, mir in (("r", 1, False), ("l", -1, True)):
			x0, x1 = sorted((S(3.7) * sx, S(4.5) * sx))
			b.cube("chest", f"rib_{side}_{ri}", (x0, chest_y + S(ry), chest_z - S(2.8)), (x1, chest_y + S(ry + 1.1), chest_z + S(2.0)), uv["vertebra"], mirror=mir)

	# Vertebral ridge (small bumps on base gnawer; tall jagged spined rack on pack leader!)
	b.bone("spine", "chest", (0, chest_y + S(3.0), chest_z + S(3.0)))
	for vi, vy in enumerate((0.4, 2.2, 4.0, 5.6)):
		b.cube("spine", f"knob_{vi}", (-S(0.5), chest_y + S(vy), chest_z + S(2.8)), (S(0.5), chest_y + S(vy + 1.3), chest_z + S(4.2)), uv["vertebra"])

	if leader:
		b.bone("spines", "chest", (0, chest_y + S(3.2), chest_z + S(3.2)))
		spine_specs = [
			(0.3, 4.8, -18, "spine_mid"),
			(1.8, 6.8, -12, "spine_tall"),
			(3.4, 7.6, -6, "spine_tall"),
			(5.0, 6.4, 4, "spine_tall"),
			(6.3, 4.6, 12, "spine_mid"),
		]
		for si, (vy, slen, tilt, uk) in enumerate(spine_specs):
			py, pz = chest_y + S(vy), chest_z + S(3.0)
			b.cube(
				"spines", f"dorsal_{si}",
				(-S(0.55), py, pz), (S(0.55), py + S(1.3), pz + S(slen)),
				uv[uk], rotation=(tilt, 0, 0), origin=(0, py, pz),
			)
		for side, sx, mir in (("r", 1, False), ("l", -1, True)):
			px = S(3.4) * sx
			py, pz = chest_y + S(4.2), chest_z + S(2.2)
			x0, x1 = sorted((px - S(0.5), px + S(0.5)))
			b.cube(
				"spines", f"scapula_quill_{side}",
				(x0, py, pz), (x1, py + S(1.2), pz + S(5.2)),
				uv["spine_mid"], mirror=mir, rotation=(8, 18 * sx, 22 * sx), origin=(px, py, pz),
			)

	# Forward-and-DOWN craned neck (+55 counteracts -70 to -15, slinging skull low ahead of the shoulders!)
	neck_y, neck_z = chest_y + S(6.2), chest_z - S(0.4)
	b.bone("neck", "chest", (0, neck_y, neck_z), (52, 0, 0))
	b.cube("neck", "throat", (-S(1.9), neck_y - S(1.6), neck_z - S(4.6)), (S(1.9), neck_y + S(2.0), neck_z + S(0.6)), uv["neck"])
	if leader:
		b.bone("neck_spines", "neck", (0, neck_y + S(1.8), neck_z - S(2.0)))
		for ni, nz in enumerate((-1.2, -3.2)):
			b.cube(
				"neck_spines", f"quill_{ni}",
				(-S(0.45), neck_y + S(1.6), neck_z + S(nz)), (S(0.45), neck_y + S(5.0), neck_z + S(nz + 1.2)),
				uv["spine_mid"], rotation=(28, 0, 0), origin=(0, neck_y + S(1.8), neck_z + S(nz)),
			)

	# Lowered corpse-eater skull & long hinged scavenging jaw (hangs BELOW shoulder height!)
	head_y, head_z = neck_y - S(0.8), neck_z - S(4.2)
	b.bone("head", "neck", (0, head_y, head_z), (26, 0, 0))
	b.cube("head", "cranium", (-S(3.0), head_y - S(2.0), head_z - S(5.8)), (S(3.0), head_y + S(3.2), head_z + S(0.8)), uv["head"])
	b.cube("head", "muzzle", (-S(2.1), head_y - S(2.2), head_z - S(9.2)), (S(2.1), head_y + S(0.2), head_z - S(5.4)), uv["snout"])
	# Jagged upper scavenging fangs
	for side, sx, mir in (("r", 1, False), ("l", -1, True)):
		fx0, fx1 = sorted((S(1.2) * sx, S(1.95) * sx))
		b.cube("head", f"fang_{side}", (fx0, head_y - S(3.7), head_z - S(8.9)), (fx1, head_y - S(1.9), head_z - S(8.0)), uv["fang"], mirror=mir)
		bx0, bx1 = sorted((S(1.4) * sx, S(3.2) * sx))
		b.cube("head", f"brow_{side}", (bx0, head_y + S(2.1), head_z - S(6.0)), (bx1, head_y + S(3.3), head_z - S(2.4)), uv["vertebra"], mirror=mir)

	# Swept-back corpse-hound ears
	for side, sx, mir in (("right", 1, False), ("left", -1, True)):
		ex0, ex1 = sorted((S(2.7) * sx, S(3.6) * sx))
		b.bone(f"{side}_ear", "head", (S(2.9) * sx, head_y + S(1.8), head_z - S(1.8)), (22, -18 * sx, -16 * sx))
		b.cube(f"{side}_ear", "pinna", (ex0, head_y + S(0.8), head_z - S(2.0)), (ex1, head_y + S(2.8), head_z + S(2.4)), uv["ear"], mirror=mir)

	# Hinged lower jaw hanging slightly open (-12 deg Blockbench drops front of jaw down!)
	jaw_y, jaw_z = head_y - S(1.8), head_z - S(1.6)
	b.bone("jaw", "head", (0, jaw_y, jaw_z), (-12, 0, 0))
	b.cube("jaw", "mandible", (-S(1.9), jaw_y - S(1.5), jaw_z - S(7.4)), (S(1.9), jaw_y + S(0.2), jaw_z + S(0.2)), uv["jaw"])
	for side, sx, mir in (("r", 1, False), ("l", -1, True)):
		tx0, tx1 = sorted((S(1.0) * sx, S(1.7) * sx))
		b.cube("jaw", f"tusk_{side}", (tx0, jaw_y - S(0.1), jaw_z - S(7.2)), (tx1, jaw_y + S(1.3), jaw_z - S(6.4)), uv["fang"], mirror=mir)

	# Long apelike corpse-raking arms hanging down from the hunched shoulders so claw tips hover cleanly at y~0.4 above the ground!
	sh_y, sh_z = chest_y + S(5.2), chest_z - S(0.6)
	arm_len, fore_len = S(6.6), S(6.8)
	for side, sx, mir in (("right", 1, False), ("left", -1, True)):
		ax = S(4.6) * sx
		b.bone(f"{side}_arm", "chest", (ax, sh_y, sh_z), (72, 0, 10 * sx))
		b.cube(f"{side}_arm", "humerus", (ax - S(1.2), sh_y - arm_len, sh_z - S(1.4)), (ax + S(1.2), sh_y + S(0.8), sh_z + S(1.4)), uv["arm"], mirror=mir)
		b.cube(f"{side}_arm", "deltoid_spur", (ax - S(0.8), sh_y + S(0.2), sh_z - S(0.9)), (ax + S(0.8), sh_y + S(2.0), sh_z + S(0.9)), uv["vertebra"], mirror=mir)

		elb_y = sh_y - arm_len
		b.bone(f"{side}_forearm", f"{side}_arm", (ax, elb_y, sh_z), (24, 0, -4 * sx))
		b.cube(f"{side}_forearm", "ulna", (ax - S(1.1), elb_y - fore_len, sh_z - S(1.2)), (ax + S(1.1), elb_y + S(0.4), sh_z + S(1.2)), uv["forearm"], mirror=mir)

		wr_y = elb_y - fore_len
		b.bone(f"{side}_hand", f"{side}_forearm", (ax, wr_y, sh_z), (-18, 0, -4 * sx))
		b.cube(f"{side}_hand", "palm", (ax - S(1.5), wr_y - S(1.8), sh_z - S(1.6)), (ax + S(1.5), wr_y + S(0.2), sh_z + S(1.4)), uv["hand"], mirror=mir)
		for ci, (ox, oz, rx_c, rz_c) in enumerate(((-0.95, -1.2, 22, -10), (0.0, -1.4, 18, 0), (0.95, -1.0, 14, 10))):
			cx = ax + S(ox) * sx
			cz = sh_z + S(oz)
			x0, x1 = sorted((cx - S(0.4), cx + S(0.4)))
			b.cube(
				f"{side}_hand", f"claw_{ci}",
				(x0, wr_y - S(4.2), cz - S(0.45)), (x1, wr_y - S(1.5), cz + S(0.45)),
				uv["claw"], mirror=mir, rotation=(rx_c, 0, rz_c * sx), origin=(cx, wr_y - S(1.6), cz),
			)
	return b


def build_gnawer_animations(name: str, scale: float, leader: bool) -> list[dict]:
	S = lambda v: round(v * scale, 4)

	# 1. idle (loop)
	idle = make_anim(name, "idle", 2.8, "loop", {
		"root": {"position": closed_loop(2.8, (0.0, Z), (0.5, (0, -S(0.4), 0)))},
		"waist": {"rotation": closed_loop(2.8, (0.0, Z), (0.5, (2, 0, 0)))},
		"chest": {"rotation": closed_loop(2.8, (0.0, Z), (0.5, (-4, 0, 0)))},
		"neck": {"rotation": closed_loop(2.8, (0.0, Z), (0.35, (4, -6, 0)), (0.7, (-3, 6, 0)))},
		"head": {"rotation": closed_loop(2.8, (0.0, (0, -5, 0)), (0.35, (5, 9, 3)), (0.7, (-4, -7, -3)))},
		"jaw": {"rotation": closed_loop(2.8, (0.0, (2, 0, 0)), (0.45, (16, 0, 0)), (0.75, (4, 0, 0)))},
		"tail": {"rotation": closed_loop(2.8, (0.0, (0, -8, 0)), (0.5, (4, 8, 0)))},
		**mirror_rot("ear", closed_loop(2.8, (0.0, Z), (0.4, (-6, 5, 4)), (0.6, (2, -2, -2)))),
		**mirror_rot("arm", closed_loop(2.8, (0.0, Z), (0.5, (-6, 0, 4)))),
		**mirror_rot("forearm", closed_loop(2.8, (0.0, Z), (0.5, (-8, 0, 0)))),
		**mirror_rot("hand", closed_loop(2.8, (0.0, Z), (0.35, (10, 0, 5)), (0.7, (-6, 0, -4)))),
	})

	# 2. walk (loop): skulking pack prowl, lowered head bobbing, long arms swinging low beside crouching hind legs
	walk = make_anim(name, "walk", 0.8, "loop", {
		"root": {"position": closed_loop(0.8, (0.0, (0, 0, 0)), (0.25, (0, S(0.7), 0)), (0.5, (0, 0, 0)), (0.75, (0, S(0.7), 0)))},
		"waist": {"rotation": closed_loop(0.8, (0.0, (3, -6, -2)), (0.5, (3, 6, 2)))},
		"chest": {"rotation": closed_loop(0.8, (0.0, (5, 7, 2)), (0.5, (5, -7, -2)))},
		"neck": {"rotation": closed_loop(0.8, (0.0, (-4, -4, 0)), (0.5, (-4, 4, 0)))},
		"head": {"rotation": closed_loop(0.8, (0.0, (-4, -4, -2)), (0.25, (3, 0, 0)), (0.5, (-4, 4, 2)), (0.75, (3, 0, 0)))},
		"jaw": {"rotation": closed_loop(0.8, (0.0, (4, 0, 0)), (0.5, (12, 0, 0)))},
		"tail": {"rotation": closed_loop(0.8, (0.0, (0, -14, 0)), (0.5, (0, 14, 0)))},
		"right_leg": {"rotation": closed_loop(0.8, (0.0, (-28, 0, 0)), (0.5, (24, 0, 0)))},
		"left_leg": {"rotation": closed_loop(0.8, (0.0, (24, 0, 0)), (0.5, (-28, 0, 0)))},
		"right_shin": {"rotation": closed_loop(0.8, (0.0, (12, 0, 0)), (0.25, (6, 0, 0)), (0.5, (-14, 0, 0)), (0.75, (26, 0, 0)))},
		"left_shin": {"rotation": closed_loop(0.8, (0.0, (-14, 0, 0)), (0.25, (26, 0, 0)), (0.5, (12, 0, 0)), (0.75, (6, 0, 0)))},
		"right_foot": {"rotation": closed_loop(0.8, (0.0, (16, 0, 0)), (0.5, (-10, 0, 0)))},
		"left_foot": {"rotation": closed_loop(0.8, (0.0, (-10, 0, 0)), (0.5, (16, 0, 0)))},
		"right_arm": {"rotation": closed_loop(0.8, (0.0, (24, 0, 6)), (0.5, (-30, 0, 4)))},
		"left_arm": {"rotation": closed_loop(0.8, (0.0, (-30, 0, -4)), (0.5, (24, 0, -6)))},
		"right_forearm": {"rotation": closed_loop(0.8, (0.0, (-6, 0, 0)), (0.5, (-22, 0, 0)))},
		"left_forearm": {"rotation": closed_loop(0.8, (0.0, (-22, 0, 0)), (0.5, (-6, 0, 0)))},
	})

	# 3. run (loop): bounding four-limb corpse-hound lope!
	run = make_anim(name, "run", 0.5, "loop", {
		"root": {"position": closed_loop(0.5, (0.0, (0, -S(0.6), 0)), (0.35, (0, S(2.0), -S(0.8))), (0.7, (0, -S(0.8), S(0.4))))},
		"waist": {"rotation": closed_loop(0.5, (0.0, (10, 0, 0)), (0.4, (-12, 0, 0)), (0.75, (8, 0, 0)))},
		"chest": {"rotation": closed_loop(0.5, (0.0, (10, 0, 0)), (0.4, (-10, 0, 0)), (0.75, (8, 0, 0)))},
		"head": {"rotation": closed_loop(0.5, (0.0, (-12, 0, 0)), (0.4, (4, 0, 0)), (0.75, (-10, 0, 0)))},
		"jaw": {"rotation": closed_loop(0.5, (0.0, (12, 0, 0)), (0.4, (24, 0, 0)))},
		**mirror_rot("leg", closed_loop(0.5, (0.0, (-34, 0, 0)), (0.45, (32, 0, 0)))),
		**mirror_rot("shin", closed_loop(0.5, (0.0, (22, 0, 0)), (0.45, (-18, 0, 0)))),
		**mirror_rot("arm", closed_loop(0.5, (0.0, (32, 0, 10)), (0.45, (-52, 0, 8)))),
		**mirror_rot("forearm", closed_loop(0.5, (0.0, (-18, 0, 0)), (0.45, (-28, 0, 0)))),
	})

	# 4. attack (once): savage double-claw rend + snapping bite
	attack = make_anim(name, "attack", 0.55, "once", {
		"chest": {"rotation": [(0.0, Z), (0.16, (-14, 18, 0)), (0.30, (18, -20, 0)), (0.55, Z)]},
		"neck": {"rotation": [(0.0, Z), (0.16, (-10, -8, 0)), (0.30, (16, 10, 0)), (0.55, Z)]},
		"head": {"rotation": [(0.0, Z), (0.16, (-12, -12, -4)), (0.30, (10, 14, 4)), (0.55, Z)]},
		"jaw": {"rotation": [(0.0, Z), (0.16, (28, 0, 0)), (0.30, (-8, 0, 0)), (0.55, Z)]},
		"right_arm": {"rotation": [(0.0, Z), (0.16, (-96, 16, 30)), (0.30, (-18, -28, -14)), (0.55, Z)]},
		"right_forearm": {"rotation": [(0.0, Z), (0.16, (-26, 0, 0)), (0.30, (10, 0, -10)), (0.55, Z)]},
		"right_hand": {"rotation": [(0.0, Z), (0.16, (-20, 0, 10)), (0.30, (24, 0, -12)), (0.55, Z)]},
		"left_arm": {"rotation": [(0.0, Z), (0.16, (20, -10, -14)), (0.30, (-68, 18, -20)), (0.55, Z)]},
		"left_forearm": {"rotation": [(0.0, Z), (0.16, (-12, 0, 0)), (0.30, (-22, 0, 8)), (0.55, Z)]},
	})

	# 5. lunge (once, yellow tell!): deep coil back onto haunches (0.22s), explosive pounce with wide claws & jaws (0.46s)
	lunge = make_anim(name, "lunge", 0.85, "once", {
		"root": {"position": [
			(0.0, Z), (0.22, (0, -S(3.2), S(2.4))), (0.46, (0, S(4.8), -S(4.2))),
			(0.68, (0, -S(1.0), -S(1.2))), (0.85, Z),
		]},
		"waist": {"rotation": [(0.0, Z), (0.22, (20, 0, 0)), (0.46, (-18, 0, 0)), (0.68, (10, 0, 0)), (0.85, Z)]},
		"chest": {"rotation": [(0.0, Z), (0.22, (16, 0, 0)), (0.46, (-16, 0, 0)), (0.68, (12, 0, 0)), (0.85, Z)]},
		"head": {"rotation": [(0.0, Z), (0.22, (-22, 0, 0)), (0.46, (-10, 0, 0)), (0.68, (6, 0, 0)), (0.85, Z)]},
		"jaw": {"rotation": [(0.0, Z), (0.22, (14, 0, 0)), (0.46, (34, 0, 0)), (0.66, (-6, 0, 0)), (0.85, Z)]},
		**mirror_rot("leg", [(0.0, Z), (0.22, (-32, 0, 0)), (0.46, (38, 0, 0)), (0.68, (-12, 0, 0)), (0.85, Z)]),
		**mirror_rot("shin", [(0.0, Z), (0.22, (36, 0, 0)), (0.46, (-24, 0, 0)), (0.68, (16, 0, 0)), (0.85, Z)]),
		**mirror_rot("arm", [(0.0, Z), (0.22, (34, 0, 18)), (0.46, (-88, 14, 28)), (0.68, (-24, 0, 10)), (0.85, Z)]),
		**mirror_rot("forearm", [(0.0, Z), (0.22, (-28, 0, 0)), (0.46, (-12, 0, 8)), (0.85, Z)]),
		**mirror_rot("hand", [(0.0, Z), (0.46, (-18, 0, 12)), (0.66, (20, 0, -8)), (0.85, Z)]),
	})

	# 6. feed (loop): crouched over an unburied body, tearing flesh and wrenching upward
	feed = make_anim(name, "feed", 1.2, "loop", {
		"root": {"position": closed_loop(1.2, (0.0, (0, -S(2.6), 0)), (0.55, (0, -S(1.2), S(0.6))))},
		"waist": {"rotation": closed_loop(1.2, (0.0, (14, 0, 0)), (0.55, (6, 0, 0)))},
		"chest": {"rotation": closed_loop(1.2, (0.0, (18, 0, 0)), (0.55, (-6, 0, 0)))},
		"neck": {"rotation": closed_loop(1.2, (0.0, (22, -5, 0)), (0.35, (26, 6, 0)), (0.65, (-14, 0, 0)))},
		"head": {"rotation": closed_loop(1.2, (0.0, (14, -8, -4)), (0.35, (18, 8, 4)), (0.65, (-18, 0, 0)))},
		"jaw": {"rotation": closed_loop(1.2, (0.0, (28, 0, 0)), (0.30, (-8, 0, 0)), (0.65, (18, 0, 0)))},
		**mirror_rot("arm", closed_loop(1.2, (0.0, (-34, -10, 8)), (0.55, (-18, -6, 12)))),
		**mirror_rot("forearm", closed_loop(1.2, (0.0, (-22, 0, 0)), (0.55, (-34, 0, 0)))),
	})

	# 7. knit_back (once, 1.5s): a badly hurt gnawer collapsed & broken on the ground wrenching its dislocated limbs
	# and snapped spine back into place, hauling itself upright with a snarl!
	knit_back = make_anim(name, "knit_back", 1.5, "once", {
		"root": {"position": [
			(0.0, (0, -S(8.4), S(1.0))), (0.32, (0, -S(8.0), S(0.8))),
			(0.68, (0, -S(5.2), S(0.4))), (1.02, (0, -S(1.4), -S(0.4))),
			(1.28, (0, S(0.6), 0)), (1.5, Z),
		]},
		"waist": {"rotation": [
			(0.0, (24, 18, 42)), (0.32, (20, -14, 34)),
			(0.68, (-12, 10, -14)), (1.02, (12, -4, 4)), (1.5, Z),
		]},
		"body": {"rotation": [
			(0.0, (18, -24, -22)), (0.36, (22, 18, -16)),
			(0.72, (-20, -12, 10)), (1.05, (8, 0, 0)), (1.5, Z),
		]},
		"chest": {"rotation": [
			(0.0, (16, 26, -28)), (0.36, (10, -22, -18)),
			(0.74, (-26, 14, 12)), (1.08, (10, -6, 0)), (1.5, Z),
		]},
		"neck": {"rotation": [
			(0.0, (28, -32, -20)), (0.40, (24, 26, 16)),
			(0.78, (-22, -10, -8)), (1.15, (-8, 4, 0)), (1.5, Z),
		]},
		"head": {"rotation": [
			(0.0, (22, -38, -32)), (0.42, (16, 30, 24)),
			(0.80, (-20, -14, 12)), (1.18, (-10, 6, 0)), (1.5, Z),
		]},
		"jaw": {"rotation": [
			(0.0, (32, 0, 12)), (0.42, (26, 0, -10)),
			(0.80, (-6, 0, 0)), (1.18, (28, 0, 0)), (1.5, Z),
		]},
		"right_arm": {"rotation": [
			(0.0, (-64, -36, 58)), (0.38, (-48, 22, 42)),
			(0.74, (-22, -12, 14)), (1.10, (12, 0, 8)), (1.5, Z),
		]},
		"left_arm": {"rotation": [
			(0.0, (48, 30, -64)), (0.38, (-54, -24, -38)),
			(0.74, (-26, 10, -14)), (1.10, (10, 0, -8)), (1.5, Z),
		]},
		**mirror_rot("forearm", [(0.0, (-48, 0, 20)), (0.45, (14, 0, -12)), (0.85, (-28, 0, 0)), (1.5, Z)]),
		**mirror_rot("leg", [(0.0, (-46, 18, 34)), (0.45, (-34, -10, 18)), (0.85, (-16, 0, 6)), (1.5, Z)]),
		**mirror_rot("shin", [(0.0, (44, 0, 0)), (0.50, (28, 0, 0)), (0.95, (10, 0, 0)), (1.5, Z)]),
	})

	out = [idle, walk, run, attack, lunge, feed, knit_back]
	if leader:
		roar = make_anim(name, "roar", 1.0, "once", {
			"chest": {"rotation": [(0.0, Z), (0.22, (12, 0, 0)), (0.48, (-22, 0, 0)), (0.78, (-18, 0, 0)), (1.0, Z)]},
			"neck": {"rotation": [(0.0, Z), (0.22, (10, 0, 0)), (0.48, (-24, 0, 0)), (0.78, (-20, 0, 0)), (1.0, Z)]},
			"head": {"rotation": [(0.0, Z), (0.22, (8, 0, 0)), (0.48, (-20, -6, 0)), (0.78, (-18, 6, 0)), (1.0, Z)]},
			"jaw": {"rotation": [(0.0, Z), (0.22, (6, 0, 0)), (0.45, (38, 0, 0)), (0.78, (34, 0, 0)), (1.0, Z)]},
			"spines": {"rotation": [(0.0, Z), (0.45, (-14, 0, 0)), (0.78, (-12, 0, 0)), (1.0, Z)]},
			**mirror_rot("arm", [(0.0, Z), (0.22, (-26, 0, 10)), (0.48, (22, 12, 36)), (0.78, (18, 10, 32)), (1.0, Z)]),
		})
		out.append(roar)
	return out


def build_gnawers():
	sheet, uv = make_gnawer_sheet()
	gnawer_rig = _build_gnawer_rig("grave_gnawer", sheet, uv, scale=1.0, leader=False)
	gnawer_anims = build_gnawer_animations("grave_gnawer", scale=1.0, leader=False)

	leader_rig = _build_gnawer_rig("grave_gnawer_leader", sheet, uv, scale=1.24, leader=True)
	leader_anims = build_gnawer_animations("grave_gnawer_leader", scale=1.24, leader=True)
	return (gnawer_rig, gnawer_anims), (leader_rig, leader_anims)


# =====================================================================
# B5: BLOATED DEAD & BLOATED DEAD SPINED (shared 64x64 UV sheet)
# =====================================================================

def make_bloated_sheet() -> tuple[IndexedSheet, dict]:
	sheet = IndexedSheet(64, 64)
	uv = {
		"head": sheet.alloc_box("head", 7, 7, 7, "hide", detail_fn=paint_feral_skull(eye_row=2)),
		"jaw": sheet.alloc_box("jaw", 5, 2, 5, "hide", detail_fn=paint_jaw_maw),
		"neck": sheet.alloc_box("neck", 4, 3, 4, "sinew", hi=4, mid=3, side=2, back=2, lo=1, dark=1),
		"spine_core": sheet.alloc_box("spine_core", 4, 9, 4, "sinew", detail_fn=paint_ribbed_torso),
		"chest": sheet.alloc_box("chest", 9, 6, 6, "hide", detail_fn=paint_ribbed_torso),
		"belly": sheet.alloc_box("belly", 11, 9, 9, "sinew", detail_fn=paint_swollen_belly, hi=5, mid=4, side=3, back=3, lo=2, dark=1),
		"boil": sheet.alloc_box("boil", 4, 4, 3, "sinew", detail_fn=paint_swollen_belly, hi=5, mid=4, side=3, back=3, lo=2, dark=1),
		"waist": sheet.alloc_box("waist", 8, 3, 6, "filth", hi=4, mid=3, side=2, back=2, lo=1, dark=1),
		"rib": sheet.alloc_box("rib", 1, 2, 5, "bone", hi=6, mid=5, side=4, back=4, lo=3, dark=2),
		"arm": sheet.alloc_box("arm", 3, 7, 3, "hide", hi=5, mid=4, side=3, back=3, lo=2, dark=1),
		"forearm": sheet.alloc_box("forearm", 3, 7, 3, "hide", detail_fn=paint_filth_caked("hide")),
		"hand": sheet.alloc_box("hand", 3, 3, 2, "filth", hi=4, mid=3, side=2, back=2, lo=1, dark=1),
		"leg": sheet.alloc_box("leg", 3, 6, 3, "hide", hi=4, mid=3, side=3, back=2, lo=2, dark=1),
		"shin": sheet.alloc_box("shin", 3, 6, 3, "hide", detail_fn=paint_filth_caked("hide")),
		"foot": sheet.alloc_box("foot", 3, 2, 5, "filth", hi=4, mid=3, side=2, back=2, lo=1, dark=1),
		"spine_long": sheet.alloc_box("spine_long", 1, 6, 1, "bone", detail_fn=paint_claw_tip),
		"spine_short": sheet.alloc_box("spine_short", 1, 4, 1, "bone", detail_fn=paint_claw_tip),
	}
	return sheet, uv


def _build_bloated_rig(name: str, sheet: IndexedSheet, uv: dict, spined: bool) -> RigBuilder:
	b = RigBuilder(name, sheet)
	b.bone("root", None, (0, 0, 0))

	# Wide-splayed, bowed corpse legs buckling under the massive swollen gut
	hip_y = 11.5
	b.bone("waist", "root", (0, hip_y, 0.5))
	b.cube("waist", "pelvis", (-4.2, hip_y - 1.5, -2.5), (4.2, hip_y + 1.5, 3.2), uv["waist"])

	for side, sx, mir in (("right", 1, False), ("left", -1, True)):
		lx = 2.9 * sx
		b.bone(f"{side}_leg", "waist", (lx, hip_y - 0.5, 0.5), (10, 0, 8 * sx))
		b.cube(f"{side}_leg", "thigh", (lx - 1.5, 5.5, -1.1), (lx + 1.5, hip_y, 2.1), uv["leg"], mirror=mir)

		b.bone(f"{side}_shin", f"{side}_leg", (lx, 5.8, 0.5), (-16, 0, -8 * sx))
		b.cube(f"{side}_shin", "calf", (lx - 1.4, 1.5, -1.0), (lx + 1.4, 6.0, 2.0), uv["shin"], mirror=mir)

		b.bone(f"{side}_foot", f"{side}_shin", (lx, 1.6, 0.5), (6, 0, 0))
		b.cube(f"{side}_foot", "sole", (lx - 1.6, 0.0, -3.2), (lx + 1.6, 1.7, 1.8), uv["foot"], mirror=mir)

	# Torso & ENORMOUS distended swollen gut-sac (visibly protruding & lumpy, scales up in swell & burst!)
	b.bone("body", "waist", (0, 12.6, 0.5))
	# Inner spinal/viscera column bridging pelvis to ribcage so when belly ruptures in burst, a torn spine remains!
	b.cube("body", "spine_core", (-2.2, 12.0, -1.2), (2.2, 21.4, 2.8), uv["spine_core"])
	b.bone("belly", "body", (0, 16.5, -1.5))
	b.cube("belly", "gut_sac", (-5.6, 11.8, -6.6), (5.6, 21.2, 2.8), uv["belly"])
	# Asymmetrical gas-blister boils breaking up the silhouette from front and sides
	b.cube("belly", "boil_r", (3.8, 14.5, -5.8), (7.0, 18.8, -1.8), uv["boil"], rotation=(8, -14, -12), origin=(5.0, 16.5, -3.8))
	b.cube("belly", "boil_l", (-6.8, 13.0, -5.2), (-3.6, 17.2, -1.4), uv["boil"], mirror=True, rotation=(-6, 12, 14), origin=(-5.0, 15.0, -3.4))
	b.cube("belly", "boil_low", (-3.2, 10.6, -6.2), (2.4, 13.4, -2.6), uv["boil"], rotation=(12, 0, -6), origin=(0, 12.0, -4.4))

	if spined:
		b.bone("belly_spines", "belly", (0, 16.5, -2.0))
		belly_quills = [
			(3.4, 18.2, -6.0, (-48, -22, 26), "spine_long"),
			(-3.6, 17.6, -5.8, (-46, 24, -24), "spine_long"),
			(5.6, 15.6, -3.4, (-18, -58, 52), "spine_short"),
			(-5.8, 14.8, -3.0, (-16, 56, -50), "spine_short"),
			(1.6, 13.2, -6.2, (-62, -10, 12), "spine_short"),
			(-2.0, 19.6, -5.2, (-38, 12, -16), "spine_short"),
		]
		for qi, (qx, qy, qz, qrot, uk) in enumerate(belly_quills):
			hlen = 5.6 if uk == "spine_long" else 3.8
			b.cube("belly_spines", f"quill_{qi}", (qx - 0.5, qy, qz - 0.5), (qx + 0.5, qy + hlen, qz + 0.5), uv[uk], rotation=qrot, origin=(qx, qy, qz))

	# Split ribcage above the swollen gut, with articulated left/right rib-cages that pry open when swelling!
	chest_y = 20.8
	b.bone("chest", "body", (0, chest_y, 0.2), (6, 0, -3))
	b.cube("chest", "thorax", (-4.6, chest_y - 0.4, -2.8), (4.6, chest_y + 6.0, 3.2), uv["chest"])

	for side, sx, mir in (("right", 1, False), ("left", -1, True)):
		b.bone(f"{side}_ribs", "chest", (1.6 * sx, chest_y + 2.2, -2.4), (0, -14 * sx, 0))
		for ri, ry in enumerate((0.2, 2.0, 3.8)):
			rx0, rx1 = sorted((1.2 * sx, 4.8 * sx))
			b.cube(
				f"{side}_ribs", f"rib_{ri}",
				(rx0, chest_y + ry, -5.2 + ri * 0.3), (rx1, chest_y + ry + 1.1, -2.2),
				uv["rib"], mirror=mir, rotation=(8, -18 * sx, 6 * sx), origin=(1.6 * sx, chest_y + ry + 0.5, -2.4),
			)

	if spined:
		b.bone("back_spines", "chest", (0, chest_y + 3.0, 2.8))
		back_quills = [
			(0.0, chest_y + 4.8, 2.4, (44, 0, 0), "spine_long"),
			(1.8, chest_y + 3.2, 2.6, (52, -18, 22), "spine_long"),
			(-1.8, chest_y + 3.0, 2.6, (50, 18, -22), "spine_long"),
			(0.0, chest_y + 1.2, 2.8, (60, 0, 0), "spine_short"),
		]
		for qi, (qx, qy, qz, qrot, uk) in enumerate(back_quills):
			hlen = 6.0 if uk == "spine_long" else 4.2
			b.cube("back_spines", f"spine_{qi}", (qx - 0.55, qy, qz - 0.55), (qx + 0.55, qy + hlen, qz + 0.55), uv[uk], rotation=qrot, origin=(qx, qy, qz))

		b.bone("shoulder_spines", "chest", (0, chest_y + 5.4, 0.2))
		for side, sx, mir in (("r", 1, False), ("l", -1, True)):
			for si, (ox, oz, rz_s) in enumerate(((3.8, -0.4, 34), (4.6, 1.0, 52))):
				qx = ox * sx
				qy = chest_y + 5.2 - si * 0.5
				b.cube(
					"shoulder_spines", f"sh_{side}_{si}",
					(qx - 0.5, qy, oz - 0.5), (qx + 0.5, qy + 5.2, oz + 0.5),
					uv["spine_long"], mirror=mir, rotation=(10, -12 * sx, -rz_s * sx), origin=(qx, qy, oz),
				)

	# Broken-necked lolling head & slack-jawed mouth
	neck_y = chest_y + 5.6
	b.bone("neck", "chest", (0, neck_y, -0.2), (-8, 0, 10))
	b.cube("neck", "stump", (-2.0, neck_y - 0.4, -2.0), (2.0, neck_y + 2.4, 1.8), uv["neck"])

	head_y = neck_y + 2.0
	b.bone("head", "neck", (0, head_y, -0.4), (-6, 5, 8))
	b.cube("head", "skull", (-3.5, head_y - 0.2, -4.2), (3.5, head_y + 6.6, 2.6), uv["head"])
	b.cube("head", "blister", (-3.9, head_y + 1.8, -2.8), (-1.8, head_y + 4.8, 0.4), uv["boil"])

	b.bone("jaw", "head", (0, head_y + 0.2, 0.6), (-16, 0, 4))
	b.cube("jaw", "mandible", (-2.6, head_y - 1.8, -4.3), (2.6, head_y + 0.2, 0.8), uv["jaw"])

	# Uneven dangling bloated corpse arms
	for side, sx, mir, drop in (("right", 1, False, 0.0), ("left", -1, True, -0.8)):
		ax = 5.4 * sx
		sy = chest_y + 5.0 + drop
		b.bone(f"{side}_arm", "chest", (ax, sy, 0.2), (12 if sx > 0 else 6, 0, (11 if sx > 0 else 15) * sx))
		b.cube(f"{side}_arm", "upper", (ax - 1.5, sy - 7.0, -1.3), (ax + 1.5, sy + 0.8, 1.7), uv["arm"], mirror=mir)

		ey = sy - 6.8
		b.bone(f"{side}_forearm", f"{side}_arm", (ax, ey, 0.2), (18, 0, -4 * sx))
		b.cube(f"{side}_forearm", "lower", (ax - 1.4, ey - 7.0, -1.2), (ax + 1.4, ey + 0.4, 1.6), uv["forearm"], mirror=mir)

		wy = ey - 6.8
		b.bone(f"{side}_hand", f"{side}_forearm", (ax, wy, 0.2), (8, 0, 0))
		b.cube(f"{side}_hand", "club_hand", (ax - 1.6, wy - 2.8, -1.3), (ax + 1.6, wy + 0.2, 1.4), uv["hand"], mirror=mir)
	return b


def build_bloated_animations(name: str, spined: bool) -> list[dict]:
	# 1. idle (loop): visibly pulsing swollen belly + drunken corpse sway
	idle = make_anim(name, "idle", 3.0, "loop", {
		"waist": {"rotation": closed_loop(3.0, (0.0, (0, 0, -2)), (0.5, (2, 0, 2)))},
		"belly": {
			"scale": closed_loop(3.0, (0.0, (1.0, 1.0, 1.0)), (0.5, (1.08, 1.07, 1.10))),
			"rotation": closed_loop(3.0, (0.0, Z), (0.5, (-3, 0, 2))),
		},
		"chest": {"rotation": closed_loop(3.0, (0.0, (0, 0, -2)), (0.5, (-4, 0, 3)))},
		"right_ribs": {"rotation": closed_loop(3.0, (0.0, Z), (0.5, (0, -8, 0)))},
		"left_ribs": {"rotation": closed_loop(3.0, (0.0, Z), (0.5, (0, 8, 0)))},
		"head": {"rotation": closed_loop(3.0, (0.0, (0, -4, 0)), (0.5, (5, 6, 6)))},
		"jaw": {"rotation": closed_loop(3.0, (0.0, (4, 0, 0)), (0.5, (16, 0, 0)))},
		**mirror_rot("arm", closed_loop(3.0, (0.0, (0, 0, 0)), (0.5, (-5, 0, 5)))),
	})

	# 2. walk (loop): heavy staggering shamble, swollen belly sloshing side-to-side
	walk = make_anim(name, "walk", 1.0, "loop", {
		"root": {"position": closed_loop(1.0, (0.0, (0, 0, 0)), (0.25, (0, -0.7, 0)), (0.5, (0, 0, 0)), (0.75, (0, -0.7, 0)))},
		"waist": {"rotation": closed_loop(1.0, (0.0, (4, -8, -5)), (0.5, (4, 8, 5)))},
		"belly": {
			"rotation": closed_loop(1.0, (0.0, (4, 10, 6)), (0.5, (4, -10, -6))),
			"scale": closed_loop(1.0, (0.0, (1.02, 1.0, 1.04)), (0.5, (1.07, 1.05, 1.09))),
		},
		"chest": {"rotation": closed_loop(1.0, (0.0, (8, 7, 5)), (0.5, (8, -7, -5)))},
		"head": {"rotation": closed_loop(1.0, (0.0, (4, -6, 6)), (0.5, (-2, 6, -4)))},
		"jaw": {"rotation": closed_loop(1.0, (0.0, (8, 0, 0)), (0.5, (18, 0, 0)))},
		"right_leg": {"rotation": closed_loop(1.0, (0.0, (-26, 0, 0)), (0.5, (22, 0, 0)))},
		"left_leg": {"rotation": closed_loop(1.0, (0.0, (22, 0, 0)), (0.5, (-26, 0, 0)))},
		"right_shin": {"rotation": closed_loop(1.0, (0.0, (8, 0, 0)), (0.5, (24, 0, 0)))},
		"left_shin": {"rotation": closed_loop(1.0, (0.0, (24, 0, 0)), (0.5, (8, 0, 0)))},
		"right_arm": {"rotation": closed_loop(1.0, (0.0, (18, 0, 6)), (0.5, (-24, 0, 4)))},
		"left_arm": {"rotation": closed_loop(1.0, (0.0, (-24, 0, -4)), (0.5, (18, 0, -6)))},
	})

	# 3. run (loop): frantic stumbling suicide rush, arms thrown back, gut thrust ahead!
	run = make_anim(name, "run", 0.65, "loop", {
		"root": {"position": closed_loop(0.65, (0.0, (0, 0, 0)), (0.25, (0, 0.9, 0)), (0.5, (0, 0, 0)), (0.75, (0, 0.9, 0)))},
		"waist": {"rotation": closed_loop(0.65, (0.0, (10, -10, -6)), (0.5, (10, 10, 6)))},
		"belly": {"scale": closed_loop(0.65, (0.0, (1.06, 1.05, 1.08)), (0.5, (1.16, 1.14, 1.18)))},
		"chest": {"rotation": closed_loop(0.65, (0.0, (14, 8, 4)), (0.5, (14, -8, -4)))},
		"head": {"rotation": closed_loop(0.65, (0.0, (-10, -6, 6)), (0.5, (-10, 6, -6)))},
		"jaw": {"rotation": closed_loop(0.65, (0.0, (18, 0, 0)), (0.5, (28, 0, 0)))},
		"right_leg": {"rotation": closed_loop(0.65, (0.0, (-38, 0, 0)), (0.5, (32, 0, 0)))},
		"left_leg": {"rotation": closed_loop(0.65, (0.0, (32, 0, 0)), (0.5, (-38, 0, 0)))},
		**mirror_rot("arm", closed_loop(0.65, (0.0, (-58, 0, 18)), (0.5, (-42, 0, 12)))),
	})

	# 4. attack (once): clumsy double-arm corpse slam
	attack = make_anim(name, "attack", 0.65, "once", {
		"chest": {"rotation": [(0.0, Z), (0.22, (-16, 0, 0)), (0.40, (22, 0, 0)), (0.65, Z)]},
		"belly": {"scale": [(0.0, (1, 1, 1)), (0.22, (1.14, 1.12, 1.16)), (0.40, (1.04, 1.02, 1.06)), (0.65, (1, 1, 1))]},
		"head": {"rotation": [(0.0, Z), (0.22, (-12, 0, 0)), (0.40, (14, 0, 0)), (0.65, Z)]},
		"jaw": {"rotation": [(0.0, Z), (0.22, (28, 0, 0)), (0.40, (-4, 0, 0)), (0.65, Z)]},
		**mirror_rot("arm", [(0.0, Z), (0.22, (-96, 12, 24)), (0.40, (-22, -14, -8)), (0.65, Z)]),
		**mirror_rot("forearm", [(0.0, Z), (0.22, (-20, 0, 0)), (0.40, (8, 0, 0)), (0.65, Z)]),
	})

	# 5. swell (once, 1.2s — red tell!): violent convulsing inflation of belly & chest, ribs prying wide open!
	swell_tracks = {
		"root": {"position": [(0.0, Z), (0.3, (0, -0.8, 0.2)), (0.6, (0, -1.4, 0.4)), (0.9, (0, -1.8, 0.5)), (1.2, (0, -2.0, 0.6))]},
		"waist": {"rotation": [(0.0, Z), (0.3, (-6, 5, -3)), (0.6, (-10, -6, 4)), (0.9, (-14, 6, -4)), (1.2, (-16, 0, 0))]},
		"belly": {
			"scale": [(0.0, (1.0, 1.0, 1.0)), (0.35, (1.16, 1.14, 1.18)), (0.7, (1.28, 1.25, 1.32)), (1.0, (1.38, 1.35, 1.44)), (1.2, (1.42, 1.38, 1.48))],
			"rotation": [(0.0, Z), (0.4, (-4, 4, -3)), (0.8, (-8, -4, 3)), (1.2, (-10, 0, 0))],
		},
		"chest": {
			"scale": [(0.0, (1.0, 1.0, 1.0)), (0.7, (1.10, 1.08, 1.12)), (1.2, (1.18, 1.15, 1.20))],
			"rotation": [(0.0, Z), (0.5, (-10, 5, 0)), (0.9, (-16, -5, 0)), (1.2, (-20, 0, 0))],
		},
		"right_ribs": {"rotation": [(0.0, Z), (0.6, (0, -18, 6)), (1.2, (0, -32, 10))]},
		"left_ribs": {"rotation": [(0.0, Z), (0.6, (0, 18, -6)), (1.2, (0, 32, -10))]},
		"head": {"rotation": [(0.0, Z), (0.5, (-14, -8, 8)), (0.9, (-24, 8, -6)), (1.2, (-28, 0, 6))]},
		"jaw": {"rotation": [(0.0, Z), (0.5, (20, 0, 0)), (1.2, (38, 0, 0))]},
		**mirror_rot("arm", [(0.0, Z), (0.6, (22, 10, 28)), (1.2, (36, 16, 44))]),
	}
	if spined:
		swell_tracks["back_spines"] = {"scale": [(0.0, (1, 1, 1)), (1.2, (1.18, 1.25, 1.18))]}
		swell_tracks["shoulder_spines"] = {"scale": [(0.0, (1, 1, 1)), (1.2, (1.18, 1.25, 1.18))]}
	swell = make_anim(name, "swell", 1.2, "once", swell_tracks)

	# 6. burst (once, 0.8s): peak grotesque distension (0.24s) -> explosive gore rupture & splayed rib collapse (0.42s..0.8s)!
	burst_tracks = {
		"root": {"position": [(0.0, (0, -1.6, 0.4)), (0.24, (0, -2.4, 0.8)), (0.40, (0, 1.8, 1.4)), (0.65, (0, -6.5, 2.4)), (0.8, (0, -8.2, 2.8))]},
		"waist": {"rotation": [(0.0, (-12, 0, 0)), (0.24, (-18, 0, 0)), (0.40, (-34, 0, 0)), (0.8, (-52, 0, 8))]},
		"belly": {
			"scale": [(0.0, (1.25, 1.22, 1.28)), (0.24, (1.58, 1.50, 1.64)), (0.36, (0.48, 0.36, 0.44)), (0.8, (0.38, 0.28, 0.35))],
		},
		"chest": {
			"scale": [(0.0, (1.12, 1.10, 1.14)), (0.24, (1.24, 1.20, 1.26)), (0.40, (1.02, 0.95, 1.0)), (0.8, (1.0, 0.92, 1.0))],
			"rotation": [(0.0, (-14, 0, 0)), (0.24, (-24, 0, 0)), (0.40, (-48, 0, -6)), (0.8, (-38, 0, -8))],
		},
		"right_ribs": {"rotation": [(0.0, (0, -20, 6)), (0.24, (0, -34, 12)), (0.38, (12, -58, 22)), (0.8, (10, -52, 18))]},
		"left_ribs": {"rotation": [(0.0, (0, 20, -6)), (0.24, (0, 34, -12)), (0.38, (12, 58, -22)), (0.8, (10, 52, -18))]},
		"head": {"rotation": [(0.0, (-18, 0, 0)), (0.24, (-30, 0, 0)), (0.40, (-52, 10, 12)), (0.8, (-42, 14, 16))]},
		"jaw": {"rotation": [(0.0, (28, 0, 0)), (0.24, (42, 0, 0)), (0.40, (48, 0, 0)), (0.8, (36, 0, 0))]},
		**mirror_rot("arm", [(0.0, (28, 12, 34)), (0.24, (40, 18, 48)), (0.40, (64, 28, 76)), (0.8, (48, 18, 62))]),
		**mirror_rot("leg", [(0.0, Z), (0.24, (-16, 0, 8)), (0.42, (-42, 0, 18)), (0.8, (-58, 0, 22))]),
	}
	if spined:
		burst_tracks["belly_spines"] = {"scale": [(0.0, (1.1, 1.15, 1.1)), (0.24, (1.35, 1.45, 1.35)), (0.38, (0.2, 0.2, 0.2)), (0.8, (0.1, 0.1, 0.1))]}
		burst_tracks["back_spines"] = {"scale": [(0.0, (1.1, 1.15, 1.1)), (0.24, (1.30, 1.40, 1.30)), (0.40, (0.6, 0.5, 0.6)), (0.8, (0.5, 0.4, 0.5))]}
	burst = make_anim(name, "burst", 0.8, "once", burst_tracks)

	return [idle, walk, run, attack, swell, burst]


def build_bloated():
	sheet, uv = make_bloated_sheet()
	base_rig = _build_bloated_rig("bloated_dead", sheet, uv, spined=False)
	base_anims = build_bloated_animations("bloated_dead", spined=False)

	spined_rig = _build_bloated_rig("bloated_dead_spined", sheet, uv, spined=True)
	spined_anims = build_bloated_animations("bloated_dead_spined", spined=True)
	return (base_rig, base_anims), (spined_rig, spined_anims)
