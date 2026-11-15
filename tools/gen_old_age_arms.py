"""Draws and writes the old age's peoples' arms (src/main/java/com/jaymospowers/armory/OldAgeArms.java): the sands'
hooked sickle-sword, the plains' short legion blade and tall shield, leaf-bladed spear and round shield, Blossom's broad
sabre and the north's bearded axe, and their forged heads (tinkering/WeaponParts.OLD_AGE). Our own art, drawn here from
shapes laid along the sprite's diagonal (gen_war_arms' way), in the pack's palette; with every model, item definition,
Better Combat file, tag line and lang key they need (lang part old_age_arms.json). Run it from the root:

    python -I tools/gen_old_age_arms.py

The parts' ids must match the Kinds added to tinkering/WeaponParts.
"""
import json
import math
import os
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "tools"))
import gen_gear_picks as gear  # noqa: E402
import gen_war_arms as war  # noqa: E402

ASSETS = war.ASSETS
DATA = war.DATA
MCDATA = war.MCDATA
rgb = gear.rgb
WOOD = war.WOOD
DARK_WOOD = war.DARK_WOOD
IRON = war.IRON
STEEL = war.STEEL
BRONZE = [rgb("e8b868"), rgb("b07a34"), rgb("6a4418")]
GOLD = [rgb("f4dc78"), rgb("c8a034"), rgb("7a5a18")]
LINEN = [rgb("e8dcc0"), rgb("bca880"), rgb("7a6a48")]
RED_WRAP = [rgb("c0402c"), rgb("8a2618"), rgb("4c140c")]
LEATHER = [rgb("8a5a34"), rgb("5e3c22"), rgb("3a2414")]


# ---- the weapons (16x16 unless said) ----

def sickle_sword():
	"""The sands' hooked sickle-sword: a linen-wrapped grip, a short straight neck, then the blade curving out and back
	in a hook, its edge on the outside of the curve."""
	def hook(a, b):
		if 6.0 <= a <= 13.5:
			c = -0.075 * (a - 6.0) ** 2 + 0.12 * (a - 6.0)
			return abs(b - c) <= 1.0 + 0.25 * math.sin(math.pi * (a - 6.0) / 7.5)
		return False
	return war.diagonal(16, 1, 14, [
		(lambda a, b: 0 <= a <= 4 and abs(b) <= 0.5, LINEN),
		(lambda a, b: a <= 0.8 and abs(b) <= 1.0, GOLD),
		(lambda a, b: 4 <= a <= 4.8 and abs(b) <= 1.2, GOLD),
		(lambda a, b: 4.8 <= a <= 6.5 and abs(b) <= 0.5, BRONZE),
		(hook, BRONZE),
	])


def legion_sword():
	"""The plains' short legion blade: a round pommel, a bone grip, a short guard, a broad straight blade with a long point."""
	return war.diagonal(16, 1, 14, [
		(lambda a, b: 0 <= a <= 3.5 and abs(b) <= 0.5, LINEN),
		(lambda a, b: a <= 1.0 and abs(b) <= 1.2, BRONZE),
		(lambda a, b: 3.5 <= a <= 4.3 and abs(b) <= 1.6, BRONZE),
		(lambda a, b: 4.3 <= a <= 12.5 and abs(b) <= min(1.1, (12.5 - a) * 0.6 + 0.1), STEEL),
	])


def broad_sabre():
	"""Blossom's broad sabre: a red-wrapped grip, a round guard, a blade that widens toward its tip and curves back."""
	def blade(a, b):
		if 4.8 <= a <= 13.6:
			c = 0.035 * (a - 4.8) ** 2
			w = 0.8 + 0.08 * (a - 4.8)
			if a > 12.4:
				w *= (13.6 - a) / 1.2
			return -0.2 <= b - c <= w * 1.5
		return False
	return war.diagonal(16, 1, 14, [
		(lambda a, b: 0 <= a <= 4 and abs(b) <= 0.5, RED_WRAP),
		(lambda a, b: a <= 0.7 and abs(b) <= 0.9, GOLD),
		(lambda a, b: 4 <= a <= 4.8 and abs(b) <= 1.8, GOLD),
		(blade, STEEL),
	])


def bearded_axe():
	"""The north's bearded axe: a long haft and a narrow head whose blade drops in a long beard below it."""
	def head(a, b):
		if 0.5 <= b <= 4.2:
			low = 10.8 - (b - 0.5) * 1.1 if b > 1.6 else 10.6
			return low <= a <= 12.6 - max(0.0, b - 3.4) * 0.5
		return False
	return war.diagonal(16, 1, 14, [
		(lambda a, b: 0 <= a <= 13.5 and abs(b) <= 0.5, WOOD),
		(lambda a, b: 1.0 <= a <= 2.2 and abs(b) <= 0.7, LEATHER),
		(head, IRON),
		(lambda a, b: 12.3 <= a <= 13.5 and -1.2 <= b <= 0.5, IRON),
	])


