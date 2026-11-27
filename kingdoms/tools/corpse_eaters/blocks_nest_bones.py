"""Rows F1 (corpse_nest) and F2 (old_skeleton & fallen_warrior, with optional blade).
All cube rotations use single-axis angles in {-45, -22.5, 0, 22.5, 45} and bones rest at (0,0,0),
so the exact same rig exports cleanly to Blockbench .bbmodel, GeckoLib (entity + block), AND vanilla models/block/*.json!"""
from __future__ import annotations

from .common import (
	IndexedSheet,
	RigBuilder,
	Z,
	closed_loop,
	make_anim,
	paint_claw_tip,
	paint_hollow_skull,
	paint_mound_filth,
	paint_rusted_armour,
)


# =====================================================================
# F1: CORPSE_NEST — organic mound of bones, skulls, ribs & grave-filth
# =====================================================================

def _paint_vent_mouth(sheet: IndexedSheet, rects):
	"""Deep dark recessed burrow vent with visceral sinew rim around a pitch-dark interior."""
	x0, y0, x1, y1 = rects["north"]
	w, h = x1 - x0, y1 - y0
	for ly in range(h):
		for lx in range(w):
			rim = lx in (0, w - 1) or ly == 0
			sheet.put(x0 + lx, y0 + ly, "sinew" if rim else "hide", 3 if rim else 0)


