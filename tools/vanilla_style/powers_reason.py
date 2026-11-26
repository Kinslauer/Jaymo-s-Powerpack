"""Reason look, Jaymo's Powers: dusts, loose parts and script-painted skins (docs/Vanilla-Style.md).

    python -I tools/vanilla_style/powers_reason.py

Run it from the repository root AFTER the generators that write these textures (gen_stone_age.py, gen_ores.py,
gen_future_*.py ... each still writes its old art). Fish icons and spawn eggs are fixed in gen_fishing_species.py itself.
- DUSTS: one pile shape, vanilla's redstone/glowstone/gunpowder pile, in each material's colour.
- CLEAN_ITEMS / CLEAN_BLOCKS: our own parts whose generator jitters or blurs; reason_clean's smoother plus a fold into a
  few ramp steps, half-transparent pixels made solid.
- SKINS: future-world, model and creature skins whose generators colour every pixel with a jitter; same clean-up.
"""
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent.parent
TEX = ROOT / "src/main/resources/assets/jaymospowers/textures"
sys.path.insert(0, str(Path(__file__).resolve().parent))
sys.path.insert(0, str(ROOT / "tools"))
import art_audit  # noqa: E402
import reason_clean  # noqa: E402

# redstone's pile, as levels 0 (darkest) to 6 (lightest) in the order of vanilla's own shades
_LEV = {"f": 0, "g": 1, "e": 2, "a": 3, "b": 4, "c": 5, "d": 6}
_REDSTONE = """
................
................
................
.......aa.......
......abca......
.....acdcef.....
....acdcbdef....
...acccdceaef...
..abdcbedbecgf..
..acbcdcbeeaef..
..fecdcbeceegf..
...fecbeaeegf...
....ffgeegff....
......ffff......
................
................
"""
PILE_ROWS = [r for r in _REDSTONE.strip("\n").split("\n")]
FACTORS = [0.34, 0.42, 0.52, 0.68, 0.84, 1.0, 1.0]

DUSTS = {  # item path under textures/item -> mid colour (the dust's own colour as the old icon had it)
	"copper_dust": 0xc8744b, "gold_dust": 0xdebf4a, "iron_dust": 0xbab4af, "tin/tin_dust": 0x969697,
	"stone_age/bronze_dust": 0x9e6533, "stone_age/clay_dust": 0x9096a6, "stone_age/flint_dust": 0x49494f,
	"stone_age/glass_dust": 0xb9d1d8, "stone_age/sawdust": 0xad8b5a, "stone_age/cane_chad": 0x94ae6c,
	"coke/ash": 0x84827f, "minerals/nitre_earth": 0x4d3724, "minerals/saltpetre": 0xc3bfb4, "minerals/sulphur": 0xb99b20,
}
# a few pale crystals on the nitre earth (level of the lightest shade)
CRYSTALS = {"minerals/nitre_earth": [(7, 5), (10, 7), (6, 10)]}


def pile(mid):
	rgb = ((mid >> 16) & 255, (mid >> 8) & 255, mid & 255)
	ramp = []
	for i, f in enumerate(FACTORS):
		c = tuple(min(255, int(v * f)) for v in rgb)
		if i == 6:
			c = tuple(min(255, int(v + (255 - v) * 0.3)) for v in rgb)
		ramp.append(c)
	img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
	for y, row in enumerate(PILE_ROWS):
		for x, ch in enumerate(row):
			if ch in _LEV:
				img.putpixel((x, y), ramp[_LEV[ch]] + (255,))
	return img


def solid(img, cutoff=128):
	img = img.convert("RGBA")
	img.putdata([p[:3] + (255,) if p[3] >= cutoff else (0, 0, 0, 0) for p in img.getdata()])
	return img


def fold(img, n, keep_alpha):
	"""smooth, then fold into n colours drawn only from the opaque pixels."""
	img = reason_clean.smooth(img.convert("RGBA"))
	px = img.load()
	w, h = img.size
	seen = [(x, y) for y in range(h) for x in range(w) if px[x, y][3] > 0]
	if not seen:
		return img
	strip = Image.new("RGB", (len(seen), 1))
	strip.putdata([px[x, y][:3] for x, y in seen])
	few = strip.quantize(n, method=Image.Quantize.MEDIANCUT).convert("RGB")
	got = [few.getpixel((i, 0)) for i in range(len(seen))]
	# palette steps that are a hair apart in brightness are one step: the most used of the pair wins
	count = {}
	for c in got:
		count[c] = count.get(c, 0) + 1
	lum = lambda c: c[0] * 0.299 + c[1] * 0.587 + c[2] * 0.114
	merge = {}
	for c in sorted(count, key=lambda k: -count[k]):
		for d in list(merge.values()):
			if abs(lum(c) - lum(d)) < 7 and max(abs(c[i] - d[i]) for i in range(3)) < 40:
				merge[c] = d
				break
		else:
			merge[c] = c
	out = img.copy()
	op = out.load()
	for i, (x, y) in enumerate(seen):
		op[x, y] = merge[got[i]] + ((px[x, y][3],) if keep_alpha else (255,))
	return out