def leaf_spear():
	"""The plains' leaf-bladed spear (32x32, held as the glaive is): an ash shaft, a bronze socket, a leaf-shaped head."""
	def leaf(a, b):
		if 22.5 <= a <= 30.5:
			t = (a - 22.5) / 8.0
			return abs(b) <= 1.9 * math.sin(math.pi * min(1.0, t * 1.15)) ** 0.7 * (1.0 - 0.6 * t) + 0.2
		return False
	return war.diagonal(32, 2, 29, [
		(lambda a, b: 0 <= a <= 22 and abs(b) <= 0.5, WOOD),
		(lambda a, b: 0 <= a <= 1.2 and abs(b) <= 0.8, BRONZE),
		(lambda a, b: 20.5 <= a <= 23 and abs(b) <= 0.9, BRONZE),
		(leaf, STEEL),
	])


# ---- the shields: 3D boards like the Armory's round shield, each with its own texture (64x64, 1 px a model unit) ----

def shield_image(board, w, h, face, back, rim):
	"""The board's front at (0, 16), its back at (w, 16), its edges beside them; the handle where the round shield's is."""
	img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
	for y in range(h):
		for x in range(w):
			if board(x, y):
				img.putpixel((x, 16 + y), face(x, y))
				img.putpixel((w + x, 16 + y), back(x, y))
	for y in range(h):
		for x in (2 * w, 2 * w + 1):
			img.putpixel((x, 16 + y), rim[1])
	for x in range(2 * w):
		img.putpixel((x, 16 + h), rim[1])
	# the handle (the round shield's uv: columns 26 to 42, rows 0 to 12)
	for y in range(0, 12):
		for x in range(26, 42):
			img.putpixel((x, y), LEATHER[1] if (x + y) % 5 else LEATHER[2])
	return img


def shield_model(name, w, h, board_box, blocking):
	"""The model: the board (w by h, 1 deep) and the handle, displayed as the round shield is (or as it is raised)."""
	base = json.load(open(os.path.join(ASSETS, "models", "item", "armory", "iron_round_shield" + ("_blocking" if blocking else "") + ".json")))
	u = lambda px: px / 4.0
	board = {"from": board_box[0], "to": board_box[1], "faces": {
		"south": {"uv": [u(0), u(16), u(w), u(16 + h)], "texture": "#t"},
		"north": {"uv": [u(w), u(16), u(2 * w), u(16 + h)], "texture": "#t"},
		"east": {"uv": [u(2 * w), u(16), u(2 * w + 1), u(16 + h)], "texture": "#t"},
		"west": {"uv": [u(2 * w + 1), u(16), u(2 * w + 2), u(16 + h)], "texture": "#t"},
		"up": {"uv": [u(0), u(16 + h), u(w), u(17 + h)], "texture": "#t"},
		"down": {"uv": [u(w), u(16 + h), u(2 * w), u(17 + h)], "texture": "#t"}}}
	handle = base["elements"][1]
	tex = "jaymospowers:item/armory/" + name
	return {"gui_light": "front", "textures": {"t": tex, "particle": tex}, "elements": [board, handle], "display": base["display"]}


def tall_shield():
	w, h = 10, 20

	def board(x, y):
		return not ((x in (0, w - 1)) and (y in (0, h - 1)))

	def face(x, y):
		if x in (0, w - 1) or y in (0, h - 1):
			return BRONZE[1]
		if abs(x - 4.5) <= 1.5 and abs(y - 9.5) <= 1.5:
			return (GOLD[0] if x < 5 and y < 10 else GOLD[1])
		# the painted face: deep red, a gold bar above and below the boss
		if abs(x - 4.5) <= 3.5 and y in (4, 15):
			return GOLD[1]
		return (RED_WRAP[0] if (x + y) % 7 else RED_WRAP[1])

	def back(x, y):
		return (DARK_WOOD[1] if y % 3 else DARK_WOOD[2])
	return shield_image(board, w, h, face, back, BRONZE), w, h, ([-5, -10, 1], [5, 10, 2])