def _paint_visored_helm(sheet: IndexedSheet, rects):
	"""Ancient knight barbute/nasal helm: steel brow & cheekplates with a dark T-visor slit in front."""
	paint_rusted_armour(sheet, rects)
	x0, y0, x1, y1 = rects["north"]
	w, h = x1 - x0, y1 - y0
	if w >= 6 and h >= 5:
		for lx in range(1, w - 1):
			sheet.put(x0 + lx, y0 + 2, "hide", 0)
		for ly in range(2, h):
			sheet.put(x0 + w // 2, y0 + ly, "hide", 0)


def build_corpse_nest():
	name = "corpse_nest"
	sheet = IndexedSheet(64, 64)
	uv = {
		"base_slab": sheet.alloc_box("base_slab", 14, 3, 14, "filth", detail_fn=paint_mound_filth),
		"lobe": sheet.alloc_box("lobe", 6, 3, 6, "filth", detail_fn=paint_mound_filth),
		"mid_mound": sheet.alloc_box("mid_mound", 11, 4, 11, "filth", detail_fn=paint_mound_filth),
		"crown": sheet.alloc_box("crown", 8, 3, 8, "hide", detail_fn=paint_mound_filth),
		"vent": sheet.alloc_box("vent", 5, 4, 4, "sinew", detail_fn=_paint_vent_mouth, hi=3, mid=2, side=2, back=1, lo=1, dark=0),
		"eye_glint": sheet.alloc_box("eye_glint", 1, 1, 1, "eyes", hi=6, mid=6, side=5, back=5, lo=4, dark=3),
		"skull": sheet.alloc_box("skull", 4, 4, 4, "bone", detail_fn=paint_hollow_skull),
		"rib": sheet.alloc_box("rib", 1, 5, 2, "bone", detail_fn=paint_claw_tip),
		"femur": sheet.alloc_box("femur", 2, 2, 7, "bone", hi=6, mid=5, side=4, back=4, lo=3, dark=2),
		"spine_col": sheet.alloc_box("spine_col", 2, 6, 2, "bone", hi=5, mid=4, side=4, back=3, lo=2, dark=1),
		"hoard_book": sheet.alloc_box("hoard_book", 4, 1, 5, "hide", hi=4, mid=3, side=3, back=2, lo=2, dark=1),
		"hoard_gilt": sheet.alloc_box("hoard_gilt", 4, 1, 1, "eyes", hi=5, mid=4, side=4, back=3, lo=3, dark=2),
	}

	b = RigBuilder(name, sheet)
	b.bone("root", None, (0, 0, 0))

	# Organic stepped charnel mound base (fills [-8..8] footprint unevenly, never a plain cube!)
	b.bone("mound_base", "root", (0, 0, 0))
	b.cube("mound_base", "core_apron", (-7.0, 0.0, -7.0), (7.0, 3.2, 7.0), uv["base_slab"])
	b.cube("mound_base", "lobe_nw", (-7.8, 0.0, -7.8), (-1.8, 2.4, -1.8), uv["lobe"])
	b.cube("mound_base", "lobe_se", (1.6, 0.0, 1.8), (7.8, 2.6, 7.8), uv["lobe"])
	b.cube("mound_base", "lobe_ne", (2.0, 0.0, -7.6), (7.6, 2.0, -2.0), uv["lobe"])
	b.cube("mound_base", "lobe_sw", (-7.6, 0.0, 2.0), (-1.8, 2.2, 7.6), uv["lobe"])

	# Upper heaving mound & dark sinew-lined breeding burrow vent
	b.bone("mound_body", "mound_base", (0, 3.0, 0))
	b.cube("mound_body", "heap_mid", (-5.6, 2.8, -5.2), (5.6, 7.2, 5.8), uv["mid_mound"])
	b.cube("mound_body", "hump_back", (-4.2, 6.6, -2.2), (4.0, 10.2, 5.2), uv["crown"])
	b.cube("mound_body", "hump_side", (1.4, 5.8, -4.4), (5.8, 8.8, 1.4), uv["lobe"])

	b.bone("vent", "mound_body", (0, 5.5, -2.5))
	b.cube("vent", "maw_hollow", (-2.6, 3.2, -6.0), (2.6, 6.8, -1.8), uv["vent"])
	# Faint hungry eyes glinting inside the dark breeding vent
	b.cube("vent", "glint_r", (0.7, 4.6, -5.2), (1.5, 5.3, -4.4), uv["eye_glint"])
	b.cube("vent", "glint_l", (-1.5, 4.4, -5.0), (-0.7, 5.1, -4.2), uv["eye_glint"])

	# Curved rib-arches framing the vent (pry apart when breeding/spawning!)
	b.bone("rib_arch_right", "mound_body", (3.0, 6.2, -2.8))
	for ri, rz in enumerate((-5.0, -3.2, -1.4)):
		b.cube(
			"rib_arch_right", f"rib_{ri}",
			(2.2, 5.8, rz), (3.4, 11.2 - ri * 0.4, rz + 1.1),
			uv["rib"], rotation=(0, 0, 22.5), origin=(2.8, 6.0, rz + 0.5),
		)

	b.bone("rib_arch_left", "mound_body", (-3.0, 6.2, -2.8))
	for ri, rz in enumerate((-5.0, -3.2, -1.4)):
		b.cube(
			"rib_arch_left", f"rib_{ri}",
			(-3.4, 5.8, rz), (-2.2, 11.2 - ri * 0.4, rz + 1.1),
			uv["rib"], mirror=True, rotation=(0, 0, -22.5), origin=(-2.8, 6.0, rz + 0.5),
		)

	# Skulls, femurs & spinal column jutting at crisp single-axis angles
	b.bone("skulls", "mound_body", (0, 8.5, 1.0))
	b.cube(
		"skulls", "skull_crown",
		(-2.2, 9.4, -0.8), (1.8, 13.4, 3.2),
		uv["skull"], rotation=(-22.5, 0, 0), origin=(0.0, 10.2, 1.2),
	)
	b.cube(
		"skulls", "skull_west",
		(-6.4, 4.8, -1.6), (-2.4, 8.8, 2.4),
		uv["skull"], rotation=(0, 0, 22.5), origin=(-4.4, 5.8, 0.4),
	)
	b.cube(
		"skulls", "skull_east",
		(2.4, 2.4, -6.4), (6.2, 6.2, -2.6),
		uv["skull"], rotation=(0, -22.5, 0), origin=(4.3, 4.0, -4.5),
	)

	b.bone("bones_jut", "mound_base", (0, 4.0, 0))
	b.cube(
		"bones_jut", "spine_pike",
		(0.8, 7.6, 1.8), (2.6, 14.2, 3.6),
		uv["spine_col"], rotation=(22.5, 0, 0), origin=(1.7, 8.0, 2.7),
	)
	b.cube(
		"bones_jut", "femur_sw",
		(-6.8, 1.8, 1.2), (-5.0, 3.4, 7.8),
		uv["femur"], rotation=(22.5, 0, 0), origin=(-5.9, 2.4, 3.0),
	)
	b.cube(
		"bones_jut", "femur_ne",
		(3.2, 5.6, -5.8), (4.8, 7.2, 1.0),
		uv["femur"], rotation=(-22.5, 0, 0), origin=(4.0, 6.2, -2.0),
	)
	b.cube(
		"bones_jut", "femur_cross",
		(-4.8, 1.4, -7.4), (2.2, 3.0, -5.8),
		uv["femur"], rotation=(0, 22.5, 0), origin=(-1.2, 2.2, -6.6),
	)

	# Half-buried tome/hoard corner tucked partly inside the mound under the ribs
	b.bone("hoard", "mound_body", (-3.0, 3.2, 2.8))
	b.cube(
		"hoard", "tome_cover",
		(-4.8, 2.6, 1.4), (-1.4, 3.8, 5.4),
		uv["hoard_book"], rotation=(0, -22.5, 0), origin=(-3.1, 3.2, 3.4),
	)
	b.cube(
		"hoard", "tome_clasp",
		(-4.6, 2.7, 4.6), (-1.8, 3.8, 5.5),
		uv["hoard_gilt"], rotation=(0, -22.5, 0), origin=(-3.1, 3.2, 3.4),
	)

	idle = make_anim(name, "idle", 3.6, "loop", {
		"mound_body": {"scale": closed_loop(3.6, (0.0, (1.0, 1.0, 1.0)), (0.5, (1.03, 1.05, 1.03)))},
		"rib_arch_right": {"rotation": closed_loop(3.6, (0.0, Z), (0.5, (0, 0, 6)))},
		"rib_arch_left": {"rotation": closed_loop(3.6, (0.0, Z), (0.5, (0, 0, -6)))},
	})
	spawn = make_anim(name, "spawn", 1.1, "once", {
		"mound_body": {
			"scale": [(0.0, (1, 1, 1)), (0.28, (1.08, 0.94, 1.08)), (0.55, (1.12, 1.16, 1.12)), (0.85, (0.98, 1.02, 0.98)), (1.1, (1, 1, 1))],
			"rotation": [(0.0, Z), (0.28, (3, 0, -3)), (0.55, (-4, 0, 3)), (1.1, Z)],
		},
		"rib_arch_right": {"rotation": [(0.0, Z), (0.28, (0, 0, -4)), (0.55, (0, 0, 26)), (0.85, (0, 0, 10)), (1.1, Z)]},
		"rib_arch_left": {"rotation": [(0.0, Z), (0.28, (0, 0, 4)), (0.55, (0, 0, -26)), (0.85, (0, 0, -10)), (1.1, Z)]},
		"skulls": {"rotation": [(0.0, Z), (0.35, (-6, 5, -4)), (0.62, (8, -6, 5)), (1.1, Z)]},
	})
	hit = make_anim(name, "hit", 0.4, "once", {
		"mound_body": {"rotation": [(0.0, Z), (0.10, (4, 0, -5)), (0.22, (-3, 0, 4)), (0.4, Z)]},
		"skulls": {"rotation": [(0.0, Z), (0.12, (-8, 6, 6)), (0.26, (5, -4, -4)), (0.4, Z)]},
	})
	return b, [idle, spawn, hit]


# =====================================================================
# F2a: OLD_SKELETON — slumped cave skeleton, optional ancient blade
# =====================================================================

def build_old_skeleton():
	name = "old_skeleton"
	sheet = IndexedSheet(64, 64)
	uv = {
		"skull": sheet.alloc_box("skull", 6, 6, 6, "bone", detail_fn=paint_hollow_skull),
		"jaw": sheet.alloc_box("jaw", 5, 2, 5, "bone", hi=5, mid=4, side=3, back=3, lo=2, dark=1),
		"spine": sheet.alloc_box("spine", 2, 8, 2, "bone", hi=5, mid=4, side=4, back=3, lo=2, dark=1),
		"clavicle": sheet.alloc_box("clavicle", 7, 1, 2, "bone", hi=6, mid=5, side=4, back=4, lo=3, dark=2),
		"rib_bar": sheet.alloc_box("rib_bar", 3, 1, 4, "bone", hi=6, mid=5, side=4, back=4, lo=3, dark=2),
		"pelvis": sheet.alloc_box("pelvis", 6, 3, 4, "bone", hi=5, mid=4, side=3, back=3, lo=2, dark=1),
		"limb": sheet.alloc_box("limb", 2, 7, 2, "bone", hi=5, mid=4, side=4, back=3, lo=2, dark=1),
		"foot": sheet.alloc_box("foot", 2, 1, 4, "bone", hi=5, mid=4, side=3, back=3, lo=2, dark=1),
		"rubble": sheet.alloc_box("rubble", 5, 2, 4, "filth", detail_fn=paint_mound_filth),
		"blade": sheet.alloc_box("blade", 1, 11, 2, "sinew", detail_fn=paint_rusted_armour),
		"guard": sheet.alloc_box("guard", 5, 1, 2, "eyes", hi=5, mid=4, side=3, back=3, lo=2, dark=1),
		"grip": sheet.alloc_box("grip", 1, 4, 1, "filth", hi=5, mid=4, side=3, back=3, lo=2, dark=1),
	}

	b = RigBuilder(name, sheet)
	b.bone("root", None, (0, 0, 0))

	# Cave dust & stone rubble apron beneath the slumped skeleton
	b.bone("rubble", "root", (0, 0, 0))
	b.cube("rubble", "dust_back", (-5.0, 0.0, 0.5), (5.2, 1.4, 6.8), uv["rubble"])
	b.cube("rubble", "stone_prop", (-3.8, 0.0, 3.6), (3.6, 4.2, 7.2), uv["rubble"])

	# Pelvis seated on the cave floor
	b.bone("pelvis", "root", (0, 2.2, 1.8))
	b.cube("pelvis", "hip_bowl", (-3.2, 0.8, 0.2), (3.2, 3.4, 4.0), uv["pelvis"])

	# Hollow open ribcage slumped backward (+22.5 deg x tilts top backward toward +z against the stone prop!)
	b.bone("chest", "pelvis", (0, 3.2, 2.4))
	sp_o = (0, 3.2, 2.6)
	b.cube("chest", "vertebral_column", (-1.0, 3.0, 2.0), (1.0, 10.6, 3.8), uv["spine"], rotation=(22.5, 0, 0), origin=sp_o)
	b.cube("chest", "clavicles", (-3.8, 9.4, 0.6), (3.8, 10.6, 2.8), uv["clavicle"], rotation=(22.5, 0, 0), origin=sp_o)
	for ri, ry in enumerate((4.6, 6.4, 8.2)):
		for side, sx, mir in (("r", 1, False), ("l", -1, True)):
			x0, x1 = sorted((0.9 * sx, 3.6 * sx))
			b.cube("chest", f"rib_{side}_{ri}", (x0, ry, -0.8), (x1, ry + 1.0, 2.8), uv["rib_bar"], mirror=mir, rotation=(22.5, 0, 0), origin=sp_o)

	# Slumped skull bowed forward onto the collarbones (-22.5 deg x pitches cranium forward/down!)
	b.bone("head", "chest", (0, 10.6, 4.8))
	hd_o = (0, 10.6, 4.8)
	b.cube("head", "cranium", (-3.0, 9.8, 0.2), (3.0, 15.6, 6.0), uv["skull"], rotation=(-22.5, 0, 0), origin=hd_o)
	b.bone("jaw", "head", (0, 10.0, 4.2))
	b.cube("jaw", "mandible", (-2.4, 8.6, 0.6), (2.4, 10.2, 4.8), uv["jaw"])

	# Drawn-up skeletal legs (femurs angled up to bent knees, shins angled down to skeletal feet flat on y>=0)
	b.bone("right_leg", "pelvis", (2.1, 2.2, 1.2))
	b.cube("right_leg", "femur", (1.2, 1.6, -4.6), (3.0, 3.4, 1.6), uv["limb"], rotation=(-22.5, 0, 0), origin=(2.1, 2.2, 1.2))
	b.bone("right_shin", "right_leg", (2.1, 4.6, -4.0))
	b.cube("right_shin", "tibia", (1.3, 0.8, -5.0), (2.9, 4.9, -3.2), uv["limb"], rotation=(22.5, 0, 0), origin=(2.1, 4.6, -4.0))
	b.cube("right_shin", "foot", (1.1, 0.0, -7.6), (3.1, 1.2, -4.0), uv["foot"])

	b.bone("left_leg", "pelvis", (-2.2, 2.0, 1.2))
	b.cube("left_leg", "femur", (-3.2, 1.0, -5.0), (-1.4, 2.8, 1.6), uv["limb"], mirror=True, rotation=(0, 22.5, 0), origin=(-2.2, 2.0, 1.2))
	b.bone("left_shin", "left_leg", (-3.4, 1.8, -4.4))
	b.cube("left_shin", "tibia", (-4.4, 0.4, -7.2), (-2.6, 2.0, -2.0), uv["limb"], mirror=True)
	b.cube("left_shin", "foot", (-4.6, 0.0, -7.8), (-2.4, 1.2, -4.8), uv["foot"], mirror=True)

	# Skeletal arms: right arm resting forward toward the blade hilt, left arm slumped beside the hip
	b.bone("right_arm", "chest", (4.0, 9.4, 4.2))
	b.cube("right_arm", "humerus", (3.2, 3.6, 3.2), (4.8, 9.8, 4.8), uv["limb"], rotation=(-22.5, 0, 0), origin=(4.0, 9.4, 4.2))
	b.bone("right_forearm", "right_arm", (4.0, 4.2, 1.8))
	b.cube("right_forearm", "ulna", (3.2, 3.4, -3.4), (4.8, 5.0, 2.2), uv["limb"], rotation=(0, 22.5, 0), origin=(4.0, 4.2, 1.8))

	b.bone("left_arm", "chest", (-4.0, 9.4, 4.2))
	b.cube("left_arm", "humerus", (-5.0, 3.2, 3.2), (-3.4, 9.8, 4.8), uv["limb"], mirror=True, rotation=(0, 0, -22.5), origin=(-4.0, 9.4, 4.2))
	b.bone("left_forearm", "left_arm", (-5.8, 3.6, 4.0))
	b.cube("left_forearm", "ulna", (-6.6, 1.2, -0.8), (-5.0, 2.8, 4.4), uv["limb"], mirror=True, rotation=(22.5, 0, 0), origin=(-5.8, 2.4, 4.0))

	# Optional ancient notched iron blade planted/leaning in the right skeletal hand!
	b.bone("blade", "root", (1.6, 5.0, -3.2))
	bl_o = (1.6, 1.2, -3.2)
	b.cube("blade", "steel_blade", (1.1, 0.8, -4.2), (2.1, 10.6, -2.2), uv["blade"], rotation=(0, 0, -22.5), origin=bl_o)
	b.cube("blade", "crossguard", (-0.8, 10.2, -4.1), (4.0, 11.3, -2.3), uv["guard"], rotation=(0, 0, -22.5), origin=bl_o)
	b.cube("blade", "hilt_grip", (1.0, 11.2, -3.8), (2.2, 14.6, -2.6), uv["grip"], rotation=(0, 0, -22.5), origin=bl_o)
	b.cube("blade", "pommel", (0.7, 14.4, -4.0), (2.5, 15.6, -2.4), uv["guard"], rotation=(0, 0, -22.5), origin=bl_o)

	idle = make_anim(name, "idle", 2.0, "loop", {
		"head": {"rotation": closed_loop(2.0, (0.0, Z), (0.5, Z))},
	})
	disturb = make_anim(name, "disturb", 0.55, "once", {
		"chest": {"rotation": [(0.0, Z), (0.12, (-4, 0, 3)), (0.28, (3, 0, -2)), (0.55, Z)]},
		"head": {"rotation": [(0.0, Z), (0.12, (8, -6, 5)), (0.28, (-5, 4, -4)), (0.55, Z)]},
		"jaw": {"rotation": [(0.0, Z), (0.14, (14, 0, 0)), (0.30, (-2, 0, 0)), (0.55, Z)]},
		"blade": {"rotation": [(0.0, Z), (0.14, (0, 0, 5)), (0.32, (0, 0, -3)), (0.55, Z)]},
	})
	return b, [idle, disturb]


# =====================================================================
# F2b: FALLEN_WARRIOR — kneeling armored ancient warrior, optional blade
# =====================================================================

def build_fallen_warrior():
	name = "fallen_warrior"
	sheet = IndexedSheet(64, 64)
	uv = {
		"skull": sheet.alloc_box("skull", 6, 6, 6, "bone", detail_fn=paint_hollow_skull),
		"helm": sheet.alloc_box("helm", 7, 6, 7, "sinew", detail_fn=_paint_visored_helm),
		"helm_crest": sheet.alloc_box("helm_crest", 2, 2, 7, "eyes", hi=5, mid=4, side=3, back=3, lo=2, dark=1),
		"cuirass": sheet.alloc_box("cuirass", 8, 8, 5, "sinew", detail_fn=paint_rusted_armour),
		"surcoat": sheet.alloc_box("surcoat", 8, 4, 5, "hide", hi=4, mid=3, side=3, back=2, lo=2, dark=1),
		"pauldron": sheet.alloc_box("pauldron", 4, 4, 5, "sinew", detail_fn=paint_rusted_armour),
		"limb_bone": sheet.alloc_box("limb_bone", 2, 6, 2, "bone", hi=5, mid=4, side=4, back=3, lo=2, dark=1),
		"gauntlet": sheet.alloc_box("gauntlet", 3, 6, 3, "sinew", detail_fn=paint_rusted_armour),
		"greave": sheet.alloc_box("greave", 3, 3, 6, "sinew", detail_fn=paint_rusted_armour),
		"rubble": sheet.alloc_box("rubble", 6, 2, 5, "filth", detail_fn=paint_mound_filth),
		"blade": sheet.alloc_box("blade", 1, 11, 3, "sinew", detail_fn=paint_rusted_armour),
		"guard": sheet.alloc_box("guard", 6, 1, 2, "eyes", hi=6, mid=5, side=4, back=4, lo=3, dark=2),
		"grip": sheet.alloc_box("grip", 1, 4, 2, "filth", hi=5, mid=4, side=3, back=3, lo=2, dark=1),
	}

	b = RigBuilder(name, sheet)
	b.bone("root", None, (0, 0, 0))

	b.bone("rubble", "root", (0, 0, 0))
	b.cube("rubble", "earth_pad", (-5.6, 0.0, -4.8), (5.6, 1.2, 5.8), uv["rubble"])

	# Kneeling warrior: shins folded back on the cave floor, torso slumped forward in a last stand!
	b.bone("pelvis", "root", (0, 4.2, 0.8))
	b.cube("pelvis", "tattered_fauld", (-4.0, 2.4, -1.6), (4.0, 5.8, 3.2), uv["surcoat"])

	for side, sx, mir in (("right", 1, False), ("left", -1, True)):
		lx = 2.4 * sx
		b.bone(f"{side}_leg", "pelvis", (lx, 4.0, 0.8))
		b.cube(f"{side}_leg", "thigh", (lx - 1.2, 1.2, -1.8), (lx + 1.2, 4.6, 0.6), uv["limb_bone"], mirror=mir, rotation=(-22.5, 0, 0), origin=(lx, 4.0, 0.2))
		b.cube(f"{side}_leg", "shin_greave", (lx - 1.5, 0.2, -1.8), (lx + 1.5, 2.8, 4.8), uv["greave"], mirror=mir)

	# Forward-bowed corroded steel cuirass (-22.5 deg x pitches chest forward over the knees!)
	b.bone("chest", "pelvis", (0, 5.2, 0.8))
	ch_o = (0, 5.2, 0.8)
	b.cube("chest", "cuirass", (-4.2, 5.0, -2.0), (4.2, 12.6, 3.0), uv["cuirass"], rotation=(-22.5, 0, 0), origin=ch_o)
	b.cube("chest", "spine_exposed", (-1.0, 4.8, 2.4), (1.0, 12.2, 3.6), uv["limb_bone"], rotation=(-22.5, 0, 0), origin=ch_o)

	# Asymmetrical battered pauldrons
	b.bone("right_pauldron", "chest", (4.6, 11.2, -0.8))
	b.cube("right_pauldron", "plate", (3.6, 9.2, -3.4), (7.2, 13.0, 1.6), uv["pauldron"], rotation=(0, 0, -22.5), origin=(4.6, 11.2, -0.8))

	b.bone("left_pauldron", "chest", (-4.6, 10.8, -0.8))
	b.cube("left_pauldron", "plate", (-7.0, 8.8, -3.2), (-3.6, 12.4, 1.6), uv["pauldron"], mirror=True, rotation=(0, 0, 22.5), origin=(-4.6, 10.8, -0.8))

	# Bowed skull inside a dented ancient nasal/barbute helm
	b.bone("head", "chest", (0, 11.8, -1.8))
	hd_o = (0, 11.8, -1.8)
	b.cube("head", "skull", (-2.8, 10.6, -5.6), (2.8, 15.8, -0.2), uv["skull"], rotation=(-22.5, 0, 0), origin=hd_o)
	b.bone("helm", "head", (0, 13.2, -2.8))
	b.cube("helm", "casque", (-3.4, 11.4, -6.2), (3.4, 16.4, 0.4), uv["helm"], rotation=(-22.5, 0, 0), origin=hd_o)
	b.cube("helm", "comb_crest", (-0.9, 15.6, -6.0), (0.9, 17.4, 0.4), uv["helm_crest"], rotation=(-22.5, 0, 0), origin=hd_o)
	b.cube("helm", "nasal_guard", (-0.6, 10.2, -6.5), (0.6, 13.4, -5.6), uv["helm_crest"], rotation=(-22.5, 0, 0), origin=hd_o)

	# Gauntleted arms reaching forward and inward as if resting on a planted sword (or slumped over knees when bare)
	for side, sx, mir in (("right", 1, False), ("left", -1, True)):
		ax = 4.8 * sx
		b.bone(f"{side}_arm", "chest", (ax, 10.6, -1.0))
		b.cube(f"{side}_arm", "humerus", (ax - 1.0, 5.2, -2.2), (ax + 1.0, 11.0, -0.2), uv["limb_bone"], mirror=mir, rotation=(-22.5, 0, 0), origin=(ax, 10.6, -1.0))

		fx = 4.2 * sx
		b.bone(f"{side}_forearm", f"{side}_arm", (fx, 6.2, -3.2))
		x0, x1 = sorted((0.6 * sx, 4.4 * sx))
		b.cube(f"{side}_forearm", "vambrace", (x0, 5.4, -5.8), (x1, 7.8, -3.2), uv["gauntlet"], mirror=mir, rotation=(0, -22.5 * sx, 0), origin=(fx, 6.6, -4.4))

	# Optional point-planted ancient warrior greatsword/longsword driven into the stone before the knees!
	b.bone("blade", "root", (0.0, 7.0, -5.0))
	b.cube("blade", "fullered_blade", (-0.6, 0.4, -6.2), (0.6, 11.2, -3.8), uv["blade"])
	b.cube("blade", "crossguard", (-3.2, 11.0, -6.0), (3.2, 12.2, -4.0), uv["guard"])
	b.cube("blade", "leather_grip", (-0.6, 12.0, -5.6), (0.6, 15.2, -4.4), uv["grip"])
	b.cube("blade", "wheel_pommel", (-1.1, 15.0, -5.9), (1.1, 16.4, -4.1), uv["guard"])

	idle = make_anim(name, "idle", 2.0, "loop", {
		"head": {"rotation": closed_loop(2.0, (0.0, Z), (0.5, Z))},
	})
	disturb = make_anim(name, "disturb", 0.6, "once", {
		"chest": {"rotation": [(0.0, Z), (0.14, (5, 0, -3)), (0.32, (-3, 0, 2)), (0.6, Z)]},
		"head": {"rotation": [(0.0, Z), (0.14, (8, 5, -4)), (0.34, (-4, -3, 3)), (0.6, Z)]},
		"right_pauldron": {"rotation": [(0.0, Z), (0.16, (0, 0, 8)), (0.36, (0, 0, -4)), (0.6, Z)]},
		"left_pauldron": {"rotation": [(0.0, Z), (0.16, (0, 0, -8)), (0.36, (0, 0, 4)), (0.6, Z)]},
		"blade": {"rotation": [(0.0, Z), (0.14, (4, 0, -4)), (0.32, (-3, 0, 3)), (0.6, Z)]},
	})
	return b, [idle, disturb]
