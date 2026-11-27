"""Reason look, other mods' textures (docs/Vanilla-Style.md, "Credits and licences").

    python -I tools/vanilla_style/other_mods.py <extracted jars folder> <vanilla 26.3 assets/minecraft/textures folder>

Writes replacements into src/main/resources/resourcepacks/vanilla_style/assets/<modid>/textures/<same path>, so the
mods' own jars never change. Two kinds:
- CLEANED (licence allows changes: Cube Animals CC0, Vanilla Constructs MIT, Eternal Nether MIT): their own pixels,
  smoothed (jitter removed) and folded into a few ramp steps.
- PAINTED (all rights reserved or limited licence: Shippy Ships, Cooking for Blockheads, Stellarity, Knight Armor): fresh
  paintings. The original is looked at only to know what the item is; no pixel is copied. Shapes are drawn here, the
  colours come from vanilla's own ramps or the dye colours, and the knight armour starts from vanilla's iron armour.
Simply Tents and the knight weapons are on 3D (GeckoLib / custom) models, so a flat icon would not show: skipped.
"""
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent.parent
OUT = ROOT / "src/main/resources/resourcepacks/vanilla_style/assets"
sys.path.insert(0, str(Path(__file__).resolve().parent))
import fish_icons  # noqa: E402
import powers_reason  # noqa: E402
from vstyle import MAT, hx  # noqa: E402


def save(mod, rel, img):
	p = OUT / mod / "textures" / rel
	p.parent.mkdir(parents=True, exist_ok=True)
	img.save(p)


def lum(c):
	return c[0] * 0.299 + c[1] * 0.587 + c[2] * 0.114


def sh(c, f):
	return tuple(max(0, min(255, int(round(v * f)))) for v in c[:3])


def lt(c, f):
	return tuple(min(255, int(round(v + (255 - v) * f))) for v in c[:3])


def ramp4(c):
	"""a dye or paint colour as 4 steps: outline, shade, mid, light."""
	c = ((c >> 16) & 255, (c >> 8) & 255, c & 255) if isinstance(c, int) else c
	return [sh(c, 0.45), sh(c, 0.72), tuple(c[:3]), lt(c, 0.28)]


def outline_pass(cells, rampof):
	"""cells: {(x, y): (part, level 0..3)} -> image, with the outer ring in the part's darkest shade (lit side one lighter)."""
	img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
	px = img.load()
	for (x, y), (part, lv) in cells.items():
		r = rampof[part]
		edge = [(x + dx, y + dy) not in cells for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))]
		if any(edge):
			lit = ((x - 1, y) not in cells or (x, y - 1) not in cells) and not ((x + 1, y) not in cells or (x, y + 1) not in cells)
			lv = 1 if lit else 0
		px[x, y] = r[max(0, min(len(r) - 1, lv))] + (255,)
	return img


# ---------------------------------------------------------------------------------------------------------------- sails
CLOTH = [(0x7a, 0x7e, 0x88), (0xa4, 0xa8, 0xb2), (0xd0, 0xd3, 0xda), (0xf0, 0xf1, 0xf4)]
RED = [(0x5c, 0x1a, 0x18), (0x8e, 0x2a, 0x26), (0xc2, 0x3a, 0x34), (0xde, 0x58, 0x4e)]
GRID = 10  # the cloth's painted area, 10 wide and 9 tall


def _g(*rows):
	return rows