def old_round_shield():
	w, h = 14, 14

	def board(x, y):
		return (x - 6.5) ** 2 + (y - 6.5) ** 2 <= 7.1 ** 2

	def face(x, y):
		r = math.hypot(x - 6.5, y - 6.5)
		if r >= 5.9:
			return BRONZE[2 if x + y > 14 else 1]
		if r <= 1.6:
			return BRONZE[0]
		# a bronze face hammered in rings
		return (BRONZE[0] if 3.4 <= r <= 4.0 else BRONZE[1])

	def back(x, y):
		return (WOOD[1] if x % 3 else WOOD[2])
	return shield_image(board, w, h, face, back, BRONZE), w, h, ([-7, -7, 1], [7, 7, 2])


# ---- the Tool Station heads ----

def grid_rows(test):
	"""16 rows from a test on plain (x, y): '#' outline, 'x' metal, 'o' shine."""
	filled = {(x, y) for y in range(16) for x in range(16) if test(x, y)}
	edge = {(x, y) for x, y in filled if any((x + dx, y + dy) not in filled for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))}
	rows = []
	for y in range(16):
		row = ""
		for x in range(16):
			if (x, y) not in filled:
				row += "."
			elif (x, y) in edge:
				row += "#"
			else:
				row += "o" if (x - 1, y) in edge or (x, y - 1) in edge else "x"
		rows.append(row)
	return rows


def hook_head(a, b):
	if 5.0 <= a <= 12.5:
		c = -0.075 * (a - 5.0) ** 2 + 0.12 * (a - 5.0)
		return abs(b - c) <= 1.1 + 0.25 * math.sin(math.pi * (a - 5.0) / 7.5)
	return 1.0 <= a <= 5.5 and abs(b) <= 0.7


PARTS = {
	"sickle_sword_blade": ("Sickle-Sword Blade", lambda: war.rows_of([hook_head], 1, 14)),
	"legion_sword_blade": ("Legion Blade", lambda: war.rows_of([
		lambda a, b: 0.5 <= a <= 2.0 and abs(b) <= 0.5,
		lambda a, b: 2.0 <= a <= 11.5 and abs(b) <= min(1.3, (11.5 - a) * 0.7 + 0.1)], 1, 14)),
	"tall_shield_face": ("Tall Shield Face", lambda: grid_rows(lambda x, y: 4 <= x <= 11 and 1 <= y <= 14 and not (x in (4, 11) and y in (1, 14)))),
	"leaf_spear_head": ("Leaf Spear Head", lambda: war.rows_of([
		lambda a, b: 0.5 <= a <= 3.5 and abs(b) <= 0.8,
		lambda a, b: 3.5 <= a <= 12.0 and abs(b) <= 2.0 * math.sin(math.pi * min(1.0, (a - 3.5) / 8.5 * 1.15)) ** 0.7 * (1.0 - 0.6 * (a - 3.5) / 8.5) + 0.2],
		2, 13)),
	"old_round_shield_face": ("Round Shield Face", lambda: grid_rows(lambda x, y: (x - 7.5) ** 2 + (y - 7.5) ** 2 <= 6.6 ** 2)),
	"broad_sabre_blade": ("Broad Sabre Blade", lambda: war.rows_of([
		lambda a, b: 0.5 <= a <= 2.5 and abs(b) <= 0.5,
		lambda a, b: 2.5 <= a <= 12.5 and -0.8 <= b - 0.03 * (a - 2.5) ** 2 <= (1.1 + 0.1 * (a - 2.5)) * (1.4 if a <= 11.3 else 1.4 * (12.5 - a) / 1.2)],
		1, 14)),
	"bearded_axe_head": ("Bearded Axe Head", lambda: war.rows_of([
		lambda a, b: 6.0 <= a <= 9.0 and abs(b) <= 1.0,
		lambda a, b: -4.8 <= b <= -1.0 and (6.4 - (abs(b) - 1.0) * 1.2 if abs(b) > 2.0 else 6.2) <= a <= 9.0 - max(0.0, abs(b) - 3.8) * 0.6],
		1, 14)),
}

NAMES = {
	"sickle_sword": ("Sunstep Sickle-Sword", "A hooked blade of the sands' old people. Its hook drags a raised shield down."),
	"legion_sword": ("Pillared Short Blade", "The plains' old legions stabbed with it from behind the tall shield."),
	"tall_shield": ("Pillared Tall Shield", "A tall curved board: slow to bring up, and it covers you well."),
	"leaf_spear": ("Pillared Leaf Spear", "A leaf-bladed spear for one hand, carried with the round shield."),
	"old_round_shield": ("Pillared Round Shield", "A bronze-faced round shield, quick to raise."),
	"broad_sabre": ("Court Broad Sabre", "The walled court's broad curved blade, heavy at the tip."),
	"bearded_axe": ("Mound-kin Bearded Axe", "The north's old axe. Its long beard hooks a shield aside."),
}