def _files(listing):
	return [TEX / p for p in listing]


def fold_until_clean(path, img, n, keep_alpha, floor=8):
	"""fold into n colours, and into fewer while the art check still reports soft gradients or too many colours."""
	while True:
		out = fold(img, n, keep_alpha)
		out.save(path)
		if n <= floor or not art_audit.faults(path):
			return
		n = max(floor, n * 2 // 3)


def run_list(paths, n, items):
	for p in paths:
		img = Image.open(p).convert("RGBA")
		if items:
			img = solid(img)
		fold_until_clean(p, img, n, not items)


# our own parts and blocks that failed the art check (2026-11-26); each is only smoothed and folded
CLEAN_ITEMS = """
aether/moa_feather backpack band_plan_breather band_plan_cell band_plan_plating band_plan_steady
band_plan_wild bauble/bottled_fairy bauble/cracked_fairy_bottle bauble/pink_fairy boilie boneiron_plate bottle_of_cloud
costume glacia_ice_glass jarred_symbiote landing_net prosthetic/rig_arm prosthetic/rig_leg
relic_focus_stone relic_hale_coin relic_heartseed relic_last_ember relic_stride_spur relic_tide_scale relic_wind_knot
resonance_resonant_fork resonance_tuning_fork rig/scrap rustfall_plate tinker_armour/chestplate/plating_broken_queen_slime
tinker_armour/chestplate/plating_queen_slime tinker_armour/leggings/plating_queen_slime tinkering/meteoric_iron
training_hourglass web_ball web_cartridge web_symbiote_mass wind_gale_heart wish_orb_1 wish_orb_2 wish_orb_3 wish_orb_4
wish_orb_5 wish_orb_6 wish_orb_7 wreck_core armory/diamond_saber armory/golden_saber armory/iron_saber
armory/netherite_lance armory/netherite_saber armory/stone_saber armory/wooden_saber chain_axe_model
""".split()
CLEAN_BLOCKS = """
aether/upfall bonding_vat_brine chamber_door eden/sunroot_barrel_bottom farworld/field_anvil_body farworld/glacia_rock
farworld/moon_rock farworld/verdant_rock minerals/nether_sulphur_ore minerals/nitre_bed_side minerals/nitre_bed_top_0
minerals/nitre_bed_top_1 minerals/nitre_bed_top_2 minerals/sandstone_saltpetre_ore tinkering/pattern_chest_drawer_front
verdant_gate web_symbiote_red_goo
""".split()
ITEM_COLOURS, BLOCK_COLOURS, SKIN_COLOURS = 20, 48, 56


def skins():
	"""Every entity skin the art check reports, found by name so a new skin of the same kind is cleaned too."""
	e = TEX / "entity"
	out = []
	for pat in ("future/*.png", "dino/*.png", "rogue_fighter/*.png", "unseeing/*.png", "worn/*.png", "gun_*.png",
				"web_symbiote_*.png", "xenoform_*.png", "backpack.png", "bomb_glider.png", "hoverboard.png", "space_pod.png", "chest/eden/sunroot.png"):
		out += sorted(e.glob(pat))
	return [p for p in out if "eyes" not in p.name]


def main():
	for key, mid in DUSTS.items():
		img = pile(mid)
		for (x, y) in CRYSTALS.get(key, []):
			img.putpixel((x, y), (236, 234, 226, 255))
		img.save(TEX / "item" / (key + ".png"))
	run_list([TEX / "item" / (p + ".png") for p in CLEAN_ITEMS if (TEX / "item" / (p + ".png")).exists()], ITEM_COLOURS, True)
	run_list([TEX / "block" / (p + ".png") for p in CLEAN_BLOCKS if (TEX / "block" / (p + ".png")).exists()], BLOCK_COLOURS, False)
	for p in skins():
		img = Image.open(p).convert("RGBA")
		if art_audit.faults(p):
			fold_until_clean(p, img, SKIN_COLOURS, True, 16)


if __name__ == "__main__":
	main()