SAILS = {
	"base": lambda x, y: False,
	"brick": lambda x, y: y % 3 == 2 or (x + (y // 3) * 2) % 4 == 0,
	"checker": lambda x, y: (x // 2 + y // 2) % 2 == 0,
	"circle": lambda x, y: (x - 4.5) ** 2 + (y - 4) ** 2 <= 9,
	"creeper": lambda x, y: (y in (2, 3) and x in (2, 3, 6, 7)) or (y in (4, 5) and x in (4, 5)) or (y in (6, 7) and x in (3, 4, 5, 6) and not (y == 7 and x in (4, 5))),
	"cross": lambda x, y: abs(abs(x - 4.5) - abs(y - 4) * 1.1) < 0.8,
	"flow": lambda x, y: (x + y) % 5 in (0, 1) and (x - y) % 4 != 0 or (x * 2 + y) % 7 == 0,
	"flower": lambda x, y: (x, y) in {(4, 1), (5, 1), (2, 3), (3, 3), (6, 3), (7, 3), (3, 5), (6, 5), (4, 6), (5, 6)} or (x, y) in {(4, 3), (5, 3), (4, 4), (5, 4)},
	"globe": lambda x, y: ((x - 4.5) ** 2 + (y - 4) ** 2 <= 16 and ((x - 4.5) ** 2 + (y - 4) ** 2 >= 9 or y == 4 or x in (4, 5))),
	"guster": lambda x, y: (x, y) in {(2, 1), (3, 1), (4, 1), (5, 1), (6, 2), (6, 3), (5, 4), (4, 4), (3, 4), (3, 3), (4, 3), (3, 6), (4, 6), (5, 6), (6, 6), (7, 6), (2, 5), (7, 5)},
	"large_checker": lambda x, y: (x // 3 + y // 3) % 2 == 0,
	"large_stripes": lambda x, y: x // 3 % 2 == 0,
	"mojang": lambda x, y: (x in (1, 2) and 1 <= y <= 7) or (x in (7, 8) and 1 <= y <= 7) or (y == x - 1 and 2 <= x <= 4) or (y == 9 - x and 5 <= x <= 7 and y >= 2),
	"piglin": lambda x, y: (2 <= x <= 7 and 3 <= y <= 6 and not (x in (3, 6) and y in (4, 5))) or (x in (1, 8) and y == 2),
	"rhombus": lambda x, y: abs(x - 4.5) + abs(y - 4) * 1.1 <= 4.2,
	"skull": lambda x, y: (2 <= x <= 7 and 1 <= y <= 5 and not (x in (3, 6) and y in (3, 4)) and not (x in (4, 5) and y == 5)) or (x in (3, 4, 5, 6) and y in (6, 7) and (x + y) % 2 == 1),
	"straight_cross": lambda x, y: x in (4, 5) or y in (3, 4),
	"stripes": lambda x, y: x % 4 in (0, 1),
	"vanilla_stripes": lambda x, y: y % 4 in (0, 1),
}


def sail(pattern):
	cells = {}
	for x in range(1, 15):
		for y in (1, 2):
			cells[(x, y)] = ("wood", 2)
	for y in range(3, 14):
		x0, x1 = (2, 13) if y < 12 else (3, 12) if y == 12 else (4, 11)
		for x in range(x0, x1 + 1):
			cells[(x, y)] = ("cloth", 2)
	for y in range(4, 13):
		for x in range(3, 13):
			if (x, y) in cells and SAILS[pattern](x - 3, y - 4):
				cells[(x, y)] = ("red", 2)
	# shade: the right column and the lower rows a step darker, the top-left a step lighter
	for (x, y), (part, lv) in list(cells.items()):
		if part == "wood":
			continue
		lv = 2 + (1 if (x <= 4 and y <= 6) else 0) - (1 if (x >= 12 or y >= 12) else 0)
		cells[(x, y)] = (part, lv)
	rampof = {"wood": [tuple(c) for c in MAT["wood"][:4]], "cloth": CLOTH, "red": RED}
	return outline_pass(cells, rampof)


# --------------------------------------------------------------------------------------------------------------- ovens
DYES = {"white": 0xd5d9dc, "orange": 0xe0711c, "magenta": 0xb53ea8, "light_blue": 0x3aa6d6, "yellow": 0xf1c01c, "lime": 0x6fb819,
		"pink": 0xe988ab, "gray": 0x474f52, "light_gray": 0x8a8a82, "cyan": 0x158a91, "purple": 0x7a2ab4, "blue": 0x35399d,
		"brown": 0x724728, "green": 0x4f6a1c, "red": 0x9c2020, "black": 0x1b1b21}
GLASS = [(0x16, 0x16, 0x1a), (0x26, 0x26, 0x2c), (0x3a, 0x3a, 0x42), (0x58, 0x58, 0x62)]
FLAME = [(0x8a, 0x2a, 0x10), (0xc8, 0x45, 0x1a), (0xf2, 0x76, 0x1c), (0xff, 0xbe, 0x3b)]


def oven(dye, active):
	r = ramp4(DYES[dye])
	img = Image.new("RGBA", (16, 16))
	px = img.load()
	for y in range(16):
		for x in range(16):
			c = r[2]
			if y == 0:
				c = r[3]
			elif y == 15 or x == 15:
				c = r[0]
			elif x == 0:
				c = r[3] if y < 15 else r[1]
			elif y == 5:
				c = r[0]
			elif y == 6:
				c = r[3]
			elif y == 14:
				c = r[1]
			px[x, y] = c + (255,)
	# knobs on the control strip
	for kx in (3, 6, 9, 12):
		px[kx, 2] = r[1]
		px[kx + 1, 2] = r[1]
		px[kx, 3] = r[0]
		px[kx + 1, 3] = r[0]
	# the window: a dark frame, the glass (or the fire) inside, a glint
	for y in range(8, 13):
		for x in range(3, 13):
			frame = x in (3, 12) or y in (8, 12)
			px[x, y] = (r[0] if frame else GLASS[1]) + (255,)
	for x in range(4, 12):
		px[x, 8] = r[0] + (255,)
	if active:
		for y in range(9, 12):
			for x in range(4, 12):
				v = 3 if y == 11 and x % 3 else (2 if (x + y) % 3 else 1)
				px[x, y] = FLAME[v if y > 9 else 1] + (255,)
		for x in (5, 8, 10):
			px[x, 11] = FLAME[3] + (255,)
	else:
		for x, y in ((9, 9), (8, 10), (7, 11)):
			px[x, y] = GLASS[3] + (255,)
		for x in range(4, 12):
			px[x, 9] = GLASS[2] + (255,)
	# a handle under the window
	for x in range(4, 12):
		px[x, 13] = r[3] + (255,)
	return img


# -------------------------------------------------------------------------------------------------------------- knight
STEEL = [hx(c) for c in ("1c2229", "3b4550", "5f6c78", "8896a2", "b0bcc6", "d2dae0", "eef2f4")]
LEATHER = [hx(c) for c in ("2a1a10", "4a2e1a", "6b4424", "8a5a30")]
ORANGE = [hx(c) for c in ("5a2a0c", "8a3e10", "c4601a", "e88a2c")]


def recolour(img, ramp):
	cols = sorted({p[:3] for p in img.getdata() if p[3] > 0}, key=lum)
	k = len(cols)
	m = {c: ramp[round(i * (len(ramp) - 1) / max(1, k - 1))] for i, c in enumerate(cols)}
	out = img.copy()
	op = out.load()
	for y in range(16):
		for x in range(16):
			if op[x, y][3] > 0:
				op[x, y] = m[op[x, y][:3]] + (255,)
	return out


def knight(van, piece):
	img = Image.open(van / "item" / ("iron_" + piece + ".png")).convert("RGBA")
	out = recolour(img, STEEL)
	px = out.load()
	if piece == "helmet":  # the black T of the visor
		for x in range(6, 11):
			px[x, 8] = STEEL[0] + (255,)
		for y in (9, 10):
			for x in (7, 8):
				px[x, y] = STEEL[0] + (255,)
	elif piece == "chestplate":  # a leather strap across the chest, and a gold rivet
		for i in range(8):
			px[3 + i, 3 + i] = LEATHER[2 if i % 2 else 3] + (255,)
		px[7, 7] = ORANGE[3] + (255,)
	elif piece == "leggings":  # orange cloth showing below the plates
		for y in range(9, 13):
			for x in range(4, 12):
				if px[x, y][3] and x not in (7, 8):
					px[x, y] = ORANGE[1 + (x + y) % 3 // 2] + (255,)
	elif piece == "boots":
		for y in (11, 12):
			for x in range(16):
				if px[x, y][3] and x in (3, 4, 5, 6, 10, 11, 12, 13):
					px[x, y] = LEATHER[1 + (x % 2)] + (255,)
	return out


# ------------------------------------------------------------------------------------------------------------ stellarity
def grid_img(rows, legend):
	img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
	px = img.load()
	for y, r in enumerate(rows):
		for x, ch in enumerate(r):
			if ch in legend:
				px[x, y] = legend[ch] + (255,)
	return img


VOID = [hx(c) for c in ("1c0e22", "3e1a4c", "6a2c7e", "9a46a8", "c868d0", "e898e8")]


def clover():
	rows = ["................", "...aa....aa.....", "..abba..abba....", "..abcba.abcba...", "...abcbabcba....",
			"....abcddcba....", ".....abddba.....", "....abcddcba....", "...abcba.abcba..", "..abcba..abcba..",
			"..abba....abba..", "...aa.....aa....", "........f.......", ".......f........", "......f.........", "................"]
	rows = ["................", "....aa....aa....", "...abba..abba...", "...abcbaabcba...", "....abcbbcba....",
			".....abddba.....", "....abcbbcba....", "...abcbaabcba...", "...abba..abba...", "....aa.f.aa.....",
			"........f.......", ".......f........", "......f.........", "................", "................", "................"]
	return grid_img(rows, {"a": VOID[0], "b": VOID[1], "c": VOID[2], "d": VOID[4], "f": VOID[1]})


def koi():
	pal = dict(back=0x5a2a88, flank=0x9a5acc, belly=0xe8dcf4, fin=0xf4f0f8, pattern="smooth", pc=0xc88af0)
	return fish_icons.icon("carp", pal, False, "oily")


def harvester(tier):
	ramps = {3: [hx(c) for c in ("1e1830", "3a2e68", "5a4aa0", "8a6ad0", "c88ae8")], 4: [hx(c) for c in ("2a1030", "5a2058", "8a3080", "c0489c", "f088cc")],
			 5: [hx(c) for c in ("3a1008", "7a2410", "c4481c", "f08428", "ffd05a")]}[tier]
	img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
	px = img.load()
	for i in range(9):  # the blade runs from the top right down to the guard
		x, y = 13 - i, 2 + i
		for dx, c in ((-1, ramps[0]), (0, ramps[4] if i < 4 else ramps[3]), (1, ramps[1])):
			px[x + dx, y] = c + (255,)
		px[x, y - 1] = ramps[0] + (255,)
	# the guard across the blade, the grip and the pommel
	for x, y in ((3, 10), (4, 11), (5, 12), (6, 11), (7, 10), (8, 11), (4, 9), (7, 12), (6, 13)):
		px[x, y] = STEEL[2 if (x + y) % 2 else 3] + (255,)
	for x, y in ((4, 13), (3, 14), (2, 14)):
		px[x, y] = hx("684e1e") + (255,)
	px[1, 15] = STEEL[1] + (255,)
	px[2, 15] = STEEL[1] + (255,)
	# tier 4 grows crystal spikes off the blade
	if tier >= 4:
		for x, y in ((9, 4), (6, 7), (10, 3)):
			px[x, y] = ramps[1] + (255,)
		px[11, 6] = ramps[3] + (255,)
		px[12, 7] = ramps[0] + (255,)
	return img


def end_map():
	paper = [hx(c) for c in ("6e5a38", "a08a5c", "d8c898", "efe2b8")]
	cells = {}
	for y in range(1, 15):
		for x in range(1, 15):
			cells[(x, y)] = ("paper", 2 if (x + y) % 5 else 3)
	for y in range(4, 12):
		for x in range(5, 11):
			cells[(x, y)] = ("void", 2 if y < 9 else 1)
	for x, y in ((6, 5), (7, 5), (9, 5), (6, 4), (9, 4), (7, 8), (8, 8), (7, 7), (8, 7)):
		cells[(x, y)] = ("void", 3)
	for x in (6, 7, 8, 9):
		cells[(x, 11)] = ("void", 0)
	return outline_pass(cells, {"paper": paper, "void": VOID[:4]})


def light_disc(van):
	img = Image.open(van / "item" / "music_disc_13.png").convert("RGBA")
	px = img.load()
	label = [hx(c) for c in ("5a2058", "8a3080", "c0489c", "f088cc")]
	for y in range(16):
		for x in range(16):
			r, g, b, a = px[x, y]
			if a and max(r, g, b) - min(r, g, b) > 24:
				l = lum((r, g, b))
				px[x, y] = label[0 if l < 90 else 1 if l < 150 else 2 if l < 200 else 3] + (255,)
	return img


# ------------------------------------------------------------------------------------------------------------- cleaning
def clean_file(src, dst_mod, rel, n, items):
	img = Image.open(src).convert("RGBA")
	if items:
		img = powers_reason.solid(img)
	few = powers_reason.fold(img, n, not items)
	save(dst_mod, rel, few)


def clean_mod(jars, jar, mod, subdirs, n, items, min_colours):
	base = jars / jar / "assets" / mod / "textures"
	count = 0
	for sub in subdirs:
		for p in sorted((base / sub).rglob("*.png")):
			img = Image.open(p).convert("RGBA")
			if img.width > 128 or img.height > 128 and img.height % img.width:
				continue
			if img.height > img.width and img.height % img.width == 0:  # an animation strip: leave it
				continue
			if len({q[:3] for q in img.getdata() if q[3] > 0}) < min_colours:
				continue
			clean_file(p, mod, str(p.relative_to(base)), n, items)
			count += 1
	return count


def main(jars, van):
	jars, van = Path(jars), Path(van)
	n = {}
	n["cube entity"] = clean_mod(jars, "cube-animals-1550917-9411257", "cubeanimals", ["entity"], 56, False, 64)
	n["cube item"] = clean_mod(jars, "cube-animals-1550917-9411257", "cubeanimals", ["item"], 16, True, 20)
	n["vanilla constructs"] = clean_mod(jars, "vanilla-constructs-il0WctcM", "vanillaconstructs", ["item"], 18, True, 24)
	eb = jars / "eternal-nether-TIviaC6F/assets/eternalnether/textures/block"
	for name in ("chiseled_withered_blackstone", "chiseled_withered_blackstone_top", "cracked_withered_blackstone", "withered_basalt",
				 "withered_blackstone", "withered_blackstone_top", "withered_coal_block", "withered_quartz_block"):
		clean_file(eb / (name + ".png"), "eternalnether", "block/" + name + ".png", 24, False)
	clean_file(jars / "eternal-nether-TIviaC6F/assets/eternalnether/textures/item/netherite_bell.png", "eternalnether", "item/netherite_bell.png", 16, True)
	for p in SAILS:
		save("shippy-ships", "item/%s_sail_pattern.png" % p, sail(p))
	for d in DYES:
		save("cookingforblockheads", "block/%s_oven_front.png" % d, oven(d, False))
		save("cookingforblockheads", "block/%s_oven_front_active.png" % d, oven(d, True))
	for piece, name in (("helmet", "head"), ("chestplate", "chest"), ("leggings", "legging"), ("boots", "boot")):
		save("knightarmorweapons", "item/knight_armor%s.png" % name, knight(van, piece))
	save("stellarity", "item/decayed_clover.png", clover())
	save("stellarity", "item/food/fish/ender_koi.png", koi())
	for tier, name in ((3, "tier_3"), (4, "tier_4"), (5, "molten_tier_4")):
		save("stellarity", "item/harvester/%s.png" % name, harvester(tier))
	save("stellarity", "item/map/end_city_map.png", end_map())
	save("stellarity", "item/music_disc/deviants_light_music_box.png", light_disc(van))
	print(n)


if __name__ == "__main__":
	main(sys.argv[1], sys.argv[2])