def main():
	glaive = json.load(open(os.path.join(ASSETS, "models", "item", "armory", "iron_glaive.json")))
	write = war.write
	for item, draw in (("sickle_sword", sickle_sword), ("legion_sword", legion_sword), ("broad_sabre", broad_sabre), ("bearded_axe", bearded_axe)):
		write(os.path.join(ASSETS, "items", item + ".json"), {"model": war.armory_item(item, draw())})
	write(os.path.join(ASSETS, "items", "leaf_spear.json"), {"model": war.armory_item("leaf_spear", leaf_spear(), display=glaive["display"])})
	for name, make in (("tall_shield", tall_shield), ("old_round_shield", old_round_shield)):
		img, w, h, box = make()
		gear.save(img, war.ARMORY + name)
		for blocking in (False, True):
			rel = war.ARMORY + name + ("_blocking" if blocking else "")
			write(os.path.join(ASSETS, "models", rel + ".json"), shield_model(name, w, h, box, blocking))
		write(os.path.join(ASSETS, "items", name + ".json"), {"model": {"type": "minecraft:condition", "property": "minecraft:using_item",
			"on_false": {"type": "minecraft:model", "model": "jaymospowers:" + war.ARMORY + name},
			"on_true": {"type": "minecraft:model", "model": "jaymospowers:" + war.ARMORY + name + "_blocking"}}})
	# Better Combat: the sickle-sword and the sabre cut like sabres; the legion blade stabs and cuts close; the leaf spear
	# thrusts one-handed (a shield in the other hand); the bearded axe chops like a war axe
	write(os.path.join(DATA, "weapon_attributes", "sickle_sword.json"), {"parent": "jaymospowers:combo_saber"})
	write(os.path.join(DATA, "weapon_attributes", "broad_sabre.json"), {"parent": "jaymospowers:combo_saber"})
	write(os.path.join(DATA, "weapon_attributes", "bearded_axe.json"), {"parent": "jaymospowers:combo_war_axe"})
	stab = lambda mult, sound: {"hitbox": "FORWARD_BOX", "damage_multiplier": mult, "angle": 0, "upswing": 0.4,
		"animation": "bettercombat:one_handed_stab", "swing_sound": {"id": sound}}
	cut = lambda side, mult: {"hitbox": "HORIZONTAL_PLANE", "damage_multiplier": mult, "angle": 90, "upswing": 0.45,
		"animation": "bettercombat:one_handed_slash_horizontal_" + side, "swing_sound": {"id": "bettercombat:sword_slash"}}
	write(os.path.join(DATA, "weapon_attributes", "legion_sword.json"), {"attributes": {"range_bonus": -0.25, "category": "sword",
		"attacks": [stab(1.0, "bettercombat:rapier_stab"), stab(1.0, "bettercombat:rapier_stab"), cut("right", 0.9)]}})
	write(os.path.join(DATA, "weapon_attributes", "leaf_spear.json"), {"attributes": {"range_bonus": 1.0, "category": "spear",
		"attacks": [stab(1.0, "bettercombat:spear_stab"), stab(1.05, "bettercombat:spear_stab"), stab(1.15, "bettercombat:spear_stab")]}})
	war.merge_tag(os.path.join(MCDATA, "tags", "item", "swords.json"),
		["jaymospowers:sickle_sword", "jaymospowers:legion_sword", "jaymospowers:leaf_spear", "jaymospowers:broad_sabre"])
	war.merge_tag(os.path.join(MCDATA, "tags", "item", "axes.json"), ["jaymospowers:bearded_axe"])
	war.merge_tag(os.path.join(MCDATA, "tags", "item", "enchantable", "durability.json"), ["jaymospowers:tall_shield", "jaymospowers:old_round_shield"])
	war.merge_tag(os.path.join(DATA, "tags", "item", "enchantable", "shield.json"), ["jaymospowers:tall_shield", "jaymospowers:old_round_shield"])
	lang = {"message.jaymospowers.smithing.needs_pattern": "No pattern for the %s."}
	# (the names only: no line under them explaining how they work)
	for item, (name, _hint) in NAMES.items():
		lang["item.jaymospowers." + item] = name
	for part, (name, rows) in PARTS.items():
		rows = rows()
		stone = gear.part_model(part, "stone", rows)
		iron = gear.part_model(part, "iron", rows)
		node = {"type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0,
			"cases": [{"when": "stone", "model": stone}, {"when": "iron", "model": iron}], "fallback": stone}
		write(os.path.join(ASSETS, "items", part + ".json"), {"model": gear.hot(node)})
		lang["item.jaymospowers." + part] = name
	write(os.path.join(ASSETS, "lang", "parts", "old_age_arms.json"), lang)


if __name__ == "__main__":
	main()
